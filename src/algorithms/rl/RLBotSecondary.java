/* ============================================================================
 * RLBotSecondary.java — RL-optimized secondary bot (2 per team).
 * All strategy constants read from RLConfig.
 * ============================================================================*/
package algorithms.rl;

import characteristics.IRadarResult;
import characteristics.Parameters;
import algorithms.external.BotState;
import characteristics.IRadarResult.Types;

public class RLBotSecondary extends RLBotBase {

    private enum S { FOLLOW, HUNT, PATROL, RETREAT, DEAD }
    private S secState;
    private double holdX = 1500, holdY = 1000;

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
        super.step();

        if (getHealth() <= 0) {
            secState = S.DEAD;
            return;
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
        
        if (fireAngle != null) {
            fire(fireAngle);
            return;
        }

        boolean hasMain = false;
        double hx = 0, hy = 0, count = 0;
        for (java.util.Map.Entry<String, BotState> entry : allyPos.entrySet()) {
            String name = entry.getKey();
            BotState b = entry.getValue();
            if (b.isAlive() && name.startsWith("MAIN")) {
                hasMain = true;
                hx += b.getPosition().getX();
                hy += b.getPosition().getY();
                count++;
            }
        }
        if (count > 0) { holdX = hx/count; holdY = hy/count; }

        if (secState != S.DEAD) {
            if (getHealth() < RLConfig.MAX_HEALTH_SEC * RLConfig.HP_RETREAT_PCT_SEC && rlEnemies.isEmpty()) {
                secState = S.RETREAT;
            } else if (target != null && target.distance < 800) {
                secState = S.HUNT;
            } else if (hasMain) {
                secState = S.FOLLOW;
            } else {
                secState = S.PATROL;
            }
        }

        switch (secState) {
             case FOLLOW:
                 double tx = holdX + (teamA ? -RLConfig.FLANK_OFFSET : RLConfig.FLANK_OFFSET);
                 goTo(tx, holdY);
                 break;
             case HUNT:
                 if (target != null) {
                     potentialFieldMove(RLConfig.PATROL_EVASION_RANGE, RLConfig.PATROL_EVASION_RANGE * 2.5);
                 }
                 break;
             case PATROL:
                 double targetX = teamA ? RLConfig.ADVANCE_X_A : RLConfig.MAP_WIDTH - RLConfig.ADVANCE_X_A;
                 double targetY;
                 
                 if (getHealth() > 0) {
                     targetY = holdY > RLConfig.MAP_CY ? RLConfig.MAP_HEIGHT - RLConfig.FLANK_Y_OFFSET : RLConfig.FLANK_Y_OFFSET;
                 } else {
                     targetY = RLConfig.MAP_HEIGHT - RLConfig.FLANK_Y_OFFSET;
                 }
                 
                 double d = Math.hypot(targetX - myPos.getX(), targetY - myPos.getY());
                 if (d < RLConfig.PATROL_THRESHOLD) {
                     potentialFieldMove(RLConfig.PATROL_EVASION_RANGE, RLConfig.PATROL_EVASION_RANGE * 2.0);
                 } else {
                     goTo(targetX, targetY);
                 }
                 break;
             case RETREAT:
                 goTo(teamA ? 300 : 2700, holdY);
                 break;
             case DEAD:
                 break;
        }
    }
}
