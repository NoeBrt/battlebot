package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * AdaptiveKiteMain
 * - Adaptive pressure: push when team healthy, kite when behind or low HP.
 */
public class AdaptiveKiteMain extends ClaudeUtils {
    private static final double ANCHOR_X_A = 930.0;
    private static final double ANCHOR_X_B = 2070.0;
    private static final double[] ANCHOR_Y = {790.0, 1000.0, 1210.0};

    private static final double PUSH_MIN = 230.0;
    private static final double PUSH_MAX = 760.0;
    private static final double KITE_MIN = 360.0;
    private static final double KITE_MAX = 920.0;

    private double anchorX, anchorY;

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

        anchorX = teamA ? ANCHOR_X_A : ANCHOR_X_B;
        anchorY = ANCHOR_Y[M1.equals(id) ? 0 : M2.equals(id) ? 1 : 2];

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
            else state = atAnchor() ? S.HOLDING : S.ADVANCING;
        }

        switch (state) {
            case ADVANCING: goTo(anchorX, anchorY); break;
            case HOLDING: break;
            case FIRING: adaptiveFight(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }
    }

    private void adaptiveFight() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = atAnchor() ? S.HOLDING : S.ADVANCING;
            return;
        }

        int alliesAlive = 0;
        for (BotInfo b : allies.values()) if (b.alive) alliesAlive++;
        boolean defensive = getHealth() < 120.0 || alliesAlive <= 2;

        double d = Math.hypot(tgt.x - myX, tgt.y - myY);
        double min = defensive ? KITE_MIN : PUSH_MIN;
        double max = defensive ? KITE_MAX : PUSH_MAX;

        if (d > max) goTo(tgt.x, tgt.y);
        else if (d < min) {
            double away = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(away)) stepTurnTo(away); else doMove(true);
        }
    }

    private boolean atAnchor() {
        return Math.hypot(myX - anchorX, myY - anchorY) < 70.0;
    }
}
