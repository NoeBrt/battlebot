/* =============================================================================
 * ClaudeSecondary.java  –  Brain for the two secondary (scout) bots (NBOT / SBOT).
 *
 * ── Strategy ────────────────────────────────────────────────────────────────
 *  Secondary bots are 3× faster than mains but have ⅓ the health.
 *  Their job is threefold:
 *    1. FLANKING   – Rush to an extreme Y position (north / south edge of arena)
 *                    then advance toward the enemy half of the map.
 *                    This creates a crossfire angle: enemies hit by perpendicular
 *                    fire cannot dodge both mains AND scouts simultaneously.
 *    2. SCOUTING   – Radar range = 500 mm vs mains' 300 mm.  Scouts detect enemies
 *                    earlier and broadcast their position, giving mains time to aim.
 *    3. HARASSMENT – Fire opportunistically at any detected enemy (focus-fire same
 *                    target as mains via FOCUS broadcast).
 *                    Kiting band [ENG_BACK, ENG_MAX] keeps scouts at safe distance.
 *
 *  RETREATING  →  Health < HP_RETREAT → return to spawn-side flank corner.
 *                 Even retreating scouts still broadcast enemy positions.
 *
 * ── Key design note ──────────────────────────────────────────────────────────
 *  Scouts switch to FIRING as soon as enemies are detected, even mid-flanking.
 *  Flanking progress (reachedFlankY, reachedFlankX) is preserved across
 *  interruptions, so the scout resumes from where it left off once the enemy
 *  disappears from radar.
 *
 * ── To enable ────────────────────────────────────────────────────────────────
 *  In Parameters.java set:
 *    teamASecondaryBotBrainClassName = "algorithms.external.ClaudeSecondary";
 *    teamBSecondaryBotBrainClassName = "algorithms.external.ClaudeSecondary";
 * =============================================================================*/
package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;

public class ClaudeSecondary extends ClaudeUtils {

    // ── Flank Y targets (close to arena edge; margin kept for obstacle avoid) ──
    // NBOT rushes to low Y (north edge), SBOT to high Y (south edge).
    private static final double FLANK_Y_NBOT = 220.0;
    private static final double FLANK_Y_SBOT = 1780.0;

    // ── Harassment X: how deep into the map each scout pushes ────────────────
    // This creates a crossfire angle with the main bots at x≈950 (Team A).
    private static final double HARASS_X_A = 1300.0;
    private static final double HARASS_X_B = 1700.0;

    // ── Engagement kiting band ───────────────────────────────────────────────
    // ENG_BACK: too close → back off perpendicular to escape
    // ENG_MAX:  too far   → advance toward enemy
    // Between ENG_BACK and ENG_MAX: stand still and fire
    private static final double ENG_BACK = 250.0;
    private static final double ENG_MAX  = 920.0;

    // ── Health gate ──────────────────────────────────────────────────────────
    // Secondary health = 100.  Retreat when critically low.
    private static final double HP_RETREAT = 40.0;

    // ── Instance state ───────────────────────────────────────────────────────
    private double  flankY;         // extreme Y waypoint for this bot
    private double  harassX;        // lateral penetration X waypoint
    private boolean reachedFlankY  = false; // phase 1 complete
    private boolean reachedFlankX  = false; // phase 2 complete

    // =========================================================================
    public ClaudeSecondary() { super(); }

