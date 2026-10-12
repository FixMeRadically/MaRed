package com.fixmer.mared.technology.scripts;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline bounded documentation, independent of Minecraft and executable scripts. */
public final class ScriptHelpManual {
    public enum Kind {TEXT,HEADING,CODE,DIAGRAM,LINK}
    public record Block(Kind kind,String text,String target) {}
    public record Article(String id,String group,String title,String summary,String keywords,List<Block> blocks){
        public Article {blocks=List.copyOf(blocks);}
        public String copyText(){var s=new StringBuilder(title+"\n\n"+summary+"\n\n");for(var b:blocks)s.append(b.kind==Kind.CODE?"```\n"+b.text+"\n```":b.text).append("\n\n");return s.toString();}
    }
    private final List<Article> articles;
    public ScriptHelpManual(List<Article> articles){if(articles.isEmpty())throw new IllegalArgumentException("Empty Help");this.articles=List.copyOf(articles);}
    public List<Article> articles(){return articles;}
    public Article article(String id){return articles.stream().filter(a->a.id.equals(id)).findFirst().orElse(articles.get(0));}
    public List<Article> search(String query){String q=Objects.requireNonNullElse(query,"").strip().toLowerCase(Locale.ROOT);String[] words=q.split("\\s+");return articles.stream().filter(a->{String text=(a.title+" "+a.group+" "+a.summary+" "+a.keywords+" "+a.copyText()).toLowerCase(Locale.ROOT);for(String word:words)if(!text.contains(word))return false;return true;}).toList();}
    public static ScriptHelpManual load(String language){
        String name=language!=null&&language.startsWith("ru")?"ru":"en";
        try(var in=ScriptHelpManual.class.getResourceAsStream("/assets/mared/help/scripts_"+name+".md")){
            if(in==null)throw new IOException("Bundled Help resource missing");byte[] data=in.readNBytes(256*1024+1);if(data.length>256*1024)throw new IOException("Help size limit");return parse(new String(data,StandardCharsets.UTF_8));
        }catch(IOException|RuntimeException bad){return new ScriptHelpManual(List.of(new Article("unavailable","Help","Help unavailable",bad.toString(),"",List.of(new Block(Kind.TEXT,"Bundled documentation could not be loaded. Reinstall the complete MaRed JAR.","")))));}
    }
    public static ScriptHelpManual parse(String text){
        if(text.length()>256*1024)throw new IllegalArgumentException("Help size limit");var pages=new ArrayList<Article>();var blocks=new ArrayList<Block>();String[] meta=null;StringBuilder paragraph=new StringBuilder(),code=new StringBuilder();boolean inCode=false;
        for(String line:(text+"\n@@ END").split("\\R",-1)){
            if(inCode){if(line.startsWith("```")){blocks.add(new Block(Kind.CODE,code.toString().stripTrailing(),""));code.setLength(0);inCode=false;}else code.append(line).append('\n');continue;}
            if(line.startsWith("@@ ")){flush(paragraph,blocks);if(meta!=null)pages.add(new Article(meta[0],meta[1],meta[2],meta[3],meta[4],blocks));blocks=new ArrayList<>();if(line.equals("@@ END")){meta=null;continue;}meta=line.substring(3).split("\\|",5);if(meta.length!=5||!meta[0].matches("[a-z0-9_-]+")||pages.size()>=64)throw new IllegalArgumentException("Invalid Help article");}
            else if(meta!=null){if(line.startsWith("```")){flush(paragraph,blocks);inCode=true;}else if(line.startsWith("## ")){flush(paragraph,blocks);blocks.add(new Block(Kind.HEADING,line.substring(3),""));}else if(line.startsWith("::: ")){flush(paragraph,blocks);String[] diagram=line.substring(4).split("\\|",2);blocks.add(new Block(Kind.DIAGRAM,diagram.length==2?diagram[1]:diagram[0],diagram[0]));}else if(line.startsWith("=> ")){flush(paragraph,blocks);String[] link=line.substring(3).split("\\|",2);if(link.length!=2)throw new IllegalArgumentException("Invalid Help link");blocks.add(new Block(Kind.LINK,link[1],link[0]));}else if(line.isBlank())flush(paragraph,blocks);else{if(paragraph.length()>0)paragraph.append('\n');paragraph.append(line);}}
        }
        if(inCode||pages.isEmpty())throw new IllegalArgumentException("Incomplete Help");var ids=new HashSet<String>();for(var a:pages)if(!ids.add(a.id))throw new IllegalArgumentException("Duplicate Help id");for(var a:pages)for(var b:a.blocks)if(b.kind==Kind.LINK&&!ids.contains(b.target))throw new IllegalArgumentException("Missing Help target");return new ScriptHelpManual(pages);
    }
    private static void flush(StringBuilder text,List<Block> blocks){if(text.length()>0){blocks.add(new Block(Kind.TEXT,text.toString(),""));text.setLength(0);}}
}
