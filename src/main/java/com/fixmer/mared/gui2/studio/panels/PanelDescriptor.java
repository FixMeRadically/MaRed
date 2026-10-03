package com.fixmer.mared.gui2.studio.panel;

import java.util.function.BooleanSupplier;

import com.fixmer.mared.gui2.docking.DockPosition;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;

/**
 * Описание панели Studio.
 *
 * 0.3.1:
 *   - visibleByDefault теперь BooleanSupplier — панель сама решает,
 *     показываться ли, опираясь на текущие настройки. Controller
 *     больше не содержит switch по конкретным id.
 *   - Gate вызывается при каждой initialize() — настройки могут
 *     меняться между сессиями.
 */
public record PanelDescriptor(
    String id,
    String moduleId,
    String titleKey,
    DockPosition defaultPosition,
    int defaultOrder,
    BooleanSupplier visibleByDefault,
    PanelFactory factory
) {

    public interface PanelFactory {
        com.fixmer.mared.gui2.docking.DockPanel create(StudioEventBus bus);
    }

    public PanelDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("panel id required");
        }
        if (defaultPosition == null) defaultPosition = DockPosition.CENTER;
        if (moduleId == null) moduleId = "";
        if (titleKey == null) titleKey = "";
        if (visibleByDefault == null) visibleByDefault = () -> true;
    }

    public boolean isVisible() {
        try {
            return visibleByDefault.getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }
}