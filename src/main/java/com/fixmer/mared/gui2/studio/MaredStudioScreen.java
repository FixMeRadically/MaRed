package com.fixmer.mared.gui2.studio;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.runner.MaredFileRunner;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.gui2.settings.MaredSettingsScreen;
import com.fixmer.mared.gui2.docking.layout.DockLayoutCalculator;
import com.fixmer.mared.gui2.docking.render.DockRenderer;
import com.fixmer.mared.gui2.framework.components.overlay.MaredConfirmDialog;
import com.fixmer.mared.gui2.framework.components.overlay.MaredContextMenu;
import com.fixmer.mared.gui2.framework.components.overlay.MaredNameDialog;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;
import com.fixmer.mared.services.command.CommandFileService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MaredStudioScreen extends Screen implements Disposable {

    private final MaredStudioController controller;
    private final CommandFileService commandService = new CommandFileService();

    private SubscriptionGroup screenSubs = new SubscriptionGroup();
    private boolean closing = false;

    public MaredStudioScreen() {
        super(Component.literal("MaRed Studio"));
        controller = new MaredStudioController();
    }

    @Override
    public void dispose() { screenSubs.dispose(); }

    @Override
    protected void init() {
        screenSubs.dispose();
        screenSubs = new SubscriptionGroup();

        controller.initialize();

        controller.topBarPanel().topBar().setActions(
            this::onNewFile, this::onSaveFile, this::onRunFile,
            this::onSettings, this::onClose);

        subscribeBusEvents();

        DockLayoutCalculator.calculate(
            controller.dockManager().layout(), this.width, this.height);
    }

    private void subscribeBusEvents() {
        var bus = controller.bus();
        screenSubs.add(bus.subscribe(StudioEvents.RequestNewFileEvent.class,
            e -> onNewFile()));
        screenSubs.add(bus.subscribe(StudioEvents.RequestRenameFileEvent.class,
            e -> onRenameFile(e.fileName())));
        screenSubs.add(bus.subscribe(StudioEvents.RequestDuplicateFileEvent.class,
            e -> onDuplicateFile(e.fileName())));
        screenSubs.add(bus.subscribe(StudioEvents.RequestDeleteFileEvent.class,
            e -> onDeleteFile(e.fileName())));
        screenSubs.add(bus.subscribe(StudioEvents.RequestReloadPersistentEvent.class,
            e -> publish(commandService.reloadPersistent())));
    }

    @Override
    public void onClose() {
        closing = true;
        MaredContextMenu.close();
        super.onClose();
    }

    @Override
    public void removed() {
        screenSubs.dispose();
        if (closing) controller.shutdown();
    }

    private void onNewFile() {
        Minecraft.getInstance().setScreen(new MaredNameDialog(
            this,
            MaredLang.get("mared.dialog.new_command_file"),
            (name, persistent) -> {
                publish(commandService.create(name, persistent));
                refreshExplorer();
            },
            0xFFFF55FF, true,
            name -> MaredCommandStorage.listCommands().contains(name)
                ? MaredLang.format("mared.dialog.error.exists", name) : null
        ));
    }

    private void onRenameFile(String oldName) {
        Minecraft.getInstance().setScreen(new MaredNameDialog(
            this, "Rename: " + oldName,
            (newName, persistent) -> {
                publish(commandService.rename(oldName, newName));
                refreshExplorer();
            },
            0xFFAA00, true,
            name -> MaredCommandStorage.listCommands().contains(name)
                ? "File already exists: " + name : null
        ));
    }

    private void onDuplicateFile(String fileName) {
        publish(commandService.duplicate(fileName));
        refreshExplorer();
    }

    private void onDeleteFile(String fileName) {
        if (!commandService.exists(fileName)) {
            log("[studio] delete skipped: file does not exist: " + fileName);
            refreshExplorer();
            return;
        }
        boolean wasPersistent = commandService.isPersistent(fileName);
        String title = wasPersistent
            ? MaredLang.format("mared.dialog.delete_persistent_title", fileName)
            : MaredLang.format("mared.dialog.delete_title", "file", fileName);
        String message = wasPersistent
            ? MaredLang.get("mared.dialog.delete_persistent_message")
            : MaredLang.format("mared.dialog.delete_message", fileName);

        Minecraft.getInstance().setScreen(new MaredConfirmDialog(
            this, title, message,
            () -> { publish(commandService.delete(fileName)); refreshExplorer(); },
            wasPersistent
        ));
    }

    private void onSaveFile() {
        controller.bus().publish(new StudioEvents.SaveRequestedEvent());
    }

    private void onRunFile() {
        String text = controller.workspacePanel().workspaceComponent().editor().getValue();
        if (text == null || text.trim().isEmpty()) { log("[run] file is empty"); return; }
        var ws = controller.workspacePanel().workspaceComponent();
        if (ws.currentFileName() != null && ws.isDirty()) ws.save();
        MaredFileRunner.run(text, this::log);
    }

    private void onSettings() {
        Minecraft.getInstance().setScreen(new MaredSettingsScreen(this));
    }

    private void publish(CommandFileService.Result r) {
        if (r == null || r.logLine() == null) return;
        log(r.logLine());
    }

    private void refreshExplorer() {
        var ex = controller.explorerPanel();
        if (ex != null) ex.explorerComponent().reload();
    }

    private void log(String line) {
        controller.bus().publish(new StudioEvents.LogEvent(line));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MaredThemeRegistry.active().bgScreen);

        super.render(graphics, mouseX, mouseY, partialTick);

        MaredRenderContext context = new MaredRenderContext(graphics);
        DockRenderer.render(controller.dockManager(), context, width, height);
        DockRenderer.dispatchMove(controller.dockManager(), mouseX, mouseY);
        DockRenderer.renderDividers(controller.dockManager(), context, width, height, mouseX, mouseY);

        MaredContextMenu.render(graphics, this.font, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (MaredContextMenu.isOpen()) {
            MaredContextMenu.mouseClicked(mx, my, button);
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (DockRenderer.beginDividerDrag(controller.dockManager(), mx, my, width, height, button)) return true;
        return DockRenderer.dispatchClick(controller.dockManager(), mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (DockRenderer.isDraggingDivider()) {
            DockRenderer.endDividerDrag(controller.dockManager());
            return true;
        }
        if (super.mouseReleased(mx, my, button)) return true;
        return DockRenderer.dispatchReleased(controller.dockManager(), mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (DockRenderer.isDraggingDivider()) {
            DockRenderer.updateDividerDrag(controller.dockManager(), mx, my, width, height);
            return true;
        }
        if (super.mouseDragged(mx, my, button, dragX, dragY)) return true;
        return DockRenderer.dispatchDrag(controller.dockManager(), mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (MaredContextMenu.isOpen()) return true;
        if (super.mouseScrolled(mx, my, scrollX, scrollY)) return true;
        return DockRenderer.dispatchScroll(controller.dockManager(), mx, my, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx, my);
        DockRenderer.dispatchMove(controller.dockManager(), mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MaredContextMenu.isOpen()) { MaredContextMenu.keyPressed(keyCode); return true; }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (DockRenderer.dispatchKey(controller.dockManager(), keyCode, scanCode, modifiers)) return true;
        boolean ctrl = (modifiers & 2) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_N) { onNewFile(); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_R) { onRunFile(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (MaredContextMenu.isOpen()) return true;
        if (super.charTyped(codePoint, modifiers)) return true;
        return DockRenderer.dispatchChar(controller.dockManager(), codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}