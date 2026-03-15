import re

with open("src/algorithms/rl/RLBotSecondary.java", "r") as f:
    text = f.read()

text = text.replace("private enum S { SUPPORT, FLANKING, PATROL, RETREATING, DEAD }", "private enum S { FOLLOW, HUNT, PATROL, RETREAT, DEAD }")

with open("src/algorithms/rl/RLBotSecondary.java", "w") as f:
    f.write(text)
