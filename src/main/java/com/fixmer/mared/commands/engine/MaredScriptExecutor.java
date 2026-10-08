package com.fixmer.mared.commands.engine;

import com.fixmer.genesis.technology.runtime.FrameExecutor;
import com.fixmer.genesis.technology.runtime.ExecutionLimits;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.MaredSettings;
import java.util.List;

/** Minecraft context adapter. The frame machine and limits live in the reusable core. */
public class MaredScriptExecutor extends FrameExecutor {
    private final MaredScriptContext context;
    private final boolean verbose;
    public interface LoopOwner {
        boolean onBodyFinished(MaredScriptContext ctx,MaredScriptExecutor exec,Frame frame);
        String describe();
        default void onDiscard(MaredScriptContext ctx) {}
    }
    public MaredScriptExecutor(MaredScriptContext context,List<MaredScriptCommand> commands){this(context,commands,ExecutionLimits.DEFAULT);}
    public MaredScriptExecutor(MaredScriptContext context,List<MaredScriptCommand> commands,ExecutionLimits limits){
        super(limits,java.util.Objects.requireNonNull(context).executionScope());
        this.context=context;this.verbose=MaredSettings.isVerboseScriptLog();initialize(adapt(commands));
    }
    public MaredScriptContext getContext(){return context;}
    private List<Instruction> adapt(List<MaredScriptCommand> commands){
        if(commands==null)return List.of();
        return commands.stream().map(command -> (Instruction)new Instruction(){
            public void execute(FrameExecutor executor){command.execute(context,MaredScriptExecutor.this);}
            public int getDelayTicks(){return command.getDelayTicks();}
            public String describe(){return command.describe();}
        }).toList();
    }
    private FrameExecutor.LoopOwner adapt(LoopOwner owner){
        if(owner==null)return null;
        return new FrameExecutor.LoopOwner(){
            public boolean onBodyFinished(FrameExecutor exec,Frame frame){return owner.onBodyFinished(context,MaredScriptExecutor.this,frame);}
            public String describe(){return owner.describe();}
            public void onDiscard(){owner.onDiscard(context);}
        };
    }
    public Frame pushBody(List<MaredScriptCommand> body){return pushInstructions(adapt(body));}
    public Frame pushLoopBody(List<MaredScriptCommand> body,LoopOwner owner){return pushLoopInstructions(adapt(body),adapt(owner));}
    public Frame pushFunctionBody(List<MaredScriptCommand> body,LoopOwner owner){return pushFunctionInstructions(adapt(body),adapt(owner));}
    @Override protected void trace(String kind,Object value){
        if(kind.equals("error")){context.log("[error] "+((Throwable)value).getMessage());return;}
        if(verbose)context.log(value==null?MaredLang.get("mared.log.mared."+kind):MaredLang.format("mared.log.mared."+kind,value));
    }
}
