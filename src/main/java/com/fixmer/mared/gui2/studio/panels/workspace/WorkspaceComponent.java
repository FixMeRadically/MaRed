package com.fixmer.mared.gui2.studio.panels.workspace;

import com.fixmer.mared.gui2.framework.components.editor.MaredMultiLineEditBox;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredBounds;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;
import com.fixmer.mared.services.command.CommandFileService;
import com.fixmer.mared.services.threading.MaredThreading;
import com.fixmer.mared.services.threading.TaskScheduler;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.1: Ctrl+S больше не обрабатывается здесь — это глобальный
 * shortcut StudioActions.FILE_SAVE, который публикует
 * SaveRequestedEvent. Workspace реагирует на событие.
 */
public final class WorkspaceComponent extends MaredComponent implements Disposable {

    private static final int HEADER_H = 18;

    private final StudioEventBus bus;
    private final MaredMultiLineEditBox editor;
    private final SubscriptionGroup subs = new SubscriptionGroup();
    private final CommandFileService commandService = new CommandFileService();

    private String currentFileName = null;
    private String lastSavedText = "";
    private boolean dirty = false;

    private boolean saveInFlight = false;
    private boolean pendingSave  = false;
    private String pendingOpenFile = null;

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    public WorkspaceComponent(StudioEventBus bus) {
        this.bus = bus;
        this.editor = new MaredMultiLineEditBox(
            0, 0, 100, 100, 0xFF55AAFF, this::onEditorChanged);
        this.editor.setEditable(false);

        subs.add(bus.subscribe(StudioEvents.FileSelectedEvent.class,
            e -> openFile(e.fileName())));
        subs.add(bus.subscribe(StudioEvents.SaveRequestedEvent.class,
            e -> save()));
        subs.add(bus.subscribe(StudioEvents.FileRenamedEvent.class,
            e -> onFileRenamed(e.oldName(), e.newName())));
        subs.add(bus.subscribe(StudioEvents.FileDeletedEvent.class,
            e -> onFileDeleted(e.fileName())));
    }

    @Override
    public void dispose() { subs.dispose(); }

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

    public MaredMultiLineEditBox editor() { return editor; }
    public String currentFileName() { return currentFileName; }
    public boolean isDirty() { return dirty; }
    public boolean isSaving() { return saveInFlight; }

    public void openFile(String name) {
        if (name == null || name.isEmpty()) return;
        if (name.equals(currentFileName)) return;

        if (dirty && currentFileName != null) {
            boolean saved = saveSync();
            if (!saved) {
                pendingOpenFile = name;
                bus.publish(new StudioEvents.LogEvent(
                    com.fixmer.mared.services.logging.LogSettings.Level.WARN,
                    "studio",
                    "open blocked — could not save '" + currentFileName + "'"));
                return;
            }
        }

        String text = commandService.read(name);
        if (text == null) text = "";

        currentFileName = name;
        lastSavedText = text;
        dirty = false;
        pendingOpenFile = null;
        editor.setValue(text);
        editor.setEditable(true);
        editor.setFocused(isFocused());

        bus.publish(new StudioEvents.LogEvent(
            com.fixmer.mared.services.logging.LogSettings.Level.INFO,
            "studio",
            "opened: " + name + " (" + text.length() + " chars)"));
        bus.publish(new StudioEvents.FileOpenedEvent(name));
    }

    public void save() {
        if (currentFileName == null) return;

        if (saveInFlight) {
            pendingSave = true;
            return;
        }

        final String content = editor.getValue();
        final String fileName = currentFileName;

        if (!MaredThreading.isInitialized()) {
            saveSync();
            return;
        }

        saveInFlight = true;
        TaskScheduler scheduler = MaredThreading.scheduler();
        scheduler.submit("Save:" + fileName,
            () -> commandService.write(fileName, content),
            ok -> onSaveComplete(fileName, content, ok),
            err -> onSaveError(fileName, err),
            null
        );
    }

