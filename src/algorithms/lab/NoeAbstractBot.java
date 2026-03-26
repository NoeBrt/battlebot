package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.IFrontSensorResult;
import characteristics.Parameters;
import robotsimulator.Brain;

import java.util.ArrayList;

public abstract class NoeAbstractBot extends Brain {

  // ------------------------------------------------------------------ //
  //  Format broadcast : "id:state|x:val|y:val|tx:val|ty:val|hp:val|ta:val"
  //  Champ "ta" (target age en ticks) ajouté pour la fraîcheur de cible.
  // ------------------------------------------------------------------ //
  protected record BotMessage(int senderId, State senderState,
                              double x, double y,
                              double targetX, double targetY,
                              double hp, int targetAge) {}

  // ------------------------------------------------------------------ //
  //  Suivi de cible avec horodatage (en ticks simulés)
  // ------------------------------------------------------------------ //
  protected static class TargetInfo {
    double x, y;
    int age;          // nombre de ticks depuis la dernière observation
    boolean valid;

    TargetInfo() { valid = false; age = Integer.MAX_VALUE; }

    void update(double nx, double ny) {
      x = nx; y = ny; age = 0; valid = true;
    }

    void tick() { if (valid) age++; }

    /** Une cible est considérée périmée après MAX_TARGET_AGE ticks sans confirmation. */
    boolean isStale(int maxAge) { return !valid || age > maxAge; }
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
    RADAR_MODE
  }

  protected static final int M1 = 0;
  protected static final int M2 = 1;
  protected static final int M3 = 2;
  protected static final int S1 = 3;
  protected static final int S2 = 4;

  protected static final double ANGLEPRECISION = 0.001;
  protected static final double SECONDARY_RADAR_RANGE = Parameters.teamASecondaryBotFrontalDetectionRange;

  protected final ArrayList<BotMessage> teamMessages = new ArrayList<>();

  protected double myX = 0;
  protected double myY = 0;

  // Remplace les anciens targetX/targetY/targetFound par un TargetInfo structuré
  protected final TargetInfo target = new TargetInfo();

  /** Accesseurs de compatibilité (évite de casser les appels existants) */
  protected double targetX() { return target.x; }
  protected double targetY() { return target.y; }
  protected boolean targetFound() { return target.valid && !target.isStale(MAX_TARGET_AGE); }

  protected double myHp = 1.0;
  protected int myId;
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
        + (target.valid ? " tgt=(" + round2(target.x) + "," + round2(target.y) + ") age=" + target.age : ""));
    onStep();
  }

  protected abstract void onStep();

  // ------------------------------------------------------------------ //
  //  Broadcast — inclut l'âge de la cible pour que les alliés filtrent
  // ------------------------------------------------------------------ //
  protected void broadcastStatus() {
    String msg = myId + ":" + currentState
        + "|x:" + round2(myX)
        + "|y:" + round2(myY)
        + "|tx:" + (target.valid ? round2(target.x) : "NaN")
        + "|ty:" + (target.valid ? round2(target.y) : "NaN")
        + "|hp:" + round2(myHp)
        + "|ta:" + (target.valid ? target.age : 9999);
    broadcast(msg);
  }

  // ------------------------------------------------------------------ //
  //  Fusion de cible inter-alliés : accepte la cible la plus fraîche
  // ------------------------------------------------------------------ //
  protected void mergeTeamTargets() {
    for (BotMessage bm : teamMessages) {
      if (Double.isNaN(bm.targetX()) || Double.isNaN(bm.targetY())) continue;
      // On met à jour uniquement si la cible alliée est plus fraîche que la nôtre
      if (!target.valid || bm.targetAge() < target.age) {
        target.update(bm.targetX(), bm.targetY());
        // On simule l'âge reçu (le message a voyagé 1 tick)
        target.age = bm.targetAge() + 1;
      }
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
      return new BotMessage(senderId, senderState, x, y, tx, ty, hp, ta);

    } catch (Exception e) {
      return null;
    }
  }

  private double parseDoubleOrNaN(String s) {
    if ("NaN".equals(s)) return Double.NaN;
    return Double.parseDouble(s);
  }

  // ------------------------------------------------------------------ //

  protected boolean isFrontEnemyDetected() {
    IFrontSensorResult.Types t = detectFront().getObjectType();
    return t == IFrontSensorResult.Types.OpponentMainBot
        || t == IFrontSensorResult.Types.OpponentSecondaryBot;
  }

  protected boolean isFrontWall() {
    return detectFront().getObjectType() == IFrontSensorResult.Types.WALL;
  }

  protected boolean isFrontObstacle() {
    return !(detectFront().getObjectType() == IFrontSensorResult.Types.NOTHING);
  }

  protected IRadarResult nearestEnemy() {
    IRadarResult nearest = null;
    double minDist = Double.MAX_VALUE;
    for (IRadarResult r : detectRadar()) {
      if (isEnemy(r) && r.getObjectDistance() < minDist) {
        minDist = r.getObjectDistance();
        nearest = r;
      }
    }
    if (nearest != null) {
      double ex = myX + nearest.getObjectDistance() * Math.cos(nearest.getObjectDirection());
      double ey = myY + nearest.getObjectDistance() * Math.sin(nearest.getObjectDirection()); // BUG FIX: sin
      target.update(ex, ey);
    }
    return nearest;
  }

  protected boolean isEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.OpponentMainBot
        || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
  }

  protected boolean isSecondaryEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
  }

  protected boolean isDead() {
    return getHealth() <= 0;
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