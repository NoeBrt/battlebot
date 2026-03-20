package algorithms.tbot;

import java.util.*;
import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;
import robotsimulator.Brain;

/**
 * TBotBase -- Abstract base for TacticalBot main and secondary bots.
 *
 * Provides: world model (enemies, allies, wrecks), communication protocol,
 * NR-7 lead targeting, segment-circle safe-fire, potential-field movement,
 * obstacle avoidance, and target selection with Lanchester-optimal focus fire.
 */
public abstract class TBotBase extends Brain {

    // ── Bot identity ──────────────────────────────────────────────────────
    protected static final String NBOT = "NBOT";
    protected static final String SBOT = "SBOT";
    protected static final String M1   = "1";
    protected static final String M2   = "2";
    protected static final String M3   = "3";

    // ── Roles ─────────────────────────────────────────────────────────────
    public enum Role { ANCHOR, FLANKER_N, FLANKER_S, SCOUT, ASSASSIN, ESCORT }

    // ── Arena constants ───────────────────────────────────────────────────
    protected static final double MAP_W      = TBotConfig.MAP_WIDTH;
    protected static final double MAP_H      = TBotConfig.MAP_HEIGHT;
    protected static final double BOT_R      = TBotConfig.BOT_R;
    protected static final double BULLET_R   = Parameters.bulletRadius;
    protected static final double ANGLE_PREC = Parameters.teamAMainBotStepTurnAngle * 1.8;

    // ── Avoidance state machine ───────────────────────────────────────────
    protected enum AState { NONE, TURN_RIGHT, TURN_LEFT, BACK }
    protected AState avoidState = AState.NONE;
    protected double savedH;
    protected double avoidTX, avoidTY;

    // ── Instance state ────────────────────────────────────────────────────
    protected String  id;
    protected double  myX, myY;
    protected boolean teamA;
    protected Role    role;
    protected int     tick = 0;

    // ── World model ───────────────────────────────────────────────────────
    protected List<TEnemy>          enemies = new ArrayList<>();
    protected List<double[]>        wrecks  = new ArrayList<>();
    protected Map<String, TAlly>    allies  = new LinkedHashMap<>();

    // ── Firing ────────────────────────────────────────────────────────────
    protected int    fireCooldown = 0;
    protected double focusX = Double.NaN;
    protected double focusY = Double.NaN;
    protected TEnemy lastFiredTarget = null;

    // ── Team stats (computed each tick) ───────────────────────────────────
    protected int    aliveMainCount;
    protected int    aliveSecCount;
    protected int    aliveAllies;
    protected double teamAvgX, teamAvgY;
    protected double teamSpread;
    protected double teamTotalHP;

    // =====================================================================
    //  Inner class: TEnemy — tracked enemy with NR-7 intercept
    // =====================================================================
    protected static class TEnemy {
        double x, y, prevX, prevY, ppX, ppY;
        double speedX, speedY;
        double distance, direction;
        Types  type;
        int    updateCount, stale;
        int    estimatedDmg;

