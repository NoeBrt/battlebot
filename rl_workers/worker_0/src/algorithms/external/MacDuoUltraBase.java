/* =============================================================================
 * MacDuoUltraBase.java — State-of-the-art base class for MacDuoUltra bots.
 *
 * Algorithmic foundations:
 *  • Lanchester Square Law: concentrate all fire on ONE target for N² attrition.
 *  • Newton-Raphson 7-iteration lead targeting with acceleration modelling.
 *  • Anti-Gravity / Potential Field movement for unpredictable combat positioning.
 *  • Segment-circle safe-fire filtering (allies + wrecks).
 *  • Distance-adaptive multi-angle fire spread.
 *  • Damage-tracking focus-fire coordination across all 5 bots.
 * =============================================================================*/
package algorithms.external;

import java.util.*;
import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;
import robotsimulator.Brain;

public abstract class MacDuoUltraBase extends Brain {

    // ── Bot identity tokens ──────────────────────────────────────────────────
    protected static final String NBOT = "NBOT";
    protected static final String SBOT = "SBOT";
    protected static final String M1   = "1";
    protected static final String M2   = "2";
    protected static final String M3   = "3";

    // ── Arena & physics ──────────────────────────────────────────────────────
    protected static final double MAP_W    = 3000.0;
    protected static final double MAP_H    = 2000.0;
    protected static final double BOT_R    = 50.0;
    protected static final double BULLET_R = Parameters.bulletRadius;
    protected static final double ANGLE_PREC = Parameters.teamAMainBotStepTurnAngle * 1.8;
    protected static final int    STALE_TTL  = 70;

    // ── State machine ────────────────────────────────────────────────────────
    protected enum S {
        INIT, ADVANCING, FLANKING, HOLDING, FIRING,
        TURN_RIGHT, TURN_LEFT, BACK,
        RETREATING, DEAD
    }

    // ── Instance state ───────────────────────────────────────────────────────
    protected String  id;
    protected double  myX, myY;
    protected boolean teamA;
    protected S       state;
    protected S       resumeState;

    // Avoidance bookmarks
    protected double savedH;
    protected double avoidTX, avoidTY;

    // World model
    protected List<UEnemy>         enemies = new ArrayList<>();
    protected List<double[]>       wrecks  = new ArrayList<>();
    protected Map<String, UAlly>   allies  = new LinkedHashMap<>();

    // Firing
    protected int    fireCooldown = 0;
    protected double focusX = Double.NaN;
    protected double focusY = Double.NaN;

    // =====================================================================
    //  Inner class: UEnemy — tracked enemy with NR-7 intercept
    // =====================================================================
    protected static class UEnemy {
        double x, y, prevX, prevY, ppX, ppY;
        double speedX, speedY;
        double distance, direction;
        Types  type;
        int    updateCount, stale;
        int    estimatedDmg;

        UEnemy(double x, double y, double d, double dir, Types t) {
            this.x = prevX = ppX = x;
            this.y = prevY = ppY = y;
            distance = d; direction = dir; type = t;
            updateCount = stale = estimatedDmg = 0;
        }

        void update(double nx, double ny, double nd, double ndir) {
            ppX = prevX; ppY = prevY;
            prevX = x;   prevY = y;
            x = nx; y = ny; distance = nd; direction = ndir;
            updateCount++; stale = 0;
            if (updateCount >= 2) { speedX = x - prevX; speedY = y - prevY; }
        }

        /** NR-7 iterative intercept with oscillation dampening + acceleration. */
        double[] intercept(double fromX, double fromY) {
            if (updateCount < 2) return new double[]{x, y};

            double vx = ((x - prevX) * (prevX - ppX) < 0) ? 0.0 : speedX;
            double vy = ((y - prevY) * (prevY - ppY) < 0) ? 0.0 : speedY;

            // Micro-acceleration correction (if 3+ observations)
            if (updateCount >= 3) {
                double ax = speedX - (prevX - ppX);
                double ay = speedY - (prevY - ppY);
                if (Math.abs(ax) < 2.5) vx += ax * 0.25;
                if (Math.abs(ay) < 2.5) vy += ay * 0.25;
            }

            double dx = x - fromX, dy = y - fromY;
            double t = Math.sqrt(dx * dx + dy * dy) / Parameters.bulletVelocity;

            for (int i = 0; i < 7; i++) {
                double px = clampX(x + vx * t);
                double py = clampY(y + vy * t);
                dx = px - fromX; dy = py - fromY;
                t = Math.sqrt(dx * dx + dy * dy) / Parameters.bulletVelocity;
            }
            return new double[]{ clampX(x + vx * t), clampY(y + vy * t) };
        }

