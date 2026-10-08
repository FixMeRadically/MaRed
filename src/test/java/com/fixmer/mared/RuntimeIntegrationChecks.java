package com.fixmer.mared;
import java.util.*;
import com.fixmer.mared.commands.engine.*;
import com.fixmer.mared.commands.events_cmd.MaredWaitUntilCommand;
/** Runs compiled production classes on the real Minecraft/NeoForge classpath, without a game window. */
public class RuntimeIntegrationChecks {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static MaredScriptCommand cmd(java.util.function.BiConsumer<MaredScriptContext,MaredScriptExecutor> fn){return new MaredScriptCommand(){public void execute(MaredScriptContext c,MaredScriptExecutor e){fn.accept(c,e);}public String describe(){return "context-test";}};}
 public static void main(String[]args){
  net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(java.nio.file.Path.of(".").toAbsolutePath());
  var c=new MaredScriptContext(null,null,s->{});int[] count={0};
  c.registerFunction("next",List.of(),List.of(cmd((ctx,e)->e.setReturnValue((long)++count[0]))));
  check(c.substitute("${next()}").equals("1"),"First function substitution");
  check(c.substitute("${next()}").equals("2"),"No result cache for repeated call");
  c.setVariable("value",1L);check(c.substitute("$value").equals("1"),"Initial value");
  c.setVariable("value",2L);check(c.substitute("$value").equals("2"),"Same tick sees changed variable");
  c.registerFunction("wait",List.of("temporary"),List.of(new MaredWaitUntilCommand("false")));
  try{c.callFunction("wait",List.of(3L));throw new AssertionError("Synchronous wait accepted");}catch(IllegalStateException expected){checks++;}
  check(!c.hasVariable("temporary"),"Absent parameter removed after failure");
  c.setVariable("temporary",null);
  try{c.callFunction("wait",List.of(3L));throw new AssertionError("Synchronous wait accepted");}catch(IllegalStateException expected){checks++;}
  check(c.hasVariable("temporary")&&c.getVariable("temporary")==null,"Existing null binding restored");
  for(var params:List.of(List.of("same","same"),List.of("self"),List.of("global.x"))){
   try{c.registerFunction("bad",params,List.of());throw new AssertionError("Bad parameters accepted");}catch(IllegalArgumentException expected){checks++;}
  }
  var fork=c.forkFor(null,null);
  check(fork.getServer()==null&&fork.hasFunction("next"),"Fork keeps functions");
  check(!fork.hasVariable("temporary"),"Fork has independent variables");
  c.setVariable("a",1L);c.setVariable("b",2L);
  c.registerFunction("swap",List.of("a","b"),List.of(cmd((ctx,e)->check(ctx.getVariable("a").equals(2L)&&ctx.getVariable("b").equals(1L),"Arguments resolve before binding"))));
  var swap=new MaredScriptExecutor(c,List.of(new com.fixmer.mared.commands.control.MaredCallCommand("swap",List.of("$b","$a"))));
  for(int i=0;i<100&&!swap.isFinished();i++)swap.tick();
  check(swap.isFinished()&&swap.failure()==null&&c.getVariable("a").equals(1L)&&c.getVariable("b").equals(2L),"Actual call restores bindings");
  int[] hits={0};var wait=new MaredScriptExecutor(c,List.of(new MaredWaitUntilCommand("$ready"),cmd((ctx,e)->hits[0]++)));
  for(int i=0;i<4;i++)wait.tick();check(hits[0]==0&&!wait.isFinished(),"Actual context waits");
  c.setVariable("ready",true);for(int i=0;i<100&&!wait.isFinished();i++)wait.tick();check(hits[0]==1&&wait.isFinished(),"Actual context resumes");
  var guard=new MaredScriptExecutor(c,List.of(new com.fixmer.mared.commands.control.MaredIfCommand("true && )",List.of(cmd((ctx,e)->hits[0]++)))));
  guard.tick();check(guard.isFinished()&&guard.failure()!=null&&hits[0]==1,"Actual malformed guard stops");
  MaredScriptRunner.stopAll();var live=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->e.pauseCurrent(50))));
  var done=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->{})));MaredScriptRunner.start(live);MaredScriptRunner.start(done);MaredScriptRunner.tick(null);
  check(MaredScriptRunner.getActiveCount()==1&&!live.isFinished(),"Actual runner preserves survivor");MaredScriptRunner.stopAll();
  var owned=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->e.pauseCurrent(50)),cmd((ctx,e)->hits[0]++)));
  var sibling=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->e.pauseCurrent(50))));
  MaredScriptRunner.start(owned);MaredScriptRunner.start(sibling);MaredScriptRunner.tick(null);
  var run=new com.fixmer.mared.technology.runtime.FileRun("owned");run.reserveBlock();run.attach(owned);run.blockSubmitted();run.submissionDone();
  check(run.snapshot().state()==com.fixmer.mared.technology.runtime.FileRun.State.WAITING,"Run observes actual wait");
  run.requestStop();check(!owned.isFinished(),"UI stop does not mutate executor stack");
  MaredScriptRunner.tick(null);
  check(run.snapshot().state()==com.fixmer.mared.technology.runtime.FileRun.State.CANCELLED&&!sibling.isFinished()&&hits[0]==1,"Owned cancellation preserves sibling and skips following instruction");
  MaredScriptRunner.stopAll();
  var paused=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->hits[0]++)));paused.pauseFromDebugger();paused.requestStop();paused.tick();
  check(paused.isFinished()&&hits[0]==1,"Cancellation bypasses debugger pause");
  var failed=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->{throw new IllegalStateException("expected run error");})));
  var failedRun=new com.fixmer.mared.technology.runtime.FileRun("failed");failedRun.reserveBlock();failedRun.attach(failed);failedRun.blockSubmitted();failedRun.submissionDone();failed.tick();
  check(failedRun.snapshot().state()==com.fixmer.mared.technology.runtime.FileRun.State.FAILED,"Executor failure is retained in run state");
  String wrapped="{\n// } ignored\nmc data merge entity @s {Name:'a } // ; b', Nested:{x:1}};\n}";
  var parsed=MaredScriptParser.parse(wrapped);
  check(parsed.size()==1&&parsed.get(0) instanceof com.fixmer.mared.commands.server_cmd.MaredMcCommand,"Outer MR wrapper ignores quoted NBT and comment braces");
  var ownerA=new com.fixmer.genesis.technology.runtime.ExecutionScope();var ownerB=new com.fixmer.genesis.technology.runtime.ExecutionScope();
  var scoped=new MaredScriptContext(null,null,msg->{});scoped.setExecutionScope(ownerA);
  check(scoped.fork().executionScope()==ownerA&&scoped.forkWith(null).executionScope()==ownerA&&scoped.forkFor(null,null).executionScope()==ownerA,"Actual context forks preserve owner");
  try{scoped.setExecutionScope(ownerB);throw new AssertionError("Context owner replaced");}catch(IllegalStateException expected){checks++;}
  var scopedB=new MaredScriptContext(null,null,msg->{});scopedB.setExecutionScope(ownerB);
  com.fixmer.mared.commands.events.MaredEventRegistry.removeAllEvents();
  com.fixmer.mared.commands.events.MaredEventRegistry.register("owned_test",List.of(cmd((ctx,e)->hits[0]++)),scoped,true);
  com.fixmer.mared.commands.events.MaredEventRegistry.register("owned_test",List.of(cmd((ctx,e)->hits[0]++)),scopedB,true);
  check(com.fixmer.mared.commands.events.MaredEventRegistry.count("owned_test")==2,"Actual event replacement preserves another owner");
  ownerA.close();check(com.fixmer.mared.commands.events.MaredEventRegistry.count("owned_test")==1,"Actual owner cleanup removes only its event");
  com.fixmer.mared.commands.events.MaredEventRegistry.fire("owned_test",null,Map.of());MaredScriptRunner.tick(null);
  check(ownerB.snapshot().count(com.fixmer.genesis.technology.runtime.ExecutionScope.Kind.EXECUTOR)==0&&hits[0]==2,"Actual event fork inherits owner and completes child");
  new com.fixmer.mared.commands.events_cmd.MaredOffCommand("all").execute(scopedB);
  check(com.fixmer.mared.commands.events.MaredEventRegistry.totalCount()==0&&ownerB.snapshot().resources()==0,"Actual off releases owned resource lease");
  com.fixmer.mared.commands.events.MaredEventRegistry.register("exit_test",List.of(new com.fixmer.mared.commands.control.MaredExitCommand()),scopedB,false);
  com.fixmer.mared.commands.events.MaredEventRegistry.fire("exit_test",null,Map.of());MaredScriptRunner.tick(null);
  check(!com.fixmer.mared.commands.events.MaredEventRegistry.has("exit_test"),"Actual exit removes its queued event entry");
  var background=new com.fixmer.mared.technology.runtime.FileRun("background");var bgContext=new MaredScriptContext(null,null,msg->{});bgContext.setExecutionScope(background.scope());
  var bgRoot=new MaredScriptExecutor(bgContext,List.of(cmd((ctx,e)->com.fixmer.mared.commands.events.MaredEventRegistry.registerAfter(1,List.of(cmd((child,exec)->exec.pauseCurrent(20))),ctx))));
  background.reserveBlock();background.attach(bgRoot);background.blockSubmitted();background.submissionDone();bgRoot.tick();
  check(background.snapshot().state()==com.fixmer.mared.technology.runtime.FileRun.State.BACKGROUND,"Actual after keeps run alive beyond root completion");
  MaredScriptRunner.tick(null);background.requestStop();background.finishClientCancellation();
  check(background.snapshot().state()==com.fixmer.mared.technology.runtime.FileRun.State.CANCELLED&&MaredScriptRunner.getActiveCount()==0,"Actual background child acknowledges owned stop");
  ownerB.close();com.fixmer.mared.commands.events.MaredEventRegistry.removeAllEvents();MaredScriptRunner.stopAll();

  var linkedBase=new MaredScriptContext(null,null,line->{});
  linkedBase.setCapabilityHost(new com.fixmer.genesis.technology.links.InvocationCapabilities<MaredScriptContext>(){
   public boolean supports(String name){return name.equals("linked_probe");}
   public Object call(String name,List<Object> values,MaredScriptContext ctx){return 42L;}
  });
  for(var linkedFork:List.of(linkedBase.fork(),linkedBase.forkWith(null),linkedBase.forkFor(null,null)))
   check(com.fixmer.mared.commands.expr.MaredExpr.eval("linked_probe()",linkedFork).equals(42L),"Actual fork keeps explicit invocation capabilities");
  var badReturn=new MaredScriptExecutor(linkedBase,List.of(new com.fixmer.mared.commands.control.MaredReturnCommand("true && )")));
  badReturn.tick();check(badReturn.failure()!=null,"Actual return expression failure stops execution");
  System.out.println("Real classpath runtime checks passed: "+checks);
 }
}
