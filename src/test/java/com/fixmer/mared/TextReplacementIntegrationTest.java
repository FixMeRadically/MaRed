package com.fixmer.mared;

import com.fixmer.genesis.technology.editor.TextSearch;
import com.fixmer.mared.technology.editor.DocumentReplacement;
import com.fixmer.mared.gui2.framework.components.editor.*;
import com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextReplacementIntegrationTest {
    @Test void replacementsAreLiteralNonOverlappingAndCaseAware() {
        assertEquals(new TextSearch.Replacement("$1\\x$1\\x",2),new TextSearch("aa",true).replaceAll("aaaa","$1\\x",100));
        assertEquals("xba",new TextSearch("aba",true).replaceAll("ababa","x",100).text());
        assertEquals("X X 😀",new TextSearch("привет",false).replaceAll("ПРИВЕТ привет 😀","X",100).text());
        assertEquals("A x",new TextSearch("a",true).replaceAll("A a","x",100).text());
        assertEquals("b",new TextSearch("a",true).replaceAll("aba","",100).text());
        assertEquals("a",new TextSearch("",true).replaceAll("a","x",100).text());
        assertTrue(new TextSearch("a",false).matches("😀A",2,3));
        assertFalse(new TextSearch("a",true).matches("😀A",2,3));
        assertFalse(new TextSearch("a",false).matches("A",0,2));
    }
    @Test void linearReplacementMatchesReferenceOnRandomInputs() {
        var rng=new java.util.Random(704);
        for(int n=0;n<2000;n++) {
            var s=new StringBuilder();for(int i=rng.nextInt(200);i>0;i--)s.append("ab\n".charAt(rng.nextInt(3)));
            String source=s.toString(),query=n%2==0?"aba":"aa",replacement=n%3==0?"":"$1\\value";
            assertEquals(source.replace(query,replacement),new TextSearch(query,true).replaceAll(source,replacement,10000).text());
        }
    }
    @Test void replaceAllIsOneUndoStepAndPreservesDirtyState() {
        var session=new DocumentSession();session.storageId="saved";session.setInitialText("😀 one\none");session.document.setCursor(1,3);
        var result=new TextSearch("one",true).replaceAll(session.currentText(),"two",100);
        assertTrue(DocumentReplacement.apply(session,session.currentText(),result.text(),5));
        assertTrue(session.dirty);assertEquals(1,session.history.undoSize());assertEquals("😀 two\ntwo",session.currentText());
        assertTrue(session.history.undo(session.document));session.recomputeDirty();
        assertEquals("😀 one\none",session.currentText());assertFalse(session.dirty);assertEquals(1,session.document.cursorLine());assertEquals(3,session.document.cursorCol());
        assertTrue(session.history.redo(session.document));session.recomputeDirty();assertTrue(session.dirty);
        int version=session.document.contentVersion();
        assertFalse(DocumentReplacement.apply(session,session.currentText(),session.currentText(),0));assertEquals(version,session.document.contentVersion());assertEquals(1,session.history.undoSize());
    }
    @Test void limitsAndStaleSnapshotsLeaveDocumentAndHistoryUntouched() {
        assertThrows(IllegalArgumentException.class,()->new TextSearch("a",true).replaceAll("aaa","xxxx",10));
        assertThrows(IllegalArgumentException.class,()->new TextSearch("a",true).replaceAll("aaa","b",2));
        var session=new DocumentSession();session.setInitialText("safe");
        assertThrows(IllegalStateException.class,()->DocumentReplacement.apply(session,"old","new",0));
        assertThrows(IllegalArgumentException.class,()->DocumentReplacement.apply(session,"safe","я".repeat(3*1024*1024),0));
        assertEquals("safe",session.currentText());assertEquals(0,session.history.undoSize());assertFalse(session.dirty);
    }
    @Test void atomicReplacementNeverCoalescesWithTypingOrBackspace() {
        var doc=new EditorDocument();var history=new EditorHistory();doc.setValue("abc");
        var replacement=new Edit(0,0,"a","",0,0,0,0);doc.applyEdit(replacement);history.record(replacement);
        var deletion=new Edit(0,0,"b","",0,0,0,0);doc.applyEdit(deletion);history.recordCoalescible(deletion);
        assertEquals(2,history.undoSize());assertTrue(history.undo(doc));assertEquals("bc",doc.getValue());assertTrue(history.undo(doc));assertEquals("abc",doc.getValue());
        history.clear();doc.setValue("");
        for(int i=0;i<3;i++){var e=new Edit(0,i,"","x",0,i,0,i+1);doc.applyEdit(e);history.recordCoalescible(e);}
        assertEquals(1,history.undoSize());assertEquals(3L,history.retainedCharacters());assertTrue(history.undo(doc));assertEquals("",doc.getValue());
    }
    @Test void historyBoundsLargeReplacementsAndReleasesDiscardedRedo() {
        var history=new EditorHistory();var text="x".repeat(3*1024*1024);
        for(int i=0;i<4;i++)history.record(new Edit(0,0,text,text,0,0,0,0));
        assertEquals(1,history.undoSize());assertEquals(6L*1024*1024,history.retainedCharacters());
        var doc=new EditorDocument();doc.setValue(text);assertTrue(history.undo(doc));
        history.record(new Edit(0,0,"","a",0,0,0,1));assertEquals(1L,history.retainedCharacters());assertEquals(0,history.redoSize());
        history.clear();assertEquals(0L,history.retainedCharacters());
    }
}
