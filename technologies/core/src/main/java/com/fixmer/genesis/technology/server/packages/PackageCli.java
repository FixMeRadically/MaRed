package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;

/** Explicit local authoring/verification only. Does not download, install, restart or trust new keys. */
public final class PackageCli {
    private PackageCli(){}
    public static void main(String[] args){
        try{
            if(args.length==2&&args[0].equals("keygen")){keys(Path.of(args[1]));return;}
            if(args.length==2&&args[0].equals("demo")){demo(Path.of(args[1]));return;}
            if(args.length==5&&args[0].equals("create")){create(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),Path.of(args[4]));return;}
            if((args.length==7&&args[0].equals("verify"))||((args.length==8||args.length==9)&&args[0].equals("plan"))){
                PublicKey key=PackageFiles.publicKey(Path.of(args[2]));var trust=Map.of(PackageFiles.keyId(key),key);var target=target(args);var next=PackageArchive.verify(Path.of(args[1]),trust,target);
                if(args[0].equals("verify")){System.out.println(json(summary(next,target)));return;}
                PackageArchive.Verified previous=null;
                if(args.length==9){
                    // Base may have a different loader version. It is still signature-verified against its own declared version;
                    // the immutable distribution/Minecraft and parent edge are checked again by the planner.
                    byte[] raw=untrustedManifest(Path.of(args[8]));Manifest old=PackageCodec.decode(raw);
                    previous=PackageArchive.verify(Path.of(args[8]),trust,new Target(target.distribution(),target.side(),target.minecraft(),old.loaderVersion(),target.protocol()));
                }
                var plan=PackagePlanner.plan(previous,next,target,Path.of(args[7]));System.out.println(json(planJson(plan)));if(!plan.ready())System.exit(3);return;
            }
            throw new IllegalArgumentException("Usage: keygen/demo <NEW-directory> | create <spec.properties> <private.der> <public.der> <NEW.zip> | verify <zip> <pinned-public.der> <distributionUUID> <client|server> <minecraft> <neoforge> | plan <same-six-arguments> <profile> [previous.zip]");
        }catch(Exception bad){System.err.println(json(Map.of("ok",false,"error",bad.getMessage()==null?bad.getClass().getSimpleName():bad.getMessage(),"writesApplied",0)));System.exit(2);}
    }
    private static Target target(String[] args){Side side=switch(args[4]){case "client"->Side.CLIENT;case "server"->Side.SERVER;default->throw new IllegalArgumentException("TARGET_SIDE_REQUIRED");};return new Target(UUID.fromString(args[3]),side,args[5],args[6],PROTOCOL);}
    private static byte[] untrustedManifest(Path archive)throws IOException{
        PackageFiles.safe(archive);if(Files.size(archive)>MAX_ARCHIVE)throw new IOException("ARCHIVE_SIZE_LIMIT");try(var zip=new java.util.zip.ZipFile(archive.toFile())){var entry=zip.getEntry(PackageArchive.MANIFEST);if(entry==null||entry.getSize()<0||entry.getSize()>MAX_MANIFEST)throw new IOException("MANIFEST_SIZE_LIMIT");try(var in=zip.getInputStream(entry)){byte[] raw=in.readNBytes(MAX_MANIFEST+1);if(raw.length>MAX_MANIFEST)throw new IOException("MANIFEST_SIZE_LIMIT");return raw;}}
    }
    static void keys(Path directory)throws IOException,GeneralSecurityException{
        directory=directory.toAbsolutePath().normalize();PackageFiles.safe(directory);Files.createDirectory(directory);
        try{PackageFiles.privateMode(directory,true);var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Path secret=directory.resolve("private.der"),pub=directory.resolve("public.der");Files.write(secret,pair.getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW);PackageFiles.privateMode(secret,false);Files.write(pub,pair.getPublic().getEncoded(),StandardOpenOption.CREATE_NEW);PackageFiles.privateMode(pub,false);
            System.out.println(json(Map.of("ok",true,"keyId",PackageFiles.keyId(pair.getPublic()),"privateKeyWritten",true,"gameFilesChanged",false)));
        }catch(IOException|GeneralSecurityException bad){Files.deleteIfExists(directory.resolve("private.der"));Files.deleteIfExists(directory.resolve("public.der"));Files.deleteIfExists(directory);throw bad;}
    }
    static void create(Path spec,Path privateFile,Path publicFile,Path output)throws IOException{
        var p=PackageFiles.properties(spec);var allowed=new HashSet<>(List.of("distribution","release","parent","version","minecraft","loaderVersion","minProtocol","maxProtocol"));
        var indices=new TreeSet<Integer>();for(String key:p.stringPropertyNames())if(key.matches("file\\.(0|[1-9][0-9]{0,2})\\.(path|side|source)")){int index=Integer.parseInt(key.split("\\.")[1]);if(index>=MAX_FILES)throw new IOException("FILE_COUNT_LIMIT");indices.add(index);}else if(!allowed.contains(key))throw new IOException("UNKNOWN_SPEC_PROPERTY");
        var sources=new HashMap<String,Path>();var entries=new ArrayList<Entry>();int expected=0;
        for(int i:indices){if(i!=expected++)throw new IOException("FILE_INDICES_MUST_BE_CONTIGUOUS");String prefix="file."+i+".";String path=modPath(required(p,prefix+"path"));Path source=Path.of(required(p,prefix+"source"));if(!source.isAbsolute())source=spec.toAbsolutePath().getParent().resolve(source);source=source.normalize();
            String hash=PackageFiles.digest(source,MAX_FILE);entries.add(new Entry(path,Side.valueOf(required(p,prefix+"side")),Files.size(source),hash));if(sources.put(path,source)!=null)throw new IOException("DUPLICATE_SOURCE_PATH");
        }
        PublicKey pub=PackageFiles.publicKey(publicFile);String parent=p.getProperty("parent","");
        var manifest=new Manifest(UUID.fromString(required(p,"distribution")),UUID.fromString(required(p,"release")),parent.isEmpty()?null:UUID.fromString(parent),required(p,"version"),required(p,"minecraft"),"neoforge",required(p,"loaderVersion"),Integer.parseInt(p.getProperty("minProtocol","1")),Integer.parseInt(p.getProperty("maxProtocol","1")),PackageFiles.keyId(pub),entries);
        PackageArchive.create(output,manifest,sources,PackageFiles.privateKey(privateFile),pub);System.out.println(json(Map.of("ok",true,"release",manifest.release(),"files",entries.size(),"gameFilesChanged",false)));
    }
    private static String required(Properties p,String key)throws IOException{String value=p.getProperty(key);if(value==null||value.isBlank())throw new IOException("MISSING_SPEC_PROPERTY: "+key);return value;}
    static Map<String,Object> summary(PackageArchive.Verified verified,Target target){var result=new LinkedHashMap<String,Object>();result.put("ok",true);result.put("report","signed_package_v37");result.put("distribution",verified.manifest().distribution());result.put("release",verified.manifest().release());result.put("signer",verified.manifest().signer());result.put("packageSha256",verified.archiveSha256());result.put("filesVerified",verified.manifest().files().size());result.put("targetFiles",verified.manifest().forSide(target.side()).size());result.put("side",target.side());result.put("readOnly",true);return result;}
    static Map<String,Object> planJson(PackagePlanner.Plan plan){var result=new LinkedHashMap<String,Object>();result.put("report","package_plan_v37");result.put("ready",plan.ready());result.put("readOnly",true);result.put("writesApplied",0);result.put("writesPlanned",plan.writesPlanned());result.put("distribution",plan.distribution());result.put("fromRelease",plan.fromRelease());result.put("toRelease",plan.toRelease());result.put("side",plan.side());result.put("packageSha256",plan.packageSha256());result.put("profileRoot",plan.profileRoot());
        result.put("changes",plan.changes().stream().map(c->{var row=new LinkedHashMap<String,Object>();row.put("path",c.path());row.put("action",c.action());row.put("reason",c.reason());row.put("expectedBefore",c.expectedBefore());row.put("expectedAfter",c.expectedAfter());row.put("observedBefore",c.observedBefore());row.put("existed",c.existed());return row;}).toList());return result;}
    static String json(Object value){
        if(value==null)return "null";if(value instanceof Boolean||value instanceof Number)return value.toString();
        if(value instanceof Map<?,?> map){var parts=new ArrayList<String>();map.forEach((k,v)->parts.add(json(String.valueOf(k))+":"+json(v)));return "{"+String.join(",",parts)+"}";}
        if(value instanceof Collection<?> list)return "["+String.join(",",list.stream().map(PackageCli::json).toList())+"]";
        var out=new StringBuilder("\"");for(char c:value.toString().toCharArray()){switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");default->{if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}}return out.append('"').toString();
    }
    static void demo(Path directory)throws Exception{
        directory=directory.toAbsolutePath().normalize();PackageFiles.safe(directory);Files.createDirectory(directory);PackageFiles.privateMode(directory,true);
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();UUID distribution=UUID.randomUUID(),release=UUID.randomUUID();Path payload=directory.resolve("demo.jar");Files.writeString(payload,"Diagnostic payload only; not a Minecraft mod",StandardOpenOption.CREATE_NEW);
        var manifest=new Manifest(distribution,release,null,"demo-1","1.21.1","neoforge","21.1.demo",1,1,PackageFiles.keyId(pair.getPublic()),List.of(new Entry("mods/demo.jar",Side.BOTH,Files.size(payload),PackageFiles.digest(payload,MAX_FILE))));Path archive=directory.resolve("demo.zip");PackageArchive.create(archive,manifest,Map.of("mods/demo.jar",payload),pair.getPrivate(),pair.getPublic());
        var target=new Target(distribution,Side.CLIENT,"1.21.1","21.1.demo",1);var verified=PackageArchive.verify(archive,Map.of(PackageFiles.keyId(pair.getPublic()),pair.getPublic()),target);Path profile=directory.resolve("empty-profile");Files.createDirectory(profile);
        System.out.println(json(summary(verified,target)));System.out.println(json(planJson(PackagePlanner.plan(null,verified,target,profile))));
    }
}
