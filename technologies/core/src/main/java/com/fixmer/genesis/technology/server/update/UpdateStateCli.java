package com.fixmer.genesis.technology.server.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import static com.fixmer.genesis.technology.server.update.UpdateProtocol.*;

/** Local demo and read-only inspection. Does not launch, pause, patch or stop Minecraft. */
public final class UpdateStateCli {
    private UpdateStateCli() {}
    public static void main(String[] args){
        if(args.length!=2||!args[0].equals("demo")&&!args[0].equals("status")){
            System.err.println("Usage: UpdateStateCli demo <NEW-directory> | status <existing-directory>");System.exit(2);return;
        }
        try{
            Path directory=Path.of(args[1]);
            if(args[0].equals("status")){
                var report=UpdateJournal.inspect(directory);
                System.out.println("{\"report\":\"server_update_core_v35\",\"read_only\":true,\"retained_receipts\":"+report.retainedReceipts()+",\"unfinished_tail_bytes\":"+report.unfinishedTailBytes()+",\"state\":"+json(report.state())+"}");
            }else demo(directory);
        }catch(IOException|IllegalArgumentException bad){System.err.println("Update state error: "+bad.getMessage());System.exit(2);}
    }
    private static void demo(Path directory)throws IOException{
        UUID update=UUID.randomUUID();Command last=null;
        try(var journal=UpdateJournal.create(directory)){
            System.out.println("{\"demo\":true,\"world_modified\":false,\"message\":\"Protocol simulation only; milestone commands are not proof of real server work\"}");
            for(Action action:new Action[]{Action.BEGIN,Action.PACKAGE_VERIFIED,Action.OFFER,Action.QUIESCE,Action.CHECKPOINT_SAVED,Action.CONFIRM_SWITCH,Action.PROCESS_STARTED,Action.WORLD_READY,Action.OPEN_GAMEPLAY,Action.FINISH}){
                var state=journal.state();last=new Command(UUID.randomUUID(),state.epoch(),state.revision(),update,action,
                    action==Action.BEGIN?sha("Genesis update protocol demo".getBytes(StandardCharsets.UTF_8)):"","");
                print(journal.execute(last));
                if(action==Action.BEGIN)print(journal.execute(last));
            }
        }
        try(var reopened=UpdateJournal.open(directory)){
            System.out.println("{\"demo_reopened\":true,\"state\":"+json(reopened.state())+"}");
            print(reopened.execute(last));
        }
    }
    private static void print(Result result){System.out.println("{\"outcome\":\""+result.outcome()+"\",\"rejection\":\""+result.rejection()+"\",\"applied_revision\":"+result.appliedRevision()+",\"state\":"+json(result.state())+"}");}
    private static String json(State state){return "{\"protocol\":"+VERSION+",\"revision\":"+state.revision()+",\"epoch\":"+state.epoch()+",\"update_id\":\""+state.updateId()+"\",\"package_sha256\":\""+state.packageSha256()+"\",\"phase\":\""+state.phase()+"\",\"gameplay_opened\":"+state.gameplayOpened()+",\"failure_code\":\""+state.failureCode()+"\"}";}
}
