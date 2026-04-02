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
    //startCurvedMove(16, 1, Parameters.Direction.LEFT, true);
    initState(MOVE_FORWARD, x, y);
    sendLogMessage("[MainBot:" + id() + "] spawn=(" + myX + "," + myY + ")");
  }

  @Override
  public void step() { stepState(); }

  @Override
  protected void onStep() {
    scanAround();
    broadcastStatus();
    if (isDead()) { transitionTo(RADAR_MODE); return; }
    mergeTeamTargets();
    if (allyFoundATargetWhileBeingFree()) {
      transitionTo(ATTACK_MODE);
      return;
    }/*
    if (isInDeadZone(myX, myY) && currentState != AVOID_DEAD_ZONE && currentState != AVOID_OBSTACLE) {
      transitionTo(AVOID_DEAD_ZONE);
      return;
    }*/
    switch (currentState) {
      case MOVE_FORWARD -> stateMoveForward();
      case IDLE_WATCH   -> stateIdleWatch();
      case ATTACK_MODE  -> stateAttackMode();
      case FIRE         -> stateFire();
      case REPOSITION   -> stateReposition();
      case DODGE        -> stateDodge();
      //case MOVE_SLALOM  -> stateMoveSlalom();
      case RADAR_MODE   -> stateRadarMode();
      case AVOID_OBSTACLE -> stateAvoidObstacle();
      case AVOID_DEAD_ZONE -> stateAvoidDeadZone();
    }
  }

  private void stateMoveForward() {
    if (nearestEnemy != null || targetFound()) {
      lockedTarget = nearestEnemy;
      transitionTo(ATTACK_MODE);
      return;
    }
    if (isFrontObstacle()) {
      targetAngle = normalizeAngle(getHeading() + Parameters.RIGHTTURNFULLANGLE);
      transitionTo(AVOID_OBSTACLE);
      return;
    }
    move();
    updatePosition(STEP_SIZE);
  }

  private void stateAvoidDeadZone() {
    if (!isInDeadZone(myX, myY)) {
      transitionTo(previousState);
      return;
    }
    if (!isAligned(targetAngle, AIM_THRESHOLD * 2)) {
      turnToward(targetAngle);
    } else if (!isFrontObstacle()) {
      move();
      updatePosition(STEP_SIZE);
    } else {
      targetAngle = computeEscapeAngle(STEP_SIZE);
    }
  }

  private void stateAttackMode() {
    if (isFrontObstacle()) {
      transitionTo(AVOID_OBSTACLE);
      return;
    }
    if (!targetFound()) {
      lockedTarget = null;
      target.valid = false;
      sendLogMessage("[MainBot:" + id() + "] cible perdue → MOVE_FORWARD");
      transitionTo(MOVE_FORWARD);
      return;
    }
    double dist = distanceTo(targetX(), targetY());
    double angle = angleTo(targetX(), targetY());
    if (dist > FIRE_RANGE) {
      if (!isAligned(angle, AIM_THRESHOLD * 2)) turnToward(angle);
      else { move(); updatePosition(STEP_SIZE); }
      return;
    }
    fire(angle);
    //transitionTo(REPOSITION); // bouge après chaque tir pour être imprévisible
  }

  private void stateRadarMode() {
  }
/*
  private void stateMoveSlalom() {
    if (nearestEnemy != null) {
      lockedTarget = nearestEnemy;
      stopCurvedMove();
      transitionTo(ATTACK_MODE);
      return;
    }
    stepCurvedMove(STEP_SIZE);
  }*/

  private void stateIdleWatch() {
    if (nearestEnemy != null) {
      lockedTarget = nearestEnemy;
      transitionTo(ATTACK_MODE);
    }
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

  private void stateAvoidObstacle() {
    if (!isAligned(targetAngle, AIM_THRESHOLD * 2)) {
      turnToward(targetAngle);
      return;
    }
    if (!isFrontObstacle()) {
      transitionTo(MOVE_FORWARD);
    }
  }

  private void transitionTo(State newState) {
    sendLogMessage("[MainBot:" + id() + "] " + currentState + " → " + newState);
    if (newState == DODGE) dodgeSteps = 5;
    if (newState == REPOSITION) {
      repositionSteps = 3;
      repositionDir = (Math.random() < 0.5)
          ? Parameters.Direction.LEFT : Parameters.Direction.RIGHT;
    }
    if (newState == AVOID_OBSTACLE) targetAngle = computeAvoidAngle();
    if (newState == AVOID_DEAD_ZONE) targetAngle = computeEscapeAngle(STEP_SIZE);
    if (newState != AVOID_DEAD_ZONE) previousState = currentState;
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

  private boolean allyFoundATargetWhileBeingFree() {
    return targetFound() && currentState != ATTACK_MODE
      && currentState != FIRE
      && currentState != DODGE
      && currentState != AVOID_DEAD_ZONE
      && currentState != AVOID_OBSTACLE;
  }

  private String id() {
    return switch (myId) {
      case M1 -> "M1"; case M2 -> "M2"; case M3 -> "M3"; default -> "?";
    };
  }
}