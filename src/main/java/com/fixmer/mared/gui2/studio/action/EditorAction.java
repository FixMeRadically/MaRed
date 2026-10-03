package com.fixmer.mared.gui2.studio.action;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Одно действие редактора.
 *
 * 0.3.1:
 *   - parsedShortcut() — ленивый парсер строки. Registry использует
 *     его для executeByKey(keyCode, modifiers).
 */
public final class EditorAction {

    private final String id;
    private final String titleKey;
    private final String shortcutDisplay;
    private final Predicate<EditorActionContext> enabled;
    private final Consumer<EditorActionContext> execute;

    private volatile Shortcut parsedShortcut;
    private volatile boolean parsed = false;

    public EditorAction(String id, String titleKey, String shortcut,
                        Predicate<EditorActionContext> enabled,
                        Consumer<EditorActionContext> execute) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("action id required");
        }
        this.id = id;
        this.titleKey = (titleKey == null || titleKey.isBlank()) ? id : titleKey;
        this.shortcutDisplay = shortcut == null ? "" : shortcut;
        this.enabled = enabled == null ? (ctx -> true) : enabled;
        this.execute = execute == null ? (ctx -> {}) : execute;
    }

    public static EditorAction of(String id, String titleKey, String shortcut,
                                  Consumer<EditorActionContext> execute) {
        return new EditorAction(id, titleKey, shortcut, ctx -> true, execute);
    }

    public static EditorAction of(String id, String titleKey,
                                  Consumer<EditorActionContext> execute) {
        return new EditorAction(id, titleKey, "", ctx -> true, execute);
    }

    public String id() { return id; }
    public String titleKey() { return titleKey; }
    public String shortcutDisplay() { return shortcutDisplay; }
    public Consumer<EditorActionContext> execute() { return execute; }

    public boolean isEnabled(EditorActionContext ctx) {
        try { return enabled.test(ctx); }
        catch (Throwable t) { return false; }
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