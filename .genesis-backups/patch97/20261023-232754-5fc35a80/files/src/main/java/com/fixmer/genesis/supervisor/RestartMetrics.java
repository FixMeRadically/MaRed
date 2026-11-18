package com.fixmer.genesis.supervisor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Bounded local timings. One cycle ID spans shutdown of the old JVM and launch of the new one. */
final class RestartMetrics {
    private final Path file;
    private long cycle = System.nanoTime();
    private String cycleId = UUID.randomUUID().toString();
    private String cycleKind = "initial";
    private String sampledLaunch = "";
    private long worldLoading = -1;
    private long worldReady = -1;
    private boolean worldLoadWritten;
    private final Set<String> milestones = new java.util.HashSet<>();

    RestartMetrics(Path game) {
        file = game.resolve("config/mared/dev/logs/timings.jsonl");
    }
    synchronized void begin() { begin("restart"); }
    synchronized void begin(String kind) {
        if (!Set.of("restart", "rollback", "cds_fallback").contains(kind))
            throw new IllegalArgumentException("Unknown timing cycle kind");
        cycle = System.nanoTime();
        cycleId = UUID.randomUUID().toString();
        cycleKind = kind;
        sampledLaunch = "";
        worldLoading = worldReady = -1;
        worldLoadWritten = false;
        milestones.clear();
    }
    synchronized void event(String launch, String phase, long duration, SupervisorLog log) {
        event(launch,phase,duration,java.util.List.of(),log);
    }
    synchronized void event(String launch,String phase,long duration,java.util.List<String> changes,SupervisorLog log) {
        try {
            if (changes.size()>8||changes.stream().anyMatch(s->s.length()>240))throw new IOException("Invalid cache details");
            if (launch == null || !phase.matches("[a-z_]{1,48}") || duration < 0 || duration > 86_400_000)
                throw new IOException("Invalid timing event");
            boolean milestone = Set.of("mod_loaded", "world_loading", "world_ready", "player_restored", "title_ready", "return_failed", "resources_ready").contains(phase);
            if (milestone) {
                if (!sampledLaunch.equals(launch)) {
                    sampledLaunch = launch;
                    worldLoading = worldReady = -1;
                    worldLoadWritten = false;
                    milestones.clear();
                }
                // HTTP retries must not bias the report. First mark for a launch wins.
                if (!milestones.add(phase)) return;
                if (phase.equals("world_loading")) worldLoading = duration;
                if (phase.equals("world_ready")) worldReady = duration;
            }
            RuntimeFiles.safe(file);
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > 2 * 1024 * 1024)
                Files.move(file, file.resolveSibling("timings.previous.jsonl"), StandardCopyOption.REPLACE_EXISTING);
            long elapsed = (System.nanoTime() - cycle) / 1_000_000;
            String json = "{\"time\":" + SupervisorHttpServer.quote(Instant.now().toString())
                + ",\"cycleId\":" + SupervisorHttpServer.quote(cycleId)
                + ",\"cycleKind\":" + SupervisorHttpServer.quote(cycleKind)
                + ",\"launch\":" + SupervisorHttpServer.quote(launch)
                + ",\"phase\":" + SupervisorHttpServer.quote(phase)
                + ",\"valueMs\":" + duration
                + ",\"measure\":" + SupervisorHttpServer.quote(milestone ? "jvm_uptime" : "duration")
                + ",\"cycleMs\":" + elapsed
                + (changes.isEmpty()?"":",\"changedInputs\":["+changes.stream().map(SupervisorHttpServer::quote).collect(java.util.stream.Collectors.joining(","))+"]") + "}\n";
            Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("[timing] " + phase + "=" + duration + " ms (" + (milestone ? "JVM uptime" : "duration") + "); cycle=" + elapsed + " ms");
            // Async HTTP delivery can put world_ready before world_loading.
            if (!worldLoadWritten && worldLoading >= 0 && worldReady >= worldLoading) {
                worldLoadWritten = true;
                event(launch, "world_load", worldReady - worldLoading, log);
            }
        } catch (IOException e) {
            log.warn("Timing write failed: " + e.getMessage());
        }
    }
}
