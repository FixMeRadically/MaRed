package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.remote.*;import net.minecraft.client.Minecraft;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** Capture actual Minecraft.getUser on the game thread; private I/O remains off it. No token logging. */
public final class AuthClient89 {
 private AuthClient89(){}
 public static String serve(Path root,Map<String,String> context,String previous){try{
  var request=SessionHandoff89.request(root,context,System.currentTimeMillis());if(request.isEmpty()){Files.deleteIfExists(root.resolve("auth89.response"));return previous;}if(request.get("nonce").equals(previous))return previous;
  CompletableFuture<Map<String,String>> capture=new CompletableFuture<>();Minecraft.getInstance().execute(()->{try{var u=Minecraft.getInstance().getUser();capture.complete(Map.of("account",u.getProfileId().toString(),"name",u.getName(),"accessToken",context.get("mode").equals("LOCAL_OFFLINE")?"":u.getAccessToken(),"userType",context.get("mode").equals("LOCAL_OFFLINE")?"legacy":u.getType().getName()));}catch(RuntimeException e){capture.completeExceptionally(new IllegalStateException("AUTH89_SESSION"));}});
  var user=capture.get(3,TimeUnit.SECONDS);SessionHandoff89.answer(root,request,user,System.currentTimeMillis());return request.get("nonce");
 }catch(Exception ignored){return previous;}}
 public static Map<String,String> initial(com.google.gson.JsonObject r){return Map.of("sourceRun",r.get("request").getAsString(),"profile",r.get("profile").getAsString(),"account",r.get("account").getAsString(),"mode",r.get("authMode").getAsString(),"scope","INITIAL","pid",r.get("pid").getAsString(),"birth",r.get("birth").getAsString());}
}
