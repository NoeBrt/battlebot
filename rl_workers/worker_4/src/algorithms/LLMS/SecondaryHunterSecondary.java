package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * SecondaryHunterSecondary
 * - Scouts aggressively dive enemy secondaries first, then kite mains.
 */
public class SecondaryHunterSecondary extends ClaudeUtils {
    private static final double STAGE_X_A = 1200.0;
    private static final double STAGE_X_B = 1800.0;
    private static final double[] STAGE_Y = {700.0, 1300.0};

    private static final double DIVE_MAX = 760.0;
    private static final double DIVE_MIN = 210.0;

    private double stageX, stageY;

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
            stageY = STAGE_Y[0];
        } else {
            myX = teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
            myY = teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
            stageY = STAGE_Y[1];
        }

        stageX = teamA ? STAGE_X_A : STAGE_X_B;

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
            case ADVANCING: goTo(stageX, stageY); break;
            case FIRING: diveFight(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }
    }

    @Override
    protected TrackedEnemy chooseTarget() {
        if (enemies.isEmpty()) return null;

        TrackedEnemy sec = null;
        double secD = Double.MAX_VALUE;
        TrackedEnemy main = null;
        double mainD = Double.MAX_VALUE;

        for (TrackedEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            if (e.type == characteristics.IRadarResult.Types.OpponentSecondaryBot) {
                if (d < secD) { secD = d; sec = e; }
            } else {
                if (d < mainD) { mainD = d; main = e; }
            }
        }
        return sec != null ? sec : main;
    }

    private void diveFight() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = S.ADVANCING;
            return;
        }

        double d = Math.hypot(tgt.x - myX, tgt.y - myY);
        if (d > DIVE_MAX) {
            goTo(tgt.x, tgt.y);
        } else if (d < DIVE_MIN) {
            double away = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(away)) stepTurnTo(away); else doMove(true);
        } else {
            double toward = snapCardinal(Math.atan2(tgt.y - myY, tgt.x - myX));
            if (!isFacing(toward)) stepTurnTo(toward); else doMove(true);
        }
    }
}
