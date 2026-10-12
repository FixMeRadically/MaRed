package com.fixmer.mared.technology.editor;

import com.fixmer.genesis.technology.editor.EditorLayout;
import com.fixmer.genesis.technology.editor.EditorLayout.Rect;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.gui2.runtime.RuntimeProvider;
import com.fixmer.mared.gui2.studio.StudioSession;
import com.fixmer.mared.gui2.studio.action.StudioActions;
import com.fixmer.mared.gui2.studio.panels.workspace.WorkspaceComponent;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Session-owned Logic workbench. Adapts existing document/actions without copying their storage or save logic. */
public final class EditorWorkbench implements Disposable {
    private record Button(Rect bounds,String label,String hint,Runnable action,boolean floating) {}
    private static final String[] TABS={"Commands","Scripts","Events / Triggers","Blueprint","Behavior / States","Variables / Data","Network","Sandbox"};
    private final StudioSession session;
    private final WorkbenchPreferences prefs=WorkbenchPreferences.load();
    private final CommandHelpBrowser help;
    private final com.fixmer.mared.technology.scripts.ScriptProjectEditor scripts;
    private MaredComponent handbook(){return selected==1||showSharedProject?scripts.guide():help;}
    private final ScriptLinksPanel links;
    private boolean showLinks;
    private boolean showProjectSearch,showSharedProject;
    private final ProjectSearchPanel projectSearch;
    private final com.fixmer.mared.gui2.studio.events.SubscriptionGroup searchSubscriptions=new com.fixmer.mared.gui2.studio.events.SubscriptionGroup();
    private final List<Button> buttons=new ArrayList<>();
    private EditorLayout.Frame frame;
    private Rect helpWindow;
    private double dragMouseX,dragMouseY;
    private Rect dragWindow;
    private int width,height,selected,railScroll,drag,settingsScroll;
    private boolean settings,buildingHelpWindow;
    private double phase;
    private MaredComponent captured;
    private net.minecraft.client.gui.components.EditBox searchQuery;
    private Rect searchBounds;
    private String pluginPanelId;
    private com.fixmer.mared.gui2.docking.DockPanel pluginPanel(){return session.controller().extensionPanels().stream().filter(p->p.id().equals(pluginPanelId)).findFirst().orElse(null);}
    private void nextPluginPanel(){showSharedProject=false;scripts.setReadOnly(false);var panels=session.controller().extensionPanels();int current=-1;for(int i=0;i<panels.size();i++)if(panels.get(i).id().equals(pluginPanelId))current=i;pluginPanelId=current+1<panels.size()?panels.get(current+1).id():null;showLinks=false;showProjectSearch=false;if(selected==4){selected=1;prefs.selectedTab=1;}clearFocus();}
    public EditorWorkbench(StudioSession session){
        this.session=session;
        selected=prefs.selectedTab;showSharedProject=selected>=2;
        var controller=session.controller();
        controller.workspacePanel().workspaceComponent().setSpanResolver(com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver.INSTANCE);
        controller.explorerPanel().explorerComponent().setFilesOnly(true);
        controller.consolePanel().consoleComponent().logPanel().setCompact(true);
        help=new CommandHelpBrowser(controller.bus(),controller.inspectorPanel().inspectorComponent());
        session.focusManager().attachTo(help);
        links=new ScriptLinksPanel(controller.bus(),()->{selected=0;prefs.selectedTab=0;showLinks=false;showProjectSearch=false;pluginPanelId=null;clearFocus();});
        links.setAi(selected==4);session.focusManager().attachTo(links);
        projectSearch=new ProjectSearchPanel(hit->workspace().openSearchHit(hit),this::closeProjectSearch);
        session.focusManager().attachTo(projectSearch);
        searchSubscriptions.add(controller.bus().subscribe(com.fixmer.mared.gui2.studio.events.StudioEvents.RequestProjectSearchEvent.class,event->openProjectSearch()));
        scripts=new com.fixmer.mared.technology.scripts.ScriptProjectEditor(session.overlayManager(),session.actionContext()::log);
        scripts.preferences(prefs);session.focusManager().attachTo(scripts);session.focusManager().attachTo(scripts.filesPane());session.focusManager().attachTo(scripts.guide());session.focusManager().attachTo(scripts.logsPane());
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.new_project","mared.scripts.new_project","",ctx->!ctx.commandsActive()&&!scripts.readOnly(),ctx->scripts.newProject()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.new_file","mared.scripts.new_file","Ctrl+N",ctx->!ctx.commandsActive()&&!scripts.readOnly(),ctx->scripts.newFile()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.save","mared.scripts.save","Ctrl+S",ctx->!ctx.commandsActive()&&!scripts.readOnly(),ctx->scripts.save()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.close","mared.scripts.close","",ctx->!ctx.commandsActive()&&!scripts.readOnly(),ctx->scripts.closeDocument()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.run_java","mared.scripts.launch_primary","Ctrl+R",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canLaunchPrimary(),ctx->{if(prefs.scriptsAutoOpenLogs)prefs.logsOpen=true;scripts.launchPrimary();}));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.stop_java","mared.scripts.stop_java","Ctrl+Shift+R",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canStopPrimary(),ctx->scripts.stopPrimary()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.entry_point","mared.scripts.entry_point","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canRunJava(),ctx->scripts.configureEntryPoint()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.copy_output","mared.scripts.copy_output","Ctrl+Shift+L",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.hasRunOutput(),ctx->scripts.copyRunOutput()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.libraries_path","mared.scripts.libraries_path","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canUseLocalJavaInputs(),ctx->scripts.copyLibrariesPath()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.resources_path","mared.scripts.resources_path","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canUseLocalJavaInputs(),ctx->scripts.copyResourcesPath()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.export_java","mared.scripts.export_java","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canExportJava(),ctx->scripts.exportJava()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.init_gradle","mared.scripts.gradle_init","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canConfigureGradle(),ctx->scripts.initializeGradle()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.jdk_home","mared.scripts.jdk_home","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canConfigureBuildTools(),ctx->scripts.configureBuildTool("java_home")));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.wrapper_jar","mared.scripts.wrapper_jar","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canConfigureBuildTools(),ctx->scripts.configureBuildTool("wrapper_jar")));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.gradle_task","mared.scripts.gradle_task","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canConfigureGradle(),ctx->scripts.configureGradleTask()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.build_gradle","mared.scripts.build_gradle","Ctrl+B",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canBuildGradle(),ctx->buildScript()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.save_all","mared.scripts.save_all","Ctrl+Alt+S",ctx->!ctx.commandsActive()&&!scripts.readOnly(),ctx->scripts.saveAll()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.dependencies","mared.scripts.dependencies","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canBuildGradle(),ctx->scripts.manageDependencies()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.check_build_tools","mared.scripts.check_build_tools","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canConfigureBuildTools(),ctx->scripts.checkBuildTools()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.reveal_file","mared.scripts.reveal_file","Ctrl+Shift+E",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canRevealFile(),ctx->scripts.revealActiveFile()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.collapse_folders","mared.scripts.collapse_folders","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canBrowseFiles(),ctx->scripts.collapseFolders()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.expand_folders","mared.scripts.expand_folders","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canBrowseFiles(),ctx->scripts.expandFolders()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.new_neoforge","mared.scripts.new_neoforge","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canConfigureBuildTools(),ctx->scripts.newNeoForgeProject()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.import_folder","mared.scripts.import_folder","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canImportGradle(),ctx->scripts.browseImportFolder()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.import_minecraft","mared.scripts.import_minecraft","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canImportGradle(),ctx->scripts.importMinecraftMod()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.minecraft_info","mared.scripts.minecraft_project_info","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.hasProject(),ctx->scripts.showMinecraftProjectInfo()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.new_folder","mared.scripts.new_folder","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canManageFiles(),ctx->scripts.newFolder()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.rename","mared.scripts.rename","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canManageFiles(),ctx->scripts.renameSelected()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.delete","mared.scripts.delete","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canManageFiles(),ctx->scripts.deleteSelected()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.import_gradle","mared.scripts.import_gradle","",ctx->!ctx.commandsActive()&&!scripts.readOnly()&&scripts.canImportGradle(),ctx->scripts.importGradleProject()));
        session.actionContext().setCommandsActive(selected!=1);
    }
    private void buildScript(){if(scripts.canBuildGradle()){if(prefs.scriptsAutoOpenLogs)prefs.logsOpen=true;scripts.buildGradle();}}
    private void runScript(){boolean available=scripts.canRunJava();scripts.runJava();if(available&&prefs.scriptsAutoOpenLogs)prefs.logsOpen=true;}
    private WorkspaceComponent workspace(){return session.controller().workspacePanel().workspaceComponent();}
    private MaredComponent files(){if(selected==1||showSharedProject)return scripts.filesPane();return session.controller().explorerPanel().component();}
    private MaredComponent logs(){if(selected==1||showSharedProject)return scripts.logsPane();return session.controller().consolePanel().component();}
    public void tick(float dt){scripts.tick();if(prefs.animation&&!MaredSettings.isReducedMotion())phase=(phase+Math.max(0,Math.min(.1,dt))*.18)%(Math.PI*2);}
    public void suspended(){if(scripts.javaRunning())scripts.stopJava();drag=0;captured=null;help.cancelInteraction();links.cancelInteraction();projectSearch.cancelInteraction();session.pointerManager().releaseAll();session.focusManager().clear();}
    @Override public void dispose(){suspended();searchSubscriptions.dispose();projectSearch.dispose();scripts.dispose();prefs.save();}
    private void layout(int w,int h){width=w;height=h;frame=EditorLayout.calculate(w,h,prefs.filesRatio,prefs.helpRatio,prefs.logsRatio,prefs.filesOpen,helpColumn(),prefs.logsOpen,(selected==1||showSharedProject)?52:22);helpWindow=HelpWindowGeometry.calculate(w,h,prefs.helpWindowX,prefs.helpWindowY,prefs.helpWindowW,prefs.helpWindowH);}
    private boolean helpColumn(){return settings||(prefs.helpOpen&&prefs.helpDocked);}
    private boolean helpFloating(){return prefs.helpOpen&&!prefs.helpDocked;}
    public void render(MaredRenderContext ctx){
        session.actionContext().runs().update(session.controller().consolePanel().consoleComponent().logPanel());
        scripts.setReadOnly(selected!=1&&showSharedProject);session.actionContext().setCommandsActive(selected!=1&&!showSharedProject);layout(ctx.width(),ctx.height());buttons.clear();var g=ctx.graphics();var font=ctx.font();
        if(width<260||height<150){g.fill(0,0,width,height,bg());g.drawString(font,EditorText.translate("Reduce GUI scale · Esc to return"),6,8,text(),false);return;}
        g.fill(0,0,width,height,bg());
        Rect editor=frame.editor();
        if(prefs.background&&editor.width()>0&&editor.height()>0){
            g.enableScissor(editor.x(),editor.y(),editor.right(),editor.bottom());
            int cx=editor.x()+editor.width()*3/4,cy=editor.y()+editor.height()/2;
            int radius=Math.min(95,Math.min(editor.width(),editor.height())/3);
            octahedron(g,cx,cy,radius,phase,quiet());
            for(int i=0;i<24;i++){double a=i*Math.PI/12+phase*.4;int x=cx+(int)(Math.cos(a)*radius*1.4),y=cy+(int)(Math.sin(a)*radius*.35);g.fill(x,y,x+1,y+1,quiet());}
            g.disableScissor();
        }
        rail(ctx);toolbar(ctx);
        face(g,frame.files(),panel(),prefs.depth);
        if(prefs.filesOpen&&frame.files().width()>25){
            Rect body=new Rect(frame.files().x()+2,frame.files().y()+2,Math.max(0,frame.files().width()-4),Math.max(0,frame.files().height()-4));renderComponent(files(),body,ctx);
            button(ctx,new Rect(frame.files().right()-44,frame.files().y()+2,12,12),"<","Collapse files",()->{prefs.filesOpen=false;clearFocus();});
        }else button(ctx,new Rect(frame.files().x(),frame.files().y(),frame.files().width(),Math.min(22,frame.files().height())),">","Expand files",()->prefs.filesOpen=true);
        var plugin=pluginPanel();
        if(showProjectSearch){renderComponent(projectSearch,editor.inset(3),ctx);}
        else if(showSharedProject){renderComponent(scripts,editor.inset(3),ctx);}
        else if(showLinks||selected==4){renderComponent(links,editor.inset(3),ctx);}
        else if(plugin!=null){renderComponent(plugin.component(),editor.inset(3),ctx);}
        else if(selected==1){renderComponent(scripts,editor.inset(3),ctx);}
        else if(selected==0){
            renderComponent(workspace(),editor.inset(3),ctx);
            if(workspace().host().isEmpty()&&editor.width()>150&&editor.height()>100){
                // The projected core is deliberately quiet; the text remains the primary focus.
                if(prefs.background)octahedron(g,editor.x()+editor.width()/2,editor.y()+editor.height()/2-3,Math.min(36,editor.height()/5),phase,quiet());
                g.drawString(font,EditorText.translate(TABS[selected]).toUpperCase(Locale.ROOT),editor.x()+12,editor.y()+43,text(),false);
                g.drawString(font,EditorText.translate("Open a file, or create one with Ctrl+N"),editor.x()+12,editor.y()+57,dim(),false);
            }
        }else{
            face(g,editor.inset(3),panel(),prefs.depth);
            g.enableScissor(editor.x(),editor.y(),editor.right(),editor.bottom());
            g.drawString(font,EditorText.translate(TABS[selected]),editor.x()+12,editor.y()+15,text(),false);
            g.drawString(font,EditorText.translate("This editor is planned for a later stage."),editor.x()+12,editor.y()+33,dim(),false);
            g.drawString(font,EditorText.translate("Your open documents remain in Commands / Scripts."),editor.x()+12,editor.y()+47,dim(),false);g.disableScissor();
        }
        face(g,frame.logs(),panel(),prefs.depth);
        Rect logHead=new Rect(frame.logs().x(),frame.logs().y(),frame.logs().width(),Math.min(15,frame.logs().height()));
        button(ctx,new Rect(logHead.x()+2,logHead.y()+1,Math.min(90,Math.max(0,logHead.width()-4)),Math.max(0,logHead.height()-2)),(prefs.logsOpen?"v  ":"^  ")+EditorText.translate("LOGS"),"Expand / collapse logs",()->{prefs.logsOpen=!prefs.logsOpen;clearFocus();});
        if(selected!=1&&logHead.width()>225){
            var runs=session.actionContext().runs();
            int lx=logHead.x()+94;
            button(ctx,new Rect(lx,logHead.y()+1,18,13),"<","Previous run",runs::previous);
            button(ctx,new Rect(lx+20,logHead.y()+1,18,13),">","Next run",runs::next);
            String caption=runs.logCaption();
            button(ctx,new Rect(lx+40,logHead.y()+1,Math.max(1,logHead.right()-lx-44),13),font.plainSubstrByWidth(caption,Math.max(1,logHead.right()-lx-52)),"Switch all logs / selected run",runs::toggleAllLogs);
        }
        if(prefs.logsOpen&&frame.logs().height()>20)renderComponent(logs(),new Rect(frame.logs().x()+2,frame.logs().y()+16,Math.max(0,frame.logs().width()-4),Math.max(0,frame.logs().height()-18)),ctx);
        if(helpColumn()&&frame.help().width()>20){
            face(g,frame.help(),panel(),prefs.depth);
            if(settings){g.enableScissor(frame.help().x(),frame.help().y(),frame.help().right(),frame.help().bottom());try{renderSettings(ctx);}finally{g.disableScissor();}}
            else {
                button(ctx,new Rect(frame.help().x()+4,frame.help().y()+2,Math.max(1,frame.help().width()-28),16),"Open window","Open full handbook window",()->dockHelp(false));
                button(ctx,new Rect(frame.help().right()-20,frame.help().y()+2,16,16),"x","Close handbook",this::closeHelp);
                help.setWide(false);
                renderComponent(handbook(),new Rect(frame.help().x()+2,frame.help().y()+20,Math.max(0,frame.help().width()-4),Math.max(0,frame.help().height()-22)),ctx);
            }
        }
        face(g,frame.status(),bg(),false);
        var doc=selected==1||showSharedProject?scripts.document():workspace().editor().document();
        String state=selected==1||showSharedProject?scripts.state():workspace().isSaving()?"SAVING":workspace().isDirty()?"UNSAVED":"READY";
        String runStatus=selected==1||showSharedProject?scripts.runStatus():session.actionContext().runs().status();
        String status="GENESIS / LOGIC   ·   "+(runStatus.isEmpty()?"":runStatus+"   ·   ")+EditorText.translate(TABS[selected])+"   ·   "+EditorText.translate(state);
        g.drawString(font,font.plainSubstrByWidth(status,Math.max(0,frame.status().width()-105)),frame.status().x()+6,frame.status().y()+3,dim(),false);
        if(frame.status().width()>150)g.drawString(font,"Ln "+(doc.cursorLine()+1)+", Col "+(doc.cursorCol()+1),frame.status().right()-95,frame.status().y()+3,dim(),false);
        divider(ctx,frame.files().right(),frame.files().y(),frame.files().bottom(),prefs.filesOpen);
        if(helpColumn())divider(ctx,frame.help().x(),frame.help().y(),frame.help().bottom(),true);
        if(prefs.logsOpen)g.fill(frame.logs().x(),frame.logs().y(),frame.logs().right(),frame.logs().y()+1,drag==3?accent():edge());
        if(helpFloating())renderHelpWindow(ctx);
        for(Button b:buttons)if((!helpFloating()||!helpWindow.contains(ctx.mouseX(),ctx.mouseY())||b.floating)&&b.bounds.contains(ctx.mouseX(),ctx.mouseY())){
            int tw=font.width(b.hint)+10,tx=Math.max(0,Math.min(width-tw,ctx.mouseX()+10)),ty=Math.max(0,Math.min(height-16,ctx.mouseY()+14));
            g.pose().pushPose();g.pose().translate(0,0,200);g.fill(tx,ty,tx+tw,ty+14,raised());g.drawString(font,b.hint,tx+5,ty+3,text(),false);g.pose().popPose();break;
        }
    }
    private void rail(MaredRenderContext ctx){
        var g=ctx.graphics();face(g,frame.rail(),panel(),prefs.depth);
        button(ctx,new Rect(3,3,24,25),"","Return to Genesis",()->{suspended();RuntimeProvider.get().navigation().returnToGenesis();});
        octahedron(g,15,15,8,phase,accent());
        int bottom=Math.max(34,height-28),max=Math.max(0,TABS.length*22-(bottom-34));railScroll=Math.min(railScroll,max);
        g.enableScissor(0,34,frame.rail().right(),bottom);
        for(int i=0;i<TABS.length;i++){int y=34+i*22-railScroll;if(y<34||y+20>bottom)continue;final int index=i;
            button(ctx,new Rect(3,y,24,20),"",TABS[i],()->{selected=index;showSharedProject=index>=2;scripts.setReadOnly(selected!=1&&showSharedProject);session.actionContext().setCommandsActive(selected!=1&&!showSharedProject);prefs.selectedTab=index;pluginPanelId=null;showLinks=false;showProjectSearch=false;links.setAi(index==4);settings=false;clearFocus();});
            drawRailIcon(ctx,i,8,y+4,i==selected?text():dim());
            if(i==selected)g.fill(0,y+5,2,y+15,accent());
        }g.disableScissor();
        button(ctx,new Rect(3,Math.max(0,height-24),24,20),"*","Logic editor settings",()->{if(selected==1||showSharedProject){openAllSettings();return;}settings=!settings;if(settings)prefs.helpOpen=false;clearFocus();});
    }
    private void drawRailIcon(MaredRenderContext ctx,int kind,int x,int y,int color){LogicRailIcons.draw(ctx.graphics(),kind,x+1,y+2,color);}
    private void openProjectSearch(){showSharedProject=false;scripts.setReadOnly(false);selected=0;session.actionContext().setCommandsActive(true);prefs.selectedTab=0;showLinks=false;pluginPanelId=null;showProjectSearch=true;clearFocus();session.focusManager().request(projectSearch);projectSearch.focusQuery();}
    private void closeProjectSearch(){showProjectSearch=false;clearFocus();workspace().requestFocus();}
    private String wt(String key){return MaredLang.get("mared.workbench."+key);}
    private void editProject(){selected=1;prefs.selectedTab=1;showSharedProject=false;showLinks=false;showProjectSearch=false;pluginPanelId=null;scripts.setReadOnly(false);session.actionContext().setCommandsActive(false);clearFocus();}
    private void projectMenu(int x,int y){
        if(selected==1){scripts.projectMenu(x,y);return;}
        session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(x,y,List.of(
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("edit_project"),this::editProject),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("view_project"),()->{showSharedProject=true;showProjectSearch=false;showLinks=false;pluginPanelId=null;scripts.setReadOnly(true);session.actionContext().setCommandsActive(false);clearFocus();}),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("new_project"),()->{editProject();scripts.newProject();}),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("import_project"),()->{editProject();scripts.browseImportFolder();}),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.scripts.import_minecraft"),()->{editProject();scripts.importMinecraftMod();}),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("plugins"),this::nextPluginPanel),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("return_tools"),()->{showSharedProject=false;scripts.setReadOnly(false);session.actionContext().setCommandsActive(selected!=1);clearFocus();})
        )).category(accent()));
    }
    private void commandMenu(int x,int y,boolean run){
        var list=new ArrayList<com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry>();
        if(run){
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("run"),"Ctrl+R",()->session.executeAction(StudioActions.FILE_RUN)));
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("stop"),"Ctrl+Shift+R",()->session.executeAction(StudioActions.FILE_STOP)));
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("check"),"F7",()->session.executeAction(StudioActions.EDIT_CHECK)));
        }else{
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("new_file"),"Ctrl+N",()->session.executeAction(StudioActions.FILE_NEW)));
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("save"),"Ctrl+S",()->session.executeAction(StudioActions.FILE_SAVE)));
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("refresh"),()->session.executeAction(StudioActions.FILE_RELOAD_PERSISTENT)));
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("find"),"Ctrl+F",()->session.executeAction(StudioActions.EDIT_FIND)));
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("search_project"),this::openProjectSearch));
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("transfer"),()->session.executeAction(StudioActions.PROJECT_TRANSFER)));
            list.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("links"),()->{showProjectSearch=false;showLinks=true;pluginPanelId=null;links.setAi(false);clearFocus();}));
        }
        session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(x,y,list).category(accent()));
    }
    private void viewMenu(int x,int y){
        var entries=new ArrayList<com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry>();
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt(prefs.filesOpen?"hide_files":"show_files"),()->{prefs.filesOpen=!prefs.filesOpen;clearFocus();}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt(prefs.logsOpen?"hide_logs":"show_logs"),()->{prefs.logsOpen=!prefs.logsOpen;clearFocus();}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt(prefs.helpOpen?"hide_help":"show_help"),this::toggleHelp));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("editor_settings"),()->{if(selected==1||showSharedProject){openAllSettings();return;}settings=true;prefs.helpOpen=false;clearFocus();}));
        session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(x,y,entries).category(accent()));
    }
    private void openMenu(String key,int x,int y){
        if(key.equals("view_menu")){viewMenu(x,y);return;}
        if(key.equals("project_menu")){projectMenu(x,y);return;}
        if(selected==1||showSharedProject){if(key.equals("file_menu"))scripts.fileMenu(x,y);else{scripts.runMenu(x,y);}}
        else commandMenu(x,y,key.equals("run_menu"));
    }
    private void toolbar(MaredRenderContext ctx){
        face(ctx.graphics(),frame.toolbar(),panel(),prefs.depth);int x=frame.toolbar().x()+6;
        String helpLabel=selected==1||showSharedProject?"Help":wt("help");
        int helpW=Math.min(70,Math.max(40,ctx.font().width(helpLabel)+8));
        int scriptReserve=selected==1&&scripts.hasProject()?65:0;
        String[] menuKeys={"project_menu","file_menu","run_menu","view_menu"};
        for(int i=0;i<menuKeys.length;i++){
            String key=menuKeys[i];int ww=Math.max(36,ctx.font().width(wt(key))+14),xx=x;
            if(x+ww+(i<menuKeys.length-1?23:0)>frame.toolbar().right()-helpW-9-scriptReserve){
                int first=i;button(ctx,new Rect(x,3,20,16),"…",wt("more_menus"),()->{
                    var entries=new ArrayList<com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry>();
                    for(int j=first;j<menuKeys.length;j++){String remaining=menuKeys[j];entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt(remaining),()->openMenu(remaining,xx,22)));}
                    session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(xx,22,entries).category(accent()));
                });x+=23;break;
            }
            button(ctx,new Rect(x,3,ww,16),wt(key),wt(key),()->openMenu(key,xx,22));x+=ww+3;
        }
        if(showSharedProject&&selected!=1){int ww=ctx.font().width(wt("read_only"))+10;if(x+ww<frame.toolbar().right()-helpW-9)ctx.graphics().drawString(ctx.font(),wt("read_only"),x+4,7,dim(),false);}
        else if(selected==1){
            if(scripts.hasProject()){
                int saveW=ctx.font().width(wt("save"))+12;
                if(x+saveW+3+48<frame.toolbar().right()-helpW-9){button(ctx,new Rect(x,3,saveW,16),wt("save"),wt("save"),scripts::saveAll);x+=saveW+3;}
                int launchW=Math.min(Math.min(130,Math.max(38,ctx.font().width(scripts.launchLabel())+12)),Math.max(20,frame.toolbar().right()-helpW-9-x-21));
                if(x+launchW+20<frame.toolbar().right()-helpW-9){int xx=x;
                    button(ctx,new Rect(x,3,launchW,16),launchW<42?"▶":ctx.font().plainSubstrByWidth(scripts.launchLabel(),launchW-8),scripts.launchLabel(),()->{if(prefs.scriptsAutoOpenLogs)prefs.logsOpen=true;scripts.launchPrimary();});
                    button(ctx,new Rect(x+launchW+1,3,17,16),"▾",MaredLang.get("mared.scripts.launch_choose"),()->scripts.launchMenu(xx,22));x+=launchW+21;
                }else if(x+20<frame.toolbar().right()-helpW-9){int xx=x;button(ctx,new Rect(x,3,18,16),"▾",MaredLang.get("mared.scripts.launch_choose"),()->scripts.launchMenu(xx,22));x+=21;}
                if(scripts.canStopPrimary()){int stopW=ctx.font().width(wt("stop"))+12;if(x+stopW<frame.toolbar().right()-helpW-9)button(ctx,new Rect(x,3,stopW,16),wt("stop"),wt("stop"),scripts::stopPrimary);}
            }

        }
        String caption=scripts.hasProject()?scripts.projectName():wt("no_project");
        if(x+ctx.font().width(caption)+12<frame.toolbar().right()-helpW-9)ctx.graphics().drawString(ctx.font(),caption,x+6,7,dim(),false);
        button(ctx,new Rect(frame.toolbar().right()-helpW-5,3,helpW,16),helpLabel,helpLabel,this::toggleHelp);
        if(selected==1||showSharedProject){
            int sw=Math.min(680,Math.max(1,width-16));searchBounds=new Rect((width-sw)/2+8,26,Math.max(1,sw-16),22);
            if(searchQuery==null){searchQuery=new net.minecraft.client.gui.components.EditBox(ctx.font(),0,0,80,18,net.minecraft.network.chat.Component.literal(st("search_query")));searchQuery.setMaxLength(com.fixmer.genesis.technology.scripts.ScriptSearch.MAX_QUERY);searchQuery.setBordered(false);}
            searchQuery.setX(searchBounds.x()+4);searchQuery.setY(29);searchQuery.setWidth(Math.max(1,searchBounds.width()-8));
            searchQuery.setHint(net.minecraft.network.chat.Component.literal(st("search_query")));searchQuery.setTextColor(text());
            ctx.graphics().fill(searchBounds.x(),searchBounds.y(),searchBounds.right(),searchBounds.bottom(),panel());
            searchQuery.render(ctx.graphics(),ctx.mouseX(),ctx.mouseY(),0);

        }
    }
    private void renderSettings(MaredRenderContext ctx){
        settingsScroll=Math.max(0,Math.min(settingsScroll,Math.max(0,197-frame.help().height())));
        int x=frame.help().x()+8,y=frame.help().y()+8,w=Math.max(1,frame.help().width()-16);
        ctx.graphics().drawString(ctx.font(),EditorText.translate("EDITOR SETTINGS"),x,y,text(),false);y+=22-settingsScroll;
        settingsButton(ctx,new Rect(x,y,w,18),EditorText.translate("Theme")+": "+com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().displayName,"Change interface theme",this::nextTheme);y+=22;
        settingsButton(ctx,new Rect(x,y,w,18),EditorText.translate("Background")+": "+EditorText.translate(prefs.background?"ON":"OFF"),"Genesis background",()->prefs.background=!prefs.background);y+=22;
        settingsButton(ctx,new Rect(x,y,w,18),EditorText.translate("Animation")+": "+EditorText.translate(prefs.animation?"ON":"OFF"),"Also respects Reduced Motion",()->prefs.animation=!prefs.animation);y+=22;
        settingsButton(ctx,new Rect(x,y,w,18),EditorText.translate("Pixel depth")+": "+EditorText.translate(prefs.depth?"ON":"OFF"),"Bevelled panel faces",()->prefs.depth=!prefs.depth);y+=26;
        settingsButton(ctx,new Rect(x,y,w,18),"Reset panel sizes","Restore compact layout",()->{prefs.filesRatio=.18;prefs.helpRatio=.29;prefs.logsRatio=.24;prefs.filesOpen=true;prefs.logsOpen=false;prefs.helpWindowX=.18;prefs.helpWindowY=.10;prefs.helpWindowW=.70;prefs.helpWindowH=.80;});y+=22;
        settingsButton(ctx,new Rect(x,y,w,18),"All settings…","Open existing settings",this::openAllSettings);
    }
    public void openAllSettings(){
        if(selected==1||showSharedProject){
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(mc.screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen shell)shell.suspendForSettings();
            mc.setScreen(new com.fixmer.mared.gui2.settings.MaredSettingsScreen(mc.screen,prefs,()->scripts.trimLogs(),action->{
                if(action.equals("dependencies"))scripts.manageDependencies();else if(action.equals("check"))scripts.checkBuildTools();else if(action.startsWith("build_"))scripts.configureBuildOption(action);else scripts.configureBuildTool(action);
            }));
        }else session.executeAction(StudioActions.VIEW_SETTINGS);
    }
    private static String st(String key){return MaredLang.get("mared.scripts."+key);}
    private static String onOff(boolean value){return EditorText.translate(value?"ON":"OFF");}
    private void nextTheme(){
        var registry=com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.all();
        if(registry.isEmpty())return;
        int current=registry.indexOf(com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active());
        var next=registry.get((current+1)%registry.size());
        com.fixmer.mared.MaredSettings.setThemeId(next.id);
        com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.setActive(next);
        com.fixmer.mared.MaredSettings.save();
    }
    private void focusHelp(){
        clearFocus();
        if(prefs.helpOpen&&(selected==1||showSharedProject))session.focusManager().request(handbook());
    }
    private void toggleHelp(){prefs.helpOpen=!prefs.helpOpen;settings=false;focusHelp();}
    private void dockHelp(boolean docked){
        prefs.helpDocked=docked;prefs.helpOpen=true;settings=false;help.cancelInteraction();focusHelp();
    }
    private void closeHelp(){prefs.helpOpen=false;help.cancelInteraction();clearFocus();}
    private void beginWindowDrag(int kind,double mx,double my){
        clearFocus();help.cancelInteraction();drag=kind;dragWindow=helpWindow;dragMouseX=mx;dragMouseY=my;
    }
    private void renderHelpWindow(MaredRenderContext ctx){
        Rect r=helpWindow;var g=ctx.graphics();
        g.pose().pushPose();g.pose().translate(0,0,150);
        buildingHelpWindow=true;
        try {
            EditorDialogStyle.active(LOGIC).panel(g,r.x(),r.y(),r.width(),r.height());
            g.drawString(ctx.font(),ctx.font().plainSubstrByWidth((selected==1||showSharedProject?MaredLang.get("mared.scripts.guide"):EditorText.translate("Command handbook")),Math.max(0,r.width()-141)),r.x()+10,r.y()+10,text(),false);
            button(ctx,new Rect(r.right()-125,r.y()+3,100,16),"Dock right","Show compact handbook panel",()->dockHelp(true));
            button(ctx,new Rect(r.right()-21,r.y()+3,16,16),"x","Close handbook",this::closeHelp);
            help.setWide(true);
            renderComponent(handbook(),new Rect(r.x()+3,r.y()+23,Math.max(0,r.width()-6),Math.max(0,r.height()-35)),ctx);
            g.fill(r.right()-11,r.bottom()-4,r.right()-3,r.bottom()-3,dim());
            g.fill(r.right()-7,r.bottom()-8,r.right()-3,r.bottom()-7,dim());
        } finally { buildingHelpWindow=false;g.pose().popPose(); }
    }
    private void settingsButton(MaredRenderContext ctx,Rect rect,String label,String hint,Runnable action){
        if(rect.y()>=frame.help().y()+26&&rect.bottom()<=frame.help().bottom()-2)button(ctx,rect,label,hint,action);
    }
    private void button(MaredRenderContext ctx,Rect r,String label,String hint,Runnable action){
        if(r.width()<1||r.height()<1)return;
        label=EditorText.translate(label);hint=EditorText.translate(hint);
        boolean hover=r.contains(ctx.mouseX(),ctx.mouseY());if(hover)face(ctx.graphics(),r,raised(),prefs.depth);
        String text=ctx.font().plainSubstrByWidth(label,Math.max(0,r.width()-4));ctx.graphics().drawString(ctx.font(),text,r.x()+(r.width()-ctx.font().width(text))/2,r.y()+(r.height()-8)/2,hover?text():dim(),false);
        buttons.add(new Button(r,label,hint,action,buildingHelpWindow));
    }
    private void renderComponent(MaredComponent c,Rect r,MaredRenderContext ctx){
        if(r.width()<1||r.height()<1)return;
        c.layout(new MaredBounds(r.x(),r.y(),r.width(),r.height()));c.mouseMoved(ctx.mouseX(),ctx.mouseY());
        ctx.graphics().enableScissor(r.x(),r.y(),r.right(),r.bottom());c.render(ctx);ctx.graphics().disableScissor();
    }
    private void divider(MaredRenderContext ctx,int x,int y,int bottom,boolean resizable){boolean hover=resizable&&Math.abs(ctx.mouseX()-x)<=2&&ctx.mouseY()>=y&&ctx.mouseY()<bottom;ctx.graphics().fill(x,y,x+1,bottom,hover?accent():edge());}
    private void clearFocus(){if(!showProjectSearch)projectSearch.cancelInteraction();captured=null;session.pointerManager().releaseAll();session.focusManager().clear();}
    private List<MaredComponent> visible(){var result=new ArrayList<MaredComponent>(4);if(prefs.filesOpen)result.add(files());var plugin=pluginPanel();if(showProjectSearch)result.add(projectSearch);else if(showSharedProject)result.add(scripts);else if(showLinks||selected==4)result.add(links);else if(plugin!=null)result.add(plugin.component());else if(selected==1)result.add(scripts);else if(selected==0)result.add(workspace());if(prefs.logsOpen)result.add(logs());if(prefs.helpOpen&&!settings)result.add(handbook());return result;}
    public boolean click(double mx,double my,int button){
        if(frame==null)return false;
        if(session.overlayManager().mouseClicked(mx,my,button)||session.overlayManager().shouldBlockGenericInput())return true;
        if(button==0&&(selected==1||showSharedProject)&&searchBounds!=null&&searchBounds.contains(mx,my)){
            clearFocus();scripts.searchFiles(searchQuery.getValue());return true;
        }
        if(helpFloating()&&helpWindow!=null&&helpWindow.contains(mx,my)){
            if(button==0){
                for(int i=buttons.size()-1;i>=0;i--){Button b=buttons.get(i);if(b.floating&&b.bounds.contains(mx,my)){b.action.run();return true;}}
                if(mx>=helpWindow.right()-12&&my>=helpWindow.bottom()-12){beginWindowDrag(5,mx,my);return true;}
                if(my<helpWindow.y()+22){beginWindowDrag(4,mx,my);return true;}
            }
            if(handbook().mouseClicked(mx,my,button))captured=handbook();
            return true;
        }
        if(button==0){
            // Header buttons have priority over neighbouring resize handles.
            for(Button b:buttons)if(!b.floating&&b.bounds.contains(mx,my)){b.action.run();return true;}
            if(my>=frame.files().y()&&my<frame.files().bottom()){
                if(prefs.filesOpen&&Math.abs(mx-frame.files().right())<=3){drag=1;clearFocus();return true;}
                if(helpColumn()&&Math.abs(mx-frame.help().x())<=3){drag=2;clearFocus();return true;}
            }
            if(prefs.logsOpen&&mx>=frame.logs().x()&&mx<frame.logs().right()&&Math.abs(my-frame.logs().y())<=3){drag=3;clearFocus();return true;}
        }
        for(var c:visible())if(!(c==handbook()&&helpFloating())&&c.mouseClicked(mx,my,button)){captured=c;return true;}
        clearFocus();return true;
    }
    public boolean drag(double mx,double my,int button,double dx,double dy){
        if(session.overlayManager().shouldBlockGenericInput())return true;
        if(drag==4||drag==5){
            double offsetX=mx-dragMouseX,offsetY=my-dragMouseY;
            Rect next=HelpWindowGeometry.calculate(width,height,
                drag==4?(dragWindow.x()+offsetX)/Math.max(1,width):prefs.helpWindowX,
                drag==4?(dragWindow.y()+offsetY)/Math.max(1,height):prefs.helpWindowY,
                drag==5?(dragWindow.width()+offsetX)/Math.max(1,width):prefs.helpWindowW,
                drag==5?(dragWindow.height()+offsetY)/Math.max(1,height):prefs.helpWindowH);
            prefs.helpWindowX=next.x()/(double)Math.max(1,width);prefs.helpWindowY=next.y()/(double)Math.max(1,height);
            prefs.helpWindowW=next.width()/(double)Math.max(1,width);prefs.helpWindowH=next.height()/(double)Math.max(1,height);
            return true;
        }
        if(drag!=0){
            if(drag==1)prefs.filesRatio=Math.max(.08,Math.min(.6,(mx-frame.rail().width())/Math.max(1,width-frame.rail().width())));
            if(drag==2)prefs.helpRatio=Math.max(.08,Math.min(.6,(width-mx)/Math.max(1,width-frame.rail().width())));
            if(drag==3)prefs.logsRatio=Math.max(.08,Math.min(.65,(frame.status().y()-my)/Math.max(1,frame.status().y()-frame.toolbar().height())));
            return true;
        }
        return captured!=null&&captured.mouseDragged(mx,my,button,dx,dy);
    }
    public boolean release(double mx,double my,int button){boolean handled=drag!=0;drag=0;if(captured!=null){handled|=captured.mouseReleased(mx,my,button);captured=null;}return handled;}
    public boolean scroll(double mx,double my,double dx,double dy){
        if(session.overlayManager().mouseScrolled(mx,my,dx,dy)||session.overlayManager().shouldBlockGenericInput())return true;
        if(helpFloating()&&helpWindow!=null&&helpWindow.contains(mx,my)){handbook().mouseScrolled(mx,my,dx,dy);return true;}
        if(frame!=null&&frame.rail().contains(mx,my)){railScroll=Math.max(0,railScroll+(dy<0?22:dy>0?-22:0));return true;}
        if(frame!=null&&settings&&frame.help().contains(mx,my)){settingsScroll=Math.max(0,Math.min(Math.max(0,197-frame.help().height()),settingsScroll+(dy<0?22:dy>0?-22:0)));return true;}
        for(var c:visible())if(c.mouseScrolled(mx,my,dx,dy))return true;return false;
    }
    public boolean key(int key,int scan,int mods){
        session.actionContext().setCommandsActive(selected!=1);
        if(session.overlayManager().keyPressed(key,scan,mods)||session.overlayManager().shouldBlockGenericInput())return true;
        // Scripts Help owns its input in both floating and docked layouts.
        if((selected==1||showSharedProject)&&prefs.helpOpen&&!settings&&session.focusManager().owner()==handbook()){
            if(key==256){closeHelp();return true;}
            if(key!=295)return handbook().keyPressed(key,scan,mods);
        }
        if((selected==1||showSharedProject)&&session.focusManager().owner()==scripts.logsPane()&&scripts.logsPane().keyPressed(key,scan,mods))return true;
        if(selected==1||showSharedProject){
            if(key==70&&(mods&7)==3){scripts.searchProjectText();return true;}
            if(key==71&&(mods&7)==2){scripts.goToLine();return true;}
            if(key==79&&(mods&7)==2){scripts.searchOpenFiles();return true;}
        }
        if((selected==1||showSharedProject)&&(mods&7)==2&&(key==70||key==80)){
            if(key==70)scripts.searchText();else scripts.searchFiles();return true;
        }
        if(selected!=1&&!showSharedProject&&key==70&&(mods&3)==3){openProjectSearch();return true;}
        if(selected!=1&&key==80&&(mods&6)==6){nextPluginPanel();return true;}
        if(key==256&&helpFloating()){
            if(handbook().keyPressed(key,scan,mods))return true;
            closeHelp();return true;
        }
        if(helpFloating()&&key!=295&&session.focusManager().owner()==handbook())return handbook().keyPressed(key,scan,mods);
        if(key==256){var owner=session.focusManager().owner();return owner!=null&&visible().contains(owner)&&owner.keyPressed(key,scan,mods);}
        if(showProjectSearch&&key==80&&(mods&3)==3)return session.actions().executeByKey(key,mods,session.actionContext());
        if(showProjectSearch&&session.focusManager().owner()==projectSearch&&key!=295)return projectSearch.keyPressed(key,scan,mods);
        if((showLinks||selected==4)&&session.focusManager().owner()==links&&links.keyPressed(key,scan,mods))return true;
        if(session.actions().executeByKey(key,mods,session.actionContext()))return true;
        if(key==295){var focusable=visible().stream().filter(MaredComponent::focusable).toList();if(!focusable.isEmpty()){int i=focusable.indexOf(session.focusManager().owner());session.focusManager().request(focusable.get((i+1)%focusable.size()));return true;}}
        MaredComponent owner=session.focusManager().owner();if(owner!=null&&visible().contains(owner))return owner.keyPressed(key,scan,mods);return false;
    }
    public boolean character(char c,int mods){if(session.overlayManager().charTyped(c,mods)||session.overlayManager().shouldBlockGenericInput())return true;var owner=session.focusManager().owner();return owner!=null&&visible().contains(owner)&&owner.charTyped(c,mods);}
}
