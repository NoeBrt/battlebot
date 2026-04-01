package algorithms.lab;

import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import characteristics.Parameters;
import robotsimulator.Brain;

import java.util.*;

public abstract class NoeAbstractBot extends Brain {

  protected static class Position {
    private static final double EPSILON = 1e-6;
    private double x, y;

    Position(double x, double y) {
      this.x = x;
      this.y = y;
    }

    public boolean isClose(Position p) {
      return Math.abs(x - p.x) < EPSILON && Math.abs(y - p.y) < EPSILON;
    }

    public double getX() {
      return x;
    }

    public double getY() {
      return y;
    }

    public void setX(double x) {
      this.x = x;
    }

    public void setY(double y) {
      this.y = y;
    }
  }

  protected record BotMessage(int senderId,
                              Position myPos,
                              double hp,
                              List<TargetInfo> targets) {
  }

  protected static class TargetInfo {
    Position pos;
    Position prevPos;
    int age;
    boolean valid;
    boolean isSecondary;

    TargetInfo() {
      pos     = new Position(Double.NaN, Double.NaN);
      prevPos = new Position(Double.NaN, Double.NaN);
      valid   = false;
      age     = Integer.MAX_VALUE;
      isSecondary = false;
    }

    void update(double nx, double ny, boolean secondary) {
      if (valid) { prevPos.setX(pos.getX()); prevPos.setY(pos.getY()); }
      else       { prevPos.setX(nx);         prevPos.setY(ny); }
      pos.setX(nx); pos.setY(ny);
      age = 0; valid = true;
      isSecondary = secondary;
    }

    void update(double nx, double ny) { update(nx, ny, false); }

    /** Vélocité estimée (pixels/tick). Zéro si première observation. */
    double velocityX() { return pos.getX() - prevPos.getX(); }
    double velocityY() { return pos.getY() - prevPos.getY(); }

    void tick() { if (valid) age++; }
    boolean isStale() { return !valid || age > MAX_TARGET_AGE; }
  }

  public enum BotAction {
    DODGE(0),
    FIRE(1),
    TURN_LEFT(2), TURN_RIGHT(2),
    MOVE_FWD(3), MOVE_BACK(3);

    public final int priority;

    BotAction(int p) {
      this.priority = p;
    }
  }

  protected static final int MAX_TARGET_AGE = 30;
  protected static final int M1 = 0, M2 = 1, M3 = 2, S1 = 3, S2 = 4;
  protected static final double ANGLEPRECISION = 0.001;
  protected static final double SECONDARY_RADAR_RANGE =
    Parameters.teamASecondaryBotFrontalDetectionRange;
  protected static final double FIRE_RANGE = Parameters.bulletRange;
  protected static final double STEP_TURN = 0.01 * Math.PI;
  protected static final double BULLET_VELOCITY = Parameters.bulletVelocity;


  protected final PriorityQueue<BotAction> actionQueue =
    new PriorityQueue<>(Comparator.comparingInt(a -> a.priority));

  protected final ArrayList<BotMessage> teamMessages = new ArrayList<>();
  protected final List<Position> wreckedEnemiesPos = new ArrayList<>();
  protected final List<TargetInfo> targets = new ArrayList<>();

  protected double myX = 0, myY = 0;
  protected double targetAngle;
  protected final TargetInfo target = new TargetInfo();
  protected IRadarResult nearestEnemy = null;
  protected double myHp = 1.0;
  protected int myId;
  protected boolean isTeamA;

  protected void initState(double startX, double startY) {
    myX = startX;
    myY = startY;
    myHp = getHealth();
  }

  protected void stepState() {
    myHp = getHealth();
    target.tick();
    receiveMessages();
    sendLogMessage("[Bot:" + myId + "] "
      + round2(myX) + "," + round2(myY)
      + (target.valid
      ? " tgt=(" + round2(target.pos.getX()) + "," + round2(target.pos.getY())
      + ") age=" + target.age
      : ""));
    onStep();
  }

  protected abstract void onStep();

  protected void broadcastStatus() {
    StringBuilder t = new StringBuilder();
    for (int i = 0; i < targets.size(); i++) {
      TargetInfo target = targets.get(i);
      if (i > 0) t.append(";");
      t.append(round2(target.pos.getX())).append(",")
        .append(round2(target.pos.getY())).append(",")
        .append(target.age).append(",")
        .append(target.isSecondary ? 1 : 0);
    }
    String msg = myId
      + "|x:" + round2(myX)
      + "|y:" + round2(myY)
      + "|hp:" + round2(myHp)
      + "|targets:" + t;
    broadcast(msg);
  }

