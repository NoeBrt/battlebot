# Stratégie MARL - Multi-Agent Robocode

## Architecture de communication

Chaque robot dispose de :
- **send log message** — envoyer un message de log
- **broadcast** — diffuser un message à tous les alliés
- **fetch all messages** — récupérer tous les messages reçus
- **start radar()** — lancer le scan radar
- **fire(→)** — tirer sur la cible
- **detect radar()** — détecter les ennemis via le radar

---

## Rôles

### Main (leader)
- **Focus-fire(x, y)** — cible un ennemi spécifique et concentre le tir dessus
- Target la même cible ("focus") en utilisant le radar ou les infos des secondaires
- Gère les priorités de ciblage (Se → Si)
- Fait des tours de terrain par scanning

### Secondary (support)
- **Orbit(x, y)** — orbite autour du main ou d'un point donné
- **Move-forward** — avance vers l'ennemi si nécessaire
- ~~dying_retreat~~ *possible, se dirige vers un endroit utile pour le radar*
- Envoie la position des ennemis au main

---

## Stratégies envisagées

### Strat 1 — Encerclement
1. Les **secondaires** contournent les ennemis secondaires
2. Se cacher derriere un enemy secondaire

### Strat 2 — Orbite protectrice
1. Les **secondaires** orbitent autour d'un **main**
2. Le main focus-fire pendant que les secondaires fournissent les positions ennemies

---

## Résumé visuel

```
     [Main]              [Secondary]
  focus-fire(x,y)  ←→   orbit(x,y)
                         move-forward
```
