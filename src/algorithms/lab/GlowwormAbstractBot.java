package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.IFrontSensorResult;
import characteristics.Parameters;
import robotsimulator.Brain;

import java.util.ArrayList;
import java.util.List;

/**
 * GlowwormAbstractBot — implémentation de la stratégie Glowworm Swarm Optimization (GSO).
 *
 * Principe :
 *   - Chaque bot possède une luciférine (luciferin) qui représente son attractivité.
 *   - Les bots secondaires ont une luciférine de base plus élevée que les principaux.
 *   - La luciférine augmente quand un ennemi est détecté, d'autant plus si cet ennemi
 *     a peu de HP (cible facile).
 *   - Chaque bot se déplace probabilistiquement vers l'allié le plus brillant
 *     (luciférine la plus haute) dans son voisinage perceptif (range dynamique).
 *   - La range perceptive se réduit si trop d'alliés sont visibles (densité locale).
 *
 * Format du broadcast :
 *   "id:luciferin|x:val|y:val|tx:val|ty:val|hp:val|eh:val"
 *   "eh" = enemy health estimée (0..1, NaN si pas de cible)
 */
public abstract class GlowwormAbstractBot extends Brain {

    // ------------------------------------------------------------------ //
    // Structures de données internes
    // ------------------------------------------------------------------ //

    protected static class Position {
        double x, y;
        Position(double x, double y) { this.x = x; this.y = y; }
        double distTo(Position o) {
            double dx = x - o.x, dy = y - o.y;
            return Math.sqrt(dx * dx + dy * dy);
        }
    }

    /**
     * Message reçu d'un allié : sa position, sa luciférine et sa cible éventuelle.
     */
    protected record GlowMessage(
        int senderId,
        double luciferin,
        Position pos,
        Position targetPos,   // peut être NaN si pas de cible
        double myHp,
        double enemyHp        // NaN si pas de cible détectée par l'émetteur
    ) {}

    // ------------------------------------------------------------------ //
    // Constantes GSO
    // ------------------------------------------------------------------ //

    /** Luciférine initiale (secondaires démarrent plus haut). */
    protected static final double LUCIFERIN_BASE_MAIN      = 5.0;
    protected static final double LUCIFERIN_BASE_SECONDARY = 10.0;

    /** Décroissance naturelle par tick (ρ dans le modèle GSO). */
    protected static final double LUCIFERIN_DECAY          = 0.4;

    /** Gain lié à la détection d'un ennemi (γ). */
    protected static final double LUCIFERIN_GAIN_ENEMY     = 8.0;

    /** Bonus supplémentaire si l'ennemi est faible (HP < LOW_HP_THRESHOLD). */
    protected static final double LUCIFERIN_GAIN_WEAK_ENEMY = 5.0;

    /** Seuil HP pour considérer un ennemi « faible ». */
    protected static final double LOW_HP_THRESHOLD         = 0.3;

    /** Rayon perceptif initial (rs dans GSO). */
    protected static final double SENSOR_RANGE_INIT        = Parameters.teamAMainBotFrontalDetectionRange;

    /** Rayon perceptif maximum. */
    protected static final double SENSOR_RANGE_MAX         = Parameters.teamAMainBotFrontalDetectionRange;

    /** Rayon perceptif minimum (évite l'isolement total). */
    protected static final double SENSOR_RANGE_MIN         = Parameters.teamAMainBotRadius * 4;

    /** Nb d'alliés cible dans le voisinage (nt). */
    protected static final int    NEIGHBOR_COUNT_TARGET    = 3;

    /** Pas d'adaptation du rayon perceptif. */
    protected static final double SENSOR_RANGE_STEP        = 20.0;

    /** Seuil d'alignement angulaire (radians). */
    protected static final double AIM_THRESHOLD            = 0.05;

    // ------------------------------------------------------------------ //
    // Identifiants robots (hérités de la convention NoeBot)
    // ------------------------------------------------------------------ //
    protected static final int M1 = 0, M2 = 1, M3 = 2, S1 = 3, S2 = 4;

    // ------------------------------------------------------------------ //
    // État interne du bot
    // ------------------------------------------------------------------ //

    protected int    myId;
    protected boolean isMain;
    protected boolean isTeamA;

    protected double myX, myY;
    protected double myHp = 1.0;

    /** Luciférine courante. */
    protected double luciferin;

    /** Rayon perceptif courant (dynamique). */
    protected double sensorRange;

    /**
     * Cible vue directement par le radar propre ce tick. NaN si rien détecté.
     */
    protected double targetX = Double.NaN, targetY = Double.NaN;
    protected double enemyHpEstimate = Double.NaN;

