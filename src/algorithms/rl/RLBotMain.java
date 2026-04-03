/* ============================================================================
 * RLBotMain.java — Self-contained RL bot implementation.
 *
 * This file contains all classes needed by the RL bots:
 *   Position, BotState, RLConfig, RLEnemy, RLBotBase, RLBotMain
 *
 * Only RLBotMain is public. RLBotSecondary (separate file) extends RLBotBase.
 * No dependency on algorithms.external.
 * ============================================================================*/
package algorithms.rl;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import robotsimulator.Brain;
import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;

/* ═══════════════════════════════════════════════════════════════════════════
 *  Position
 * ═══════════════════════════════════════════════════════════════════════════*/
class Position {
    private double x, y;

    Position(double x, double y) { this.x = x; this.y = y; }

    void setX(double x) { this.x = x; }
    void setY(double y) { this.y = y; }
    double getX() { return x; }
    double getY() { return y; }

    @Override public String toString() { return "X : " + x + "; Y : " + y; }

    @Override public int hashCode() { return Objects.hash(x, y); }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Position other = (Position) obj;
        return Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
            && Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y);
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 *  BotState — tracks an allied bot's position and status
 * ═══════════════════════════════════════════════════════════════════════════*/
class BotState {
    private Position position = new Position(0, 0);
    private boolean isAlive = true;
    String whoAmI;
    double getHeading;

    BotState() {}
    BotState(double x, double y, boolean alive, String whoAmI, double getHeading) {
        position.setX(x); position.setY(y);
        isAlive = alive;
        this.whoAmI = whoAmI;
        this.getHeading = getHeading;
    }

    void setPosition(double x, double y, double getHeading) {
        position.setX(x); position.setY(y);
        this.getHeading = getHeading;
    }

    Position getPosition() { return position; }
    void setAlive(boolean alive) { isAlive = alive; }
    boolean isAlive() { return isAlive; }
}

/* ═══════════════════════════════════════════════════════════════════════════
 *  RLConfig -- GEN 4 ()
 *  --- Generation 4/40 ---
 *  Gen best fitness : 2.6672
 *  Gen best score   : A=0.6971 B=0.5886
 *  Gen best winRate : 0.7883
 *  Global best      : 2.6672
 *  Sigma            : 0.2424
 *  Time             : 31915.4s
 *  --- Opponent Breakdown (dual-side) ---
 *      vs Yomi         : Fit= 3.325 (A= 3.844 B= 2.806) | WinRate=0.93 (A=1.00 B=0.86)
 *      vs MacDuo       : Fit= 1.179 (A= 3.558 B=-1.199) | WinRate=0.50 (A=1.00 B=0.00)
 *      vs NoeBot       : Fit= 3.239 (A= 3.239 B= 3.239) | WinRate=1.00 (A=1.00 B=1.00)
 *      vs Superhero    : Fit= 4.431 (A= 4.028 B= 4.835) | WinRate=1.00 (A=1.00 B=1.00)
 *      vs AdaptiveK    : Fit= 1.540 (A=-0.189 B= 3.269) | WinRate=0.50 (A=0.00 B=1.00)
 *  --------------------------
 *
 * ═══════════════════════════════════════════════════════════════════════════*/
class RLConfig {

    public static final int    STALE_TTL = 631;
    public static final double TARGET_PROXIMITY_WEIGHT = 202.887309;
    public static final double TARGET_TYPE_BONUS = 78.882525;
    public static final double TARGET_LOWHP_CRITICAL_BONUS = 297.424640;
    public static final double TARGET_LOWHP_MODERATE_BONUS = 182.485306;
    public static final double TARGET_FOCUS_BONUS = 410.158612;
    public static final double TARGET_SAFEFIRE_BONUS = 236.114896;
    public static final double TARGET_STALE_PENALTY = 12.045753;
    public static final double PF_ENEMY_REPEL_STRENGTH = 2.853184;
    public static final double PF_ENEMY_ATTRACT_STRENGTH = 1.718614;
    public static final double PF_TANGENTIAL_STRENGTH = 0.316234;
    public static final double PF_ALLY_REPEL_RANGE = 92.793198;
    public static final double PF_ALLY_REPEL_STRENGTH = 1.225616;
    public static final double PF_WALL_STRENGTH = 0.496162;
    public static final double PF_WRECK_RANGE = 264.326646;
    public static final double HOLD_X_OFFSET = 1292.139388;
    public static final double KITE_MIN_AGGRO = 262.474868;
    public static final double KITE_MAX_AGGRO = 784.031435;
    public static final double KITE_MIN_NORMAL = 304.415413;
    public static final double KITE_MAX_NORMAL = 792.960464;
    public static final double KITE_MIN_DEFEN = 316.166344;
    public static final double KITE_MAX_DEFEN = 1017.547306;
    public static final double HP_RETREAT_MAIN = 51.057507;
    public static final int    NOFIRE_REPOSITION_TICKS = 18;
    public static final double HP_RETREAT_PCT_SEC = 0.240732;
    public static final double FLANK_Y_OFFSET = 378.676869;
    public static final double ADVANCE_X_A = 1992.683174;
    public static final double PATROL_EVASION_RANGE = 249.494684;

