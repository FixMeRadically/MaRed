package com.fixmer.mared.technology.server.setup;
import com.google.gson.JsonObject;import net.minecraft.network.chat.Component;import java.util.*;
/** Bounded display only. Storage estimates are never process/installation authority. */
public final class InstallPreflight87 {
 private InstallPreflight87(){}
 private static long number(JsonObject d,String name){String s=d.get(name).getAsString();if(!s.matches("0|[1-9][0-9]{0,15}"))throw new IllegalArgumentException();long n=Long.parseLong(s);if(n>9007199254740991L)throw new IllegalArgumentException();return n;}
 private static String mib(long n){return Long.toString((n+1048575)/1048576);}
 public static void add(List<Component> lines,JsonObject state){try{
  if(!state.has("preflight87"))return;JsonObject d=state.getAsJsonObject("preflight87");if(number(d,"schema")!=87)return;String stage=d.get("stage").getAsString();if(!Set.of("SCANNING","CHECKED","REFUSED").contains(stage))return;
  long files=number(d,"files"),bytes=number(d,"profileBytes"),required=number(d,"requiredBytes"),available=number(d,"availableBytes");if(files>200000||bytes>20L*1024*1024*1024)return;
  lines.add(Component.translatable("mared.remote70.preflight87."+stage));lines.add(Component.translatable("mared.remote70.preflight87.profile",files,mib(bytes)));
  if(required>0){lines.add(Component.translatable("mared.remote70.preflight87.space",mib(required),mib(available)));lines.add(Component.translatable("mared.remote70.preflight87.estimate"));}
 }catch(RuntimeException malformed){/* Never present malformed numbers or raw exception text as a verified check. */}}
}
