package com.fixmer.mared.gui2.framework.components.editor;

import com.fixmer.genesis.technology.editor.CodeBrackets;
import java.util.ArrayList;
import java.util.List;

/** Bracket navigation follows the colour lexer's string/comment rules, without evaluating code. */
public final class CodeBracketNavigator {
    public record Pair(int open,int close) {}
    private CodeBracketNavigator() {}
    public static List<Pair> pairs(EditorDocument doc, String path) {
        return CodeBrackets.pairs(path, doc.lineCount(), doc::line).stream()
            .map(pair -> new Pair(pair.open(), pair.close())).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public static boolean jump(EditorDocument doc,String path){
        int at=EditorTextEdits.offset(doc,doc.cursorLine(),doc.cursorCol());var pairs=pairs(doc,path);
        for(int position:new int[]{at,at-1})for(Pair pair:pairs){
            if(pair.open()==position||pair.close()==position){EditorTextEdits.select(doc,pair.open()==position?pair.close():pair.open(),pair.open()==position?pair.close():pair.open());return true;}
        }
        return false;
    }
    public static boolean selectBlock(EditorDocument doc,String path){
        int[] selection=doc.orderedSelection();int start=EditorTextEdits.offset(doc,selection==null?doc.cursorLine():selection[0],selection==null?doc.cursorCol():selection[1]);
        int end=selection==null?start:EditorTextEdits.offset(doc,selection[2],selection[3]);Pair best=null;
        for(Pair pair:pairs(doc,path)){
            if(pair.open()>start||pair.close()<end||selection!=null&&pair.open()+1==start&&pair.close()==end)continue;
            if(best==null||pair.close()-pair.open()<best.close()-best.open())best=pair;
        }
        if(best==null)return false;EditorTextEdits.select(doc,best.open()+1,best.close());return true;
    }
}
