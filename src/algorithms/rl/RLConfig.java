package algorithms.rl;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RLConfig {

    public static final int    STALE_TTL = 631;
    public static final double TARGET_PROXIMITY_WEIGHT = 202.887309;
    public static final double TARGET_TYPE_BONUS = 78.882525;
    public static final double TARGET_LOWHP_CRITICAL_BONUS = 297.424640;
    public static final double TARGET_LOWHP_MODERATE_BONUS = 182.485306;
    public static final double TARGET_FOCUS_BONUS = 410.158612;
    public static final double TARGET_SAFEFIRE_BONUS = 236.114896;
    public static final double TARGET_STALE_PENALTY = 12.045753;
    public static final double PF_ENEMY_REPEL_STRENGTH = 2.853184;
    public static final double PF_ENEMY_ATTRACT_STRENGTH = 1.718614;
    public static final double PF_TANGENTIAL_STRENGTH = 0.316234;
    public static final double PF_ALLY_REPEL_RANGE = 92.793198;
    public static final double PF_ALLY_REPEL_STRENGTH = 1.225616;
    public static final double PF_WALL_STRENGTH = 0.496162;
    public static final double PF_WRECK_RANGE = 264.326646;
    public static final double HOLD_X_OFFSET = 1292.139388;
    public static final double KITE_MIN_AGGRO = 262.474868;
    public static final double KITE_MAX_AGGRO = 784.031435;
    public static final double KITE_MIN_NORMAL = 304.415413;
    public static final double KITE_MAX_NORMAL = 792.960464;
    public static final double KITE_MIN_DEFEN = 316.166344;
    public static final double KITE_MAX_DEFEN = 1017.547306;
    public static final double HP_RETREAT_MAIN = 51.057507;
    public static final int    NOFIRE_REPOSITION_TICKS = 18;
    public static final double HP_RETREAT_PCT_SEC = 0.240732;
    public static final double FLANK_Y_OFFSET = 378.676869;
    public static final double ADVANCE_X_A = 1992.683174;
    public static final double PATROL_EVASION_RANGE = 249.494684;

    public static final double MAP_WIDTH = 3000.000000;
    public static final double MAP_HEIGHT = 2000.000000;
    public static final double MAP_CX = 1500.000000;
    public static final double MAP_CY = 1000.000000;
    public static final double FORMATION_Y_BASE = 1000.000000;
    public static final double FORMATION_Y_OFFSET = 260.000000;
    public static final double WALL_MARGIN = 200.000000;
    public static final double SAFE_ZONE_X = 200.000000;
    public static final double FLANK_OFFSET = 150.000000;
    public static final double HEALTH_HIGH_THRESHOLD = 200.000000;
    public static final double HEALTH_LOW_THRESHOLD = 100.000000;
    public static final double PATROL_THRESHOLD = 200.000000;
    public static final double FIRING_ANGLE_TOLERANCE = 0.200000;
    public static final double FIRING_SAFETY_RADIUS = 55.000000;
    public static final double TANGENTIAL_SCALE_REF = 100.000000;
    public static final double TANGENTIAL_MIN_DIST = 80.000000;
    public static final double MAX_HEALTH_MAIN = 300.000000;
    public static final double MAX_HEALTH_SEC = 100.000000;

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
