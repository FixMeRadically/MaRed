package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.storage.RepositoryPath;
import java.io.*;
import java.lang.module.ModuleFinder;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Explicit local Java execution in child JVMs. Process limits are NOT an OS security sandbox. */
public final class JavaSourceRunner {
    public static final int MAX_SOURCES=512, MAX_SOURCE_BYTES=16*1024*1024;
    public static final int MAX_LIBRARIES=32, MAX_LIBRARY_BYTES=8*1024*1024, MAX_LIBRARIES_BYTES=64*1024*1024;
    public static final int MAX_RESOURCES=512, MAX_RESOURCE_BYTES=8*1024*1024, MAX_RESOURCES_BYTES=32*1024*1024;
    public enum Status { SUCCESS, COMPILE_ERROR, RUNTIME_ERROR, CANCELLED, TIMEOUT, OUTPUT_LIMIT, UNAVAILABLE, INPUT_ERROR, IO_ERROR }
    public record Limits(long compileMillis,long runMillis,int outputBytes,int memoryMiB) {
        public Limits {
            if(compileMillis<1||compileMillis>60_000||runMillis<1||runMillis>60_000||outputBytes<1||outputBytes>256*1024||memoryMiB<16||memoryMiB>512)throw new IllegalArgumentException("Invalid Java execution limits");
        }
        public static Limits defaults(){return new Limits(20_000,10_000,64*1024,128);}
    }
    public record Result(Status status,String phase,int exitCode,String output) {}
    public static final class Cancellation {
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private volatile String phase="prepare";
        public void cancel(){cancelled.set(true);}
        public boolean cancelled(){return cancelled.get();}
        public String phase(){return phase;}
    }
    record ProcessResult(Status status,int exitCode,String output) {}
    private final Path javaHome;
    public JavaSourceRunner(){this(Path.of(System.getProperty("java.home")));}
    public JavaSourceRunner(Path javaHome){this.javaHome=Objects.requireNonNull(javaHome).toAbsolutePath().normalize();}
    private Path executable(){return javaHome.resolve("bin").resolve(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java");}
    public boolean available(){return Files.isExecutable(executable())&&(javaHome.equals(Path.of(System.getProperty("java.home")).toAbsolutePath().normalize())
            ? ModuleFinder.ofSystem().find("jdk.compiler").isPresent()
            : Files.isExecutable(javaHome.resolve("bin").resolve(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"javac.exe":"javac")));}
    public static boolean validMainClass(String value){return value!=null&&value.length()<=192&&value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");}
    static String argument(String value)throws IOException {
        if(value.indexOf('\n')>=0||value.indexOf('\r')>=0||value.indexOf('\0')>=0)throw new IOException("Invalid compiler argument");
        return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";
    }
    static Map<String,byte[]> validated(Map<String,String> sources)throws IOException {
        if(sources==null||sources.isEmpty()||sources.size()>MAX_SOURCES)throw new IOException("Java source count must be 1..512");
        Map<String,byte[]> result=new TreeMap<>();Set<String> aliases=new HashSet<>();long total=0;
        for(var e:sources.entrySet()){
            String path=e.getKey(),text=e.getValue();
            if(!com.fixmer.genesis.technology.storage.ProjectPath.valid(path)||!path.endsWith(".java")||RepositoryPath.leaf(path).equals("module-info.java")||!aliases.add(path.toLowerCase(Locale.ROOT)))throw new IOException("Invalid or duplicate Java source path: "+path);
            if(text==null||text.length()>SourceProjects.MAX_BYTES)throw new IOException("Java source file exceeds 4 MiB");
            byte[] b;
            try{ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));b=new byte[encoded.remaining()];encoded.get(b);}
            catch(CharacterCodingException bad){throw new IOException("Invalid UTF-8 source",bad);}
            if(b.length>SourceProjects.MAX_BYTES||(total+=b.length)>MAX_SOURCE_BYTES)throw new IOException("Java project exceeds source size limits");
            result.put(path,b);
        }
        return result;
    }
    public static boolean validLibrary(String name){return com.fixmer.genesis.technology.storage.ProjectPath.valid(name)&&name.indexOf('/')<0&&name.toLowerCase(Locale.ROOT).endsWith(".jar");}
    static Map<String,byte[]> librarySnapshot(Map<String,byte[]> libraries)throws IOException {
        if(libraries==null||libraries.size()>MAX_LIBRARIES)throw new IOException("Java libraries count exceeds 32");
        Map<String,byte[]> result=new TreeMap<>();Set<String> aliases=new HashSet<>();long total=0;
        for(var e:libraries.entrySet()){
            byte[] bytes=e.getValue();
            if(!validLibrary(e.getKey())||!aliases.add(e.getKey().toLowerCase(Locale.ROOT)))throw new IOException("Invalid or duplicate library name: "+e.getKey());
            if(bytes==null||bytes.length==0||bytes.length>MAX_LIBRARY_BYTES||(total+=bytes.length)>MAX_LIBRARIES_BYTES)throw new IOException("Java library size limit (8 MiB each / 64 MiB total)");
            result.put(e.getKey(),bytes.clone());
        }
        return result;
    }
    /** Inspect the same central-directory entries that javac/java will read. No class loading. */
    static long verifyLibrary(Path path,long remaining,Cancellation cancellation)throws IOException {
        long expanded=0;int count=0,manifests=0;Set<String> names=new HashSet<>();
        try(var jar=new java.util.jar.JarFile(path.toFile(),false)){
            var entries=jar.entries();byte[] buffer=new byte[8192];
            while(entries.hasMoreElements()){
                if(cancellation.cancelled())throw new IOException("Library inspection cancelled");
                var e=entries.nextElement();String name=e.getName();
                if(++count>4096||!names.add(name)||name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf(':')>=0||Arrays.stream(name.split("/",-1)).anyMatch(part->part.equals("..")||part.equals(".")))throw new IOException("Invalid or excessive JAR entries: "+path.getFileName());
                if(name.equalsIgnoreCase("META-INF/INDEX.LIST"))throw new IOException("JAR indexes are unsupported; use explicit libraries: "+path.getFileName());
                if(name.equalsIgnoreCase("META-INF/MANIFEST.MF")&&++manifests>1)throw new IOException("Ambiguous JAR manifests: "+path.getFileName());
                if(e.isDirectory())continue;
                long fileBytes=0;int n;
                try(var in=jar.getInputStream(e)){while((n=in.read(buffer))!=-1){
                    if(cancellation.cancelled())throw new IOException("Library inspection cancelled");
                    fileBytes+=n;expanded+=n;
                    if(fileBytes>MAX_LIBRARY_BYTES||expanded>remaining||name.equalsIgnoreCase("META-INF/MANIFEST.MF")&&fileBytes>64*1024)throw new IOException("Expanded JAR size limit: "+path.getFileName());
                }}
            }
            if(count==0)throw new IOException("Empty library archive: "+path.getFileName());
            var manifest=jar.getManifest();
            if(manifest!=null){String cp=manifest.getMainAttributes().getValue(java.util.jar.Attributes.Name.CLASS_PATH);
                if(cp!=null&&!cp.isBlank())throw new IOException("Manifest Class-Path is unsupported; place every dependency explicitly in libraries: "+path.getFileName());}
        }
        return expanded;
    }
    public static boolean validResource(String name){return com.fixmer.genesis.technology.storage.ProjectPath.valid(name)&&!name.toLowerCase(Locale.ROOT).endsWith(".class");}
    static Map<String,byte[]> resourceSnapshot(Map<String,byte[]> resources)throws IOException {
        if(resources==null||resources.size()>MAX_RESOURCES)throw new IOException("Resource count exceeds 512");
        Map<String,byte[]> result=new TreeMap<>();Map<String,String> spelling=new HashMap<>();Set<String> files=new HashSet<>(),folders=new HashSet<>();long total=0;
        for(var e:new TreeMap<>(resources).entrySet()){
            String path=e.getKey();byte[] data=e.getValue();if(!validResource(path))throw new IOException("Invalid resource path (compiled classes are not resources): "+path);
            String prefix="";String[] parts=path.split("/");
            for(int i=0;i<parts.length;i++){
                prefix=prefix.isEmpty()?parts[i]:prefix+"/"+parts[i];String folded=prefix.toLowerCase(Locale.ROOT);String prior=spelling.putIfAbsent(folded,prefix);
                if(prior!=null&&!prior.equals(prefix))throw new IOException("Case alias in resources: "+prefix);
                if(i<parts.length-1){if(files.contains(folded))throw new IOException("Resource file/directory conflict: "+prefix);folders.add(folded);}
                else{if(folders.contains(folded)||!files.add(folded))throw new IOException("Resource file/directory conflict: "+prefix);}
            }
            if(data==null||data.length>MAX_RESOURCE_BYTES||(total+=data.length)>MAX_RESOURCES_BYTES)throw new IOException("Resource size limit (8 MiB each / 32 MiB total)");
            result.put(path,data.clone());
        }
        return result;
    }
    public Result run(Map<String,String> sources,String mainClass,Limits limits,Cancellation cancellation){
        return run(sources,Map.of(),mainClass,limits,cancellation);
    }
    /** Local JARs are copied into the run snapshot and used only after explicit Run. */
    public Result run(Map<String,String> sources,Map<String,byte[]> libraries,String mainClass,Limits limits,Cancellation cancellation){
        return run(sources,libraries,Map.of(),mainClass,limits,cancellation);
    }
    public Result run(Map<String,String> sources,Map<String,byte[]> libraries,Map<String,byte[]> resources,String mainClass,Limits limits,Cancellation cancellation){
        Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);Path scratch=null;String output="";
        try{
            if(cancellation.cancelled())return new Result(Status.CANCELLED,"prepare",-1,"");
            if(!validMainClass(mainClass))return new Result(Status.INPUT_ERROR,"prepare",-1,"Invalid main class");
            Map<String,byte[]> sourceBytes,libraryBytes,resourceBytes;
            try{sourceBytes=validated(sources);libraryBytes=librarySnapshot(libraries);resourceBytes=resourceSnapshot(resources);}catch(IOException e){return new Result(Status.INPUT_ERROR,"prepare",-1,e.getMessage());}
            if(!available())return new Result(Status.UNAVAILABLE,"prepare",-1,"Java runtime must include the jdk.compiler module (use a full JDK).");
            try{BuildTools.validateCompilerForHost(javaHome);}catch(IOException incompatible){return new Result(Status.INPUT_ERROR,"prepare",-1,incompatible.getMessage());}
            scratch=Files.createTempDirectory("genesis-java-");Path classes=Files.createDirectory(scratch.resolve("classes"));Path empty=Files.createDirectory(scratch.resolve("empty"));
            Path libs=Files.createDirectory(scratch.resolve("libraries"));var classpath=new ArrayList<String>();long expanded=0;
            for(var e:libraryBytes.entrySet()){
                if(cancellation.cancelled())return new Result(Status.CANCELLED,"prepare",-1,"");
                Path file=libs.resolve(e.getKey());Files.write(file,e.getValue());
                try{expanded+=verifyLibrary(file,128L*1024*1024-expanded,cancellation);}
                catch(IOException bad){return new Result(cancellation.cancelled()?Status.CANCELLED:Status.INPUT_ERROR,"prepare",-1,bad.getMessage());}
                classpath.add(file.toString());
            }
            String libraryPath=classpath.isEmpty()?empty.toString():String.join(File.pathSeparator,classpath);
            var compilerArgs=new ArrayList<String>(List.of("-encoding","UTF-8","-proc:none","-processorpath",empty.toString(),"-implicit:none","--release",Integer.toString(Runtime.version().feature()),"-classpath",libraryPath,"-sourcepath",scratch.resolve("src").toString(),"-d",classes.toString()));
            for(var e:sourceBytes.entrySet()){if(cancellation.cancelled())return new Result(Status.CANCELLED,"prepare",-1,"");Path file=scratch.resolve(e.getKey());Files.createDirectories(file.getParent());Files.write(file,e.getValue());compilerArgs.add(file.toString());}
            StringBuilder args=new StringBuilder();for(String arg:compilerArgs)args.append(argument(arg)).append('\n');
            Path argFile=scratch.resolve("javac.args");Files.writeString(argFile,args,StandardCharsets.UTF_8);
            var compile=new ArrayList<String>(jvm(256));compile.addAll(List.of("-m","jdk.compiler/com.sun.tools.javac.Main","@"+argFile));
            cancellation.phase="compile";ProcessResult compiled=process(compile,scratch,limits.compileMillis,limits.outputBytes,cancellation);
            output=compiled.output;
            if(compiled.status!=Status.SUCCESS)return new Result(compiled.status==Status.RUNTIME_ERROR?Status.COMPILE_ERROR:compiled.status,"compile",compiled.exitCode,output);
            cancellation.phase="prepare";
            for(var e:resourceBytes.entrySet()){
                if(cancellation.cancelled())return new Result(Status.CANCELLED,"prepare",-1,output);
                Path file=classes.resolve(e.getKey());
                if(Files.exists(file))return new Result(Status.INPUT_ERROR,"prepare",-1,output+"Resource conflicts with compiled output: "+e.getKey());
                Files.createDirectories(file.getParent());Files.write(file,e.getValue(),StandardOpenOption.CREATE_NEW);
            }
            cancellation.phase="run";var launch=new ArrayList<String>(jvm(limits.memoryMiB));launch.addAll(List.of("-cp",classes+ (classpath.isEmpty()?"":File.pathSeparator+libraryPath),mainClass));
            ProcessResult executed=process(launch,scratch,limits.runMillis,limits.outputBytes,cancellation);
            return new Result(executed.status,"run",executed.exitCode,output+executed.output);
        }catch(InterruptedException e){Thread.currentThread().interrupt();cancellation.cancel();return new Result(Status.CANCELLED,cancellation.phase,-1,output);}
        catch(IOException|RuntimeException e){return new Result(Status.IO_ERROR,cancellation.phase,-1,output+e.getMessage());}
        finally{cancellation.phase="cleanup";if(scratch!=null)cleanup(scratch);cancellation.phase="finished";}
    }
    List<String> jvm(int memory){return List.of(executable().toString(),"-Xmx"+memory+"m","-XX:MaxMetaspaceSize=96m","-XX:+ExitOnOutOfMemoryError","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8");}
    static ProcessResult process(List<String> command,Path directory,long timeout,int limit,Cancellation cancellation)throws IOException,InterruptedException {
        if(cancellation.cancelled())return new ProcessResult(Status.CANCELLED,-1,"");
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        for(String key:List.of("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS","JDK_JAVAC_OPTIONS","CLASSPATH"))builder.environment().remove(key);
        Process child=builder.start();
        try{child.getOutputStream().close();}catch(IOException e){child.destroyForcibly();throw e;}
        Set<ProcessHandle> descendants=ConcurrentHashMap.newKeySet();
        var bytes=new ByteArrayOutputStream(Math.min(limit,8192));var overflow=new AtomicBoolean();var readError=new AtomicReference<IOException>();
        Thread reader=new Thread(()->{try(InputStream in=child.getInputStream()){byte[] b=new byte[4096];int n;
            while((n=in.read(b))>=0){synchronized(bytes){int room=limit-bytes.size();if(n>room)overflow.set(true);if(room>0)bytes.write(b,0,Math.min(room,n));}if(overflow.get())break;}
        }catch(IOException e){readError.set(e);}},"Genesis Java output");reader.setDaemon(true);
        Thread shutdown=new Thread(()->terminate(child,descendants),"Genesis Java shutdown");boolean hook=false,descendantsAvailable=true;Status status=Status.SUCCESS;
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
        try{
            Runtime.getRuntime().addShutdownHook(shutdown);hook=true;reader.start();
            while(true){
                if(descendantsAvailable)try(var stream=child.descendants()){stream.limit(1024).forEach(h->{if(descendants.size()<1024)descendants.add(h);});}catch(RuntimeException ignored){descendantsAvailable=false; /* Some hosts deny process-tree enumeration. */ }
                if(cancellation.cancelled()||Thread.currentThread().isInterrupted()){status=Status.CANCELLED;break;}
                if(overflow.get()){status=Status.OUTPUT_LIMIT;break;}
                if(readError.get()!=null){status=Status.IO_ERROR;break;}
                if(!child.isAlive())break;
                if(System.nanoTime()>=deadline){status=Status.TIMEOUT;break;}
                child.waitFor(25,TimeUnit.MILLISECONDS);
            }
        }finally{
            // Also remove ordinary descendants after successful main() completion.
            terminate(child,descendants);
            if(hook)try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException ignored){}
            child.waitFor(2,TimeUnit.SECONDS);reader.join(1000);
            if(reader.isAlive()){child.getInputStream().close();reader.join(1000);}
        }
        int code=child.isAlive()?-1:child.exitValue();
        if(status==Status.SUCCESS){if(overflow.get())status=Status.OUTPUT_LIMIT;else if(readError.get()!=null)status=Status.IO_ERROR;else if(code!=0)status=Status.RUNTIME_ERROR;}
        String text;synchronized(bytes){text=bytes.toString(StandardCharsets.UTF_8);}
        return new ProcessResult(status,code,text);
    }
    private static void terminate(Process process,Set<ProcessHandle> known){
        try(var stream=process.descendants()){stream.limit(1024).forEach(h->{if(known.size()<1024)known.add(h);if(h.isAlive())h.destroyForcibly();});}catch(RuntimeException ignored){}
        for(ProcessHandle handle:known)try{if(handle.isAlive())handle.destroyForcibly();}catch(RuntimeException ignored){}
        if(process.isAlive())process.destroyForcibly();
    }
    private static void cleanup(Path root){
        // Do not follow symlinks created by user code; cap work on an unexpectedly large tree.
        try(var stream=Files.walk(root)){var paths=stream.limit(4097).toList();if(paths.size()>4096)return;
            for(Path p:paths.stream().sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);
        }catch(IOException|RuntimeException ignored){ /* A locked or oversized scratch directory may remain in the OS temp folder. */ }
    }
}
