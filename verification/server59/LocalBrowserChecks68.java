import com.fixmer.genesis.technology.server.firstlaunch.LocalFileBrowser68;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.*;

/** Real pure-JDK filesystem checks, no native UI or Minecraft stubs. */
public final class LocalBrowserChecks68 {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private static void refused(Throwing action,String label)throws Exception{try{action.run();throw new AssertionError(label);}catch(IOException expected){checks++;}}
    private interface Throwing{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("genesis-browser68-");
        try{
            Path folder=Files.createDirectory(root.resolve("мои проекты"));Files.createDirectory(folder.resolve("nested"));Files.writeString(folder.resolve("One.jar"),"payload");Files.writeString(folder.resolve("other.txt"),"not a jar");Files.writeString(folder.resolve("UPPER.JAR"),"upper");
            var jarList=LocalFileBrowser68.list(folder,true,"",()->false);
            check(jarList.entries().size()==3,"Folders plus JAR files only");check(jarList.entries().get(0).directory(),"Directories first");
            check(LocalFileBrowser68.list(folder,false,"",()->false).entries().size()==1,"Directory selection hides files");
            check(LocalFileBrowser68.list(folder,true,"ONE",()->false).entries().get(0).name().equals("One.jar"),"Case-independent name filter");
            check(LocalFileBrowser68.select(folder.resolve("One.jar"),false).equals(folder.resolve("One.jar")),"Unicode and spaces in containing directory");
            check(LocalFileBrowser68.select(folder,true).equals(folder),"Directory selection");
            refused(()->LocalFileBrowser68.select(folder.resolve("other.txt"),false),"Non-JAR accepted");
            refused(()->LocalFileBrowser68.select(folder,false),"Directory accepted as JAR");
            refused(()->LocalFileBrowser68.select(folder.resolve("One.jar"),true),"File accepted as directory");
            refused(()->LocalFileBrowser68.select(folder.resolve("missing.jar"),false),"Missing file accepted");
            Files.createFile(folder.resolve("empty.jar"));refused(()->LocalFileBrowser68.select(folder.resolve("empty.jar"),false),"Empty JAR accepted");
            refused(()->LocalFileBrowser68.select(folder.resolve("UPPER.JAR"),false),"Backend-incompatible extension accepted");
            Files.writeString(folder.resolve("a b.jar"),"payload");refused(()->LocalFileBrowser68.select(folder.resolve("a b.jar"),false),"Backend-incompatible basename accepted");
            Path large=folder.resolve("large.jar");try(var channel=FileChannel.open(large,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){channel.position(64L*1024*1024);channel.write(ByteBuffer.wrap(new byte[]{0}));}
            refused(()->LocalFileBrowser68.select(large,false),"Oversized JAR accepted");Files.delete(large);
            refused(()->LocalFileBrowser68.list(folder,true,"",()->true),"Cancelled scan continued");
            refused(()->LocalFileBrowser68.list(folder,true,"x".repeat(257),()->false),"Unbounded query");
            Path many=Files.createDirectory(root.resolve("many"));for(int i=0;i<1005;i++)Files.writeString(many.resolve("mod"+i+".jar"),"j");
            var capped=LocalFileBrowser68.list(many,true,"",()->false);check(capped.truncated()&&capped.entries().size()==1000,"Entry cap and honest partial marker");
            check(LocalFileBrowser68.list(many,true,"mod1004",()->false).entries().size()==1,"Filter finds entries beyond visible page cap");
            try{jarList.entries().clear();throw new AssertionError("Mutable listing");}catch(UnsupportedOperationException expected){checks++;}
            try{Path link=folder.resolve("linked.jar");Files.createSymbolicLink(link,folder.resolve("One.jar"));refused(()->LocalFileBrowser68.select(link,false),"Symlink selected");check(LocalFileBrowser68.list(folder,true,"linked",()->false).entries().isEmpty(),"Symlink not listed");}catch(UnsupportedOperationException|FileSystemException unavailable){System.out.println("Symlink creation unavailable on this host; link checks skipped");}
            check(Files.readString(folder.resolve("One.jar")).equals("payload"),"Selection altered content");
            System.out.println("LocalBrowser68 filesystem checks passed: "+checks);
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
