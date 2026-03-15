import re

# ------------- RLBotBase.java -------------
with open("src/algorithms/rl/RLBotBase.java", "r") as f:
    text_base = f.read()

old_goto = """    protected void goTo(double x, double y) {
        double angle = Math.atan2(y - myPos.getY(), x - myPos.getX());
        turnTo(angle);
        myMove(true);
    }"""

new_goto = """    protected void goTo(double x, double y) {
        double angle = Math.atan2(y - myPos.getY(), x - myPos.getX());
        double forwardDiff = Math.abs(normalize(angle - getHeading()));
        double forwardDiffWrapped = Math.abs(forwardDiff - 2 * Math.PI);
        double minForward = Math.min(forwardDiff, forwardDiffWrapped);
        
        double backwardBodyAngle = normalize(angle - Math.PI);
        double backwardDiff = Math.abs(normalize(backwardBodyAngle - getHeading()));
        double backwardDiffWrapped = Math.abs(backwardDiff - 2 * Math.PI);
        double minBackward = Math.min(backwardDiff, backwardDiffWrapped);

        if (minBackward < minForward) {
            if (!isSameDirection(getHeading(), backwardBodyAngle)) {
                turnTo(backwardBodyAngle);
            } else {
                myMove(false);
            }
        } else {
            if (!isSameDirection(getHeading(), angle)) {
                turnTo(angle);
            } else {
                myMove(true);
            }
        }
    }"""

text_base = text_base.replace(old_goto, new_goto)

# Replace potentialFieldMove
# Match from "protected void potentialFieldMove" to the end of the method
match = re.search(r'protected void potentialFieldMove\(.*?\).*?double moveAngle = Math\.atan2\(fy, fx\);.*?\}', text_base, re.DOTALL)
if match:
    old_pf = match.group(0)
    # the signature might be updated to have focusAngle
    start_idx = old_pf.find("double moveAngle = Math.atan2(fy, fx);")
    prefix = old_pf[:start_idx]
    
    new_pf_body = """double moveAngle = Math.atan2(fy, fx);
        if (Double.isNaN(moveAngle)) moveAngle = getHeading();
        
        double forwardDiff = Math.abs(normalize(moveAngle - getHeading()));
        double forwardDiffWrapped = Math.abs(forwardDiff - 2 * Math.PI);
        double minForward = Math.min(forwardDiff, forwardDiffWrapped);
        
        double backwardBodyAngle = normalize(moveAngle - Math.PI);
        double backwardDiff = Math.abs(normalize(backwardBodyAngle - getHeading()));
        double backwardDiffWrapped = Math.abs(backwardDiff - 2 * Math.PI);
        double minBackward = Math.min(backwardDiff, backwardDiffWrapped);

        if (minBackward < minForward) {
            if (!isSameDirection(getHeading(), backwardBodyAngle)) {
                turnTo(backwardBodyAngle);
            } else {
                myMove(false);
            }
        } else {
            if (!isSameDirection(getHeading(), moveAngle)) {
                turnTo(moveAngle);
            } else {
                myMove(true);
            }
        }
    }"""
    text_base = text_base.replace(old_pf, prefix + new_pf_body)

# In RLBotBase, also make them take NO focusAngle
text_base = re.sub(r'protected void potentialFieldMove\(double kiteMin, double kiteMax, Double focusAngle\)', 'protected void potentialFieldMove(double kiteMin, double kiteMax)', text_base)

with open("src/algorithms/rl/RLBotBase.java", "w") as f:
    f.write(text_base)

# ------------- RLBotMain.java -------------
with open("src/algorithms/rl/RLBotMain.java", "r") as f:
    text_main = f.read()

# Fix step: Remove focusAngle, just fire and return!
step_main_old = r'public void step\(\) \{.*'
step_main_new = """public void step() {
        super.step();

        if (getHealth() <= 0) {
            mainState = S.DEAD;
            return;
        }

        int aliveAllies = 0;
        for (BotState b : allyPos.values()) if (b.isAlive()) aliveAllies++;
        
        if (aliveAllies >= 3 && getHealth() > RLConfig.HEALTH_HIGH_THRESHOLD) {
            kiteMin = RLConfig.KITE_MIN_AGGRO;
            kiteMax = RLConfig.KITE_MAX_AGGRO;
        } else if (aliveAllies <= 2 || getHealth() < RLConfig.HEALTH_LOW_THRESHOLD) {
            kiteMin = RLConfig.KITE_MIN_DEFEN;
            kiteMax = RLConfig.KITE_MAX_DEFEN;
        } else {
            kiteMin = RLConfig.KITE_MIN_NORMAL;
            kiteMax = RLConfig.KITE_MAX_NORMAL;
        }

        boolean hasEnemies = !rlEnemies.isEmpty();
        
        if (mainState != S.DEAD) {
             if (getHealth() < RLConfig.HP_RETREAT_MAIN && !hasEnemies) {
                 mainState = S.RETREATING;
             } else if (hasEnemies) {
                 mainState = (noFireTicks > RLConfig.NOFIRE_REPOSITION_TICKS) ? S.FLANKING : S.FIRING;
             } else {
                 mainState = S.ADVANCING;
             }
        }

        Double fireAngle = null;
        RLEnemy target = chooseBestTarget();
        if (target != null) {
            double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
            if (dist <= Parameters.bulletRange) {
                double t = dist / Parameters.bulletVelocity;
                double predX = target.x + target.speedX * t;
                double predY = target.y + target.speedY * t;
                double tempAngle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());

                if (isFiringLineSafe(predX, predY)) {
                    fireAngle = tempAngle;
                }
            }
        }

        // 1 action per tick! Prioritize firing if possible
        if (fireAngle != null && noFireTicks > 2) {
            fire(fireAngle);
            noFireTicks = 0;
            return;
        } else {
            noFireTicks++;
        }

        // Otherwise move
        switch (mainState) {
            case ADVANCING:
                goTo(holdX, holdY);
                break;
            case FIRING:
                potentialFieldMove(kiteMin, kiteMax);
                break;
            case FLANKING:
                double flankY = myPos.getY() + (MAIN1.equals(whoAmI) ? -RLConfig.FLANK_OFFSET : RLConfig.FLANK_OFFSET);
                if (flankY < RLConfig.WALL_MARGIN) flankY = RLConfig.WALL_MARGIN;
                if (flankY > RLConfig.MAP_HEIGHT - RLConfig.WALL_MARGIN) flankY = RLConfig.MAP_HEIGHT - RLConfig.WALL_MARGIN;
                goTo(myPos.getX(), flankY);
                break;
            case RETREATING:
                goTo(teamA ? 300 : 2700, holdY);
                break;
            case DEAD: break;
        }
    }
}
"""

