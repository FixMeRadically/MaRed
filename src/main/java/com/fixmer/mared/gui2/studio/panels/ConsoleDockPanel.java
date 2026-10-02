package com.fixmer.mared.gui2.studio.panels;

import com.fixmer.mared.gui2.docking.MaredDockPanel;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.panels.console.ConsoleComponent;
import com.fixmer.mared.gui2.theme.ModuleType;

/**
 * Нижняя консоль MaRed Studio.
 *
 * 0.3.0: вместо MaredEmptyComponent — ConsoleComponent с реальным
 * MaredLogPanel (5 уровней, 14 категорий, поиск, экспорт).
 */
public final class ConsoleDockPanel extends MaredDockPanel {

    private final ConsoleComponent component;

    public ConsoleDockPanel(StudioEventBus bus) {
        this(new ConsoleComponent(bus));
    }

    private ConsoleDockPanel(ConsoleComponent component) {
        super("console", "Console", component, ModuleType.LOGIC);
        this.component = component;
    }

    public ConsoleComponent consoleComponent() {
        return component;
    }

    @Override
    public int minHeight() {
        return 150;
    }
}