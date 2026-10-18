package com.fixmer.genesis.supervisor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Integer bitmap glyphs. No system font, antialiasing, font substitution or platform metrics. */
final class PixelText {
    private record Glyph(int x,int y,int w,int h,int advance,int top) { }
    private final Map<Integer,Glyph> glyphs=new HashMap<>();
    private final Map<Integer,BufferedImage> tinted=new HashMap<>();
    private BufferedImage atlas;
    static PixelText load(Path game){
        PixelText font=new PixelText();
        try{
            Path file=game.resolve("config/mared/dev/restart-font.properties");RuntimeFiles.safe(file);
            if(!Files.isRegularFile(file)||Files.size(file)>65536)return font;
            Properties p=new Properties();try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(in);}
            String name=p.getProperty("image","");if(!name.matches("restart-font-[a-f0-9-]+\\.png"))return font;
            Path image=file.resolveSibling(name);RuntimeFiles.safe(image);if(Files.size(image)>4*1024*1024)return font;
            BufferedImage sheet=ImageIO.read(image.toFile());if(sheet==null||sheet.getWidth()>1024||sheet.getHeight()>1024)return font;
            for(String k:p.stringPropertyNames())if(k.startsWith("glyph."))try{
                int cp=Integer.parseInt(k.substring(6));String[] n=p.getProperty(k).split(",");
                Glyph g=new Glyph(Integer.parseInt(n[0]),Integer.parseInt(n[1]),Integer.parseInt(n[2]),Integer.parseInt(n[3]),Integer.parseInt(n[4]),Integer.parseInt(n[5]));
                if(g.x>=0&&g.y>=0&&g.w>0&&g.h>0&&g.x+g.w<=sheet.getWidth()&&g.y+g.h<=sheet.getHeight()&&g.advance>0&&g.advance<=64&&Math.abs(g.top)<32)font.glyphs.put(cp,g);
            }catch(RuntimeException ignored) { }
            font.atlas=sheet;
        }catch(IOException|RuntimeException ignored) { }
        return font;
    }
    int width(String s){return s.codePoints().map(cp->{Glyph g=glyphs.get(cp);return g!=null?g.advance:cp==' '?4:6;}).sum();}
    void centered(Graphics2D g,String s,int x,int y,int rgb){draw(g,s,x-width(s)/2,y,rgb);}
    private void draw(Graphics2D g,String s,int x,int y,int rgb){
        for(int cp:s.codePoints().toArray()){
            Glyph glyph=glyphs.get(cp);
            if(glyph!=null&&atlas!=null){
                BufferedImage image=tinted.computeIfAbsent(rgb,c->tint(atlas,c));
                g.drawImage(image,x,y+glyph.top,x+glyph.w,y+glyph.top+glyph.h,glyph.x,glyph.y,glyph.x+glyph.w,glyph.y+glyph.h,null);x+=glyph.advance;
            }else{drawFallback(g,cp,x,y,rgb);x+=cp==' '?4:6;}
        }
    }
    private static BufferedImage tint(BufferedImage source,int rgb){
        BufferedImage out=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++)out.setRGB(x,y,(source.getRGB(x,y)&0xFF000000)|(rgb&0xFFFFFF));return out;
    }
    private static final Map<Integer,String> FALLBACK=new HashMap<>();
    static{
        String[] rows={
          "A:01110/10001/10001/11111/10001/10001/10001","B:11110/10001/10001/11110/10001/10001/11110","C:01111/10000/10000/10000/10000/10000/01111",
          "D:11110/10001/10001/10001/10001/10001/11110","E:11111/10000/10000/11110/10000/10000/11111","F:11111/10000/10000/11110/10000/10000/10000",
          "G:01111/10000/10000/10111/10001/10001/01111","H:10001/10001/10001/11111/10001/10001/10001","I:11111/00100/00100/00100/00100/00100/11111",
          "J:00111/00010/00010/00010/00010/10010/01100","K:10001/10010/10100/11000/10100/10010/10001","L:10000/10000/10000/10000/10000/10000/11111",
          "M:10001/11011/10101/10101/10001/10001/10001","N:10001/11001/11001/10101/10011/10011/10001","O:01110/10001/10001/10001/10001/10001/01110",
          "P:11110/10001/10001/11110/10000/10000/10000","Q:01110/10001/10001/10001/10101/10010/01101","R:11110/10001/10001/11110/10100/10010/10001",
          "S:01111/10000/10000/01110/00001/00001/11110","T:11111/00100/00100/00100/00100/00100/00100","U:10001/10001/10001/10001/10001/10001/01110",
          "V:10001/10001/10001/10001/10001/01010/00100","W:10001/10001/10001/10101/10101/11011/10001","X:10001/10001/01010/00100/01010/10001/10001",
          "Y:10001/10001/01010/00100/00100/00100/00100","Z:11111/00001/00010/00100/01000/10000/11111",
          "0:01110/10001/10011/10101/11001/10001/01110","1:00100/01100/00100/00100/00100/00100/01110","2:01110/10001/00001/00010/00100/01000/11111",
          "3:11110/00001/00001/01110/00001/00001/11110","4:00010/00110/01010/10010/11111/00010/00010","5:11111/10000/10000/11110/00001/00001/11110",
          "6:01110/10000/10000/11110/10001/10001/01110","7:11111/00001/00010/00100/01000/01000/01000","8:01110/10001/10001/01110/10001/10001/01110",
          "9:01110/10001/10001/01111/00001/00001/01110",".:00000/00000/00000/00000/00000/00100/00100","·:00000/00000/00000/00100/00000/00000/00000",
          "Б:11111/10000/10000/11110/10001/10001/11110","Г:11111/10000/10000/10000/10000/10000/10000","Д:00110/01010/01010/01010/01010/11111/10001",
          "Ж:10101/10101/01110/00100/01110/10101/10101","З:11110/00001/00001/01110/00001/00001/11110","И:10001/10001/10011/10101/11001/10001/10001",
          "Й:01010/00100/10001/10011/10101/11001/10001","Л:00111/01001/01001/01001/01001/01001/10001","П:11111/10001/10001/10001/10001/10001/10001",
          "Ф:00100/01110/10101/10101/10101/01110/00100","Ц:10010/10010/10010/10010/10010/11111/00001","Ч:10001/10001/10001/01111/00001/00001/00001",
          "Ш:10101/10101/10101/10101/10101/10101/11111","Щ:10101/10101/10101/10101/10101/11111/00001","Ъ:11000/01000/01000/01110/01001/01001/01110",
          "Ы:10001/10001/10001/11101/10011/10011/11101","Ь:10000/10000/10000/11110/10001/10001/11110","Э:11110/00001/00001/01111/00001/00001/11110",
          "Ю:10010/10101/10101/11101/10101/10101/10010","Я:01111/10001/10001/01111/00101/01001/10001"};
        for(String row:rows)FALLBACK.put(row.codePointAt(0),row.substring(row.indexOf(':')+1).replace("/",""));
        String from="АВЕКМНОРСТУХЁ",to="ABEKMHOPCTYXE";for(int i=0;i<from.length();i++)FALLBACK.put((int)from.charAt(i),FALLBACK.get((int)to.charAt(i)));
    }
    private static void drawFallback(Graphics2D g,int cp,int x,int y,int rgb){
        if(cp==' ')return;String bits=FALLBACK.get(Character.toUpperCase(cp));if(bits==null)bits="11111100011000110001100011000111111";
        g.setColor(new Color(rgb));for(int row=0;row<7;row++)for(int col=0;col<5;col++)if(bits.charAt(row*5+col)=='1')g.fillRect(x+col,y+row,1,1);
    }
}
