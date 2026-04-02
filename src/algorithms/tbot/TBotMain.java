package algorithms.tbot;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * TBotMain -- Utility AI main bot (3 per team).
 *
 * Roles: ANCHOR (center hold, max fire), FLANKER_N / FLANKER_S (crossfire angles).
 * Every tick: perception -> fire -> score 5 behaviors -> execute best -> post-move fire.
 */
public class TBotMain extends TBotBase {

    private enum Behavior { ADVANCE, COMBAT, FLANK, RETREAT, REGROUP }

    private double holdX, holdY;
    private double kiteMin, kiteMax;
    private int noFireTicks = 0;
    private int lastRoleSwapTick = 0;
    private boolean isDead = false;

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

        // Initial role assignment
        if (M2.equals(id))      role = Role.ANCHOR;
        else if (M1.equals(id)) role = Role.FLANKER_N;
        else                    role = Role.FLANKER_S;

        computeFormation();
        kiteMin = TBotConfig.KITE_MIN_NORMAL;
        kiteMax = TBotConfig.KITE_MAX_NORMAL;

        TAlly self = allies.get(id);
        self.update(myX, myY, getHeading());
        self.role = role;
    }

    @Override
    public void step() {
        tickPerception();

        if (getHealth() <= 0) {
            if (!isDead) {
                isDead = true;
                broadcastDead();
            }
            return;
        }

        broadcastPosition();
        updateKiteRange();
        reassignRolesIfNeeded();
        computeFormation();

        // Pre-move fire attempt
        TEnemy target = chooseTarget();
        boolean fired = tryFire(target);
        if (fired) {
            broadcastFocus(target);
            noFireTicks = 0;
        } else {
            noFireTicks++;
        }

        // Behavior selection via utility scoring
        if (avoidState != AState.NONE) {
            doAvoidStep();
        } else {
            double sAdvance = scoreAdvance(target);
            double sCombat  = scoreCombat(target, fired);
            double sFlank   = scoreFlank(target);
            double sRetreat = scoreRetreat();
            double sRegroup = scoreRegroup();

            // Apply role bonuses
            if (role == Role.ANCHOR) {
                sCombat  += TBotConfig.U_ANCHOR_BONUS;
                sAdvance += TBotConfig.U_ANCHOR_BONUS * 0.5;
            } else if (role == Role.FLANKER_N || role == Role.FLANKER_S) {
                sFlank += TBotConfig.U_FLANKER_BONUS;
            }

            Behavior best = pickBest(sAdvance, sCombat, sFlank, sRetreat, sRegroup);
            executeBehavior(best, target);
        }

        // Post-move fire attempt (skip if pre-move fired — cooldown prevents firing anyway)
        if (!fired) {
            target = chooseTarget();
            fired = tryFire(target);
            if (fired) {
                broadcastFocus(target);
                noFireTicks = 0;
            }
        }
    }

    // ── Utility Scoring ───────────────────────────────────────────────────

    private double scoreAdvance(TEnemy target) {
        double score = TBotConfig.U_ADVANCE_BASE;
        double distToHold = Math.hypot(myX - holdX, myY - holdY);
        if (distToHold < 50) return 0; // Already there
        if (target == null || enemies.isEmpty()) score += TBotConfig.U_ADVANCE_NO_ENEMY;
        return score;
    }

    private double scoreCombat(TEnemy target, boolean canFire) {
        if (target == null) return 0;
        double score = TBotConfig.U_COMBAT_HAS_TARGET;
        if (canFire) score += TBotConfig.U_COMBAT_SAFEFIRE;
        double dist = Math.hypot(target.x - myX, target.y - myY);
        if (dist < 500) score += 0.2;
        return score;
    }

    private double scoreFlank(TEnemy target) {
        if (target == null) return 0;
        double score = 0;
        if (noFireTicks > TBotConfig.U_FLANK_BLOCKED_THRESH) {
            score = TBotConfig.U_FLANK_BLOCKED_TICKS *
                    Math.min(1.0, (double)(noFireTicks - TBotConfig.U_FLANK_BLOCKED_THRESH) / 30.0);
        }
        return score;
    }

    private double scoreRetreat() {
        double hp = getHealth();
        double score = 0;
        if (hp < TBotConfig.U_RETREAT_HP_THRESH) {
            score = TBotConfig.U_RETREAT_HP_FACTOR * (1.0 - hp / TBotConfig.U_RETREAT_HP_THRESH);
        }
        if (aliveAllies <= 2) score += TBotConfig.U_RETREAT_ALONE;
        return score;
    }

    private double scoreRegroup() {
        if (teamSpread > TBotConfig.U_REGROUP_DIST_THRESH) {
            double score = TBotConfig.U_REGROUP_SPREAD * (teamSpread / TBotConfig.U_REGROUP_DIST_THRESH);
            if (teamTotalHP < TBotConfig.COORD_REGROUP_HP_THRESH) score += 0.3;
            return score;
        }
        return 0;
    }

    private Behavior pickBest(double sAdv, double sCom, double sFla, double sRet, double sReg) {
        double max = sAdv;
        Behavior b = Behavior.ADVANCE;
        if (sCom > max) { max = sCom; b = Behavior.COMBAT; }
        if (sFla > max) { max = sFla; b = Behavior.FLANK; }
        if (sRet > max) { max = sRet; b = Behavior.RETREAT; }
        if (sReg > max) { b = Behavior.REGROUP; }
        return b;
    }

    // ── Behavior Execution ────────────────────────────────────────────────

    private void executeBehavior(Behavior b, TEnemy target) {
        switch (b) {
            case ADVANCE:
                goTo(holdX, holdY);
                break;
            case COMBAT: {
                double side = getTangentialSide();
                doPotentialFieldMove(kiteMin, kiteMax, side);
                break;
            }
            case FLANK:
                executeFlank(target);
                break;
            case RETREAT:
                executeRetreat();
                break;
            case REGROUP:
                goTo(teamAvgX, teamAvgY);
                break;
        }
    }

    private void executeFlank(TEnemy target) {
        if (target == null) { goTo(holdX, holdY); return; }
        // Move to a position perpendicular to the target, offset by flanking direction
        double side = (role == Role.FLANKER_N) ? -1.0 : 1.0;
        double toTargetX = target.x - myX, toTargetY = target.y - myY;
        double dist = Math.hypot(toTargetX, toTargetY);
        if (dist < 1.0) return;
        double nx = toTargetX / dist, ny = toTargetY / dist;
        // Perpendicular offset
        double perpX = -ny * side, perpY = nx * side;
        double flankX = myX + perpX * TBotConfig.FORM_FLANKER_Y_ADJ + nx * 50;
        double flankY = myY + perpY * TBotConfig.FORM_FLANKER_Y_ADJ + ny * 50;
        flankX = Math.max(150, Math.min(MAP_W - 150, flankX));
        flankY = Math.max(150, Math.min(MAP_H - 150, flankY));
        goTo(flankX, flankY);
    }

    private void executeRetreat() {
        double retreatX = teamA ? TBotConfig.FORM_RETREAT_X : (MAP_W - TBotConfig.FORM_RETREAT_X);
        goTo(retreatX, TBotConfig.MAP_CY);
    }

    // ── Kiting & Formation ────────────────────────────────────────────────

    private void updateKiteRange() {
        if (aliveAllies >= TBotConfig.U_AGGRO_ALIVE_THRESH && getHealth() > TBotConfig.U_AGGRO_HP_THRESH) {
            kiteMin = TBotConfig.KITE_MIN_AGGRO;
            kiteMax = TBotConfig.KITE_MAX_AGGRO;
        } else if (aliveAllies <= 2 || getHealth() < TBotConfig.U_RETREAT_HP_THRESH) {
            kiteMin = TBotConfig.KITE_MIN_DEFEN;
            kiteMax = TBotConfig.KITE_MAX_DEFEN;
        } else {
            kiteMin = TBotConfig.KITE_MIN_NORMAL;
            kiteMax = TBotConfig.KITE_MAX_NORMAL;
        }
    }

    private void computeFormation() {
        int deadMain = 3 - aliveMainCount;
        int deadSec  = 2 - aliveSecCount;
        double ySpread = TBotConfig.FORM_Y_SPREAD * (1.0 - TBotConfig.FORM_ADAPT_DEAD_MAIN * deadMain);
        ySpread = Math.max(100, ySpread);

        double cx = TBotConfig.MAP_CX;
        double offset = TBotConfig.FORM_HOLD_X_OFFSET / 2.0;

        if (role == Role.ANCHOR) {
            holdX = teamA ? (cx - offset + TBotConfig.FORM_ANCHOR_X_ADJ) : (cx + offset - TBotConfig.FORM_ANCHOR_X_ADJ);
            holdY = TBotConfig.MAP_CY;
        } else if (role == Role.FLANKER_N) {
            holdX = teamA ? (cx - offset) : (cx + offset);
            holdY = TBotConfig.MAP_CY - ySpread - TBotConfig.FORM_FLANKER_Y_ADJ;
        } else {
            holdX = teamA ? (cx - offset) : (cx + offset);
            holdY = TBotConfig.MAP_CY + ySpread + TBotConfig.FORM_FLANKER_Y_ADJ;
        }

        holdX = Math.max(200, Math.min(MAP_W - 200, holdX));
        holdY = Math.max(200, Math.min(MAP_H - 200, holdY));
    }

    private double getTangentialSide() {
        if (role == Role.FLANKER_N) return 1.0;
        if (role == Role.FLANKER_S) return -1.0;
        return teamA ? 1.0 : -1.0;
    }

    // ── Role Management ───────────────────────────────────────────────────

    private void reassignRolesIfNeeded() {
        if (tick - lastRoleSwapTick < TBotConfig.COORD_ROLE_SWAP_CD) return;

        // Check if anchor is dead
        TAlly anchorAlly = null;
        for (TAlly a : allies.values()) {
            if (a.id.equals(id)) continue;
            if (a.role == Role.ANCHOR && !a.alive) {
                anchorAlly = a;
                break;
            }
        }

        if (anchorAlly != null && role != Role.ANCHOR) {
            // I'm a flanker, anchor is dead -> become anchor if I have more HP than other flanker
            boolean shouldBeAnchor = true;
            for (TAlly a : allies.values()) {
                if (a.id.equals(id) || !a.alive) continue;
                if ((a.role == Role.FLANKER_N || a.role == Role.FLANKER_S) && a.health > getHealth()) {
                    shouldBeAnchor = false;
                    break;
                }
            }
            if (shouldBeAnchor) {
                role = Role.ANCHOR;
                lastRoleSwapTick = tick;
                broadcastRole();
            }
        }
    }
}
