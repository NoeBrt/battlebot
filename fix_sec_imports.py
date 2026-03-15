import re

with open("src/algorithms/rl/RLBotSecondary.java", "r") as f:
    text = f.read()

imports = """import characteristics.IRadarResult;
import characteristics.Parameters;
import algorithms.external.BotState;
import characteristics.IRadarResult.Types;
"""

text = re.sub(r'import characteristics\.IRadarResult;\nimport characteristics\.Parameters;\n', imports, text)

# add holdX and holdY to class vars
text = text.replace("private S secState;", "private S secState;\n    private double holdX = 1500, holdY = 1000;")

with open("src/algorithms/rl/RLBotSecondary.java", "w") as f:
    f.write(text)
