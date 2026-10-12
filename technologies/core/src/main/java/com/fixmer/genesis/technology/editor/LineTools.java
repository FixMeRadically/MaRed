package com.fixmer.genesis.technology.editor;

import java.util.*;

/** Pure complete-line edits. Coordinates are zero-based UTF-16; text must already use LF. */
public final class LineTools {
    public enum Operation { DUPLICATE, UP, DOWN, COMMENT }
    public record Result(String text,int first,int last,int cursorLine,int cursorColumn,int lastColumn) {}
    private LineTools() {}
    public static Result edit(String text,int first,int last,int cursorLine,int cursorColumn,Operation operation,int limit) {
        Objects.requireNonNull(operation);
        if(text.indexOf('\r')>=0||text.length()>limit)throw new IllegalArgumentException("Invalid or oversized document");
        var lines=new ArrayList<>(Arrays.asList(text.split("\n",-1)));
        if(first<0||last<first||last>=lines.size())throw new IllegalArgumentException("Invalid line range");
        cursorLine=Math.max(first,Math.min(last,cursorLine));cursorColumn=Math.max(0,Math.min(lines.get(cursorLine).length(),cursorColumn));
        switch(operation){
            case DUPLICATE -> {
                var block=new ArrayList<>(lines.subList(first,last+1));
                long added=block.size();for(String line:block)added+=line.length();
                if((long)text.length()+added>limit)throw new IllegalArgumentException("Output size limit");
                lines.addAll(last+1,block);int count=last-first+1;first+=count;last+=count;cursorLine+=count;
            }
            case UP -> {if(first>0){String previous=lines.remove(first-1);lines.add(last,previous);first--;last--;cursorLine--;}}
            case DOWN -> {if(last+1<lines.size()){String next=lines.remove(last+1);lines.add(first,next);first++;last++;cursorLine++;}}
            case COMMENT -> {
                boolean all=true,nonblank=false;int nonblankCount=0;
                for(int i=first;i<=last;i++){String line=lines.get(i);int indent=indent(line);if(indent==line.length())continue;nonblank=true;nonblankCount++;all&=line.startsWith("//",indent);}
                if(!all&&(long)text.length()+3L*nonblankCount>limit)throw new IllegalArgumentException("Output size limit");
                if(nonblank)for(int i=first;i<=last;i++){
                    String line=lines.get(i);int indent=indent(line);if(indent==line.length())continue;
                    int delta;
                    if(all){int remove=2;if(indent+2<line.length()&&line.charAt(indent+2)==' ')remove++;lines.set(i,line.substring(0,indent)+line.substring(indent+remove));delta=-remove;}
                    else{lines.set(i,line.substring(0,indent)+"// "+line.substring(indent));delta=3;}
                    if(i==cursorLine&&cursorColumn>=indent)cursorColumn=Math.max(indent,cursorColumn+delta);
                }
            }
        }
        String result=String.join("\n",lines);
        if(result.length()>limit)throw new IllegalArgumentException("Output size limit");
        cursorColumn=Math.min(cursorColumn,lines.get(cursorLine).length());
        return new Result(result,first,last,cursorLine,cursorColumn,lines.get(last).length());
    }
    public static int offset(String text,int line,int column) {
        if(line<0)throw new IllegalArgumentException("Invalid line");
        int start=0;for(int i=0;i<line;i++){int end=text.indexOf('\n',start);if(end<0)throw new IllegalArgumentException("Invalid line");start=end+1;}
        int end=text.indexOf('\n',start);if(end<0)end=text.length();
        if(column<0||column>end-start)throw new IllegalArgumentException("Invalid column");return start+column;
    }
    private static int indent(String line){int i=0;while(i<line.length()&&(line.charAt(i)==' '||line.charAt(i)=='\t'))i++;return i;}
}
