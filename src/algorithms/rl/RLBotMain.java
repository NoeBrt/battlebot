/* ============================================================================
 * RLBotMain.java — RL-optimized main bot (3 per team).
 * All strategy constants read from RLConfig.
 * ============================================================================*/
package algorithms.rl;

import characteristics.IRadarResult;
import characteristics.Parameters;
import algorithms.external.BotState;

public class RLBotMain extends RLBotBase {

    private double holdX, holdY;
    private double kiteMin, kiteMax;
    private int noFireTicks = 0;
    
    private enum S { ADVANCING, FIRING, FLANKING, RETREATING, DEAD }
    private S mainState;

    @Override
    public void activate() {
        super.activate();

        boolean hasN = false, hasS = false;
        for (IRadarResult o : detectRadar()) {
            if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) hasN = true;
            else if (isSameDirection(o.getObjectDirection(), Parameters.SOUTH)) hasS = true;
        }
        if (hasN && hasS)       whoAmI = MAIN2;
        else if (!hasN && hasS) whoAmI = MAIN1;
        else                    whoAmI = MAIN3; // Top one

        if      (MAIN1.equals(whoAmI)) { myPos.setX(teamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX);
                                         myPos.setY(teamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY); }
        else if (MAIN2.equals(whoAmI)) { myPos.setX(teamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX);
                                         myPos.setY(teamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY); }
        else                           { myPos.setX(teamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX);
                                         myPos.setY(teamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY); }

        // hold X offset -> 1500.0 ± RLConfig.HOLD_X_OFFSET / 2.0
        double offset = RLConfig.HOLD_X_OFFSET / 2.0;
        holdX = teamA ? (RLConfig.MAP_CX - offset) : (RLConfig.MAP_CX + offset);
        
        double cy = RLConfig.FORMATION_Y_BASE;
        double dy = RLConfig.FORMATION_Y_OFFSET;
        double[] holdYs = {cy - dy, cy, cy + dy};
        holdY = holdYs[MAIN1.equals(whoAmI) ? 0 : MAIN2.equals(whoAmI) ? 1 : 2];

        mainState = S.ADVANCING;
        kiteMin = RLConfig.KITE_MIN_NORMAL;
        kiteMax = RLConfig.KITE_MAX_NORMAL;
    }

    @Override
    public void step() {
        super.step();

        if (getHealth() <= 0) {
            mainState = S.DEAD;
            return;
        }

        int aliveAllies = 0;
        for (BotState b : allyPos.values()) if (b.isAlive()) aliveAllies++;
        
        if (aliveAllies >= 3 && getHealth() > RLConfig.HEALTH_HIGH_THRESHOLD) {
            kiteMin = RLConfig.KITE_MIN_AGGRO;
            kiteMax = RLConfig.KITE_MAX_AGGRO;
        } else if (aliveAllies <= 2 || getHealth() < RLConfig.HEALTH_LOW_THRESHOLD) {
            kiteMin = RLConfig.KITE_MIN_DEFEN;
            kiteMax = RLConfig.KITE_MAX_DEFEN;
        } else {
            kiteMin = RLConfig.KITE_MIN_NORMAL;
            kiteMax = RLConfig.KITE_MAX_NORMAL;
        }

        boolean hasEnemies = !rlEnemies.isEmpty();
        
        if (mainState != S.DEAD) {
             if (getHealth() < RLConfig.HP_RETREAT_MAIN && !hasEnemies) {
                 mainState = S.RETREATING;
             } else if (hasEnemies) {
                 mainState = (noFireTicks > RLConfig.NOFIRE_REPOSITION_TICKS) ? S.FLANKING : S.FIRING;
             } else {
                 mainState = S.ADVANCING;
             }
        }

        Double fireAngle = null;
        RLEnemy target = chooseBestTarget();
        if (target != null) {
            double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
            if (dist <= Parameters.bulletRange) {
                double t = dist / Parameters.bulletVelocity;
                double predX = target.x + target.speedX * t;
                double predY = target.y + target.speedY * t;
                double tempAngle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());

                if (isFiringLineSafe(predX, predY)) {
                    fireAngle = tempAngle;
                }
            }
        }

        // 1 action per tick! Prioritize firing if possible
        if (fireAngle != null && noFireTicks > 2) {
            fire(fireAngle);
            noFireTicks = 0;
            return;
        } else {
            noFireTicks++;
        }

        // Otherwise move
        switch (mainState) {
            case ADVANCING:
                goTo(holdX, holdY);
                break;
            case FIRING:
                potentialFieldMove(kiteMin, kiteMax);
                break;
            case FLANKING:
                double flankY = myPos.getY() + (MAIN1.equals(whoAmI) ? -RLConfig.FLANK_OFFSET : RLConfig.FLANK_OFFSET);
                if (flankY < RLConfig.WALL_MARGIN) flankY = RLConfig.WALL_MARGIN;
                if (flankY > RLConfig.MAP_HEIGHT - RLConfig.WALL_MARGIN) flankY = RLConfig.MAP_HEIGHT - RLConfig.WALL_MARGIN;
                goTo(myPos.getX(), flankY);
                break;
            case RETREATING:
                goTo(teamA ? 300 : 2700, holdY);
                break;
            case DEAD: break;
        }
    }
}
