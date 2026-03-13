/* =============================================================================
 * MacDuoUltraSecondary.java — State-of-the-art secondary/scout bot (2 per team).
 *
 * Strategy: Three-phase aggressive scouting with continuous intel relay.
 *  Phase 1 (INIT):      Rush to extreme Y position for flanking angle.
 *  Phase 2 (FLANKING):  Advance into enemy territory, broadcasting all contacts.
 *  Phase 3 (ADVANCING): Patrol and maintain vision; retreat when low HP.
 *
 * Key advantages over existing secondaries:
 *  • Continuous broadcasting every step (not gated by freeze/shooter-around).
 *  • Dynamic patrol: adjusts Y based on enemy positions, not fixed.
 *  • Emergency retreat to safe zone when HP < 35% (instead of dying uselessly).
 *  • Anti-gravity movement during patrol for unpredictable evasion.
 *  • Shares TRACK velocity data for main bot lead-targeting.
 *  • Shares DMG data so mains can prioritize nearly-dead enemies.
 * =============================================================================*/
package algorithms.external;

import characteristics.IRadarResult;
import characteristics.Parameters;

public class MacDuoUltraSecondary extends MacDuoUltraBase {

    private static final double HP_RETREAT_PCT = 0.35;

    // Flank targets (extreme Y to create crossfire angles)
    private double flankY;
    private double advanceX;
    private boolean flankReached = false;

    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);

        // Determine identity: NBOT = top (lower Y), SBOT = bottom (higher Y)
        id = NBOT;
        for (IRadarResult o : detectRadar()) {
            if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC)
                id = SBOT;
        }

        if (NBOT.equals(id)) {
            myX = teamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX;
            myY = teamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY;
            flankY = 300.0;  // Rush to near-top edge
        } else {
            myX = teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
            myY = teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
            flankY = 1700.0; // Rush to near-bottom edge
        }

        advanceX = teamA ? 1400.0 : 1600.0; // Target X in enemy territory

        state = S.INIT;
        resumeState = S.INIT;

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

        // ── Always broadcast position ────────────────────────────────────
        broadcast("POS " + id + " " + myX + " " + myY + " " + getHeading());

        // ── Health-gated retreat ─────────────────────────────────────────
        double maxHP = Parameters.teamASecondaryBotHealth;
        if (getHealth() < maxHP * HP_RETREAT_PCT && state != S.RETREATING && state != S.DEAD) {
            if (!isAvoiding()) state = S.RETREATING;
        }

        // ── Execute state machine ────────────────────────────────────────
        switch (state) {
            case INIT:
                executeFlankRush();
                break;
            case FLANKING:
                executeAdvance();
                break;
            case ADVANCING:
                executePatrol();
                break;
            case RETREATING:
                executeRetreat();
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

    // =====================================================================
    //  PHASE 1: FLANK RUSH — reach extreme Y position quickly
    // =====================================================================

    private void executeFlankRush() {
        double dy = flankY - myY;
        if (Math.abs(dy) < 30.0) {
            // Reached flank position → advance toward enemy
            flankReached = true;
            state = S.FLANKING;
            return;
        }

        // Move toward flank Y: first go N/S to flank Y
        double targetAngle;
        if (NBOT.equals(id)) {
            targetAngle = 3.0 * Math.PI / 2.0; // NORTH (up, -Y in screen)
        } else {
            targetAngle = Math.PI / 2.0; // SOUTH (down, +Y in screen)
        }

        if (!isFacing(targetAngle)) stepTurnTo(targetAngle);
        else doMove(true);
    }

    // =====================================================================
    //  PHASE 2: ADVANCE — push into enemy territory
    // =====================================================================

    private void executeAdvance() {
        // Check if any main ally is alive and relatively close
        boolean mainAlive = false;
        for (String mid : new String[]{M1, M2, M3}) {
            UAlly a = allies.get(mid);
            if (a != null && a.alive) { mainAlive = true; break; }
        }

        if (!mainAlive) {
            // All mains dead — retreat and survive
            state = S.RETREATING;
            return;
        }

        double dx = advanceX - myX;
        if (Math.abs(dx) < 50.0) {
            // Reached advance target → switch to patrol
            state = S.ADVANCING;
            return;
        }

        // Move toward enemy territory (EAST if teamA, WEST if teamB)
        double targetAngle = teamA ? 0.0 : Math.PI;
        if (!isFacing(targetAngle)) stepTurnTo(targetAngle);
        else doMove(true);
    }

    // =====================================================================
    //  PHASE 3: PATROL — dynamic patrol with anti-gravity evasion
    // =====================================================================

    private void executePatrol() {
        // If enemies detected nearby, use anti-gravity evasion movement
        if (!enemies.isEmpty()) {
            UEnemy closest = null;
            double minDist = Double.MAX_VALUE;
            for (UEnemy e : enemies) {
                double d = Math.hypot(e.x - myX, e.y - myY);
                if (d < minDist) { minDist = d; closest = e; }
            }

            if (closest != null && minDist < 400.0) {
                // Too close to enemy — evade using potential field
                double angle = potentialFieldAngle(250.0, 500.0);
                if (!isFacing(angle)) stepTurnTo(angle);
                else doMove(true);
                return;
            }
        }

        // Otherwise, patrol forward slowly
        double targetAngle = teamA ? 0.0 : Math.PI;

        // Bounds check: if too deep in enemy territory or too close to wall, turn back
        if ((teamA && myX > 2400) || (!teamA && myX < 600)) {
            // Reverse direction
            targetAngle = teamA ? Math.PI : 0.0;
        }

        // Vertical bounds
        if (myY < 200) targetAngle = Math.PI / 2.0;
        if (myY > MAP_H - 200) targetAngle = 3.0 * Math.PI / 2.0;

        if (!isFacing(targetAngle)) stepTurnTo(targetAngle);
        else doMove(true);
    }

    // =====================================================================
    //  RETREAT — fall back to safe zone when low HP
    // =====================================================================

    private void executeRetreat() {
        double safeX = teamA ? 350.0 : MAP_W - 350.0;
        double safeY = NBOT.equals(id) ? 300.0 : MAP_H - 300.0;

        if (Math.hypot(myX - safeX, myY - safeY) < 100) {
            // Reached safe zone — hold position
            return;
        }

        goTo(safeX, safeY);
    }
}
