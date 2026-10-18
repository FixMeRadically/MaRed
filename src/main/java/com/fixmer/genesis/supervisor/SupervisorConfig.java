package com.fixmer.genesis.supervisor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

/** UTF-8 supervisor.properties. Paths resolve relative to the config; command.N preserves argument boundaries. */
final class SupervisorConfig {

    private Path gameDir;
    Path gameDir() { return gameDir; }
    private int startupTimeoutMs,sessionLeaseMs;
    int startupTimeoutMs(){return startupTimeoutMs;}
    int sessionLeaseMs(){return sessionLeaseMs;}
    private boolean restartUi;
    private boolean reducedMotion;
    private boolean fastLaunch;
    boolean restartUi() {return restartUi;}
    boolean reducedMotion() {return reducedMotion;}
    boolean fastLaunch() {return fastLaunch;}
    private boolean warmCache;
    private int warmCacheMs;
    private long warmCacheBytes;
    private String cdsMode;
    boolean warmCache() { return warmCache; }
    int warmCacheMs() { return warmCacheMs; }
    long warmCacheBytes() { return warmCacheBytes; }
    String cdsMode() { return cdsMode; }
    private String token;
    private String instanceId;
    String token() { return token; }
    String instanceId() { return instanceId; }

    private final Path workDir;
    private final List<String> command;
    private final int controlPort;
    private final int gracefulStopMs;
    private final int gracefulRestartWaitMs;
    private final int restartDelayMs;
    private final Path logFile;
    private final Path configPath;

    private SupervisorConfig(Path configPath, Path workDir, List<String> command,
                   int controlPort, int gracefulStopMs, int gracefulRestartWaitMs,
                   int restartDelayMs, Path logFile) {
        this.configPath = configPath;
        this.workDir = workDir;
        this.command = List.copyOf(command);
        this.controlPort = controlPort;
        this.gracefulStopMs = gracefulStopMs;
        this.gracefulRestartWaitMs = gracefulRestartWaitMs;
        this.restartDelayMs = restartDelayMs;
        this.logFile = logFile;
    }

    Path configPath()             { return configPath; }
    Path workDir()                { return workDir; }
    List<String> command()        { return command; }
    int controlPort()             { return controlPort; }
    int gracefulStopMs()          { return gracefulStopMs; }
    int gracefulRestartWaitMs()   { return gracefulRestartWaitMs; }
    int restartDelayMs()          { return restartDelayMs; }
    Path logFile()                { return logFile; }

    static SupervisorConfig load(String[] args) throws IOException {
        Path propsPath = locateConfig(args);
        if (propsPath == null) {
            throw new IOException(
                "supervisor.properties not found. Looked at: --config=..., "
                + "./supervisor.properties, ./config/mared/supervisor.properties, "
                + "and next to the mod jar.");
        }
        if (!Files.isRegularFile(propsPath)) {
            throw new IOException("SupervisorConfig file does not exist: " + propsPath);
        }

        Properties props = new Properties();
        try (Reader in = Files.newBufferedReader(propsPath, StandardCharsets.UTF_8)) {
            props.load(in);
        }

        Path baseDir = propsPath.getParent() != null
                ? propsPath.getParent()
                : Path.of("").toAbsolutePath().normalize();

        String wdRaw = props.getProperty("workDir", ".").trim();
        if (wdRaw.isEmpty()) wdRaw = ".";
        Path workDir = resolve(baseDir, wdRaw);
        if (!Files.isDirectory(workDir)) {
            throw new IOException("workDir does not exist or is not a directory: " + workDir);
        }

        List<String> command = buildCommand(props);
        if (command.isEmpty() || command.get(0).isBlank()) {
            throw new IOException(
                "No launch command configured in " + propsPath + ". "
                + "Set 'command' or numbered 'command.0', 'command.1', ...");
        }

        int port     = parseInt(props, "controlPort",          27670, 1, 65535);
        int graceful = parseInt(props, "gracefulStopMs",        2000, 100, 60_000);
        int restart  = parseInt(props, "restartStopBudgetMs", 2000, 100, 120_000);
        int delay    = parseInt(props, "restartDelayMs",        0, 0, 60_000);

        Path logFile = null;
        String lf = props.getProperty("logFile", "").trim();
        if (!lf.isEmpty()) logFile = resolve(baseDir, lf);

        SupervisorConfig result = new SupervisorConfig(propsPath, workDir, command, port,
            graceful, restart, delay, logFile);
        result.gameDir = resolve(baseDir, props.getProperty("gameDir", workDir.toString()));
        if (!Files.isDirectory(result.gameDir)) throw new IOException("gameDir is not a directory");
        result.startupTimeoutMs=parseInt(props,"session.startupTimeoutMs",900_000,500,3_600_000);
        result.sessionLeaseMs=parseInt(props,"session.leaseTimeoutMs",120_000,2000,600_000);
        result.restartUi=Boolean.parseBoolean(props.getProperty("restartUi.enabled","true"));
        result.reducedMotion=Boolean.parseBoolean(props.getProperty("restartUi.reducedMotion","false"));
        result.fastLaunch=Boolean.parseBoolean(props.getProperty("fastLaunch.enabled","true"));
        result.warmCache=Boolean.parseBoolean(props.getProperty("warmCache.enabled","false"));
        result.warmCacheMs=parseInt(props,"warmCache.maxMs",250,10,5000);
        result.warmCacheBytes=(long)parseInt(props,"warmCache.maxMiB",128,1,1024)*1024*1024;
        result.cdsMode=props.getProperty("cds.mode","default").trim();
        if(!java.util.Set.of("off","default","dynamic").contains(result.cdsMode))throw new IOException("cds.mode must be off, default or dynamic");
        result.token = props.getProperty("controlToken", "").trim();
        result.instanceId = props.getProperty("instanceId", "manual").trim();
        if (result.token.length() < 32) throw new IOException("controlToken must contain at least 32 characters");
        return result;
    }

