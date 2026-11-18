package com.fixmer.mared.gui2.framework.components.editor;

/** UTF-16 coordinates shared by assisted edits; each mutation records exactly one undo delta. */
public final class EditorTextEdits {
    private EditorTextEdits() {}
    public static int offset(EditorDocument doc,int line,int column){
        int value=0;for(int i=0;i<line;i++)value+=doc.lineLength(i)+1;return value+column;
    }
    public static int[] position(String text,int offset){
        offset=Math.max(0,Math.min(text.length(),offset));int line=0,start=0;
        for(int i=0;i<offset;i++)if(text.charAt(i)=='\n'){line++;start=i+1;}
        return new int[]{line,offset-start};
    }
    public static void select(EditorDocument doc,int start,int end){
        String text=doc.getValue();int[] a=position(text,start),b=position(text,end);doc.clearSelection();doc.setCursor(b[0],b[1]);
        if(end!=start){doc.startSelection(a[0],a[1]);doc.extendSelection(b[0],b[1]);}
    }
    public static boolean replace(EditorDocument doc,EditorHistory history,int start,int end,String text){
        String before=doc.getValue();if(start<0||end<start||end>before.length())throw new IllegalArgumentException("Invalid edit range");
        if(before.substring(start,end).equals(text))return false;
        int[] at=position(before,start),after=Edit.advancePosition(at[0],at[1],text);
        var edit=new Edit(at[0],at[1],before.substring(start,end),text,doc.cursorLine(),doc.cursorCol(),after[0],after[1]);
        history.record(edit);doc.applyEdit(edit);return true;
    }
    public static boolean deleteLines(EditorDocument doc,EditorHistory history){
        int[] selection=doc.orderedSelection();int first=selection==null?doc.cursorLine():selection[0],last=selection==null?first:selection[2];
        if(selection!=null&&selection[3]==0&&last>first)last--;
        int start=offset(doc,first,0),end=offset(doc,last,doc.lineLength(last));
        if(last+1<doc.lineCount())end++;else if(first>0)start--;
        boolean changed=replace(doc,history,start,end,"");doc.setCursor(Math.min(first,doc.lineCount()-1),0);return changed;
    }
    public static boolean trimTrailing(EditorDocument doc,EditorHistory history){
        String before=doc.getValue();StringBuilder after=new StringBuilder(before.length());
        for(int i=0;i<doc.lineCount();i++){
            String row=doc.line(i);int end=row.length();while(end>0&&(row.charAt(end-1)==' '||row.charAt(end-1)=='\t'))end--;
            if(i>0)after.append('\n');after.append(row,0,end);
        }
        int line=doc.cursorLine(),column=doc.cursorCol();boolean changed=replace(doc,history,0,before.length(),after.toString());doc.setCursor(line,column);return changed;
    }
}
