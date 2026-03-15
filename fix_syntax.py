import re

with open("src/algorithms/rl/RLBotBase.java", "r") as f:
    text = f.read()

# Grab the `potentialFieldMove` and everything after it to clean up the trailing braces
match = re.search(r'(protected void potentialFieldMove\(.*)', text, re.DOTALL)
if match:
    old_body = match.group(1)
    
    # We will just write a completely fresh clean version.
    new_pf = """protected void potentialFieldMove(double kiteMin, double kiteMax) {
        double fx = 0, fy = 0;

        // 1. Enemy Field
        for (RLEnemy e : rlEnemies) {
            double dx = myPos.getX() - e.x;
            double dy = myPos.getY() - e.y;
            double d = Math.hypot(dx, dy);
            if (d == 0) continue;

            double force = 0;
            if (d < kiteMin) force = RLConfig.PF_ENEMY_REPEL_STRENGTH * (kiteMin - d) / 100.0;
            else if (d > kiteMax) force = -RLConfig.PF_ENEMY_ATTRACT_STRENGTH * (d - kiteMax) / 100.0;
            
            fx += (dx/d) * force;
            fy += (dy/d) * force;
            
            // Tangential
            fx += -(dy/d) * RLConfig.PF_TANGENTIAL_STRENGTH;
            fy += (dx/d) * RLConfig.PF_TANGENTIAL_STRENGTH;
        }

        // 2. Ally Repel
        for (BotState b : allyPos.values()) {
             if (!b.isAlive() || (b.getPosition().getX() == myPos.getX() && b.getPosition().getY() == myPos.getY())) continue;
             double dx = myPos.getX() - b.getPosition().getX();
             double dy = myPos.getY() - b.getPosition().getY();
             double d = Math.hypot(dx, dy);
             if (d < RLConfig.PF_ALLY_REPEL_RANGE && d > 0) {
                 double force = RLConfig.PF_ALLY_REPEL_STRENGTH * (RLConfig.PF_ALLY_REPEL_RANGE - d);
                 fx += (dx/d) * force;
                 fy += (dy/d) * force;
             }
        }

        // 3. Wall Repel
        double wallD = 200;
        if (myPos.getX() < wallD) fx += RLConfig.PF_WALL_STRENGTH * (wallD - myPos.getX());
        if (myPos.getX() > 3000 - wallD) fx -= RLConfig.PF_WALL_STRENGTH * (myPos.getX() - (3000 - wallD));
        if (myPos.getY() < wallD) fy += RLConfig.PF_WALL_STRENGTH * (wallD - myPos.getY());
        if (myPos.getY() > 2000 - wallD) fy -= RLConfig.PF_WALL_STRENGTH * (myPos.getY() - (2000 - wallD));

        // 4. Wreck Repel
        for (double[] w : wreckPositions) {
            double dx = myPos.getX() - w[0];
            double dy = myPos.getY() - w[1];
            double d = Math.hypot(dx, dy);
            if (d < RLConfig.PF_WRECK_RANGE && d > 0) {
                double force = RLConfig.PF_WALL_STRENGTH * (RLConfig.PF_WRECK_RANGE - d);
                fx += (dx/d) * force;
                fy += (dy/d) * force;
            }
        }

        double moveAngle = Math.atan2(fy, fx);
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
    }
}"""
    text = text.replace(old_body, new_pf)
    with open("src/algorithms/rl/RLBotBase.java", "w") as f:
        f.write(text)
