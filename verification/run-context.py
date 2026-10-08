"""Test unchanged production context methods; strip Minecraft player-data refresh only.
This is a source-extraction harness, not compilation of the complete Minecraft mod.
"""
from pathlib import Path
import os, tempfile, subprocess
root=Path(__file__).resolve().parents[1]
jdk=os.environ.get('JAVA_HOME')
java=str(Path(jdk)/'bin/java') if jdk else 'java'
javac=str(Path(jdk)/'bin/javac') if jdk else 'javac'
source=root/'src/main/java/com/fixmer/mared/commands'
paths=list((root/'technologies/core/src/main/java').rglob('*.java'))
paths += [p for p in (root/'verification/stubs').rglob('*.java') if p.name not in ['MaredScriptContext.java','ServerPlayer.java']]
paths += [source/p for p in ['engine/MaredScriptCommand.java','engine/MaredScriptExecutor.java','control/MaredIfCommand.java','expr/MaredExpr.java','expr/MaredMethods.java','events_cmd/MaredWaitUntilCommand.java']]
# EventRegistry is not part of this context harness; the other harness compiles it directly.
paths += [root/'verification/ContextRegressionTest.java']
with tempfile.TemporaryDirectory() as temp:
    work=Path(temp)
    context=(source/'engine/MaredScriptContext.java').read_text()
    start=context.index('    public void refreshPlayerData()')
    end=context.index('    public void refreshEventData(')
    context=context[:start]+context[end:]
    context=context.replace('import net.minecraft.server.level.ServerLevel;','')
    extras={
      'MaredScriptContext.java':context,
      'ServerPlayer.java':'package net.minecraft.server.level; public class ServerPlayer { public Chain getName(){return new Chain();} public Chain level(){return new Chain();} public static class Chain { public String getString(){return "stub";} public Chain dimension(){return this;} public Chain location(){return this;} } }',
      'MaredTicks.java':'package com.fixmer.mared; public class MaredTicks { public static long get(){return 0;} }',
      'MaredGlobalStorage.java':'package com.fixmer.mared.commands.storage; public class MaredGlobalStorage { private static final java.util.Map<String,Object> values=new java.util.HashMap<>(); public static void set(String n,Object v){values.put(n,v);} public static Object get(String n){return values.get(n);} public static boolean has(String n){return values.containsKey(n);} public static void remove(String n){values.remove(n);} }',
      'MaredBuiltins.java':'package com.fixmer.mared.commands.expr; public class MaredBuiltins { public static Object call(String n,java.util.List<Object> args,com.fixmer.mared.commands.engine.MaredScriptContext c){throw new IllegalArgumentException("Unknown builtin: "+n);} }'
    }
    for name,text in extras.items():
        p=work/name;p.write_text(text);paths.append(p)
    classes=work/'classes';classes.mkdir()
    subprocess.run([javac,'-encoding','UTF-8','-d',str(classes),*map(str,paths)],check=True)
    subprocess.run([java,'-cp',str(classes),'ContextRegressionTest'],check=True)
