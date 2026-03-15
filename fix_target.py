import re

with open("src/algorithms/rl/RLBotBase.java", "r") as f:
    text = f.read()

# Add lastTarget tracker
class_sig = """    protected List<RLEnemy> rlEnemies = new ArrayList<>();"""
class_sig_new = """    protected List<RLEnemy> rlEnemies = new ArrayList<>();
    protected RLEnemy lastTarget = null;"""
text = text.replace(class_sig, class_sig_new)

old_target = """    protected RLEnemy chooseBestTarget() {
        RLEnemy best = null;
        double maxScore = -999999;
        
        for (RLEnemy e : rlEnemies) {
            double score = 0;
            score += RLConfig.TARGET_PROXIMITY_WEIGHT / Math.max(100.0, e.distance);
            if (e.type == Types.MAIN_BOT) score += RLConfig.TARGET_TYPE_BONUS;
            
            // Note: RL doesn't know exact HP without team communication, 
            // but we can estimate based on observed hits or default logic.
            // Using placeholder logic:
            if (e.estimatedDmg > 200) score += RLConfig.TARGET_LOWHP_CRITICAL_BONUS;
            else if (e.estimatedDmg > 100) score += RLConfig.TARGET_LOWHP_MODERATE_BONUS; 
            
            if (isFiringLineSafe(e.x, e.y)) score += RLConfig.TARGET_SAFEFIRE_BONUS;
            
            int stale = currentTick - e.lastSeenTick;
            score -= (stale * RLConfig.TARGET_STALE_PENALTY);
            
            if (score > maxScore) {
                maxScore = score;
                best = e;
            }
        }
        return best;
    }"""

new_target = """    protected RLEnemy chooseBestTarget() {
        RLEnemy best = null;
        double maxScore = -999999;
        
        for (RLEnemy e : rlEnemies) {
            double score = 0;
            score += RLConfig.TARGET_PROXIMITY_WEIGHT / Math.max(100.0, e.distance);
            if (e.type == Types.MAIN_BOT) score += RLConfig.TARGET_TYPE_BONUS;
            
            // Stickiness Bonus: Don't bounce targets unnecessarily
            if (lastTarget != null && lastTarget.x == e.x && lastTarget.y == e.y) {
                score += RLConfig.TARGET_FOCUS_BONUS;
            }
            
            if (e.estimatedDmg > 200) score += RLConfig.TARGET_LOWHP_CRITICAL_BONUS;
            else if (e.estimatedDmg > 100) score += RLConfig.TARGET_LOWHP_MODERATE_BONUS; 
            
            // Firing line safety is now a hard requirement, but we still score it
            if (isFiringLineSafe(e.x, e.y)) score += RLConfig.TARGET_SAFEFIRE_BONUS;
            
            int stale = currentTick - e.lastSeenTick;
            score -= (stale * RLConfig.TARGET_STALE_PENALTY);
            
            if (score > maxScore) {
                maxScore = score;
                best = e;
            }
        }
        if (best != null) {
            lastTarget = best;
        }
        return best;
    }"""

text = text.replace(old_target, new_target)

with open("src/algorithms/rl/RLBotBase.java", "w") as f:
    f.write(text)
