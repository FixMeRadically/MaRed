package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.*;import java.nio.channels.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.regex.Pattern;
/** Bounded private diagnostics, never installation/recovery authority. Saved before a replacement starts. */
public final class FailureReport101 {
 private static final Set<String> STATUS=Set.of("CAPTURED","RESTORED","ACTION_REQUIRED","RECOVERY_FAILED");
 private static final Set<String> LOGS=Set.of("console.log","game-latest.log","crash.log");
 public record Snapshot(String id,String status,String reason,boolean acknowledged,String text){}
 public static final class Capture {
  private final Deque<String> lines=new ArrayDeque<>();private int size;
  public synchronized void add(String line){String text=redact(line);if(text.length()>16384)text=DiagnosticText.prefix(text,16384);lines.addLast(text);size+=text.length()+1;while(lines.size()>2500||size>512*1024){String first=lines.removeFirst();size-=first.length()+1;}}
  synchronized String text(){return String.join("\n",lines)+"\n";}
 }
 private static final Pattern KEY=Pattern.compile("(?i)((?:access[_-]?token|refresh[_-]?token|client[_-]?secret|password|GENESIS_SUPERVISOR_TOKEN)[\\\"']?\\s*[=:]\\s*)");
 private static final Pattern ARG=Pattern.compile("(?i)(--(?:accessToken|refreshToken|password)(?:[\\t ]+|=))");
 /** Scan quoted values without recursive regex matching; preserve escaped/unterminated secrets. */
 private static String maskValues144(String text,Pattern prefix,boolean argument){
  var matcher=prefix.matcher(text);var out=new StringBuilder();int copied=0,search=0;
  while(matcher.find(search)){
   int start=matcher.end(),end=start;char quote=start<text.length()?text.charAt(start):0;
   boolean quoted=quote=='\"'||quote=='\'';boolean closed=false;
   if(quoted){
    end++;
    while(end<text.length()){
     char c=text.charAt(end);
     if(c=='\r'||c=='\n')break;
     end++;
     if(c==quote){closed=true;break;}
     if(c=='\\'&&end<text.length()&&text.charAt(end)!='\r'&&text.charAt(end)!='\n')end++;
    }
   }else{
    while(end<text.length()){
     char c=text.charAt(end);
     if(Character.isWhitespace(c)||!argument&&(c=='\"'||c=='\''||c==','||c==';'||c=='}'))break;
     end++;
    }
   }
   out.append(text,copied,start);
   if(end>start){if(quoted)out.append(quote);out.append("[REDACTED]");if(closed)out.append(quote);}
   copied=end;search=Math.max(end,start);
  }
  return out.append(text,copied,text.length()).toString();
 }
 public static String redact(String text){if(text==null)return "";return maskValues144(maskValues144(text,KEY,false),ARG,true).replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+","$1[REDACTED]");}
 private FailureReport101(){}
 private static String uuid(String id)throws IOException{try{if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();return id;}catch(RuntimeException e){throw new IOException("FAILURE101_ID");}}
 private static Path root(Path game){return game.toAbsolutePath().normalize().resolve("config/mared/dev/failures101");}
 private static void safe(Path path)throws IOException {for(Path p=path.toAbsolutePath().normalize();p!=null;p=p.getParent())if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(p)||!p.toRealPath().equals(p)))throw new IOException("FAILURE101_LINK");}
 private static void write(Path file,String text)throws IOException {safe(file);Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),".failure101-",".tmp");try{Files.writeString(temp,text,StandardCharsets.UTF_8);try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
 private static void write(Path file,Properties p)throws IOException {StringWriter out=new StringWriter();p.store(out,"Genesis failure diagnostic; no executable commands");write(file,out.toString());}
 private static Properties props(Path file)throws IOException {safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>4096)throw new IOException("FAILURE101_RECORD");Properties parsed=RecoveryConnection92.parse(Files.readString(file));Properties mutable=new Properties();mutable.putAll(parsed);return mutable;}
 private static String tail(Path file,int limit)throws IOException {
  safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return "";
  try(var channel=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
   long size=channel.size(),start=Math.max(0,size-limit);boolean partial=false;
   if(start>0){
    var previous=ByteBuffer.allocate(1);channel.position(start-1);
    if(channel.read(previous)==1){byte last=previous.get(0);partial=last!='\n'&&last!='\r';}
    else partial=true;
   }
   ByteBuffer data=ByteBuffer.allocate((int)Math.min(size,limit));channel.position(start);
   while(data.hasRemaining()&&channel.read(data)>0){}data.flip();
   if(partial){
    // A clipped line can contain a secret whose key/opening quote is outside the tail.
    while(data.hasRemaining()){byte next=data.get();if(next=='\n'||next=='\r')break;}
    if(data.hasRemaining()&&data.get(data.position())=='\n')data.get();
   }
   return (partial?"[Incomplete first log line omitted]\n":"")+redact(StandardCharsets.UTF_8.decode(data).toString());
  }
 }
 public static String capture(Path game,String launch,String transaction,String reason,Capture console,long spawnWall)throws IOException {
  uuid(launch);if(!StartupAbort101.reason(reason))reason="PROCESS_EXIT";Path root=root(game);safe(root);Files.createDirectories(root);Path folder=root.resolve(launch);safe(folder);Files.createDirectories(folder);
  write(folder.resolve("console.log"),console.text());
  Path latest=game.resolve("logs/latest.log");if(Files.isRegularFile(latest,LinkOption.NOFOLLOW_LINKS)&&Files.getLastModifiedTime(latest).toMillis()>=spawnWall)write(folder.resolve("game-latest.log"),tail(latest,2*1024*1024));
  Path crashes=game.resolve("crash-reports"),selected=null;long newest=spawnWall;if(Files.isDirectory(crashes,LinkOption.NOFOLLOW_LINKS)){safe(crashes);int n=0;try(var stream=Files.newDirectoryStream(crashes)){for(Path p:stream){if(++n>128)break;if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){long stamp=Files.getLastModifiedTime(p).toMillis();if(stamp>=newest){selected=p;newest=stamp;}}}}}if(selected!=null)write(folder.resolve("crash.log"),tail(selected,2*1024*1024));
  Properties p=new Properties();p.setProperty("schema","101");p.setProperty("id",launch);p.setProperty("status","CAPTURED");p.setProperty("reason",reason);p.setProperty("ack","false");p.setProperty("created",Long.toString(System.currentTimeMillis()));write(folder.resolve("report.properties"),p);
  Properties pointer=new Properties();pointer.setProperty("schema","101");pointer.setProperty("id",launch);write(root.resolve("latest.properties"),pointer);try{prune(root,launch);}catch(IOException retained){/* A cleanup failure cannot discard a saved report. */}return launch;
 }
 public static void status(Path game,String id,String state)throws IOException {uuid(id);if(!STATUS.contains(state))throw new IOException("FAILURE101_STATE");Path file=root(game).resolve(id).resolve("report.properties");Properties p=props(file);if(!id.equals(p.getProperty("id"))||!"101".equals(p.getProperty("schema")))throw new IOException("FAILURE101_RECORD");p.setProperty("status",state);p.setProperty("ack","false");write(file,p);}
 public static Snapshot read(Path game)throws IOException {
  Path root=root(game),index=root.resolve("latest.properties");safe(root);if(!Files.exists(index,LinkOption.NOFOLLOW_LINKS))return null;Properties pointer=props(index);if(!pointer.stringPropertyNames().equals(Set.of("schema","id"))||!"101".equals(pointer.getProperty("schema")))throw new IOException("FAILURE101_INDEX");String id=uuid(pointer.getProperty("id"));Path folder=root.resolve(id);Properties p=props(folder.resolve("report.properties"));
  if(!"101".equals(p.getProperty("schema"))||!id.equals(p.getProperty("id"))||!STATUS.contains(p.getProperty("status"))||!StartupAbort101.reason(p.getProperty("reason"))||!Set.of("true","false").contains(p.getProperty("ack")))throw new IOException("FAILURE101_RECORD");
  StringBuilder text=new StringBuilder();for(String name:List.of("console.log","crash.log","game-latest.log")){Path file=folder.resolve(name);if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))text.append("=== ").append(name).append(" ===\n").append(tail(file,90*1024)).append('\n');}
  return new Snapshot(id,p.getProperty("status"),p.getProperty("reason"),Boolean.parseBoolean(p.getProperty("ack")),text.toString());
 }
 public static void acknowledge(Path game,String id)throws IOException {uuid(id);Path file=root(game).resolve(id).resolve("report.properties");Properties p=props(file);if(!id.equals(p.getProperty("id")))throw new IOException("FAILURE101_RECORD");p.setProperty("ack","true");write(file,p);}
 private static void prune(Path root,String current)throws IOException {
  var dirs=new ArrayList<Path>();int scanned=0;try(var stream=Files.newDirectoryStream(root)){for(Path d:stream){if(++scanned>128)return;if(!Files.isDirectory(d,LinkOption.NOFOLLOW_LINKS)||d.getFileName().toString().equals(current))continue;try{String id=uuid(d.getFileName().toString());Properties p=props(d.resolve("report.properties"));if(!id.equals(p.getProperty("id"))||!"101".equals(p.getProperty("schema"))||!STATUS.contains(p.getProperty("status")))continue;boolean foreign=false;try(var files=Files.newDirectoryStream(d)){for(Path f:files)if(!LOGS.contains(f.getFileName().toString())&&!f.getFileName().toString().equals("report.properties")){foreign=true;break;}else safe(f);}if(!foreign)dirs.add(d);}catch(IOException unknown){}}}
  dirs.sort(Comparator.comparing(p->p.getFileName().toString()));for(int i=0;i<dirs.size()-7;i++){Path d=dirs.get(i);try(var files=Files.newDirectoryStream(d)){for(Path f:files)Files.delete(f);}Files.delete(d);}
 }
}
