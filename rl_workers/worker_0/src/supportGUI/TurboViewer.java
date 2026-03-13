package supportGUI;

import robotsimulator.SimulatorEngine;

import javax.swing.*;
import java.lang.reflect.Field;

public class TurboViewer {
    public static void main(String[] args) throws Exception {
        int delayMs = (args.length > 0) ? Integer.parseInt(args[0]) : 1;
        if (delayMs < 1) delayMs = 1;

        Viewer.main(new String[0]);

        // Let GUI initialize, then patch engine timer delay through reflection.
        Thread.sleep(600);

        Field framedGuiField = Viewer.class.getDeclaredField("framedGUI");
        framedGuiField.setAccessible(true);
        Object framedGui = framedGuiField.get(null);

        Field mainPanelField = FramedGUI.class.getDeclaredField("mainPanel");
        mainPanelField.setAccessible(true);
        Object mainPanel = mainPanelField.get(framedGui);

        Field simField = MainPanel.class.getDeclaredField("sim");
        simField.setAccessible(true);
        Object simPanel = simField.get(mainPanel);

        Field engineField = SimulatorPanel.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        SimulatorEngine engine = (SimulatorEngine) engineField.get(simPanel);

        Field gameClockField = SimulatorEngine.class.getDeclaredField("gameClock");
        gameClockField.setAccessible(true);
        Timer gameClock = (Timer) gameClockField.get(engine);

        gameClock.setDelay(delayMs);
        gameClock.setInitialDelay(delayMs);

        System.out.println("TurboViewer active: gameClock delay=" + delayMs + "ms");
    }
}
