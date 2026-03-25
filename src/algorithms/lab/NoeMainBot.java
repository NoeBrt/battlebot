package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

public abstract class NoeMainBot extends NoeAbstractBot {

  protected static final int NORTH  = 0;
  protected static final int CENTER = 1;
  protected static final int SOUTH  = 2;

  protected static final int STATE_MOVE_TO_POSITION = 0;
  protected static final int STATE_ROTATE_TO_FIRE   = 1;
  protected static final int STATE_IDLE_WATCH        = 2;
  protected static final int STATE_AIM               = 3;
  protected static final int STATE_FIRE              = 4;
  protected static final int STATE_REPOSITION        = 5;
  protected static final int STATE_DODGE             = 6;

  private static final double STEP_SIZE          = 10.0;
  private static final double AIM_THRESHOLD      = 0.05;  // radians
  private static final double POSITION_THRESHOLD = 15.0;  // pixels
  private static final double LOW_HEALTH_RATIO   = 0.4;
  private static final double FIRE_HEADING       = 0.0;   // cap initial face aux ennemis

  protected int role;
  private double destX, destY;
  private IRadarResult lockedTarget;
  private int dodgeSteps;
  private int repositionSteps;
  private Parameters.Direction repositionDir; // direction latérale tirée aléatoirement

  // à adapter selon la taille réelle du terrain
  private static final double[][] DEPLOY_POSITIONS = {
    { 200, 100 },  // NORTH
    { 200, 300 },  // CENTER
    { 200, 500 },  // SOUTH
  };

  protected void initMainBot(double spawnX, double spawnY) {
    destX = DEPLOY_POSITIONS[role][0];
    destY = DEPLOY_POSITIONS[role][1];
    initState(STATE_MOVE_TO_POSITION, spawnX, spawnY);
    sendLogMessage("[MainBot] role=" + roleName() + " spawn=(" + spawnX + "," + spawnY + ")");
  }

  @Override
  public void step() {
    stepState();
  }

  @Override
  protected void onStep() {
    broadcastStatus();
    switch (currentState) {
      case STATE_MOVE_TO_POSITION -> stateMoveTo();
      case STATE_ROTATE_TO_FIRE   -> stateRotateToFire();
      case STATE_IDLE_WATCH       -> stateIdleWatch();
      case STATE_AIM              -> stateAim();
      case STATE_FIRE             -> stateFire();
      case STATE_REPOSITION       -> stateReposition();
      case STATE_DODGE            -> stateDodge();
    }
  }

  private void stateMoveTo() {
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      transitionTo(STATE_AIM);
      return;
    }
    if (distanceTo(destX, destY) < POSITION_THRESHOLD) {
      transitionTo(STATE_ROTATE_TO_FIRE);
      return;
    }
    moveToward(destX, destY);
  }

  private void stateRotateToFire() {
    if (isAligned(FIRE_HEADING, AIM_THRESHOLD)) {
      transitionTo(STATE_IDLE_WATCH);
      return;
    }
    turnToward(FIRE_HEADING);
  }

  private void stateIdleWatch() {
    if (myHp < LOW_HEALTH_RATIO) {
      transitionTo(STATE_DODGE);
      return;
    }
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      transitionTo(STATE_AIM);
      return;
    }
    stepTurn(Parameters.Direction.RIGHT); // balayage passif
  }

  private void stateAim() {
    if (myHp < LOW_HEALTH_RATIO) {
      transitionTo(STATE_DODGE);
      return;
    }
    IRadarResult refreshed = nearestEnemy();
    if (refreshed != null) lockedTarget = refreshed; // rafraîchit si toujours visible
    if (lockedTarget == null) {
      transitionTo(STATE_IDLE_WATCH);
      return;
    }
    double aimAngle = lockedTarget.getObjectDirection();
    if (isAligned(aimAngle, AIM_THRESHOLD)) {
      transitionTo(STATE_FIRE);
      return;
    }
    turnToward(aimAngle);
  }

  private void stateFire() {
    if (myHp < LOW_HEALTH_RATIO) {
      transitionTo(STATE_DODGE);
      return;
    }
    if (lockedTarget != null) {
      fire(lockedTarget.getObjectDirection());
    }
    transitionTo(STATE_REPOSITION);
  }

  private void stateReposition() {
    if (myHp < LOW_HEALTH_RATIO) {
      transitionTo(STATE_DODGE);
      return;
    }
    if (repositionSteps <= 0) {
      transitionTo(STATE_AIM);
      return;
    }
    // pivote perpendiculairement au cap de tir puis avance
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
    if (dodgeSteps <= 0) {
      transitionTo(STATE_IDLE_WATCH);
      return;
    }
    moveBack();
    updatePosition(-STEP_SIZE);
    dodgeSteps--;
  }


  private void transitionTo(int newState) {
    sendLogMessage("[MainBot:" + roleName() + "] " + stateName(currentState) + " → " + stateName(newState));
    if (newState == STATE_DODGE)       dodgeSteps = 5;
    if (newState == STATE_REPOSITION) {
      repositionSteps = 3;
      repositionDir = (Math.random() < 0.5) // aléatoire pour rester imprévisible
        ? Parameters.Direction.LEFT
        : Parameters.Direction.RIGHT;
    }
    currentState = newState;
  }

  private void moveToward(double tx, double ty) {
    double angle = angleTo(tx, ty);
    if (!isAligned(angle, AIM_THRESHOLD * 2)) {
      turnToward(angle);
    } else {
      move();
      updatePosition(STEP_SIZE);
    }
  }

  private void turnToward(double targetAngle) {
    double diff = normalizeAngle(targetAngle - getHeading());
    stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
  }

  private String roleName() {
    return switch (role) {
      case NORTH  -> "NORTH";
      case CENTER -> "CENTER";
      case SOUTH  -> "SOUTH";
      default     -> "?";
    };
  }

  private String stateName(int s) {
    return switch (s) {
      case STATE_MOVE_TO_POSITION -> "MOVE";
      case STATE_ROTATE_TO_FIRE   -> "ROTATE";
      case STATE_IDLE_WATCH       -> "WATCH";
      case STATE_AIM              -> "AIM";
      case STATE_FIRE             -> "FIRE";
      case STATE_REPOSITION       -> "REPOSITION";
      case STATE_DODGE            -> "DODGE";
      default                     -> "?";
    };
  }
}