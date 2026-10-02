package com.fixmer.mared.welcome;

/**
 * Одна страница онбординга.
 *
 * Содержимое задаётся кодом (в MaredWelcomeData), не JSON —
 * для анимаций и точной раскладки удобнее код.
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
    public final String title;
    public final String subtitle;
    public final String[] bulletPoints;

    public MaredWelcomePage(Kind kind, String title, String subtitle, String... bullets) {
        this.kind = kind;
        this.title = title;
        this.subtitle = subtitle;
        this.bulletPoints = bullets;
    }
}
