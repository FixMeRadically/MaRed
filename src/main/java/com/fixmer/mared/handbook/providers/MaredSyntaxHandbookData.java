package com.fixmer.mared.handbook.providers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.handbook.MaredHandbookData;
import com.fixmer.mared.handbook.MaredHandbookEntry;
import com.fixmer.mared.handbook.MaredHandbookSection;

public final class MaredSyntaxHandbookData implements MaredHandbookData {

    private final List<MaredHandbookSection> sections = new ArrayList<>(8);
    private final List<MaredHandbookEntry> allEntries = new ArrayList<>(64);
    private final Map<String, MaredHandbookEntry> byId = new HashMap<>(64);

    public MaredSyntaxHandbookData() {
    }

    @Override public String id() { return "syntax"; }
    @Override public String displayName() { return MaredLang.get("mared.handbook.syntax"); }

    @Override public List<MaredHandbookSection> sections() {
        return Collections.unmodifiableList(sections);
    }

    @Override public List<MaredHandbookEntry> allEntries() {
        return Collections.unmodifiableList(allEntries);
    }

    @Override public MaredHandbookEntry findById(String id) {
        return byId.get(id);
    }

    private MaredHandbookSection section(String id, String name, String parent, int order) {
        MaredHandbookSection s = new MaredHandbookSection(id, name, parent, order, false);
        sections.add(s);
        return s;
    }

    private void add(MaredHandbookSection s, MaredHandbookEntry e) {
        s.addEntry(e);
        allEntries.add(e);
        if (e.id != null) byId.put(e.id, e);
    }
}
