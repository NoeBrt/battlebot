package supportGUI;

import robotsimulator.Bot;
import robotsimulator.SimulatorEngine;
import characteristics.Parameters;

import characteristics.Parameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.*;

/**
 * Headless match runner that uses the real GUI infrastructure (Viewer/MainPanel/SimulatorPanel)
 * but hides the window and programmatically presses the 2 start buttons.
 * This guarantees the exact same initialization and behavior as the GUI.
 */
public class HeadlessMatchRunner {

    private static class MatchResult {
        double scoreA, scoreB;
        int winA, winB, draw;
        int deadMainA, deadSecA, deadMainB, deadSecB;
        double hpRatioA, hpRatioB;
        long elapsedMs;
        int teamAId, teamBId;
    }

    // Log stream: writes to file. Null if no log dir specified.
    private static PrintStream log;

    public static void main(String[] args) throws Exception {
        int n = (args.length > 0) ? Integer.parseInt(args[0]) : 5;
        long timeoutMs = (args.length > 1) ? Long.parseLong(args[1]) : 90000L;
        int delayMs = (args.length > 2) ? Integer.parseInt(args[2]) : 1;
        String logDir = (args.length > 3) ? args[3] : "logs";
        

        // Setup log file
        File logDirFile = new File(logDir);
        logDirFile.mkdirs();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File logFile = new File(logDirFile, "match_" + timestamp + ".log");
        log = new PrintStream(new FileOutputStream(logFile), true);
        System.out.println("Logging details to: " + logFile.getAbsolutePath());
        logBoth("=== Headless Match Run ===");
        logBoth(String.format("matches=%d timeout=%dms delay=%dms", n, timeoutMs, delayMs));
        logBoth(String.format("TeamA strategies: main=%s secondary=%s",
                Parameters.teamAMainBotBrainClassName,
                Parameters.teamASecondaryBotBrainClassName));
        logBoth(String.format("TeamB strategies: main=%s secondary=%s",
                Parameters.teamBMainBotBrainClassName,
                Parameters.teamBSecondaryBotBrainClassName));
        logBoth("Team A (" + Parameters.teamAName + "): Main=" + Parameters.teamAMainBotBrainClassName
                + "  Secondary=" + Parameters.teamASecondaryBotBrainClassName);
        logBoth("Team B (" + Parameters.teamBName + "): Main=" + Parameters.teamBMainBotBrainClassName
                + "  Secondary=" + Parameters.teamBSecondaryBotBrainClassName);

        double[] diffs = new double[n];
        int winsA = 0, winsB = 0, draws = 0;
        double sumA = 0.0, sumB = 0.0;
        double sumDeadMainA = 0, sumDeadSecA = 0, sumDeadMainB = 0, sumDeadSecB = 0;
        double sumHpA = 0, sumHpB = 0;
        long sumTimeMs = 0;

        for (int i = 0; i < n; i++) {
            System.out.printf(">>> Match %d/%d...", i + 1, n);
            log.printf("%n>>> Starting match %d/%d...%n", i + 1, n);
            MatchResult r = runOne(timeoutMs, delayMs);
            sumA += r.scoreA;
            sumB += r.scoreB;
            diffs[i] = r.scoreA - r.scoreB;
            winsA += r.winA;
            winsB += r.winB;
            draws += r.draw;
            sumDeadMainA += r.deadMainA;
            sumDeadSecA  += r.deadSecA;
            sumDeadMainB += r.deadMainB;
            sumDeadSecB  += r.deadSecB;
            sumHpA       += r.hpRatioA;
            sumHpB       += r.hpRatioB;
            sumTimeMs    += r.elapsedMs;

            String result = r.winA == 1 ? "A_WIN" : r.winB == 1 ? "B_WIN" : "DRAW";
            // Console: concise one-liner
            System.out.printf(" %s  A=%.3f B=%.3f  (%.1fs)%n", result, r.scoreA, r.scoreB, r.elapsedMs / 1000.0);
            // Log file: full details
            log.printf("MATCH %d: elapsed=%dms scoreA=%.3f scoreB=%.3f hpA=%.3f hpB=%.3f deadA(main=%d,sec=%d) deadB(main=%d,sec=%d) result=%s%n",
                    i + 1, r.elapsedMs, r.scoreA, r.scoreB, r.hpRatioA, r.hpRatioB,
                    r.deadMainA, r.deadSecA, r.deadMainB, r.deadSecB, result);
        }

        double meanDiff = mean(diffs);
        double stdDiff = stddev(diffs, meanDiff);

        String summary = String.format(
                "=== SUMMARY ===%n" +
                "matches=%d%n" +
                "avgScoreA=%.3f avgScoreB=%.3f%n" +
                "winRateA=%.3f winRateB=%.3f drawRate=%.3f%n" +
                "avgDeadMainA=%.3f avgDeadSecA=%.3f%n" +
                "avgDeadMainB=%.3f avgDeadSecB=%.3f%n" +
                "avgHpA=%.3f avgHpB=%.3f%n" +
                "avgTimeMs=%.0f%n" +
                "mean(scoreA-scoreB)=%.3f std=%.3f",
                n,
                sumA / n, sumB / n,
                winsA / (double) n, winsB / (double) n, draws / (double) n,
                sumDeadMainA / n, sumDeadSecA / n,
                sumDeadMainB / n, sumDeadSecB / n,
                sumHpA / n, sumHpB / n,
                sumTimeMs / (double) n,
                meanDiff, stdDiff);
        logBoth(summary);
        logBoth("Log saved: " + logFile.getAbsolutePath());
        log.close();

        System.exit(0);
    }