    // =========================================================================
    //  activate
    // =========================================================================
    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);

        // ── Identify NBOT vs SBOT ────────────────────────────────────────────
        // At spawn, SecBot1 (y=800) and SecBot2 (y=1200) are 400 mm apart —
        // within the 500 mm secondary radar range.
        // SecBot2 (y=1200) sees SecBot1 (y=800) to the NORTH → becomes SBOT.
        // SecBot1 (y=800)  sees nothing to the NORTH            → stays NBOT.
        id = NBOT;
        for (IRadarResult o : detectRadar()) {
            if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) id = SBOT;
        }

        if (NBOT.equals(id)) {
            myX = teamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX;
            myY = teamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY;
        } else {
            myX = teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
            myY = teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
        }

        flankY  = NBOT.equals(id) ? FLANK_Y_NBOT : FLANK_Y_SBOT;
        harassX = teamA ? HARASS_X_A : HARASS_X_B;

        state       = S.FLANKING;
        resumeState = S.FLANKING;

        BotInfo self = allies.get(id);
        self.update(myX, myY, getHeading());
        self.alive = true;
    }

    // =========================================================================
    //  step
    // =========================================================================
    @Override
    public void step() {
        // ── Decrement firing cooldown ─────────────────────────────────────────
        if (fireCooldown > 0) fireCooldown--;

        // ── Perception ───────────────────────────────────────────────────────
        scanRadar();
        readMessages();
        ageEnemies();

        // ── Death check ───────────────────────────────────────────────────────
        if (getHealth() <= 0) {
            state = S.DEAD;
            allies.get(id).alive = false;
            broadcast("DEAD " + id);
            return;
        }

        // ── Broadcast position ────────────────────────────────────────────────
        broadcast("POS " + id + " " + myX + " " + myY + " " + getHeading());

        // ── Opportunistic fire ────────────────────────────────────────────────
        TrackedEnemy tgt = chooseTarget();
        if (tryFire(tgt)) {
            broadcast("FOCUS " + (int) tgt.x + " " + (int) tgt.y);
        }

        // ── State transitions ─────────────────────────────────────────────────
        if (!isAvoiding() && state != S.DEAD && state != S.RETREATING) {
            if (!enemies.isEmpty() && state == S.FLANKING) {
                // Enemy detected mid-flank → switch to active engagement
                state = S.FIRING;
            } else if (enemies.isEmpty() && state == S.FIRING) {
                // Enemies gone → resume flanking from wherever we are
                state = S.FLANKING;
            }
        }

        // Health-gated retreat
        if (getHealth() <= HP_RETREAT && !isAvoiding() && state != S.DEAD) {
            state = S.RETREATING;
        }

        // ── Dispatch ─────────────────────────────────────────────────────────
        switch (state) {
            case FLANKING:
                doFlank();
                break;
            case HOLDING:
                doEngage(); // treat HOLDING as ready-to-engage
                break;
            case FIRING:
                doEngage();
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

    /**
     * Two-phase flanking movement:
     *   Phase 1 (reachedFlankY = false): Turn toward extreme Y and move there.
     *   Phase 2 (reachedFlankY = true):  Turn toward enemy half and advance.
     *
     * On arrival at (harassX, flankY) the bot switches to HOLDING, ready to
     * engage from the crossfire position.
     */
    private void doFlank() {
        if (!reachedFlankY) {
            // ── Phase 1: push to extreme Y ──────────────────────────────────
            // Parameters.NORTH = -π/2 → normalised = 3π/2
            // Parameters.SOUTH =  π/2 → normalised = π/2
            double targetH = NBOT.equals(id)
                ? normA(Parameters.NORTH)   // 3π/2 = "up" in screen coords
                : normA(Parameters.SOUTH);  // π/2  = "down"

            boolean reached = NBOT.equals(id) ? (myY <= flankY) : (myY >= flankY);

            if (reached) {
                reachedFlankY = true;
                return; // will execute phase 2 next step
            }
            if (!isFacing(targetH)) stepTurnTo(targetH);
            else doMove(true);

        } else if (!reachedFlankX) {
            // ── Phase 2: advance toward enemy half ──────────────────────────
            // Team A → head EAST; Team B → head WEST
            double targetH = teamA ? normA(Parameters.EAST) : normA(Parameters.WEST);

            boolean reached = teamA ? (myX >= harassX) : (myX <= harassX);

            if (reached) {
                reachedFlankX = true;
                state = S.HOLDING;
                return;
            }
            if (!isFacing(targetH)) stepTurnTo(targetH);
            else doMove(true);

        } else {
            // Both phases done — should be in HOLDING; fire handled by tryFire
            state = S.HOLDING;
        }
    }

    /**
     * Active engagement at the crossfire position.
     * Kiting:
     *   d < ENG_BACK  → back away (turn to safe cardinal + move forward away)
     *   d > ENG_MAX   → close the distance with goTo
     *   in [ENG_BACK, ENG_MAX] → hold and fire (already done by tryFire above)
     */
    private void doEngage() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = S.FLANKING; // resume flanking if nothing to shoot
            return;
        }
        state = S.FIRING;

        double d = Math.hypot(tgt.x - myX, tgt.y - myY);

        if (d > ENG_MAX) {
            // Close in
            goTo(tgt.x, tgt.y);
        } else if (d < ENG_BACK) {
            // Too close — move away from enemy (snap to cardinal away direction)
            double awayAngle = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(awayAngle)) stepTurnTo(awayAngle);
            else doMove(true);
        }
        // In optimal band: only fire (handled at top of step)
    }

    /** Return to spawn-side flank corner at reduced health. */
    private void doRetreat() {
        double safeX = teamA ? 280.0 : 2720.0;
        // Retreat toward own side while staying at the flank Y position
        goTo(safeX, flankY);
    }
}
