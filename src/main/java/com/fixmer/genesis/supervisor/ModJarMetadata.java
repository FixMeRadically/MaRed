package com.fixmer.genesis.supervisor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.*;
/** Conservative metadata preflight for ordinary NeoForge JARs. No project code is loaded. */
public final class ModJarMetadata {
    public record Dependency(String id, String range, String type) {
    }
    public record Info(String id, String version, List<Dependency> dependencies) {
    }
    private ModJarMetadata() {
    }
    private static String value(String input) throws IOException {
        String s=input.trim();
        if(s.length()<2 || (s.charAt(0)!='"' && s.charAt(0)!=39) || s.charAt(s.length()-1)!=s.charAt(0)) throw new IOException("Expected literal TOML string: "+s);
        s=s.substring(1,s.length()-1);
        if(s.contains("${") || s.indexOf('\\')>=0) throw new IOException("Unresolved or unsupported metadata expression");
        return s;
    }
    private static String uncomment(String input) {
        char quote=0;
        boolean escaped=false;
        for(int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if(quote!=0) {
                if(escaped)escaped=false;
                else if(c=='\\'&&quote=='"')escaped=true;
                else if(c==quote)quote=0;
            }
            else if(c=='"'||c==39)quote=c;
            else if(c=='#')return input.substring(0,i).trim();
        }
        return input.trim();
    }
    public static Info read(Path file) throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>256L*1024*1024) throw new IOException("Invalid or oversized mod JAR");
        try(JarFile jar=new JarFile(file.toFile(),false)) {
            var entry=jar.getJarEntry("META-INF/neoforge.mods.toml");
            if(entry==null)throw new IOException("Not a packaged NeoForge mod (missing META-INF/neoforge.mods.toml)");
            String text;
            try(var in=jar.getInputStream(entry)) {
                byte[] data=in.readNBytes(256*1024+1);
                if(data.length>256*1024)throw new IOException("Metadata size limit");
                text=new String(data,StandardCharsets.UTF_8);
            }
            List<Map<String,String>> mods=new ArrayList<>(),dependencies=new ArrayList<>();
            Map<String,String> current=null;
            char multiline=0;
            String loader="";
            for(String raw:text.split("\\R")) {
                String line=uncomment(raw);
                if(multiline!=0) {
                    if(raw.contains(String.valueOf(multiline).repeat(3)))multiline=0;
                    continue;
                }
                int a=line.indexOf("\"\"\""),b=line.indexOf(String.valueOf((char)39).repeat(3));
                if(a>=0||b>=0) {
                    char q=a>=0?'"':(char)39;
                    String mark=String.valueOf(q).repeat(3);
                    int start=line.indexOf(mark);
                    if(line.indexOf(mark,start+3)<0)multiline=q;
                    continue;
                }
                if(line.isEmpty())continue;
                if(line.startsWith("[")) {
                    if(line.equals("[[mods]]")) {
                        current=new LinkedHashMap<>();
                        mods.add(current);
                    }
                    else if(line.matches("\\[\\[dependencies\\.[a-z][a-z0-9_]{1,63}]]")) {
                        current=new LinkedHashMap<>();
                        dependencies.add(current);
                    }
                    else current=null;
                    continue;
                }
                int eq=line.indexOf('=');
                if(eq<0)continue;
                String key=line.substring(0,eq).trim(),rawValue=line.substring(eq+1);
                if(current!=null && Set.of("modId","version","versionRange","type","side").contains(key)) {
                    if(current.putIfAbsent(key,value(rawValue))!=null)throw new IOException("Duplicate metadata key: "+key);
                }
                else if(key.equals("modLoader"))loader=value(rawValue);
            }
            if(!loader.equals("javafml") || mods.size()!=1)throw new IOException("Installation supports one javafml mod per JAR");
            String id=mods.get(0).getOrDefault("modId",""),version=mods.get(0).getOrDefault("version","");
            if(!id.matches("[a-z][a-z0-9_]{1,63}") || Set.of("mared","genesis","genesis_core","minecraft","neoforge").contains(id))throw new IOException("Unsupported or protected mod id: "+id);
            if(version.isBlank())throw new IOException("Missing mod version");
            List<Dependency> deps=new ArrayList<>();
            for(var d:dependencies)deps.add(new Dependency(d.getOrDefault("modId",""),d.getOrDefault("versionRange",""),d.getOrDefault("type","required")));
            return new Info(id,version,List.copyOf(deps));
        }
    }
    public static void compatible(Info info,Map<String,String> loaded) throws IOException {
        for(var d:info.dependencies()) {
            String installed=loaded.get(d.id());
            if(d.type().equals("required")) {
                if(installed==null)throw new IOException("Required mod missing: "+d.id());
                if(!inRange(installed,d.range()))throw new IOException("Incompatible "+d.id()+": running "+installed+", required "+d.range());
            }
            else if(d.type().equals("optional") && installed!=null && !inRange(installed,d.range()))throw new IOException("Incompatible optional mod: "+d.id());
            else if(d.type().equals("incompatible") && installed!=null && inRange(installed,d.range()))throw new IOException("Incompatible installed mod: "+d.id());
        }
    }
    private static int compare(String a,String b) throws IOException {
        if(!a.matches("[0-9]+(?:\\.[0-9]+)*") || !b.matches("[0-9]+(?:\\.[0-9]+)*"))throw new IOException("Automatic preflight supports numeric release versions only");
        String[] x=a.split("\\."),y=b.split("\\.");
        for(int i=0;i<Math.max(x.length,y.length);i++) {
            var av=new java.math.BigInteger(i<x.length?x[i]:"0");
            var bv=new java.math.BigInteger(i<y.length?y[i]:"0");
            int result=av.compareTo(bv);
            if(result!=0)return result;
        }
        return 0;
    }
    static boolean inRange(String version,String range) throws IOException {
        range=range.trim();
        if(range.isEmpty())return true;
        if(range.startsWith("[")&&range.endsWith("]")&&!range.contains(","))return compare(version,range.substring(1,range.length()-1))==0;
        if((range.startsWith("[")||range.startsWith("("))&&(range.endsWith("]")||range.endsWith(")"))) {
            String[] bounds=range.substring(1,range.length()-1).split(",",-1);
            if(bounds.length!=2)throw new IOException("Unsupported version range: "+range);
            if(!bounds[0].isBlank()) {
                int n=compare(version,bounds[0].trim());
                if(n<0 || n==0&&range.charAt(0)=='(')return false;
            }
            if(!bounds[1].isBlank()) {
                int n=compare(version,bounds[1].trim());
                if(n>0 || n==0&&range.charAt(range.length()-1)==')')return false;
            }
            return true;
        }
        return compare(version,range)==0;
    }
}