  protected void mergeTeamTargets() {
    double bestDistSq = Double.POSITIVE_INFINITY;
    double bestX = Double.NaN;
    double bestY = Double.NaN;
    for (BotMessage bm : teamMessages) {
      for (TargetInfo t : bm.targets()) {
        double tx = t.pos.getX();
        double ty = t.pos.getY();
        if (Double.isNaN(tx)) continue;
        double dx = tx - myX;
        double dy = ty - myY;
        double distSq = dx * dx + dy * dy;
        if (distSq < bestDistSq) {
          bestDistSq = distSq;
          bestX = tx;
          bestY = ty;
        }
      }
    }
    if (!Double.isNaN(bestX)) {
      target.update(bestX, bestY);
    }
  }

  protected void flushBelow(int threshold) {
    actionQueue.removeIf(a -> a.priority > threshold);
  }

  /**
   * Vide toute la queue.
   */
  protected void flushAll() {
    actionQueue.clear();
  }

  protected void enqueue(BotAction... actions) {
    for (BotAction a : actions) actionQueue.add(a);
  }

  protected boolean executeNext() {
    if (actionQueue.isEmpty()) return false;
    dispatch(actionQueue.poll());
    return true;
  }

  /**
   * À surcharger si un bot a des actions spécifiques supplémentaires.
   */
  protected void dispatch(BotAction action) {
    switch (action) {
      case TURN_LEFT -> stepTurn(Parameters.Direction.LEFT);
      case TURN_RIGHT -> stepTurn(Parameters.Direction.RIGHT);
      case MOVE_FWD -> {
        move();
        updatePosition(Parameters.teamAMainBotSpeed);
      }
      case MOVE_BACK, DODGE -> {
        moveBack();
        updatePosition(-Parameters.teamAMainBotSpeed);
      }
      case FIRE -> {
        if (targetFound()) fire(angleTo(target.pos.getX(), target.pos.getY()));
      }
    }
  }

  protected boolean isFrontObstacle() {
    return detectFront().getObjectType() != IFrontSensorResult.Types.NOTHING;
  }

  protected boolean isFrontWall() {
    return detectFront().getObjectType() == IFrontSensorResult.Types.WALL;
  }

  protected void scanAround(double radarRange) {
    double minDist = radarRange;
    nearestEnemy = null;

    for (IRadarResult r : detectRadar()) {
      if (isWreckedEnemy(r)) addWreckedEnemy(r);
      if (isEnemy(r) && r.getObjectDistance() < minDist) {
        minDist = r.getObjectDistance();
        nearestEnemy = r;
      }
    }

    if (nearestEnemy != null) {
      double ex = myX + nearestEnemy.getObjectDistance() * Math.cos(nearestEnemy.getObjectDirection());
      double ey = myY + nearestEnemy.getObjectDistance() * Math.sin(nearestEnemy.getObjectDirection());
      boolean isSecondary = isSecondaryEnemy(nearestEnemy);
      target.update(ex, ey);
    }
  }

  protected void predictiveFire() {
    if (!targetFound()) return;

    double dx = target.pos.getX() - myX;
    double dy = target.pos.getY() - myY;
    double vx = target.velocityX();
    double vy = target.velocityY();
    double Vb = BULLET_VELOCITY;

    double a = vx*vx + vy*vy - Vb*Vb;
    double b = 2 * (dx*vx + dy*vy);
    double c = dx*dx + dy*dy;

    double aimAngle;

    if (Math.abs(a) < 1e-6) {
      aimAngle = angleTo(target.pos.getX(), target.pos.getY());
    } else {
      double discriminant = b*b - 4*a*c;

      if (discriminant < 0) {
        aimAngle = angleTo(target.pos.getX(), target.pos.getY());
      } else {
        double sqrtD = Math.sqrt(discriminant);
        double t1 = (-b - sqrtD) / (2*a);
        double t2 = (-b + sqrtD) / (2*a);

        double t = -1;
        if (t1 > 0 && t2 > 0) t = Math.min(t1, t2);
        else if (t1 > 0)       t = t1;
        else if (t2 > 0)       t = t2;

        if (t < 0) {
          aimAngle = angleTo(target.pos.getX(), target.pos.getY());
        } else {
          // Point d'impact prédit
          double impactX = target.pos.getX() + vx * t;
          double impactY = target.pos.getY() + vy * t;
          aimAngle = Math.atan2(impactY - myY, impactX - myX);
        }
      }
    }

    fire(normalizeAngle(aimAngle));
  }

