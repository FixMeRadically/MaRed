package com.fixmer.mared.gui2.docking;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import com.fixmer.mared.gui2.docking.layout.DockBounds;



/**
 * Узел Dock дерева MaRed.
 *
 * Представляет одну область интерфейса:
 *
 * LEFT
 * RIGHT
 * CENTER
 * BOTTOM
 *
 * Внутри содержит Dock панели.
 *
 * В будущем может содержать:
 *
 * - дочерние узлы;
 * - вкладки;
 * - разделители;
 * - сохранение layout.
 */
public final class DockNode {



    private final DockPosition position;



    private final List<DockPanel> panels =
            new ArrayList<>();



    /**
     * Текущая геометрия области.
     */
    private DockBounds bounds =
            new DockBounds(
                    0,
                    0,
                    0,
                    0
            );




    public DockNode(
            DockPosition position
    ){

        this.position = position;

    }





    /**
     * Позиция узла.
     */
    public DockPosition position(){

        return position;

    }





    /**
     * Добавить панель.
     */
    public void add(
            DockPanel panel
    ){

        if(panel == null)
            return;


        if(!panels.contains(panel))
        {
            panels.add(panel);
        }

    }





    /**
     * Удалить панель.
     */
    public void remove(
            DockPanel panel
    ){

        panels.remove(panel);

    }





    /**
     * Есть ли панели внутри.
     */
    public boolean hasPanels(){

        return !panels.isEmpty();

    }





    /**
     * Получить панели только для чтения.
     *
     * Защищает Dock систему от случайного изменения списка.
     */
    public List<DockPanel> panels(){

        return Collections.unmodifiableList(
                panels
        );

    }





    /**
     * Геометрия узла.
     */
    public DockBounds bounds(){

        return bounds;

    }





    /**
     * Обновить размеры.
     */
    public void setBounds(
            DockBounds bounds
    ){

        if(bounds == null)
            return;


        this.bounds = bounds;

    }


}