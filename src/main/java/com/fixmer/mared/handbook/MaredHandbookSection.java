package com.fixmer.mared.handbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Категория руководства (например, "Mared / Основы" или "Vanilla / Команды").
 * Содержит список записей.
 */
public final class MaredHandbookSection {

    public final String id;
    public final String name;
    public final String parent;
    public final boolean vanilla;
    public final int order;

    private final List<MaredHandbookEntry> entries = new ArrayList<>(16);

    public MaredHandbookSection(String id, String name, String parent, int order, boolean vanilla) {
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.order = order;
        this.vanilla = vanilla;
    }

    public void addEntry(MaredHandbookEntry e) {
        if (e != null) entries.add(e);
    }

    public List<MaredHandbookEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() { return entries.size(); }

    public boolean isEmpty() { return entries.isEmpty(); }
}
