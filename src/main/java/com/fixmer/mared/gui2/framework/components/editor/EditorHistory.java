package com.fixmer.mared.gui2.framework.components.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * История undo/redo как список дельт.
 *
 * 0.3.1 (audit #67):
 *   Раньше хранила полный снимок документа на каждую edit-операцию.
 *   На 100 KB-скрипте 100 undo-шагов — ~10 MB string content + GC на
 *   каждый keystroke.
 *
 *   Теперь хранит Edit (startLine, startCol, removed, inserted).
 *   Плюс coalescing: набор "hello" даёт одну undo-запись, а не пять.
 *
 * Публичный контракт:
 *   history.record(edit)             — атомарная запись (paste, replace).
 *   history.recordCoalescible(edit)  — попытаться смержить с предыдущей
 *                                      (набор, backspace, forward delete).
 *   history.undo(doc)                — откатить, вернуть true/false.
 *   history.redo(doc)                — повторить.
 *   history.clear()                  — сброс (setValue).
 *
 * Коалесинг работает только для однострочных операций в пределах
 * COALESCE_WINDOW_MS. Многострочные (paste, insertNewLine, merge lines)
 * всегда atomic.
 */
public final class EditorHistory {

    private static final int  MAX_UNDO = 500;
    private static final long MAX_RETAINED_CHARS = 8L * 1024 * 1024;
    private long retainedCharacters;
    private static long size(Edit e) { return (long) e.removedText().length() + e.insertedText().length(); }
    private void discardRedo() { for (Edit e : redoStack) retainedCharacters -= size(e); redoStack.clear(); }
    private void trim() {
        // Keep the latest operation undoable even for an unusually large external edit.
        while (undoStack.size() > MAX_UNDO || undoStack.size() > 1 && retainedCharacters > MAX_RETAINED_CHARS)
            retainedCharacters -= size(undoStack.removeLast());
    }
    public long retainedCharacters() { return retainedCharacters; }
    private static final long COALESCE_WINDOW_NANOS = 500_000_000L; // 500ms

    private final Deque<Edit> undoStack = new ArrayDeque<>(MAX_UNDO);
    private final Deque<Edit> redoStack = new ArrayDeque<>(MAX_UNDO);

    private long lastRecordNanos = 0L;

    // ============================================================
    //  Record
    // ============================================================

    /** Атомарная запись. Сбрасывает redo. */
    public void record(Edit e) {
        if (e == null || e.isEmpty()) return;

        undoStack.push(e);
        retainedCharacters += size(e);
        discardRedo();
        trim();
        lastRecordNanos = 0L; // Atomic edits never merge with later typing/deletion.
    }

    /**
     * Попытаться смержить с предыдущей записью.
     * Если окно коалесинга истекло или операция несовместима —
     * ведёт себя как record().
     */
    public void recordCoalescible(Edit e) {
        if (e == null || e.isEmpty()) return;

        long now = System.nanoTime();
        boolean withinWindow = (now - lastRecordNanos) < COALESCE_WINDOW_NANOS;

        Edit prev = undoStack.peek();
        if (withinWindow && prev != null && canCoalesce(prev, e)) {
            undoStack.pop(); retainedCharacters -= size(prev);
            Edit merged = merge(prev, e);
            undoStack.push(merged); retainedCharacters += size(merged);
            discardRedo(); trim();
            lastRecordNanos = now;
            return;
        }

        record(e);
        lastRecordNanos = now;
    }

    // ============================================================
    //  Undo / Redo
    // ============================================================

    public boolean undo(EditorDocument doc) {
        if (undoStack.isEmpty()) return false;
        Edit e = undoStack.pop();
        doc.revertEdit(e);
        redoStack.push(e);
        lastRecordNanos = 0L;
        return true;
    }

    public boolean redo(EditorDocument doc) {
        if (redoStack.isEmpty()) return false;
        Edit e = redoStack.pop();
        doc.applyEdit(e);
        undoStack.push(e);
        lastRecordNanos = 0L;
        return true;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        lastRecordNanos = 0L;
        retainedCharacters = 0L;
    }

    public int undoSize() { return undoStack.size(); }
    public int redoSize() { return redoStack.size(); }

    // ============================================================
    //  Coalescing
    // ============================================================

    private static boolean canCoalesce(Edit prev, Edit next) {
        // Многострочные — никогда.
        if (!prev.isSingleLine() || !next.isSingleLine()) return false;

        // Insert + Insert
        if (prev.isPureInsert() && next.isPureInsert()) {
            if (prev.startLine() != next.startLine()) return false;
            int prevEndCol = prev.startCol() + prev.insertedText().length();
            return next.startCol() == prevEndCol;
        }

        // Delete + Delete
        if (prev.isPureDelete() && next.isPureDelete()) {
            if (prev.startLine() != next.startLine()) return false;

            // Backspace: next.startCol + next.removed.length == prev.startCol
            if (prev.startCol() == next.startCol()
                    + next.removedText().length()) {
                return true;
            }
            // Forward delete: same position
            if (prev.startCol() == next.startCol()) {
                return true;
            }
            return false;
        }

        return false;
    }

    private static Edit merge(Edit prev, Edit next) {
        // Insert + Insert: пред = (L, C), вставка = prev.text + next.text
        if (prev.isPureInsert() && next.isPureInsert()) {
            return new Edit(
                prev.startLine(), prev.startCol(),
                "",
                prev.insertedText() + next.insertedText(),
                prev.cursorBeforeLine(), prev.cursorBeforeCol(),
                next.cursorAfterLine(), next.cursorAfterCol()
            );
        }

        // Backspace: prev перед next, объединяем по next.startCol
        if (prev.startCol() == next.startCol()
                + next.removedText().length()) {
            return new Edit(
                next.startLine(), next.startCol(),
                next.removedText() + prev.removedText(),
                "",
                prev.cursorBeforeLine(), prev.cursorBeforeCol(),
                next.cursorAfterLine(), next.cursorAfterCol()
            );
        }

        // Forward delete: prev и next в одной позиции
        return new Edit(
            prev.startLine(), prev.startCol(),
            prev.removedText() + next.removedText(),
            "",
            prev.cursorBeforeLine(), prev.cursorBeforeCol(),
            next.cursorAfterLine(), next.cursorAfterCol()
        );
    }
}