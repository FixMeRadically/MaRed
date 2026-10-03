package com.fixmer.mared.gui2.studio.panels.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Контейнер открытых документов.
 *
 * 0.3.2:
 *   Хранит список DocumentSession, активный индекс, обеспечивает
 *   поиск по storageId. Логика UI-переключения — в WorkspaceComponent.
 *
 * Не потокобезопасен — всё с main thread.
 */
public final class WorkspaceHost {

    private final List<DocumentSession> documents = new ArrayList<>(8);
    private int activeIndex = -1;

    // ============================================================
    //  Чтение
    // ============================================================

    public List<DocumentSession> documents() {
        return Collections.unmodifiableList(documents);
    }

    public int count() { return documents.size(); }

    public int activeIndex() { return activeIndex; }

    public DocumentSession active() {
        if (activeIndex < 0 || activeIndex >= documents.size()) return null;
        return documents.get(activeIndex);
    }

    public DocumentSession byStorageId(String storageId) {
        if (storageId == null) return null;
        for (DocumentSession s : documents) {
            if (storageId.equals(s.storageId)) return s;
        }
        return null;
    }

    public DocumentSession byIndex(int idx) {
        if (idx < 0 || idx >= documents.size()) return null;
        return documents.get(idx);
    }

    public boolean isEmpty() { return documents.isEmpty(); }

    /** Индекс документа. -1 если не найден. */
    public int indexOf(DocumentSession s) {
        return documents.indexOf(s);
    }

    // ============================================================
    //  Мутации
    // ============================================================

    public void add(DocumentSession session) {
        if (session == null) return;
        documents.add(session);
        activeIndex = documents.size() - 1;
    }

    public void addAt(DocumentSession session, int idx) {
        if (session == null) return;
        int clamped = Math.max(0, Math.min(idx, documents.size()));
        documents.add(clamped, session);
        activeIndex = clamped;
    }

    public boolean remove(DocumentSession session) {
        if (session == null) return false;
        int idx = documents.indexOf(session);
        if (idx < 0) return false;

        documents.remove(idx);

        if (documents.isEmpty()) {
            activeIndex = -1;
            return true;
        }
        // Активируем соседа: следующий, или предыдущий если последний.
        if (idx <= activeIndex) {
            activeIndex--;
        }
        if (activeIndex < 0) activeIndex = 0;
        if (activeIndex >= documents.size()) activeIndex = documents.size() - 1;
        return true;
    }

    public void activate(DocumentSession session) {
        int idx = documents.indexOf(session);
        if (idx >= 0) activeIndex = idx;
    }

    public void activate(int idx) {
        if (idx < 0 || idx >= documents.size()) return;
        activeIndex = idx;
    }

    public void activateNext() {
        if (documents.isEmpty()) return;
        activeIndex = (activeIndex + 1) % documents.size();
    }

    public void activatePrev() {
        if (documents.isEmpty()) return;
        activeIndex = (activeIndex - 1 + documents.size()) % documents.size();
    }

    /** Есть ли среди документов хотя бы один грязный. */
    public boolean hasAnyDirty() {
        for (DocumentSession s : documents) {
            if (s.dirty) return true;
        }
        return false;
    }

    /** Все грязные документы. */
    public List<DocumentSession> dirtyDocuments() {
        List<DocumentSession> out = new ArrayList<>(2);
        for (DocumentSession s : documents) {
            if (s.dirty) out.add(s);
        }
        return out;
    }
}