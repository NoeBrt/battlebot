package algorithms.external;

import characteristics.IRadarResult;
import characteristics.Parameters;
import robotsimulator.Brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MacDuoBaseBot extends Brain {

	protected static final String NBOT = "NBOT";
    protected static final String SBOT = "SBOT";
	protected static final String MAIN1 = "1";
	protected static final String MAIN2 = "2";
	protected static final String MAIN3 = "3";
	
	protected static final double ANGLEPRECISION = 0.001;
	protected enum State {FIRST_RDV, MOVING, MOVING_BACK, TURNING_LEFT, TURNING_RIGHT, FIRE, DEAD };
	
	protected final double BOT_RADIUS = 50;
	protected final double BULLET_RADIUS = 5;
	protected final double DISTANCE_SCOUT_SHOOTER = 1250;

	protected String whoAmI;
	
	 //---VARIABLES---//
	protected Position myPos;
	protected boolean freeze;
    protected boolean isTeamA;
	protected boolean rdv_point;
	protected boolean turningTask = false;
	protected State state;
	protected double oldAngle;
	protected double obstacleDirection = 0;
	protected Parameters.Direction turnedDirection;
	protected double targetX, targetY;
	protected boolean isShooterAvoiding; 
	protected double rdvXToReach, rdvYToReach;
	protected List<Ennemy> enemyTargets = new ArrayList<>();
	protected List<double[]> wreckPositions = new ArrayList<>();
	protected List<Ennemy> enemyPosToAvoid = new ArrayList<>();

	
	
	protected Map<String, BotState> allyPos = new HashMap<>();	// Stocker la position des alliés
	protected Map<Position, IRadarResult.Types> oppPos = new HashMap<>();	// Stocker la position des opposants
	protected Map<String, Double[]> wreckPos = new HashMap<>();	// Stocker la position des débris
	
    public MacDuoBaseBot() {
		super();
		allyPos.put(NBOT, new BotState());
		allyPos.put(SBOT, new BotState());
		allyPos.put(MAIN1, new BotState());
		allyPos.put(MAIN2, new BotState());
		allyPos.put(MAIN3, new BotState());
	}
	protected abstract void detection();
	
	protected void reach_rdv_point(double tX, double tY) {
	    double angleToTarget = Math.atan2(tY - myPos.getY(), tX - myPos.getX());

	    // Calculer la distance
	    double distanceToScout = Math.sqrt(Math.pow(myPos.getX() - tX, 2) + Math.pow(myPos.getY() - tY, 2));

	    // Vérifier si on est dans la portée du scout
	    if (whoAmI==NBOT || whoAmI==SBOT || distanceToScout > 450) {

		    // Adapter la direction aux angles cardinaux et diagonaux
		    angleToTarget = getNearestAllowedDirection(angleToTarget);
		    if (!isSameDirection(getHeading(), angleToTarget)) {
		        turnTo(angleToTarget);
		    } else {
		        myMove(true);
		    }

	    }
	}
	
	// StepTurn Gauche ou Droite, selon un angle cible
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
	
	//=========================================BASE=========================================

	protected boolean isSameDirection(double dir1, double dir2) {
		double diff = Math.abs(normalize(dir1) - normalize(dir2));
		return diff < ANGLEPRECISION || Math.abs(diff - 2 * Math.PI) < ANGLEPRECISION;
	}
	
	protected boolean isRoughlySameDirection(double dir1, double dir2) {
		double diff = Math.abs(normalize(dir1) - normalize(dir2));
		return diff < 0.5;
	}
	
	protected double normalize(double dir){
	    double res=dir;
	    while (res<0) res+=2*Math.PI;
	    while (res>=2*Math.PI) res-=2*Math.PI;
	    return res;
	}
	
	protected boolean isHeading(double dir){
		return Math.abs(Math.sin(getHeading()-dir))<ANGLEPRECISION;
	}
	
	protected double myGetHeading(){
	    return normalize(getHeading());
	  }
	
	protected void sendMyPosition() {
		broadcast("POS "+whoAmI+" "+myPos.getX()+" "+myPos.getY()+" "+getHeading());
	}
	
	protected void turnLeft() {
		double avoidance = 0.5;
		if (!isSameDirection(getHeading(),oldAngle+(-avoidance * Math.PI))) {
			stepTurn(Parameters.Direction.LEFT);
	    } else {
	        state = State.MOVING;
	        myMove(true);
	    }				
	}
	
	protected void turnRight() {
		double avoidance = 0.5;
		if (!isSameDirection(getHeading(),oldAngle+(avoidance * Math.PI))) {
            stepTurn(Parameters.Direction.RIGHT);
	    } else {
	        state = State.MOVING;
	        myMove(true);
	    }				
	}
	
	protected double distance(Position p1, Position p2) {
	    return Math.sqrt(Math.pow(p2.getX() - p1.getX(), 2) + Math.pow(p2.getY() - p1.getY(), 2));
	}
	
	protected double getCurrentSpeed() throws Exception {
		switch (whoAmI) {
			case NBOT:
			case SBOT: 
				return Parameters.teamASecondaryBotSpeed;
			case MAIN1:
			case MAIN2:
			case MAIN3:
				return Parameters.teamAMainBotSpeed;
			default :
				throw new Exception("Pas de vitesse pour ce robot");
		}
	}
	
	/**
	 * Cette méthode ajuste l'angle vers la direction la plus proche parmi :
	 * Nord, Sud, Est, Ouest, Nord-Est, Nord-Ouest, Sud-Est, Sud-Ouest
	 */
	protected double getNearestAllowedDirection(double angle) {
	    double[] allowedAngles = {
	        0,                      // Est (0°)
	        //Math.PI / 4,            // Nord-Est (45°)
	        Math.PI / 2,            // Nord (90°)
	        //3 * Math.PI / 4,        // Nord-Ouest (135°)
	        Math.PI,                // Ouest (180°)
	        //-3 * Math.PI / 4,       // Sud-Ouest (-135°)
	        -Math.PI / 2,           // Sud (-90°)
	        //-Math.PI / 4            // Sud-Est (-45°)
	    };

	    double bestAngle = allowedAngles[0];
	    double minDiff = Math.abs(normalize(angle) - normalize(bestAngle));

	    for (double allowed : allowedAngles) {
	        double diff = Math.abs(normalize(angle) - normalize(allowed));
	        if (diff < minDiff) {
	            bestAngle = allowed;
	            minDiff = diff;
	        }
	    }
	    return bestAngle;
	}

	protected boolean isPointInTrajectory(double robotX, double robotY, double robotHeading, double pointX, double pointY) {
		// Dimensions de la zone
		double pathLength = BOT_RADIUS*2; // Longueur de la trajectoire
		double pathWidth = BOT_RADIUS*2;   // Largeur totale (50 mm de chaque côté)
	
		// Calcul du vecteur direction du robot
		double dirX = Math.cos(robotHeading);
		double dirY = Math.sin(robotHeading);
	
		// Calcul des coins de la zone
		double halfWidth = pathWidth / 2;
	
		// Centre avant et arrière
		double frontX = robotX + pathLength * dirX;
		double frontY = robotY + pathLength * dirY;
		double backX = robotX;
		double backY = robotY;
	
		// Vecteur perpendiculaire pour déterminer la largeur de la zone
		double perpX = -dirY;
		double perpY = dirX;
	
		// Coins du rectangle de la trajectoire
		double ALX = backX + halfWidth * perpX;  // Arrière gauche
		double ALY = backY + halfWidth * perpY;
		double ARX = backX - halfWidth * perpX;  // Arrière droit
		double ARY = backY - halfWidth * perpY;
		double PLX = frontX + halfWidth * perpX; // Avant gauche
		double PLY = frontY + halfWidth * perpY;
		double PRX = frontX - halfWidth * perpX; // Avant droit
		double PRY = frontY - halfWidth * perpY;
	
		// Vérifier si le point est dans le rectangle
		return isPointInRectangle(pointX, pointY, ALX, ALY, ARX, ARY, PLX, PLY, PRX, PRY);
	}

	protected boolean isPointInRectangle(double Px, double Py, double ALX, double ALY, double ARX, double ARY, 
                                     double PLX, double PLY, double PRX, double PRY) {
		// Produit scalaire pour vérifier si le point est dans la zone
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
		// Position du centre de l'obstacle
		double obstacleX = robotX + obstacle.getObjectDistance() * Math.cos(obstacle.getObjectDirection());
		double obstacleY = robotY + obstacle.getObjectDistance() * Math.sin(obstacle.getObjectDirection());
		double obstacleRadius = obstacle.getObjectRadius();
	
		// Calcul des coins du rectangle englobant
		Position topLeft = new Position(obstacleX - obstacleRadius, obstacleY + obstacleRadius);
		Position topRight = new Position(obstacleX + obstacleRadius, obstacleY + obstacleRadius);
		Position bottomLeft = new Position(obstacleX - obstacleRadius, obstacleY - obstacleRadius);
		Position bottomRight = new Position(obstacleX + obstacleRadius, obstacleY - obstacleRadius);
	
		return new Position[]{topLeft, topRight, bottomLeft, bottomRight};
	}
	
	protected Position[] getObstacleCorners(double obstacleRadius , double robotX, double robotY) {
		double distance = distance(myPos, new Position (robotX, robotY));
		double direction = Math.atan2(robotY - myPos.getY(), robotX - myPos.getX());
		// Position du centre de l'obstacle
		double obstacleX = robotX + distance * Math.cos(direction);
		double obstacleY = robotY + distance * Math.sin(direction);
	
		// Calcul des coins du rectangle englobant
		Position topLeft = new Position(obstacleX - obstacleRadius, obstacleY - obstacleRadius);
		Position topRight = new Position(obstacleX + obstacleRadius, obstacleY - obstacleRadius);
		Position bottomLeft = new Position(obstacleX - obstacleRadius, obstacleY + obstacleRadius);
		Position bottomRight = new Position(obstacleX + obstacleRadius, obstacleY + obstacleRadius);
	
		return new Position[]{topLeft, topRight, bottomLeft, bottomRight};
	}
	
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
						if(whoAmI==SBOT)
					    obstacleInPathLeft = isPointInTrajectory(myPos.getX(), myPos.getY(), (getHeading() - avoidance * Math.PI), p.getX(), p.getY());
					}
			    }
			}
		}

		if (!obstacleInPathRight) {
			state = State.TURNING_RIGHT;
			targetX = myPos.getX() + Math.cos(getHeading()+avoidance*Math.PI) *50;
			targetY= myPos.getY() + Math.sin(getHeading()+avoidance*Math.PI) *50;

			return;
		}
		if (!obstacleInPathLeft) {
			state = State.TURNING_LEFT;
			targetX = myPos.getX() + Math.cos(getHeading()-avoidance*Math.PI) *50;
			targetY= myPos.getY() + Math.sin(getHeading()-avoidance*Math.PI) *50;
			return;
		}
		state = State.MOVING_BACK;
		targetX = myPos.getX() - Math.cos(getHeading()) *50;
		targetY= myPos.getY() - Math.sin(getHeading()) *50;
		return;
	}
	
	protected void myMove(boolean forward) {
		double speed = (whoAmI == NBOT || whoAmI == SBOT) ? Parameters.teamASecondaryBotSpeed : Parameters.teamAMainBotSpeed;

		if (forward) {
			double myPredictedX = myPos.getX() + Math.cos(getHeading()) * speed;
			double myPredictedY = myPos.getY() + Math.sin(getHeading()) * speed;
			
			if (whoAmI == NBOT || whoAmI == SBOT) {
				if (myPredictedX > 150 && myPredictedX < 2850 && myPredictedY > 150 && myPredictedY < 1850){
					move(); 
					myPos.setX(myPredictedX);
					myPos.setY(myPredictedY);
					sendMyPosition();
					return;
				}
			} else {
				if (myPredictedX > 100 && myPredictedX < 2900 && myPredictedY > 100 && myPredictedY < 1900){
					
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
				if (myPredictedX > 150 && myPredictedX < 2850 && myPredictedY > 150 && myPredictedY < 1850){
					moveBack(); 
					myPos.setX(myPredictedX);
					myPos.setY(myPredictedY);
					sendMyPosition();
					return;
				}
			} else {
				if (myPredictedX > 100 && myPredictedX < 2900 && myPredictedY > 100 && myPredictedY < 1900){
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
	
	protected boolean hasReachedTarget(double targetX, double targetY, boolean movingForward) {
		boolean reachedX, reachedY;
		double currentX = myPos.getX();
		double currentY = myPos.getY();
		
		if (movingForward) {
		// Lorsque le robot avance, on s'attend à ce que les composantes 
		// suivent le signe de cos(avoidanceHeading) et sin(avoidanceHeading)
		if (Math.cos(getHeading()) > 0) {
		reachedX = (currentX >= targetX);
		} else if (Math.cos(getHeading()) < 0) {
		reachedX = (currentX <= targetX);
		} else {
		reachedX = true;
		}
		if (Math.sin(getHeading()) > 0) {
		reachedY = (currentY >= targetY);
		} else if (Math.sin(getHeading()) < 0) {
		reachedY = (currentY <= targetY);
		} else {
		reachedY = true;
		}
		} else { // movingBackward
		// Lorsqu'on recule, la direction effective est inversée
		if (Math.cos(getHeading()) > 0) {
		reachedX = (currentX <= targetX);
		} else if (Math.cos(getHeading()) < 0) {
		reachedX = (currentX >= targetX);
		} else {
		reachedX = true;
		}
		if (Math.sin(getHeading()) > 0) {
		reachedY = (currentY <= targetY);
		} else if (Math.sin(getHeading()) < 0) {
		reachedY = (currentY >= targetY);
		} else {
		reachedY = true;
		}
		}

		return reachedX && reachedY;
	}
	    
}