    /**
     * Cible consolidée : propre si disponible, sinon meilleure cible alliée
     * (celle de l'allié le plus brillant qui en rapporte une).
     * C'est cette valeur que tous les bots utilisent pour attaquer/converger.
     */
    protected double sharedTargetX = Double.NaN, sharedTargetY = Double.NaN;

    /** Messages reçus des alliés ce tick. */
    protected final List<GlowMessage> teamMessages = new ArrayList<>();

    /** L'allié « le plus brillant » dans mon voisinage ce tick. */
    protected GlowMessage brightestNeighbor = null;

    protected enum State {
        GLOW_MOVE,      // déplacement probabiliste vers le plus brillant
        ATTACK,         // tir sur l'ennemi détecté
        AVOID_OBSTACLE, // contournement d'obstacle frontal
        IDLE            // mort / attente
    }

    protected State currentState = State.GLOW_MOVE;
    protected double avoidAngle  = 0;

    // ------------------------------------------------------------------ //
    // Initialisation
    // ------------------------------------------------------------------ //

    protected void initBot(double startX, double startY, boolean main) {
        isMain    = main;
        myX       = startX;
        myY       = startY;
        myHp      = getHealth();
        luciferin = main ? LUCIFERIN_BASE_MAIN : LUCIFERIN_BASE_SECONDARY;
        sensorRange = SENSOR_RANGE_INIT;
        currentState = State.GLOW_MOVE;
    }

    // ------------------------------------------------------------------ //
    // Boucle principale
    // ------------------------------------------------------------------ //

    @Override
    public void step() {
        myHp = getHealth();
        receiveMessages();
        scanEnvironment();
        mergeTeamTarget();   // consolide targetX/Y + cibles alliées → sharedTarget
        updateLuciferin();
        broadcastLuciferin();
        findBrightestNeighbor();
        adaptSensorRange();
        onStep();
    }

    /** Point d'extension pour les sous-classes. */
    protected abstract void onStep();

    // ------------------------------------------------------------------ //
    // GSO : mise à jour de la luciférine
    // ------------------------------------------------------------------ //

    /**
     * ℓᵢ(t+1) = (1 - ρ) · ℓᵢ(t) + γ · J(xᵢ(t))
     * avec J(x) = gain si ennemi détecté ce tick (via radar propre).
     */
    private void updateLuciferin() {
        double gain = 0.0;
        if (!Double.isNaN(targetX)) {
            gain += LUCIFERIN_GAIN_ENEMY;
            if (!Double.isNaN(enemyHpEstimate) && enemyHpEstimate < LOW_HP_THRESHOLD) {
                gain += LUCIFERIN_GAIN_WEAK_ENEMY;
            }
        }
        luciferin = (1.0 - LUCIFERIN_DECAY) * luciferin + gain;
        luciferin = Math.max(0, luciferin);
    }

    /**
     * Consolide la cible partagée (sharedTargetX/Y).
     *
     * Priorité :
     *   1. Ma propre détection radar (fraîche, fiable).
     *   2. La cible de l'allié le plus brillant qui en signale une —
     *      car il brille précisément parce qu'il voit (ou voyait) un ennemi.
     *
     * Résultat utilisé par doAttack() et doGlowMove() pour tous les bots.
     */
    private void mergeTeamTarget() {
        // Priorité 1 : je vois un ennemi moi-même
        if (!Double.isNaN(targetX)) {
            sharedTargetX = targetX;
            sharedTargetY = targetY;
            return;
        }

        // Priorité 2 : chercher l'allié avec la luciférine la plus haute
        // qui partage une cible valide
        double bestLuciferin = -1;
        sharedTargetX = Double.NaN;
        sharedTargetY = Double.NaN;

        for (GlowMessage gm : teamMessages) {
            if (Double.isNaN(gm.targetPos().x) || Double.isNaN(gm.targetPos().y)) continue;
            if (gm.luciferin() > bestLuciferin) {
                bestLuciferin = gm.luciferin();
                sharedTargetX = gm.targetPos().x;
                sharedTargetY = gm.targetPos().y;
            }
        }
    }

    // ------------------------------------------------------------------ //
    // GSO : sélection probabiliste du voisin le plus brillant
    // ------------------------------------------------------------------ //

