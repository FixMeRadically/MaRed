package com.fixmer.mared.gui2.studio.action;

import java.util.List;

import com.fixmer.mared.commands.runner.MaredFileRunner;
import com.fixmer.mared.gui2.framework.overlay.CommandPaletteOverlay;
import com.fixmer.mared.gui2.framework.overlay.OverlayManager;
import com.fixmer.mared.gui2.studio.ScreenNavigator;
import com.fixmer.mared.gui2.studio.events.StudioEvents;

/**
 * 0.3.2:
 *   - VIEW_COMMAND_PALETTE — action для открытия Command Palette.
 *   - File actions получили enabled-predicate, требующий fileName:
 *     в палитре они не показываются (args = EMPTY).
 */
public final class StudioActions {

    private StudioActions() {}

    // --- TopBar actions ---

    public static final String FILE_NEW                = "mared:file.new";
    public static final String FILE_SAVE               = "mared:file.save";
    public static final String FILE_SAVE_AS = "mared:file.save_as";
    public static final String EDIT_FIND = "mared:edit.find";
    public static final String EDIT_FIND_NEXT = "mared:edit.find_next";
    public static final String EDIT_FIND_PREVIOUS = "mared:edit.find_previous";
    public static final String EDIT_COMPLETE = "mared:edit.complete";
    public static final String EDIT_CHECK = "mared:edit.check";
    public static final String FILE_STOP = "mared:file.stop";
    public static final String FILE_RUN                = "mared:file.run";
    public static final String FILE_RELOAD_PERSISTENT  = "mared:file.reload_persistent";
    public static final String VIEW_SETTINGS           = "mared:view.settings";
    public static final String STUDIO_CLOSE            = "mared:studio.close";

    /** 0.3.2: Command Palette. */
    public static final String VIEW_COMMAND_PALETTE    = "mared:view.command_palette";

    // --- File actions (context menu) ---

    public static final String FILE_OPEN        = "mared:file.open";
    public static final String FILE_RENAME      = "mared:file.rename";
    public static final String FILE_DUPLICATE   = "mared:file.duplicate";
    public static final String FILE_DELETE      = "mared:file.delete";

    public static final String SEPARATOR = "---";

    public static final List<String> TOP_BAR_ORDER = List.of(
        FILE_NEW, FILE_SAVE, FILE_RUN, FILE_STOP, FILE_RELOAD_PERSISTENT,
        VIEW_SETTINGS, STUDIO_CLOSE
    );

    public static final List<String> FILE_CONTEXT_MENU = List.of(
        FILE_OPEN, SEPARATOR, FILE_RENAME, FILE_DUPLICATE, SEPARATOR, FILE_DELETE
    );

    public static boolean isSeparator(String id) { return SEPARATOR.equals(id); }

    // ============================================================
    //  Registration
    // ============================================================

