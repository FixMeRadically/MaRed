package com.fixmer.genesis.technology.server.process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
/** Diagnostic child process. Does not load Minecraft, mods, saves or a network game port. */
public final class ServerProcessDemo {
    private ServerProcessDemo(){}
    public static void main(String[] args)throws Exception {
        System.out.println("Genesis demo process started. This is NOT a Minecraft server.");
        long exitAfter=args.length>0?Long.parseLong(args[0]):0;int exitCode=args.length>1?Integer.parseInt(args[1]):0;
        if(exitAfter>0){var timer=new Thread(()->{try{Thread.sleep(exitAfter);}catch(InterruptedException ignored){}System.out.println("Demo scheduled exit: "+exitCode);System.exit(exitCode);},"demo-exit");timer.setDaemon(true);timer.start();}
        var heartbeat=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"demo-heartbeat");t.setDaemon(true);return t;});heartbeat.scheduleAtFixedRate(()->System.out.println("Demo heartbeat; process is alive, game readiness is not checked."),1,2,TimeUnit.SECONDS);
        try(var in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null){if(line.equals("stop")){System.out.println("Demo received stop; graceful exit.");return;}}}
        finally{heartbeat.shutdownNow();}
    }
}
