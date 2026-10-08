package com.fixmer.mared.technology.catalog;

import com.fixmer.genesis.technology.catalog.CommandTree;
import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;

/** Client-thread catalog. Root mutations are detected; nested mutations are periodically refreshed. */
public final class MinecraftCommandCatalog {
    private MinecraftCommandCatalog() {}
    public enum State { OFFLINE, WAITING, READY, ERROR }
    private static ClientPacketListener connection;
    private static CommandDispatcher<SharedSuggestionProvider> dispatcher;
    private static long stamp, structure, checkedAt;
    private static State state=State.OFFLINE;
    public static State state(){return state;}
    private static long rootStamp(CommandDispatcher<SharedSuggestionProvider> tree){
        long result=System.identityHashCode(tree.getRoot());
        for(var child:tree.getRoot().getChildren())result=31*result+System.identityHashCode(child)+child.getName().hashCode();
        return result;
    }
    public static void poll(ClientPacketListener next){update(next,false);}
    public static void refresh(ClientPacketListener next){update(next,true);}
    private static void update(ClientPacketListener next,boolean force){
        if(next==null){if(connection!=null||state!=State.OFFLINE)reset();return;}
        long now=System.nanoTime();
        boolean changedConnection=next!=connection;
        if(changedConnection){connection=next;dispatcher=null;state=State.WAITING;MaredCommandRegistry.install(CommandTree.empty());}
        try {
            var current=next.getCommands();
            long currentStamp=current==null?0:rootStamp(current);
            // Periodic rebuilding also covers nested changes in the same dispatcher. No per-frame tree copy.
            if(!force&&current==dispatcher&&currentStamp==stamp&&now-checkedAt<5_000_000_000L)return;
            if(!force&&state==State.ERROR&&now-checkedAt<1_000_000_000L)return;
            long nextStructure=current==null?0:BrigadierCatalog.structuralStamp(current);
            if(!force&&state!=State.ERROR&&current==dispatcher&&currentStamp==stamp&&nextStructure==structure){checkedAt=now;return;}
            var snapshot=current==null?CommandTree.empty():BrigadierCatalog.snapshot(current,MinecraftArgumentMetadata::describe);
            dispatcher=current;stamp=currentStamp;structure=nextStructure;checkedAt=now;
            state=snapshot.names().isEmpty()?State.WAITING:State.READY;
            MaredCommandRegistry.install(snapshot);
        }catch(RuntimeException|LinkageError error){state=State.ERROR;checkedAt=now;}
    }
    public static void reset(){connection=null;dispatcher=null;stamp=0;structure=0;checkedAt=0;state=State.OFFLINE;MaredCommandRegistry.install(CommandTree.empty());}
}
