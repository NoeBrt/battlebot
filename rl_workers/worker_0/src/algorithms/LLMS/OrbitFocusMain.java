package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * OrbitFocusMain
 * - Main bots advance to a midline then orbit around focused target.
 * - Keeps medium range and circles target to reduce predictability.
 */
public class OrbitFocusMain extends ClaudeUtils {
    private static final double HOLD_X_A = 900.0;
    private static final double HOLD_X_B = 2100.0;
    private static final double[] FORM_Y = {780.0, 1000.0, 1220.0};

    private static final double ORBIT_MIN = 320.0;
    private static final double ORBIT_MAX = 760.0;

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

        int idx = M1.equals(id) ? 0 : M2.equals(id) ? 1 : 2;
        formY = FORM_Y[idx];
        formX = teamA ? HOLD_X_A : HOLD_X_B;

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
            else if (atFormation()) state = S.HOLDING;
            else state = S.ADVANCING;
        }

        switch (state) {
            case ADVANCING: goTo(formX, formY); break;
            case HOLDING: break;
            case FIRING: doOrbitFight(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }
    }

    private void doOrbitFight() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = atFormation() ? S.HOLDING : S.ADVANCING;
            return;
        }

        double d = Math.hypot(tgt.x - myX, tgt.y - myY);
        if (d > ORBIT_MAX) {
            goTo(tgt.x, tgt.y);
            return;
        }
        if (d < ORBIT_MIN) {
            double away = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(away)) stepTurnTo(away); else doMove(true);
            return;
        }

        // Orbit tangentially around target (M1/M3 opposite direction for spread)
        double base = Math.atan2(tgt.y - myY, tgt.x - myX);
        double tangent = base + (M2.equals(id) ? Math.PI / 2.0 : (M1.equals(id) ? -Math.PI / 2.0 : Math.PI / 2.0));
        double snapped = snapCardinal(tangent);
        if (!isFacing(snapped)) stepTurnTo(snapped); else doMove(true);
    }

    private boolean atFormation() {
        return Math.hypot(myX - formX, myY - formY) < 70.0;
    }
}
