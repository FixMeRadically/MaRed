package com.fixmer.mared.technology.editor;

import com.fixmer.mared.gui2.framework.components.editor.Edit;
import com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession;
import com.fixmer.genesis.technology.editor.TextSearch;

/** One undo entry, containing only the changed span, without storage or network access. */
public final class DocumentReplacement {
    public static final int MAX_OUTPUT_CHARS = 4 * 1024 * 1024;
    private DocumentReplacement() {}

    public static boolean apply(DocumentSession session, String before, String after, int cursorOffset) {
        if (!session.currentText().equals(before)) throw new IllegalStateException("document changed");
        if (after.indexOf('\r') >= 0) throw new IllegalArgumentException("replacement text must use LF");
        if (after.length() > MAX_OUTPUT_CHARS || after.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4 * 1024 * 1024)
            throw new IllegalArgumentException("replacement size limit");
        if (before.equals(after)) return false;
        int prefix = 0, endBefore = before.length(), endAfter = after.length();
        while (prefix < endBefore && prefix < endAfter && before.charAt(prefix) == after.charAt(prefix)) prefix++;
        if (prefix > 0 && Character.isHighSurrogate(before.charAt(prefix - 1))) prefix--;
        while (endBefore > prefix && endAfter > prefix && before.charAt(endBefore - 1) == after.charAt(endAfter - 1)) { endBefore--; endAfter--; }
        var start = TextSearch.position(before, prefix);
        var cursor = TextSearch.position(after, cursorOffset);
        var doc = session.document;
        var edit = new Edit(start.line(), start.column(), before.substring(prefix, endBefore), after.substring(prefix, endAfter),
            doc.cursorLine(), doc.cursorCol(), cursor.line(), cursor.column());
        doc.applyEdit(edit);
        session.history.record(edit);
        session.recomputeDirty();
        session.view.resetBlink();
        return true;
    }
}
