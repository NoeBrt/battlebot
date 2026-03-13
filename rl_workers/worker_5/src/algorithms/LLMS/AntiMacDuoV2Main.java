package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/** AntiMacDuoV2Main: target assignment 2+1 + decoupled fire/move + ally spacing. */
public class AntiMacDuoV2Main extends ClaudeUtils {
    private static final double HOLD_X_A = 980.0, HOLD_X_B = 2020.0;
    private static final double[] HOLD_Y = {780.0, 1000.0, 1220.0};
    private static final double MIN_R = 300.0, MAX_R = 740.0;
    private double holdX, holdY;

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
            double pri = sec ? 120.0 : 20.0;
            // 2+1 assignment: M1/M2 collapse on same best secondary, M3 freer to punish closest
            if (M3.equals(id) && !sec) pri += 30.0;
            double score = pri - d;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    @Override
    public void step() {
        if (fireCooldown > 0) fireCooldown--;
        scanRadar(); readMessages(); ageEnemies();
        if (getHealth() <= 0) { state = S.DEAD; allies.get(id).alive = false; broadcast("DEAD " + id); return; }

        broadcast("POS " + id + " " + myX + " " + myY + " " + getHeading());
        TrackedEnemy t = chooseTarget();
        if (tryFire(t)) broadcast("FOCUS " + (int) t.x + " " + (int) t.y);

        if (!isAvoiding() && state != S.DEAD) {
            if (!enemies.isEmpty()) state = S.FIRING;
            else state = Math.hypot(myX - holdX, myY - holdY) < 70 ? S.HOLDING : S.ADVANCING;
        }

        switch (state) {
            case ADVANCING: goTo(holdX, holdY); break;
            case FIRING: fightMove(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }

        t = chooseTarget();
        if (tryFire(t)) broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
    }

    private void fightMove() {
        TrackedEnemy t = chooseTarget();
        if (t == null) return;

        // ally spacing (anti-clump)
        for (BotInfo b : allies.values()) {
            if (!b.alive || b.id.equals(id)) continue;
            double db = Math.hypot(b.x - myX, b.y - myY);
            if (db > 1.0 && db < 130.0) {
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
}
