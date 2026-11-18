package com.fixmer.mared.gui2.framework.components.editor;

/**
 * Одна undo-операция как дельта, а не снапшот всего документа.
 *
 * 0.3.1 (audit #67):
 *   Раньше EditorHistory хранил полный снимок документа на каждую
 *   букву. Скрипт 100 KB при 100 undo-шагах — это ~10 MB строк в
 *   памяти, плюс аллокации на каждый keystroke.
 *
 *   Теперь запись содержит только замену [start .. start+removed)
 *   на inserted. Для набора "hello" — один Edit с inserted="hello".
 *
 * Позиции: startLine/startCol — точка в документе ДО применения edit'а.
 * removedText и insertedText могут содержать '\n'.
 *
 * Курсор: cursorBefore — позиция курсора в документе ДО, cursorAfter —
 * ПОСЛЕ. Хранятся явно, потому что для backspace/forward-delete/
 * selection-replace связь с (startLine, startCol) нелинейна.
 */
public record Edit(
    int startLine, int startCol,
    String removedText, String insertedText,
    int cursorBeforeLine, int cursorBeforeCol,
    int cursorAfterLine, int cursorAfterCol
) {

    public Edit {
        if (removedText == null) removedText = "";
        if (insertedText == null) insertedText = "";
    }

    public boolean isEmpty() {
        return removedText.isEmpty() && insertedText.isEmpty();
    }

    public boolean isPureInsert() {
        return removedText.isEmpty() && !insertedText.isEmpty();
    }

    public boolean isPureDelete() {
        return insertedText.isEmpty() && !removedText.isEmpty();
    }

    public boolean isSingleLine() {
        return removedText.indexOf('\n') < 0
            && insertedText.indexOf('\n') < 0;
    }

    /**
     * Позиция сразу после `text`, если его вставить в (line, col).
     * Учитывает возможные переводы строк.
     */
    public static int[] advancePosition(int line, int col, String text) {
        if (text == null || text.isEmpty()) return new int[]{line, col};
        int lastNl = text.lastIndexOf('\n');
        if (lastNl < 0) {
            return new int[]{line, col + text.length()};
        }
        int newLines = 0;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) == '\n') newLines++;
        }
        return new int[]{
            line + newLines,
            text.length() - lastNl - 1
        };
    }
}