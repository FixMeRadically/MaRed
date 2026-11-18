package com.fixmer.genesis.technology.server.process;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated local console only. No public bind, CORS, filesystem routes or arbitrary stdin. */
final class ServerControlServer implements AutoCloseable {
    private final ServerProcessSupervisor owner;
    private final HttpServer server;
    private final ThreadPoolExecutor workers;
    private final String token,instance;
    ServerControlServer(ServerProcessSupervisor owner,int port,String token,String instance)throws IOException {
        this.owner=owner;this.token=token;this.instance=instance;
        server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),16);
        workers=new ThreadPoolExecutor(2,4,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"genesis-server-control");t.setDaemon(true);return t;});
        server.setExecutor(workers);server.createContext("/",this::handle);
    }
    int port(){return server.getAddress().getPort();}
    void start(){server.start();}
    private void handle(HttpExchange exchange)throws IOException {
        try{
            String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
            String host=exchange.getRequestHeaders().getFirst("Host"),origin=exchange.getRequestHeaders().getFirst("Origin");
            if(!Set.of("127.0.0.1:"+port(),"localhost:"+port()).contains(host==null?"":host)){send(exchange,403,"application/json","{\"error\":\"INVALID_HOST\"}");return;}
            if(origin!=null&&!origin.equals("http://127.0.0.1:"+port())&&!origin.equals("http://localhost:"+port())){send(exchange,403,"application/json","{\"error\":\"INVALID_ORIGIN\"}");return;}
            if(exchange.getRequestURI().getRawQuery()!=null){send(exchange,400,"application/json","{\"error\":\"QUERY_PARAMETERS_UNSUPPORTED\"}");return;}
            if(method.equals("GET")&&Set.of("/","/app.js","/style.css").contains(path)){
                String resource=path.equals("/")?"index.html":path.substring(1),mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":"text/html";
                try(var in=ServerControlServer.class.getResourceAsStream("/com/fixmer/genesis/server-console/"+resource)){
                    if(in==null){send(exchange,404,"text/plain","Console resources missing; build genesis-core resources.");return;}
                    send(exchange,200,mime,new String(in.readNBytes(65536),StandardCharsets.UTF_8));return;
                }
            }
            String authorization=exchange.getRequestHeaders().getFirst("Authorization");
            if(authorization==null||authorization.length()!=71||!MessageDigest.isEqual(authorization.getBytes(StandardCharsets.US_ASCII),("Bearer "+token).getBytes(StandardCharsets.US_ASCII))){send(exchange,401,"application/json","{\"error\":\"AUTH_REQUIRED\"}");return;}
            if(method.equals("GET")&&path.equals("/api/status")){send(exchange,200,"application/json",ServerJson.object(owner.snapshot()));return;}
            if(method.equals("GET")&&path.equals("/api/logs")){send(exchange,200,"application/json",ServerJson.object(Map.of("text",owner.logTail())));return;}
            if(!method.equals("POST")||!path.startsWith("/api/")){send(exchange,404,"application/json","{\"error\":\"UNKNOWN_ROUTE\"}");return;}
            String action=path.substring(5);if(!Set.of("start","stop","restart","shutdown","force-stop","resolve","checkpoint","prepare-stop","prepare-restart","cancel-prepare").contains(action)){send(exchange,404,"application/json","{\"error\":\"UNKNOWN_ACTION\"}");return;}
            if(!instance.equals(exchange.getRequestHeaders().getFirst("X-Genesis-Instance"))){send(exchange,409,"application/json","{\"error\":\"WRONG_INSTANCE\"}");return;}
            byte[] bytes=exchange.getRequestBody().readNBytes(129);if(bytes.length>128){send(exchange,413,"application/json","{\"error\":\"REQUEST_SIZE_LIMIT\"}");return;}
            String id=new String(bytes,StandardCharsets.US_ASCII);long revision;
            try{if(!ServerProcessSupervisor.canonicalUuid(id))throw new IllegalArgumentException();revision=Long.parseLong(exchange.getRequestHeaders().getFirst("X-Genesis-Revision"));if(revision<0)throw new IllegalArgumentException();}
            catch(IllegalArgumentException bad){send(exchange,400,"application/json","{\"error\":\"INVALID_REQUEST_ID_OR_REVISION\"}");return;}
            boolean confirmed="checked".equals(exchange.getRequestHeaders().getFirst("X-Genesis-Confirmation"));
            var reply=owner.command(action,UUID.fromString(id),revision,confirmed);
            send(exchange,reply.accepted()?202:reply.outcome().equals("unknown")?503:409,"application/json",ServerJson.object(Map.of("accepted",reply.accepted(),"outcome",reply.outcome(),"message",reply.message(),"revision",reply.revision())));
        }catch(IOException bad){try{send(exchange,503,"application/json","{\"error\":\"CONTROL_IO_FAILED\"}");}catch(IOException ignored){}}
        catch(RuntimeException bad){try{send(exchange,500,"application/json","{\"error\":\"CONTROL_FAILED\"}");}catch(IOException ignored){}}
        finally{exchange.close();}
    }
    private static void send(HttpExchange exchange,int status,String type,String text)throws IOException {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);var headers=exchange.getResponseHeaders();headers.set("Content-Type",type+"; charset=utf-8");headers.set("Cache-Control","no-store");headers.set("X-Content-Type-Options","nosniff");headers.set("X-Frame-Options","DENY");
        headers.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
    }
    @Override public void close(){server.stop(0);workers.shutdownNow();}
}
