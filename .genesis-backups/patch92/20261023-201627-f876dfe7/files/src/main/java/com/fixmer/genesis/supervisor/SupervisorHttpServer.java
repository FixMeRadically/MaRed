package com.fixmer.genesis.supervisor;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Authenticated loopback control, exact paths and explicit HTTP methods. */
final class SupervisorHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService pool = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(32), r -> { Thread t = new Thread(r, "supervisor-http"); t.setDaemon(true); return t; },
        new ThreadPoolExecutor.AbortPolicy());
    private final SupervisorConfig config;
    private final Supervisor supervisor;
    private final SupervisorLog log;
    SupervisorHttpServer(SupervisorConfig config, Supervisor supervisor, SupervisorLog log) throws IOException {
        this.config = config; this.supervisor = supervisor; this.log = log;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", config.controlPort()), 16);
        server.setExecutor(pool); server.createContext("/", this::handle);
    }
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    void start() { server.start(); }
    @Override public void close() {if(closed.compareAndSet(false,true)){try{server.stop(1);}finally{pool.shutdownNow();}}}
    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            boolean read = path.equals("/status") || path.equals("/logs") || path.equals("/identity");
            if (!read && !path.equals("/start") && !path.equals("/stop") && !path.equals("/restart") && !path.equals("/deployment/ready") && !path.equals("/metrics") && !path.equals("/retire") && !path.equals("/handoff/commit") && !path.equals("/handoff/cancel")) { respond(ex,404,"{}"); return; }
            if (!ex.getRequestMethod().equals(read ? "GET" : "POST")) {
                ex.getResponseHeaders().set("Allow", read ? "GET" : "POST"); respond(ex,405,"{}"); return;
            }
            String token = ex.getRequestHeaders().getFirst("X-Genesis-Token");
            if (ex.getRequestHeaders().containsKey("Origin") || token == null || !MessageDigest.isEqual(
                    token.getBytes(StandardCharsets.UTF_8), config.token().getBytes(StandardCharsets.UTF_8))) {
                respond(ex,403,"{\"error\":\"unauthorized\"}"); return;
            }
            if(path.equals("/identity")){respond(ex,200,supervisor.identityProperties(),"text/plain; charset=utf-8");return;}
            if(path.equals("/handoff/commit")||path.equals("/handoff/cancel")||path.equals("/retire")){
                String instance=ex.getRequestHeaders().getFirst("X-Genesis-Instance");boolean accepted;
                if(path.equals("/retire"))accepted=supervisor.retire(instance);
                else if(path.equals("/handoff/cancel")){
                    // close() must not run in this HTTP handler, which it needs to drain.
                    accepted=supervisor.cancelHandoffAsync(instance);
                }else{
                    long parent;try{parent=Long.parseLong(ex.getRequestHeaders().getFirst("X-Genesis-Parent"));}catch(RuntimeException e){respond(ex,400,"{}");return;}
                    accepted=supervisor.commitHandoff(instance,parent);
                }
                respond(ex,accepted?202:409,"{\"ok\":"+accepted+"}");return;
            }
            if (path.equals("/status")) { respond(ex,200,supervisor.statusJson()); return; }
            if (path.equals("/logs")) {
                int n = 200;
                String q = ex.getRequestURI().getRawQuery();
                if (q != null && q.startsWith("n=")) try { n = Math.max(1, Math.min(2000,Integer.parseInt(q.substring(2)))); }
                    catch (NumberFormatException e) { respond(ex,400,"{}"); return; }
                String body = log.tail(n).stream().map(SupervisorHttpServer::quote).collect(java.util.stream.Collectors.joining(","));
                respond(ex,200,"{\"lines\":[" + body + "]}"); return;
            }
            if(path.equals("/metrics")) {
                long duration;
                try{duration=Long.parseLong(ex.getRequestHeaders().getFirst("X-Genesis-Duration"));}
                catch(RuntimeException e){respond(ex,400,"{}");return;}
                boolean accepted=supervisor.recordMetric(ex.getRequestHeaders().getFirst("X-Genesis-Launch"),ex.getRequestHeaders().getFirst("X-Genesis-Phase"),duration);
                respond(ex,accepted?202:409,"{\"ok\":"+accepted+"}");return;
            }
            if (path.equals("/deployment/ready")) {
                boolean accepted = supervisor.confirmDeployment(ex.getRequestHeaders().getFirst("X-Genesis-Trial"));
                respond(ex,accepted?202:409,"{\"ok\":"+accepted+"}");return;
            }
            boolean accepted = supervisor.requestHttp(path.substring(1), "cooperative=true".equals(ex.getRequestURI().getRawQuery()),ex.getRequestHeaders().getFirst("X-Genesis-Launch"));
            respond(ex, accepted ? 202 : 409, "{\"ok\":" + accepted + "}");
        } catch (Exception e) { log.error("HTTP: " + e); respond(ex,500,"{\"error\":\"internal error\"}"); }
        finally { ex.close(); }
    }
    private static void respond(HttpExchange ex, int code, String text) throws IOException {
        respond(ex,code,text,"application/json; charset=utf-8");
    }
    private static void respond(HttpExchange ex,int code,String text,String type)throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code,data.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(data); }
    }
    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) switch(c) {
            case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\");
            case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
            default -> { if (c < 32) b.append(String.format("\\u%04x", (int)c)); else b.append(c); }
        }
        return b.append('"').toString();
    }
}
