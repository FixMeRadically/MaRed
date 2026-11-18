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
    public static void install(Path server,Path app,byte[] key)throws Exception{
        Path root=ServerApp104.root(server),args=FirstLaunchFiles.safe(server.resolve("user_jvm_args.txt"));
        byte[] before=FirstLaunchFiles.read(args,1024*1024);String text=new String(before,StandardCharsets.UTF_8);
        long own=text.lines().filter(s->s.replace("\uFEFF","").trim().equals(OPTION)).count();
        if(own==1){
            var pin=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("agent106"),16384));
            if(!pin.keySet().equals(Set.of("schema","profile","sha","coreSha"))||!pin.get("schema").equals("106")||!pin.get("profile").equals(FirstLaunchFiles.safe(server).toString())||!pin.get("sha").equals(FirstLaunchFiles.hash(root.resolve("boot106/agent.jar"),64L*1024*1024))||!pin.get("coreSha").equals(FirstLaunchFiles.hash(root.resolve("boot106/core.jar"),64L*1024*1024)))throw new IOException("SERVER106_AGENT_BINDING");
            return; // Loaded agent/core files can be held open on Windows; never replace a valid live bootstrap.
        }
        if(own>1)throw new IOException("SERVER106_AGENT_DUPLICATE");
        if(own==0&&text.contains(".genesis/server104/boot106/agent.jar"))throw new IOException("SERVER106_AGENT_OPTION_CHANGED");
        Path boot=FirstLaunchFiles.safe(root.resolve("boot106"));Files.createDirectories(boot);FirstLaunchFiles.privateMode(boot);
        byte[] core=FirstLaunchFiles.read(app.resolve("runtime/core.jar"),64*1024*1024);
        Path coreFile=boot.resolve("core.jar");FirstLaunchFiles.write(coreFile,core,true);
        byte[] entry=null;try(var in=new JarInputStream(new ByteArrayInputStream(core))){for(JarEntry e;(e=in.getNextJarEntry())!=null;)if(e.getName().equals("com/fixmer/genesis/bootstrap/agent106/Entry.class")){entry=in.readNBytes(1024*1024+1);break;}}
        if(entry==null||entry.length>1024*1024)throw new IOException("SERVER106_AGENT_ENTRY");
        var manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Premain-Class","com.fixmer.genesis.bootstrap.agent106.Entry");
        var out=new ByteArrayOutputStream();try(var jar=new JarOutputStream(out,manifest)){JarEntry e=new JarEntry("com/fixmer/genesis/bootstrap/agent106/Entry.class");e.setTime(0);jar.putNextEntry(e);jar.write(entry);jar.closeEntry();}
        byte[] agent=out.toByteArray();FirstLaunchFiles.write(boot.resolve("agent.jar"),agent,true);
        FirstLaunchFiles.write(root.resolve("agent106"),LocalApply67.sign(key,Map.of("schema","106","profile",FirstLaunchFiles.safe(server).toString(),"sha",FirstLaunchFiles.sha(agent),"coreSha",FirstLaunchFiles.sha(core))),true);
        if(own==0){
            Path original=root.resolve("args106-original.txt");if(!Files.exists(original))FirstLaunchFiles.write(original,before,false);
            byte[] after=(text+(text.endsWith("\n")||text.isEmpty()?"":"\n")+OPTION+"\n").getBytes(StandardCharsets.UTF_8);
            if(!Arrays.equals(before,FirstLaunchFiles.read(args,1024*1024)))throw new IOException("SERVER106_ARGS_CHANGED");FirstLaunchFiles.write(args,after,true);
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
