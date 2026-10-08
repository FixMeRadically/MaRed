package com.fixmer.mared.gui2.runtime;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.UiContext;
import com.fixmer.mared.gui2.framework.overlay.ConfirmDialogOverlay;
import com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry;
import com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay;
import com.fixmer.mared.gui2.navigation.MaredNavigation;
import com.fixmer.mared.gui2.navigation.SpaceGraph;
import com.fixmer.mared.gui2.navigation.SpaceId;
import com.fixmer.mared.gui2.spaces.ContentSpace;
import com.fixmer.mared.gui2.spaces.GenesisSpace;
import com.fixmer.mared.gui2.spaces.LogicSpace;
import com.fixmer.mared.gui2.spaces.ResourcesSpace;
import com.fixmer.mared.gui2.spaces.ScenariosSpace;
import com.fixmer.mared.gui2.spaces.StudioSpace;
import com.fixmer.mared.gui2.spaces.ToolsSpace;
import com.fixmer.mared.gui2.spaces.WorldSpace;
import com.fixmer.mared.gui2.studio.StudioSession;
import com.fixmer.mared.gui2.studio.action.EditorAction;
import com.fixmer.mared.gui2.studio.action.EditorActionArgs;
import com.fixmer.mared.gui2.studio.action.StudioActions;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;
import com.fixmer.mared.gui2.studio.panels.workspace.WorkspaceComponent;

import net.minecraft.client.gui.screens.Screen;

/**
 * StudioRuntime — владелец сессии MaRed.
 *
 * Живёт столько же, сколько MaredShellScreen. Не пересоздаётся
 * при переходе между пространствами (F4, Esc, клики по узлам
 * Genesis). Одна сессия на всё открытие Shell'а.
 *
 * Что внутри:
 *   - StudioSession      — редактор: контроллер, actions, файлы, workspace;
 *   - SpaceGraph         — граф пространств (Genesis, Studio, Content, ...);
 *   - MaredNavigation    — стек навигации;
 *   - SubscriptionGroup  — bus-подписки, которые нужны постоянно
 *                          (context menu, dirty close, reload persistent).
 *
 * Что сюда придёт позже (по плану 1.5.39.3–1.5.39.5):
 *   - MaredProject (project context)
 *   - SelectionState
 *   - UndoStack (CommandStack)
 *   - SettingsSnapshot
 *
 * Чего здесь НЕ должно быть:
 *   - UI (никаких MaredComponent / Screen / render);
 *   - Minecraft-зависимостей, кроме Screen для session.initialize();
 *   - Genesis-специфики (Genesis — просто одно из пространств графа).
 *
 * Genesis о Runtime НЕ знает. Genesis говорит "user selected CONTENT",
 * Shell + Runtime решают, что делать (push/push/replace).
 */
public final class StudioRuntime implements Disposable {

    private final Screen host;
    private final UiContext uiContext;

    private final StudioSession session;
    private final SpaceGraph graph;
    private final MaredNavigation navigation;
    private final SubscriptionGroup subs = new SubscriptionGroup();

    private boolean disposed = false;

    public StudioRuntime(Screen host, UiContext uiContext) {
        this.host = host;
        this.uiContext = uiContext;

        // 1. Session — редактор. Инициализируется один раз.
        this.session = new StudioSession();
        try {
            this.session.initialize(host, uiContext);
        } catch (Throwable t) {
            Mared.LOGGER.error("[runtime] session init failed", t);
        }

        // 2. Graph + navigation.
        this.graph = new SpaceGraph();
        this.navigation = new MaredNavigation(graph);

        buildGraph();
        subscribeBus();

        Mared.LOGGER.info("[runtime] StudioRuntime created");
    }

    // ============================================================
    //  Graph
    // ============================================================

    private void buildGraph() {
        graph.register(SpaceId.GENESIS,   () -> new GenesisSpace(navigation));
        graph.register(SpaceId.CONTENT,   ContentSpace::new);
        graph.register(SpaceId.WORLD,     WorldSpace::new);
        graph.register(SpaceId.LOGIC,     LogicSpace::new);
        graph.register(SpaceId.RESOURCES, ResourcesSpace::new);
        graph.register(SpaceId.TOOLS,     ToolsSpace::new);
        graph.register(SpaceId.SCENARIOS, ScenariosSpace::new);
        graph.register(SpaceId.STUDIO,    StudioSpace::new);

        graph.connect(SpaceId.GENESIS, SpaceId.CONTENT);
        graph.connect(SpaceId.GENESIS, SpaceId.WORLD);
        graph.connect(SpaceId.GENESIS, SpaceId.LOGIC);
        graph.connect(SpaceId.GENESIS, SpaceId.RESOURCES);
        graph.connect(SpaceId.GENESIS, SpaceId.TOOLS);
        graph.connect(SpaceId.GENESIS, SpaceId.SCENARIOS);
        graph.connect(SpaceId.GENESIS, SpaceId.STUDIO);
        graph.connect(SpaceId.STUDIO,  SpaceId.GENESIS);
        graph.connect(SpaceId.CONTENT, SpaceId.STUDIO);
        graph.connect(SpaceId.WORLD,   SpaceId.STUDIO);
        graph.connect(SpaceId.LOGIC,   SpaceId.STUDIO);
        graph.connect(SpaceId.RESOURCES, SpaceId.STUDIO);
        graph.connect(SpaceId.TOOLS,   SpaceId.STUDIO);
        graph.connect(SpaceId.SCENARIOS, SpaceId.STUDIO);
    }

