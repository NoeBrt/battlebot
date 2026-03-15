import json
with open("rl_results/run_20260315_014625/latest.json") as f:
    d = json.load(f)
print("Generation:", d["generation"])
print("Fitness A:", d["fitness_a"])
print("History length:", len(d["history"]))
last_hist = d["history"][-1]
if "winRateA" in last_hist:
    print("WinRate A:", last_hist["winRateA"])
else:
    print("Last hist:", last_hist)

