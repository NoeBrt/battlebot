package algorithms.tbot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TBotConfig -- 72 tunable parameters for the TacticalBot system.
 *
 * Fields are NOT final so javac will not inline them.
 * Override at runtime: java -Dtbot.config=path/to/config.json
 */
public class TBotConfig {

    // ── Target Selection (10) ─────────────────────────────────────────────
    public static int    STALE_TTL                = 70;
    public static double TGT_PROXIMITY_WEIGHT     = 800.0;
    public static double TGT_TYPE_BONUS_SEC       = 200.0;
    public static double TGT_LOWHP_CRIT           = 350.0;
    public static double TGT_LOWHP_MOD            = 180.0;
    public static double TGT_FOCUS_BONUS          = 400.0;
    public static double TGT_SAFEFIRE             = 120.0;
    public static double TGT_STALE_PENALTY        = 5.0;
    public static double TGT_DMG_COMMITTED        = 200.0;
    public static double TGT_LAST_HIT_BONUS       = 150.0;

    // ── Main Bot Utility Weights (16) ─────────────────────────────────────
    public static double U_ADVANCE_BASE           = 0.4;
    public static double U_ADVANCE_NO_ENEMY       = 0.6;
    public static double U_COMBAT_HAS_TARGET      = 0.8;
    public static double U_COMBAT_SAFEFIRE        = 0.3;
    public static double U_FLANK_BLOCKED_TICKS    = 0.5;
    public static int    U_FLANK_BLOCKED_THRESH   = 20;
    public static double U_RETREAT_HP_FACTOR      = 0.7;
    public static double U_RETREAT_HP_THRESH      = 80.0;
    public static double U_RETREAT_ALONE          = 0.4;
    public static double U_HOLD_AT_POS            = 0.3;
    public static double U_REGROUP_SPREAD         = 0.3;
    public static double U_REGROUP_DIST_THRESH    = 500.0;
    public static double U_ANCHOR_BONUS           = 0.2;
    public static double U_FLANKER_BONUS          = 0.2;
    public static int    U_AGGRO_ALIVE_THRESH     = 3;
    public static double U_AGGRO_HP_THRESH        = 200.0;

    // ── Secondary Bot Utility Weights (8) ─────────────────────────────────
    public static double US_SCOUT_BASE            = 0.5;
    public static double US_ASSASSIN_BASE         = 0.6;
    public static double US_ASSASSIN_HP_THRESH    = 60.0;
    public static double US_ESCORT_BASE           = 0.4;
    public static double US_RETREAT_HP_PCT        = 0.35;
    public static double US_EVADE_RANGE           = 250.0;
    public static double US_DEEP_SCOUT_X          = 1600.0;
    public static double US_FLANK_Y               = 300.0;

    // ── Potential Field (14) ──────────────────────────────────────────────
    public static double PF_ENEMY_REPEL           = 2.5;
    public static double PF_ENEMY_ATTRACT         = 0.6;
    public static double PF_TANGENTIAL            = 0.7;
    public static double PF_ALLY_REPEL_RANGE      = 200.0;
    public static double PF_ALLY_REPEL_STR        = 0.9;
    public static double PF_WALL_STR              = 0.6;
    public static double PF_WRECK_RANGE           = 150.0;
    public static double PF_WRECK_STR             = 0.5;
    public static double PF_FOCUS_PULL            = 0.3;
    public static double PF_FORMATION_PULL        = 0.2;
    public static double PF_FLANKER_PERP          = 0.4;
    public static double PF_ANCHOR_CENTER         = 0.3;
    public static double PF_SEC_EVADE             = 1.5;
    public static double PF_SEC_TANGENTIAL        = 1.0;

    // ── Kiting Ranges (6) ─────────────────────────────────────────────────
    public static double KITE_MIN_AGGRO           = 300.0;
    public static double KITE_MAX_AGGRO           = 700.0;
    public static double KITE_MIN_NORMAL          = 360.0;
    public static double KITE_MAX_NORMAL          = 780.0;
    public static double KITE_MIN_DEFEN           = 420.0;
    public static double KITE_MAX_DEFEN           = 900.0;

    // ── Formation & Positioning (10) ──────────────────────────────────────
    public static double FORM_HOLD_X_OFFSET       = 1100.0;
    public static double FORM_Y_SPREAD            = 260.0;
    public static double FORM_ANCHOR_X_ADJ        = 0.0;
    public static double FORM_FLANKER_Y_ADJ       = 100.0;
    public static double FORM_RETREAT_X           = 300.0;
    public static double FORM_REGROUP_X           = 600.0;
    public static double FORM_SEC_ESCORT_OFFSET   = 150.0;
    public static double FORM_SEC_FLANK_Y_OFFSET  = 400.0;
    public static double FORM_ADAPT_DEAD_MAIN     = 0.3;
    public static double FORM_ADAPT_DEAD_SEC      = 0.15;

    // ── Firing (4) ────────────────────────────────────────────────────────
    public static int    FIRE_NOFIRE_THRESH       = 20;
    public static int    FIRE_ANGLE_OFFSETS       = 7;
    public static double FIRE_SEC_ENGAGE_RANGE    = 600.0;
    public static double FIRE_LEAD_ACCEL_FACTOR   = 0.25;

    // ── Coordination (4) ──────────────────────────────────────────────────
    public static double COORD_FOCUS_RADIUS       = 120.0;
    public static int    COORD_ROLE_SWAP_CD       = 100;
    public static double COORD_THREAT_RADIUS      = 400.0;
    public static double COORD_REGROUP_HP_THRESH  = 150.0;

    // ── Fixed Constants (never tuned) ─────────────────────────────────────
    public static final double MAP_WIDTH  = 3000.0;
    public static final double MAP_HEIGHT = 2000.0;
    public static final double MAP_CX     = 1500.0;
    public static final double MAP_CY     = 1000.0;
    public static final double BOT_R      = 50.0;
    public static final double WALL_MARGIN = 200.0;

    // ── Runtime config loading ────────────────────────────────────────────
    static {
        String configPath = System.getProperty("tbot.config");
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
                    Field f = TBotConfig.class.getDeclaredField(entry.getKey());
                    if (f.getType() == int.class) {
                        f.setInt(null, (int) Math.round(entry.getValue()));
                        loaded++;
                    } else if (f.getType() == double.class) {
                        f.setDouble(null, entry.getValue());
                        loaded++;
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (IllegalAccessException e) {
                    System.err.println("[TBotConfig] Cannot set " + entry.getKey() + ": " + e.getMessage());
                }
            }
            System.out.println("[TBotConfig] Loaded " + loaded + " params from " + path);
        } catch (Exception e) {
            System.err.println("[TBotConfig] Failed to load " + path + ": " + e.getMessage());
        }
    }

    private static Map<String, Double> parseJsonParams(String json) {
        Map<String, Double> params = new HashMap<>();
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
        Matcher m = Pattern.compile("\"(\\w+)\"\\s*:\\s*(-?[\\d.]+(?:[eE][+-]?\\d+)?)").matcher(section);
        while (m.find()) {
            try {
                params.put(m.group(1), Double.parseDouble(m.group(2)));
            } catch (NumberFormatException ignored) {}
        }
        return params;
    }
}