    private boolean saveSync() {
        if (currentFileName == null) return true;
        String content = editor.getValue();
        boolean ok = commandService.write(currentFileName, content);
        onSaveComplete(currentFileName, content, ok);
        return ok;
    }

    private void onSaveComplete(String fileName, String content, boolean ok) {
        if (!fileName.equals(currentFileName)) {
            saveInFlight = false;
            maybeFlushPending();
            return;
        }
        saveInFlight = false;

        if (ok) {
            lastSavedText = content;
            dirty = !editor.getValue().equals(lastSavedText);
            bus.publish(new StudioEvents.LogEvent(
                com.fixmer.mared.services.logging.LogSettings.Level.INFO,
                "studio", "saved: " + fileName));
        } else {
            dirty = true;
            bus.publish(new StudioEvents.LogEvent(
                com.fixmer.mared.services.logging.LogSettings.Level.ERROR,
                "studio", "FAILED to save: " + fileName));
        }
        maybeFlushPending();
    }

    private void onSaveError(String fileName, Throwable err) {
        saveInFlight = false;
        if (fileName.equals(currentFileName)) dirty = true;
        bus.publish(new StudioEvents.LogEvent(
            com.fixmer.mared.services.logging.LogSettings.Level.ERROR,
            "studio", "save error (" + fileName + "): " + err.getMessage()));
        maybeFlushPending();
    }

    private void maybeFlushPending() {
        if (pendingSave) {
            pendingSave = false;
            save();
        } else if (pendingOpenFile != null && !dirty) {
            String next = pendingOpenFile;
            pendingOpenFile = null;
            openFile(next);
        }
    }

    private void onFileRenamed(String oldName, String newName) {
        if (oldName == null || !oldName.equals(currentFileName)) return;
        currentFileName = newName;
        bus.publish(new StudioEvents.FileOpenedEvent(newName));
        bus.publish(new StudioEvents.LogEvent(
            com.fixmer.mared.services.logging.LogSettings.Level.INFO,
            "studio", "workspace now editing: " + newName));
    }

    private void onFileDeleted(String fileName) {
        if (fileName == null || !fileName.equals(currentFileName)) return;
        currentFileName = null;
        dirty = false;
        lastSavedText = "";
        pendingOpenFile = null;
        editor.setEditable(false);
        editor.setFocused(false);
        bus.publish(new StudioEvents.LogEvent(
            com.fixmer.mared.services.logging.LogSettings.Level.WARN,
            "studio", "open file was deleted externally: " + fileName));
    }

    private void onEditorChanged() {
        if (currentFileName == null) return;
        dirty = !editor.getValue().equals(lastSavedText);
    }

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

    @Override
    protected void safeRender(MaredRenderContext context) {
        GuiGraphics g = context.graphics();
        Font font = context.font();

        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF0E0E16);

        String title;
        if (currentFileName == null) {
            title = "Workspace — no file selected";
        } else {
            title = currentFileName
                + (dirty ? "  ●" : "")
                + (saveInFlight ? "…" : "");
        }

        g.drawString(font, title, bounds.x() + 6, bounds.y() + 5,
            currentFileName == null ? ThemeColors.muted() : ThemeColors.text(),
            false);

        g.fill(bounds.x(), bounds.y() + HEADER_H - 1,
               bounds.right(), bounds.y() + HEADER_H, 0xFF222233);

        editor.render(g, lastMouseX, lastMouseY, 0f);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = (int) mouseX;
        lastMouseY = (int) mouseY;
        editor.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (editor.isMouseOver(mx, my)) {
            requestFocus();
            capturePointer(button);
            return editor.mouseClicked(mx, my, button);
        }
        return true;
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
        // Ctrl+S перехватывается централизованно в MaredStudioScreen через
        // EditorActionRegistry (StudioActions.FILE_SAVE).
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
}