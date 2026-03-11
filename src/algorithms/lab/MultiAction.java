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

    @Override
    public void activate() {
        initDirTaskDone = false;
        previous_angle = getHeading();
        turnRightTask = false;
        i = 0;
    }

    @Override
    public void step() {
        if (Math.random()<0.01) {
            fire(Math.random()*Math.PI*2);
            return;
        }
        if (!initDirTaskDone) {
            if (!SameDirection.compute(getHeading(), Parameters.EAST)) {
                stepTurn(Parameters.Direction.LEFT);
            } else {
                previous_angle = getHeading();
                initDirTaskDone = true;
            }
            return;
        }
        if (detectRadar().stream().anyMatch(o -> IRadarResult.Types.OpponentSecondaryBot != o.getObjectType()) && !turnRightTask) {
//            if (i % 2 == 0) {
//                stepTurn(Parameters.Direction.RIGHT);
//            } else {
//
//            }
            move();
            i++;
            return;
        }
        if (detectRadar().stream().anyMatch(o -> IRadarResult.Types.OpponentSecondaryBot == o.getObjectType())) {
            stepTurn(Parameters.Direction.RIGHT);
            return;
        }
    }

}