        double estimatedHP() {
            return Math.max(0, ((type == Types.OpponentSecondaryBot) ? 100.0 : 300.0) - estimatedDmg);
        }

        private double clampX(double v) { return Math.max(BOT_R, Math.min(MAP_W - BOT_R, v)); }
        private double clampY(double v) { return Math.max(BOT_R, Math.min(MAP_H - BOT_R, v)); }
    }

    // =====================================================================
    //  Inner class: UAlly — ally position
    // =====================================================================
    protected static class UAlly {
        final String id;
        double x, y, heading;
        boolean alive;
        UAlly(String id) { this.id = id; }
        void update(double x, double y, double h) {
            this.x = x; this.y = y; heading = h; alive = true;
        }
    }

    // ── Constructor ──────────────────────────────────────────────────────────
    protected MacDuoUltraBase() {
        super();
        for (String s : new String[]{NBOT, SBOT, M1, M2, M3})
            allies.put(s, new UAlly(s));
    }

    // =====================================================================
    //  MOVEMENT
    // =====================================================================

    protected boolean isSecondary() { return NBOT.equals(id) || SBOT.equals(id); }

    protected void doMove(boolean fwd) {
        double spd    = isSecondary() ? Parameters.teamASecondaryBotSpeed : Parameters.teamAMainBotSpeed;
        double margin = isSecondary() ? 150.0 : 100.0;
        double sign   = fwd ? 1.0 : -1.0;
        double nx = myX + sign * Math.cos(getHeading()) * spd;
        double ny = myY + sign * Math.sin(getHeading()) * spd;
        if (nx > margin && nx < MAP_W - margin && ny > margin && ny < MAP_H - margin) {
            if (fwd) move(); else moveBack();
            myX = nx; myY = ny;
        } else {
            initiateAvoid(state);
        }
    }

    protected void stepTurnTo(double targetAngle) {
        double diff = normA(targetAngle - getHeading());
        if (diff > Math.PI) diff -= 2.0 * Math.PI;
        if (Math.abs(diff) > ANGLE_PREC)
            stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
    }

    protected boolean isFacing(double target) {
        return aDist(getHeading(), target) < ANGLE_PREC;
    }

    /** Navigate toward (tx,ty) using cardinal-snapped heading. */
    protected void goTo(double tx, double ty) {
        double snapped = snapCardinal(Math.atan2(ty - myY, tx - myX));
        if (!isFacing(snapped)) stepTurnTo(snapped);
        else doMove(true);
    }

    // =====================================================================
    //  OBSTACLE AVOIDANCE
    // =====================================================================

    protected void initiateAvoid(S priorState) {
        if (!isAvoiding()) resumeState = priorState;
        savedH = normA(getHeading());
        boolean blockR = false, blockL = false;

        for (IRadarResult o : detectRadar()) {
            if (o.getObjectType() == Types.BULLET) continue;
            double cx = myX + o.getObjectDistance() * Math.cos(o.getObjectDirection());
            double cy = myY + o.getObjectDistance() * Math.sin(o.getObjectDirection());
            double r  = o.getObjectRadius() + BOT_R + 6.0;
            if (!blockR) blockR = pathBlocked(cx, cy, r, normA(savedH + 0.5 * Math.PI));
            if (!blockL) blockL = pathBlocked(cx, cy, r, normA(savedH - 0.5 * Math.PI));
        }

        double ext = BOT_R * 3.5;
        if (!blockR) {
            state   = S.TURN_RIGHT;
            avoidTX = myX + Math.cos(savedH + 0.5 * Math.PI) * ext;
            avoidTY = myY + Math.sin(savedH + 0.5 * Math.PI) * ext;
        } else if (!blockL) {
            state   = S.TURN_LEFT;
            avoidTX = myX + Math.cos(savedH - 0.5 * Math.PI) * ext;
            avoidTY = myY + Math.sin(savedH - 0.5 * Math.PI) * ext;
        } else {
            state   = S.BACK;
            avoidTX = myX - Math.cos(savedH) * ext;
            avoidTY = myY - Math.sin(savedH) * ext;
        }
    }

    protected void doAvoidStep() {
        switch (state) {
            case TURN_RIGHT: {
                double goal = normA(savedH + 0.5 * Math.PI);
                if (!isFacing(goal)) { stepTurnTo(goal); return; }
                if (!reachedAvoid(true)) doMove(true); else endAvoid();
                break;
            }
            case TURN_LEFT: {
                double goal = normA(savedH - 0.5 * Math.PI);
                if (!isFacing(goal)) { stepTurnTo(goal); return; }
                if (!reachedAvoid(true)) doMove(true); else endAvoid();
                break;
            }
            case BACK:
                if (!reachedAvoid(false)) doMove(false); else endAvoid();
                break;
            default: break;
        }
    }

