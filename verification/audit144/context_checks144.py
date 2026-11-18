from pathlib import Path
import tempfile
import subprocess
r=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="mared-audit144-context-") as temp:
    w=Path(temp)
    source=r/'src/main/java/com/fixmer/mared/commands';s=(source/'engine/MaredScriptContext.java').read_text();a=s.index('    public static Map<String,Object> playerSnapshot143(');z=s.index('    /** Private callback bindings',a);s=s[:a]+s[z:];a=s.index('    public void refreshPlayerData()');z=s.index('    public void refreshEventData(',a);s=s[:a]+s[z:];s=s.replace('import net.minecraft.server.level.ServerLevel;','').replace('import net.minecraft.world.entity.player.Player;','')
    stubs={
    'MaredScriptContext.java':s,
    'Mared.java':'package com.fixmer.mared;public class Mared { public static final String VERSION="fixture"; public static final Logger LOGGER=new Logger();public static class Logger{public void warn(String s,Object...a){}public void error(String s,Object...a){}}}',
    'MaredSettings.java':'package com.fixmer.mared;public class MaredSettings{public static boolean isVerboseScriptLog(){return false;}}',
    'MaredTicks.java':'package com.fixmer.mared;public class MaredTicks{public static long get(){return 0;}}',
    'MaredLang.java':'package com.fixmer.mared;public class MaredLang{public static String get(String s){return s;}public static String format(String s,Object...a){return s;}}',
    'ServerPlayer.java':'package net.minecraft.server.level;public class ServerPlayer{public Chain getName(){return new Chain();}public Chain level(){return new Chain();}public static class Chain{public String getString(){return "stub";}public Chain dimension(){return this;}public Chain location(){return this;}}}',
    'MinecraftServer.java':'package net.minecraft.server;public class MinecraftServer{}',
    'MaredEventRegistry.java':'package com.fixmer.mared.commands.events;public class MaredEventRegistry{public static class Entry{public String type="fixture";}public static Entry currentEntry(){return null;}public static boolean removeEntry(Entry e){return true;}}',
    'MaredGlobalStorage.java':'package com.fixmer.mared.commands.storage;public class MaredGlobalStorage{static final java.util.Map<String,Object> m=new java.util.HashMap<>();public static void set(String s,Object v){m.put(s,v);}public static Object get(String s){return m.get(s);}public static boolean has(String s){return m.containsKey(s);}public static void remove(String s){m.remove(s);}}',
    'MaredBuiltins.java':'package com.fixmer.mared.commands.expr;public class MaredBuiltins{public static Object call(String n,java.util.List<Object>a,com.fixmer.mared.commands.engine.MaredScriptContext c){throw new IllegalArgumentException(n);}}',
    'MaredDebugCommand.java':'package com.fixmer.mared.commands.server_cmd;public class MaredDebugCommand{}',
    'MaredActionRegistry.java':'''package com.fixmer.mared.commands.input;import com.fixmer.genesis.technology.runtime.ExecutionScope;
    public class MaredActionRegistry{public enum Action{FORWARD,BACK,LEFT,RIGHT,SNEAK,SPRINT,JUMP}public static final java.util.Set<Action> pressed=java.util.EnumSet.noneOf(Action.class);public static void press(ExecutionScope s,Action a){pressed.add(a);}public static void release(ExecutionScope s,Action a){pressed.remove(a);}public static boolean isPressed(ExecutionScope s,Action a){return pressed.contains(a);}public static void toggle(ExecutionScope s,Action a,boolean on){if(on)press(s,a);else release(s,a);}}'''
    }
    paths=list((r/'technologies/core/src/main/java').rglob('*.java'))
    paths+=[source/x for x in ['engine/MaredScriptCommand.java','engine/MaredScriptExecutor.java','expr/MaredExpr.java','expr/MaredMethods.java','actions/MaredMoveCommand.java','events_cmd/MaredWaitUntilCommand.java']]
    paths+=list((source/'control').glob('*.java'))
    for n,s in stubs.items():p=w/n;p.write_text(s);paths.append(p)
    paths += [r/'verification/ContextRegressionTest.java',r/'src/test/java/com/fixmer/mared/CommandChecks144.java']
    p=subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(w/'classes'),*map(str,paths)],text=True,capture_output=True);(w/'compile.log').write_text(p.stdout+p.stderr);print(p.stderr);p.check_returncode()
    for n in ['ContextRegressionTest','com.fixmer.mared.CommandChecks144']:
     p=subprocess.run(['java','-Xmx128m','-cp',str(w/'classes'),n],text=True,capture_output=True);(w/(n.split('.')[-1]+'.log')).write_text(p.stdout+p.stderr);print(p.stdout,p.stderr);p.check_returncode()
