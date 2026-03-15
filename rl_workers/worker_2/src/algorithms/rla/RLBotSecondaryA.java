/* ============================================================================
 * RLBotSecondaryA.java — RL-optimized secondary bot (2 per team).
 * All strategy constants read from RLConfigA.
 * ============================================================================*/
package algorithms.rla;

import characteristics.IRadarResult;
import characteristics.Parameters;

public class RLBotSecondaryA extends RLBotBaseA {

    private enum S { SUPPORT, FLANKING, PATROL, RETREATING, DEAD }
    private S secState;

    @Override
    public void activate() {
        super.activate();

        whoAmI = NBOT;
        for (IRadarResult o: detectRadar()) {
			if (isSameDirection(o.getObjectDirection(),Parameters.NORTH)) whoAmI = SBOT;
		}
		if (whoAmI == NBOT){
			myPos.setX(teamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX);
			myPos.setY(teamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY);
	    } else {
			myPos.setX(teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX);
			myPos.setY(teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY);
	    }
        
        secState = S.PATROL;
    }

    @Override
    public void step() {
        super.step(); // Perform detection and update rlEnemies
        
        if (getHealth() <= 0) { secState = S.DEAD; return; }
        
        // HP Retreat
        if (getHealth() < (RLConfigA.MAX_HEALTH_SEC * RLConfigA.HP_RETREAT_PCT_SEC)) {
            secState = S.RETREATING;
        } else {
            secState = S.PATROL; 
        }
        
        // Act based on State
        switch (secState) {
            case PATROL:
                double targetX = teamA ? RLConfigA.ADVANCE_X_A : (RLConfigA.MAP_WIDTH - RLConfigA.ADVANCE_X_A);
                
                double targetY;
                if (NBOT.equals(whoAmI)) {
                     targetY = RLConfigA.FLANK_Y_OFFSET;
                } else {
                     targetY = RLConfigA.MAP_HEIGHT - RLConfigA.FLANK_Y_OFFSET;
                }
                
                double d = Math.hypot(targetX - myPos.getX(), targetY - myPos.getY());
                if (d < RLConfigA.PATROL_THRESHOLD) {
                    potentialFieldMove(RLConfigA.PATROL_EVASION_RANGE, RLConfigA.PATROL_EVASION_RANGE * 2.0);
                } else {
                    goTo(targetX, targetY);
                }
                break;
                
            case RETREATING:
                double safeX = teamA ? RLConfigA.SAFE_ZONE_X : (RLConfigA.MAP_WIDTH - RLConfigA.SAFE_ZONE_X);
                goTo(safeX, myPos.getY());
                break;
                
            case DEAD: break;
        }
        
        // Fire
        RLEnemy target = chooseBestTarget();
        if (target != null) {
             double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
             if (dist < Parameters.bulletRange) {
                 double t = dist / Parameters.bulletVelocity;
                 double predX = target.x + target.speedX * t;
                 double predY = target.y + target.speedY * t;
                 double angle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());
                 if (Math.abs(angle - getHeading()) < 0.2) fire(angle);
             }
        }
    }
}
