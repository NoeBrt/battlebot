package algorithms.external;

public class BotState {
	private Position position = new Position(0, 0);
	private boolean isAlive = true;
    String whoAmI;
    double getHeading;

	public BotState() {}
	public BotState(double x, double y, boolean alive, String whoAmI, double getHeading) {
		position.setX(x);
		position.setY(y);
		isAlive = alive;
        this.whoAmI = whoAmI;
        this.getHeading = getHeading;
	}

	public void setPosition(double x, double y, double getHeading) {
		position.setX(x);
		position.setY(y);
		this.getHeading = getHeading;
	}
	
	public Position getPosition() {return position;}
	public void setAlive(boolean alive) {isAlive = alive;}
	public boolean isAlive() {return isAlive;}
}
