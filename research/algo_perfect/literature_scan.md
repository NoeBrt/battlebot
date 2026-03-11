# Literature scan (anti-MacDuo focus) — 2026-03-11

Objectif: extraire des principes état de l’art applicables immédiatement au duel battlebot vs MacDuo.

## Sources rapides consultées

### GitHub (implémentations / inspirations)
- https://github.com/parthnan/AntiGrav-WavesurfingBot (anti-gravity / wavesurfing Robocode)
- https://github.com/MBaranPeker/Pursuit-Evasion-Game-with-Deep-Reinforcement-Learning-in-an-environment-with-an-obstacle (pursuit-evasion)
- https://github.com/Winnie-Qi/Multi-Agents-Motion-Planning-and-Collision-Avoidance (ORCA/RVO-like ideas)
- https://github.com/czw0aaron/MUCTAGA_UAVs-Target-Assignment_xiashun (cooperative target assignment)

### Scholar-like
- API Semantic Scholar momentanément rate-limited (HTTP 429), à reprendre sur prochaine fenêtre.

## Principes SOTA retenus (actionnables)

1. **Target assignment explicite (pas nearest-only)**
   - Répartir les 3 mains en 2+1: deux bots finissent la cible primaire, un bot coupe/zone la secondaire.
   - Prioriser kill confirm (TTK minimal) plutôt que DPS dilué.

2. **Découplage mouvement / tir**
   - Boucle de tir à chaque tick, indépendante de l’état locomotion.
   - Pendant turn step, conserver tracking & trigger si safe-fire.

3. **Anti-gravity spacing**
   - Potentiel répulsif alliés + murs + épaves pour réduire blocages internes.
   - Potentiel attractif modéré vers kill zone ennemie.

4. **Pursuit-evasion adaptative**
   - Si avantage numérique: pression raccourcie (range max ↓).
   - Si désavantage/low HP: kite (range min ↑, trajectoire tangentielle).

5. **Scout sacrificiel orienté information**
   - 1 secondary passe en mode info-first (spot, broadcast fréquent), même au coût de survie.
   - Les mains exploitent cette info pour tirs indirects synchronisés.

## Design AntiMacDuo v2 (prochain commit)

- Main: `AntiMacDuoV2Main`
  - target assignment 2+1 sur secondaire prioritaire
  - séparation fire pipeline / movement pipeline
  - anti-gravity local pour garder les lignes de tir
- Secondary: `AntiMacDuoV2Secondary`
  - `NBOT` scout agressif info-first
  - `SBOT` harass tangent + bait
- Messages:
  - `PRIO <x> <y> <type> <ttl>`
  - `SCOUT <x> <y>`
  - `COLLAPSE <x> <y>`

## Critère de succès

- Benchmark MacDuo-only (AB+BA, 3+3, timeout 30000)
- Objectif minimal v2: passer de 0/6 à >=2/6 avec gain sur avgScore candidate.
