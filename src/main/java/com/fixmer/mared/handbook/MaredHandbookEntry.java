package com.fixmer.mared.handbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Одна запись руководства — команда, переменная, селектор,
 * синтаксическая конструкция или рецепт.
 *
 * Все поля immutable. Создаётся через Builder.
 */
public final class MaredHandbookEntry {

    // ============================================================
    //  Основное
    // ============================================================

    public final String id;                 // уникальный id ("mared.say", "vanilla.give")
    public final String name;               // отображаемое имя ("say", "@s", "if/else")
    public final String syntax;             // "say <текст> [scope=all|self]"
    public final String shortDescription;   // одна строка
    public final String fullDescription;    // многострочное описание (может быть null)
    public final String category;           // "Mared / Основы"
    public final String section;            // "Mared"
    public final boolean vanilla;           // true — ванильная команда
    public final boolean mared;             // true — Mared-команда

    // ============================================================
    //  Примеры
    // ============================================================

    public final List<Example> examples;

    public static final class Example {
        public final String code;           // "say \"Hello, $self\""
        public final String note;           // опциональное пояснение
        public Example(String code, String note) {
            this.code = code;
            this.note = note;
        }
    }

    // ============================================================
    //  Параметры
    // ============================================================

    public final List<Parameter> parameters;

    public static final class Parameter {
        public final String name;           // "text"
        public final String type;           // "string", "int", "expr", "..."
        public final String description;    // пояснение
        public final boolean optional;      // необязательный

        public Parameter(String name, String type, String description, boolean optional) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.optional = optional;
        }
    }

    // ============================================================
    //  Связи и заметки
    // ============================================================

    /** id других записей, на которые ссылаемся ("see also"). */
    public final List<String> seeAlso;

    /** Список "полезно знать" — предупреждения и нюансы. */
    public final List<String> notes;

    /** Теги для поиска: "мультиплеер", "singleplayer", "chat" и т.п. */
    public final List<String> tags;

    // ============================================================
    //  Конструктор
    // ============================================================

    private MaredHandbookEntry(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.syntax = b.syntax;
        this.shortDescription = b.shortDescription;
        this.fullDescription = b.fullDescription;
        this.category = b.category;
        this.section = b.section;
        this.vanilla = b.vanilla;
        this.mared = b.mared;
        this.examples = Collections.unmodifiableList(new ArrayList<>(b.examples));
        this.parameters = Collections.unmodifiableList(new ArrayList<>(b.parameters));
        this.seeAlso = Collections.unmodifiableList(new ArrayList<>(b.seeAlso));
        this.notes = Collections.unmodifiableList(new ArrayList<>(b.notes));
        this.tags = Collections.unmodifiableList(new ArrayList<>(b.tags));
    }

    // ============================================================
    //  Утилиты
    // ============================================================

    /** Первый пример для превью (или null). */
    public String firstExampleCode() {
        return examples.isEmpty() ? null : examples.get(0).code;
    }

    /** Поиск по ключевым словам — true, если хоть одно совпало. */
    public boolean matches(String queryLower) {
        if (queryLower.isEmpty()) return true;
        if (containsLower(name, queryLower)) return true;
        if (containsLower(syntax, queryLower)) return true;
        if (containsLower(shortDescription, queryLower)) return true;
        if (fullDescription != null && containsLower(fullDescription, queryLower)) return true;
        if (containsLower(category, queryLower)) return true;
        for (Example ex : examples) {
            if (containsLower(ex.code, queryLower)) return true;
            if (ex.note != null && containsLower(ex.note, queryLower)) return true;
        }
        for (Parameter p : parameters) {
            if (containsLower(p.name, queryLower)) return true;
            if (containsLower(p.description, queryLower)) return true;
        }
        for (String tag : tags) {
            if (containsLower(tag, queryLower)) return true;
        }
        return false;
    }

    private static boolean containsLower(String haystack, String needleLower) {
        if (haystack == null) return false;
        return haystack.toLowerCase().contains(needleLower);
    }

    // ============================================================
    //  Builder
    // ============================================================

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public static final class Builder {
        final String id;
        final String name;

        String syntax = "";
        String shortDescription = "";
        String fullDescription = null;
        String category = "Misc";
        String section = "Misc";
        boolean vanilla = false;
        boolean mared = false;

        final List<Example> examples = new ArrayList<>(4);
        final List<Parameter> parameters = new ArrayList<>(4);
        final List<String> seeAlso = new ArrayList<>(4);
        final List<String> notes = new ArrayList<>(2);
        final List<String> tags = new ArrayList<>(4);

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder syntax(String s) { this.syntax = s == null ? "" : s; return this; }
        public Builder shortDescription(String s) { this.shortDescription = s == null ? "" : s; return this; }
        public Builder fullDescription(String s) { this.fullDescription = s; return this; }
        public Builder category(String s) { this.category = s; return this; }
        public Builder section(String s) { this.section = s; return this; }
        public Builder vanilla(boolean v) { this.vanilla = v; return this; }
        public Builder mared(boolean v) { this.mared = v; return this; }

        public Builder example(String code) { examples.add(new Example(code, null)); return this; }
        public Builder example(String code, String note) { examples.add(new Example(code, note)); return this; }

        public Builder param(String name, String type, String description) {
            parameters.add(new Parameter(name, type, description, false));
            return this;
        }
        public Builder paramOpt(String name, String type, String description) {
            parameters.add(new Parameter(name, type, description, true));
            return this;
        }

        public Builder see(String... ids) {
            for (String id : ids) seeAlso.add(id);
            return this;
        }

        public Builder note(String s) { notes.add(s); return this; }

        public Builder tags(String... ts) {
            for (String t : ts) tags.add(t);
            return this;
        }

        public MaredHandbookEntry build() {
            return new MaredHandbookEntry(this);
        }
    }
}
