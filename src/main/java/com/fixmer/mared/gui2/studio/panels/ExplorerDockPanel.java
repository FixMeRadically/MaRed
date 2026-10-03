package com.fixmer.mared.gui2.studio.panels;

import com.fixmer.mared.gui2.docking.MaredDockPanel;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.panels.explorer.ExplorerComponent;
import com.fixmer.mared.gui2.modules.theme.ModuleType;

/**
 * Explorer панель MaRed Studio.
 *
 * 0.3.0: вместо статического ExplorerTreeComponent — реальный список
 * файлов команд.
 */
public final class ExplorerDockPanel extends MaredDockPanel {

    private final ExplorerComponent component;

    public ExplorerDockPanel(StudioEventBus bus) {
        this(new ExplorerComponent(bus));
    }

    private ExplorerDockPanel(ExplorerComponent component) {
        super("explorer", "Explorer", component, ModuleType.CONTENT);
        this.component = component;
    }

    public ExplorerComponent explorerComponent() {
        return component;
    }

    @Override
    public boolean closable() {
        return false;
    }

    @Override
    public int minWidth() {
        return 240;
    }

    @Override
    public int minHeight() {
        return 300;
    }
}