package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

/**
 * GlowwormMainBot — robot principal utilisant la stratégie GSO.
 *
 * Spécificités du main bot dans GSO :
 *   - Luciférine de BASE plus FAIBLE que les secondaires (il brille moins).
 *   - Il suit donc les secondaires qui ont détecté des ennemis.
 *   - Quand il voit un ennemi lui-même, sa luciférine monte et il attire
 *     éventuellement d'autres alliés.
 *
 * États :
 *   GLOW_MOVE      → déplacement GSO (vers voisin brillant ou exploration)
 *   ATTACK         → engagement de l'ennemi détecté
 *   AVOID_OBSTACLE → contournement
 *   IDLE           → mort
 */
public class GlowwormMainBot extends GlowwormAbstractBot {

    private static final double STEP_SIZE = Parameters.teamAMainBotSpeed;

    @Override
    public void activate() {
        isTeamA = (getHeading() == Parameters.EAST);
        isMain  = true;

        // Détermination de l'identifiant (même logique que NoeMainBot)
        boolean top = false, bottom = false;
        for (IRadarResult o : detectRadar()) {
            if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) top = true;
            else if (isSameDirection(o.getObjectDirection(), Parameters.SOUTH)) bottom = true;
        }
        if (top && bottom)       myId = M2;
        else if (!top && bottom) myId = M1;
        else                     myId = M3;

        double x = 0, y = 0;
        switch (myId) {
            case M1 -> {
                x = isTeamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX;
                y = isTeamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY;
            }
            case M2 -> {
                x = isTeamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX;
                y = isTeamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY;
            }
            case M3 -> {
                x = isTeamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX;
                y = isTeamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY;
            }
        }
        initBot(x, y, true);
        sendLogMessage("[GlowMain:" + idStr() + "] spawn=(" + myX + "," + myY
            + ") luciferin=" + luciferin);
    }

    @Override
    protected void onStep() {
        if (isDead()) {
            currentState = State.IDLE;
        }

        sendLogMessage("[GlowMain:" + idStr() + "] state=" + currentState
            + " luciferin=" + round2(luciferin)
            + " sensorRange=" + round2(sensorRange)
            + (brightestNeighbor != null ? " brightestId=" + brightestNeighbor.senderId() : ""));

        switch (currentState) {
            case GLOW_MOVE      -> stateGlowMove();
            case ATTACK         -> stateAttack();
            case AVOID_OBSTACLE -> doAvoidObstacle();
            case IDLE           -> {} // mort → rien
        }
    }

    // ------------------------------------------------------------------ //

    private void stateGlowMove() {
        // Si une cible est connue (propre ou partagée par un allié) → attaquer
        if (!Double.isNaN(sharedTargetX)) {
            currentState = State.ATTACK;
            return;
        }
        // Sinon : déplacement GSO
        doGlowMove(STEP_SIZE);
    }

    private void stateAttack() {
        if (Double.isNaN(sharedTargetX)) {
            // Cible perdue pour tout le monde → retour en exploration
            currentState = State.GLOW_MOVE;
            return;
        }
        doAttack(STEP_SIZE);
    }

    // ------------------------------------------------------------------ //

    private String idStr() {
        return switch (myId) {
            case M1 -> "M1"; case M2 -> "M2"; case M3 -> "M3"; default -> "?";
        };
    }
}