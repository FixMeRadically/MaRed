package com.fixmer.mared.dev;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
/** Small asynchronous milestone reports. Never delays the server/render thread or sends launch arguments. */
public final class DevTelemetry {
    private static final ExecutorService WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r-> {
        Thread t=new Thread(r,"mared-dev-timings");t.setDaemon(true);return t;
    }
    ,new ThreadPoolExecutor.DiscardPolicy());
    private static final class Http {static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();}
    private DevTelemetry() {
    }
    public static void mark(String phase) {
        if("mod_loaded".equals(phase)){com.fixmer.genesis.supervisor.GameSessionClient.start();System.out.println("[Genesis restart] v32 loaded; supervisor="+"1".equals(System.getenv("GENESIS_SUPERVISOR"))+"; transition="+(System.getenv("GENESIS_TRANSITION_FILE")!=null)+"; controller="+("32".equals(System.getenv("GENESIS_FRAME_HANDOFF"))?"32":"30".equals(System.getenv("GENESIS_FRAME_HANDOFF"))?"30":"28".equals(System.getenv("GENESIS_FRAME_HANDOFF"))?"28":"legacy"));}
        com.fixmer.genesis.supervisor.GameSessionClient.milestone(phase);
        report(phase,ManagementFactory.getRuntimeMXBean().getUptime());
    }
    public static void report(String phase,long milliseconds) {
        if(!"4".equals(System.getenv("GENESIS_SUPERVISOR_PROTOCOL")))return;
        try {
            WORKER.execute(()-> {
                try {
                    HttpClient client=Http.CLIENT;
                    int port=Integer.parseInt(System.getenv("GENESIS_SUPERVISOR_PORT")); var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/metrics")) .header("X-Genesis-Token",System.getenv("GENESIS_SUPERVISOR_TOKEN")) .header("X-Genesis-Launch",System.getenv("GENESIS_LAUNCH_ID")) .header("X-Genesis-Phase",phase).header("X-Genesis-Duration",Long.toString(milliseconds)) .timeout(Duration.ofSeconds(2)).POST(HttpRequest.BodyPublishers.noBody()).build(); client.send(request,HttpResponse.BodyHandlers.discarding());
                }
                catch(Exception ignored) {
                }
            }
            );
        }
        catch(RejectedExecutionException ignored) {
        }
    }
}
