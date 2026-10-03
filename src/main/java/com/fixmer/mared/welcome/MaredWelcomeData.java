package com.fixmer.mared.welcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Набор страниц онбординга.
 *
 * 0.3.2 (audit #105):
 *   Страницы строятся один раз, но содержат translation keys, а не
 *   готовые строки. Данные не зависят от текущего языка — reload()
 *   нужен только если сам набор страниц меняется (например, из JSON
 *   в будущем), а не при смене языка.
 */
public final class MaredWelcomeData {

    private MaredWelcomeData() {}

    private static volatile List<MaredWelcomePage> pages = null;

    public static List<MaredWelcomePage> pages() {
        List<MaredWelcomePage> p = pages;
        if (p == null) {
            synchronized (MaredWelcomeData.class) {
                p = pages;
                if (p == null) {
                    p = build();
                    pages = p;
                }
            }
        }
        return Collections.unmodifiableList(p);
    }

    /**
     * Сбрасывает кэш. Нужен при смене набора страниц (например,
     * подгрузка онбординга из внешнего источника). Смена языка
     * не требует reload — MaredWelcomePage резолвит ключи сам.
     */
    public static void reload() { pages = null; }

    private static List<MaredWelcomePage> build() {
        List<MaredWelcomePage> list = new ArrayList<>(5);

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.INTRO,
            "mared.welcome.intro.title",
            "mared.welcome.intro.subtitle",
            "mared.welcome.intro.b1",
            "mared.welcome.intro.b2",
            "mared.welcome.intro.b3"
        ));

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.INTERFACE,
            "mared.welcome.ui.title",
            "mared.welcome.ui.subtitle",
            "mared.welcome.ui.b1",
            "mared.welcome.ui.b2",
            "mared.welcome.ui.b3",
            "mared.welcome.ui.b4"
        ));

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.COMMANDS,
            "mared.welcome.cmd.title",
            "mared.welcome.cmd.subtitle",
            "mared.welcome.cmd.b1",
            "mared.welcome.cmd.b2",
            "mared.welcome.cmd.b3"
        ));

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.SCRIPTS,
            "mared.welcome.scr.title",
            "mared.welcome.scr.subtitle",
            "mared.welcome.scr.b1",
            "mared.welcome.scr.b2",
            "mared.welcome.scr.b3"
        ));

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.FINAL,
            "mared.welcome.final.title",
            "mared.welcome.final.subtitle"
        ));

        return list;
    }
}