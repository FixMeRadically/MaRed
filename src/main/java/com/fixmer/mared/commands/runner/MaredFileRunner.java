package com.fixmer.mared.commands.runner;

import com.fixmer.mared.commands.engine.*;
import com.fixmer.mared.technology.runtime.CommandFilePlan;
import com.fixmer.mared.technology.runtime.FileRun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Mixed files: raw MC lines outside leading-{ MR blocks. All MR is parsed before effects. */
public final class MaredFileRunner {
    private MaredFileRunner() {}
    private record Section(CommandFilePlan.Part part, List<MaredScriptCommand> commands) {}
    private static final class Pending {
        final FileRun run;
        final List<Section> sections;
        final ClientPacketListener connection;
        final MinecraftServer server;
        final UUID player;
        final boolean persistent;
        final Consumer<String> legacyLogger;
        int index;
        long legacySequence;
        Pending(FileRun run, List<Section> sections, Minecraft mc, boolean persistent, Consumer<String> logger) {
            this.run=run; this.sections=sections; connection=mc.getConnection(); server=mc.getSingleplayerServer();
            player=mc.player.getUUID(); this.persistent=persistent; legacyLogger=logger;
        }
    }
    // Client-thread owned; server callbacks touch only the synchronized FileRun.
    private static final List<Pending> active = new ArrayList<>();
    public static void run(String text, Consumer<String> logger) { start(text, "Command file", logger); }
    public static FileRun start(String text, String title) { return start(text, title, null); }
    private static FileRun start(String text, String title, Consumer<String> logger) {
        FileRun run = new FileRun(title);
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) throw new IllegalStateException("Player unavailable");
            if (active.size() >= 32) throw new IllegalStateException("Too many active file runs (32)");
            var plan = CommandFilePlan.parse(text);
            if (plan.parts().isEmpty()) throw new IllegalArgumentException("File is empty");
            var sections = new ArrayList<Section>();
            for (var part : plan.parts()) {
                try { sections.add(new Section(part, part.script() ? MaredScriptParser.parse(part.text()) : List.of())); }
                catch (RuntimeException error) { throw new IllegalArgumentException("MR block at file line " + part.line() + ": " + error.getMessage(), error); }
            }
            active.add(new Pending(run, List.copyOf(sections), mc, plan.persistent(), logger));
        } catch (RuntimeException error) {
            run.fail(error); run.snapshot();
            if (logger != null) for (var entry : run.journal().snapshot()) logger.accept(entry.message());
        }
        return run;
    }
    /** Called once per client tick, including while disconnected. Never sends more than four MC lines per file per tick. */
    public static void tickClient() {
        Minecraft mc = Minecraft.getInstance();
        for (var iterator = active.iterator(); iterator.hasNext();) {
            Pending pending = iterator.next(); FileRun run = pending.run;
            if (mc.getConnection() != pending.connection || mc.player == null
                || !mc.player.getUUID().equals(pending.player) || mc.getSingleplayerServer() != pending.server) run.disconnected();
            if (run.stopRequested() && pending.server == null) {
                // The client is the MP executor owner, even after player disappearance.
                run.finishClientCancellation();
            }
            run.snapshot(); // detect an executor error before sending further sections
            if (run.stopRequested()) { pending.index = pending.sections.size(); run.submissionDone(); }
            int budget = 4;
            while (pending.index < pending.sections.size() && budget-- > 0 && !run.stopRequested()) {
                Section section = pending.sections.get(pending.index++);
                try {
                    if (!section.part().script()) {
                        pending.connection.sendCommand(section.part().text()); run.commandSent();
                        run.journal().add("[cmd] /" + section.part().text());
                    } else if (run.reserveBlock()) {
                        Runnable submit = () -> {
                            try {
                                if (run.stopRequested()) return;
                                var initiator = pending.server == null ? null : pending.server.getPlayerList().getPlayer(pending.player);
                                if (pending.server != null && initiator == null) throw new IllegalStateException("Server player unavailable");
                                var context = new MaredScriptContext(initiator, pending.server, run.journal()::add);
                                context.setExecutionScope(run.scope());
                                context.setPersistent(pending.persistent); context.forceRefreshPlayerData();
                                var executor = new MaredScriptExecutor(context, section.commands());
                                run.attach(executor); MaredScriptRunner.start(executor);
                                if (!section.commands().isEmpty() && executor.isFinished())
                                    throw new IllegalStateException("Executor rejected by active-script limit");
                            } catch (RuntimeException error) { run.fail(error); }
                            finally { run.blockSubmitted(); }
                        };
                        if (pending.server != null) {
                            try { if (!com.fixmer.mared.technology.runtime.ScriptDispatch.submit(pending.server, submit)) {
                                run.blockSubmitted(); run.fail(new IllegalStateException("Server dispatch queue full"));
                            } }
                            catch (RuntimeException error) { run.blockSubmitted(); throw error; }
                        } else submit.run();
                    }
                } catch (RuntimeException error) { run.fail(error); }
            }
            if (pending.index == pending.sections.size()) run.submissionDone();
            var snapshot = run.snapshot();
            if (pending.legacyLogger != null) {
                for (var entry : run.journal().snapshot()) if (entry.sequence() > pending.legacySequence) {
                    pending.legacyLogger.accept(entry.message()); pending.legacySequence = entry.sequence();
                }
            }
            if (snapshot.terminal()) iterator.remove();
        }
    }
}