    // ============================================================
    //  Bus подписки — живут постоянно, пока жив Runtime
    // ============================================================

    private void subscribeBus() {
        var bus = session.controller().bus();

        subs.add(bus.subscribe(StudioEvents.RequestNewFileEvent.class,
            e -> session.executeAction(StudioActions.FILE_NEW)));
        subs.add(bus.subscribe(StudioEvents.RequestReloadPersistentEvent.class,
            e -> session.executeAction(StudioActions.FILE_RELOAD_PERSISTENT)));
        subs.add(bus.subscribe(StudioEvents.RequestContextMenuForFileEvent.class,
            e -> showFileContextMenu(e.x(), e.y(), e.fileName())));
        subs.add(bus.subscribe(StudioEvents.RequestContextMenuEvent.class,
            e -> showRawContextMenu(e.x(), e.y(), e.entries())));
        subs.add(bus.subscribe(StudioEvents.RequestCloseDirtyDocumentEvent.class,
            e -> showCloseDirtyConfirm(e.documentTitle())));
    }

    private void showFileContextMenu(int x, int y, String fileName) {
        if (session.overlayManager() == null) return;
        if (session.actions() == null || session.actionContext() == null) return;

        EditorActionArgs args = EditorActionArgs.of("fileName", fileName);
        List<ContextMenuEntry> items = new ArrayList<>();

        for (String id : StudioActions.FILE_CONTEXT_MENU) {
            if (StudioActions.isSeparator(id)) {
                items.add(ContextMenuEntry.divider());
                continue;
            }
            EditorAction a = session.actions().byId(id);
            if (a == null) continue;
            if (!a.isEnabled(session.actionContext(), args)) continue;
            final String actionId = id;
            items.add(ContextMenuEntry.of(
                MaredLang.get(a.titleKey()),
                () -> session.executeAction(actionId, args)
            ));
        }
        stripRedundantDividers(items);
        if (items.isEmpty()) return;
        session.overlayManager().push(new ContextMenuOverlay(x, y, items).category(com.fixmer.mared.technology.editor.GenesisEditorVisuals.LOGIC));
    }

    private void showRawContextMenu(int x, int y, List<ContextMenuEntry> entries) {
        if (session.overlayManager() == null) return;
        if (entries == null || entries.isEmpty()) return;
        session.overlayManager().push(new ContextMenuOverlay(x, y, entries).category(com.fixmer.mared.technology.editor.GenesisEditorVisuals.LOGIC));
    }

    private static void stripRedundantDividers(List<ContextMenuEntry> items) {
        while (!items.isEmpty() && items.get(0).separator()) items.remove(0);
        while (!items.isEmpty() && items.get(items.size() - 1).separator()) {
            items.remove(items.size() - 1);
        }
        for (int i = items.size() - 1; i > 0; i--) {
            if (items.get(i).separator() && items.get(i - 1).separator()) {
                items.remove(i);
            }
        }
    }

    private void showCloseDirtyConfirm(String title) {
        if (session.overlayManager() == null) return;

        var ws = session.controller().workspacePanel();
        WorkspaceComponent wc = (ws == null) ? null : ws.workspaceComponent();
        if (wc == null) return;

        session.overlayManager().push(new ConfirmDialogOverlay(
            MaredLang.get("mared.dialog.unsaved_title"),
            MaredLang.format("mared.dialog.unsaved_message_named", title),
            () -> wc.confirmPendingClose(WorkspaceComponent.CloseAction.DISCARD),
            true, com.fixmer.mared.technology.editor.GenesisEditorVisuals.LOGIC, true, "mared.dialog.confirm"
        ));
    }

    // ============================================================
    //  Public API
    // ============================================================

    public Screen host()             { return host; }
    public UiContext uiContext()     { return uiContext; }
    public StudioSession session()   { return session; }
    public SpaceGraph graph()        { return graph; }
    public MaredNavigation navigation() { return navigation; }

    public boolean isDisposed() { return disposed; }

    public void tick(float dt) {
        if (disposed) return;
        navigation.tick(dt);
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;

        try { subs.dispose(); }
        catch (Throwable t) { Mared.LOGGER.warn("[runtime] subs dispose", t); }

        try { navigation.back(); /* no-op if depth<=1 */ }
        catch (Throwable ignored) { }

        try { session.shutdown(); }
        catch (Throwable t) { Mared.LOGGER.warn("[runtime] session shutdown", t); }

        Mared.LOGGER.info("[runtime] StudioRuntime disposed");
    }
}
