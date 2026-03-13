/* =============================================================================
 * ClaudeMain.java  –  Brain for the three main bots (M1 / M2 / M3).
 *
 * ── Strategy ────────────────────────────────────────────────────────────────
 *  ADVANCING  →  Move as a staggered triangle to the holding line.
 *                Fire opportunistically at every step (fireCooldown permitting).
 *
 *  HOLDING    →  Idle at formation; watch for enemies.
 *
 *  FIRING     →  Focus-fire on the single closest enemy (Lanchester Square Law).
 *                Broadcast FOCUS <x> <y> so all bots converge on same target.
 *                Kiting: maintain [KITE_MIN, KITE_MAX] mm distance band.
 *                  < KITE_MIN → back away (turn away + forward)
 *                  > KITE_MAX → advance toward enemy
 *                  in band    → stand still and fire
 *
 *  RETREATING →  Health < HP_RETREAT and no enemy visible → fall back to spawn side.
 *
 *  TURN_LEFT / TURN_RIGHT / BACK  →  Obstacle avoidance (handled in base class).
 *
 * ── To enable ────────────────────────────────────────────────────────────────
 *  In Parameters.java set:
 *    teamAMainBotBrainClassName = "algorithms.external.ClaudeMain";
 *    teamBMainBotBrainClassName = "algorithms.external.ClaudeMain";
 * =============================================================================*/
package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;

public class ClaudeMain extends ClaudeUtils {

    // ── Formation parameters ─────────────────────────────────────────────────
    // Bots advance from their spawn X (~200 for A, ~2800 for B) to HOLD_X.
    private static final double HOLD_X_A = 950.0;
    private static final double HOLD_X_B = 2050.0;
    // Formation Y values match spawn Ys (800 / 1000 / 1200), so no Y-adjustment needed.
    private static final double[] FORM_Y = {800.0, 1000.0, 1200.0};

    // ── Kiting band ──────────────────────────────────────────────────────────
    // Optimal engagement distance: bullet range = 1000 mm.
    // Stay between KITE_MIN and KITE_MAX to maintain a clean firing line.
    private static final double KITE_MIN = 350.0;
    private static final double KITE_MAX = 870.0;

    // ── Health threshold ─────────────────────────────────────────────────────
    // Main bot health = 300 (takes 30 hits).  Retreat when < HP_RETREAT & no enemies.
    private static final double HP_RETREAT = 100.0;

    // ── Instance state ───────────────────────────────────────────────────────
    private double formX, formY;

    // =========================================================================
    public ClaudeMain() { super(); }

