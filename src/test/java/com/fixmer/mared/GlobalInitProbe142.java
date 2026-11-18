package com.fixmer.mared;
import java.nio.file.*;import com.fixmer.mared.commands.storage.MaredGlobalStorage;
public class GlobalInitProbe142{
 public static void main(String[]args)throws Exception{
  Path root=Files.createTempDirectory("mared-global-init142-");net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(root);Path file=root.resolve("config/mared/visited.json");Files.createDirectories(file.getParent());var text=new StringBuilder("{");for(int n=0;n<50000;n++)text.append('"').append("__marker").append(n).append("\":true,");text.append("\"__last142\":true}");Files.writeString(file,text);
  var field=MaredGlobalStorage.class.getDeclaredField("loaded");field.setAccessible(true);
  var loader=new Thread(()->MaredGlobalStorage.get("__last142"));loader.start();long deadline=System.nanoTime()+5_000_000_000L;while(!(boolean)field.get(null)&&System.nanoTime()<deadline)Thread.onSpinWait();boolean during=loader.isAlive();Object seen=MaredGlobalStorage.get("__last142");loader.join();if(!Boolean.TRUE.equals(seen))throw new AssertionError("Uninitialized marker observed");System.out.println("publishedWhileLoaderAlive="+during+", visibleAtPublish="+seen+", final="+MaredGlobalStorage.get("__last142"));try(var paths=Files.walk(root)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
 }
}
