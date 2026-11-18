package com.fixmer.mared;
/** Real Windows identity/wait fixture; no Minecraft or supervisor state is synthesized. */
public final class BootstrapSourceProbe106 {
 public static void main(String[]args)throws Exception{var root=java.nio.file.Path.of(args[0]);var process=ProcessHandle.current();String text=process.pid()+"\n"+process.info().startInstant().orElseThrow().toEpochMilli()+"\n";java.nio.file.Files.writeString(root.resolve("identity106"),text);long until=System.nanoTime()+45_000_000_000L;while(!java.nio.file.Files.exists(root.resolve("exit106"))&&System.nanoTime()<until)Thread.sleep(50);}
}
