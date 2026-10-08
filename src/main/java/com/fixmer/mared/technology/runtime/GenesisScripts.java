package com.fixmer.mared.technology.runtime;

import com.fixmer.genesis.technology.runtime.*;
import com.fixmer.mared.commands.engine.*;
import java.util.List;

/** MR host API for other mods. Call start on the context's game-owner thread with a fresh context. */
public final class GenesisScripts {
    private GenesisScripts() {}
    public record Program(List<MaredScriptCommand> commands){public Program{commands=List.copyOf(commands);}}
    public record Run(ExecutionScope scope,MaredScriptExecutor executor) implements AutoCloseable {
        /** Nonblocking cancellation; normal runner ticks acknowledge executor cleanup. */
        @Override public void close(){scope.close();}
        public FrameExecutor.Snapshot snapshot(){return executor.snapshot();}
    }
    /** MR only. Raw Minecraft command files belong to the connection-bound file adapter. */
    public static Program compile(String script){return new Program(MaredScriptParser.parse(script));}
    public static Run start(Program program,MaredScriptContext context,ExecutionLimits limits){
        java.util.Objects.requireNonNull(program);java.util.Objects.requireNonNull(context);java.util.Objects.requireNonNull(limits);
        if(context.executionScope()!=null)throw new IllegalArgumentException("Use a fresh context for each run");
        var scope=new ExecutionScope();context.setExecutionScope(scope);
        var executor=new MaredScriptExecutor(context,program.commands(),limits);
        MaredScriptRunner.start(executor);return new Run(scope,executor);
    }
    public static Run start(Program program,MaredScriptContext context){return start(program,context,ExecutionLimits.DEFAULT);}
}
