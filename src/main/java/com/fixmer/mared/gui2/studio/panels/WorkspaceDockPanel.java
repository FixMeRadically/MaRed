package com.fixmer.mared.gui2.studio.panels;

import com.fixmer.mared.gui2.docking.MaredDockPanel;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.panels.workspace.WorkspaceComponent;
import com.fixmer.mared.gui2.theme.ModuleType;

/**
 * Центральная рабочая область MaRed Studio.
 *
 * 0.3.0: содержит WorkspaceComponent с MaredMultiLineEditBox.
 */
public final class WorkspaceDockPanel extends MaredDockPanel {

    private final WorkspaceComponent component;

    public WorkspaceDockPanel(StudioEventBus bus) {
        this(new WorkspaceComponent(bus));
    }

    private WorkspaceDockPanel(WorkspaceComponent component) {
        super("workspace", "Workspace", component, ModuleType.WORLD);
        this.component = component;
    }

    public WorkspaceComponent workspaceComponent() {
        return component;
    }

    @Override
    public boolean closable() {
        return false;
    }
}