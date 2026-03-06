/* =============================================================================
 * ClaudeUtils.java
 * Shared data structures + abstract base class for ClaudeMain / ClaudeSecondary.
 *
 * ── Algorithmic foundations ─────────────────────────────────────────────────
 *  • Lanchester Square Law (1916): concentrate all fire on ONE target to
 *    maximise attrition efficiency; eliminating one bot squares the force.
 *  • Newton-Raphson iterative lead targeting: 5 iterations converge to the
 *    exact intercept angle for a moving target.
 *  • Segment-circle intersection for safe-fire check: no ally-friendly fire.
 *  • Cardinal-snapping navigation: restricts movement to E/S/W/N so obstacle
 *    avoidance is predictable and avoidance rectangles are axis-aligned.
 * =============================================================================*/
package algorithms.LLMS;

import java.util.*;
import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;
import robotsimulator.Brain;

// ─────────────────────────────────────────────────────────────────────────────
//  TrackedEnemy  –  position history + NR intercept prediction
// ─────────────────────────────────────────────────────────────────────────────
class TrackedEnemy {
    double x, y;
    double prevX, prevY, ppX, ppY;
    double speedX, speedY;
    double distance, direction;
    Types  type;
    int    updateCount;
    int    stale;   // steps since last observation; removed when > STALE_TTL

    TrackedEnemy(double x, double y, double d, double dir, Types t) {
        this.x = prevX = ppX = x;
        this.y = prevY = ppY = y;
        distance = d; direction = dir; type = t;
        speedX = speedY = 0;
        updateCount = stale = 0;
    }

    void update(double nx, double ny, double nd, double ndir) {
        ppX = prevX; ppY = prevY;
        prevX = x;   prevY = y;
        x = nx; y = ny; distance = nd; direction = ndir;
        updateCount++;
        stale = 0;
        if (updateCount >= 2) { speedX = x - prevX; speedY = y - prevY; }
    }