    public static void registerAll(EditorActionRegistry registry) {
        if (registry == null) return;

        registry.register(new EditorAction(
            FILE_NEW, "mared.action.file.new.title", "Ctrl+N",
            ctx -> true, StudioActions::openNewFileDialog));

        registry.register(new EditorAction(
            FILE_SAVE, "mared.action.file.save.title", "Ctrl+S",
            ctx -> true,
            ctx -> ctx.controller().bus().publish(
                new StudioEvents.SaveRequestedEvent())));

        registry.register(new EditorAction(FILE_SAVE_AS, "mared.action.file.save_as.title", "Ctrl+Shift+S",
            ctx -> ctx.workspace() != null && !ctx.workspace().host().isEmpty(), ctx -> ctx.workspace().requestSaveAs()));
        registry.register(new EditorAction(EDIT_FIND, "mared.action.edit.find.title", "Ctrl+F",
            ctx -> ctx.workspace() != null && !ctx.workspace().host().isEmpty(), ctx -> ctx.workspace().openSearch()));
        registry.register(new EditorAction(EDIT_FIND_NEXT, "mared.action.edit.find_next.title", "F3",
            ctx -> ctx.workspace() != null && !ctx.workspace().host().isEmpty(), ctx -> ctx.workspace().findNext(false)));
        registry.register(new EditorAction(EDIT_FIND_PREVIOUS, "mared.action.edit.find_previous.title", "Shift+F3",
            ctx -> ctx.workspace() != null && !ctx.workspace().host().isEmpty(), ctx -> ctx.workspace().findNext(true)));
        registry.register(new EditorAction(EDIT_COMPLETE, "mared.action.edit.complete.title", "Ctrl+Space",
            ctx -> ctx.workspace() != null && !ctx.workspace().host().isEmpty(), ctx -> ctx.workspace().completeMinecraft()));
        registry.register(new EditorAction(EDIT_CHECK, "mared.action.edit.check.title", "F7",
            ctx -> ctx.workspace() != null && !ctx.workspace().host().isEmpty(), ctx -> ctx.workspace().checkSyntax()));

        registry.register(new EditorAction(
            FILE_RUN, "mared.action.file.run.title", "Ctrl+R",
            ctx -> ctx.workspace() != null && !ctx.workspace().host().isEmpty() && ctx.runs().canStart(), StudioActions::runFile));
        registry.register(new EditorAction(FILE_STOP, "mared.action.file.stop.title", "Ctrl+Shift+R",
            ctx -> ctx.runs().canStop(), ctx -> ctx.runs().stop()));

        registry.register(new EditorAction(
            FILE_RELOAD_PERSISTENT,
            "mared.action.file.reload_persistent.title", "",
            ctx -> true, ctx -> ctx.publish(ctx.commands().reloadPersistent())));

        registry.register(new EditorAction(
            VIEW_SETTINGS, "mared.action.view.settings.title", "",
            ctx -> true,
            ctx -> {
                if (ctx.screen() != null) {
                    ScreenNavigator.openSettings(ctx.screen());
                }
            }));

        registry.register(new EditorAction(
            VIEW_COMMAND_PALETTE,
            "mared.action.view.command_palette.title",
            "Ctrl+Shift+P",
            ctx -> true,
            StudioActions::openCommandPalette));

        registry.register(new EditorAction(
            STUDIO_CLOSE, "mared.action.studio.close.title", "Esc",
            ctx -> true,
            ctx -> { if (ctx.screen() != null) ctx.screen().onClose(); }));

        // File actions — enabled только с fileName.
        registry.register(EditorAction.ofArgs(
            FILE_OPEN, "mared.action.file.open.title", "",
            StudioActions::openFileAction));
        registry.register(EditorAction.ofArgs(
            FILE_RENAME, "mared.action.file.rename.title", "",
            StudioActions::renameFileAction));
        registry.register(EditorAction.ofArgs(
            FILE_DUPLICATE, "mared.action.file.duplicate.title", "",
            StudioActions::duplicateFileAction));
        registry.register(EditorAction.ofArgs(
            FILE_DELETE, "mared.action.file.delete.title", "",
            StudioActions::deleteFileAction));

        // Специальный случай: file actions не должны показываться в палитре.
        // Пере-регистрация с enabled-predicate, требующим fileName.
        registry.unregister(FILE_OPEN);
        registry.unregister(FILE_RENAME);
        registry.unregister(FILE_DUPLICATE);
        registry.unregister(FILE_DELETE);

        registry.register(new EditorAction(
            FILE_OPEN, "mared.action.file.open.title", "",
            (ctx, args) -> args.has("fileName"),
            (ctx, args) -> openFileAction(ctx, args)));
        registry.register(new EditorAction(
            FILE_RENAME, "mared.action.file.rename.title", "",
            (ctx, args) -> args.has("fileName"),
            (ctx, args) -> renameFileAction(ctx, args)));
        registry.register(new EditorAction(
            FILE_DUPLICATE, "mared.action.file.duplicate.title", "",
            (ctx, args) -> args.has("fileName"),
            (ctx, args) -> duplicateFileAction(ctx, args)));
        registry.register(new EditorAction(
            FILE_DELETE, "mared.action.file.delete.title", "",
            (ctx, args) -> args.has("fileName"),
            (ctx, args) -> deleteFileAction(ctx, args)));
    }