    /**
     * Sélectionne l'allié dont la luciférine est strictement supérieure à la mienne
     * et dont la distance est dans mon sensorRange, avec probabilité proportionnelle
     * à l'écart de luciférine (roulette wheel).
     */
    private void findBrightestNeighbor() {
        List<GlowMessage> candidates = new ArrayList<>();
        double sumDiff = 0.0;

        for (GlowMessage gm : teamMessages) {
            double dist = gm.pos().distTo(new Position(myX, myY));
            if (dist <= sensorRange && gm.luciferin() > luciferin) {
                candidates.add(gm);
                sumDiff += (gm.luciferin() - luciferin);
            }
        }

        brightestNeighbor = null;
        if (candidates.isEmpty() || sumDiff <= 0) return;

        // Sélection par roulette wheel
        double r = Math.random() * sumDiff;
        double cumul = 0.0;
        for (GlowMessage gm : candidates) {
            cumul += (gm.luciferin() - luciferin);
            if (cumul >= r) {
                brightestNeighbor = gm;
                break;
            }
        }
        if (brightestNeighbor == null) brightestNeighbor = candidates.get(candidates.size() - 1);
    }

    /**
     * Adapte le rayon perceptif en fonction du nombre de voisins courant.
     * rs(t+1) = min(rsMax, max(rsMin, rs(t) + β · (nt − |Nᵢ(t)|)))
     */
    private void adaptSensorRange() {
        long neighborCount = teamMessages.stream()
            .filter(gm -> gm.pos().distTo(new Position(myX, myY)) <= sensorRange)
            .count();
        double beta = 0.05;
        sensorRange += beta * (NEIGHBOR_COUNT_TARGET - neighborCount);
        sensorRange = Math.max(SENSOR_RANGE_MIN, Math.min(SENSOR_RANGE_MAX, sensorRange));
    }

    // ------------------------------------------------------------------ //
    // Scan de l'environnement (radar)
    // ------------------------------------------------------------------ //

    private void scanEnvironment() {
        targetX = Double.NaN;
        targetY = Double.NaN;
        enemyHpEstimate = Double.NaN;

        double minDist = Double.MAX_VALUE;
        for (IRadarResult r : detectRadar()) {
            if (isEnemy(r) && r.getObjectDistance() < minDist) {
                minDist = r.getObjectDistance();
                targetX = myX + r.getObjectDistance() * Math.cos(r.getObjectDirection());
                targetY = myY + r.getObjectDistance() * Math.sin(r.getObjectDirection());
                // On ne connaît pas l'HP de l'ennemi directement → on estime via
                // les messages alliés qui auraient tiré sur lui récemment.
                enemyHpEstimate = estimateEnemyHp(r);
            }
        }
    }

    /**
     * Estime l'HP ennemi à partir des messages alliés (si un allié cible
     * le même point, on prend son estimation).
     */
    private double estimateEnemyHp(IRadarResult r) {
        double ex = myX + r.getObjectDistance() * Math.cos(r.getObjectDirection());
        double ey = myY + r.getObjectDistance() * Math.sin(r.getObjectDirection());
        for (GlowMessage gm : teamMessages) {
            if (Double.isNaN(gm.targetPos().x)) continue;
            double dx = gm.targetPos().x - ex;
            double dy = gm.targetPos().y - ey;
            if (dx * dx + dy * dy < 100) { // même cible à ±10 px
                return gm.enemyHp();
            }
        }
        return Double.NaN; // inconnu
    }

    // ------------------------------------------------------------------ //
    // Déplacement GSO — une seule action simulateur par tick
    // ------------------------------------------------------------------ //

    /**
     * Déplacement GSO : si le cap est bon → move(), sinon → stepTurn().
     * Une seule action par tick.
     */
    protected void doGlowMove(double stepSize) {
        if (isFrontObstacle()) {
            transitionAvoid();
            return;
        }
        double desiredAngle;
        if (brightestNeighbor != null) {
            desiredAngle = Math.atan2(
                brightestNeighbor.pos().y - myY,
                brightestNeighbor.pos().x - myX);
        } else if (!Double.isNaN(sharedTargetX)) {
            desiredAngle = Math.atan2(sharedTargetY - myY, sharedTargetX - myX);
        } else {
            desiredAngle = getHeading(); // continuer tout droit
        }

        if (isAligned(desiredAngle)) {
            move();
            myX += stepSize * Math.cos(getHeading());
            myY += stepSize * Math.sin(getHeading());
        } else {
            double diff = normalizeAngle(desiredAngle - getHeading());
            stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
        }
    }

    /**
     * Attaque :
     *   - À portée → fire() (tir à 360°, pas d'alignement nécessaire).
     *   - Hors portée → s'approcher : tourner si mal orienté, avancer si orienté.
     * Une seule action par tick.
     */
    protected void doAttack(double stepSize) {
        if (Double.isNaN(sharedTargetX)) {
            currentState = State.GLOW_MOVE;
            return;
        }
        double angle = Math.atan2(sharedTargetY - myY, sharedTargetX - myX);
        double dist  = Math.hypot(sharedTargetX - myX, sharedTargetY - myY);
        if (dist <= Parameters.bulletRange) {
            fire(angle); // une action : tir
        } else {
            // Approche : une action (tour ou avance)
            if (isAligned(angle)) {
                move();
                myX += stepSize * Math.cos(getHeading());
                myY += stepSize * Math.sin(getHeading());
            } else {
                double diff = normalizeAngle(angle - getHeading());
                stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
            }
        }
    }

