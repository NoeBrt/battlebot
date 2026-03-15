package algorithms.rl;

import characteristics.IRadarResult;
import characteristics.Parameters;

public class RLBotSecondary_fixed extends RLBotBase_fixed {

    private enum S { PATROL, SUPPORT, RETREATING, DEAD }
    private S secState;

    @Override
    public void activate() {
        super.activate();

        whoAmI = NBOT;
        for (IRadarResult o : detectRadar()) {
            if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) whoAmI = SBOT;
        }

        if (whoAmI == NBOT) {
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

        if (getHealth() < (RLConfig.MAX_HEALTH_SEC * RLConfig.HP_RETREAT_PCT_SEC)) {
            secState = S.RETREATING;
        } else if (!rlEnemies.isEmpty()) {
            secState = S.SUPPORT;
        } else {
            secState = S.PATROL;
        }

        switch (secState) {
            case PATROL:
                double targetX = teamA ? RLConfig.ADVANCE_X_A : (RLConfig.MAP_WIDTH - RLConfig.ADVANCE_X_A);
                double targetY = NBOT.equals(whoAmI) ? RLConfig.FLANK_Y_OFFSET : (RLConfig.MAP_HEIGHT - RLConfig.FLANK_Y_OFFSET);
                double d = Math.hypot(targetX - myPos.getX(), targetY - myPos.getY());
                if (d < RLConfig.PATROL_THRESHOLD) {
                    potentialFieldMove(RLConfig.PATROL_EVASION_RANGE, RLConfig.PATROL_EVASION_RANGE * 1.8);
                } else {
                    goTo(targetX, targetY);
                }
                break;
            case SUPPORT:
                RLEnemy target = chooseBestTarget();
                if (target != null) {
                    double desiredMin = RLConfig.PATROL_EVASION_RANGE * 0.70;
                    double desiredMax = RLConfig.PATROL_EVASION_RANGE * 1.35;
                    potentialFieldMove(desiredMin, desiredMax);
                    aimAndMaybeFire(target, RLConfig.FIRING_ANGLE_TOLERANCE + 0.05);
                }
                break;
            case RETREATING:
                double safeX = teamA ? RLConfig.SAFE_ZONE_X : (RLConfig.MAP_WIDTH - RLConfig.SAFE_ZONE_X);
                goTo(safeX, myPos.getY());
                break;
            case DEAD:
                break;
        }

        if (secState != S.SUPPORT) {
            RLEnemy target = chooseBestTarget();
            if (target != null) aimAndMaybeFire(target, RLConfig.FIRING_ANGLE_TOLERANCE + 0.05);
        }
    }
}
