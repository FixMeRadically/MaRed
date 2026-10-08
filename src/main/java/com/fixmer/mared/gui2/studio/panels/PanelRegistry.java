package com.fixmer.mared.gui2.studio.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.docking.DockPosition;
import com.fixmer.mared.gui2.studio.panels.ConsoleDockPanel;
import com.fixmer.mared.gui2.studio.panels.ExplorerDockPanel;
import com.fixmer.mared.gui2.studio.panels.InspectorDockPanel;
import com.fixmer.mared.gui2.studio.panels.TopBarDockPanel;
import com.fixmer.mared.gui2.studio.panels.WorkspaceDockPanel;

/**
 * Реестр панелей Studio.
 *
 * 0.3.1: visibleByDefault — BooleanSupplier. Gate читает MaredSettings
 * в момент вызова. Controller больше не знает про id панелей.
 */
public final class PanelRegistry {

    private PanelRegistry() {}

    private static final Map<String, PanelDescriptor> PANELS =
        new LinkedHashMap<>(16);

    private static boolean bootstrapped = false;

    private static final Comparator<PanelDescriptor> BY_ORDER =
        Comparator.comparingInt(PanelDescriptor::defaultOrder);

    // ============================================================
    //  Bootstrap
    // ============================================================

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;

        register(new PanelDescriptor(
            "topbar",
            "mared:core",
            "mared.panel.topbar.title",
            DockPosition.TOP,
            0,
            () -> true,
            TopBarDockPanel::new));

        register(new PanelDescriptor(
            "explorer",
            "mared:scripts",
            "mared.panel.explorer.title",
            DockPosition.LEFT,
            10,
            MaredSettings::isLayoutShowSidebar,
            ExplorerDockPanel::new));

        register(new PanelDescriptor(
            "workspace",
            "mared:scripts",
            "mared.panel.workspace.title",
            DockPosition.CENTER,
            10,
            () -> true,
            WorkspaceDockPanel::new));

        register(new PanelDescriptor(
            "inspector",
            "mared:scripts",
            "mared.panel.inspector.title",
            DockPosition.RIGHT,
            10,
            MaredSettings::isLayoutShowRightPanel,
            InspectorDockPanel::new));

        register(new PanelDescriptor(
            "console",
            "mared:core",
            "mared.panel.console.title",
            DockPosition.BOTTOM,
            10,
            MaredSettings::isLayoutShowConsole,
            ConsoleDockPanel::new));
    }

    // ============================================================
    //  Регистрация
    // ============================================================

    public static synchronized void register(PanelDescriptor d) {
        if (d == null) return;
        if (PANELS.containsKey(d.id())) {
            Mared.LOGGER.warn(
                "[panel] duplicate panel id '{}', overriding previous",
                d.id());
        }
        PANELS.put(d.id(), d);
    }

    public static synchronized void unregister(String id) {
        if (id == null) return;
        PANELS.remove(id);
    }

    public static synchronized void reset() {
        PANELS.clear();
        bootstrapped = false;
    }

    // ============================================================
    //  Чтение
    // ============================================================

    public static synchronized PanelDescriptor byId(String id) {
        bootstrap();
        if (id == null) return null;
        return PANELS.get(id);
    }

    public static synchronized List<PanelDescriptor> all() {
        bootstrap();
        List<PanelDescriptor> list = new ArrayList<>(PANELS.values());
        list.sort(BY_ORDER);
        return list;
    }

    public static synchronized List<PanelDescriptor> forPosition(
            DockPosition position) {
        bootstrap();
        List<PanelDescriptor> list = new ArrayList<>();
        for (PanelDescriptor d : PANELS.values()) {
            if (d.defaultPosition() == position) list.add(d);
        }
        list.sort(BY_ORDER);
        return list;
    }

    /** Панели, у которых gate сейчас возвращает true. */
    public static synchronized List<PanelDescriptor> visibleNow() {
        bootstrap();
        List<PanelDescriptor> list = new ArrayList<>();
        for (PanelDescriptor d : PANELS.values()) {
            if (d.isVisible()) list.add(d);
        }
        list.sort(BY_ORDER);
        return list;
    }

    public static synchronized int count() {
        bootstrap();
        return PANELS.size();
    }

    public static synchronized Map<String, PanelDescriptor> raw() {
        bootstrap();
        return Collections.unmodifiableMap(new LinkedHashMap<>(PANELS));
    }
}