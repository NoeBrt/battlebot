package algorithms.external;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import characteristics.IRadarResult;
import characteristics.Parameters;

class Segment {
    public Position start;
    public Position end;
    public Segment(Position start, Position end) {
        this.start = start;
        this.end = end;
    }
}

//====================================================================================
//====================================ABSTRACT BOT====================================
//====================================================================================

//=================================================================================
//====================================SECONDARY====================================
//=================================================================================

public class MacDuoSecondary extends MacDuoBaseBot{	
    
    
    
    // points de rdv
    //private static final double TARGET_X1 = 600; // TargetX2 - 300
    //private static final double TARGET_Y1 = 840; // TargetY2 - 660
    //private static final double TARGET_X2 = 900; 
    //private static final double TARGET_Y2 = 1500;
    
    private double rdvY; // TargetY2 - 660

    private boolean isShooterAround = false;
        
    private boolean firstTurning = true;

    //=========================================CORE=========================================	
	public MacDuoSecondary() {super();}

	@Override
	public void activate() {
		isTeamA = (getHeading() == Parameters.EAST);

		//détermination de l'emplacement du bot
		whoAmI = NBOT;
		for (IRadarResult o: detectRadar()) {
			if (isSameDirection(o.getObjectDirection(),Parameters.NORTH)) whoAmI = SBOT;
		}
		if (whoAmI == NBOT){
			myPos = new Position((isTeamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX),
								 (isTeamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY));
	    } else {
			myPos = new Position((isTeamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX),
								 (isTeamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY));
	    }

		
	    //INIT
	    rdv_point=true;
	    state = State.FIRST_RDV;
	    oldAngle = myGetHeading();

    	rdvY = (whoAmI == SBOT)? 1650 : 750;

	}

