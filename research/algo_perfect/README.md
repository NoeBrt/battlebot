# Recherche — Algorithme « parfait » (itératif)

Objectif: centraliser les hypothèses, idées testables, résultats de tournois et prochaines actions pour converger vers une stratégie dominante.

## Axes de recherche

1. **Focus fire optimisé**
   - Verrouillage rapide d’une cible commune
   - Priorité secondaire > main selon contexte
   - Tiebreak sur HP résiduel / distance / accessibilité

2. **Mouvement en arcs (mains)**
   - Orbites tangentielles avec correction radiale
   - Variation par rôle M1/M2/M3 pour éviter la collision de trajectoires
   - Objectif: maintenir fenêtre de tir + réduire prévisibilité

3. **Sacrificial scout (info > survie)**
   - Un bot sacrifié pour spotting continu
   - Broadcast fréquent des ennemis même sous pression
   - Main bots en batterie de tir à distance sur coordonnées relayées

4. **Kiting adaptatif**
   - Passage auto agressif/défensif selon HP et nombre d’alliés vivants
   - Gestion des plages de distance optimales par type de bot

5. **Coordination Main/Secondary**
   - Rôles explicites: ancre, pression, harcèlement, scout
   - Protocoles de message légers mais robustes

## Protocole d’évaluation standard

- Format: candidat vs baseline (AB + BA)
- Volume: 6 matchs/pairing (3+3), puis 12 en confirmation
- Runtime: headless `delay=1`, `timeout=30000`
- Métriques:
  - avgScore candidate / baseline
  - winRate / lossRate / drawRate
  - mean(diff), std(diff)
  - indicateur de mobilité (movedIn200ms ou proxy)

## Baselines de référence

- MacDuo
- OrbitFocus
- AdaptiveKite
- PhalanxFocus
- SecondaryHunter

## Backlog immédiat

- [ ] Évaluer `ArcCircle` complet vs baselines
- [ ] Créer variante `ArcCircleScout` (secondary sacrifié)
- [ ] Ajouter critère de target-switch basé sur HP ennemi + risque de friendly fire
- [ ] Vérifier robustesse en timeout et rounds longs

## Journal d’itération

> Ajouter ici, à chaque test:
> - date
> - candidat
> - résumé métriques
> - verdict
> - décision suivante
