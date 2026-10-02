package com.fixmer.mared.welcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fixmer.mared.MaredLang;

/**
 * Набор страниц онбординга.
 *
 * Пока статический. Можно будет загружать из JSON в будущем.
 */
public final class MaredWelcomeData {

    private MaredWelcomeData() {}

    private static List<MaredWelcomePage> pages = null;

    public static List<MaredWelcomePage> pages() {
        if (pages == null) pages = build();
        return Collections.unmodifiableList(pages);
    }

    public static void reload() { pages = null; }

    private static List<MaredWelcomePage> build() {
        List<MaredWelcomePage> list = new ArrayList<>(5);

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.INTRO,
            MaredLang.get("mared.welcome.intro.title"),
            MaredLang.get("mared.welcome.intro.subtitle"),
            MaredLang.get("mared.welcome.intro.b1"),
            MaredLang.get("mared.welcome.intro.b2"),
            MaredLang.get("mared.welcome.intro.b3")
        ));

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.INTERFACE,
            MaredLang.get("mared.welcome.ui.title"),
            MaredLang.get("mared.welcome.ui.subtitle"),
            MaredLang.get("mared.welcome.ui.b1"),
            MaredLang.get("mared.welcome.ui.b2"),
            MaredLang.get("mared.welcome.ui.b3"),
            MaredLang.get("mared.welcome.ui.b4")
        ));

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.COMMANDS,
            MaredLang.get("mared.welcome.cmd.title"),
            MaredLang.get("mared.welcome.cmd.subtitle"),
            MaredLang.get("mared.welcome.cmd.b1"),
            MaredLang.get("mared.welcome.cmd.b2"),
            MaredLang.get("mared.welcome.cmd.b3")
        ));

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.SCRIPTS,
            MaredLang.get("mared.welcome.scr.title"),
            MaredLang.get("mared.welcome.scr.subtitle"),
            MaredLang.get("mared.welcome.scr.b1"),
            MaredLang.get("mared.welcome.scr.b2"),
            MaredLang.get("mared.welcome.scr.b3")
        ));

        list.add(new MaredWelcomePage(
            MaredWelcomePage.Kind.FINAL,
            MaredLang.get("mared.welcome.final.title"),
            MaredLang.get("mared.welcome.final.subtitle")
        ));

        return list;
    }
}
