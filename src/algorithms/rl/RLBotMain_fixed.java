package algorithms.rl;

import characteristics.IRadarResult;
import characteristics.Parameters;
import algorithms.external.BotState;

public class RLBotMain_fixed extends RLBotBase_fixed {

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
        if (hasN && hasS) whoAmI = MAIN2;
        else if (!hasN && hasS) whoAmI = MAIN1;
        else whoAmI = MAIN3;

        if (MAIN1.equals(whoAmI)) {
            myPos.setX(teamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX);
            myPos.setY(teamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY);
        } else if (MAIN2.equals(whoAmI)) {
            myPos.setX(teamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX);
            myPos.setY(teamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY);
        } else {
            myPos.setX(teamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX);
            myPos.setY(teamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY);
        }

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
        boolean lowHp = getHealth() < RLConfig.HP_RETREAT_MAIN;

        if (lowHp) {
            mainState = S.RETREATING;
        } else if (hasEnemies) {
            mainState = (noFireTicks > RLConfig.NOFIRE_REPOSITION_TICKS) ? S.FLANKING : S.FIRING;
        } else {
            mainState = S.ADVANCING;
        }

        switch (mainState) {
            case ADVANCING:
                goTo(holdX, holdY);
                break;
            case FIRING:
                potentialFieldMove(kiteMin, kiteMax);
                break;
            case FLANKING:
                double flankDelta = MAIN2.equals(whoAmI) ? 0.0 : (MAIN1.equals(whoAmI) ? -RLConfig.FLANK_Y_OFFSET : RLConfig.FLANK_Y_OFFSET);
                double flankY = myPos.getY() + flankDelta;
                if (flankY < RLConfig.WALL_MARGIN) flankY = RLConfig.WALL_MARGIN;
                if (flankY > RLConfig.MAP_HEIGHT - RLConfig.WALL_MARGIN) flankY = RLConfig.MAP_HEIGHT - RLConfig.WALL_MARGIN;
                goTo(myPos.getX(), flankY);
                break;
            case RETREATING:
                double retreatX = teamA ? 300.0 : 2700.0;
                goTo(retreatX, holdY);
                break;
            case DEAD:
                break;
        }

        RLEnemy target = chooseBestTarget();
        if (target != null) {
            boolean fired = aimAndMaybeFire(target, RLConfig.FIRING_ANGLE_TOLERANCE);
            if (fired) noFireTicks = 0;
            else noFireTicks++;
        } else {
            noFireTicks = 0;
        }
    }
}