text_main = re.sub(step_main_old, step_main_new, text_main, flags=re.DOTALL)
with open("src/algorithms/rl/RLBotMain.java", "w") as f:
    f.write(text_main)

# ------------- RLBotSecondary.java -------------
with open("src/algorithms/rl/RLBotSecondary.java", "r") as f:
    text_sec = f.read()

step_sec_old = r'public void step\(\) \{.*'
step_sec_new = """public void step() {
        super.step();

        if (getHealth() <= 0) {
            secState = S.DEAD;
            return;
        }

        Double fireAngle = null;
        RLEnemy target = chooseBestTarget();
        if (target != null) {
             double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
             if (dist <= Parameters.bulletRange) {
                 double t = dist / Parameters.bulletVelocity;
                 double predX = target.x + target.speedX * t;
                 double predY = target.y + target.speedY * t;
                 double tempAngle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());

                 if (isFiringLineSafe(predX, predY)) {
                     fireAngle = tempAngle;
                 }
             }
        }
        
        if (fireAngle != null) {
            fire(fireAngle);
            return;
        }

        boolean hasMain = false;
        double hx = 0, hy = 0, count = 0;
        for (java.util.Map.Entry<String, BotState> entry : allyPos.entrySet()) {
            String name = entry.getKey();
            BotState b = entry.getValue();
            if (b.isAlive() && name.startsWith("MAIN")) {
                hasMain = true;
                hx += b.getPosition().getX();
                hy += b.getPosition().getY();
                count++;
            }
        }
        if (count > 0) { holdX = hx/count; holdY = hy/count; }

        if (secState != S.DEAD) {
            if (getHealth() < RLConfig.MAX_HEALTH_SEC * RLConfig.HP_RETREAT_PCT_SEC && rlEnemies.isEmpty()) {
                secState = S.RETREAT;
            } else if (target != null && target.distance < 800) {
                secState = S.HUNT;
            } else if (hasMain) {
                secState = S.FOLLOW;
            } else {
                secState = S.PATROL;
            }
        }

        switch (secState) {
             case FOLLOW:
                 double tx = holdX + (teamA ? -RLConfig.FLANK_OFFSET : RLConfig.FLANK_OFFSET);
                 goTo(tx, holdY);
                 break;
             case HUNT:
                 if (target != null) {
                     potentialFieldMove(RLConfig.PATROL_EVASION_RANGE, RLConfig.PATROL_EVASION_RANGE * 2.5);
                 }
                 break;
             case PATROL:
                 double targetX = teamA ? RLConfig.ADVANCE_X_A : RLConfig.MAP_WIDTH - RLConfig.ADVANCE_X_A;
                 double targetY;
                 
                 if (getHealth() > 0) {
                     targetY = holdY > RLConfig.MAP_CY ? RLConfig.MAP_HEIGHT - RLConfig.FLANK_Y_OFFSET : RLConfig.FLANK_Y_OFFSET;
                 } else {
                     targetY = RLConfig.MAP_HEIGHT - RLConfig.FLANK_Y_OFFSET;
                 }
                 
                 double d = Math.hypot(targetX - myPos.getX(), targetY - myPos.getY());
                 if (d < RLConfig.PATROL_THRESHOLD) {
                     potentialFieldMove(RLConfig.PATROL_EVASION_RANGE, RLConfig.PATROL_EVASION_RANGE * 2.0);
                 } else {
                     goTo(targetX, targetY);
                 }
                 break;
             case RETREAT:
                 goTo(teamA ? 300 : 2700, holdY);
                 break;
             case DEAD:
                 break;
        }
    }
}
"""

text_sec = re.sub(step_sec_old, step_sec_new, text_sec, flags=re.DOTALL)
with open("src/algorithms/rl/RLBotSecondary.java", "w") as f:
    f.write(text_sec)
