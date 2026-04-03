package algorithms.rl;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RLConfig {

    // Tunable parameters — NOT final so javac won't inline them.
    // Defaults here are the current best trained values.
    // Override at runtime with: java -Drl.config=path/to/config.json
    public static int    STALE_TTL = 693;
    public static double TARGET_PROXIMITY_WEIGHT = 765.948092;
    public static double TARGET_TYPE_BONUS = 230.530894;
    public static double TARGET_LOWHP_CRITICAL_BONUS = 189.572630;
    public static double TARGET_LOWHP_MODERATE_BONUS = 115.327775;
    public static double TARGET_FOCUS_BONUS = 360.264254;
    public static double TARGET_SAFEFIRE_BONUS = 172.014766;
    public static double TARGET_STALE_PENALTY = 8.639297;
    public static double PF_ENEMY_REPEL_STRENGTH = 1.930377;
    public static double PF_ENEMY_ATTRACT_STRENGTH = 1.832924;
    public static double PF_TANGENTIAL_STRENGTH = 0.665129;
    public static double PF_ALLY_REPEL_RANGE = 124.230752;
    public static double PF_ALLY_REPEL_STRENGTH = 0.903529;
    public static double PF_WALL_STRENGTH = 0.278072;
    public static double PF_WRECK_RANGE = 289.841694;
    public static double HOLD_X_OFFSET = 1180.365073;
    public static double KITE_MIN_AGGRO = 332.300267;
    public static double KITE_MAX_AGGRO = 651.520595;
    public static double KITE_MIN_NORMAL = 227.989347;
    public static double KITE_MAX_NORMAL = 551.063255;
    public static double KITE_MIN_DEFEN = 386.735487;
    public static double KITE_MAX_DEFEN = 984.302775;
    public static double HP_RETREAT_MAIN = 47.044497;
    public static int    NOFIRE_REPOSITION_TICKS = 19;
    public static double HP_RETREAT_PCT_SEC = 0.300054;
    public static double FLANK_Y_OFFSET = 406.271603;
    public static double ADVANCE_X_A = 1342.731860;
    public static double PATROL_EVASION_RANGE = 243.502084;

    // Fixed constants (never overridden by config)
    public static final double MAP_WIDTH = 3000.0;
    public static final double MAP_HEIGHT = 2000.0;
    public static final double MAP_CX = 1500.0;
    public static final double MAP_CY = 1000.0;
    public static final double FORMATION_Y_BASE = 1000.0;
    public static final double FORMATION_Y_OFFSET = 260.0;
    public static final double WALL_MARGIN = 200.0;
    public static final double SAFE_ZONE_X = 200.0;
    public static final double FLANK_OFFSET = 150.0;
    public static final double HEALTH_HIGH_THRESHOLD = 200.0;
    public static final double HEALTH_LOW_THRESHOLD = 100.0;
    public static final double PATROL_THRESHOLD = 200.0;
    public static final double FIRING_ANGLE_TOLERANCE = 0.2;
    public static final double FIRING_SAFETY_RADIUS = 55.0; // 50 bot radius + 5 bullet radius
    public static final double TANGENTIAL_SCALE_REF = 100.0;
    public static final double TANGENTIAL_MIN_DIST = 80.0;
    public static final double MAX_HEALTH_MAIN = 300.0;
    public static final double MAX_HEALTH_SEC = 100.0;

    static {
        String configPath = System.getProperty("rl.config");
        if (configPath != null && !configPath.isEmpty()) {
            loadConfig(configPath);
        }
    }

    public static void loadConfig(String path) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(path)));
            Map<String, Double> params = parseJsonParams(json);
            int loaded = 0;
            for (Map.Entry<String, Double> entry : params.entrySet()) {
                try {
                    Field f = RLConfig.class.getDeclaredField(entry.getKey());
                    if (f.getType() == int.class) {
                        f.setInt(null, (int) Math.round(entry.getValue()));
                        loaded++;
                    } else if (f.getType() == double.class) {
                        f.setDouble(null, entry.getValue());
                        loaded++;
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (IllegalAccessException e) {
                    System.err.println("[RLConfig] Cannot set field " + entry.getKey() + ": " + e.getMessage());
                }
            }
            System.out.println("[RLConfig] Loaded " + loaded + " params from " + path);
        } catch (Exception e) {
            System.err.println("[RLConfig] Failed to load " + path + ": " + e.getMessage());
        }
    }

    private static Map<String, Double> parseJsonParams(String json) {
        Map<String, Double> params = new HashMap<>();

        // Find "paramsA" or "params" section; fall back to entire json
        String section = json;
        int idx = json.indexOf("\"paramsA\"");
        if (idx < 0) idx = json.indexOf("\"params\"");
        if (idx >= 0) {
            int braceStart = json.indexOf('{', idx);
            if (braceStart >= 0) {
                int depth = 1, pos = braceStart + 1;
                while (pos < json.length() && depth > 0) {
                    char c = json.charAt(pos);
                    if (c == '{') depth++;
                    else if (c == '}') depth--;
                    pos++;
                }
                section = json.substring(braceStart, pos);
            }
        }

        // Extract "KEY": NUMBER pairs
        Matcher m = Pattern.compile("\"(\\w+)\"\\s*:\\s*(-?[\\d.]+(?:[eE][+-]?\\d+)?)").matcher(section);
        while (m.find()) {
            try {
                params.put(m.group(1), Double.parseDouble(m.group(2)));
            } catch (NumberFormatException ignored) {}
        }
        return params;
    }
}
