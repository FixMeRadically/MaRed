package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.storage.ProjectPath;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

/** Bounded, side-effect-free interpretation of compiler/Gradle output, not a build executor. */
public final class BuildDiagnostics {
    public enum Severity { ERROR, WARNING }
    public record Diagnostic(Severity severity,String file,int line,int column,String message,String raw){
        public Diagnostic{
            Objects.requireNonNull(severity);Objects.requireNonNull(file);Objects.requireNonNull(message);Objects.requireNonNull(raw);
            if(!file.isEmpty()&&(!ProjectPath.valid(file)||line<1||column<0)||file.isEmpty()&&(line!=0||column!=0))throw new IllegalArgumentException("Invalid diagnostic position");
        }
    }
    public record Report(List<Diagnostic> diagnostics,boolean limited){public Report{diagnostics=List.copyOf(diagnostics);}}
    public static final int MAX_DIAGNOSTICS=200,MAX_OUTPUT=256*1024,MAX_LINE=4096;
    private static final Pattern ANSI=Pattern.compile("\\x1B\\[[0-9;]*[A-Za-z]");
    private static final Pattern JAVAC=Pattern.compile("^(.+?):([0-9]+)(?::([0-9]+))?:\\s*(error|warning|ошибка|предупреждение):\\s*(.*)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern GRADLE=Pattern.compile("^(?:Build file|Settings file|Script|Initialization script) ['\"](.+?)['\"] line: ([0-9]+).*$");
    private BuildDiagnostics(){}
    public static Report parse(String output,Path project){return parse(output,Objects.requireNonNull(project).toAbsolutePath().normalize().toString());}
    /** String root allows consumers to inspect Windows build output on another OS. */
    public static Report parse(String output,String projectDirectory){
        Objects.requireNonNull(output);Objects.requireNonNull(projectDirectory);boolean limited=output.length()>MAX_OUTPUT;
        if(limited)output=output.substring(output.length()-MAX_OUTPUT);var found=new LinkedHashSet<Diagnostic>();
        for(String raw:output.split("\\R",-1)){
            if(raw.length()>MAX_LINE){limited=true;continue;}String text=ANSI.matcher(raw).replaceAll("").strip();if(text.isEmpty())continue;
            Diagnostic diagnostic=null;Matcher javac=JAVAC.matcher(text),gradle=GRADLE.matcher(text);
            if(javac.matches()){
                int line=number(javac.group(2)),column=javac.group(3)==null?0:number(javac.group(3));String file=relative(javac.group(1),projectDirectory);
                if(line<1||column<0)file="";String type=javac.group(4).toLowerCase(Locale.ROOT);Severity severity=type.equals("warning")||type.equals("предупреждение")?Severity.WARNING:Severity.ERROR;
                diagnostic=new Diagnostic(severity,file,file.isEmpty()?0:line,file.isEmpty()?0:column,javac.group(5),text);
            }else if(gradle.matches()){
                int line=number(gradle.group(2));String file=relative(gradle.group(1),projectDirectory);if(line<1)file="";
                diagnostic=new Diagnostic(Severity.ERROR,file,file.isEmpty()?0:line,0,text,text);
            }else if(text.startsWith("Execution failed for task ")||text.startsWith("FAILURE: Build failed")||text.startsWith("> Could not resolve ")||text.startsWith("> Could not find ")){
                diagnostic=new Diagnostic(Severity.ERROR,"",0,0,text,text);
            }
            if(diagnostic!=null){if(found.contains(diagnostic))continue;if(found.size()==MAX_DIAGNOSTICS){limited=true;break;}found.add(diagnostic);}
        }
        return new Report(new ArrayList<>(found),limited);
    }
    private static int number(String value){try{int n=Integer.parseInt(value);return n>=0&&n<=10_000_000?n:-1;}catch(NumberFormatException invalid){return -1;}}
    private static String relative(String value,String root){
        value=value.strip();if(value.startsWith("file:"))try{URI uri=URI.create(value);if(uri.getAuthority()!=null&&!uri.getAuthority().isEmpty())return "";value=uri.getPath();if(value==null)return "";if(value.matches("^/[A-Za-z]:/.*"))value=value.substring(1);}catch(IllegalArgumentException invalid){return "";}
        value=value.replace('\\','/');root=root.replace('\\','/');while(root.endsWith("/")&&root.length()>1)root=root.substring(0,root.length()-1);
        boolean absolute=value.startsWith("/")||value.matches("^[A-Za-z]:/.*"),windows=root.matches("^[A-Za-z]:/.*")||root.startsWith("//");
        if(absolute){String prefix=root.endsWith("/")?root:root+"/";boolean inside=windows?value.regionMatches(true,0,prefix,0,prefix.length()):value.startsWith(prefix);if(!inside)return "";value=value.substring(prefix.length());}
        else if(value.startsWith("./"))value=value.substring(2);
        return ProjectPath.valid(value)&&GradleProjectImport.editable(value)?value:"";
    }
}
