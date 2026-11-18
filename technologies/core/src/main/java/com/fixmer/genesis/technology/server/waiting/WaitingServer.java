package com.fixmer.genesis.technology.server.waiting;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.fixmer.genesis.technology.server.players.*;

/** External loopback-only service. Remote listeners/TLS/proxies are deliberately not configurable yet. */
public final class WaitingServer implements AutoCloseable {
    private final Path root;private final WaitingPreparation preparation;private final PlayerUpdateJournal journal;final WaitingBroker broker;
    private final HttpServer http;private final ThreadPoolExecutor workers;private final ScheduledExecutorService timer;
    private final CountDownLatch stopped=new CountDownLatch(1);private boolean closed;
    private WaitingServer(Path root)throws IOException{
        this.root=WaitingFiles.safe(root);Map<String,String> config=WaitingFiles.read(root.resolve("waiting.v56"));WaitingFiles.keys(config,"schema","update","packageSha256");
        if(!config.get("schema").equals("56"))throw new IOException("UNSUPPORTED_WAITING_ROOT");
        preparation=WaitingPreparation.load(root);if(preparation!=null)preparation.matches(config.get("update"),config.get("packageSha256"));
        WaitingFiles.privateMode(root,true);journal=PlayerUpdateJournal.open(root.resolve("state"));
        WaitingBroker b=null;HttpServer h=null;ThreadPoolExecutor w=null;ScheduledExecutorService t=null;
        try{
            var state=journal.state();if(!state.update().toString().equals(config.get("update"))||!state.packageSha256().equals(config.get("packageSha256")))throw new IOException("WAITING_ROOT_BINDING_CHANGED");
            WaitingFiles.privateMode(root.resolve("state"),true);for(Path file:List.of(root.resolve("state/players.v55"),root.resolve("state/players.lock")))WaitingFiles.privateMode(file,false);
            b=new WaitingBroker(journal,System::nanoTime);broker=b;
            transportLimits();h=HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),0),16);http=h;
            w=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),new ThreadPoolExecutor.CallerRunsPolicy());workers=w;
            t=Executors.newSingleThreadScheduledExecutor(r->{Thread thread=new Thread(r,"Genesis-waiting-maintenance");thread.setDaemon(true);return thread;});timer=t;
            http.setExecutor(workers);http.createContext("/",this::handle);
            WaitingFiles.write(root.resolve("controller.key"),Map.of("instance",broker.instance.toString(),"token",broker.controller),true);
            WaitingFiles.write(root.resolve("endpoint.v56"),Map.of("schema","56","instance",broker.instance.toString(),"port",Integer.toString(port()),"epoch",Long.toString(state.epoch())),true);
            http.start();timer.scheduleWithFixedDelay(()->{try{broker.maintain();}catch(Exception failed){broker.fail();timer.shutdown();}},200,200,TimeUnit.MILLISECONDS);
        }catch(IOException|RuntimeException bad){if(t!=null)t.shutdownNow();if(h!=null)h.stop(0);if(w!=null)w.shutdownNow();journal.close();throw bad;}
    }
    public static WaitingServer start(Path root)throws IOException{return new WaitingServer(root.toAbsolutePath().normalize());}
    /** JDK-specific bounds documented by Oracle; standalone entry point sets them before HTTP initialization. */
    static void transportLimits(){
        Map.of("jdk.httpserver.maxConnections","24","sun.net.httpserver.maxReqHeaders","24","sun.net.httpserver.maxReqHeaderSize","8192","sun.net.httpserver.maxReqTime","5000","sun.net.httpserver.maxRspTime","5000","sun.net.httpserver.maxIdleConnections","0","sun.net.httpserver.drainAmount","0").forEach(System::setProperty);
    }
    public int port(){return http.getAddress().getPort();}
    public UUID instance(){return broker.instance;}
    public void await()throws InterruptedException{stopped.await();}
    private void handle(HttpExchange exchange)throws IOException{
        boolean stopRequested=false;
        try{
            String path=exchange.getRequestURI().getRawPath();String method=exchange.getRequestMethod();var headers=exchange.getRequestHeaders();
            if(!exchange.getRemoteAddress().getAddress().isLoopbackAddress()||exchange.getRequestURI().getRawQuery()!=null||exchange.getRequestURI().isAbsolute()||headers.containsKey("Origin")||headers.containsKey("Referer")||headers.containsKey("Transfer-Encoding"))throw new HttpFailure(400,"INVALID_REQUEST");
            String host=one(headers,"Host"),instance=one(headers,"X-Genesis-Instance"),authorization=one(headers,"Authorization");
            if(!host.equals("127.0.0.1:"+port()))throw new HttpFailure(400,"INVALID_HOST");
            if(!instance.equals(broker.instance.toString()))throw new HttpFailure(409,"OWNER_CHANGED");
            if(!authorization.matches("Bearer [a-f0-9]{64}"))throw new HttpFailure(401,"UNAUTHORIZED");String token=authorization.substring(7);
            boolean controller=path.startsWith("/v1/controller/");WaitingBroker.Grant grant=null;
            if(controller){if(!broker.controller(token))throw new HttpFailure(403,"FORBIDDEN");}
            else {grant=broker.player(token);if(grant==null)throw new HttpFailure(403,"GRANT_REVOKED");}
            Map<String,String> response;
            if(method.equals("GET")&&(path.equals("/v1/player/status")||path.equals("/v1/controller/status"))){
                if(headers.containsKey("Content-Length")&&!one(headers,"Content-Length").equals("0"))throw new HttpFailure(400,"BODY_NOT_ALLOWED");
                response=controller?broker.status():broker.playerStatus(grant);
            }else if(method.equals("POST")){
                if(!one(headers,"Content-Type").equals("application/vnd.genesis.waiting-v56"))throw new HttpFailure(415,"CONTENT_TYPE_REQUIRED");
                long length=WaitingFiles.number(one(headers,"Content-Length"));if(length>WaitingFiles.MAX)throw new HttpFailure(413,"BODY_LIMIT");
                byte[] body=exchange.getRequestBody().readNBytes((int)length+1);if(body.length!=length)throw new HttpFailure(400,"BODY_LENGTH");Map<String,String> input=WaitingFiles.decode(body);
                response=switch(path){
                    case "/v1/player/decision"->broker.decide(grant,input);
                    case "/v1/controller/game-close"->broker.gameClose(input);
                    case "/v1/controller/game-open"->{requirePrepared();yield broker.gameOpen(input);}
                    case "/v1/controller/game-event"->{
                        if(!"logout".equals(input.get("action")))requirePrepared();
                        yield broker.gameEvent(input);
                    }
                    case "/v1/controller/issue"->{if(broker.gameActive())throw new IOException("GAME_ACTOR_OWNS_GRANTS");yield broker.issue(input);}
                    case "/v1/controller/action"->broker.control(input);
                    case "/v1/controller/revoke"->{if(broker.gameActive())throw new IOException("GAME_ACTOR_OWNS_GRANTS");yield broker.revoke(input);}
                    case "/v1/controller/stop"->{WaitingFiles.keys(input,"confirm");if(!input.get("confirm").equals("true"))throw new HttpFailure(400,"CONFIRM_REQUIRED");stopRequested=true;yield Map.of("stopping","true");}
                    default->throw new HttpFailure(404,"NOT_FOUND");
                };
                // Exact path authorization, independent of a shared prefix.
            }else throw new HttpFailure(405,"METHOD_NOT_ALLOWED");
            respond(exchange,200,response);
        }catch(HttpFailure e){respond(exchange,e.status,Map.of("error",e.code));}
        catch(WaitingBroker.Unavailable e){respond(exchange,503,Map.of("error","SERVICE_REVIEW_REQUIRED"));}
        catch(IOException|IllegalArgumentException|ArithmeticException e){respond(exchange,409,Map.of("error","REQUEST_REFUSED"));}
        catch(RuntimeException e){respond(exchange,503,Map.of("error","SERVICE_REVIEW_REQUIRED"));}
        finally{exchange.close();if(stopRequested)new Thread(()->{try{close();}catch(IOException ignored){}},"Genesis-waiting-stop").start();}
    }
    private void requirePrepared()throws IOException{if(preparation==null)throw new IOException("SANDBOX_CANNOT_BIND_GAME");preparation.check();}
    private static String one(Headers headers,String key)throws HttpFailure{List<String> values=headers.get(key);if(values==null||values.size()!=1)throw new HttpFailure(400,"HEADER_REQUIRED");return values.get(0);}
    private static void respond(HttpExchange exchange,int status,Map<String,String> values)throws IOException{
        byte[] body=WaitingFiles.encode(values);var h=exchange.getResponseHeaders();h.set("Content-Type","application/vnd.genesis.waiting-v56");h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("Connection","close");exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);
    }
    private static final class HttpFailure extends IOException{final int status;final String code;HttpFailure(int status,String code){this.status=status;this.code=code;}}
    @Override public synchronized void close()throws IOException{
        if(closed)return;closed=true;broker.stop();timer.shutdownNow();http.stop(0);workers.shutdownNow();
        try{journal.close();}finally{stopped.countDown();}
        // Endpoint/key remain historical; next owner atomically rotates both. No PID-based killing or blind replay.
    }
}
