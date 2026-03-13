package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * OrbitFocusSecondary
 * - Fast scouts take top/bottom lanes then maintain side harassment.
 */
public class OrbitFocusSecondary extends ClaudeUtils {
    private static final double FLANK_Y_N = 230.0;
    private static final double FLANK_Y_S = 1770.0;
    private static final double HARASS_X_A = 1400.0;
    private static final double HARASS_X_B = 1600.0;

    private static final double HARASS_MIN = 260.0;
    private static final double HARASS_MAX = 900.0;

    private double flankY;
    private double harassX;
    private boolean reachedY = false;

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
        } else {
            myX = teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
            myY = teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
        }

        flankY = NBOT.equals(id) ? FLANK_Y_N : FLANK_Y_S;
        harassX = teamA ? HARASS_X_A : HARASS_X_B;

        state = S.FLANKING;
        resumeState = S.FLANKING;
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
            else state = S.FLANKING;
        }

        switch (state) {
            case FLANKING: doFlank(); break;
            case FIRING: doHarassFight(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }
    }

    private void doFlank() {
        if (!reachedY) {
            double heading = NBOT.equals(id) ? normA(Parameters.NORTH) : normA(Parameters.SOUTH);
            boolean done = NBOT.equals(id) ? myY <= flankY : myY >= flankY;
            if (done) {
                reachedY = true;
                return;
            }
            if (!isFacing(heading)) stepTurnTo(heading); else doMove(true);
            return;
        }
        goTo(harassX, flankY);
    }

    private void doHarassFight() {
        TrackedEnemy tgt = chooseTarget();
        if (tgt == null) {
            state = S.FLANKING;
            return;
        }

        double d = Math.hypot(tgt.x - myX, tgt.y - myY);
        if (d > HARASS_MAX) {
            goTo(tgt.x, tgt.y);
        } else if (d < HARASS_MIN) {
            double away = snapCardinal(Math.atan2(myY - tgt.y, myX - tgt.x));
            if (!isFacing(away)) stepTurnTo(away); else doMove(true);
        } else {
            double base = Math.atan2(tgt.y - myY, tgt.x - myX);
            double tangent = base + (NBOT.equals(id) ? Math.PI / 2.0 : -Math.PI / 2.0);
            double snapped = snapCardinal(tangent);
            if (!isFacing(snapped)) stepTurnTo(snapped); else doMove(true);
        }
    }
}
