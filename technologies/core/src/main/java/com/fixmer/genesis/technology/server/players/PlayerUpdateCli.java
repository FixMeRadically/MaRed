package com.fixmer.genesis.technology.server.players;

import java.nio.file.*;
import java.util.*;
import static com.fixmer.genesis.technology.server.players.PlayerUpdateProtocol.*;

/** Inspection and deterministic offline rehearsal only. No arbitrary mutation endpoint or game commands. */
public final class PlayerUpdateCli {
    private PlayerUpdateCli() {}
    public static void main(String[] args) {
        try{
            if(args.length!=2)throw new IllegalArgumentException("Usage: status DIRECTORY | demo NEW_DIRECTORY");
            Path root=Path.of(args[1]);
            switch(args[0]){
                case "status" -> {var i=PlayerUpdateJournal.inspect(root);System.out.println(json(i.state(),true,i.unfinishedTailBytes()));}
                case "demo" -> demo(root);
                default -> throw new IllegalArgumentException("Only status and demo are supported");
            }
        }catch(Exception e){System.err.println("Player coordination: "+e.getMessage());System.exit(2);}
    }
    private static UUID id(long n){return new UUID(55,n);}
    private static void demo(Path root)throws Exception{
        var offers=new ArrayList<Offer>();for(int i=1;i<=18;i++)offers.add(new Offer(id(i),id(100+i),id(200+i)));
        var initial=start(id(500),"a".repeat(64),Policy.defaults(120000),0,offers);
        try(var journal=PlayerUpdateJournal.create(root,initial)){
            for(Offer o:offers)apply(journal,Command.bound(journal.state(),Action.ACCEPT,o.player(),1));
            apply(journal,Command.control(journal.state(),Action.FREEZE_COHORT,2));
            System.out.println(json(journal.state(),false,0));
            for(Offer o:offers)apply(journal,Command.bound(journal.state(),Action.DOWNLOADED,o.player(),3));
            State s=journal.state();UUID run=id(600);
            apply(journal,new Command(UUID.randomUUID(),s.epoch(),s.revision(),s.update(),4,Action.CONFIRM_SWITCH,NONE,NONE,NONE,"",run));
            for(int i=1;i<=5;i++){
                apply(journal,Command.bound(journal.state(),Action.REPORT_RUNTIME,id(i),5));
                apply(journal,Command.bound(journal.state(),Action.ACCEPT_RUNTIME,id(i),5));
            }
            System.out.println(json(journal.state(),false,0));
            s=journal.state();apply(journal,new Command(UUID.randomUUID(),s.epoch(),s.revision(),s.update(),6,Action.SERVER_READY,NONE,NONE,NONE,"",run));
            apply(journal,Command.control(journal.state(),Action.OPEN_GATE,7));
            apply(journal,Command.bound(journal.state(),Action.MARK_CONNECTED,id(1),8));
            System.out.println(json(journal.state(),false,0));
        }
    }
    private static void apply(PlayerUpdateJournal journal,Command c)throws Exception{Result r=journal.execute(c);if(!r.accepted())throw new IllegalStateException(r.reason());}
    private static String json(State s,boolean readOnly,int tail){
        return "{\"schema\":55,\"scope\":\"LOCAL_COORDINATION_MODEL\",\"read_only\":"+readOnly+",\"historical\":"+readOnly+",\"update\":"+quote(s.update().toString())+",\"revision\":"+s.revision()+",\"epoch\":"+s.epoch()+",\"phase\":"+quote(s.phase().name())+",\"paused\":"+s.paused()+",\"cohort_frozen\":"+s.frozen()+",\"cohort\":"+s.cohort().size()+",\"model_ready\":"+s.readyCount()+",\"required\":"+s.requiredCount()+",\"model_server_ready\":"+s.serverReady()+",\"model_gate_open\":"+s.gateOpen()+",\"unfinished_tail_bytes\":"+tail+",\"actual_clients_verified\":false,\"authorization_verified\":false,\"install_authority\":false,\"production_launch_allowed\":false,\"world_modified\":false}";
    }
    private static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
}
