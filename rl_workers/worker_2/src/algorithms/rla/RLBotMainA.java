/* ============================================================================
 * RLBotMainA.java — RL-optimized main bot (3 per team).
 * All strategy constants read from RLConfigA.
 * ============================================================================*/
package algorithms.rla;

import characteristics.IRadarResult;
import characteristics.Parameters;
import algorithms.external.BotState;

public class RLBotMainA extends RLBotBaseA {

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

        // hold X offset -> 1500.0 ± RLConfigA.HOLD_X_OFFSET / 2.0
        double offset = RLConfigA.HOLD_X_OFFSET / 2.0;
        holdX = teamA ? (RLConfigA.MAP_CX - offset) : (RLConfigA.MAP_CX + offset);
        
        double cy = RLConfigA.FORMATION_Y_BASE;
        double dy = RLConfigA.FORMATION_Y_OFFSET;
        double[] holdYs = {cy - dy, cy, cy + dy};
        holdY = holdYs[MAIN1.equals(whoAmI) ? 0 : MAIN2.equals(whoAmI) ? 1 : 2];

        mainState = S.ADVANCING;
        kiteMin = RLConfigA.KITE_MIN_NORMAL;
        kiteMax = RLConfigA.KITE_MAX_NORMAL;
    }

    @Override
    public void step() {
        super.step(); // Perform detection and update rlEnemies

        if (getHealth() <= 0) {
            mainState = S.DEAD;
            return;
        }

        // Update active kiting range based on team status
        int aliveAllies = 0;
        for (BotState b : allyPos.values()) if (b.isAlive()) aliveAllies++;
        
        if (aliveAllies >= 3 && getHealth() > RLConfigA.HEALTH_HIGH_THRESHOLD) {
            kiteMin = RLConfigA.KITE_MIN_AGGRO;
            kiteMax = RLConfigA.KITE_MAX_AGGRO;
        } else if (aliveAllies <= 2 || getHealth() < RLConfigA.HEALTH_LOW_THRESHOLD) {
            kiteMin = RLConfigA.KITE_MIN_DEFEN;
            kiteMax = RLConfigA.KITE_MAX_DEFEN;
        } else {
            kiteMin = RLConfigA.KITE_MIN_NORMAL;
            kiteMax = RLConfigA.KITE_MAX_NORMAL;
        }

        boolean hasEnemies = !rlEnemies.isEmpty();
        
        // State transitions
        if (mainState != S.DEAD) {
             if (getHealth() < RLConfigA.HP_RETREAT_MAIN && !hasEnemies) {
                 mainState = S.RETREATING;
             } else if (hasEnemies) {
                 mainState = (noFireTicks > RLConfigA.NOFIRE_REPOSITION_TICKS) ? S.FLANKING : S.FIRING;
             } else {
                 mainState = S.ADVANCING;
             }
        }

        // Act based on State
        switch (mainState) {
            case ADVANCING:
                goTo(holdX, holdY);
                break;
            case FIRING:
                potentialFieldMove(kiteMin, kiteMax);
                break;
            case FLANKING:
                double flankY = myPos.getY() + (MAIN1.equals(whoAmI) ? -RLConfigA.FLANK_OFFSET : RLConfigA.FLANK_OFFSET);
                if (flankY < RLConfigA.WALL_MARGIN) flankY = RLConfigA.WALL_MARGIN; 
                if (flankY > RLConfigA.MAP_HEIGHT - RLConfigA.WALL_MARGIN) flankY = RLConfigA.MAP_HEIGHT - RLConfigA.WALL_MARGIN;

                goTo(myPos.getX(), flankY);
                break;
            case RETREATING:
                double retreatX = teamA ? 300 : 2700;
                goTo(retreatX, holdY);
                break;
            case DEAD: break;
        }

        // Firing Logic
        RLEnemy target = chooseBestTarget();
        if (target != null) {
            double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
            if (dist <= Parameters.bulletRange) {
                // Simple prediction
                double t = dist / Parameters.bulletVelocity;
                double predX = target.x + target.speedX * t;
                double predY = target.y + target.speedY * t;
                double angle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());
                
                if (Math.abs(angle - getHeading()) < 0.15) { // Roughly facing
                    fire(angle);
                    noFireTicks = 0;
                } else {
                    noFireTicks++;
                    // Note: potentialFieldMove might turn us away. 
                    // If prioritizing fire, we might want to turnTo(angle) here.
                    // But PF usually handles orientation.
                }
            }
        } else {
            noFireTicks = 0;
        }
    }
}
