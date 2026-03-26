package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

import static algorithms.lab.NoeAbstractBot.State.*;

/**
 * Bot secondaire — Éclaireur.
 *
 * Rôle : grande portée radar 360°, rapide, ne tire pas.
 * Stratégie :
 *  • Slalom permanent pour couvrir le terrain.
 *  • Scan radar à chaque tick → remonte les positions ennemies aux Main via broadcast.
 *  • Intègre aussi les cibles des alliés (mergeTeamTargets) pour les relayer
 *    si un Main détecte quelque chose que l'autre Secondary ne voit pas encore.
 */
public class NoeSecondaryBot extends NoeAbstractBot {

  private static final double STEP_SIZE     = Parameters.teamASecondaryBotSpeed;
  private static final double AIM_THRESHOLD = 0.05;

  protected int role;

  @Override
  public void activate() {
    isTeamA = (getHeading() == Parameters.EAST);
    boolean top = false;
    for (IRadarResult o : detectRadar()) {
      if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) top = true;
    }
    myId = top ? S2 : S1;

    double x = 0, y = 0;
    switch (myId) {
      case S1 -> { x = isTeamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX;
        y = isTeamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY; }
      case S2 -> { x = isTeamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
        y = isTeamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY; }
    }
    if (myId == S1) {
      startCurvedMove(10, 1, Parameters.Direction.LEFT, false);
      initState(MOVE_SLALOM, x, y);
    } else {
      initState(MOVE_FORWARD, x, y);
    }
    sendLogMessage("[SecBot:" + id() + "] spawn=(" + myX + "," + myY + ")");
  }

  @Override
  public void step() { stepState(); }

  @Override
  protected void onStep() {
    // 1. Scan radar en priorité absolue — c'est le rôle principal du Secondary
    IRadarResult enemy = nearestEnemy(); // met à jour target si ennemi détecté

    // 2. Fusionner avec les cibles alliées (au cas où un allié voit mieux)
    mergeTeamTargets();

    // 3. Broadcast systématique (inclut la cible si elle existe et est fraîche)
    broadcastStatus();

    // 4. Comportement de déplacement
    switch (currentState) {
      case MOVE_SLALOM  -> stateMoveSlalom();
      case MOVE_FORWARD -> stateMoveForward();
      case IDLE_WATCH   -> stateIdleWatch();
      default           -> stateMoveSlalom(); // fallback sécurisé
    }
  }

  // ------------------------------------------------------------------ //
  //  États
  // ------------------------------------------------------------------ //

  /**
   * Déplacement en slalom — couverture de terrain maximale.
   * Le Secondary ne s'arrête jamais pour tirer, il reste en mouvement
   * pour éclairer la zone et rester difficile à toucher.
   */
  private void stateMoveSlalom() {
    stepCurvedMove(STEP_SIZE);
  }

  private void stateMoveForward() {
    if (isFrontObstacle()) { transitionTo(MOVE_SLALOM); return; }
    move();
    updatePosition(STEP_SIZE);
  }

  private void stateIdleWatch() {
    // État de secours : retourne au slalom
    transitionTo(MOVE_SLALOM);
  }

  private void transitionTo(State newState) {
    sendLogMessage("[SecBot:" + id() + "] " + currentState + " → " + newState);
    currentState = newState;
  }

  @SuppressWarnings("unused")
  private void moveToward(double tx, double ty) {
    double angle = angleTo(tx, ty);
    if (!isAligned(angle, AIM_THRESHOLD * 2)) turnToward(angle);
    else { move(); updatePosition(STEP_SIZE); }
  }

  private void turnToward(double targetAngle) {
    double diff = normalizeAngle(targetAngle - getHeading());
    stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
  }

  private String id() {
    return switch (myId) {
      case S1 -> "S1"; case S2 -> "S2"; default -> "?";
    };
  }
}