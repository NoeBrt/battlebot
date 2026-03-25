package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.IFrontSensorResult;
import robotsimulator.Brain;

import java.util.ArrayList;

public abstract class NoeAbstractBot extends Brain {

  // Format broadcast : "id:state|x:val|y:val|tx:val|ty:val|hp:val"
  protected record BotMessage(int senderId, int senderState, double x, double y,
                              double targetX, double targetY, double hp) {}

  protected final ArrayList<BotMessage> teamMessages = new ArrayList<>();

  protected double myX = 0;
  protected double myY = 0;
  protected double targetX = Double.NaN; // cible courante estimée
  protected double targetY = Double.NaN;
  protected double myHp = 1.0;
  protected int myId;
  protected int currentState;

  protected void initState(int initialState, double startX, double startY) {
    currentState = initialState;
    myX = startX;
    myY = startY;
    myHp = getHealth();
  }

  protected void stepState() {
    myHp = getHealth();
    receiveMessages();
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
}