package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.genesis.technology.scripts.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Actual files, bounded wire decoder, journal transactions and permission-filtered hub queries. */
public final class WorkspaceToolsChecks126 {
    private static int checks;
    private static final UUID O=new UUID(126,1),D=new UUID(126,2);
    private static void ok(boolean value){if(!value)throw new AssertionError("Tools126 #"+checks);checks++;}
    private interface Work{void run()throws Exception;}
    private static void reject(Work work)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError("Rejection missing #"+checks);}
    private static Map<String,String> call(WorkspaceHub122 h,UUID who,String op,String p,Map<String,String> values)throws Exception{var q=new TreeMap<>(values);q.put("request",UUID.randomUUID().toString());q.put("op",op);q.put("project",p);return h.execute(who,true,"Player",q);}
    private static String rev(WorkspaceHub122 h,String p)throws Exception{return call(h,O,"VIEW",p,Map.of()).get("revision");}
    private static void grant(WorkspaceHub122 h,String p,String role,String prefix,String deny,String hidden)throws Exception{call(h,O,"MEMBER",p,Map.of("target",D.toString(),"role",role,"prefix",prefix,"deny",deny,"hidden",hidden,"rev",rev(h,p)));}
    private static void approve(WorkspaceHub122 h,String p,String paths)throws Exception{var a=call(h,O,"PREVIEW",p,Map.of("paths",paths,"rev",rev(h,p)));call(h,O,"APPROVE",p,Map.of("hash",a.get("hash"),"rev",a.get("revision")));}
    private static String compressed(byte[] raw)throws Exception{var b=new ByteArrayOutputStream();try(var z=new DeflaterOutputStream(b)){z.write(raw);}return Base64.getEncoder().encodeToString(b.toByteArray());}
    private static void imports(Path root)throws Exception{
        ok(WorkspaceProject126.unpack(WorkspaceProject126.pack(Map.of())).isEmpty());
        ok(WorkspaceProject126.unpack(WorkspaceProject126.pack(Map.of("boundary.txt","x".repeat(16384)))).get("boundary.txt").length()==16384);
        var text=Map.of("build.gradle","plugins { id 'java' }\n","src/Test.java","Привет 😀\nHello\n");String packed=WorkspaceProject126.pack(text);ok(WorkspaceProject126.unpack(packed).equals(text));
        for(var bad:List.of(Map.of("../secret","x"),Map.of("CON.txt","x"),Map.of("a.txt","x","A.txt","x"),Map.of("a","x","a/b","x"),Map.of("a/b","x","a","x"),Map.of("a.txt","\u0000"),Map.of("a.txt","x".repeat(16385)),Map.of("a.txt","\uD800")))reject(()->WorkspaceProject126.pack(bad));
        reject(()->WorkspaceProject126.unpack("!"));reject(()->WorkspaceProject126.unpack(packed+"\n"));reject(()->WorkspaceProject126.unpack("A".repeat(27000)));reject(()->WorkspaceProject126.unpack(compressed(new byte[WorkspaceProject126.MAX_RAW+1])));
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){out.writeInt(2);for(int i=0;i<2;i++){out.writeUTF("a.txt");out.writeInt(0);}}reject(()->WorkspaceProject126.unpack(compressed(bytes.toByteArray())));
        bytes.reset();try(var out=new DataOutputStream(bytes)){out.writeInt(1);out.writeUTF("a.txt");out.writeInt(1);out.writeByte(0xFF);}reject(()->WorkspaceProject126.unpack(compressed(bytes.toByteArray())));
        Path input=Files.createDirectories(root.resolve("input/src"));Files.writeString(input.resolve("Test.java"),"Ж😀");Files.write(root.resolve("input/image.png"),new byte[]{1,2});Files.createDirectories(root.resolve("input/build/libs"));Files.writeString(root.resolve("input/build/libs/generated.java"),"output");Files.createDirectories(root.resolve("input/.git"));Files.writeString(root.resolve("input/.git/secret.txt"),"hidden");
        var scan=WorkspaceProject126.scan(root.resolve("input"));ok(scan.files().equals(Map.of("src/Test.java","Ж😀")));ok(scan.skipped().contains("build/"));ok(scan.skipped().contains(".git/"));ok(scan.skipped().contains("image.png"));
        Files.write(input.resolve("Bad.java"),new byte[]{(byte)255});reject(()->WorkspaceProject126.scan(root.resolve("input")));Files.delete(input.resolve("Bad.java"));Files.writeString(input.resolve("Big.java"),"x".repeat(16385));reject(()->WorkspaceProject126.scan(root.resolve("input")));Files.delete(input.resolve("Big.java"));
        var template=WorkspaceProject126.template("testmod");ok(template.containsKey("build.gradle"));ok(template.containsKey(NeoForgeProjectTemplate.entry("testmod")));ok(template.get("gradle.properties").contains("neo_version=21.1.264"));ok(!template.containsKey("gradlew"));ok(template.containsKey(".gitignore"));ok(WorkspaceProject126.unpack(WorkspaceProject126.pack(template)).equals(template));reject(()->WorkspaceProject126.template("mared"));
        Path wrapper=WorkspaceProject126.wrapper(root.resolve("wrapper"));BuildTools.validateWrapper(wrapper);ok(WorkspaceProject126.wrapper(root.resolve("wrapper")).equals(wrapper));Files.write(wrapper,new byte[]{1});reject(()->WorkspaceProject126.wrapper(root.resolve("wrapper")));ok(Arrays.equals(Files.readAllBytes(wrapper),new byte[]{1}));
        String p;
        try(var hub=new WorkspaceHub122(root.resolve("hub"))){
            p=call(hub,O,"CREATE","",Map.of("text","Project tools")).get("project");String revision=rev(hub,p);
            var imported=call(hub,O,"IMPORT_TEXT126",p,Map.of("data",packed,"rev",revision));ok(imported.get("imported").equals("2"));ok(Long.parseLong(imported.get("revision"))==Long.parseLong(revision)+1);
            reject(()->call(hub,O,"IMPORT_TEXT126",p,Map.of("data",packed,"rev",revision)));String stable=rev(hub,p);final String collisionRevision=stable;
            reject(()->call(hub,O,"IMPORT_TEXT126",p,Map.of("data",WorkspaceProject126.pack(Map.of("a.txt","must not exist","build.gradle","overwrite")),"rev",collisionRevision)));ok(rev(hub,p).equals(stable));ok(!call(hub,O,"VIEW",p,Map.of()).get("files").contains("a.txt"));
            grant(hub,p,"DEVELOPER","z","CREATE","");stable=rev(hub,p);String deniedRevision=stable;
            reject(()->call(hub,D,"IMPORT_TEXT126",p,Map.of("data",WorkspaceProject126.pack(Map.of("a.txt","not created","z/file.txt","denied")),"rev",deniedRevision)));ok(rev(hub,p).equals(stable));ok(!call(hub,O,"VIEW",p,Map.of()).get("files").contains("a.txt"));
            grant(hub,p,"DEVELOPER","src","","CODE");reject(()->call(hub,D,"IMPORT_TEXT126",p,Map.of("data",WorkspaceProject126.pack(Map.of("src/Hidden.java","no")),"rev",rev(hub,p))));
            var hidden=call(hub,D,"SEARCH126",p,Map.of("query","Привет","matchCase","false"));ok(hidden.get("hits").isEmpty());reject(()->call(hub,D,"CHANGE126",p,Map.of("path","src/Test.java")));
            grant(hub,p,"DEVELOPER","","","");var found=call(hub,D,"SEARCH126",p,Map.of("query","hello","matchCase","false"));ok(found.get("hits").startsWith("src/Test.java|2|Hello"));ok(call(hub,D,"SEARCH126",p,Map.of("query","hello","matchCase","true")).get("hits").isEmpty());
            reject(()->call(hub,D,"SEARCH126",p,Map.of("query","","matchCase","false")));reject(()->call(hub,D,"SEARCH126",p,Map.of("query","x","matchCase","FALSE")));
            var change=call(hub,D,"CHANGE126",p,Map.of("path","src/Test.java"));ok(change.get("beforeText").isEmpty());ok(change.get("afterText").contains("😀"));
            var wire=new TreeMap<>(change);wire.put("request",UUID.randomUUID().toString());wire.put("online","");ok(WorkspaceCodec122.decode(WorkspaceCodec122.encode(wire,WorkspaceCodec122.RESPONSE),WorkspaceCodec122.RESPONSE).equals(wire));
            approve(hub,p,"build.gradle\nsrc/Test.java");change=call(hub,D,"CHANGE126",p,Map.of("path","src/Test.java"));ok(change.get("beforeHash").equals(change.get("afterHash")));
            call(hub,O,"IMPORT_TEXT126",p,Map.of("data",WorkspaceProject126.pack(Map.of("many.txt","find\n".repeat(120))),"rev",rev(hub,p)));var many=call(hub,D,"SEARCH126",p,Map.of("query","find","matchCase","false"));ok(many.get("hits").lines().count()==100);ok(many.get("searchMore").equals("true"));
            grant(hub,p,"VIEWER","","","");reject(()->call(hub,D,"IMPORT_TEXT126",p,Map.of("data",WorkspaceProject126.pack(Map.of("no.txt","no")),"rev",rev(hub,p))));reject(()->call(hub,D,"CHANGE126",p,Map.of("path","src/Test.java")));
            String second=call(hub,O,"CREATE","",Map.of("text","Template")).get("project");var created=call(hub,O,"TEMPLATE126",second,Map.of("id","newmod","rev",rev(hub,second)));ok(Integer.parseInt(created.get("imported"))==template.size());reject(()->call(hub,O,"BUILD_SOURCE",second,Map.of()));
            String before=rev(hub,second);reject(()->call(hub,O,"TEMPLATE126",second,Map.of("id","newmod","rev",before)));ok(rev(hub,second).equals(before));
            approve(hub,second,String.join("\n",WorkspaceProject126.template("newmod").keySet()));var source=call(hub,O,"BUILD_SOURCE",second,Map.of());var download=new WorkspaceSources125.Download(source.get("sourceHash"),source.get("sourceFiles"));while(download.next()!=null){var entry=download.next();int offset=download.offset();var chunk=call(hub,O,"BUILD_CHUNK",second,Map.of("hash",download.hash(),"path",entry.path(),"offset",Integer.toString(offset)));download.accept(entry.path(),offset,chunk.get("chunk"));}ok(download.verified().size()==template.size());
        }
        try(var hub=new WorkspaceHub122(root.resolve("hub"))){ok(call(hub,O,"SEARCH126",p,Map.of("query","Привет","matchCase","true")).get("hits").contains("src/Test.java|1|"));}
    }
    private static void diffs(){
        var changed=WorkspaceDiff126.compare("a\nb\nc","a\nnew\nc");ok(changed.lines().stream().anyMatch(l->l.kind()=='-'&&l.text().equals("b")));ok(changed.lines().stream().anyMatch(l->l.kind()=='+'&&l.text().equals("new")));ok(!changed.truncated());ok(WorkspaceDiff126.compare("","").lines().isEmpty());ok(WorkspaceDiff126.compare("","😀").lines().get(0).kind()=='+');
        Random random=new Random(126);for(int round=0;round<80;round++){String a=String.join("\n",random.ints(8,0,4).mapToObj(Integer::toString).toList()),b=String.join("\n",random.ints(8,0,4).mapToObj(Integer::toString).toList());var diff=WorkspaceDiff126.compare(a,b);ok(String.join("\n",diff.lines().stream().filter(l->l.kind()!='+').map(WorkspaceDiff126.Line::text).toList()).equals(a));ok(String.join("\n",diff.lines().stream().filter(l->l.kind()!='-').map(WorkspaceDiff126.Line::text).toList()).equals(b));}
        var limited=WorkspaceDiff126.compare("a\n".repeat(1000),"b\n".repeat(1000));ok(limited.truncated());ok(limited.lines().size()==500);
    }
    public static void main(String[] args)throws Exception{Path root=Files.createTempDirectory("Workspace tools126 ");try{imports(root);diffs();}finally{try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}System.out.println("Workspace126 checks passed: "+checks+"; real atomic imports, templates, wrapper integrity, ACL/search/diff, source transfer and journal replay; no Minecraft rendering.");}
}
