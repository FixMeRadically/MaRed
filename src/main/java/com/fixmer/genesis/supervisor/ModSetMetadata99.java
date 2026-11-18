package com.fixmer.genesis.supervisor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
/** Bounded foreign declarations. Does not execute a mod or pretend to be NeoForge's full TOML/resolver. */
final class ModSetMetadata99 {
 record Descriptor(Set<String> ids,List<ModJarMetadata.Dependency> dependencies) {}
 static Set<String> ids(Path file)throws IOException{return read(file).ids();}
 static Descriptor read(Path file)throws IOException {
  try(var jar=new JarFile(file.toFile(),false)){
   var entry=jar.getJarEntry("META-INF/neoforge.mods.toml");if(entry==null)return new Descriptor(Set.of(),List.of());
   byte[] bytes;try(var in=jar.getInputStream(entry)){bytes=in.readNBytes(256*1024+1);}if(bytes.length>256*1024)throw new IOException("SET99_METADATA_LIMIT");
   var tables=ModMetadataText.parse(bytes,false);var ids=new HashSet<String>();
   for(var t:tables)if(t.name().equals("[[mods]]")){
    String id=t.values().getOrDefault("modId", "");
    if(!id.matches("[a-z][a-z0-9_]{1,63}")||!ids.add(id))throw new IOException("SET99_FOREIGN_MOD_ID");
   }
   var dependencies=new ArrayList<ModJarMetadata.Dependency>();
   for(var t:tables)for(String owner:ids)if(t.name().equals("[[dependencies."+owner+"]]")){
    var d=t.values();String id=d.getOrDefault("modId", "");
    if(!id.matches("[a-z][a-z0-9_]{1,63}"))throw new IOException("SET99_DEPENDENCY_ID");
    dependencies.add(new ModJarMetadata.Dependency(id,d.getOrDefault("versionRange",""),d.getOrDefault("type","required"),d.getOrDefault("side","BOTH")));
   }
   return new Descriptor(Set.copyOf(ids),List.copyOf(dependencies));
  }
 }
}
