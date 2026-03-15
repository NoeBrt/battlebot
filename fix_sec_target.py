import re

with open("src/algorithms/rl/RLBotSecondary.java", "r") as f:
    text = f.read()

old_loop = """        boolean hasMain = false;
        double hx = 0, hy = 0, count = 0;
        for (BotState b : allyPos.values()) {
            if (b.isAlive() && b.getType() == Types.MAIN_BOT) {
                hasMain = true;
                hx += b.getPosition().getX();
                hy += b.getPosition().getY();
                count++;
            }
        }"""

new_loop = """        boolean hasMain = false;
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
        }"""

text = text.replace(old_loop, new_loop)

# Also fix holdX, holdY scope issue if they were declared inside and we try to use them outside
# But I put them at the top. Let's make sure.

with open("src/algorithms/rl/RLBotSecondary.java", "w") as f:
    f.write(text)