        TEnemy(double x, double y, double d, double dir, Types t) {
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

            if (updateCount >= 3) {
                double ax = speedX - (prevX - ppX);
                double ay = speedY - (prevY - ppY);
                if (Math.abs(ax) < 2.5) vx += ax * TBotConfig.FIRE_LEAD_ACCEL_FACTOR;
                if (Math.abs(ay) < 2.5) vy += ay * TBotConfig.FIRE_LEAD_ACCEL_FACTOR;
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
    //  Inner class: TAlly — ally position + state
    // =====================================================================
    protected static class TAlly {
        final String id;
        double x, y, heading;
        double health;
        boolean alive;
        Role role;

        TAlly(String id) { this.id = id; alive = true; health = -1; }
        void update(double x, double y, double h) {
            this.x = x; this.y = y; heading = h; alive = true;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────
    protected TBotBase() {
        super();
        for (String s : new String[]{NBOT, SBOT, M1, M2, M3})
            allies.put(s, new TAlly(s));
    }

    // =====================================================================
    //  PERCEPTION PIPELINE
    // =====================================================================

    protected void tickPerception() {
        tick++;
        if (fireCooldown > 0) fireCooldown--;
        scanRadar();
        readMessages();
        ageEnemies();
        updateTeamStats();
    }

    protected void scanRadar() {
        for (IRadarResult o : detectRadar()) {
            double ox = myX + o.getObjectDistance() * Math.cos(o.getObjectDirection());
            double oy = myY + o.getObjectDistance() * Math.sin(o.getObjectDirection());

            if (avoidState == AState.NONE && o.getObjectType() != Types.BULLET) {
                double r = o.getObjectRadius() + BOT_R + 6.0;
                if (pathBlocked(ox, oy, r, getHeading()))
                    initiateAvoid();
            }

            switch (o.getObjectType()) {
                case OpponentMainBot:
                case OpponentSecondaryBot: {
                    double d = Math.hypot(ox - myX, oy - myY);
                    broadcast("ENEMY " + o.getObjectDirection() + " " + d
                              + " " + o.getObjectType() + " " + ox + " " + oy);
                    TEnemy e = upsertEnemy(ox, oy, d, o.getObjectDirection(), o.getObjectType());
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
            try {
                switch (p[0]) {
                    case "POS":
                        if (p.length >= 5) {
                            TAlly b = allies.get(p[1]);
                            if (b != null) {
                                b.update(dbl(p[2]), dbl(p[3]), dbl(p[4]));
                                if (p.length >= 6) b.health = dbl(p[5]);
                            }
                        }
                        break;
                    case "DEAD": {
                        TAlly b = allies.get(p[1]);
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
                    case "ROLE":
                        if (p.length >= 3) {
                            TAlly b = allies.get(p[1]);
                            if (b != null) {
                                try { b.role = Role.valueOf(p[2]); } catch (Exception ignored) {}
                            }
                        }
                        break;
                    default: break;
                }
            } catch (Exception ignored) {}
        }
    }

    protected void ageEnemies() {
        Iterator<TEnemy> it = enemies.iterator();
        while (it.hasNext()) {
            TEnemy e = it.next();
            e.stale++;
            if (e.stale > TBotConfig.STALE_TTL) it.remove();
        }
    }

    protected TEnemy upsertEnemy(double x, double y, double d, double dir, Types type) {
        for (double[] w : wrecks)
            if (Math.hypot(x - w[0], y - w[1]) < 60.0) return null;
        for (TEnemy e : enemies)
            if (Math.hypot(x - e.x, y - e.y) < 80.0) { e.update(x, y, d, dir); return e; }
        TEnemy created = new TEnemy(x, y, d, dir, type);
        enemies.add(created);
        return created;
    }

    protected void applyTrack(double x, double y, double vx, double vy, Types type) {
        TEnemy e = upsertEnemy(x, y, Math.hypot(x - myX, y - myY), Math.atan2(y - myY, x - myX), type);
        if (e == null) return;
        e.speedX = vx; e.speedY = vy;
        if (e.updateCount < 2) e.updateCount = 2;
        e.stale = 0;
    }

    protected void applyDmg(double x, double y, int dmg) {
        for (TEnemy e : enemies)
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

    protected void updateTeamStats() {
        aliveMainCount = 0;
        aliveSecCount = 0;
        teamAvgX = myX; teamAvgY = myY;
        teamTotalHP = getHealth();
        int count = 1;

        for (TAlly a : allies.values()) {
            if (!a.alive || a.id.equals(id)) continue;
            boolean isSec = a.id.equals(NBOT) || a.id.equals(SBOT);
            if (isSec) aliveSecCount++; else aliveMainCount++;
            teamAvgX += a.x; teamAvgY += a.y;
            if (a.health > 0) teamTotalHP += a.health;
            count++;
        }
        // Count self
        boolean selfSec = id.equals(NBOT) || id.equals(SBOT);
        if (selfSec) aliveSecCount++; else aliveMainCount++;

        teamAvgX /= count;
        teamAvgY /= count;
        aliveAllies = aliveMainCount + aliveSecCount;

        // Compute spread: max distance from centroid
        teamSpread = 0;
        for (TAlly a : allies.values()) {
            if (!a.alive || a.id.equals(id)) continue;
            double d = Math.hypot(a.x - teamAvgX, a.y - teamAvgY);
            if (d > teamSpread) teamSpread = d;
        }
        double selfDist = Math.hypot(myX - teamAvgX, myY - teamAvgY);
        if (selfDist > teamSpread) teamSpread = selfDist;
    }

    // =====================================================================
    //  COMMUNICATION HELPERS
    // =====================================================================

    protected void broadcastPosition() {
        broadcast("POS " + id + " " + myX + " " + myY + " " + getHeading() + " " + getHealth());
    }

    protected void broadcastDead() {
        broadcast("DEAD " + id);
    }

    protected void broadcastFocus(TEnemy target) {
        if (target != null) {
            broadcast("FOCUS " + (int)target.x + " " + (int)target.y);
            focusX = target.x; focusY = target.y;
        }
    }

    protected void broadcastRole() {
        if (role != null) broadcast("ROLE " + id + " " + role.name());
    }

    // =====================================================================
    //  FIRING ENGINE
    // =====================================================================

    protected double computeLeadAngle(TEnemy e) {
        double[] p = e.intercept(myX, myY);
        return Math.atan2(p[1] - myY, p[0] - myX);
    }

    protected boolean isSafeFire(double angle) {
        double ex = myX + Math.cos(angle) * Parameters.bulletRange;
        double ey = myY + Math.sin(angle) * Parameters.bulletRange;
        for (TAlly b : allies.values()) {
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
     * Multi-angle fire with configurable offset count.
     * Returns true if successfully fired.
     */
    protected boolean tryFire(TEnemy tgt) {
        if (tgt == null || fireCooldown > 0) return false;
        double base = computeLeadAngle(tgt);
        double dist = Math.hypot(tgt.x - myX, tgt.y - myY);
        if (dist > Parameters.bulletRange + 50) return false;

        double maxDev = Math.atan((BOT_R + BULLET_R - 1) / Math.max(dist, 1.0));

        int n = TBotConfig.FIRE_ANGLE_OFFSETS;
        // Build offsets: 0, -0.33, 0.33, -0.67, 0.67, -1.0, 1.0, ...
        for (int i = 0; i < n; i++) {
            double frac;
            if (i == 0) frac = 0;
            else frac = ((i % 2 == 1) ? -1 : 1) * ((i + 1) / 2) / ((n - 1) / 2.0);
            double a = base + maxDev * frac;
            if (isSafeFire(a)) {
                fire(a);
                fireCooldown = Parameters.bulletFiringLatency;
                tgt.estimatedDmg += (int) Parameters.bulletDamage;
                broadcast("DMG " + (int) tgt.x + " " + (int) tgt.y + " " + tgt.estimatedDmg);
                lastFiredTarget = tgt;
                return true;
            }
        }
        return false;
    }

    /**
     * Lanchester-optimal target selection with configurable scoring.
     */
    protected TEnemy chooseTarget() {
        if (enemies.isEmpty()) return null;
        TEnemy best = null;
        double bestScore = -1e9;

        for (TEnemy e : enemies) {
            double d = Math.hypot(e.x - myX, e.y - myY);
            if (d > Parameters.bulletRange + 200) continue;

            double score = TBotConfig.TGT_PROXIMITY_WEIGHT - d;

            if (e.type == Types.OpponentSecondaryBot)
                score += TBotConfig.TGT_TYPE_BONUS_SEC;

            double hp = e.estimatedHP();
            if (hp <= 30)       score += TBotConfig.TGT_LOWHP_CRIT;
            else if (hp <= 100) score += TBotConfig.TGT_LOWHP_MOD;

            if (!Double.isNaN(focusX) && Math.hypot(e.x - focusX, e.y - focusY) < TBotConfig.COORD_FOCUS_RADIUS)
                score += TBotConfig.TGT_FOCUS_BONUS;

            if (e.estimatedDmg > 0) score += TBotConfig.TGT_DMG_COMMITTED * (e.estimatedDmg / 300.0);
            if (lastFiredTarget != null && Math.hypot(e.x - lastFiredTarget.x, e.y - lastFiredTarget.y) < 80)
                score += TBotConfig.TGT_LAST_HIT_BONUS;

            score -= e.stale * TBotConfig.TGT_STALE_PENALTY;

            if (isSafeFire(computeLeadAngle(e))) score += TBotConfig.TGT_SAFEFIRE;

            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    // =====================================================================
    //  MOVEMENT ENGINE
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
            initiateAvoid();
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

    protected void goTo(double tx, double ty) {
        double raw = Math.atan2(ty - myY, tx - myX);
        double snapped = snapCardinal(raw);
        double backAngle = normA(snapped + Math.PI);
        double fwdDiff = aDist(getHeading(), snapped);
        double bwdDiff = aDist(getHeading(), backAngle);

        if (bwdDiff < fwdDiff && bwdDiff < ANGLE_PREC) {
            doMove(false);
        } else if (!isFacing(snapped)) {
            stepTurnTo(snapped);
        } else {
            doMove(true);
        }
    }

    /**
     * Potential-field movement with configurable forces.
     * tangentialSide: 1 = orbit clockwise, -1 = counter-clockwise, 0 = no preference
     */
    protected double potentialFieldAngle(double kiteMin, double kiteMax, double tangentialSide) {
        double fx = 0, fy = 0;

        // 1. Enemy forces
        for (TEnemy e : enemies) {
            double dx = e.x - myX, dy = e.y - myY;
            double dist = Math.hypot(dx, dy);
            if (dist < 1.0) continue;
            double nx = dx / dist, ny = dy / dist;

            if (dist < kiteMin) {
                double s = TBotConfig.PF_ENEMY_REPEL * (kiteMin - dist) / kiteMin;
                fx -= nx * s; fy -= ny * s;
            } else if (dist > kiteMax) {
                double s = TBotConfig.PF_ENEMY_ATTRACT * (dist - kiteMax) / kiteMax;
                fx += nx * s; fy += ny * s;
            } else {
                double side = (tangentialSide != 0) ? tangentialSide :
                              (M1.equals(id) ? 1.0 : (M3.equals(id) ? -1.0 : (teamA ? 1.0 : -1.0)));
                fx += (-ny * side) * TBotConfig.PF_TANGENTIAL;
                fy += ( nx * side) * TBotConfig.PF_TANGENTIAL;
            }
        }

        // 2. Focus target pull
        if (!Double.isNaN(focusX)) {
            double dx = focusX - myX, dy = focusY - myY;
            double dist = Math.hypot(dx, dy);
            if (dist > 1.0 && dist < Parameters.bulletRange) {
                fx += (dx / dist) * TBotConfig.PF_FOCUS_PULL;
                fy += (dy / dist) * TBotConfig.PF_FOCUS_PULL;
            }
        }

        // 3. Formation pull (toward team centroid)
        {
            double dx = teamAvgX - myX, dy = teamAvgY - myY;
            double dist = Math.hypot(dx, dy);
            if (dist > 50.0) {
                fx += (dx / dist) * TBotConfig.PF_FORMATION_PULL;
                fy += (dy / dist) * TBotConfig.PF_FORMATION_PULL;
            }
        }

        // 4. Ally repulsion (anti-clump)
        for (TAlly a : allies.values()) {
            if (!a.alive || a.id.equals(id)) continue;
            double dx = a.x - myX, dy = a.y - myY;
            double dist = Math.hypot(dx, dy);
            if (dist < TBotConfig.PF_ALLY_REPEL_RANGE && dist > 1.0) {
                double s = TBotConfig.PF_ALLY_REPEL_STR * (TBotConfig.PF_ALLY_REPEL_RANGE - dist) / TBotConfig.PF_ALLY_REPEL_RANGE;
                fx -= (dx / dist) * s;
                fy -= (dy / dist) * s;
            }
        }

        // 5. Wall repulsion
        double wf = TBotConfig.PF_WALL_STR;
        double wm = TBotConfig.WALL_MARGIN;
        if (myX < wm)          fx += wf * (wm - myX) / wm;
        if (myX > MAP_W - wm)  fx -= wf * (myX - (MAP_W - wm)) / wm;
        if (myY < wm)          fy += wf * (wm - myY) / wm;
        if (myY > MAP_H - wm)  fy -= wf * (myY - (MAP_H - wm)) / wm;

        // 6. Wreck avoidance
        for (double[] w : wrecks) {
            double dx = w[0] - myX, dy = w[1] - myY;
            double dist = Math.hypot(dx, dy);
            if (dist < TBotConfig.PF_WRECK_RANGE && dist > 1.0) {
                double s = TBotConfig.PF_WRECK_STR * (TBotConfig.PF_WRECK_RANGE - dist) / TBotConfig.PF_WRECK_RANGE;
                fx -= (dx / dist) * s;
                fy -= (dy / dist) * s;
            }
        }

        return (Math.hypot(fx, fy) < 0.01) ? getHeading() : Math.atan2(fy, fx);
    }

    /** Execute potential-field movement: snap to cardinal, decide fwd/bwd. */
    protected void doPotentialFieldMove(double kiteMin, double kiteMax, double tangentialSide) {
        if (avoidState != AState.NONE) {
            doAvoidStep();
            return;
        }
        double angle = potentialFieldAngle(kiteMin, kiteMax, tangentialSide);
        double snapped = snapCardinal(angle);
        double backAngle = normA(snapped + Math.PI);
        double fwdDiff = aDist(getHeading(), snapped);
        double bwdDiff = aDist(getHeading(), backAngle);

        if (bwdDiff < fwdDiff - 0.01) {
            if (isFacing(backAngle)) doMove(false);
            else stepTurnTo(backAngle);
        } else {
            if (isFacing(snapped)) doMove(true);
            else stepTurnTo(snapped);
        }
    }

    // =====================================================================
    //  OBSTACLE AVOIDANCE
    // =====================================================================

    protected void initiateAvoid() {
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
            avoidState = AState.TURN_RIGHT;
            avoidTX = myX + Math.cos(savedH + 0.5 * Math.PI) * ext;
            avoidTY = myY + Math.sin(savedH + 0.5 * Math.PI) * ext;
        } else if (!blockL) {
            avoidState = AState.TURN_LEFT;
            avoidTX = myX + Math.cos(savedH - 0.5 * Math.PI) * ext;
            avoidTY = myY + Math.sin(savedH - 0.5 * Math.PI) * ext;
        } else {
            avoidState = AState.BACK;
            avoidTX = myX - Math.cos(savedH) * ext;
            avoidTY = myY - Math.sin(savedH) * ext;
        }
    }

    protected void doAvoidStep() {
        switch (avoidState) {
            case TURN_RIGHT: {
                double goal = normA(savedH + 0.5 * Math.PI);
                if (!isFacing(goal)) { stepTurnTo(goal); return; }
                if (!reachedAvoid(true)) doMove(true); else avoidState = AState.NONE;
                break;
            }
            case TURN_LEFT: {
                double goal = normA(savedH - 0.5 * Math.PI);
                if (!isFacing(goal)) { stepTurnTo(goal); return; }
                if (!reachedAvoid(true)) doMove(true); else avoidState = AState.NONE;
                break;
            }
            case BACK:
                if (!reachedAvoid(false)) doMove(false); else avoidState = AState.NONE;
                break;
            default: break;
        }
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

    private static double dbl(String s) { return Double.parseDouble(s); }
}