	@Override
	public void step() {
		
		//DEBUG MESSAGE
        boolean debug = true;

		detection();
		readMessages();
		
        if (debug && whoAmI == NBOT) {
        	sendLogMessage("#NBOT *thinks* (x,y)= ("+(int)myPos.getX()+", "+(int)myPos.getY()+") theta= "+(int)(myGetHeading()*180/(double)Math.PI)+"°. #State= "+state);
        }
        if (debug && whoAmI == SBOT) {
        	sendLogMessage("#SBOT *thinks* (x,y)= ("+(int)myPos.getX()+", "+(int)myPos.getY()+") theta= "+(int)(myGetHeading()*180/(double)Math.PI)+"°. #State= "+state);
        }
        
		if(rdv_point) {
			if (whoAmI == NBOT) {
				if (!isSameDirection(getHeading(), Parameters.NORTH) && firstTurning) {
					if (isTeamA) {
						stepTurn(Parameters.Direction.LEFT);
					} else stepTurn(Parameters.Direction.RIGHT);
					return;
				}
				if (myPos.getY() > rdvY) {
					firstTurning = false;
					myMove(true);
				} else if ((isTeamA && !isSameDirection(getHeading(), Parameters.EAST)) || (!isTeamA && !isSameDirection(getHeading(), Parameters.WEST))) {
					if (isTeamA) {
						stepTurn(Parameters.Direction.RIGHT);
					} else 	stepTurn(Parameters.Direction.LEFT);
					return;
				} else {
						rdv_point = false;
						state = State.MOVING;
					}
				return;
			} else {
				if (!isSameDirection(getHeading(), Parameters.SOUTH) && firstTurning) {
					if (isTeamA) {
						stepTurn(Parameters.Direction.RIGHT);
					} else 	stepTurn(Parameters.Direction.LEFT);
					return;
				}
				if (myPos.getY() < rdvY) {
					firstTurning = false;
					myMove(true);
				} else if ( (isTeamA && !isSameDirection(getHeading(), Parameters.EAST)) || (!isTeamA && !isSameDirection(getHeading(), Parameters.WEST))) {
					if (isTeamA) {
						stepTurn(Parameters.Direction.LEFT);
					} else stepTurn(Parameters.Direction.RIGHT);
					return;
				} else {
						rdv_point = false;
						state = State.MOVING;
					}
				return;
			}
		}
		isShooterAround = false;

		// J'avance si au moins un allié est à moins de 500 de distance
		for (Map.Entry<String, BotState> entry : allyPos.entrySet()) {
			double distance = distance(entry.getValue().getPosition(), myPos);

			if (entry.getValue().isAlive() && distance < DISTANCE_SCOUT_SHOOTER && entry.getKey() != NBOT && entry.getKey() != SBOT) {
				isShooterAround = true;
				break;
			}
		}
		
		if (getHealth() <= 0) {
			state = State.DEAD;
			allyPos.put(whoAmI, new BotState(myPos.getX(), myPos.getY(), false, whoAmI, getHeading()));
			broadcast("DEAD " + whoAmI);
			return;
		}
		if (freeze|| !isShooterAround) return;
	
		
		try {
			switch (state) {
				case MOVING :
					boolean b = false;
					for (Map.Entry<String, BotState> entry : allyPos.entrySet()) {
						double distance = distance(entry.getValue().getPosition(), myPos);
						if (entry.getValue().isAlive() && distance < DISTANCE_SCOUT_SHOOTER && entry.getKey() != NBOT && entry.getKey() != SBOT) {
							b = true;
							break;
						}
					}
					if (!b){
						myMove(false);
					}else {
						myMove(true);
					}
					break;
				case MOVING_BACK :
				    // Ici, on recule jusqu'à atteindre la cible calculée pour le recul
				    if (!hasReachedTarget(targetX, targetY, false)) {
				        myMove(false);
				    } else {
				        // Une fois la cible atteinte, on peut par exemple relancer l'évitement ou passer à un autre état
				        initiateObstacleAvoidance();
				    }
				    break;
				case TURNING_LEFT :
					turnLeft();
					break;
				case TURNING_RIGHT:
					turnRight();	
					break;
				default:
					break;	
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//=========================================ADDED=========================================

	@Override
	protected void detection () {
		// Détection des ennemis et envoi d'infos
		freeze = false;
		// Dans votre méthode principale
		for (IRadarResult o : detectRadar()) {
			double oX = myPos.getX() + o.getObjectDistance() * Math.cos(o.getObjectDirection());
			double oY = myPos.getY() + o.getObjectDistance() * Math.sin(o.getObjectDirection());
			if (allyPos.get(whoAmI).isAlive() && o.getObjectType() != IRadarResult.Types.BULLET) {
				if (state == State.MOVING_BACK) {
					for (Position p : getObstacleCorners(o, myPos.getX(), myPos.getY())) {
				        boolean obstacleInPath = isPointInTrajectory(myPos.getX(), myPos.getY(), normalize(getHeading() + Math.PI), p.getX(), p.getY());	
				        if (obstacleInPath) {
				            obstacleDirection = o.getObjectDirection();
							freeze = true;
				            initiateObstacleAvoidance(); // STRATEGIE ANTI NOOB
				        } 
				    }
				} 
				else {
					for (Position p : getObstacleCorners(o, myPos.getX(), myPos.getY())) {
				        boolean obstacleInPath = isPointInTrajectory(myPos.getX(), myPos.getY(), getHeading(), p.getX(), p.getY());
	
				        if (obstacleInPath) {
				            obstacleDirection = o.getObjectDirection();
							freeze = true;
				            initiateObstacleAvoidance(); // STRATEGIE ANTI NOOB
				        } 
				    }
				}
			}
			switch (o.getObjectType()) {
				case OpponentMainBot:
				case OpponentSecondaryBot:
					// Transmettre la position des ennemis : ENEMY dir dist type enemyX enemyY
					broadcast("ENEMY " + o.getObjectDirection() + " " + o.getObjectDistance() + " " + o.getObjectType() + " " + oX + " " + oY);
					if (o.getObjectDistance() < BOT_RADIUS*4) {
						broadcast("MOVING_BACK " + whoAmI + " " + oX + " " + oY);
						freeze = true;
					}
					break;
			
				case Wreck:
					broadcast("WRECK " + oX + " " + oY);
					break;
				default:
					break;
			}
	    	
	    }		
	}
		
	// Interprète les messages des alliés
	private void readMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            String[] parts = msg.split(" ");
            switch (parts[0]) {
	            case "POS" :
	            	double allyX = Double.parseDouble(parts[2]);
	                double allyY = Double.parseDouble(parts[3]);
	                double heading = Double.parseDouble(parts[4]);
	            	allyPos.put(parts[1], new BotState(allyX, allyY, true,parts[1], heading));
	            	break;
				case "DEAD" :
					BotState bot = allyPos.get(parts[1]);
					bot.setAlive(false);
                    break;
            }
        }
    }


}