package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.IFrontSensorResult;
import characteristics.Parameters;
import robotsimulator.Brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class NoeAbstractBot extends Brain {

  protected static class Position {
    private static final double EPSILON = 1e-6;
    private double x;
    private double y;

    Position(double x, double y) {
      this.x = x;
      this.y = y;
    }

    public boolean isClose(Position p) {
      return Math.abs(x - p.x) < EPSILON &&
        Math.abs(y - p.y) < EPSILON;
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
  }

  //  Format broadcast : "id:state|x:val|y:val|tx:val|ty:val|hp:val|ta:val"
  //  Champ "ta" (target age en ticks) ajouté pour la fraîcheur de cible.
  protected record BotMessage(int senderId, State senderState,
                              Position myPos,
                              Position targetPos,
                              double hp, int targetAge) {}

  protected static class TargetInfo {
    Position pos;
    int age;          // nombre de ticks depuis la dernière observation
    boolean valid;

    TargetInfo() {
      pos = new Position(Double.NaN, Double.NaN);
      valid = false;
      age = Integer.MAX_VALUE;
    }

    void update(double nx, double ny) {
      pos.setX(nx);
      pos.setY(ny);
      age = 0;
      valid = true;
    }

    void tick() { if (valid) age++; }

    boolean isStale() { return !valid || age > NoeAbstractBot.MAX_TARGET_AGE; }
  }

  protected static final int MAX_TARGET_AGE = 30; // ticks avant d'oublier la cible

  protected enum State {
    MOVE_FORWARD,
    IDLE_WATCH,
    FIRE,
    REPOSITION,
    DODGE,
    ATTACK_MODE,
    MOVE_SLALOM,
    RADAR_MODE,
    AVOID_OBSTACLE,
    AVOID_DEAD_ZONE
  }

  protected static final int M1 = 0;
  protected static final int M2 = 1;
  protected static final int M3 = 2;
  protected static final int S1 = 3;
  protected static final int S2 = 4;

  protected static final double ANGLEPRECISION = 0.001;
  protected static final double SECONDARY_RADAR_RANGE = Parameters.teamASecondaryBotFrontalDetectionRange;
  protected static final double FIRE_RANGE = Parameters.bulletRange;

  protected final ArrayList<BotMessage> teamMessages = new ArrayList<>();

  protected double myX = 0;
  protected double myY = 0;

  protected final TargetInfo target = new TargetInfo();

  protected double targetX() { return target.pos.getX(); }
  protected double targetY() { return target.pos.getY(); }
  protected boolean targetFound() { return target.valid && !target.isStale(); }
  protected List<Position> wreckedEnemiesPos = new ArrayList<>();
  protected IRadarResult nearestEnemy = null;

  protected double myHp = 1.0;
  protected int myId;
  protected State previousState;
  protected State currentState;
  protected boolean isTeamA;

  protected int curveN;
  protected int curveK;
  protected int curveTick = 0;
  protected boolean curveMoving;
  protected boolean curveAlternateState = false;
  protected boolean curveActive = false;
  protected Parameters.Direction curveTurnDir;

  // ------------------------------------------------------------------ //

  protected void initState(State initialState, double startX, double startY) {
    currentState = initialState;
    myX = startX;
    myY = startY;
    myHp = getHealth();
  }

  protected void stepState() {
    myHp = getHealth();
    target.tick();          // vieillit la cible à chaque tick
    receiveMessages();
    sendLogMessage("[Bot:" + myId + "] " + round2(myX) + "," + round2(myY)
        + (target.valid ? " tgt=(" + round2(target.pos.getX()) + "," + round2(target.pos.getY()) + ") age=" + target.age : ""));
    onStep();
  }

  protected abstract void onStep();

  protected void broadcastStatus() {
    String msg = myId + ":" + currentState
        + "|x:" + round2(myX)
        + "|y:" + round2(myY)
        + "|tx:" + (target.valid ? round2(target.pos.getX()) : "NaN")
        + "|ty:" + (target.valid ? round2(target.pos.getY()) : "NaN")
        + "|hp:" + round2(myHp)
        + "|ta:" + (target.valid ? target.age : 9999);
    broadcast(msg);
  }

  protected void mergeTeamTargets() {
    for (BotMessage bm : teamMessages) {
      if (Double.isNaN(bm.targetPos().getX()) || Double.isNaN(bm.targetPos().getY())) continue;
      // On met à jour uniquement si la cible alliée est plus fraîche que la nôtre
      if (!target.valid || bm.targetAge() < target.age) {
        target.update(bm.targetPos().getX(), bm.targetPos().getY());
        // On simule l'âge reçu (le message a voyagé 1 tick)
        target.age = bm.targetAge() + 1;
      }
    }
  }

  private void addWreckedEnnemies(IRadarResult enemy) {
    double ex = myX + enemy.getObjectDistance() * Math.cos(enemy.getObjectDirection());
    double ey = myY + enemy.getObjectDistance() * Math.sin(enemy.getObjectDirection());
    Position newPos = new Position(ex, ey);
    boolean alreadyKnown = wreckedEnemiesPos.stream()
      .anyMatch(p -> p.isClose(newPos));
    if (!alreadyKnown) {
      wreckedEnemiesPos.add(newPos);
    }
  }

  private void receiveMessages() {
    teamMessages.clear();
    for (String raw : fetchAllMessages()) {
      BotMessage msg = parseMessage(raw);
      if (msg != null) teamMessages.add(msg);
    }
  }

  private BotMessage parseMessage(String raw) {
    try {
      String[] sections = raw.split("\\|");
      String[] header = sections[0].split(":");
      int senderId = Integer.parseInt(header[0]);
      State senderState = State.valueOf(header[1]);

      if (senderId == myId) return null;

      double x = Double.NaN, y = Double.NaN;
      double tx = Double.NaN, ty = Double.NaN;
      double hp = 1.0;
      int ta = 9999;

      for (int i = 1; i < sections.length; i++) {
        String[] kv = sections[i].split(":");
        if (kv.length < 2) continue;
        switch (kv[0]) {
          case "x"  -> x  = Double.parseDouble(kv[1]);
          case "y"  -> y  = Double.parseDouble(kv[1]);
          case "tx" -> tx = parseDoubleOrNaN(kv[1]);
          case "ty" -> ty = parseDoubleOrNaN(kv[1]);
          case "hp" -> hp = Double.parseDouble(kv[1]);
          case "ta" -> ta = Integer.parseInt(kv[1]);
        }
      }
      return new BotMessage(senderId, senderState, new Position(x, y), new Position(tx, ty), hp, ta);

    } catch (Exception e) {
      return null;
    }
  }

  private double parseDoubleOrNaN(String s) {
    if ("NaN".equals(s)) return Double.NaN;
    return Double.parseDouble(s);
  }

  // ------------------------------------------------------------------ //

  protected boolean isFrontObstacle() {
    return !(detectFront().getObjectType() == IFrontSensorResult.Types.NOTHING);
  }

  protected void scanAround() {
    double minDist = SECONDARY_RADAR_RANGE;
    nearestEnemy = null;

    for (IRadarResult r : detectRadar()) {
      if (isWreckedEnemy(r)) addWreckedEnnemies(r);

      if (isEnemy(r) && r.getObjectDistance() < minDist) {
        minDist = r.getObjectDistance();
        nearestEnemy = r;
      }
    }

    if (nearestEnemy != null) {
      double ex = myX + nearestEnemy.getObjectDistance() * Math.cos(nearestEnemy.getObjectDirection());
      double ey = myY + nearestEnemy.getObjectDistance() * Math.sin(nearestEnemy.getObjectDirection());
      target.update(ex, ey);
    }
  }

  protected boolean isEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.OpponentMainBot
        || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
  }

  private boolean isWreckedEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.Wreck;
  }

  protected boolean isSecondaryEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
  }

  protected boolean isDead() {
    return getHealth() <= 0;
  }

  protected boolean isInDeadZone(double x, double y) {
    for (Position wreck : wreckedEnemiesPos) {
      double dx = x - wreck.getX();
      double dy = y - wreck.getY();
      if (dx * dx + dy * dy < SECONDARY_RADAR_RANGE * SECONDARY_RADAR_RANGE) return true;
    }
    return false;
  }

  protected double safeAngle(double intendedAngle, double stepSize) {
    if (isInDeadZone(myX, myY)) {
      // On est déjà dedans → calculer la direction vers la sortie la plus proche
      Position nearest = wreckedEnemiesPos.stream()
        .filter(w -> {
          double dx = myX - w.getX(), dy = myY - w.getY();
          return dx * dx + dy * dy < SECONDARY_RADAR_RANGE * SECONDARY_RADAR_RANGE;
        })
        .min(Comparator.comparingDouble(w -> {
          double dx = myX - w.getX(), dy = myY - w.getY();
          return dx * dx + dy * dy;
        }))
        .orElse(null);

      if (nearest != null) {
        // Angle qui s'éloigne de l'épave
        return normalizeAngle(Math.atan2(myY - nearest.getY(), myX - nearest.getX()));
      }
    }
    // On est dehors → vérifier que le prochain pas ne rentre pas
    double nextX = myX + stepSize * Math.cos(intendedAngle);
    double nextY = myY + stepSize * Math.sin(intendedAngle);
    if (!isInDeadZone(nextX, nextY)) return intendedAngle; // aucun problème
    // Le prochain pas entre dans une zone → chercher angle libre le plus proche
    for (int i = 1; i <= 18; i++) {
      double offset = i * (Math.PI / 18);
      for (double sign : new double[]{1, -1}) {
        double candidate = normalizeAngle(intendedAngle + sign * offset);
        double cx = myX + stepSize * Math.cos(candidate);
        double cy = myY + stepSize * Math.sin(candidate);
        if (!isInDeadZone(cx, cy)) return candidate;
      }
    }
    return normalizeAngle(intendedAngle + Math.PI);
  }

  // ------------------------------------------------------------------ //

  protected double normalizeAngle(double angle) {
    while (angle > Math.PI)  angle -= 2 * Math.PI;
    while (angle < -Math.PI) angle += 2 * Math.PI;
    return angle;
  }

  protected double angleTo(double x, double y) {
    return Math.atan2(y - myY, x - myX);
  }

  protected double distanceTo(double x, double y) {
    double dx = x - myX, dy = y - myY;
    return Math.sqrt(dx * dx + dy * dy);
  }

  protected void updatePosition(double stepSize) {
    myX += stepSize * Math.cos(getHeading());
    myY += stepSize * Math.sin(getHeading());
  }

  protected boolean isAligned(double targetAngle, double threshold) {
    return Math.abs(normalizeAngle(targetAngle - getHeading())) < threshold;
  }

  protected double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  protected boolean isSameDirection(double dir1, double dir2) {
    double diff = Math.abs(normalizeAngle(dir1) - normalizeAngle(dir2));
    return diff < ANGLEPRECISION || Math.abs(diff - 2 * Math.PI) < ANGLEPRECISION;
  }

  protected void startCurvedMove(int n, int k, Parameters.Direction dir, boolean alternate) {
    curveN = n; curveK = k; curveTurnDir = dir;
    curveMoving = true; curveTick = 0; curveActive = true;
    curveAlternateState = alternate;
  }

  protected boolean stepCurvedMove(double stepSize) {
    if (!curveActive) return false;
    if (curveMoving) {
      move();
      updatePosition(stepSize);
      curveTick++;
      if (curveTick >= curveN) { curveTick = 0; curveMoving = false; }
    } else {
      stepTurn(curveTurnDir);
      curveTick++;
      if (curveTick >= curveK) {
        curveTick = 0; curveMoving = true;
        if (curveAlternateState)
          curveTurnDir = (curveTurnDir == Parameters.Direction.RIGHT)
              ? Parameters.Direction.LEFT : Parameters.Direction.RIGHT;
      }
    }
    return true;
  }

  protected void stopCurvedMove() { curveActive = false; }
  protected boolean isCurvedMoveActive() { return curveActive; }
}