package com.fixmer.genesis.technology.server.process;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Operator entry point, independent of the Minecraft client and its window supervisor. */
public final class ServerSupervisorCli {
    private ServerSupervisorCli(){}
    public static void main(String[] args){
        if(args.length==5&&args[0].equals("prepare-restart-for")&&args[4].equals("--confirm")){
            try{
                if(!ServerFiles.canonicalUuid(args[2])||!ServerFiles.canonicalUuid(args[3]))throw new IOException("EVIDENCE_CANONICAL_RUN_AND_OWNER_REQUIRED");
                System.out.println(request(ServerProcessConfig.load(Path.of(args[1])),"prepare-restart",true,args[2],args[3]));
            }catch(Exception failed){System.err.println("Server supervisor: "+failed.getMessage());System.exit(2);}return;
        }
        if(args.length<2||args.length>3){usage();System.exit(2);return;}
        try{
            String action=args[0];Path input=Path.of(args[1]);
            if(action.equals("demo")){Path config=createDemo(input);System.out.println("Demo config: "+config);run(ServerProcessConfig.load(config));return;}
            var config=ServerProcessConfig.load(input);
            if(action.equals("run")){run(config);return;}
            boolean confirmed=args.length==3&&args[2].equals("--confirm");
            if(Set.of("force-stop","resolve","prepare-stop","prepare-restart").contains(action)&&!confirmed)throw new IOException("This action requires --confirm after reviewing the running processes and possible unsaved data");
            if(action.equals("token")){var endpoint=ServerFiles.read(config.stateDir().resolve("control.properties"));System.out.println(endpoint.getProperty("token",""));return;}
            if(action.equals("status")&&!online(config)){offline(config);return;}
            if(!Set.of("status","logs","start","stop","restart","shutdown","force-stop","resolve","checkpoint","prepare-stop","prepare-restart","cancel-prepare").contains(action)){usage();System.exit(2);return;}
            String result=request(config,action,confirmed);System.out.println(result);
        }catch(Exception bad){System.err.println("Server supervisor: "+bad.getMessage());System.exit(2);}
    }
    private static void usage(){System.err.println("ServerSupervisorCli demo <NEW-directory> | run/status/logs/token/start/stop/restart/shutdown/checkpoint <config> | force-stop/resolve/prepare-stop/prepare-restart <config> --confirm");}
    private static void run(ServerProcessConfig config)throws Exception {
        try(var supervisor=new ServerProcessSupervisor(config)){
            Thread hook=new Thread(()->{try{supervisor.close();}catch(IOException ignored){}},"genesis-server-supervisor-shutdown");Runtime.getRuntime().addShutdownHook(hook);
            System.out.println("Genesis server supervisor v51");System.out.println("Local console: http://127.0.0.1:"+supervisor.port()+"/");
            System.out.println("Control key is private. Read with: ServerSupervisorCli token <config>");System.out.println("Config: "+config.file());
            System.out.println("Lifecycle readiness requires a fresh bound game report; checkpoints are not world-quiescence or installation proofs.");
            try{supervisor.run();}finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){}}
        }
    }
    static Path createDemo(Path root)throws IOException {
        root=root.toAbsolutePath().normalize();ServerFiles.safe(root);Files.createDirectory(root);ServerFiles.privateMode(root,true);
        var p=new Properties();p.setProperty("name","Genesis demo · без Minecraft");p.setProperty("workDir",root.toString());p.setProperty("stateDir",root.resolve("state").toString());p.setProperty("javaHome",System.getProperty("java.home"));
        p.setProperty("arg.0","-cp");p.setProperty("arg.1",ServerProcessSupervisor.absoluteClasspath());p.setProperty("arg.2",ServerProcessDemo.class.getName());p.setProperty("stop.graceMs","2000");p.setProperty("orphan.force","true");
        Path config=root.resolve("supervisor.properties");ServerFiles.write(config,p);return config;
    }
    private static boolean online(ServerProcessConfig config)throws IOException {
        Path file=config.stateDir().resolve("control.properties");if(!Files.isRegularFile(file))return false;var p=ServerFiles.read(file);
        return p.getProperty("online","false").equals("true")&&ServerFiles.sameProcess(ServerFiles.number(p,"pid",0),p.getProperty("start",""));
    }
    private static void offline(ServerProcessConfig config)throws IOException {
        var result=new LinkedHashMap<String,Object>();result.put("online",false);result.put("report","server_supervisor_v51");
        Path summary=config.stateDir().resolve("summary.properties");if(Files.isRegularFile(summary))ServerFiles.read(summary).forEach((k,v)->result.put(String.valueOf(k),v));result.put("online",false);result.put("summaryIsHistorical",true);result.put("preparedRestartHistorical",true);result.put("preparedRestartPending",false);result.put("minecraftReady",false);result.put("lifecycleFresh",false);result.put("saveConfirmed",false);
        Path current=config.stateDir().resolve("current.properties");if(Files.isRegularFile(current)){
            String run=ServerFiles.read(current).getProperty("run","");
            if(ServerProcessSupervisor.canonicalUuid(run)){
                Path status=config.stateDir().resolve("runs").resolve(run).resolve("status.properties");
                if(Files.isRegularFile(status)){var child=ServerFiles.read(status);result.put("processRunning",ServerProcessSupervisor.alive(child,"child"));result.put("guardianRunning",ServerProcessSupervisor.alive(child,"guardian"));}
            }
        }
        System.out.println(ServerJson.object(result));
    }
    private static String request(ServerProcessConfig config,String action,boolean confirmed)throws Exception {return request(config,action,confirmed,null,null);}
    private static String request(ServerProcessConfig config,String action,boolean confirmed,String expectedRun,String expectedOwner)throws Exception {
        var endpoint=ServerFiles.read(config.stateDir().resolve("control.properties"));
        if(!online(config))throw new IOException("Supervisor is offline; run it before sending commands");
        if(expectedOwner!=null&&!expectedOwner.equals(endpoint.getProperty("instance")))throw new IOException("EVIDENCE_OWNER_CHANGED_NO_COMMAND_SENT");
        int port=(int)ServerFiles.number(endpoint,"port",0);if(port<1||port>65535)throw new IOException("Invalid local control port");String base="http://127.0.0.1:"+port;
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).proxy(new ProxySelector(){public java.util.List<Proxy> select(URI uri){return java.util.List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,IOException bad){}}).build();
        String token=endpoint.getProperty("token","");
        if(action.equals("status")||action.equals("logs"))return send(client,HttpRequest.newBuilder(URI.create(base+"/api/"+action)).header("Authorization","Bearer "+token).timeout(Duration.ofSeconds(5)).GET().build());
        String status=send(client,HttpRequest.newBuilder(URI.create(base+"/api/status")).header("Authorization","Bearer "+token).timeout(Duration.ofSeconds(5)).GET().build());
        if(expectedRun!=null&&(!status.contains("\"run\":\""+expectedRun+"\"")||!status.contains("\"instance\":\""+expectedOwner+"\"")))throw new IOException("EVIDENCE_RUN_CHANGED_NO_COMMAND_SENT");
        var matcher=java.util.regex.Pattern.compile("\\\"revision\\\":([0-9]+)").matcher(status);if(!matcher.find())throw new IOException("Missing control revision");String revision=matcher.group(1);
        var builder=HttpRequest.newBuilder(URI.create(base+"/api/"+action)).header("Authorization","Bearer "+token).header("X-Genesis-Instance",endpoint.getProperty("instance","")).header("X-Genesis-Revision",revision).timeout(Duration.ofSeconds(5));
        if(confirmed)builder.header("X-Genesis-Confirmation","checked");
        return send(client,builder.POST(HttpRequest.BodyPublishers.ofString(UUID.randomUUID().toString())).build());
    }
    private static String send(HttpClient client,HttpRequest request)throws Exception {
        var response=client.send(request,HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));if(response.statusCode()>=300)throw new IOException("Control HTTP "+response.statusCode()+": "+response.body());return response.body();
    }
}
