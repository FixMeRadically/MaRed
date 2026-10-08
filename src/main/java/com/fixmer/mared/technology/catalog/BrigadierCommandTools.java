package com.fixmer.mared.technology.catalog;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Live dispatcher adapter. Call on the host thread; this class never calls execute(). */
public final class BrigadierCommandTools<S> {
    public static final int MAX_COMMAND_CHARS = 8192, MAX_SUGGESTIONS = 128;
    public record Candidate(int start, int end, String text, String tooltip) {}
    public record Check(boolean valid, int offset, String message) {}
    private final CommandDispatcher<S> dispatcher;
    private final S source;
    public BrigadierCommandTools(CommandDispatcher<S> dispatcher, S source) {
        this.dispatcher = java.util.Objects.requireNonNull(dispatcher);
        this.source = java.util.Objects.requireNonNull(source);
    }
    public Check check(String input) {
        if (input.length() > MAX_COMMAND_CHARS) return new Check(false, 0, "mared.editor.mc_too_large");
        try {
            var parsed = dispatcher.parse(input.stripTrailing(), source);
            if (parsed.getReader().canRead()) {
                var failure = parsed.getExceptions().values().stream()
                    .max(java.util.Comparator.comparingInt(e -> e.getCursor())).orElse(null);
                return new Check(false, failure == null ? parsed.getReader().getCursor() : Math.max(0, failure.getCursor()),
                    failure == null ? "mared.editor.mc_unknown" : failure.getRawMessage().getString());
            }
            var context = parsed.getContext();
            for (int depth = 0; context.getChild() != null && depth < 128; depth++) context = context.getChild();
            if (context.getChild() != null || context.getCommand() == null)
                return new Check(false, input.stripTrailing().length(), "mared.editor.mc_incomplete");
            return new Check(true, 0, "");
        } catch (RuntimeException error) { return new Check(false, 0, "mared.editor.mc_provider_failed"); }
    }
    public CompletableFuture<List<Candidate>> complete(String input, int cursor) {
        if (input.length() > MAX_COMMAND_CHARS || cursor < 0 || cursor > input.length())
            return CompletableFuture.failedFuture(new IllegalArgumentException("mared.editor.mc_too_large"));
        try {
            var parsed = dispatcher.parse(input, source);
            // A provider may never finish. Time out the derived future, not a provider's shared future.
            return dispatcher.getCompletionSuggestions(parsed, cursor).thenApply(suggestions ->
                suggestions.getList().stream().limit(MAX_SUGGESTIONS)
                    .filter(s -> s.getRange().getStart() >= 0 && s.getRange().getEnd() >= s.getRange().getStart()
                        && s.getRange().getEnd() <= input.length() && s.getText().length() <= MAX_COMMAND_CHARS)
                    .map(s -> new Candidate(s.getRange().getStart(), s.getRange().getEnd(), s.getText(),
                        s.getTooltip() == null ? "" : s.getTooltip().getString())).toList())
                .orTimeout(3, TimeUnit.SECONDS);
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
    }
}
