package com.fixmer.genesis.technology.server.firstlaunch;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import com.fixmer.genesis.technology.server.packages.*;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;

/** Local authoring and verification for four signed pilot inventories; never installs into a profile. */
public final class FirstLaunchArtifacts {
    private FirstLaunchArtifacts(){}
    static Map<String,Path> jars(Path mods)throws IOException{
        FirstLaunchFiles.safe(mods);if(!Files.isDirectory(mods))throw new IOException("MOD_DIRECTORY_REQUIRED");var out=new TreeMap<String,Path>();try(var files=Files.list(mods)){for(Path p:files.toList())if(p.getFileName().toString().endsWith(".jar")){if(out.size()>=MAX_FILES)throw new IOException("MOD_LIMIT");out.put("mods/"+p.getFileName(),FirstLaunchFiles.safe(p));}}return out;
    }
    static Manifest manifest(Map<String,Path> files,Side side,UUID distribution,UUID release,UUID parent,String mc,String neo,String signer)throws IOException{
        var entries=new ArrayList<Entry>();for(var e:files.entrySet())entries.add(new Entry(e.getKey(),side,Files.size(e.getValue()),FirstLaunchFiles.hash(e.getValue(),MAX_FILE)));return new Manifest(distribution,release,parent,parent==null?"first58.0":"first58.1",mc,"neoforge",neo,1,1,signer,entries);
    }
    static String inventory(Manifest m){var body=new StringBuilder();m.files().forEach(e->body.append(e.path()).append('=').append(e.sha256()).append('\n'));return FirstLaunchFiles.sha(body.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    public static Map<String,Object> verify(Path archive,Path pub,Target target)throws Exception{
        PublicKey key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(FirstLaunchFiles.read(pub,1024)));var verified=PackageArchive.verify(archive,Map.of(FirstLaunchFiles.sha(key.getEncoded()),key),target);var m=verified.manifest();
        if(m.files().stream().anyMatch(e->e.side()!=target.side()))throw new IOException("FIRST58_SEPARATE_SIDE_ARCHIVE_REQUIRED");
        var out=new LinkedHashMap<String,Object>();out.put("distribution",m.distribution().toString());out.put("release",m.release().toString());out.put("parent",m.parent()==null?"none":m.parent().toString());out.put("archiveSha256",verified.archiveSha256());out.put("inventorySha256",inventory(m));out.put("files",m.files().stream().map(e->Map.of("path",e.path(),"sha256",e.sha256(),"bytes",e.bytes())).toList());out.put("installAuthority",false);return out;
    }
    public static Map<String,Object> author(Path root,List<Path> directories,String mc,String neo)throws Exception{
        root=FirstLaunchFiles.safe(root);Files.createDirectory(root);FirstLaunchFiles.privateMode(root);var key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();FirstLaunchFiles.write(root.resolve("public.der"),key.getPublic().getEncoded(),false);FirstLaunchFiles.write(root.resolve("private.der"),key.getPrivate().getEncoded(),false);
        UUID distribution=UUID.randomUUID(),previous=UUID.randomUUID(),next=UUID.randomUUID();var report=new LinkedHashMap<String,Object>();report.put("distribution",distribution.toString());report.put("previous",previous.toString());report.put("next",next.toString());
        for(int i=0;i<4;i++){
            Side side=i%2==0?Side.SERVER:Side.CLIENT;String name=side.name().toLowerCase(Locale.ROOT)+(i<2?"-0":"-1");var sources=jars(directories.get(i));var manifest=manifest(sources,side,distribution,i<2?previous:next,i<2?null:previous,mc,neo,FirstLaunchFiles.sha(key.getPublic().getEncoded()));Path output=root.resolve(name+".zip");PackageArchive.create(output,manifest,sources,key.getPrivate(),key.getPublic());report.put(name,verify(output,root.resolve("public.der"),new Target(distribution,side,mc,neo,1)));
        }return report;
    }
    static String json(Object value){
        if(value instanceof Map<?,?> map)return "{"+String.join(",",map.entrySet().stream().map(e->json(String.valueOf(e.getKey()))+":"+json(e.getValue())).toList())+"}";
        if(value instanceof Collection<?> list)return "["+String.join(",",list.stream().map(FirstLaunchArtifacts::json).toList())+"]";
        if(value instanceof Boolean||value instanceof Number)return value.toString();
        return "\""+String.valueOf(value).replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
    }
    public static void main(String[] a){try{
        Object result;
        if(a.length==8&&a[0].equals("author"))result=author(Path.of(a[1]),List.of(Path.of(a[2]),Path.of(a[3]),Path.of(a[4]),Path.of(a[5])),a[6],a[7]);
        else if(a.length==8&&a[0].equals("verify"))result=verify(Path.of(a[1]),Path.of(a[2]),new Target(UUID.fromString(a[3]),Side.valueOf(a[4]),a[5],a[6],Integer.parseInt(a[7])));
        else if(a.length==2&&a[0].equals("private")){FirstLaunchFiles.privateMode(FirstLaunchFiles.safe(Path.of(a[1])));result=Map.of("private",true);}
        else throw new IOException("FIRST58_ARGUMENTS");
        System.out.println(json(result));
    }catch(Exception bad){System.err.println("First58 artifact verification failed; no profile was installed.");System.exit(2);}}
}
