package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/** AntiMacDuoV3Main: V2 + safer multi-angle fire pipeline + tighter anti-clump movement. */
public class AntiMacDuoV3Main extends ClaudeUtils {
    private static final double HOLD_X_A = 980.0, HOLD_X_B = 2020.0;
    private static final double[] HOLD_Y = {760.0, 1000.0, 1240.0};
    // Slightly wider kiting bubble to reduce close-range trades vs MacDuo.
    // Slightly tighter engagement envelope to avoid over-kiting into low-pressure stalemates.
    private static final double MIN_R = 360.0, MAX_R = 720.0;
    private static final double FIRE_SPREAD = 0.10;
    private double holdX, holdY;
    private int noFireTicks = 0;

    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);
        boolean hasN = false, hasS = false;
        for (IRadarResult o : detectRadar()) {
            if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) hasN = true;
            if (aDist(o.getObjectDirection(), Parameters.SOUTH) < ANGLE_PREC) hasS = true;
        }
        if (hasN && hasS) id = M2; else if (!hasN && hasS) id = M1; else id = M3;

        if (M1.equals(id)) { myX = teamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX; myY = teamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY; }
        else if (M2.equals(id)) { myX = teamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX; myY = teamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY; }
        else { myX = teamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX; myY = teamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY; }

        holdX = teamA ? HOLD_X_A : HOLD_X_B;
        holdY = HOLD_Y[M1.equals(id) ? 0 : M2.equals(id) ? 1 : 2];
        state = S.ADVANCING; resumeState = S.ADVANCING;
        BotInfo self = allies.get(id); self.update(myX, myY, getHeading()); self.alive = true;
    }

    @Override
    protected TrackedEnemy chooseTarget() {
        if (enemies.isEmpty()) return null;
        TrackedEnemy best = null; double bestScore = -1e9;
        for (TrackedEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            boolean sec = e.type == characteristics.IRadarResult.Types.OpponentSecondaryBot;
            // Adaptive secondary pressure: strong nearby focus, reduced long-range tunnel vision.
            double pri = sec ? (d < 650.0 ? 170.0 : 120.0) : 10.0;
            if (M3.equals(id) && !sec) pri += 55.0;
            if (!Double.isNaN(focusX) && Math.hypot(e.x - focusX, e.y - focusY) < 140.0) pri += 80.0;
            double score = pri - d;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    @Override
    protected boolean tryFire(TrackedEnemy tgt) {
        if (tgt == null || fireCooldown > 0) return false;
        double base = computeLeadAngle(tgt);
        double[] angles = {base, base - FIRE_SPREAD, base + FIRE_SPREAD, base - 2 * FIRE_SPREAD, base + 2 * FIRE_SPREAD};
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
        scanRadar(); readMessages(); ageEnemies();
        if (getHealth() <= 0) { state = S.DEAD; allies.get(id).alive = false; broadcast("DEAD " + id); return; }

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

        if (!isAvoiding() && state != S.DEAD) {
            if (!enemies.isEmpty()) state = (noFireTicks > 25 ? S.FLANKING : S.FIRING);
            else state = Math.hypot(myX - holdX, myY - holdY) < 70 ? S.HOLDING : S.ADVANCING;
        }

        switch (state) {
            case ADVANCING: goTo(holdX, holdY); break;
            case FIRING: fightMove(); break;
            case FLANKING: clearShotReposition(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }

        t = chooseTarget();
        fired = tryFire(t);
        if (t != null && fired) {
            noFireTicks = 0;
            broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
        }
    }

    private void fightMove() {
        TrackedEnemy t = chooseTarget();
        if (t == null) return;

        for (BotInfo b : allies.values()) {
            if (!b.alive || b.id.equals(id)) continue;
            double db = Math.hypot(b.x - myX, b.y - myY);
            if (db > 1.0 && db < 150.0) {
                double awayAlly = Math.atan2(myY - b.y, myX - b.x);
                if (!isFacing(awayAlly)) stepTurnTo(awayAlly); else doMove(true);
                return;
            }
        }

        double dx = t.x - myX, dy = t.y - myY;
        double d = Math.hypot(dx, dy), base = Math.atan2(dy, dx);

        if (d > MAX_R) { if (!isFacing(base)) stepTurnTo(base); else doMove(true); return; }
        if (d < MIN_R) { double away = normA(base + Math.PI); if (!isFacing(away)) stepTurnTo(away); else doMove(true); return; }

        double side = M1.equals(id) ? -1.0 : (M3.equals(id) ? 1.0 : (teamA ? 1.0 : -1.0));
        double tang = base + side * Math.PI / 2.0;
        if (!isFacing(tang)) stepTurnTo(tang); else doMove(true);
    }

    private void clearShotReposition() {
        TrackedEnemy t = chooseTarget();
        if (t == null) return;
        double base = Math.atan2(t.y - myY, t.x - myX);
        double side = M1.equals(id) ? -1.0 : (M3.equals(id) ? 1.0 : (teamA ? 1.0 : -1.0));
        double angle = base + side * Math.PI / 3.0;
        if (!isFacing(angle)) stepTurnTo(angle);
        else doMove(true);
    }
}
