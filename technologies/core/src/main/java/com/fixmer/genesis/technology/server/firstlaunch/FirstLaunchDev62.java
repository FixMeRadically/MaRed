package com.fixmer.genesis.technology.server.firstlaunch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Immutable local dev output snapshot; jar inventory verification remains separate. */
public final class FirstLaunchDev62 {
    private FirstLaunchDev62() {}
    public static String hash(Path root) throws IOException {
        root=FirstLaunchFiles.safe(root); if(!Files.isDirectory(root))throw new IOException("DEV62_DIRECTORY");
        var entries=new TreeMap<String,String>();long bytes=0;
        try(var walk=Files.walk(root)) {
            var iterator=walk.iterator();int nodes=0;
            while(iterator.hasNext()) {
                if(++nodes>120000)throw new IOException("DEV62_NODE_LIMIT");
                Path path=iterator.next();
                FirstLaunchFiles.safe(path);
                if(Files.isDirectory(path))continue;
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("DEV62_FILE");
                String name=root.relativize(path).toString().replace('\\','/');
                if(root.relativize(path).getNameCount()>33||name.contains("\n")||name.contains("\r")||name.contains("="))throw new IOException("DEV62_PATH");
                bytes+=Files.size(path);
                if(entries.size()>=30000||bytes>256L*1024*1024)throw new IOException("DEV62_LIMIT");
                entries.put(name,FirstLaunchFiles.hash(path,64L*1024*1024));
            }
        }
        var body=new StringBuilder();entries.forEach((k,v)->body.append(k).append('=').append(v).append('\n'));
        return FirstLaunchFiles.sha(body.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static void verify(Path profile,Collection<Path> loaded,String expected) throws IOException {
        Path root=FirstLaunchFiles.safe(profile.resolve(".genesis-dev62"));
        if(!expected.matches("[a-f0-9]{64}")||!hash(root).equals(expected))throw new IOException("DEV62_SNAPSHOT_CHANGED");
        String text=new String(FirstLaunchFiles.read(root.resolve("roots.v62"),8192),StandardCharsets.US_ASCII);
        int count=0;Set<String> groups=new HashSet<>();
        for(String line:text.split("\n")) {
            String[] pair=line.split("=",-1);
            if(pair.length!=2||!pair[0].matches("[A-Za-z][A-Za-z0-9_.-]{0,60}")||!pair[1].equals("mods/"+pair[0])||!groups.add(pair[0])||++count>16)throw new IOException("DEV62_ROOTS");
            Path directory=FirstLaunchFiles.safe(root.resolve(pair[1]));boolean found=false;
            for(Path path:loaded)try{if(Files.isSameFile(directory,path)){found=true;break;}}catch(IOException|RuntimeException ignored){}
            if(!found)throw new IOException("DEV62_SNAPSHOT_NOT_LOADED");
        }
        if(count==0)throw new IOException("DEV62_ROOTS_EMPTY");
    }
    public static void serverMode(String mode,boolean authenticated,String address) throws IOException {
        if(mode.equals("ONLINE")){if(!authenticated)throw new IOException("FIRST62_ONLINE_REQUIRED");}
        else if(mode.equals("LOCAL_OFFLINE")){
            if(authenticated||!"127.0.0.1".equals(address))throw new IOException("FIRST62_LOCAL_OFFLINE_BINDING_REQUIRED");
        } else throw new IOException("FIRST62_UNKNOWN_AUTH_MODE");
    }
}
