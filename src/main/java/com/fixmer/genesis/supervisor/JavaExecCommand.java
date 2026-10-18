package com.fixmer.genesis.supervisor;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Public JavaExec components, without parsing a command-line string or OS argv.
 * Modular entry inference is deliberately left to Gradle, outside this classic helper. */
public final class JavaExecCommand {
    private JavaExecCommand() { }
    public static List<String> classic(String executable,List<String> jvm,List<String> classpath,String main,List<String> args)throws IOException {
        if(executable==null||!Path.of(executable).isAbsolute())throw new IOException("Unresolved Java executable");
        var result=new ArrayList<String>();result.add(executable);result.addAll(jvm);
        if(main==null||main.isBlank()){
            if(classpath.size()!=1)throw new IOException("Missing Java main entry");
            result.add("-jar");result.add(classpath.get(0));
        }else{
            if(!classpath.isEmpty()){result.add("-cp");result.add(String.join(File.pathSeparator,classpath));}
            result.add(main);
        }
        result.addAll(args);return List.copyOf(result);
    }
}
