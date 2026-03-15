import re

with open("src/algorithms/rl/RLBotBase.java", "r") as f:
    text = f.read()

old_safe = """    protected boolean isFiringLineSafe(double tx, double ty) {
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
            double distSq = (bx - proX) * (bx - proX) + (by - proY) * (by - proY);
            
            if (distSq < (40 * 40)) return false; // 40 distance check (30 radius + margin)
        }
        return true; 
    }"""

new_safe = """    protected boolean isFiringLineSafe(double tx, double ty) {
        double x1 = myPos.getX(), y1 = myPos.getY();
        double x2 = tx, y2 = ty;
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx*dx + dy*dy;
        if (lenSq == 0) return true;

        for (BotState b : allyPos.values()) {
            if (!b.isAlive() || (b.getPosition().getX() == x1 && b.getPosition().getY() == y1)) continue;
            
            double bx = b.getPosition().getX();
            double by = b.getPosition().getY();
            
            // We want to account for the physical radius of the bot and a small margin
            // Simovie hitboxes are somewhat large, so use radius=60 to be safe.
            double t = ((bx - x1) * dx + (by - y1) * dy) / lenSq;
            if (t < 0 || t > 1) continue;
            
            double proX = x1 + t * dx;
            double proY = y1 + t * dy;
            double distSq = (bx - proX) * (bx - proX) + (by - proY) * (by - proY);
            
            if (distSq < (60 * 60)) return false;
        }

        // Also avoid shooting through wrecks, just like MacDuo
        for (double[] w : wreckPositions) {
            double wx = w[0];
            double wy = w[1];
            double t = ((wx - x1) * dx + (wy - y1) * dy) / lenSq;
            if (t < 0 || t > 1) continue;
            
            double proX = x1 + t * dx;
            double proY = y1 + t * dy;
            double distSq = (wx - proX) * (wx - proX) + (wy - proY) * (wy - proY);
            if (distSq < (50 * 50)) return false;
        }
        return true;
    }"""

text = text.replace(old_safe, new_safe)

with open("src/algorithms/rl/RLBotBase.java", "w") as f:
    f.write(text)