    public static final double MAP_WIDTH = 3000.000000;
    public static final double MAP_HEIGHT = 2000.000000;
    public static final double MAP_CX = 1500.000000;
    public static final double MAP_CY = 1000.000000;
    public static final double FORMATION_Y_BASE = 1000.000000;
    public static final double FORMATION_Y_OFFSET = 260.000000;
    public static final double WALL_MARGIN = 200.000000;
    public static final double SAFE_ZONE_X = 200.000000;
    public static final double FLANK_OFFSET = 150.000000;
    public static final double HEALTH_HIGH_THRESHOLD = 200.000000;
    public static final double HEALTH_LOW_THRESHOLD = 100.000000;
    public static final double PATROL_THRESHOLD = 200.000000;
    public static final double FIRING_ANGLE_TOLERANCE = 0.200000;
    public static final double FIRING_SAFETY_RADIUS = 55.000000;
    public static final double TANGENTIAL_SCALE_REF = 100.000000;
    public static final double TANGENTIAL_MIN_DIST = 80.000000;
    public static final double MAX_HEALTH_MAIN = 300.000000;
    public static final double MAX_HEALTH_SEC = 100.000000;

}

/* ═══════════════════════════════════════════════════════════════════════════
 *  RLEnemy — tracks an individual enemy with position history and velocity
 * ═══════════════════════════════════════════════════════════════════════════*/
class RLEnemy {
    double x, y, prevX, prevY, ppX, ppY;
    double speedX, speedY;
    double distance, direction;
    Types type;
    int updateCount;
    int lastSeenTick;
    int estimatedDmg;

    RLEnemy(double x, double y, double d, double dir, Types t, int tick) {
        this.x = prevX = ppX = x;
        this.y = prevY = ppY = y;
        distance = d; direction = dir; type = t;
        updateCount = 0; lastSeenTick = tick; estimatedDmg = 0;
    }

