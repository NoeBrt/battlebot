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
    // Best config from tbot_run_20260322_150209 (fitness=-0.1273, gen=1)
    public static int    STALE_TTL                = 71;
    public static double TGT_PROXIMITY_WEIGHT     = 311.179284;
    public static double TGT_TYPE_BONUS_SEC       = 148.727106;
    public static double TGT_LOWHP_CRIT           = 423.127323;
    public static double TGT_LOWHP_MOD            = 276.197335;
    public static double TGT_FOCUS_BONUS          = 548.428940;
    public static double TGT_SAFEFIRE             = 138.669379;
    public static double TGT_STALE_PENALTY        = 9.678676;
    public static double TGT_DMG_COMMITTED        = 295.943712;
    public static double TGT_LAST_HIT_BONUS       = 53.767984;

    // ── Main Bot Utility Weights (16) ─────────────────────────────────────
    public static double U_ADVANCE_BASE           = 0.112894;
    public static double U_ADVANCE_NO_ENEMY       = 0.242796;
    public static double U_COMBAT_HAS_TARGET      = 0.600000;
    public static double U_COMBAT_SAFEFIRE        = 0.124861;
    public static double U_FLANK_BLOCKED_TICKS    = 0.513332;
    public static int    U_FLANK_BLOCKED_THRESH   = 24;
    public static double U_RETREAT_HP_FACTOR      = 0.444932;
    public static double U_RETREAT_HP_THRESH      = 33.019187;
    public static double U_RETREAT_ALONE          = 0.613157;
    public static double U_HOLD_AT_POS            = 0.604988;
    public static double U_REGROUP_SPREAD         = 0.299712;
    public static double U_REGROUP_DIST_THRESH    = 391.862920;
    public static double U_ANCHOR_BONUS           = 0.334443;
    public static double U_FLANKER_BONUS          = 0.187569;
    public static int    U_AGGRO_ALIVE_THRESH     = 3;
    public static double U_AGGRO_HP_THRESH        = 211.712459;

    // ── Secondary Bot Utility Weights (8) ─────────────────────────────────
    public static double US_SCOUT_BASE            = 0.776522;
    public static double US_ASSASSIN_BASE         = 0.453281;
    public static double US_ASSASSIN_HP_THRESH    = 64.471850;
    public static double US_ESCORT_BASE           = 0.034691;
    public static double US_RETREAT_HP_PCT        = 0.372410;
    public static double US_EVADE_RANGE           = 244.141231;
    public static double US_DEEP_SCOUT_X          = 1346.480002;
    public static double US_FLANK_Y               = 330.256068;

    // ── Potential Field (14) ──────────────────────────────────────────────
    public static double PF_ENEMY_REPEL           = 0.842841;
    public static double PF_ENEMY_ATTRACT         = 0.134777;
    public static double PF_TANGENTIAL            = 0.349332;
    public static double PF_ALLY_REPEL_RANGE      = 202.311906;
    public static double PF_ALLY_REPEL_STR        = 0.815259;
    public static double PF_WALL_STR              = 0.479950;
    public static double PF_WRECK_RANGE           = 242.169229;
    public static double PF_WRECK_STR             = 1.556195;
    public static double PF_FOCUS_PULL            = 0.054046;
    public static double PF_FORMATION_PULL        = 0.069578;
    public static double PF_FLANKER_PERP          = 0.262955;
    public static double PF_ANCHOR_CENTER         = 0.918105;
    public static double PF_SEC_EVADE             = 1.676121;
    public static double PF_SEC_TANGENTIAL        = 1.222049;

    // ── Kiting Ranges (6) ─────────────────────────────────────────────────
    public static double KITE_MIN_AGGRO           = 233.236862;
    public static double KITE_MAX_AGGRO           = 889.467859;
    public static double KITE_MIN_NORMAL          = 411.896991;
    public static double KITE_MAX_NORMAL          = 919.084019;
    public static double KITE_MIN_DEFEN           = 458.354901;
    public static double KITE_MAX_DEFEN           = 612.841193;

    // ── Formation & Positioning (10) ──────────────────────────────────────
    public static double FORM_HOLD_X_OFFSET       = 1306.162627;
    public static double FORM_Y_SPREAD            = 106.792828;
    public static double FORM_ANCHOR_X_ADJ        = -166.546129;
    public static double FORM_FLANKER_Y_ADJ       = 87.955665;
    public static double FORM_RETREAT_X           = 355.316465;
    public static double FORM_REGROUP_X           = 520.528087;
    public static double FORM_SEC_ESCORT_OFFSET   = 309.710101;
    public static double FORM_SEC_FLANK_Y_OFFSET  = 438.985152;
    public static double FORM_ADAPT_DEAD_MAIN     = 0.349451;
    public static double FORM_ADAPT_DEAD_SEC      = 0.123867;

    // ── Firing (4) ────────────────────────────────────────────────────────
    public static int    FIRE_NOFIRE_THRESH       = 12;
    public static int    FIRE_ANGLE_OFFSETS       = 5;
    public static double FIRE_SEC_ENGAGE_RANGE    = 606.761933;
    public static double FIRE_LEAD_ACCEL_FACTOR   = 0.292119;

    // ── Coordination (4) ──────────────────────────────────────────────────
    public static double COORD_FOCUS_RADIUS       = 94.050789;
    public static int    COORD_ROLE_SWAP_CD       = 55;
    public static double COORD_THREAT_RADIUS      = 767.744354;
    public static double COORD_REGROUP_HP_THRESH  = 176.776412;

    // ── Fixed Constants (never tuned) ─────────────────────────────────────
    public static final double MAP_WIDTH  = 3000.0;
    public static final double MAP_HEIGHT = 2000.0;
    public static final double MAP_CX     = 1500.0;
    public static final double MAP_CY     = 1000.0;
    public static final double BOT_R      = 50.0;
    public static final double WALL_MARGIN = 200.0;

    // ── Runtime config loading ────────────────────────────────────────────
    static {
        String configPath = System.getProperty("TBotConfig.configPath");
        if (configPath == null) configPath = System.getProperty("tbot.config");
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
