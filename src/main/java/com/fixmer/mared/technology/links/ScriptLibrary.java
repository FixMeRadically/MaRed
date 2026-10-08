package com.fixmer.mared.technology.links;
import com.fixmer.genesis.technology.links.*;
import com.fixmer.genesis.technology.links.ScriptLinks.Binding;
import com.fixmer.genesis.technology.links.ScriptExports.Kind;
import com.fixmer.genesis.technology.runtime.*;
import com.fixmer.mared.commands.engine.*;
import com.fixmer.mared.commands.expr.MaredExpr;
import java.util.*;
/** Invocation API shared by AI and other mods. Use the context's owner thread. */
public final class ScriptLibrary {
    private ScriptLibrary() {
    }
    public record Target(Map<String,MaredScriptContext.Func> functions,String function) {
        public Target {
            functions=Map.copyOf(functions);
        }
    }
    public record Action(ExecutionScope scope,MaredScriptExecutor executor) implements LinkedBehavior.Action {
        public boolean done() {
            return executor.snapshot().finished()&&scope.snapshot().resources()==0;
        }
        public Throwable failure() {
            return scope.snapshot().failure()!=null?scope.snapshot().failure():executor.failure();
        }
        public void close() {
            scope.close();
        }
    }
    private record Invocation(MaredScriptContext context,MaredScriptContext.Func function) {
    }
    private static Invocation prepare(CapabilityRegistry.Snapshot<Target> registry,Binding binding,MaredScriptContext base,ExecutionLimits limits) {
        if(base.getServer()!=null&&!base.getServer().isSameThread())throw new IllegalStateException("Invoke linked functions on the server owner thread");
        String error=registry.problem(binding);
        if(error!=null)throw new IllegalArgumentException(error);
        if(base.executionScope()!=null)throw new IllegalArgumentException("Linked invocations need a fresh unowned base context");
        var entry=registry.resolve(binding.reference());
        var values=new ArrayList<Object>();
        for(String param:entry.symbol().parameters())values.add(MaredExpr.eval(binding.arguments().get(param),base));
        var context=base.fork();
        context.setEventEntry(null);
        context.setInvocationLimits(limits);
        context.setExecutionScope(new ExecutionScope());
        try {
            entry.target().functions().forEach((name,fn)->context.registerFunction(name,fn.params,fn.body));
            var fn=context.getFunction(entry.target().function());
            if(fn==null)throw new IllegalArgumentException("Export target function missing");
            for(int i=0;i<fn.params.size();i++)context.setVariable(fn.params.get(i),values.get(i));
            return new Invocation(context,fn);
        }
        catch(RuntimeException failure) {
            context.executionScope().close();
            throw failure;
        }
    }
    public static boolean condition(CapabilityRegistry.Snapshot<Target> registry,Binding binding,MaredScriptContext base,ExecutionLimits limits) {
        if(binding.reference().kind()!=Kind.CONDITION)throw new IllegalArgumentException("Expected condition");
        var invocation=prepare(registry,binding,base,limits);
        var context=invocation.context();
        try {
            var values=new ArrayList<Object>();
            for(String p:invocation.function().params)values.add(context.getVariable(p));
            Object result=context.callFunction(registry.resolve(binding.reference()).target().function(),values);
            if(!(result instanceof Boolean value))throw new IllegalStateException("Condition must return boolean");
            return value;
        }
        finally {
            context.executionScope().close();
        }
    }
    public static Action action(CapabilityRegistry.Snapshot<Target> registry,Binding binding,MaredScriptContext base,ExecutionLimits limits) {
        if(binding.reference().kind()!=Kind.ACTION)throw new IllegalArgumentException("Expected action");
        var invocation=prepare(registry,binding,base,limits);
        var context=invocation.context();
        try {
            var executor=new MaredScriptExecutor(context,invocation.function().body,limits);
            var frame=executor.peekTopFrame();
            if(frame!=null)frame.functionCall=true;
            MaredScriptRunner.start(executor);
            return new Action(context.executionScope(),executor);
        }
        catch(RuntimeException error) {
            context.executionScope().fail(error);
            throw error;
        }
    }
}
