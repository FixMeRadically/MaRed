package com.fixmer.genesis.technology.scripts;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Official NeoForge MDK snapshot, packaged locally. Creating a project executes no build code. */
public final class NeoForgeProjectTemplate {
    private NeoForgeProjectTemplate() {}
    public static final String GRADLE_VERSION="9.2.1";
    public static final String COMMIT="3bca8f1e0756c001e4b71f266c328d1568d29363";
    public static final String SOURCE="https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/"+COMMIT;
    private static final String ARCHIVE_SHA256="74f2297d902a00ce67954649eedeefdb475b92396ea2a78d20d23adea169babc";
    private static final String RESOURCE="neoforge-mdk-1.21.1.zip";
    public static boolean validId(String id){return id!=null&&id.matches("[a-z][a-z0-9_]{1,47}")&&!Set.of("minecraft","neoforge","mared","javafml").contains(id);}
    public static String entry(String id){validate(id);return "src/main/java/com/example/mod_"+id+"/ExampleMod.java";}
    private static void validate(String id){if(!validId(id))throw new IllegalArgumentException("Invalid or reserved mod id");}
    private static final class Bundle {static final Map<String,byte[]> FILES=load();}
    private static Map<String,byte[]> load(){
        try(var in=NeoForgeProjectTemplate.class.getResourceAsStream(RESOURCE)){
            if(in==null)throw new IOException("Official NeoForge MDK resource is missing; rebuild genesis-core");
            byte[] archive=in.readNBytes(2*1024*1024+1);
            if(archive.length>2*1024*1024||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)).equals(ARCHIVE_SHA256))throw new IOException("Official MDK integrity check failed");
            var files=new LinkedHashMap<String,byte[]>();int total=0;
            try(var zip=new ZipInputStream(new ByteArrayInputStream(archive))){
                for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                    String path=e.getName();
                    if(e.isDirectory()||path.startsWith("/")||path.contains("\\")||Arrays.asList(path.split("/")).contains("..")||files.size()>=64)throw new IOException("Invalid MDK entry");
                    byte[] data=zip.readNBytes(1024*1024+1);total+=data.length;
                    if(data.length>1024*1024||total>4*1024*1024||files.put(path,data)!=null)throw new IOException("MDK entry limit");
                }
            }
            if(!files.containsKey("gradle/wrapper/gradle-wrapper.jar")||!files.containsKey("build.gradle"))throw new IOException("Incomplete MDK");
            return Collections.unmodifiableMap(files);
        }catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException("Cannot load official NeoForge MDK",e);}
    }
    public static String wrapperProperties(){return new String(Bundle.FILES.get("gradle/wrapper/gradle-wrapper.properties"),StandardCharsets.UTF_8);}
    public static byte[] wrapperJar(){return Bundle.FILES.get("gradle/wrapper/gradle-wrapper.jar").clone();}
    /** Text files remain editable; wrapper bytes come directly from the upstream MDK. */
    public static Map<String,String> files(String id){
        validate(id);var out=new LinkedHashMap<String,String>();
        for(var e:Bundle.FILES.entrySet()){
            if(e.getKey().endsWith(".jar"))continue;
            String path=e.getKey().replace("com/example/examplemod/","com/example/mod_"+id+"/").replace("assets/examplemod/","assets/"+id+"/");
            String text=new String(e.getValue(),StandardCharsets.UTF_8);
            if(path.endsWith(".java")||path.endsWith(".json")||path.equals("gradle.properties"))text=text.replace("com.example.examplemod","com.example.mod_"+id).replace("examplemod",id);
            // Match the host's NeoForge baseline; retain the upstream build, plugin, mappings and runs.
            if(path.equals("gradle.properties"))text=text.replace("neo_version=21.1.259","neo_version=21.1.235").replace("mod_name=Example Mod","mod_name="+id);
            out.put(path,text);
        }
        out.put("genesis-project.properties","format=1\nlanguage=JAVA\nmod_version=1.0.0\nworkspace=imported_gradle\ngradle_task=build\n");
        out.put("GENESIS-MDK.md","# NeoForge MDK\n\nOfficial source: "+SOURCE+"\nArchive SHA-256: "+ARCHIVE_SHA256+"\n\nOnly mod id/package/name and NeoForge baseline (21.1.235, matching MaRed) are customized.\nOriginal TEMPLATE_LICENSE.txt, wrapper, scripts, example registration, Config, client events, localization, metadata templates, datagen and publishing configuration are included.\n\nUse JDK 21. In Scripts: Ctrl+Alt+S saves all, Ctrl+B builds. First build needs network access for Gradle, mappings and dependencies. Output: build/libs/"+id+"-1.0.0.jar.\nExternal Windows command: gradlew.bat build; Linux/macOS: ./gradlew build. runClient/runServer launch separate instances.\nThe generated project is independent of MaRed. Existing projects are never converted.\n");
        return Collections.unmodifiableMap(out);
    }
}
