package com.fixmer.genesis.technology.scripts;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
/** Per-invocation Gradle output report. The init script runs only with an explicitly requested build. */
public final class GradleOutputs  {
    private GradleOutputs() {
    }
    public static List<String> prune(Path project)throws IOException {
        Path state=project.resolve(".genesis-build");var folders=new ArrayList<Path>();var issues=new ArrayList<String>();int entries=0;
        if(!Files.isDirectory(state,LinkOption.NOFOLLOW_LINKS))return issues;
        try(var files=Files.newDirectoryStream(state)){for(Path p:files){if(++entries>10000)throw new IOException("Build state entry limit");if(p.getFileName().toString().matches("[a-f0-9-]{36}")&&Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))folders.add(p);}}
        folders.sort(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toMillis();}catch(IOException e){return Long.MAX_VALUE;}}));
        for(int i=0;i<folders.size()-15;i++){Path folder=folders.get(i);try{Files.walkFileTree(folder,new SimpleFileVisitor<Path>(){
            public FileVisitResult preVisitDirectory(Path p,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException{if(Files.isSymbolicLink(p)||!p.toRealPath().startsWith(state.toRealPath()))throw new IOException("Linked build snapshot");return FileVisitResult.CONTINUE;}
            public FileVisitResult visitFile(Path p,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException{Files.delete(p);return FileVisitResult.CONTINUE;}
            public FileVisitResult postVisitDirectory(Path p,IOException error)throws IOException{if(error!=null)throw error;Files.delete(p);return FileVisitResult.CONTINUE;}
        });}catch(IOException e){if(issues.size()<16)issues.add("Build snapshot cleanup deferred: "+e.getMessage());}}
        return List.copyOf(issues);
    }
    public static Path script(Path work,String id)throws IOException {
        Files.createDirectories(work);
        Path script=work.resolve("capture.gradle");
        Files.writeString(script,SCRIPT,StandardCharsets.UTF_8);
        return script;
    }
    public static List<Path> outputs(Path project,String id)throws IOException {
        var p=read(project,id);
        var result=new ArrayList<Path>();
        for(int i=0;i<Integer.parseInt(p.getProperty("archives","0"));i++) {
            String path=decode(p.getProperty("archive."+i));
            Path file=Path.of(path).toAbsolutePath().normalize();
            if(!file.startsWith(project.toAbsolutePath().normalize()))throw new IOException("Gradle archive is outside project: "+file);
            result.add(file);
        }
        return List.copyOf(result);
    }
    public static List<String> inputExclusions(Path project,String id)throws IOException {
        var p=read(project,id);
        var paths=new ArrayList<String>();
        int count;
        try {
            count=Integer.parseInt(p.getProperty("excludes","0"));
        }
        catch(NumberFormatException e) {
            throw new IOException("Malformed output exclusions",e);
        }
        if(count<0||count>10000)throw new IOException("Output exclusion count limit");
        Path root=project.toAbsolutePath().normalize();
        for(int i=0;i<count;i++) {
            Path path=Path.of(decode(p.getProperty("exclude."+i))).toAbsolutePath().normalize();
            if(path.equals(root)||!path.startsWith(root))continue;
            paths.add(root.relativize(path).toString().replace('\\','/'));
        }
        return List.copyOf(paths);
    }
    public static boolean reported(Path project,String id) {
        return Files.isRegularFile(project.resolve(".genesis-build").resolve(id).resolve("outputs.properties"),LinkOption.NOFOLLOW_LINKS);
    }
    public static Map<String,byte[]> runtime(Path project,String id,String artifact,BooleanSupplier cancelled)throws IOException {
        var p=read(project,id);
        int at=-1;
        for(int i=0;i<Integer.parseInt(p.getProperty("archives","0"));i++)if(project.resolve(artifact).toAbsolutePath().normalize().equals(Path.of(decode(p.getProperty("archive."+i))).toAbsolutePath().normalize())) {
            at=i;
            break;
        }
        if(at<0)throw new IOException("Artifact absent from this Gradle invocation");
        int count=Integer.parseInt(p.getProperty("runtime."+at+".count","0"));
        if(count>128)throw new IOException("Runtime classpath limit: 128 JARs");
        long total=0;
        var result=new LinkedHashMap<String,byte[]>();
        Path root=project.resolve(".genesis-build").resolve(id).toAbsolutePath().normalize();
        for(int i=0;i<count;i++) {
            if(cancelled.getAsBoolean())throw new IOException("Runtime snapshot cancelled");
            String name=p.getProperty("runtime."+at+"."+i);
            if(name==null||!name.matches("[0-9a-f]{64}\\.jar"))throw new IOException("Invalid runtime report filename");
            Path file=root.resolve("runtime").resolve(name);
            if(Files.isSymbolicLink(file)||!file.toRealPath().startsWith(root.toRealPath()))throw new IOException("Linked runtime snapshot");
            long size=Files.size(file);
            if(size>64L*1024*1024||(total+=size)>512L*1024*1024)throw new IOException("Runtime snapshot size limit: 64 MiB per JAR / 512 MiB total");
            byte[] data;
            try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
                data=in.readNBytes(64*1024*1024+1);
            }
            if(data.length!=size||!hash(data).equals(name.substring(0,64)))throw new IOException("Runtime dependency changed since build: "+name);
            result.put(name,data);
        }
        return Collections.unmodifiableMap(result);
    }
    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch(java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    private static String decode(String value)throws IOException {
        try {
            return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);
        }
        catch(RuntimeException e) {
            throw new IOException("Malformed Gradle output path",e);
        }
    }
    private static Properties read(Path project,String id)throws IOException {
        if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IOException("Invalid Gradle build identity");
        Path folder=project.resolve(".genesis-build").resolve(id),file=folder.resolve("outputs.properties");
        for(Path f=file;f!=null;f=f.getParent())if(Files.isSymbolicLink(f))throw new IOException("Linked Gradle output report");
        if(!file.toRealPath().startsWith(project.toRealPath()))throw new IOException("Gradle output report escapes project");
        var p=new Properties();
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            byte[] b=in.readNBytes(4*1024*1024+1);
            if(b.length>4*1024*1024)throw new IOException("Gradle output report limit");
            p.load(new StringReader(new String(b,StandardCharsets.UTF_8)));
        }
        if(!id.equals(p.getProperty("id")))throw new IOException("Stale Gradle output report");
        try {
            int count=Integer.parseInt(p.getProperty("archives","0"));
            if(count<0||count>128)throw new IOException("Gradle archive count limit");
        }
        catch(NumberFormatException bad) {
            throw new IOException("Malformed Gradle archive count",bad);
        }
        return p;
    }
    private static final String SCRIPT="""
 import org.gradle.api.tasks.bundling.AbstractArchiveTask
 import java.nio.file.Files
 import java.nio.file.StandardCopyOption
 import java.security.MessageDigest
 import java.util.Base64
 def genesisId = System.getProperty('genesis.build.id')
 def genesisRoot = new File(System.getProperty('genesis.build.root')).canonicalFile
 def genesisFolder = new File(genesisRoot, '.genesis-build/' + genesisId)
 def genesisPackagingTask = { task -> task instanceof AbstractArchiveTask || task.name.toLowerCase().contains('jarjar') || ['remapjar', 'shadowjar'].contains(task.name.toLowerCase()) }
 def genesisArchives = []
 def genesisExcludes = []
 gradle.taskGraph.whenReady { graph ->
     genesisArchives = graph.allTasks.findAll { genesisPackagingTask(it) }
     genesisExcludes = graph.allTasks.collectMany { it.outputs.files.files as List }.findAll { it.canonicalFile != genesisRoot && it.canonicalFile.toPath().startsWith(genesisRoot.toPath()) }.collect { it.canonicalPath }.unique()
 }
 def genesisCapture = {
     def report = new Properties()
     report.setProperty('id', genesisId)
     if (genesisExcludes.size() > 10000) throw new GradleException('Genesis output exclusion limit: 10000')
     report.setProperty('excludes', genesisExcludes.size().toString())
     genesisExcludes.eachWithIndex { path, i -> report.setProperty('exclude.' + i, Base64.encoder.encodeToString(path.getBytes('UTF-8'))) }
     int index = 0
     def snapshotCache = [:]
     long snapshotBytes = 0
     genesisArchives.each { task ->
         def candidates = task instanceof AbstractArchiveTask ? [task.archiveFile.get().asFile] : task.outputs.files.files.findAll { it.isFile() && it.name.toLowerCase().matches('.*[.](jar|zip|tar|tgz)') }
         candidates.each { candidate ->
         def archive = candidate.canonicalFile
         if (!archive.isFile()) return
         if (!archive.toPath().startsWith(genesisRoot.toPath())) throw new GradleException('Genesis output outside project: ' + archive)
         if (index >= 128) throw new GradleException('Genesis archive limit: 128')
         report.setProperty('archive.' + index, Base64.encoder.encodeToString(archive.absolutePath.getBytes('UTF-8')))
         def runtime = task.project.configurations.findByName('runtimeClasspath')
         def names = []
         if (runtime != null && runtime.canBeResolved) {
             runtime.files.findAll { it.isFile() && it.name.toLowerCase().endsWith('.jar') && it.canonicalFile != archive }.each { dependency ->
                 // Game/API classes belong to the host loader; do not duplicate the Minecraft distribution.
                 def gameArchive = false
                 def zip = new java.util.zip.ZipFile(dependency)
                 try { gameArchive = zip.getEntry('net/minecraft/server/MinecraftServer.class') != null || zip.getEntry('net/minecraft/client/Minecraft.class') != null } finally { zip.close() }
                 if (gameArchive) return
                 def key = dependency.canonicalPath
                 def hash = snapshotCache[key]
                 if (hash == null) {
                     if ((snapshotBytes += dependency.length()) > 512L * 1024 * 1024) throw new GradleException('Genesis runtime snapshot exceeds 512 MiB')
                     if (dependency.length() > 64L * 1024 * 1024) throw new GradleException('Genesis runtime dependency exceeds 64 MiB: ' + dependency)
                     def digest = MessageDigest.getInstance('SHA-256')
                     dependency.withInputStream { input -> byte[] buf = new byte[65536]; int n; while ((n = input.read(buf)) != -1) digest.update(buf, 0, n) }
                     hash = digest.digest().encodeHex().toString()
                     def destination = new File(genesisFolder, 'runtime/' + hash + '.jar')
                     destination.parentFile.mkdirs()
                     Files.copy(dependency.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                     snapshotCache[key] = hash
                 }
                 names.add(hash + '.jar')
             }
         }
         names = names.unique()
         if (names.size() > 128) throw new GradleException('Genesis runtime classpath exceeds 128 JARs')
         report.setProperty('runtime.' + index + '.count', names.size().toString())
         names.eachWithIndex { name, i -> report.setProperty('runtime.' + index + '.' + i, name) }
         index++
         }
     }
     report.setProperty('archives', index.toString())
     genesisFolder.mkdirs()
     def pending = new File(genesisFolder, 'outputs.part')
     pending.withWriter('UTF-8') { report.store(it, 'Genesis build outputs') }
     Files.move(pending.toPath(), new File(genesisFolder, 'outputs.properties').toPath(), StandardCopyOption.REPLACE_EXISTING)
 }
 gradle.projectsEvaluated {
     def capture = gradle.rootProject.tasks.register('genesisCaptureOutputs_' + genesisId.replace('-', '_')) {
         group = 'genesis'
         description = 'Capture verified archive outputs and runtime libraries for this invocation'
         doLast { genesisCapture() }
     }
     gradle.allprojects { project ->
         project.tasks.configureEach { archive ->
             if (!genesisPackagingTask(archive)) return
             archive.finalizedBy(capture)
             capture.configure { mustRunAfter(archive) }
         }
     }
 }
 """;
}
