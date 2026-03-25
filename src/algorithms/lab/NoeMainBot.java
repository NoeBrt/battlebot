package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

import static algorithms.lab.NoeAbstractBot.State.*;

public class NoeMainBot extends NoeAbstractBot {

  private static final double STEP_SIZE          = Parameters.teamAMainBotSpeed;
  private static final double AIM_THRESHOLD      = 0.05;  // radians
  private static final double LOW_HEALTH_RATIO   = 0.1;

  protected int role;
  private IRadarResult lockedTarget;
  private int dodgeSteps;
  private int repositionSteps;
  private Parameters.Direction repositionDir; // direction latérale tirée aléatoirement


  @Override
  public void activate() {
    isTeamA = (getHeading() == Parameters.EAST);
    boolean top = false;
    boolean bottom = false;
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
    startCurvedMove(16, 1, Parameters.Direction.LEFT, true);
    initState(MOVE_SLALOM, x, y);
    sendLogMessage("[MainBot: " + id() + "] spawn=(" + myX + "," + myY + ")");
  }

  @Override
  public void step() {
    stepState();
  }

  @Override
  protected void onStep() {
    broadcastStatus();
    boolean actionToPerformed = false;
    // Compute team message action in priority
    if (!teamMessages.isEmpty()) {
      for (BotMessage bm : teamMessages) {
        if (bm.targetFound()) {
          targetX = bm.targetX();
          targetY = bm.targetY();
          transitionTo(ATTACK_MODE);
          return;
        }
      }
    }
    switch (currentState) {
      case MOVE_FORWARD     -> stateMoveForward();
      case IDLE_WATCH       -> stateIdleWatch();
      case ATTACK_MODE      -> stateAttackMode();
      case FIRE             -> stateFire();
      case REPOSITION       -> stateReposition();
      case DODGE            -> stateDodge();
      case MOVE_SLALOM      -> stateMoveSlalom();
    }
  }

  private void stateMoveForward() {
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      transitionTo(ATTACK_MODE);
      return;
    }
    move();
    updatePosition(STEP_SIZE);
  }

  private void stateAttackMode() {
    fire(lockedTarget.getObjectDirection());
    IRadarResult enemy = nearestEnemy();
    if (enemy == null) {
      transitionTo(MOVE_FORWARD);
    }
    // L'ennemi ciblé est probablement mort,
    // il faut s'en éloigner pour ne pas donner d'information
    if (enemy == null && lockedTarget != null) {
      transitionTo(MOVE_FORWARD);
    }
    lockedTarget = enemy;
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
    if (myHp < LOW_HEALTH_RATIO) {
      transitionTo(DODGE);
      return;
    }
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      transitionTo(ATTACK_MODE);
    }
  }

  private void stateFire() {
    if (myHp < LOW_HEALTH_RATIO) {
      transitionTo(DODGE);
      return;
    }
    if (lockedTarget != null) {
      fire(lockedTarget.getObjectDirection());
    }
    transitionTo(REPOSITION);
  }

  private void stateReposition() {
    if (myHp < LOW_HEALTH_RATIO) {
      transitionTo(DODGE);
      return;
    }
    if (repositionSteps <= 0) {
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
    if (dodgeSteps <= 0) {
      transitionTo(IDLE_WATCH);
      return;
    }
    moveBack();
    updatePosition(-STEP_SIZE);
    dodgeSteps--;
  }


  private void transitionTo(State newState) {
    sendLogMessage("[MainBot:" + id() + "] " + currentState + " → " + newState);
    if (newState == DODGE) dodgeSteps = 5;
    if (newState == REPOSITION) {
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

  private String id() {
    return switch (role) {
      case M1   -> "M1";
      case M2   -> "M2";
      case M3   -> "M3";
      default     -> "?";
    };
  }
}