    /**
     * Évitement d'obstacle : tourner jusqu'à être aligné sur avoidAngle,
     * puis avancer si la voie est libre.
     * Une seule action par tick.
     */
    protected void doAvoidObstacle() {
        if (isAligned(avoidAngle)) {
            if (!isFrontObstacle()) {
                currentState = State.GLOW_MOVE;
            } else {
                // La voie est toujours bloquée → chercher un nouvel angle
                avoidAngle = normalizeAngle(avoidAngle + Parameters.RIGHTTURNFULLANGLE);
                stepTurn(Parameters.Direction.RIGHT);
            }
        } else {
            double diff = normalizeAngle(avoidAngle - getHeading());
            stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
        }
    }

    // ------------------------------------------------------------------ //
    // Broadcast & réception
    // ------------------------------------------------------------------ //

    protected void broadcastLuciferin() {
        String txStr = Double.isNaN(targetX) ? "NaN" : round2(targetX) + "";
        String tyStr = Double.isNaN(targetY) ? "NaN" : round2(targetY) + "";
        String ehStr = Double.isNaN(enemyHpEstimate) ? "NaN" : round2(enemyHpEstimate) + "";
        String msg = myId + ":" + round2(luciferin)
            + "|x:" + round2(myX)
            + "|y:" + round2(myY)
            + "|tx:" + txStr
            + "|ty:" + tyStr
            + "|hp:" + round2(myHp)
            + "|eh:" + ehStr;
        broadcast(msg);
    }

    private void receiveMessages() {
        teamMessages.clear();
        for (String raw : fetchAllMessages()) {
            GlowMessage gm = parseGlowMessage(raw);
            if (gm != null) teamMessages.add(gm);
        }
    }

    private GlowMessage parseGlowMessage(String raw) {
        try {
            String[] sections = raw.split("\\|");
            String[] header   = sections[0].split(":");
            int    senderId   = Integer.parseInt(header[0]);
            double lucif      = Double.parseDouble(header[1]);
            if (senderId == myId) return null;

            double x = 0, y = 0, tx = Double.NaN, ty = Double.NaN, hp = 1, eh = Double.NaN;
            for (int i = 1; i < sections.length; i++) {
                String[] kv = sections[i].split(":");
                if (kv.length < 2) continue;
                switch (kv[0]) {
                    case "x"  -> x  = Double.parseDouble(kv[1]);
                    case "y"  -> y  = Double.parseDouble(kv[1]);
                    case "tx" -> tx = parseDoubleOrNaN(kv[1]);
                    case "ty" -> ty = parseDoubleOrNaN(kv[1]);
                    case "hp" -> hp = Double.parseDouble(kv[1]);
                    case "eh" -> eh = parseDoubleOrNaN(kv[1]);
                }
            }
            return new GlowMessage(senderId, lucif,
                new Position(x, y),
                new Position(tx, ty),
                hp, eh);
        } catch (Exception e) {
            return null;
        }
    }

    private double parseDoubleOrNaN(String s) {
        return "NaN".equals(s) ? Double.NaN : Double.parseDouble(s);
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    protected boolean isFrontObstacle() {
        return detectFront().getObjectType() != IFrontSensorResult.Types.NOTHING;
    }

    protected boolean isEnemy(IRadarResult r) {
        return r.getObjectType() == IRadarResult.Types.OpponentMainBot
            || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
    }

    protected boolean isDead() { return getHealth() <= 0; }

    protected boolean isAligned(double angle) {
        return Math.abs(normalizeAngle(angle - getHeading())) < AIM_THRESHOLD;
    }

    protected void turnToward(double angle) {
        double diff = normalizeAngle(angle - getHeading());
        stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
    }

    protected void transitionAvoid() {
        avoidAngle   = normalizeAngle(getHeading() + Parameters.RIGHTTURNFULLANGLE);
        currentState = State.AVOID_OBSTACLE;
    }

    protected double normalizeAngle(double a) {
        while (a >  Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    protected boolean isSameDirection(double d1, double d2) {
        double diff = Math.abs(normalizeAngle(d1) - normalizeAngle(d2));
        return diff < 0.001 || Math.abs(diff - 2 * Math.PI) < 0.001;
    }

    protected double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}