package supportGUI;

import javax.swing.*;
import java.lang.reflect.Field;

public class HiddenTurboViewer {
    public static void main(String[] args) throws Exception {
        int delayMs = (args.length > 0) ? Integer.parseInt(args[0]) : 1;
        if (delayMs < 1) delayMs = 1;

        Viewer.main(new String[0]);
        Thread.sleep(600);

        Field framedGuiField = Viewer.class.getDeclaredField("framedGUI");
        framedGuiField.setAccessible(true);
        JFrame frame = (JFrame) framedGuiField.get(null);

        // Hide window but keep simulation running.
        frame.setVisible(false);

        // Reuse TurboViewer clock acceleration via reflection path.
        Field mainPanelField = FramedGUI.class.getDeclaredField("mainPanel");
        mainPanelField.setAccessible(true);
        Object mainPanel = mainPanelField.get(frame);

        Field simField = MainPanel.class.getDeclaredField("sim");
        simField.setAccessible(true);
        Object simPanel = simField.get(mainPanel);

        Field engineField = SimulatorPanel.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        Object engine = engineField.get(simPanel);

        Field gameClockField = engine.getClass().getDeclaredField("gameClock");
        gameClockField.setAccessible(true);
        Timer gameClock = (Timer) gameClockField.get(engine);
        gameClock.setDelay(delayMs);
        gameClock.setInitialDelay(delayMs);

        System.out.println("HiddenTurboViewer active: hidden=true delay=" + delayMs + "ms");

        // Keep process alive while simulation runs in background
        Thread.currentThread().join();
    }
}
