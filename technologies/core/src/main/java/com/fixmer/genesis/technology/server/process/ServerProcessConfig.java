package com.fixmer.genesis.technology.server.process;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** UTF-8 properties. Numbered argument boundaries are preserved; no shell is invoked. */
public record ServerProcessConfig(Path file,String name,Path workDir,Path stateDir,Path javaHome,
                                 List<String> arguments,int port,long graceMs,long forceGraceMs,
                                 boolean startOnBoot,boolean restartOnCrash,int maxRestarts,long restartDelayMs,
                                 boolean orphanForce,long maxLogBytes) {
    public ServerProcessConfig {arguments=List.copyOf(arguments);}
    public Path javaExecutable(){return javaHome.resolve("bin").resolve(System.getProperty("os.name","").startsWith("Windows")?"java.exe":"java");}
    public static ServerProcessConfig load(Path file)throws IOException {
        file=file.toAbsolutePath().normalize();Properties p=ServerFiles.read(file);Path base=file.getParent();
        Set<String> allowed=Set.of("name","workDir","stateDir","javaHome","control.port","stop.graceMs","stop.forceGraceMs","start.onBoot","restart.onCrash","restart.maxAttempts","restart.delayMs","orphan.force","log.maxBytes");
        var args=new TreeMap<Integer,String>();
        for(String key:p.stringPropertyNames()){
            if(key.matches("arg\\.(0|[1-9][0-9]{0,2})")){int index=Integer.parseInt(key.substring(4));String value=p.getProperty(key);if(index>=128||value.isEmpty()||value.length()>4096||value.indexOf('\0')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0)throw new IOException("Invalid launch argument: "+key);args.put(index,value);}
            else if(!allowed.contains(key))throw new IOException("Unknown configuration property: "+key);
        }
        if(args.isEmpty())throw new IOException("No numbered server arguments configured");
        int expected=0,total=0;for(var item:args.entrySet()){if(item.getKey()!=expected++)throw new IOException("Argument numbers must be contiguous from arg.0");total+=item.getValue().length()+3;}if(total>24000)throw new IOException("Launch command is too long");
        String name=p.getProperty("name","Genesis server");if(name.isBlank()||name.length()>80||name.chars().anyMatch(c->c<32))throw new IOException("Invalid server display name");
        Path work=resolve(base,required(p,"workDir"));Path state=resolve(base,p.getProperty("stateDir",".genesis-server"));Path home=resolve(base,required(p,"javaHome"));
        if(work.equals(state)||work.startsWith(state)||home.startsWith(state))throw new IOException("State directory must be separate from server/JDK roots");
        return new ServerProcessConfig(file,name,work,state,home,new ArrayList<>(args.values()),(int)range(p,"control.port",0,0,65535),range(p,"stop.graceMs",60000,100,300000),range(p,"stop.forceGraceMs",3000,100,30000),bool(p,"start.onBoot",true),bool(p,"restart.onCrash",false),(int)range(p,"restart.maxAttempts",3,0,20),range(p,"restart.delayMs",1000,100,60000),bool(p,"orphan.force",false),range(p,"log.maxBytes",16*1024*1024,1024,256L*1024*1024));
    }
    Properties snapshot(){
        var p=new Properties();p.setProperty("name",name);p.setProperty("workDir",workDir.toString());p.setProperty("stateDir",stateDir.toString());p.setProperty("javaHome",javaHome.toString());
        p.setProperty("control.port",Integer.toString(port));p.setProperty("stop.graceMs",Long.toString(graceMs));p.setProperty("stop.forceGraceMs",Long.toString(forceGraceMs));p.setProperty("start.onBoot",Boolean.toString(startOnBoot));p.setProperty("restart.onCrash",Boolean.toString(restartOnCrash));p.setProperty("restart.maxAttempts",Integer.toString(maxRestarts));p.setProperty("restart.delayMs",Long.toString(restartDelayMs));p.setProperty("orphan.force",Boolean.toString(orphanForce));p.setProperty("log.maxBytes",Long.toString(maxLogBytes));
        for(int i=0;i<arguments.size();i++)p.setProperty("arg."+i,arguments.get(i));return p;
    }
    public void validateLaunch()throws IOException {
        if(!Files.isDirectory(workDir)||!Files.isRegularFile(javaExecutable()))throw new IOException("Server directory or Java executable is missing");
        for(String arg:arguments)if(arg.startsWith("@")&&!arg.startsWith("@@")){Path path=resolve(workDir,arg.substring(1));if(!Files.isRegularFile(path))throw new IOException("Java argument file is missing: "+path.getFileName());}
    }
    static Path resolve(Path base,String raw)throws IOException {try{Path p=Path.of(raw);return (p.isAbsolute()?p:base.resolve(p)).toAbsolutePath().normalize();}catch(InvalidPathException bad){throw new IOException("Invalid configuration path",bad);}}
    private static String required(Properties p,String key)throws IOException {String value=p.getProperty(key,"");if(value.isBlank())throw new IOException("Missing configuration property: "+key);return value;}
    private static boolean bool(Properties p,String key,boolean fallback)throws IOException {String value=p.getProperty(key,Boolean.toString(fallback));if(!value.equals("true")&&!value.equals("false"))throw new IOException("Expected true/false: "+key);return Boolean.parseBoolean(value);}
    private static long range(Properties p,String key,long fallback,long min,long max)throws IOException {long value=ServerFiles.number(p,key,fallback);if(value<min||value>max)throw new IOException("Configuration value out of range: "+key);return value;}
}
