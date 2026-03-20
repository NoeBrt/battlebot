package algorithms.tbot;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * TBotSecondary -- Utility AI secondary bot (2 per team).
 *
 * Roles: SCOUT (intel gathering, wide radar), ASSASSIN (finishing wounded enemies).
 * Speed=3 + radar=500 makes these bots excellent scouts and pursuit units.
 */
public class TBotSecondary extends TBotBase {

    private enum Behavior { SCOUT, ASSASSIN, ESCORT, EVADE, RETREAT }

    private boolean isDead = false;
    private double scoutTargetX, scoutTargetY;
    private int patrolPhase = 0;

    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);

        // NBOT = top secondary, SBOT = bottom secondary
        id = NBOT;
        for (IRadarResult o : detectRadar()) {
            if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) id = SBOT;
        }

        if (NBOT.equals(id)) {
            myX = teamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX;
            myY = teamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY;
            role = Role.SCOUT;
        } else {
            myX = teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
            myY = teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
            role = Role.ASSASSIN;
        }

        TAlly self = allies.get(id);
        self.update(myX, myY, getHeading());
        self.role = role;

        computeScoutTarget();
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

        // Always fire when possible (secondaries are opportunistic)
        TEnemy target = chooseTarget();
        boolean fired = tryFire(target);
        if (fired) broadcastFocus(target);

        // Behavior selection
        if (avoidState != AState.NONE) {
            doAvoidStep();
        } else {
            double sScout    = scoreScout();
            double sAssassin = scoreAssassin(target);
            double sEscort   = scoreEscort();
            double sEvade    = scoreEvade();
            double sRetreat  = scoreRetreat();

            Behavior best = pickBest(sScout, sAssassin, sEscort, sEvade, sRetreat);
            executeBehavior(best, target);
        }

        // Post-move fire
        target = chooseTarget();
        fired = tryFire(target);
        if (fired) broadcastFocus(target);
    }

    // ── Utility Scoring ───────────────────────────────────────────────────

    private double scoreScout() {
        if (role != Role.SCOUT) return TBotConfig.US_SCOUT_BASE * 0.3;
        double score = TBotConfig.US_SCOUT_BASE;
        // Higher when no enemies known (need intel)
        if (enemies.isEmpty()) score += 0.3;
        return score;
    }

    private double scoreAssassin(TEnemy target) {
        if (target == null) return 0;
        double score = 0;
        // Check for low-HP enemies
        for (TEnemy e : enemies) {
            double hp = e.estimatedHP();
            if (hp < TBotConfig.US_ASSASSIN_HP_THRESH && hp > 0) {
                score = TBotConfig.US_ASSASSIN_BASE;
                double dist = Math.hypot(e.x - myX, e.y - myY);
                if (dist < 600) score += 0.3; // Close enough to chase
                break;
            }
        }
        if (role == Role.ASSASSIN) score += 0.1;
        return score;
    }

    private double scoreEscort() {
        double score = TBotConfig.US_ESCORT_BASE;
        // Higher when main bots are nearby and fighting
        if (aliveMainCount == 0) return 0;
        // Lower when already close to mains
        double distToMains = distToMainCentroid();
        if (distToMains < TBotConfig.FORM_SEC_ESCORT_OFFSET) score *= 0.3;
        return score;
    }

    private double scoreEvade() {
        // Check for nearby enemies
        double closestDist = Double.MAX_VALUE;
        for (TEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            if (d < closestDist) closestDist = d;
        }
        if (closestDist < TBotConfig.US_EVADE_RANGE) {
            return TBotConfig.PF_SEC_EVADE * (1.0 - closestDist / TBotConfig.US_EVADE_RANGE);
        }
        return 0;
    }

    private double scoreRetreat() {
        double hpPct = getHealth() / 100.0; // Secondary max HP = 100
        if (hpPct < TBotConfig.US_RETREAT_HP_PCT) {
            return 0.8 * (1.0 - hpPct / TBotConfig.US_RETREAT_HP_PCT);
        }
        return 0;
    }

    private Behavior pickBest(double sSc, double sAs, double sEs, double sEv, double sRe) {
        double max = sSc;
        Behavior b = Behavior.SCOUT;
        if (sAs > max) { max = sAs; b = Behavior.ASSASSIN; }
        if (sEs > max) { max = sEs; b = Behavior.ESCORT; }
        if (sEv > max) { max = sEv; b = Behavior.EVADE; }
        if (sRe > max) { b = Behavior.RETREAT; }
        return b;
    }

    // ── Behavior Execution ────────────────────────────────────────────────

    private void executeBehavior(Behavior b, TEnemy target) {
        switch (b) {
            case SCOUT:
                executeScout();
                break;
            case ASSASSIN:
                executeAssassin();
                break;
            case ESCORT:
                executeEscort();
                break;
            case EVADE:
                executeEvade();
                break;
            case RETREAT:
                executeRetreat();
                break;
        }
    }

    private void executeScout() {
        double dist = Math.hypot(myX - scoutTargetX, myY - scoutTargetY);
        if (dist < 80) {
            patrolPhase = (patrolPhase + 1) % 4;
            computeScoutTarget();
        }
        goTo(scoutTargetX, scoutTargetY);
    }

    private void executeAssassin() {
        // Find lowest HP enemy and chase it
        TEnemy weakest = null;
        double weakestHP = Double.MAX_VALUE;
        for (TEnemy e : enemies) {
            double hp = e.estimatedHP();
            if (hp < weakestHP && hp > 0) {
                weakestHP = hp;
                weakest = e;
            }
        }
        if (weakest != null) {
            double dist = Math.hypot(weakest.x - myX, weakest.y - myY);
            if (dist < TBotConfig.FIRE_SEC_ENGAGE_RANGE) {
                // Orbit at engage range using PF
                double side = NBOT.equals(id) ? 1.0 : -1.0;
                doPotentialFieldMove(TBotConfig.FIRE_SEC_ENGAGE_RANGE * 0.6,
                                     TBotConfig.FIRE_SEC_ENGAGE_RANGE, side);
            } else {
                goTo(weakest.x, weakest.y);
            }
        } else {
            executeEscort();
        }
    }

    private void executeEscort() {
        double[] mainCentroid = getMainCentroid();
        // Stay behind the main formation
        double offsetX = teamA ? -TBotConfig.FORM_SEC_ESCORT_OFFSET : TBotConfig.FORM_SEC_ESCORT_OFFSET;
        double escortY = mainCentroid[1] + (NBOT.equals(id) ? -TBotConfig.FORM_SEC_FLANK_Y_OFFSET : TBotConfig.FORM_SEC_FLANK_Y_OFFSET);
        double escortX = mainCentroid[0] + offsetX;
        escortX = Math.max(150, Math.min(MAP_W - 150, escortX));
        escortY = Math.max(150, Math.min(MAP_H - 150, escortY));
        goTo(escortX, escortY);
    }

    private void executeEvade() {
        // Anti-gravity: repel from all nearby enemies with boosted strength
        double side = NBOT.equals(id) ? 1.0 : -1.0;
        double evadeKiteMin = TBotConfig.US_EVADE_RANGE;
        double evadeKiteMax = TBotConfig.US_EVADE_RANGE * 2;
        doPotentialFieldMove(evadeKiteMin, evadeKiteMax, side);
    }

    private void executeRetreat() {
        double retreatX = teamA ? 200.0 : (MAP_W - 200.0);
        double retreatY = NBOT.equals(id) ? 400.0 : (MAP_H - 400.0);
        goTo(retreatX, retreatY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void computeScoutTarget() {
        double baseY = NBOT.equals(id) ? 300.0 : (MAP_H - 300.0);
        double deepX = teamA ? TBotConfig.US_DEEP_SCOUT_X : (MAP_W - TBotConfig.US_DEEP_SCOUT_X);
        double homeX = teamA ? 500.0 : (MAP_W - 500.0);
        double midY = TBotConfig.MAP_CY;

        switch (patrolPhase) {
            case 0: scoutTargetX = deepX;  scoutTargetY = baseY;  break;
            case 1: scoutTargetX = deepX;  scoutTargetY = midY;   break;
            case 2: scoutTargetX = homeX;  scoutTargetY = midY;   break;
            case 3: scoutTargetX = homeX;  scoutTargetY = baseY;  break;
        }
        scoutTargetX = Math.max(200, Math.min(MAP_W - 200, scoutTargetX));
        scoutTargetY = Math.max(200, Math.min(MAP_H - 200, scoutTargetY));
    }

    private double[] getMainCentroid() {
        double sx = 0, sy = 0;
        int count = 0;
        for (TAlly a : allies.values()) {
            if (!a.alive) continue;
            if (a.id.equals(M1) || a.id.equals(M2) || a.id.equals(M3)) {
                sx += a.x; sy += a.y; count++;
            }
        }
        if (count == 0) return new double[]{myX, myY};
        return new double[]{sx / count, sy / count};
    }

    private double distToMainCentroid() {
        double[] c = getMainCentroid();
        return Math.hypot(myX - c[0], myY - c[1]);
    }
}
