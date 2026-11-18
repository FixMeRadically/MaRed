package com.fixmer.genesis.technology.server.preparation;

import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Local NEW diagnostic transactions only. No game-folder apply, process, HTTP, or player commands. */
public final class DiagnosticUpdateCli {
    private DiagnosticUpdateCli(){}
    public static void main(String[] args){
        try{
            Map<String,Object> result;
            if(args.length==3&&args[0].equals("execute"))result=DiagnosticServerUpdate.execute(request(Path.of(args[1])),Path.of(args[2]));
            else if(args.length==2&&args[0].equals("demo"))result=demo(Path.of(args[1]));
            else if(args.length==2&&args[0].equals("status"))result=DiagnosticServerUpdate.status(Path.of(args[1]));
            else if(args.length==3&&args[0].equals("recheck"))result=DiagnosticServerUpdate.recheck(Path.of(args[1]),args[2]);
            else if(args.length==4&&args[0].equals("recover")&&args[3].equals("--confirm"))result=DiagnosticServerUpdate.recover(Path.of(args[1]),args[2],true);
            else throw new IOException("Usage: execute REQUEST.properties NEW_DIRECTORY | demo NEW_DIRECTORY | status DIRECTORY | recheck DIRECTORY CYCLE_SHA256 | recover DIRECTORY CYCLE_SHA256 --confirm. Diagnostic only; no existing-game apply.");
            System.out.println(PreparationCli.json(result));
            if("RECOVERY_REQUIRED".equals(result.get("outcome")))System.exit(3);
        }catch(Exception bad){
            // Some diagnostic writes may have happened before an error. Do not claim a no-write failure.
            System.err.println(PreparationCli.json(Map.of("ok",false,"error",String.valueOf(bad.getMessage()),"diagnosticWritesMayHaveOccurred",true,"realGameFilesChanged",false,"installAuthority",false)));
            System.exit(2);
        }
    }
    static DiagnosticServerUpdate.Request request(Path input)throws IOException{
        var p=new Properties(){@Override public synchronized Object put(Object k,Object v){if(containsKey(k))throw new IllegalArgumentException("DUPLICATE_PROPERTY");return super.put(k,v);}};
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(PreparationFiles.read(input,65536))).toString();
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        try(var reader=new StringReader(text)){p.load(reader);}catch(IllegalArgumentException bad){throw new IOException("DIAGNOSTIC_REQUEST_PROPERTIES",bad);}
        if(!p.stringPropertyNames().equals(Set.of("previous","next","publicKey","distribution","minecraft","loader")))throw new IOException("DIAGNOSTIC_REQUEST_EXACT_FIELDS_REQUIRED");
        for(String key:p.stringPropertyNames())if(p.getProperty(key).isBlank()||p.getProperty(key).indexOf('\0')>=0)throw new IOException("DIAGNOSTIC_REQUEST_EMPTY_FIELD");
        Path base=input.toAbsolutePath().normalize().getParent();
        try{return new DiagnosticServerUpdate.Request(path(base,p.getProperty("previous")),path(base,p.getProperty("next")),path(base,p.getProperty("publicKey")),UUID.fromString(p.getProperty("distribution")),p.getProperty("minecraft"),p.getProperty("loader"),p.getProperty("loader"));}
        catch(IllegalArgumentException bad){throw new IOException("DIAGNOSTIC_REQUEST_INVALID",bad);}
    }
    private static Path path(Path base,String value){return base.resolve(Path.of(value)).toAbsolutePath().normalize();}
    static DiagnosticServerUpdate.Request fixture(Path root)throws Exception{
        PreparationFiles.newDirectory(root);Path payload=root.resolve("payload");PreparationFiles.newDirectory(payload);
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();PreparationFiles.write(root.resolve("public.der"),pair.getPublic().getEncoded());
        UUID distribution=UUID.randomUUID(),first=UUID.randomUUID();
        archive(root.resolve("old.zip"),payload,"old",distribution,first,null,Map.of("mods/shared.jar","OLD_SHARED","mods/remove.jar","REMOVE","mods/keep.jar","KEEP","mods/client.jar","CLIENT_OLD"),pair);
        archive(root.resolve("next.zip"),payload,"new",distribution,UUID.randomUUID(),first,Map.of("mods/shared.jar","NEW_SHARED","mods/added.jar","ADDED","mods/keep.jar","KEEP","mods/client.jar","CLIENT_NEW"),pair);
        return new DiagnosticServerUpdate.Request(root.resolve("old.zip"),root.resolve("next.zip"),root.resolve("public.der"),distribution,"1.21.1","21.1.demo","21.1.demo");
    }
    static Map<String,Object> demo(Path root)throws Exception{
        var request=fixture(root.toAbsolutePath().normalize());var report=DiagnosticServerUpdate.execute(request,root.toAbsolutePath().normalize().resolve("cycle"));
        report.put("payloadKind","text_markers_not_minecraft_mods");return report;
    }
    private static void archive(Path zip,Path dir,String version,UUID distribution,UUID release,UUID parent,Map<String,String> data,KeyPair key)throws IOException{
        var entries=new ArrayList<PackageModel.Entry>();var sources=new HashMap<String,Path>();
        for(var e:new TreeMap<>(data).entrySet()){
            Path p=dir.resolve(version+"-"+Path.of(e.getKey()).getFileName());byte[] bytes=e.getValue().getBytes(StandardCharsets.UTF_8);Files.write(p,bytes,StandardOpenOption.CREATE_NEW);
            entries.add(new PackageModel.Entry(e.getKey(),e.getKey().contains("client")?PackageModel.Side.CLIENT:PackageModel.Side.BOTH,bytes.length,PreparationFiles.digest(bytes)));sources.put(e.getKey(),p);
        }
        var manifest=new PackageModel.Manifest(distribution,release,parent,version,"1.21.1","neoforge","21.1.demo",1,1,PreparationFiles.digest(key.getPublic().getEncoded()),entries);
        PackageArchive.create(zip,manifest,sources,key.getPrivate(),key.getPublic());
    }
}
