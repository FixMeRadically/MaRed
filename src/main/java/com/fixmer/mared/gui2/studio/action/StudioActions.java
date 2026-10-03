package com.fixmer.mared.gui2.studio.action;

import java.util.List;

import com.fixmer.mared.gui2.studio.ScreenNavigator;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.commands.runner.MaredFileRunner;

/**
 * Регистрация built-in actions Studio.
 *
 * 0.3.1:
 *   - shortcut'ы — единый источник правды. Screen не хардкодит
 *     Ctrl+N/R/S/Esc — они уже тут.
 *   - FILE_RUN получает enabled-predicate: доступен только когда
 *     Workspace существует.
 */
public final class StudioActions {

    private StudioActions() {}

    public static final String FILE_NEW                = "mared:file.new";
    public static final String FILE_SAVE               = "mared:file.save";
    public static final String FILE_RUN                = "mared:file.run";
    public static final String FILE_RELOAD_PERSISTENT  = "mared:file.reload_persistent";
    public static final String VIEW_SETTINGS           = "mared:view.settings";
    public static final String STUDIO_CLOSE            = "mared:studio.close";

    public static final List<String> TOP_BAR_ORDER = List.of(
        FILE_NEW,
        FILE_SAVE,
        FILE_RUN,
        FILE_RELOAD_PERSISTENT,
        VIEW_SETTINGS,
        STUDIO_CLOSE
    );

    public static void registerAll(EditorActionRegistry registry) {
        if (registry == null) return;

        registry.register(new EditorAction(
            FILE_NEW,
            "mared.action.file.new.title",
            "Ctrl+N",
            ctx -> true,
            StudioActions::openNewFileDialog));

        registry.register(new EditorAction(
            FILE_SAVE,
            "mared.action.file.save.title",
            "Ctrl+S",
            ctx -> true,
            ctx -> ctx.controller().bus().publish(
                new StudioEvents.SaveRequestedEvent())));

        registry.register(new EditorAction(
            FILE_RUN,
            "mared.action.file.run.title",
            "Ctrl+R",
            ctx -> ctx.workspace() != null,
            StudioActions::runFile));

        registry.register(new EditorAction(
            FILE_RELOAD_PERSISTENT,
            "mared.action.file.reload_persistent.title",
            "",
            ctx -> true,
            ctx -> ctx.publish(ctx.commands().reloadPersistent())));

        registry.register(new EditorAction(
            VIEW_SETTINGS,
            "mared.action.view.settings.title",
            "",
            ctx -> true,
            ctx -> ScreenNavigator.openSettings(ctx.screen())));

        registry.register(new EditorAction(
            STUDIO_CLOSE,
            "mared.action.studio.close.title",
            "Esc",
            ctx -> true,
            ctx -> {
                if (ctx.screen() != null) ctx.screen().onClose();
            }));
    }

    private static void openNewFileDialog(EditorActionContext ctx) {
        ScreenNavigator.openNewFileDialog(ctx.screen(), (name, persistent) -> {
            var r = ctx.commands().create(name, persistent);
            ctx.publish(r);
            if (r.ok()) ctx.refreshExplorer();
        });
    }

    private static void runFile(EditorActionContext ctx) {
        var ws = ctx.workspace();
        if (ws == null) {
            ctx.log("[run] workspace unavailable");
            return;
        }
        String text = ws.editor().getValue();
        if (text == null || text.trim().isEmpty()) {
            ctx.log("[run] file is empty");
            return;
        }
        if (ws.currentFileName() != null && ws.isDirty()) {
            ws.save();
        }
        MaredFileRunner.run(text, ctx::log);
    }
}