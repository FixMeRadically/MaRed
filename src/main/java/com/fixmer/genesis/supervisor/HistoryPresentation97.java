package com.fixmer.genesis.supervisor;
import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;import java.util.function.BiFunction;
/** Whitelisted presentation model. Raw properties and exception messages never reach UI/clipboard. */
public final class HistoryPresentation97 {
    public record Line(String key,List<String> args,boolean heading){public Line{args=List.copyOf(args);}}
    private static Line line(String key,String...args){return new Line("mared.history97."+key,List.of(args),false);}
    private static Line heading(String key,String...args){return new Line("mared.history97."+key,List.of(args),true);}
    private static String at(long ms,ZoneId zone){return ms==0?"?":DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss",Locale.ROOT).format(Instant.ofEpochMilli(ms).atZone(zone));}
    private static String seconds(long ms){return String.format(Locale.ROOT,"%.2f",ms/1000.0);}
    public static List<Line> lines(int tab,RestartHistory97.Snapshot snapshot,ZoneId zone,boolean identifiers){
        List<Line> out=new ArrayList<>();out.add(line("history_notice"));
        if(snapshot.unreadable()>0)out.add(line("partial",Integer.toString(snapshot.unreadable())));if(snapshot.limited())out.add(line("limited"));
        if(tab==0){
            if(snapshot.attempts().isEmpty())out.add(line("no_attempts"));else{out.add(heading("latest_attempt"));attempt(out,snapshot.attempts().get(0),zone,identifiers);}
            if(snapshot.cycles().isEmpty())out.add(line("no_cycles"));else{out.add(heading("latest_cycle"));cycle(out,snapshot.cycles().get(0),zone,identifiers);}
        }else if(tab==1){
            if(snapshot.attempts().isEmpty())out.add(line("no_attempts"));int index=0;for(var a:snapshot.attempts()){out.add(heading("attempt_number",Integer.toString(++index)));attempt(out,a,zone,identifiers);}
        }else{
            out.add(line("parallel_notice"));if(snapshot.cycles().isEmpty())out.add(line("no_cycles"));int index=0;for(var c:snapshot.cycles()){out.add(heading("cycle_number",Integer.toString(++index)));cycle(out,c,zone,identifiers);}
        }
        return List.copyOf(out);
    }
    private static void attempt(List<Line> out,RestartHistory97.Attempt a,ZoneId zone,boolean ids){
        out.add(line("kind."+a.kind()));out.add(line("phase."+a.kind()+"."+a.phase()));if(!a.reason().isEmpty())out.add(line("reason."+a.reason()));
        out.add(line("advice."+a.phase()));out.add(line("recorded",at(a.updatedMs(),zone)));if(a.legacy())out.add(line("legacy"));if(ids)out.add(line("id",a.id()));
    }
    private static void cycle(List<Line> out,RestartHistory97.Cycle c,ZoneId zone,boolean ids){
        out.add(line("cycle_kind."+c.kind()));out.add(line("readiness."+c.readiness()));out.add(line("mode."+c.mode()));Map<String,Long>d=c.durations();
        if(d.containsKey("total")&&!c.readiness().equals("unconfirmed"))out.add(line("timing.total",seconds(d.get("total"))));
        for(String key:List.of("shutdown","prepare","spawn","launcher_gap","mod_loaded","scene_total"))if(d.containsKey(key))out.add(line("timing."+key,seconds(d.get(key))));
        if(d.containsKey("resources_ready")&&d.containsKey("mod_loaded")){
            if(d.get("resources_ready")>=d.get("mod_loaded"))out.add(line("timing.resources",seconds(d.get("resources_ready")-d.get("mod_loaded"))));else out.add(line("inconsistent"));
        }
        if(d.containsKey("world_ready")&&d.containsKey("world_loading")){
            if(d.get("world_ready")>=d.get("world_loading"))out.add(line("timing.world",seconds(d.get("world_ready")-d.get("world_loading"))));else out.add(line("inconsistent"));
        }
        out.add(line("recorded",at(c.updatedMs(),zone)));if(ids)out.add(line("id",c.id()));
    }
    public static String clipboard(RestartHistory97.Snapshot snapshot,BiFunction<String,Object[],String> translate){
        var compact=new RestartHistory97.Snapshot(snapshot.attempts().stream().limit(6).toList(),snapshot.cycles().stream().limit(4).toList(),snapshot.unreadable(),snapshot.limited());
        StringBuilder out=new StringBuilder(translate.apply("mared.history97.report_title",new Object[0])).append('\n');
        for(int tab:List.of(1,2))for(Line line:lines(tab,compact,ZoneOffset.UTC,true))out.append(translate.apply(line.key(),line.args().toArray())).append('\n');
        out.append(translate.apply("mared.history97.report_utc",new Object[0]));return DiagnosticText.prefix(out.toString(),16000);
    }
    private HistoryPresentation97(){}
}
