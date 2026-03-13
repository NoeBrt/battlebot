package algorithms.example;

import characteristics.IFrontSensorResult;
import characteristics.Parameters;
import robotsimulator.Brain;

public class FollowWallLeft extends Brain {

    private static final double ANGLEPRECISION = 0.1;

    // Grafcet steps
    private static final int STEP_0  = 0;
    private static final int STEP_1  = 1;
    private static final int STEP_2A = 2;
    private static final int STEP_2  = 3;
    private static final int STEP_3A = 4;
    private static final int STEP_3  = 5;

    private int state;
    private double oldAngle;

    public FollowWallLeft() { super(); }

    @Override
    public void activate() {
        state = STEP_0;
    }

    @Override
    public void step() {

        switch (state) {

            // 0: oldAngle <- getHeading
            case STEP_0:
                oldAngle = getHeading();
                state = STEP_1;
                return;

            // 1: turn left until facing NORTH
            case STEP_1:
                if (!isSameDirection(getHeading(), Parameters.SOUTH)) {
                    stepTurn(Parameters.Direction.LEFT);
                    return;
                }
                state = STEP_2A;
                return;

            // 2a: move once
            case STEP_2A:
                move();
                state = STEP_2;
                return;

            // 2: move while no wall
            case STEP_2:
                if (detectFront().getObjectType() != IFrontSensorResult.Types.WALL) {
                    move();
                    return;
                }
                state = STEP_3A;
                return;

            // 3a: save angle and start right turn
            case STEP_3A:
                oldAngle = getHeading();
                stepTurn(Parameters.Direction.RIGHT);
                state = STEP_3;
                return;

            // 3: continue right turn until 90 degrees
            case STEP_3:
                double target = oldAngle + Parameters.RIGHTTURNFULLANGLE;
                if (!isSameDirection(getHeading(), target)) {
                    stepTurn(Parameters.Direction.RIGHT);
                    return;
                }
                state = STEP_2A;
                return;

            default:
                state = STEP_0;
        }
    }

    private boolean isSameDirection(double dir1, double dir2) {
        return Math.abs(normalize(dir1) - normalize(dir2)) < ANGLEPRECISION;
    }

    private double normalize(double dir) {
        while (dir < 0) dir += 2 * Math.PI;
        while (dir >= 2 * Math.PI) dir -= 2 * Math.PI;
        return dir;
    }
}