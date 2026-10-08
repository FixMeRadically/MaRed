package com.fixmer.mared;

import com.fixmer.genesis.technology.editor.TextSearch;
import com.fixmer.mared.technology.editor.ScriptDiagnostics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorDocumentToolsIntegrationTest {
    @Test void literalSearchWrapsAndPreservesUtf16Offsets() {
        var query = new TextSearch("aba", true);
        assertEquals(new TextSearch.Match(2, 5), query.find("ababa", 1, false));
        assertEquals(new TextSearch.Match(2, 5), query.find("ababa", 0, true));
        assertNull(new TextSearch("", false).find("abc", 0, false));
        assertNull(new TextSearch("a", true).find("A", 0, false));
        assertEquals(new TextSearch.Match(0, 6), new TextSearch("привет", false).find("ПРИВЕТ", 0, false));
        assertEquals(new TextSearch.Match(0, 1), new TextSearch("i", false).find("İx", 0, false));
        assertEquals(new TextSearch.Position(1, 2), TextSearch.position("a\n😀x", 4));
    }

    @Test void parserDiagnosticsKeepOriginalLinesAndBoundNesting() {
        assertEquals(3, ScriptDiagnostics.check("\n\nwait").line());
        assertEquals(3, ScriptDiagnostics.check("\n{\nwait\n}").line());
        assertTrue(ScriptDiagnostics.check("print ok").valid());
        assertTrue(ScriptDiagnostics.check("{".repeat(200) + "}".repeat(200)).line() > 0);
        assertEquals(-1, ScriptDiagnostics.check("x".repeat(ScriptDiagnostics.MAX_CHECK_CHARS + 1)).line());
    }

    @Test void literalSearchMatchesReferenceAcrossRandomDocuments() {
        var random = new java.util.Random(5061);
        for (int sample = 0; sample < 1_000; sample++) {
            var text = new StringBuilder();
            int size = random.nextInt(80);
            for (int i = 0; i < size; i++) text.append("abc\n".charAt(random.nextInt(4)));
            String source = text.toString();
            String query = switch (random.nextInt(4)) { case 0 -> "aa"; case 1 -> "ab"; case 2 -> "\n"; default -> "abc"; };
            int from = random.nextInt(size + 1);
            int forward = source.indexOf(query, from);
            if (forward < 0) forward = source.indexOf(query);
            int backward = from == 0 ? -1 : source.lastIndexOf(query, from - 1);
            if (backward < 0) backward = source.lastIndexOf(query);
            var search = new TextSearch(query, true);
            assertEquals(forward < 0 ? null : new TextSearch.Match(forward, forward + query.length()), search.find(source, from, false));
            assertEquals(backward < 0 ? null : new TextSearch.Match(backward, backward + query.length()), search.find(source, from, true));
        }
    }
}
