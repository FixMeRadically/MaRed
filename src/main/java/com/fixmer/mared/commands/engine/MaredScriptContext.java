package com.fixmer.mared.commands.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredTicks;
import com.fixmer.mared.commands.expr.MaredExpr;
import com.fixmer.mared.commands.storage.MaredGlobalStorage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class MaredScriptContext implements com.fixmer.genesis.technology.expression.ExpressionEnvironment {

    public static final class Func {
        public final List<String> params;
        public final List<MaredScriptCommand> body;
        public Func(List<String> params, List<MaredScriptCommand> body) {
            this.params = List.copyOf(params);
            this.body = List.copyOf(body);
        }
    }

    private static final int MAX_CALL_DEPTH = 64;

    private ServerPlayer initiator;
    private final MinecraftServer server;
    private final Map<String, Object> variables;
    private final Consumer<String> logger;

    private final Map<String, Func> functions = new HashMap<>(8);

    private com.fixmer.mared.commands.events.MaredEventRegistry.Entry eventEntry;
    public com.fixmer.mared.commands.events.MaredEventRegistry.Entry eventEntry() { return eventEntry; }
    public void setEventEntry(com.fixmer.mared.commands.events.MaredEventRegistry.Entry entry) { eventEntry = entry; }
    private com.fixmer.genesis.technology.runtime.ExecutionScope executionScope;
    public com.fixmer.genesis.technology.runtime.ExecutionScope executionScope() { return executionScope; }
    public void setExecutionScope(com.fixmer.genesis.technology.runtime.ExecutionScope scope) {
        if (executionScope != null && executionScope != scope) throw new IllegalStateException("Context owner cannot be replaced");
        executionScope = scope;
    }

    private com.fixmer.genesis.technology.links.InvocationCapabilities<MaredScriptContext> capabilityHost;
    private com.fixmer.genesis.technology.runtime.ExecutionLimits invocationLimits=com.fixmer.genesis.technology.runtime.ExecutionLimits.DEFAULT;
    public void setCapabilityHost(com.fixmer.genesis.technology.links.InvocationCapabilities<MaredScriptContext> host){capabilityHost=host;}
    public void setInvocationLimits(com.fixmer.genesis.technology.runtime.ExecutionLimits limits){invocationLimits=java.util.Objects.requireNonNull(limits);}
    private boolean persistent = false;
    private int callDepth = 0;
    private long lastRefreshTick = -1;

    public MaredScriptContext(ServerPlayer initiator, MinecraftServer server, Consumer<String> logger) {
        this.initiator = initiator;
        this.server = server;
        this.logger = logger;
        this.variables = new HashMap<>(64);
    }

    // ============================================================
    //  Fork — копия для одного вызова
    // ============================================================

    /**
     * Копия контекста с тем же initiator/server/logger.
     *
     * Используется в BindRegistry / EventRegistry, чтобы каждый вызов
     * обработчика имел собственные переменные — иначе параллельные
     * вызовы могут перезаписать друг другу $v, $i, и т.п.
     *
     * Копируются ссылки на неизменяемые определения функций.
     */
    public MaredScriptContext fork() {
        MaredScriptContext copy = new MaredScriptContext(initiator, server, logger);
        copy.functions.putAll(this.functions);
        copy.persistent = this.persistent;
        copy.executionScope = executionScope;
        copy.eventEntry = eventEntry;
        copy.capabilityHost = capabilityHost;copy.invocationLimits=invocationLimits;
        return copy;
    }

    /** Fork с новым initiator (для событий с известным игроком). */
    public MaredScriptContext forkWith(ServerPlayer newInitiator) {
        MaredScriptContext copy = new MaredScriptContext(newInitiator, server, logger);
        copy.functions.putAll(this.functions);
        copy.persistent = this.persistent;
        copy.executionScope = executionScope;
        copy.eventEntry = eventEntry;
        copy.capabilityHost = capabilityHost;copy.invocationLimits=invocationLimits;
        return copy;
    }

    /** Bind an event invocation to the server that actually fired it. */
    public MaredScriptContext forkFor(ServerPlayer player, MinecraftServer targetServer) {
        MaredScriptContext copy = new MaredScriptContext(player, targetServer, logger);
        copy.functions.putAll(functions);
        copy.persistent = persistent;
        copy.executionScope = executionScope;
        copy.eventEntry = eventEntry;
        copy.capabilityHost = capabilityHost;copy.invocationLimits=invocationLimits;
        return copy;
    }

    // ============================================================
    //  Getters / Setters
    // ============================================================

    public ServerPlayer getInitiator() { return initiator; }
    public void setInitiator(ServerPlayer player) { this.initiator = player; }
    public MinecraftServer getServer() { return server; }

    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean p) { this.persistent = p; }

    public void log(String line) { if (logger != null) logger.accept(line); }

    // ============================================================
    //  Переменные
    // ============================================================

    public void setVariable(String name, Object value) {
        if (name == null) return;
        if (name.startsWith("global.")) {
            MaredGlobalStorage.set(name, value);
        } else {
            variables.put(name, value);
        }
    }

    public Object getVariable(String name) {
        if (name == null) return null;

        if (variables.containsKey(name)) return variables.get(name);

        if (name.startsWith("global.")) return MaredGlobalStorage.get(name);

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
        if ("self".equals(name) || "world".equals(name)) return true;
        return false;
    }

    public void removeVariable(String name) {
        if (name != null && name.startsWith("global.")) MaredGlobalStorage.remove(name);
        else variables.remove(name);
    }

    public Map<String, Object> getAllVariables() { return variables; }

    // ============================================================
    //  Функции
    // ============================================================

    public void registerFunction(String name, List<String> params, List<MaredScriptCommand> body) {
        if (new java.util.HashSet<>(params).size() != params.size()) throw new IllegalArgumentException("Duplicate parameters");
        for (String p : params) if (!p.matches("[a-zA-Z_][a-zA-Z0-9_]*") || p.equals("self") || p.equals("world"))
            throw new IllegalArgumentException("Invalid parameter: " + p);
        functions.put(name, new Func(List.copyOf(params), List.copyOf(body)));
    }

    @Override public Object callBuiltin(String name, List<Object> args) {
        if(capabilityHost!=null&&capabilityHost.supports(name))return capabilityHost.call(name,args,this);
        return com.fixmer.mared.commands.expr.MaredBuiltins.call(name, args, this);
    }
    @Override public Object callMethod(Object receiver, String name, List<Object> args) {
        return com.fixmer.mared.commands.expr.MaredMethods.call(receiver, name, args);
    }
    public boolean hasFunction(String name) { return functions.containsKey(name); }
    public Func getFunction(String name) { return functions.get(name); }

    public Object callFunction(String name, List<Object> args) {
        Func fn = functions.get(name);
        if (fn == null) throw new RuntimeException("unknown function: " + name);

        if (callDepth >= MAX_CALL_DEPTH) {
            throw new RuntimeException("call stack overflow (max " + MAX_CALL_DEPTH + ")");
        }

        int pn = fn.params.size();
        Map<String, Object> backup = new HashMap<>(pn * 2);
        java.util.Set<String> present = new java.util.HashSet<>(variables.keySet());
        for (int i = 0; i < pn; i++) {
            String p = fn.params.get(i);
            backup.put(p, variables.get(p));
        }
        for (int i = 0; i < pn; i++) {
            String p = fn.params.get(i);
            variables.put(p, i < args.size() ? args.get(i) : null);
        }

        callDepth++;
        Object result = null;
        MaredScriptExecutor exec = new MaredScriptExecutor(this, fn.body, invocationLimits);
        try {
            MaredScriptExecutor.Frame top = exec.peekTopFrame();
            if (top != null) top.functionCall = true;

            exec.tick();
            if (exec.failure() != null) throw new IllegalStateException("Function failed: " + name, exec.failure());
            if (!exec.isFinished()) throw new IllegalStateException("Expression function cannot wait or exceed a tick budget: " + name);
            if (exec.hasReturnValue()) result = exec.getReturnValue();
        } finally {
            exec.stopAll();
            callDepth--;
            for (Map.Entry<String, Object> e : backup.entrySet()) {
                if (present.contains(e.getKey())) variables.put(e.getKey(), e.getValue());
                else variables.remove(e.getKey());
            }
        }
        return result;
    }

    // ============================================================
    //  Refresh player data
    // ============================================================

    public void refreshPlayerData() {
        ServerPlayer player = initiator;
        if (player == null) {
            setVariable("mared_version", Mared.VERSION);
            setVariable("mc_version", "1.21.1");
            return;
        }

        long nowTick = MaredTicks.get();
        if (nowTick == lastRefreshTick && nowTick != 0) return;
        lastRefreshTick = nowTick;

        refreshPlayerDataImpl(player);
    }

    public void forceRefreshPlayerData() {
        lastRefreshTick = -1;
        refreshPlayerData();
    }

    private void refreshPlayerDataImpl(ServerPlayer player) {
        variables.put("player", player.getName().getString());
        variables.put("self", player.getName().getString());
        variables.put("uuid", player.getUUID().toString());

        variables.put("x", (long) Math.floor(player.getX()));
        variables.put("y", (long) Math.floor(player.getY()));
        variables.put("z", (long) Math.floor(player.getZ()));

        variables.put("x_exact", player.getX());
        variables.put("y_exact", player.getY());
        variables.put("z_exact", player.getZ());

        variables.put("yaw", (long) player.getYRot());
        variables.put("pitch", (long) player.getXRot());

        variables.put("hp", (long) player.getHealth());
        variables.put("max_hp", (long) player.getMaxHealth());
        variables.put("food", (long) player.getFoodData().getFoodLevel());
        variables.put("saturation", (double) player.getFoodData().getSaturationLevel());
        variables.put("air", (long) player.getAirSupply());

        variables.put("xp", (long) player.totalExperience);
        variables.put("xp_level", (long) player.experienceLevel);
        variables.put("xp_total", (long) player.totalExperience);

        String gamemode;
        try {
            gamemode = player.gameMode.getGameModeForPlayer().getName();
        } catch (Throwable t) {
            gamemode = "unknown";
        }
        variables.put("gamemode", gamemode);

        if (player.level() != null) {
            var loc = player.level().dimension().location();
            variables.put("dimension", loc.getPath());
            variables.put("dimension_full", loc.toString());
        } else {
            variables.put("dimension", "unknown");
            variables.put("dimension_full", "unknown");
        }

        variables.put("is_sneaking", player.isShiftKeyDown());
        variables.put("is_sprinting", player.isSprinting());
        variables.put("is_on_ground", player.onGround());
        variables.put("is_in_water", player.isInWater());
        variables.put("is_in_lava", player.isInLava());
        variables.put("is_on_fire", player.isOnFire());
        variables.put("is_flying", player.getAbilities().flying);
        variables.put("is_alive", player.isAlive());
        variables.put("is_swimming", player.isSwimming());
        variables.put("is_using_item", player.isUsingItem());
        variables.put("is_blocking", player.isBlocking());

        refreshInventory(player);
        refreshEffects(player);
        refreshWorld(player);

        if (server != null) {
            try {
                variables.put("player_count", (long) server.getPlayerCount());
                variables.put("difficulty", server.getWorldData().getDifficulty().getKey());
                variables.put("is_hardcore", server.getWorldData().isHardcore());
            } catch (Throwable t) {
                variables.put("player_count", 1L);
                variables.put("difficulty", "normal");
                variables.put("is_hardcore", false);
            }
        } else {
            variables.put("player_count", 1L);
            variables.put("difficulty", "normal");
            variables.put("is_hardcore", false);
        }

        variables.put("tick", MaredTicks.get());
        variables.put("now_ms", System.currentTimeMillis());
        variables.put("mared_version", Mared.VERSION);
        variables.put("mc_version", "1.21.1");
    }

    private void refreshInventory(ServerPlayer player) {
        try {
            var main = player.getMainHandItem();
            if (!main.isEmpty()) {
                variables.put("held_item", main.getItem().toString());
                variables.put("held_count", (long) main.getCount());
                variables.put("held_name", main.getHoverName().getString());
            } else {
                variables.put("held_item", "");
                variables.put("held_count", 0L);
                variables.put("held_name", "");
            }

            var off = player.getOffhandItem();
            if (!off.isEmpty()) {
                variables.put("offhand_item", off.getItem().toString());
                variables.put("offhand_count", (long) off.getCount());
            } else {
                variables.put("offhand_item", "");
                variables.put("offhand_count", 0L);
            }

            var inv = player.getInventory();
            variables.put("armor_helm",  inv.getArmor(3).isEmpty() ? "" : inv.getArmor(3).getItem().toString());
            variables.put("armor_chest", inv.getArmor(2).isEmpty() ? "" : inv.getArmor(2).getItem().toString());
            variables.put("armor_legs",  inv.getArmor(1).isEmpty() ? "" : inv.getArmor(1).getItem().toString());
            variables.put("armor_boots", inv.getArmor(0).isEmpty() ? "" : inv.getArmor(0).getItem().toString());
        } catch (Throwable t) {
            variables.put("held_item", "");
            variables.put("held_count", 0L);
            variables.put("held_name", "");
            variables.put("offhand_item", "");
            variables.put("offhand_count", 0L);
            variables.put("armor_helm", "");
            variables.put("armor_chest", "");
            variables.put("armor_legs", "");
            variables.put("armor_boots", "");
        }
    }

    private void refreshEffects(ServerPlayer player) {
        try {
            List<String> effects = new ArrayList<>(4);
            for (var effect : player.getActiveEffects()) {
                effects.add(effect.getEffect().getKey().location().getPath());
            }
            variables.put("effects", effects);
            variables.put("effect_count", (long) effects.size());
        } catch (Throwable t) {
            variables.put("effects", new ArrayList<String>());
            variables.put("effect_count", 0L);
        }

        try {
            var team = player.getTeam();
            variables.put("team", team != null ? team.getName() : "");
        } catch (Throwable t) {
            variables.put("team", "");
        }
    }

    private void refreshWorld(ServerPlayer player) {
        if (player.level() == null) {
            variables.put("time", 0L);
            variables.put("day_count", 0L);
            variables.put("is_day", true);
            variables.put("is_night", false);
            variables.put("is_raining", false);
            variables.put("is_thundering", false);
            variables.put("weather", "clear");
            variables.put("moon_phase", 0L);
            variables.put("seed", 0L);
            variables.put("world", "unknown");
            variables.put("world_name", "unknown");
            return;
        }

        long dayTime = player.level().getDayTime();
        long timeOfDay = dayTime % 24000L;
        variables.put("time", timeOfDay);
        variables.put("day_count", dayTime / 24000L);
        variables.put("is_day", timeOfDay >= 0 && timeOfDay < 12000);
        variables.put("is_night", timeOfDay >= 12000);
        variables.put("is_raining", player.level().isRaining());
        variables.put("is_thundering", player.level().isThundering());

        String weather;
        if (player.level().isThundering()) weather = "thunder";
        else if (player.level().isRaining()) weather = "rain";
        else weather = "clear";
        variables.put("weather", weather);

        variables.put("moon_phase", dayTime / 24000L % 8);

        try {
            if (player.level() instanceof ServerLevel sl) {
                variables.put("seed", sl.getSeed());
            } else {
                variables.put("seed", 0L);
            }
        } catch (Throwable t) {
            variables.put("seed", 0L);
        }

        var loc = player.level().dimension().location();
        variables.put("world", loc.toString());
        variables.put("world_name", loc.getPath());
    }

    public void refreshEventData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return;
        for (Map.Entry<String, Object> kv : data.entrySet()) {
            variables.put(kv.getKey(), kv.getValue());
        }
    }

    // ============================================================
    //  substitute — вычисление по текущей области без кэша результатов
    // ============================================================

    public String substitute(String input) {
        if (input == null || input.isEmpty()) return input;

        return doSubstitute(input);
    }

    private String doSubstitute(String input) {
        int n = input.length();

        boolean hasDollar = false;
        boolean hasBackslash = false;
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (c == '$') { hasDollar = true; break; }
            if (c == '\\') hasBackslash = true;
        }
        if (!hasDollar) return hasBackslash ? unescape(input) : input;

        StringBuilder result = new StringBuilder(n + 16);
        int i = 0;

        while (i < n) {
            char c = input.charAt(i);

            if (c == '\\' && i + 1 < n && input.charAt(i + 1) == '$') {
                result.append('$');
                i += 2;
                continue;
            }

            if (c == '$' && i + 1 < n && input.charAt(i + 1) == '{') {
                int start = i + 2;
                int end = findMatching(input, start, n, '{', '}');
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
                        if (Character.isLetter(first) || first == '_') {
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

            if (c == '$') {
                int start = i + 1;
                int end = start;
                while (end < n && isVarChar(input.charAt(end))) end++;
                if (end > start) {
                    String varName = input.substring(start, end);

                    if (end < n && input.charAt(end) == '(') {
                        int close = findMatching(input, end + 1, n, '(', ')');
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
            int close = findMatching(s, i + 1, n, '(', ')');
            return close > 0 ? close + 1 : i;
        }

        while (i < n) {
            char c = s.charAt(i);

            if (c == '.') {
                int j = i + 1;
                while (j < n && isIdentChar(s.charAt(j))) j++;
                if (j == i + 1) break;
                if (j < n && s.charAt(j) == '(') {
                    int close = findMatching(s, j + 1, n, '(', ')');
                    if (close < 0) break;
                    i = close + 1;
                    continue;
                }
                i = j;
                continue;
            }

            if (c == '[') {
                int close = findMatching(s, i + 1, n, '[', ']');
                if (close < 0) break;
                i = close + 1;
                continue;
            }

            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
                int j = i + 1;
                while (j < n && s.charAt(j) == ' ') j++;
                if (j < n) {
                    char nc = s.charAt(j);
                    if (Character.isLetterOrDigit(nc) || nc == '$'
                        || nc == '"' || nc == '(' || nc == '-') {
                        i = j;
                        continue;
                    }
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

    private static int findMatching(String s, int start, int n, char open, char close) {
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
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isVarChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isIdentChar(char c) { return isVarChar(c); }

    private static String unescape(String s) {
        if (s == null || s.indexOf('\\') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n'  -> { out.append('\n'); i++; }
                    case 't'  -> { out.append('\t'); i++; }
                    case 'r'  -> { out.append('\r'); i++; }
                    case '\\' -> { out.append('\\'); i++; }
                    case '"'  -> { out.append('"'); i++; }
                    case '\'' -> { out.append('\''); i++; }
                    default   -> out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}