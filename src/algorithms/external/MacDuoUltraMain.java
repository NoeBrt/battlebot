/* =============================================================================
 * MacDuoUltraMain.java — State-of-the-art main bot (3 per team).
 *
 * Superiority over all existing strategies:
 *  1. Anti-Gravity / Potential Field combat movement — unpredictable, smooth,
 *     maintains optimal kiting range while orbiting tangentially.
 *  2. NR-7 lead targeting with acceleration modelling (vs NR-5 in all others).
 *  3. Distance-adaptive 7-angle fire spread (vs fixed 5-angle in V3).
 *  4. Damage-tracking target selection — focus nearly-dead enemies for fastest
 *     kills under Lanchester Square Law.
 *  5. Dual fire attempts per step (pre-move + post-move) for max DPS.
 *  6. Dynamic kiting range: tighter when team is strong, wider when weak.
 *  7. Wreck-aware safe-fire (avoids shooting through debris).
 *  8. Coordinated focus-fire with damage broadcasts across all 5 bots.
 *  9. Anti-clump force field prevents allies from clustering (easy AoE target).
 * 10. Clear-shot repositioning when firing line blocked for too long.
 * =============================================================================*/
package algorithms.external;

import characteristics.IRadarResult;
import characteristics.Parameters;

public class MacDuoUltraMain extends MacDuoUltraBase {

    // Formation holding points
    private static final double HOLD_X_A = 950.0, HOLD_X_B = 2050.0;
    private static final double[] HOLD_Y  = {740.0, 1000.0, 1260.0};

    // Dynamic kiting parameters (adjusted by team health)
    private double kiteMin, kiteMax;
    private static final double KITE_MIN_AGGRO  = 300.0, KITE_MAX_AGGRO  = 700.0;
    private static final double KITE_MIN_NORMAL = 360.0, KITE_MAX_NORMAL = 780.0;
    private static final double KITE_MIN_DEFEN  = 420.0, KITE_MAX_DEFEN  = 900.0;

    // Retreat threshold
    private static final double HP_RETREAT = 60.0;

