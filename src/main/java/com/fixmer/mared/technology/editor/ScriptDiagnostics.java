package com.fixmer.mared.technology.editor;

import com.fixmer.mared.commands.engine.MaredScriptParser;

/** Checks MR grammar only. This never executes a script or queries the game world. */
public final class ScriptDiagnostics {
    public record Result(int line, String message) { public boolean valid() { return line == 0; } }
    public static final int MAX_CHECK_CHARS = 1_048_576;
    private ScriptDiagnostics() {}
    public static Result check(String source) {
        if (source.length() > MAX_CHECK_CHARS) return new Result(-1, "mared.editor.check_too_large");
        try { MaredScriptParser.parse(source); return new Result(0, "mared.editor.check_ok"); }
        catch (MaredScriptParser.ParseException error) { return new Result(error.line, error.getMessage()); }
        catch (IllegalArgumentException error) { return new Result(-1, error.getMessage()); }
    }
}
