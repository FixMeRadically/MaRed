package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.jar.*;

/** Installs one pinned bootstrap option, without editing the BAT that is currently executing. */
public final class ServerAgent106 {
    public static final String OPTION="-javaagent:.genesis/server104/boot106/agent.jar";
    private ServerAgent106() {}
    public static void install(Path server,Path app,byte[] key)throws Exception{install(server,app,key,false);}
    /** Only a stopped, exclusively checked profile may replace an older valid bootstrap. */
    public static void prepareStopped(Path server,Path app,byte[] key)throws Exception{
        try(var guard=ServerOffline107.acquire(server)){install(server,app,key,true);}
    }
    private static void install(Path server,Path app,byte[] key,boolean upgrade)throws Exception{
        Path root=ServerApp104.root(server),args=FirstLaunchFiles.safe(server.resolve("user_jvm_args.txt"));
        byte[] before=FirstLaunchFiles.read(args,1024*1024);String text=new String(before,StandardCharsets.UTF_8);
        long own=text.lines().filter(s->s.replace("\uFEFF","").trim().equals(OPTION)).count();
        if(own==1){
            var pin=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("agent106"),16384));
            if(!pin.keySet().equals(Set.of("schema","profile","sha","coreSha"))||!pin.get("schema").equals("106")||!pin.get("profile").equals(FirstLaunchFiles.safe(server).toString())||!pin.get("sha").equals(FirstLaunchFiles.hash(root.resolve("boot106/agent.jar"),64L*1024*1024))||!pin.get("coreSha").equals(FirstLaunchFiles.hash(root.resolve("boot106/core.jar"),64L*1024*1024)))throw new IOException("SERVER106_AGENT_BINDING");
            if(!upgrade||pin.get("coreSha").equals(FirstLaunchFiles.hash(app.resolve("runtime/core.jar"),64L*1024*1024)))return;
            // The caller holds owner/start/game/world locks and the configured listening port.
        }
        if(own>1)throw new IOException("SERVER106_AGENT_DUPLICATE");
        if(own==0&&text.contains(".genesis/server104/boot106/agent.jar"))throw new IOException("SERVER106_AGENT_OPTION_CHANGED");
        Path boot=FirstLaunchFiles.safe(root.resolve("boot106"));Files.createDirectories(boot);FirstLaunchFiles.privateMode(boot);
        byte[] core=FirstLaunchFiles.read(app.resolve("runtime/core.jar"),64*1024*1024);
        Path coreFile=boot.resolve("core.jar");
        byte[] entry=null;try(var in=new JarInputStream(new ByteArrayInputStream(core))){for(JarEntry e;(e=in.getNextJarEntry())!=null;)if(e.getName().equals("com/fixmer/genesis/bootstrap/agent106/Entry.class")){entry=in.readNBytes(1024*1024+1);break;}}
        if(entry==null||entry.length>1024*1024)throw new IOException("SERVER106_AGENT_ENTRY");
        var manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Premain-Class","com.fixmer.genesis.bootstrap.agent106.Entry");
        var out=new ByteArrayOutputStream();try(var jar=new JarOutputStream(out,manifest)){JarEntry e=new JarEntry("com/fixmer/genesis/bootstrap/agent106/Entry.class");e.setTime(0);jar.putNextEntry(e);jar.write(entry);jar.closeEntry();}
        byte[] agent=out.toByteArray();
        var writes=new LinkedHashMap<Path,byte[]>();writes.put(boot.resolve("agent.jar"),agent);writes.put(coreFile,core);
        writes.put(root.resolve("agent106"),LocalApply67.sign(key,Map.of("schema","106","profile",FirstLaunchFiles.safe(server).toString(),"sha",FirstLaunchFiles.sha(agent),"coreSha",FirstLaunchFiles.sha(core))));
        var originals=new LinkedHashMap<Path,byte[]>();for(Path p:writes.keySet())originals.put(p,Files.exists(p)?FirstLaunchFiles.read(p,64*1024*1024):null);
        var completed=new ArrayList<Path>();
        try {
        for(var item:writes.entrySet()){FirstLaunchFiles.write(item.getKey(),item.getValue(),true);completed.add(item.getKey());}
        if(!Arrays.equals(before,FirstLaunchFiles.read(args,1024*1024)))throw new IOException("SERVER106_ARGS_CHANGED");
        if(own==0){
            Path original=root.resolve("args106-original.txt");if(!Files.exists(original))FirstLaunchFiles.write(original,before,false);
            byte[] after=(text+(text.endsWith("\n")||text.isEmpty()?"":"\n")+OPTION+"\n").getBytes(StandardCharsets.UTF_8);
            if(!Arrays.equals(before,FirstLaunchFiles.read(args,1024*1024)))throw new IOException("SERVER106_ARGS_CHANGED");FirstLaunchFiles.write(args,after,true);
        }
        } catch(Exception failed) {
            Collections.reverse(completed);for(Path p:completed)try{
                if(!Arrays.equals(Files.readAllBytes(p),writes.get(p)))throw new IOException("SERVER107_BOOTSTRAP_CHANGED");
                byte[] original=originals.get(p);if(original==null)Files.delete(p);else FirstLaunchFiles.write(p,original,true);
            }catch(Exception restore){failed.addSuppressed(restore);}
            throw failed;
        }
    }
    public static void removeOwnOption(Path server)throws Exception{
        Path root=ServerApp104.root(server),args=FirstLaunchFiles.safe(server.resolve("user_jvm_args.txt"));byte[] before=FirstLaunchFiles.read(args,1024*1024);
        String text=new String(before,StandardCharsets.UTF_8);StringBuilder result=new StringBuilder();int count=0;
        for(String line:text.split("(?<=\\n)",-1)){if(line.replace("\uFEFF","").trim().equals(OPTION)){count++;continue;}result.append(line);}
        if(text.startsWith("\uFEFF")&&!result.toString().startsWith("\uFEFF"))result.insert(0,"\uFEFF");
        if(count!=1)throw new IOException("SERVER106_AGENT_OPTION_CHANGED");
        if(!Arrays.equals(before,FirstLaunchFiles.read(args,1024*1024)))throw new IOException("SERVER106_ARGS_CHANGED");FirstLaunchFiles.write(args,result.toString().getBytes(StandardCharsets.UTF_8),true);
    }
}
