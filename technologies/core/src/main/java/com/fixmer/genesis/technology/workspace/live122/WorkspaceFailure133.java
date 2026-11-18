package com.fixmer.genesis.technology.workspace.live122;
import java.io.*;import java.nio.file.*;import java.time.Instant;
/** Error details remain available even when writing the dedicated log fails. */
public final class WorkspaceFailure133 {
 private WorkspaceFailure133(){}
 public static String format(Throwable failure){var buffer=new StringWriter();failure.printStackTrace(new PrintWriter(buffer));return Instant.now()+"\n"+buffer;}
 public static synchronized String report(Path path,Throwable failure){String details=format(failure);try{Files.createDirectories(path.toAbsolutePath().getParent());if(Files.exists(path)&&Files.size(path)>1024*1024)Files.move(path,path.resolveSibling(path.getFileName().toString().equals("mared-workspace.log")?"mared-workspace.previous.log":path.getFileName()+".previous"),StandardCopyOption.REPLACE_EXISTING);Files.writeString(path,details+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception writeFailure){details+="\nLog write failed: "+writeFailure;}return details;}
}
