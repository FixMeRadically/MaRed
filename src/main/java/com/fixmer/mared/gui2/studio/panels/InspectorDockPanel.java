package com.fixmer.mared.gui2.studio.panels;

import com.fixmer.mared.gui2.docking.MaredDockPanel;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.panels.inspector.InspectorComponent;
import com.fixmer.mared.gui2.modules.theme.ModuleType;

/**
 * Панель свойств выбранного объекта.
 *
 * 0.3.0: InspectorComponent с описанием Mared-команд.
 */
public final class InspectorDockPanel extends MaredDockPanel {

    private final InspectorComponent component;

    public InspectorDockPanel(StudioEventBus bus) {
        this(new InspectorComponent(bus));
    }

    private InspectorDockPanel(InspectorComponent component) {
        super("inspector", "Inspector", component, ModuleType.RESOURCES);
        this.component = component;
    }

    public InspectorComponent inspectorComponent() {
        return component;
    }

    @Override
    public int minWidth() {
        return 280;
    }
}