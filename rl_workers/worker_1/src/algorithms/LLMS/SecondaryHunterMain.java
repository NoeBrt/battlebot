package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * SecondaryHunterMain
 * - Prioritizes enemy secondary bots first to collapse enemy support.
 */
public class SecondaryHunterMain extends ClaudeUtils {
    private static final double PUSH_X_A = 980.0;
    private static final double PUSH_X_B = 2020.0;
    private static final double[] FORM_Y = {820.0, 1000.0, 1180.0};
    private static final double FIGHT_MIN = 300.0;
    private static final double FIGHT_MAX = 860.0;

    private double formX, formY;

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

        formX = teamA ? PUSH_X_A : PUSH_X_B;
        formY = FORM_Y[M1.equals(id) ? 0 : M2.equals(id) ? 1 : 2];

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
            else state = atFormation() ? S.HOLDING : S.ADVANCING;
        }

        switch (state) {
            case ADVANCING: goTo(formX, formY); break;
            case HOLDING: break;
            case FIRING: fight(); break;
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

    private void fight() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = atFormation() ? S.HOLDING : S.ADVANCING;
            return;
        }

        double d = Math.hypot(tgt.x - myX, tgt.y - myY);
        if (d > FIGHT_MAX) goTo(tgt.x, tgt.y);
        else if (d < FIGHT_MIN) {
            double away = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(away)) stepTurnTo(away); else doMove(true);
        }
    }

    private boolean atFormation() {
        return Math.hypot(myX - formX, myY - formY) < 70.0;
    }
}
