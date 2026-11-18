package com.fixmer.genesis.technology.server.remote;

import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Portable host entry. Controls/report do not deploy a mod or invoke a Python launcher. */
public final class ServerLauncher104 {
    private ServerLauncher104() {}
    public static void main(String[] args) throws Exception {
        Path initial=Path.of("").toAbsolutePath();
        try{Path own=Path.of(ServerLauncher104.class.getProtectionDomain().getCodeSource().getLocation().toURI());if(Files.isRegularFile(own)&&Files.isDirectory(own.getParent().resolve("libraries")))initial=own.getParent();}catch(Exception ignored){}
        if(args.length==0&&!GraphicsEnvironment.isHeadless()){Path folder=initial;SwingUtilities.invokeLater(()->ServerHost105.show(folder,java(),ServerLauncher104::deploy));return;}
        if(args.length>2)throw new IllegalArgumentException("start/status/restart/stop/report/acknowledge <server folder>");
        String action=args.length==0?"start":args[0];Path server=args.length>1?Path.of(args[1]).toAbsolutePath():initial;
        if(!Set.of("start","status","restart","stop","report","acknowledge").contains(action))throw new IllegalArgumentException("start/status/restart/stop/report/acknowledge <server folder>");
        switch(action){
            case "status" -> System.out.println(ServerLocal105.observe(server));
            case "restart","stop" -> {ServerLocal105.command(server,action);System.out.println("QUEUED");}
            case "report" -> System.out.println(ServerDiagnostics105.export(server));
            case "acknowledge" -> {ServerLocal105.acknowledge(server);System.out.println("ACKNOWLEDGED");}
            default -> {Path app=deploy(server),output=ServerApp104.root(server).resolve("logs/launcher.log");Files.createDirectories(output.getParent());Process process=ServerApp104.helper(app,"start",server,java(),output);if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroy();throw new IOException("SERVER104_HELPER_TIMEOUT");}System.out.println(Files.readString(output));if(process.exitValue()!=0)System.exit(2);}
        }
    }
    private static Path java(){return Path.of(System.getProperty("java.home"),"bin","java.exe");}
    private static Path deploy(Path server)throws IOException{return ServerApp104.deploy(server,ServerLauncher104.class.getResourceAsStream("/genesis/server59/controller.zip"),ServerLauncher104.class.getResourceAsStream("/genesis/server104/mared.jar"));}
}
