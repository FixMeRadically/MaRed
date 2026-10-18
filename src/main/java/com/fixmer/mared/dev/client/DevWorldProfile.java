package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.dev.DevPaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Distance cap for supervised world entry (explicit config wins), then bounded gradual restoration. */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public final class DevWorldProfile {
    private static boolean recovered, active, seenWorld, transition, settling, waiting, unready;
    private static Properties ownedJournal;
    private static long waitSince, unreadySince, nextStep;
    private static long settleNs, intervalNs, startupNs;
    private static int step;
    private DevWorldProfile() { }

    public static boolean isActive(){return active;}

    private static Path journal() { return DevPaths.file("world-profile-restore.properties"); }
    private static void safe(Path file) throws IOException {
        for (Path p = file.toAbsolutePath(); p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Linked world profile path");
    }
    private static Properties read(Path file) throws IOException {
        safe(file);
        Properties p = new Properties();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return p;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 8192)
            throw new IOException("Invalid world profile file / size limit");
        try (var r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid world profile properties", e); }
        return p;
    }
    private static int number(Properties p, String key, int fallback, int min, int max) throws IOException {
        try {
            int n = Integer.parseInt(p.getProperty(key, Integer.toString(fallback)).trim());
            if (n < min || n > max) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new IOException("Invalid world profile setting: " + key, e); }
    }
    private static int distance(Properties p, String key) throws IOException {
        if (!p.containsKey(key)) throw new IOException("Incomplete world profile journal: " + key);
        return number(p, key, 2, 2, 64);
    }
    private static boolean managed(Properties p, String axis) throws IOException {
        String value = p.getProperty("manage" + axis, "true");
        if (!value.equals("true") && !value.equals("false")) throw new IOException("Invalid ownership flag");
        return Boolean.parseBoolean(value);
    }
    private static void validate(Properties p) throws IOException {
        distance(p, "render"); distance(p, "simulation");
        distance(p, "capRender"); distance(p, "capSimulation");
        number(p, "previousRender", distance(p, "capRender"), 2, 64);
        number(p, "previousSimulation", distance(p, "capSimulation"), 2, 64);
        managed(p, "Render"); managed(p, "Simulation");
    }
    private static Properties copy(Properties p) { Properties q = new Properties(); q.putAll(p); return q; }
    private static void write(Properties p, boolean creating) throws IOException {
        Path file = journal(); safe(file);
        if (creating) {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Previous world profile journal retained");
        } else if (ownedJournal == null || !read(file).equals(ownedJournal)) {
            throw new IOException("World profile journal changed externally; no overwrite");
        }
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "world-profile-", ".tmp");
        try {
            try (var w = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                p.store(w, "Original distances and current/previous owned step; recover before starting a new profile");
            }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            ownedJournal = copy(p);
        } finally { Files.deleteIfExists(temp); }
    }
    private static void reset() {
        active = seenWorld = false;
        ownedJournal = null;
        settling = waiting = unready = transition = false; nextStep = 0;
    }
    public static void prepare() {
        if (!recovered) { restore(); recovered = true; }
        if (active || !DevMode.isEnabled()) return;
        // Recover an earlier journal above even when tuning is now suppressed.
        if (!com.fixmer.genesis.supervisor.StartupCompatibility.worldTuningAllowed(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get())) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (Files.exists(journal(), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Previous world profile could not be restored");
            Properties cfg = read(DevPaths.file("startup.properties"));
            if (!Boolean.parseBoolean(cfg.getProperty("fastWorld.enabled", Boolean.toString(DevRestartService.isUnderSupervisor())))) return;
            int render = mc.options.renderDistance().get(), simulation = mc.options.simulationDistance().get();
            if (render < 2 || render > 64 || simulation < 2 || simulation > 64)
                throw new IOException("Unsupported option range");
            int capRender = Math.min(render, number(cfg, "fastWorld.viewDistance", 4, 2, 64));
            int capSimulation = Math.min(simulation, Math.min(capRender,
                number(cfg, "fastWorld.simulationDistance", 4, 2, 64)));
            settleNs = TimeUnit.MILLISECONDS.toNanos(number(cfg, "fastWorld.restoreAfterReadyMs", 2000, 0, 30000));
            intervalNs = TimeUnit.MILLISECONDS.toNanos(number(cfg, "fastWorld.restoreStepMs", 500, 100, 10000));
            startupNs = TimeUnit.MILLISECONDS.toNanos(number(cfg, "fastWorld.startupTimeoutMs", 120000, 1000, 600000));
            step = number(cfg, "fastWorld.restoreStep", 2, 1, 8);
            if (render == capRender && simulation == capSimulation) return;
            Properties p = new Properties();
            p.setProperty("render", Integer.toString(render));
            p.setProperty("simulation", Integer.toString(simulation));
            p.setProperty("capRender", Integer.toString(capRender));
            p.setProperty("capSimulation", Integer.toString(capSimulation));
            p.setProperty("previousRender", Integer.toString(render));
            p.setProperty("previousSimulation", Integer.toString(simulation));
            p.setProperty("manageRender", "true"); p.setProperty("manageSimulation", "true");
            write(p, true); // Write-ahead: settings are never changed before the recovery record exists.
            active = true; seenWorld = settling = waiting = unready = false; transition = true;
            mc.options.renderDistance().set(capRender);
            mc.options.simulationDistance().set(capSimulation);
            transition = false;
            Mared.LOGGER.info("[Mared dev] World startup cap: render={}, simulation={}; gradual restore enabled", capRender, capSimulation);
        } catch (Exception e) {
            Mared.LOGGER.warn("[Mared dev] World profile skipped", e);
            // If applying one option failed, use the journal to restore whichever was changed.
            if (active) restore();
        }
    }
    private static boolean matchesOwned(Properties p, String axis, int current) throws IOException {
        return managed(p, axis) && (current == distance(p, "cap" + axis)
            || ((!active || transition) && current == number(p, "previous" + axis, distance(p, "cap" + axis), 2, 64)));
    }
    public static void restore() {
        try {
            Properties p = read(journal());
            if (!Files.exists(journal(), LinkOption.NOFOLLOW_LINKS)) { reset(); return; }
            validate(p); // An empty or partial journal is retained, never guessed.
            if (active && ownedJournal != null && !p.equals(ownedJournal))
                throw new IOException("World profile journal changed externally; retained");
            Minecraft mc = Minecraft.getInstance();
            int render = mc.options.renderDistance().get(), simulation = mc.options.simulationDistance().get();
            if (matchesOwned(p, "Render", render)) mc.options.renderDistance().set(distance(p, "render"));
            if (matchesOwned(p, "Simulation", simulation)) mc.options.simulationDistance().set(distance(p, "simulation"));
            mc.options.save();
            Files.delete(journal());
            reset();
        } catch (Exception e) {
            reset();
            Mared.LOGGER.warn("[Mared dev] Could not restore world profile; journal retained", e);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { tickAt(System.nanoTime()); }

    // Monotonic clock supplied explicitly for deterministic checks; always called on the client thread.
    static void tickAt(long now) {
        Minecraft mc = Minecraft.getInstance();
        if (!recovered && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            restore(); recovered = true;
        }
        if (!active) return;
        if (mc.level != null) seenWorld = true;
        if (seenWorld && mc.level == null) { restore(); return; }
        boolean ready = mc.level != null && mc.player != null && mc.hasSingleplayerServer() && mc.getOverlay() == null;
        if (!waiting) { waiting = true; waitSince = now; }
        if (!ready) {
            settling = false;
            if (!unready) { unready = true; unreadySince = now; }
            if (now - unreadySince >= startupNs) {
                Mared.LOGGER.warn("[Mared dev] World startup readiness timed out; restoring distances");
                restore();
            }
            return;
        }
        unready = false;
        if (!settling) { settling = true; nextStep = now + settleNs; }
        if (now - nextStep < 0) return;
        // One step per tick at most: a long pause must not cause a burst of catch-up changes.
        nextStep = now + intervalNs;
        try {
            Properties p = copy(ownedJournal);
            int render = mc.options.renderDistance().get(), simulation = mc.options.simulationDistance().get();
            // Only the exact current value is owned during the live session. Previous is recovery-only.
            if (render != distance(p, "capRender")) p.setProperty("manageRender", "false");
            if (simulation != distance(p, "capSimulation")) p.setProperty("manageSimulation", "false");
            int nextRender = managed(p, "Render") ? Math.min(distance(p, "render"), render + step) : render;
            int nextSimulation = managed(p, "Simulation") ? Math.min(distance(p, "simulation"), simulation + step) : simulation;
            p.setProperty("previousRender", Integer.toString(render));
            p.setProperty("previousSimulation", Integer.toString(simulation));
            p.setProperty("capRender", Integer.toString(nextRender));
            p.setProperty("capSimulation", Integer.toString(nextSimulation));
            validate(p);
            write(p, false);
            transition = true;
            if (nextRender != render) mc.options.renderDistance().set(nextRender);
            if (nextSimulation != simulation) mc.options.simulationDistance().set(nextSimulation);
            mc.options.save(); // Persist every committed step so crash recovery needs at most its previous value.
            Mared.LOGGER.info("[Mared dev] World distance restore step: render={}, simulation={}", nextRender, nextSimulation);
            transition = false;
            boolean doneRender = !managed(p, "Render") || nextRender == distance(p, "render");
            boolean doneSimulation = !managed(p, "Simulation") || nextSimulation == distance(p, "simulation");
            if (doneRender && doneSimulation) {
                Files.delete(journal());
                reset();
                Mared.LOGGER.info("[Mared dev] World startup profile complete after {} ms; settings restored (chunk rendering may continue)",
                    TimeUnit.NANOSECONDS.toMillis(now - waitSince));
            }
        } catch (Exception e) {
            Mared.LOGGER.warn("[Mared dev] Gradual world profile stopped; attempting recovery", e);
            restore();
        }
    }
}
