package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.editor.DocumentSearch;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.io.IOException;

/** Snapshot adapter over the shared literal search engine; never reads project files. */
public final class ScriptSearch {
    public static final int MAX_QUERY=DocumentSearch.MAX_QUERY,MAX_HITS=DocumentSearch.MAX_RESULTS;
    public record Hit(String path,int line,int column,int length,String preview) {}
    public record Result(List<Hit> hits,boolean limited,Map<String,String> errors) {
        public Result{hits=List.copyOf(hits);errors=Map.copyOf(errors);}
        public Result(List<Hit> hits,boolean limited){this(hits,limited,Map.of());}
    }
    private ScriptSearch() {}
    private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();}
    private static Result convert(DocumentSearch.Result result){return new Result(result.hits().stream().map(h->new Hit(h.path(),h.line(),h.column(),h.matchedText().length(),h.preview())).toList(),result.limited(),result.errors());}
    public static Result files(List<String> paths,String query,BooleanSupplier cancelled) {
        check(cancelled);
        if(query.isEmpty())return new Result(paths.stream().limit(DocumentSearch.MAX_FILES).map(p->new Hit(p,-1,0,0,p)).toList(),paths.size()>DocumentSearch.MAX_FILES);
        try {return convert(DocumentSearch.search(paths,p->"",query,DocumentSearch.Mode.PATH,false,cancelled));}
        catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    public static Result project(List<String> paths,DocumentSearch.Reader reader,String query,BooleanSupplier cancelled) throws IOException {
        return convert(DocumentSearch.search(paths,reader,query,DocumentSearch.Mode.CONTENT,false,cancelled));
    }
    public static Result text(String path,String source,String query,BooleanSupplier cancelled) {
        check(cancelled);if(source.length()>4*1024*1024)throw new IllegalArgumentException("Document snapshot too large");
        try {return convert(DocumentSearch.search(List.of(path),p->source,query,DocumentSearch.Mode.CONTENT,false,cancelled));}
        catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
}
