package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

public class NoeSecondaryBot extends NoeAbstractBot {

  private static final double STEP_SIZE = Parameters.teamASecondaryBotSpeed;
  private static final double RADAR_RANGE = Parameters.teamASecondaryBotFrontalDetectionRange;
  private static final double AIM_THRESHOLD = 0.05;
  private static final double ORBIT_RADIUS = 300.0;  // distance orbitale cible
  private static final double ORBIT_REPLAN_THRESHOLD = 20.0; // replanifie si cible dévie de + de N px
  private static final int CURVE_N = 3;
  private static final int CURVE_K = 2;

  private Parameters.Direction slalomDir = Parameters.Direction.LEFT;

  private double lastOrbitTargetX = Double.NaN;
  private double lastOrbitTargetY = Double.NaN;

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
      case S1 -> {
        x = isTeamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX;
        y = isTeamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY;
      }
      case S2 -> {
        x = isTeamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX;
        y = isTeamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY;
      }
    }
    initState(x, y);
    enqueueSlalomBlock();
    sendLogMessage("[SecBot:" + id() + "] spawn=(" + myX + "," + myY + ")");
  }

  @Override
  public void step() {
    stepState();
  }

  @Override
  protected void onStep() {
    scanAround(RADAR_RANGE);
    mergeTeamTargets();
    broadcastStatus();
    if (isDead()) {
      flushAll();
      return;
    }
    if (targetFound()) {
      flushAll();
      enqueueOrbitBlock();
      //enqueueForwardPlan();
    } else if (actionQueue.isEmpty()) {
      enqueueForwardPlan();
    }
    executeNext();
  }

  private void enqueueForwardPlan() {
    double safe = safeAngle(getHeading(), STEP_SIZE);
    if (!isAligned(safe, AIM_THRESHOLD)) enqueueTurnToward(safe);
    for (int i = 0; i < 5; i++) enqueue(BotAction.MOVE_FWD);
  }

  private void maybeReplanOrbit() {
    boolean queueEmpty = actionQueue.isEmpty();
    boolean targetMoved = Double.isNaN(lastOrbitTargetX)
      || distanceTo(lastOrbitTargetX, lastOrbitTargetY)
      > ORBIT_REPLAN_THRESHOLD;  // déviation cible vs plan précédent
    if (queueEmpty || targetMoved) {
      flushAll();
      enqueueOrbitBlock();
      lastOrbitTargetX = targetX();
      lastOrbitTargetY = targetY();
    }
  }

  private void enqueueOrbitBlock() {
    double dist = distanceTo(targetX(), targetY());
    double angleToTgt = angleTo(targetX(), targetY());
    double radialError = dist - ORBIT_RADIUS;
    if (Math.abs(radialError) > STEP_SIZE * 2) {
      double correctionAngle = radialError > 0
        ? angleToTgt                          // avancer vers la cible
        : normalizeAngle(angleToTgt + Math.PI); // reculer
      double safe = safeAngle(correctionAngle, STEP_SIZE);
      enqueueTurnToward(safe);
      int steps = (int) Math.min(Math.abs(radialError) / STEP_SIZE, 10);
      for (int i = 0; i < steps; i++) enqueue(BotAction.MOVE_FWD);
    }

    double tangent = normalizeAngle(angleToTgt + Math.PI / 2);
    double safe = safeAngle(tangent, STEP_SIZE);
    enqueueTurnToward(safe);
    for (int i = 0; i < 8; i++) enqueue(BotAction.MOVE_FWD);
  }

  private void enqueueSlalomBlock() {
    for (int i = 0; i < CURVE_N; i++) enqueue(BotAction.MOVE_FWD);
    BotAction turn = slalomDir == Parameters.Direction.LEFT
      ? BotAction.TURN_LEFT : BotAction.TURN_RIGHT;
    for (int i = 0; i < CURVE_K; i++) enqueue(turn);
    slalomDir = (slalomDir == Parameters.Direction.LEFT)
      ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT;
  }

  private void enqueueTurnToward(double angle) {
    double delta = normalizeAngle(angle - getHeading());
    int ticks = (int) Math.round(Math.abs(delta) / STEP_TURN);
    BotAction dir = delta > 0 ? BotAction.TURN_RIGHT : BotAction.TURN_LEFT;
    for (int i = 0; i < ticks; i++) enqueue(dir);
  }

  private String id() {
    return switch (myId) {
      case S1 -> "S1";
      case S2 -> "S2";
      default -> "?";
    };
  }
}