package com.fixmer.mared.handbook;

import java.util.List;

/**
 * Провайдер данных для руководства.
 *
 * Может быть один общий (для всей игры) или несколько — по одному на
 * каждую вкладку (commands, scripts, npc, ...). Регистрируется в
 * MaredHandbookRegistry.
 */
public interface MaredHandbookData {

    /** Уникальный id провайдера, например "commands". */
    String id();

    /** Отображаемое имя, например "Commands". */
    String displayName();

    /** Все секции. */
    List<MaredHandbookSection> sections();

    /** Все записи (собраны из секций). */
    List<MaredHandbookEntry> allEntries();

    /** Найти запись по id (или null). */
    MaredHandbookEntry findById(String id);
}
