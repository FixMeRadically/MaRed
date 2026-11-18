package com.fixmer.genesis.supervisor;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** The actual supervisor HTTP adapter on loopback, without launching or stopping a game. */
public final class SupervisorControlChecks123 {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Supervisor123 #"+checks);}
    private static HttpResponse<String> send(HttpClient client,int port,String path,String method,Map<String,String> headers)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(4));
        headers.forEach(request::header);
        request.method(method,HttpRequest.BodyPublishers.noBody());
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("Genesis supervisor123 ");
        try {
            int port;try(var socket=new java.net.ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
            String secret=UUID.randomUUID().toString()+UUID.randomUUID(),instance=UUID.randomUUID().toString();
            var properties=new Properties();properties.setProperty("workDir",root.toString());properties.setProperty("gameDir",root.toString());
            properties.setProperty("command.0",Path.of(System.getProperty("java.home"),"bin","java.exe").toString());
            properties.setProperty("controlPort",Integer.toString(port));properties.setProperty("controlToken",secret);properties.setProperty("instanceId",instance);
            properties.setProperty("restartUi.enabled","false");
            Path file=root.resolve("supervisor.properties");try(var writer=Files.newBufferedWriter(file)){properties.store(writer,"test");}
            var config=SupervisorConfig.load(new String[]{"--config="+file});
            try(var log=new SupervisorLog(null);var supervisor=new Supervisor(config,log);var control=new SupervisorHttpServer(config,supervisor,log);var client=HttpClient.newHttpClient()) {
                control.start();
                check(send(client,port,"/status","GET",Map.of()).statusCode()==403);
                check(send(client,port,"/status","GET",Map.of("X-Genesis-Token","incorrect")).statusCode()==403);
                var status=send(client,port,"/status","GET",Map.of("X-Genesis-Token",secret));
                check(status.statusCode()==200);check(status.body().contains("\"state\":\"STOPPED\""));check(!status.body().contains(secret));
                check(send(client,port,"/status","GET",Map.of("X-Genesis-Token",secret,"Origin","https://example.invalid")).statusCode()==403);
                check(send(client,port,"/restart","GET",Map.of("X-Genesis-Token",secret)).statusCode()==405);
                check(send(client,port,"/restart/unexpected","POST",Map.of("X-Genesis-Token",secret)).statusCode()==404);
                check(send(client,port,"/logs?n=invalid","GET",Map.of("X-Genesis-Token",secret)).statusCode()==400);
                check(send(client,port,"/restart95/prepare","POST",Map.of("X-Genesis-Token",secret)).statusCode()==403);
                String nonce=UUID.randomUUID().toString().replace("-",""),time=Long.toString(System.currentTimeMillis()),path="/recovery92/identity";
                var headers=new HashMap<>(Map.of("X-Genesis-Instance",instance,"X-Genesis-Recovery-Nonce",nonce,"X-Genesis-Recovery-Time",time));
                headers.put("X-Genesis-Recovery-Proof",RecoveryAuth92.request(secret,instance,"GET",path,nonce,time,null,null,null));
                var identity=send(client,port,path,"GET",headers);
                check(identity.statusCode()==200);
                check(RecoveryAuth92.equal(identity.headers().firstValue("X-Genesis-Recovery-Response").orElse(null),RecoveryAuth92.response(secret,nonce,200,identity.body())));
                check(!identity.body().contains(secret));
                check(send(client,port,path,"GET",headers).statusCode()==403);
                // Signing a foreign instance does not authorize the current one.
                headers.put("X-Genesis-Instance",UUID.randomUUID().toString());
                check(send(client,port,path,"GET",headers).statusCode()==403);
                check(supervisor.statusJson().contains("\"pid\":-1"));
            }
            Files.writeString(root.resolve("gradlew.bat"),"@exit /b 0\r\n");
            Files.writeString(root.resolve("gradlew"),"exit 0\n");
            var command=ManagedChildProcess.normalize(List.of("./gradlew","runClient"),root);
            if(System.getProperty("os.name").startsWith("Windows")) {
                check(command.get(0).equals("cmd.exe"));
                check(command.get(command.size()-1).contains("gradlew.bat"));
                check(command.get(command.size()-1).contains("--no-daemon"));
            }
        } finally {try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Supervisor123 passed: "+checks+"; actual HTTP authentication/replay/response binding/Windows command normalization; no Minecraft restart.");
    }
}
