package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * ArcCircleSecondary
 * - Lightweight flank support while mains execute circular arc pressure.
 */
public class ArcCircleSecondary extends ClaudeUtils {
    private static final double LANE_X_A = 1360.0;
    private static final double LANE_X_B = 1640.0;
    private static final double[] LANE_Y = {320.0, 1680.0};

    private static final double RANGE_MIN = 230.0;
    private static final double RANGE_MAX = 760.0;

    private double laneX, laneY;

    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);
        id = NBOT;
        for (IRadarResult o : detectRadar()) {
            if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) id = SBOT;
        }

        if (NBOT.equals(id)) {
            myX = teamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX;
            myY = teamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY;
            laneY = LANE_Y[0];
        } else {
            myX = teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
            myY = teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
            laneY = LANE_Y[1];
        }
        laneX = teamA ? LANE_X_A : LANE_X_B;

        state = S.ADVANCING;
        resumeState = S.ADVANCING;
        BotInfo self = allies.get(id);
        self.update(myX, myY, getHeading());
        self.alive = true;
    }

    @Override
    public void step() {
        if (fireCooldown > 0) fireCooldown--;
        scanRadar();
        readMessages();
        ageEnemies();

        if (getHealth() <= 0) {
            state = S.DEAD;
            allies.get(id).alive = false;
            broadcast("DEAD " + id);
            return;
        }

        broadcast("POS " + id + " " + myX + " " + myY + " " + getHeading());

        TrackedEnemy tgt = chooseTarget();
        if (tryFire(tgt)) broadcast("FOCUS " + (int) tgt.x + " " + (int) tgt.y);

        if (!isAvoiding() && state != S.DEAD) {
            if (!enemies.isEmpty()) state = S.FIRING;
            else state = S.ADVANCING;
        }

        switch (state) {
            case ADVANCING: goTo(laneX, laneY); break;
            case FIRING: skirmish(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }
    }

    private void skirmish() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = S.ADVANCING;
            return;
        }

        double d = Math.hypot(tgt.x - myX, tgt.y - myY);
        if (d > RANGE_MAX) {
            goTo(tgt.x, tgt.y);
            return;
        }
        if (d < RANGE_MIN) {
            double away = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(away)) stepTurnTo(away); else doMove(true);
            return;
        }

        double base = Math.atan2(tgt.y - myY, tgt.x - myX);
        double tangent = base + (NBOT.equals(id) ? Math.PI / 2.0 : -Math.PI / 2.0);
        if (!isFacing(tangent)) stepTurnTo(tangent); else doMove(true);
    }
}
