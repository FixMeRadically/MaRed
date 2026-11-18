package com.fixmer.genesis.technology.server.waiting;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import com.fixmer.genesis.technology.server.players.*;
import static com.fixmer.genesis.technology.server.players.PlayerUpdateProtocol.*;

/** Separate JVM entry point. No inherited Minecraft classpath, world directory or install action. */
public final class WaitingCli {
    private WaitingCli() {}
    public static void main(String[] args){try{run(args);}catch(Exception failed){System.err.println("Waiting service request failed; inspect status before retrying. No automatic replay.");System.exit(2);}}
    static void run(String[] a)throws Exception{
        if(a.length<2)throw new IOException("Usage: init/serve/status/action/issue/revoke/stop ROOT; player-status/decision CREDENTIAL");
        String action=a[0];Path path=WaitingFiles.safe(Path.of(a[1]));
        switch(action){
            case "init-prepared"->{
                if(a.length!=6)throw new IOException("init-prepared NEW_ROOT PREPARATION INTENT_SHA256 BARRIER_MILLIS PLAYER_UUID_CSV");
                WaitingPreparation.init(path,Path.of(a[2]),a[3],WaitingFiles.number(a[4]),a[5]);
            }
            case "init"->{
                if(a.length!=6)throw new IOException("init NEW_ROOT UPDATE SHA BARRIER_MILLIS UUID_CSV_OR_DASH");
                UUID update=WaitingFiles.id(a[2]);String sha=a[3];long wait=WaitingFiles.number(a[4]);var offers=new ArrayList<Offer>();
                if(!a[5].equals("-"))for(String id:a[5].split(",",-1))offers.add(new Offer(WaitingFiles.id(id),UUID.randomUUID(),UUID.randomUUID()));
                State state=start(update,sha,Policy.defaults(wait),0,offers);WaitingFiles.directory(path);
                try(var journal=PlayerUpdateJournal.create(path.resolve("state"),state)){
                    WaitingFiles.privateMode(path.resolve("state"),true);for(Path p:List.of(path.resolve("state/players.v55"),path.resolve("state/players.lock")))WaitingFiles.privateMode(p,false);
                    WaitingFiles.write(path.resolve("waiting.v56"),Map.of("schema","56","update",update.toString(),"packageSha256",sha),false);
                }
                System.out.println("Created NEW local consent sandbox. Package and Minecraft identities are not verified.");
            }
            case "serve"->{
                if(a.length!=2)throw new IOException("Unexpected arguments");
                try(var service=WaitingServer.start(path)){
                    var hook=new Thread(()->{try{service.close();}catch(IOException ignored){}});Runtime.getRuntime().addShutdownHook(hook);
                    System.out.println("Waiting service56 listening on 127.0.0.1:"+service.port()+"; owner review required; no install authority.");
                    service.await();try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException stopping){}
                }
            }
            case "status"->{if(a.length!=2)throw new IOException("Unexpected arguments");print(controller(path,"status",null));}
            case "action"->{
                if(a.length!=4||!a[3].equals("--confirm")||!Set.of("resume","freeze","cancel").contains(a[2]))throw new IOException("Explicit action confirmation required");
                var status=controller(path,"status",null);print(controller(path,"action",Map.of("request",UUID.randomUUID().toString(),"revision",status.get("revision"),"action",a[2])));
            }
            case "issue"->{
                if(a.length!=4)throw new IOException("issue ROOT PLAYER NEW_CREDENTIAL_FILE");Path output=WaitingFiles.safe(Path.of(a[3]));
                if(output.startsWith(path)||path.startsWith(output))throw new IOException("Credential output must be outside service root");
                if(Files.exists(output,LinkOption.NOFOLLOW_LINKS)||!Files.isDirectory(output.getParent(),LinkOption.NOFOLLOW_LINKS))throw new IOException("New credential file and existing parent required");
                var status=controller(path,"status",null);var issued=controller(path,"issue",Map.of("request",UUID.randomUUID().toString(),"revision",status.get("revision"),"player",WaitingFiles.id(a[2]).toString()));
                var result=new TreeMap<>(issued);result.put("port",WaitingFiles.read(path.resolve("endpoint.v56")).get("port"));WaitingFiles.write(output,result,false);
                System.out.println("Credential saved privately. Previous player grant revoked. Token not printed.");
            }
            case "revoke"->{if(a.length!=3)throw new IOException("revoke ROOT PLAYER");print(controller(path,"revoke",Map.of("player",WaitingFiles.id(a[2]).toString())));}
            case "stop"->{if(a.length!=3||!a[2].equals("--confirm"))throw new IOException("Explicit stop confirmation required");print(controller(path,"stop",Map.of("confirm","true")));}
            case "player-status"->{if(a.length!=2)throw new IOException("Unexpected arguments");print(player(path,"status",null));}
            case "decision"->{
                if(a.length!=4||!Set.of("accept","decline").contains(a[2]))throw new IOException("decision CREDENTIAL accept_or_decline NEW_INTENT_FILE");
                var credential=WaitingFiles.read(path);Path intent=WaitingFiles.safe(Path.of(a[3]));if(intent.equals(path))throw new IOException("Intent cannot replace credential");
                var status=player(path,"status",null);var values=new TreeMap<String,String>();
                for(String key:List.of("instance","player","connection","offer"))values.put(key,credential.get(key));
                values.put("request",UUID.randomUUID().toString());values.put("revision",status.get("revision"));values.put("decision",a[2]);WaitingFiles.write(intent,values,false);
                print(player(path,"decision",decisionBody(values)));
            }
            case "retry-decision"->{
                if(a.length!=3)throw new IOException("retry-decision CREDENTIAL INTENT");var credential=WaitingFiles.read(path);var intent=WaitingFiles.read(Path.of(a[2]));WaitingFiles.keys(intent,"instance","player","connection","offer","request","revision","decision");
                for(String key:List.of("instance","player","connection","offer"))if(!Objects.equals(credential.get(key),intent.get(key)))throw new IOException("Intent belongs to another grant");
                print(player(path,"decision",decisionBody(intent)));
            }
            default->throw new IOException("Unsupported command");
        }
    }
    private static Map<String,String> decisionBody(Map<String,String> values){return Map.of("request",values.get("request"),"revision",values.get("revision"),"decision",values.get("decision"));}
    private static Map<String,String> controller(Path root,String action,Map<String,String> body)throws Exception{
        var endpoint=WaitingFiles.read(root.resolve("endpoint.v56"));var credential=WaitingFiles.read(root.resolve("controller.key"));
        if(!Objects.equals(endpoint.get("instance"),credential.get("instance")))throw new IOException("Owner changed");return request(endpoint,credential.get("token"),"/v1/controller/"+action,body);
    }
    private static Map<String,String> player(Path credential,String action,Map<String,String> body)throws Exception{
        var values=WaitingFiles.read(credential);return request(values,values.get("token"),"/v1/player/"+action,body);
    }
    static Map<String,String> request(Map<String,String> endpoint,String token,String path,Map<String,String> body)throws Exception{return WaitingClient.request(endpoint,token,path,body);}
    private static void print(Map<String,String> values)throws IOException{
        if(values.containsKey("token"))throw new IOException("Refusing to print credential");System.out.write(WaitingFiles.encode(values));
        if(values.getOrDefault("outcome","APPLIED").equals("REJECTED"))throw new IOException("Command rejected");
    }
}
