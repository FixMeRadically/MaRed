package com.fixmer.genesis.supervisor;
import java.util.*;
/** No Minecraft linkage. Warning-only LoadingErrorScreen is attention, never a fatal error. */
public final class StartupErrorProbe101 {
 private StartupErrorProbe101(){}
 public static String classify(Object screen){
  if(screen==null)return "";String name=screen.getClass().getName();
  if(name.equals("net.neoforged.neoforge.client.gui.LoadingErrorScreen")){
   // Public loader API works across module boundaries; private issue lists are only a fallback.
   try{Object errors=Class.forName("net.neoforged.fml.ModLoader",false,screen.getClass().getClassLoader()).getMethod("hasErrors").invoke(null);if(errors instanceof Boolean value)return value?"LOADER_ERROR":"ATTENTION";}catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){}
   return classifyIssues(screen,"modLoadErrors");
  }
  return name.equals("net.minecraft.client.gui.screens.ErrorScreen")?"ATTENTION":"";
 }
 static String classifyIssues(Object object,String field){try{var f=object.getClass().getDeclaredField(field);if(!f.trySetAccessible())return "ATTENTION";Object errors=f.get(object);return errors instanceof List<?> list&&!list.isEmpty()?"LOADER_ERROR":"ATTENTION";}catch(ReflectiveOperationException|RuntimeException unavailable){return "ATTENTION";}}
}
