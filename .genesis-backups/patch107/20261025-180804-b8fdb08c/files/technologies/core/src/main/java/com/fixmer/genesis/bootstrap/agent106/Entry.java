package com.fixmer.genesis.bootstrap.agent106;

import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** Thin premain only. Controller classes load in an isolated loader, never Minecraft's parent loader. */
public final class Entry {
    private Entry() {}
    public static boolean applies(String command,Map<String,String> env,boolean disabled){return !disabled&&!env.containsKey("GENESIS_INPLACE_VERSION")&&command.matches("(?s).*--launchTarget\\s+neoforgeserver(?:\\s.*|$)");}
    public static void premain(String ignored,Instrumentation instrumentation){
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))return;
        if(!applies(System.getProperty("sun.java.command",""),System.getenv(),Boolean.getBoolean("genesis.server.auto.disabled")))return;
        Path server=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();int result;
        try{
            Path self=Path.of(Entry.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();Path boot=server.resolve(".genesis/server104/boot106");
            if(!self.equals(boot.resolve("agent.jar"))||!self.toRealPath().equals(self)||!boot.resolve("core.jar").toRealPath().equals(boot.resolve("core.jar")))throw new IOException("SERVER106_AGENT_LOCATION");
            try(var isolated=new URLClassLoader(new URL[]{boot.resolve("core.jar").toUri().toURL()},ClassLoader.getPlatformClassLoader())){
                Class<?> type=Class.forName("com.fixmer.genesis.technology.server.remote.ServerBoot106",true,isolated);
                result=((Number)type.getMethod("run",Path.class).invoke(null,server)).intValue();
                if(result==-1)return;
            }
        }catch(Throwable failed){
            if(failed instanceof java.lang.reflect.InvocationTargetException i&&i.getCause()!=null)failed=i.getCause();
            try{failed.printStackTrace(System.err);}catch(Exception ignoredLog){}
            try{Path logs=server.resolve(".genesis/server104/logs");Files.createDirectories(logs);var text=new StringWriter();failed.printStackTrace(new PrintWriter(text));byte[] data=text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(logs.resolve("premain106-error.log"),Arrays.copyOf(data,Math.min(data.length,32768)));}catch(Exception ignoredLog){}
            result=2;
        }
        // No Minecraft main has run in this launcher JVM. Never fall through after taking ownership.
        System.exit(result);
    }
}
