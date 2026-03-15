package supportGUI;

import robotsimulator.SimulatorEngine;
import robotsimulator.Bot;
import javax.swing.Timer;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class AutoRecordingViewer {
    public static void main(String[] args) {
        try {
            System.out.println("AutoRecordingViewer: Starting Viewer...");
            
            // 1. Launch the original Viewer
            new Thread(() -> {
                try {
                    supportGUI.Viewer.main(new String[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // 2. Wait for GUI initialization
            Thread.sleep(5000); // 5s to be safe

            // 3. Automate Key Inputs (More robust sequence)
            Robot robot = new Robot();
            System.out.println("Pressing keys...");
            
            // Splash screen?
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_SPACE);
            Thread.sleep(500);
            
            // Maybe ENTER to start?
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            Thread.sleep(500);

            // Maybe P to play?
            robot.keyPress(KeyEvent.VK_P);
            robot.keyRelease(KeyEvent.VK_P);
            Thread.sleep(500);
            
            // Try SPACE again
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_SPACE);
            Thread.sleep(500);

            // 4. Hook internal engine state
            Field framedGuiField = supportGUI.Viewer.class.getDeclaredField("framedGUI");
            framedGuiField.setAccessible(true);
            Object framedGui = null;
            
            for (int i=0; i<20; i++) {
                try {
                    framedGui = framedGuiField.get(null);
                    if (framedGui != null) break;
                } catch (Exception e) {}
                Thread.sleep(500);
            }
            if (framedGui == null) {
                System.out.println("Failed to access GUI. Exiting.");
                System.exit(1);
            }

            Field mainPanelField = framedGui.getClass().getDeclaredField("mainPanel");
            mainPanelField.setAccessible(true);
            Object mainPanel = mainPanelField.get(framedGui);

            Field simField = mainPanel.getClass().getDeclaredField("sim");
            simField.setAccessible(true);
            Object simPanel = simField.get(mainPanel);

            Field engineField = simPanel.getClass().getDeclaredField("engine");
            engineField.setAccessible(true);
            SimulatorEngine engine = (SimulatorEngine) engineField.get(simPanel);

            System.out.println("Engine hooked. Monitoring health...");
            
            long startTime = System.currentTimeMillis();
            boolean matchRunning = false;
            
            while (true) {
                ArrayList<Bot> bots = engine.getBots();
                if (bots != null) {
                    if (bots.size() == 0) {
                        System.out.println("No bots found...");
                    } else {
                        System.out.println("Bots detected: " + bots.size());
                    }
                    
                    int aliveTeamA = 0;
                    int aliveTeamB = 0;
                    // Usually total ~6 bots?
                    for (Bot b : bots) {
                        if (b.getHealth() > 0) {
                            if (b.getTeam() == 0) aliveTeamA++;
                            else aliveTeamB++;
                        }
                    }
                    
                    if (!matchRunning) {
                        if (aliveTeamA > 0 && aliveTeamB > 0) {
                            matchRunning = true;
                            System.out.println("Match detected running (A:" + aliveTeamA + " B:" + aliveTeamB + ")");
                        } else {
                            // Try forcing start if not running yet?
                            if (System.currentTimeMillis() - startTime > 10000 && !matchRunning) {
                                // Maybe press SPACE?
                            }
                        }
                    } else {
                        // Match running
                        if (aliveTeamA == 0 || aliveTeamB == 0) {
                            System.out.println("Match finished. Exiting.");
                            Thread.sleep(10000); // 6s to see end
                            System.exit(0);
                        }
                    }
                }
                
                if (System.currentTimeMillis() - startTime > 300000) { // 90s max
                    System.out.println("Timeout reached.");
                    System.exit(0);
                }
                Thread.sleep(500);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
