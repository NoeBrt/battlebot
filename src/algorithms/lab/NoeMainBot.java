package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

import static algorithms.lab.NoeAbstractBot.State.*;

public class NoeMainBot extends NoeAbstractBot {

  private static final double STEP_SIZE        = Parameters.teamAMainBotSpeed;
  private static final double AIM_THRESHOLD    = 0.05;   // radians
  private static final double LOW_HEALTH_RATIO = 0.1;

  protected int role;
  private IRadarResult lockedTarget;
  private int dodgeSteps;
  private int repositionSteps;
  private Parameters.Direction repositionDir;

  @Override
  public void activate() {
    isTeamA = (getHeading() == Parameters.EAST);
    boolean top = false, bottom = false;
    for (IRadarResult o : detectRadar()) {
      if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) top = true;
      else if (isSameDirection(o.getObjectDirection(), Parameters.SOUTH)) bottom = true;
    }
    if (top && bottom) myId = M2;
    else if (!top && bottom) myId = M1;
    else myId = M3;

    double x = 0, y = 0;
    switch (myId) {
      case M1 -> { x = isTeamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX;
        y = isTeamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY; }
      case M2 -> { x = isTeamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX;
        y = isTeamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY; }
      case M3 -> { x = isTeamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX;
        y = isTeamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY; }
    }
    startCurvedMove(16, 1, Parameters.Direction.LEFT, true);
    initState(MOVE_FORWARD, x, y);
    sendLogMessage("[MainBot:" + id() + "] spawn=(" + myX + "," + myY + ")");
  }

  @Override
  public void step() { stepState(); }

  @Override
  protected void onStep() {
    broadcastStatus();

    // --- Mort : passer en mode radar passif ---
    if (isDead()) { transitionTo(RADAR_MODE); return; }

    // --- Fusion des cibles alliées (Secondary en priorité car plus fraîches) ---
    mergeTeamTargets();

    // --- Si une cible fraîche est connue et qu'on n'est pas encore en attaque ---
    if (targetFound() && currentState != ATTACK_MODE
        && currentState != FIRE
        && currentState != DODGE) {
      transitionTo(ATTACK_MODE);
      return;
    }

    switch (currentState) {
      case MOVE_FORWARD -> stateMoveForward();
      case IDLE_WATCH   -> stateIdleWatch();
      case ATTACK_MODE  -> stateAttackMode();
      case FIRE         -> stateFire();
      case REPOSITION   -> stateReposition();
      case DODGE        -> stateDodge();
      case MOVE_SLALOM  -> stateMoveSlalom();
      case RADAR_MODE   -> stateRadarMode();
    }
  }

  // ------------------------------------------------------------------ //
  //  États
  // ------------------------------------------------------------------ //

  private void stateMoveForward() {
    IRadarResult enemy = nearestEnemy(); // met à jour target si trouvé
    if (enemy != null) {
      lockedTarget = enemy;
      transitionTo(ATTACK_MODE);
      return;
    }
    if (isFrontObstacle()) { transitionTo(REPOSITION); return; }
    move();
    updatePosition(STEP_SIZE);
  }

  /**
   * Mode attaque : tire vers la cible connue et actualise via le radar.
   * Si la cible est périmée (ennemi perdu), retour en patrouille.
   */
  private void stateAttackMode() {
    if (myHp < LOW_HEALTH_RATIO) { transitionTo(DODGE); return; }

    // Rafraîchissement radar propre
    IRadarResult radarEnemy = nearestEnemy();
    if (radarEnemy != null) lockedTarget = radarEnemy;

    if (!targetFound()) {
      // Cible périmée : retour en patrouille
      sendLogMessage("[MainBot:" + id() + "] cible perdue → MOVE_FORWARD");
      transitionTo(MOVE_FORWARD);
      return;
    }

    // Tir vers la position estimée
    double angle = angleTo(targetX(), targetY());
    fire(angle);
    transitionTo(REPOSITION); // bouge après chaque tir pour être imprévisible
  }

  private void stateRadarMode() {
    // Bot "mort" : continue à scanner pour alimenter les alliés
    nearestEnemy();
    broadcastStatus();
  }

  private void stateMoveSlalom() {
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      stopCurvedMove();
      transitionTo(ATTACK_MODE);
      return;
    }
    stepCurvedMove(STEP_SIZE);
  }

  private void stateIdleWatch() {
    if (myHp < LOW_HEALTH_RATIO) { transitionTo(DODGE); return; }
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      transitionTo(ATTACK_MODE);
    }
    // Si une cible fraîche vient des alliés, targetFound() sera true
    // et le bloc en tête de onStep() aura déjà déclenché ATTACK_MODE.
  }

  private void stateFire() {
    if (myHp < LOW_HEALTH_RATIO) { transitionTo(DODGE); return; }
    if (targetFound()) {
      double angle = angleTo(targetX(), targetY());
      fire(angle);
    }
    transitionTo(REPOSITION);
  }

  private void stateReposition() {
    if (myHp < LOW_HEALTH_RATIO) { transitionTo(DODGE); return; }
    if (repositionSteps <= 0) {
      // Après repositionnement, retour attaque si cible encore valide
      transitionTo(targetFound() ? ATTACK_MODE : MOVE_FORWARD);
      return;
    }
    double lateralAngle = normalizeAngle(getHeading() + Math.PI / 2
        * (repositionDir == Parameters.Direction.RIGHT ? 1 : -1));
    if (!isAligned(lateralAngle, AIM_THRESHOLD * 2)) {
      turnToward(lateralAngle);
    } else {
      move();
      updatePosition(STEP_SIZE);
      repositionSteps--;
    }
  }

  private void stateDodge() {
    if (dodgeSteps <= 0) { transitionTo(IDLE_WATCH); return; }
    moveBack();
    updatePosition(-STEP_SIZE);
    dodgeSteps--;
  }

  // ------------------------------------------------------------------ //
  //  Utilitaires
  // ------------------------------------------------------------------ //

  private void transitionTo(State newState) {
    sendLogMessage("[MainBot:" + id() + "] " + currentState + " → " + newState);
    if (newState == DODGE) dodgeSteps = 5;
    if (newState == REPOSITION) {
      repositionSteps = 3;
      repositionDir = (Math.random() < 0.5)
          ? Parameters.Direction.LEFT : Parameters.Direction.RIGHT;
    }
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
      case M1 -> "M1"; case M2 -> "M2"; case M3 -> "M3"; default -> "?";
    };
  }
}