package com.fixmer.mared.gui2.framework.components.editor;

import com.fixmer.genesis.technology.editor.CodeSyntaxLexer;
import java.util.*;

/** Local snippet templates. Markers are removed before insertion; no code is executed. */
public final class CodeSnippets {
    public record Snippet(String id,String template) {}
    public record Range(int start,int end) {}
    public record Expansion(String text,List<Range> fields,int cursor) {public Expansion{fields=List.copyOf(fields);}}
    private CodeSnippets() {}
    public static List<Snippet> forPath(String path){
        String lower=path==null?"":path.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".mcfunction"))return List.of(new Snippet("execute","execute as ${target:@a} at @s run ${command:say ready}${cursor}"),new Snippet("function","function ${namespace:map}:${path:main}${cursor}"),new Snippet("scoreboard","scoreboard players set ${target:@s} ${objective:score} ${value:0}${cursor}"),new Snippet("say","say ${message:Hello}${cursor}"));
        if(lower.endsWith(".mr"))return List.of(new Snippet("mr_block","{\n    ${selection}${body:}\n}${cursor}"));
        return switch(CodeSyntaxLexer.languageFor(path)){
            case JAVA -> List.of(
                new Snippet("if","if (${condition:true}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("if_else","if (${condition:true}) {\n    ${selection}${then:}\n} else {\n    ${otherwise:}\n}${cursor}"),
                new Snippet("for","for (int ${index:i} = 0; ${condition:i < 10}; ${step:i++}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("each","for (${type:var} ${item:item} : ${items:items}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("while","while (${condition:true}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("try","try {\n    ${selection}${body:}\n} catch (${exception:Exception} ${name:exception}) {\n    ${handler:throw new RuntimeException(exception);}\n}${cursor}"),
                new Snippet("method","private ${type:void} ${name:method}(${arguments:}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("constructor","public ${name:Example}(${arguments:}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("class","public final class ${name:Example} {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("record","public record ${name:Value}(${components:int value}) {\n    ${body:}\n}${cursor}"),
                new Snippet("log","com.mojang.logging.LogUtils.getLogger().info(\"${message:Message}\");${cursor}"),
                new Snippet("main","public static void main(String[] args) {\n    ${selection}${body:}\n}${cursor}"));
            case PYTHON -> List.of(
                new Snippet("if","if ${condition:True}:\n    ${selection}${body:pass}${cursor}"),
                new Snippet("for","for ${item:item} in ${items:items}:\n    ${selection}${body:pass}${cursor}"),
                new Snippet("method","def ${name:method}(${arguments:}):\n    ${selection}${body:pass}${cursor}"),
                new Snippet("try","try:\n    ${selection}${body:pass}\nexcept ${exception:Exception} as ${name:exception}:\n    ${handler:raise}${cursor}"));
            case JAVASCRIPT -> List.of(
                new Snippet("if","if (${condition:true}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("each","for (const ${item:item} of ${items:items}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("method","function ${name:method}(${arguments:}) {\n    ${selection}${body:}\n}${cursor}"),
                new Snippet("try","try {\n    ${selection}${body:}\n} catch (${name:error}) {\n    ${handler:console.error(error);}\n}${cursor}"));
            default -> List.of();
        };
    }
    public static Expansion expand(Snippet snippet,String indent,String selection){
        var out=new StringBuilder();var fields=new ArrayList<Range>();int cursor=-1;String template=snippet.template();
        for(int i=0;i<template.length();){
            if(template.startsWith("${",i)){
                int end=template.indexOf('}',i+2);if(end<0)throw new IllegalArgumentException("marker");
                String marker=template.substring(i+2,end);int colon=marker.indexOf(':');
                if(marker.equals("cursor"))cursor=out.length();
                else if(marker.equals("selection")){
                    int rowStart=out.lastIndexOf("\n")+1;String prefix=out.substring(rowStart);
                    if(!prefix.isBlank())prefix=indent;
                    String[] rows=selection.split("\n",-1);out.append(rows[0]);
                    for(int row=1;row<rows.length;row++){
                        String value=rows[row];if(!indent.isEmpty()&&value.startsWith(indent))value=value.substring(indent.length());
                        out.append('\n').append(prefix).append(value);
                    }
                }
                else if(colon>0){int start=out.length();String value=marker.substring(colon+1);if(marker.startsWith("body:")&&!selection.isEmpty())value="";out.append(value);fields.add(new Range(start,out.length()));}
                else throw new IllegalArgumentException("marker");
                i=end+1;
            }else{
                char c=template.charAt(i++);out.append(c);if(c=='\n')out.append(indent);
            }
            if(out.length()>1024*1024)throw new IllegalArgumentException("limit");
        }
        return new Expansion(out.toString(),fields,cursor<0?out.length():cursor);
    }
    public static Session insert(EditorDocument doc,EditorHistory history,Snippet snippet){
        if(doc.getValue().length()>1024*1024)throw new IllegalArgumentException("limit");
        int[] selection=doc.orderedSelection();int row=selection==null?doc.cursorLine():selection[0],col=selection==null?doc.cursorCol():selection[1];
        int start=EditorTextEdits.offset(doc,row,col),end=selection==null?start:EditorTextEdits.offset(doc,selection[2],selection[3]);
        String line=doc.line(row);int spaces=0;while(spaces<Math.min(col,line.length())&&(line.charAt(spaces)==' '||line.charAt(spaces)=='\t'))spaces++;
        var expansion=expand(snippet,line.substring(0,spaces),doc.getSelectedText());
        if((long)doc.getValue().length()-end+start+expansion.text().length()>1024*1024)throw new IllegalArgumentException("limit");
        EditorTextEdits.replace(doc,history,start,end,expansion.text());
        var fields=new ArrayList<Range>();for(Range field:expansion.fields())fields.add(new Range(field.start()+start,field.end()+start));
        var session=new Session(fields,expansion.cursor()+start,doc.getValue());session.select(doc);return session;
    }
    public static final class Session {
        private final List<Range> fields;
        private int index,cursor;
        private String snapshot;
        private boolean finished;
        private Session(List<Range> fields,int cursor,String snapshot){this.fields=fields;this.cursor=cursor;this.snapshot=snapshot;}
        public boolean finished(){return finished;}
        private void select(EditorDocument doc){
            if(index>=fields.size()){finished=true;EditorTextEdits.select(doc,cursor,cursor);}
            else {var field=fields.get(index);EditorTextEdits.select(doc,field.start(),field.end());}
        }
        /** false cancels stale sessions and lets Tab behave normally. */
        public boolean advance(EditorDocument doc,boolean backwards){
            if(finished||index>=fields.size())return false;
            Range field=fields.get(index);String actual=doc.getValue();int delta=actual.length()-snapshot.length();
            int newEnd=field.end()+delta;
            if(newEnd<field.start()||actual.length()>1024*1024||!actual.startsWith(snapshot.substring(0,field.start()))||!actual.endsWith(snapshot.substring(field.end()))){finished=true;return false;}
            int caret=EditorTextEdits.offset(doc,doc.cursorLine(),doc.cursorCol());
            if(caret<field.start()||caret>newEnd){finished=true;return false;}
            fields.set(index,new Range(field.start(),newEnd));
            for(int i=index+1;i<fields.size();i++){Range next=fields.get(i);fields.set(i,new Range(next.start()+delta,next.end()+delta));}
            if(cursor>=field.end())cursor+=delta;
            snapshot=actual;index=backwards?Math.max(0,index-1):index+1;select(doc);return true;
        }
    }
}
