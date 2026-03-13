package algorithms.external;

import characteristics.IRadarResult;

public class Ennemy {
	double x, y;
	double previousX, previousY;
	double prevPreviousX, prevPreviousY;
	double distance, direction, previousDirection;
	IRadarResult.Types type;
	double speedX, speedY;
	boolean hasMovedTwice;
	double predictedX, predictedY;

	public Ennemy(double x, double y, double distance, double direction, IRadarResult.Types type) {
		this.x = x;
		this.y = y;
		this.distance = distance;
		this.direction = direction;
		this.previousDirection = direction;
		this.previousX = x;
		this.previousY = y;
		this.prevPreviousX = x;
		this.prevPreviousY = y;
		this.type = type;
		this.speedX = 0;
		this.speedY = 0;
		this.hasMovedTwice = false;
		this.predictedX = x;
		this.predictedY = y;
	}

	public void updatePosition(double newX, double newY, double newDistance, double newDirection) {
		this.prevPreviousX = this.previousX;
		this.prevPreviousY = this.previousY;
		this.previousX = this.x;
		this.previousY = this.y;
		this.previousDirection = this.direction;

		this.x = newX;
		this.y = newY;
		this.distance = newDistance;
		this.direction = newDirection;

		if (hasMovedTwice) {
			double dx = x - previousX;
			double dy = y - previousY;
			this.speedX = dx; // Vitesse actuelle
			this.speedY = dy;
		} else if (x != previousX || y != previousY) {
			hasMovedTwice = true;
		}
	}

	public void predictPosition(double bulletTravelTime) {
		if (!hasMovedTwice) {
			this.predictedX = x;
			this.predictedY = y;
		} else {
			// Calcul de l'accélération ou détection d'oscillation
			double prevDx = previousX - prevPreviousX;
			double prevDy = previousY - prevPreviousY;
			double currentDx = x - previousX;
			double currentDy = y - previousY;

			// Vérifie si le mouvement change de direction (oscillation)
			boolean isOscillatingX = (prevDx * currentDx < 0); // Changement de signe en X
			boolean isOscillatingY = (prevDy * currentDy < 0); // Changement de signe en Y

			if (isOscillatingX || isOscillatingY) {
				// Si oscillation détectée, limiter la prédiction à une position moyenne ou actuelle
				this.predictedX = (x + previousX) / 2; // Position moyenne comme approximation
				this.predictedY = (y + previousY) / 2;
			} else {
				// Mouvement linéaire : extrapolation basée sur la vitesse actuelle
				this.predictedX = x + speedX * bulletTravelTime;
				this.predictedY = y + speedY * bulletTravelTime;
			}
		}
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getPredictedX() {
		return predictedX;
	}

	public double getPredictedY() {
		return predictedY;
	}

	public IRadarResult.Types getType() {
		return type;
	}
}
