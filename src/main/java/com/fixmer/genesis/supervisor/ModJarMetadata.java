package com.fixmer.genesis.supervisor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.*;
/** Conservative metadata preflight for ordinary NeoForge JARs. No project code is loaded. */
public final class ModJarMetadata {
    public record Dependency(String id, String range, String type,String side) {
        public Dependency(String id,String range,String type){this(id,range,type,"BOTH");}
    }
    public record Info(String id, String version, List<Dependency> dependencies) {
    }
    private ModJarMetadata() {
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
                text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString();
            }
            var tables=ModMetadataText.parse(text.getBytes(StandardCharsets.UTF_8));
            var mods=tables.stream().filter(t->t.name().equals("[[mods]]")).toList();
            if(!tables.get(0).values().getOrDefault("modLoader", "").equals("javafml")||mods.size()!=1)throw new IOException("Installation supports one javafml mod per JAR");
            String id=mods.get(0).values().getOrDefault("modId",""),version=mods.get(0).values().getOrDefault("version","");
            if(!id.matches("[a-z][a-z0-9_]{1,63}")||Set.of("mared","genesis","genesis_core","minecraft","neoforge").contains(id))throw new IOException("Unsupported or protected mod id: "+id);
            if(version.isBlank())throw new IOException("Missing mod version");
            List<Dependency> deps=new ArrayList<>();
            for(var table:tables)if(table.name().equals("[[dependencies."+id+"]]")){
                var d=table.values();String dependency=d.getOrDefault("modId", "");
                if(!dependency.matches("[a-z][a-z0-9_]{1,63}"))throw new IOException("Invalid dependency id");
                deps.add(new Dependency(dependency,d.getOrDefault("versionRange",""),d.getOrDefault("type","required"),d.getOrDefault("side","BOTH")));
            }
            return new Info(id,version,List.copyOf(deps));
        }
    }
    public static void compatible(Info info,Map<String,String> loaded) throws IOException {
        compatible(info,loaded,"BOTH");
    }
    public static void compatible(Info info,Map<String,String> loaded,String side)throws IOException {
        for(var d:info.dependencies()) {
            if(!Set.of("CLIENT","SERVER","BOTH").contains(d.side())||!Set.of("required","optional","incompatible","discouraged").contains(d.type()))throw new IOException("Unsupported dependency declaration: "+d.id());
            if(!side.equals("BOTH")&&!d.side().equals("BOTH")&&!d.side().equals(side))continue;
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
