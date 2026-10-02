package com.fixmer.mared.gui2.studio.panels.workspace;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.gui2.framework.components.editor.MaredMultiLineEditBox;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;
import com.fixmer.mared.services.command.CommandFileService;
import com.fixmer.mared.services.threading.CancellationToken;
import com.fixmer.mared.services.threading.MaredThreading;
import com.fixmer.mared.services.threading.TaskScheduler;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Компонент редактирования одного файла.
 *
 * 0.3.0 (Phase F4): не ходит в MaredCommandStorage напрямую —
 * read/write через CommandFileService (services.command).
 */
public final class WorkspaceComponent extends MaredComponent implements Disposable {

    private static final int HEADER_H = 18;
    private static final boolean DEBUG_SAVE = false;

    private final StudioEventBus bus;
    private final MaredMultiLineEditBox editor;
    private final SubscriptionGroup subs = new SubscriptionGroup();
    private final CommandFileService commandService = new CommandFileService();

    private String currentFileName = null;
    private String lastSavedText = "";
    private boolean dirty = false;
    private boolean saving = false;
    private CancellationToken currentSaveToken = null;

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
    }

    @Override
    public void dispose() {
        if (currentSaveToken != null) currentSaveToken.cancel();
        subs.dispose();
    }

    public MaredMultiLineEditBox editor() { return editor; }
    public String currentFileName() { return currentFileName; }
    public boolean isDirty() { return dirty; }
    public boolean isSaving() { return saving; }

    public void openFile(String name) {
        if (name == null || name.isEmpty()) return;
        if (name.equals(currentFileName)) return;
        if (dirty && currentFileName != null) saveSync();

        String text = commandService.read(name);
        if (text == null) text = "";

        currentFileName = name;
        lastSavedText = text;
        dirty = false;
        editor.setValue(text);
        editor.setEditable(true);
        editor.setFocused(true);

        bus.publish(new StudioEvents.LogEvent(
            "[studio] opened: " + name + " (" + text.length() + " chars)"));
        bus.publish(new StudioEvents.FileOpenedEvent(name));
    }

    public void save() {
        if (currentFileName == null) return;
        final String content = editor.getValue();
        final String fileName = currentFileName;

        if (currentSaveToken != null) {
            currentSaveToken.cancel();
            currentSaveToken = null;
        }

        if (!MaredThreading.isInitialized()) { saveSync(); return; }

        final CancellationToken token = new CancellationToken();
        currentSaveToken = token;
        saving = true;

        TaskScheduler scheduler = MaredThreading.scheduler();
        scheduler.submit("Save:" + fileName,
            () -> {
                token.throwIfCancelled();
                boolean ok = commandService.write(fileName, content);
                token.throwIfCancelled();
                return ok;
            },
            ok -> onSaveComplete(fileName, content, ok),
            err -> onSaveError(fileName, err),
            token
        );
    }

    private void saveSync() {
        if (currentFileName == null) return;
        String content = editor.getValue();
        boolean ok = commandService.write(currentFileName, content);
        onSaveComplete(currentFileName, content, ok);
    }

    private void onSaveComplete(String fileName, String content, boolean ok) {
        if (!fileName.equals(currentFileName)) { saving = false; return; }
        saving = false;
        currentSaveToken = null;
        if (ok) {
            lastSavedText = content;
            dirty = false;
            bus.publish(new StudioEvents.LogEvent("[studio] saved: " + fileName));
        } else {
            bus.publish(new StudioEvents.LogEvent(
                "[studio] FAILED to save: " + fileName));
        }
    }

    private void onSaveError(String fileName, Throwable err) {
        saving = false;
        currentSaveToken = null;
        bus.publish(new StudioEvents.LogEvent(
            "[studio] save error (" + fileName + "): " + err.getMessage()));
    }

    private void onEditorChanged() {
        if (currentFileName == null) return;
        dirty = !editor.getValue().equals(lastSavedText);
    }

    @Override
    public void layout(com.fixmer.mared.gui2.framework.core.MaredBounds bounds) {
        super.layout(bounds);
        int ex = bounds.x() + 2;
        int ey = bounds.y() + HEADER_H + 2;
        int ew = Math.max(20, bounds.width() - 4);
        int eh = Math.max(20, bounds.height() - HEADER_H - 4);
        editor.setX(ex); editor.setY(ey);
        editor.setWidth(ew); editor.setHeight(eh);
    }

    @Override
    protected void safeRender(MaredRenderContext context) {
        GuiGraphics g = context.graphics();
        Font font = context.font();

        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF0E0E16);

        String title = currentFileName == null
            ? "Workspace — no file selected"
            : currentFileName + (dirty ? "  ●" : "") + (saving ? "…" : "");

        g.drawString(font, title, bounds.x() + 6, bounds.y() + 5,
            currentFileName == null ? ThemeColors.muted() : ThemeColors.text(), false);

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
            editor.setFocused(true);
            return editor.mouseClicked(mx, my, button);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        return editor.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return editor.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!editor.isMouseOver(mx, my)) return false;
        return editor.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & 2) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) { save(); return true; }
        if (editor.isFocused()) return editor.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editor.isFocused()) return editor.charTyped(codePoint, modifiers);
        return false;
    }
}