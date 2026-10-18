package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Streaming, bounded JFR summary. Never emits JVM arguments, environment, file contents,
 * thread names, full file paths, or the JVMInformation payload used for the clock. */
public final class StartupProfileReport {
    private static final class Phase {
        long javaSamples,nativeSamples,classes,gcs,pauseNanos,readNanos;
        final Map<String,Long> methods=new HashMap<>(),groups=new HashMap<>(),reads=new HashMap<>();
        final PriorityQueue<SlowClass> slow=new PriorityQueue<>(Comparator.comparingLong(SlowClass::nanos));
    }
    private record SlowClass(String name,long nanos){}
    private StartupProfileReport(){}
    public static void main(String[] args){
        try{
            if(args.length!=4)throw new IOException("Expected recording and three phase boundaries");
            Path file=Path.of(args[0]);long mod=Long.parseLong(args[1]),resources=Long.parseLong(args[2]),ready=Long.parseLong(args[3]);
            if(mod<=0||resources<mod||ready<resources||ready>900000)throw new IOException("Incomplete or inconsistent milestones");
            System.out.println(summarize(file,mod,resources,ready));
        }catch(Exception e){System.err.println("Startup profile unavailable: "+(e instanceof IOException?"recording is not ready, unsupported or exceeds limits":"invalid profile data"));System.exit(2);}
    }
    static String summarize(Path file,long mod,long resources,long ready)throws IOException {
        RuntimeFiles.safe(file);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)<32||Files.size(file)>128L*1024*1024)throw new IOException("Recording size");
        long deadline=System.nanoTime()+20_000_000_000L,jvmStart=-1;int events=0;
        try(var recording=new RecordingFile(file)){
            while(recording.hasMoreEvents()){
                bounded(++events,deadline);var e=recording.readEvent();
                if(e.getEventType().getName().equals("jdk.JVMInformation")){jvmStart=e.getLong("jvmStartTime");break;}
            }
        }
        if(jvmStart<=0)throw new IOException("JVM clock missing");
        Phase[] phases={new Phase(),new Phase(),new Phase()};long first=Long.MAX_VALUE,last=0;events=0;
        try(var recording=new RecordingFile(file)){
            while(recording.hasMoreEvents()){
                bounded(++events,deadline);var e=recording.readEvent();long uptime=e.getStartTime().toEpochMilli()-jvmStart;
                first=Math.min(first,uptime);last=Math.max(last,e.getEndTime().toEpochMilli()-jvmStart);
                if(uptime<0||uptime>ready)continue;
                int index=uptime<mod?0:uptime<resources?1:2;Phase phase=phases[index];String type=e.getEventType().getName();
                switch(type){
                    case "jdk.ExecutionSample", "jdk.NativeMethodSample" -> {
                        if(type.equals("jdk.ExecutionSample"))phase.javaSamples++;else phase.nativeSamples++;
                        var stack=e.getStackTrace();if(stack==null||stack.getFrames().isEmpty())continue;
                        var frames=stack.getFrames();var method=frames.get(0).getMethod();
                        increment(phase.methods,method.getType().getName()+"."+method.getName(),2048);
                        String group="jdk_other";
                        for(var frame:frames){String found=group(frame.getMethod().getType().getName());if(found!=null){group=found;break;}}
                        increment(phase.groups,group,16);
                    }
                    case "jdk.ClassLoad" -> {
                        phase.classes++;var clazz=e.getClass("loadedClass");long duration=e.getDuration().toNanos();
                        if(clazz!=null){phase.slow.add(new SlowClass(clazz.getName(),duration));if(phase.slow.size()>10)phase.slow.poll();}
                    }
                    case "jdk.GarbageCollection" -> phase.gcs++;
                    case "jdk.GCPhasePause" -> phase.pauseNanos+=e.getDuration().toNanos();
                    case "jdk.FileRead" -> {
                        long duration=e.getDuration().toNanos();phase.readNanos+=duration;
                        String path=e.getString("path"),name="[other files]";
                        if(path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".jar"))try{name=Path.of(path).getFileName().toString();}catch(InvalidPathException ignored){}
                        increment(phase.reads,name,duration,256);
                    }
                    default -> { }
                }
            }
        }
        StringBuilder json=new StringBuilder("{\"schema\":1,\"recording_first_uptime_ms\":").append(first==Long.MAX_VALUE?0:first).append(",\"recording_last_uptime_ms\":").append(last);
        json.append(",\"boundaries_ms\":{\"mod_loaded\":").append(mod).append(",\"resources_ready\":").append(resources).append(",\"world_ready\":").append(ready).append("},\"phases\":{");
        String[] names={"jvm_to_mod_loaded","mod_to_resources_ready","resources_to_world_ready"};
        for(int i=0;i<phases.length;i++){
            if(i>0)json.append(',');Phase p=phases[i];json.append(quote(names[i])).append(":{\"java_samples\":").append(p.javaSamples).append(",\"native_samples\":").append(p.nativeSamples)
                .append(",\"class_load_events\":").append(p.classes).append(",\"gc_count\":").append(p.gcs).append(",\"gc_pause_ms\":").append(ms(p.pauseNanos)).append(",\"slow_file_read_ms\":").append(ms(p.readNanos));
            json.append(",\"sample_groups\":");map(json,p.groups,16,false);
            json.append(",\"top_sampled_methods\":");map(json,p.methods,12,false);
            json.append(",\"slow_reads_by_jar_ms\":");map(json,p.reads,8,true);
            json.append(",\"slow_class_loads\":[");boolean comma=false;
            for(var slow:p.slow.stream().sorted(Comparator.comparingLong(SlowClass::nanos).reversed()).toList()){
                if(comma)json.append(',');comma=true;json.append("{\"class\":").append(quote(slow.name())).append(",\"duration_ms\":").append(ms(slow.nanos())).append('}');
            }
            json.append("]}");
        }
        return json.append("}}").toString();
    }
    private static void bounded(int events,long deadline)throws IOException {if(events>2_000_000||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("Profile limits");}
    private static void increment(Map<String,Long> map,String key,int limit){increment(map,key,1,limit);}
    private static void increment(Map<String,Long> map,String key,long value,int limit){if(!map.containsKey(key)&&map.size()>=limit)key="[other]";map.merge(key,value,Long::sum);}
    private static String group(String name){
        if(name.startsWith("org.spongepowered."))return "mixin";
        if(name.startsWith("org.objectweb.asm."))return "bytecode_asm";
        if(name.startsWith("net.neoforged.")||name.startsWith("cpw.mods."))return "neoforge_loader";
        if(name.startsWith("java.util.zip.")||name.startsWith("java.util.jar.")||name.startsWith("jdk.internal.jimage."))return "archive_io";
        if(name.startsWith("net.minecraft."))return "minecraft";
        if(name.startsWith("com.fixmer."))return "mared_genesis";
        return null;
    }
    private static double ms(long nanos){return Math.round(nanos/1000.0)/1000.0;}
    private static String quote(String value){return SupervisorHttpServer.quote(value.length()>300?value.substring(0,297)+"...":value);}
    private static void map(StringBuilder json,Map<String,Long> values,int limit,boolean millis){
        json.append('{');boolean comma=false;
        for(var entry:values.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(limit).toList()){
            if(comma)json.append(',');comma=true;json.append(quote(entry.getKey())).append(':');if(millis)json.append(ms(entry.getValue()));else json.append(entry.getValue());
        }
        json.append('}');
    }
}
