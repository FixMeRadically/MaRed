package com.fixmer.mared.gui2.settings;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.technology.editor.WorkbenchPreferences;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;
import java.util.function.Consumer;

/** Scripts pages in the existing settings screen. Edits stay in a draft until Save. */
public final class ScriptsSettingsTab implements MaredSettingsTab {
    private record Row(String label,Runnable action) {}
    private final String page;
    private final WorkbenchPreferences draft;
    private final Consumer<String> tools;
    private List<Row> rows=List.of();
    private int scroll,contentHeight;
    public ScriptsSettingsTab(String page,WorkbenchPreferences draft,Consumer<String> tools){this.page=page;this.draft=draft;this.tools=tools;}
    @Override public String id(){return "scripts_"+page;}
    @Override public String displayName(){return tr("settings_page_"+page);}
    @Override public int accentColor(){return MaredThemeRegistry.active().accent;}
    private static String tr(String key){return MaredLang.get("mared.scripts."+key);}
    private static String bool(boolean value){return MaredLang.get("mared.scripts.settings_"+(value?"on":"off"));}
    private Row toggle(String key,boolean value,Runnable action){return new Row(tr(key)+": "+bool(value),action);}
    private List<Row> build(){
        var r=new ArrayList<Row>();
        switch(page){
            case "layout" -> {
                r.add(toggle("settings_background",draft.background,()->draft.background=!draft.background));
                r.add(toggle("settings_animation",draft.animation,()->draft.animation=!draft.animation));
                r.add(toggle("settings_depth",draft.depth,()->draft.depth=!draft.depth));
                r.add(toggle("settings_files",draft.filesOpen,()->draft.filesOpen=!draft.filesOpen));
                r.add(toggle("settings_logs",draft.logsOpen,()->draft.logsOpen=!draft.logsOpen));
                r.add(toggle("settings_help",draft.helpOpen,()->draft.helpOpen=!draft.helpOpen));
                r.add(toggle("settings_help_docked",draft.helpDocked,()->draft.helpDocked=!draft.helpDocked));
                r.add(new Row(tr("settings_files_size")+": "+Math.round(draft.filesRatio*100)+"%",()->draft.filesRatio=next(draft.filesRatio)));
                r.add(new Row(tr("settings_help_size")+": "+Math.round(draft.helpRatio*100)+"%",()->draft.helpRatio=next(draft.helpRatio)));
                r.add(new Row(tr("settings_logs_size")+": "+Math.round(draft.logsRatio*100)+"%",()->draft.logsRatio=next(draft.logsRatio)));
                r.add(new Row(tr("settings_reset_layout"),()->{draft.filesRatio=.18;draft.helpRatio=.29;draft.logsRatio=.24;draft.filesOpen=true;draft.logsOpen=false;draft.helpOpen=false;draft.helpDocked=false;draft.helpWindowX=.18;draft.helpWindowY=.10;draft.helpWindowW=.70;draft.helpWindowH=.80;}));
            }
            case "editor" -> {
                r.add(toggle("settings_reveal",draft.scriptsAutoReveal,()->draft.scriptsAutoReveal=!draft.scriptsAutoReveal));
                r.add(toggle("settings_snippet",draft.scriptsJavaSnippet,()->draft.scriptsJavaSnippet=!draft.scriptsJavaSnippet));
            }
            case "logs" -> {
                r.add(toggle("settings_keep_logs",draft.scriptsKeepLogs,()->draft.scriptsKeepLogs=!draft.scriptsKeepLogs));
                r.add(toggle("settings_open_logs",draft.scriptsAutoOpenLogs,()->draft.scriptsAutoOpenLogs=!draft.scriptsAutoOpenLogs));
                r.add(new Row(tr("settings_log_limit")+": "+draft.scriptsLogCapacity,()->draft.scriptsLogCapacity=draft.scriptsLogCapacity<1024?1024:draft.scriptsLogCapacity<2048?2048:256));
                for(String name:List.of("TRACE","DEBUG","INFO","WARN","ERROR"))r.add(filter(tr("settings_level")+" "+name,name,draft.scriptsHiddenLevels));
                for(String name:List.of("editor","java","gradle","server","tools"))r.add(filter(tr("settings_category")+" "+name,name,draft.scriptsHiddenCategories));
            }
            case "tools" -> {
                r.add(new Row(tr("jdk_home"),()->tools.accept("java_home")));
                r.add(new Row(tr("wrapper_jar"),()->tools.accept("wrapper_jar")));
                r.add(new Row(tr("build_timeout_minutes"),()->tools.accept("build_timeout_minutes")));
                r.add(new Row(tr("build_max_workers"),()->tools.accept("build_max_workers")));
                r.add(new Row(tr("dependencies"),()->tools.accept("dependencies")));
                r.add(new Row(tr("check_build_tools"),()->tools.accept("check")));
            }
            case "keys" -> {
                for(String label:List.of("search_files:Ctrl+P","search_text:Ctrl+F","search_project_text:Ctrl+Shift+F","search_goto_line:Ctrl+G","search_open_files:Ctrl+O")){
                    var parts=label.split(":");r.add(new Row(tr(parts[0])+"   "+parts[1],null));
                }
                r.add(new Row(tr("settings_palette_keys"),null));
            }
        }
        return r;
    }
    private Row filter(String label,String name,Set<String> hidden){return new Row(label+": "+bool(!hidden.contains(name)),()->{if(!hidden.remove(name))hidden.add(name);});}
    private static double next(double value){return value>=.49?.1:Math.round((value+.05)*100)/100.0;}
    @Override public void render(GuiGraphics g,Font font,int x,int y,int w,int h,SettingsContext ctx,int mx,int my){
        rows=build();contentHeight=rows.size()*26+8;scroll=Math.max(0,Math.min(scroll,Math.max(0,contentHeight-h)));
        var theme=MaredThemeRegistry.active();g.enableScissor(x,y,x+w,y+h);
        try{for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int top=y+i*26-scroll;boolean hover=mx>=x&&mx<x+w&&my>=top&&my<top+22;
            g.fill(x,top,x+w,top+22,hover&&row.action()!=null?theme.bgHover:theme.bgPanelRaised);
            g.drawString(font,font.plainSubstrByWidth(row.label(),Math.max(1,w-16)),x+8,top+7,theme.text,false);
        }}finally{g.disableScissor();}
    }
    @Override public boolean mouseClicked(double mx,double my,int button,int x,int y,int w,int h,SettingsContext ctx){
        if(button!=0||mx<x||mx>=x+w||my<y||my>=y+h)return false;
        int offset=(int)my-y+scroll,index=offset/26;
        if(offset%26<22&&index<rows.size()&&rows.get(index).action()!=null)rows.get(index).action().run();return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta,int x,int y,int w,int h,SettingsContext ctx){
        if(mx<x||mx>=x+w||my<y||my>=y+h)return false;
        scroll=Math.max(0,Math.min(Math.max(0,contentHeight-h),scroll+(delta<0?26:delta>0?-26:0)));return true;
    }
}
