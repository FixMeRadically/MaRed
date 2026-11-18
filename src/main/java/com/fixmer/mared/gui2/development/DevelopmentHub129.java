package com.fixmer.mared.gui2.development;

import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.dev.client.*;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.technology.server.setup.*;
import com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Navigation over the existing authenticated server/workspace/restart services. */
public final class DevelopmentHub129 extends MaredScreen {
    public enum Section { SERVER, TEAM, SUPERVISOR, UPDATES, DIAGNOSTICS }
    private record TextRow(FormattedCharSequence text, int y, boolean heading) {}
    private record Action(DevelopmentButton129 button, int y, BooleanSupplier enabled) {}
    private final Screen previous;
    private final Section section;
    private DevelopmentLayout129 layout;
    private final List<TextRow> rows = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private final List<DevelopmentButton129> navigation = new ArrayList<>();
    private int scroll, bodyHeight, cursor;
    private String operationNotice = "";
    public DevelopmentHub129(Screen previous, Section section) {
        super(tr(section.name().toLowerCase(java.util.Locale.ROOT))); this.previous = previous; this.section = section;
    }
    private static Component tr(String key) { return Component.translatable("mared.development129." + key); }
    @Override protected void init() {
        layout = DevelopmentLayout129.of(width, height);
        rows.clear(); actions.clear(); navigation.clear(); cursor = 0;
        switch (section) {
            case SERVER -> {
                text("server_intro", true); text("server_help", false);
                action("server_open", true, () -> open151(new ServerRouteScreen116(parent151(this))), () -> true);
                action("join", false, () -> InPlaceServerService104.join128(parent151(this)),
                    () -> InPlaceServerService104.matchesFolder128(InPlaceServerService104.folder()) && InPlaceServerService104.canJoin128());
                action("server_tools", false, this::tools, () -> true);
                text("join_help", false);
            }
            case TEAM -> {
                text("team_intro", true); text("team_help", false);
                action("workspace", true, () -> open151(new WorkspaceScreen122(parent151(this))), () -> minecraft.getConnection() != null);
                text("workspace_help", false);
                action("access", false, () -> open151(new ServerAccessScreen88(parent151(this))),
                    () -> minecraft.getConnection() != null && DedicatedPanelService75.hasRemote() && DedicatedPanelService75.canRemote88("PING"));
                text("access_help", false);
            }
            case SUPERVISOR -> {
                text("supervisor_intro", true); text("supervisor_help", false);
                action("activate", true, () -> confirm("activate_confirm", () -> DevActivationService.activate()),
                    () -> !DevRestartService.isUnderSupervisor() && !DevActivationService.isActivating() && !DevRestartService.isRestartInProgress());
                action("restart", false, () -> confirm("restart_confirm", () -> DevRestartService.restartAsync()),
                    () -> DevMode.isEnabled() && DevRestartService.isUnderSupervisor() && !DevRestartService.isRestartInProgress() && !DevActivationService.isActivating());
                text("restart_help", false);
                action("history", false, () -> open151(new RestartHistoryScreen97(parent151(this))), () -> true);
            }
            case UPDATES -> {
                text("updates_intro", true); text("updates_help", false);
                action("rollout", true, this::rollout, () -> remoteFolder()
                    || minecraft.getConnection() == null && !InPlaceServerService104.folder().isBlank());
                action("publication", false, this::tools, () -> true);
                text("publication_help", false);
                action("history", false, () -> open151(new RestartHistoryScreen97(parent151(this))), () -> true);
            }
            case DIAGNOSTICS -> {
                text("diagnostics_intro", true); text("diagnostics_help", false);
                action("server_open", true, () -> open151(new ServerRouteScreen116(parent151(this))), () -> true);
                action("server_tools", false, this::tools, () -> true);
                action("history", false, () -> open151(new RestartHistoryScreen97(parent151(this))), () -> true);
            }
        }
        bodyHeight = cursor + 8;
        if(!embedded151()){var back = new DevelopmentButton129(8, Math.max(0, height - 30), layout.navWidth(),
            Component.translatable("gui.back"), false, this::onClose);
        if (layout.narrow()) back.icon(9);
        back.setTooltip(Tooltip.create(Component.translatable("gui.back")));
        navigation.add(addRenderableWidget(back));}
        updateControls();
    }
    private void text(String key, boolean heading) {
        for (var line : font.split(tr(key), Math.max(1, layout.contentWidth() - 16))) {
            rows.add(new TextRow(line, cursor, heading)); cursor += font.lineHeight + 4;
        }
        cursor += heading ? 6 : 10;
    }
    private void action(String key, boolean primary, Runnable run, BooleanSupplier enabled) {
        var button = addRenderableWidget(new DevelopmentButton129(layout.contentX() + 8, 0,
            Math.max(1, layout.contentWidth() - 16), tr(key), primary, run));
        button.setTooltip(Tooltip.create(tr(key)));
        actions.add(new Action(button, cursor, enabled)); cursor += 32;
    }
    private void updateControls() {
        scroll = layout.clampScroll(scroll, bodyHeight);
        for (var action : actions) {
            int y = layout.contentY() + action.y() - scroll;
            action.button().setY(y);
            // A clipped button must never receive clicks outside the scrolling viewport.
            action.button().visible = y >= layout.contentY() && y + 24 <= layout.contentBottom();
            action.button().active = action.enabled().getAsBoolean();
        }
    }
    private boolean remoteFolder() {
        return minecraft.getConnection() != null && DedicatedPanelService75.hasRemote()
            && "true".equals(DedicatedPanelService75.remote().get("inplace104"));
    }
    private void rollout() { open151(new FolderUpdateScreen118(parent151(this), InPlaceServerService104.folder(), remoteFolder())); }
    private void tools() { open151(new ServerToolsScreen116(parent151(this), minecraft.getConnection() != null)); }
    private void confirm(String message, java.util.function.Supplier<java.util.concurrent.CompletableFuture<Boolean>> run) {
        open151(new ConfirmScreen(yes -> {
            open151(parent151(this));
            if (yes) {
                operationNotice = "";
                run.get().whenComplete((ok, error) -> minecraft.execute(() -> {
                    if (error != null || !Boolean.TRUE.equals(ok)) operationNotice = DevActivationService.lastStatus;
                }));
            }
        }, title, tr(message)));
    }
    @Override public void tick() {
        if (DedicatedPanelService75.hasRemote() && minecraft.getConnection() != null) DedicatedPanelService75.poll(false, "");
        else if (section == Section.SERVER && !InPlaceServerService104.folder().isBlank()) InPlaceServerService104.poll(InPlaceServerService104.folder());
        if (layout != null) updateControls();
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < layout.contentX() || y < layout.contentY() || y >= layout.contentBottom()) return false;
        scroll = layout.clampScroll(scroll - (int)(dy * 26), bodyHeight); updateControls(); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 68 && (modifiers & 15) == 6) { onClose(); return true; }
        if (key == 267 || key == 266) { scroll += key == 267 ? layout.viewportHeight() : -layout.viewportHeight(); updateControls(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        var t = MaredThemeRegistry.active();
        g.fill(0, 0, width, height, embedded151()?t.bgPanel:t.bgScreen);
        
        g.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(1, width - 24)), 12, 10, t.text, false);
        Component status = section == Section.SUPERVISOR ? tr(DevRestartService.isUnderSupervisor() ? "supervisor_on" : "supervisor_off")
            : tr(minecraft.getConnection() != null ? "connected" : "disconnected");
        if (section == Section.SERVER && minecraft.getConnection() == null && !InPlaceServerService104.folder().isBlank())
            status = Component.translatable("mared.development129.server_status", Component.translatable("mared.server104.phase." +
                com.fixmer.genesis.technology.server.remote.ServerView105.of(InPlaceServerService104.state(), true, InPlaceServerService104.busy()).phase()));
        if (section == Section.SUPERVISOR && (DevActivationService.isActivating() || DevRestartService.isRestartInProgress() || !operationNotice.isBlank()))
            status = Component.literal(operationNotice.isBlank() ? DevActivationService.lastStatus : operationNotice);
        g.drawString(font, font.plainSubstrByWidth(status.getString(), Math.max(1, width - 24)), 12, 22, t.textDim, false);
        g.enableScissor(layout.contentX(), layout.contentY(), width - 8, layout.contentBottom());
        for (var row : rows) g.drawString(font, row.text(), layout.contentX() + 8,
            layout.contentY() + row.y() - scroll, row.heading() ? t.text : t.textDim, false);
        g.disableScissor();
        for (var action : actions) action.button().render(g, mx, my, dt);
        for (var button : navigation) button.render(g, mx, my, dt);
        if (bodyHeight > layout.viewportHeight() && layout.viewportHeight() > 0) {
            int h = Math.max(8, layout.viewportHeight() * layout.viewportHeight() / bodyHeight);
            int y = layout.contentY() + scroll * (layout.viewportHeight() - h) / (bodyHeight - layout.viewportHeight());
            g.fill(width - 5, layout.contentY(), width - 3, layout.contentBottom(), t.scrollTrack);
            g.fill(width - 5, y, width - 3, y + h, t.scrollThumb);
        }
    }
    @Override public void onClose() { open151(previous); }
    @Override public boolean isPauseScreen() { return false; }
}
