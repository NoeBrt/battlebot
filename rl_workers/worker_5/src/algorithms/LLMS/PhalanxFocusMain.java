package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * PhalanxFocusMain
 * - Maintains a compact horizontal line, then applies adaptive focus pressure.
 * - Prefers enemy secondaries while own team has numbers advantage.
 */
public class PhalanxFocusMain extends ClaudeUtils {
    private static final double STAGE_X_A = 970.0;
    private static final double STAGE_X_B = 2030.0;
    private static final double[] STAGE_Y = {760.0, 1000.0, 1240.0};

    private static final double PRESS_MIN = 280.0;
    private static final double PRESS_MAX = 800.0;
    private static final double SAFE_MIN = 360.0;
    private static final double SAFE_MAX = 940.0;

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
            case FIRING: doPressureFight(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }
    }

    @Override
    protected TrackedEnemy chooseTarget() {
        if (enemies.isEmpty()) return null;

        int alliesAlive = 0;
        for (BotInfo b : allies.values()) if (b.alive) alliesAlive++;
        boolean aggressive = getHealth() > 120.0 && alliesAlive >= 3;

        TrackedEnemy sec = null;
        double secD = Double.MAX_VALUE;
        TrackedEnemy any = null;
        double anyD = Double.MAX_VALUE;

        for (TrackedEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            if (d < anyD) { anyD = d; any = e; }
            if (e.type == characteristics.IRadarResult.Types.OpponentSecondaryBot && d < secD) {
                secD = d;
                sec = e;
            }
        }

        return aggressive && sec != null ? sec : any;
    }

    private void doPressureFight() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = atStage() ? S.HOLDING : S.ADVANCING;
            return;
        }

        int alliesAlive = 0;
        for (BotInfo b : allies.values()) if (b.alive) alliesAlive++;
        boolean safe = getHealth() < 110.0 || alliesAlive <= 2;

        double min = safe ? SAFE_MIN : PRESS_MIN;
        double max = safe ? SAFE_MAX : PRESS_MAX;
        double d = Math.hypot(tgt.x - myX, tgt.y - myY);

        if (d > max) {
            goTo(tgt.x, tgt.y);
        } else if (d < min) {
            double away = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(away)) stepTurnTo(away); else doMove(true);
        }
    }

    private boolean atStage() {
        return Math.hypot(myX - stageX, myY - stageY) < 75.0;
    }
}
