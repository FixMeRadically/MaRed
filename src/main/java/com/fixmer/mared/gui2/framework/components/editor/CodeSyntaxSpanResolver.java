package com.fixmer.mared.gui2.framework.components.editor;

import com.fixmer.genesis.technology.editor.CodeSyntaxLexer;
import java.util.*;

/** Per-document state checkpoints and bounded visible-line cache; all work stays on the render thread. */
public final class CodeSyntaxSpanResolver implements SpanResolver {
    private static final int MAX_LINES=200_000,MAX_LINE_CHARS=32_768,MAX_FRAME_LINES=2048,MAX_FRAME_CHARS=65_536,CACHE_LINES=256;
    private static final int[] DARK={0xFF569CD6,0xFFC586C0,0xFFCE9178,0xFFB5CEA8,0xFF6A9955,0xFF4EC9B0,0xFFDCDCAA,0xFFDCDCAA,0xFF569CD6,0xFF4FC1FF,0xFF9CDCFE,0xFF9CDCFE,0};
    private static final int[] LIGHT={0xFF0000FF,0xFFAF00DB,0xFFA31515,0xFF098658,0xFF008000,0xFF267F99,0xFF795E26,0xFF795E26,0xFF0000FF,0xFF0070C1,0xFF001080,0xFF0451A5,0};
    private static final class Entry {
        final List<CodeSyntaxLexer.Token> tokens;List<TextSpan> spans;int palette=-1;
        Entry(List<CodeSyntaxLexer.Token> tokens){this.tokens=tokens;}
    }
    private final CodeSyntaxLexer.Language language;
    private final ArrayList<CodeSyntaxLexer.State> boundaries=new ArrayList<>();
    private final LinkedHashMap<Integer,Entry> cache=new LinkedHashMap<>(32,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Integer,Entry> entry){return size()>CACHE_LINES;}};
    private EditorDocument document;
    private int revision,palette,baseColor,linesRemaining,charsRemaining,unsupportedFrom=Integer.MAX_VALUE;
    private long deadline;
    private boolean light,frameOpen;
    public CodeSyntaxSpanResolver(CodeSyntaxLexer.Language language,boolean light,int baseColor){this.language=Objects.requireNonNull(language);this.light=light;this.baseColor=baseColor;}
    public CodeSyntaxLexer.Language language(){return language;}
    public void theme(boolean light,int baseColor){if(this.light!=light||this.baseColor!=baseColor){this.light=light;this.baseColor=baseColor;palette++;}}
    @Override public int baseColor(){return baseColor;}
    public int color(CodeSyntaxLexer.Kind kind){return kind==CodeSyntaxLexer.Kind.OPERATOR?baseColor:(light?LIGHT:DARK)[kind.ordinal()];}
    private void sync(EditorDocument doc){
        if(document!=doc){document=doc;revision=doc.contentVersion();boundaries.clear();boundaries.add(CodeSyntaxLexer.State.NORMAL);cache.clear();unsupportedFrom=Integer.MAX_VALUE;return;}
        if(revision==doc.contentVersion())return;
        int from=doc.changedLineSince(revision);revision=doc.contentVersion();
        if(from<boundaries.size()-1)boundaries.subList(from+1,boundaries.size()).clear();
        cache.keySet().removeIf(line->line>=from);
        if(from<=unsupportedFrom)unsupportedFrom=Integer.MAX_VALUE;
    }
    @Override public void beginFrame(EditorDocument doc){sync(doc);linesRemaining=MAX_FRAME_LINES;charsRemaining=MAX_FRAME_CHARS;deadline=0;frameOpen=true;}
    @Override public List<TextSpan> resolveSpans(int line,String text,EditorDocument doc){
        if(doc==null||text==null||line<0||line>=doc.lineCount()||language==CodeSyntaxLexer.Language.PLAIN||line>=MAX_LINES)return List.of();
        sync(doc);Entry cached=cache.get(line);if(cached!=null)return spans(cached);
        if(!frameOpen)beginFrame(doc);
        if(deadline==0)deadline=System.nanoTime()+3_000_000L;
        if(line>=unsupportedFrom)return List.of();
        // Scan missing prefix states progressively instead of rescanning from line zero on every draw.
        while(boundaries.size()-1<=line){
            int index=boundaries.size()-1;
            if(linesRemaining<=0||charsRemaining<=0||System.nanoTime()-deadline>=0)return List.of();
            String value=index==line?text:doc.line(index);
            if(value.length()>MAX_LINE_CHARS){unsupportedFrom=index;return List.of();}
            var parsed=CodeSyntaxLexer.lex(language,value,boundaries.get(index));
            boundaries.add(parsed.next());cache.put(index,new Entry(parsed.tokens()));linesRemaining--;charsRemaining-=Math.max(1,value.length());
        }
        cached=cache.get(line);
        if(cached==null){
            // A backwards scroll may revisit an evicted line while its input state is still cached.
            if(linesRemaining<=0||charsRemaining<=0||System.nanoTime()-deadline>=0)return List.of();
            if(text.length()>MAX_LINE_CHARS)return List.of();
            var parsed=CodeSyntaxLexer.lex(language,text,boundaries.get(line));cached=new Entry(parsed.tokens());cache.put(line,cached);linesRemaining--;charsRemaining-=Math.max(1,text.length());
        }
        return spans(cached);
    }
    private List<TextSpan> spans(Entry entry){
        if(entry.palette!=palette){var spans=new ArrayList<TextSpan>(entry.tokens.size());for(var token:entry.tokens)spans.add(new TextSpan(token.start(),token.end(),color(token.kind())));entry.spans=List.copyOf(spans);entry.palette=palette;}
        return entry.spans;
    }
}
