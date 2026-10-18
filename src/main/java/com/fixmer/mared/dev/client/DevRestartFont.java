package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevPaths;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Export resource-pack bitmap glyphs while the game is idle, not on the restart critical path.
 * The exact advances come from Minecraft's active font on its own client thread. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class DevRestartFont {
    private static boolean started;
    private DevRestartFont() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(started||mc.player==null||mc.getOverlay()!=null||!DevStartupWork.stable())return;started=true;
        String sample="GENESIS Подготовка перезапуска Завершение текущей игры Подготовка файлов и установка изменений Запуск Minecraft Загрузка ресурсов Minecraft Открытие мира Восстановление игрока Возврат предыдущей версии мода Готово";
        Map<Integer,Integer> advances=new LinkedHashMap<>();sample.codePoints().distinct().forEach(cp->advances.put(cp,mc.font.width(new String(Character.toChars(cp)))));
        ResourceManager resources=mc.getResourceManager();
        DevStartupWork.submit("font",()->{try{export(resources,advances,DevPaths.root());}catch(Exception|LinkageError e){Mared.LOGGER.info("[Mared dev] Bitmap font export unavailable; using pixel fallback");}});
    }
    private record Glyph(BufferedImage image,int advance,int top) { }
    private static void export(ResourceManager resources,Map<Integer,Integer> advances,Path dir)throws IOException{
        Map<Integer,Glyph> glyphs=new LinkedHashMap<>();Set<String> visited=new HashSet<>();
        collect(resources,"minecraft:default",advances,glyphs,visited,0);
        if(!glyphs.containsKey((int)'G')||!glyphs.containsKey((int)'E'))return;
        int count=glyphs.size(),cols=16,cell=32;BufferedImage atlas=new BufferedImage(cols*cell,((count+cols-1)/cols)*cell,BufferedImage.TYPE_INT_ARGB);
        Properties p=new Properties();String name="restart-font-"+UUID.randomUUID()+".png";p.setProperty("image",name);p.setProperty("source","minecraft bitmap resource providers");
        Graphics2D g=atlas.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        try{int i=0;for(var entry:glyphs.entrySet()){
            Glyph glyph=entry.getValue();int x=i%cols*cell,y=i/cols*cell;i++;g.drawImage(glyph.image,x,y,null);
            p.setProperty("glyph."+entry.getKey(),x+","+y+","+glyph.image.getWidth()+","+glyph.image.getHeight()+","+glyph.advance+","+glyph.top);
        }}finally{g.dispose();}
        Files.createDirectories(dir);Path image=dir.resolve(name),file=dir.resolve("restart-font.properties");
        ImageIO.write(atlas,"png",image.toFile());Path temp=Files.createTempFile(dir,"font-",".tmp");
        try{try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Minecraft glyphs for Genesis handover");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
        try(var old=Files.newDirectoryStream(dir,"restart-font-*.png")){int n=0;for(Path previous:old){if(++n>32)break;if(!previous.equals(image)&&!Files.isSymbolicLink(previous))try{Files.deleteIfExists(previous);}catch(IOException ignored){}}}
    }
    private static void collect(ResourceManager resources,String font,Map<Integer,Integer> advances,Map<Integer,Glyph> out,Set<String> visited,int depth)throws IOException{
        if(depth>8||visited.size()>32||!visited.add(font))return;
        String[] id=split(font);ResourceLocation location=ResourceLocation.parse(id[0]+":font/"+id[1]+".json");
        var found=resources.getResource(location);if(found.isEmpty())return;
        JsonObject json;
        try(var in=found.get().open()){
            byte[] bytes=in.readNBytes(262145);if(bytes.length>262144)return;json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }
        if(!json.has("providers"))return;
        for(JsonElement element:json.getAsJsonArray("providers")){
            JsonObject provider=element.getAsJsonObject();String type=provider.get("type").getAsString();type=type.substring(type.indexOf(':')+1);
            if(type.equals("reference")){collect(resources,provider.get("id").getAsString(),advances,out,visited,depth+1);continue;}
            if(!type.equals("bitmap")||!provider.has("chars")||!provider.has("file"))continue;
            String[] texture=split(provider.get("file").getAsString());
            var asset=resources.getResource(ResourceLocation.parse(texture[0]+":textures/"+texture[1]));if(asset.isEmpty())continue;
            BufferedImage source;try(var in=asset.get().open()){byte[] bytes=in.readNBytes(4*1024*1024+1);if(bytes.length>4*1024*1024)continue;var memory=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes));try{source=ImageIO.read(memory);}finally{try{memory.close();}catch(IOException ignored){}}}
            if(source==null||source.getWidth()>4096||source.getHeight()>4096)continue;
            JsonArray rows=provider.getAsJsonArray("chars");if(rows.isEmpty())continue;
            int columns=rows.get(0).getAsString().codePointCount(0,rows.get(0).getAsString().length());if(columns==0)continue;
            int cw=source.getWidth()/columns,ch=source.getHeight()/rows.size();if(cw==0||ch==0)continue;
            int height=provider.has("height")?provider.get("height").getAsInt():8,ascent=provider.has("ascent")?provider.get("ascent").getAsInt():7;
            int width=(int)Math.ceil((double)cw*height/ch);if(height<=0||height>32||width<=0||width>32)continue;
            for(int y=0;y<rows.size();y++){
                int[] chars=rows.get(y).getAsString().codePoints().toArray();if(chars.length!=columns)continue;
                for(int x=0;x<columns;x++){
                    int cp=chars[x];if(cp==0||cp==32||!advances.containsKey(cp)||out.containsKey(cp)||advances.get(cp)<=0)continue;
                    BufferedImage glyph=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=glyph.createGraphics();
                    try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(source,0,0,width,height,x*cw,y*ch,(x+1)*cw,(y+1)*ch,null);}finally{g.dispose();}
                    out.put(cp,new Glyph(glyph,advances.get(cp),7-ascent));
                }
            }
        }
    }
    private static String[] split(String id){int colon=id.indexOf(':');return colon<0?new String[]{"minecraft",id}:new String[]{id.substring(0,colon),id.substring(colon+1)};}
}
