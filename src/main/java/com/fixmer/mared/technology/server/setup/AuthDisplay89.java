package com.fixmer.mared.technology.server.setup;
import com.google.gson.JsonObject;import net.minecraft.network.chat.Component;import java.util.*;
/** Numeric/stage display only, never credentials. */
public final class AuthDisplay89 {
 private AuthDisplay89(){}
 public static void add(List<Component> lines,JsonObject state){try{if(!state.has("auth89"))return;var a=state.getAsJsonObject("auth89");if(a.get("schema").getAsInt()!=89)return;String stage=a.get("stage").getAsString();if(Set.of("CHECKING","VERIFIED","OFFLINE","REFUSED").contains(stage))lines.add(Component.translatable("mared.auth89."+stage));}catch(RuntimeException bad){}}
}
