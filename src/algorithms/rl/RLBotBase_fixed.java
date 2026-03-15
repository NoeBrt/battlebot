package algorithms.rl;

import java.util.*;
import algorithms.external.*;
import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;

public abstract class RLBotBase_fixed extends MacDuoBaseBot {

    protected boolean teamA;
    protected int currentTick = 0;
    protected String focusedTargetKey = null;

    protected static class RLEnemy {
        public double x, y, prevX, prevY, ppX, ppY;
        public double speedX, speedY;
        public double distance, direction;
        public Types type;
        public int updateCount;
        public int lastSeenTick;
        public int estimatedDmg;

        RLEnemy(double x, double y, double d, double dir, Types t, int tick) {
            this.x = prevX = ppX = x;
            this.y = prevY = ppY = y;
            distance = d;
            direction = dir;
            type = t;
            updateCount = 0;
            lastSeenTick = tick;
            estimatedDmg = 0;
        }

        void update(double nx, double ny, double nd, double ndir, int tick) {
            ppX = prevX;
            ppY = prevY;
            prevX = x;
            prevY = y;
            x = nx;
            y = ny;
            distance = nd;
            direction = ndir;
            if (tick > lastSeenTick) updateCount++;
            lastSeenTick = tick;
            if (updateCount >= 2) {
                speedX = x - prevX;
                speedY = y - prevY;
            } else {
                speedX = 0;
                speedY = 0;
            }
        }

        String key() {
            return type.name() + ":" + ((int) Math.round(x / 25.0)) + ":" + ((int) Math.round(y / 25.0));
        }
    }

    protected List<RLEnemy> rlEnemies = new ArrayList<>();

    @Override
    public void activate() {
        this.teamA = (getHeading() == Parameters.EAST);
        this.isTeamA = teamA;
        currentTick = 0;
        focusedTargetKey = null;
        rlEnemies.clear();
        this.myPos = new Position(0, 0);
    }

    @Override
    public void step() {
        currentTick++;
        sendMyPosition();
        detection();
        readMessages();
        pruneStaleEnemies();
    }

    protected void goTo(double x, double y) {
        double angle = Math.atan2(y - myPos.getY(), x - myPos.getX());
        turnTo(angle);
        myMove(true);
    }

    protected void readMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            String[] parts = msg.split(" ");
            if (parts.length < 2) continue;

