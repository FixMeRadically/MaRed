package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static com.fixmer.genesis.technology.server.process.ServerNativeOwnerProtocol.*;

/** Optional real-Windows diagnostic entrypoint, shared by guardian --native-diagnostic. */
public final class ServerNativeOwnerCli {
    private ServerNativeOwnerCli(){}
    public static void main(String[] args){if(args.length!=1){System.err.println("Native diagnostic requires a helper executable");System.exit(2);return;}System.exit(run(Path.of(args[0])));}
    static int run(Path helper){
        try {
            var results=new LinkedHashMap<String,Object>();
            try(var session=ServerNativeOwnerSession.diagnostic(helper,Binding.fresh())){
                session.start();session.stop();session.awaitEmpty(10_000);session.finish();
                var snapshot=session.snapshot();if(!Boolean.TRUE.equals(snapshot.get("gracefulDiagnosticComplete")))throw new IOException("NATIVE_GRACEFUL_TEST_FAILED");results.put("graceful",true);
            }
            try(var session=ServerNativeOwnerSession.diagnostic(helper,Binding.fresh())){
                session.start();session.force(true);Event empty=session.awaitEmpty(10_000);session.finish();
                if(!empty.forced()||empty.exitCode()!=124||Boolean.TRUE.equals(session.snapshot().get("gracefulDiagnosticComplete")))throw new IOException("NATIVE_FORCE_TEST_FAILED");results.put("forced",true);
            }
            try(var session=ServerNativeOwnerSession.diagnostic(helper,Binding.fresh())){session.finish();if(Boolean.TRUE.equals(session.snapshot().get("gracefulDiagnosticComplete")))throw new IOException("NATIVE_EMPTY_CLOSE_TEST_FAILED");results.put("unused",true);}
            for(boolean eof:List.of(true,false)) {
                try(var session=ServerNativeOwnerSession.diagnostic(helper,Binding.fresh())){
                    Event started=session.start();ProcessHandle child=ProcessHandle.of(started.pid()).orElseThrow(()->new IOException("NATIVE_CHILD_OBSERVATION_FAILED"));
                    if(child.info().startInstant().isEmpty())throw new IOException("NATIVE_CHILD_IDENTITY_UNAVAILABLE");
                    if(eof)session.disconnectOwnerPipe();else session.close();
                    long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                    while(child.isAlive()&&System.nanoTime()<until)Thread.sleep(10);
                    if(child.isAlive()||Boolean.TRUE.equals(session.snapshot().get("gracefulDiagnosticComplete")))throw new IOException("NATIVE_LOST_OWNER_CHILD_REMAINS");
                    results.put(eof?"ownerPipeLoss":"ownerCrash",true);
                }
            }
            results.put("schema",44);results.put("all_passed",true);results.put("installAuthority",false);results.put("gameLaunched",false);
            System.out.println(ServerJson.object(results));return 0;
        }catch(Exception bad){String code=bad.getMessage()!=null&&bad.getMessage().matches("NATIVE_[A-Z_]+")?bad.getMessage():"NATIVE_DIAGNOSTIC_FAILED";System.err.println("Native owner diagnostic failed: "+code+" ("+bad.getClass().getSimpleName()+"; no install authority)");return 2;}
    }
}