    private double holdX, holdY;
    private int noFireTicks = 0;

    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);

        boolean hasN = false, hasS = false;
        for (IRadarResult o : detectRadar()) {
            if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) hasN = true;
            if (aDist(o.getObjectDirection(), Parameters.SOUTH) < ANGLE_PREC) hasS = true;
        }
        if (hasN && hasS)       id = M2;
        else if (!hasN && hasS) id = M1;
        else                    id = M3;

        if      (M1.equals(id)) { myX = teamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX;
                                  myY = teamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY; }
        else if (M2.equals(id)) { myX = teamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX;
                                  myY = teamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY; }
        else                    { myX = teamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX;
                                  myY = teamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY; }

        holdX = teamA ? HOLD_X_A : HOLD_X_B;
        holdY = HOLD_Y[M1.equals(id) ? 0 : M2.equals(id) ? 1 : 2];
        state = S.ADVANCING;
        resumeState = S.ADVANCING;
        kiteMin = KITE_MIN_NORMAL;
        kiteMax = KITE_MAX_NORMAL;

        UAlly self = allies.get(id);
        self.update(myX, myY, getHeading());
        self.alive = true;
    }

    @Override
    public void step() {
        // ── Cooldown & perception ────────────────────────────────────────
        if (fireCooldown > 0) fireCooldown--;
        scanRadar();
        readMessages();
        ageEnemies();

        // ── Death check ──────────────────────────────────────────────────
        if (getHealth() <= 0) {
            state = S.DEAD;
            allies.get(id).alive = false;
            broadcast("DEAD " + id);
            return;
        }

        // ── Broadcast position ───────────────────────────────────────────
        broadcast("POS " + id + " " + myX + " " + myY + " " + getHeading());

        // ── Dynamic kiting range based on team strength ──────────────────
        updateKiteRange();

        // ── FIRST FIRE ATTEMPT (before movement) ─────────────────────────
        UEnemy t = chooseTarget();
        boolean fired = tryFire(t);
        if (t != null) {
            if (fired) {
                noFireTicks = 0;
                broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
            } else {
                noFireTicks++;
                if (noFireTicks % 6 == 0)
                    broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
            }
        } else {
            noFireTicks = 0;
        }

        // ── State transitions ────────────────────────────────────────────
        if (!isAvoiding() && state != S.DEAD) {
            if (getHealth() < HP_RETREAT && enemies.isEmpty()) {
                state = S.RETREATING;
            } else if (!enemies.isEmpty()) {
                state = (noFireTicks > 20) ? S.FLANKING : S.FIRING;
            } else {
                state = (Math.hypot(myX - holdX, myY - holdY) < 60) ? S.HOLDING : S.ADVANCING;
            }
        }

        // ── Execute state ────────────────────────────────────────────────
        switch (state) {
            case ADVANCING:
                goTo(holdX, holdY);
                break;
            case FIRING:
                potentialFieldCombatMove();
                break;
            case FLANKING:
                clearShotReposition();
                break;
            case RETREATING:
                retreat();
                break;
            case HOLDING:
                break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK:
                doAvoidStep();
                break;
            default:
                break;
        }

        // ── SECOND FIRE ATTEMPT (after movement, new position) ───────────
        t = chooseTarget();
        fired = tryFire(t);
        if (t != null && fired) {
            noFireTicks = 0;
            broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
        }
    }

    // =====================================================================
    //  DYNAMIC KITING RANGE
    // =====================================================================

    private void updateKiteRange() {
        int aliveAllies = countAliveAllies();
        double myHP = getHealth();

        if (aliveAllies >= 3 && myHP > 200) {
            // Team is strong — aggressive kiting (close range, high DPS)
            kiteMin = KITE_MIN_AGGRO;
            kiteMax = KITE_MAX_AGGRO;
        } else if (aliveAllies <= 1 || myHP < 100) {
            // Team is weak — defensive kiting (stay far, survive)
            kiteMin = KITE_MIN_DEFEN;
            kiteMax = KITE_MAX_DEFEN;
        } else {
            kiteMin = KITE_MIN_NORMAL;
            kiteMax = KITE_MAX_NORMAL;
        }
    }

    // =====================================================================
    //  POTENTIAL FIELD COMBAT MOVEMENT
    // =====================================================================

    /**
     * Uses the anti-gravity potential field to compute optimal combat heading.
     * Creates smooth, unpredictable orbital movement at optimal firing range.
     */
    private void potentialFieldCombatMove() {
        double angle = potentialFieldAngle(kiteMin, kiteMax);
        if (!isFacing(angle)) stepTurnTo(angle);
        else doMove(true);
    }

    // =====================================================================
    //  CLEAR-SHOT REPOSITIONING
    // =====================================================================

    /**
     * When firing line has been blocked for 20+ ticks, reposition to create
     * a new angle. M1 flanks low, M3 flanks high, M2 uses diagonal.
     */
    private void clearShotReposition() {
        UEnemy t = chooseTarget();
        if (t == null) { state = S.ADVANCING; return; }
        double base = Math.atan2(t.y - myY, t.x - myX);
        double side = M1.equals(id) ? -1.0 : (M3.equals(id) ? 1.0 : (teamA ? 1.0 : -1.0));
        double angle = base + side * Math.PI / 3.0;
        if (!isFacing(angle)) stepTurnTo(angle);
        else doMove(true);
    }

    // =====================================================================
    //  RETREAT
    // =====================================================================

    private void retreat() {
        double safeX = teamA ? 250.0 : MAP_W - 250.0;
        double safeY = holdY;
        if (Math.hypot(myX - safeX, myY - safeY) < 80) {
            state = S.HOLDING;
        } else {
            goTo(safeX, safeY);
        }
    }
}
