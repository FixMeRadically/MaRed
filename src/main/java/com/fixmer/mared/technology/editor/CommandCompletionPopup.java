package com.fixmer.mared.technology.editor;

import com.fixmer.genesis.technology.editor.ScriptHeads;
import com.fixmer.genesis.technology.editor.TextSearch;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.components.editor.Edit;
import com.fixmer.mared.gui2.framework.core.MaredBounds;
import com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession;
import com.fixmer.mared.technology.catalog.BrigadierCommandTools.Candidate;
import com.fixmer.mared.technology.catalog.MinecraftCommandTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Per-workspace request state. A late provider result cannot edit another document or cursor. */
public final class CommandCompletionPopup {
    private DocumentSession document;
    private MinecraftCommandTools.Session session;
    private ScriptHeads.Snippet snippet;
    private int version, line, column, selected, scroll;
    private long sequence;
    private List<Candidate> rows = List.of();
    private CompletableFuture<?> pending;
    private MaredBounds box = new MaredBounds(0, 0, 0, 0);
    private int visible;
    private String message = "";
    public String message() { return message; }
    public void dismiss() {
        sequence++; rows = List.of(); document = null; message = "";
        if (pending != null) pending.cancel(false);
        pending = null;
    }
    public boolean applicable(DocumentSession current) {
        return current != null && current == document && current.document.contentVersion() == version
            && current.document.cursorLine() == line && current.document.cursorCol() == column
            && session != null && session.current();
    }
    public void reconcile(DocumentSession current) { if (document != null && !applicable(current)) dismiss(); }
    public void request(DocumentSession current) {
        dismiss();
        if (current == null) return;
        String source = current.currentText();
        if (source.length() > ScriptDiagnostics.MAX_CHECK_CHARS) { message = MaredLang.get("mared.editor.check_too_large"); return; }
        int offset = current.document.cursorCol();
        for (int i = 0; i < current.document.cursorLine(); i++) offset += current.document.lineLength(i) + 1;
        var context = ScriptHeads.at(source, offset);
        if (context == null) { message = MaredLang.get("mared.editor.complete_context"); return; }
        if (context.input().dynamic()) { message = MaredLang.get("mared.editor.complete_dynamic"); return; }
        var connection = MinecraftCommandTools.session();
        if (connection == null) { message = MaredLang.get("mared.editor.complete_no_connection"); return; }
        document = current; snippet = context; session = connection;
        version = current.document.contentVersion(); line = current.document.cursorLine(); column = current.document.cursorCol();
        long request = ++sequence;
        selected = 0; scroll = 0;
        message = MaredLang.get("mared.editor.complete_wait");
        pending = session.tools().complete(context.input().text(), context.input().commandOffset(offset));
        @SuppressWarnings("unchecked") var result = (CompletableFuture<List<Candidate>>) pending;
        result.whenComplete((candidates, error) -> Minecraft.getInstance().execute(() -> {
            if (request != sequence || !applicable(current)) return;
            pending = null;
            if (error != null) { message = MaredLang.get("mared.editor.complete_failed"); rows = List.of(); return; }
            rows = List.copyOf(candidates);
            message = rows.isEmpty() ? MaredLang.get("mared.editor.complete_empty") : "";
        }));
    }
    private boolean accept() {
        if (rows.isEmpty() || !applicable(document)) { dismiss(); return true; }
        var candidate = rows.get(selected);
        String source = document.currentText();
        int cursor = column;
        for (int i = 0; i < line; i++) cursor += document.document.lineLength(i) + 1;
        int replacementEnd = snippet.input().replacementEnd(candidate.start(), candidate.end(), candidate.text(), snippet.input().commandOffset(cursor));
        int start = snippet.input().sourceOffset(candidate.start()), end = snippet.input().sourceOffset(replacementEnd);
        var from = TextSearch.position(source, start); var to = TextSearch.position(source, end);
        int[] after = Edit.advancePosition(from.line(), from.column(), candidate.text());
        var edit = new Edit(from.line(), from.column(), source.substring(start, end), candidate.text(),
            line, column, after[0], after[1]);
        document.history.record(edit);
        document.document.applyEdit(edit);
        document.recomputeDirty(); document.view.resetBlink();
        dismiss(); return true;
    }
    public boolean key(int key, int modifiers) {
        if (key == 256 && (document != null || !message.isEmpty())) { dismiss(); return true; }
        if (rows.isEmpty()) return false;
        if (modifiers == 0 && (key == 264 || key == 265)) {
            selected = Math.floorMod(selected + (key == 264 ? 1 : -1), rows.size());
            scroll = Math.max(0, Math.min(selected, Math.max(scroll, selected - Math.max(1, visible) + 1)));
            return true;
        }
        if (modifiers == 0 && (key == 257 || key == 335 || key == 258)) return accept();
        return false;
    }
    public boolean click(double x, double y, int button) {
        if (rows.isEmpty() || !box.contains(x, y)) return false;
        if (button == 0) { selected = Math.min(rows.size() - 1, scroll + (int)(y - box.y() - 4) / 14); selected = Math.max(scroll, selected); accept(); }
        return true;
    }
    public boolean scroll(double x, double y, double delta) {
        if (rows.isEmpty() || !box.contains(x, y)) return false;
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible), scroll + (delta < 0 ? 1 : delta > 0 ? -1 : 0))); return true;
    }
    public void render(GuiGraphics g, Font font, MaredBounds area, int caretX, int caretY) {
        if (rows.isEmpty() || area.width() < 80 || area.height() < 24) return;
        visible = Math.max(1, Math.min(8, (area.height() - 8) / 14));
        int height = Math.min(rows.size(), visible) * 14 + 8;
        int width = Math.min(area.width(), 280);
        int x = Math.max(area.x(), Math.min(area.right() - width, caretX));
        int y = Math.max(area.y(), Math.min(area.bottom() - height, caretY + 12));
        box = new MaredBounds(x, y, width, height);
        scroll = Math.min(scroll, Math.max(0, rows.size() - visible));
        g.pose().pushPose();
        try {
            g.pose().translate(0, 0, 30);
            g.fill(x, y, x + width, y + height, raised());
            g.renderOutline(x, y, width, height, edge());
            g.fill(x, y, x + 2, y + height, accent());
            for (int i = scroll; i < Math.min(rows.size(), scroll + visible); i++) {
                int rowY = y + 4 + (i - scroll) * 14;
                if (i == selected) g.fill(x + 3, rowY - 1, x + width - 3, rowY + 12, selected());
                String text = rows.get(i).text();
                g.drawString(font, com.fixmer.mared.gui2.framework.render.TextUtils.ellipsize(font, text, width - 12), x + 7, rowY + 2, text(), false);
            }
        } finally { g.pose().popPose(); }
    }
}
