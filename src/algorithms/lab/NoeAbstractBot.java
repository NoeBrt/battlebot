package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.IFrontSensorResult;
import characteristics.Parameters;
import robotsimulator.Brain;

import java.util.ArrayList;

public abstract class NoeAbstractBot extends Brain {

  // Format broadcast : "id:state|x:val|y:val|tx:val|ty:val|hp:val"
  protected record BotMessage(int senderId, int senderState, double x, double y,
                              double targetX, double targetY, double hp) {
    protected boolean targetFound() {
      return !Double.isNaN(targetX) && !Double.isNaN(targetY);
    }
  }

  protected enum State {
    MOVE_FORWARD,
    IDLE_WATCH,
    FIRE,
    REPOSITION,
    DODGE,
    ATTACK_MODE,
    MOVE_SLALOM
    }

  protected static final int M1  = 0;
  protected static final int M2 = 1;
  protected static final int M3  = 2;
  protected static final int S1 = 3;
  protected static final int S2 = 4;

  protected static final double ANGLEPRECISION = 0.001;
  protected static final double SECONDARY_RADAR_RANGE = Parameters.teamASecondaryBotFrontalDetectionRange;

  protected final ArrayList<BotMessage> teamMessages = new ArrayList<>();

  protected double myX = 0;
  protected double myY = 0;
  protected double targetX = Double.NaN; // cible courante estimée
  protected double targetY = Double.NaN;
  protected double myHp = 1.0;
  protected int myId;
  protected State currentState;
  protected boolean isTeamA;

  protected int curveN;                          // ticks d'avance par segment
  protected int curveK;                          // ticks de rotation par segment
  protected int curveTick      = 0;              // tick courant dans le segment
  protected boolean curveMoving;                 // true = phase move, false = phase turn
  protected boolean curveAlternateState = false; // alternate L/R à chaque segment
  protected boolean curveActive         = false;
  protected Parameters.Direction curveTurnDir;

  protected void initState(State initialState, double startX, double startY) {
    currentState = initialState;
    myX = startX;
    myY = startY;
    myHp = getHealth();
  }

  protected void stepState() {
    myHp = getHealth();
    receiveMessages();
    sendLogMessage("[MainBot: " + myId + "] " + myX + "," + myY );
    onStep();
  }

  protected abstract void onStep();

  protected void broadcastStatus() {
    String msg = myId + ":" + currentState
      + "|x:" + round2(myX)
      + "|y:" + round2(myY)
      + "|tx:" + round2(targetX)
      + "|ty:" + round2(targetY)
      + "|hp:" + round2(myHp);
    broadcast(msg);
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
      int senderId    = Integer.parseInt(header[0]);
      int senderState = Integer.parseInt(header[1]);

      if (senderId == myId) return null; // ignore ses propres messages

      double x = Double.NaN, y = Double.NaN;
      double tx = Double.NaN, ty = Double.NaN;
      double hp = 1.0;

      for (int i = 1; i < sections.length; i++) {
        String[] kv = sections[i].split(":");
        if (kv.length < 2) continue;
        switch (kv[0]) {
          case "x"  -> x  = Double.parseDouble(kv[1]);
          case "y"  -> y  = Double.parseDouble(kv[1]);
          case "tx" -> tx = Double.parseDouble(kv[1]);
          case "ty" -> ty = Double.parseDouble(kv[1]);
          case "hp" -> hp = Double.parseDouble(kv[1]);
        }
      }
      return new BotMessage(senderId, senderState, x, y, tx, ty, hp);

    } catch (Exception e) {
      return null; // message malformé — ignoré
    }
  }

  // ----------------------------------------------------------

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
    return nearest;
  }

  protected boolean isEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.OpponentMainBot
      || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
  }

  protected boolean isSecondaryEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
  }

  // ----------------------------------------------------------

  protected double normalizeAngle(double angle) {
    while (angle >  Math.PI) angle -= 2 * Math.PI;
    while (angle < -Math.PI) angle += 2 * Math.PI;
    return angle;
  }

  protected double angleTo(double x, double y) {
    return Math.atan2(y - myY, x - myX);
  }

  protected double distanceTo(double x, double y) {
    double dx = x - myX;
    double dy = y - myY;
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

  // alternate=false : même sens → arc/orbital   |   alternate=true : L/R → slalom/zigzag
  protected void startCurvedMove(int n, int k, Parameters.Direction dir, boolean alternate) {
    curveN              = n;
    curveK              = k;
    curveTurnDir        = dir;
    curveMoving         = true;
    curveTick           = 0;
    curveActive         = true;
    curveAlternateState = alternate;
  }

  // Exécute un tick du mouvement courbé.
  // Retourne true tant que le mouvement est actif, false si stopCurvedMove() a été appelé.
  // À appeler une fois par step dans onStep() quand le bot est en mode déplacement courbé.
  protected boolean stepCurvedMove(double stepSize) {
    if (!curveActive) return false;

    if (curveMoving) {
      move();
      updatePosition(stepSize);
      curveTick++;
      if (curveTick >= curveN) {
        curveTick   = 0;
        curveMoving = false; // bascule en phase rotation
      }
    } else {
      stepTurn(curveTurnDir);
      curveTick++;
      if (curveTick >= curveK) {
        curveTick   = 0;
        curveMoving = true; // bascule en phase avance
        if (curveAlternateState) // inverse la direction pour slalom
          curveTurnDir = (curveTurnDir == Parameters.Direction.RIGHT)
            ? Parameters.Direction.LEFT
            : Parameters.Direction.RIGHT;
      }
    }
    return true;
  }

  protected void stopCurvedMove() {
    curveActive = false;
  }

  protected boolean isCurvedMoveActive() {
    return curveActive;
  }
}