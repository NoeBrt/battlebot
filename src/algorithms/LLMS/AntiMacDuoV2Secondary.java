package algorithms.LLMS;

import characteristics.IRadarResult;
import characteristics.Parameters;

/** AntiMacDuoV2Secondary: NBOT scout sacrifice, SBOT harass + focus relay. */
public class AntiMacDuoV2Secondary extends ClaudeUtils {
    private static final double STAGE_X_A = 1460.0, STAGE_X_B = 1540.0;
    private static final double[] STAGE_Y = {240.0, 1760.0};
    private double sx, sy;

    @Override
    public void activate() {
        teamA = (getHeading() == Parameters.EAST);
        id = NBOT;
        for (IRadarResult o : detectRadar()) if (aDist(o.getObjectDirection(), Parameters.NORTH) < ANGLE_PREC) id = SBOT;
        if (NBOT.equals(id)) { myX = teamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX; myY = teamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY; sy = STAGE_Y[0]; }
        else { myX = teamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX; myY = teamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY; sy = STAGE_Y[1]; }
        sx = teamA ? STAGE_X_A : STAGE_X_B;
        state = S.ADVANCING; resumeState = S.ADVANCING;
        BotInfo self = allies.get(id); self.update(myX, myY, getHeading()); self.alive = true;
    }

    @Override
    protected TrackedEnemy chooseTarget() {
        if (enemies.isEmpty()) return null;
        TrackedEnemy best = null; double bestScore = -1e9;
        for (TrackedEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            double pri = (e.type == characteristics.IRadarResult.Types.OpponentSecondaryBot) ? 90.0 : 0.0;
            if (NBOT.equals(id)) pri += 15.0; // scout pressures information on nearest threat
            double sc = pri - d;
            if (sc > bestScore) { bestScore = sc; best = e; }
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

        if (!isAvoiding() && state != S.DEAD) state = enemies.isEmpty() ? S.ADVANCING : S.FIRING;

        switch (state) {
            case ADVANCING: goTo(sx, sy); break;
            case FIRING: skirmish(); break;
            case TURN_LEFT:
            case TURN_RIGHT:
            case BACK: doAvoidStep(); break;
            default: break;
        }

        t = chooseTarget();
        if (tryFire(t)) broadcast("FOCUS " + (int) t.x + " " + (int) t.y);
    }

    private void skirmish() {
        TrackedEnemy t = chooseTarget();
        if (t == null) return;
        double d = Math.hypot(t.x - myX, t.y - myY);
        double base = Math.atan2(t.y - myY, t.x - myX);
        if (NBOT.equals(id)) {
            // scout: accept risk to keep contact
            if (d > 690) { if (!isFacing(base)) stepTurnTo(base); else doMove(true); }
            else { double tang = base + Math.PI / 2.0; if (!isFacing(tang)) stepTurnTo(tang); else doMove(true); }
            return;
        }
        // SBOT safer harass
        if (d > 760) { if (!isFacing(base)) stepTurnTo(base); else doMove(true); }
        else if (d < 280) { double away = normA(base + Math.PI); if (!isFacing(away)) stepTurnTo(away); else doMove(true); }
        else { double tang = base - Math.PI / 2.0; if (!isFacing(tang)) stepTurnTo(tang); else doMove(true); }
    }
}
