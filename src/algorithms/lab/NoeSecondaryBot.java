package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

import static algorithms.lab.NoeAbstractBot.State.*;

public class NoeSecondaryBot extends NoeAbstractBot {

  private static final double STEP_SIZE = Parameters.teamASecondaryBotSpeed;
  private static final double AIM_THRESHOLD = 0.05;
  private int approachTick = 0;
  private static final int APPROACH_SLOW_FACTOR = 3;

  @Override
  public void activate() {
    isTeamA = (getHeading() == Parameters.EAST);
    isMain = false;
    boolean top = false;
    for (IRadarResult o : detectRadar()) {
      if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) top = true;
    }
    myId = top ? S2 : S1;

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
    if ((myId == S1 && isTeamA) || (myId == S2 && !isTeamA)) {
      startCurvedMove(3, 1, Parameters.Direction.RIGHT, false);
      initState(MOVE_SLALOM, x, y);
    } else {
      startCurvedMove(4, 1, Parameters.Direction.LEFT, false);
      initState(MOVE_SLALOM, x, y);
    }
    sendLogMessage("[SecBot:" + id() + "] spawn=(" + myX + "," + myY + ")");
  }

  @Override
  public void step() {
    stepState();
  }

  @Override
  protected void onStep() {
    scanAround();
    broadcastStatus();
    mergeTeamTargets();
    if (isDead()) transitionTo(IDLE_WATCH);
    switch (currentState) {
      case MOVE_SLALOM -> stateMoveSlalom();
      case MOVE_FORWARD -> stateMoveForward();
      case IDLE_WATCH -> stateIdleWatch();
      case APPROACH -> stateApproach();
      case AVOID_OBSTACLE -> stateAvoidObstacle();
      default -> stateMoveSlalom(); // fallback sécurisé
    }
  }

  private void stateMoveSlalom() {
    if (isFrontObstacle()) {
      transitionTo(AVOID_OBSTACLE);
      return;
    }
    stepCurvedMove(STEP_SIZE);
  }

  private void stateAvoidObstacle() {
    if (!isAligned(targetAngle, AIM_THRESHOLD * 2)) {
      turnToward(targetAngle);
      return;
    }
    if (!isFrontObstacle()) {
      transitionTo(MOVE_SLALOM);
    } else {
      targetAngle = computeAvoidAngle();
    }
  }

  private void stateMoveForward() {
    if (isFrontObstacle()) {
      transitionTo(AVOID_OBSTACLE);
      return;
    }
    move();
    updatePosition(STEP_SIZE);
  }

  private void stateIdleWatch() {

  }

  private void stateApproach() {
    if (!isFrontObstacle()) {
      approachTick = 0;
      transitionTo(myId == S1 ? MOVE_SLALOM : MOVE_FORWARD);
      return;
    }/*
    if (nearestEnemy != null) {
      approachTick = 0;
      transitionTo(IDLE_WATCH);
      return;
    }*/
    approachTick++;
    if (approachTick >= APPROACH_SLOW_FACTOR) {
      approachTick = 0;
      move();
      updatePosition(STEP_SIZE);
    }
  }

  private void transitionTo(State newState) {
    sendLogMessage("[SecBot:" + id() + "] " + currentState + " -> " + newState);
    if (newState == AVOID_OBSTACLE) targetAngle = computeAvoidAngle();
    currentState = newState;
  }

  @SuppressWarnings("unused")
  private void moveToward(double tx, double ty) {
    double angle = angleTo(tx, ty);
    if (!isAligned(angle, AIM_THRESHOLD * 2)) turnToward(angle);
    else {
      move();
      updatePosition(STEP_SIZE);
    }
  }

  private void turnToward(double targetAngle) {
    double diff = normalizeAngle(targetAngle - getHeading());
    stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
  }

  private String id() {
    return switch (myId) {
      case S1 -> "S1";
      case S2 -> "S2";
      default -> "?";
    };
  }
}