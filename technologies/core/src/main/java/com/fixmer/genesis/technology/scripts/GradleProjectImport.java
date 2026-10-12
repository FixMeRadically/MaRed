package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.storage.RepositoryPath;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Bounded local directory copy/export. Never evaluates Gradle, downloads dependencies or follows links. */
public final class GradleProjectImport {
    public static final int MAX_ENTRIES=2000, MAX_FILE_BYTES=16*1024*1024;
    public static final long MAX_TOTAL_BYTES=128L*1024*1024;
    public record Summary(int files,long bytes) {}
    public record Edit(String baseline,String text) {}
    // Snapshot equality describes the copied data, not provider-specific metadata.
    private record Stamp(long size,String sha256,boolean directory) {}
    private record Snapshot(SortedMap<String,Stamp> entries,long bytes) {}
    private static final Set<String> HIDDEN=Set.of(".gitignore",".gitattributes",".editorconfig",".gitkeep",".github");
    private static final Set<String> SKIP=Set.of(".git",".gradle",".idea",".vscode",".settings",".genesis-gradle-cache",".genesis-recovery",".exports",".genesis-build");
    private GradleProjectImport() {}
    public static boolean editable(String path){
        return com.fixmer.genesis.technology.storage.ProjectPath.valid(path)&&!path.equalsIgnoreCase("genesis-project.properties")&&
                (com.fixmer.genesis.technology.storage.ProjectPath.plainTextDotfile(path)||path.equals("gradlew")||path.equals("gradlew.bat")||path.toLowerCase(Locale.ROOT).matches(".+\\.(java|kt|kts|gradle|groovy|scala|py|rs|cpp|c|h|hpp|json|txt|md|toml|properties|xml|yaml|yml|cfg|conf|mcmeta|mr|mcfunction|csv|js|ts|css|html|glsl|vert|frag|sql|bat|sh)"));
    }
    private static boolean validPath(String path){
        return com.fixmer.genesis.technology.storage.ProjectPath.valid(path);
    }
    private static boolean skipped(Path relative,boolean directory){
        String name=relative.getFileName().toString();
        if(SKIP.contains(name))return true;
        if(!directory&&(name.equals(".build-tools.properties")||name.equals(DependencyJournal.NAME)||relative.toString().equals("genesis-project.properties")))return true;
        if(directory&&(name.equals("build")||name.equals("out"))){for(Path part:relative)if(part.toString().equals("src")||part.toString().equals("tests"))return false;return true;}
        return false;
    }
    private static void cancelled(BooleanSupplier stop)throws IOException{if(stop.getAsBoolean())throw new IOException("Import/export cancelled");}
    private static Snapshot scan(Path root,BooleanSupplier stop,boolean fingerprint)throws IOException{return scan(root,stop,fingerprint,"");}
    private static Snapshot scan(Path root,BooleanSupplier stop,boolean fingerprint,String prefix)throws IOException{
        if(!root.isAbsolute()||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(root))throw new IOException("Select an absolute regular project directory");
        Path real=root.toRealPath();var result=new TreeMap<String,Stamp>();var aliases=new HashSet<String>();long[] total={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            private void inspect(Path path,BasicFileAttributes attributes)throws IOException{
                cancelled(stop);
                // walkFileTree may supply cached directory-enumeration attributes on Windows.
                attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(++count[0]>MAX_ENTRIES)throw new IOException("Imported project exceeds 2000 entries");
                String relative=root.relativize(path).toString().replace('\\','/');
                if(!validPath(relative)||!aliases.add(relative.toLowerCase(Locale.ROOT)))throw new IOException("Invalid or case-aliased project path: "+relative);
                if(attributes.isSymbolicLink()||!path.toRealPath().startsWith(real))throw new IOException("Link or junction escapes project: "+relative);
                if(!attributes.isDirectory()&&!attributes.isRegularFile())throw new IOException("Not a regular project file: "+relative);
                if(!attributes.isDirectory()){
                    if(attributes.size()>MAX_FILE_BYTES)throw new IOException("Imported file exceeds 16 MiB: "+relative);
                    if(editable(prefix.isEmpty()?relative:prefix+"/"+relative)&&attributes.size()>SourceProjects.MAX_BYTES)throw new IOException("Editable file exceeds 4 MiB: "+relative);
                    total[0]+=attributes.size();if(total[0]>MAX_TOTAL_BYTES)throw new IOException("Imported project exceeds 128 MiB");
                }
                String hash=null;
                if(fingerprint&&!attributes.isDirectory())hash=transfer(path,OutputStream.nullOutputStream(),attributes.size(),null,stop);
                // Directory sizes/timestamps are not a portable representation of their contents.
                result.put(relative,new Stamp(attributes.isDirectory()?0:attributes.size(),hash,attributes.isDirectory()));
            }
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes a)throws IOException{
                cancelled(stop);if(dir.equals(root))return FileVisitResult.CONTINUE;
                if(skipped(prefix.isEmpty()?root.relativize(dir):Path.of(prefix).resolve(root.relativize(dir)),true))return FileVisitResult.SKIP_SUBTREE;inspect(dir,a);return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes a)throws IOException{
                if(!skipped(prefix.isEmpty()?root.relativize(file):Path.of(prefix).resolve(root.relativize(file)),false))inspect(file,a);return FileVisitResult.CONTINUE;
            }
        });
        return new Snapshot(Collections.unmodifiableSortedMap(result),total[0]);
    }
    private static void unchanged(Path source,Snapshot before,BooleanSupplier stop)throws IOException{
        if(!before.equals(scan(source,stop,true)))throw new IOException("Project changed while copying/exporting; retry with external writers stopped");
    }
    /** Destination must be an empty staging directory owned by the caller. Caller publishes or removes it. */
    public static Summary copy(Path source,Path destination,BooleanSupplier stop)throws IOException{
        return copy(source,destination,stop,true,false);
    }
    public static Summary copy(Path source,Path destination,BooleanSupplier stop,boolean requireBuild,boolean allowMetadata)throws IOException{return copy(source,destination,stop,requireBuild,allowMetadata,"");}
    static Summary copyContents(Path source,Path destination,BooleanSupplier stop,String prefix)throws IOException{return copy(source,destination,stop,false,true,prefix);}
    private static Summary copy(Path source,Path destination,BooleanSupplier stop,boolean requireBuild,boolean allowMetadata,String prefix)throws IOException{
        Objects.requireNonNull(stop);if(!Files.isDirectory(destination,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(destination))throw new IOException("Invalid import staging directory");
        try(var entries=Files.list(destination)){if(entries.findAny().isPresent())throw new IOException("Import staging directory must be empty");}
        Path from=source.toAbsolutePath().normalize(),to=destination.toRealPath();Path real=from.toRealPath();
        if(real.startsWith(to)||to.startsWith(real))throw new IOException("Source and destination must not contain each other");
        if(!allowMetadata&&Files.exists(from.resolve("genesis-project.properties"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Source already has Genesis metadata");
        if(requireBuild&&!BuildTools.gradleRoot(from))throw new IOException("Select a Gradle root with build.gradle(.kts) or settings.gradle(.kts)");
        Snapshot snapshot=scan(from,stop,true,prefix);for(String path:snapshot.entries.keySet())if(prefix.isEmpty()&&path.equalsIgnoreCase("genesis-project.properties"))throw new IOException("Source already has Genesis metadata");int files=0;
        for(var e:snapshot.entries.entrySet()){
            cancelled(stop);Path input=from.resolve(e.getKey()),output=to.resolve(e.getKey());
            if(e.getValue().directory){Files.createDirectories(output);continue;}
            Files.createDirectories(output.getParent());try(var out=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)){transfer(input,out,e.getValue().size,e.getValue().sha256,stop);}
            // Preserve executable Wrapper scripts on Unix; copy does not execute them.
            if(Files.isExecutable(input))output.toFile().setExecutable(true);
            files++;
        }
        if(!snapshot.equals(scan(from,stop,true,prefix)))throw new IOException("Copy source changed; retry with external writers stopped");return new Summary(files,snapshot.bytes);
    }
    public static List<String> editableFiles(Path root)throws IOException{
        var result=new ArrayList<String>();for(var e:scan(root,()->false,false).entries.entrySet())if(!e.getValue().directory&&editable(e.getKey()))result.add(e.getKey());return List.copyOf(result);
    }
    /** Refuse publishing an addition that would make the complete project unreadable. */
    static void validateAddition(Path project,Path data,boolean directory,String destination)throws IOException{
        Snapshot current=scan(project,()->false,false);long bytes;int count;
        if(directory){Snapshot addition=scan(data,()->false,false,"src");bytes=addition.bytes;count=addition.entries.size()+1;
            for(String path:addition.entries.keySet())if(!com.fixmer.genesis.technology.storage.ProjectPath.valid(destination+"/"+path))throw new IOException("Destination path limit: "+path);
        }else{bytes=Files.size(data);count=1;if(editable(destination)&&bytes>SourceProjects.MAX_BYTES)throw new IOException("Editable copy exceeds 4 MiB");}
        if(current.entries.size()+count>MAX_ENTRIES||current.bytes+bytes>MAX_TOTAL_BYTES)throw new IOException("Copy exceeds project entry or byte limit");
    }
    public static List<String> allFiles(Path root)throws IOException{
        var result=new ArrayList<String>();for(var e:scan(root,()->false,false).entries.entrySet())if(!e.getValue().directory&&com.fixmer.genesis.technology.storage.ProjectPath.valid(e.getKey()))result.add(e.getKey());return List.copyOf(result);
    }
    public static List<String> folders(Path root)throws IOException{
        var result=new ArrayList<String>();for(var e:scan(root,()->false,false).entries.entrySet())if(e.getValue().directory&&com.fixmer.genesis.technology.storage.ProjectPath.valid(e.getKey()))result.add(e.getKey());return List.copyOf(result);
    }
    private static MessageDigest digest(){
        try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError("SHA-256 is required by Java",impossible);}
    }
    private static String transfer(Path input,OutputStream output,long expected,String expectedHash,BooleanSupplier stop)throws IOException{
        MessageDigest hash=digest();long count=0;
        try(var in=Files.newInputStream(input,LinkOption.NOFOLLOW_LINKS)){
            byte[] buffer=new byte[64*1024];int n;
            while((n=in.read(buffer))!=-1){
                cancelled(stop);count+=n;
                if(count>expected||count>MAX_FILE_BYTES)throw new IOException("File changed or size limit exceeded: "+input.getFileName());
                hash.update(buffer,0,n);output.write(buffer,0,n);
            }
        }
        cancelled(stop);
        if(count!=expected)throw new IOException("File changed while reading: "+input.getFileName());
        String value=HexFormat.of().formatHex(hash.digest());
        if(expectedHash!=null&&!expectedHash.equals(value))throw new IOException("File content changed while copying/exporting: "+input.getFileName());
        return value;
    }
    private static String text(byte[] data)throws IOException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString().replace("\r\n","\n").replace('\r','\n');}
    public static Path export(Path project,Path exports,String id,Map<String,Edit> edits,byte[] metadata)throws IOException{
        if(!SourceProjects.validProject(id)||metadata==null||metadata.length>4096)throw new IOException("Invalid export project id or metadata");
        Objects.requireNonNull(edits);
        Snapshot snapshot=scan(project,()->false,true);for(String path:edits.keySet())if(!editable(path)||!snapshot.entries.containsKey(path)||snapshot.entries.get(path).directory)throw new IOException("Missing editable file: "+path);
        Files.createDirectories(exports);Path temporary=Files.createTempFile(exports,".export-",".tmp"),target=exports.resolve(id+"-"+UUID.randomUUID()+".zip");
        long[] total={0};
        try{
            try(var out=new ZipOutputStream(Files.newOutputStream(temporary),StandardCharsets.UTF_8)){
                ZipEntry manifest=new ZipEntry("genesis-project.properties");manifest.setTime(0);out.putNextEntry(manifest);out.write(metadata);out.closeEntry();
                for(var e:snapshot.entries.entrySet()){
                    String path=e.getKey();boolean dir=e.getValue().directory;var entry=new ZipEntry(path+(dir?"/":""));entry.setTime(0);out.putNextEntry(entry);
                    if(!dir){Edit edit=edits.get(path);if(edit!=null){
                        if(edit.text==null||edit.text.length()>SourceProjects.MAX_BYTES)throw new IOException("Draft size limit: "+path);
                        byte[] disk;try(var input=Files.newInputStream(project.resolve(path),LinkOption.NOFOLLOW_LINKS)){disk=input.readNBytes(SourceProjects.MAX_BYTES+1);}
                        if(disk.length>SourceProjects.MAX_BYTES||!HexFormat.of().formatHex(digest().digest(disk)).equals(e.getValue().sha256)||!text(disk).equals(edit.baseline))throw new IOException("Source changed outside Scripts: "+path);
                        byte[] data=disk;
                        if(!edit.text.equals(edit.baseline)){ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(edit.text));data=new byte[encoded.remaining()];encoded.get(data);}
                        if(data.length>SourceProjects.MAX_BYTES)throw new IOException("Draft exceeds 4 MiB: "+path);out.write(data);total[0]+=data.length;
                    }else{transfer(project.resolve(path),out,e.getValue().size,e.getValue().sha256,()->false);total[0]+=e.getValue().size;}
                    if(total[0]>MAX_TOTAL_BYTES)throw new IOException("Export exceeds 128 MiB");}out.closeEntry();
                }
            }
            unchanged(project,snapshot,()->false);
            byte[] current;try(var input=Files.newInputStream(project.resolve("genesis-project.properties"),LinkOption.NOFOLLOW_LINKS)){current=input.readNBytes(4097);}
            if(!Arrays.equals(metadata,current))throw new IOException("Project metadata changed while exporting");
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);return target;
        }finally{Files.deleteIfExists(temporary);}
    }
}
