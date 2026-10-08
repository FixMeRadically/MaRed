package com.fixmer.mared.commands.engine;
import java.util.*;
public class MaredScriptContext implements com.fixmer.genesis.technology.expression.ExpressionEnvironment {
 private boolean persistent; public boolean isPersistent(){return persistent;} public void setPersistent(boolean value){persistent=value;}

 public static class Func { public List<String> params; public List<MaredScriptCommand> body; public Func(List<String> p,List<MaredScriptCommand> b){params=p;body=b;} }
 public MaredScriptContext() {} public MaredScriptContext(net.minecraft.server.level.ServerPlayer p,net.minecraft.server.MinecraftServer s,java.util.function.Consumer<String> logger){server=s;}
 public net.minecraft.server.level.ServerPlayer getInitiator(){return null;}
 public MaredScriptContext forkFor(net.minecraft.server.level.ServerPlayer p,net.minecraft.server.MinecraftServer s){var c=new MaredScriptContext();c.server=s;return c;}
 public void refreshPlayerData(){} public void refreshEventData(Map<String,Object> values){vars.putAll(values);}
 public net.minecraft.server.MinecraftServer server; public net.minecraft.server.MinecraftServer getServer(){return server;}
 public final Map<String,Object> vars=new HashMap<>(); public final Map<String,Func> funcs=new HashMap<>();
 public Func getFunction(String n){return funcs.get(n);} public Object getVariable(String n){return vars.get(n);}
 public boolean hasVariable(String n){return vars.containsKey(n);} public void setVariable(String n,Object v){vars.put(n,v);}
 public void removeVariable(String n){vars.remove(n);} public void log(String line){}
 public boolean hasFunction(String n){return funcs.containsKey(n);} public Object callFunction(String n,List<Object> args){return callBuiltin(n,args);}
 public int calls; public Object callBuiltin(String n,List<Object> args){calls++;return 10L;}
 public Object callMethod(Object v,String n,List<Object> args){calls++;return v;}
 public String substitute(String s){return s;}
}
