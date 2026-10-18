package com.fixmer.mared.gui2.framework.components.editor;

import java.util.*;

/** Line/block commands share one delta-history operation and preserve a useful block selection. */
public final class EditorLineActions {
    public enum Action { INDENT, OUTDENT, DUPLICATE, UP, DOWN, COMMENT }
    private EditorLineActions() { }
    public static boolean apply(EditorDocument doc,EditorHistory history,Action action,String unit,String prefix){
        int[] selection=doc.orderedSelection();boolean selected=selection!=null;
        if(action==Action.INDENT&&!selected){
            if(unit==null||unit.isEmpty())unit="    ";int line=doc.cursorLine(),column=doc.cursorCol();
            var edit=new Edit(line,column,"",unit,line,column,line,column+unit.length());history.record(edit);doc.applyEdit(edit);return true;
        }
        int first=selected?selection[0]:doc.cursorLine(),last=selected?selection[2]:first;
        if(selected&&selection[3]==0&&last>first)last--;
        int count=last-first+1,start=first,end=last,afterLine=doc.cursorLine(),afterCol=doc.cursorCol();
        var lines=new ArrayList<String>();for(int i=first;i<=last;i++)lines.add(doc.line(i));
        int selectFirst=first,selectLast=last;
        switch(action){
            case DUPLICATE -> {var copy=new ArrayList<>(lines);lines.addAll(copy);afterLine+=count;selectFirst+=count;selectLast+=count;}
            case UP -> {if(first==0)return false;start--;lines.add(doc.line(start));afterLine--;selectFirst--;selectLast--;}
            case DOWN -> {if(last+1>=doc.lineCount())return false;end++;lines.add(0,doc.line(end));afterLine++;selectFirst++;selectLast++;}
            default -> {
                if(unit==null||unit.isEmpty())unit="    ";
                if(action==Action.COMMENT&&(prefix==null||prefix.isEmpty()))return false;
                boolean uncomment=action==Action.COMMENT&&lines.stream().filter(s->!s.isBlank()).allMatch(s->s.stripLeading().startsWith(prefix));
                for(int i=0;i<lines.size();i++){
                    String before=lines.get(i),after=before;int columnDelta=0;
                    if(action==Action.INDENT){after=unit+before;columnDelta=unit.length();}
                    else if(action==Action.OUTDENT){int remove=before.startsWith("\t")?1:0;if(remove==0)while(remove<Math.min(unit.length(),before.length())&&before.charAt(remove)==' ')remove++;after=before.substring(remove);columnDelta=-remove;}
                    else if(!before.isBlank()){
                        int indent=0;while(indent<before.length()&&(before.charAt(indent)==' '||before.charAt(indent)=='\t'))indent++;
                        if(uncomment){int remove=prefix.length();if(indent+remove<before.length()&&before.charAt(indent+remove)==' ')remove++;after=before.substring(0,indent)+before.substring(indent+remove);if(afterLine==first+i&&afterCol>indent)columnDelta=-Math.min(remove,afterCol-indent);}
                        else{after=before.substring(0,indent)+prefix+" "+before.substring(indent);if(afterLine==first+i&&afterCol>=indent)columnDelta=prefix.length()+1;}
                    }
                    if(afterLine==first+i)afterCol=Math.max(0,afterCol+columnDelta);lines.set(i,after);
                }
            }
        }
        String removed=doc.getTextRange(start,0,end,doc.lineLength(end)),inserted=String.join("\n",lines);
        if(removed.equals(inserted))return false;
        if(selected){afterLine=selectLast;afterCol=lines.get(selectLast-start).length();}
        int localLine=Math.max(0,Math.min(lines.size()-1,afterLine-start));afterCol=Math.min(afterCol,lines.get(localLine).length());
        var edit=new Edit(start,0,removed,inserted,doc.cursorLine(),doc.cursorCol(),afterLine,afterCol);
        history.record(edit);doc.applyEdit(edit);
        if(selected){doc.startSelection(selectFirst,0);doc.extendSelection(selectLast,doc.lineLength(selectLast));}
        return true;
    }
    public static String commentPrefix(String path){
        if(path==null)return "";String value=path.toLowerCase(Locale.ROOT);
        if(value.endsWith(".py")||value.endsWith(".toml")||value.endsWith(".properties"))return "#";
        for(String suffix:List.of(".java",".rs",".cpp",".c",".h",".hpp",".gradle",".kts",".js"))if(value.endsWith(suffix))return "//";
        return "";
    }
}
