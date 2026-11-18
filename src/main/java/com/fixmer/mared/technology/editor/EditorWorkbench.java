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
import org.lwjgl.glfw.GLFW;
import java.util.*;
import com.fixmer.genesis.technology.creation.CreationCatalog145;
import com.fixmer.genesis.technology.creation.CreationCatalog145.Direction;
import com.fixmer.genesis.technology.creation.CreationCatalog145.Section;
import com.fixmer.genesis.technology.creation.CreationCatalog145.Tool;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Session-owned Logic workbench. Adapts existing document/actions without copying their storage or save logic. */
public final class EditorWorkbench implements Disposable {
    private record Button(Rect bounds,String label,String hint,Runnable action,boolean floating) {}
    private static final String[] TABS={"Commands","Scripts","Events / Triggers","Blueprint","Behavior / States","Variables / Data","Network","Sandbox"};
    private final StudioSession session;
    public WorkbenchPreferences preferences152(){return prefs;}
    private final WorkbenchPreferences prefs=WorkbenchPreferences.load();
    private final CommandHelpBrowser help;
    private final com.fixmer.mared.technology.scripts.ScriptProjectEditor scripts;
    private final com.fixmer.mared.technology.scripts.ScriptProjectEditor commands;
    private Direction direction145;
    private Section section145;
    private boolean serverTool145(){return section145!=null&&(section145.tool()==Tool.SERVER||section145.tool()==Tool.SUPERVISOR||section145.tool()==Tool.SERVER_TOOLS);}
    public void enterDirection145(Direction direction){
        if(direction145!=null&&section145!=null)prefs.creationSections145.put(direction145.id(),section145.id());
        direction145=Objects.requireNonNull(direction);GenesisEditorVisuals.colors152(prefs,direction145);prefs.creationDirection145=direction.id();
        selectSection145(CreationCatalog145.section(direction,prefs.creationSections145.get(direction.id())));
    }
    private void selectSection145(Section section){
        if(section145!=null&&!section145.id().equals(section.id()))development151.close();section145=section;prefs.creationSections145.put(direction145.id(),section.id());
        selected=section.tool()==Tool.COMMANDS||section.tool()==Tool.EXTENSIONS&&direction145==Direction.MAPS&&selected==0?0:1;prefs.selectedTab=selected;prefs.sourceTab133=selected;commandContext133=selected==0;
        legacyCommands133=false;showSharedProject=showLinks=showProjectSearch=false;pluginPanelId=null;settings=false;
        sourceEditor().setReadOnly(false);sourceEditor().context145(direction145,section);session.actionContext().setCommandsActive(false);clearFocus();
    }
    private String directionTitle145(){return MaredLang.get(direction145.key());}
    private String sectionTitle145(){return MaredLang.get(section145.key());}
    private void sectionTabs145(MaredRenderContext ctx){
        String breadcrumb=directionTitle145()+"  /  "+sectionTitle145()+"  ▾";
        int x=frame.toolbar().x()+6;
        int width=Math.min(ctx.font().width(breadcrumb)+12,frame.toolbar().width()-12);
        button(ctx,new Rect(x,26,width,16),breadcrumb,wt("choose_section"),()->{
            var entries=new ArrayList<com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry>();
            for(Section section:CreationCatalog145.sections(direction145)){
                String label=MaredLang.get(section.key());
                if(section.equals(section145))label="✓ "+label;
                entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(label,()->selectSection145(section)));
            }
            session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(x,44,entries).category(accent()));
        });
    }
    public boolean canMirrorModel147(String id,String file){return !scripts.modelDraft147(id,file)&&!commands.modelDraft147(id,file);}
    private void showLinks147(boolean ai){selectSection145(CreationCatalog145.section(direction145,"tools147"));showLinks=true;links.setAi(ai);clearFocus();}
    private void codeSection147(){selectSection145(CreationCatalog145.section(direction145,direction145==Direction.MAPS&&selected==0?"commands":"scripts"));}
    private java.util.List<com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry> toolEntries151(int x,int y){
        var entries=new ArrayList<com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry>();
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.code"),()->{codeSection147();sourceEditor().codeToolsMenu(x,y);}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.search"),()->{codeSection147();sourceEditor().searchProjectText();}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.snippets"),()->{codeSection147();sourceEditor().snippetsMenu(x,y);}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.links"),()->showLinks147(false)));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.ai"),()->showLinks147(true)));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.palette"),()->{codeSection147();session.executeAction(StudioActions.VIEW_COMMAND_PALETTE);}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.workflow"),sourceEditor()::showWorkflow));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.export"),sourceEditor()::exportJava));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.seed_models"),sourceEditor()::seedModels147));
        for(var extension:session.controller().extensionPanels())entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(extension.id(),()->{selectSection145(CreationCatalog145.section(direction145,"tools147"));pluginPanelId=extension.id();clearFocus();}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("editor_settings"),this::openAllSettings));
        return java.util.List.copyOf(entries);
    }
    private void toolsMenu147(int x,int y){session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(x,y,toolEntries151(x,y)).category(accent()));}
    private boolean toolCatalog147(){return section145!=null&&section145.tool()==Tool.EXTENSIONS&&!showLinks&&pluginPanelId==null&&!showProjectSearch;}
    private int toolsScroll151,toolsMax151;
    private void renderTools147(MaredRenderContext ctx,Rect area){
        face(ctx.graphics(),area,panel(),prefs.depth);int x=area.x()+12,y=area.y()+12,w=Math.max(1,area.width()-24);
        var entries=toolEntries151(x,y);toolsMax151=Math.max(0,entries.size()*26-Math.max(0,area.height()-24));toolsScroll151=Math.max(0,Math.min(toolsScroll151,toolsMax151));
        for(int i=0;i<entries.size();i++){var entry=entries.get(i);int yy=y+i*26-toolsScroll151;
            if(yy<area.y()+2||yy+22>area.bottom()-2)continue;
            button(ctx,new Rect(x,yy,w,22),entry.label(),entry.label(),entry.action());
        }
    }
    private boolean modelTool146(){return section145!=null&&(section145.id().equals("models")||section145.id().equals("animations"));}
    private void renderBlockbench146(MaredRenderContext ctx,Rect area){
        face(ctx.graphics(),area,panel(),prefs.depth);int x=area.x()+12,y=area.y()+12,w=Math.max(1,area.width()-24);
        ctx.graphics().drawString(ctx.font(),sectionTitle145()+" · Blockbench",x,y,text(),false);y+=22;
        for(String line:com.fixmer.mared.gui2.framework.render.TextUtils.wrapLinesCached(ctx.font(),MaredLang.get("mared.blockbench146.description"),w)){
            if(y>area.bottom()-86)break;ctx.graphics().drawString(ctx.font(),line,x,y,dim(),false);y+=12;
        }
        if(area.height()>100){
            y=Math.min(y+12,area.bottom()-66);
            button(ctx,new Rect(x,y,Math.min(280,w),22),MaredLang.get("mared.blockbench146.create"),"Blockbench",()->{suspended();sourceEditor().openBlockbench146(true);});
            button(ctx,new Rect(x,y+28,Math.min(280,w),22),MaredLang.get("mared.blockbench146.open"),"Blockbench",()->{suspended();sourceEditor().openBlockbench146(false);});
        }
    }
    private final DevelopmentPane151 development151=new DevelopmentPane151();
    private void renderServer145(MaredRenderContext ctx,Rect area){
        development151.select(section145.id());renderComponent(development151,area,ctx);
    }
    private boolean commandReference133,commandContext133,legacyCommands133;
    private boolean nativeEditor133(){return (selected<=1||showSharedProject)&&!legacyCommands133&&!serverTool145()&&!modelTool146()&&!toolCatalog147();}
    private com.fixmer.mared.technology.scripts.ScriptProjectEditor sourceEditor(){return selected==0||selected>1&&commandContext133?commands:scripts;}
    private MaredComponent handbook(){if(legacyCommands133)return help;return selected==0&&commandReference133?help:nativeEditor133()?sourceEditor().guide():help;}
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
    private String pluginPanelId;
    private com.fixmer.mared.gui2.docking.DockPanel pluginPanel(){return session.controller().extensionPanels().stream().filter(p->p.id().equals(pluginPanelId)).findFirst().orElse(null);}
    private void nextPluginPanel(){showSharedProject=false;sourceEditor().setReadOnly(false);var panels=session.controller().extensionPanels();int current=-1;for(int i=0;i<panels.size();i++)if(panels.get(i).id().equals(pluginPanelId))current=i;pluginPanelId=current+1<panels.size()?panels.get(current+1).id():null;showLinks=false;showProjectSearch=false;if(selected==4){selected=1;prefs.selectedTab=1;}clearFocus();}
    public EditorWorkbench(StudioSession session){
        this.session=session;session.focusManager().attachTo(development151);
        selected=prefs.selectedTab;commandContext133=selected<=1?selected==0:prefs.sourceTab133==0;showSharedProject=selected>=2;
        var controller=session.controller();
        controller.workspacePanel().workspaceComponent().setSpanResolver(com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver.INSTANCE);
        controller.explorerPanel().explorerComponent().setFilesOnly(true);
        controller.consolePanel().consoleComponent().logPanel().setCompact(true);
        help=new CommandHelpBrowser(controller.bus(),controller.inspectorPanel().inspectorComponent());
        session.focusManager().attachTo(help);
        links=new ScriptLinksPanel(controller.bus(),()->{enterDirection145(Direction.MAPS);selectSection145(CreationCatalog145.section(Direction.MAPS,"commands"));legacyCommands133=true;session.actionContext().setCommandsActive(true);clearFocus();});
        links.setAi(selected==4);session.focusManager().attachTo(links);
        projectSearch=new ProjectSearchPanel(hit->workspace().openSearchHit(hit),this::closeProjectSearch);
        session.focusManager().attachTo(projectSearch);
        searchSubscriptions.add(controller.bus().subscribe(com.fixmer.mared.gui2.studio.events.StudioEvents.RequestProjectSearchEvent.class,event->openProjectSearch()));
        scripts=new com.fixmer.mared.technology.scripts.ScriptProjectEditor(session.overlayManager(),session.actionContext()::log);
        commands=new com.fixmer.mared.technology.scripts.ScriptProjectEditor(session.overlayManager(),session.actionContext()::log,true);commands.preferences(prefs);session.focusManager().attachTo(commands);session.focusManager().attachTo(commands.filesPane());session.focusManager().attachTo(commands.guide());session.focusManager().attachTo(commands.logsPane());
        scripts.fileOpened147(()->{if(toolCatalog147())codeSection147();});commands.fileOpened147(()->{if(toolCatalog147())codeSection147();});
        scripts.preferences(prefs);session.focusManager().attachTo(scripts);session.focusManager().attachTo(scripts.filesPane());session.focusManager().attachTo(scripts.guide());session.focusManager().attachTo(scripts.logsPane());
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.new_project","mared.scripts.new_project","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly(),ctx->sourceEditor().newProject()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.next_diagnostic","mared.scripts.next_diagnostic","F8",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&sourceEditor().hasBuildReport(),ctx->sourceEditor().nextDiagnostic(false)));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.previous_diagnostic","mared.scripts.previous_diagnostic","Shift+F8",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&sourceEditor().hasBuildReport(),ctx->sourceEditor().nextDiagnostic(true)));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.new_file","mared.scripts.new_file","Ctrl+N",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly(),ctx->sourceEditor().newFile()).inScope(com.fixmer.mared.gui2.studio.action.EditorAction.Scope.SCRIPTS));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.save","mared.scripts.save","Ctrl+S",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly(),ctx->sourceEditor().save()).inScope(com.fixmer.mared.gui2.studio.action.EditorAction.Scope.SCRIPTS));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.close","mared.scripts.close","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly(),ctx->sourceEditor().closeDocument()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.run_java","mared.scripts.launch_primary","Ctrl+R",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canLaunchPrimary(),ctx->{if(prefs.scriptsAutoOpenLogs)prefs.logsOpen=true;sourceEditor().launchPrimary();}).inScope(com.fixmer.mared.gui2.studio.action.EditorAction.Scope.SCRIPTS));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.stop_java","mared.scripts.stop_java","Ctrl+Shift+R",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canStopPrimary(),ctx->sourceEditor().stopPrimary()).inScope(com.fixmer.mared.gui2.studio.action.EditorAction.Scope.SCRIPTS));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.entry_point","mared.scripts.entry_point","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canRunJava(),ctx->sourceEditor().configureEntryPoint()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.copy_output","mared.scripts.copy_output","Ctrl+Shift+L",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().hasRunOutput(),ctx->sourceEditor().copyRunOutput()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.libraries_path","mared.scripts.libraries_path","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canUseLocalJavaInputs(),ctx->sourceEditor().copyLibrariesPath()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.resources_path","mared.scripts.resources_path","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canUseLocalJavaInputs(),ctx->sourceEditor().copyResourcesPath()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.export_java","mared.scripts.export_java","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canExportJava(),ctx->sourceEditor().exportJava()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.init_gradle","mared.scripts.gradle_init","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canConfigureGradle(),ctx->sourceEditor().initializeGradle()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.jdk_home","mared.scripts.jdk_home","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canConfigureBuildTools(),ctx->sourceEditor().configureBuildTool("java_home")));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.wrapper_jar","mared.scripts.wrapper_jar","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canConfigureBuildTools(),ctx->sourceEditor().configureBuildTool("wrapper_jar")));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.gradle_task","mared.scripts.gradle_task","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canConfigureGradle(),ctx->sourceEditor().configureGradleTask()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.build_gradle","mared.scripts.build_gradle","Ctrl+B",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canBuildGradle(),ctx->buildScript()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.save_all","mared.scripts.save_all","Ctrl+Alt+S",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly(),ctx->sourceEditor().saveAll()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.dependencies","mared.scripts.dependencies","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canBuildGradle(),ctx->sourceEditor().manageDependencies()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.check_build_tools","mared.scripts.check_build_tools","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canConfigureBuildTools(),ctx->sourceEditor().checkBuildTools()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.reveal_file","mared.workbench.focus_files","Ctrl+Shift+E",ctx->!serverTool145(),ctx->showExplorer(true)));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.collapse_folders","mared.scripts.collapse_folders","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canBrowseFiles(),ctx->sourceEditor().collapseFolders()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.expand_folders","mared.scripts.expand_folders","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canBrowseFiles(),ctx->sourceEditor().expandFolders()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.new_neoforge","mared.scripts.new_neoforge","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canConfigureBuildTools(),ctx->sourceEditor().newNeoForgeProject()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.import_folder","mared.scripts.import_folder","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canImportGradle(),ctx->sourceEditor().browseImportFolder()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.import_minecraft","mared.scripts.import_minecraft","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canImportGradle(),ctx->sourceEditor().importMinecraftMod()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.minecraft_info","mared.scripts.minecraft_project_info","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().hasProject(),ctx->sourceEditor().showMinecraftProjectInfo()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.new_folder","mared.scripts.new_folder","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canManageFiles(),ctx->sourceEditor().newFolder()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.rename","mared.scripts.rename","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canManageFiles(),ctx->sourceEditor().renameSelected()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.delete","mared.scripts.delete","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canManageFiles(),ctx->sourceEditor().deleteSelected()));
        session.actions().register(new com.fixmer.mared.gui2.studio.action.EditorAction("genesis:scripts.import_gradle","mared.scripts.import_gradle","",ctx->!ctx.commandsActive()&&!serverTool145()&&!modelTool146()&&!toolCatalog147()&&!sourceEditor().readOnly()&&sourceEditor().canImportGradle(),ctx->sourceEditor().importGradleProject()));
        Direction initial=prefs.creationDirection145.isEmpty()?(selected==0?Direction.MAPS:Direction.MODS):Direction.fromId(prefs.creationDirection145);
        enterDirection145(initial);
    }
    private void selectShared133(com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122 shared){
        boolean commands="COMMANDS".equals(shared.context132().get("mode133"));
        enterDirection145(commands?Direction.MAPS:Direction.fromId(shared.context132().getOrDefault("direction147","mods")));
        selectSection145(CreationCatalog145.section(direction145,commands?"commands":"scripts"));
    }

    public void openShared132(com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122 shared){selectShared133(shared);sourceEditor().joinShared132(shared);}
    public void openSharedFile132(com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122 shared,String path,int line){selectShared133(shared);sourceEditor().joinShared132(shared);sourceEditor().openSharedFile132(path,line);}
    private void buildScript(){if(sourceEditor().canBuildGradle()){if(prefs.scriptsAutoOpenLogs)prefs.logsOpen=true;sourceEditor().buildGradle();}}
    private void runScript(){boolean available=sourceEditor().canRunJava();sourceEditor().runJava();if(available&&prefs.scriptsAutoOpenLogs)prefs.logsOpen=true;}
    private WorkspaceComponent workspace(){return session.controller().workspacePanel().workspaceComponent();}
    private MaredComponent files(){if(nativeEditor133())return sourceEditor().filesPane();return session.controller().explorerPanel().component();}
    private MaredComponent logs(){if(nativeEditor133())return sourceEditor().logsPane();return session.controller().consolePanel().component();}
    public void tick(float dt){if(serverTool145())development151.tick();scripts.tick();commands.tick();if(prefs.animation&&!MaredSettings.isReducedMotion())phase=(phase+Math.max(0,Math.min(.1,dt))*.18)%(Math.PI*2);}
    public void suspended(){drag=0;captured=null;help.cancelInteraction();links.cancelInteraction();projectSearch.cancelInteraction();session.pointerManager().releaseAll();session.focusManager().clear();}
    @Override public void dispose(){development151.close();suspended();searchSubscriptions.dispose();projectSearch.dispose();scripts.dispose();commands.dispose();prefs.save();}
    private void layout(int w,int h){width=w;height=h;frame=EditorLayout.calculate(w,h,prefs.filesRatio,prefs.helpRatio,prefs.logsRatio,prefs.filesOpen&&!serverTool145(),helpColumn(),prefs.logsOpen&&!serverTool145(),(nativeEditor133())?76:46,Math.min(prefs.railWidth152,Math.max(34,w/3)));helpWindow=HelpWindowGeometry.calculate(w,h,prefs.helpWindowX,prefs.helpWindowY,prefs.helpWindowW,prefs.helpWindowH);}
    private boolean helpColumn(){return settings||(prefs.helpOpen&&prefs.helpDocked);}
    private boolean helpFloating(){return prefs.helpOpen&&!prefs.helpDocked;}
    public void render(MaredRenderContext ctx){
        GenesisEditorVisuals.colors152(prefs,direction145);
        session.actionContext().runs().update(session.controller().consolePanel().consoleComponent().logPanel());
        sourceEditor().setReadOnly(selected!=1&&showSharedProject);session.actionContext().setCommandsActive(legacyCommands133&&selected==0);layout(ctx.width(),ctx.height());buttons.clear();var g=ctx.graphics();var font=ctx.font();
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
        if(!serverTool145())renderExplorer(ctx);
        var plugin=pluginPanel();
        if(toolCatalog147()){renderTools147(ctx,editor.inset(3));}
        else if(modelTool146()){renderBlockbench146(ctx,editor.inset(3));}
        else if(serverTool145()){renderServer145(ctx,new Rect(frame.toolbar().x(),editor.y(),frame.toolbar().width()-frame.help().width(),frame.status().y()-editor.y()).inset(3));}
        else if(showProjectSearch){renderComponent(projectSearch,editor.inset(3),ctx);}
        else if(showSharedProject){renderComponent(sourceEditor(),editor.inset(3),ctx);}
        else if(showLinks||selected==4){renderComponent(links,editor.inset(3),ctx);}
        else if(plugin!=null){renderComponent(plugin.component(),editor.inset(3),ctx);}
        else if(selected<=1&&!legacyCommands133){renderComponent(sourceEditor(),editor.inset(3),ctx);}
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
        if(!serverTool145()){face(g,frame.logs(),panel(),prefs.depth);
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
        }if(helpColumn()&&frame.help().width()>20){
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
        var doc=nativeEditor133()?sourceEditor().document():workspace().editor().document();
        String state=nativeEditor133()?sourceEditor().state():workspace().isSaving()?"SAVING":workspace().isDirty()?"UNSAVED":"READY";
        String runStatus=nativeEditor133()?sourceEditor().runStatus():session.actionContext().runs().status();
        String status="MARED / "+directionTitle145()+" / "+sectionTitle145()+"   ·   "+(runStatus.isEmpty()?"":runStatus+"   ·   ")+EditorText.translate(TABS[selected])+"   ·   "+EditorText.translate(state);
        if(serverTool145())status="MARED / "+directionTitle145()+" / "+sectionTitle145();
        g.drawString(font,font.plainSubstrByWidth(status,Math.max(0,frame.status().width()-105)),frame.status().x()+6,frame.status().y()+3,dim(),false);
        if(!serverTool145()&&frame.status().width()>150)g.drawString(font,"Ln "+(doc.cursorLine()+1)+", Col "+(doc.cursorCol()+1),frame.status().right()-95,frame.status().y()+3,dim(),false);
        divider(ctx,frame.rail().right(),30,frame.status().y(),true);
        if(!serverTool145())divider(ctx,frame.files().right(),frame.files().y(),frame.files().bottom(),prefs.filesOpen);
        if(helpColumn())divider(ctx,frame.help().x(),frame.help().y(),frame.help().bottom(),true);
        if(prefs.logsOpen&&!serverTool145())g.fill(frame.logs().x(),frame.logs().y(),frame.logs().right(),frame.logs().y()+1,drag==3?accent():edge());
        if(helpFloating())renderHelpWindow(ctx);
        for(Button b:buttons)if((!helpFloating()||!helpWindow.contains(ctx.mouseX(),ctx.mouseY())||b.floating)&&b.bounds.contains(ctx.mouseX(),ctx.mouseY())){
            int tw=Math.max(1,Math.min(width-8,font.width(b.hint)+10));
            var tooltipLines=com.fixmer.mared.gui2.framework.render.TextUtils.wrapLinesCached(font,b.hint,Math.max(1,tw-10));
            int th=Math.min(Math.max(1,height-8),tooltipLines.size()*10+8),tx=Math.max(0,Math.min(width-tw,ctx.mouseX()+10)),ty=Math.max(0,Math.min(height-th,ctx.mouseY()+14));
            g.pose().pushPose();g.pose().translate(0,0,200);g.fill(tx,ty,tx+tw,ty+th,raised());
            g.enableScissor(tx,ty,tx+tw,ty+th);
            try{for(int i=0;i<tooltipLines.size();i++)g.drawString(font,tooltipLines.get(i),tx+5,ty+4+i*10,text(),false);}finally{g.disableScissor();g.pose().popPose();}break;
        }
    }
    private void renderExplorer(MaredRenderContext ctx){
        Rect panelBounds=frame.files();
        face(ctx.graphics(),panelBounds,panel(),prefs.depth);
        if(!prefs.filesOpen||panelBounds.width()<=25){
            button(ctx,new Rect(panelBounds.x(),panelBounds.y(),panelBounds.width(),
                    Math.min(22,panelBounds.height())),">",wt("show_files"),()->showExplorer(false));
            return;
        }

        int x=panelBounds.x()+3;
        int y=panelBounds.y()+2;
        int width=panelBounds.width()-6;
        int rowHeight=19;
        int collapseWidth=Math.min(19,width);
        String heading=ctx.font().plainSubstrByWidth(wt("explorer_title"),Math.max(0,width-collapseWidth-8));
        ctx.graphics().drawString(ctx.font(),heading,x+3,y+5,text(),false);
        button(ctx,new Rect(x+width-collapseWidth,y,collapseWidth,rowHeight),"<",wt("hide_files"),()->{
            prefs.filesOpen=false;
            clearFocus();
        });

        int bodyY=y+rowHeight+2;
        if(nativeEditor133()&&sourceEditor().canManageFiles()){
            var editor=sourceEditor();
            String fileLabel=wt("explorer_new_file");
            String folderLabel=wt("explorer_new_folder");
            int fileMin=ctx.font().width(fileLabel)+8;
            int folderMin=ctx.font().width(folderLabel)+8;
            boolean showFolder=editor.canCreateFolder();
            boolean full=showFolder&&width>=fileMin+folderMin+3;
            int folderWidth=full?folderMin:showFolder&&width>=52?22:0;
            int fileWidth=width-(folderWidth==0?0:folderWidth+3);
            button(ctx,new Rect(x,bodyY,fileWidth,rowHeight),fileWidth>=fileMin?fileLabel:"+",
                    wt("explorer_new_file_hint"),editor::newFile);
            if(folderWidth>0){
                Rect folderButton=new Rect(x+fileWidth+3,bodyY,folderWidth,rowHeight);
                button(ctx,folderButton,full?folderLabel:"",wt("explorer_new_folder_hint"),editor::newFolder);
                if(!full){
                    int iconX=folderButton.x()+(folderButton.width()-12)/2;
                    int iconY=folderButton.y()+5;
                    int color=folderButton.contains(ctx.mouseX(),ctx.mouseY())?text():dim();
                    ctx.graphics().fill(iconX,iconY,iconX+5,iconY+2,color);
                    ctx.graphics().renderOutline(iconX,iconY+1,12,8,color);
                }
            }
            bodyY+=rowHeight+2;
        }
        int bodyHeight=Math.max(0,panelBounds.bottom()-bodyY-2);
        renderComponent(files(),new Rect(x,bodyY,width,bodyHeight),ctx);
    }

    private void rail(MaredRenderContext ctx){
        var g=ctx.graphics();face(g,frame.rail(),panel(),prefs.depth);
        button(ctx,new Rect(3,3,Math.max(1,frame.rail().width()-6),25),frame.rail().width()>40?"Genesis":"","Return to Genesis",()->{suspended();RuntimeProvider.get().navigation().returnToGenesis();});
        if(frame.rail().width()<=40)octahedron(g,15,15,8,phase,0xFFFFFFFF);
        var sections=CreationCatalog145.sections(direction145);
        int start=34,bottom=Math.max(start,height-78),max=Math.max(0,sections.size()*24-(bottom-start));railScroll=Math.min(railScroll,max);
        g.enableScissor(0,start,frame.rail().right(),bottom);
        for(int i=0;i<sections.size();i++){
            Section section=sections.get(i);int y=start+i*24-railScroll;if(y<start||y+20>bottom)continue;
            String label=frame.rail().width()>64?ctx.font().plainSubstrByWidth(MaredLang.get(section.key()),Math.max(0,frame.rail().width()-34)):"";
            Rect row=new Rect(3,y,Math.max(1,frame.rail().width()-6),20);
            boolean active=section.id().equals(section145.id()),hover=row.contains(ctx.mouseX(),ctx.mouseY());
            g.fill(row.x(),row.y(),row.right(),row.bottom(),active?selected():hover?hover():panel());
            drawSection156(ctx,section.id(),8,y+3,active?accent():dim());
            if(!label.isEmpty())g.drawString(ctx.font(),label,27,y+6,active?text():dim(),false);
            buttons.add(new Button(row,label,MaredLang.get(section.key()),()->selectSection145(section),false));
            if(section.id().equals(section145.id()))g.fill(0,y+4,2,y+16,accent());
        }
        g.disableScissor();
        button(ctx,new Rect(3,Math.max(34,height-73),Math.max(1,frame.rail().width()-6),20),frame.rail().width()>40?"Workspace":"W",MaredLang.get("mared.creation145.workspace"),()->{suspended();com.fixmer.mared.gui2.development.DevelopmentNavigation129.open(com.fixmer.mared.gui2.development.DevelopmentHub129.Section.TEAM);});
        button(ctx,new Rect(3,Math.max(56,height-49),Math.max(1,frame.rail().width()-6),20),frame.rail().width()>40?wt("help"):"?",wt("help"),this::toggleHelp);
        button(ctx,new Rect(3,Math.max(78,height-25),Math.max(1,frame.rail().width()-6),20),frame.rail().width()>40?wt("editor_settings"):"*",wt("editor_settings"),this::openAllSettings);
    }
    private void drawSection156(MaredRenderContext ctx,String id,int x,int y,int color){
        var shape=com.fixmer.genesis.technology.editor.SectionIcons156.shape(id);
        for(int row=0;row<shape.size();row++){String pixels=shape.get(row);for(int col=0;col<pixels.length();col++)if(pixels.charAt(col)=='#')ctx.graphics().fill(x+col,y+row,x+col+1,y+row+1,color);}
    }
    private void openProjectSearch(){if(nativeEditor133()&&!legacyCommands133){sourceEditor().searchProjectText();return;}showSharedProject=false;sourceEditor().setReadOnly(false);selected=0;session.actionContext().setCommandsActive(true);prefs.selectedTab=0;showLinks=false;pluginPanelId=null;showProjectSearch=true;clearFocus();session.focusManager().request(projectSearch);projectSearch.focusQuery();}
    private void closeProjectSearch(){showProjectSearch=false;clearFocus();workspace().requestFocus();}
    private String wt(String key){return MaredLang.get("mared.workbench."+key);}
    private void editProject(){legacyCommands133=false;selected=commandContext133?0:1;prefs.selectedTab=selected;showSharedProject=false;showLinks=false;showProjectSearch=false;pluginPanelId=null;sourceEditor().setReadOnly(false);session.actionContext().setCommandsActive(legacyCommands133&&selected==0);clearFocus();}
    private void projectMenu(int x,int y){
        if(legacyCommands133){session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(x,y,List.of(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.commands133.projects_view"),()->{legacyCommands133=false;session.actionContext().setCommandsActive(false);clearFocus();}),com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("transfer"),()->session.executeAction(StudioActions.PROJECT_TRANSFER)))).category(accent()));return;}
        if(selected<=1){sourceEditor().projectMenu(x,y);return;}
        session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(x,y,List.of(
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("edit_project"),this::editProject),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("view_project"),()->{showSharedProject=true;showProjectSearch=false;showLinks=false;pluginPanelId=null;sourceEditor().setReadOnly(true);session.actionContext().setCommandsActive(legacyCommands133&&selected==0);clearFocus();}),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("new_project"),()->{editProject();sourceEditor().newProject();}),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("import_project"),()->{editProject();sourceEditor().browseImportFolder();}),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.scripts.import_minecraft"),()->{editProject();sourceEditor().importMinecraftMod();}),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("plugins"),this::nextPluginPanel),
            com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("return_tools"),()->{showSharedProject=false;sourceEditor().setReadOnly(false);session.actionContext().setCommandsActive(legacyCommands133&&selected==0);clearFocus();})
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
    private void showExplorer(boolean reveal) {
        prefs.filesOpen=true;
        if(reveal&&nativeEditor133())sourceEditor().revealActiveFile();
        session.focusManager().request(files());
    }

    private void viewMenu(int x,int y){
        var entries=new ArrayList<com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry>();
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.tools147.open"),()->toolsMenu147(x,y)));
        if(selected==0)entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get(legacyCommands133?"mared.commands133.projects_view":"mared.commands133.legacy_view"),()->{legacyCommands133=!legacyCommands133;showSharedProject=showLinks=showProjectSearch=false;pluginPanelId=null;session.actionContext().setCommandsActive(legacyCommands133);clearFocus();}));
        if(selected==0)entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(MaredLang.get("mared.commands133.reference"),()->{commandReference133=true;prefs.helpOpen=true;clearFocus();}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("focus_files"),"Ctrl+Shift+E",()->showExplorer(true)));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("focus_next"),"F6",()->cyclePaneFocus(false)));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.hotkey(wt("focus_previous"),"Shift+F6",()->cyclePaneFocus(true)));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt(prefs.filesOpen?"hide_files":"show_files"),()->{if(prefs.filesOpen){prefs.filesOpen=false;clearFocus();}else showExplorer(false);}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt(prefs.logsOpen?"hide_logs":"show_logs"),()->{prefs.logsOpen=!prefs.logsOpen;clearFocus();}));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt(prefs.helpOpen?"hide_help":"show_help"),this::toggleHelp));
        entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt("editor_settings"),()->{if(nativeEditor133()){openAllSettings();return;}settings=true;prefs.helpOpen=false;clearFocus();}));
        session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(x,y,entries).category(accent()));
    }
    private void openMenu(String key,int x,int y){
        if(key.equals("view_menu")){viewMenu(x,y);return;}
        if(key.equals("project_menu")){projectMenu(x,y);return;}
        if(nativeEditor133()){if(key.equals("file_menu"))sourceEditor().fileMenu(x,y);else{sourceEditor().runMenu(x,y);}}
        else commandMenu(x,y,key.equals("run_menu"));
    }
    private void toolbar(MaredRenderContext ctx){
        face(ctx.graphics(),frame.toolbar(),panel(),prefs.depth);int x=frame.toolbar().x()+6;
        String helpLabel=wt("help");
        int helpW=Math.min(70,Math.max(40,ctx.font().width(helpLabel)+8));
        int scriptReserve=selected==1&&sourceEditor().hasProject()?65:0;
        int menuRight=frame.toolbar().right()-helpW-paletteWidth(ctx)-13;
        if(serverTool145()){sectionTabs145(ctx);toolbarAccess(ctx,helpW);return;}
        String[] menuKeys={"file_menu","project_menu","run_menu","view_menu"};
        for(int i=0;i<menuKeys.length;i++){
            String key=menuKeys[i];int ww=Math.max(36,ctx.font().width(wt(key))+14),xx=x;
            if(x+ww+(i<menuKeys.length-1?23:0)>menuRight-scriptReserve){
                int first=i;button(ctx,new Rect(x,3,20,16),"…",wt("more_menus"),()->{
                    var entries=new ArrayList<com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry>();
                    for(int j=first;j<menuKeys.length;j++){String remaining=menuKeys[j];entries.add(com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry.of(wt(remaining),()->openMenu(remaining,xx,22)));}
                    session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay(xx,22,entries).category(accent()));
                });x+=23;break;
            }
            button(ctx,new Rect(x,3,ww,16),wt(key),wt(key),()->openMenu(key,xx,22));x+=ww+3;
        }
        if(showSharedProject&&selected!=1){int ww=ctx.font().width(wt("read_only"))+10;if(x+ww<menuRight)ctx.graphics().drawString(ctx.font(),wt("read_only"),x+4,7,dim(),false);}
        else if(selected<=1&&!legacyCommands133&&!serverTool145()&&!modelTool146()&&!toolCatalog147()){
            if(sourceEditor().hasProject()){
                int saveW=ctx.font().width(wt("save"))+12;
                if(x+saveW+3+48<menuRight){button(ctx,new Rect(x,3,saveW,16),wt("save"),wt("save"),sourceEditor()::saveAll);x+=saveW+3;}
                int launchW=Math.min(Math.min(130,Math.max(38,ctx.font().width(sourceEditor().launchLabel())+12)),Math.max(20,menuRight-x-21));
                if(x+launchW+20<menuRight){int xx=x;
                    button(ctx,new Rect(x,3,launchW,16),launchW<42?"▶":com.fixmer.mared.gui2.framework.render.TextUtils.ellipsize(ctx.font(),sourceEditor().launchLabel(),launchW-8),sourceEditor().launchLabel(),()->{if(prefs.scriptsAutoOpenLogs)prefs.logsOpen=true;sourceEditor().launchPrimary();});
                    button(ctx,new Rect(x+launchW+1,3,17,16),"▾",MaredLang.get("mared.scripts.launch_choose"),()->sourceEditor().launchMenu(xx,22));x+=launchW+21;
                }else if(x+20<menuRight){int xx=x;button(ctx,new Rect(x,3,18,16),"▾",MaredLang.get("mared.scripts.launch_choose"),()->sourceEditor().launchMenu(xx,22));x+=21;}
                if(sourceEditor().canStopPrimary()){int stopW=ctx.font().width(wt("stop"))+12;if(x+stopW<menuRight)button(ctx,new Rect(x,3,stopW,16),wt("stop"),wt("stop"),sourceEditor()::stopPrimary);}
            }

        }
        String caption=legacyCommands133?"Commands":sourceEditor().hasProject()?sourceEditor().projectName():wt("no_project");
        if(x+ctx.font().width(caption)+12<menuRight)ctx.graphics().drawString(ctx.font(),caption,x+6,7,dim(),false);
        toolbarAccess(ctx,helpW);
        sectionTabs145(ctx);
        if(nativeEditor133()){
            int available=frame.toolbar().width()-12;
            int fileWidth=(available-4)/2;
            button(ctx,new Rect(frame.toolbar().x()+6,50,fileWidth,20),wt("quick_files"),
                    wt("quick_files_hint"),sourceEditor()::searchFiles);
            button(ctx,new Rect(frame.toolbar().x()+10+fileWidth,50,available-fileWidth-4,20),
                    wt("quick_project"),wt("quick_project_hint"),sourceEditor()::searchProjectText);
        }
    }

    private int paletteWidth(MaredRenderContext ctx){
        return frame.toolbar().width()<300?26:Math.min(100,ctx.font().width(wt("actions"))+14);
    }

    private void toolbarAccess(MaredRenderContext ctx,int helpWidth){
        int right=frame.toolbar().right()-5;
        button(ctx,new Rect(right-helpWidth,3,helpWidth,16),wt("help"),wt("help")+" · F1",this::toggleHelp);
        int paletteWidth=paletteWidth(ctx);
        button(ctx,new Rect(right-helpWidth-paletteWidth-4,3,paletteWidth,16),
                frame.toolbar().width()<300?">_":wt("actions"),wt("actions_hint"),
                ()->session.executeAction(StudioActions.VIEW_COMMAND_PALETTE));
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
        if(nativeEditor133()){
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(mc.screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen shell)shell.suspendForSettings();
            mc.setScreen(new com.fixmer.mared.gui2.settings.MaredSettingsScreen(mc.screen,prefs,()->sourceEditor().trimLogs(),action->{
                if(action.equals("dependencies"))sourceEditor().manageDependencies();else if(action.equals("check"))sourceEditor().checkBuildTools();else if(action.startsWith("build_"))sourceEditor().configureBuildOption(action);else sourceEditor().configureBuildTool(action);
            },selected==0));
        }else session.executeAction(StudioActions.VIEW_SETTINGS);
    }
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
        if(prefs.helpOpen&&(nativeEditor133()))session.focusManager().request(handbook());
    }
    private void toggleHelp(){commandReference133=false;prefs.helpOpen=!prefs.helpOpen;settings=false;focusHelp();}
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
            g.drawString(ctx.font(),ctx.font().plainSubstrByWidth((nativeEditor133()?MaredLang.get("mared.scripts.guide"):EditorText.translate("Command handbook")),Math.max(0,r.width()-141)),r.x()+10,r.y()+10,text(),false);
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
        ctx.graphics().enableScissor(r.x(),r.y(),r.right(),r.bottom());
        try {c.render(ctx);} finally {ctx.graphics().disableScissor();}
        if(c.isFocused())ctx.graphics().renderOutline(r.x(),r.y(),r.width(),r.height(),text());
    }
    private void divider(MaredRenderContext ctx,int x,int y,int bottom,boolean resizable){boolean hover=resizable&&Math.abs(ctx.mouseX()-x)<=2&&ctx.mouseY()>=y&&ctx.mouseY()<bottom;ctx.graphics().fill(x,y,x+1,bottom,hover?accent():edge());}
    private void clearFocus(){if(!showProjectSearch)projectSearch.cancelInteraction();captured=null;session.pointerManager().releaseAll();session.focusManager().clear();}
    private List<MaredComponent> visible(){var result=new ArrayList<MaredComponent>(4);if(prefs.filesOpen&&!serverTool145())result.add(files());var plugin=pluginPanel();if(serverTool145())result.add(development151);else if(showProjectSearch)result.add(projectSearch);else if(showSharedProject)result.add(sourceEditor());else if(showLinks||selected==4)result.add(links);else if(plugin!=null)result.add(plugin.component());else if(selected<=1&&!legacyCommands133&&!serverTool145()&&!modelTool146()&&!toolCatalog147())result.add(sourceEditor());else if(selected==0)result.add(workspace());if(prefs.logsOpen&&!serverTool145())result.add(logs());if(prefs.helpOpen&&!settings)result.add(handbook());return result;}
    public boolean click(double mx,double my,int button){
        if(frame==null)return false;
        if(session.overlayManager().mouseClicked(mx,my,button)||session.overlayManager().shouldBlockGenericInput())return true;
        if(helpFloating()&&helpWindow!=null&&helpWindow.contains(mx,my)){
            if(button==0){
                for(int i=buttons.size()-1;i>=0;i--){Button b=buttons.get(i);if(b.floating&&b.bounds.contains(mx,my)){b.action.run();return true;}}
                if(mx>=helpWindow.right()-12&&my>=helpWindow.bottom()-12){beginWindowDrag(5,mx,my);return true;}
                if(my<helpWindow.y()+22){beginWindowDrag(4,mx,my);return true;}
            }
            if(handbook().mouseClicked(mx,my,button))captured=handbook();
            return true;
        }
        if(button==0&&Math.abs(mx-frame.rail().right())<=3&&my>=30&&my<frame.status().y()){drag=6;clearFocus();return true;}
        if(button==0){
            // Header buttons have priority over neighbouring resize handles.
            for(Button b:buttons)if(!b.floating&&b.bounds.contains(mx,my)){b.action.run();return true;}
            if(my>=frame.files().y()&&my<frame.files().bottom()){
                if(!serverTool145()&&prefs.filesOpen&&Math.abs(mx-frame.files().right())<=3){drag=1;clearFocus();return true;}
                if(helpColumn()&&Math.abs(mx-frame.help().x())<=3){drag=2;clearFocus();return true;}
            }
            if(!serverTool145()&&prefs.logsOpen&&mx>=frame.logs().x()&&mx<frame.logs().right()&&Math.abs(my-frame.logs().y())<=3){drag=3;clearFocus();return true;}
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
            if(drag==6)prefs.railWidth152=Math.max(34,Math.min(Math.min(300,width/3),(int)Math.round(mx)));
            if(drag==1)prefs.filesRatio=Math.max(.08,Math.min(.6,(mx-frame.rail().width())/Math.max(1,width-frame.rail().width())));
            if(drag==2)prefs.helpRatio=Math.max(.08,Math.min(.6,(width-mx)/Math.max(1,width-frame.rail().width())));
            if(drag==3)prefs.logsRatio=Math.max(.08,Math.min(.65,(frame.status().y()-my)/Math.max(1,frame.status().y()-frame.toolbar().height())));
            return true;
        }
        return captured!=null&&captured.mouseDragged(mx,my,button,dx,dy);
    }
    public boolean release(double mx,double my,int button){boolean handled=drag!=0;if(drag==6)prefs.save();drag=0;if(captured!=null){handled|=captured.mouseReleased(mx,my,button);captured=null;}return handled;}
    public boolean scroll(double mx,double my,double dx,double dy){
        if(session.overlayManager().mouseScrolled(mx,my,dx,dy)||session.overlayManager().shouldBlockGenericInput())return true;
        if(helpFloating()&&helpWindow!=null&&helpWindow.contains(mx,my)){handbook().mouseScrolled(mx,my,dx,dy);return true;}
        if(frame!=null&&toolCatalog147()&&frame.editor().contains(mx,my)){toolsScroll151=Math.max(0,Math.min(toolsMax151,toolsScroll151+(int)(-dy*26)));return true;}
        if(frame!=null&&frame.rail().contains(mx,my)){railScroll=Math.max(0,railScroll+(dy<0?22:dy>0?-22:0));return true;}
        if(frame!=null&&settings&&frame.help().contains(mx,my)){settingsScroll=Math.max(0,Math.min(Math.max(0,197-frame.help().height()),settingsScroll+(dy<0?22:dy>0?-22:0)));return true;}
        for(var c:visible())if(c.mouseScrolled(mx,my,dx,dy))return true;return false;
    }
    private boolean cyclePaneFocus(boolean reverse){
        if(frame==null||width<260||height<150)return false;
        var panes=visible().stream().filter(MaredComponent::focusable)
                .filter(pane->pane!=files()||frame.files().width()>25)
                .filter(pane->pane!=logs()||frame.logs().height()>20)
                .toList();
        if(panes.isEmpty())return false;
        int current=panes.indexOf(session.focusManager().owner());
        int next=current<0?(reverse?panes.size()-1:0):Math.floorMod(current+(reverse?-1:1),panes.size());
        session.focusManager().request(panes.get(next));
        return true;
    }

    public boolean key(int key,int scan,int mods){
        session.actionContext().setCommandsActive(legacyCommands133&&selected==0);
        if(session.overlayManager().keyPressed(key,scan,mods)||session.overlayManager().shouldBlockGenericInput())return true;
        int commandModifiers=mods&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SHIFT|GLFW.GLFW_MOD_ALT|GLFW.GLFW_MOD_SUPER);
        if(key==GLFW.GLFW_KEY_P&&commandModifiers==(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SHIFT)){
            return session.executeAction(StudioActions.VIEW_COMMAND_PALETTE);
        }
        if(key==GLFW.GLFW_KEY_F1&&commandModifiers==0){
            toggleHelp();
            return true;
        }
        if(key==GLFW.GLFW_KEY_F6&&(commandModifiers==0||commandModifiers==GLFW.GLFW_MOD_SHIFT)){
            if(cyclePaneFocus(commandModifiers!=0))return true;
        }
        if(key==GLFW.GLFW_KEY_E&&commandModifiers==(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SHIFT)&&!serverTool145()){
            return session.executeAction("genesis:scripts.reveal_file");
        }
        if(nativeEditor133()&&commandModifiers==GLFW.GLFW_MOD_CONTROL&&key==GLFW.GLFW_KEY_P){
            sourceEditor().searchFiles();return true;
        }
        if(nativeEditor133()&&commandModifiers==(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SHIFT)&&key==GLFW.GLFW_KEY_F){
            sourceEditor().searchProjectText();return true;
        }
        // Plain Find stays within the active help, logs or editor pane.
        // Scripts Help owns its input in both floating and docked layouts.
        if((nativeEditor133())&&prefs.helpOpen&&!settings&&session.focusManager().owner()==handbook()){
            if(key==256){if(!handbook().keyPressed(key,scan,mods))closeHelp();return true;}
            if(key!=295)return handbook().keyPressed(key,scan,mods);
        }
        if((nativeEditor133())&&session.focusManager().owner()==sourceEditor().logsPane()&&sourceEditor().logsPane().keyPressed(key,scan,mods))return true;
        if(serverTool145())return session.focusManager().owner()==development151&&development151.keyPressed(key,scan,mods);
        if(nativeEditor133()&&commandModifiers==GLFW.GLFW_MOD_CONTROL){
            switch(key){
                case GLFW.GLFW_KEY_F -> {sourceEditor().searchText();return true;}
                case GLFW.GLFW_KEY_G -> {sourceEditor().goToLine();return true;}
                case GLFW.GLFW_KEY_O -> {sourceEditor().searchOpenFiles();return true;}
            }
        }
        if(selected!=1&&!showSharedProject&&key==70&&(mods&3)==3){openProjectSearch();return true;}
        if(selected!=1&&key==80&&(mods&6)==6){nextPluginPanel();return true;}
        if(key==256&&helpFloating()){
            if(handbook().keyPressed(key,scan,mods))return true;
            closeHelp();return true;
        }
        if(helpFloating()&&key!=295&&session.focusManager().owner()==handbook())return handbook().keyPressed(key,scan,mods);
        if(selected==0&&!legacyCommands133&&key==296){commands.checkCommands133();return true;}
        if(key==256){var owner=session.focusManager().owner();return owner!=null&&visible().contains(owner)&&owner.keyPressed(key,scan,mods);}
        if(showProjectSearch&&session.focusManager().owner()==projectSearch&&key!=295)return projectSearch.keyPressed(key,scan,mods);
        if((showLinks||selected==4)&&session.focusManager().owner()==links&&links.keyPressed(key,scan,mods))return true;
        if(session.actions().executeByKey(key,mods,session.actionContext()))return true;
        MaredComponent owner=session.focusManager().owner();if(owner!=null&&visible().contains(owner))return owner.keyPressed(key,scan,mods);return false;
    }
    public boolean character(char c,int mods){if(session.overlayManager().charTyped(c,mods)||session.overlayManager().shouldBlockGenericInput())return true;var owner=session.focusManager().owner();return owner!=null&&visible().contains(owner)&&owner.charTyped(c,mods);}
}
