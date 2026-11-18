package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded diagnostic history only. No process, network, deployment or recovery decisions. */
public final class RestartHistory97 {
    public static final Set<String> DURATIONS=Set.of("shutdown","prepare","spawn","launcher_gap","mod_loaded","resources_ready","world_loading","world_ready","title_ready","scene_total","world_load","total");
    private static final Set<String> REASONS=Set.of("","prepare_timeout","explicit_stop","client_cancelled","owner_closed","normal_exit_and_save_confirmed","save_exit_unconfirmed","restart_failed","awaiting_client_commit","awaiting_final_save_and_exit","final_save_and_exit_verified","handoff_not_confirmed");
    private static final int MAX_FILES=48,SCAN_LIMIT=160,MAX_BYTES=8192;
    public record Attempt(String kind,String id,String phase,String reason,long createdMs,long updatedMs,boolean legacy){}
    public record Cycle(String id,String kind,String mode,long createdMs,long updatedMs,Map<String,Long> durations){public Cycle{durations=Map.copyOf(durations);}public String readiness(){return durations.containsKey("world_ready")?"world":durations.containsKey("title_ready")?"menu":"unconfirmed";}}
    public record Snapshot(List<Attempt> attempts,List<Cycle> cycles,int unreadable,boolean limited){public Snapshot{attempts=List.copyOf(attempts);cycles=List.copyOf(cycles);}public static Snapshot empty(){return new Snapshot(List.of(),List.of(),0,false);}}
    private RestartHistory97(){}
    private static boolean uuid(String s){try{return s!=null&&UUID.fromString(s).toString().equals(s);}catch(RuntimeException bad){return false;}}
    private static void check(boolean value)throws IOException{if(!value)throw new IOException("HISTORY97_INVALID");}
    private static Path safe(Path path)throws IOException{
        path=path.toAbsolutePath().normalize();RuntimeFiles.safe(path);
        Path existing=path;while(existing!=null&&!Files.exists(existing,LinkOption.NOFOLLOW_LINKS))existing=existing.getParent();
        if(existing!=null)check(existing.toRealPath().equals(existing));
        return path;
    }
    private static Path directory(Path game)throws IOException{return safe(safe(game).resolve("config/mared/dev/history97"));}
    private static Properties readProperties(Path file)throws IOException{
        safe(file);check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)<=MAX_BYTES);
        try(var in=Files.newInputStream(file)){byte[] b=in.readNBytes(MAX_BYTES+1);check(b.length<=MAX_BYTES);return RecoveryConnection92.parse(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString());}
    }
    private static long time(Properties p,String key)throws IOException{try{long n=Long.parseLong(p.getProperty(key,""));check(n>=0&&n<=4_102_444_800_000L);return n;}catch(NumberFormatException bad){throw new IOException("HISTORY97_TIME");}}
    private static Set<String> phases(String kind){return kind.equals("restart")?Set.of("PREPARED","COMMITTED","CANCELLED","EXPIRED","DONE","HOLD"):Set.of("WAITING","COMMITTED","CONFIRMED","HOLD");}
    private static Attempt attempt(Properties p,boolean legacy)throws IOException{
        String kind=p.getProperty("kind"),id=p.getProperty("id"),phase=p.getProperty("phase");check(Set.of("restart","handoff").contains(kind)&&uuid(id)&&phases(kind).contains(phase));
        long created=time(p,"created_ms"),updated=time(p,"updated_ms");check(updated>=created);
        String reason=p.getProperty("reason","");if(!REASONS.contains(reason))reason="unknown";
        return new Attempt(kind,id,phase,reason,created,updated,legacy);
    }
    private static Cycle cycle(Properties p)throws IOException{
        String id=p.getProperty("id"),kind=p.getProperty("cycle_kind"),mode=p.getProperty("mode");check(uuid(id)&&Set.of("initial","restart","rollback","cds_fallback").contains(kind)&&Set.of("unrecorded","direct_cache","direct_config","gradle").contains(mode));
        long created=time(p,"created_ms"),updated=time(p,"updated_ms");check(updated>=created);Map<String,Long> values=new HashMap<>();
        for(String key:DURATIONS)if(p.containsKey("d."+key))try{long n=Long.parseLong(p.getProperty("d."+key));check(n>=0&&n<=86_400_000L);values.put(key,n);}catch(NumberFormatException bad){throw new IOException("HISTORY97_DURATION");}
        return new Cycle(id,kind,mode,created,updated,values);
    }
    private static boolean filename(Path path,String kind,String id){return path.getFileName().toString().equals(kind+"-"+id+".properties");}
    public static Snapshot read(Path game)throws IOException{
        Path dir=directory(game);List<Attempt> attempts=new ArrayList<>();List<Cycle> cycles=new ArrayList<>();Set<String> seen=new HashSet<>();int errors=0,scanned=0;boolean limited=false;
        if(Files.exists(dir,LinkOption.NOFOLLOW_LINKS)){
            check(Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS));
            try(var stream=Files.newDirectoryStream(dir)){
                for(Path path:stream){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(++scanned>SCAN_LIMIT){limited=true;break;}
                    if(!path.getFileName().toString().matches("(?:restart|handoff|cycle)-[a-f0-9-]{36}\\.properties"))continue;
                    try{Properties p=readProperties(path);check("97".equals(p.getProperty("schema")));String kind=p.getProperty("kind"),id=p.getProperty("id");check(filename(path,kind,id));
                        if("cycle".equals(kind))cycles.add(cycle(p));else{Attempt a=attempt(p,false);attempts.add(a);seen.add(a.kind()+a.id());}
                    }catch(IOException|RuntimeException invalid){errors++;}
                }
            }
        }
        for(String kind:List.of("restart","handoff")){
            Path path=safe(game.resolve("config/mared/dev/"+(kind.equals("restart")?"restart95-last.properties":"handoff96-last.properties")));
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))continue;
            try{Properties old=readProperties(path);check(old.getProperty("schema","").equals(kind.equals("restart")?"95":"96"));String id=old.getProperty(kind.equals("restart")?"request":"id");
                if(seen.contains(kind+id))continue;Properties p=new Properties();p.setProperty("kind",kind);p.setProperty("id",Objects.toString(id,""));p.setProperty("phase",old.getProperty(kind.equals("restart")?"state":"phase",""));p.setProperty("reason",old.getProperty("reason",""));long t=Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toMillis();p.setProperty("created_ms",Long.toString(t));p.setProperty("updated_ms",Long.toString(t));attempts.add(attempt(p,true));
            }catch(IOException|RuntimeException invalid){errors++;}
        }
        attempts.sort(Comparator.comparingLong(Attempt::updatedMs).reversed().thenComparing(Attempt::id));cycles.sort(Comparator.comparingLong(Cycle::updatedMs).reversed().thenComparing(Cycle::id));
        return new Snapshot(attempts.stream().limit(24).toList(),cycles.stream().limit(12).toList(),errors,limited);
    }
    public static synchronized void recordAttempt(Path game,String kind,String id,String phase,String reason){
        try{Properties p=new Properties();p.setProperty("kind",kind);p.setProperty("id",id);p.setProperty("phase",phase);p.setProperty("reason",REASONS.contains(reason)?reason:"unknown");persist(game,p);}catch(IOException|RuntimeException ignored){}
    }
    public static synchronized void recordCycle(Path game,String id,String kind,String mode,long created,Map<String,Long> values){
        try{Properties p=new Properties();p.setProperty("kind","cycle");p.setProperty("id",id);p.setProperty("cycle_kind",kind);p.setProperty("mode",mode);p.setProperty("created_ms",Long.toString(created));
            for(String key:DURATIONS)if(values.containsKey(key))p.setProperty("d."+key,Long.toString(values.get(key)));persist(game,p);
        }catch(IOException|RuntimeException ignored){}
    }
    private static void persist(Path game,Properties p)throws IOException{
        Path dir=directory(game);Files.createDirectories(dir);String kind=p.getProperty("kind"),id=p.getProperty("id");check(Set.of("restart","handoff","cycle").contains(kind)&&uuid(id));
        Path file=safe(dir.resolve(kind+"-"+id+".properties"));boolean newEntry=!Files.exists(file,LinkOption.NOFOLLOW_LINKS);long now=System.currentTimeMillis();
        if(!p.containsKey("created_ms")){
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){Properties old=readProperties(file);check(old.getProperty("schema","").equals("97")&&old.getProperty("id","").equals(id)&&old.getProperty("kind","").equals(kind));p.setProperty("created_ms",old.getProperty("created_ms",""));}
            else p.setProperty("created_ms",Long.toString(now));
        }
        p.setProperty("schema","97");p.setProperty("updated_ms",Long.toString(Math.max(now,time(p,"created_ms"))));if(kind.equals("cycle"))cycle(p);else attempt(p,false);
        // Bounded scan before any replacement. Unknown/linked files are never deleted.
        List<Path> files=new ArrayList<>();if(newEntry)try(var scan=Files.newDirectoryStream(dir)){for(Path path:scan){if(files.size()>=SCAN_LIMIT)throw new IOException("HISTORY97_DIRECTORY_LIMIT");files.add(path);}}
        Path tmp=Files.createTempFile(dir,".history97-",".tmp");try{
            try(var out=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){p.store(out,"History only; no permission to launch, restore or install.");}
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(tmp);}
        if(!newEntry)return;
        List<Map.Entry<Path,Long>> owned=new ArrayList<>();for(Path path:files)try{
            if(!path.getFileName().toString().matches("(?:restart|handoff|cycle)-[a-f0-9-]{36}\\.properties"))continue;Properties old=readProperties(path);String k=old.getProperty("kind"),i=old.getProperty("id");check(old.getProperty("schema","").equals("97")&&filename(path,k,i));long t=k.equals("cycle")?cycle(old).updatedMs():attempt(old,false).updatedMs();owned.add(Map.entry(path,t));
        }catch(IOException|RuntimeException ignored){}
        if(owned.stream().noneMatch(e->e.getKey().equals(file)))owned.add(Map.entry(file,time(p,"updated_ms")));
        owned.sort(Comparator.<Map.Entry<Path,Long>>comparingLong(Map.Entry::getValue).thenComparing(e->e.getKey().toString()));int remove=owned.size()-MAX_FILES;
        for(var old:owned){if(remove<=0)break;if(old.getKey().equals(file))continue;safe(old.getKey());Files.deleteIfExists(old.getKey());remove--;}
    }
}
