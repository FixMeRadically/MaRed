package com.fixmer.mared.technology.editor;

import com.fixmer.mared.commands.runner.MaredFileRunner;
import com.fixmer.mared.technology.runtime.FileRun;
import com.fixmer.mared.services.logging.LogEntry;
import com.fixmer.mared.services.logging.LogSettings;
import com.fixmer.mared.gui2.framework.components.console.MaredLogPanel;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import com.fixmer.mared.services.logging.LogService;

/** Session-owned run selection/history. All methods are invoked on the client thread. */
public final class EditorRuns {
    private final List<FileRun> history = new ArrayList<>();
    private final IdentityHashMap<FileRun, Long> forwarded = new IdentityHashMap<>();
    private FileRun current;
    private int selected = -1;
    private boolean allLogs = true;
    private FileRun displayed;
    private long displayedRevision = -1;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    public boolean canStart() { return current == null || current.snapshot().terminal() || current.snapshot().state() == FileRun.State.BACKGROUND; }
    private FileRun stopTarget() { return !allLogs && selected >= 0 ? history.get(selected) : current; }
    public boolean canStop() { var target=stopTarget(); return target != null && !target.snapshot().terminal() && !target.stopRequested(); }
    public void start(String text, String title) {
        if (!canStart()) return;
        current = MaredFileRunner.start(text, title);
        history.add(current); while (history.size() > 8) {var evicted=history.remove(0);evicted.requestStop();forwarded.remove(evicted); }
        selected = history.size()-1; allLogs = false;
    }
    public void stop() { var target=stopTarget(); if (target != null) target.requestStop(); }
    public void close() { for(var run:history) run.requestStop(); history.clear(); forwarded.clear(); current=null; displayed=null; }
    public String status() {
        if (current == null) return "";
        var s = current.snapshot();
        return s.state().name() + " · " + s.steps() + " steps · " + s.elapsedMillis()/1000 + "s · " + s.backgroundResources() + " resources";
    }
    public String logCaption() {
        if (allLogs || selected < 0) return "All logs";
        var s=history.get(selected).snapshot();
        return "Run " + (selected+1) + " / " + history.size() + " · " + s.title() + " · " + s.state();
    }
    public void toggleAllLogs() { allLogs=!allLogs; if(selected<0)allLogs=true; displayedRevision=-1; }
    public void previous() { if(!history.isEmpty()){selected=Math.floorMod(selected-1,history.size());allLogs=false;displayedRevision=-1;} }
    public void next() { if(!history.isEmpty()){selected=Math.floorMod(selected+1,history.size());allLogs=false;displayedRevision=-1;} }
    public void update(MaredLogPanel panel) {
        for (var run : history) {
            run.snapshot(); long last = forwarded.getOrDefault(run, 0L);
            var lines = run.journal().snapshot();
            if (!lines.isEmpty() && lines.get(0).sequence() > last+1)
                LogService.get().addStructured(LogSettings.Level.WARN,"run","Run output dropped before display: " + (lines.get(0).sequence()-last-1),run.id());
            for(var entry:lines) if(entry.sequence()>last){
                LogService.get().addStructured(LogSettings.parseLevel(entry.message()),"run",entry.message(),run.id());
                last=entry.sequence();
            }
            forwarded.put(run,last);
        }
        if (allLogs || selected < 0) {
            if(displayed!=null){panel.setLocalEntries(null);displayed=null;displayedRevision=-1;} return;
        }
        var run=history.get(selected); run.snapshot(); long revision=run.journal().revision();
        if (displayed==run && displayedRevision==revision) return;
        var entries=new ArrayList<LogEntry>();
        if(run.journal().dropped()>0)entries.add(new LogEntry("",System.currentTimeMillis(),LogSettings.Level.WARN,"run","Older run lines omitted: "+run.journal().dropped(),run.id()));
        for(var entry:run.journal().snapshot()) entries.add(new LogEntry(TIME.format(Instant.ofEpochMilli(entry.timeMillis())),entry.timeMillis(),LogSettings.parseLevel(entry.message()),"run",entry.message(),run.id()));
        panel.setLocalEntries(entries, run.journal()::clear); displayed=run; displayedRevision=revision;
    }
}
