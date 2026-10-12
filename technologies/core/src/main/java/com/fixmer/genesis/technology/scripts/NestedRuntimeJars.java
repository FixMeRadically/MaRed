package com.fixmer.genesis.technology.scripts;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
/** Explicit Jar-in-Jar extraction. Snapshot paths, hashes and total bytes are bounded. */
final class NestedRuntimeJars  {
    private NestedRuntimeJars() {
    }
    static void expand(Collection<Path> mains,Path destination,List<Path> paths,JavaSourceRunner.Cancellation token,long[] expanded)throws IOException {
        var seen=new HashSet<String>();for(Path file:mains){String name=file.getFileName().toString();if(name.matches("[0-9a-f]{64}\\.jar"))seen.add(name.substring(0,64));}
        var identities=new HashMap<String,String>();for(Path main:List.copyOf(mains))walk(main,destination,paths,token,seen,expanded,identities,0);
    }
    private static void walk(Path file,Path dest,List<Path> paths,JavaSourceRunner.Cancellation token,Set<String> seen,long[] total,Map<String,String> identities,int depth)throws IOException {
        if(depth>8)throw new IOException("Jar-in-Jar nesting limit: 8");
        try(var jar=new JarFile(file.toFile(),false)) {
            var meta=jar.getJarEntry("META-INF/jarjar/metadata.json");
            if(meta==null)return;
            String text;
            try(var in=jar.getInputStream(meta)) {
                byte[] data=in.readNBytes(256*1024+1);
                if(data.length>256*1024)throw new IOException("Jar-in-Jar metadata limit");
                text=new String(data,java.nio.charset.StandardCharsets.UTF_8);
            }
            Object parsed=new Json(text).parse();
            if(!(parsed instanceof Map<?,?> object)||!(object.get("jars") instanceof List<?> entries))throw new IOException("Invalid Jar-in-Jar metadata");
            if(entries.size()>128)throw new IOException("Jar-in-Jar entry limit: 128");
            for(Object e:entries) {
                if(token.cancelled())throw new IOException("Jar-in-Jar extraction cancelled");
                if(!(e instanceof Map<?,?> item)||!(item.get("path") instanceof String path)||!(item.get("identifier") instanceof Map<?,?> id)||!(id.get("group") instanceof String group)||!(id.get("artifact") instanceof String artifact)||!(item.get("version") instanceof Map<?,?> version)||!(version.get("artifactVersion") instanceof String actual)||!(version.get("range") instanceof String range)||!ProjectDependencies.validRange(range))throw new IOException("Invalid Jar-in-Jar entry metadata");
                if(!DependencySyntax.numericContains(range,actual))throw new IOException("Nested library version is outside its declared range: "+actual);
                String identity=group+":"+artifact;
                String prior=identities.putIfAbsent(identity,actual);
                if(prior!=null&&!prior.equals(actual))throw new IOException("Conflicting nested versions: "+identity);
                if(!path.startsWith("META-INF/jarjar/")||!path.endsWith(".jar")||path.contains("..")||path.contains("\\")||path.contains(":"))throw new IOException("Invalid nested JAR path: "+path);
                var entry=jar.getJarEntry(path);
                if(entry==null||entry.isDirectory())throw new IOException("Missing nested JAR: "+path);
                byte[] bytes;
                try(var in=jar.getInputStream(entry)) {
                    bytes=in.readNBytes(64*1024*1024+1);
                }
                if(bytes.length>64*1024*1024)throw new IOException("Nested JAR byte limit");
                String hash=GradleOutputs.hash(bytes);
                if(!seen.add(hash))continue;
                if(paths.size()>=128)throw new IOException("Runtime classpath limit: 128");
                Files.createDirectories(dest);
                Path nested=dest.resolve(hash+".jar");
                Files.write(nested,bytes,StandardOpenOption.CREATE_NEW);
                total[0]+=JarInspection.inspect(nested,token::cancelled,512L*1024*1024-total[0]).expandedBytes();
                paths.add(nested);
                walk(nested,dest,paths,token,seen,total,identities,depth+1);
            }
        }
    }
    private static final class Json  {
        final String text;
        int at;
        Json(String text) {
            this.text=text;
        }
        Object parse()throws IOException {
            Object v=value(0);
            ws();
            if(at!=text.length())throw bad();
            return v;
        }
        IOException bad() {
            return new IOException("Malformed Jar-in-Jar JSON at "+at);
        }
        void ws() {
            while(at<text.length()&&Character.isWhitespace(text.charAt(at)))at++;
        }
        Object value(int depth)throws IOException {
            ws();
            if(depth>32||at>=text.length())throw bad();
            char c=text.charAt(at);
            if(c=='"')return string();
            if(c=='{') {
                at++;
                var map=new LinkedHashMap<String,Object>();
                ws();
                if(take('}'))return map;
                do {
                    ws();
                    String key=string();
                    ws();
                    if(!take(':')||map.containsKey(key))throw bad();
                    map.put(key,value(depth+1));
                    ws();
                    if(take('}'))return map;
                }
                while(take(','));
                throw bad();
            }
            if(c=='[') {
                at++;
                var list=new ArrayList<Object>();
                ws();
                if(take(']'))return list;
                do {
                    list.add(value(depth+1));
                    ws();
                    if(take(']'))return list;
                }
                while(take(','));
                throw bad();
            }
            for(String lit:List.of("true","false","null"))if(text.startsWith(lit,at)) {
                at+=lit.length();
                return lit.equals("true")?Boolean.TRUE:lit.equals("false")?Boolean.FALSE:null;
            }
            int start=at;
            while(at<text.length()&&"-+0123456789.eE".indexOf(text.charAt(at))>=0)at++;
            if(start==at)throw bad();
            String number=text.substring(start,at);
            if(!number.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))throw bad();
            try {
                return new java.math.BigDecimal(number);
            }
            catch(NumberFormatException e) {
                throw bad();
            }
        }
        boolean take(char c) {
            if(at<text.length()&&text.charAt(at)==c) {
                at++;
                return true;
            }
            return false;
        }
        String string()throws IOException {
            if(!take('"'))throw bad();
            var b=new StringBuilder();
            while(at<text.length()) {
                char c=text.charAt(at++);
                if(c=='"')return b.toString();
                if(c<32)throw bad();
                if(c=='\\') {
                    if(at>=text.length())throw bad();
                    char e=text.charAt(at++);
                    switch(e) {
                        case '"','\\','/'->b.append(e);
                        case 'b'->b.append('\b');
                        case 'f'->b.append('\f');
                        case 'n'->b.append('\n');
                        case 'r'->b.append('\r');
                        case 't'->b.append('\t');
                        case 'u'-> {
                            if(at+4>text.length())throw bad();
                            try {
                                b.append((char)Integer.parseInt(text.substring(at,at+4),16));
                            }
                            catch(NumberFormatException n) {
                                throw bad();
                            }
                            at+=4;
                        }
                        default->throw bad();
                    }
                }
                else b.append(c);
            }
            throw bad();
        }
    }
}
