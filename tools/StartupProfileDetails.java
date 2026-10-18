import java.io.*;
import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Source-file CLI: analyze an existing recording; no Minecraft/bootstrap rebuild required. */
public final class StartupProfileDetails {
    private static final Set<String> WAIT=Set.of("sun.nio.ch.Net.poll","sun.nio.ch.WEPoll.wait","sun.nio.ch.EPoll.wait","sun.nio.ch.KQueue.poll","sun.nio.fs.WindowsNativeDispatcher.GetQueuedCompletionStatus0","jdk.internal.misc.Unsafe.park","sun.misc.Unsafe.park","java.lang.Object.wait","java.lang.Object.wait0","java.lang.Thread.sleep","java.lang.Thread.sleep0","java.lang.Thread.yield","java.lang.Thread.yield0");
    private static final class ThreadStats {
        final long id;String role;long javaSamples,nativeSamples,waitSamples,classes;
        final Map<String,Long> javaStacks=new HashMap<>(),nativeStacks=new HashMap<>();
        ThreadStats(long id,String role){this.id=id;this.role=role;}
    }
    private static final class Phase {
        long javaSamples,nativeSamples,waitSamples,classes,gcs,pauseNanos;
        final Map<String,Long> javaMethods=new HashMap<>(),nativeMethods=new HashMap<>(),loaders=new HashMap<>(),packages=new HashMap<>();
        final Map<Long,ThreadStats> threads=new HashMap<>();
    }
    private StartupProfileDetails(){}
    public static void main(String[] args){
        try{
            if(args.length!=5)throw new IOException("Arguments");
            long mod=Long.parseLong(args[1]),resources=Long.parseLong(args[2]),ready=Long.parseLong(args[3]);
            if(mod<=0||resources<mod||ready<resources||ready>900000)throw new IOException("Milestones");
            System.out.println(summarize(Path.of(args[0]),mod,resources,ready,args[4].equals("all")));
        }catch(Exception error){System.err.println("Detailed profile could not be read; check recording completion, limits and JDK availability.");System.exit(2);}
    }
    private static String summarize(Path file,long mod,long resources,long ready,boolean all)throws IOException {
        for(Path p=file.toAbsolutePath();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked input");
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)<32||Files.size(file)>128L*1024*1024)throw new IOException("Size");
        long deadline=System.nanoTime()+20_000_000_000L,jvmStart=-1;int count=0;
        try(var recording=new RecordingFile(file)){while(recording.hasMoreEvents()){bound(++count,deadline);var event=recording.readEvent();if(event.getEventType().getName().equals("jdk.JVMInformation")){jvmStart=event.getLong("jvmStartTime");break;}}}
        if(jvmStart<=0)throw new IOException("Clock");
        Phase[] phases={new Phase(),new Phase(),new Phase()};long first=Long.MAX_VALUE,last=0;count=0;
        try(var recording=new RecordingFile(file)){
            while(recording.hasMoreEvents()){
                bound(++count,deadline);var event=recording.readEvent();long uptime=event.getStartTime().toEpochMilli()-jvmStart;
                first=Math.min(first,uptime);last=Math.max(last,event.getEndTime().toEpochMilli()-jvmStart);
                if(uptime<0||uptime>ready)continue;int index=uptime<mod?0:uptime<resources?1:2;Phase phase=phases[index];
                switch(event.getEventType().getName()){
                    case "jdk.ExecutionSample", "jdk.NativeMethodSample" -> {
                        boolean nativeSample=event.getEventType().getName().equals("jdk.NativeMethodSample");
                        if(nativeSample)phase.nativeSamples++;else phase.javaSamples++;
                        var thread=thread(phase,event.getThread("sampledThread"));if(nativeSample)thread.nativeSamples++;else thread.javaSamples++;
                        var stack=event.getStackTrace();if(stack==null||stack.getFrames().isEmpty())continue;
                        List<RecordedFrame> frames=stack.getFrames();String leaf=method(frames.get(0));
                        boolean waiting=nativeSample&&WAIT.contains(leaf);
                        if(waiting){phase.waitSamples++;thread.waitSamples++;}
                        if(nativeSample){add(phase.nativeMethods,leaf,2048);if(!waiting)add(thread.nativeStacks,stack(frames),512);}
                        else {add(phase.javaMethods,leaf,2048);add(thread.javaStacks,stack(frames),512);}
                    }
                    case "jdk.ClassLoad" -> {
                        phase.classes++;thread(phase,event.getThread()).classes++;
                        var clazz=event.getClass("loadedClass");if(clazz!=null)add(phase.packages,packageName(clazz.getName()),256);
                        RecordedClassLoader loader=event.getValue("definingClassLoader");
                        add(phase.loaders,loader==null||loader.getType()==null?"bootstrap":loader.getType().getName(),128);
                    }
                    case "jdk.GarbageCollection" -> phase.gcs++;
                    case "jdk.GCPhasePause" -> phase.pauseNanos+=event.getDuration().toNanos();
                    default -> { }
                }
            }
        }
        StringBuilder out=new StringBuilder("{\"schema\":2,\"focus\":").append(q(all?"all_phases":"startup")).append(",\"recording_first_uptime_ms\":").append(first==Long.MAX_VALUE?0:first).append(",\"recording_last_uptime_ms\":").append(last);
        out.append(",\"boundaries_ms\":{\"mod_loaded\":").append(mod).append(",\"resources_ready\":").append(resources).append(",\"world_ready\":").append(ready).append("},\"phases\":{");
        String[] names={"jvm_to_mod_loaded","mod_to_resources_ready","resources_to_world_ready"};
        for(int i=0;i<(all?3:1);i++){
            if(i>0)out.append(',');Phase phase=phases[i];out.append(q(names[i])).append(":{\"java_samples\":").append(phase.javaSamples).append(",\"native_samples\":").append(phase.nativeSamples).append(",\"recognized_native_wait_samples\":").append(phase.waitSamples).append(",\"class_load_events\":").append(phase.classes).append(",\"gc_count\":").append(phase.gcs).append(",\"gc_pause_ms\":").append(ms(phase.pauseNanos));
            out.append(",\"top_java_methods\":");map(out,phase.javaMethods,10);out.append(",\"top_native_methods\":");map(out,phase.nativeMethods,10);
            out.append(",\"class_load_by_loader\":");map(out,phase.loaders,8);out.append(",\"class_load_by_package\":");map(out,phase.packages,12);
            out.append(",\"tracked_threads\":").append(phase.threads.size()).append(",\"threads\":[");boolean comma=false;
            var sorted=phase.threads.values().stream().sorted(Comparator.comparingInt((ThreadStats t)->priority(t.role)).thenComparing(Comparator.comparingLong((ThreadStats t)->t.javaSamples+t.nativeSamples-t.waitSamples).reversed()).thenComparingLong(t->t.id)).limit(8).toList();
            for(var thread:sorted){
                if(comma)out.append(',');comma=true;out.append("{\"thread_id\":").append(thread.id).append(",\"role\":").append(q(thread.role)).append(",\"java_samples\":").append(thread.javaSamples).append(",\"native_samples\":").append(thread.nativeSamples).append(",\"recognized_native_wait_samples\":").append(thread.waitSamples).append(",\"class_load_events\":").append(thread.classes);
                out.append(",\"top_java_stacks\":");stacks(out,thread.javaStacks,thread.role.equals("main_or_render")?4:2);
                out.append(",\"top_native_non_wait_stacks\":");stacks(out,thread.nativeStacks,thread.role.equals("main_or_render")||thread.role.equals("early_display")?3:1);out.append('}');
            }
            out.append("]}");
        }
        return out.append("}}").toString();
    }
    private static int priority(String role){return switch(role){case "main_or_render" ->0;case "early_display" ->1;case "mod_loading" ->2;default ->3;};}
    private static ThreadStats thread(Phase phase,RecordedThread recorded){
        long id=recorded==null?-1:recorded.getJavaThreadId();String role=role(recorded==null?null:recorded.getJavaName());
        if(!phase.threads.containsKey(id)&&phase.threads.size()>=256){id=-1;role="other";}
        ThreadStats stats=phase.threads.get(id);if(stats==null){stats=new ThreadStats(id,role);phase.threads.put(id,stats);}else if(stats.role.equals("other")&&!role.equals("other"))stats.role=role;
        return stats;
    }
    private static String role(String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        if(n.equals("main")||n.equals("render thread"))return "main_or_render";
        if(n.startsWith("earlydisplay")||n.startsWith("early-display")||n.equals("early display"))return "early_display";
        if(n.startsWith("modloading")||n.startsWith("mod-loading"))return "mod_loading";
        if(n.startsWith("forkjoinpool"))return "fork_join";
        if(n.startsWith("worker-"))return "minecraft_worker";
        if(n.startsWith("httpclient")||n.startsWith("netty")||n.startsWith("nioeventloop")||n.equals("filesystemwatchservice"))return "network_or_watch";
        if(n.startsWith("jfr")||n.startsWith("jmx")||n.startsWith("monitor "))return "jfr_or_monitor";
        return "other";
    }
    private static String method(RecordedFrame frame){var method=frame.getMethod();return method.getType().getName()+"."+method.getName();}
    private static String stack(List<RecordedFrame> frames){
        List<String> out=new ArrayList<>();int size=frames.size();
        for(int i=0;i<Math.min(size,10);i++)out.add(method(frames.get(i)));
        if(size>14){out.add("[intermediate frames omitted]");for(int i=size-4;i<size;i++)out.add(method(frames.get(i)));}
        else for(int i=10;i<size;i++)out.add(method(frames.get(i)));
        return String.join("\n",out);
    }
    private static String packageName(String name){String[] parts=name.split("\\.");return String.join(".",Arrays.copyOf(parts,Math.min(3,Math.max(1,parts.length-1))));}
    private static void bound(int count,long deadline)throws IOException {if(count>2_000_000||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("Limits");}
    private static void add(Map<String,Long> map,String key,int limit){if(!map.containsKey(key)&&map.size()>=limit)key="[other]";map.merge(key,1L,Long::sum);}
    private static double ms(long nanos){return Math.round(nanos/1000.0)/1000.0;}
    private static String q(String value){
        StringBuilder out=new StringBuilder("\"");for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){case '"' ->out.append("\\\"");case '\\' ->out.append("\\\\");case '\n' ->out.append("\\n");case '\r' ->out.append("\\r");case '\t' ->out.append("\\t");default ->{if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}}}return out.append('"').toString();
    }
    private static void map(StringBuilder out,Map<String,Long> values,int limit){out.append('{');boolean comma=false;for(var entry:values.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(limit).toList()){if(comma)out.append(',');comma=true;out.append(q(entry.getKey())).append(':').append(entry.getValue());}out.append('}');}
    private static void stacks(StringBuilder out,Map<String,Long> values,int limit){out.append('[');boolean comma=false;for(var entry:values.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(limit).toList()){if(comma)out.append(',');comma=true;out.append("{\"samples\":").append(entry.getValue()).append(",\"frames\":[");boolean frameComma=false;for(String frame:entry.getKey().split("\n")){if(frameComma)out.append(',');frameComma=true;out.append(q(frame));}out.append("]}");}out.append(']');}
}
