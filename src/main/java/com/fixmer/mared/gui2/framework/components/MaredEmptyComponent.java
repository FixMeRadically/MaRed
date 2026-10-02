package com.fixmer.mared.gui2.framework.components;


import com.fixmer.mared.gui2.framework.core.MaredRenderContext;



/**
 * Пустой компонент-заглушка.
 *
 * Используется пока настоящий UI компонент
 * ещё не подключён.
 *
 * Например:
 *
 * Explorer
 * Inspector
 * Console
 *
 * Позже заменяется полноценными компонентами.
 */
public final class MaredEmptyComponent
        extends MaredPanel {



    public MaredEmptyComponent(){

    }





    @Override
    protected void safeRender(
            MaredRenderContext context
    ){

        /*
         * Ничего не рисуем.
         *
         * Компонент существует,
         * но пока пустой.
         */

    }


}