    void update(double nx, double ny, double nd, double ndir, int tick) {
        ppX = prevX; ppY = prevY;
        prevX = x; prevY = y;
        x = nx; y = ny; distance = nd; direction = ndir;
        if (tick > lastSeenTick) updateCount++;
        lastSeenTick = tick;
        if (updateCount >= 2) { speedX = x - prevX; speedY = y - prevY; }
        else { speedX = 0; speedY = 0; }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 *  RLBotBase — abstract base for RL bots (extends Brain directly)
 * ═══════════════════════════════════════════════════════════════════════════*/
abstract class RLBotBase extends Brain {

    // ── Constants ────────────────────────────────────────────────────────
    protected static final String NBOT  = "NBOT";
    protected static final String SBOT  = "SBOT";
    protected static final String MAIN1 = "1";
    protected static final String MAIN2 = "2";
    protected static final String MAIN3 = "3";

    protected static final double ANGLEPRECISION = 0.001;
    private static final double BOT_RADIUS = 50;

    protected enum State { FIRST_RDV, MOVING, MOVING_BACK, TURNING_LEFT, TURNING_RIGHT, FIRE, DEAD }

    // ── Fields (from MacDuoBaseBot) ──────────────────────────────────────
    protected String whoAmI;
    protected Position myPos;
    protected boolean isTeamA;
    protected Map<String, BotState> allyPos = new HashMap<>();
    protected List<double[]> wreckPositions = new ArrayList<>();
    protected State state;
    protected double oldAngle;
    protected double targetX, targetY;
    protected boolean isShooterAvoiding;

    // ── Fields (RL-specific) ─────────────────────────────────────────────
    protected boolean teamA;
    protected int currentTick = 0;
    protected List<RLEnemy> rlEnemies = new ArrayList<>();
    protected RLEnemy lastTarget = null;
    protected String focusedTargetKey = null;

    // ── Constructor ──────────────────────────────────────────────────────
    protected RLBotBase() {
        super();
        allyPos.put(NBOT, new BotState());
        allyPos.put(SBOT, new BotState());
        allyPos.put(MAIN1, new BotState());
        allyPos.put(MAIN2, new BotState());
        allyPos.put(MAIN3, new BotState());
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    public void activate() {
        this.teamA = (getHeading() == Parameters.EAST);
        this.isTeamA = teamA;
        currentTick = 0;
        rlEnemies.clear();
        this.myPos = new Position(0, 0);
    }

    @Override
    public void step() {
        currentTick++;
        sendMyPosition();
        detection();
        pruneStaleEnemies();
        readMessages();
    }

    // ── Navigation ───────────────────────────────────────────────────────

    protected void goTo(double x, double y) {
        double angle = Math.atan2(y - myPos.getY(), x - myPos.getX());
        double forwardDiff = Math.abs(normalize(angle - getHeading()));
        double forwardDiffWrapped = Math.abs(forwardDiff - 2 * Math.PI);
        double minForward = Math.min(forwardDiff, forwardDiffWrapped);

        double backwardBodyAngle = normalize(angle - Math.PI);
        double backwardDiff = Math.abs(normalize(backwardBodyAngle - getHeading()));
        double backwardDiffWrapped = Math.abs(backwardDiff - 2 * Math.PI);
        double minBackward = Math.min(backwardDiff, backwardDiffWrapped);

        if (minBackward < minForward) {
            if (!isSameDirection(getHeading(), backwardBodyAngle)) {
                turnTo(backwardBodyAngle);
            } else {
                myMove(false);
            }
        } else {
            if (!isSameDirection(getHeading(), angle)) {
                turnTo(angle);
            } else {
                myMove(true);
            }
        }
    }

    protected void potentialFieldMove(double kiteMin, double kiteMax) {
        double fx = 0, fy = 0;
        double swirlDir = teamA ? 1.0 : -1.0;

        // 1. Enemy Field
        for (RLEnemy e : rlEnemies) {
            double dx = myPos.getX() - e.x;
            double dy = myPos.getY() - e.y;
            double d = Math.hypot(dx, dy);
            if (d == 0) continue;

            double force = 0;
            if (d < kiteMin) force = RLConfig.PF_ENEMY_REPEL_STRENGTH * (kiteMin - d) / 100.0;
            else if (d > kiteMax) force = -RLConfig.PF_ENEMY_ATTRACT_STRENGTH * (d - kiteMax) / 100.0;

            fx += (dx/d) * force;
            fy += (dy/d) * force;

            // Tangential (distance-scaled, team-aware swirl)
            double tangentialScale = RLConfig.TANGENTIAL_SCALE_REF / Math.max(d, RLConfig.TANGENTIAL_MIN_DIST);
            fx += -(dy/d) * RLConfig.PF_TANGENTIAL_STRENGTH * tangentialScale * swirlDir;
            fy +=  (dx/d) * RLConfig.PF_TANGENTIAL_STRENGTH * tangentialScale * swirlDir;
        }

        // 2. Ally Repel
        for (BotState b : allyPos.values()) {
             if (!b.isAlive() || Math.hypot(b.getPosition().getX() - myPos.getX(), b.getPosition().getY() - myPos.getY()) < 1.0) continue;
             double dx = myPos.getX() - b.getPosition().getX();
             double dy = myPos.getY() - b.getPosition().getY();
             double d = Math.hypot(dx, dy);
             if (d < RLConfig.PF_ALLY_REPEL_RANGE && d > 0) {
                 double force = RLConfig.PF_ALLY_REPEL_STRENGTH * (RLConfig.PF_ALLY_REPEL_RANGE - d) / RLConfig.PF_ALLY_REPEL_RANGE;
                 fx += (dx/d) * force;
                 fy += (dy/d) * force;
             }
        }

        // 3. Wall Repel
        double wallD = RLConfig.WALL_MARGIN;
        if (myPos.getX() < wallD) fx += RLConfig.PF_WALL_STRENGTH * (wallD - myPos.getX());
        if (myPos.getX() > RLConfig.MAP_WIDTH - wallD) fx -= RLConfig.PF_WALL_STRENGTH * (myPos.getX() - (RLConfig.MAP_WIDTH - wallD));
        if (myPos.getY() < wallD) fy += RLConfig.PF_WALL_STRENGTH * (wallD - myPos.getY());
        if (myPos.getY() > RLConfig.MAP_HEIGHT - wallD) fy -= RLConfig.PF_WALL_STRENGTH * (myPos.getY() - (RLConfig.MAP_HEIGHT - wallD));

        // 4. Wreck Repel
        for (double[] w : wreckPositions) {
            double dx = myPos.getX() - w[0];
            double dy = myPos.getY() - w[1];
            double d = Math.hypot(dx, dy);
            if (d < RLConfig.PF_WRECK_RANGE && d > 0) {
                double force = RLConfig.PF_WALL_STRENGTH * (RLConfig.PF_WRECK_RANGE - d) / RLConfig.PF_WRECK_RANGE;
                fx += (dx/d) * force;
                fy += (dy/d) * force;
            }
        }

        double moveAngle = Math.atan2(fy, fx);
        if (Double.isNaN(moveAngle)) moveAngle = getHeading();

        double forwardDiff = Math.abs(normalize(moveAngle - getHeading()));
        double forwardDiffWrapped = Math.abs(forwardDiff - 2 * Math.PI);
        double minForward = Math.min(forwardDiff, forwardDiffWrapped);

        double backwardBodyAngle = normalize(moveAngle - Math.PI);
        double backwardDiff = Math.abs(normalize(backwardBodyAngle - getHeading()));
        double backwardDiffWrapped = Math.abs(backwardDiff - 2 * Math.PI);
        double minBackward = Math.min(backwardDiff, backwardDiffWrapped);

        if (minBackward < minForward) {
            if (!isSameDirection(getHeading(), backwardBodyAngle)) {
                turnTo(backwardBodyAngle);
            } else {
                myMove(false);
            }
        } else {
            if (!isSameDirection(getHeading(), moveAngle)) {
                turnTo(moveAngle);
            } else {
                myMove(true);
            }
        }
    }

    // ── Movement Primitives (from MacDuoBaseBot) ─────────────────────────

    protected void turnTo(double targetAngle) {
        double currentAngle = getHeading();
        double diff = normalize(targetAngle - currentAngle);
        if (diff > Math.PI) {
            diff -= 2 * Math.PI;
        } else if (diff < -Math.PI) {
            diff += 2 * Math.PI;
        }
        if (diff > ANGLEPRECISION) {
            stepTurn(Parameters.Direction.RIGHT);
        } else {
            stepTurn(Parameters.Direction.LEFT);
        }
    }

    protected boolean isSameDirection(double dir1, double dir2) {
        double diff = Math.abs(normalize(dir1) - normalize(dir2));
        return diff < ANGLEPRECISION || Math.abs(diff - 2 * Math.PI) < ANGLEPRECISION;
    }

    protected double normalize(double dir) {
        double res = dir;
        while (res < 0) res += 2 * Math.PI;
        while (res >= 2 * Math.PI) res -= 2 * Math.PI;
        return res;
    }

    protected double myGetHeading() {
        return normalize(getHeading());
    }

    protected void sendMyPosition() {
        broadcast("POS " + whoAmI + " " + myPos.getX() + " " + myPos.getY() + " " + getHeading());
    }

    protected void myMove(boolean forward) {
        double speed = (whoAmI == NBOT || whoAmI == SBOT) ? Parameters.teamASecondaryBotSpeed : Parameters.teamAMainBotSpeed;

        if (forward) {
            double myPredictedX = myPos.getX() + Math.cos(getHeading()) * speed;
            double myPredictedY = myPos.getY() + Math.sin(getHeading()) * speed;

            if (whoAmI == NBOT || whoAmI == SBOT) {
                if (myPredictedX > 150 && myPredictedX < 2850 && myPredictedY > 150 && myPredictedY < 1850) {
                    move();
                    myPos.setX(myPredictedX);
                    myPos.setY(myPredictedY);
                    sendMyPosition();
                    return;
                }
            } else {
                if (myPredictedX > 100 && myPredictedX < 2900 && myPredictedY > 100 && myPredictedY < 1900) {
                    move();
                    myPos.setX(myPredictedX);
                    myPos.setY(myPredictedY);
                    sendMyPosition();
                    return;
                }
            }
        } else {
            double myPredictedX = myPos.getX() - Math.cos(getHeading()) * speed;
            double myPredictedY = myPos.getY() - Math.sin(getHeading()) * speed;

            if (whoAmI == NBOT || whoAmI == SBOT) {
                if (myPredictedX > 150 && myPredictedX < 2850 && myPredictedY > 150 && myPredictedY < 1850) {
                    moveBack();
                    myPos.setX(myPredictedX);
                    myPos.setY(myPredictedY);
                    sendMyPosition();
                    return;
                }
            } else {
                if (myPredictedX > 100 && myPredictedX < 2900 && myPredictedY > 100 && myPredictedY < 1900) {
                    moveBack();
                    myPos.setX(myPredictedX);
                    myPos.setY(myPredictedY);
                    sendMyPosition();
                    return;
                }
            }
        }
        initiateObstacleAvoidance();
    }

    // ── Obstacle Avoidance (from MacDuoBaseBot) ──────────────────────────

    protected void initiateObstacleAvoidance() {
        isShooterAvoiding = true;
        boolean obstacleInPathRight = false;
        boolean obstacleInPathLeft = false;
        oldAngle = myGetHeading();
        double avoidance = 0.5;

        for (IRadarResult o : detectRadar()) {
            myPos.getX();
            o.getObjectDistance();
            Math.cos(o.getObjectDirection());
            myPos.getY();
            o.getObjectDistance();
            Math.sin(o.getObjectDirection());
            if (allyPos.get(whoAmI).isAlive() && o.getObjectType() != IRadarResult.Types.BULLET) {
                for (Position p : getObstacleCorners(o, myPos.getX(), myPos.getY())) {
                    if (!obstacleInPathRight) {
                        obstacleInPathRight = isPointInTrajectory(myPos.getX(), myPos.getY(), (getHeading() + avoidance * Math.PI), p.getX(), p.getY());
                    }
                    if (!obstacleInPathLeft) {
                        if (whoAmI == SBOT)
                        obstacleInPathLeft = isPointInTrajectory(myPos.getX(), myPos.getY(), (getHeading() - avoidance * Math.PI), p.getX(), p.getY());
                    }
                }
            }
        }

        if (!obstacleInPathRight) {
            state = State.TURNING_RIGHT;
            targetX = myPos.getX() + Math.cos(getHeading() + avoidance * Math.PI) * 50;
            targetY = myPos.getY() + Math.sin(getHeading() + avoidance * Math.PI) * 50;
            return;
        }
        if (!obstacleInPathLeft) {
            state = State.TURNING_LEFT;
            targetX = myPos.getX() + Math.cos(getHeading() - avoidance * Math.PI) * 50;
            targetY = myPos.getY() + Math.sin(getHeading() - avoidance * Math.PI) * 50;
            return;
        }
        state = State.MOVING_BACK;
        targetX = myPos.getX() - Math.cos(getHeading()) * 50;
        targetY = myPos.getY() - Math.sin(getHeading()) * 50;
    }

    protected boolean isPointInTrajectory(double robotX, double robotY, double robotHeading, double pointX, double pointY) {
        double pathLength = BOT_RADIUS * 2;
        double pathWidth = BOT_RADIUS * 2;

        double dirX = Math.cos(robotHeading);
        double dirY = Math.sin(robotHeading);
        double halfWidth = pathWidth / 2;

        double frontX = robotX + pathLength * dirX;
        double frontY = robotY + pathLength * dirY;

        double perpX = -dirY;
        double perpY = dirX;

        double ALX = robotX + halfWidth * perpX;
        double ALY = robotY + halfWidth * perpY;
        double ARX = robotX - halfWidth * perpX;
        double ARY = robotY - halfWidth * perpY;
        double PLX = frontX + halfWidth * perpX;
        double PLY = frontY + halfWidth * perpY;
        double PRX = frontX - halfWidth * perpX;
        double PRY = frontY - halfWidth * perpY;

        return isPointInRectangle(pointX, pointY, ALX, ALY, ARX, ARY, PLX, PLY, PRX, PRY);
    }

    protected boolean isPointInRectangle(double Px, double Py, double ALX, double ALY, double ARX, double ARY,
                                         double PLX, double PLY, double PRX, double PRY) {
        double APx = Px - ALX;
        double APy = Py - ALY;
        double ABx = ARX - ALX;
        double ABy = ARY - ALY;
        double ADx = PLX - ALX;
        double ADy = PLY - ALY;

        double dotAB = APx * ABx + APy * ABy;
        double dotAD = APx * ADx + APy * ADy;
        double dotAB_AB = ABx * ABx + ABy * ABy;
        double dotAD_AD = ADx * ADx + ADy * ADy;

        return (0 <= dotAB && dotAB <= dotAB_AB) && (0 <= dotAD && dotAD <= dotAD_AD);
    }

    protected Position[] getObstacleCorners(IRadarResult obstacle, double robotX, double robotY) {
        double obstacleX = robotX + obstacle.getObjectDistance() * Math.cos(obstacle.getObjectDirection());
        double obstacleY = robotY + obstacle.getObjectDistance() * Math.sin(obstacle.getObjectDirection());
        double obstacleRadius = obstacle.getObjectRadius();

        Position topLeft     = new Position(obstacleX - obstacleRadius, obstacleY + obstacleRadius);
        Position topRight    = new Position(obstacleX + obstacleRadius, obstacleY + obstacleRadius);
        Position bottomLeft  = new Position(obstacleX - obstacleRadius, obstacleY - obstacleRadius);
        Position bottomRight = new Position(obstacleX + obstacleRadius, obstacleY - obstacleRadius);

        return new Position[]{topLeft, topRight, bottomLeft, bottomRight};
    }

    // ── Detection & Targeting ────────────────────────────────────────────

    protected void detection() {
        for (IRadarResult o : detectRadar()) {
            if (o.getObjectType() == Types.OpponentMainBot || o.getObjectType() == Types.OpponentSecondaryBot) {
                broadcast("ENEMY " + o.getObjectDirection() + " " + o.getObjectDistance() + " " +
                          (o.getObjectType() == Types.OpponentMainBot ? "MainBot" : "SecondaryBot") + " " +
                          (myPos.getX() + o.getObjectDistance() * Math.cos(o.getObjectDirection())) + " " +
                          (myPos.getY() + o.getObjectDistance() * Math.sin(o.getObjectDirection())));

                double d = o.getObjectDistance();
                double dir = o.getObjectDirection();
                double ox = myPos.getX() + d * Math.cos(dir);
                double oy = myPos.getY() + d * Math.sin(dir);

                boolean found = false;
                for (RLEnemy e : rlEnemies) {
                    if (e.type == o.getObjectType() && Math.hypot(e.x - ox, e.y - oy) < 60) {
                        e.update(ox, oy, d, dir, currentTick);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    rlEnemies.add(new RLEnemy(ox, oy, d, dir, o.getObjectType(), currentTick));
                }
            } else if (o.getObjectType() == Types.Wreck) {
                 double ox = myPos.getX() + o.getObjectDistance() * Math.cos(o.getObjectDirection());
                 double oy = myPos.getY() + o.getObjectDistance() * Math.sin(o.getObjectDirection());
                 boolean exists = false;
                 for (double[] w : wreckPositions) {
                     if (Math.abs(w[0] - ox) < 20 && Math.abs(w[1] - oy) < 20) { exists = true; break; }
                 }
                 if (!exists) wreckPositions.add(new double[]{ox, oy});
            }
        }
    }

    protected void pruneStaleEnemies() {
        rlEnemies.removeIf(e -> (currentTick - e.lastSeenTick) > RLConfig.STALE_TTL);
    }

    private String enemyKey(RLEnemy e) {
        int qx = (int)(e.x / 100.0);
        int qy = (int)(e.y / 100.0);
        return e.type.name() + "_" + qx + "_" + qy;
    }

    protected Double aimAndMaybeFire(RLEnemy target) {
        if (target == null) return null;
        double dist = Math.hypot(target.x - myPos.getX(), target.y - myPos.getY());
        if (dist > Parameters.bulletRange) return null;

        double t = dist / Parameters.bulletVelocity;
        double predX = target.x + target.speedX * t;
        double predY = target.y + target.speedY * t;

        if (!isFiringLineSafe(predX, predY)) return null;

        return Math.atan2(predY - myPos.getY(), predX - myPos.getX());
    }

    protected RLEnemy chooseBestTarget() {
        RLEnemy best = null;
        double maxScore = -Double.MAX_VALUE;

        for (RLEnemy e : rlEnemies) {
            int stale = currentTick - e.lastSeenTick;
            if (stale > RLConfig.STALE_TTL) continue;

            double dist = Math.hypot(e.x - myPos.getX(), e.y - myPos.getY());
            double score = 0;

            score += (RLConfig.TARGET_PROXIMITY_WEIGHT - dist);
            if (e.type == Types.OpponentSecondaryBot) score += RLConfig.TARGET_TYPE_BONUS;

            double hp = (e.type == Types.OpponentSecondaryBot ? RLConfig.MAX_HEALTH_SEC : RLConfig.MAX_HEALTH_MAIN) - e.estimatedDmg;
            if (hp < 30) score += RLConfig.TARGET_LOWHP_CRITICAL_BONUS;
            else if (hp < 60) score += RLConfig.TARGET_LOWHP_MODERATE_BONUS;

            if (focusedTargetKey != null && focusedTargetKey.equals(enemyKey(e)))
                score += RLConfig.TARGET_FOCUS_BONUS;
            if (isFiringLineSafe(e.x, e.y)) score += RLConfig.TARGET_SAFEFIRE_BONUS;
            score -= (stale * RLConfig.TARGET_STALE_PENALTY);

            if (score > maxScore) {
                maxScore = score;
                best = e;
            }
        }

        focusedTargetKey = (best != null) ? enemyKey(best) : null;
        return best;
    }

    protected boolean isFiringLineSafe(double tx, double ty) {
        double x1 = myPos.getX(), y1 = myPos.getY();
        double x2 = tx, y2 = ty;
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx*dx + dy*dy;
        if (lenSq == 0) return true;

        for (BotState b : allyPos.values()) {
            if (!b.isAlive() || Math.hypot(b.getPosition().getX() - x1, b.getPosition().getY() - y1) < 1.0) continue;

            double bx = b.getPosition().getX();
            double by = b.getPosition().getY();

            double t = ((bx - x1) * dx + (by - y1) * dy) / lenSq;
            if (t < 0 || t > 1) continue;

            double proX = x1 + t * dx;
            double proY = y1 + t * dy;
            double distSq = (bx - proX)*(bx - proX) + (by - proY)*(by - proY);

            if (distSq < (RLConfig.FIRING_SAFETY_RADIUS * RLConfig.FIRING_SAFETY_RADIUS)) return false;
        }
        return true;
    }

    // ── Communication ────────────────────────────────────────────────────

    protected void readMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            String[] parts = msg.split(" ");
            if (parts.length < 2) continue;

            String header = parts[0];
            switch (header) {
                case "ENEMY":
                    if (parts.length >= 6) {
                        try {
                            double dir = Double.parseDouble(parts[1]);
                            double dist = Double.parseDouble(parts[2]);
                            Types type = parts[3].contains("MainBot") ? Types.OpponentMainBot : Types.OpponentSecondaryBot;
                            double x = Double.parseDouble(parts[4]);
                            double y = Double.parseDouble(parts[5]);
                            updateRLEnemy(x, y, dist, dir, type);
                        } catch(Exception e) {}
                    }
                    break;
                case "POS":
                     if (parts.length >= 5) {
                         String who = parts[1];
                         double x = Double.parseDouble(parts[2]);
                         double y = Double.parseDouble(parts[3]);
                         double h = Double.parseDouble(parts[4]);
                         BotState b = allyPos.get(who);
                         if (b == null) allyPos.put(who, new BotState(x, y, true, who, h));
                         else b.setPosition(x, y, h);
                     }
                     break;
                case "DEAD":
                     BotState b = allyPos.get(parts[1]);
                     if (b != null) b.setAlive(false);
                     break;
                case "WRECK":
                     if (parts.length >= 3) {
                         double x = Double.parseDouble(parts[1]);
                         double y = Double.parseDouble(parts[2]);
                         boolean exists = false;
                         for (double[] w : wreckPositions) {
                             if (Math.abs(w[0] - x) < 20 && Math.abs(w[1] - y) < 20) { exists = true; break; }
                         }
                         if (!exists) wreckPositions.add(new double[]{x, y});
                     }
                     break;
            }
        }
    }

    private void updateRLEnemy(double x, double y, double d, double dir, Types type) {
        for (RLEnemy e : rlEnemies) {
            if (e.type == type && Math.hypot(e.x - x, e.y - y) < 60) {
                e.update(x, y, d, dir, currentTick);
                return;
            }
        }
        rlEnemies.add(new RLEnemy(x, y, d, dir, type, currentTick));
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 *  RLBotMain — RL-optimized main bot (3 per team)
 * ═══════════════════════════════════════════════════════════════════════════*/
public class RLBotMain extends RLBotBase {

    private double holdX, holdY;
    private double kiteMin, kiteMax;
    private int noFireTicks = 0;

    private enum S { ADVANCING, FIRING, FLANKING, RETREATING, DEAD }
    private S mainState;

    @Override
    public void activate() {
        super.activate();

        boolean hasN = false, hasS = false;
        for (IRadarResult o : detectRadar()) {
            if (isSameDirection(o.getObjectDirection(), Parameters.NORTH)) hasN = true;
            else if (isSameDirection(o.getObjectDirection(), Parameters.SOUTH)) hasS = true;
        }
        if (hasN && hasS)       whoAmI = MAIN2;
        else if (!hasN && hasS) whoAmI = MAIN1;
        else                    whoAmI = MAIN3;

        if      (MAIN1.equals(whoAmI)) { myPos.setX(teamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX);
                                         myPos.setY(teamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY); }
        else if (MAIN2.equals(whoAmI)) { myPos.setX(teamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX);
                                         myPos.setY(teamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY); }
        else                           { myPos.setX(teamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX);
                                         myPos.setY(teamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY); }

        double offset = RLConfig.HOLD_X_OFFSET / 2.0;
        holdX = teamA ? (RLConfig.MAP_CX - offset) : (RLConfig.MAP_CX + offset);

        double cy = RLConfig.FORMATION_Y_BASE;
        double dy = RLConfig.FORMATION_Y_OFFSET;
        double[] holdYs = {cy - dy, cy, cy + dy};
        holdY = holdYs[MAIN1.equals(whoAmI) ? 0 : MAIN2.equals(whoAmI) ? 1 : 2];

        mainState = S.ADVANCING;
        kiteMin = RLConfig.KITE_MIN_NORMAL;
        kiteMax = RLConfig.KITE_MAX_NORMAL;
    }

    @Override
    public void step() {
        super.step();

        if (getHealth() <= 0) {
            mainState = S.DEAD;
            return;
        }

        int aliveAllies = 0;
        for (BotState b : allyPos.values()) if (b.isAlive()) aliveAllies++;

        if (aliveAllies >= 3 && getHealth() > RLConfig.HEALTH_HIGH_THRESHOLD) {
            kiteMin = RLConfig.KITE_MIN_AGGRO;
            kiteMax = RLConfig.KITE_MAX_AGGRO;
        } else if (aliveAllies <= 2 || getHealth() < RLConfig.HEALTH_LOW_THRESHOLD) {
            kiteMin = RLConfig.KITE_MIN_DEFEN;
            kiteMax = RLConfig.KITE_MAX_DEFEN;
        } else {
            kiteMin = RLConfig.KITE_MIN_NORMAL;
            kiteMax = RLConfig.KITE_MAX_NORMAL;
        }

        boolean hasEnemies = !rlEnemies.isEmpty();

        if (mainState != S.DEAD) {
             if (getHealth() < RLConfig.HP_RETREAT_MAIN) {
                 mainState = S.RETREATING;
             } else if (hasEnemies) {
                 mainState = (noFireTicks > RLConfig.NOFIRE_REPOSITION_TICKS) ? S.FLANKING : S.FIRING;
             } else {
                 mainState = S.ADVANCING;
             }
        }

        RLEnemy target = chooseBestTarget();
        Double fireAngle = aimAndMaybeFire(target);

        // 1 action per tick! Prioritize firing if possible
        if (fireAngle != null && noFireTicks > 2) {
            fire(fireAngle);
            noFireTicks = 0;
            return;
        } else {
            noFireTicks++;
        }

        // Otherwise move
        switch (mainState) {
            case ADVANCING:
                goTo(holdX, holdY);
                break;
            case FIRING:
                potentialFieldMove(kiteMin, kiteMax);
                break;
            case FLANKING:
                double flankY = myPos.getY() + (MAIN1.equals(whoAmI) ? -RLConfig.FLANK_OFFSET : RLConfig.FLANK_OFFSET);
                if (flankY < RLConfig.WALL_MARGIN) flankY = RLConfig.WALL_MARGIN;
                if (flankY > RLConfig.MAP_HEIGHT - RLConfig.WALL_MARGIN) flankY = RLConfig.MAP_HEIGHT - RLConfig.WALL_MARGIN;
                double flankX = myPos.getX() + (teamA ? RLConfig.FLANK_OFFSET * 0.5 : -RLConfig.FLANK_OFFSET * 0.5);
                flankX = Math.max(RLConfig.WALL_MARGIN, Math.min(RLConfig.MAP_WIDTH - RLConfig.WALL_MARGIN, flankX));
                goTo(flankX, flankY);
                break;
            case RETREATING:
                goTo(teamA ? 300 : 2700, holdY);
                break;
            case DEAD: break;
        }
    }
}
