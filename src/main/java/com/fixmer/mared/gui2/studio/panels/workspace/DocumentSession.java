package com.fixmer.mared.gui2.studio.panels.workspace;

import com.fixmer.mared.gui2.framework.components.editor.EditorDocument;
import com.fixmer.mared.gui2.framework.components.editor.EditorHistory;
import com.fixmer.mared.gui2.framework.components.editor.EditorView;

/**
 * Одна открытая вкладка документа.
 *
 * 0.3.2 (multi-document workspace):
 *   Раньше Workspace хранил один currentFileName + один EditorDocument.
 *   Теперь каждый документ имеет свой session:
 *     - EditorDocument / EditorHistory / EditorView — независимая
 *       история undo/redo и независимый scroll;
 *     - storageId — имя файла на диске (null = "untitled");
 *     - dirty + lastSavedText — для индикации изменений;
 *     - saveInFlight / pendingSave — сериализация записи per-document;
 *     - exists — false после FileDeletedEvent; при этом документ
 *       остаётся открытым, но с флагом "удалён" (не воскрешаем при
 *       Save без явного действия пользователя).
 *
 * Lifecycle:
 *   - создаётся при openFile() или newUntitled();
 *   - активируется по клику на таб;
 *   - закрывается при закрытии таба (с проверкой dirty).
 */
public final class DocumentSession {

    public final EditorDocument document = new EditorDocument();
    public final EditorHistory  history  = new EditorHistory();
    public final EditorView     view     = new EditorView();

    /** Имя файла на диске. null — untitled-документ. */
    public String storageId;

    /** Текст на момент последнего успешного save. */
    public String lastSavedText = "";

    /** Изменён с момента последнего save. */
    public boolean dirty = false;

    /** true, если файл существует на диске. false после FileDeletedEvent. */
    public boolean exists = true;

    // ============================================================
    //  Save state (per-document)
    // ============================================================

    public boolean saveInFlight = false;
    public boolean pendingSave  = false;

    /** Файл, который хотят открыть, но save текущего провалился. */
    public String pendingOpenFile = null;

    // ============================================================
    //  Identity
    // ============================================================

    /** Уникальный display title. */
    public String title() {
        if (storageId != null) return storageId;
        return "(untitled)";
    }

    /** Есть ли смысл сохранять (есть имя И есть куда сохранять). */
    public boolean canSave() {
        return storageId != null && exists;
    }

    // ============================================================
    //  Editor bridge
    // ============================================================

    public String currentText() {
        return document.getValue();
    }

    public void setInitialText(String text) {
        if (text == null) text = "";
        history.clear();
        document.setValue(text);
        view.resetScroll();
        lastSavedText = text;
        dirty = false;
    }

    public void recomputeDirty() {
        if (storageId == null) {
            // untitled всегда dirty, если непустой
            dirty = !document.getValue().isEmpty();
            return;
        }
        dirty = !document.getValue().equals(lastSavedText);
    }
}