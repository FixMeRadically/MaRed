package com.fixmer.mared.welcome;

import com.fixmer.mared.MaredLang;

/**
 * Одна страница онбординга.
 *
 * 0.3.2 (audit #105):
 *   Модель хранит translation keys, а не готовые строки.
 *   Раньше MaredWelcomeData кэшировал переведённые строки на момент
 *   первого вызова pages() — смена языка требовала явного reload().
 *   Теперь render резолвит ключи через MaredLang.get() каждый кадр,
 *   язык применяется мгновенно.
 *
 * Публичные геттеры title()/subtitle()/bulletPoints() возвращают
 * УЖЕ ПЕРЕВЕДЁННЫЕ строки — совместимость с существующим renderer'ом.
 * Для доступа к ключам — titleKey()/subtitleKey()/bulletKeys().
 */
public final class MaredWelcomePage {

    public enum Kind {
        INTRO,       // приветствие, логотип
        INTERFACE,   // подсветка элементов интерфейса
        COMMANDS,    // как пользоваться handbook
        SCRIPTS,     // первый скрипт
        FINAL        // кнопки вкладок
    }

    public final Kind kind;
    private final String titleKey;
    private final String subtitleKey;
    private final String[] bulletKeys;

    public MaredWelcomePage(Kind kind,
                            String titleKey,
                            String subtitleKey,
                            String... bulletKeys) {
        this.kind = kind;
        this.titleKey = titleKey != null ? titleKey : "";
        this.subtitleKey = subtitleKey != null ? subtitleKey : "";
        this.bulletKeys = bulletKeys != null ? bulletKeys : new String[0];
    }

    // ============================================================
    //  Keys
    // ============================================================

    public String titleKey()    { return titleKey; }
    public String subtitleKey() { return subtitleKey; }

    public String[] bulletKeys() {
        // Возвращаем копию — caller не должен менять массив.
        String[] out = new String[bulletKeys.length];
        System.arraycopy(bulletKeys, 0, out, 0, bulletKeys.length);
        return out;
    }

    public int bulletCount() { return bulletKeys.length; }

    // ============================================================
    //  Resolved (для существующего renderer'а)
    // ============================================================

    /** Переведённый title. Резолвится каждый вызов. */
    public String title() {
        return MaredLang.get(titleKey);
    }

    /** Переведённый subtitle. Резолвится каждый вызов. */
    public String subtitle() {
        return MaredLang.get(subtitleKey);
    }

    /**
     * Переведённые bullet points.
     * Каждый вызов создаёт новый массив — не кэшировать между кадрами.
     */
    public String[] bulletPoints() {
        String[] out = new String[bulletKeys.length];
        for (int i = 0; i < bulletKeys.length; i++) {
            out[i] = MaredLang.get(bulletKeys[i]);
        }
        return out;
    }

    /** Как в старом API — массив всех bullet'ов переведённых. */
    public String[] bulletPointsRaw() {
        return bulletPoints();
    }
}