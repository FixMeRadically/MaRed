package com.fixmer.mared.gui2.studio.panels.workspace;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.components.editor.MaredMultiLineEditBox;
import com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredBounds;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.core.NarratableComponent;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;
import com.fixmer.mared.services.command.CommandFileService;
import com.fixmer.mared.services.logging.LogSettings;
import com.fixmer.mared.services.threading.MaredThreading;
import com.fixmer.mared.services.threading.TaskScheduler;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Компонент рабочей области Studio.
 *
 * 0.3.2 (multi-document):
 *   - Работает с WorkspaceHost — список DocumentSession.
 *   - Tab bar в header'е: имя + dirty + close.
 *   - Клик по табу — активация, средний клик / × — закрытие.
 *   - Save-сериализация — per-document (saveInFlight в session).
 *   - Save All при закрытии Studio.
 *
 * 0.3.2 (syntax highlighting):
 *   - Редактор использует MaredScriptSpanResolver.
 *
 * 0.3.2 (accessibility):
 *   - Реализует NarratableComponent — FocusTraversal озвучивает
 *     состояние Workspace при Tab-навигации.
 */
public final class WorkspaceComponent extends MaredComponent
        implements Disposable, NarratableComponent {

    private static final int HEADER_H    = 18;
    private static final int TAB_MIN_W   = 80;
    private static final int TAB_MAX_W   = 180;
    private static final int TAB_GAP     = 2;
    private static final int CLOSE_BTN_W = 12;
    private static final int CLOSE_BTN_PAD = 4;

    private final StudioEventBus bus;
    private final WorkspaceHost host = new WorkspaceHost();
    private final MaredMultiLineEditBox editor;
    private com.fixmer.mared.gui2.framework.components.editor.SpanResolver spanResolver=MaredScriptSpanResolver.INSTANCE;
    public void setSpanResolver(com.fixmer.mared.gui2.framework.components.editor.SpanResolver resolver){
        spanResolver=java.util.Objects.requireNonNull(resolver);
        for(var document:host.documents())document.view.setSpanResolver(resolver);
        editor.setSpanResolver(resolver);
    }
    private final SubscriptionGroup subs = new SubscriptionGroup();
    private final com.fixmer.mared.technology.storage.WorkspaceDrafts drafts = new com.fixmer.mared.technology.storage.WorkspaceDrafts();
    private boolean restored;
    private boolean disposed;
    private final com.fixmer.mared.technology.editor.CommandCompletionPopup completion = new com.fixmer.mared.technology.editor.CommandCompletionPopup();
    private record PendingCheck(DocumentSession document, int version, long sequence,
                                com.fixmer.mared.technology.editor.MinecraftDiagnosticsJob job) {}
    private final java.util.ArrayDeque<PendingCheck> minecraftChecks = new java.util.ArrayDeque<>();
    public void completeMinecraft() {
        if (host.active() == null || disposed) return;
        if (searchBox != null) searchBox.setFocused(false);
        if (replaceBox != null) replaceBox.setFocused(false);
        requestFocus(); editor.setFocused(true);
        completion.request(host.active());
    }
    private boolean diagnosticStale(DocumentSession s) {
        return s.diagnosticVersion != s.document.contentVersion()
            || (s.diagnosticCatalogRevision >= 0 && s.diagnosticCatalogRevision != com.fixmer.mared.commands.registry.MaredCommandRegistry.revision());
    }
    private void advanceMinecraftChecks() {
        while (!minecraftChecks.isEmpty()) {
            var check = minecraftChecks.peek(); var s = check.document();
            if (!host.documents().contains(s) || s.document.contentVersion() != check.version() || s.diagnosticSequence != check.sequence()) {
                minecraftChecks.remove(); continue;
            }
            var result = check.job().advance();
            if (result == null) { minecraftChecks.remove(); minecraftChecks.addLast(check); return; }
            minecraftChecks.remove();
            s.diagnosticCatalogRevision = check.job().revision();
            s.diagnosticLine = result.line();
            s.diagnosticMessage = result.message().startsWith("mared.") ? MaredLang.get(result.message()) : result.message();
            return;
        }
    }
    private java.util.function.Consumer<DocumentSession> saveAsHandler;
    public void setSaveAsHandler(java.util.function.Consumer<DocumentSession> handler) { saveAsHandler = handler; }
    public void requestSaveAs() { requestSaveAs(host.active()); }
    private void requestSaveAs(DocumentSession document) {
        if (document == null) return;
        if (disposed || document.saveInFlight || saveAsHandler == null) {
            document.closeAfterSaveAs = false;
            return;
        }
        saveAsHandler.accept(document);
    }

    /** Create a new file atomically; preserve the document, history and view. */
    public void saveAs(DocumentSession document, String name, Runnable created) {
        if (disposed || !host.documents().contains(document) || document.saveInFlight)
            throw new IllegalStateException(MaredLang.get("mared.editor.save_as_unavailable"));
        if (com.fixmer.mared.commands.storage.MaredCommandStorage.validateName(name) != null)
            throw new IllegalArgumentException(MaredLang.get("mared.dialog.error.charset"));
        if (host.byStorageId(name) != null || com.fixmer.mared.commands.storage.MaredCommandStorage.exists(name))
            throw new IllegalArgumentException(MaredLang.format("mared.dialog.error.exists", name));
        String snapshot = document.currentText();
        long sequence = ++document.saveSequence;
        document.saveInFlight = true;
        document.saveAsInFlight = true;
        document.inFlightText = snapshot;
        java.util.function.Consumer<Boolean> complete = ok -> {
            if (disposed || document.saveSequence != sequence || !host.documents().contains(document)) return;
            document.saveInFlight = false;
            document.saveAsInFlight = false;
            document.inFlightText = null;
            if (ok) {
                document.storageId = name;
                document.exists = true;
                document.lastSavedText = snapshot;
                document.recomputeDirty();
                created.run();
                bus.publish(new StudioEvents.LogEvent(LogSettings.Level.INFO, "studio", "saved as: " + name));
                boolean close = document.closeAfterSaveAs && !document.dirty;
                document.closeAfterSaveAs = false;
                if (close) doClose(document);
                else maybeFlushPending(document);
            } else {
                document.closeAfterSaveAs = false;
                document.pendingSave = false;
                bus.publish(new StudioEvents.LogEvent(LogSettings.Level.ERROR, "studio",
                    MaredLang.format("mared.editor.save_as_failed", name)));
            }
        };
        try {
            if (!MaredThreading.isInitialized()) {
                complete.accept(com.fixmer.mared.commands.storage.MaredCommandStorage.createCommand(name, snapshot));
            } else MaredThreading.scheduler().submit("Save as:" + name,
                () -> com.fixmer.mared.commands.storage.MaredCommandStorage.createCommand(name, snapshot),
                complete, error -> complete.accept(false), null);
        } catch (RuntimeException error) {
            complete.accept(false);
            throw error;
        }
    }
    private net.minecraft.client.gui.components.EditBox searchBox;
    private boolean searchOpen, searchCase, replaceOpen;
    private net.minecraft.client.gui.components.EditBox replaceBox;
    private int searchHeight() { return searchOpen ? SEARCH_H * (replaceOpen ? 2 : 1) : 0; }
    private boolean searchFieldFocused() { return searchBox != null && searchBox.isFocused() || replaceOpen && replaceBox != null && replaceBox.isFocused(); }
    private String searchMessage = "";
    private static final int SEARCH_H = 22, STATUS_H = 16;
    private long nextCheckpoint;

    public boolean canEditLines() { return !disposed && host.active()!=null && editor.isEditable() && isFocused() && !searchFieldFocused(); }
    public void editLines(com.fixmer.genesis.technology.editor.LineTools.Operation operation) {
        var session=host.active();if(!canEditLines())return;
        var doc=session.document;int[] selection=doc.orderedSelection();
        int first=selection==null?doc.cursorLine():selection[0],last=selection==null?first:selection[2];
        if(selection!=null&&selection[3]==0&&last>first)last--;
        try {
            String before=session.currentText();
            var result=com.fixmer.genesis.technology.editor.LineTools.edit(before,first,last,doc.cursorLine(),doc.cursorCol(),operation,
                com.fixmer.mared.technology.editor.DocumentReplacement.MAX_OUTPUT_CHARS);
            int offset=com.fixmer.genesis.technology.editor.LineTools.offset(result.text(),selection==null?result.cursorLine():result.last(),
                selection==null?result.cursorColumn():result.lastColumn());
            if(!com.fixmer.mared.technology.editor.DocumentReplacement.apply(session,before,result.text(),offset))return;
            if(selection!=null){doc.startSelection(result.first(),0);doc.extendSelection(result.last(),doc.lineLength(result.last()));}
            onEditorChanged();closeSearch();requestFocus();editor.setFocused(true);
            session.view.ensureCursorVisible(editor.getWidth(),editor.getHeight(),doc,net.minecraft.client.Minecraft.getInstance().font);
        } catch(IllegalArgumentException error){searchMessage=MaredLang.get("mared.editor.replace.limit");}
    }
    public String commandFragment() {
        var session=host.active();if(!canEditLines())throw new IllegalStateException("No focused document");
        var doc=session.document;int[] selection=doc.orderedSelection();
        int first=selection==null?doc.cursorLine():selection[0],last=selection==null?first:selection[2];
        int start=selection==null?0:selection[1],end=selection==null?doc.lineLength(last):selection[3];
        if(selection!=null&&end==0&&last>first){last--;end=doc.lineLength(last);}
        return com.fixmer.mared.technology.runtime.CommandFragment.extract(session.currentText(),first,start,last,end);
    }

    public void openSearch() {
        completion.dismiss();
        if (host.active() == null) return;
        searchOpen = true;
        replaceOpen = false;
        ensureSearch();
        layout(bounds);
        requestFocus();
        editor.setFocused(false);
        searchBox.setFocused(bounds.width() >= 160 && bounds.height() >= 100);
        replaceBox.setFocused(false);
    }
    private void ensureSearch() {
        if (searchBox != null) return;
        searchBox = new net.minecraft.client.gui.components.EditBox(
            net.minecraft.client.Minecraft.getInstance().font, 0, 0, 80, 16,
            Component.literal(MaredLang.get("mared.editor.find")));
        searchBox.setBordered(false);
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.literal(MaredLang.get("mared.editor.find")));
        searchBox.setResponder(query -> { searchMessage = ""; });
        replaceBox = new net.minecraft.client.gui.components.EditBox(
            net.minecraft.client.Minecraft.getInstance().font, 0, 0, 80, 16,
            Component.literal(MaredLang.get("mared.editor.replace.with")));
        replaceBox.setBordered(false);
        replaceBox.setMaxLength(2048);
        replaceBox.setHint(Component.literal(MaredLang.get("mared.editor.replace.with")));
        replaceBox.setResponder(value -> { searchMessage = ""; });
    }
    public void openReplace() {
        openSearch();
        if (!searchOpen || host.active() == null) return;
        replaceOpen = true;
        String selected = host.active().document.getSelectedText();
        if (!selected.isEmpty() && selected.length() <= 128 && selected.indexOf('\n') < 0) searchBox.setValue(selected);
        layout(bounds);
    }
    public void replaceOne() {
        var session = host.active();
        if (session == null || disposed || !editor.isEditable()) return;
        ensureSearch();
        String before = session.currentText(), query = searchBox.getValue();
        if (query.isEmpty()) return;
        int[] selection = session.document.orderedSelection();
        if (selection == null) { findNext(false); return; }
        int start = selection[1], end = selection[3];
        for (int i = 0; i < selection[0]; i++) start += session.document.lineLength(i) + 1;
        for (int i = 0; i < selection[2]; i++) end += session.document.lineLength(i) + 1;
        if (!new com.fixmer.genesis.technology.editor.TextSearch(query, searchCase).matches(before, start, end)) {
            findNext(false); return;
        }
        String replacement = replaceBox.getValue();
        if ((long) before.length() - (end - start) + replacement.length() > com.fixmer.mared.technology.editor.DocumentReplacement.MAX_OUTPUT_CHARS) {
            searchMessage = MaredLang.get("mared.editor.replace.limit"); return;
        }
        try {
            boolean changed = com.fixmer.mared.technology.editor.DocumentReplacement.apply(session, before,
                before.substring(0, start) + replacement + before.substring(end), start + replacement.length());
            onEditorChanged();
            findNext(false);
            searchMessage = changed ? MaredLang.format("mared.editor.replace.done", 1) : MaredLang.get("mared.editor.replace.unchanged");
        } catch (IllegalArgumentException error) { searchMessage = MaredLang.get("mared.editor.replace.limit"); }
    }
    public void replaceAll() {
        var session = host.active();
        if (session == null || disposed || !editor.isEditable()) return;
        ensureSearch();
        String before = session.currentText();
        try {
            var result = new com.fixmer.genesis.technology.editor.TextSearch(searchBox.getValue(), searchCase)
                .replaceAll(before, replaceBox.getValue(), com.fixmer.mared.technology.editor.DocumentReplacement.MAX_OUTPUT_CHARS);
            if (result.count() == 0) { searchMessage = MaredLang.get("mared.editor.not_found"); return; }
            int offset = session.document.cursorCol();
            for (int i = 0; i < session.document.cursorLine(); i++) offset += session.document.lineLength(i) + 1;
            boolean changed = com.fixmer.mared.technology.editor.DocumentReplacement.apply(session, before, result.text(), offset);
            onEditorChanged();
            session.view.ensureCursorVisible(editor.getWidth(), editor.getHeight(), session.document, net.minecraft.client.Minecraft.getInstance().font);
            searchMessage = changed ? MaredLang.format("mared.editor.replace.done", result.count()) : MaredLang.get("mared.editor.replace.unchanged");
        } catch (IllegalArgumentException error) { searchMessage = MaredLang.get("mared.editor.replace.limit"); }
    }
    private void closeSearch() {
        searchOpen = false;
        if (searchBox != null) searchBox.setFocused(false);
        if (replaceBox != null) replaceBox.setFocused(false);
        searchMessage = "";
        layout(bounds);
        editor.setFocused(isFocused());
    }
    public void findNext(boolean backwards) {
        DocumentSession s = host.active();
        if (s == null) return;
        if (!searchOpen) { openSearch(); return; }
        String source = s.currentText(), query = searchBox.getValue();
        if (query.isEmpty()) return;
        int[] selection = s.document.orderedSelection();
        int fromLine = backwards && selection != null ? selection[0] : s.document.cursorLine();
        int from = backwards && selection != null ? selection[1] : s.document.cursorCol();
        for (int line = 0; line < fromLine; line++) from += s.document.lineLength(line) + 1;
        var match = new com.fixmer.genesis.technology.editor.TextSearch(query, searchCase).find(source, from, backwards);
        if (match == null) { searchMessage = MaredLang.get("mared.editor.not_found"); return; }
        searchMessage = "";
        var begin = com.fixmer.genesis.technology.editor.TextSearch.position(source, match.start());
        var end = com.fixmer.genesis.technology.editor.TextSearch.position(source, match.end());
        s.document.startSelection(begin.line(), begin.column());
        s.document.extendSelection(end.line(), end.column());
        s.document.setCursor(end.line(), end.column());
        s.view.ensureCursorVisible(editor.getWidth(), editor.getHeight(), s.document,
            net.minecraft.client.Minecraft.getInstance().font);
        s.view.resetBlink();
    }
    public void checkSyntax() {
        completion.dismiss();
        DocumentSession s = host.active();
        if (s == null || disposed) return;
        int version = s.document.contentVersion();
        if (s.diagnosticVersion == version && s.diagnosticMessage.equals(MaredLang.get("mared.editor.checking"))) return;
        String snapshot = s.currentText();
        long sequence = ++s.diagnosticSequence;
        s.diagnosticVersion = version;
        s.diagnosticLine = 0;
        s.diagnosticCatalogRevision = -1;
        s.diagnosticMessage = MaredLang.get("mared.editor.checking");
        java.util.function.Consumer<com.fixmer.mared.technology.editor.ScriptDiagnostics.Result> complete = result -> {
            if (disposed || !host.documents().contains(s) || s.diagnosticSequence != sequence
                    || s.document.contentVersion() != version) return;
            if (result.valid()) {
                minecraftChecks.addLast(new PendingCheck(s, version, sequence,
                    new com.fixmer.mared.technology.editor.MinecraftDiagnosticsJob(snapshot)));
                return;
            }
            s.diagnosticVersion = version;
            s.diagnosticLine = result.line();
            s.diagnosticMessage = result.message().startsWith("mared.") ? MaredLang.get(result.message()) : result.message();
        };
        if (!MaredThreading.isInitialized()) { complete.accept(com.fixmer.mared.technology.editor.ScriptDiagnostics.check(snapshot)); return; }
        try {
            MaredThreading.scheduler().submit("Check syntax:" + s.title(),
                () -> com.fixmer.mared.technology.editor.ScriptDiagnostics.check(snapshot), complete,
                error -> complete.accept(new com.fixmer.mared.technology.editor.ScriptDiagnostics.Result(-1,
                    MaredLang.get("mared.editor.check_failed"))), null);
        } catch (RuntimeException error) {
            complete.accept(new com.fixmer.mared.technology.editor.ScriptDiagnostics.Result(-1,
                MaredLang.get("mared.editor.check_failed")));
        }
    }
    private void jumpToDiagnostic() {
        DocumentSession s = host.active();
        if (s == null) return;
        if (diagnosticStale(s) || s.diagnosticLine <= 0) { checkSyntax(); return; }
        s.document.clearSelection();
        s.document.setCursor(s.diagnosticLine - 1, 0);
        s.view.ensureCursorVisible(editor.getWidth(), editor.getHeight(), s.document,
            net.minecraft.client.Minecraft.getInstance().font);
        requestFocus();
        if (searchBox != null) searchBox.setFocused(false);
        if (replaceBox != null) replaceBox.setFocused(false);
        editor.setFocused(true);
        s.view.resetBlink();
    }
    private void drawEditorTools(GuiGraphics g, Font font) {
        var s = host.active();
        if (s == null) return;
        if (searchOpen) {
            ensureSearch();
            searchBox.setTextColor(com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());
            searchBox.setTextColorUneditable(com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim());
            int y = bounds.y() + HEADER_H;
            g.fill(bounds.x(), y, bounds.right(), y + searchHeight(),
                com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised());
            if (bounds.width() < 160 || bounds.height() < 100) {
                g.drawString(font, ellipsize(font, MaredLang.get("mared.editor.replace.enlarge"), bounds.width() - 12), bounds.x() + 6, y + 6,
                    com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
            } else {
                searchBox.render(g, lastMouseX, lastMouseY, 0);
                int x = bounds.right() - 90;
                g.drawString(font, "<", x + 4, y + 6, com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(), false);
                g.drawString(font, ">", x + 21, y + 6, com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(), false);
                g.drawString(font, "Aa", x + 38, y + 6, searchCase ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
                g.drawString(font, "R", x + 59, y + 6, replaceOpen ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
                g.drawString(font, "x", x + 79, y + 6, com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
                if (replaceOpen) {
                    replaceBox.setTextColor(com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());
                    replaceBox.render(g, lastMouseX, lastMouseY, 0);
                    g.drawString(font, "1", bounds.right() - 65, y + SEARCH_H + 6, com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(), false);
                    g.drawString(font, MaredLang.get("mared.editor.replace.all"), bounds.right() - 42, y + SEARCH_H + 6, com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(), false);
                }
            }
        }
        int y = bounds.bottom() - STATUS_H;
        g.fill(bounds.x(), y, bounds.right(), bounds.bottom(), com.fixmer.mared.technology.editor.GenesisEditorVisuals.panel());
        String label = !completion.message().isEmpty() ? completion.message()
            : !searchMessage.isEmpty() ? searchMessage
            : s.diagnosticMessage.isEmpty() ? MaredLang.get("mared.editor.check_hint")
            : diagnosticStale(s) ? MaredLang.get("mared.editor.check_stale") : s.diagnosticMessage;
        boolean error = searchMessage.isEmpty() && !diagnosticStale(s) && s.diagnosticLine > 0;
        g.drawString(font, ellipsize(font, label, bounds.width() - 12), bounds.x() + 6, y + 4,
            error ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.danger() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
    }
    private java.util.List<com.fixmer.mared.technology.storage.WorkspaceDrafts.Draft> lastCheckpoint;
    private final CommandFileService commandService = new CommandFileService();

    // Кэш ширин табов для hit-test в mouseClicked.
    private int[] tabXCache = new int[0];
    private int[] tabWCache = new int[0];
    private int   tabBarX = 0;
    private int   tabBarW = 0;

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    // Pending-сессия для закрытия через confirm-dialog.
    private DocumentSession pendingCloseSession = null;

    public WorkspaceComponent(StudioEventBus bus) {
        this.bus = bus;
        this.editor = new MaredMultiLineEditBox(
            0, 0, 100, 100, com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent(), this::onEditorChanged);
        this.editor.setEditable(false);

        // 0.3.2: подсветка Mared-скриптов.
        this.editor.setSpanResolver(MaredScriptSpanResolver.INSTANCE);

        subs.add(bus.subscribe(StudioEvents.FileSelectedEvent.class,
            e -> openFile(e.fileName())));
        subs.add(bus.subscribe(StudioEvents.SaveRequestedEvent.class,
            e -> saveActive()));
        subs.add(bus.subscribe(StudioEvents.FileRenamedEvent.class,
            e -> onFileRenamed(e.oldName(), e.newName())));
        subs.add(bus.subscribe(StudioEvents.FileDeletedEvent.class,
            e -> onFileDeleted(e.fileName())));
    }

    @Override
    public void dispose() {
        for(DocumentSession s:host.documents())cancelQueuedSave(s);
        checkpoint(true);
        disposed = true;
        completion.dismiss(); minecraftChecks.clear();
        saveAsHandler = null;
        subs.dispose();
    }

    @Override
    public boolean focusable() { return true; }

    @Override
    public void onFocusGained() {
        if (editor.isEditable()) editor.setFocused(!searchFieldFocused());
    }

    @Override
    public void onFocusLost() {
        editor.setFocused(false);
        completion.dismiss();
        if (searchBox != null) searchBox.setFocused(false);
        if (replaceBox != null) replaceBox.setFocused(false);
    }

    // ============================================================
    //  Public API — совместимость
    // ============================================================

    public MaredMultiLineEditBox editor() { return editor; }

    public String currentFileName() {
        DocumentSession s = host.active();
        return s != null ? s.storageId : null;
    }

    public boolean isDirty() {
        DocumentSession s = host.active();
        return s != null && s.dirty;
    }

    public boolean isSaving() {
        DocumentSession s = host.active();
        return s != null && s.saveInFlight;
    }

    // ============================================================
    //  Public API — multi-doc
    // ============================================================

    public WorkspaceHost host() { return host; }

    public boolean hasAnyDirty() { return host.hasAnyDirty(); }

    public void openUntitled() {
        DocumentSession s = new DocumentSession();
        s.storageId = null;
        s.setInitialText("");
        host.add(s);
        activateSession(s);
    }

    /** Opening search results never saves another document or trusts an obsolete position. */
    public boolean openSearchHit(com.fixmer.genesis.technology.editor.DocumentSearch.Hit hit) {
        openFile(hit.path(), false);
        var document = host.active();
        if (document == null || !hit.path().equals(document.storageId)) return false;
        if (hit.line() >= 0) {
            String line = document.document.line(hit.line());
            int end = hit.column() + hit.matchedText().length();
            if (hit.line() >= document.document.lineCount() || hit.column() < 0 || end > line.length()
                    || !line.substring(hit.column(), end).equals(hit.matchedText())) {
                bus.publish(new StudioEvents.LogEvent(LogSettings.Level.WARN, "studio", MaredLang.get("mared.project_search.stale")));
                return false;
            }
            document.document.startSelection(hit.line(), hit.column());
            document.document.extendSelection(hit.line(), end);
            document.document.setCursor(hit.line(), end);
            document.view.ensureCursorVisible(editor.getWidth(), editor.getHeight(), document.document,
                net.minecraft.client.Minecraft.getInstance().font);
            document.view.resetBlink();
        }
        requestFocus(); editor.setFocused(true);
        return true;
    }

    public void openFile(String name) { openFile(name, true); }

    private void openFile(String name, boolean saveCurrent) {
        if (name == null || name.isEmpty()) return;

        DocumentSession existing = host.byStorageId(name);
        if (existing != null) {
            activateSession(existing);
            return;
        }

        DocumentSession active = host.active();
        if (saveCurrent && active != null && active.dirty && active.storageId != null && !active.saveAsInFlight) {
            boolean saved = saveSync(active);
            if (!saved) {
                active.pendingOpenFile = name;
                bus.publish(new StudioEvents.LogEvent(
                    LogSettings.Level.WARN, "studio",
                    "open blocked — could not save '" + active.storageId + "'"));
                return;
            }
        }

        String text;
        try { text=commandService.read(name); }
        catch(Exception error){bus.publish(new StudioEvents.LogEvent(LogSettings.Level.ERROR,"studio","Cannot read "+name+": "+error.getMessage()));return;}
        if (text == null) text = "";

        DocumentSession s = new DocumentSession();
        s.storageId = name;
        s.setInitialText(text);

        host.add(s);
        activateSession(s);

        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.INFO, "studio",
            "opened: " + name + " (" + text.length() + " chars)"));
        bus.publish(new StudioEvents.FileOpenedEvent(name));
    }

    public void save() {
        saveActive();
    }

    public boolean saveAll() {
        boolean allOk = true;
        for (DocumentSession s : host.documents()) {
            if (!s.dirty) continue;
            if (!s.canSave()) { requestSaveAs(s); return false; }
            if (!saveSync(s)) allOk = false;
        }
        return allOk;
    }

    public void closeActive() {
        DocumentSession s = host.active();
        if (s == null) return;
        requestClose(s);
    }

    private void requestClose(DocumentSession s) {
        if (s.dirty) {
            pendingCloseSession = s;
            bus.publish(new StudioEvents.RequestCloseDirtyDocumentEvent(
                s.storageId != null ? s.storageId : "(untitled)"));
            return;
        }
        doClose(s);
    }

    public void confirmPendingClose(CloseAction action) {
        DocumentSession s = pendingCloseSession;
        pendingCloseSession = null;
        if (s == null) return;

        switch (action) {
            case SAVE -> {
                if (!s.canSave()) {
                    s.closeAfterSaveAs = true;
                    requestSaveAs(s);
                    return;
                }
                boolean ok = saveSync(s);
                if (!ok) {
                    bus.publish(new StudioEvents.LogEvent(
                        LogSettings.Level.ERROR, "studio",
                        "save failed; close cancelled: " + s.storageId));
                    return;
                }
                doClose(s);
            }
            case DISCARD -> doClose(s);
            case CANCEL  -> { /* nothing */ }
        }
    }

    public void cancelPendingClose() {
        pendingCloseSession = null;
    }

    public enum CloseAction { SAVE, DISCARD, CANCEL }

    private void cancelQueuedSave(DocumentSession s) {
        ++s.saveSequence;
        if(s.saveInFlight && s.storageId!=null)try {
            com.fixmer.mared.commands.storage.MaredCommandStorage.invalidateWrite(s.storageId);
        }catch(Exception error){com.fixmer.mared.Mared.LOGGER.error("Cannot invalidate queued save",error);}
        s.saveInFlight=false;s.saveAsInFlight=false;s.inFlightText=null;s.pendingSave=false;
    }

    private void doClose(DocumentSession s) {
        cancelQueuedSave(s);
        host.remove(s);
        checkpoint(false);

        if (host.active() != null) {
            activateSession(host.active());
        } else {
            editor.setEditable(false);
            editor.attachDocument(null, null, null);
        }

        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.INFO, "studio",
            "closed document: " + (s.storageId != null ? s.storageId : "(untitled)")));
    }

    // ============================================================
    //  Внутреннее — save
    // ============================================================

    private void saveActive() {
        DocumentSession s = host.active();
        if (s == null) return;
        if (s.saveInFlight) {
            s.pendingSave = true;
            return;
        }
        if (!s.canSave()) { requestSaveAs(s); return; }
        saveAsync(s);
    }

    private void saveAsync(DocumentSession s) {
        if (!s.canSave() || disposed) return;
        if (!MaredThreading.isInitialized()) { saveSync(s); return; }
        final String content=s.currentText(), fileName=s.storageId;
        final long sequence=++s.saveSequence;
        try {
            var ticket=com.fixmer.mared.commands.storage.MaredCommandStorage.captureWrite(fileName,s.lastSavedText,s.saveInFlight?s.inFlightText:null);
            s.saveInFlight=true;
            s.inFlightText=content;
            MaredThreading.scheduler().submit("Save:"+fileName,
                ()->com.fixmer.mared.commands.storage.MaredCommandStorage.writeCommand(ticket,content),
                ok->{if(!disposed&&s.saveSequence==sequence)onSaveComplete(s,fileName,content,ok);},
                error->{if(!disposed&&s.saveSequence==sequence)onSaveError(s,fileName,error);},null);
        } catch (Exception error) { onSaveError(s,fileName,error); }
    }

    private boolean saveSync(DocumentSession s) {
        if(!s.canSave() || s.saveAsInFlight)return false;
        ++s.saveSequence;
        String content=s.currentText();
        try {
            var ticket=com.fixmer.mared.commands.storage.MaredCommandStorage.captureWrite(s.storageId,s.lastSavedText,s.saveInFlight?s.inFlightText:null);
            boolean ok=com.fixmer.mared.commands.storage.MaredCommandStorage.writeCommand(ticket,content);
            onSaveComplete(s,s.storageId,content,ok);return ok;
        }catch(Exception error){onSaveError(s,s.storageId,error);return false;}
    }

    private void onSaveComplete(DocumentSession s, String fileName,
                                String content, boolean ok) {
        if (!fileName.equals(s.storageId)) {
            s.saveInFlight = false;
            maybeFlushPending(s);
            return;
        }
        s.saveInFlight = false;
        s.inFlightText = null;

        if (ok) {
            s.lastSavedText = content;
            s.recomputeDirty();
            bus.publish(new StudioEvents.LogEvent(
                LogSettings.Level.INFO, "studio", "saved: " + fileName));
        } else {
            s.dirty = true;
            bus.publish(new StudioEvents.LogEvent(
                LogSettings.Level.ERROR, "studio", "FAILED to save: " + fileName));
        }
        maybeFlushPending(s);
    }

    private void onSaveError(DocumentSession s, String fileName, Throwable err) {
        s.saveInFlight = false;
        s.inFlightText = null;
        if (fileName.equals(s.storageId)) s.dirty = true;
        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.ERROR, "studio",
            "save error (" + fileName + "): " + err.getMessage()));
        maybeFlushPending(s);
    }

    private void maybeFlushPending(DocumentSession s) {
        if (s.pendingSave && s.canSave() && host.documents().contains(s) && !disposed) {
            s.pendingSave = false;
            saveAsync(s);
        } else if (s.pendingOpenFile != null && !s.dirty) {
            String next = s.pendingOpenFile;
            s.pendingOpenFile = null;
            openFile(next);
        }
    }

    // ============================================================
    //  Внутреннее — activation
    // ============================================================

    private void restoreDrafts() {
        try {
            for(var recovered:drafts.recover()){
                DocumentSession s=new DocumentSession();s.storageId=recovered.name();s.setInitialText(recovered.text());host.add(s);
                bus.publish(new StudioEvents.LogEvent(LogSettings.Level.WARN,"studio","Recovered draft of "+recovered.originalName()+" as "+recovered.name()));
            }
            if(host.active()!=null)activateSession(host.active());
            if(com.fixmer.mared.gui2.runtime.RuntimeProvider.isInstalled())
                com.fixmer.mared.gui2.runtime.RuntimeProvider.get().session().refreshExplorer();
        }catch(Exception error){bus.publish(new StudioEvents.LogEvent(LogSettings.Level.ERROR,"studio","Draft recovery failed; journal preserved: "+error.getMessage()));}
    }
    private void checkpoint(boolean flush) {
        if(disposed || !restored)return;
        var failure=drafts.takeFailure();
        if(failure!=null){lastCheckpoint=null;bus.publish(new StudioEvents.LogEvent(LogSettings.Level.ERROR,"studio","Background draft checkpoint failed; retrying: "+failure.getMessage()));}
        var snapshot=new java.util.ArrayList<com.fixmer.mared.technology.storage.WorkspaceDrafts.Draft>();
        for(DocumentSession s:host.documents())if(s.dirty)snapshot.add(new com.fixmer.mared.technology.storage.WorkspaceDrafts.Draft(s.recoveryId,s.storageId,s.currentText()));
        if(!flush&&snapshot.equals(lastCheckpoint))return;
        try {if(flush)drafts.flush(snapshot);else drafts.submit(snapshot);lastCheckpoint=java.util.List.copyOf(snapshot);}
        catch(Exception error){com.fixmer.mared.Mared.LOGGER.error("Draft checkpoint failed",error);bus.publish(new StudioEvents.LogEvent(LogSettings.Level.ERROR,"studio","Draft checkpoint failed: "+error.getMessage()));}
    }

    private void activateSession(DocumentSession s) {
        if (s == null) return;
        host.activate(s);
        editor.attachDocument(s.document, s.history, s.view);
        editor.setSpanResolver(spanResolver);
        editor.setEditable(true);
        completion.dismiss();
        editor.setFocused(isFocused() && !searchFieldFocused());
        searchMessage = "";
    }

    // ============================================================
    //  Lifecycle документа
    // ============================================================

    private void onFileRenamed(String oldName, String newName) {
        if (oldName == null || newName == null) return;

        DocumentSession s = host.byStorageId(oldName);
        if (s == null) return;

        ++s.saveSequence;
        if(s.saveInFlight && s.inFlightText!=null)try {
            if(commandService.read(newName).equals(s.inFlightText))s.lastSavedText=s.inFlightText;
        }catch(Exception error){com.fixmer.mared.Mared.LOGGER.warn("Cannot reconcile renamed document",error);}
        s.saveInFlight=false;s.saveAsInFlight=false;s.inFlightText=null;s.pendingSave=false;
        s.storageId = newName;
        s.exists = true;
        s.recomputeDirty();
        checkpoint(false);

        bus.publish(new StudioEvents.FileOpenedEvent(newName));
        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.INFO, "studio",
            "workspace renamed: " + oldName + " → " + newName));
    }

    private void onFileDeleted(String fileName) {
        if (fileName == null) return;

        DocumentSession s = host.byStorageId(fileName);
        if (s == null) return;

        ++s.saveSequence;
        s.saveInFlight=false;
        s.saveAsInFlight=false;
        s.inFlightText=null;
        s.pendingSave=false;
        s.exists = false;
        s.dirty = !s.currentText().isEmpty();
        checkpoint(false);

        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.WARN, "studio",
            "open file was deleted externally: " + fileName));
    }

    private void onEditorChanged() {
        completion.dismiss();
        DocumentSession s = host.active();
        if (s == null) return;
        s.recomputeDirty();
    }

    // ============================================================
    //  Layout
    // ============================================================

    @Override
    public void layout(MaredBounds bounds) {
        super.layout(bounds);
        int ex = bounds.x() + 2;
        int ey = bounds.y() + HEADER_H + searchHeight() + 2;
        int ew = Math.max(20, bounds.width() - 4);
        int eh = Math.max(1, bounds.height() - HEADER_H - searchHeight() - STATUS_H - 4);
        if (searchBox != null) {
            searchBox.setX(bounds.x() + 6); searchBox.setY(bounds.y() + HEADER_H + 3);
            searchBox.setWidth(Math.max(1, bounds.width() - 102)); searchBox.setHeight(16);
        }
        if (replaceBox != null) {
            replaceBox.setX(bounds.x() + 6); replaceBox.setY(bounds.y() + HEADER_H + SEARCH_H + 3);
            replaceBox.setWidth(Math.max(1, bounds.width() - 82)); replaceBox.setHeight(16);
        }
        if (bounds.width() < 160 || bounds.height() < 100) {
            if (searchBox != null) searchBox.setFocused(false);
            if (replaceBox != null) replaceBox.setFocused(false);
        }
        editor.setX(ex);
        editor.setY(ey);
        editor.setWidth(ew);
        editor.setHeight(eh);
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    protected void safeRender(MaredRenderContext context) {
        if(!restored){restored=true;restoreDrafts();}
        if(System.nanoTime()>=nextCheckpoint){nextCheckpoint=System.nanoTime()+2_000_000_000L;checkpoint(false);}
        editor.setAccentColor(com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent());
        completion.reconcile(host.active());
        advanceMinecraftChecks();
        GuiGraphics g = context.graphics();
        Font font = context.font();

        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
            com.fixmer.mared.technology.editor.GenesisEditorVisuals.sunken());

        drawTabBar(g, font);

        g.fill(bounds.x(), bounds.y() + HEADER_H - 1,
               bounds.right(), bounds.y() + HEADER_H, com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge());

        if (host.active() == null) {
            String hint = host.isEmpty()
                ? com.fixmer.mared.technology.editor.EditorText.translate("Workspace — no files open")
                : com.fixmer.mared.technology.editor.EditorText.translate("Workspace — no active document");
            g.drawString(font, hint, bounds.x() + 8,
                bounds.y() + HEADER_H + 8,
                com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
            return;
        }

        editor.render(g, lastMouseX, lastMouseY, 0f);
        drawEditorTools(g, font);
        var active = host.active();
        if (editor.isFocused() && active != null) completion.render(g, font,
            new MaredBounds(editor.getX(), editor.getY(), editor.getWidth(), editor.getHeight()),
            active.view.caretX(), active.view.caretY());
    }

    private void drawTabBar(GuiGraphics g, Font font) {
        int availW = bounds.width();
        int n = host.count();

        if (n == 0) {
            g.drawString(font, com.fixmer.mared.technology.editor.EditorText.translate("Workspace — no files"),
                bounds.x() + 8, bounds.y() + 5, com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
            tabXCache = new int[0];
            tabWCache = new int[0];
            tabBarX = bounds.x();
            tabBarW = availW;
            return;
        }

        int totalGaps = (n - 1) * TAB_GAP;
        int spaceForTabs = Math.max(1, availW - totalGaps);

        int[] natural = new int[n];
        int naturalTotal = 0;
        for (int i = 0; i < n; i++) {
            DocumentSession s = host.byIndex(i);
            String label = tabLabel(s);
            int extra = s.dirty ? font.width(" ●") : 0;
            int contentW = font.width(label) + extra
                + CLOSE_BTN_W + CLOSE_BTN_PAD * 2;
            int naturalW = Math.max(TAB_MIN_W,
                Math.min(TAB_MAX_W, contentW));
            natural[i] = naturalW;
            naturalTotal += naturalW;
        }

        int[] widths = new int[n];
        if (naturalTotal <= spaceForTabs) {
            System.arraycopy(natural, 0, widths, 0, n);
        } else {
            int even = Math.max(TAB_MIN_W, spaceForTabs / n);
            for (int i = 0; i < n; i++) widths[i] = even;
        }

        int x = bounds.x();
        int y = bounds.y();
        int h = HEADER_H - 1;
        int activeIdx = host.activeIndex();

        for (int i = 0; i < n; i++) {
            DocumentSession s = host.byIndex(i);
            int w = widths[i];
            boolean active = (i == activeIdx);

            int bg = active ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.sunken();
            int textColor = active ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.text() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim();
            int accent = active ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge();

            g.fill(x, y, x + w, y + h, bg);
            if (active) {
                g.fill(x, y + h - 2, x + w, y + h, accent);
            }
            g.renderOutline(x, y, w, h, com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge());

            String label = tabLabel(s);
            int closeX = x + w - CLOSE_BTN_W - CLOSE_BTN_PAD;
            int labelMaxW = closeX - x - 6;
            String drawn = ellipsize(font, label, labelMaxW);
            g.drawString(font, drawn, x + 6, y + 5, textColor, false);

            if (s.dirty) {
                int dotX = x + 6 + font.width(drawn) + 3;
                g.drawString(font, "●", dotX, y + 5, com.fixmer.mared.technology.editor.GenesisEditorVisuals.warn(), false);
            }

            g.drawString(font, "×", closeX, y + 5,
                active ? com.fixmer.mared.technology.editor.GenesisEditorVisuals.danger() : com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);

            x += w + TAB_GAP;
        }

        tabXCache = new int[n];
        tabWCache = new int[n];
        int cx = bounds.x();
        for (int i = 0; i < n; i++) {
            tabXCache[i] = cx;
            tabWCache[i] = widths[i];
            cx += widths[i] + TAB_GAP;
        }
        tabBarX = bounds.x();
        tabBarW = availW;
    }

    private String tabLabel(DocumentSession s) {
        if (s.storageId != null) return s.storageId;
        return "(untitled)";
    }

    private static String ellipsize(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        int ell = font.width("…");
        if (maxW <= ell) return "";
        return font.plainSubstrByWidth(s, maxW - ell) + "…";
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = (int) mouseX;
        lastMouseY = (int) mouseY;
        editor.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        completion.reconcile(host.active());
        if (completion.click(mx, my, button)) return true;
        if (!bounds.contains(mx, my)) return false;

        if (my >= bounds.y() && my < bounds.y() + HEADER_H) {
            int idx = tabIndexAt(mx, my);
            if (idx < 0) return true;

            DocumentSession s = host.byIndex(idx);
            if (s == null) return true;

            if (isOverCloseButton(mx, idx)) {
                requestClose(s);
                return true;
            }
            if (button == 2) {
                requestClose(s);
                return true;
            }
            if (button == 0) {
                if (idx != host.activeIndex()) {
                    activateSession(s);
                }
                return true;
            }
            return true;
        }

        if (host.active() != null && my >= bounds.bottom() - STATUS_H) {
            if (button == 0) jumpToDiagnostic();
            return true;
        }
        if (searchOpen && my < bounds.y() + HEADER_H + searchHeight()) {
            if (button != 0 || bounds.width() < 160 || bounds.height() < 100) return true;
            requestFocus(); editor.setFocused(false);
            ensureSearch();
            if (replaceOpen && my >= bounds.y() + HEADER_H + SEARCH_H) {
                if (mx >= bounds.right() - 70) {
                    if (mx < bounds.right() - 48) replaceOne(); else replaceAll();
                } else {
                    searchBox.setFocused(false); replaceBox.setFocused(true); replaceBox.mouseClicked(mx, my, button);
                }
            } else if (mx >= bounds.right() - 90) {
                int action = (int) mx - (bounds.right() - 90);
                if (action < 17) findNext(true);
                else if (action < 34) findNext(false);
                else if (action < 55) { searchCase = !searchCase; searchMessage = ""; }
                else if (action < 75) { replaceOpen = !replaceOpen; replaceBox.setFocused(false); layout(bounds); }
                else closeSearch();
            } else { replaceBox.setFocused(false); searchBox.setFocused(true); searchBox.mouseClicked(mx, my, button); }
            return true;
        }
        if (editor.isMouseOver(mx, my)) {
            if (searchBox != null) searchBox.setFocused(false);
        if (replaceBox != null) replaceBox.setFocused(false);
            requestFocus(); editor.setFocused(true);
            capturePointer(button);
            return editor.mouseClicked(mx, my, button);
        }
        return true;
    }

    private int tabIndexAt(double mx, double my) {
        if (my < bounds.y() || my >= bounds.y() + HEADER_H) return -1;
        for (int i = 0; i < tabXCache.length; i++) {
            int x = tabXCache[i];
            int w = tabWCache[i];
            if (mx >= x && mx < x + w) return i;
        }
        return -1;
    }

    private boolean isOverCloseButton(double mx, int idx) {
        if (idx < 0 || idx >= tabXCache.length) return false;
        int x = tabXCache[idx];
        int w = tabWCache[idx];
        int closeX = x + w - CLOSE_BTN_W - CLOSE_BTN_PAD;
        return mx >= closeX && mx < closeX + CLOSE_BTN_W;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button,
                                double dragX, double dragY) {
        if (searchOpen && searchFieldFocused()) return (replaceBox.isFocused() ? replaceBox : searchBox).mouseDragged(mx, my, button, dragX, dragY);
        return editor.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean handled = editor.mouseReleased(mx, my, button);
        if (hasPointerCapture(button)) releasePointer();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                 double scrollX, double scrollY) {
        completion.reconcile(host.active());
        if (completion.scroll(mx, my, scrollY)) return true;
        if (!editor.isMouseOver(mx, my)) return false;
        return editor.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        completion.reconcile(host.active());
        if (completion.key(keyCode, modifiers)) return true;
        boolean ctrl = (modifiers & 2) != 0;
        if (keyCode == 256 && searchOpen) { closeSearch(); return true; }
        if (searchOpen && searchFieldFocused()) {
            if (keyCode == 258 && replaceOpen && !ctrl) {
                boolean replacing = replaceBox.isFocused();
                replaceBox.setFocused(!replacing); searchBox.setFocused(replacing); return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (replaceOpen && ctrl) replaceAll();
                else if (replaceOpen && replaceBox.isFocused()) replaceOne();
                else findNext((modifiers & 1) != 0);
                return true;
            }
            // Undo/redo belong to the document only after focus returns to the editor.
            if (ctrl && (keyCode == 90 || keyCode == 89)) return true;
            if ((replaceBox.isFocused() ? replaceBox : searchBox).keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (ctrl && keyCode == 87) { // Ctrl+W — закрыть активный
            closeActive();
            return true;
        }
        if (ctrl && keyCode == 258) { // Ctrl+Tab
            boolean shift = (modifiers & 1) != 0;
            if (shift) host.activatePrev();
            else       host.activateNext();
            DocumentSession s = host.active();
            if (s != null) activateSession(s);
            return true;
        }

        if (editor.isFocused()) {
            return editor.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchOpen && searchFieldFocused()) return (replaceBox.isFocused() ? replaceBox : searchBox).charTyped(codePoint, modifiers);
        if (editor.isFocused()) return editor.charTyped(codePoint, modifiers);
        return false;
    }

    // ============================================================
    //  Narration (0.3.2 accessibility)
    // ============================================================

    @Override
    public Component narrationText() {
        DocumentSession s = host.active();
        if (s == null) {
            return Component.literal(MaredLang.get(host.isEmpty()
                ? "mared.narration.workspace.empty"
                : "mared.narration.workspace.empty_active"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(MaredLang.format("mared.narration.workspace.title",
            s.title()));

        if (s.dirty) {
            sb.append(' ').append(MaredLang.get(
                "mared.narration.workspace.dirty"));
        }
        if (!s.exists) {
            sb.append(' ').append(MaredLang.get(
                "mared.narration.workspace.deleted"));
        }

        int lineCount = s.document.lineCount();
        int line = s.document.cursorLine() + 1;
        int col = s.document.cursorCol() + 1;

        sb.append(' ').append(MaredLang.format(
            "mared.narration.workspace.position", lineCount, line, col));

        int total = host.count();
        if (total > 1) {
            sb.append(' ').append(MaredLang.format(
                "mared.narration.workspace.multi", total));
        }
        return Component.literal(sb.toString());
    }
}