    /**
     * Newton-Raphson iterative intercept (5 iterations).
     * Oscillation damping: if the velocity reverses in X or Y, zero that component.
     * Returns {predictedX, predictedY}.
     */
    double[] intercept(double fromX, double fromY) {
        if (updateCount < 2) return new double[]{x, y};

        // Dampen oscillating axes
        double vx = ((x - prevX) * (prevX - ppX) < 0) ? 0.0 : speedX;
        double vy = ((y - prevY) * (prevY - ppY) < 0) ? 0.0 : speedY;

        // Initial travel-time estimate
        double dx = x - fromX, dy = y - fromY;
        double t  = Math.sqrt(dx*dx + dy*dy) / Parameters.bulletVelocity;

        for (int i = 0; i < 5; i++) {
            double px = x + vx * t, py = y + vy * t;
            dx = px - fromX; dy = py - fromY;
            t  = Math.sqrt(dx*dx + dy*dy) / Parameters.bulletVelocity;
        }
        return new double[]{x + vx * t, y + vy * t};
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BotInfo  –  ally position/state as broadcast by teammates
// ─────────────────────────────────────────────────────────────────────────────
class BotInfo {
    final String id;
    double x, y, heading;
    boolean alive;

    BotInfo(String id) { this.id = id; alive = false; x = y = heading = 0; }

    void update(double x, double y, double h) {
        this.x = x; this.y = y; heading = h; alive = true;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ClaudeUtils  –  abstract base
// ─────────────────────────────────────────────────────────────────────────────
public abstract class ClaudeUtils extends Brain {

    // ── Bot identity tokens ──────────────────────────────────────────────────
    protected static final String NBOT = "NBOT";
    protected static final String SBOT = "SBOT";
    protected static final String M1   = "M1";
    protected static final String M2   = "M2";
    protected static final String M3   = "M3";

    // ── Arena dimensions ─────────────────────────────────────────────────────
    protected static final double MAP_W = 3000.0;
    protected static final double MAP_H = 2000.0;

    // ── Physical constants ───────────────────────────────────────────────────
    protected static final double BOT_R     = 50.0;
    protected static final double BULLET_R  = Parameters.bulletRadius;   // 5 mm
    // Step-turn angle = 0.01 * PI ≈ 0.03142 rad.  ANGLE_PREC slightly larger
    // so `isFacing()` triggers safely within one extra step.
    protected static final double ANGLE_PREC = Parameters.teamAMainBotStepTurnAngle * 1.8; // ≈ 0.0565 rad

    // Enemy TTL: remove after this many steps without an update
    protected static final int STALE_TTL = 80;

    // ── State enumeration ────────────────────────────────────────────────────
    protected enum S {
        ADVANCING,   // moving toward formation / flank waypoint
        FLANKING,    // secondary: push to extreme Y then harass X
        HOLDING,     // idle at formation position
        FIRING,      // actively engaging enemies
        TURN_RIGHT,  // obstacle avoidance: turn 90° right then step forward
        TURN_LEFT,   // obstacle avoidance: turn 90° left  then step forward
        BACK,        // obstacle avoidance: reverse
        RETREATING,  // low health, return to safe zone
        DEAD
    }

    // ── Instance fields ──────────────────────────────────────────────────────
    protected String id;
    protected double myX, myY;
    protected boolean teamA;
    protected S state;
    protected S resumeState; // state to restore after obstacle avoidance

    // Avoidance bookmarks
    protected double savedH;           // heading at moment avoidance started
    protected double avoidTX, avoidTY; // target point to reach before resuming

    // World model
    protected List<TrackedEnemy>   enemies = new ArrayList<>();
    protected List<double[]>       wrecks  = new ArrayList<>();
    protected Map<String, BotInfo> allies  = new LinkedHashMap<>();

    // Firing cooldown (bulletFiringLatency = 20)
    protected int fireCooldown = 0;

    // Focus-fire hint received via broadcast
    protected double focusX = Double.NaN;
    protected double focusY = Double.NaN;

    // ── Constructor ──────────────────────────────────────────────────────────
    protected ClaudeUtils() {
        super();
        for (String s : new String[]{NBOT, SBOT, M1, M2, M3})
            allies.put(s, new BotInfo(s));
    }

    // =========================================================================
    //  Movement helpers
    // =========================================================================

    /**
     * Move one step forward (fwd=true) or backward (fwd=false).
     * Updates myX/myY on success; triggers avoidance if out of bounds.
     */
    protected void doMove(boolean fwd) {
        boolean sec    = isSecondary();
        double  spd    = sec ? Parameters.teamASecondaryBotSpeed : Parameters.teamAMainBotSpeed;
        double  margin = sec ? 150.0 : 100.0;
        double  sign   = fwd ? 1.0 : -1.0;
        double  nx     = myX + sign * Math.cos(getHeading()) * spd;
        double  ny     = myY + sign * Math.sin(getHeading()) * spd;

        if (nx > margin && nx < MAP_W - margin && ny > margin && ny < MAP_H - margin) {
            if (fwd) move(); else moveBack();
            myX = nx; myY = ny;
        } else {
            initiateAvoid(state);
        }
    }

    /**
     * Execute one step-turn toward targetAngle.
     * Uses the shortest arc (handles wrap-around correctly).
     */
    protected void stepTurnTo(double targetAngle) {
        double diff = normA(targetAngle - getHeading());
        if (diff > Math.PI) diff -= 2.0 * Math.PI;
        if (Math.abs(diff) > ANGLE_PREC)
            stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
    }

    /** Returns true if bot heading is within ANGLE_PREC of target. */
    protected boolean isFacing(double target) {
        return aDist(getHeading(), target) < ANGLE_PREC;
    }

    /**
     * Navigate toward (tx, ty): snap the desired angle to the nearest cardinal
     * direction (N/S/E/W), turn if necessary, then move forward.
     */
    protected void goTo(double tx, double ty) {
        double raw     = Math.atan2(ty - myY, tx - myX);
        double snapped = snapCardinal(raw);
        if (!isFacing(snapped)) stepTurnTo(snapped);
        else doMove(true);
    }

    // =========================================================================
    //  Obstacle avoidance
    // =========================================================================

    /**
     * Detect which side is clear and initiate the appropriate avoidance manoeuvre.
     * Saves `priorState` so it can be restored after avoidance completes.
     */
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

        if (!blockR) {
            state   = S.TURN_RIGHT;
            avoidTX = myX + Math.cos(savedH + 0.5 * Math.PI) * BOT_R * 3.5;
            avoidTY = myY + Math.sin(savedH + 0.5 * Math.PI) * BOT_R * 3.5;
        } else if (!blockL) {
            state   = S.TURN_LEFT;
            avoidTX = myX + Math.cos(savedH - 0.5 * Math.PI) * BOT_R * 3.5;
            avoidTY = myY + Math.sin(savedH - 0.5 * Math.PI) * BOT_R * 3.5;
        } else {
            state   = S.BACK;
            avoidTX = myX - Math.cos(savedH) * BOT_R * 3.5;
            avoidTY = myY - Math.sin(savedH) * BOT_R * 3.5;
        }
    }

    /** Execute one avoidance step (call when state is TURN_RIGHT / TURN_LEFT / BACK). */
    protected void doAvoidStep() {
        switch (state) {
            case TURN_RIGHT: {
                double goal = normA(savedH + 0.5 * Math.PI);
                if (!isFacing(goal)) { stepTurnTo(goal); return; }
                if (!reachedAvoidTarget(true)) doMove(true);
                else                           endAvoid();
                break;
            }
            case TURN_LEFT: {
                double goal = normA(savedH - 0.5 * Math.PI);
                if (!isFacing(goal)) { stepTurnTo(goal); return; }
                if (!reachedAvoidTarget(true)) doMove(true);
                else                           endAvoid();
                break;
            }
            case BACK:
                if (!reachedAvoidTarget(false)) doMove(false);
                else                            endAvoid();
                break;
            default:
                break;
        }
    }

    private void endAvoid() {
        state       = (resumeState != null) ? resumeState : S.HOLDING;
        resumeState = null;
    }

    protected boolean reachedAvoidTarget(boolean fwd) {
        double h  = getHeading();
        double ch = Math.cos(h), sh = Math.sin(h);
        boolean rx = (Math.abs(ch) < 0.01) ? true
            : ch > 0 ? (fwd ? myX >= avoidTX : myX <= avoidTX)
                     : (fwd ? myX <= avoidTX : myX >= avoidTX);
        boolean ry = (Math.abs(sh) < 0.01) ? true
            : sh > 0 ? (fwd ? myY >= avoidTY : myY <= avoidTY)
                     : (fwd ? myY <= avoidTY : myY >= avoidTY);
        return rx && ry;
    }

    /**
     * Returns true if a circle at (cx, cy) with radius r lies in the forward
     * cone (width = 2*BOT_R, depth = 4*BOT_R) along `heading`.
     */
    protected boolean pathBlocked(double cx, double cy, double r, double heading) {
        double dirX = Math.cos(heading), dirY = Math.sin(heading);
        double dx   = cx - myX,          dy   = cy - myY;
        double along = dx * dirX + dy * dirY;
        double perp  = Math.abs(dx * (-dirY) + dy * dirX);
        return along > 0.0 && along < BOT_R * 4.0 && perp < BOT_R + r;
    }

    protected boolean isAvoiding() {
        return state == S.TURN_LEFT || state == S.TURN_RIGHT || state == S.BACK;
    }

    // =========================================================================
    //  Firing helpers
    // =========================================================================

    /**
     * Compute the lead angle for `e` using Newton-Raphson iterative intercept.
     * Clamps the predicted position inside the arena.
     */
    protected double computeLeadAngle(TrackedEnemy e) {
        double[] p  = e.intercept(myX, myY);
        double   px = Math.max(BOT_R, Math.min(MAP_W - BOT_R, p[0]));
        double   py = Math.max(BOT_R, Math.min(MAP_H - BOT_R, p[1]));
        return Math.atan2(py - myY, px - myX);
    }

    /**
     * Returns true if firing along `angle` will not intersect any live ally's
     * body (circle of radius BOT_R + BULLET_R around each ally's centre).
     */
    protected boolean isSafeFire(double angle) {
        double ex = myX + Math.cos(angle) * Parameters.bulletRange;
        double ey = myY + Math.sin(angle) * Parameters.bulletRange;
        for (BotInfo b : allies.values()) {
            if (!b.alive) continue;
            if (Math.hypot(b.x - myX, b.y - myY) < 8.0) continue; // skip self
            if (segHitsCircle(myX, myY, ex, ey, b.x, b.y, BOT_R + BULLET_R)) return false;
        }
        return true;
    }

    /**
     * Segment–circle intersection test.
     * Uses perpendicular distance from segment to circle centre.
     */
    protected boolean segHitsCircle(double ax, double ay, double bx, double by,
                                     double cx, double cy, double r) {
        double dx  = bx - ax, dy = by - ay;
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return false;
        double ux = dx / len, uy = dy / len;
        double vx = cx - ax, vy = cy - ay;
        double proj = vx * ux + vy * uy;
        double perp = Math.abs(vx * uy - vy * ux);
        return proj > -r && proj < len + r && perp < r;
    }

    /**
     * Fire at `tgt` if cooldown allows and the firing line is safe.
     * Returns true if a shot was fired.
     */
    protected boolean tryFire(TrackedEnemy tgt) {
        if (tgt == null || fireCooldown > 0) return false;
        double angle = computeLeadAngle(tgt);
        if (isSafeFire(angle)) {
            fire(angle);
            fireCooldown = Parameters.bulletFiringLatency;
            return true;
        }
        return false;
    }

    /**
     * Target selection using Lanchester focus-fire principle:
     *   1. If a FOCUS hint was received this step, try to match it to a known enemy.
     *   2. Otherwise pick the closest enemy (minimises time-to-kill under Lanchester).
     */
    protected TrackedEnemy chooseTarget() {
        if (enemies.isEmpty()) return null;

        // Honour focus hint from ally broadcast
        if (!Double.isNaN(focusX)) {
            for (TrackedEnemy e : enemies) {
                if (Math.hypot(e.x - focusX, e.y - focusY) < 100.0) {
                    focusX = Double.NaN; focusY = Double.NaN;
                    return e;
                }
            }
            focusX = Double.NaN; focusY = Double.NaN;
        }

        // Fallback: closest enemy
        TrackedEnemy best = null;
        double       minD = Double.MAX_VALUE;
        for (TrackedEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            if (d < minD) { minD = d; best = e; }
        }
        return best;
    }

    // =========================================================================
    //  Perception
    // =========================================================================

    /**
     * Full radar sweep:
     *  – obstacle avoidance triggering (when not already avoiding)
     *  – enemy / wreck detection + broadcast
     */
    protected void scanRadar() {
        for (IRadarResult o : detectRadar()) {
            double ox = myX + o.getObjectDistance() * Math.cos(o.getObjectDirection());
            double oy = myY + o.getObjectDistance() * Math.sin(o.getObjectDirection());

            // Obstacle avoidance: check forward path only when not already in avoidance
            if (!isAvoiding() && o.getObjectType() != Types.BULLET) {
                double r = o.getObjectRadius() + BOT_R + 6.0;
                if (pathBlocked(ox, oy, r, getHeading())) {
                    initiateAvoid(state);
                }
            }

            switch (o.getObjectType()) {
                case OpponentMainBot:
                case OpponentSecondaryBot: {
                    double d = Math.hypot(ox - myX, oy - myY);
                    broadcast("ENEMY " + o.getObjectDirection() + " " + d
                              + " " + o.getObjectType() + " " + ox + " " + oy);
                    addOrUpdateEnemy(ox, oy, d, o.getObjectDirection(), o.getObjectType());
                    break;
                }
                case Wreck:
                    broadcast("WRECK " + ox + " " + oy);
                    addWreck(ox, oy);
                    break;
                default:
                    break;
            }
        }
    }

    /** Parse all pending broadcast messages from teammates. */
    protected void readMessages() {
        for (String msg : fetchAllMessages()) {
            String[] p = msg.split(" ");
            if (p.length < 2) continue;
            switch (p[0]) {
                case "POS":
                    if (p.length >= 5) {
                        BotInfo b = allies.get(p[1]);
                        if (b != null)
                            b.update(Double.parseDouble(p[2]),
                                     Double.parseDouble(p[3]),
                                     Double.parseDouble(p[4]));
                    }
                    break;
                case "DEAD": {
                    BotInfo b = allies.get(p[1]);
                    if (b != null) b.alive = false;
                    break;
                }
                case "ENEMY":
                    if (p.length >= 6) {
                        Types t = p[3].contains("Main") ? Types.OpponentMainBot
                                                        : Types.OpponentSecondaryBot;
                        addOrUpdateEnemy(Double.parseDouble(p[4]),
                                         Double.parseDouble(p[5]),
                                         Double.parseDouble(p[2]),
                                         Double.parseDouble(p[1]), t);
                    }
                    break;
                case "WRECK":
                    if (p.length >= 3)
                        addWreck(Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                    break;
                case "FOCUS":
                    if (p.length >= 3) {
                        focusX = Double.parseDouble(p[1]);
                        focusY = Double.parseDouble(p[2]);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /** Increment staleness counters; remove enemies not seen for STALE_TTL steps. */
    protected void ageEnemies() {
        Iterator<TrackedEnemy> it = enemies.iterator();
        while (it.hasNext()) {
            TrackedEnemy e = it.next();
            e.stale++;
            if (e.stale > STALE_TTL) it.remove();
        }
    }

    protected void addOrUpdateEnemy(double x, double y, double d, double dir, Types type) {
        // Ignore if position matches a known wreck
        for (double[] w : wrecks)
            if (Math.hypot(x - w[0], y - w[1]) < 60.0) return;

        // Update existing entry if close enough
        for (TrackedEnemy e : enemies) {
            if (Math.hypot(x - e.x, y - e.y) < 80.0) { e.update(x, y, d, dir); return; }
        }
        enemies.add(new TrackedEnemy(x, y, d, dir, type));
    }

    protected void addWreck(double wx, double wy) {
        for (double[] w : wrecks)
            if (Math.hypot(wx - w[0], wy - w[1]) < 40.0) return;
        wrecks.add(new double[]{wx, wy});
        // Remove enemy record that became a wreck
        enemies.removeIf(e -> Math.hypot(e.x - wx, e.y - wy) < 60.0);
    }

    // =========================================================================
    //  Angle utilities
    // =========================================================================

    /** Normalise angle to [0, 2π). */
    protected double normA(double a) {
        while (a < 0.0)           a += 2.0 * Math.PI;
        while (a >= 2.0 * Math.PI) a -= 2.0 * Math.PI;
        return a;
    }

    /** Shortest angular distance in [0, π]. */
    protected double aDist(double a, double b) {
        double d = Math.abs(normA(a) - normA(b));
        return Math.min(d, 2.0 * Math.PI - d);
    }

    /**
     * Snap angle to nearest cardinal direction:
     *   EAST = 0, SOUTH = π/2, WEST = π, NORTH = 3π/2  (all normalised to [0, 2π))
     */
    protected double snapCardinal(double angle) {
        double na   = normA(angle);
        double best = 0.0;
        double minD = Double.MAX_VALUE;
        for (double c : new double[]{0.0, Math.PI / 2.0, Math.PI, 3.0 * Math.PI / 2.0}) {
            double d = aDist(na, c);
            if (d < minD) { minD = d; best = c; }
        }
        return best;
    }

    protected boolean isSecondary() { return NBOT.equals(id) || SBOT.equals(id); }
}
