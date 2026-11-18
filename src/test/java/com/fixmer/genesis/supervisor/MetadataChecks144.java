package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.jar.*;
public final class MetadataChecks144 {
 static int count;static final String BASE="modLoader=\"javafml\"\n[[mods]]\nmodId=\"example\"\nversion=\"1\"\n";
 static Path jar(byte[] bytes)throws Exception {Path p=Files.createTempFile("metadata144-",".jar");try(var out=new JarOutputStream(Files.newOutputStream(p))){out.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));out.write(bytes);out.closeEntry();}return p;}
 static void candidate(String text,boolean accepted)throws Exception {Path p=jar(text.getBytes(StandardCharsets.UTF_8));try{boolean ok=true;try{ModJarMetadata.read(p);}catch(IOException e){ok=false;}if(ok!=accepted)throw new AssertionError("candidate="+ok+": "+text);count++;}finally{Files.delete(p);}}
 public static void main(String[] args)throws Exception{
  candidate(BASE,true);candidate(BASE.replace("javafml","bad")+"modLoader=\"javafml\"\n",false);
  candidate("modLoader=\"bad\"\n"+BASE,false);candidate(BASE.replace("version=\"1\"","version=\"1\" + \"2\""),false);
  candidate(BASE+"version=\"2\"\n",false);candidate(BASE+"description='''unfinished\n",false);
  candidate(BASE.replace("version=\"1\"","description=\"text with ''' inside\"\nversion=\"1\""),true);
  candidate(BASE+"description='''line\nline'''\n",true);candidate(BASE+"description=\"\"\"line\nline\"\"\"\n",true);
  candidate(BASE+"description='''closed''' junk\n",false);candidate(BASE+"displayName=\"unclosed\n",false);
  candidate(BASE.replace("version=\"1\"","version=\"${file.jarVersion}\""),false);
  candidate(BASE.replace("example","mared"),false);candidate(BASE.replace("version=\"1\"","version=\"\""),false);
  Path p=jar((BASE+"[[dependencies.example]]\nmodId=\"minecraft\"\nversionRange=\"[1,2)\"\n[[dependencies.unrelated]]\nmodId=\"missing\"\n").getBytes(StandardCharsets.UTF_8));
  try{var info=ModJarMetadata.read(p);if(info.dependencies().size()!=1||!info.dependencies().get(0).id().equals("minecraft"))throw new AssertionError("unrelated candidate dependency");count++;if(ModSetMetadata99.read(p).dependencies().size()!=1)throw new AssertionError("foreign dependency owner");count++;}finally{Files.delete(p);}
  p=jar((BASE+"[[mods]]\nmodId=\"second\"\nversion=\"2\"\n").getBytes(StandardCharsets.UTF_8));try{if(ModSetMetadata99.ids(p).size()!=2)throw new AssertionError("foreign multiple mods");count++;}finally{Files.delete(p);}
  p=jar(BASE.replace("version=\"1\"","version=\"${file.jarVersion}\"").getBytes(StandardCharsets.UTF_8));
  try{if(!ModSetMetadata99.ids(p).contains("example"))throw new AssertionError("installed foreign version placeholder must not be resolved by preflight");count++;}finally{Files.delete(p);}
  byte[] invalid=Arrays.copyOf(BASE.getBytes(StandardCharsets.UTF_8),BASE.length()+2);invalid[invalid.length-2]=(byte)0xc3;invalid[invalid.length-1]=0x28;p=jar(invalid);
  try{try{ModJarMetadata.read(p);throw new AssertionError("invalid UTF8 accepted");}catch(IOException expected){count++;}try{ModSetMetadata99.read(p);throw new AssertionError("foreign invalid UTF8 accepted");}catch(IOException expected){count++;}}finally{Files.delete(p);}
  System.out.println("MetadataChecks144: "+count+" passed");
 }
}
