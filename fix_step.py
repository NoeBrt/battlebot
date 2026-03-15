import re

def replace_step(filepath, new_step_code):
    with open(filepath, "r") as f:
        text = f.read()
    
    # We will find the entire public void step() block and replace it
    match = re.search(r'public void step\(\) \{.*', text, re.DOTALL)
    if match:
        prefix = text[:match.start()]
        with open(filepath, "w") as f:
            f.write(prefix + new_step_code)

main_step = """public void step() {
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

        Double focusAngle = null;
        RLEnemy target = chooseBestTarget();
        if (target != null) {
            double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
            if (dist <= Parameters.bulletRange) {
                double t = dist / Parameters.bulletVelocity;
                double predX = target.x + target.speedX * t;
                double predY = target.y + target.speedY * t;
                double tempAngle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());

                if (isFiringLineSafe(predX, predY)) {
                    focusAngle = tempAngle;
                }
            }
        }

        switch (mainState) {
            case ADVANCING:
                goTo(holdX, holdY); // Doesn't reverse, normal move
                break;
            case FIRING:
                potentialFieldMove(kiteMin, kiteMax, focusAngle);
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

        if (focusAngle != null) {
            double diff = Math.abs(normalize(focusAngle - getHeading()));
            if (diff < 0.20) {
                fire(focusAngle);
                noFireTicks = 0;
            } else {
                noFireTicks++;
            }
        } else {
            noFireTicks = 0;
        }
    }
}
"""

sec_step = """public void step() {
        super.step();

        if (getHealth() <= 0) {
            secState = S.DEAD;
            return;
        }

        RLEnemy target = chooseBestTarget();
        Double focusAngle = null;
        if (target != null) {
             double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
             if (dist <= Parameters.bulletRange) {
                 double t = dist / Parameters.bulletVelocity;
                 double predX = target.x + target.speedX * t;
                 double predY = target.y + target.speedY * t;
                 double tempAngle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());

                 if (isFiringLineSafe(predX, predY)) {
                     focusAngle = tempAngle;
                 }
             }
        }

        boolean hasMain = false;
        double hx = 0, hy = 0, count = 0;
        for (BotState b : allyPos.values()) {
            if (b.isAlive() && b.getType() == Types.MAIN_BOT) {
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
                     potentialFieldMove(RLConfig.PATROL_EVASION_RANGE, RLConfig.PATROL_EVASION_RANGE * 2.5, focusAngle);
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
                     potentialFieldMove(RLConfig.PATROL_EVASION_RANGE, RLConfig.PATROL_EVASION_RANGE * 2.0, focusAngle);
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

        if (focusAngle != null) {
            double diff = Math.abs(normalize(focusAngle - getHeading()));
            if (diff < 0.20) {
                fire(focusAngle);
            }
        }
    }
}
"""

replace_step("src/algorithms/rl/RLBotMain.java", main_step)
replace_step("src/algorithms/rl/RLBotSecondary.java", sec_step)

