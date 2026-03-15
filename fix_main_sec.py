import re

# ------------- RLBotMain.java -------------
with open("src/algorithms/rl/RLBotMain.java", "r") as f:
    text_main = f.read()

# Replace step() logic
old_step = """        // Act based on State
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
                double retreatX = teamA ? 300 : 2700;
                goTo(retreatX, holdY);
                break;
            case DEAD: break;
        }

        // Firing Logic
        RLEnemy target = chooseBestTarget();
        if (target != null) {
            double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
            if (dist <= Parameters.bulletRange) {
                // Simple prediction
                double t = dist / Parameters.bulletVelocity;
                double predX = target.x + target.speedX * t;
                double predY = target.y + target.speedY * t;
                double angle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());
                
                if (Math.abs(angle - getHeading()) < 0.15) { // Roughly facing
                    fire(angle);
                    noFireTicks = 0;
                } else {
                    noFireTicks++;
                    // Note: potentialFieldMove might turn us away. 
                    // If prioritizing fire, we might want to turnTo(angle) here.
                    // But PF usually handles orientation.
                }
            }
        } else {
            noFireTicks = 0;
        }"""

new_step = """        // Firing Logic & Intent
        Double focusAngle = null;
        RLEnemy target = chooseBestTarget();
        if (target != null) {
            double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
            if (dist <= Parameters.bulletRange) {
                // Simple prediction
                double t = dist / Parameters.bulletVelocity;
                double predX = target.x + target.speedX * t;
                double predY = target.y + target.speedY * t;
                double tempAngle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());

                if (isFiringLineSafe(predX, predY)) {
                    focusAngle = tempAngle;
                }
            }
        }

        // Act based on State
        switch (mainState) {
            case ADVANCING:
                goTo(holdX, holdY);
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
                double retreatX = teamA ? 300 : 2700;
                goTo(retreatX, holdY);
                break;
            case DEAD: break;
        }

        if (focusAngle != null) {
            double diff = Math.abs(normalize(focusAngle - getHeading()));
            if (diff < 0.20) { // Roughly facing 
                fire(focusAngle);
                noFireTicks = 0;
            } else {
                noFireTicks++;
            }
        } else {
            noFireTicks = 0;
        }"""

text_main = text_main.replace(old_step, new_step)
with open("src/algorithms/rl/RLBotMain.java", "w") as f:
    f.write(text_main)

# ------------- RLBotSecondary.java -------------
with open("src/algorithms/rl/RLBotSecondary.java", "r") as f:
    text_sec = f.read()

old_sec_step = """        switch (secState) {
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
                 
                 // Use SBOT vs NBOT identity if possible, else default to Y fallback
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

        // Action: Fire
        if (target != null) {
             double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
             if (dist <= Parameters.bulletRange) {
                 double t = dist / Parameters.bulletVelocity;
                 double predX = target.x + target.speedX * t;
                 double predY = target.y + target.speedY * t;
                 double angle = Math.atan2(predY - myPos.getY(), predX - myPos.getX());

                 if (Math.abs(angle - getHeading()) < 0.2) fire(angle);
             }
        }"""

new_sec_step = """        Double focusAngle = null;
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
                 
                 // Use SBOT vs NBOT identity if possible, else default to Y fallback
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

        // Action: Fire
        if (focusAngle != null) {
            double diff = Math.abs(normalize(focusAngle - getHeading()));
            if (diff < 0.20) {
                fire(focusAngle);
            }
        }"""

text_sec = text_sec.replace(old_sec_step, new_sec_step)
with open("src/algorithms/rl/RLBotSecondary.java", "w") as f:
    f.write(text_sec)
