package com.fixmer.mared.gui2.framework.components.editor;

import java.util.*;

/** Local lexical colouring only. No compiler, classpath, regex backtracking or filesystem access. */
public final class CodeSyntaxLexer {
    public enum Language { JAVA, PYTHON, RUST, CPP, JAVASCRIPT, GROOVY, KOTLIN, JSON, JSONC, TOML, PROPERTIES, PLAIN, GLSL }
    public enum Kind { KEYWORD, CONTROL, STRING, NUMBER, COMMENT, TYPE, FUNCTION, ANNOTATION, LITERAL, CONSTANT, IDENTIFIER, PROPERTY, OPERATOR }
    public enum Mode { NORMAL, COMMENT, STRING, QUOTED, PROPERTY_VALUE }
    public record Token(int start,int end,Kind kind) { }
    public record State(Mode mode,int depth,String closing) {
        public static final State NORMAL=new State(Mode.NORMAL,0,"");
    }
    public record Line(List<Token> tokens,State next) { }
    private static Set<String> words(String value){return Set.copyOf(Arrays.asList(value.split(" +")));}
    private static final Set<String> JAVA=words("abstract assert boolean break byte case catch char class const continue default do double else enum exports extends final finally float for if implements import instanceof int interface long module native new non-sealed open opens package permits private protected provides public record requires return sealed short static strictfp super switch synchronized this throw throws to transient transitive try uses var void volatile when while with yield");
    private static final Set<String> PYTHON=words("and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case type");
    private static final Set<String> RUST=words("as async await break const continue crate dyn else enum extern fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait type unsafe use where while abstract become box do final macro override priv typeof unsized virtual yield try union");
    private static final Set<String> CPP=words("alignas alignof and asm auto bitand bitor bool break case catch char char8_t char16_t char32_t class co_await co_return co_yield compl concept const consteval constexpr constinit const_cast continue decltype default delete do double dynamic_cast else enum explicit export extern float for friend goto if inline int long mutable namespace new noexcept not operator or private protected public register reinterpret_cast requires return short signed sizeof static static_assert static_cast struct switch template this thread_local throw try typedef typeid typename union unsigned using virtual void volatile wchar_t while xor");
    private static final Set<String> GLSL=words("attribute const uniform varying layout centroid flat smooth noperspective in out inout float double int void bool true false invariant discard return mat2 mat3 mat4 vec2 vec3 vec4 ivec2 ivec3 ivec4 bvec2 bvec3 bvec4 uint uvec2 uvec3 uvec4 sampler2D sampler3D samplerCube struct precision highp mediump lowp if else for while do break continue switch case default");
    private static final Set<String> JS=words("async await break case catch class const continue debugger default delete do else export extends finally for from function get if import in instanceof let new of return set static super switch this throw try typeof var void while with yield");
    private static final Set<String> GROOVY=words("as assert boolean break byte case catch char class const continue def default do double else enum extends final finally float for if implements import in instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws trait transient try var void volatile while");
    private static final Set<String> KOTLIN=words("abstract actual annotation as break by catch class companion const constructor continue crossinline data delegate do dynamic else enum expect external field file final finally for fun get if import in infix init inline inner interface internal is lateinit noinline object open operator out override package param private property protected public receiver reified return sealed set setparam super suspend tailrec this throw try typealias typeof val value var vararg when where while");
    private static final Set<String> CONTROL=words("assert async await break case catch continue default do elif else except finally for if lambda match raise return switch throw throws try when while yield");
    private static final Set<String> PY_TYPES=words("bool bytes bytearray complex dict float frozenset int list memoryview object range set slice str tuple type");
    private static final Set<String> DECLARATION=words("class interface enum record struct trait type typealias namespace");
    private CodeSyntaxLexer() { }
    public static Language languageFor(String path){
        String value=path==null?"":path.toLowerCase(Locale.ROOT);
        if(value.matches(".+\\.(glsl|vsh|fsh|gsh|csh|vert|frag)"))return Language.GLSL;
        if(value.endsWith(".java"))return Language.JAVA;
        if(value.endsWith(".py"))return Language.PYTHON;
        if(value.endsWith(".rs"))return Language.RUST;
        if(value.endsWith(".cpp")||value.endsWith(".c")||value.endsWith(".h")||value.endsWith(".hpp")||value.endsWith(".cc")||value.endsWith(".cxx"))return Language.CPP;
        if(value.endsWith(".js")||value.endsWith(".mjs")||value.endsWith(".cjs"))return Language.JAVASCRIPT;
        if(value.endsWith(".gradle")||value.endsWith(".groovy"))return Language.GROOVY;
        if(value.endsWith(".kt")||value.endsWith(".kts"))return Language.KOTLIN;
        if(value.endsWith(".jsonc"))return Language.JSONC;
        if(value.endsWith(".json")||value.endsWith(".mcmeta")||value.endsWith(".bbmodel"))return Language.JSON;
        if(value.endsWith(".toml"))return Language.TOML;
        if(value.endsWith(".properties"))return Language.PROPERTIES;
        return Language.PLAIN;
    }
    private static Set<String> keywords(Language language){return switch(language){case GLSL->GLSL;case JAVA->JAVA;case PYTHON->PYTHON;case RUST->RUST;case CPP->CPP;case JAVASCRIPT->JS;case GROOVY->GROOVY;case KOTLIN->KOTLIN;default->Set.of();};}
    private static boolean json(Language language){return language==Language.JSON||language==Language.JSONC;}
    private static boolean hashComments(Language language){return language==Language.PYTHON||language==Language.TOML;}
    private static boolean slashComments(Language language){return !hashComments(language)&&language!=Language.JSON&&language!=Language.PROPERTIES&&language!=Language.PLAIN;}
    private static final Set<String> BASIC_LITERALS=words("true false null"),PY_LITERALS=words("None True False Ellipsis"),CPP_LITERALS=words("true false nullptr NULL"),JS_LITERALS=words("true false null undefined NaN Infinity"),TOML_LITERALS=words("true false inf nan");
    private static final Set<String> CPP_PREFIXES=words("u8 u l");
    private static boolean literal(String word,Language language){return (switch(language){case PYTHON->PY_LITERALS;case CPP->CPP_LITERALS;case JAVASCRIPT->JS_LITERALS;case TOML->TOML_LITERALS;default->BASIC_LITERALS;}).contains(word);}
    private static boolean rawDelimiter(Language language,String delimiter){return language==Language.RUST||language==Language.CPP||language==Language.KOTLIN&&delimiter.equals("\"\"\"")||language==Language.TOML&&delimiter.equals("'''");}
    private static int spaces(String s,int i){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;return i;}
    private static boolean identifierStart(String s,int i){return i<s.length()&&Character.isJavaIdentifierStart(s.codePointAt(i));}
    private static int identifierEnd(String s,int i){while(i<s.length()&&Character.isJavaIdentifierPart(s.codePointAt(i)))i+=Character.charCount(s.codePointAt(i));return i;}
    private static boolean escapedAt(String s,int index){int slashes=0;while(index>0&&s.charAt(--index)=='\\')slashes++;return (slashes&1)!=0;}
    private static int quotedEnd(String s,int start,char quote){for(int i=start;i<s.length();i++){if(s.charAt(i)=='\\'){i++;continue;}if(s.charAt(i)==quote)return i+1;}return -1;}
    private static int multiEnd(String s,int start,String delimiter,boolean raw){int at=start;while((at=s.indexOf(delimiter,at))>=0){if(raw||!escapedAt(s,at))return at+delimiter.length();at++;}return -1;}
    private static final class Tokens {
        final ArrayList<Token> values=new ArrayList<>();boolean overflow;
        void add(int start,int end,Kind kind){
            if(end<=start||overflow)return;
            if(!values.isEmpty()){Token last=values.get(values.size()-1);if(last.end==start&&last.kind==kind){values.set(values.size()-1,new Token(last.start,end,kind));return;}}
            if(values.size()>=2048){overflow=true;values.clear();return;}values.add(new Token(start,end,kind));
        }
        List<Token> finish(){return List.copyOf(values);}
    }
    public static Line lex(Language language,String text,State incoming){
        Objects.requireNonNull(language);Objects.requireNonNull(text);Objects.requireNonNull(incoming);
        if(language==Language.PLAIN)return new Line(List.of(),State.NORMAL);
        if(language==Language.PROPERTIES)return properties(text,incoming);
        Tokens tokens=new Tokens();int length=text.length(),i=0,depth=incoming.depth();Mode mode=incoming.mode();String closing=incoming.closing(),previous="";
        if(length==0&&mode==Mode.QUOTED&&language!=Language.RUST)return new Line(List.of(),State.NORMAL);
        while(i<length){
            int start=i;char c=text.charAt(i);
            if(mode==Mode.COMMENT){
                while(i<length){
                    if(text.startsWith("*/",i)){depth--;i+=2;if(depth==0){mode=Mode.NORMAL;break;}}
                    else if(language==Language.RUST&&text.startsWith("/*",i)){depth++;i+=2;}else i++;
                }
                tokens.add(start,i,Kind.COMMENT);continue;
            }
            if(mode==Mode.STRING){
                int end=multiEnd(text,i,closing,rawDelimiter(language,closing));
                i=end<0?length:end;tokens.add(start,i,Kind.STRING);if(end>=0){mode=Mode.NORMAL;closing="";}continue;
            }
            if(mode==Mode.QUOTED){
                int end=quotedEnd(text,i,closing.charAt(0));i=end<0?length:end;tokens.add(start,i,Kind.STRING);
                if(end>=0||language!=Language.RUST&&!escapedAt(text,length)){mode=Mode.NORMAL;closing="";}continue;
            }
            if(Character.isWhitespace(c)){i++;continue;}
            if(hashComments(language)&&c=='#'||slashComments(language)&&text.startsWith("//",i)){tokens.add(i,length,Kind.COMMENT);break;}
            if(slashComments(language)&&text.startsWith("/*",i)){depth=1;mode=Mode.COMMENT;i+=2;
                while(i<length){if(text.startsWith("*/",i)){depth--;i+=2;if(depth==0){mode=Mode.NORMAL;break;}}else if(language==Language.RUST&&text.startsWith("/*",i)){depth++;i+=2;}else i++;}
                tokens.add(start,i,Kind.COMMENT);continue;}
            if((language==Language.CPP||language==Language.GLSL)&&c=='#'&&spaces(text,0)==i){i=spaces(text,i+1);int directive=i;i=identifierEnd(text,i);tokens.add(start,i,Kind.ANNOTATION);String name=text.substring(directive,i);int value=spaces(text,i);if((name.equals("include")||name.equals("include_next"))&&value<length&&text.charAt(value)=='<'){int end=text.indexOf('>',value+1);if(end>=0){tokens.add(value,end+1,Kind.STRING);i=end+1;}}continue;}
            if(language==Language.TOML&&c=='['&&spaces(text,0)==i){int end=text.indexOf(']',i+1);if(end>=0){i=end+1;if(i<length&&text.charAt(i)==']')i++;tokens.add(start,i,Kind.PROPERTY);continue;}}
            if(c=='@'&&!json(language)){i=identifierEnd(text,i+1);while(i<length&&text.charAt(i)=='.'&&identifierStart(text,i+1))i=identifierEnd(text,i+1);tokens.add(start,i,Kind.ANNOTATION);continue;}
            // Rust raw strings r##"..."##, including byte strings.
            if(language==Language.RUST&&(c=='r'||text.startsWith("br",i))){int r=c=='r'?i:i+1,end=r+1;while(end<length&&text.charAt(end)=='#')end++;
                if(end<length&&text.charAt(end)=='\"'){closing="\""+"#".repeat(end-r-1);int found=multiEnd(text,end+1,closing,true);i=found<0?length:found;tokens.add(start,i,Kind.STRING);mode=found<0?Mode.STRING:Mode.NORMAL;if(mode==Mode.NORMAL)closing="";continue;}}
            // C++ raw strings, including the standard encoding prefixes.
            if(language==Language.CPP){int r=text.startsWith("u8R\"",i)?i+2:text.startsWith("uR\"",i)||text.startsWith("UR\"",i)||text.startsWith("LR\"",i)?i+1:text.startsWith("R\"",i)?i:-1;
                if(r>=0){int open=text.indexOf('(',r+2);if(open>=0&&open-r-2<=16){String delimiter=text.substring(r+2,open);if(delimiter.chars().noneMatch(ch->Character.isWhitespace(ch)||ch=='\\'||ch==')')){closing=")"+delimiter+"\"";int found=multiEnd(text,open+1,closing,true);i=found<0?length:found;tokens.add(start,i,Kind.STRING);mode=found<0?Mode.STRING:Mode.NORMAL;if(mode==Mode.NORMAL)closing="";continue;}}}}
            if(language==Language.RUST&&c=='\''&&identifierStart(text,i+1)){int end=identifierEnd(text,i+1);if(end==length||text.charAt(end)!='\''){i=end;tokens.add(start,i,Kind.TYPE);continue;}}
            int quote=i;
            if(identifierStart(text,i)&&(language==Language.PYTHON||language==Language.CPP||language==Language.RUST)){
                int end=identifierEnd(text,i);String prefix=text.substring(i,end).toLowerCase(Locale.ROOT);
                boolean stringPrefix=language==Language.PYTHON?prefix.length()<=2&&!prefix.isEmpty()&&prefix.chars().allMatch(ch->"rfbu".indexOf(ch)>=0):language==Language.RUST?prefix.equals("b"):CPP_PREFIXES.contains(prefix);
                if(stringPrefix&&end<length&&(text.charAt(end)=='\"'||text.charAt(end)=='\''))quote=end;
            }
            char q=text.charAt(quote);
            if(q=='\"'||q=='\''&&!json(language)||q=='`'&&language==Language.JAVASCRIPT){
                String delimiter=String.valueOf(q);boolean multi=q=='`';
                if(q!='`'&&(language==Language.JAVA&&q=='\"'||language==Language.PYTHON||language==Language.GROOVY||language==Language.KOTLIN||language==Language.TOML)&&text.startsWith(delimiter.repeat(3),quote)){delimiter=delimiter.repeat(3);multi=true;}
                int end=multi?multiEnd(text,quote+delimiter.length(),delimiter,rawDelimiter(language,delimiter)):language==Language.TOML&&q=='\''?(text.indexOf(q,quote+1)<0?-1:text.indexOf(q,quote+1)+1):quotedEnd(text,quote+1,q);
                i=end<0?length:end;int next=spaces(text,i);Kind kind=(json(language)&&next<length&&text.charAt(next)==':'||language==Language.TOML&&next<length&&text.charAt(next)=='=')?Kind.PROPERTY:Kind.STRING;
                tokens.add(start,i,kind);
                if(end<0){if(multi){mode=Mode.STRING;closing=delimiter;}else if(language==Language.RUST&&q=='"'||escapedAt(text,length)&&(language==Language.PYTHON||language==Language.CPP||language==Language.JAVASCRIPT||language==Language.GROOVY)){mode=Mode.QUOTED;closing=String.valueOf(q);}}
                continue;
            }
            if(Character.isDigit(c)||c=='.'&&i+1<length&&Character.isDigit(text.charAt(i+1))&&(i==0||text.charAt(i-1)!='.')){i=numberEnd(text,i,language);tokens.add(start,i,Kind.NUMBER);continue;}
            if(identifierStart(text,i)){
                i=identifierEnd(text,i);String word=text.substring(start,i);Kind kind=Kind.IDENTIFIER;
                if(literal(word,language))kind=Kind.LITERAL;
                else if(keywords(language).contains(word))kind=CONTROL.contains(word)?Kind.CONTROL:Kind.KEYWORD;
                else if(language==Language.TOML&&spaces(text,i)<length&&text.charAt(spaces(text,i))=='=')kind=Kind.PROPERTY;
                else if(!json(language)&&language!=Language.TOML){
                    if(DECLARATION.contains(previous)||Character.isUpperCase(word.codePointAt(0))||language==Language.PYTHON&&PY_TYPES.contains(word))kind=Kind.TYPE;
                    if(word.length()>1&&word.indexOf('_')>=0&&word.equals(word.toUpperCase(Locale.ROOT)))kind=Kind.CONSTANT;
                    if(kind==Kind.IDENTIFIER&&spaces(text,i)<length&&text.charAt(spaces(text,i))=='(')kind=Kind.FUNCTION;
                }
                tokens.add(start,i,kind);previous=word;continue;
            }
            i+=Character.charCount(text.codePointAt(i));tokens.add(start,i,Kind.OPERATOR);previous="";
        }
        State next=mode==Mode.NORMAL?State.NORMAL:incoming.mode()==mode&&incoming.depth()==depth&&incoming.closing().equals(closing)?incoming:new State(mode,depth,closing);
        return new Line(tokens.finish(),next);
    }
    private static int numberEnd(String text,int index,Language language){
        int i=index,n=text.length();boolean hex=i+1<n&&text.charAt(i)=='0'&&(text.charAt(i+1)=='x'||text.charAt(i+1)=='X');
        boolean radix=i+1<n&&text.charAt(i)=='0'&&"xXbBoO".indexOf(text.charAt(i+1))>=0;
        if(radix)i+=2;
        while(i<n){char c=text.charAt(i);if(Character.isDigit(c)||c=='_'||hex&&"abcdefABCDEF".indexOf(c)>=0){i++;continue;}
            if(c=='.'&&(i+1>=n||text.charAt(i+1)!='.')&&(i==index||text.charAt(i-1)!='.')){i++;continue;}
            if((hex?c=='p'||c=='P':c=='e'||c=='E')){i++;if(i<n&&(text.charAt(i)=='+'||text.charAt(i)=='-'))i++;continue;}break;
        }
        if((language==Language.JAVASCRIPT&&i<n&&text.charAt(i)=='n')||(language==Language.PYTHON&&i<n&&(text.charAt(i)=='j'||text.charAt(i)=='J')))return i+1;
        if(language==Language.RUST&&i<n&&"iuf".indexOf(text.charAt(i))>=0)i=identifierEnd(text,i);
        else while(i<n&&"fFdDlLuU".indexOf(text.charAt(i))>=0)i++;
        return Math.max(index+1,i);
    }
    private static Line properties(String text,State incoming){
        int i=spaces(text,0),n=text.length();if(i==n)return new Line(List.of(),State.NORMAL);
        if(incoming.mode()==Mode.PROPERTY_VALUE)return new Line(List.of(new Token(i,n,Kind.STRING)),escapedAt(text,n)?incoming:State.NORMAL);
        if(text.charAt(i)=='#'||text.charAt(i)=='!')return new Line(List.of(new Token(i,n,Kind.COMMENT)),State.NORMAL);
        int start=i;while(i<n){char c=text.charAt(i);if(c=='\\'&&i+1<n){i+=2;continue;}if(c=='='||c==':'||Character.isWhitespace(c))break;i++;}
        Tokens tokens=new Tokens();tokens.add(start,i,Kind.PROPERTY);i=spaces(text,i);if(i<n&&(text.charAt(i)=='='||text.charAt(i)==':')){tokens.add(i,i+1,Kind.OPERATOR);i++;}i=spaces(text,i);tokens.add(i,n,Kind.STRING);
        return new Line(tokens.finish(),escapedAt(text,n)?new State(Mode.PROPERTY_VALUE,0,""):State.NORMAL);
    }
}