    // =========================================================================
    //  activate  –  called once at simulation start
    // =========================================================================
    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);

        // ── Identify which of the three main bots we are ─────────────────────
        // Robots are spawned at Y = 800 (M1), 1000 (M2), 1200 (M3) for both teams.
        // At startup each bot can see its immediate neighbours via radar (range 300 mm,
        // inter-bot gap = 200 mm).  M1 sees one ally to the SOUTH only;
        // M2 sees allies both NORTH and SOUTH; M3 sees one ally to the NORTH only.
        boolean hasN = false, hasS = false;
        for (IRadarResult o : detectRadar()) {
            // Parameters.NORTH = -π/2  →  normalised = 3π/2
            if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) hasN = true;
            if (aDist(o.getObjectDirection(), Parameters.SOUTH) < ANGLE_PREC) hasS = true;
        }

        if      (hasN && hasS)  id = M2;
        else if (!hasN && hasS) id = M1;
        else                    id = M3;

        // ── Set initial position ──────────────────────────────────────────────
        if (M1.equals(id)) {
            myX = teamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX;
            myY = teamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY;
        } else if (M2.equals(id)) {
            myX = teamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX;
            myY = teamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY;
        } else { // M3
            myX = teamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX;
            myY = teamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY;
        }

        // ── Formation target ──────────────────────────────────────────────────
        formX = teamA ? HOLD_X_A : HOLD_X_B;
        int idx = M1.equals(id) ? 0 : M2.equals(id) ? 1 : 2;
        formY = FORM_Y[idx];

        // ── Initialise state ──────────────────────────────────────────────────
        state       = S.ADVANCING;
        resumeState = S.ADVANCING;

        BotInfo self = allies.get(id);
        self.update(myX, myY, getHeading());
        self.alive = true;
    }

    // =========================================================================
    //  step  –  called every simulation tick
    // =========================================================================
    @Override
    public void step() {
        // ── Decrement firing cooldown ─────────────────────────────────────────
        if (fireCooldown > 0) fireCooldown--;

        // ── Perception ───────────────────────────────────────────────────────
        scanRadar();      // updates enemies list + may trigger obstacle avoidance
        readMessages();   // process ally broadcasts (POS / DEAD / ENEMY / FOCUS)
        ageEnemies();     // remove stale enemy records

        // ── Death check ───────────────────────────────────────────────────────
        if (getHealth() <= 0) {
            state = S.DEAD;
            allies.get(id).alive = false;
            broadcast("DEAD " + id);
            return;
        }

        // ── Broadcast own position every step ────────────────────────────────
        broadcast("POS " + id + " " + myX + " " + myY + " " + getHeading());

        // ── Opportunistic fire every step ─────────────────────────────────────
        // Fire regardless of current movement state (fire() and move() are independent).
        TrackedEnemy tgt = chooseTarget();
        if (tryFire(tgt)) {
            // Announce focus target → allies converge on same enemy (Lanchester)
            broadcast("FOCUS " + (int) tgt.x + " " + (int) tgt.y);
        }

        // ── State transitions (skip if currently in avoidance) ────────────────
        if (!isAvoiding() && state != S.DEAD && state != S.RETREATING) {
            if (!enemies.isEmpty()) {
                state = S.FIRING;
            } else if (state == S.FIRING) {
                // Enemy list cleared → return to formation
                state = (atFormation()) ? S.HOLDING : S.ADVANCING;
            }
        }

        // Health-gated retreat (only when no enemies visible to prevent panic retreat)
        if (getHealth() <= HP_RETREAT && enemies.isEmpty()
                && !isAvoiding() && state != S.DEAD) {
            state = S.RETREATING;
        }

        // ── Dispatch ─────────────────────────────────────────────────────────
        switch (state) {
            case ADVANCING:
                doAdvance();
                break;
            case HOLDING:
                // Idle — fire is already handled above
                break;
            case FIRING:
                doFire();
                break;
            case RETREATING:
                doRetreat();
                break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK:
                doAvoidStep();
                break;
            default:
                break;
        }
    }

    // =========================================================================
    //  Behaviour implementations
    // =========================================================================

    /** Move toward the formation waypoint; switch to HOLDING on arrival. */
    private void doAdvance() {
        if (atFormation()) { state = S.HOLDING; return; }
        goTo(formX, formY);
    }

    /**
     * Kiting combat loop.
     * fire() is already called at the top of step(); here we only handle
     * the movement component (advance / retreat to maintain optimal range).
     */
    private void doFire() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = atFormation() ? S.HOLDING : S.ADVANCING;
            return;
        }

        double d = Math.hypot(tgt.x - myX, tgt.y - myY);

        if (d < KITE_MIN) {
            // Too close — back away toward our side while keeping fire open
            double awayAngle = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(awayAngle)) stepTurnTo(awayAngle);
            else doMove(true);
        } else if (d > KITE_MAX) {
            // Too far — close the distance
            goTo(tgt.x, tgt.y);
        }
        // In [KITE_MIN, KITE_MAX]: hold position, only fire (already done above)
    }

    /** Return to spawn-side safe zone. */
    private void doRetreat() {
        double safeX = teamA ? 280.0 : 2720.0;
        goTo(safeX, myY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean atFormation() {
        return Math.hypot(myX - formX, myY - formY) < 65.0;
    }
}
