"""Exercise the actual diagnostic method extracted from the handoff class; Minecraft and Gson are explicit shims."""
from pathlib import Path
import subprocess,tempfile
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--project',type=Path,default=Path(__file__).resolve().parents[2])
parser.add_argument('--classes',type=Path,required=True)
options=parser.parse_args();project=options.project;classes=options.classes
s=(project/'src/main/java/com/fixmer/mared/technology/server/setup/RemoteApplyService70.java').read_text()
method=s[s.index('    private static void fail166('):s.index('    private static Process launch(')]
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp);logger=p/'com/fixmer/mared/Mared.java';logger.parent.mkdir(parents=True)
 logger.write_text('''package com.fixmer.mared; public class Mared {public static Logger LOGGER=new Logger();public static String text="";public static class Logger{public void error(String format,Object...values){text=java.util.Arrays.toString(values);}}}''')
 driver='''public class Handoff166 {
 static boolean armIntent=true;static String phase="ARMED";static JsonObject state=new JsonObject();
 static class JsonObject extends java.util.HashMap<String,String>{JsonObject deepCopy(){JsonObject d=new JsonObject();d.putAll(this);return d;}void addProperty(String k,String v){put(k,v);}}
 '''+method+'''
 public static void main(String[] args){
 state.addProperty("reportDirectory","fixture-directory");JsonObject previous=state;
 fail166("REMOTE70_HANDOFF_FAILED",new IllegalStateException("SECRET_TOKEN"));
 if(armIntent||!phase.equals("FAILED")||state==previous||!state.get("reportDirectory").equals("fixture-directory")||!state.get("failureReason").equals("REMOTE70_HANDOFF_FAILED")||com.fixmer.mared.Mared.text.contains("SECRET_TOKEN"))throw new AssertionError();
 armIntent=true;fail166("REMOTE70_COMMAND_FAILED",new java.io.IOException("SECRET_PATH"));if(armIntent||!state.get("failureReason").equals("REMOTE70_COMMAND_FAILED")||com.fixmer.mared.Mared.text.contains("SECRET_PATH"))throw new AssertionError();
 fail166("REMOTE70_WORKER_EXIT",null);if(!state.get("failureReason").equals("REMOTE70_WORKER_EXIT"))throw new AssertionError();
 System.out.println("Handoff166: command failure disarms exit, report preserved, secrets omitted, unexpected worker exit diagnosed: PASS; extracted method with explicit logger/JSON shims.");
 }}'''
 (p/'Handoff166.java').write_text(driver)
 subprocess.run(['java','com.sun.tools.javac.Main','-d',str(p),str(logger),str(p/'Handoff166.java')],check=True)
 subprocess.run(['java','-cp',str(p),'Handoff166'],check=True)
