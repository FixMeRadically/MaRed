package com.fixmer.genesis.technology.expression;

import java.util.*;
import java.util.regex.*;

/** Per-operation bounds; cooperative instruction budgets cannot interrupt host library calls. */
public final class ValueLimits140 {
    public static final int MAX_TEXT=1_048_576,MAX_LIST=1_000_000;
    private ValueLimits140() {}
    public static void textSize(long size){if(size>MAX_TEXT)throw new IllegalArgumentException("Text result exceeds 1 Mi characters");}
    public static void listSize(long size){if(size<0||size>MAX_LIST)throw new IllegalArgumentException("List exceeds 1000000 elements");}
    public static String repeat(String value,int count){if(count<=0)return "";textSize((long)value.length()*count);return value.repeat(count);}
    public static void append(StringBuilder builder,String text){textSize((long)builder.length()+text.length());builder.append(text);}
    public static String replace(String text,String target,String replacement){
        textSize(text.length());textSize(replacement.length());long count=0;
        if(target.isEmpty())count=(long)text.length()+1;
        else for(int from=0;(from=text.indexOf(target,from))>=0;from+=target.length())count++;
        textSize((long)text.length()+count*((long)replacement.length()-target.length()));
        return text.replace(target,replacement);
    }
    public static String format(String pattern,Object[] args){
        if(pattern.length()>16384||args.length>128)throw new IllegalArgumentException("Format limit");
        Matcher spec=Pattern.compile("%[^a-zA-Z%]*[a-zA-Z%]").matcher(pattern);
        while(spec.find()){
            Matcher digits=Pattern.compile("[0-9]+").matcher(spec.group());
            while(digits.find())try{if(Integer.parseInt(digits.group())>4096)throw new IllegalArgumentException("Format field limit");}catch(NumberFormatException overflow){throw new IllegalArgumentException("Format field limit",overflow);}
        }
        var result=new StringBuilder();Appendable bounded=new Appendable(){
            public Appendable append(CharSequence text){ValueLimits140.append(result,String.valueOf(text));return this;}
            public Appendable append(CharSequence text,int start,int end){ValueLimits140.append(result,String.valueOf(text).substring(start,end));return this;}
            public Appendable append(char value){textSize((long)result.length()+1);result.append(value);return this;}
        };
        try(var formatter=new Formatter(bounded,Locale.ROOT)){formatter.format(pattern,args);return result.toString();}
        catch(IllegalFormatException invalid){return pattern;}
    }
    public static void insertion(List<?> target,Object value){listSize((long)target.size()+1);valueFor(target,value);}
    public static void valueFor(List<?> target,Object value){
        if(!(value instanceof List<?>))return;
        var visited=new IdentityHashMap<Object,Boolean>();var queue=new ArrayDeque<Object>();queue.add(value);
        int scanned=0;
        while(!queue.isEmpty()){
            Object item=queue.removeFirst();if(item==target)throw new IllegalArgumentException("Cyclic list insertion");
            if(item instanceof List<?> list&&visited.put(item,Boolean.TRUE)==null){
                if(++scanned>100000)throw new IllegalArgumentException("List graph limit");listSize(list.size());
                for(Object child:list)if(child instanceof List<?>)queue.addLast(child);
            }
        }
    }
    private static final class Budget implements CharSequence {
        final String text;final Counter counter;
        Budget(String text,Counter counter){this.text=text;this.counter=counter;}
        public int length(){return text.length();}
        public char charAt(int index){counter.check();return text.charAt(index);}
        public CharSequence subSequence(int from,int to){counter.check();return new Budget(text.substring(from,to),counter);}
        public String toString(){counter.check();return text;}
    }
    private static final class Counter {
        final long start=System.nanoTime();int calls;
        void check(){if(++calls>2_000_000||(calls&255)==0&&System.nanoTime()-start>20_000_000L)throw new IllegalArgumentException("Regular expression operation limit");}
    }
    private static Matcher matcher(String text,String regex){
        textSize(text.length());if(regex.length()>2048)throw new IllegalArgumentException("Regular expression length limit");
        return Pattern.compile(regex).matcher(new Budget(text,new Counter()));
    }
    public static boolean matches(String text,String regex){try{return matcher(text,regex).matches();}catch(StackOverflowError exhausted){throw new IllegalArgumentException("Regular expression nesting limit",exhausted);}}
    public static String replaceAll(String text,String regex,String replacement){
        textSize(replacement.length());
        try {
            Matcher matcher=matcher(text,regex);var result=new StringBuilder();int copied=0;
            while(matcher.find()){
                append(result,text.substring(copied,matcher.start()));
                replacement142(result,matcher,replacement);
                copied=matcher.end();
            }
            append(result,text.substring(copied));return result.toString();
        } catch(StackOverflowError exhausted){throw new IllegalArgumentException("Regular expression nesting limit",exhausted);}
    }
    /** JDK replacement grammar, expanded incrementally instead of allocating an unbounded result. */
    private static void replacement142(StringBuilder out,Matcher match,String replacement){
        for(int i=0;i<replacement.length();i++){
            char c=replacement.charAt(i);
            if(c=='\\'){
                if(++i==replacement.length())throw new IllegalArgumentException("Missing escaped replacement character");
                append(out,String.valueOf(replacement.charAt(i)));
            } else if(c=='$') {
                if(++i==replacement.length())throw new IllegalArgumentException("Missing replacement group");
                String group;
                if(replacement.charAt(i)=='{'){
                    int end=replacement.indexOf('}',i+1);
                    if(end<0)throw new IllegalArgumentException("Missing replacement group terminator");
                    String name=replacement.substring(i+1,end);
                    if(!name.matches("[A-Za-z][A-Za-z0-9]*"))throw new IllegalArgumentException("Invalid named group");
                    group=match.group(name);i=end;
                } else {
                    char digit=replacement.charAt(i);
                    if(digit<'0'||digit>'9')throw new IllegalArgumentException("Invalid replacement group");
                    int number=digit-'0';
                    while(i+1<replacement.length()){
                        char next=replacement.charAt(i+1);
                        if(next<'0'||next>'9')break;
                        long candidate=(long)number*10+next-'0';
                        if(candidate>match.groupCount())break;
                        number=(int)candidate;i++;
                    }
                    group=match.group(number);
                }
                if(group!=null)append(out,group);
            } else {textSize((long)out.length()+1);out.append(c);}
        }
    }
    /** Split only at top-level commas, retaining quoted/list/function expressions intact. */
    public static List<String> arguments(String source){
        if(source.isBlank())return List.of();var result=new ArrayList<String>();var stack=new ArrayDeque<Character>();char quote=0;int start=0;
        for(int i=0;i<source.length();i++){
            char c=source.charAt(i);
            if(quote!=0){if(c=='\\')i++;else if(c==quote)quote=0;continue;}
            if(c=='"'||c=='\''){quote=c;continue;}
            if(c=='('||c=='['||c=='{'){if(stack.size()>=128)throw new IllegalArgumentException("Argument nesting limit");stack.push(c);}
            else if(c==')'||c==']'||c=='}'){if(stack.isEmpty()||!pair(stack.pop(),c))throw new IllegalArgumentException("Unbalanced argument");}
            else if(c==','&&stack.isEmpty()){argument(result,source.substring(start,i));start=i+1;}
        }
        if(quote!=0||!stack.isEmpty())throw new IllegalArgumentException("Unclosed argument");argument(result,source.substring(start));return List.copyOf(result);
    }
    private static boolean pair(char a,char b){return a=='('&&b==')'||a=='['&&b==']'||a=='{'&&b=='}';}
    private static void argument(List<String> result,String text){if(text.isBlank())throw new IllegalArgumentException("Empty argument");if(result.size()>=128)throw new IllegalArgumentException("Argument count limit");result.add(text.strip());}
}
