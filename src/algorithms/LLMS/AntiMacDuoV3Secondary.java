package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/** AntiMacDuoV3Secondary: V2 scout/harass split + safer multi-angle firing. */
public class AntiMacDuoV3Secondary extends ClaudeUtils {
    private static final double STAGE_X_A = 1460.0, STAGE_X_B = 1540.0;
    private static final double[] STAGE_Y = {230.0, 1770.0};
    private static final double FIRE_SPREAD = 0.12;
    private double sx, sy;
    private int noFireTicks = 0;

    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);
        id = NBOT;
        for (IRadarResult o : detectRadar()) if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) id = SBOT;
        if (NBOT.equals(id)) {
            myX = teamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX;
            myY = teamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY;
            sy = STAGE_Y[0];
        } else {
            myX = teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
            myY = teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
            sy = STAGE_Y[1];
        }
        sx = teamA ? STAGE_X_A : STAGE_X_B;
        state = S.ADVANCING;
        resumeState = S.ADVANCING;
        BotInfo self = allies.get(id);
        self.update(myX, myY, getHeading());
        self.alive = true;
    }

    @Override
    protected TrackedEnemy chooseTarget() {
        if (enemies.isEmpty()) return null;
        TrackedEnemy best = null;
        double bestScore = -1e9;
        for (TrackedEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            boolean sec = (e.type == characteristics.IRadarResult.Types.OpponentSecondaryBot);
            double pri = sec ? 110.0 : 15.0;
            if (NBOT.equals(id)) pri += 20.0;
            if (!Double.isNaN(focusX) && Math.hypot(e.x - focusX, e.y - focusY) < 140.0) pri += 70.0;
            double sc = pri - d;
            if (sc > bestScore) {
                bestScore = sc;
                best = e;
            }
        }
        return best;
    }

    @Override
    protected boolean tryFire(TrackedEnemy tgt) {
        if (tgt == null || fireCooldown > 0) return false;
        double base = computeLeadAngle(tgt);
        double[] angles = {base, base - FIRE_SPREAD, base + FIRE_SPREAD};
        for (double a : angles) {
            if (isSafeFire(a)) {
                fire(a);
                fireCooldown = Parameters.bulletFiringLatency;
                return true;
            }
        }
        return false;
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

        TrackedEnemy t = chooseTarget();
        boolean fired = tryFire(t);
        if (t != null) {
            if (fired) {
                noFireTicks = 0;
                broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
            } else {
                noFireTicks++;
                if (noFireTicks % 8 == 0) broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
            }
        } else noFireTicks = 0;

        if (!isAvoiding() && state != S.DEAD) state = enemies.isEmpty() ? S.ADVANCING : (noFireTicks > 20 ? S.FLANKING : S.FIRING);

        switch (state) {
            case ADVANCING:
                goTo(sx, sy);
                break;
            case FIRING:
                skirmish();
                break;
            case FLANKING:
                clearShotReposition();
                break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK:
                doAvoidStep();
                break;
            default:
                break;
        }

        t = chooseTarget();
        fired = tryFire(t);
        if (t != null && fired) {
            noFireTicks = 0;
            broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
        }
    }

    private void skirmish() {
        TrackedEnemy t = chooseTarget();
        if (t == null) return;
        double d = Math.hypot(t.x - myX, t.y - myY);
        double base = Math.atan2(t.y - myY, t.x - myX);

        if (NBOT.equals(id)) {
            if (d > 700) {
                if (!isFacing(base)) stepTurnTo(base); else doMove(true);
            } else {
                double tang = base + Math.PI / 2.0;
                if (!isFacing(tang)) stepTurnTo(tang); else doMove(true);
            }
            return;
        }

        if (d > 760) {
            if (!isFacing(base)) stepTurnTo(base); else doMove(true);
        } else if (d < 300) {
            double away = normA(base + Math.PI);
            if (!isFacing(away)) stepTurnTo(away); else doMove(true);
        } else {
            double tang = base - Math.PI / 2.0;
            if (!isFacing(tang)) stepTurnTo(tang); else doMove(true);
        }
    }

    private void clearShotReposition() {
        TrackedEnemy t = chooseTarget();
        if (t == null) return;
        double base = Math.atan2(t.y - myY, t.x - myX);
        double side = NBOT.equals(id) ? 1.0 : -1.0;
        double angle = base + side * Math.PI / 3.0;
        if (!isFacing(angle)) stepTurnTo(angle);
        else doMove(true);
    }
}
