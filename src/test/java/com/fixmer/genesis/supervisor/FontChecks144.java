package com.fixmer.genesis.supervisor;
import java.awt.image.BufferedImage;import java.nio.file.*;import java.util.*;import javax.imageio.ImageIO;
public final class FontChecks144 {
 public static void main(String[] args)throws Exception{
  Path game=Files.createTempDirectory("font144-");int count=0;
  try{
   Path dir=game.resolve("config/mared/dev");Files.createDirectories(dir);Path image=dir.resolve("restart-font-a.png"),props=dir.resolve("restart-font.properties");
   ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),"png",image.toFile());
   for(String invalid:List.of("1,0,2147483647,1,15,0","0,1,1,2147483647,15,0","0,0,1,1,15,-2147483648","0,0,1,1,65,0","0,0,1,1,15,32","0,0,1,1,15,0,8")){
    Files.writeString(props,"image=restart-font-a.png\nglyph.65="+invalid+"\n");if(PixelText.load(game).width("A")!=6)throw new AssertionError("invalid glyph accepted: "+invalid);count++;
   }
   Files.writeString(props,"image=restart-font-a.png\nglyph.65=0,0,1,1,15,0\n");if(PixelText.load(game).width("A")!=15)throw new AssertionError("valid glyph lost");count++;
   ImageIO.write(new BufferedImage(4096,4096,BufferedImage.TYPE_INT_ARGB),"png",image.toFile());
   var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long start=bean.getThreadAllocatedBytes(Thread.currentThread().getId());
   if(PixelText.load(game).width("A")!=6)throw new AssertionError("oversized sheet accepted");long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().getId())-start;
   if(allocated>=16L*1024*1024)throw new AssertionError("oversized decode allocated "+allocated);count++;System.out.println("Oversized PNG allocated bytes="+allocated);
  }finally{try(var walk=Files.walk(game)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  System.out.println("FontChecks144: "+count+" passed");
 }
}
