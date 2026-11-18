package com.fixmer.genesis.technology.server.workbench156;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;import java.nio.charset.*;import java.io.*;import java.util.*;
/** Explicit local server scope. Config and settings catalogs are disjoint; edits use compare-and-swap. */
public final class ServerFiles156 {
 public record Entry(String path,String group,String kind){}
 public record Document(String path,String text,String hash){}
 private final Path root;private List<String> roots=List.of();
 private static final Set<String> SETTINGS=Set.of("server.properties","ops.json","whitelist.json","banned-players.json","banned-ips.json");
 private static final Set<String> EXT=Set.of("toml","json","properties","yml","yaml","cfg","conf","ini");
 public ServerFiles156(Path selected)throws IOException{root=FirstLaunchFiles.safe(selected);if(!Files.isDirectory(root)||!Files.isRegularFile(root.resolve("server.properties")))throw new IOException("SERVER156_SELECT_FOLDER");refreshRoots();}
 public Path root(){return root;}
 private Path resolve(String relative)throws IOException{
  if(relative==null||relative.isBlank()||relative.indexOf('\\')>=0||relative.indexOf(':')>=0||relative.startsWith("/")||Arrays.stream(relative.split("/",-1)).anyMatch(x->x.isBlank()||x.equals(".")||x.equals("..")))throw new IOException("SERVER156_PATH");
  Path path=root.resolve(relative).normalize();if(!path.startsWith(root)||path.equals(root))throw new IOException("SERVER156_PATH");
  FirstLaunchFiles.safe(path);return path;
 }
 private static String worldName(String world)throws IOException{if(world.isEmpty()||world.length()>100||world.equals(".")||world.equals("..")||world.chars().anyMatch(c->Character.isISOControl(c)||c==92||c=='/'||c==':'))throw new IOException("SERVER156_WORLD_PATH");return world;}
 private String world()throws IOException{var properties=new Properties();String source=new String(FirstLaunchFiles.read(resolve("server.properties"),2*1024*1024),StandardCharsets.UTF_8);properties.load(new StringReader(source.startsWith("\uFEFF")?source.substring(1):source));return worldName(properties.getProperty("level-name","world"));}
 private void refreshRoots()throws IOException{roots=List.of("config","defaultconfigs",world()+"/serverconfig");}
 private List<String> configRoots(){return roots;}
 private boolean allowed(String p)throws IOException{return p!=null&&(SETTINGS.contains(p)||configRoots().stream().anyMatch(x->p.startsWith(x+"/"))&&EXT.contains(p.substring(p.lastIndexOf('.')+1).toLowerCase(Locale.ROOT)));}
 public List<Entry> catalog(boolean settings)throws IOException{
  refreshRoots();var result=new ArrayList<Entry>();if(settings){for(String name:SETTINGS)if(Files.isRegularFile(resolve(name)))result.add(new Entry(name,"Minecraft","settings"));}
  else for(String folder:configRoots()){
   Path directory=resolve(folder);if(!Files.isDirectory(directory))continue;
   try(var paths=Files.walk(directory,6)){
    var entries=paths.iterator();int scanned=0;
    while(entries.hasNext()){
     if(++scanned>4096)throw new IOException("SERVER156_CATALOG_LIMIT");
     Path file=entries.next();
    if(Files.isSymbolicLink(file))throw new IOException("SERVER156_LINK");
    FirstLaunchFiles.safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
    String relative=root.relativize(file).toString().replace('\\','/');if(!allowed(relative))continue;
    String part=directory.relativize(file).toString().replace('\\','/');String group=part.contains("/")?part.substring(0,part.indexOf('/')):part.replaceFirst("[-.].*$","");result.add(new Entry(relative,group,"config"));
    if(result.size()>1024)throw new IOException("SERVER156_CATALOG_LIMIT");
   }}
  }
  result.sort(Comparator.comparing(Entry::group).thenComparing(Entry::path));return List.copyOf(result);
 }
 public Document read(String relative)throws IOException{refreshRoots();if(!allowed(relative))throw new IOException("SERVER156_PATH");byte[] bytes=FirstLaunchFiles.read(resolve(relative),2*1024*1024);String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();return new Document(relative,text,FirstLaunchFiles.sha(bytes));}
 public record Draft(String base,String text){}
 private Path draftPath(String path)throws IOException{refreshRoots();if(!allowed(path))throw new IOException("SERVER156_PATH");return root.resolve(".genesis/config-drafts156").resolve(FirstLaunchFiles.sha(path.getBytes(StandardCharsets.UTF_8))+".draft");}
 public synchronized void remember(Document before,String text)throws IOException{
  Path target=draftPath(before.path());FirstLaunchFiles.safe(target);
  if(text.equals(before.text())){Files.deleteIfExists(target);return;}
  if(text.getBytes(StandardCharsets.UTF_8).length>2*1024*1024)throw new IOException("SERVER156_FILE_LIMIT");Files.createDirectories(target.getParent());FirstLaunchFiles.privateMode(target.getParent());
  FirstLaunchFiles.write(target,(before.hash()+"\n"+text).getBytes(StandardCharsets.UTF_8),true);
 }
 public synchronized Draft draft(String path)throws IOException{
  Path target=draftPath(path);if(!Files.exists(target))return null;String text=new String(FirstLaunchFiles.read(target,2*1024*1024+100),StandardCharsets.UTF_8);int split=text.indexOf('\n');if(split!=64||!text.substring(0,split).matches("[a-f0-9]{64}"))throw new IOException("SERVER156_DRAFT_FORMAT");return new Draft(text.substring(0,split),text.substring(split+1));
 }
 public synchronized Document save(Document before,String text)throws IOException{
  if(text.getBytes(StandardCharsets.UTF_8).length>2*1024*1024)throw new IOException("SERVER156_FILE_LIMIT");if(before.path.equals("server.properties")){var props=new Properties();props.load(new StringReader(text.startsWith("\uFEFF")?text.substring(1):text));worldName(props.getProperty("level-name","world"));for(String name:List.of("server-port","query.port","rcon.port"))if(props.containsKey(name)){try{int port=Integer.parseInt(props.getProperty(name));if(port<1||port>65535)throw new NumberFormatException();}catch(NumberFormatException invalid){throw new IOException("SERVER156_INVALID_PORT");}}for(String name:List.of("online-mode","white-list","enable-rcon","enable-query"))if(props.containsKey(name)&&!Set.of("true","false").contains(props.getProperty(name)))throw new IOException("SERVER156_INVALID_BOOLEAN");}
  Path lockPath=FirstLaunchFiles.safe(root.resolve(".genesis/config-write156.lock"));
  Files.createDirectories(lockPath.getParent());
  try(var channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
      var lock=channel.tryLock()){
   if(lock==null)throw new IOException("SERVER156_CONFIG_BUSY");
   return saveLocked(before,text);
  }catch(OverlappingFileLockException busy){throw new IOException("SERVER156_CONFIG_BUSY",busy);}
 }
 private Document saveLocked(Document before,String text)throws IOException{
  var current=read(before.path);if(!current.hash.equals(before.hash))throw new IOException("SERVER156_CHANGED_RELOAD");if(text.equals(current.text))return current;
  Path backup=root.resolve(".genesis/config-backups156").resolve(UUID.randomUUID().toString());Files.createDirectories(backup);FirstLaunchFiles.write(backup.resolve("before"),current.text.getBytes(StandardCharsets.UTF_8),false);FirstLaunchFiles.write(backup.resolve("path"),before.path.getBytes(StandardCharsets.UTF_8),false);
  if(!read(before.path).hash.equals(before.hash))throw new IOException("SERVER156_CHANGED_RELOAD");FirstLaunchFiles.write(resolve(before.path),text.getBytes(StandardCharsets.UTF_8),true);return read(before.path);
 }
}