            String header = parts[0];
            switch (header) {
                case "ENEMY":
                    if (parts.length >= 6) {
                        try {
                            double dir = Double.parseDouble(parts[1]);
                            double dist = Double.parseDouble(parts[2]);
                            Types type = parts[3].contains("MainBot") ? Types.OpponentMainBot : Types.OpponentSecondaryBot;
                            double x = Double.parseDouble(parts[4]);
                            double y = Double.parseDouble(parts[5]);
                            updateRLEnemy(x, y, dist, dir, type);
                        } catch (Exception ignored) {
                        }
                    }
                    break;
                case "POS":
                    if (parts.length >= 5) {
                        String who = parts[1];
                        double x = Double.parseDouble(parts[2]);
                        double y = Double.parseDouble(parts[3]);
                        double h = Double.parseDouble(parts[4]);
                        BotState b = allyPos.get(who);
                        if (b == null) allyPos.put(who, new BotState(x, y, true, who, h));
                        else b.setPosition(x, y, h);
                    }
                    break;
                case "DEAD":
                    BotState b = allyPos.get(parts[1]);
                    if (b != null) b.setAlive(false);
                    break;
                case "WRECK":
                    if (parts.length >= 3) {
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        boolean exists = false;
                        for (double[] w : wreckPositions) {
                            if (Math.abs(w[0] - x) < 20 && Math.abs(w[1] - y) < 20) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) wreckPositions.add(new double[]{x, y});
                    }
                    break;
            }
        }
    }

    protected void pruneStaleEnemies() {
        Iterator<RLEnemy> it = rlEnemies.iterator();
        while (it.hasNext()) {
            RLEnemy e = it.next();
            if (currentTick - e.lastSeenTick > RLConfig.STALE_TTL) it.remove();
        }
    }

    private void updateRLEnemy(double x, double y, double d, double dir, Types type) {
        for (RLEnemy e : rlEnemies) {
            if (e.type == type && Math.hypot(e.x - x, e.y - y) < 60) {
                e.update(x, y, d, dir, currentTick);
                return;
            }
        }
        rlEnemies.add(new RLEnemy(x, y, d, dir, type, currentTick));
    }

    @Override
    protected void detection() {
        for (IRadarResult o : detectRadar()) {
            if (o.getObjectType() == Types.OpponentMainBot || o.getObjectType() == Types.OpponentSecondaryBot) {
                double d = o.getObjectDistance();
                double dir = o.getObjectDirection();
                double ox = myPos.getX() + d * Math.cos(dir);
                double oy = myPos.getY() + d * Math.sin(dir);

                broadcast("ENEMY " + dir + " " + d + " " +
                        (o.getObjectType() == Types.OpponentMainBot ? "MainBot" : "SecondaryBot") + " " + ox + " " + oy);

                updateRLEnemy(ox, oy, d, dir, o.getObjectType());
            } else if (o.getObjectType() == Types.Wreck) {
                double ox = myPos.getX() + o.getObjectDistance() * Math.cos(o.getObjectDirection());
                double oy = myPos.getY() + o.getObjectDistance() * Math.sin(o.getObjectDirection());
                boolean exists = false;
                for (double[] w : wreckPositions) {
                    if (Math.abs(w[0] - ox) < 20 && Math.abs(w[1] - oy) < 20) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) wreckPositions.add(new double[]{ox, oy});
            }
        }
    }

    protected static double normalizeAngle(double a) {
        while (a <= -Math.PI) a += 2.0 * Math.PI;
        while (a > Math.PI) a -= 2.0 * Math.PI;
        return a;
    }

    protected static double angleDiff(double a, double b) {
        return Math.abs(normalizeAngle(a - b));
    }

    protected boolean isNearlySamePosition(double x, double y, double ox, double oy) {
        return Math.hypot(x - ox, y - oy) < 1e-6;
    }

    protected RLEnemy chooseBestTarget() {
        RLEnemy best = null;
        double bestScore = -Double.MAX_VALUE;

        for (RLEnemy e : rlEnemies) {
            int stale = currentTick - e.lastSeenTick;
            if (stale > RLConfig.STALE_TTL) continue;

            double dist = Math.hypot(e.x - myPos.getX(), e.y - myPos.getY());
            double score = 0.0;

            score += (RLConfig.TARGET_PROXIMITY_WEIGHT - dist);
            if (e.type == Types.OpponentSecondaryBot) score += RLConfig.TARGET_TYPE_BONUS;

            double hp = (e.type == Types.OpponentSecondaryBot ? RLConfig.MAX_HEALTH_SEC : RLConfig.MAX_HEALTH_MAIN) - e.estimatedDmg;
            if (hp < 30) score += RLConfig.TARGET_LOWHP_CRITICAL_BONUS;
            else if (hp < 60) score += RLConfig.TARGET_LOWHP_MODERATE_BONUS;

            if (focusedTargetKey != null && focusedTargetKey.equals(e.key())) score += RLConfig.TARGET_FOCUS_BONUS;
            if (isFiringLineSafe(e.x, e.y)) score += RLConfig.TARGET_SAFEFIRE_BONUS;
            score -= stale * RLConfig.TARGET_STALE_PENALTY;

            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }

        focusedTargetKey = (best == null) ? null : best.key();
        return best;
    }

    protected boolean isFiringLineSafe(double tx, double ty) {
        double x1 = myPos.getX();
        double y1 = myPos.getY();
        double x2 = tx;
        double y2 = ty;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return true;

        for (BotState b : allyPos.values()) {
            if (!b.isAlive()) continue;
            double bx = b.getPosition().getX();
            double by = b.getPosition().getY();
            if (isNearlySamePosition(bx, by, x1, y1)) continue;

            double t = ((bx - x1) * dx + (by - y1) * dy) / lenSq;
            if (t < 0 || t > 1) continue;

            double projX = x1 + t * dx;
            double projY = y1 + t * dy;
            double distSq = (bx - projX) * (bx - projX) + (by - projY) * (by - projY);
            if (distSq < 40.0 * 40.0) return false;
        }
        return true;
    }

    protected boolean aimAndMaybeFire(RLEnemy target, double tolerance) {
        if (target == null) return false;

        double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
        if (dist > Parameters.bulletRange) return false;

        double t = dist / Parameters.bulletVelocity;
        double predX = target.x + target.speedX * t;
        double predY = target.y + target.speedY * t;
        double angle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());
        double err = angleDiff(angle, getHeading());

        if (err <= tolerance && isFiringLineSafe(predX, predY)) {
            fire(angle);
            return true;
        }

        turnTo(angle);
        return false;
    }

    protected void potentialFieldMove(double kiteMin, double kiteMax) {
        double fx = 0.0;
        double fy = 0.0;
        double swirlSign = teamA ? 1.0 : -1.0;

        for (RLEnemy e : rlEnemies) {
            double dx = myPos.getX() - e.x;
            double dy = myPos.getY() - e.y;
            double d = Math.hypot(dx, dy);
            if (d < 1e-6) continue;

            double nx = dx / d;
            double ny = dy / d;
            double force = 0.0;

            if (d < kiteMin) force = RLConfig.PF_ENEMY_REPEL_STRENGTH * (kiteMin - d) / 100.0;
            else if (d > kiteMax) force = -RLConfig.PF_ENEMY_ATTRACT_STRENGTH * (d - kiteMax) / 100.0;

            fx += nx * force;
            fy += ny * force;

            double tangential = RLConfig.PF_TANGENTIAL_STRENGTH / Math.max(d, 80.0);
            fx += swirlSign * (-ny) * tangential * 100.0;
            fy += swirlSign * (nx) * tangential * 100.0;
        }

        for (BotState b : allyPos.values()) {
            if (!b.isAlive()) continue;
            double ax = b.getPosition().getX();
            double ay = b.getPosition().getY();
            if (isNearlySamePosition(ax, ay, myPos.getX(), myPos.getY())) continue;

            double dx = myPos.getX() - ax;
            double dy = myPos.getY() - ay;
            double d = Math.hypot(dx, dy);
            if (d < RLConfig.PF_ALLY_REPEL_RANGE && d > 1e-6) {
                double force = RLConfig.PF_ALLY_REPEL_STRENGTH * (RLConfig.PF_ALLY_REPEL_RANGE - d) / RLConfig.PF_ALLY_REPEL_RANGE;
                fx += (dx / d) * force * 100.0;
                fy += (dy / d) * force * 100.0;
            }
        }

        double wallD = RLConfig.WALL_MARGIN;
        if (myPos.getX() < wallD) fx += RLConfig.PF_WALL_STRENGTH * (wallD - myPos.getX());
        if (myPos.getX() > RLConfig.MAP_WIDTH - wallD) fx -= RLConfig.PF_WALL_STRENGTH * (myPos.getX() - (RLConfig.MAP_WIDTH - wallD));
        if (myPos.getY() < wallD) fy += RLConfig.PF_WALL_STRENGTH * (wallD - myPos.getY());
        if (myPos.getY() > RLConfig.MAP_HEIGHT - wallD) fy -= RLConfig.PF_WALL_STRENGTH * (myPos.getY() - (RLConfig.MAP_HEIGHT - wallD));

        for (double[] w : wreckPositions) {
            double dx = myPos.getX() - w[0];
            double dy = myPos.getY() - w[1];
            double d = Math.hypot(dx, dy);
            if (d < RLConfig.PF_WRECK_RANGE && d > 1e-6) {
                double force = RLConfig.PF_WALL_STRENGTH * (RLConfig.PF_WRECK_RANGE - d) / RLConfig.PF_WRECK_RANGE;
                fx += (dx / d) * force * 100.0;
                fy += (dy / d) * force * 100.0;
            }
        }

        double angle = Math.atan2(fy, fx);
        if (Double.isNaN(angle)) angle = getHeading();
        turnTo(angle);
        myMove(true);
    }
}
