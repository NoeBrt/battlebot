package algorithms.lab;

import characteristics.IRadarResult;
import characteristics.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import static algorithms.lab.NoeAbstractBot.State.*;

public class NoeMainBot extends NoeAbstractBot {

  private static final double STEP_SIZE        = Parameters.teamAMainBotSpeed;
  private static final double AIM_THRESHOLD    = 0.05;   // radians
  private static final double LOW_HEALTH_RATIO = 0.1;
  private static final double WAYPOINT_REACH   = 30;     // distance pour valider un waypoint
  private static final String PATH_FILE        = "paths/main_paths.txt";

  protected int role;
  private IRadarResult lockedTarget;
  private int dodgeSteps;
  private int repositionSteps;
  private Parameters.Direction repositionDir;

  // Suivi de chemin
  private final List<double[]> waypoints = new ArrayList<>();
  private int waypointIndex = 0;
  private boolean pathPriority = false; // si true, chemin complet avant tout combat

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
      case M1 -> { x = isTeamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX;
        y = isTeamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY; }
      case M2 -> { x = isTeamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX;
        y = isTeamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY; }
      case M3 -> { x = isTeamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX;
        y = isTeamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY; }
    }
    initState(MOVE_FORWARD, x, y);
    loadWaypoints();
    if (!waypoints.isEmpty()) {
      currentState = FOLLOW_PATH;
      sendLogMessage("[MainBot:" + id() + "] path loaded, " + waypoints.size() + " waypoints");
    } else {
      startCurvedMove(16, 1, Parameters.Direction.LEFT, true);
    }
    sendLogMessage("[MainBot:" + id() + "] spawn=(" + myX + "," + myY + ")");
  }

  @Override
  public void step() { stepState(); }

  @Override
  protected void onStep() {
    broadcastStatus();

    if (isDead()) { transitionTo(RADAR_MODE); return; }

    mergeTeamTargets();

    boolean pathLock = pathPriority && currentState == FOLLOW_PATH;
    if (targetFound() && !pathLock
        && currentState != ATTACK_MODE
        && currentState != FIRE
        && currentState != DODGE) {
      transitionTo(ATTACK_MODE);
      return;
    }

    switch (currentState) {
      case FOLLOW_PATH  -> stateFollowPath();
      case MOVE_FORWARD -> stateMoveForward();
      case IDLE_WATCH   -> stateIdleWatch();
      case ATTACK_MODE  -> stateAttackMode();
      case FIRE         -> stateFire();
      case REPOSITION   -> stateReposition();
      case DODGE        -> stateDodge();
      case MOVE_SLALOM  -> stateMoveSlalom();
      case RADAR_MODE   -> stateRadarMode();
    }
  }

  private void stateMoveForward() {
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      transitionTo(ATTACK_MODE);
      return;
    }
    // Esquive en arc si obstacle
    if (avoidActive) {
      if (!stepAvoid(STEP_SIZE)) {
        // Esquive terminee, reprendre la marche
      }
      return;
    }
    if (isFrontObstacle()) {
      startAvoid(STEP_SIZE);
      stepAvoid(STEP_SIZE);
      return;
    }
    move();
    updatePosition(STEP_SIZE);
  }

  private void stateAttackMode() {
    if (myHp < LOW_HEALTH_RATIO) { transitionTo(DODGE); return; }
    IRadarResult radarEnemy = nearestEnemy();
    if (radarEnemy != null) lockedTarget = radarEnemy;
    if (!targetFound()) {
      sendLogMessage("[MainBot:" + id() + "] cible perdue → MOVE_FORWARD");
      transitionTo(MOVE_FORWARD);
      return;
    }
    double angle = angleTo(targetX(), targetY());
    fire(angle);
    transitionTo(REPOSITION); // bouge après chaque tir pour être imprévisible
  }

  private void stateRadarMode() {
    nearestEnemy();
    broadcastStatus();
  }

  private void stateMoveSlalom() {
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      stopCurvedMove();
      transitionTo(ATTACK_MODE);
      return;
    }
    stepCurvedMove(STEP_SIZE);
  }

  private void stateIdleWatch() {
    if (myHp < LOW_HEALTH_RATIO) { transitionTo(DODGE); return; }
    IRadarResult enemy = nearestEnemy();
    if (enemy != null) {
      lockedTarget = enemy;
      transitionTo(ATTACK_MODE);
    }
    // Si une cible fraîche vient des alliés, targetFound() sera true
    // et le bloc en tête de onStep() aura déjà déclenché ATTACK_MODE.
  }

  private void stateFire() {
    if (myHp < LOW_HEALTH_RATIO) { transitionTo(DODGE); return; }
    if (targetFound()) {
      double angle = angleTo(targetX(), targetY());
      fire(angle);
    }
    transitionTo(REPOSITION);
  }

  private void stateReposition() {
    if (myHp < LOW_HEALTH_RATIO) { transitionTo(DODGE); return; }
    if (repositionSteps <= 0) {
      transitionTo(targetFound() ? ATTACK_MODE : MOVE_FORWARD);
      return;
    }
    // Arc lateral : avance + tourne en meme temps
    arcStep(STEP_SIZE, repositionDir);
    repositionSteps--;
  }

  private void stateDodge() {
    if (dodgeSteps <= 0) { transitionTo(IDLE_WATCH); return; }
    // Recule en tournant pour etre imprevisible
    moveBack();
    updatePosition(-STEP_SIZE);
    stepTurn(repositionDir);
    dodgeSteps--;
  }

  private void transitionTo(State newState) {
    sendLogMessage("[MainBot:" + id() + "] " + currentState + " → " + newState);
    stopAvoid();
    if (newState == DODGE) dodgeSteps = 5;
    if (newState == REPOSITION) {
      repositionSteps = 3;
      repositionDir = (Math.random() < 0.5)
          ? Parameters.Direction.LEFT : Parameters.Direction.RIGHT;
    }
    currentState = newState;
  }

  // ---- Suivi de chemin ---- //

  private void stateFollowPath() {
    if (!pathPriority) {
      IRadarResult enemy = nearestEnemy();
      if (enemy != null) {
        lockedTarget = enemy;
        stopAvoid();
        transitionTo(ATTACK_MODE);
        return;
      }
    }

    if (waypointIndex >= waypoints.size()) {
      sendLogMessage("[MainBot:" + id() + "] path complete → MOVE_FORWARD");
      stopAvoid();
      startCurvedMove(16, 1, Parameters.Direction.LEFT, true);
      transitionTo(MOVE_FORWARD);
      return;
    }

    // Esquive en cours → la terminer d'abord
    if (avoidActive) {
      stepAvoid(STEP_SIZE);
      return;
    }

    double[] wp = waypoints.get(waypointIndex);
    double dist = distanceTo(wp[0], wp[1]);

    if (dist < WAYPOINT_REACH) {
      waypointIndex++;
      sendLogMessage("[MainBot:" + id() + "] waypoint " + waypointIndex + " atteint");
      return;
    }

    // Obstacle devant → esquive en arc au lieu de sauter le waypoint
    if (isFrontObstacle()) {
      startAvoid(STEP_SIZE);
      stepAvoid(STEP_SIZE);
      return;
    }

    // Mouvement fluide : arc vers le waypoint (avance + tourne en meme temps)
    smoothMoveToward(wp[0], wp[1], STEP_SIZE);
  }

  private void loadWaypoints() {
    waypoints.clear();
    waypointIndex = 0;
    pathPriority = false;
    File f = new File(PATH_FILE);
    if (!f.exists()) return;
    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
      String myTag = id() + ":";
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.startsWith("PRIORITY:")) {
          pathPriority = Boolean.parseBoolean(line.substring("PRIORITY:".length()));
          continue;
        }
        if (!line.startsWith(myTag)) continue;
        String data = line.substring(myTag.length()).trim();
        if (data.isEmpty()) break;
        for (String pt : data.split(";")) {
          String[] xy = pt.split(",");
          if (xy.length == 2) {
            waypoints.add(new double[]{
                Double.parseDouble(xy[0]), Double.parseDouble(xy[1])});
          }
        }
        break;
      }
    } catch (Exception e) {
      // Pas de chemin, pas grave
    }
  }

  private String id() {
    return switch (myId) {
      case M1 -> "M1"; case M2 -> "M2"; case M3 -> "M3"; default -> "?";
    };
  }
}