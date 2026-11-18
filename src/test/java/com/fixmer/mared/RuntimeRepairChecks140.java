package com.fixmer.mared;

import java.util.*;
import java.nio.file.*;
import java.net.URLClassLoader;
import com.fixmer.mared.commands.engine.*;
import com.fixmer.mared.commands.control.*;
import com.fixmer.mared.commands.expr.*;
import com.fixmer.mared.gui2.framework.overlay.*;
import com.fixmer.mared.gui2.framework.components.*;
import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.gui2.studio.panels.workspace.*;
import com.fixmer.mared.services.logging.*;
import com.fixmer.mared.technology.runtime.*;
import com.fixmer.mared.plugin.*;
import net.minecraft.client.gui.*;

/** Runtime/UI models on the real classpath. Does not render Minecraft or claim Windows acceptance. */
public final class RuntimeRepairChecks140 {
    static int checks;static boolean healthyConstructed;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void rejects(Runnable fn){checks++;try{fn.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|ArithmeticException expected){}}
    static MaredScriptContext run(String script){var c=new MaredScriptContext(null,null,s->{});var e=new MaredScriptExecutor(c,MaredScriptParser.parse(script));for(int n=0;n<200&&!e.isFinished();n++)e.tick();if(e.failure()!=null)throw new AssertionError("Script failed: "+script,e.failure());ok(e.isFinished(),"script completed");return c;}
    static LogService log()throws Exception{var c=LogService.class.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
    static class Window implements Overlay {
        final String name;final List<String> events;final OverlayLayer layer;final boolean escape;
        Window(String n,List<String> events,OverlayLayer l,boolean escape){name=n;this.events=events;layer=l;this.escape=escape;}
        public OverlayLayer layer(){return layer;}public boolean closeOnEscape(){return escape;}public boolean isInputBarrier(){return true;}
        public void render(GuiGraphics g,Font f,int w,int h,int x,int y){}public boolean mouseClicked(double x,double y,int b){events.add(name);return true;}public void onClose(){events.add("close "+name);}
    }
    public static class BrokenProvider implements MaredPlugin{public BrokenProvider(){throw new IllegalStateException("Expected broken-provider fixture140");}public String id(){return "broken140";}}
    public static class HealthyProvider implements MaredPlugin{public HealthyProvider(){healthyConstructed=true;}public String id(){return "healthy140";}public void onLoad(PluginContext ctx){ctx.registerAction(com.fixmer.mared.gui2.studio.action.EditorAction.of("healthy140","healthy140",c->{}));}}
    public static class PartialProvider implements MaredPlugin{public String id(){return "partial140";}public void onLoad(PluginContext ctx){ctx.registerModule(new com.fixmer.mared.modules.ModuleDescriptor("partial140"));throw new IllegalStateException("Expected partial-provider fixture140");}}
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("mared-runtime140-");net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(temp);
        try{
            ok(run("func echo(s) { return $s }; call echo(\"a,b\")").getVariable("__last_return").equals("a,b"),"quoted call argument");
            ok(run("func echo(s) { return $s }; set out = call echo(min(1,2))").getVariable("out").equals(1L),"nested call argument");
            ok(run("func echo(s) { return $s }; set out = call echo([1,2])").getVariable("out").equals(List.of(1L,2L)),"list call argument");
            ok(run("set a = 10; set b = 20; func f(a,b) { return [$a,$b] }; set out = call f($b,$a)").getVariable("out").equals(List.of(20L,10L)),"arguments evaluated before binding");
            var parameters=run("func f(newParam) { return $newParam }; set out = call f(3)");ok(!parameters.hasVariable("newParam"),"absent function parameter restored");
            ok(run("set out = 'a;b'").getVariable("out").equals("a;b"),"single quote semicolon");ok(run("set out = 'http://site'").getVariable("out").equals("http://site"),"single quote slashes");
            ok(run("func f() { return \"a\" + \"b\" }; set out = call f()").getVariable("out").equals("ab"),"return concatenation");
            var loop=run("set i = 99; set count = 0; for $i = 2147483647 to 2147483647 { set count = $count + 1 }");ok(loop.getVariable("count").equals(1L)&&loop.getVariable("i").equals(99L),"boundary loop and binding");
            var absent=run("array items = [1]; for $i in $items { set out = $i }");ok(!absent.hasVariable("i"),"absent loop variable removed");
            var c=new MaredScriptContext(null,null,s->{});c.setVariable("i",99L);var e=new MaredScriptExecutor(c,MaredScriptParser.parse("for $i = 1 to 5 { wait 2 seconds }"));e.tick();e.stopAll();ok(c.getVariable("i").equals(99L),"cancelled loop restored");
            var functionCancel=new MaredScriptContext(null,null,s->{});functionCancel.setVariable("a",7L);functionCancel.registerFunction("f",List.of("a"),MaredScriptParser.parse("wait 2 seconds"));var cancel=new MaredScriptExecutor(functionCancel,MaredScriptParser.parse("set out = call f(2)"));cancel.tick();cancel.stopAll();ok(functionCancel.getVariable("a").equals(7L),"cancelled function restored");
            rejects(()->MaredMethods.call(List.of(1L),"filter",List.of("unknown_function()")));
            ok(MaredMethods.call("abc","sub",List.of(100L)).equals(""),"string sub clamp");ok(MaredMethods.call(List.of(1L),"slice",List.of(100L)).equals(List.of()),"slice clamp");
            ok(((List<?>)MaredMethods.call(List.of(1L,2L),"shuffled",List.of())).size()==2,"documented shuffled");
            var range=MaredBuiltins.call("range",List.of(0L),null);MaredMethods.call(range,"push",List.of(9L));ok(range.equals(List.of(9L)),"mutable empty range");
            rejects(()->MaredBuiltins.call("range",List.of(1000001L),null));rejects(()->MaredBuiltins.call("repeat",List.of("x",2147483647L),null));
            for(int n=0;n<50;n++){long value=(long)MaredBuiltins.call("random",List.of(Long.MAX_VALUE-1,Long.MAX_VALUE),null);ok(value>=Long.MAX_VALUE-1,"random inclusive max");}
            var mixed=new ArrayList<Object>(List.of(2L,10L,"15",3L,"20"));MaredMethods.call(mixed,"sort",List.of());ok(mixed.equals(List.of(2L,3L,10L,"15","20")),"total mixed order");
            var self=new ArrayList<Object>();rejects(()->MaredMethods.call(self,"push",List.of(self)));
            var journal=new RunJournal();journal.add("a");journal.clear();ok(journal.dropped()==0,"journal clear not overflow");
            var first=log();first.addStructured(LogSettings.Level.ERROR,"run","first","source140");first.save();first.add("second");first.save();var restored=log();restored.ensureLoaded();ok(restored.snapshot().size()==2,"log repeated save");ok(restored.snapshot().get(0).level==LogSettings.Level.ERROR&&"source140".equals(restored.snapshot().get(0).source),"structured log roundtrip");
            var early=log();early.add("session");early.ensureLoaded();ok(early.snapshot().size()==3,"early entry retains disk restore");first.clear();var cleared=log();cleared.ensureLoaded();ok(cleared.size()==0,"log clear persisted");ok(LogSettingsService.get().shouldShow("[print] text"),"print category visible");
            var doc=new DocumentSession();doc.storageId="a";doc.setInitialText("a\r\nb");doc.recomputeDirty();ok(!doc.dirty,"CRLF initial state clean");
            var events=new ArrayList<String>();var manager=new OverlayManager();manager.push(new Window("older",events,OverlayLayer.MODAL,true));manager.push(new Window("newer",events,OverlayLayer.MODAL,true));manager.mouseClicked(1,1,0);ok(events.equals(List.of("newer")),"topmost receives input");
            events.clear();manager=new OverlayManager();manager.push(new Window("behind",events,OverlayLayer.MODAL,true));manager.push(new Window("locked",events,OverlayLayer.POPUP,false));manager.keyPressed(256,0,0);ok(events.isEmpty()&&manager.size()==2,"escape respects barrier");
            var menu=new ContextMenuOverlay(0,0,List.of(ContextMenuEntry.of("ab",()->{})));menu.keyPressed(65,0,0);menu.charTyped('a',0);menu.keyPressed(66,0,0);menu.charTyped('b',0);var buffer=ContextMenuOverlay.class.getDeclaredField("searchBuffer");buffer.setAccessible(true);ok(buffer.get(menu).toString().equals("ab"),"menu accumulates prefix");
            var list=new MaredList();list.layout(new MaredBounds(0,0,100,100));list.add("first");ok(!list.mouseClicked(5,2,0)&&list.selected()==null,"list padding not row");
            var container=new MaredContainer();container.addChild(new MaredPanel());ok(container.children().size()==1,"container addChild uses actual tree");
            var dialog=new NameDialogOverlay("test",name->{},0,false,name->{throw new IllegalArgumentException("Invalid fixture path");}).customInput().initialValue("name");var onOk=NameDialogOverlay.class.getDeclaredMethod("onOk");onOk.setAccessible(true);onOk.invoke(dialog);ok(!dialog.consumeCloseRequest(),"invalid validator retained dialog");
            ok(CommandFragment.extract("say one\nsay two",0,0,1,0).strip().equals("say one"),"selected newline belongs to preceding command");
            var once=run("set a = 0; set b = 0; once { repeat 1 { set global.a140 = 1 } }; once { repeat 1 { set global.b140 = 1 } }");ok(once.getVariable("global.a140").equals(1L)&&once.getVariable("global.b140").equals(1L),"distinct nested once blocks");
            com.fixmer.mared.commands.storage.MaredGlobalStorage.set("global.__first_join_140",true);com.fixmer.mared.commands.storage.MaredGlobalStorage.resetSession140();ok(Boolean.TRUE.equals(com.fixmer.mared.commands.storage.MaredGlobalStorage.get("global.__first_join_140")),"first join survives session reset");
            Path services=temp.resolve("services/META-INF/services");Files.createDirectories(services);Files.writeString(services.resolve(MaredPlugin.class.getName()),BrokenProvider.class.getName()+"\n"+HealthyProvider.class.getName()+"\n"+PartialProvider.class.getName()+"\n");ClassLoader before=Thread.currentThread().getContextClassLoader();
            try(var loader=new URLClassLoader(new java.net.URL[]{temp.resolve("services").toUri().toURL()},before)){
                Thread.currentThread().setContextClassLoader(loader);PluginRegistry.clear();PluginLoader.reset();PluginLoader.loadAll();ok(healthyConstructed&&PluginRegistry.count()==1,"bad provider does not stop healthy plugin");ok(com.fixmer.mared.modules.ModuleRegistry.all().stream().noneMatch(m->m.id().equals("partial140")),"failed onLoad leaves no module");ok(PluginRegistry.pendingActionCount()==1,"only successful plugin action staged");PluginRegistry.unloadAll();ok(PluginRegistry.pendingActionCount()==0,"unload clears deferred actions");
            }finally{Thread.currentThread().setContextClassLoader(before);PluginRegistry.clear();PluginLoader.reset();}
            System.out.println("RuntimeRepair140 checks passed="+checks);
        }finally{try(var paths=Files.walk(temp)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
