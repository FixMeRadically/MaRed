package com.fixmer.genesis.technology.scripts;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded discovery and fingerprint-checked delivery of existing build outputs. Never installs mods. */
public final class BuildArtifacts {
    public static final int MAX_ENTRIES=10000,MAX_ARTIFACTS=128,MAX_DEPTH=16;
    public static final long MAX_FILE_BYTES=256L*1024*1024,MAX_TOTAL_BYTES=512L*1024*1024;
    private static final Set<String> SKIP=Set.of(".git",".gradle",".idea","node_modules","out","run","target");
    public record Artifact(String path,long size,String sha256,String inputs,String buildId,String profile){
        public Artifact(String path,long size,String sha256){this(path,size,sha256,"","","");}
        public Artifact withBuild(String input,String id){return new Artifact(path,size,sha256,input,id,profile);}
        public Artifact{if(inputs==null||!inputs.isEmpty()&&!inputs.matches("[0-9a-f]{64}")||buildId==null||!buildId.isEmpty()&&!buildId.matches("[a-f0-9-]{36}")||profile==null||!validPath(path)||size<0||size>MAX_FILE_BYTES||sha256==null||!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid artifact snapshot");}
        public String kind(){if(!profile.isEmpty())return profile;String name=path.toLowerCase(Locale.ROOT);return name.endsWith("-sources.jar")?"sources":name.endsWith("-javadoc.jar")?"javadoc":name.endsWith(".jar")?"jar":"distribution";}
    }
    public record Inventory(List<Artifact> artifacts,boolean limited,List<String> issues){public Inventory{artifacts=List.copyOf(artifacts);issues=List.copyOf(issues);}}
    public static Inventory empty(){return new Inventory(List.of(),false,List.of());}
    private BuildArtifacts(){}
    private static IOException fail(String message){return new IOException(message);}
    private static void stop(BooleanSupplier cancelled)throws IOException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw fail("Artifact operation cancelled");}
    private static boolean archive(String name){String n=name.toLowerCase(Locale.ROOT);return n.endsWith(".jar")||n.endsWith(".zip")||n.endsWith(".tar")||n.endsWith(".tar.gz")||n.endsWith(".tgz");}
    private static boolean validPath(String path){
        if(path==null||path.isEmpty()||path.length()>1024)return false;String[] parts=path.split("/",-1);if(parts.length>MAX_DEPTH)return false;
        for(String part:parts){if(part.isEmpty()||part.equals(".")||part.equals("..")||part.length()>255||part.endsWith(".")||part.endsWith(" ")||part.chars().anyMatch(c->c<32||c==127||"<>:\"\\|?*".indexOf(c)>=0))return false;String stem=part.split("\\.",2)[0].toUpperCase(Locale.ROOT);if(stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))return false;}
        return archive(parts[parts.length-1]);
    }
    private static void safe(Path root,Path path)throws IOException{
        if(!path.normalize().startsWith(root))throw fail("Artifact path escapes project");
        for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw fail("Linked artifact path is not supported");
        if(!path.toRealPath().startsWith(root.toRealPath()))throw fail("Resolved artifact path escapes project");
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static Artifact fingerprint(Path root,Path file,BooleanSupplier cancelled)throws IOException{return fingerprint(root,file,cancelled,MAX_FILE_BYTES);}
    private static Artifact fingerprint(Path root,Path file,BooleanSupplier cancelled,long budget)throws IOException{
        stop(cancelled);safe(root,file);if(!validPath(root.relativize(file).toString().replace('\\','/')))throw fail("Artifact path is not portable");var before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()>budget)throw fail("Artifact file exceeds limit or is not regular");
        var hash=digest();long count=0;try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer,0,(int)Math.min(buffer.length,before.size()-count+1)))!=-1){stop(cancelled);count+=n;if(count>before.size())throw fail("Artifact grew beyond limit");hash.update(buffer,0,n);}}
        stop(cancelled);safe(root,file);var after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(count!=before.size()||count!=after.size()||!after.isRegularFile()||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw fail("Artifact changed while reading");
        String profile="";if(file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")){String name=file.getFileName().toString().toLowerCase(Locale.ROOT);boolean source=name.endsWith("-sources.jar"),javadoc=name.endsWith("-javadoc.jar");var info=JarInspection.inspect(file,cancelled,!source&&!javadoc);profile=source?"sources":javadoc?"javadoc":info.mod()?(info.embedded()?"mod_embedded":"mod"):info.embedded()?"embedded":"jar";}return new Artifact(root.relativize(file).toString().replace('\\','/'),count,HexFormat.of().formatHex(hash.digest()),"","",profile);
    }
    private static boolean same(Artifact a,Artifact b){return a.path().equals(b.path())&&a.size()==b.size()&&a.sha256().equals(b.sha256());}
    public static Inventory capture(Path project,List<Path> files,BooleanSupplier cancelled)throws IOException{var found=new ArrayList<Artifact>();var issues=new ArrayList<String>();long bytes=0;var unique=new HashSet<Path>();for(Path file:files){if(!unique.add(file.toAbsolutePath().normalize()))continue;if(found.size()>=MAX_ARTIFACTS){issues.add("Archive count limit");break;}try{Artifact a=fingerprint(project.toAbsolutePath().normalize(),file,cancelled);if((bytes+=a.size())>MAX_TOTAL_BYTES){issues.add("Archive byte budget");break;}found.add(a);}catch(IOException e){stop(cancelled);issues.add(e.getMessage());}}return new Inventory(found,!issues.isEmpty(),issues);}
    public static Inventory scan(Path project,BooleanSupplier cancelled)throws IOException{
        Path root=Objects.requireNonNull(project).toAbsolutePath().normalize();Objects.requireNonNull(cancelled);safe(root,root);if(!Files.isDirectory(root))throw fail("Artifact project is not a directory");
        class Scan extends SimpleFileVisitor<Path>{
            final List<Artifact> found=new ArrayList<>();final List<String> issues=new ArrayList<>();int entries;long bytes;boolean limited;
            void issue(String message){if(issues.size()<16)issues.add(message);else limited=true;}
            boolean visit()throws IOException{stop(cancelled);if(++entries>MAX_ENTRIES){limited=true;return false;}return true;}
            void outputs(Path directory)throws IOException{
                if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))return;
                try{safe(root,directory);if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)){issue("Not a regular output directory: "+root.relativize(directory));return;}
                    try(var files=Files.newDirectoryStream(directory)){for(Path file:files){if(!visit())return;if(!archive(file.getFileName().toString()))continue;
                        if(found.size()>=MAX_ARTIFACTS){limited=true;return;}
                        try{safe(root,file);long size=Files.size(file);if(size>MAX_FILE_BYTES||size>MAX_TOTAL_BYTES-bytes){limited=true;issue("Artifact size budget reached: "+root.relativize(file));continue;}
                            Artifact snapshot=fingerprint(root,file,cancelled,size);bytes+=snapshot.size();found.add(snapshot);
                        }catch(IOException error){stop(cancelled);issue(root.relativize(file)+": "+error.getMessage());}
                    }}
                }catch(IOException error){stop(cancelled);issue(root.relativize(directory)+": "+error.getMessage());}
            }
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException{
                if(!visit())return FileVisitResult.TERMINATE;
                if(!dir.equals(root)){String name=dir.getFileName().toString();if(name.startsWith(".")||SKIP.contains(name.toLowerCase(Locale.ROOT)))return FileVisitResult.SKIP_SUBTREE;}
                try{safe(root,dir);}catch(IOException error){issue(error.getMessage());return FileVisitResult.SKIP_SUBTREE;}
                if(dir.getFileName()!=null&&dir.getFileName().toString().equals("build")&&!dir.equals(root)){if(dir.getParent().equals(root))return FileVisitResult.SKIP_SUBTREE;outputs(dir.resolve("libs"));outputs(dir.resolve("distributions"));if(entries>MAX_ENTRIES||found.size()>=MAX_ARTIFACTS){limited=true;return FileVisitResult.TERMINATE;}return FileVisitResult.SKIP_SUBTREE;}
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException{if(!visit())return FileVisitResult.TERMINATE;if(attrs.isDirectory())limited=true;return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFileFailed(Path file,IOException error)throws IOException{if(!visit())return FileVisitResult.TERMINATE;issue(root.relativize(file)+": "+error.getMessage());return FileVisitResult.CONTINUE;}
        }
        var scan=new Scan();scan.outputs(root.resolve("build/libs"));scan.outputs(root.resolve("build/distributions"));Files.walkFileTree(root,EnumSet.noneOf(FileVisitOption.class),MAX_DEPTH,scan);stop(cancelled);scan.found.sort(Comparator.comparing(Artifact::path));return new Inventory(scan.found,scan.limited,scan.issues);
    }
    public static Path deliver(Path project,Artifact expected,Path destination,BooleanSupplier cancelled)throws IOException{
        Path root=project.toAbsolutePath().normalize(),exports=destination.toAbsolutePath().normalize();Objects.requireNonNull(expected);Objects.requireNonNull(cancelled);stop(cancelled);
        Path source=root.resolve(expected.path()).normalize();safe(root,source);
        if(!expected.inputs().isEmpty()&&!BuildInputs.capture(root,cancelled,expected.buildId().isEmpty()||!GradleOutputs.reported(root,expected.buildId())?List.of():GradleOutputs.inputExclusions(root,expected.buildId())).equals(expected.inputs()))throw fail("Project inputs changed since this build; build again before delivery");
        if(!same(fingerprint(root,source,cancelled),expected))throw fail("Artifact changed since the build; build again before delivery");
        for(Path p=exports;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw fail("Linked delivery folder is not supported");
        Files.createDirectories(exports);for(Path p=exports;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw fail("Linked delivery folder is not supported");
        Path folder=Files.createTempDirectory(exports,root.getFileName()+"-"),part=null;boolean published=false;
        try{
            part=Files.createTempFile(folder,".artifact-",".part");var hash=digest();long count=0;
            try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(part,StandardOpenOption.TRUNCATE_EXISTING)){byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))!=-1){stop(cancelled);count+=n;if(count>expected.size())throw fail("Artifact changed while copying");out.write(buffer,0,n);hash.update(buffer,0,n);}}
            if(count!=expected.size()||!HexFormat.of().formatHex(hash.digest()).equals(expected.sha256())||!same(fingerprint(root,source,cancelled),expected))throw fail("Artifact changed while copying; no file delivered");
            if(!expected.inputs().isEmpty()&&!BuildInputs.capture(root,cancelled,expected.buildId().isEmpty()||!GradleOutputs.reported(root,expected.buildId())?List.of():GradleOutputs.inputExclusions(root,expected.buildId())).equals(expected.inputs()))throw fail("Project inputs changed during delivery");
            stop(cancelled);Path target=folder.resolve(source.getFileName().toString());Files.move(part,target);published=true;return target;
        }finally{if(!published){if(part!=null)try{Files.deleteIfExists(part);}catch(IOException ignored){}try{Files.deleteIfExists(folder);}catch(IOException ignored){}}}
    }
}