    private static Path resolve(Path baseDir, String value) {
        Path p = Path.of(stripQuotes(value));
        return (p.isAbsolute() ? p : baseDir.resolve(p)).normalize();
    }

    static String stripQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2) {
            char first = t.charAt(0);
            char last  = t.charAt(t.length() - 1);
            if ((first == '"'  && last == '"')
             || (first == '\'' && last == '\'')) {
                return t.substring(1, t.length() - 1);
            }
        }
        return t;
    }

    private static Path locateConfig(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                String raw = stripQuotes(arg.substring("--config=".length()));
                if (raw == null || raw.isEmpty()) continue;
                return Path.of(raw).toAbsolutePath().normalize();
            }
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();

        Path direct = cwd.resolve("supervisor.properties");
        if (Files.isRegularFile(direct)) return direct;

        Path inConfig = cwd.resolve("config").resolve("mared").resolve("supervisor.properties");
        if (Files.isRegularFile(inConfig)) return inConfig;

        Path legacy = cwd.resolve("supervisor").resolve("supervisor.properties");
        if (Files.isRegularFile(legacy)) return legacy;

        return null;
    }

    private static List<String> buildCommand(Properties props) throws IOException {
        TreeMap<Integer, String> numbered = new TreeMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("command.")) {
                String suffix = key.substring("command.".length());
                try {
                    int idx = Integer.parseInt(suffix);
                    if (idx < 0 || numbered.put(idx, props.getProperty(key)) != null) throw new IOException("Invalid command index: " + key);
                } catch (NumberFormatException e) { throw new IOException("Invalid command key: " + key, e); }
            }
        }
        if (!numbered.isEmpty()) {
            List<String> out = new ArrayList<>(numbered.size());
            for (int i = 0; i < numbered.size(); i++) {
                String v = numbered.get(i);
                if (v == null) {
                    throw new IOException("command." + i
                        + " missing (numbering must be contiguous from 0)");
                }
                if (i == 0 && v.isBlank()) throw new IOException("command.0 is empty");
                out.add(v);
            }
            return out;
        }
        String single = props.getProperty("command", "").trim();
        return splitCommand(single);
    }

    static List<String> splitCommand(String s) throws IOException {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c) || quote != 0) started = true;
            if (quote != 0) {
                if (c == quote) { quote = 0; continue; }
                cur.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (started) { out.add(cur.toString()); cur.setLength(0); started = false; }
            } else {
                cur.append(c);
            }
        }
        if (quote != 0) throw new IOException("Unclosed quote in command; prefer command.N");
        if (started) out.add(cur.toString());
        return out;
    }

    private static int parseInt(Properties p, String key, int def, int min, int max)
            throws IOException {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try {
            int n = Integer.parseInt(v.trim());
            if (n < min || n > max) {
                throw new IOException(key + " must be in [" + min + ", " + max + "], got " + n);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid integer for " + key + ": " + v, e);
        }
    }
}