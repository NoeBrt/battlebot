package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * GlowwormSecondaryBot — robot secondaire utilisant la stratégie GSO.
 *
 * Spécificités du secondaire dans GSO :
 *   - Luciférine de BASE plus HAUTE que les mains (il brille naturellement plus).
 *   - Il sert d'«éclaireur» : en détectant des ennemis il monte encore en luciférine
 *     et attire les mains vers lui.
 *   - Son déplacement de base est organique : il avance tout droit et dévie
 *     légèrement sa trajectoire de façon aléatoire à chaque tick (marche de Lévy),
 *     ce qui lui permet de couvrir naturellement le terrain sans pattern rigide.
 *   - Quand un allié brille plus que lui, il converge vers lui.
 *
 * États :
 *   GLOW_MOVE      → déplacement organique + attraction si voisin plus brillant
 *   ATTACK         → tir sur l'ennemi détecté
 *   AVOID_OBSTACLE → contournement
 *   IDLE           → mort
 */
public class GlowwormSecondaryBot extends GlowwormAbstractBot {

    private static final double STEP_SIZE = Parameters.teamASecondaryBotSpeed;

    /**
     * Angle de dérive organique courant (s'accumule doucement).
     * Simule une marche de Lévy : petites déviations fréquentes,
     * grandes déviations rares.
     */
    private double wanderAngle = 0.0;

    /**
     * Amplitude maximale de la perturbation angulaire par tick (radians).
     * Une valeur faible = trajectoire quasi-droite avec légères courbes.
     */
    private static final double WANDER_JITTER   = 0.18;  // bruit de cap par tick

    @Override
    public void activate() {
        isTeamA = (getHeading() == Parameters.EAST);
        isMain  = false;

        // Détermination de l'identifiant (même logique que NoeSecondaryBot)
        boolean top = false;
        for (IRadarResult o : detectRadar()) {
            if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) top = true;
        }
        myId = top ? S2 : S1;

        // Angle de wander initial légèrement différent selon le bot pour diversifier
        wanderAngle = (myId == S1) ? 0.3 : -0.3;

        double x = 0, y = 0;
        switch (myId) {
            case S1 -> {
                x = isTeamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX;
                y = isTeamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY;
            }
            case S2 -> {
                x = isTeamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
                y = isTeamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
            }
        }
        initBot(x, y, false);
        sendLogMessage("[GlowSec:" + idStr() + "] spawn=(" + myX + "," + myY
            + ") luciferin=" + luciferin);
    }

    @Override
    protected void onStep() {
        if (isDead()) {
            currentState = State.IDLE;
        }

        sendLogMessage("[GlowSec:" + idStr() + "] state=" + currentState
            + " luciferin=" + round2(luciferin)
            + " sensorRange=" + round2(sensorRange)
            + (brightestNeighbor != null ? " brightestId=" + brightestNeighbor.senderId() : ""));

        switch (currentState) {
            case GLOW_MOVE      -> stateGlowMove();
            case ATTACK         -> stateAttack();
            case AVOID_OBSTACLE -> doAvoidObstacle();
            case IDLE           -> {}
        }
    }

    // ------------------------------------------------------------------ //

    private void stateGlowMove() {
        if (isFrontObstacle()) {
            transitionAvoid();
            return;
        }

        // Si une cible est connue (propre ou partagée) → transition vers ATTACK
        if (!Double.isNaN(sharedTargetX)) {
            currentState = State.ATTACK;
            return;
        }

        if (brightestNeighbor != null) {
            // Un allié brille plus que moi → convergence directe vers lui
            doGlowMove(STEP_SIZE);
        } else {
            // Personne ne brille plus → dérive organique (wander)
            doWander();
        }
    }

    /**
     * Déplacement organique : une seule action par tick.
     *
     * wanderAngle accumule un biais de cap aléatoire borné. Chaque tick :
     *   - Si le biais est assez grand (dépassement du seuil) → stepTurn pour corriger,
     *     et on consomme partiellement le biais.
     *   - Sinon → move() pour avancer.
     *
     * Résultat : le bot avance la plupart du temps avec des virages ponctuels,
     * produisant une trajectoire douce et organique sans jamais tourner en rond.
     */
    private void doWander() {
        wanderAngle += (Math.random() * 2 - 1) * WANDER_JITTER;
        wanderAngle = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, wanderAngle));

        if (Math.abs(wanderAngle) > AIM_THRESHOLD) {
            // Ce tick : corriger le cap d'un stepTurn dans la direction du biais
            stepTurn(wanderAngle > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
            // Réduire le biais d'un pas (stepTurnAngle consommé)
            double stepAngle = Parameters.teamASecondaryBotStepTurnAngle;
            wanderAngle -= Math.signum(wanderAngle) * stepAngle;
        } else {
            // Ce tick : avancer
            move();
            myX += STEP_SIZE * Math.cos(getHeading());
            myY += STEP_SIZE * Math.sin(getHeading());
        }
    }

    private void stateAttack() {
        if (Double.isNaN(sharedTargetX)) {
            currentState = State.GLOW_MOVE;
            return;
        }
        doAttack(STEP_SIZE);
    }

    // ------------------------------------------------------------------ //

    private String idStr() {
        return switch (myId) {
            case S1 -> "S1"; case S2 -> "S2"; default -> "?";
        };
    }
}