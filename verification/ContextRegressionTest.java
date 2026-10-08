import java.util.*;
import com.fixmer.mared.commands.engine.*;
import com.fixmer.mared.commands.events_cmd.MaredWaitUntilCommand;
/** Tests production context methods; the runner removes only Minecraft data-refresh methods. */
public class ContextRegressionTest {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static MaredScriptCommand cmd(java.util.function.BiConsumer<MaredScriptContext,MaredScriptExecutor> fn){return new MaredScriptCommand(){public void execute(MaredScriptContext c,MaredScriptExecutor e){fn.accept(c,e);}public String describe(){return "context-test";}};}
 public static void main(String[]args){
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
  var server=new net.minecraft.server.MinecraftServer();var fork=c.forkFor(null,server);
  check(fork.getServer()==server&&fork.hasFunction("next"),"Event fork binds server and keeps functions");
  check(!fork.hasVariable("temporary"),"Fork has independent variables");
  System.out.println("Production context method checks passed: "+checks);
 }
}