    // ============================================================
    //  Top bar impl
    // ============================================================

    private static void openNewFileDialog(EditorActionContext ctx) {
        OverlayManager overlays = ctx.overlays();
        if (overlays == null) return;
        ScreenNavigator.openNewFileDialog(overlays, (name, persistent) -> {
            var r = ctx.commands().create(name, persistent);
            ctx.publish(r);
            if (r.ok()) {
                ctx.refreshExplorer();
                ctx.controller().bus().publish(new StudioEvents.FileSelectedEvent(name));
            }
        });
    }

    private static void runFile(EditorActionContext ctx) {
        var ws = ctx.workspace();
        if (ws == null) { ctx.log("[run] workspace unavailable"); return; }
        String text = ws.editor().getValue();
        if (text == null || text.trim().isEmpty()) {
            ctx.log("[run] file is empty");
            return;
        }
        ctx.runs().start(text, ws.currentFileName());
    }

    private static void openCommandPalette(EditorActionContext ctx) {
        OverlayManager overlays = ctx.overlays();
        if (overlays == null) return;
        if (ctx.controller() == null) return;

        EditorActionRegistry registry = ctx.controller().actions();
        if (registry == null) {
            // fallback — не должно случаться
            return;
        }

        overlays.push(new CommandPaletteOverlay(registry, ctx, null));
    }

    // ============================================================
    //  File actions
    // ============================================================

    private static void openFileAction(EditorActionContext ctx,
                                       EditorActionArgs args) {
        String fileName = args.string("fileName");
        if (fileName == null || fileName.isEmpty()) return;
        ctx.controller().bus().publish(new StudioEvents.FileSelectedEvent(fileName));
    }

    private static void renameFileAction(EditorActionContext ctx,
                                         EditorActionArgs args) {
        String oldName = args.string("fileName");
        if (oldName == null || oldName.isEmpty()) return;
        OverlayManager overlays = ctx.overlays();
        if (overlays == null) return;

        ScreenNavigator.openRenameDialog(overlays, oldName,
            (newName, persistent) -> {
                var r = ctx.commands().rename(oldName, newName);
                ctx.publish(r);
                if (r.ok()) {
                    ctx.controller().bus().publish(
                        new StudioEvents.FileRenamedEvent(oldName, newName));
                    ctx.refreshExplorer();
                }
            });
    }

    private static void duplicateFileAction(EditorActionContext ctx,
                                            EditorActionArgs args) {
        String fileName = args.string("fileName");
        if (fileName == null || fileName.isEmpty()) return;
        var r = ctx.commands().duplicate(fileName);
        ctx.publish(r);
        if (r.ok()) ctx.refreshExplorer();
    }

    private static void deleteFileAction(EditorActionContext ctx,
                                         EditorActionArgs args) {
        String fileName = args.string("fileName");
        if (fileName == null || fileName.isEmpty()) return;

        if (!ctx.commands().exists(fileName)) {
            ctx.log("[studio] delete skipped: file does not exist: " + fileName);
            ctx.refreshExplorer();
            return;
        }
        boolean persistent;
        try{persistent=ctx.commands().isPersistent(fileName);}
        catch(Exception error){ctx.log("[studio] cannot read persistent configuration: "+error.getMessage());return;}
        OverlayManager overlays = ctx.overlays();
        if (overlays == null) return;

        ScreenNavigator.openDeleteConfirm(overlays, fileName, persistent, () -> {
            var r = ctx.commands().delete(fileName);
            ctx.publish(r);
            if (r.ok()) {
                ctx.controller().bus().publish(
                    new StudioEvents.FileDeletedEvent(fileName));
                ctx.refreshExplorer();
            }
        });
    }
}