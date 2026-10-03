package com.fixmer.mared.gui2.docking;


import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.modules.theme.ModuleType;




/**
 * Базовая реализация Dock панели MaRed.
 *
 * Все системные панели наследуются от неё.
 *
 * Содержит:
 *
 * - ID панели
 * - название
 * - GUI компонент
 * - тип модуля
 */
public abstract class MaredDockPanel
        implements DockPanel {





    private final String id;


    private final String title;


    private final MaredComponent component;


    private final ModuleType moduleType;









    /**
     * Старый конструктор.
     *
     * Оставляем для совместимости.
     *
     * По умолчанию:
     * TOOLS
     */
    protected MaredDockPanel(
            String id,
            String title,
            MaredComponent component
    ){

        this(
                id,
                title,
                component,
                ModuleType.TOOLS
        );

    }









    /**
     * Новый конструктор
     * с указанием модуля.
     */
    protected MaredDockPanel(
            String id,
            String title,
            MaredComponent component,
            ModuleType moduleType
    ){

        this.id = id;

        this.title = title;

        this.component = component;

        this.moduleType =
                moduleType == null
                        ? ModuleType.TOOLS
                        : moduleType;

    }









    @Override
    public String id(){

        return id;

    }








    @Override
    public String title(){

        return title;

    }








    @Override
    public MaredComponent component(){

        return component;

    }









    /**
     * Тип модуля панели.
     *
     * Используется:
     *
     * DockRenderer
     * ModuleColorRegistry
     * Accent система
     */
    @Override
    public ModuleType moduleType(){

        return moduleType;

    }









    @Override
    public int minWidth(){

        return 200;

    }









    @Override
    public int minHeight(){

        return 120;

    }









    @Override
    public boolean movable(){

        return true;

    }









    @Override
    public boolean resizable(){

        return true;

    }




    @Override
    public int accentColor() {
        com.fixmer.mared.gui2.modules.theme.ModuleTheme mt =
            com.fixmer.mared.gui2.modules.theme.ModuleColorRegistry.get(moduleType());
        return mt != null ? mt.accent() : 0xFF55AAFF;
    }
}