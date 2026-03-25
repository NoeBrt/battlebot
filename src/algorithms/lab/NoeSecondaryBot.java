package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

import static algorithms.lab.NoeAbstractBot.State.*;

public class NoeSecondaryBot extends NoeAbstractBot {

  private static final double STEP_SIZE          = Parameters.teamAMainBotSpeed;
  private static final double AIM_THRESHOLD      = 0.05;  // radians
  private static final double LOW_HEALTH_RATIO   = 0.1;

  protected int role;

  @Override
  public void activate() {
    isTeamA = (getHeading() == Parameters.EAST);
    boolean top = false;
    for (IRadarResult o : detectRadar()) {
      if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) top = true;
    }
    if (top) myId = S2;
    else myId = S1;
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
    startCurvedMove(2, 1, Parameters.Direction.LEFT, false);
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
    // Compute team message action in priority
    if (!teamMessages.isEmpty()) {
      for (BotMessage bm : teamMessages) {
        if (bm.targetFound()) {
          targetX = bm.targetX();
          targetY = bm.targetY();
          return;
        }
      }
    }
    switch (currentState) {
      case MOVE_FORWARD     -> stateMoveForward();
      case IDLE_WATCH       -> stateIdleWatch();
      case MOVE_SLALOM      -> stateMoveSlalom();
    }
  }

  private void stateMoveForward() {
    move();
    updatePosition(STEP_SIZE);
  }

  private void stateMoveSlalom() {
    stepCurvedMove(STEP_SIZE);
    updatePosition(STEP_SIZE);
  }

  private void stateIdleWatch() {

  }

  private void transitionTo(State newState) {
    sendLogMessage("[MainBot:" + id() + "] " + currentState + " → " + newState);
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
      case S1   -> "S1";
      case S2   -> "S2";
      default     -> "?";
    };
  }
}