/* ============================================================================
 * RLBotBaseA.java — RL-optimized base (extends MacDuoBaseBot).
 * All strategy constants read from RLConfigA.
 * ============================================================================*/
package algorithms.rla;

import java.util.*;
import algorithms.external.*;
import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;

public abstract class RLBotBaseA extends MacDuoBaseBot {

    protected boolean teamA;
    protected int currentTick = 0;

    // Use a custom enemy wrapper for RL state tracking (stale, damage estimation)
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
            distance = d; direction = dir; type = t;
            updateCount = 0; lastSeenTick = tick; estimatedDmg = 0;
        }

        void update(double nx, double ny, double nd, double ndir, int tick) {
            ppX = prevX; ppY = prevY;
            prevX = x; prevY = y;
            x = nx; y = ny; distance = nd; direction = ndir;
            if (tick > lastSeenTick) updateCount++;
            lastSeenTick = tick;
            if (updateCount >= 2) { speedX = x - prevX; speedY = y - prevY; }
            else { speedX = 0; speedY = 0; }
        }
    }

    protected List<RLEnemy> rlEnemies = new ArrayList<>();

    @Override
    public void activate() {
        // super.activate(); // REMOVED: Brain.activate() is abstract
        
        // Initialize fields
        this.teamA = (getHeading() == Parameters.EAST);
        this.isTeamA = teamA; 
        currentTick = 0;
        rlEnemies.clear();
        this.myPos = new Position(0, 0); // Initialize to avoid NPE. Logic updates this.
        
        // Reset allied positions if needed (kept in map)
        // MacDuoBaseBot constructor initialized them.
    }

    @Override
    public void step() {
        // super.step(); // REMOVED: Brain.step() is abstract
        
        currentTick++;
        detectRadar(); // updates radar results
        detection();   // processes radar results into rlEnemies
        
        // Communication
        broadcastStatus();
        broadcastEnemies();
        readMessages(); // Process incoming
    }
    
    // Broadcast own state
    protected void broadcastStatus() {
        // POS who x y heading
        try {
            broadcast("POS " + whoAmI + " " + myPos.getX() + " " + myPos.getY() + " " + getHeading());
        } catch (Exception e) {}
    }

    // Broadcast detected enemies
    protected void broadcastEnemies() {
        for (RLEnemy e : rlEnemies) {
            // Only broadcast if I actually SAW it this tick (avoid echo chambers)
            if (e.lastSeenTick == currentTick) {
                try {
                     // ENEMY dir dist type x y
                     // We send 0 0 for dir/dist as receiver should recalc from X/Y
                     String typeStr = (e.type == Types.OpponentMainBot) ? "OpponentMainBot" : "OpponentSecondaryBot";
                     broadcast("ENEMY 0 0 " + typeStr + " " + e.x + " " + e.y);
                } catch (Exception ex) {}
            }
        }
    }
    
    // Helper for navigation
    protected void goTo(double x, double y) {
        double angle = Math.atan2(y - myPos.getY(), x - myPos.getX());
        turnTo(angle);
        myMove(true);
    }
    
    // Communication helpers
    protected void readMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            String[] parts = msg.split(" ");
            if (parts.length < 2) continue;
            
            String header = parts[0];
            switch (header) {
                case "ENEMY":
                    // Format: ENEMY dir dist type x y
                    if (parts.length >= 6) {
                        try {
                            double distInMsg = Double.parseDouble(parts[2]); // Not used, recalculated
                            double dirInMsg = Double.parseDouble(parts[1]);  // Not used, recalculated
                            Types type = parts[3].contains("MainBot") ? Types.OpponentMainBot : Types.OpponentSecondaryBot;
                            double x = Double.parseDouble(parts[4]);
                            double y = Double.parseDouble(parts[5]);
                            
                            // Recalculate relative to ME
                            double dx = x - myPos.getX();
                            double dy = y - myPos.getY();
                            double dist = Math.hypot(dx, dy);
                            double dir = Math.atan2(dy, dx) - getHeading(); // Relative angle
                            while (dir > Math.PI) dir -= 2*Math.PI;
                            while (dir < -Math.PI) dir += 2*Math.PI;
                            
                            updateRLEnemy(x, y, dist, dir, type);
                        } catch(Exception e) {}
                    }
                    break;
                case "POS":
                     // POS who x y heading
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
                     // WRECK x y
                     if (parts.length >= 3) {
                         double x = Double.parseDouble(parts[1]);
                         double y = Double.parseDouble(parts[2]);
                         boolean exists = false;
                         for (double[] w : wreckPositions) {
                             if (Math.abs(w[0] - x) < 20 && Math.abs(w[1] - y) < 20) { exists = true; break; }
                         }
                         if (!exists) wreckPositions.add(new double[]{x, y});
                     }
                     break;
            }
        }
    }
    
    private void updateRLEnemy(double x, double y, double d, double dir, Types type) {
        boolean found = false;
        for (RLEnemy e : rlEnemies) {
            if (Math.hypot(e.x - x, e.y - y) < 60) { 
                e.update(x, y, d, dir, currentTick);
                found = true;
                break;
            }
        }
        if (!found) {
            rlEnemies.add(new RLEnemy(x, y, d, dir, type, currentTick));
        }
    }

    @Override
    protected void detection() {
        for (IRadarResult o : detectRadar()) {
            if (o.getObjectType() == Types.OpponentMainBot || o.getObjectType() == Types.OpponentSecondaryBot) {
                double d = o.getObjectDistance();
                double dir = o.getObjectDirection();
                double ox = myPos.getX() + d * Math.cos(dir);
                double oy = myPos.getY() + d * Math.sin(dir);
                
                boolean found = false;
                for (RLEnemy e : rlEnemies) {
                    if (Math.hypot(e.x - ox, e.y - oy) < 60) { 
                        e.update(ox, oy, d, dir, currentTick);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    rlEnemies.add(new RLEnemy(ox, oy, d, dir, o.getObjectType(), currentTick));
                }
            } else if (o.getObjectType() == Types.Wreck) {
                 double ox = myPos.getX() + o.getObjectDistance() * Math.cos(o.getObjectDirection());
                 double oy = myPos.getY() + o.getObjectDistance() * Math.sin(o.getObjectDirection());
                 // Using MacDuoBaseBot wreck storage
                 boolean exists = false;
                 for (double[] w : wreckPositions) {
                     if (Math.abs(w[0] - ox) < 20 && Math.abs(w[1] - oy) < 20) { exists = true; break; }
                 }
                 if (!exists) wreckPositions.add(new double[]{ox, oy});
            }
        }
    }

    protected RLEnemy chooseBestTarget() {
        RLEnemy best = null;
        double maxScore = -Double.MAX_VALUE;

        for (RLEnemy e : rlEnemies) {
            double dist = Math.hypot(e.x - myPos.getX(), e.y - myPos.getY());
            double score = 0;

            score += (RLConfigA.TARGET_PROXIMITY_WEIGHT - dist);
            if (e.type == Types.OpponentSecondaryBot) score += RLConfigA.TARGET_TYPE_BONUS;

            double hp = (e.type == Types.OpponentSecondaryBot ? 100 : 300) - e.estimatedDmg;
            if (hp < 30) score += RLConfigA.TARGET_LOWHP_CRITICAL_BONUS;
            else if (hp < 60) score += RLConfigA.TARGET_LOWHP_MODERATE_BONUS;

            // Safe Fire (simplified check)
            if (isFiringLineSafe(e.x, e.y)) score += RLConfigA.TARGET_SAFEFIRE_BONUS;

            int stale = currentTick - e.lastSeenTick;
            score -= (stale * RLConfigA.TARGET_STALE_PENALTY);

            if (score > maxScore) {
                maxScore = score;
                best = e;
            }
        }
        return best;
    }

    protected boolean isFiringLineSafe(double tx, double ty) {
        double x1 = myPos.getX(), y1 = myPos.getY();
        double x2 = tx, y2 = ty;
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx*dx + dy*dy;
        if (lenSq == 0) return true;

        for (BotState b : allyPos.values()) {
            if (!b.isAlive() || (b.getPosition().getX() == x1 && b.getPosition().getY() == y1)) continue;
            
            double bx = b.getPosition().getX();
            double by = b.getPosition().getY();
            
            // Project point onto line segment (parameter t)
            double t = ((bx - x1) * dx + (by - y1) * dy) / lenSq;
            
            // Check if projection falls within segment [0, 1]
            if (t < 0 || t > 1) continue;
            
            // Distance from point to line
            double proX = x1 + t * dx;
            double proY = y1 + t * dy;
            double distSq = (bx - proX)*(bx - proX) + (by - proY)*(by - proY);
            
            // Check radius (approximate bot radius)
            if (distSq < (40 * 40)) return false; // 40 distance check (30 radius + margin)
        }
        return true; 
    }

    protected void potentialFieldMove(double kiteMin, double kiteMax) {
        double fx = 0, fy = 0;

        // 1. Enemy Field
        for (RLEnemy e : rlEnemies) {
            double dx = myPos.getX() - e.x;
            double dy = myPos.getY() - e.y;
            double d = Math.hypot(dx, dy);
            if (d == 0) continue;

            double force = 0;
            if (d < kiteMin) force = RLConfigA.PF_ENEMY_REPEL_STRENGTH * (kiteMin - d) / 100.0; 
            else if (d > kiteMax) force = -RLConfigA.PF_ENEMY_ATTRACT_STRENGTH * (d - kiteMax) / 100.0;
            
            fx += (dx/d) * force;
            fy += (dy/d) * force;
            
            // Tangential
            fx += -(dy/d) * RLConfigA.PF_TANGENTIAL_STRENGTH;
            fy += (dx/d) * RLConfigA.PF_TANGENTIAL_STRENGTH;
        }

        // 2. Ally Repel
        for (BotState b : allyPos.values()) {
             if (!b.isAlive() || (b.getPosition().getX() == myPos.getX() && b.getPosition().getY() == myPos.getY())) continue;
             double dx = myPos.getX() - b.getPosition().getX();
             double dy = myPos.getY() - b.getPosition().getY();
             double d = Math.hypot(dx, dy);
             if (d < RLConfigA.PF_ALLY_REPEL_RANGE && d > 0) {
                 double force = RLConfigA.PF_ALLY_REPEL_STRENGTH * (RLConfigA.PF_ALLY_REPEL_RANGE - d);
                 fx += (dx/d) * force;
                 fy += (dy/d) * force;
             }
        }

        // 3. Wall Repel
        double wallD = 200;
        if (myPos.getX() < wallD) fx += RLConfigA.PF_WALL_STRENGTH * (wallD - myPos.getX());
        if (myPos.getX() > 3000 - wallD) fx -= RLConfigA.PF_WALL_STRENGTH * (myPos.getX() - (3000 - wallD));
        if (myPos.getY() < wallD) fy += RLConfigA.PF_WALL_STRENGTH * (wallD - myPos.getY());
        if (myPos.getY() > 2000 - wallD) fy -= RLConfigA.PF_WALL_STRENGTH * (myPos.getY() - (2000 - wallD));

        // 4. Wreck Repel
        for (double[] w : wreckPositions) {
            double dx = myPos.getX() - w[0];
            double dy = myPos.getY() - w[1];
            double d = Math.hypot(dx, dy);
            if (d < RLConfigA.PF_WRECK_RANGE && d > 0) {
                double force = RLConfigA.PF_WALL_STRENGTH * (RLConfigA.PF_WRECK_RANGE - d);
                fx += (dx/d) * force;
                fy += (dy/d) * force;
            }
        }

        double angle = Math.atan2(fy, fx);
        if (Double.isNaN(angle)) angle = getHeading(); // Default
        
        turnTo(angle);
        myMove(true);
    }
}
