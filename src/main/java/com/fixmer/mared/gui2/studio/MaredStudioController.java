package com.fixmer.mared.gui2.studio;

import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.docking.DockManager;
import com.fixmer.mared.gui2.docking.DockPanel;
import com.fixmer.mared.gui2.docking.DockStateStorage;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.FocusManager;
import com.fixmer.mared.gui2.framework.core.PointerCaptureManager;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.panel.PanelDescriptor;
import com.fixmer.mared.gui2.studio.panel.PanelRegistry;
import com.fixmer.mared.gui2.studio.panels.ConsoleDockPanel;
import com.fixmer.mared.gui2.studio.panels.ExplorerDockPanel;
import com.fixmer.mared.gui2.studio.panels.InspectorDockPanel;
import com.fixmer.mared.gui2.studio.panels.TopBarDockPanel;
import com.fixmer.mared.gui2.studio.panels.WorkspaceDockPanel;
import com.fixmer.mared.services.logging.LogSettings;

/**
 * Главный контроллер MaRed Studio.
 *
 * 0.3.1:
 *   - Панели создаются по PanelRegistry.visibleNow() — фильтрация
 *     делегирована gate'ам в descriptor'ах.
 *   - Никаких switch по id внутри controller.
 */
public final class MaredStudioController {

    private final DockManager dockManager = new DockManager();
    private final StudioEventBus bus = new StudioEventBus();

    private FocusManager focusManager;
    private PointerCaptureManager pointerManager;

    private final Map<String, DockPanel> panelsById = new HashMap<>(8);

    private boolean initialized;

    public MaredStudioController() {}

    public DockManager dockManager() { return dockManager; }
    public StudioEventBus bus() { return bus; }

    public DockPanel panel(String id) { return panelsById.get(id); }

    public TopBarDockPanel topBarPanel() {
        return (TopBarDockPanel) panelsById.get("topbar");
    }

    public ConsoleDockPanel consolePanel() {
        return (ConsoleDockPanel) panelsById.get("console");
    }

    public ExplorerDockPanel explorerPanel() {
        return (ExplorerDockPanel) panelsById.get("explorer");
    }

    public WorkspaceDockPanel workspacePanel() {
        return (WorkspaceDockPanel) panelsById.get("workspace");
    }

    public InspectorDockPanel inspectorPanel() {
        return (InspectorDockPanel) panelsById.get("inspector");
    }

    public void initialize(FocusManager fm, PointerCaptureManager pm) {
        if (initialized) return;
        this.focusManager = fm;
        this.pointerManager = pm;

        DockStateStorage.load(dockManager.layout());

        PanelRegistry.bootstrap();
        for (PanelDescriptor d : PanelRegistry.visibleNow()) {
            createAndRegister(d);
        }

        if (focusManager != null || pointerManager != null) {
            for (var node : dockManager.nodes()) {
                for (DockPanel panel : node.panels()) {
                    if (focusManager != null) {
                        focusManager.attachTo(panel.component());
                    }
                    if (pointerManager != null) {
                        pointerManager.attachTo(panel.component());
                    }
                }
            }
        }

        initialized = true;
        bus.publish(new StudioEvents.LogEvent(
            LogSettings.Level.INFO, "studio", "initialized", null));
    }

    private void createAndRegister(PanelDescriptor d) {
        DockPanel panel;
        try {
            panel = d.factory().create(bus);
        } catch (Throwable t) {
            Mared.LOGGER.error(
                "[studio] failed to create panel '{}'", d.id(), t);
            return;
        }

        if (panel == null) {
            Mared.LOGGER.warn("[studio] factory returned null for '{}'", d.id());
            return;
        }

        panelsById.put(d.id(), panel);
        dockManager.register(d.defaultPosition(), panel);
    }

    public void shutdown() {
        if (!initialized) return;

        for (DockPanel panel : panelsById.values()) {
            disposeQuietly(panel.component());
        }

        if (focusManager != null) focusManager.clear();
        if (pointerManager != null) pointerManager.releaseAll();

        try {
            DockStateStorage.save(dockManager.layout());
        } catch (Throwable t) {
            Mared.LOGGER.warn("[docking] failed to save state", t);
        }

        try {
            com.fixmer.mared.gui2.framework.components.console.MaredLogPanel.saveToDisk();
        } catch (Throwable t) {
            Mared.LOGGER.warn("[studio] failed to save log", t);
        }

        bus.clear();
        panelsById.clear();
        initialized = false;
    }

    private static void disposeQuietly(Object o) {
        if (o instanceof Disposable d) {
            try {
                d.dispose();
            } catch (Throwable t) {
                Mared.LOGGER.warn("[studio] dispose failed", t);
            }
        }
    }
}