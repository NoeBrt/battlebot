package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

public class NoeMainBot extends NoeAbstractBot {

  private static final double AIM_THRESHOLD = 0.05;
  private static final double STEP_SIZE = Parameters.teamAMainBotSpeed;
  private static final double LATERAL_WEIGHT = 0.55;
  private static final double RADAR_RANGE = Parameters.teamBMainBotFrontalDetectionRange;

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
    initState(x, y);
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
    scan();
    executeNext();
  }

  private void scan() {
    // Urgence 0 : deadzone
    if (isInDeadZone(myX, myY)) {
      flushAll();
      enqueueEscapePlan();
      return;
    }

    // Urgence 1 : obstacle frontal — interrompt tout plan de déplacement
    if (isFrontObstacle()) {
      if (onlyMovesLeft() || actionQueue.isEmpty()) {
        flushAll();
        enqueueAvoidObstaclePlan();
      }
      return;
    }

    if (actionQueue.isEmpty() || onlyMovesLeft()) {
      if (targetFound()) {
        //flushBelow(2);
        enqueueAttackPlan();
      } else {
        if (actionQueue.isEmpty()) enqueueForwardPlan();
      }
    }
  }

  private void enqueueAvoidObstaclePlan() {
    // Pivoter de 90° dans le sens qui évite les deadzones
    double leftAngle  = normalizeAngle(getHeading() - Math.PI / 2);
    double rightAngle = normalizeAngle(getHeading() + Math.PI / 2);
    double turnAngle  = lateralScore(leftAngle) >= lateralScore(rightAngle)
      ? leftAngle : rightAngle;

    enqueueTurnToward(leftAngle);
    for (int i = 0; i < 5; i++) enqueue(BotAction.MOVE_FWD);
  }

  private void enqueueAttackPlan() {
    double dist = distanceTo(targetX(), targetY());
    double angle = angleTo(targetX(), targetY());
    if (dist <= FIRE_RANGE) {
      enqueue(BotAction.FIRE);
    } else {
      enqueueTurnToward(angle);
      enqueueForwardPlan();
    }
  }

  private void enqueueArcApproach(double angleToTarget) {
    double lateralL = normalizeAngle(angleToTarget - Math.PI / 2);
    double lateralR = normalizeAngle(angleToTarget + Math.PI / 2);
    double lateral = chooseBetterLateral(lateralL, lateralR);
    double arcAngle = blendAngles(lateral, angleToTarget);
    double safe = safeAngle(arcAngle, STEP_SIZE);

    enqueueTurnToward(safe);

    double correctionAngle = normalizeAngle(arcAngle + (Math.PI / 18));
    for (int i = 0; i < 10; i++) {
      enqueue(BotAction.MOVE_FWD);
      if (i % 4 == 3) enqueueTurnStep(correctionAngle);
    }
  }

  private void enqueueRepositionPlan() {
    double perpAngle = normalizeAngle(angleTo(targetX(), targetY()) + Math.PI / 2);
    double safe = safeAngle(perpAngle, STEP_SIZE);
    enqueueTurnToward(safe);
    for (int i = 0; i < 6; i++) enqueue(BotAction.MOVE_FWD);
  }

  private void enqueueEscapePlan() {
    double escapeAngle = safeAngle(getHeading(), STEP_SIZE);
    enqueueTurnToward(escapeAngle);
    for (int i = 0; i < 8; i++) enqueue(BotAction.MOVE_FWD);
  }

  private void enqueueForwardPlan() {
    double safe = safeAngle(getHeading(), STEP_SIZE);
    if (!isAligned(safe, AIM_THRESHOLD)) enqueueTurnToward(safe);
    for (int i = 0; i < 5; i++) enqueue(BotAction.MOVE_FWD);
  }

  private void enqueueTurnToward(double angle) {
    double delta = normalizeAngle(angle - getHeading());
    int ticks = (int) Math.round(Math.abs(delta) / STEP_TURN);
    BotAction dir = delta > 0 ? BotAction.TURN_RIGHT : BotAction.TURN_LEFT;
    for (int i = 0; i < ticks; i++) enqueue(dir);
  }

  private void enqueueTurnStep(double angle) {
    double delta = normalizeAngle(angle - getHeading());
    enqueue(delta > 0 ? BotAction.TURN_RIGHT : BotAction.TURN_LEFT);
  }

  private boolean onlyMovesLeft() {
    return !actionQueue.isEmpty() &&
      actionQueue.stream().allMatch(a -> a == BotAction.MOVE_FWD || a == BotAction.MOVE_BACK);
  }

  private double chooseBetterLateral(double left, double right) {
    return lateralScore(left) >= lateralScore(right) ? left : right;
  }

  private double lateralScore(double angle) {
    double cx = myX + STEP_SIZE * Math.cos(angle);
    double cy = myY + STEP_SIZE * Math.sin(angle);
    if (isInDeadZone(cx, cy)) return Double.NEGATIVE_INFINITY;
    return wreckedEnemiesPos.stream()
      .mapToDouble(w -> {
        double dx = cx - w.getX(), dy = cy - w.getY();
        return dx * dx + dy * dy;
      })
      .min().orElse(Double.MAX_VALUE);
  }

  private double blendAngles(double a, double b) {
    return normalizeAngle(a + normalizeAngle(b - a) * (1.0 - LATERAL_WEIGHT));
  }
}