package com.fixmer.genesis.technology.scripts;

import java.util.*;

/** Bounded parser for plain Gradle tasks --all output; never executes project code. */
public final class GradleTasks {
    public static final int MAX_TASKS=512,MAX_CHARS=256*1024,MAX_LINE=2048;
    public record Task(String path,String description,String group) {
        public Task {Objects.requireNonNull(description);Objects.requireNonNull(group);if(!GradleBuildRunner.validTask(path))throw new IllegalArgumentException("Unsupported Gradle task path");}
    }
    public record Catalog(List<Task> tasks,boolean limited) {
        public Catalog {tasks=List.copyOf(tasks);}
        public List<Task> filter(String query) {
            String q=Objects.requireNonNullElse(query,"").strip().toLowerCase(Locale.ROOT);
            if(q.isEmpty())return tasks;
            String[] words=q.split("\\s+");return tasks.stream().filter(t->{String hay=(t.path()+" "+t.description()+" "+t.group()).toLowerCase(Locale.ROOT);for(String word:words)if(!hay.contains(word))return false;return true;}).toList();
        }
    }
    private GradleTasks() {}
    public static Catalog parse(String output,boolean outputTruncated) {
        Objects.requireNonNull(output);boolean limited=outputTruncated||output.length()>MAX_CHARS;
        String text=output.substring(0,Math.min(output.length(),MAX_CHARS)).replaceAll("\u001B\\[[0-9;?]*[ -/]*[@-~]","");
        var found=new LinkedHashMap<String,Task>();String heading="",group="";
        for(String raw:text.split("\\R",-1)) {
            if(raw.length()>MAX_LINE){limited=true;heading="";group="";continue;}
            String line=raw.strip();
            if(line.matches("-{3,}")) {String h=heading.toLowerCase(Locale.ROOT);group=h.endsWith(" tasks")||h.equals("tasks")||h.startsWith("задачи ")||h.endsWith(" задачи")?heading:"";heading="";continue;}
            if(line.isEmpty()){heading="";continue;}
            if(!group.isEmpty()) {
                int separator=line.indexOf(" - ");String path=separator<0?line:line.substring(0,separator);
                if(GradleBuildRunner.validTask(path)) {
                    Task t=new Task(path,separator<0?"":line.substring(separator+3),group);
                    if(found.containsKey(path)){if(found.get(path).description().isEmpty())found.put(path,t);}
                    else if(found.size()<MAX_TASKS)found.put(path,t);else limited=true;
                    heading="";continue;
                }
            }
            heading=line;group="";
        }
        return new Catalog(found.values().stream().sorted(Comparator.comparing(Task::path)).toList(),limited);
    }
}
