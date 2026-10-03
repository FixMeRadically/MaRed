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
    private final SubscriptionGroup subs = new SubscriptionGroup();
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
            0, 0, 100, 100, 0xFF55AAFF, this::onEditorChanged);
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
        subs.dispose();
    }

    @Override
    public boolean focusable() { return true; }

    @Override
    public void onFocusGained() {
        if (editor.isEditable()) editor.setFocused(true);
    }

    @Override
    public void onFocusLost() {
        editor.setFocused(false);
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

    public void openFile(String name) {
        if (name == null || name.isEmpty()) return;

        DocumentSession existing = host.byStorageId(name);
        if (existing != null) {
            activateSession(existing);
            return;
        }

        DocumentSession active = host.active();
        if (active != null && active.dirty && active.storageId != null) {
            boolean saved = saveSync(active);
            if (!saved) {
                active.pendingOpenFile = name;
                bus.publish(new StudioEvents.LogEvent(
                    LogSettings.Level.WARN, "studio",
                    "open blocked — could not save '" + active.storageId + "'"));
                return;
            }
        }

        String text = commandService.read(name);
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
                if (s.storageId == null) {
                    pendingCloseSession = s;
                    bus.publish(new StudioEvents.LogEvent(
                        LogSettings.Level.WARN, "studio",
                        "untitled save not supported — closing cancelled"));
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

    private void doClose(DocumentSession s) {
        host.remove(s);

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
        if (s.storageId == null) {
            bus.publish(new StudioEvents.LogEvent(
                LogSettings.Level.WARN, "studio",
                "cannot save untitled document"));
            return;
        }
        if (s.saveInFlight) {
            s.pendingSave = true;
            return;
        }
        saveAsync(s);
    }

    private void saveAsync(DocumentSession s) {
        final String content = s.currentText();
        final String fileName = s.storageId;

        if (!MaredThreading.isInitialized()) {
            saveSync(s);
            return;
        }

        s.saveInFlight = true;
        TaskScheduler scheduler = MaredThreading.scheduler();
        scheduler.submit("Save:" + fileName,
            () -> commandService.write(fileName, content),
            ok -> onSaveComplete(s, fileName, content, ok),
            err -> onSaveError(s, fileName, err),
            null
        );
    }

    private boolean saveSync(DocumentSession s) {
        if (s.storageId == null) return true;
        String content = s.currentText();
        boolean ok = commandService.write(s.storageId, content);
        onSaveComplete(s, s.storageId, content, ok);
        return ok;
    }

    private void onSaveComplete(DocumentSession s, String fileName,
                                String content, boolean ok) {
        if (!fileName.equals(s.storageId)) {
            s.saveInFlight = false;
            maybeFlushPending(s);
            return;
        }
        s.saveInFlight = false;

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
        if (fileName.equals(s.storageId)) s.dirty = true;
        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.ERROR, "studio",
            "save error (" + fileName + "): " + err.getMessage()));
        maybeFlushPending(s);
    }

    private void maybeFlushPending(DocumentSession s) {
        if (s.pendingSave) {
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

    private void activateSession(DocumentSession s) {
        if (s == null) return;
        host.activate(s);
        editor.attachDocument(s.document, s.history, s.view);
        editor.setEditable(true);
        editor.setFocused(isFocused());
    }

    // ============================================================
    //  Lifecycle документа
    // ============================================================

    private void onFileRenamed(String oldName, String newName) {
        if (oldName == null || newName == null) return;

        DocumentSession s = host.byStorageId(oldName);
        if (s == null) return;

        s.storageId = newName;
        s.exists = true;

        bus.publish(new StudioEvents.FileOpenedEvent(newName));
        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.INFO, "studio",
            "workspace renamed: " + oldName + " → " + newName));
    }

    private void onFileDeleted(String fileName) {
        if (fileName == null) return;

        DocumentSession s = host.byStorageId(fileName);
        if (s == null) return;

        s.exists = false;
        s.dirty = false;
        s.lastSavedText = s.currentText();

        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.WARN, "studio",
            "open file was deleted externally: " + fileName));
    }

    private void onEditorChanged() {
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
        int ey = bounds.y() + HEADER_H + 2;
        int ew = Math.max(20, bounds.width() - 4);
        int eh = Math.max(20, bounds.height() - HEADER_H - 4);
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
        GuiGraphics g = context.graphics();
        Font font = context.font();

        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
            0xFF0E0E16);

        drawTabBar(g, font);

        g.fill(bounds.x(), bounds.y() + HEADER_H - 1,
               bounds.right(), bounds.y() + HEADER_H, 0xFF222233);

        if (host.active() == null) {
            String hint = host.isEmpty()
                ? "Workspace — no files open"
                : "Workspace — no active document";
            g.drawString(font, hint, bounds.x() + 8,
                bounds.y() + HEADER_H + 8,
                ThemeColors.muted(), false);
            return;
        }

        editor.render(g, lastMouseX, lastMouseY, 0f);
    }

    private void drawTabBar(GuiGraphics g, Font font) {
        int availW = bounds.width();
        int n = host.count();

        if (n == 0) {
            g.drawString(font, "Workspace — no files",
                bounds.x() + 8, bounds.y() + 5, ThemeColors.muted(), false);
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

            int bg = active ? 0xFF1A1A24 : 0xFF0E0E16;
            int textColor = active ? ThemeColors.text() : ThemeColors.muted();
            int accent = active ? 0xFF55AAFF : 0xFF333344;

            g.fill(x, y, x + w, y + h, bg);
            if (active) {
                g.fill(x, y + h - 2, x + w, y + h, accent);
            }
            g.renderOutline(x, y, w, h, 0xFF222233);

            String label = tabLabel(s);
            int closeX = x + w - CLOSE_BTN_W - CLOSE_BTN_PAD;
            int labelMaxW = closeX - x - 6;
            String drawn = ellipsize(font, label, labelMaxW);
            g.drawString(font, drawn, x + 6, y + 5, textColor, false);

            if (s.dirty) {
                int dotX = x + 6 + font.width(drawn) + 3;
                g.drawString(font, "●", dotX, y + 5, 0xFFFFAA00, false);
            }

            g.drawString(font, "×", closeX, y + 5,
                active ? 0xFFFF6666 : 0xFF666677, false);

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

        if (editor.isMouseOver(mx, my)) {
            requestFocus();
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
        if (!editor.isMouseOver(mx, my)) return false;
        return editor.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & 2) != 0;
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