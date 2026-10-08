import java.util.*;
import com.fixmer.mared.commands.engine.*;
import com.fixmer.mared.commands.control.*;
import com.fixmer.mared.commands.events_cmd.*;
import com.fixmer.mared.commands.expr.MaredExpr;
public class RuntimeRegressionTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static MaredScriptCommand cmd(java.util.function.BiConsumer<MaredScriptContext,MaredScriptExecutor> fn){return new MaredScriptCommand(){public void execute(MaredScriptContext c,MaredScriptExecutor e){fn.accept(c,e);}public String describe(){return "test";}};}
 static void finish(MaredScriptExecutor e){for(int i=0;i<100&&!e.isFinished();i++)e.tick();check(e.isFinished(),"Finished");}
 public static void main(String[]args){
  var c=new MaredScriptContext();
  for(var entry:Map.of("-1 + 2",1L,"8 * 2 / 4",4L,"8 * 3 % 5",4L,"1 - 2 + 3",2L).entrySet())check(MaredExpr.eval(entry.getKey(),c).equals(entry.getValue()),"Precedence: "+entry.getKey());
  check(!MaredExpr.evalBool("false && probe()",c)&&c.calls==0,"AND short circuit");
  check(MaredExpr.evalBool("true || probe()",c)&&c.calls==0,"OR short circuit");
  check(!MaredExpr.evalBool("false && (null)()",c),"Skipped branch does not check runtime call type");
  check(MaredExpr.evalBool("false || probe()",c)&&c.calls==1,"Required call executes");
  for(String bad:List.of("true && )","true & false","1 @ 2","\"unterminated","(".repeat(140)+"1"+")".repeat(140),"!".repeat(140)+"true")) {
   try{MaredExpr.eval(bad,c);throw new AssertionError("Invalid expression accepted: "+bad);}catch(RuntimeException expected){checks++;}
  }
  c.setVariable("a",1L);c.setVariable("b",2L);
  c.funcs.put("swap",new MaredScriptContext.Func(List.of("a","b","absent"),List.of(cmd((ctx,e)->check(ctx.getVariable("a").equals(2L)&&ctx.getVariable("b").equals(1L),"Caller arguments")))));
  finish(new MaredScriptExecutor(c,List.of(new MaredCallCommand("swap",List.of("$b","$a")))));
  check(c.getVariable("a").equals(1L)&&c.getVariable("b").equals(2L)&&!c.hasVariable("absent"),"Restore presence and values");
  c.funcs.put("pause",new MaredScriptContext.Func(List.of("fresh"),List.of(cmd((ctx,e)->e.pauseCurrent(10)))));
  var paused=new MaredScriptExecutor(c,List.of(new MaredCallCommand("pause",List.of("7"))));paused.tick();check(c.hasVariable("fresh"),"Bound during function");paused.stopAll();check(!c.hasVariable("fresh"),"Unbound after cancellation");
  int[] hits={0};var instruction=new MaredWaitUntilCommand("$ready");
  var waiting=new MaredScriptExecutor(c,List.of(instruction,cmd((ctx,e)->hits[0]++)));
  for(int i=0;i<4;i++)waiting.tick();check(hits[0]==0&&!waiting.isFinished(),"Waiting retries");
  var other=new MaredScriptContext();other.setVariable("ready",true);var parallel=new MaredScriptExecutor(other,List.of(instruction));finish(parallel);
  check(!waiting.isFinished(),"Shared instruction has independent wait state");
  c.setVariable("ready",true);finish(waiting);check(hits[0]==1,"Resumed once");
  var bad=new MaredScriptExecutor(c,List.of(new MaredIfCommand("true && )",List.of(cmd((ctx,e)->hits[0]++)))));finish(bad);check(bad.failure()!=null&&hits[0]==1,"Malformed guard stops");
  c.funcs.put("breakfn",new MaredScriptContext.Func(List.of(),List.of(new MaredBreakCommand(),cmd((ctx,e)->hits[0]++))));finish(new MaredScriptExecutor(c,List.of(new MaredCallCommand("breakfn",List.of()))));check(hits[0]==2,"Break cannot escape function scope");
  c.funcs.put("recursive",new MaredScriptContext.Func(List.of(),List.of(new MaredCallCommand("recursive",List.of()))));var deep=new MaredScriptExecutor(c,List.of(new MaredCallCommand("recursive",List.of())));finish(deep);check(deep.failure()!=null&&deep.getStackDepth()==0,"Stack bounded and cleaned");
  check(MaredCallCommand.resolveArg("\"a\\n\"",c).equals("a\n"),"Escaped argument");
  var limits=new com.fixmer.genesis.technology.runtime.ExecutionLimits(2,4,8,1_000_000_000L);
  int[] count={0};var budget=new MaredScriptExecutor(c,Collections.nCopies(10,cmd((ctx,e)->count[0]++)),limits);
  budget.tick();check(count[0]==2&&!budget.isFinished(),"Per-tick step budget");finish(budget);check(count[0]==4&&budget.failure()!=null,"Lifetime instruction limit");
  MaredScriptRunner.stopAll();
  var live=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->e.pauseCurrent(50))));
  var done=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->{})));
  MaredScriptRunner.start(live);MaredScriptRunner.start(done);MaredScriptRunner.tick(null);
  check(MaredScriptRunner.getActiveCount()==1&&!live.isFinished(),"Earlier survivor stays active");
  MaredScriptRunner.start(live);check(MaredScriptRunner.getActiveCount()==1,"Duplicate not admitted");MaredScriptRunner.stopAll();
  var replacement=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->e.pauseCurrent(50))));
  var stopping=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->{MaredScriptRunner.stopAll();MaredScriptRunner.start(replacement);})));MaredScriptRunner.start(stopping);MaredScriptRunner.tick(null);
  check(MaredScriptRunner.getActiveCount()==1&&!replacement.isFinished(),"Reentrant stop and start preserved");MaredScriptRunner.stopAll();
  var server=new net.minecraft.server.MinecraftServer();var ownedContext=new MaredScriptContext();ownedContext.server=server;int[] ownedHits={0};
  var owned=new MaredScriptExecutor(ownedContext,List.of(cmd((ctx,e)->ownedHits[0]++)));MaredScriptRunner.start(owned);MaredScriptRunner.tick(null);check(ownedHits[0]==0,"Other server does not execute context");MaredScriptRunner.tick(server);check(ownedHits[0]==1,"Owning server executes context");MaredScriptRunner.stopAll();
  for(int i=0;i<100;i++)MaredScriptRunner.start(new MaredScriptExecutor(c,List.of(cmd((ctx,e)->e.pauseCurrent(50)))));
  var rejected=new MaredScriptExecutor(c,List.of(cmd((ctx,e)->{})));MaredScriptRunner.start(rejected);check(rejected.isFinished()&&MaredScriptRunner.getActiveCount()==100,"Capacity rejection cleans executor");MaredScriptRunner.stopAll();
  check(MaredScriptRunner.getActiveCount()==0,"Stop clears snapshot");
  var template=new MaredScriptContext();int[] eventHits={0};
  com.fixmer.mared.commands.events.MaredEventRegistry.register("regression",List.of(cmd((ctx,e)->{check(ctx.getServer()==server,"Event fork server");eventHits[0]++;})),template,false,false);
  com.fixmer.mared.commands.events.MaredEventRegistry.fire("regression",server,Map.of());MaredScriptRunner.tick(server);check(eventHits[0]==1,"Event with null template server executes");
  com.fixmer.mared.commands.events.MaredEventRegistry.removeAllEvents();MaredScriptRunner.stopAll();
  System.out.println("Runtime/expression checks passed: "+checks);
 }
}
