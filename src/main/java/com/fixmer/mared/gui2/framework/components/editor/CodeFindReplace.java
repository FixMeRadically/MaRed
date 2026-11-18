package com.fixmer.mared.gui2.framework.components.editor;

import java.util.*;
import java.util.regex.*;

/** Literal, bounded find/replace; a preview cannot apply to a changed document. */
public final class CodeFindReplace {
    public record Options(boolean matchCase,boolean wholeWord,boolean selectedOnly) {}
    public record Match(int start,int end,int line,String preview) {}
    public record Plan(String before,String replacement,List<Match> matches,int cursor) {
        public Plan {matches=List.copyOf(matches);}
        public Match next(){for(Match match:matches)if(match.start()>=cursor)return match;return matches.isEmpty()?null:matches.get(0);}
        public boolean apply(EditorDocument doc,EditorHistory history,boolean all){
            if(!doc.getValue().equals(before))throw new IllegalStateException("changed");
            if(matches.isEmpty())return false;
            Match first=all?matches.get(0):next(),last=all?matches.get(matches.size()-1):first;
            var result=new StringBuilder();int offset=first.start();
            for(Match match:all?matches:List.of(first)){
                result.append(before,offset,match.start()).append(replacement);offset=match.end();
            }
            boolean changed=EditorTextEdits.replace(doc,history,first.start(),last.end(),result.toString());
            EditorTextEdits.select(doc,first.start(),first.start()+replacement.length());return changed;
        }
    }
    private CodeFindReplace() {}
    private static boolean word(String text,int offset,boolean previous){
        if(previous&&offset<=0||!previous&&offset>=text.length())return false;
        return Character.isJavaIdentifierPart(previous?text.codePointBefore(offset):text.codePointAt(offset));
    }
    public static Plan plan(EditorDocument doc,String query,String replacement,Options options){
        if(query==null||query.isEmpty()||query.length()>256||query.indexOf('\n')>=0)throw new IllegalArgumentException("query");
        if(replacement==null||replacement.length()>8192||replacement.indexOf('\r')>=0)throw new IllegalArgumentException("replacement");
        String before=doc.getValue();if(before.length()>1024*1024)throw new IllegalArgumentException("limit");
        int start=0,end=before.length();
        if(options.selectedOnly()){
            int[] selection=doc.orderedSelection();if(selection==null)throw new IllegalArgumentException("selection");
            start=EditorTextEdits.offset(doc,selection[0],selection[1]);end=EditorTextEdits.offset(doc,selection[2],selection[3]);
        }
        var matcher=Pattern.compile(Pattern.quote(query),options.matchCase()?0:Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(before).region(start,end);
        var matches=new ArrayList<Match>();int row=0,rowStart=0,scan=0;
        while(matcher.find()){
            if(options.wholeWord()&&(word(before,matcher.start(),true)||word(before,matcher.end(),false)))continue;
            if(matches.size()>=5000)throw new IllegalArgumentException("limit");
            while(scan<matcher.start()){if(before.charAt(scan)=='\n'){row++;rowStart=scan+1;}scan++;}
            int rowEnd=before.indexOf('\n',matcher.end());if(rowEnd<0)rowEnd=before.length();
            int left=Math.max(rowStart,matcher.start()-45),right=Math.min(rowEnd,matcher.end()+60);
            String preview=before.substring(left,right).replace('\t',' ');
            matches.add(new Match(matcher.start(),matcher.end(),row,preview));
        }
        long size=(long)before.length()+(long)matches.size()*(replacement.length()-query.length());if(size>2L*1024*1024)throw new IllegalArgumentException("limit");
        return new Plan(before,replacement,matches,EditorTextEdits.offset(doc,doc.cursorLine(),doc.cursorCol()));
    }
}
