package algorithms.lab;

import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import characteristics.Parameters;
import robotsimulator.Brain;
import util.SameDirection;

public class MultiAction extends Brain {
    private boolean initDirTaskDone;
    private double previous_angle;
    private boolean turnRightTask;
    private int i;
    private int k;
    private Parameters.Direction dir;

    @Override
    public void activate() {
        initDirTaskDone = false;
        previous_angle = getHeading();
        turnRightTask = false;
        i = 0;
        k = 0;
        dir = Parameters.Direction.RIGHT;
    }

    @Override
    public void step() {
        if (!initDirTaskDone) {
            if (!SameDirection.compute(getHeading(), Parameters.EAST)) {
                stepTurn(Parameters.Direction.LEFT);
            } else {
                previous_angle = getHeading();
                initDirTaskDone = true;
            }
            return;
        }
        if (i % 5 == 4) k++;
        if (k % 2 == 0) dir =  Parameters.Direction.RIGHT;
        else dir = Parameters.Direction.LEFT;

        if (i % 5 == 0) {
            stepTurn(dir);
        }
        else if (i % 5 == 1 || i % 5 == 2 || i % 5 == 3) {
            move();
        } else {
            fire(Math.random()*Math.PI*2);
        }
        i++;
    }

}
