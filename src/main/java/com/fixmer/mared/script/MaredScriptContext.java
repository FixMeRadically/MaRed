package com.fixmer.mared.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.commands.MaredScriptCommand;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class MaredScriptContext {

    public static final class Func {
        public final List<String> params;
        public final List<MaredScriptCommand> body;
        public Func(List<String> params, List<MaredScriptCommand> body) {
            this.params = params;
            this.body = body;
        }
    }

    private ServerPlayer initiator;
    private final MinecraftServer server;
    private final Map<String, Object> variables = new HashMap<>(64);
    private final Consumer<String> logger;

    private final Map<String, Func> functions = new HashMap<>();

    private boolean breakRequested = false;
    private boolean continueRequested = false;

    private boolean persistent = false;
    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean p) { this.persistent = p; }

    private int callDepth = 0;
    private static final int MAX_CALL_DEPTH = 256;

    private long lastRefreshTick = -1;

    public MaredScriptContext(ServerPlayer initiator, MinecraftServer server, Consumer<String> logger) {
        this.initiator = initiator;
        this.server = server;
        this.logger = logger;
    }

    public ServerPlayer getInitiator() { return initiator; }
    public void setInitiator(ServerPlayer player) { this.initiator = player; }
    public MinecraftServer getServer() { return server; }

    public void log(String line) { if (logger != null) logger.accept(line); }

    public void setVariable(String name, Object value) {
        if (name != null && name.startsWith("global.")) {
            MaredGlobalStorage.set(name, value);
        } else {
            variables.put(name, value);
        }
    }

    public Object getVariable(String name) {
        if (name == null) return null;

        Object v = variables.get(name);
        if (v != null) return v;
        if (variables.containsKey(name)) return null;

        if (name.startsWith("global.")) {
            return MaredGlobalStorage.get(name);
        }

        if ("self".equals(name)) {
            return initiator != null ? initiator.getName().getString() : "console";
        }
        if ("world".equals(name)) {
            if (initiator != null && initiator.level() != null) {
                return initiator.level().dimension().location().toString();
            }
            return "unknown";
        }

        return null;
    }

    public boolean hasVariable(String name) {
        if (name == null) return false;
        if (variables.containsKey(name)) return true;
        if (name.startsWith("global.")) return MaredGlobalStorage.has(name);
        if ("self".equals(name)) return true;
        if ("world".equals(name)) return true;
        return false;
    }

    public Map<String, Object> getAllVariables() { return variables; }

    public Object callFunction(String name, List<Object> args) {
        Func fn = functions.get(name);
        if (fn == null) {
            throw new RuntimeException("unknown function: " + name);
        }

        if (callDepth >= MAX_CALL_DEPTH) {
            throw new RuntimeException("call stack overflow (max " + MAX_CALL_DEPTH + ")");
        }

        Map<String, Object> backup = new HashMap<>();
        int pn = fn.params.size();
        for (int i = 0; i < pn; i++) {
            String p = fn.params.get(i);
            backup.put(p, variables.get(p));
        }

        for (int i = 0; i < pn; i++) {
            String p = fn.params.get(i);
            Object value = (i < args.size()) ? args.get(i) : null;
            variables.put(p, value);
        }

        callDepth++;
        Object result = null;
        try {
            MaredScriptExecutor exec = new MaredScriptExecutor(this, fn.body);

            MaredScriptExecutor.Frame top = exec.peekTopFrame();
            if (top != null) top.functionCall = true;

            int safety = 100_000;
            while (!exec.isFinished() && safety-- > 0) {
                exec.tick();
                if (exec.isWaiting()) {
                    break;
                }
            }

            if (exec.hasReturnValue()) {
                result = exec.getReturnValue();
            }
        } finally {
            callDepth--;
            for (Map.Entry<String, Object> e : backup.entrySet()) {
                variables.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    public void refreshPlayerData() {
        ServerPlayer player = initiator;
        if (player == null) {
            setVariable("mared_version", Mared.VERSION);
            setVariable("mc_version", "1.21.1");
            return;
        }

        long nowTick = MaredTicks.get();
        if (nowTick == lastRefreshTick && nowTick != 0) {
            return;
        }
        lastRefreshTick = nowTick;

        refreshPlayerDataImpl(player);
    }

    public void forceRefreshPlayerData() {
        lastRefreshTick = -1;
        refreshPlayerData();
    }

    private void refreshPlayerDataImpl(ServerPlayer player) {
        setVariable("player", player.getName().getString());
        setVariable("self", player.getName().getString());
        setVariable("uuid", player.getUUID().toString());

        setVariable("x", (long) Math.floor(player.getX()));
        setVariable("y", (long) Math.floor(player.getY()));
        setVariable("z", (long) Math.floor(player.getZ()));

        setVariable("x_exact", player.getX());
        setVariable("y_exact", player.getY());
        setVariable("z_exact", player.getZ());

        setVariable("yaw", (long) player.getYRot());
        setVariable("pitch", (long) player.getXRot());

        setVariable("hp", (long) player.getHealth());
        setVariable("max_hp", (long) player.getMaxHealth());
        setVariable("food", (long) player.getFoodData().getFoodLevel());
        setVariable("saturation", (double) player.getFoodData().getSaturationLevel());
        setVariable("air", (long) player.getAirSupply());

        setVariable("xp", (long) player.totalExperience);
        setVariable("xp_level", (long) player.experienceLevel);
        setVariable("xp_total", (long) player.totalExperience);

        String gamemode;
        try {
            gamemode = player.gameMode.getGameModeForPlayer().getName();
        } catch (Throwable t) {
            gamemode = "unknown";
        }
        setVariable("gamemode", gamemode);

        if (player.level() != null) {
            String dim = player.level().dimension().location().getPath();
            setVariable("dimension", dim);
            setVariable("dimension_full", player.level().dimension().location().toString());
        } else {
            setVariable("dimension", "unknown");
            setVariable("dimension_full", "unknown");
        }

        setVariable("is_sneaking", player.isShiftKeyDown());
        setVariable("is_sprinting", player.isSprinting());
        setVariable("is_on_ground", player.onGround());
        setVariable("is_in_water", player.isInWater());
        setVariable("is_in_lava", player.isInLava());
        setVariable("is_on_fire", player.isOnFire());
        setVariable("is_flying", player.getAbilities().flying);
        setVariable("is_alive", player.isAlive());
        setVariable("is_swimming", player.isSwimming());
        setVariable("is_using_item", player.isUsingItem());
        setVariable("is_blocking", player.isBlocking());

        try {
            var main = player.getMainHandItem();
            if (!main.isEmpty()) {
                setVariable("held_item", main.getItem().toString());
                setVariable("held_count", (long) main.getCount());
                setVariable("held_name", main.getHoverName().getString());
            } else {
                setVariable("held_item", "");
                setVariable("held_count", 0L);
                setVariable("held_name", "");
            }

            var off = player.getOffhandItem();
            if (!off.isEmpty()) {
                setVariable("offhand_item", off.getItem().toString());
                setVariable("offhand_count", (long) off.getCount());
            } else {
                setVariable("offhand_item", "");
                setVariable("offhand_count", 0L);
            }

            var inv = player.getInventory();
            setVariable("armor_helm", inv.getArmor(3).isEmpty() ? "" : inv.getArmor(3).getItem().toString());
            setVariable("armor_chest", inv.getArmor(2).isEmpty() ? "" : inv.getArmor(2).getItem().toString());
            setVariable("armor_legs", inv.getArmor(1).isEmpty() ? "" : inv.getArmor(1).getItem().toString());
            setVariable("armor_boots", inv.getArmor(0).isEmpty() ? "" : inv.getArmor(0).getItem().toString());
        } catch (Throwable t) {
            setVariable("held_item", "");
            setVariable("held_count", 0L);
            setVariable("held_name", "");
            setVariable("offhand_item", "");
            setVariable("offhand_count", 0L);
            setVariable("armor_helm", "");
            setVariable("armor_chest", "");
            setVariable("armor_legs", "");
            setVariable("armor_boots", "");
        }

        try {
            var effects = new java.util.ArrayList<String>();
            for (var effect : player.getActiveEffects()) {
                effects.add(effect.getEffect().getKey().location().getPath());
            }
            setVariable("effects", effects);
            setVariable("effect_count", (long) effects.size());
        } catch (Throwable t) {
            setVariable("effects", new java.util.ArrayList<String>());
            setVariable("effect_count", 0L);
        }

        try {
            var team = player.getTeam();
            setVariable("team", team != null ? team.getName() : "");
        } catch (Throwable t) {
            setVariable("team", "");
        }

        if (player.level() != null) {
            long dayTime = player.level().getDayTime();
            long timeOfDay = dayTime % 24000L;
            setVariable("time", timeOfDay);
            setVariable("day_count", dayTime / 24000L);
            setVariable("is_day", timeOfDay >= 0 && timeOfDay < 12000);
            setVariable("is_night", timeOfDay >= 12000);
            setVariable("is_raining", player.level().isRaining());
            setVariable("is_thundering", player.level().isThundering());

            String weather;
            if (player.level().isThundering()) weather = "thunder";
            else if (player.level().isRaining()) weather = "rain";
            else weather = "clear";
            setVariable("weather", weather);

            setVariable("moon_phase", (long) (dayTime / 24000L % 8));

            try {
                if (player.level() instanceof ServerLevel sl) {
                    setVariable("seed", sl.getSeed());
                } else {
                    setVariable("seed", 0L);
                }
            } catch (Throwable t) {
                setVariable("seed", 0L);
            }

            setVariable("world", player.level().dimension().location().toString());
            setVariable("world_name", player.level().dimension().location().getPath());
        } else {
            setVariable("time", 0L);
            setVariable("day_count", 0L);
            setVariable("is_day", true);
            setVariable("is_night", false);
            setVariable("is_raining", false);
            setVariable("is_thundering", false);
            setVariable("weather", "clear");
            setVariable("moon_phase", 0L);
            setVariable("seed", 0L);
            setVariable("world", "unknown");
            setVariable("world_name", "unknown");
        }

        if (server != null) {
            try {
                setVariable("player_count", (long) server.getPlayerCount());
                setVariable("difficulty", server.getWorldData().getDifficulty().getKey());
                setVariable("is_hardcore", server.getWorldData().isHardcore());
            } catch (Throwable t) {
                setVariable("player_count", 1L);
                setVariable("difficulty", "normal");
                setVariable("is_hardcore", false);
            }
        } else {
            setVariable("player_count", 1L);
            setVariable("difficulty", "normal");
            setVariable("is_hardcore", false);
        }

        setVariable("tick", MaredTicks.get());
        setVariable("now_ms", System.currentTimeMillis());

        setVariable("mared_version", Mared.VERSION);
        setVariable("mc_version", "1.21.1");
    }

    public void refreshEventData(Map<String, Object> data) {
        if (data == null) return;
        for (Map.Entry<String, Object> kv : data.entrySet()) {
            setVariable(kv.getKey(), kv.getValue());
        }
    }

    // ============================================================
    //  substitute — с BUG-16 FIX
    // ============================================================

    public String substitute(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder result = new StringBuilder(input.length() + 16);
        int i = 0;
        int n = input.length();

        while (i < n) {
            char c = input.charAt(i);

            // BUG-16 FIX: \$ → $ (escape, не подставлять)
            if (c == '\\' && i + 1 < n && input.charAt(i + 1) == '$') {
                result.append('$');
                i += 2;
                continue;
            }

            // ${expr}
            if (c == '$' && i + 1 < n && input.charAt(i + 1) == '{') {
                int start = i + 2;
                int end = findMatchingBrace(input, start, n);
                if (end < 0) {
                    result.append("<UNCLOSED:").append(input, i, n).append(">");
                    break;
                }
                String expr = input.substring(start, end);
                String value;
                try {
                    String evalExpr = expr;
                    if (!expr.isEmpty()) {
                        char first = expr.charAt(0);
                        if ((first >= 'a' && first <= 'z')
                            || (first >= 'A' && first <= 'Z')
                            || first == '_') {
                            evalExpr = "$" + expr;
                        }
                    }
                    Object v = MaredExpr.eval(evalExpr, this);
                    value = MaredExpr.stringify(v);
                } catch (Exception e) {
                    value = "<ERR:" + e.getMessage() + ">";
                }
                result.append(value);
                i = end + 1;
                continue;
            }

            // $var или $var.method(...)
            if (c == '$') {
                int start = i + 1;
                int end = start;
                while (end < n && isVarChar(input.charAt(end))) end++;
                if (end > start) {
                    String varName = input.substring(start, end);

                    if (end < n && input.charAt(end) == '(') {
                        int close = findMatchingParen(input, end, n);
                        if (close > 0) {
                            int exprEnd = close + 1;
                            String fullExpr = input.substring(i + 1, exprEnd);
                            try {
                                Object v = MaredExpr.eval("$" + fullExpr, this);
                                result.append(MaredExpr.stringify(v));
                                i = exprEnd;
                                continue;
                            } catch (Exception ignored) {}
                        }
                    }

                    int exprEnd = findExpressionEnd(input, end, n);
                    if (exprEnd > end) {
                        String fullExpr = input.substring(i + 1, exprEnd);
                        try {
                            Object v = MaredExpr.eval("$" + fullExpr, this);
                            result.append(MaredExpr.stringify(v));
                            i = exprEnd;
                            continue;
                        } catch (Exception ignored) {}
                    }

                    if (hasVariable(varName)) {
                        Object value = getVariable(varName);
                        result.append(MaredExpr.stringify(value));
                        i = end;
                        continue;
                    }

                    result.append('$').append(varName);
                    i = end;
                    continue;
                }
            }

            result.append(c);
            i++;
        }
        return unescape(result.toString());
    }

    private int findExpressionEnd(String s, int start, int n) {
        int i = start;

        if (i < n && s.charAt(i) == '(') {
            int close = findMatchingParen(s, i, n);
            if (close > 0) return close + 1;
            return i;
        }

        while (i < n) {
            char c = s.charAt(i);

            if (c == '.') {
                int j = i + 1;
                while (j < n && isIdentChar(s.charAt(j))) j++;
                if (j == i + 1) break;
                if (j < n && s.charAt(j) == '(') {
                    int close = findMatchingParen(s, j, n);
                    if (close < 0) break;
                    i = close + 1;
                    continue;
                }
                i = j;
                continue;
            }

            if (c == '[') {
                int close = findMatchingBracket(s, i, n);
                if (close < 0) break;
                i = close + 1;
                continue;
            }

            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
                int j = i + 1;
                while (j < n && s.charAt(j) == ' ') j++;
                if (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '$'
                        || s.charAt(j) == '"' || s.charAt(j) == '(' || s.charAt(j) == '-')) {
                    i = j;
                    continue;
                }
                break;
            }

            if (c == '=' && i + 1 < n && s.charAt(i + 1) == '=') { i += 2; continue; }
            if (c == '!' && i + 1 < n && s.charAt(i + 1) == '=') { i += 2; continue; }
            if (c == '<' || c == '>') {
                if (i + 1 < n && s.charAt(i + 1) == '=') i++;
                i++;
                continue;
            }
            if (c == '&' && i + 1 < n && s.charAt(i + 1) == '&') { i += 2; continue; }
            if (c == '|' && i + 1 < n && s.charAt(i + 1) == '|') { i += 2; continue; }

            if (c == ' ') {
                int j = i + 1;
                while (j < n && s.charAt(j) == ' ') j++;
                if (j < n) {
                    char nc = s.charAt(j);
                    if (nc == '+' || nc == '-' || nc == '*' || nc == '/' || nc == '%'
                        || nc == '=' || nc == '!' || nc == '<' || nc == '>'
                        || nc == '&' || nc == '|') {
                        i = j;
                        continue;
                    }
                }
                break;
            }

            break;
        }

        return i;
    }

    private int findMatchingBrace(String s, int start, int n) {
        int depth = 1;
        boolean inStr = false;
        char q = 0;
        for (int i = start; i < n; i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < n) { i++; continue; }
                if (c == q) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') { inStr = true; q = c; continue; }
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private int findMatchingParen(String s, int openIdx, int n) {
        int depth = 1;
        boolean inStr = false;
        char q = 0;
        for (int i = openIdx + 1; i < n; i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < n) { i++; continue; }
                if (c == q) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') { inStr = true; q = c; continue; }
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private int findMatchingBracket(String s, int openIdx, int n) {
        int depth = 1;
        boolean inStr = false;
        char q = 0;
        for (int i = openIdx + 1; i < n; i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < n) { i++; continue; }
                if (c == q) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') { inStr = true; q = c; continue; }
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static boolean isVarChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isIdentChar(char c) {
        return isVarChar(c);
    }

    private static String unescape(String s) {
        if (s == null) return null;
        if (s.indexOf('\\') < 0) return s;

        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n' -> { out.append('\n'); i++; }
                    case 't' -> { out.append('\t'); i++; }
                    case 'r' -> { out.append('\r'); i++; }
                    case '\\' -> { out.append('\\'); i++; }
                    case '"' -> { out.append('"'); i++; }
                    case '\'' -> { out.append('\''); i++; }
                    default -> out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public void requestBreak() { breakRequested = true; }
    public void requestContinue() { continueRequested = true; }
    public boolean isBreakRequested() { return breakRequested; }
    public boolean isContinueRequested() { return continueRequested; }
    public void clearBreakContinue() { breakRequested = false; continueRequested = false; }

    public void registerFunction(String name, List<String> params, List<MaredScriptCommand> body) {
        functions.put(name, new Func(params, body));
    }

    public boolean hasFunction(String name) { return functions.containsKey(name); }
    public Func getFunction(String name) { return functions.get(name); }
}