    /** Print to both console and log file */
    private static void logBoth(String msg) {
        System.out.println(msg);
        if (log != null) log.println(msg);
    }

    /** Print to log file only */
    private static void logOnly(String msg) {
        if (log != null) log.println(msg);
    }

    private static MatchResult runOne(long timeoutMs, int delayMs) throws Exception {
        // ---- 1. Launch the REAL GUI on the EDT and wait for it ----
        // Viewer.main uses invokeLater, so we must wait for the EDT to finish creating FramedGUI.
        Viewer.main(new String[0]);

        // Wait until framedGUI is populated (Viewer.main posts to EDT asynchronously)
        Field framedGuiField = Viewer.class.getDeclaredField("framedGUI");
        framedGuiField.setAccessible(true);
        JFrame frame = null;
        for (int wait = 0; wait < 50; wait++) {
            Thread.sleep(100);
            frame = (JFrame) framedGuiField.get(null);
            if (frame != null) break;
        }
        if (frame == null) throw new RuntimeException("FramedGUI did not initialize");

        // Ensure EDT has finished all pending tasks
        SwingUtilities.invokeAndWait(() -> {});

        // Keep visible but move off-screen (setVisible(false) kills the Swing repaint loop
        // which the SimulatorEngine timer depends on)
        final JFrame f = frame;
        SwingUtilities.invokeAndWait(() -> f.setLocation(-2000, -2000));

        Field mainPanelField = FramedGUI.class.getDeclaredField("mainPanel");
        mainPanelField.setAccessible(true);
        Object mainPanel = mainPanelField.get(frame);

        // ---- 2. Programmatically press the 2 buttons ON THE EDT ----
        // Button 1: MainPanel.firstAction() — switches from greeting screen to simulator panel
        Method firstActionMethod = MainPanel.class.getDeclaredMethod("firstAction");
        firstActionMethod.setAccessible(true);
        final Object mp = mainPanel;
        SwingUtilities.invokeAndWait(() -> {
            try { firstActionMethod.invoke(mp); } catch (Exception e) { throw new RuntimeException(e); }
        });

        // Small pause to let the panel switch settle
        Thread.sleep(200);

        // Button 2: MainPanel.startSimulation() — actually starts the engine
        Method startSimMethod = MainPanel.class.getDeclaredMethod("startSimulation");
        startSimMethod.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try { startSimMethod.invoke(mp); } catch (Exception e) { throw new RuntimeException(e); }
        });

        // ---- 3. Get the SimulatorEngine ----
        Field simField = MainPanel.class.getDeclaredField("sim");
        simField.setAccessible(true);
        Object simPanel = simField.get(mainPanel);

        Field engineField = SimulatorPanel.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        SimulatorEngine engine = (SimulatorEngine) engineField.get(simPanel);

        // ---- 4. Speed up the clock on the EDT ----
        Field gameClockField = SimulatorEngine.class.getDeclaredField("gameClock");
        gameClockField.setAccessible(true);
        Timer gameClock = (Timer) gameClockField.get(engine);
        SwingUtilities.invokeAndWait(() -> {
            gameClock.setDelay(delayMs);
            gameClock.setInitialDelay(delayMs);
        });

        logOnly("Simulation started (hidden). Clock delay=" + delayMs + "ms, timeout=" + timeoutMs + "ms");

        // ---- 6. Monitor the match ----
        long start = System.currentTimeMillis();
        int teamAId = Integer.MIN_VALUE;
        int teamBId = Integer.MIN_VALUE;
        long lastPrint = 0;

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

            // Log bot states every 2s (to file only)
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed - lastPrint > 2000) {
                lastPrint = elapsed;
                logOnly(String.format("--- T=%dms ---", elapsed));
                for (Bot b : bots) {
                    String team = (b.getTeam() == teamAId) ? "A" : "B";
                    boolean isMain = b.getMaxHealth() > 150.0;
                    String role = isMain ? "Main" : "Sec";
                    String status = b.isDestroyed() ? "DEAD"
                            : String.format("HP=%.0f/%.0f", b.getHealth(), b.getMaxHealth());
                    logOnly(String.format("  %s-%s: %s at (%.0f, %.0f) heading=%.2f",
                            team, role, status, b.getX(), b.getY(), b.getHeading()));
                }
            }

            // Check end condition: one team fully eliminated
            boolean aAlive = false, bAlive = false;
            for (Bot b : bots) {
                if (!b.isDestroyed()) {
                    if (b.getTeam() == teamAId) aAlive = true;
                    if (b.getTeam() == teamBId) bAlive = true;
                }
            }
            if (!aAlive || !bAlive) break;
        }

        // ---- 7. Stop & compute results ----
        gameClock.stop();
        engine.pauseSimulation();

        MatchResult r = computeResult(engine, teamAId, teamBId, System.currentTimeMillis() - start);

        // ---- 8. Cleanup: dispose frame AND reset Viewer's static field for next match ----
        final JFrame fCleanup = f;
        SwingUtilities.invokeAndWait(() -> fCleanup.dispose());
        framedGuiField.set(null, null);

        return r;
    }

    private static MatchResult computeResult(SimulatorEngine engine, int teamAId, int teamBId, long elapsedMs) {
        MatchResult r = new MatchResult();
        r.elapsedMs = elapsedMs;
        r.teamAId = teamAId;
        r.teamBId = teamBId;

        final double MAIN_W = 1.0;
        final double SEC_W = 0.75;
        final double KILL_WEIGHT = 0.6;
        final double DAMAGE_WEIGHT = 0.3;
        final double SURVIVAL_WEIGHT = 0.1;

        int deadMainA = 0, deadSecA = 0, aliveMainA = 0, aliveSecA = 0;
        int deadMainB = 0, deadSecB = 0, aliveMainB = 0, aliveSecB = 0;
        double aliveHealthWeightedA = 0.0, aliveHealthWeightedB = 0.0;

        if (teamAId == Integer.MIN_VALUE) teamAId = 0;
        if (teamBId == Integer.MIN_VALUE) teamBId = 1;

        for (Bot b : engine.getBots()) {
            boolean isMain = b.getMaxHealth() > 150.0;
            boolean dead = b.isDestroyed();
            if (b.getTeam() == teamAId) {
                if (dead) { if (isMain) deadMainA++; else deadSecA++; }
                else {
                    if (isMain) aliveMainA++; else aliveSecA++;
                    aliveHealthWeightedA += (isMain ? MAIN_W : SEC_W) * (b.getHealth() / b.getMaxHealth());
                }
            } else if (b.getTeam() == teamBId) {
                if (dead) { if (isMain) deadMainB++; else deadSecB++; }
                else {
                    if (isMain) aliveMainB++; else aliveSecB++;
                    aliveHealthWeightedB += (isMain ? MAIN_W : SEC_W) * (b.getHealth() / b.getMaxHealth());
                }
            }
        }

        double totalW = 3 * MAIN_W + 2 * SEC_W;
        double killRatioA = (deadMainB * MAIN_W + deadSecB * SEC_W) / totalW;
        double killRatioB = (deadMainA * MAIN_W + deadSecA * SEC_W) / totalW;
        double enemyDamageRatioA = 1.0 - aliveHealthWeightedB / totalW;
        double enemyDamageRatioB = 1.0 - aliveHealthWeightedA / totalW;
        double survivalRatioA = (aliveMainA * MAIN_W + aliveSecA * SEC_W) / totalW;
        double survivalRatioB = (aliveMainB * MAIN_W + aliveSecB * SEC_W) / totalW;

        r.scoreA = KILL_WEIGHT * killRatioA + DAMAGE_WEIGHT * enemyDamageRatioA + SURVIVAL_WEIGHT * survivalRatioA;
        r.scoreB = KILL_WEIGHT * killRatioB + DAMAGE_WEIGHT * enemyDamageRatioB + SURVIVAL_WEIGHT * survivalRatioB;
        r.hpRatioA = aliveHealthWeightedA / totalW;
        r.hpRatioB = aliveHealthWeightedB / totalW;
        r.deadMainA = deadMainA; r.deadSecA = deadSecA;
        r.deadMainB = deadMainB; r.deadSecB = deadSecB;

        if (r.scoreA > r.scoreB) r.winA = 1;
        else if (r.scoreB > r.scoreA) r.winB = 1;
        else r.draw = 1;

        return r;
    }

    private static double mean(double[] x) {
        double s = 0; for (double v : x) s += v; return s / x.length;
    }

    private static double stddev(double[] x, double m) {
        double s = 0; for (double v : x) { double d = v - m; s += d * d; } return Math.sqrt(s / x.length);
    }
}
