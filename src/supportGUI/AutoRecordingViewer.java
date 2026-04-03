package supportGUI;

import robotsimulator.SimulatorEngine;
import robotsimulator.Bot;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Recording-friendly match launcher. Uses the same reflection-based startup
 * as HeadlessMatchRunner but keeps the window visible for screen capture.
 * Exits when one team is eliminated or after a 5-minute timeout.
 */
public class AutoRecordingViewer {
    public static void main(String[] args) {
        try {
            long timeoutMs = 300000; // 5 minutes
            int delayMs = 1;
            if (args.length > 0) {
                timeoutMs = Long.parseLong(args[0]);
            }
            if (args.length > 1) {
                delayMs = Integer.parseInt(args[1]);
            }

            System.out.println("AutoRecordingViewer: launching with timeout=" + timeoutMs + "ms delay=" + delayMs + "ms");

            // 1. Launch Viewer (posts to EDT asynchronously)
            Viewer.main(new String[0]);

            // 2. Wait for framedGUI to be created
            Field framedGuiField = Viewer.class.getDeclaredField("framedGUI");
            framedGuiField.setAccessible(true);
            JFrame frame = null;
            for (int i = 0; i < 200; i++) {
                Thread.sleep(10);
                frame = (JFrame) framedGuiField.get(null);
                if (frame != null) break;
            }
            if (frame == null) {
                System.err.println("GUI did not initialize");
                System.exit(1);
            }
            SwingUtilities.invokeAndWait(() -> {});

            // 3. Get mainPanel
            Field mainPanelField = FramedGUI.class.getDeclaredField("mainPanel");
            mainPanelField.setAccessible(true);
            Object mainPanel = mainPanelField.get(frame);

            // 4. Press button 1: firstAction() — greeting -> simulator panel
            Method firstAction = MainPanel.class.getDeclaredMethod("firstAction");
            firstAction.setAccessible(true);
            final Object mp = mainPanel;
            SwingUtilities.invokeAndWait(() -> {
                try { firstAction.invoke(mp); } catch (Exception e) { throw new RuntimeException(e); }
            });
            Thread.sleep(20);

            // 5. Press button 2: startSimulation() — start the engine
            Method startSim = MainPanel.class.getDeclaredMethod("startSimulation");
            startSim.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try { startSim.invoke(mp); } catch (Exception e) { throw new RuntimeException(e); }
            });

            // 6. Get the engine
            Field simField = MainPanel.class.getDeclaredField("sim");
            simField.setAccessible(true);
            Object simPanel = simField.get(mainPanel);

            Field engineField = SimulatorPanel.class.getDeclaredField("engine");
            engineField.setAccessible(true);
            SimulatorEngine engine = (SimulatorEngine) engineField.get(simPanel);

            // Speed up the clock
            Field gameClockField = SimulatorEngine.class.getDeclaredField("gameClock");
            gameClockField.setAccessible(true);
            Timer gameClock = (Timer) gameClockField.get(engine);
            final int d = delayMs;
            SwingUtilities.invokeAndWait(() -> {
                gameClock.setDelay(d);
                gameClock.setInitialDelay(d);
            });

            System.out.println("Match started. Monitoring...");

            // 7. Monitor until one team is eliminated or timeout
            long start = System.currentTimeMillis();
            int teamAId = Integer.MIN_VALUE;
            int teamBId = Integer.MIN_VALUE;

            while (System.currentTimeMillis() - start < timeoutMs) {
                Thread.sleep(50);
                ArrayList<Bot> bots = engine.getBots();
                if (bots == null || bots.isEmpty()) continue;

                // Discover team IDs
                for (Bot b : bots) {
                    int t = b.getTeam();
                    if (teamAId == Integer.MIN_VALUE) teamAId = t;
                    else if (t != teamAId && teamBId == Integer.MIN_VALUE) teamBId = t;
                }
                if (teamAId == Integer.MIN_VALUE || teamBId == Integer.MIN_VALUE) continue;

                // Check end: one team fully dead
                boolean aAlive = false, bAlive = false;
                for (Bot b : bots) {
                    if (!b.isDestroyed()) {
                        if (b.getTeam() == teamAId) aAlive = true;
                        if (b.getTeam() == teamBId) bAlive = true;
                    }
                }
                if (!aAlive || !bAlive) {
                    String winner = !aAlive ? "B" : "A";
                    System.out.println("Match over. Winner: Team " + winner);
                    Thread.sleep(3000); // linger so the recording catches the end
                    break;
                }
            }

            System.out.println("Exiting.");
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
