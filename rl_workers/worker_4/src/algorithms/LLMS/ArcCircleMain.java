package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * ArcCircleMain
 * - Main bots form up then fight by moving on circular arcs around the target.
 * - Unlike cardinal-snapped movers, it keeps continuous headings to produce real arcs.
 */
public class ArcCircleMain extends ClaudeUtils {
    private static final double STAGE_X_A = 940.0;
    private static final double STAGE_X_B = 2060.0;
    private static final double[] STAGE_Y = {760.0, 1000.0, 1240.0};

    private static final double ORBIT_R_MIN = 300.0;
    private static final double ORBIT_R_MAX = 760.0;

    private double stageX, stageY;

    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);

        boolean hasN = false, hasS = false;
        for (IRadarResult o : detectRadar()) {
            if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) hasN = true;
            if (aDist(o.getObjectDirection(), Parameters.SOUTH) < ANGLE_PREC) hasS = true;
        }

        if (hasN && hasS) id = M2;
        else if (!hasN && hasS) id = M1;
        else id = M3;

        if (M1.equals(id)) {
            myX = teamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX;
            myY = teamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY;
        } else if (M2.equals(id)) {
            myX = teamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX;
            myY = teamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY;
        } else {
            myX = teamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX;
            myY = teamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY;
        }

        stageX = teamA ? STAGE_X_A : STAGE_X_B;
        stageY = STAGE_Y[M1.equals(id) ? 0 : M2.equals(id) ? 1 : 2];

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
            else state = atStage() ? S.HOLDING : S.ADVANCING;
        }

        switch (state) {
            case ADVANCING: goTo(stageX, stageY); break;
            case HOLDING: break;
            case FIRING: circleFight(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }
    }

    private void circleFight() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = atStage() ? S.HOLDING : S.ADVANCING;
            return;
        }

        double dx = tgt.x - myX;
        double dy = tgt.y - myY;
        double d = Math.hypot(dx, dy);
        double base = Math.atan2(dy, dx);

        if (d > ORBIT_R_MAX) {
            arcMove(base, 0.0);
            return;
        }

        if (d < ORBIT_R_MIN) {
            arcMove(base + Math.PI, 0.0);
            return;
        }

        double side = M1.equals(id) ? -1.0 : (M3.equals(id) ? 1.0 : (teamA ? 1.0 : -1.0));
        // tangent with slight radial correction to stay on an arc ring
        double desired = base + side * (Math.PI / 2.0);
        if (d > 560.0) desired += side * 0.22;
        else if (d < 420.0) desired -= side * 0.22;

        arcMove(desired, 0.16);
    }

    private void arcMove(double targetHeading, double lead) {
        double desired = normA(targetHeading + lead);
        if (!isFacing(desired)) {
            stepTurnTo(desired);
        } else {
            doMove(true);
        }
    }

    private boolean atStage() {
        return Math.hypot(myX - stageX, myY - stageY) < 75.0;
    }
}
