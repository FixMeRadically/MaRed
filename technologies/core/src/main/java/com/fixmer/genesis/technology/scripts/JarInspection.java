package com.fixmer.genesis.technology.scripts;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.function.BooleanSupplier;
/** Archive structure and explicit classpath only; nested JARs are resolved separately. */
public final class JarInspection  {
    private JarInspection() {
    }
    public record Info(boolean mod,boolean embedded,String entry,List<String> modIds,long expandedBytes) {
    }
    public static Map<String,String> modVersions(Path file)throws IOException {
        var result=new LinkedHashMap<String,String>();
        try(var jar=new JarFile(file.toFile(),false)) {
            var entry=jar.getJarEntry("META-INF/neoforge.mods.toml");
            if(entry==null)return result;
            String text;
            try(var in=jar.getInputStream(entry)) {
                byte[] b=in.readNBytes(256*1024+1);
                if(b.length>256*1024)throw new IOException("Mod metadata size limit");
                text=new String(b,java.nio.charset.StandardCharsets.UTF_8);
            }
            String id="",version="",table="";
            char multiline=0;
            for(String raw:text.split("\\R")) {
                String line=DependencySyntax.uncomment(raw);
                if(multiline!=0) {
                    if(raw.contains(String.valueOf(multiline).repeat(3)))multiline=0;
                    continue;
                }
                int literal=line.indexOf(String.valueOf((char)39).repeat(3)),basic=line.indexOf(String.valueOf((char)34).repeat(3));
                if(literal>=0||basic>=0) {
                    char q=literal>=0?(char)39:(char)34;
                    String mark=String.valueOf(q).repeat(3);
                    int start=line.indexOf(mark);
                    if(line.indexOf(mark,start+3)<0)multiline=q;
                    continue;
                }
                if(line.startsWith("[")) {
                    if(table.equals("[[mods]]")&&!id.isEmpty()) {
                        if(result.putIfAbsent(id,version)!=null)throw new IOException("Duplicate modId in archive: "+id);
                    }
                    id="";
                    version="";
                    table=line;
                    continue;
                }
                if(!table.equals("[[mods]]"))continue;
                var m=java.util.regex.Pattern.compile("^(modId|version)\\s*=\\s*(['\"]).*\\2$").matcher(line);
                if(m.matches()) {
                    String value=DependencySyntax.string(line.substring(line.indexOf('=')+1));
                    if(m.group(1).equals("modId"))id=value;
                    else version=value;
                }
            }
            if(table.equals("[[mods]]")&&!id.isEmpty())if(result.putIfAbsent(id,version)!=null)throw new IOException("Duplicate modId in archive: "+id);
            var manifest=jar.getManifest();
            String implementation=manifest==null?"":Objects.toString(manifest.getMainAttributes().getValue("Implementation-Version"),"");
            result.replaceAll((key,value)->value.equals("${file.jarVersion}")?implementation:value);
        }
        return Collections.unmodifiableMap(result);
    }
    public static Info inspect(Path file,BooleanSupplier cancelled)throws IOException {return inspect(file,cancelled,true,512L*1024*1024);}
    static Info inspect(Path file,BooleanSupplier cancelled,boolean metadata)throws IOException {return inspect(file,cancelled,metadata,512L*1024*1024);}
    public static Info inspect(Path file,BooleanSupplier cancelled,long remaining)throws IOException {return inspect(file,cancelled,true,remaining);}
    private static Info inspect(Path file,BooleanSupplier cancelled,boolean metadata,long remaining)throws IOException {
        long expanded=0;
        int count=0;
        var names=new HashSet<String>();
        boolean embedded=false,mod=false;
        String entry="";
        List<String> ids=List.of();
        try(var jar=new JarFile(file.toFile(),false)) {
            var en=jar.entries();
            byte[] buffer=new byte[65536];
            while(en.hasMoreElements()) {
                if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("Archive inspection cancelled");
                var e=en.nextElement();
                String n=e.getName();
                if(++count>20000||!names.add(n)||n.startsWith("/")||n.contains("\\")||n.contains(":")||Arrays.stream(n.split("/")).anyMatch(p->p.equals("..")||p.equals(".")))throw new IOException("Invalid or excessive archive entries: "+file.getFileName());
                if(e.isDirectory())continue;
                long size=0;
                try(var in=jar.getInputStream(e)) {
                    int k;
                    while((k=in.read(buffer))!=-1) {
                        if(cancelled.getAsBoolean())throw new IOException("Archive inspection cancelled");
                        size+=k;
                        expanded+=k;
                        if(size>256L*1024*1024||expanded>remaining)throw new IOException("Expanded archive limit: "+n);
                    }
                }
                if(n.equals("META-INF/jarjar/metadata.json"))embedded=true;
                if(metadata&&n.equals("META-INF/neoforge.mods.toml")) {
                    if(size>256*1024)throw new IOException("Mod metadata size limit");
                    try(var in=jar.getInputStream(e)) {
                        String text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                        ids=List.copyOf(DependencySyntax.modIds(text).getOrDefault("mods",List.of()));
                        if(ids.isEmpty())throw new IOException("No literal modId in packaged mod metadata");
                        for(String id:ids)if(!id.matches("[a-z][a-z0-9_]{1,63}"))throw new IOException("Invalid packaged modId: "+id);
                        mod=true;
                    }
                }
            }
            if(count==0)throw new IOException("Empty JAR archive");
            var manifest=jar.getManifest();
            if(manifest!=null) {
                String cp=manifest.getMainAttributes().getValue("Class-Path");
                if(cp!=null&&!cp.isBlank())throw new IOException("Implicit Manifest Class-Path is unsupported; use explicit runtime libraries");
                entry=Objects.toString(manifest.getMainAttributes().getValue("Main-Class"),"");
            }
        }
        return new Info(mod,embedded,entry,ids,expanded);
    }
}
