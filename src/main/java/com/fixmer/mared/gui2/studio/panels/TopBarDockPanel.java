package com.fixmer.mared.gui2.studio.panels;

import com.fixmer.mared.gui2.docking.MaredDockPanel;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.panels.topbar.StudioTopBar;
import com.fixmer.mared.gui2.modules.theme.ModuleType;

/**
 * Верхняя панель Studio.
 *
 * 0.3.0: рендерится без header — DockRenderer сам решает это по высоте
 * (см. MIN_HEIGHT_FOR_HEADER).
 */
public final class TopBarDockPanel extends MaredDockPanel {

    private final StudioTopBar topBar;

    public TopBarDockPanel(StudioEventBus bus) {
        this(new StudioTopBar(bus));
    }

    private TopBarDockPanel(StudioTopBar topBar) {
        super("topbar", "Top", topBar, ModuleType.TOOLS);
        this.topBar = topBar;
    }

    public StudioTopBar topBar() {
        return topBar;
    }

    @Override
    public boolean closable() {
        return false;
    }

    @Override
    public boolean movable() {
        return false;
    }

    @Override
    public boolean resizable() {
        return false;
    }

    @Override
    public int minWidth() {
        return 0;
    }

    @Override
    public int minHeight() {
        return 26;
    }
}