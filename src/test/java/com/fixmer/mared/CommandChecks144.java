package com.fixmer.mared;
import java.util.*;import com.fixmer.mared.commands.engine.*;import com.fixmer.mared.commands.actions.MaredMoveCommand;
public final class CommandChecks144 {
 static int count;static void check(boolean b,String s){if(!b)throw new AssertionError(s);count++;}
 public static void main(String[] args){
  Locale old=Locale.getDefault();
  try{Locale.setDefault(Locale.forLanguageTag("tr-TR"));check(new MaredMoveCommand("RIGHT","ON").describe().equals("move right on"),"RIGHT is locale independent");check(new MaredMoveCommand("SPRINT","OFF").describe().equals("move sprint off"),"SPRINT is locale independent");}finally{Locale.setDefault(old);}
  var c=new MaredScriptContext(null,null,s->{});String path="C:\\new\\test\\file";c.setVariable("path",path);check(c.substitute("$path").equals(path),"inserted path remains literal");
  c.setVariable("word","\\n\\t\\\\");check(c.substitute("value=${word}").equals("value=\\n\\t\\\\"),"expression result remains literal");
  check(c.substitute("first\\nsecond").equals("first\nsecond"),"template newline escape");
  check(c.substitute("\\$path").equals("$path"),"escaped dollar");
  c.setVariable("n",7L);check(c.substitute("\\\\$n").equals("\\7"),"escaped slash then substitution");
  c.registerFunction("path",List.of(),List.of(new MaredScriptCommand(){public boolean execute(MaredScriptContext ctx){return true;}public void execute(MaredScriptContext ctx,MaredScriptExecutor exec){exec.setReturnValue(path);}public String describe(){return "fixture path";}}));
  check(c.substitute("${path()}").equals(path),"function result remains literal");
  System.out.println("CommandChecks144: "+count+" passed");
 }
}
