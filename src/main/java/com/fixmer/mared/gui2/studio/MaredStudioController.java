package com.fixmer.mared.gui2.studio;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.docking.DockManager;
import com.fixmer.mared.gui2.docking.DockPosition;
import com.fixmer.mared.gui2.docking.DockStateStorage;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.panels.ConsoleDockPanel;
import com.fixmer.mared.gui2.studio.panels.ExplorerDockPanel;
import com.fixmer.mared.gui2.studio.panels.InspectorDockPanel;
import com.fixmer.mared.gui2.studio.panels.TopBarDockPanel;
import com.fixmer.mared.gui2.studio.panels.WorkspaceDockPanel;

/**
 * Главный контроллер MaRed Studio.
 *
 * 0.3.0: загрузка/сохранение ratios docking-панелей через DockStateStorage.
 * 0.3.0 (fix): панели регистрируются по настройкам MaredSettings
 * (isLayoutShowSidebar/RightPanel/Console). При изменении layout
 * MaredSettingsScreen пересоздаёт Studio.
 */
public final class MaredStudioController {

    private final DockManager dockManager = new DockManager();
    private final StudioEventBus bus = new StudioEventBus();

    private TopBarDockPanel topBarPanel;
    private ConsoleDockPanel consolePanel;
    private ExplorerDockPanel explorerPanel;
    private WorkspaceDockPanel workspacePanel;
    private InspectorDockPanel inspectorPanel;

    private boolean initialized;

    public MaredStudioController() {}

    public DockManager dockManager() { return dockManager; }
    public StudioEventBus bus() { return bus; }

    public TopBarDockPanel topBarPanel() { return topBarPanel; }
    public ConsoleDockPanel consolePanel() { return consolePanel; }
    public ExplorerDockPanel explorerPanel() { return explorerPanel; }
    public WorkspaceDockPanel workspacePanel() { return workspacePanel; }
    public InspectorDockPanel inspectorPanel() { return inspectorPanel; }

    public void initialize() {
        if (initialized) return;

        DockStateStorage.load(dockManager.layout());

        // TOP — всегда
        topBarPanel = new TopBarDockPanel(bus);
        dockManager.register(DockPosition.TOP, topBarPanel);

        // BOTTOM — Console
        if (MaredSettings.isLayoutShowConsole()) {
            consolePanel = new ConsoleDockPanel(bus);
            dockManager.register(DockPosition.BOTTOM, consolePanel);
        }

        // RIGHT — Inspector
        if (MaredSettings.isLayoutShowRightPanel()) {
            inspectorPanel = new InspectorDockPanel(bus);
            dockManager.register(DockPosition.RIGHT, inspectorPanel);
        }

        // LEFT — Explorer
        if (MaredSettings.isLayoutShowSidebar()) {
            explorerPanel = new ExplorerDockPanel(bus);
            dockManager.register(DockPosition.LEFT, explorerPanel);
        }

        // CENTER — Workspace, всегда
        workspacePanel = new WorkspaceDockPanel(bus);
        dockManager.register(DockPosition.CENTER, workspacePanel);

        initialized = true;
        bus.publish(new StudioEvents.LogEvent("[studio] initialized"));
    }

    public void shutdown() {
        if (!initialized) return;

        disposeQuietly(topBarPanel == null ? null : topBarPanel.topBar());
        disposeQuietly(consolePanel == null ? null : consolePanel.consoleComponent());
        disposeQuietly(inspectorPanel == null ? null : inspectorPanel.inspectorComponent());
        disposeQuietly(workspacePanel == null ? null : workspacePanel.workspaceComponent());

        try {
            DockStateStorage.save(dockManager.layout());
        } catch (Throwable t) {
            com.fixmer.mared.Mared.LOGGER.warn(
                "[docking] failed to save state: {}", t.getMessage());
        }

        try {
            com.fixmer.mared.gui2.framework.components.console.MaredLogPanel.saveToDisk();
        } catch (Throwable t) {
            com.fixmer.mared.Mared.LOGGER.warn(
                "[studio] failed to save log: {}", t.getMessage());
        }

        bus.clear();
        initialized = false;
    }

    private static void disposeQuietly(Object o) {
        if (o instanceof Disposable d) {
            try { d.dispose(); }
            catch (Throwable t) {
                com.fixmer.mared.Mared.LOGGER.warn(
                    "[studio] dispose failed: {}", t.getMessage());
            }
        }
    }
}