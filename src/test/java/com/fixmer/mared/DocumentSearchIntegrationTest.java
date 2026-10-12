package com.fixmer.mared;

import com.fixmer.genesis.technology.editor.DocumentSearch;
import com.fixmer.genesis.technology.storage.TextRepository;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentSearchIntegrationTest {
    @Test void pathsAndContentIncludeNestedFiles() throws Exception {
        var repo = new TextRepository(Files.createTempDirectory("genesis-search-"));
        repo.createDirectory("quests"); repo.create("quests/start", "hello"); repo.create("other", "HELLO");
        var names = DocumentSearch.search(repo,"quests/",DocumentSearch.Mode.PATH,false,()->false);
        assertEquals("quests/start",names.hits().get(0).path()); assertEquals(-1,names.hits().get(0).line());
        var all = DocumentSearch.search(repo,"hello",DocumentSearch.Mode.CONTENT,false,()->false);
        assertEquals(2,all.hits().size()); assertFalse(all.limited());
        assertEquals(1,DocumentSearch.search(repo,"hello",DocumentSearch.Mode.CONTENT,true,()->false).hits().size());
    }
    @Test void coordinatesMatchEditorNormalizationAndOverlaps() throws Exception {
        var repo = new TextRepository(Files.createTempDirectory("genesis-coordinates-"));
        repo.create("file","first\r\n😀 ababa\r\nПРИВЕТ");
        var result=DocumentSearch.search(repo,"aba",DocumentSearch.Mode.CONTENT,true,()->false);
        assertEquals(2,result.hits().size()); assertEquals(1,result.hits().get(0).line());
        assertEquals(3,result.hits().get(0).column()); assertEquals(5,result.hits().get(1).column());
        assertEquals(2,DocumentSearch.search(repo,"привет",DocumentSearch.Mode.CONTENT,false,()->false).hits().get(0).line());
    }
    @Test void errorsAreReportedWithoutDroppingReadableResults() throws Exception {
        Path root=Files.createTempDirectory("genesis-search-errors-");var repo=new TextRepository(root);
        repo.create("bad","base"); Files.write(root.resolve("bad.txt"),new byte[]{(byte)0xc3,(byte)0x28});repo.create("good","needle");
        var result=DocumentSearch.search(repo,"needle",DocumentSearch.Mode.CONTENT,true,()->false);
        assertEquals(1,result.hits().size());assertEquals("good",result.hits().get(0).path());assertTrue(result.errors().containsKey("bad"));
        assertEquals(1,DocumentSearch.search(repo,"bad",DocumentSearch.Mode.PATH,false,()->false).hits().size());
    }
    @Test void queryAndResultLimitsAreExplicit() throws Exception {
        var repo=new TextRepository(Files.createTempDirectory("genesis-search-limits-"));repo.create("file","a".repeat(500));repo.create("literal","a.b axb");
        assertEquals(1,DocumentSearch.search(repo,"a.b",DocumentSearch.Mode.CONTENT,true,()->false).hits().size());
        var result=DocumentSearch.search(repo,"a",DocumentSearch.Mode.CONTENT,true,()->false);
        assertEquals(DocumentSearch.MAX_RESULTS,result.hits().size());assertTrue(result.limited());
        assertTrue(result.hits().stream().allMatch(hit->hit.preview().length()<=162));
        assertEquals(List.of(),DocumentSearch.search(repo,"",DocumentSearch.Mode.CONTENT,false,()->false).hits());
        assertThrows(IllegalArgumentException.class,()->DocumentSearch.search(repo,"a\nb",DocumentSearch.Mode.CONTENT,false,()->false));
        assertThrows(IllegalArgumentException.class,()->DocumentSearch.search(repo,"a".repeat(129),DocumentSearch.Mode.CONTENT,false,()->false));
    }
    @Test void totalTextBudgetIsReportedAsPartial() throws Exception {
        var repo=new TextRepository(Files.createTempDirectory("genesis-search-budget-"));
        for(int i=0;i<3;i++)repo.create("file"+i,"x".repeat(3*1024*1024));
        var result=DocumentSearch.search(repo,"missing",DocumentSearch.Mode.CONTENT,false,()->false);
        assertTrue(result.limited());assertEquals(3,result.scanned());assertTrue(result.hits().isEmpty());
    }
    @Test void cancellationInterruptsLongSingleLineSearch() throws Exception {
        var repo=new TextRepository(Files.createTempDirectory("genesis-search-cancel-"));repo.create("file","x".repeat(100_000));
        var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->DocumentSearch.search(repo,"missing",DocumentSearch.Mode.CONTENT,false,()->calls.incrementAndGet()>4));
        assertTrue(calls.get()>=5);
    }
}
