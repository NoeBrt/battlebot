import re

def fix_file(filepath):
    with open(filepath, "r") as f:
        text = f.read()
    
    text = re.sub(r'potentialFieldMove\(([^,]+?),\s*([^,)]+?)\)', r'potentialFieldMove(\1, \2, focusAngle)', text)
    
    with open(filepath, "w") as f:
        f.write(text)

fix_file("src/algorithms/rl/RLBotMain.java")
fix_file("src/algorithms/rl/RLBotSecondary.java")
