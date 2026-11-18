package com.fixmer.mared;
import java.nio.file.*;import java.util.*;
import com.fixmer.mared.commands.engine.*;import com.fixmer.mared.commands.expr.*;import com.fixmer.mared.commands.server_cmd.*;import com.fixmer.mared.commands.events.*;import com.fixmer.mared.commands.actions.*;
import com.fixmer.mared.technology.runtime.*;import com.fixmer.mared.services.logging.*;import com.fixmer.mared.gui2.studio.events.*;import com.fixmer.mared.plugin.*;
import com.fixmer.genesis.technology.expression.*;import com.fixmer.genesis.technology.runtime.*;
import com.fixmer.mared.gui2.framework.overlay.*;import net.minecraft.client.gui.*;
public class RepairChecks142 {
 static int passed,failed;interface Work{void run()throws Exception;}
 static void check(String name,Work w){try{w.run();passed++;System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name+": "+e);}}
 static void ok(boolean b){if(!b)throw new AssertionError();}
 static void rejects(Runnable r){try{r.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException|ArithmeticException expected){}}
 static MaredScriptExecutor run(String code){var c=new MaredScriptContext(null,null,s->{});var e=new MaredScriptExecutor(c,MaredScriptParser.parse(code));for(int n=0;n<200&&!e.isFinished();n++)e.tick();return e;}
 static LogService log()throws Exception{var c=LogService.class.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
 static LogSettingsService filters()throws Exception{var c=LogSettingsService.class.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
 static class Ev implements StudioEvent{}
 static class Window implements Overlay{Runnable close=()->{};int opened,closed;public OverlayLayer layer(){return OverlayLayer.MODAL;}public void onOpen(){opened++;}public void onClose(){closed++;close.run();}public void render(GuiGraphics g,Font f,int w,int h,int x,int y){}}
 static FileRun assertion(boolean timer){var r=new FileRun("assert142");var c=new MaredScriptContext(null,null,r.journal()::add);c.setExecutionScope(r.scope());var e=new MaredScriptExecutor(c,MaredScriptParser.parse((timer?"after 10 { set global.bad142 = 1 }; ":"")+"assert false \"stop\""));r.reserveBlock();r.attach(e);r.blockSubmitted();r.submissionDone();for(int n=0;n<20&&!e.isFinished();n++)e.tick();return r;}
 public static void main(String[]args)throws Exception{
  Path root=Files.createTempDirectory("mared-repair142-");net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(root);Path config=root.resolve("config/mared");Files.createDirectories(config);
  check("repeat long cannot alias small count",()->ok(run("repeat -4294967295 { set x = 1 }").failure()!=null));
  check("wait NaN rejected",()->rejects(()->new MaredWaitCommand(Double.NaN,"ticks")));
  check("wait infinity rejected",()->rejects(()->new MaredWaitCommand(Double.POSITIVE_INFINITY,"ticks")));
  check("wait overflow rejected",()->rejects(()->new MaredWaitCommand(4294967296d,"ticks")));
  check("wait valid maximum preserved",()->ok(new MaredWaitCommand(Integer.MAX_VALUE,"ticks").getDelayTicks()==Integer.MAX_VALUE));
  check("wait unit typo rejected",()->rejects(()->new MaredWaitCommand(1,"tickss")));
  check("quoted scope preserved",()->{var c=(MaredSayCommand)MaredScriptParser.parse("say \"hello scope=self\"").get(0);ok(c.getText().equals("\"hello scope=self\"")&&c.getScope().equals("all"));});
  check("explicit scope parsed",()->{var c=(MaredSayCommand)MaredScriptParser.parse("say \"hello\" scope=self").get(0);ok(c.getText().equals("\"hello\"")&&c.getScope().equals("self"));});
  check("spaced assert expression valid",()->{var e=run("assert \"a\" + \"b\"; set after = 1");ok(e.failure()==null&&e.getContext().getVariable("after").equals(1L));});
  check("failed assert reports FAILED",()->ok(assertion(false).snapshot().state()==FileRun.State.FAILED));
  check("failed assert cancels timer",()->{MaredEventRegistry.removeAllEvents();var r=assertion(true);ok(r.snapshot().state()==FileRun.State.FAILED&&MaredEventRegistry.totalCount()==0);r.requestStop();MaredEventRegistry.removeAllEvents();});
  check("early save retains previous log",()->{var old=log();old.clear();old.add("old");old.save();var fresh=log();fresh.add("new");fresh.save();var disk=log();disk.ensureLoaded();ok(disk.size()==2);});
  check("Unicode log persists bounded tail",()->{var first=log();first.clear();var l=log();String text="漢".repeat(4096);int count=Integer.getInteger("mared.repair.logEntries",700);for(int i=0;i<count;i++)l.addStructured(LogSettings.Level.INFO,"run",text,"last"+i);l.save();var disk=log();disk.ensureLoaded();ok(disk.size()>1&&disk.snapshot().get(disk.size()-1).source.equals("last"+(count-1)));ok(Files.size(config.resolve("logs/current.log"))<=8*1024*1024);if(count==2000)ok(disk.snapshot().get(0).level==LogSettings.Level.WARN);});
  check("loop push failure restores variable",()->{var c=new MaredScriptContext(null,null,s->{});c.setVariable("i",99L);var e=new MaredScriptExecutor(c,MaredScriptParser.parse("for $i = 1 to 2 { set x = 1 }"),new ExecutionLimits(100,1000,1,2000000));e.tick();ok(e.failure()!=null&&c.getVariable("i").equals(99L));});
  check("default stack failure removes absent loop variable",()->{var e=run("func f(n) { if $n > 0 { call f($n - 1); return }; for $leaked = 1 to 2 { set x = 1 } }; call f(63)");ok(e.failure()!=null&&!e.getContext().hasVariable("leaked"));});
  check("dead boolean branch skips index conversion",()->ok(Boolean.FALSE.equals(MaredExpr.eval("false && [7][4294967296]",new MaredScriptContext(null,null,s->{})))));
  check("regex no match keeps valid result",()->{String text="abc".repeat(1000);ok(ValueLimits140.replaceAll(text,"missing","x".repeat(500)).equals(text));});
  check("regex groups retain JDK semantics",()->ok(ValueLimits140.replaceAll("ab ab","(a)(b)","$2$1").equals("ba ba")));
  check("event bus clear invalidates handle",()->{var bus=new StudioEventBus();var s=bus.subscribe(Ev.class,e->{});bus.clear();ok(!s.isActive());});
  check("event bus clear stops current snapshot",()->{var bus=new StudioEventBus();int[] n={0};bus.subscribe(Ev.class,e->bus.clear());bus.subscribe(Ev.class,e->n[0]++);bus.publish(new Ev());ok(n[0]==0);});
  check("describe failure cleans executor",()->{var scope=new ExecutionScope();var e=new FrameExecutor(List.of(new FrameExecutor.Instruction(){public void execute(FrameExecutor e){}public String describe(){throw new IllegalStateException("metadata fixture");}}),ExecutionLimits.DEFAULT,scope);e.tick();ok(e.isFinished()&&e.failure()!=null&&scope.snapshot().resources()==0);});
  check("slot long cannot alias hotbar",()->{try{rejects(()->new MaredSelectSlotCommand("4294967296").execute(new MaredScriptContext(null,null,s->{})));}finally{com.fixmer.mared.commands.input.MaredActionRegistry.stopAll();}});
  check("plugin unload survives registry mutation",()->{PluginRegistry.clear();int[] called={0};PluginRegistry.registerLoaded(new MaredPlugin(){public String id(){return "mutator142";}public void onUnload(){called[0]++;PluginRegistry.clear();}});PluginRegistry.registerLoaded(new MaredPlugin(){public String id(){return "next142";}public void onUnload(){called[0]++;}});PluginRegistry.unloadAll();ok(called[0]==2&&PluginRegistry.count()==0);});
  check("plugin unload logging tolerates bad identity",()->{PluginRegistry.clear();int[] called={0};PluginRegistry.registerLoaded(new MaredPlugin(){public String id(){throw new IllegalArgumentException("bad fixture id");}public void onUnload(){throw new IllegalStateException("fixture unload");}});PluginRegistry.registerLoaded(new MaredPlugin(){public String id(){return "after142";}public void onUnload(){called[0]++;}});PluginRegistry.unloadAll();ok(called[0]==1&&PluginRegistry.count()==0);});
  check("filters lazily read user choice",()->{Files.writeString(config.resolve("log_settings.json"),"{\"levels\":[\"ERROR\"],\"categories\":[\"run\"]}");var f=filters();ok(f.enabledLevels().equals(Set.of(LogSettings.Level.ERROR)));});
  check("filter mutation preserves disk choices",()->{Files.writeString(config.resolve("log_settings.json"),"{\"levels\":[\"ERROR\"],\"categories\":[\"run\"]}");var f=filters();f.toggleLevel(LogSettings.Level.INFO);ok(f.enabledCategories().equals(Set.of("run"))&&f.enabledLevels().equals(Set.of(LogSettings.Level.ERROR,LogSettings.Level.INFO)));});
  check("overlay duplicate does not open twice",()->{var m=new OverlayManager();var w=new Window();m.push(w);m.push(w);ok(m.size()==1&&w.opened==1);});
  check("overlay clear closes each instance once",()->{var m=new OverlayManager();var w=new Window();w.close=()->m.remove(w);m.push(w);m.clear();ok(w.closed==1&&m.isEmpty());});
  check("session reset retains unsaved first_join after IO refusal",()->{
   Path file=config.resolve("visited.json");Files.deleteIfExists(file);Files.createDirectory(file);
   var store=com.fixmer.mared.commands.storage.MaredGlobalStorage.class;
   try{
    com.fixmer.mared.commands.storage.MaredGlobalStorage.set("global.__first_join_retry142",true);
    com.fixmer.mared.commands.storage.MaredGlobalStorage.set("global.temporary142",42L);
    com.fixmer.mared.commands.storage.MaredGlobalStorage.resetSession140();
    ok(Boolean.TRUE.equals(com.fixmer.mared.commands.storage.MaredGlobalStorage.get("global.__first_join_retry142")));
    ok(!com.fixmer.mared.commands.storage.MaredGlobalStorage.has("global.temporary142"));
   }finally{Files.deleteIfExists(file);}
   com.fixmer.mared.commands.storage.MaredGlobalStorage.flush();
   com.fixmer.mared.commands.storage.MaredGlobalStorage.resetSession140();
   ok(Boolean.TRUE.equals(com.fixmer.mared.commands.storage.MaredGlobalStorage.get("global.__first_join_retry142")));
  });
  check("explicit language switch loads requested dictionary",()->{
   MaredLang.reload();MaredLang.setLanguage("en_us");ok(MaredLang.get("mared.dialog.cancel").equals("Cancel"));
   MaredLang.setLanguage("ru_ru");ok(MaredLang.get("mared.dialog.cancel").equals("Отмена"));MaredLang.setLanguage("en_us");
  });
  check("long history diff includes late changed line",()->{
   var a=new StringBuilder();var b=new StringBuilder();for(int n=0;n<1000;n++){if(n>0){a.append('\n');b.append('\n');}a.append("row"+n);b.append(n==900?"changed900":"row"+n);}
   var diff=com.fixmer.genesis.technology.workspace.live122.WorkspaceDiff126.compare(a.toString(),b.toString());
   ok(diff.lines().stream().anyMatch(l->l.kind()=='+'&&l.after()==901&&l.text().equals("changed900")));
  });
  check("identical long history has no fabricated changes",()->{
   String text="same\n".repeat(1000);var diff=com.fixmer.genesis.technology.workspace.live122.WorkspaceDiff126.compare(text,text);
   ok(diff.lines().stream().allMatch(l->l.kind()==' '));
  });
  check("compressed snapshot rejects trailing garbage",()->{
   byte[] data=java.util.Base64.getDecoder().decode(com.fixmer.genesis.technology.workspace.live122.WorkspaceProject126.pack(Map.of("a.mr","print 1")));
   String invalid=java.util.Base64.getEncoder().encodeToString(Arrays.copyOf(data,data.length+10));
   try{com.fixmer.genesis.technology.workspace.live122.WorkspaceProject126.unpack(invalid);throw new AssertionError("Trailing bytes accepted");}catch(java.io.IOException expected){}
  });
  check("compressed CRDT document rejects trailing garbage",()->{
   String value=com.fixmer.genesis.technology.workspace.live122.WorkspaceCodec122.document(new com.fixmer.genesis.technology.workspace.collaboration121.CollaborativeDocument121().snapshot());
   byte[] data=java.util.Base64.getDecoder().decode(value);
   String invalid=java.util.Base64.getEncoder().encodeToString(Arrays.copyOf(data,data.length+10));
   try{com.fixmer.genesis.technology.workspace.live122.WorkspaceCodec122.document(invalid);throw new AssertionError("Trailing document bytes accepted");}catch(java.io.IOException expected){}
  });
  System.out.println("Repair142 passed="+passed+" failed="+failed);
  try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
  if(failed>0)throw new AssertionError(failed+" failing regressions");
 }
}
