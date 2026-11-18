package com.fixmer.mared;
import java.util.*;import com.fixmer.mared.commands.engine.MaredScriptParser;
public final class ParserChecks144 {
 static int count;static void check(boolean b,String s){if(!b)throw new AssertionError(s);count++;}
 public static void main(String[]args){
  for(String invalid:List.of("if false\nprint yes","while false\nprint yes","repeat 2\nprint yes","func fn()\nprint yes","for $i = 1 to 2\nprint yes","once\nprint yes")){
   try{MaredScriptParser.parse(invalid);throw new AssertionError("missing block accepted: "+invalid);}catch(MaredScriptParser.ParseException expected){count++;}
  }
  check(MaredScriptParser.parse("on join\nprint yes").size()==2,"documented empty on declaration");
  check(MaredScriptParser.parse("if false {}\nprint yes").size()==2,"explicit empty body");
  check(MaredScriptParser.parse("if false\n{print yes}\nprint next").size()==2,"brace on next line");
  check(MaredScriptParser.parse("print one\rprint two").size()==2,"CR-only line endings");
  check(MaredScriptParser.parse("print one\r\nprint two").size()==2,"CRLF line endings");
  Locale old=Locale.getDefault();try{Locale.setDefault(Locale.forLanguageTag("tr-TR"));check(MaredScriptParser.parse("on TICK_CLIENT {}\nbind A HOLD {}").size()==2,"event names independent of locale");}finally{Locale.setDefault(old);}
  try{MaredScriptParser.parse("print one\rif false");throw new AssertionError("missing block");}catch(MaredScriptParser.ParseException expected){check(expected.line==2,"CR line number");}
  System.out.println("ParserChecks144: "+count+" passed");
 }
}
