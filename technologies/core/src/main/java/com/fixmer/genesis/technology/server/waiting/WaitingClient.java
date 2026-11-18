package com.fixmer.genesis.technology.server.waiting;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Loopback-only bounded client. One request, no redirects, proxies, automatic application replay or token logs. */
final class WaitingClient {
    private WaitingClient() {}
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI u,SocketAddress s,IOException e){}}).build();
    static Map<String,String> request(Map<String,String> endpoint,String token,String path,Map<String,String> body)throws Exception{
        UUID instance=WaitingFiles.id(endpoint.get("instance"));long port=WaitingFiles.number(endpoint.get("port"));if(port<1||port>65535||token==null||!token.matches("[a-f0-9]{64}"))throw new IOException("Invalid endpoint");
        if(!Set.of("/v1/controller/game-close","/v1/controller/game-open","/v1/controller/game-event","/v1/controller/status","/v1/controller/issue","/v1/controller/action","/v1/controller/revoke","/v1/controller/stop","/v1/player/status","/v1/player/decision").contains(path))throw new IOException("Unsupported route");
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(8)).header("X-Genesis-Instance",instance.toString()).header("Authorization","Bearer "+token);
        if(body==null)builder.GET();else builder.header("Content-Type","application/vnd.genesis.waiting-v56").POST(HttpRequest.BodyPublishers.ofByteArray(WaitingFiles.encode(body)));
        CompletableFuture<HttpResponse<byte[]>> future=HTTP.sendAsync(builder.build(),info->new BoundedBody());
        HttpResponse<byte[]> response;try{response=future.get(10,TimeUnit.SECONDS);}catch(InterruptedException interrupted){future.cancel(true);Thread.currentThread().interrupt();throw interrupted;}catch(Exception failed){future.cancel(true);throw failed;}
        if(response.statusCode()!=200||!response.headers().allValues("Content-Type").equals(List.of("application/vnd.genesis.waiting-v56")))throw new IOException("HTTP request refused");var result=WaitingFiles.decode(response.body());
        if(!result.getOrDefault("instance",instance.toString()).equals(instance.toString()))throw new IOException("Owner changed during request");return result;
    }
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();final ByteArrayOutputStream out=new ByteArrayOutputStream();Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){if(subscription!=null){s.cancel();return;}subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> chunks){
            try{for(ByteBuffer b:chunks){if(out.size()+b.remaining()>WaitingFiles.MAX)throw new IOException("Response size bound");byte[] bytes=new byte[b.remaining()];b.get(bytes);out.write(bytes);}subscription.request(1);}
            catch(Exception bad){subscription.cancel();result.completeExceptionally(bad);}
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(out.toByteArray());}
    }
}
