package com.fixmer.genesis.technology.server.preparation;
import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
/** Explicit local preparation only: no stdin to a game, remote routes, install or launch. */
public final class PreparationCli {
    private PreparationCli(){
    }
    public static void main(String[] args){
        try{
            Map<String,Object> result;
            if(args.length==3&&args[0].equals("prepare"))result=ServerUpdatePreparation.prepare(request(Path.of(args[1])),Path.of(args[2]));
            else if(args.length==2&&args[0].equals("status"))result=ServerUpdatePreparation.status(Path.of(args[1]));
            else if(args.length==3&&args[0].equals("recheck"))result=ServerUpdatePreparation.recheck(Path.of(args[1]),args[2]);
            else if(args.length==3&&args[0].equals("resume"))result=ServerUpdatePreparation.resume(Path.of(args[1]),args[2]);
            else if(args.length==4&&args[0].equals("cancel")&&args[3].equals("--confirm"))result=ServerUpdatePreparation.cancel(Path.of(args[1]),args[2],true);
            else if(args.length==2&&args[0].equals("demo"))result=demo(Path.of(args[1]));
            else throw new IOException("Usage: prepare REQUEST.properties NEW_DIRECTORY | status DIRECTORY | recheck/resume DIRECTORY INTENT_SHA256 | cancel DIRECTORY INTENT_SHA256 --confirm | demo NEW_DIRECTORY. No live apply/stop/install command.");
            System.out.println(json(result));
            if(result.containsKey("conflicts")&&((Number)result.get("conflicts")).longValue()>0)System.exit(3);
        }
        catch(Exception failed){
            System.err.println(json(Map.of("ok",false,"error",String.valueOf(failed.getMessage()),"profileModified",false,"installAuthority",false)));
            System.exit(2);
        }
    }
    static ServerUpdatePreparation.Request request(Path input)throws IOException {
        var p=new Properties(){
            @Override public synchronized Object put(Object key,Object value){
                if(containsKey(key))throw new IllegalArgumentException("DUPLICATE_REQUEST_PROPERTY");
                return super.put(key,value);
            }
        }
        ;
        try(var reader=new StringReader(requestText(input))){
            p.load(reader);
        }
        catch(IllegalArgumentException bad){
            throw new IOException("PREPARATION_REQUEST_PROPERTIES",bad);
        }
        Set<String> allowed=Set.of("profile","cache","next","previous","publicKey","distribution","minecraft","nextLoader","previousLoader");
        for(String key:p.stringPropertyNames())if(!allowed.contains(key))throw new IOException("PREPARATION_UNKNOWN_REQUEST_FIELD_"+key);
        Path base=input.toAbsolutePath().normalize().getParent();
        String previous=p.getProperty("previous","");
        try{
            return new ServerUpdatePreparation.Request(path(base,required(p,"profile")),path(base,required(p,"cache")),path(base,required(p,"next")),previous.isBlank()?null:path(base,previous),path(base,required(p,"publicKey")),UUID.fromString(required(p,"distribution")),required(p,"minecraft"),required(p,"nextLoader"),p.getProperty("previousLoader",""));
        }
        catch(IllegalArgumentException bad){
            throw new IOException("PREPARATION_REQUEST_INVALID",bad);
        }
    }
    private static String requestText(Path input)throws IOException {
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(PreparationFiles.read(input,65536))).toString();
        return text.startsWith("\uFEFF")?text.substring(1):text;
    }
    private static String required(Properties p,String key)throws IOException{
        String value=p.getProperty(key,"");
        if(value.isBlank()||value.indexOf('\0')>=0)throw new IOException("PREPARATION_REQUEST_MISSING_"+key);
        return value;
    }
    private static Path path(Path base,String value){
        Path path=Path.of(value);
        return(path.isAbsolute()?path:base.resolve(path)).toAbsolutePath().normalize();
    }
    static Map<String,Object> demo(Path root)throws Exception {
        root=root.toAbsolutePath().normalize();
        PreparationFiles.newDirectory(root);
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        PreparationFiles.write(root.resolve("public.der"),pair.getPublic().getEncoded());
        UUID distribution=UUID.randomUUID(),first=UUID.randomUUID();
        Path payload=root.resolve("payload");
        PreparationFiles.newDirectory(payload);
        Path profile=root.resolve("profile");
        PreparationFiles.newDirectory(profile);
        PreparationFiles.newDirectory(profile.resolve("mods"));
        Files.writeString(profile.resolve("world-marker"),"Never read or changed by preparation52");
        Map<String,String> old=Map.of("mods/shared.jar","OLD_SHARED","mods/remove.jar","OLD_REMOVE","mods/client.jar","CLIENT_ONLY");
        Map<String,String> next=Map.of("mods/shared.jar","NEW_SHARED","mods/added.jar","NEW_ADDED","mods/client.jar","NEW_CLIENT_ONLY");
        Path a=archive(root.resolve("old.zip"),payload,"old",distribution,first,null,old,pair),b=archive(root.resolve("next.zip"),payload,"next",distribution,UUID.randomUUID(),first,next,pair);
        for(var e:old.entrySet())if(!e.getKey().contains("client"))Files.writeString(profile.resolve(e.getKey()),e.getValue());
        Files.writeString(profile.resolve("mods/foreign.jar"),"UNMANAGED_PRESERVED");
        var result=ServerUpdatePreparation.prepare(new ServerUpdatePreparation.Request(profile,root.resolve("cache"),b,a,root.resolve("public.der"),distribution,"1.21.1","21.1.demo","21.1.demo"),root.resolve("prepared"));
        result.put("diagnosticOnly",true);
        result.put("payloadKind","text_markers_not_minecraft_mods");
        return result;
    }
    private static Path archive(Path zip,Path payload,String prefix,UUID distribution,UUID release,UUID parent,Map<String,String> files,KeyPair key)throws IOException {
        var entries=new ArrayList<PackageModel.Entry>();
        var sources=new HashMap<String,Path>();
        for(var e:new TreeMap<>(files).entrySet()){
            Path source=payload.resolve(prefix+"-"+Path.of(e.getKey()).getFileName());
            byte[] data=e.getValue().getBytes(StandardCharsets.UTF_8);
            Files.write(source,data,StandardOpenOption.CREATE_NEW);
            entries.add(new PackageModel.Entry(e.getKey(),e.getKey().contains("client")?PackageModel.Side.CLIENT:PackageModel.Side.BOTH,data.length,PreparationFiles.digest(data)));
            sources.put(e.getKey(),source);
        }
        var manifest=new PackageModel.Manifest(distribution,release,parent,prefix,"1.21.1","neoforge","21.1.demo",1,1,PreparationFiles.digest(key.getPublic().getEncoded()),entries);
        PackageArchive.create(zip,manifest,sources,key.getPrivate(),key.getPublic());
        return zip;
    }
    static String json(Object value){
        if(value==null)return "null";
        if(value instanceof Boolean||value instanceof Number)return value.toString();
        if(value instanceof Map<?,?> map){
            var parts=new ArrayList<String>();
            map.forEach((k,v)->parts.add(json(k.toString())+":"+json(v)));
            return "{"+String.join(",",parts)+"}";
        }
        if(value instanceof Collection<?> list)return "["+String.join(",",list.stream().map(PreparationCli::json).toList())+"]";
        var out=new StringBuilder("\"");
        for(char c:value.toString().toCharArray())switch(c){
            case '"'->out.append("\\\"");
            case '\\'->out.append("\\\\");
            default->{
                if(c<32)out.append(String.format("\\u%04x",(int)c));
                else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
