package com.fixmer.genesis.supervisor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded TOML projection for preflight identities; NeoForge remains the full resolver. */
final class ModMetadataText {
    record Table(String name, Map<String,String> values) {}
    private ModMetadataText() {}
    static String literal(String raw)throws IOException {
        String s=raw.trim();
        if(s.length()<2||(s.charAt(0)!='"'&&s.charAt(0)!='\'')||s.charAt(s.length()-1)!=s.charAt(0))throw new IOException("Expected literal metadata string");
        char q=s.charAt(0);s=s.substring(1,s.length()-1);
        if(s.indexOf(q)>=0||s.indexOf('\\')>=0||s.contains("${"))throw new IOException("Unsupported metadata expression");
        return s;
    }
    private static String uncomment(String line)throws IOException {
        char quote=0;boolean escaped=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(quote!=0){if(escaped)escaped=false;else if(c=='\\'&&quote=='"')escaped=true;else if(c==quote)quote=0;}
            else if(c=='"'||c=='\'')quote=c;
            else if(c=='#')return line.substring(0,i).trim();
        }
        if(quote!=0)throw new IOException("Unclosed metadata string");
        return line.trim();
    }
    private static int close(String s,char q,int from){
        String marker=String.valueOf(q).repeat(3);
        for(int i=from;(i=s.indexOf(marker,i))>=0;i++){
            if(q=='"'){int slashes=0;for(int p=i-1;p>=0&&s.charAt(p)=='\\';p--)slashes++;if((slashes&1)!=0)continue;}
            return i;
        }
        return -1;
    }
    private static void closingTail(String s,int at,char q)throws IOException {
        int end=at+3,extra=0;while(end<s.length()&&s.charAt(end)==q&&extra<2){end++;extra++;}
        String tail=s.substring(end).trim();if(!tail.isEmpty()&&!tail.startsWith("#"))throw new IOException("Invalid multiline metadata tail");
    }
    static List<Table> parse(byte[] bytes)throws IOException { return parse(bytes,true); }
    static List<Table> parse(byte[] bytes,boolean candidate)throws IOException {
        String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        var result=new ArrayList<Table>();Map<String,String> values=new LinkedHashMap<>();String table="";
        result.add(new Table(table,values));char multiline=0;
        for(String raw:text.split("\\R")){
            if(multiline!=0){int end=close(raw,multiline,0);if(end>=0){closingTail(raw,end,multiline);multiline=0;}continue;}
            String line=raw.trim();if(line.isEmpty()||line.startsWith("#"))continue;
            int eq=line.indexOf('=');
            if(eq>=0&&!line.startsWith("[")){
                String value=line.substring(eq+1).stripLeading();
                if(value.startsWith("\"\"\"")||value.startsWith("'''")){
                    char q=value.charAt(0);int end=close(value,q,3);
                    String key=line.substring(0,eq).trim();
                    if(selected(table,key,candidate))throw new IOException("Identity must be a single-line literal");
                    if(end<0)multiline=q;else closingTail(value,end,q);continue;
                }
            }
            line=uncomment(line);if(line.isEmpty())continue;
            if(line.startsWith("[")){
                if(!line.endsWith("]"))throw new IOException("Invalid metadata table");
                table=line;values=new LinkedHashMap<>();result.add(new Table(table,values));continue;
            }
            eq=line.indexOf('=');if(eq<0)continue;
            String key=line.substring(0,eq).trim();
            if(selected(table,key,candidate)&&values.putIfAbsent(key,literal(line.substring(eq+1)))!=null)throw new IOException("Duplicate metadata key: "+key);
        }
        if(multiline!=0)throw new IOException("Unclosed multiline metadata string");
        return result;
    }
    private static boolean selected(String table,String key,boolean candidate){
        return table.isEmpty()?key.equals("modLoader"):table.equals("[[mods]]")?(key.equals("modId")||candidate&&key.equals("version"))
            :table.matches("\\[\\[dependencies\\.[a-z][a-z0-9_]{1,63}]]")&&Set.of("modId","versionRange","type","side").contains(key);
    }
}
