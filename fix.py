import re

with open("src/algorithms/rl/RLBotBase.java", "r") as f:
    text = f.read()

# Replace potentialFieldMove
old_pf = """    protected void potentialFieldMove(double kiteMin, double kiteMax) {"""
new_pf = """    protected void potentialFieldMove(double kiteMin, double kiteMax, Double focusAngle) {"""
text = text.replace(old_pf, new_pf)

# Replace the movement logic
old_move = """        double angle = Math.atan2(fy, fx);
        if (Double.isNaN(angle)) angle = getHeading(); // Default
        
        turnTo(angle);
        myMove(true);"""

new_move = """        double moveAngle = Math.atan2(fy, fx);
        if (Double.isNaN(moveAngle)) moveAngle = getHeading(); // Default
        
        if (focusAngle != null) {
            // We want to face 'focusAngle'. 
            // Is 'moveAngle' easier to reach moving forwards or backwards while facing focusAngle?
            double forwardDiff = Math.abs(normalize(moveAngle - focusAngle));
            double backwardDiff = Math.abs(normalize(moveAngle - (focusAngle + Math.PI)));
            
            if (backwardDiff < forwardDiff) {
                turnTo(focusAngle);
                myMove(false);
            } else {
                turnTo(focusAngle);
                myMove(true);
            }
        } else {
            // No focus, just move forward towards moveAngle
            turnTo(moveAngle);
            myMove(true);
        }"""
text = text.replace(old_move, new_move)

with open("src/algorithms/rl/RLBotBase.java", "w") as f:
    f.write(text)