    private void endAvoid() {
        state       = (resumeState != null) ? resumeState : S.HOLDING;
        resumeState = null;
    }

    protected boolean reachedAvoid(boolean fwd) {
        double ch = Math.cos(getHeading()), sh = Math.sin(getHeading());
        boolean rx = Math.abs(ch) < 0.01 || (ch > 0 ? (fwd ? myX >= avoidTX : myX <= avoidTX)
                                                      : (fwd ? myX <= avoidTX : myX >= avoidTX));
        boolean ry = Math.abs(sh) < 0.01 || (sh > 0 ? (fwd ? myY >= avoidTY : myY <= avoidTY)
                                                      : (fwd ? myY <= avoidTY : myY >= avoidTY));
        return rx && ry;
    }

    protected boolean pathBlocked(double cx, double cy, double r, double heading) {
        double dirX = Math.cos(heading), dirY = Math.sin(heading);
        double dx = cx - myX, dy = cy - myY;
        double along = dx * dirX + dy * dirY;
        double perp  = Math.abs(dx * (-dirY) + dy * dirX);
        return along > 0.0 && along < BOT_R * 4.0 && perp < BOT_R + r;
    }

    protected boolean isAvoiding() {
        return state == S.TURN_LEFT || state == S.TURN_RIGHT || state == S.BACK;
    }

    // =====================================================================
    //  FIRING
    // =====================================================================

    protected double computeLeadAngle(UEnemy e) {
        double[] p = e.intercept(myX, myY);
        return Math.atan2(p[1] - myY, p[0] - myX);
    }

    /** Safe-fire check: verifies firing line avoids all allies AND wrecks. */
    protected boolean isSafeFire(double angle) {
        double ex = myX + Math.cos(angle) * Parameters.bulletRange;
        double ey = myY + Math.sin(angle) * Parameters.bulletRange;
        for (UAlly b : allies.values()) {
            if (!b.alive) continue;
            if (Math.hypot(b.x - myX, b.y - myY) < 8.0) continue;
            if (segHitsCircle(myX, myY, ex, ey, b.x, b.y, BOT_R + BULLET_R + 2)) return false;
        }
        for (double[] w : wrecks) {
            if (segHitsCircle(myX, myY, ex, ey, w[0], w[1], BOT_R + BULLET_R)) return false;
        }
        return true;
    }

    protected boolean segHitsCircle(double ax, double ay, double bx, double by,
                                     double cx, double cy, double r) {
        double dx = bx - ax, dy = by - ay;
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return false;
        double ux = dx / len, uy = dy / len;
        double vx = cx - ax, vy = cy - ay;
        double proj = vx * ux + vy * uy;
        double perp = Math.abs(vx * uy - vy * ux);
        return proj > -r && proj < len + r && perp < r;
    }

    /**
     * Distance-adaptive multi-angle fire.
     * At close range the target subtends a larger angle; we adapt the spread.
     * Tries 7 candidate angles from center outward.
     */
    protected boolean tryFire(UEnemy tgt) {
        if (tgt == null || fireCooldown > 0) return false;
        double base = computeLeadAngle(tgt);
        double dist = Math.hypot(tgt.x - myX, tgt.y - myY);
        double maxDev = Math.atan((BOT_R + BULLET_R - 1) / Math.max(dist, 1.0));

        double[] offsets = {0, -0.33, 0.33, -0.67, 0.67, -1.0, 1.0};
        for (double off : offsets) {
            double a = base + maxDev * off;
            if (isSafeFire(a)) {
                fire(a);
                fireCooldown = Parameters.bulletFiringLatency;
                tgt.estimatedDmg += (int) Parameters.bulletDamage;
                broadcast("DMG " + (int) tgt.x + " " + (int) tgt.y + " " + tgt.estimatedDmg);
                return true;
            }
        }
        return false;
    }

