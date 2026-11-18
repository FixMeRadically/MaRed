package com.fixmer.genesis.technology.expression;

import java.nio.file.*;
import java.util.*;
import com.fixmer.genesis.technology.runtime.*;
import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.workspace.live122.*;

/** Real production regressions, isolated files; no game or Windows simulation. */
public final class ValueRepairChecks140 {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void rejects(Runnable work){checks++;try{work.run();throw new AssertionError("Expected bounded rejection");}catch(IllegalArgumentException|ArithmeticException expected){}}
    private static final ExpressionEnvironment ENV=new ExpressionEnvironment(){
        public Object getVariable(String name){return null;}public boolean hasVariable(String name){return false;}public boolean hasFunction(String name){return false;}
        public Object callFunction(String n,List<Object>a){throw new IllegalArgumentException(n);}public Object callBuiltin(String n,List<Object>a){throw new IllegalArgumentException(n);}public Object callMethod(Object v,String n,List<Object>a){throw new IllegalArgumentException(n);}
    };
    public static void main(String[] args)throws Exception{
        ok(ExpressionEngine.eval("-9223372036854775808",ENV).equals(Long.MIN_VALUE),"minimum long");
        rejects(()->ExpressionEngine.eval("9223372036854775808",ENV));rejects(()->ExpressionEngine.eval("999999999999999999999999",ENV));
        ok(ExpressionEngine.eval("9007199254740993 > 9007199254740992",ENV).equals(true),"exact long order");
        ok(ExpressionEngine.addValues(1.5f,2.5f).equals(4.0),"float arithmetic");
        rejects(()->ExpressionEngine.eval("[7,8][4294967296]",ENV));
        ok(ExpressionEngine.eval("'a;b'",ENV).equals("a;b"),"single quoted expression");
        ok(ValueLimits140.arguments("\"a,b\", min(1,2), [1,2]").equals(List.of("\"a,b\"","min(1,2)","[1,2]")),"top-level arguments");
        rejects(()->ValueLimits140.arguments("1,,2"));rejects(()->ValueLimits140.arguments("[1,2"));
        rejects(()->ValueLimits140.repeat("x",Integer.MAX_VALUE));rejects(()->ValueLimits140.listSize(1000001));
        rejects(()->ValueLimits140.format("%2147483647s",new Object[]{"x"}));
        ok(ValueLimits140.format("%04d",new Object[]{7}).equals("0007"),"bounded formatter remains compatible");
        ok(ValueLimits140.matches("aaab","a+b"),"normal regex");
        long started=System.nanoTime();rejects(()->ValueLimits140.matches("a".repeat(30000)+"!","(a+)+$"));ok(System.nanoTime()-started<2_000_000_000L,"regex bounded promptly");
        ok(ValueLimits140.replace("abcabc","a","x").equals("xbcxbc"),"literal replace");
        rejects(()->ValueLimits140.replace("a".repeat(2000),"a","b".repeat(2000)));
        var cyclic=new ArrayList<Object>();cyclic.add(cyclic);rejects(()->ExpressionEngine.stringify(cyclic));
        var target=new ArrayList<Object>();rejects(()->ValueLimits140.insertion(target,target));
        var frame=new FrameExecutor(List.of(new FrameExecutor.Instruction(){public void execute(FrameExecutor e){}public int getDelayTicks(){throw new IllegalStateException("fixture delay");}}),ExecutionLimits.DEFAULT,null);
        frame.tick();ok(frame.isFinished()&&frame.failure() instanceof IllegalStateException,"delay failure recorded");
        Path root=Files.createTempDirectory("mared-core140-");
        try{
            Path project=root.resolve("project");Files.createDirectories(project.resolve("src/build"));Path source=project.resolve("src/build/A.java");Files.writeString(source,"class A{}");String before=BuildInputs.capture(project,()->false);Files.writeString(source,"class A{int x;}");ok(!before.equals(BuildInputs.capture(project,()->false)),"source folder named build counted");
            before=BuildInputs.capture(project,()->false);Files.createDirectories(project.resolve(".genesis/trash"));Files.writeString(project.resolve(".genesis/trash/old.txt"),"old");ok(before.equals(BuildInputs.capture(project,()->false)),"trash excluded");
            var sources=new SourceProjects(root.resolve("sources"));sources.createEmpty("Empty");ok(new WorkspaceScripts129(sources.root(),root.resolve("bindings")).snapshot("Empty").isEmpty(),"empty shared project");
            ok(WorkspaceProject126.unpack(WorkspaceProject126.pack(Map.of())).isEmpty(),"empty text snapshot roundtrip");
            Path imported=root.resolve("commands");Files.createDirectories(imported);Files.writeString(imported.resolve("main.mcfunction"),"say hello");Files.writeString(imported.resolve("extra.mr"),"{ print \"hello\" }");ok(WorkspaceProject126.scan(imported).files().size()==2,"Commands import extensions");
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("ValueRepair140 checks passed="+checks);
    }
}
