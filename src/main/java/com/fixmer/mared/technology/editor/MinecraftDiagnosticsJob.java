package com.fixmer.mared.technology.editor;

import com.fixmer.genesis.technology.editor.ScriptHeads;
import com.fixmer.genesis.technology.editor.TextSearch;
import com.fixmer.mared.technology.catalog.MinecraftCommandTools;
import java.util.List;

/** Incremental main-thread checks: bounded work per frame, no script/command execution. */
public final class MinecraftDiagnosticsJob {
    private final String source;
    private final List<ScriptHeads.Snippet> commands;
    private final MinecraftCommandTools.Session session;
    private int index, skipped;
    public MinecraftDiagnosticsJob(String source) {
        this.source = source;
        commands = ScriptHeads.minecraft(source, 257);
        session = commands.isEmpty() ? null : MinecraftCommandTools.session();
    }
    public long revision() { return session == null ? -1 : session.revision(); }
    public ScriptDiagnostics.Result advance() {
        if (commands.isEmpty()) return new ScriptDiagnostics.Result(0, "mared.editor.check_ok");
        if (session == null) return new ScriptDiagnostics.Result(-1, "mared.editor.mc_no_connection");
        if (!session.current()) return new ScriptDiagnostics.Result(-1, "mared.editor.mc_tree_changed");
        if (commands.size() > 256) return new ScriptDiagnostics.Result(-1, "mared.editor.mc_limit");
        long deadline = System.nanoTime() + 3_000_000L;
        for (int count = 0; index < commands.size() && count < 4; count++) {
            var snippet = commands.get(index++);
            if (snippet.input().dynamic()) { skipped++; continue; }
            var result = session.tools().check(snippet.input().text());
            if (!result.valid()) {
                int offset = Math.min(snippet.input().text().length(), Math.max(0, result.offset()));
                int line = TextSearch.position(source, snippet.input().sourceOffset(offset)).line() + 1;
                return new ScriptDiagnostics.Result(line, result.message());
            }
            if (System.nanoTime() >= deadline) break;
        }
        if (index < commands.size()) return null;
        return new ScriptDiagnostics.Result(0, skipped == 0 ? "mared.editor.check_mc_ok" : "mared.editor.check_mc_partial");
    }
}
