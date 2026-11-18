package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Per-run launch authorization. Local owner files are not a remote authentication protocol. */
final class ServerLaunchHandoff {
    static final long TIMEOUT_MS=15_000;
    private static final Set<String> KEYS=Set.of("schema","run","profile","epoch","parent.pid","parent.start","ticket","config.sha256");
    private final Properties ticket;
    private String approvedGuardianStart;
    private ServerLaunchHandoff(Properties ticket){this.ticket=copy(ticket);}
    static ServerLaunchHandoff create(Path run,UUID profile,long epoch,long parent,String start)throws IOException {
        if(epoch<1||parent<1||start.isBlank())throw new IOException("HANDOFF_INVALID_OWNER");
        Properties p=new Properties();p.setProperty("schema","40");p.setProperty("run",run.getFileName().toString());p.setProperty("profile",profile.toString());p.setProperty("epoch",Long.toString(epoch));p.setProperty("parent.pid",Long.toString(parent));p.setProperty("parent.start",start);p.setProperty("ticket",UUID.randomUUID().toString());p.setProperty("config.sha256",digest(run.resolve("launch.properties")));
        ServerLaunchHandoff result=new ServerLaunchHandoff(p);result.validate(run,parent,start);
        Path path=run.resolve("handoff.ticket");if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("HANDOFF_ALREADY_EXISTS");ServerFiles.write(path,p);return result;
    }
    static ServerLaunchHandoff load(Path run,long parent,String start)throws IOException {
        ServerLaunchHandoff result=new ServerLaunchHandoff(ServerFiles.read(run.resolve("handoff.ticket")));result.validate(run,parent,start);return result;
    }
    private void validate(Path run,long parent,String start)throws IOException {
        if(!ticket.stringPropertyNames().equals(KEYS)||!ticket.getProperty("schema","").equals("40")||!ticket.getProperty("run","").equals(run.getFileName().toString())||ServerFiles.number(ticket,"epoch",0)<1||ServerFiles.number(ticket,"parent.pid",0)!=parent||!ticket.getProperty("parent.start","").equals(start)||start.isBlank())throw new IOException("HANDOFF_INVALID_TICKET");
        for(String key:List.of("run","profile","ticket"))if(!ServerFiles.canonicalUuid(ticket.getProperty(key,"")))throw new IOException("HANDOFF_INVALID_ID");
        if(!ticket.getProperty("config.sha256","").equals(digest(run.resolve("launch.properties"))))throw new IOException("HANDOFF_CONFIG_CHANGED");
    }
    void unchanged(Path run)throws IOException {
        if(!ticket.equals(ServerFiles.read(run.resolve("handoff.ticket"))))throw new IOException("HANDOFF_TICKET_CHANGED");
        validate(run,ServerFiles.number(ticket,"parent.pid",0),ticket.getProperty("parent.start"));
    }
    Properties ready(long guardian,String start){Properties p=copy(ticket);p.setProperty("guardian.pid",Long.toString(guardian));p.setProperty("guardian.start",start);p.setProperty("challenge",UUID.randomUUID().toString());return p;}
    void validateReady(Properties ready,long guardian)throws IOException {
        Properties base=copy(ready);base.remove("guardian.pid");base.remove("guardian.start");base.remove("challenge");
        if(!base.equals(ticket)||ready.size()!=KEYS.size()+3||ServerFiles.number(ready,"guardian.pid",0)!=guardian||!ServerFiles.canonicalUuid(ready.getProperty("challenge",""))||ready.getProperty("guardian.start","").isBlank())throw new IOException("HANDOFF_INVALID_READY");
        if(!ServerFiles.sameProcess(guardian,ready.getProperty("guardian.start")))throw new IOException("HANDOFF_GUARDIAN_NOT_ALIVE");
    }
    void approve(Path run,Properties ready,long guardian)throws IOException {
        unchanged(run);validateReady(ready,guardian);
        if(!ServerFiles.sameProcess(ServerFiles.number(ticket,"parent.pid",0),ticket.getProperty("parent.start")))throw new IOException("HANDOFF_PARENT_NOT_ALIVE");
        ServerFiles.write(run.resolve("handoff.approval"),ready);approvedGuardianStart=ready.getProperty("guardian.start");
    }
    boolean approved(Path run,Properties ready)throws IOException {
        unchanged(run);Path path=run.resolve("handoff.approval");if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return false;
        if(!ready.equals(ServerFiles.read(path)))throw new IOException("HANDOFF_APPROVAL_MISMATCH");return true;
    }
    Properties approvedNativeContext(Path run,long guardian,String start)throws IOException {
        unchanged(run);Properties ready=ServerFiles.read(run.resolve("handoff.ready"));validateReady(ready,guardian);
        if(!ready.getProperty("guardian.start").equals(start)||!approved(run,ready)||!ServerFiles.sameProcess(ServerFiles.number(ticket,"parent.pid",0),ticket.getProperty("parent.start")))throw new IOException("NATIVE_HANDOFF_NOT_APPROVED");
        for(String file:List.of("stop.request","force.request","native.cancel"))if(Files.exists(run.resolve(file),LinkOption.NOFOLLOW_LINKS))throw new IOException("NATIVE_HANDOFF_CANCELLED");
        Properties status=ServerFiles.read(run.resolve("status.properties"));validateStatus(status,guardian);
        if(!status.getProperty("guardian.start","").equals(start)||!status.getProperty("phase","").equals("AWAITING_APPROVAL")||!status.getProperty("launchIntent","").equals("false")||!status.getProperty("child.pid","").equals("0"))throw new IOException("NATIVE_HANDOFF_ALREADY_LAUNCHED");
        return copy(ready);
    }
    void stamp(Properties status){status.setProperty("handoff.version","40");for(String key:List.of("run","profile","epoch","ticket"))status.setProperty("handoff."+key,ticket.getProperty(key));}
    void validateStatus(Properties status,long guardian)throws IOException {
        Properties expected=new Properties();stamp(expected);for(String key:expected.stringPropertyNames())if(!expected.getProperty(key).equals(status.getProperty(key)))throw new IOException("HANDOFF_STATUS_MISMATCH");
        if(ServerFiles.number(status,"guardian.pid",0)!=guardian||approvedGuardianStart!=null&&!approvedGuardianStart.equals(status.getProperty("guardian.start")))throw new IOException("HANDOFF_STATUS_GUARDIAN_MISMATCH");
    }
    private static Properties copy(Properties p){Properties result=new Properties();result.putAll(p);return result;}
    private static String digest(Path path)throws IOException {
        ServerFiles.safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>ServerFiles.MAX_PROPERTIES)throw new IOException("HANDOFF_CONFIG_LIMIT");
        byte[] bytes;try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(ServerFiles.MAX_PROPERTIES+1);}if(bytes.length>ServerFiles.MAX_PROPERTIES)throw new IOException("HANDOFF_CONFIG_LIMIT");
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
