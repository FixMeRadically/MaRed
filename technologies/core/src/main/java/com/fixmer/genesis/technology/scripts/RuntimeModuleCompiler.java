package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.server.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Bounded child javac; immutable snapshot, no user classes loaded while compiling. */
public final class RuntimeModuleCompiler {
    public record Prepared(RuntimeBundle bundle,String diagnostics) {}
    private RuntimeModuleCompiler() {}
    public static byte[] apiJar() throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var jar=new JarOutputStream(bytes)) {
            for(Class<?> type:List.of(ServerModule.class,ServerContext.class,ServerContext.Subscription.class)) {
                String name=type.getName().replace('.','/')+".class";
                try(var in=type.getResourceAsStream("/"+name)) {
                    if(in==null)throw new IOException("Packaged runtime API missing: "+name);
                    var entry=new JarEntry(name);entry.setTime(0);jar.putNextEntry(entry);in.transferTo(jar);jar.closeEntry();
                }
            }
        }
        return bytes.toByteArray();
    }
    private static void entry(String name) throws IOException {
        if(!JavaSourceRunner.validMainClass(name)||RuntimeBundle.protectedName(name))throw new IOException("Use your own ServerModule entry class");
    }
    private static List<Path> libraries(Path root,Map<String,byte[]> libraries,JavaSourceRunner.Cancellation token,long remaining) throws IOException {
        var result=new ArrayList<Path>();long expanded=0;
        Path folder=Files.createDirectory(root.resolve("libraries"));
        for(var e:JavaSourceRunner.librarySnapshot(libraries).entrySet()) {
            if(token.cancelled())throw new IOException("Compilation cancelled");
            Path file=folder.resolve(e.getKey());Files.write(file,e.getValue());
            expanded+=JavaSourceRunner.verifyLibrary(file,remaining-expanded,token);result.add(file);
        }
        return result;
    }
    public static Prepared compile(Map<String,String> sources,Map<String,byte[]> libraries,Map<String,byte[]> resources,String entry,JavaSourceRunner.Cancellation token) throws IOException,InterruptedException {
        return compile(sources,libraries,resources,entry,token,Path.of(System.getProperty("java.home")));
    }
    public static Prepared compile(Map<String,String> sources,Map<String,byte[]> libraries,Map<String,byte[]> resources,String entry,JavaSourceRunner.Cancellation token,Path javaHome) throws IOException,InterruptedException {
        RuntimeBundle.retryCleanup();BuildTools.validateCompilerForHost(javaHome);entry(entry);if(token.cancelled())throw new IOException("Compilation cancelled");
        var inputs=JavaSourceRunner.validated(sources);var assets=JavaSourceRunner.resourceSnapshot(resources);
        var runner=new JavaSourceRunner(javaHome);if(!runner.available())throw new IOException("Client Java needs full JDK with jdk.compiler");
        Path root=Files.createTempDirectory("genesis-server-module-");RuntimeBundle bundle=null;
        try {
            Path classes=Files.createDirectory(root.resolve("classes")),empty=Files.createDirectory(root.resolve("empty"));
            var jars=libraries(root,libraries,token,128L*1024*1024);Path api=root.resolve("genesis-server-api.jar");Files.write(api,apiJar());
            var cp=new ArrayList<String>();cp.add(api.toString());jars.forEach(p->cp.add(p.toString()));
            var args=new ArrayList<String>(List.of("-encoding","UTF-8","-proc:none","-processorpath",empty.toString(),"-implicit:none","--release",Integer.toString(Runtime.version().feature()),"-classpath",String.join(File.pathSeparator,cp),"-sourcepath",root.resolve("src").toString(),"-d",classes.toString()));
            for(var e:inputs.entrySet()){Path file=root.resolve(e.getKey());Files.createDirectories(file.getParent());Files.write(file,e.getValue());args.add(file.toString());}
            var text=new StringBuilder();for(String arg:args)text.append(JavaSourceRunner.argument(arg)).append('\n');
            Path arguments=root.resolve("javac.args");Files.writeString(arguments,text,StandardCharsets.UTF_8);
            var command=new ArrayList<>(runner.jvm(256));command.addAll(List.of("-m","jdk.compiler/com.sun.tools.javac.Main","@"+arguments));
            var result=JavaSourceRunner.process(command,root,20_000,64*1024,token);
            if(result.status()!=JavaSourceRunner.Status.SUCCESS)throw new IOException("Runtime compilation "+(result.status()==JavaSourceRunner.Status.RUNTIME_ERROR?JavaSourceRunner.Status.COMPILE_ERROR:result.status())+":\n"+result.output());
            int count=0;long size=0;try(var stream=Files.walk(classes)){var entries=stream.filter(Files::isRegularFile).iterator();while(entries.hasNext()){Path file=entries.next();
                if(++count>10000||(size+=Files.size(file))>64L*1024*1024)throw new IOException("Compiled runtime output limit");
                String name=classes.relativize(file).toString().replace(File.separatorChar,'.');
                if(name.endsWith(".class")&&RuntimeBundle.protectedName(name.substring(0,name.length()-6)))throw new IOException("Runtime cannot replace host/API classes: "+name);
            }}
            for(var e:assets.entrySet()){Path file=classes.resolve(e.getKey());if(Files.exists(file))throw new IOException("Resource/output conflict: "+e.getKey());Files.createDirectories(file.getParent());Files.write(file,e.getValue());}
            if(token.cancelled())throw new IOException("Compilation cancelled");
            var paths=new ArrayList<Path>();paths.add(classes);paths.addAll(jars);bundle=new RuntimeBundle(root,paths,entry);return new Prepared(bundle,result.output());
        } finally {if(bundle==null)cleanup(root);}
    }
    /** An already verified build artifact is copied again; deployment never runs a mutable build output. */
    public static Prepared fromJar(byte[] jarBytes,Map<String,byte[]> libraries,String entry,JavaSourceRunner.Cancellation token) throws IOException {
        RuntimeBundle.retryCleanup();entry(entry);if(jarBytes==null||jarBytes.length<1||jarBytes.length>64*1024*1024)throw new IOException("Runtime JAR limit: 64 MiB");
        Path root=Files.createTempDirectory("genesis-server-jar-");RuntimeBundle bundle=null;
        try {
            Path main=root.resolve("module.jar");Files.write(main,jarBytes);
            long[] expanded={JarInspection.inspect(main,token::cancelled).expandedBytes()};
            if(token.cancelled())throw new IOException("Deployment cancelled");
            var paths=new ArrayList<Path>();paths.add(main);paths.addAll(runtimeLibraries(root,libraries,token,expanded));NestedRuntimeJars.expand(List.copyOf(paths),root.resolve("nested"),paths,token,expanded);
            bundle=new RuntimeBundle(root,paths,entry);return new Prepared(bundle,"");
        } finally {if(bundle==null)cleanup(root);}
    }
    private static List<Path> runtimeLibraries(Path root,Map<String,byte[]> libraries,JavaSourceRunner.Cancellation token,long[] expanded)throws IOException{if(libraries==null||libraries.size()>128)throw new IOException("Runtime library count limit: 128");var out=new ArrayList<Path>();Path folder=Files.createDirectory(root.resolve("libraries"));long total=0;var hashes=new HashSet<String>();for(var e:libraries.entrySet()){if(token.cancelled())throw new IOException("Runtime library preparation cancelled");byte[] bytes=e.getValue();if(bytes==null||bytes.length>64*1024*1024||(total+=bytes.length)>512L*1024*1024)throw new IOException("Runtime libraries size limit");String hash=GradleOutputs.hash(bytes);if(!hashes.add(hash))continue;Path file=folder.resolve(hash+".jar");Files.write(file,bytes);expanded[0]+=JarInspection.inspect(file,token::cancelled,512L*1024*1024-expanded[0]).expandedBytes();out.add(file);}return out;}
    private static void cleanup(Path root){new RuntimeBundle(root,List.of(root),"genesis.cleanup.Entry").close();}
}
