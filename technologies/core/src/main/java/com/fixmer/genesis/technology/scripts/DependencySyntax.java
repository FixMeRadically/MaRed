package com.fixmer.genesis.technology.scripts;
import java.io.*;
import java.util.*;
import java.util.regex.*;
/** Lexical comments and TOML string handling; never evaluates project scripts. */
final class DependencySyntax  {
    private DependencySyntax() {
    }
    static String gradle(String s) {
        var out=new StringBuilder();
        char quote=0;
        boolean line=false,block=false,escape=false;
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i),n=i+1<s.length()?s.charAt(i+1):0;
            if(line) {
                if(c=='\n')line=false;
                out.append(c=='\n'?'\n':' ');
                continue;
            }
            if(block) {
                if(c=='*'&&n=='/') {
                    out.append("  ");
                    i++;
                    block=false;
                }
                else out.append(c=='\n'?'\n':' ');
                continue;
            }
            if(quote!=0) {
                out.append(c);
                if(escape)escape=false;
                else if(c=='\\')escape=true;
                else if(c==quote)quote=0;
                continue;
            }
            if(c=='\''||c=='"') {
                quote=c;
                out.append(c);
            }
            else if(c=='/'&&n=='/') {
                line=true;
                out.append("  ");
                i++;
            }
            else if(c=='/'&&n=='*') {
                block=true;
                out.append("  ");
                i++;
            }
            else out.append(c);
        }
        return out.toString();
    }
    private record Token(String text,boolean string) {
    }
    private static List<Token> tokens(String build) {
        String s=gradle(build);
        var tokens=new ArrayList<Token>();
        for(int i=0;i<s.length();) {
            char c=s.charAt(i);
            if(Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if(c=='\''||c=='"') {
                char quote=c;
                i++;
                var text=new StringBuilder();
                boolean triple=i+1<s.length()&&s.charAt(i)==quote&&s.charAt(i+1)==quote;
                if(triple) {
                    i+=2;
                    while(i+2<s.length()&&!(s.charAt(i)==quote&&s.charAt(i+1)==quote&&s.charAt(i+2)==quote))text.append(s.charAt(i++));
                    i=Math.min(s.length(),i+3);
                }
                else {
                    while(i<s.length()) {
                        char q=s.charAt(i++);
                        if(q==quote)break;
                        if(q=='\\'&&i<s.length())q=s.charAt(i++);
                        text.append(q);
                    }
                }
                tokens.add(new Token(text.toString(),true));
            }
            else if(Character.isJavaIdentifierStart(c)) {
                int start=i++;
                while(i<s.length()&&Character.isJavaIdentifierPart(s.charAt(i)))i++;
                tokens.add(new Token(s.substring(start,i),false));
            }
            else {
                tokens.add(new Token(String.valueOf(c),false));
                i++;
            }
        }
        return tokens;
    }
    static boolean neo(String build) {
        var tokens=tokens(build);
        var scopes=new ArrayDeque<Boolean>();
        for(int i=0;i<tokens.size();i++) {
            Token t=tokens.get(i);
            if(t.string())continue;
            if(t.text().equals("{")) {
                boolean plugin=i>0&&!tokens.get(i-1).string()&&tokens.get(i-1).text().equals("plugins")||!scopes.isEmpty()&&scopes.peek();
                scopes.push(plugin);
            }
            else if(t.text().equals("}")) {
                if(!scopes.isEmpty())scopes.pop();
            }
            else if(t.text().equals("id")&&!scopes.isEmpty()&&scopes.peek()) {
                int at=i+1;
                if(at<tokens.size()&&tokens.get(at).text().equals("("))at++;
                if(at<tokens.size()&&tokens.get(at).string()&&tokens.get(at).text().equals("net.neoforged.gradle.userdev"))return true;
            }
            else if(t.text().equals("apply")&&i+3<tokens.size()&&tokens.get(i+1).text().equals("plugin")&&tokens.get(i+2).text().equals(":")) {
                Token value=tokens.get(i+3);
                if(value.string()&&value.text().equals("net.neoforged.gradle.userdev"))return true;
            }
        }
        return false;
    }
    static boolean declares(String build,String coordinate) {
        String[] own=coordinate.split(":");
        var tokens=tokens(build);
        for(int i=0;i<tokens.size();i++) {
            Token t=tokens.get(i);
            if(t.string()||!Set.of("implementation","api","compileOnly","compileOnlyApi","runtimeOnly","jarJar","testImplementation").contains(t.text()))continue;
            int at=i+1;
            if(at<tokens.size()&&tokens.get(at).text().equals("("))at++;
            if(at>=tokens.size()||!tokens.get(at).string())continue;
            String[] p=tokens.get(at).text().split(":");
            if(p.length>=3&&p[0].equals(own[0])&&p[1].equals(own[1])&&(p.length>3?p[3]:"").equals(own.length>3?own[3]:""))return true;
        }
        return false;
    }
    static String uncomment(String line) {
        char q=0;
        boolean escaped=false;
        for(int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if(q==0) {
                if(c=='#')return line.substring(0,i).strip();
                if(c=='\''||c=='"')q=c;
            }
            else if(q=='"'&&c=='\\'&&!escaped) {
                escaped=true;
                continue;
            }
            else if(c==q&&!escaped)q=0;
            escaped=false;
        }
        return line.strip();
    }
    static String string(String value)throws IOException {
        value=value.strip();
        if(value.length()<2)throw new IOException("Expected literal TOML string");
        char q=value.charAt(0);
        if((q!='\''&&q!='"')||value.charAt(value.length()-1)!=q)throw new IOException("Expected literal TOML string");
        String inner=value.substring(1,value.length()-1);
        if(q=='\'')return inner;
        if(inner.contains("\\")) {
            var out=new StringBuilder();
            for(int i=0;i<inner.length();i++) {
                char c=inner.charAt(i);
                if(c!='\\') {
                    out.append(c);
                    continue;
                }
                if(++i>=inner.length())throw new IOException("Invalid TOML escape");
                char e=inner.charAt(i);
                switch(e) {
                    case '"','\\'->out.append(e);
                    case 'n'->out.append('\n');
                    case 'r'->out.append('\r');
                    case 't'->out.append('\t');
                    case 'b'->out.append('\b');
                    case 'f'->out.append('\f');
                    case 'u','U'-> {
                        int len=e=='u'?4:8;
                        if(i+len>=inner.length())throw new IOException("Invalid TOML unicode escape");
                        try {
                            out.appendCodePoint(Integer.parseInt(inner.substring(i+1,i+1+len),16));
                        }
                        catch(IllegalArgumentException bad) {
                            throw new IOException("Invalid TOML unicode escape",bad);
                        }
                        i+=len;
                    }
                    default->throw new IOException("Invalid TOML escape");
                }
            }
            return out.toString();
        }
        return inner;
    }
    static Map<String,List<String>> modIds(String toml)throws IOException {
        var result=new LinkedHashMap<String,List<String>>();
        String table="";
        char multiline=0;
        for(String raw:toml.split("\\R")) {
            String line=uncomment(raw);
            if(multiline!=0) {
                String mark=String.valueOf(multiline).repeat(3);
                if(raw.contains(mark))multiline=0;
                continue;
            }
            int a=line.indexOf("\"\"\""),b=line.indexOf(String.valueOf((char)39).repeat(3));
            if(a>=0||b>=0) {
                char q=a>=0?'"':'\'';
                String mark=String.valueOf(q).repeat(3);
                int n=line.indexOf(mark);
                if(line.indexOf(mark,n+3)<0)multiline=q;
                continue;
            }
            if(line.startsWith("[[")&&line.endsWith("]]")) {
                table=line.substring(2,line.length()-2).strip();
                continue;
            }
            if(line.startsWith("[")) {
                table="";
                continue;
            }
            var m=Pattern.compile("^(?:modId|\"modId\"|'modId')\\s*=\\s*(.*)$").matcher(line);
            if(m.matches())result.computeIfAbsent(table,k->new ArrayList<>()).add(string(m.group(1)));
        }
        return result;
    }
    static boolean validRange(String s) {
        if(s==null||s.length()>128)return false;
        if(s.matches("\\[[A-Za-z0-9_][A-Za-z0-9_.-]*\\]"))return true;
        if(!s.matches("[\\[(][A-Za-z0-9_][A-Za-z0-9_.-]*,[A-Za-z0-9_][A-Za-z0-9_.-]*[\\])]")&&!s.matches("\\(,[A-Za-z0-9_][A-Za-z0-9_.-]*[\\])]")&&!s.matches("[\\[(][A-Za-z0-9_][A-Za-z0-9_.-]*,\\)"))return false;
        String[] p=s.substring(1,s.length()-1).split(",",-1);
        if(!p[0].isEmpty()&&!p[1].isEmpty()&&p[0].matches("[0-9]+(?:\\.[0-9]+)*")&&p[1].matches("[0-9]+(?:\\.[0-9]+)*")) {
            int n=numericCompare(p[0],p[1]);
            if(n>0||n==0&&(s.charAt(0)!='['||s.charAt(s.length()-1)!=']'))return false;
        }
        return true;
    }
    static boolean numericContains(String range,String version) {
        if(!version.matches("[0-9]+(?:\\.[0-9]+)*"))return true;
        if(range.matches("\\[[0-9]+(?:\\.[0-9]+)*\\]"))return numericCompare(range.substring(1,range.length()-1),version)==0;
        String[] p=range.substring(1,range.length()-1).split(",",-1);
        if(p.length!=2)return true;
        for(String bound:p)if(!bound.isEmpty()&&!bound.matches("[0-9]+(?:\\.[0-9]+)*"))return true;
        if(!p[0].isEmpty()) {
            int n=numericCompare(version,p[0]);
            if(n<0||n==0&&range.charAt(0)=='(')return false;
        }
        if(!p[1].isEmpty()) {
            int n=numericCompare(version,p[1]);
            if(n>0||n==0&&range.charAt(range.length()-1)==')')return false;
        }
        return true;
    }
    static int numericCompare(String a,String b) {
        String[] x=a.split("\\."),y=b.split("\\.");
        for(int i=0;i<Math.max(x.length,y.length);i++) {
            var p=new java.math.BigInteger(i<x.length?x[i]:"0");
            var q=new java.math.BigInteger(i<y.length?y[i]:"0");
            int n=p.compareTo(q);
            if(n!=0)return n;
        }
        return 0;
    }
}
