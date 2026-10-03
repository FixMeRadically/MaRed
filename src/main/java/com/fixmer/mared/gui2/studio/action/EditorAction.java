package com.fixmer.mared.gui2.studio.action;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Одно действие редактора.
 *
 * 0.3.1:
 *   - parsedShortcut() — ленивый парсер строки.
 * 0.3.2:
 *   - Добавлены overload'ы с EditorActionArgs. Старые actions без
 *     args продолжают работать через простой конструктор.
 */
public final class EditorAction {

    private final String id;
    private final String titleKey;
    private final String shortcutDisplay;
    private final BiPredicate<EditorActionContext, EditorActionArgs> enabled;
    private final BiConsumer<EditorActionContext, EditorActionArgs> execute;

    private volatile Shortcut parsedShortcut;
    private volatile boolean parsed = false;

    // ============================================================
    //  Полный конструктор с args
    // ============================================================

    public EditorAction(String id,
                        String titleKey,
                        String shortcut,
                        BiPredicate<EditorActionContext, EditorActionArgs> enabled,
                        BiConsumer<EditorActionContext, EditorActionArgs> execute) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("action id required");
        }
        this.id = id;
        this.titleKey = (titleKey == null || titleKey.isBlank()) ? id : titleKey;
        this.shortcutDisplay = shortcut == null ? "" : shortcut;
        this.enabled = enabled == null ? ((ctx, args) -> true) : enabled;
        this.execute = execute == null ? ((ctx, args) -> {}) : execute;
    }

    // ============================================================
    //  Overloads без args — backwards compat
    // ============================================================

    public EditorAction(String id,
                        String titleKey,
                        String shortcut,
                        Predicate<EditorActionContext> enabled,
                        Consumer<EditorActionContext> execute) {
        this(id, titleKey, shortcut,
            enabled == null ? ((ctx, args) -> true)
                            : ((ctx, args) -> enabled.test(ctx)),
            execute == null ? ((ctx, args) -> {})
                            : ((ctx, args) -> execute.accept(ctx)));
    }

    // ============================================================
    //  Static factories
    // ============================================================

    public static EditorAction of(String id, String titleKey, String shortcut,
                                  Consumer<EditorActionContext> execute) {
        return new EditorAction(id, titleKey, shortcut, ctx -> true, execute);
    }

    public static EditorAction of(String id, String titleKey,
                                  Consumer<EditorActionContext> execute) {
        return new EditorAction(id, titleKey, "", ctx -> true, execute);
    }

    public static EditorAction ofArgs(String id, String titleKey,
                                      String shortcut,
                                      BiConsumer<EditorActionContext, EditorActionArgs> execute) {
        return new EditorAction(id, titleKey, shortcut,
            (ctx, args) -> true, execute);
    }

    // ============================================================
    //  Public API
    // ============================================================

    public String id() { return id; }
    public String titleKey() { return titleKey; }
    public String shortcutDisplay() { return shortcutDisplay; }

    public boolean isEnabled(EditorActionContext ctx) {
        return isEnabled(ctx, EditorActionArgs.EMPTY);
    }

    public boolean isEnabled(EditorActionContext ctx, EditorActionArgs args) {
        try {
            return enabled.test(ctx, args == null ? EditorActionArgs.EMPTY : args);
        } catch (Throwable t) {
            return false;
        }
    }

    public void run(EditorActionContext ctx) {
        run(ctx, EditorActionArgs.EMPTY);
    }

    public void run(EditorActionContext ctx, EditorActionArgs args) {
        execute.accept(ctx, args == null ? EditorActionArgs.EMPTY : args);
    }

    /** Парсится один раз, кэшируется. null — если shortcut невалиден. */
    public Shortcut parsedShortcut() {
        if (!parsed) {
            parsedShortcut = Shortcut.parse(shortcutDisplay);
            parsed = true;
        }
        return parsedShortcut;
    }
}