package com.fixmer.mared.gui2.docking;


import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.modules.theme.ModuleType;



/**
 * Панель Dock системы MaRed.
 *
 * Любая панель редактора:
 *
 * Explorer
 * Inspector
 * Console
 * Workspace
 *
 * реализует этот интерфейс.
 */
public interface DockPanel {




    /**
     * Уникальный ID панели.
     *
     * Используется для:
     *
     * - сохранения layout;
     * - восстановления;
     * - поиска.
     */
    String id();






    /**
     * Отображаемое название.
     */
    String title();






    /**
     * Сам GUI компонент.
     */
    MaredComponent component();







    /**
     * Тип модуля панели.
     *
     * Используется для:
     *
     * - цвета accent;
     * - группировки;
     * - будущих вкладок;
     * - визуальной идентификации.
     *
     * Если панель ничего не указала,
     * она считается инструментальной.
     */
    default ModuleType moduleType(){

        return ModuleType.TOOLS;

    }







    /**
     * Минимальная ширина.
     */
    default int minWidth(){

        return 150;

    }







    /**
     * Минимальная высота.
     */
    default int minHeight(){

        return 100;

    }







    /**
     * Можно ли закрыть панель.
     */
    default boolean closable(){

        return true;

    }







    /**
     * Можно ли перемещать панель.
     */
    default boolean movable(){

        return true;

    }







    /**
     * Можно ли изменить размер.
     */
    default boolean resizable(){

        return true;

    }




    /**
     * Default accent for docking chrome.
     */
    default int accentColor() {
        return 0xFF55AAFF;
    }
}