    /**
     * Lanchester-optimal target selection.
     * Score = focus_bonus + low_HP_bonus + type_bonus + proximity - staleness + safe_fire.
     */
    protected UEnemy chooseTarget() {
        if (enemies.isEmpty()) return null;
        UEnemy best = null;
        double bestScore = -1e9;

        for (UEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            if (d > Parameters.bulletRange + 200) continue;

            double score = 800.0 - d;

            boolean sec = (e.type == Types.OpponentSecondaryBot);
            if (sec) score += 200.0;

            double hp = e.estimatedHP();
            if (hp <= 30)       score += 350.0;
            else if (hp <= 100) score += 180.0;

            if (!Double.isNaN(focusX) && Math.hypot(e.x - focusX, e.y - focusY) < 120.0)
                score += 400.0;

            score -= e.stale * 5.0;

            if (isSafeFire(computeLeadAngle(e))) score += 120.0;

            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    // =====================================================================
    //  PERCEPTION
    // =====================================================================

    protected void scanRadar() {
        for (IRadarResult o : detectRadar()) {
            double ox = myX + o.getObjectDistance() * Math.cos(o.getObjectDirection());
            double oy = myY + o.getObjectDistance() * Math.sin(o.getObjectDirection());

            if (!isAvoiding() && o.getObjectType() != Types.BULLET) {
                double r = o.getObjectRadius() + BOT_R + 6.0;
                if (pathBlocked(ox, oy, r, getHeading()))
                    initiateAvoid(state);
            }

            switch (o.getObjectType()) {
                case OpponentMainBot:
                case OpponentSecondaryBot: {
                    double d = Math.hypot(ox - myX, oy - myY);
                    broadcast("ENEMY " + o.getObjectDirection() + " " + d
                              + " " + o.getObjectType() + " " + ox + " " + oy);
                    UEnemy e = upsertEnemy(ox, oy, d, o.getObjectDirection(), o.getObjectType());
                    if (e != null && e.updateCount >= 2)
                        broadcast("TRACK " + (int)e.x + " " + (int)e.y + " "
                                  + String.format(Locale.US, "%.2f", e.speedX) + " "
                                  + String.format(Locale.US, "%.2f", e.speedY)
                                  + " " + o.getObjectType());
                    break;
                }
                case Wreck:
                    broadcast("WRECK " + ox + " " + oy);
                    addWreck(ox, oy);
                    break;
                default: break;
            }
        }
    }

    protected void readMessages() {
        for (String msg : fetchAllMessages()) {
            String[] p = msg.split(" ");
            if (p.length < 2) continue;
            switch (p[0]) {
                case "POS":
                    if (p.length >= 5) {
                        UAlly b = allies.get(p[1]);
                        if (b != null) b.update(dbl(p[2]), dbl(p[3]), dbl(p[4]));
                    }
                    break;
                case "DEAD": {
                    UAlly b = allies.get(p[1]);
                    if (b != null) b.alive = false;
                    break;
                }
                case "ENEMY":
                    if (p.length >= 6) {
                        Types t = p[3].contains("Main") ? Types.OpponentMainBot : Types.OpponentSecondaryBot;
                        upsertEnemy(dbl(p[4]), dbl(p[5]), dbl(p[2]), dbl(p[1]), t);
                    }
                    break;
                case "WRECK":
                    if (p.length >= 3) addWreck(dbl(p[1]), dbl(p[2]));
                    break;
                case "FOCUS":
                    if (p.length >= 3) { focusX = dbl(p[1]); focusY = dbl(p[2]); }
                    break;
                case "TRACK":
                    if (p.length >= 6) {
                        Types t = p[5].contains("Main") ? Types.OpponentMainBot : Types.OpponentSecondaryBot;
                        applyTrack(dbl(p[1]), dbl(p[2]), dbl(p[3]), dbl(p[4]), t);
                    }
                    break;
                case "DMG":
                    if (p.length >= 4) applyDmg(dbl(p[1]), dbl(p[2]), Integer.parseInt(p[3]));
                    break;
                default: break;
            }
        }
    }

    protected void ageEnemies() {
        Iterator<UEnemy> it = enemies.iterator();
        while (it.hasNext()) { UEnemy e = it.next(); e.stale++; if (e.stale > STALE_TTL) it.remove(); }
    }

    protected UEnemy upsertEnemy(double x, double y, double d, double dir, Types type) {
        for (double[] w : wrecks)
            if (Math.hypot(x - w[0], y - w[1]) < 60.0) return null;
        for (UEnemy e : enemies)
            if (Math.hypot(x - e.x, y - e.y) < 80.0) { e.update(x, y, d, dir); return e; }
        UEnemy created = new UEnemy(x, y, d, dir, type);
        enemies.add(created);
        return created;
    }

    protected void applyTrack(double x, double y, double vx, double vy, Types type) {
        UEnemy e = upsertEnemy(x, y, Math.hypot(x - myX, y - myY), Math.atan2(y - myY, x - myX), type);
        if (e == null) return;
        e.speedX = vx; e.speedY = vy;
        if (e.updateCount < 2) e.updateCount = 2;
        e.stale = 0;
    }

    protected void applyDmg(double x, double y, int dmg) {
        for (UEnemy e : enemies)
            if (Math.hypot(e.x - x, e.y - y) < 100.0) {
                e.estimatedDmg = Math.max(e.estimatedDmg, dmg);
                return;
            }
    }

    protected void addWreck(double wx, double wy) {
        for (double[] w : wrecks)
            if (Math.hypot(wx - w[0], wy - w[1]) < 40.0) return;
        wrecks.add(new double[]{wx, wy});
        enemies.removeIf(e -> Math.hypot(e.x - wx, e.y - wy) < 60.0);
    }

    // =====================================================================
    //  POTENTIAL FIELD MOVEMENT  (Anti-Gravity inspired)
    // =====================================================================

    /**
     * Computes the optimal movement direction via potential-field analysis.
     * Enemies create attract/repel/tangential forces; allies repel to avoid
     * clumping; walls and wrecks repel. The resultant vector is the ideal
     * heading — far more unpredictable than cardinal-snap kiting.
     */
    protected double potentialFieldAngle(double kiteMin, double kiteMax) {
        double fx = 0, fy = 0;

        // 1. Enemy forces
        for (UEnemy e : enemies) {
            double dx = e.x - myX, dy = e.y - myY;
            double dist = Math.hypot(dx, dy);
            if (dist < 1.0) continue;
            double nx = dx / dist, ny = dy / dist;

            if (dist < kiteMin) {
                double s = 2.5 * (kiteMin - dist) / kiteMin;
                fx -= nx * s; fy -= ny * s;
            } else if (dist > kiteMax) {
                double s = 0.6 * (dist - kiteMax) / kiteMax;
                fx += nx * s; fy += ny * s;
            } else {
                double side = M1.equals(id) ? 1.0 : (M3.equals(id) ? -1.0 : (teamA ? 1.0 : -1.0));
                fx += (-ny * side) * 0.7;
                fy += ( nx * side) * 0.7;
            }
        }

        // 2. Ally repulsion (anti-clump)
        for (UAlly a : allies.values()) {
            if (!a.alive || a.id.equals(id)) continue;
            double dx = a.x - myX, dy = a.y - myY;
            double dist = Math.hypot(dx, dy);
            if (dist < 200.0 && dist > 1.0) {
                double s = 0.9 * (200.0 - dist) / 200.0;
                fx -= (dx / dist) * s;
                fy -= (dy / dist) * s;
            }
        }

        // 3. Wall repulsion
        double wf = 0.6;
        if (myX < 200)          fx += wf * (200 - myX) / 200;
        if (myX > MAP_W - 200)  fx -= wf * (myX - (MAP_W - 200)) / 200;
        if (myY < 200)          fy += wf * (200 - myY) / 200;
        if (myY > MAP_H - 200)  fy -= wf * (myY - (MAP_H - 200)) / 200;

        // 4. Wreck avoidance
        for (double[] w : wrecks) {
            double dx = w[0] - myX, dy = w[1] - myY;
            double dist = Math.hypot(dx, dy);
            if (dist < 150.0 && dist > 1.0) {
                double s = 0.5 * (150.0 - dist) / 150.0;
                fx -= (dx / dist) * s;
                fy -= (dy / dist) * s;
            }
        }

        return (Math.hypot(fx, fy) < 0.01) ? getHeading() : Math.atan2(fy, fx);
    }

    // =====================================================================
    //  ANGLE UTILITIES
    // =====================================================================

    protected double normA(double a) {
        while (a < 0.0)            a += 2.0 * Math.PI;
        while (a >= 2.0 * Math.PI) a -= 2.0 * Math.PI;
        return a;
    }

    protected double aDist(double a, double b) {
        double d = Math.abs(normA(a) - normA(b));
        return Math.min(d, 2.0 * Math.PI - d);
    }

    protected double snapCardinal(double angle) {
        double na = normA(angle), best = 0.0, minD = Double.MAX_VALUE;
        for (double c : new double[]{0, Math.PI / 2.0, Math.PI, 3.0 * Math.PI / 2.0}) {
            double d = aDist(na, c);
            if (d < minD) { minD = d; best = c; }
        }
        return best;
    }

    protected int countAliveAllies() {
        int c = 0;
        for (UAlly a : allies.values()) if (a.alive && !a.id.equals(id)) c++;
        return c;
    }

    private static double dbl(String s) { return Double.parseDouble(s); }
}