  protected boolean isEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.OpponentMainBot
      || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
  }

  protected boolean isSecondaryEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
  }

  private boolean isWreckedEnemy(IRadarResult r) {
    return r.getObjectType() == IRadarResult.Types.Wreck;
  }

  protected boolean isDead() {
    return getHealth() <= 0;
  }

  protected boolean isInDeadZone(double x, double y) {
    for (Position w : wreckedEnemiesPos) {
      double dx = x - w.getX(), dy = y - w.getY();
      if (dx * dx + dy * dy < SECONDARY_RADAR_RANGE * SECONDARY_RADAR_RANGE) return true;
    }
    return false;
  }

  protected double safeAngle(double intendedAngle, double stepSize) {
    if (isInDeadZone(myX, myY)) {
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
      if (nearest != null)
        return normalizeAngle(Math.atan2(myY - nearest.getY(), myX - nearest.getX()));
    }
    double nextX = myX + stepSize * Math.cos(intendedAngle);
    double nextY = myY + stepSize * Math.sin(intendedAngle);
    if (!isInDeadZone(nextX, nextY)) return intendedAngle;

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

  protected void turnToward(double angle) {
    double diff = normalizeAngle(angle - getHeading());
    stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
  }

  protected double normalizeAngle(double a) {
    while (a > Math.PI) a -= 2 * Math.PI;
    while (a < -Math.PI) a += 2 * Math.PI;
    return a;
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

  protected boolean isAligned(double angle, double threshold) {
    return Math.abs(normalizeAngle(angle - getHeading())) < threshold;
  }

  protected double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  protected boolean isSameDirection(double d1, double d2) {
    double diff = Math.abs(normalizeAngle(d1) - normalizeAngle(d2));
    return diff < ANGLEPRECISION || Math.abs(diff - 2 * Math.PI) < ANGLEPRECISION;
  }

  protected double targetX() {
    return target.pos.getX();
  }

  protected double targetY() {
    return target.pos.getY();
  }

  protected boolean targetFound() {
    return target.valid && !target.isStale();
  }

  private void addWreckedEnemy(IRadarResult r) {
    double ex = myX + r.getObjectDistance() * Math.cos(r.getObjectDirection());
    double ey = myY + r.getObjectDistance() * Math.sin(r.getObjectDirection());
    Position newPos = new Position(ex, ey);
    if (wreckedEnemiesPos.stream().noneMatch(p -> p.isClose(newPos)))
      wreckedEnemiesPos.add(newPos);
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
      String[] parts = raw.split("\\|");
      int senderId = Integer.parseInt(parts[0]);
      if (senderId == myId) return null;
      double x = Double.NaN, y = Double.NaN;
      double hp = 1.0;
      List<TargetInfo> targets = new ArrayList<>();
      for (int i = 1; i < parts.length; i++) {
        String[] kv = parts[i].split(":");
        if (kv.length < 2) continue;
        switch (kv[0]) {
          case "x" -> x = Double.parseDouble(kv[1]);
          case "y" -> y = Double.parseDouble(kv[1]);
          case "hp" -> hp = Double.parseDouble(kv[1]);
          case "targets" -> {
            if (!kv[1].isEmpty()) {
              String[] tList = kv[1].split(";");
              for (String t : tList) {
                String[] vals = t.split(",");
                if (vals.length < 4) continue;
                double tx = Double.parseDouble(vals[0]);
                double ty = Double.parseDouble(vals[1]);
                int age = Integer.parseInt(vals[2]);
                boolean sec = Integer.parseInt(vals[3]) == 1;
                TargetInfo target = new TargetInfo();
                target.update(tx, ty, sec);
                targets.add(target);
              }
            }
          }
        }
      }
      return new BotMessage(senderId, new Position(x, y), hp, targets);
    } catch (Exception e) {
      System.out.println("iiiiiiii");
      return null;
    }
  }

  private double parseDoubleOrNaN(String s) {
    return "NaN".equals(s) ? Double.NaN : Double.parseDouble(s);
  }
}