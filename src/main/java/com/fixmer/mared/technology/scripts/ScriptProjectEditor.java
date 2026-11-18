package com.fixmer.mared.technology.scripts;

import com.fixmer.mared.Mared;


import com.fixmer.genesis.technology.scripts.SourceProjects;
import com.fixmer.genesis.technology.scripts.SourceProjects.*;
import com.fixmer.mared.MaredLang;
import com.fixmer.genesis.technology.editor.ProjectFileTree;
import com.fixmer.genesis.technology.storage.RepositoryPath;
import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.gui2.framework.components.editor.MaredMultiLineEditBox;
import com.fixmer.mared.gui2.framework.overlay.*;
import com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession;
import net.neoforged.fml.loading.FMLPaths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Independent local source editor. Does not import Commands, MR, their catalog or runner. */
public final class ScriptProjectEditor extends MaredComponent implements Disposable {
    public static final int ASSIST_VERSION=33;
    public static final int UX_VERSION=34;
    private com.fixmer.mared.technology.editor.WorkbenchPreferences preferences=new com.fixmer.mared.technology.editor.WorkbenchPreferences();
    private final Deque<com.fixmer.mared.services.logging.LogEntry> scriptLogEntries=new ArrayDeque<>();
    private int scriptLogRevision,scriptFilterRevision;
    public void preferences(com.fixmer.mared.technology.editor.WorkbenchPreferences value){preferences=Objects.requireNonNull(value);scriptFilterRevision++;}
    public void trimLogs(){int max=Math.max(256,Math.min(2048,preferences.scriptsLogCapacity));while(scriptLogEntries.size()>max)scriptLogEntries.removeFirst();while(logLines.size()>max)logLines.removeFirst();scriptLogRevision++;}
    public void clearLogs(){scriptLogEntries.clear();logLines.clear();scriptLogRevision++;}
    private record Key(String project,String file) {}
    private record StartHit(MaredBounds bounds,Runnable action) {}
    private List<StartHit> startHits=List.of();
    private int startScroll,startMaxScroll;
    private record TabHit(Key key,int x,int width) {}
    private List<TabHit> tabHits=List.of();
    private final Map<String,Key> selectedDocuments=new HashMap<>();
    private int tabOffset;
    private boolean revealTab;
    private record Snapshot(Key key,String baseline,String text) {}
    private record Loaded(Project project,String path,String text,Recovery recovery,String recoveryError) {}
    private record ProjectFiles(Project project,List<String> files,String mainClass,boolean gradle,String task,ProjectFileTree tree,boolean imported,boolean watch,java.nio.file.Path directory) {}
    private record ImportedFiles(SourceProjects.Imported imported,ProjectFiles files) {}
    private record MinecraftImported(com.fixmer.genesis.technology.minecraft.MinecraftProjectImporter.Result imported,ProjectFiles files) {}
    private record Startup(Catalog catalog,com.fixmer.genesis.technology.scripts.EditorWorkspaceState.State state,ProjectFiles selected,List<Loaded> documents,List<String> warnings) {}
    private boolean replaceMatchCase=true,replaceWholeWord,replaceSelectedOnly;
    private String lastReplaceQuery="",lastReplacement="";
    private final Map<Key,com.fixmer.mared.gui2.framework.components.editor.CodeSnippets.Session> snippetSessions=new HashMap<>();
    private boolean workspaceLoaded;
    private static final ConcurrentHashMap<java.nio.file.Path,CompletableFuture<Void>> closingWorkspaces=new ConcurrentHashMap<>();
    private record Refreshed(Catalog catalog,ProjectFiles selected,Map<String,ProjectFileTree> trees) {}
    private record PreparedBuild(java.nio.file.Path directory,com.fixmer.genesis.technology.scripts.BuildTools.Settings tools,com.fixmer.genesis.technology.scripts.GradleBuildRunner.Options options) {}
    private record BuildReport(String project,String task,java.nio.file.Path directory,com.fixmer.genesis.technology.scripts.GradleBuildRunner.Result result) {}
    private final Map<String,com.fixmer.genesis.technology.scripts.GradleTasks.Catalog> taskCatalogs=new HashMap<>();
    private final Map<String,String> taskReports=new LinkedHashMap<>();
    private boolean listingTasks;
    private enum LaunchMode {BUILD,FILE,SERVER,DEDICATED,REMOTE152,AUTO156}
    private final Map<String,LaunchMode> launchModes=new HashMap<>();
    private final Map<String,BuildReport> buildReports=new LinkedHashMap<>();
    private final Map<String,com.fixmer.genesis.technology.scripts.BuildDiagnostics.Report> buildDiagnostics=new HashMap<>();
    private record DiagnosticTarget(ProjectFiles files,Key key,Loaded loaded) {}
    private java.nio.file.Path activeBuildDirectory;
    private record SaveBatch(List<Snapshot> saved,String error) {}
    private final SourceProjects storage;
    private com.fixmer.genesis.technology.creation.CreationCatalog145.Direction direction145=com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.MODS;
    private com.fixmer.genesis.technology.creation.CreationCatalog145.Section area145;
    private boolean scripting145;
    public void context145(com.fixmer.genesis.technology.creation.CreationCatalog145.Direction direction,com.fixmer.genesis.technology.creation.CreationCatalog145.Section area){
        direction145=direction;scripting145=area.tool()==com.fixmer.genesis.technology.creation.CreationCatalog145.Tool.SCRIPTS;area145=area;filesPane.rebuild();
    }
    private ProjectFileTree filtered145(ProjectFileTree source){
        if(area145==null||area145.paths().isEmpty())return source;
        return ProjectFileTree.of(source.rows(source.directories()).stream().filter(row->!row.directory()&&area145.accepts(row.path())).map(ProjectFileTree.Row::path).toList(),
            source.directories().stream().filter(area145::accepts).toList());
    }
    private void newContentProject145(){
        if(!workspaceLoaded||readOnly||disposed||pending!=null)return;
        final var direction=direction145;
        overlays.push(new NameDialogOverlay(MaredLang.get("mared.creation145.new_project")+" · "+MaredLang.get(direction.key()),name->submit(()->projectFiles(storage.createContent145(name,direction)),result->{
            ProjectFiles snapshot=(ProjectFiles)result;var catalog=new ArrayList<>(projects);catalog.add(snapshot.project);catalog.sort(Comparator.comparing(Project::id));projects=List.copyOf(catalog);applyProjectFiles(snapshot);open(snapshot.files.contains("pack.mcmeta")?"pack.mcmeta":"README.md");
        }),accent(),false,name->SourceProjects.validProject(name)?null:tr("bad_project")).customInput().acceptLabel("mared.dialog.create"));
    }
    private boolean commandsMode133;
    private final com.fixmer.mared.technology.editor.EditorRuns commandRuns133=new com.fixmer.mared.technology.editor.EditorRuns();
    private final com.fixmer.mared.technology.minecraft.MinecraftImportActions minecraftImports;
    private final JavaProjectRuns runs;
    private final ServerProjectApplies applies;
    private final Map<String,String> moduleEntries=new HashMap<>();
    private final com.fixmer.genesis.technology.scripts.GradleBuildRunner builds=new com.fixmer.genesis.technology.scripts.GradleBuildRunner();
    private boolean gradleProject,importedGradle,buildPreparing,buildPreparationCancelled,importing,delivering;
    private String importingProject="",deliveryProject="";
    private final java.util.concurrent.atomic.AtomicBoolean importCancellation=new java.util.concurrent.atomic.AtomicBoolean();
    private String gradleTask="build",buildProject="",activeBuildTask="";
    private final StringBuilder buildLine=new StringBuilder();
    private final Map<String,String> entryPoints=new HashMap<>();
    private final OverlayManager overlays;
    private final Consumer<String> logger;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"Genesis source projects");t.setDaemon(true);return t;});
    private Future<?> pending;
    private Consumer<Object> completed;
    private final Map<Key,DocumentSession> documents=new LinkedHashMap<>();
    // Only accessed by the I/O worker; reconciles close while a save is still queued.
    private final Map<Key,Recovery> workerWrites=new HashMap<>();
    private final Map<Key,String> checkpointed=new HashMap<>();
    private MaredMultiLineEditBox editor;
    private final MaredMultiLineEditBox leftEditor,rightEditor;
    private final com.fixmer.genesis.technology.editor.EditorSplitState<Key> panes=new com.fixmer.genesis.technology.editor.EditorSplitState<>();
    private final Set<MaredMultiLineEditBox> detachedPanes=Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean splitVisible,draggingDivider;
    private int dividerX;
    private String splitProject="";
    private final FilesPane filesPane=new FilesPane();
    private final ScriptHelpBrowser guide=new ScriptHelpBrowser();
    private final LogsPane logsPane=new LogsPane();
    private final Deque<String> logLines=new ArrayDeque<>();
    private List<Project> projects=List.of();
    private List<String> files=List.of();
    private Project project;
    private Key active;
    private Language newLanguage=Language.JAVA;
    private String message="";
    private String lastRunOutput="";
    private String copyProject="",copyPath="";
    private java.nio.file.Path lastTrash;
    private String lastTrashProject="";
    private boolean disposed,loadedCatalog,checkpointRequested,readOnly;
    private long retryAfter;
    private long changedAt;
    public ScriptProjectEditor(OverlayManager overlays,Consumer<String> logger){this(new SourceProjects(com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.workspace(FMLPaths.CONFIGDIR.get().resolve("mared/script_projects"))),overlays,logger);}
    public ScriptProjectEditor(SourceProjects storage,OverlayManager overlays,Consumer<String> logger){
        this.storage=storage;this.overlays=overlays;this.logger=logger;this.minecraftImports=new com.fixmer.mared.technology.minecraft.MinecraftImportActions(storage);this.runs=new JavaProjectRuns(storage);this.applies=new ServerProjectApplies(storage);
        leftEditor=new MaredMultiLineEditBox(0,0,1,1,accent(),()->changedPane(false));
        rightEditor=new MaredMultiLineEditBox(0,0,1,1,accent(),()->changedPane(true));
        editor=leftEditor;leftEditor.setEditable(false);rightEditor.setEditable(false);BackgroundAutoApply.editorOpened(this);Mared.LOGGER.info("[Genesis Scripts] editor assist v33 loaded");Mared.LOGGER.info("[Genesis UI] usability v34 loaded");
    }
    private com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122 shared132;
    private String sharedId132="",sharedStamp132="",localProject132="";
    private boolean sharedActive132(){return shared132!=null&&project!=null&&project.id().equals(sharedId132);}
    public void joinShared132(com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122 shared){
        if(shared==null||disposed||commandsMode133!="COMMANDS".equals(shared.context132().get("mode133")))return;
        if(!workspaceLoaded||pending!=null){requestedShared132=shared;return;}
        if(shared132!=shared){if(shared132!=null)shared132.releaseViews132();localProject132=project==null?"":project.id();shared132=shared;sharedId132=shared.context132().getOrDefault("actor","").equals(shared.context132().get("owner"))&&!shared.context132().getOrDefault("source132","").isEmpty()?shared.context132().get("source132"):"shared-"+shared.context132().get("project");sharedStamp132="";}
        applyShared132();
        if(active!=null&&shared.files132().contains(active.file))open(active.file);else if(!shared.files132().isEmpty())open(shared.files132().stream().sorted(Comparator.comparing((String f)->!f.startsWith("src/"))).findFirst().orElseThrow());
    }
    private com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122 requestedShared132;
    private void applyShared132(){
        if(shared132==null)return;var paths=shared132.files132();Language language=paths.stream().anyMatch(f->f.endsWith(".java"))?Language.JAVA:paths.stream().anyMatch(f->f.endsWith(".py"))?Language.PYTHON:paths.stream().anyMatch(f->f.endsWith(".rs"))?Language.RUST:Language.CPP;if(!commandsMode133&&!shared132.context132().getOrDefault("direction147","mods").equals("mods"))language=Language.FILES;Project virtual=new Project(sharedId132,language);
        var catalog=new ArrayList<>(projects.stream().filter(p->!p.id().equals(sharedId132)).toList());catalog.add(virtual);projects=List.copyOf(catalog);
        if(active!=null&&active.project.equals(sharedId132)&&!paths.contains(active.file)){active=null;editor.attachDocument(null,null,null);}
        applyProjectFiles(new ProjectFiles(virtual,paths,"",false,"build",ProjectFileTree.of(paths),false,false,null));
        if(sharedStamp132.isEmpty()){filesPane.openedProjects.add(sharedId132);filesPane.setExpanded(true);}sharedStamp132=paths.toString();
    }
    public void openSharedFile132(String path,int line){if(!sharedActive132())return;open(path,()->{var d=documents.get(active);if(d!=null)d.document.setCursor(Math.max(0,Math.min(d.document.lineCount()-1,line-1)),0);});}
    private void tickShared132(){
        if(shared132==null&&requestedShared132==null&&com.fixmer.mared.technology.workspace.live122.WorkspaceIntegrated132.current132()!=null&&commandsMode133=="COMMANDS".equals(com.fixmer.mared.technology.workspace.live122.WorkspaceIntegrated132.current132().context132().get("mode133")))requestedShared132=com.fixmer.mared.technology.workspace.live122.WorkspaceIntegrated132.current132();
        if(requestedShared132!=null&&workspaceLoaded&&pending==null){var requested=requestedShared132;requestedShared132=null;joinShared132(requested);}
        if(shared132==null)return;
        if(com.fixmer.mared.technology.workspace.live122.WorkspaceIntegrated132.current132()!=shared132){shared132.releaseViews132();boolean temporary=sharedId132.startsWith("shared-");if(temporary){String oldId=sharedId132;documents.keySet().removeIf(k->k.project.equals(oldId));projects=projects.stream().filter(p->!p.id().equals(oldId)).toList();if(project!=null&&project.id().equals(oldId)){project=null;active=null;editor.attachDocument(null,null,null);filesPane.prune();}}shared132=null;sharedId132="";message="";return;}
        if(sharedActive132()){
            if(!sharedStamp132.equals(shared132.files132().toString()))applyShared132();
            message=shared132.status132();
            for(var entry:documents.entrySet())if(entry.getKey().project.equals(sharedId132))entry.getValue().recomputeDirty();
            shared132.display132(panes.left()==null?"":panes.left().file,leftEditor,splitVisible&&panes.right()!=null?panes.right().file:"",splitVisible?rightEditor:null);
        }else shared132.releaseViews132();
    }
    private void sharedChange132(){if(shared132!=null)for(var key:documents.keySet())if(key.project.equals(sharedId132))shared132.changed132(key.file);}
    private static boolean validChild140(String parent,String name){try{return com.fixmer.genesis.technology.storage.ProjectPath.valid(com.fixmer.genesis.technology.storage.ProjectPath.child(parent,name));}catch(IllegalArgumentException invalid){return false;}}
    private void sharedNew132(){if(!sharedActive132())return;String parent=filesPane.newFileParent();overlays.push(new NameDialogOverlay(tr("new_file"),name->{String path=parent.isEmpty()?name:com.fixmer.genesis.technology.storage.ProjectPath.child(parent,name);shared132.operation132("NEW",Map.of("path",path,"rev",shared132.context132().get("revision")),d->{if("OK".equals(d.get("result"))){applyShared132();open(path);}});},accent(),false,name->validChild140(parent,name)?null:tr("bad_file")).customInput().maxLength(240));}
    private void sharedRename132(){String from=filesPane.selectedEntry;if(!sharedActive132()||!files.contains(from))return;overlays.push(new NameDialogOverlay(tr("rename"),to->shared132.operation132("RENAME",Map.of("path",from,"target",to,"rev",shared132.context132().get("revision")),d->{if("OK".equals(d.get("result"))){applyShared132();open(to);}}),accent(),false,name->com.fixmer.genesis.technology.storage.ProjectPath.valid(name)?null:tr("bad_file")).initialValue(from).customInput().maxLength(240));}
    private void sharedDelete132(){String path=filesPane.selectedEntry;if(!sharedActive132()||!files.contains(path))return;shared132.operation132("DELETE_REVIEW",Map.of("path",path,"rev",shared132.context132().get("revision")),review->{if(!review.containsKey("ticket"))return;overlays.push(new ConfirmDialogOverlay(tr("delete"),tr("delete_notice")+"\n"+path,()->shared132.operation132("DELETE",Map.of("path",path,"ticket",review.get("ticket"),"rev",review.get("revision")),done->{if("OK".equals(done.get("result")))applyShared132();}),true,accent(),true,"mared.dialog.delete"));});}
    private void sharedBuild132(){if(!sharedActive132())return;var mc=net.minecraft.client.Minecraft.getInstance();if(mc.screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen shell)shell.suspendForSettings();mc.setScreen(new com.fixmer.mared.technology.workspace.live122.WorkspaceBuild125(mc.screen,shared132.context132()));}
    public ScriptProjectEditor(OverlayManager overlays,Consumer<String> logger,boolean commands){this(new SourceProjects(com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.workspace(FMLPaths.CONFIGDIR.get().resolve(commands?"mared/command_projects":"mared/script_projects"))),overlays,logger);commandsMode133=commands;guide.commands133(commands);}
    private String tr(String key){String commandKey="mared.commands133."+key;return MaredLang.get(commandsMode133&&MaredLang.get(commandKey).equals(commandKey)==false?commandKey:"mared.scripts."+key);}
    private boolean contentProject145(){return project!=null&&project.language()==Language.FILES;}
    private boolean contentDirection145(){return !commandsMode133&&!scripting145&&direction145!=com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.MODS;}
    private void contentMenu145(int x,int y,boolean projectMenu){
        var items=new ArrayList<ContextMenuEntry>();
        if(projectMenu){items.add(item("new_project",workspaceLoaded&&!readOnly,this::newProject));items.add(item("open_project",true,this::chooseProjectMenu));items.add(item("import_folder",canImportGradle(),this::browseImportFolder));items.add(item("refresh",true,this::refresh));}
        items.add(new ContextMenuEntry(MaredLang.get("mared.creation145.export"),this::exportJava,canExportJava()&&!readOnly,false));
        items.add(item("copy_project_path",project!=null,this::copyProjectPath));
        overlays.push(new ContextMenuOverlay(x,y,items).category(accent()));
    }
    public boolean commandsMode133(){return commandsMode133;}
    public void checkCommands133(){if(!commandsMode133||active==null)return;try{var plan=com.fixmer.mared.technology.runtime.CommandFilePlan.parse(document().getValue());for(var part:plan.parts())if(part.script())com.fixmer.mared.commands.engine.MaredScriptParser.parse(part.text());message=MaredLang.get("mared.commands133.checked");appendLog(message);}catch(RuntimeException e){report(e.getMessage());}}
    private void launchCommands133(){if(!commandsMode133||readOnly||active==null||!commandRuns133.canStart())return;checkCommands133();commandRuns133.start(document().getValue(),projectName()+" / "+active.file);preferences.logsOpen=true;}

    public boolean readOnly(){return readOnly;}
    public void setReadOnly(boolean value){readOnly=value;syncPanes();}
    public com.fixmer.genesis.technology.scripts.ProjectReadView readView(){return new com.fixmer.genesis.technology.scripts.ProjectReadView(storage);}
    public boolean hasProject(){return project!=null;}
    public String projectName(){if(sharedActive132())return shared132.context132().getOrDefault("title",sharedId132);return project==null?tr("no_project"):project.id();}
    /** Capture the existing project; embedded web content never selects a storage root. */
    public boolean modelDraft147(String id,String file){var document=documents.get(new Key(id,file));return document!=null&&!document.currentText().equals(document.lastSavedText);}
    public void seedModels147(){
        if(sharedActive132()&&!"true".equals(shared132.context132().get("models147"))){overlays.push(new ScriptInfoOverlay("Blockbench",MaredLang.get("mared.tools147.update_server"),accent()));return;}
        if(!sharedActive132()||!shared132.context132().getOrDefault("actor","").equals(shared132.context132().get("owner"))||pending!=null){report(MaredLang.get("mared.tools147.seed_owner"));return;}
        String id=shared132.context132().get("source132");var workspace=shared132;
        submit(()->{var models=new TreeMap<String,String>();for(String file:storage.files(id))if(com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(file))models.put(file,storage.read(id,file));return models;},value->{
            @SuppressWarnings("unchecked") var models=(Map<String,String>)value;
            var channel=new com.fixmer.mared.technology.blockbench.WorkspaceModelClient147(workspace.context132(),workspace::modelMetadata147);
            channel.seed(models).whenComplete((ignored,failure)->{channel.close();if(failure!=null)report(failure.toString());else{message=MaredLang.get("mared.tools147.seed_done");refresh();}});
        });
    }
    public void openBlockbench146(boolean create){
        if(sharedActive132()&&!"true".equals(shared132.context132().get("models147"))){overlays.push(new ScriptInfoOverlay("Blockbench",MaredLang.get("mared.tools147.update_server"),accent()));return;}
        if((!sharedActive132()&&!canManageFiles())||builds.busy()||buildPreparing||applies.busy()||importing||delivering){
            overlays.push(new ScriptInfoOverlay("Blockbench",MaredLang.get("mared.blockbench146.unavailable"),accent()));return;
        }
        if(create){launchBlockbench146("");return;}
        String selected=filesPane.selectedEntry;
        if(!com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(selected)&&active!=null&&active.project.equals(project.id()))selected=active.file;
        if(com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(selected)){launchBlockbench146(selected);return;}
        var entries=new ArrayList<ContextMenuEntry>();
        for(String path:files)if(com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(path))entries.add(ContextMenuEntry.of(path,()->launchBlockbench146(path)));
        if(entries.isEmpty())entries.add(ContextMenuEntry.disabled(MaredLang.get("mared.blockbench146.no_models")));
        overlays.push(new ContextMenuOverlay(bounds.x()+8,bounds.y()+38,entries).category(accent()));
    }
    private void launchBlockbench146(String path){
        if(sharedActive132()&&!"true".equals(shared132.context132().get("models147"))){overlays.push(new ScriptInfoOverlay("Blockbench",MaredLang.get("mared.tools147.update_server"),accent()));return;}
        if((!sharedActive132()&&!canManageFiles())||builds.busy()||buildPreparing||applies.busy())return;
        var key=new Key(project.id(),path);var doc=documents.get(key);
        if(doc!=null&&!doc.currentText().equals(doc.lastSavedText)){
            overlays.push(new ScriptInfoOverlay("Blockbench",MaredLang.get("mared.blockbench146.dirty_source"),accent()));return;
        }
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(!(mc.screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen shell))return;
        String id=project.id();
        shell.suspendForSettings();
        if(sharedActive132()){mc.setScreen(new com.fixmer.mared.technology.blockbench.DesktopBlockbenchScreen148(shell,shared132,path,this::refresh));return;}
        mc.setScreen(new com.fixmer.mared.technology.blockbench.DesktopBlockbenchScreen148(shell,storage,id,path,(file,text)->{
            Key savedKey=new Key(id,file);var opened=documents.get(savedKey);
            if(opened!=null){opened.setInitialText(text);configureSyntax(savedKey,opened);checkpointed.put(savedKey,text);syncPanes();}
            invalidateBuild(id);message=MaredLang.get("mared.blockbench146.saved")+": "+file;appendLog(message);
        },this::refresh));
    }
    public boolean canManageFiles(){return !readOnly&&canBrowseFiles()&&!javaRunning();}
    public boolean canCreateFolder(){return canManageFiles()&&!sharedActive132();}
    public MaredComponent filesPane(){return filesPane;}
    public MaredComponent guide(){return guide;}
    public MaredComponent logsPane(){return logsPane;}
    public String language(){return newLanguage.label;}
    public void nextLanguage(){newLanguage=Language.values()[(newLanguage.ordinal()+1)%Language.values().length];}
    public String state(){return tr(pending!=null?"state_busy":dirty()?"state_unsaved":"state_ready");}
    public boolean dirty(){return active!=null&&documents.get(active).dirty;}
    public com.fixmer.mared.gui2.framework.components.editor.EditorDocument document(){return editor.document();}
    private void changed(){if(active!=null&&documents.containsKey(active)){documents.get(active).recomputeDirty();changedAt=System.nanoTime();checkpointRequested=true;sharedChange132();}}
    private void changedPane(boolean right){
        MaredMultiLineEditBox widget=right?rightEditor:leftEditor;if(widget==null)return;
        for(var d:documents.values())if(d.document==widget.document()){d.recomputeDirty();changedAt=System.nanoTime();checkpointRequested=true;sharedChange132();break;}
    }
    public boolean canRunJava(){if(commandsMode133)return false;return !sharedActive132()&&!readOnly&&!disposed&&pending==null&&project!=null&&project.language()==Language.JAVA&&!importedGradle&&files.stream().anyMatch(SourceProjects::directJavaSource)&&!runs.busy()&&!builds.busy()&&!applies.busy()&&!BackgroundAutoApply.busy();}
    public boolean javaRunning(){if(commandsMode133)return commandRuns133.canStop();return BackgroundAutoApply.busy()||runs.busy()||builds.busy()||buildPreparing||importing||delivering||applies.busy()||installingNativeMod;}
    public String runStatus(){if(commandsMode133)return commandRuns133.status();if(applies.busy())return tr("phase."+applies.phase())+" · "+applies.project();if(delivering)return tr("phase.deliver")+" · "+deliveryProject;if(importing)return tr("phase.import")+" · "+importingProject;if(builds.busy()||buildPreparing)return tr("phase."+(buildPreparing?"build_prepare":builds.phase()))+" · "+buildProject+" · "+activeBuildTask;return runs.busy()?tr("phase."+runs.phase())+" · "+runs.project():"";}
    public void runJava(){runJava(entryPoints.getOrDefault(project==null?"":project.id(),"Main"));}
    private void runJava(String mainClass){
        if(!canRunJava()){if(importedGradle)report(tr("use_gradle"));return;}String id=project.id();Map<String,Recovery> inputs=new HashMap<>();
        int total=0;for(var entry:documents.entrySet()){Key key=entry.getKey();var doc=entry.getValue();if(key.project.equals(id)&&SourceProjects.directJavaSource(key.file)){
            String text=doc.currentText();if((total+=text.length())>com.fixmer.genesis.technology.scripts.JavaSourceRunner.MAX_SOURCE_BYTES){report(tr("source_limit"));return;}inputs.put(key.file,new Recovery(doc.lastSavedText,text));}}
        if(runs.start(id,mainClass,inputs)){if(!preferences.scriptsKeepLogs)clearLogs();message=tr("run_started");appendLog("["+id+"] "+message);}
    }
    private LaunchMode launchMode(){return project==null?LaunchMode.BUILD:launchModes.getOrDefault(project.id(),gradleProject?(com.fixmer.mared.technology.server.setup.AutoDeploymentService156.available()?LaunchMode.AUTO156:LaunchMode.BUILD):LaunchMode.FILE);}
    public String launchLabel(){if(contentProject145())return MaredLang.get("mared.creation145.export");if(commandsMode133)return tr("launch_file");if(sharedActive132())return tr("build_gradle");if(launchMode()==LaunchMode.AUTO156)return tr("apply_server156");if(launchMode()==LaunchMode.REMOTE152)return tr("remote_build152");if(launchMode()==LaunchMode.DEDICATED)return tr("dedicated116.build_apply");return importedGradle&&launchMode()==LaunchMode.SERVER?tr("mod_build_apply"):tr(launchMode()==LaunchMode.BUILD?"launch_build":launchMode()==LaunchMode.SERVER?"launch_server":"launch_file");}
    public boolean canLaunchPrimary(){if(contentProject145())return !readOnly&&canExportJava();if(commandsMode133)return !readOnly&&active!=null&&commandRuns133.canStart();if(sharedActive132())return canBuildGradle();if(launchMode()==LaunchMode.AUTO156)return canBuildGradle()&&!com.fixmer.mared.technology.server.setup.AutoDeploymentService156.busy();if(launchMode()==LaunchMode.REMOTE152)return canBuildGradle()&&com.fixmer.mared.technology.server.setup.RemoteTestScreen152.available();if(launchMode()==LaunchMode.DEDICATED)return canApplyDedicated116();return launchMode()==LaunchMode.BUILD?canBuildGradle():launchMode()==LaunchMode.SERVER?canApplyModule():canRunSelectedFile();}
    public void launchPrimary(){if(contentProject145()){exportJava();return;}if(commandsMode133){launchCommands133();return;}if(sharedActive132()){sharedBuild132();return;}if(launchMode()==LaunchMode.AUTO156){buildAuto156();return;}if(launchMode()==LaunchMode.REMOTE152){buildRemote152();return;}if(launchMode()==LaunchMode.DEDICATED){applyDedicated116();return;}if(launchMode()==LaunchMode.BUILD)buildGradle();else if(launchMode()==LaunchMode.SERVER)applyServerModule();else runSelectedJavaFile();}
    private String javaLaunchFile(){return active!=null&&project!=null&&active.project.equals(project.id())?active.file:filesPane.selectedEntry;}
    public boolean canRunSelectedFile(){String path=javaLaunchFile();return canRunJava()&&SourceProjects.directJavaSource(path)&&files.contains(path);}
    public void runSelectedJavaFile(){
        if(!canRunSelectedFile()){report(tr("launch_file_note"));return;}String id=project.id(),path=javaLaunchFile();Key key=new Key(id,path);var doc=documents.get(key);
        String simple=RepositoryPath.leaf(path);simple=simple.substring(0,simple.length()-5);String guess=simple;
        if(doc!=null){var matcher=java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;").matcher(doc.currentText());if(matcher.find())guess=matcher.group(1)+"."+simple;}
        overlays.push(new NameDialogOverlay(tr("launch_entry")+" · "+path,value->{
            if(project==null||!project.id().equals(id)||!javaLaunchFile().equals(path)||!canRunSelectedFile()){report(tr("launch_changed"));return;}runJava(value);
        },accent(),false,value->com.fixmer.genesis.technology.scripts.JavaSourceRunner.validMainClass(value)?null:tr("bad_entry")).customInput().inputLabel("mared.dialog.class_label").acceptLabel("mared.dialog.run").maxLength(192).initialValue(guess));
    }
    public void launchMenu(int x,int y){if(contentProject145()){contentMenu145(x,y,false);return;}if(commandsMode133){runMenu(x,y);return;}
        var entries=new ArrayList<ContextMenuEntry>();
        if(canBuildGradle())entries.add(item("launch_build",true,this::buildGradle));
        if(canBuildGradle()&&com.fixmer.mared.technology.server.setup.AutoDeploymentService156.available())entries.add(item("apply_server156",true,this::buildAuto156));
        if(canBuildGradle()&&!sharedActive132()&&com.fixmer.mared.technology.server.setup.AutoDeploymentService156.available())entries.add(item("apply_server_once171",true,()->buildAuto156(true)));
        if(canBuildGradle()&&com.fixmer.mared.technology.server.setup.RemoteTestScreen152.available())entries.add(item("remote_server_only156",true,this::buildRemote152));
        if(canRunJava())entries.add(item("launch_file",true,this::runSelectedJavaFile));
        if(canApplyNativeMod())entries.add(item("mod_build_apply",true,this::applyServerModule));
        entries.add(item("advanced156",true,()->runMenu(x,y)));overlays.push(new ContextMenuOverlay(x,y,entries).category(accent()));
    }
    public boolean hasRunOutput(){return !lastRunOutput.isEmpty();}
    public void copyRunOutput(){if(hasRunOutput())net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(lastRunOutput);}
    public boolean canUseLocalJavaInputs(){return hasJavaProject()&&!importedGradle;}
    public boolean canImportGradle(){return !sharedActive132()&&workspaceLoaded&&!readOnly&&!disposed&&pending==null&&!javaRunning();}
    public boolean hasJavaProject(){if(commandsMode133)return false;return !disposed&&project!=null&&project.language()==Language.JAVA;}
    public void copyLibrariesPath(){if(importedGradle){report(tr("use_gradle_dependencies"));return;}if(canUseLocalJavaInputs()){
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(storage.root().resolve(project.id()).resolve("libraries").toString());message=tr("libraries_path_copied");
    }}
    public void copyResourcesPath(){if(importedGradle){report(tr("use_gradle_dependencies"));return;}if(canUseLocalJavaInputs()){
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(storage.root().resolve(project.id()).resolve("resources").toString());message=tr("resources_path_copied");
    }}
    public boolean canExportJava(){if(sharedActive132())return shared132.connected132();return project!=null&&pending==null;}
    public void exportJava(){
        if(sharedActive132()){com.fixmer.mared.technology.workspace.live122.WorkspaceSourceExport147.start(shared132,result->{message=result;appendLog(result);});return;}
        if(readOnly||!canExportJava())return;String id=project.id();var edits=new HashMap<String,Recovery>();
        for(var e:documents.entrySet())if(e.getKey().project.equals(id)){var doc=e.getValue();edits.put(e.getKey().file,new Recovery(doc.lastSavedText,doc.currentText()));}
        submit(()->storage.exportProject(id,edits),value->{String path=value.toString();message=tr("export_done")+": "+path;appendLog(message);net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(path);});
    }
    public boolean canConfigureBuildTools(){if(commandsMode133)return false;return !sharedActive132()&&workspaceLoaded&&!readOnly&&!disposed&&pending==null&&!javaRunning();}
    public boolean canConfigureGradle(){if(commandsMode133)return false;return !sharedActive132()&&!readOnly&&hasJavaProject()&&pending==null&&!javaRunning();}
    public boolean canBuildGradle(){if(commandsMode133)return false;if(sharedActive132())return shared132.context132().getOrDefault("direction147","mods").equals("mods")&&shared132.context132().getOrDefault("panels131","").contains("BUILD");return canConfigureGradle()&&gradleProject;}
    public void initializeGradle(){if(!canConfigureGradle())return;Project selected=project;submit(()->{storage.initializeGradle(selected.id());return null;},value->{message=tr("gradle_created");choose(selected);});}
    public void configureBuildTool(String key){
        if(!canConfigureBuildTools()||!Set.of("java_home","wrapper_jar").contains(key))return;
        submit(()->storage.buildTool(key),result->{
            if(disposed)return;
            overlays.push(new NameDialogOverlay(tr(key.equals("java_home")?"jdk_home":"wrapper_jar"),value->submit(()->{
                storage.setBuildTool(key,value);return storage.buildTool(key);
            },saved->{message=tr("tool_saved");appendLog(message+": "+saved,com.fixmer.mared.services.logging.LogSettings.Level.INFO,"tools");}),accent(),false,value->com.fixmer.genesis.technology.scripts.BuildTools.validAbsolutePath(value)?null:tr("absolute_path")).customInput().inputLabel("mared.dialog.path_label").acceptLabel("mared.dialog.save").maxLength(2048).initialValue((String)result));
        });
    }
    public void checkBuildTools(){
        if(!canConfigureBuildTools())return;
        String id=project==null?null:project.id();boolean gradle=gradleProject;submit(()->{
            var home=storage.javaHome();String wrapper;
            if(gradle&&id!=null)wrapper=storage.buildTools(id).wrapperJar().toString();
            else {String configured=storage.buildTool("wrapper_jar");try{wrapper=configured.isBlank()?tr("wrapper_optional"):com.fixmer.genesis.technology.scripts.BuildTools.wrapperJar(configured).toString();}catch(java.io.IOException bad){wrapper=bad.getMessage()+"\n"+tr("wrapper_optional");}}
            return "JDK: "+home+"\nGradle Wrapper: "+wrapper;
        },result->{message=tr("tools_checked");appendLog(message+"\n"+result,com.fixmer.mared.services.logging.LogSettings.Level.INFO,"tools");overlays.push(new ScriptInfoOverlay(tr("tools_checked"),message+"\n\n"+result+"\n\n"+tr("workflow.tools_note"),accent()));});
    }
    public void manageDependencies(){
        if(!canConfigureGradle()){report(tr("choose_project"));return;}String id=project.id();
        submit(()->storage.dependencyState(id),value->{if(project==null||!project.id().equals(id))return;try{dependencyMenu(id,(com.fixmer.genesis.technology.scripts.ProjectDependencies.State)value);}catch(java.io.IOException bad){report(bad.getMessage());}});
    }
    private void dependencyPrompt(String id,String title,String initial,java.util.function.Predicate<String> valid,Consumer<String> next){
        if(!canConfigureGradle()||!project.id().equals(id)){report(tr("launch_changed"));return;}
        overlays.push(new NameDialogOverlay(tr(title),value->{if(!canConfigureGradle()||!project.id().equals(id)){report(tr("launch_changed"));return;}next.accept(value);},accent(),false,value->valid.test(value)?null:tr("dependencies_bad_value")).customInput().maxLength(1024).initialValue(initial));
    }
    private void dependencyMenu(String id,com.fixmer.genesis.technology.scripts.ProjectDependencies.State state)throws java.io.IOException{
        var config=state.config();var rows=new ArrayList<ContextMenuEntry>();
        rows.add(ContextMenuEntry.of(tr("dependencies_report"),()->{var text=new StringBuilder(tr("dependencies_note")+"\n\n");for(int i=0;i<config.dependencies().size();i++){var d=config.dependencies().get(i);text.append(i+1).append(". ").append(d.mode()).append(" · ").append(d.coordinate());if(d.mode()==com.fixmer.genesis.technology.scripts.ProjectDependencies.Mode.MOD)text.append(" · ").append(d.modId()).append(' ').append(d.range()).append(' ').append(d.type()).append(' ').append(d.side());text.append('\n');}text.append("\n"+tr("dependencies_local")+": ").append(config.localLibraries()).append("\n");for(int i=0;i<config.repositories().size();i++)text.append(i+1).append(". ").append(config.repositories().get(i)).append('\n');overlays.push(new ScriptInfoOverlay(tr("dependencies"),text.toString(),accent()));}));
        rows.add(ContextMenuEntry.of(tr("dependencies_add_library"),()->dependencyMode(id,state,config)));
        rows.add(ContextMenuEntry.of(tr("dependencies_add_mod"),()->dependencyPrompt(id,"dependencies_mod_id","",value->value.matches("[a-z][a-z0-9_]{1,63}")&&!Set.of("minecraft","neoforge").contains(value),mod->dependencyPrompt(id,"dependencies_coordinate","",com.fixmer.genesis.technology.scripts.ProjectDependencies::validCoordinate,coordinate->dependencyPrompt(id,"dependencies_range","[1.0,)",com.fixmer.genesis.technology.scripts.ProjectDependencies::validRange,range->dependencyModType(id,state,config,mod,coordinate,range))))));
        rows.add(ContextMenuEntry.of(tr("dependencies_local")+": "+config.localLibraries(),()->applyDependencies(id,state,new com.fixmer.genesis.technology.scripts.ProjectDependencies.Config(config.dependencies(),config.repositories(),!config.localLibraries()))));
        rows.add(ContextMenuEntry.of(tr("libraries_path"),()->net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(storage.root().resolve(id).resolve("libraries").toString())));
        rows.add(ContextMenuEntry.of(tr("dependencies_add_repository"),()->dependencyPrompt(id,"dependencies_repository","https://",com.fixmer.genesis.technology.scripts.ProjectDependencies::validRepository,url->{try{applyDependencies(id,state,config.repository(url));}catch(IllegalArgumentException bad){report(bad.getMessage());}})));
        rows.add(config.dependencies().isEmpty()?ContextMenuEntry.disabled(tr("dependencies_remove")):ContextMenuEntry.of(tr("dependencies_remove"),()->dependencyPrompt(id,"dependencies_remove","1",value->{try{return Integer.parseInt(value)>=1&&Integer.parseInt(value)<=config.dependencies().size();}catch(NumberFormatException bad){return false;}},value->applyDependencies(id,state,config.remove(Integer.parseInt(value)-1)))));
        rows.add(config.repositories().isEmpty()?ContextMenuEntry.disabled(tr("dependencies_remove_repository")):ContextMenuEntry.of(tr("dependencies_remove_repository"),()->dependencyPrompt(id,"dependencies_remove_repository","1",value->{try{return Integer.parseInt(value)>=1&&Integer.parseInt(value)<=config.repositories().size();}catch(NumberFormatException bad){return false;}},value->{var repos=new ArrayList<>(config.repositories());repos.remove(Integer.parseInt(value)-1);applyDependencies(id,state,new com.fixmer.genesis.technology.scripts.ProjectDependencies.Config(config.dependencies(),repos,config.localLibraries()));})));
        overlays.push(new ContextMenuOverlay(16,54,rows).category(accent()));
    }
    private void dependencyMode(String id,com.fixmer.genesis.technology.scripts.ProjectDependencies.State state,com.fixmer.genesis.technology.scripts.ProjectDependencies.Config config){
        var rows=new ArrayList<ContextMenuEntry>();for(var mode:List.of(com.fixmer.genesis.technology.scripts.ProjectDependencies.Mode.LIBRARY,com.fixmer.genesis.technology.scripts.ProjectDependencies.Mode.COMPILE_ONLY,com.fixmer.genesis.technology.scripts.ProjectDependencies.Mode.EMBED))rows.add(ContextMenuEntry.of(tr("dependencies_mode_"+mode.name().toLowerCase(Locale.ROOT)),()->dependencyPrompt(id,"dependencies_coordinate","",value->com.fixmer.genesis.technology.scripts.ProjectDependencies.validCoordinate(value)&&(mode!=com.fixmer.genesis.technology.scripts.ProjectDependencies.Mode.EMBED||value.split(":").length==3),coordinate->{
            if(mode==com.fixmer.genesis.technology.scripts.ProjectDependencies.Mode.EMBED)dependencyPrompt(id,"dependencies_embed_range","["+coordinate.split(":")[2]+"]",com.fixmer.genesis.technology.scripts.ProjectDependencies::validRange,range->{try{applyDependencies(id,state,config.add(com.fixmer.genesis.technology.scripts.ProjectDependencies.Dependency.embedded(coordinate,range)));}catch(IllegalArgumentException bad){report(bad.getMessage());}});
            else try{applyDependencies(id,state,config.add(com.fixmer.genesis.technology.scripts.ProjectDependencies.Dependency.library(mode,coordinate)));}catch(IllegalArgumentException bad){report(bad.getMessage());}
        })));
        overlays.push(new ContextMenuOverlay(16,54,rows).category(accent()));
    }
    private void dependencyModType(String id,com.fixmer.genesis.technology.scripts.ProjectDependencies.State state,com.fixmer.genesis.technology.scripts.ProjectDependencies.Config config,String mod,String coordinate,String range){
        var rows=new ArrayList<ContextMenuEntry>();for(String type:List.of("required","optional"))for(String side:List.of("BOTH","CLIENT","SERVER"))rows.add(ContextMenuEntry.of(tr("dependencies_"+type)+" · "+tr("dependencies_side_"+side.toLowerCase(Locale.ROOT)),()->{try{applyDependencies(id,state,config.add(new com.fixmer.genesis.technology.scripts.ProjectDependencies.Dependency(com.fixmer.genesis.technology.scripts.ProjectDependencies.Mode.MOD,coordinate,mod,range,type,side)));}catch(IllegalArgumentException bad){report(bad.getMessage());}}));
        overlays.push(new ContextMenuOverlay(16,54,rows).category(accent()));
    }
    private void applyDependencies(String id,com.fixmer.genesis.technology.scripts.ProjectDependencies.State state,com.fixmer.genesis.technology.scripts.ProjectDependencies.Config config){
        if(!canConfigureGradle()||!project.id().equals(id)){report(tr("launch_changed"));return;}
        for(var e:documents.entrySet())if(e.getKey().project.equals(id)&&com.fixmer.genesis.technology.scripts.ProjectDependencies.FILES.contains(e.getKey().file)){
            e.getValue().recomputeDirty();if(e.getValue().dirty||!e.getValue().lastSavedText.equals(state.files().get(e.getKey().file))){report(tr("dependencies_save_first"));return;}}
        importCancellation.set(false);submit(()->{storage.setDependencies(id,state,config,importCancellation::get);return storage.dependencyState(id);},value->{
            var updated=(com.fixmer.genesis.technology.scripts.ProjectDependencies.State)value;invalidateBuild(id);taskReports.remove(id);taskCatalogs.remove(id);
            for(var e:documents.entrySet())if(e.getKey().project.equals(id)&&updated.files().containsKey(e.getKey().file)){var doc=e.getValue();doc.recomputeDirty();if(!doc.dirty){doc.setInitialText(updated.files().get(e.getKey().file));checkpointed.remove(e.getKey());}}
            if(active!=null&&active.project.equals(id))activate(active);message=tr("dependencies_saved");appendLog(message+" · "+id);if(project!=null&&project.id().equals(id))choose(project);
        });
    }
    public void configureBuildOption(String key){
        if(!canConfigureGradle()){report(tr("choose_project"));return;}
        if(!key.equals("build_timeout_minutes")&&!key.equals("build_max_workers"))return;
        String id=project.id();boolean timeout=key.equals("build_timeout_minutes");
        submit(()->storage.buildOptions(id),value->{
            var options=(com.fixmer.genesis.technology.scripts.GradleBuildRunner.Options)value;
            overlays.push(new NameDialogOverlay(tr(key),text->{
                if(!canConfigureGradle()||!project.id().equals(id)){report(tr("launch_changed"));return;}
                submit(()->{storage.setBuildOption(id,key,Integer.parseInt(text));return text;},saved->{message=tr("tool_saved");appendLog(message+" · "+id+" · "+tr(key)+": "+saved);});
            },accent(),false,text->{try{int n=Integer.parseInt(text);return n>=(timeout?1:0)&&n<=(timeout?1440:64)?null:tr("bad_build_limits");}catch(NumberFormatException bad){return tr("bad_build_limits");}}).customInput().acceptLabel("mared.dialog.save").maxLength(4).initialValue(Long.toString(timeout?options.timeoutMillis()/60_000:options.maxWorkers())));
        });
    }
    public void configureGradleTask(){if(!canConfigureGradle())return;String id=project.id();
        overlays.push(new NameDialogOverlay(tr("gradle_task"),value->submit(()->{storage.setGradleTask(id,value);return value;},result->{if(project!=null&&project.id().equals(id))gradleTask=(String)result;}),accent(),false,value->com.fixmer.genesis.technology.scripts.GradleBuildRunner.validTask(value)?null:tr("bad_task")).customInput().inputLabel("mared.dialog.task_label").acceptLabel("mared.dialog.save").maxLength(192).initialValue(gradleTask));
    }
    public void chooseGradleTask(){
        if(project==null||disposed)return;String id=project.id();var catalog=taskCatalogs.get(id);if(catalog==null)return;
        overlays.push(new GradleTasksOverlay(id,catalog,accent(),task->{
            if(!canConfigureGradle()||project==null||!project.id().equals(id)||taskCatalogs.get(id)!=catalog){report(tr("tasks_stale"));return;}
            submit(()->{storage.setGradleTask(id,task);return task;},result->{if(project!=null&&project.id().equals(id))gradleTask=(String)result;message=tr("tasks_selected")+": "+result;});
        },!readOnly));
    }
    private void rememberTaskReport(String id,String text){taskReports.remove(id);taskReports.put(id,text);while(taskReports.size()>16){String oldest=taskReports.keySet().iterator().next();taskReports.remove(oldest);taskCatalogs.remove(oldest);}}
    private void taskPreparationFailed(String error){abortBuildPreparation(com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.INPUT_ERROR,error);}
    private void invalidateBuild(String id){buildReports.remove(id);buildDiagnostics.remove(id);}
    private void abortBuildPreparation(com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status status,String detail){
        remoteBuild152="";if(nativeUiLocked)unlockDevelopmentEditing();nativeBuild=false;nativeApplyQueued="";dedicatedFolder116=null;
        if(!buildPreparing)return;buildPreparing=false;
        if(listingTasks)rememberTaskReport(buildProject,status+"\n"+detail);
        else rememberBuild(new BuildReport(buildProject,activeBuildTask,storage.root().resolve(buildProject),new com.fixmer.genesis.technology.scripts.GradleBuildRunner.Result(status,-1,detail,false,List.of())));
        listingTasks=false;lastRunOutput=status+"\n"+detail;
    }
    public void showTaskReport(){if(project!=null&&taskReports.containsKey(project.id()))overlays.push(new ScriptInfoOverlay(tr("tasks_report")+" · "+project.id(),taskReports.get(project.id()),accent()));}
    private void remoteLaunch154(){
        if(project==null||disposed)return;
        if(sharedActive132()){if(canBuildGradle())sharedBuild132();else remoteNotice154(tr("remote_build_permission154"));return;}
        if(!gradleProject){remoteNotice154(tr("remote_gradle154"));return;}
        if(!canBuildGradle()){remoteNotice154(tr("operation_busy"));return;}
        String reason=com.fixmer.mared.technology.server.setup.RemoteTestScreen152.unavailable154();
        if(!reason.isEmpty()){remoteNotice154(MaredLang.get(reason));return;}
        launchModes.put(project.id(),LaunchMode.REMOTE152);message=tr("remote_selected154");
    }
    private void remoteNotice154(String text){message=text;overlays.push(new ScriptInfoOverlay(tr("remote_build152"),text,accent()));}
    private boolean automatic156,once171;private String autoFolder156="";
    private void buildAuto156(){buildAuto156(false);}
    private void buildAuto156(boolean once){
        if(sharedActive132()){sharedBuild132();return;}
        if(!canBuildGradle()||!com.fixmer.mared.technology.server.setup.AutoDeploymentService156.available())return;
        if(documents.entrySet().stream().anyMatch(entry->entry.getKey().project.equals(project.id())&&entry.getValue().dirty)){report(tr("save_before_build"));return;}
        automatic156=true;once171=once;autoFolder156=com.fixmer.mared.technology.server.setup.InPlaceServerService104.folder();remoteBuild152=project.id();prepareGradle(false);
    }
    private String remoteBuild152="",remoteReady152="";
    private void buildRemote152(){
        if(!canBuildGradle()||sharedActive132()||!com.fixmer.mared.technology.server.setup.RemoteTestScreen152.available())return;automatic156=false;
        if(documents.entrySet().stream().anyMatch(entry->entry.getKey().project.equals(project.id())&&entry.getValue().dirty)){report(tr("save_before_build"));return;}
        remoteBuild152=project.id();prepareGradle(false);
    }
    private void openRemoteReady152(){
        String id=remoteReady152;remoteReady152="";var build=buildReports.get(id);
        if(project==null||!project.id().equals(id)||build==null||!automatic156&&!com.fixmer.mared.technology.server.setup.RemoteTestScreen152.available())return;
        var choices=build.result().inventory().artifacts().stream().filter(a->java.util.Set.of("mod","mod_embedded").contains(a.kind())).toList();
        if(automatic156&&!autoFolder156.equals(com.fixmer.mared.technology.server.setup.InPlaceServerService104.folder())){report(tr("launch_changed"));return;}if(choices.size()==1)remoteArtifact156(id,choices.get(0),automatic156);else if(!choices.isEmpty()){if(automatic156){var entries=new ArrayList<ContextMenuEntry>();for(var artifact:choices)entries.add(ContextMenuEntry.of(artifact.path(),()->remoteArtifact156(id,artifact,true)));overlays.push(new ContextMenuOverlay(editor.getX(),editor.getY(),entries).category(accent()));}else artifactMenu(editor.getX(),editor.getY());}else report(tr("artifact_no_snapshots"));
    }
    public void buildGradle(){if(commandsMode133)return;if(sharedActive132()){sharedBuild132();return;}prepareGradle(false);}
    public void discoverGradleTasks(){prepareGradle(true);}
    private void prepareGradle(boolean listing){prepareGradle(listing,false);}
    private void prepareGradle(boolean listing,boolean nativeApply){
        if(!canBuildGradle()){if(nativeApply)abortBuildPreparation(com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.INPUT_ERROR,tr("operation_busy"));return;}String id=project.id();var baseline=new HashMap<String,String>();
        for(var e:documents.entrySet())if(e.getKey().project.equals(id)&&e.getValue().dirty){if(nativeApply)abortBuildPreparation(com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.INPUT_ERROR,tr("save_before_build"));report(tr("save_before_build"));return;}
        for(var e:documents.entrySet())if(e.getKey().project.equals(id))baseline.put(e.getKey().file,e.getValue().lastSavedText);
        String task=listing?"tasks --all":nativeApply?"build":gradleTask;if(listing)taskCatalogs.remove(id);else invalidateBuild(id);listingTasks=listing;activeBuildTask=task;buildProject=id;buildPreparing=true;buildPreparationCancelled=false;
            submit(()->{synchronized(storage){storage.validateDependencies(id);for(var e:baseline.entrySet())if(!storage.read(id,e.getKey()).equals(e.getValue()))throw new java.io.IOException("File changed outside Scripts: "+e.getKey());return new PreparedBuild(storage.workingDirectory(id),storage.buildTools(id),storage.buildOptions(id));}},result->{
            if(buildPreparationCancelled||disposed){abortBuildPreparation(com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.CANCELLED,tr("build_result.CANCELLED"));message=tr("build_result.CANCELLED");return;}
            for(var e:documents.entrySet())if(e.getKey().project.equals(id)&&e.getValue().dirty){taskPreparationFailed(tr("save_before_build"));report(tr("save_before_build"));return;}
            var input=(PreparedBuild)result;activeBuildDirectory=input.directory();buildPreparing=false;if(listing?builds.startTaskListing(input.directory(),new com.fixmer.genesis.technology.scripts.GradleBuildRunner.Settings(input.tools().javaHome(),input.tools().wrapperJar()),input.options()):builds.start(input.directory(),new com.fixmer.genesis.technology.scripts.GradleBuildRunner.Settings(input.tools().javaHome(),input.tools().wrapperJar()),task,input.options())){buildProject=id;buildLine.setLength(0);if(!preferences.scriptsKeepLogs)clearLogs();appendLog(tr(listing?"tasks_started":"build_started")+" · "+id+" · "+task);appendLog(tr("using_wrapper")+": "+input.tools().wrapperJar());}else{buildPreparing=true;taskPreparationFailed(tr("operation_busy"));report(tr("operation_busy"));}
        });
    }
    // Reports are session-local, scoped to the project which actually ran the build.
    private void rememberBuild(BuildReport report){
        buildReports.remove(report.project());buildReports.put(report.project(),report);
        buildDiagnostics.put(report.project(),com.fixmer.genesis.technology.scripts.BuildDiagnostics.parse(report.result().output(),report.directory()));
        while(buildReports.size()>16){String oldest=buildReports.keySet().iterator().next();buildReports.remove(oldest);buildDiagnostics.remove(oldest);}
    }
    public boolean hasBuildReport(){return !disposed&&project!=null&&buildReports.containsKey(project.id());}
    public void showWorkflow(){if(!disposed){var body=new StringBuilder();for(int i=0;i<9;i++)body.append(tr("workflow."+i)).append("\n\n");overlays.push(new ScriptInfoOverlay(tr("workflow"),body.toString(),accent()));}}
    private String reportText(BuildReport report){
        var result=report.result();var text=new StringBuilder(tr("project_root")+": "+report.project()+"\n"+tr("gradle_task")+": "+report.task()+"\n"+tr("build_result."+result.status().name())+" · "+result.exitCode()+"\n"+tr("project_path")+": "+report.directory()+"\n\n");
        if(result.artifacts().isEmpty())text.append(tr("build_no_artifacts")).append("\n");
        else for(var artifact:result.artifacts())text.append(tr("artifact")).append(": ").append(artifact).append("\n");
        if(result.inventory().limited())text.append(tr("artifact_limited")).append("\n");
        text.append("\n").append(tr("build_report_note")).append("\n\n").append(result.output());return text.toString();
    }
    public void buildReportMenu(int x,int y){
        if(!hasBuildReport())return;BuildReport report=buildReports.get(project.id());var entries=new ArrayList<ContextMenuEntry>();
        entries.add(ContextMenuEntry.disabled(report.project()+" · "+report.task()+" · "+tr("build_result."+report.result().status().name())));
        entries.add(ContextMenuEntry.of(tr("build_report_open"),()->overlays.push(new ScriptInfoOverlay(tr("build_report")+" · "+report.project(),reportText(report),accent()))));
        entries.add(ContextMenuEntry.of(tr("build_directory_copy"),()->copyBuildPath(report.directory())));
        entries.add(ContextMenuEntry.divider());
        if(report.result().artifacts().isEmpty())entries.add(ContextMenuEntry.disabled(tr("build_no_artifacts")));
        for(var artifact:report.result().artifacts())entries.add(ContextMenuEntry.of(tr("build_artifact_copy")+": "+(artifact.startsWith(report.directory())?report.directory().relativize(artifact):artifact),()->copyBuildPath(artifact)));
        entries.add(ContextMenuEntry.of(tr("diagnostics"),()->diagnosticsMenu(x,y)));
        entries.add(ContextMenuEntry.of(tr("artifact_deliver_menu"),()->artifactMenu(x,y)));
        overlays.push(new ContextMenuOverlay(x,y,entries).category(accent()));
    }
    public void artifactMenu(int x,int y){
        if(!hasBuildReport())return;BuildReport report=buildReports.get(project.id());var inventory=report.result().inventory();var entries=new ArrayList<ContextMenuEntry>();
        entries.add(ContextMenuEntry.disabled(report.project()+" · "+report.task()));entries.add(ContextMenuEntry.of(tr("artifact_help"),()->overlays.push(new ScriptInfoOverlay(tr("artifact_help"),tr("artifact_notes"),accent()))));entries.add(ContextMenuEntry.divider());
        if(inventory.artifacts().isEmpty())entries.add(ContextMenuEntry.disabled(tr("artifact_no_snapshots")));
        for(var artifact:inventory.artifacts()){
            String label=tr("artifact_kind."+artifact.kind())+" · "+artifact.path()+" · "+artifact.size()+" B";
            entries.add(new ContextMenuEntry(label,()->deliverArtifact(report.project(),artifact),!readOnly&&pending==null&&!javaRunning(),false));
            if(artifact.kind().equals("mod")||artifact.kind().equals("mod_embedded"))entries.add(item("mod_apply_selected",NativeWorldTarget.capture()!=null&&!readOnly&&pending==null&&!javaRunning(),()->stageNativeArtifact(report.project(),artifact)));
            if(artifact.kind().equals("mod")||artifact.kind().equals("mod_embedded"))entries.add(item("server_apply66",!readOnly&&pending==null&&!javaRunning(),()->serverArtifact66(report.project(),artifact)));
            if(artifact.kind().equals("mod")||artifact.kind().equals("mod_embedded"))entries.add(item("server_publish84",!readOnly&&pending==null&&!javaRunning(),()->publishArtifact84(report.project(),artifact)));
            if(artifact.kind().equals("mod")||artifact.kind().equals("mod_embedded"))entries.add(item("remote_test152",com.fixmer.mared.technology.server.setup.RemoteTestScreen152.available()&&!readOnly&&pending==null&&!javaRunning(),()->remoteArtifact152(report.project(),artifact)));
        }
        if(inventory.limited())entries.add(ContextMenuEntry.disabled(tr("artifact_limited")));
        overlays.push(new ContextMenuOverlay(x,y,entries).category(accent()));
    }
    public void deliverArtifact(String projectId,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact artifact){
        if(readOnly||disposed||pending!=null||javaRunning())return;BuildReport report=buildReports.get(projectId);
        if(report==null||report.result().status()!=com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS||!report.result().inventory().artifacts().contains(artifact)){report(tr("artifact_result_changed"));return;}
        delivering=true;deliveryProject=projectId;importCancellation.set(false);
        submit(()->storage.deliverArtifact(projectId,artifact,importCancellation::get),value->{delivering=false;String path=value.toString();message=tr("artifact_delivered")+": "+path;appendLog(message);net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(path);});
    }
    private void remoteArtifact152(String projectId,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact artifact){remoteArtifact156(projectId,artifact,false);}
    private void remoteArtifact156(String projectId,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact artifact,boolean auto){
        if(!auto&&!com.fixmer.mared.technology.server.setup.RemoteTestScreen152.available()||readOnly||disposed||pending!=null||javaRunning())return;
        BuildReport build=buildReports.get(projectId);if(build==null||build.result().status()!=com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS||!build.result().inventory().artifacts().contains(artifact)){report(tr("artifact_result_changed"));return;}
        if(documents.entrySet().stream().anyMatch(entry->entry.getKey().project.equals(projectId)&&entry.getValue().dirty)){report(tr("publish_save84"));return;}
        delivering=true;deliveryProject=projectId;importCancellation.set(false);boolean once=auto&&once171;String targetFolder156=com.fixmer.mared.technology.server.setup.InPlaceServerService104.folder();
        submit(()->com.fixmer.genesis.technology.scripts.BuildSnapshot84.create(storage.workingDirectory(projectId),projectId,build.task(),build.result(),artifact,storage.root().resolve(".artifacts"),importCancellation::get),value->{delivering=false;if(auto&&!targetFolder156.equals(com.fixmer.mared.technology.server.setup.InPlaceServerService104.folder())){report(tr("launch_changed"));return;}var mc=net.minecraft.client.Minecraft.getInstance();var parent=mc.screen;if(parent instanceof com.fixmer.mared.gui2.shell.MaredShellScreen shell)shell.suspendForSettings();if(auto)mc.setScreen(new com.fixmer.mared.technology.server.setup.AutoDeploymentScreen156(parent,(com.fixmer.genesis.technology.scripts.BuildSnapshot84.Snapshot)value,once));else mc.setScreen(new com.fixmer.mared.technology.server.setup.RemoteTestScreen152(parent,(com.fixmer.genesis.technology.scripts.BuildSnapshot84.Snapshot)value));});
    }
    private void publishArtifact84(String projectId,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact artifact){
        if(readOnly||disposed||pending!=null||javaRunning())return;BuildReport build=buildReports.get(projectId);
        if(build==null||build.result().status()!=com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS||!build.result().inventory().artifacts().contains(artifact)){report(tr("artifact_result_changed"));return;}
        if(documents.entrySet().stream().anyMatch(e->e.getKey().project.equals(projectId)&&e.getValue().dirty)){report(tr("publish_save84"));return;}
        delivering=true;deliveryProject=projectId;importCancellation.set(false);
        submit(()->com.fixmer.genesis.technology.scripts.BuildSnapshot84.create(storage.workingDirectory(projectId),projectId,build.task(),build.result(),artifact,storage.root().resolve(".artifacts"),importCancellation::get),value->{delivering=false;
            net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new com.fixmer.mared.technology.server.setup.AutoDeploymentScreen156(mc.screen,(com.fixmer.genesis.technology.scripts.BuildSnapshot84.Snapshot)value));
        });
    }
    private void serverArtifact66(String projectId,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact artifact){serverArtifact66(projectId,artifact,com.fixmer.mared.technology.server.setup.InPlaceServerService104.folder());}
    private void serverArtifact66(String projectId,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact artifact,String folder){
        if(readOnly||disposed||pending!=null||javaRunning())return;BuildReport build=buildReports.get(projectId);
        if(build==null||build.result().status()!=com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS||!build.result().inventory().artifacts().contains(artifact)){report(tr("artifact_result_changed"));return;}
        if(documents.entrySet().stream().anyMatch(e->e.getKey().project.equals(projectId)&&e.getValue().dirty)){report(tr("publish_save84"));return;}
        delivering=true;deliveryProject=projectId;importCancellation.set(false);
        submit(()->com.fixmer.genesis.technology.scripts.BuildSnapshot84.create(storage.workingDirectory(projectId),projectId,build.task(),build.result(),artifact,storage.root().resolve(".artifacts"),importCancellation::get),value->{delivering=false;
            net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new com.fixmer.mared.technology.server.setup.ServerUpdateScreen115(mc.screen,(com.fixmer.genesis.technology.scripts.BuildSnapshot84.Snapshot)value,folder));
        });
    }
    private BuildReport navigatedBuild;
    private int diagnosticIndex=-1;
    public void nextDiagnostic(boolean previous){
        if(!hasBuildReport()||pending!=null)return;BuildReport current=buildReports.get(project.id());
        var parsed=buildDiagnostics.get(project.id());if(parsed==null)return;
        var located=parsed.diagnostics().stream().filter(d->!d.file().isEmpty()&&d.line()>0).toList();
        if(located.isEmpty()){report(tr("diagnostic_none"));return;}
        if(navigatedBuild!=current){navigatedBuild=current;diagnosticIndex=previous?0:-1;}
        diagnosticIndex=Math.floorMod(diagnosticIndex+(previous?-1:1),located.size());
        openDiagnostic(current.project(),located.get(diagnosticIndex));
    }
    public void diagnosticsMenu(int x,int y){
        if(!hasBuildReport())return;BuildReport report=buildReports.get(project.id());var parsed=buildDiagnostics.get(report.project());var entries=new ArrayList<ContextMenuEntry>();
        long errors=parsed.diagnostics().stream().filter(d->d.severity()==com.fixmer.genesis.technology.scripts.BuildDiagnostics.Severity.ERROR).count();
        entries.add(ContextMenuEntry.disabled(report.project()+" · "+tr("diagnostic_errors")+": "+errors+" · "+tr("diagnostic_warnings")+": "+(parsed.diagnostics().size()-errors)));
        entries.add(ContextMenuEntry.of(tr("build_report_open"),()->overlays.push(new ScriptInfoOverlay(tr("build_report")+" · "+report.project(),reportText(report),accent()))));entries.add(ContextMenuEntry.divider());
        if(parsed.diagnostics().isEmpty())entries.add(ContextMenuEntry.disabled(tr("diagnostic_none")));
        for(var diagnostic:parsed.diagnostics()){
            String label=tr(diagnostic.severity()==com.fixmer.genesis.technology.scripts.BuildDiagnostics.Severity.ERROR?"diagnostic_error":"diagnostic_warning")+" · "+(diagnostic.file().isEmpty()?tr("diagnostic_general"):diagnostic.file()+":"+diagnostic.line())+" — "+diagnostic.message().replace('\t',' ');
            String preview=label.length()>384?label.substring(0,384)+"…":label;
            entries.add(ContextMenuEntry.of(preview,()->{if(diagnostic.file().isEmpty())overlays.push(new ScriptInfoOverlay(tr("diagnostics"),diagnostic.raw()+"\n\n"+tr("diagnostic_no_position"),accent()));else openDiagnostic(report.project(),diagnostic);}));
        }
        if(parsed.limited())entries.add(ContextMenuEntry.disabled(tr("diagnostic_limited")));
        entries.add(ContextMenuEntry.of(tr("diagnostic_help"),()->overlays.push(new ScriptInfoOverlay(tr("diagnostic_help"),tr("diagnostic_notes"),accent()))));
        overlays.push(new ContextMenuOverlay(x,y,entries).category(accent()));
    }
    public void openDiagnostic(String projectId,com.fixmer.genesis.technology.scripts.BuildDiagnostics.Diagnostic diagnostic){
        if(disposed||pending!=null){if(!disposed)report(tr("operation_busy"));return;}
        if(diagnostic.file().isEmpty()){report(tr("diagnostic_no_position"));return;}
        var opened=new HashSet<>(documents.keySet());int openCount=documents.size();
        submit(()->{ProjectFiles snapshot=projectFiles(storage.project(projectId));String canonical=snapshot.files.stream().filter(p->p.equals(diagnostic.file())).findFirst().orElseGet(()->snapshot.files.stream().filter(p->p.equalsIgnoreCase(diagnostic.file())).findFirst().orElse(null));
            if(canonical==null)throw new java.io.IOException(tr("diagnostic_missing")+": "+diagnostic.file());Key key=new Key(projectId,canonical);
            if(!opened.contains(key)&&openCount>=16)throw new java.io.IOException(tr("open_limit"));
            return new DiagnosticTarget(snapshot,key,opened.contains(key)?null:loadDocument(snapshot.project,canonical));
        },value->{DiagnosticTarget target=(DiagnosticTarget)value;String recoveryNotice=target.loaded==null?"":installDocument(target.key,target.loaded);applyProjectFiles(target.files);activate(target.key);var doc=documents.get(target.key);
            doc.document.clearSelection();doc.document.setCursor(diagnostic.line()-1,Math.max(0,diagnostic.column()-1));doc.view.resetBlink();doc.view.ensureCursorVisible(editor.getWidth(),editor.getHeight(),doc.document,net.minecraft.client.Minecraft.getInstance().font);
            message=tr("diagnostic_opened")+": "+target.key.file+":"+diagnostic.line();
            if(target.loaded!=null&&(target.loaded.recovery!=null||!target.loaded.recoveryError.isEmpty())){message+=" · "+recoveryNotice;appendLog(message);}
        });
    }
    private void copyBuildPath(java.nio.file.Path path){net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(path.toString());message=tr("build_path_copied");}
    public void saveAll(){if(sharedActive132()){shared132.flush132();return;}if(readOnly)return;
        if(disposed||pending!=null||project==null)return;String id=project.id();if((builds.busy()||buildPreparing)&&id.equals(buildProject)){report(tr("save_during_build"));return;}var captured=new ArrayList<Snapshot>();
        for(var e:documents.entrySet())if(e.getKey().project.equals(id)&&e.getValue().dirty)captured.add(new Snapshot(e.getKey(),e.getValue().lastSavedText,e.getValue().currentText()));
        if(captured.isEmpty()){message=tr("saved");return;}
        submit(()->{var saved=new ArrayList<Snapshot>();String error="";for(Snapshot snap:captured){try{storage.save(snap.key.project,snap.key.file,snap.baseline,snap.text);workerWrites.put(snap.key,new Recovery(snap.baseline,snap.text));saved.add(snap);}catch(Exception bad){error=bad.getMessage()==null?bad.toString():bad.getMessage();break;}}return new SaveBatch(saved,error);},result->{
            SaveBatch batch=(SaveBatch)result;if(!batch.saved.isEmpty())invalidateBuild(id);for(Snapshot snap:batch.saved){var doc=documents.get(snap.key);if(doc!=null){doc.lastSavedText=snap.text;doc.recomputeDirty();checkpointed.put(snap.key,null);}}
            changedAt=0;retryAfter=0;checkpointRequested=true;if(!batch.error.isEmpty()){nativeApplyQueued="";dedicatedFolder116=null;unlockDevelopmentEditing();report(batch.error);}else message=tr("saved_all");
        });
    }
    public boolean canStopPrimary(){if(commandsMode133)return commandRuns133.canStop();return nativeUiLocked||!readOnly&&(javaRunning()||launchMode()==LaunchMode.SERVER&&canManageModule(false));}
    public void stopPrimary(){if(commandsMode133){commandRuns133.stop();return;}if(nativeUiLocked||javaRunning())stopJava();else if(launchMode()==LaunchMode.SERVER)manageServerModule(false);}
    public void stopJava(){if(commandsMode133){commandRuns133.stop();return;}autoRestartWaiting=false;com.fixmer.mared.technology.minecraft.NativeModWorkflow.cancel(stagedDeploymentToken);stagedDeploymentToken="";automaticRestartProject="";if(nativeUiLocked)unlockDevelopmentEditing();nativeBuild=false;nativeApplyQueued="";dedicatedFolder116=null;applies.cancel();runs.stop();builds.stop();buildPreparationCancelled=true;importCancellation.set(true);message=tr("stopping");}
    public void configureEntryPoint(){
        if(!canRunJava())return;String id=project.id();
        overlays.push(new NameDialogOverlay(tr("entry_point"),value->submit(()->{storage.setJavaMainClass(id,value);return value;},result->{entryPoints.put(id,(String)result);message=tr("entry_saved")+": "+result;}),accent(),false,value->com.fixmer.genesis.technology.scripts.JavaSourceRunner.validMainClass(value)?null:tr("bad_entry")).customInput().inputLabel("mared.dialog.class_label").acceptLabel("mared.dialog.save").maxLength(192).initialValue(entryPoints.getOrDefault(id,"Main")));
    }
    private void appendLog(String text){
        String category=applies.busy()?"server":builds.busy()||buildPreparing?"gradle":runs.busy()?"java":"editor";
        appendLog(text,com.fixmer.mared.services.logging.LogSettings.Level.INFO,category);
    }
    private void appendLog(String text,com.fixmer.mared.services.logging.LogSettings.Level level,String category){
        if(text==null)return;long now=System.currentTimeMillis();String time=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        for(String line:text.split("\\R",-1)){
            logLines.addLast(line);scriptLogEntries.addLast(new com.fixmer.mared.services.logging.LogEntry(time,now,level,category,line,project==null?"Scripts":project.id()));
        }
        trimLogs();logger.accept("[Scripts] "+text);
    }
    private void report(String text){message=text==null?"Error":text;appendLog(message,com.fixmer.mared.services.logging.LogSettings.Level.ERROR,"editor");}
    private void submit(Callable<?> job,Consumer<Object> done){
        if(disposed||pending!=null)return;
        try{pending=worker.submit(job);completed=done;}catch(RuntimeException e){if(restartCheckpointInFlight){restartCheckpointInFlight=false;setReadOnly(false);}taskPreparationFailed(e.toString());importing=false;delivering=false;buildPreparing=false;report(e.getMessage());}
    }
    private boolean autoRestartWaiting, restartCheckpointInFlight;
    private String automaticRestartProject="", stagedDeploymentToken="";
    private String nativeApplyQueued="";
    private boolean nativeBuild, installingNativeMod, nativeUiLocked;
    private void unlockDevelopmentEditing(){nativeUiLocked=false;setReadOnly(false);}
    private NativeWorldTarget nativeTarget;
    private String dedicatedFolder116;
    private void selectDedicated116(String id){
        launchModes.put(id,LaunchMode.DEDICATED);watchApply=false;sourceWatcher.reset();
        submit(()->{storage.setWatchApplyEnabled(id,false);return true;},value->{message=tr("dedicated116.note");});
        if(BackgroundAutoApply.selected(storage.root(),id))BackgroundAutoApply.select(storage,id,false).whenComplete((ok,error)->net.minecraft.client.Minecraft.getInstance().execute(()->{if(!disposed&&(error!=null||!Boolean.TRUE.equals(ok)))report(tr("background.control_failed"));}));
    }
    public boolean canApplyDedicated116(){if(commandsMode133)return false;return importedGradle&&canBuildGradle()&&!BackgroundAutoApply.selected(storage.root(),project.id());}
    public void applyDedicated116(){
        if(!canApplyDedicated116())return;dedicatedFolder116=com.fixmer.mared.technology.server.setup.InPlaceServerService104.folder();nativeTarget=null;nativeApplyQueued=project.id();appendLog("[Genesis integration116] target=DEDICATED; project="+project.id());saveAll();nativeUiLocked=true;setReadOnly(true);message=tr("mod_saving_building");
    }
    private final com.fixmer.genesis.technology.scripts.ProjectChangeWatcher sourceWatcher=new com.fixmer.genesis.technology.scripts.ProjectChangeWatcher();
    private boolean watchApply;
    private java.nio.file.Path watchedDirectory;
    private void toggleWatchApply(){if(sharedActive132())return;
        if(project==null||!importedGradle||readOnly||pending!=null||javaRunning())return;
        String id=project.id();boolean enabled=!watchApply;if(enabled&&launchMode()==LaunchMode.DEDICATED){report(tr("dedicated116.auto_scope"));return;}
        submit(()->{storage.setWatchApplyEnabled(id,enabled);return enabled;},value->{if(project!=null&&project.id().equals(id)){watchApply=(boolean)value;sourceWatcher.reset();message=tr(watchApply?"watch_enabled":"watch_disabled");appendLog(message);}});
    }
    private void tickWatchApply(){
        if(launchMode()==LaunchMode.DEDICATED||!watchApply||!canApplyNativeMod()||watchedDirectory==null||documents.entrySet().stream().anyMatch(e->e.getKey().project.equals(project.id())&&e.getValue().dirty))return;
        try{
            if(!sourceWatcher.poll(watchedDirectory,System.nanoTime()))return;
            String id=project.id();var selected=project;
            var clean=documents.keySet().stream().filter(k->k.project.equals(id)).toList();
            submit(()->{var texts=new HashMap<Key,String>();for(Key key:clean)texts.put(key,storage.read(id,key.file));return texts;},value->{
                if(project==null||!project.id().equals(id)||!canApplyNativeMod()||documents.entrySet().stream().anyMatch(e->e.getKey().project.equals(id)&&e.getValue().dirty))return;
                @SuppressWarnings("unchecked") var texts=(Map<Key,String>)value;
                for(var entry:texts.entrySet()){
                    var doc=documents.get(entry.getKey());if(doc==null||doc.dirty||doc.lastSavedText.equals(entry.getValue()))continue;
                    var replacement=new DocumentSession();replacement.storageId=entry.getKey().file;replacement.setInitialText(entry.getValue());documents.put(entry.getKey(),replacement);checkpointed.put(entry.getKey(),null);
                }
                if(active!=null&&active.project.equals(id))activate(active);
                appendLog(tr("watch_changed"));applyMinecraftMod();
            });
        }catch(java.io.IOException error){watchApply=false;sourceWatcher.reset();report(tr("watch_failed")+": "+error.getMessage());}
    }
    private void toggleBackgroundApply(){if(sharedActive132())return;
        if(project==null||!importedGradle||pending!=null||javaRunning())return;
        String id=project.id();boolean on=!BackgroundAutoApply.selected(storage.root(),id);if(on&&launchMode()==LaunchMode.DEDICATED){report(tr("dedicated116.auto_scope"));return;}
        BackgroundAutoApply.select(storage,id,on).whenComplete((ok,error)->net.minecraft.client.Minecraft.getInstance().execute(()->{
            if(!disposed){message=error==null&&Boolean.TRUE.equals(ok)?tr(on?"background.enabled":"background.disabled"):tr("background.control_failed");appendLog(message);}
        }));
    }
    private void backgroundReport(){if(!disposed)overlays.push(new ScriptInfoOverlay(tr("background.report"),BackgroundAutoApply.report(),accent()));}
    public void restartClientFromMenu(){
        if(disposed||readOnly||pending!=null||javaRunning()||NativeWorldTarget.capture()==null)return;
        automaticRestartProject=project==null?"manual":project.id();autoRestartWaiting=true;setReadOnly(true);
    }

    public boolean canApplyNativeMod(){if(commandsMode133)return false;return importedGradle&&canBuildGradle()&&NativeWorldTarget.capture()!=null;}
    public void applyMinecraftMod(){
        if(!canApplyNativeMod()){report(tr("mod_requires_world"));return;}
        dedicatedFolder116=null;nativeTarget=NativeWorldTarget.capture();nativeApplyQueued=project.id();saveAll();nativeUiLocked=true;setReadOnly(true);
        message=tr("mod_saving_building");
    }
    private void stageNativeArtifact(String id,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact artifact){
        reviewNativeSet99(new com.fixmer.mared.technology.minecraft.NativeModWorkflow.Request99(Map.of(id,artifact),Set.of()));
    }
    /** Explicit whole-set command: build results are not silently rebuilt or substituted after review. */
    public void reviewBuiltMods99(){
        var updates=new LinkedHashMap<String,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact>();
        for(var e:buildReports.entrySet())if(e.getValue().result().status()==com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS){
            var jars=e.getValue().result().inventory().artifacts().stream().filter(a->a.kind().equals("mod")||a.kind().equals("mod_embedded")).toList();
            if(jars.size()>1){report(tr("mod_choose_artifact")+" · "+e.getKey());return;}
            if(jars.size()==1)updates.put(e.getKey(),jars.get(0));
        }
        if(updates.isEmpty()){report(tr("set99.build_first"));return;}
        reviewNativeSet99(new com.fixmer.mared.technology.minecraft.NativeModWorkflow.Request99(updates,Set.of()));
    }
    public void removeManagedMod99(){
        if(project==null)return;reviewNativeSet99(new com.fixmer.mared.technology.minecraft.NativeModWorkflow.Request99(Map.of(),Set.of(project.id())));
    }
    private boolean currentSet99(com.fixmer.mared.technology.minecraft.NativeModWorkflow.Request99 request){
        if(documents.entrySet().stream().anyMatch(e->(request.updates().containsKey(e.getKey().project)||request.removals().contains(e.getKey().project))&&e.getValue().dirty))return false;
        for(var e:request.updates().entrySet()){
            var build=buildReports.get(e.getKey());if(build==null||build.result().status()!=com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS||!build.result().inventory().artifacts().contains(e.getValue()))return false;
        }
        return true;
    }
    private void reviewNativeSet99(com.fixmer.mared.technology.minecraft.NativeModWorkflow.Request99 request){
        if(disposed||readOnly||pending!=null||javaRunning()||NativeWorldTarget.capture()==null)return;
        if(!currentSet99(request)){report(tr("review98.changed"));return;}
        NativeWorldTarget target=NativeWorldTarget.capture();var versions=com.fixmer.mared.technology.minecraft.NativeModWorkflow.loadedVersions();
        installingNativeMod=true;nativeUiLocked=true;setReadOnly(true);importCancellation.set(false);message=tr("set99.checking");
        try{java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{return com.fixmer.mared.technology.minecraft.NativeModWorkflow.reviewSet99(storage,request,versions,importCancellation::get);}
            catch(java.io.IOException error){throw new java.util.concurrent.CompletionException(error);}
        },worker).whenComplete((review,error)->net.minecraft.client.Minecraft.getInstance().execute(()->{
            installingNativeMod=false;
            if(error!=null||disposed||!target.current()||importCancellation.get()||!currentSet99(request)){
                if(!disposed){unlockDevelopmentEditing();report(error==null?tr("review98.changed"):setError99(error));}return;
            }
            message=tr("set99.title");
            overlays.push(new ScriptInfoOverlay(message,reviewText99(review),accent())
                .cancelWith98(tr("review98.cancel"),()->{unlockDevelopmentEditing();message=tr("review98.cancelled");})
                .continueWith(tr("review98.apply"),()->confirmNativeSet99(request,target,review)));
        }));}catch(RuntimeException error){installingNativeMod=false;unlockDevelopmentEditing();report(setError99(error));}
    }
    private String setError99(Throwable error){
        while(error.getCause()!=null)error=error.getCause();String reason=String.valueOf(error.getMessage());
        String key=switch(reason){
            case "SET99_NO_CHANGES"->"unchanged";case "SET99_REMOVE_NOT_MANAGED"->"not_managed";
            case "SET99_REVIEW_CHANGED","SET99_CANDIDATE_CHANGED","SET99_INVENTORY_CHANGED","SET99_MANAGED_CHANGED","SET99_CATALOG_CHANGED"->"changed";
            case "SET99_RECOVERY_OR_PENDING"->"pending";case "SET99_FOREIGN_MOD_CONFLICT","SET99_DUPLICATE_MOD"->"conflict";
            case "SET99_RUNTIME_DIFFERS"->"runtime";case "SET99_RETENTION_LIMIT"->"retention";case "SET99_SPACE_REQUIRED"->"space";default->"";
        };
        return key.isEmpty()?reason:tr("set99.error."+key);
    }
    private String reviewText99(com.fixmer.genesis.supervisor.ModSet99.Review review){
        var text=new StringBuilder(tr("set99.note")).append("\n\n").append(tr("set99.revision")).append(": ").append(review.revision()).append("\n");
        text.append(tr("set99.total")).append(": ").append(review.entries().size()).append("\n\n");
        for(var change:review.changes())text.append(tr("set99.action."+change.action())).append(" · ").append(change.project()).append(" · ").append(change.modId()).append(" · ").append(change.beforeVersion()).append(" → ").append(change.afterVersion()).append("\n");
        text.append("\n").append(tr("set99.contents")).append("\n");
        for(var entry:review.entries())text.append(entry.modId()).append(" · ").append(entry.version()).append(" · ").append(entry.sha256(),0,12).append("\n");
        return text.append("\n").append(tr("review98.escape")).toString();
    }
    private void confirmNativeSet99(com.fixmer.mared.technology.minecraft.NativeModWorkflow.Request99 request,NativeWorldTarget target,com.fixmer.genesis.supervisor.ModSet99.Review review){
        if(disposed)return;
        if(importCancellation.get()||!target.current()||!currentSet99(request)){unlockDevelopmentEditing();report(tr("review98.changed"));return;}
        var versions=com.fixmer.mared.technology.minecraft.NativeModWorkflow.loadedVersions();installingNativeMod=true;message=tr("mod_staging");
        try{java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{return com.fixmer.mared.technology.minecraft.NativeModWorkflow.stageSet99(storage,request,versions,importCancellation::get,review);}
            catch(java.io.IOException error){throw new java.util.concurrent.CompletionException(error);}
        },worker).whenComplete((token,error)->net.minecraft.client.Minecraft.getInstance().execute(()->{
            installingNativeMod=false;
            if(error!=null||disposed||!target.current()||importCancellation.get()||!currentSet99(request)){
                com.fixmer.mared.technology.minecraft.NativeModWorkflow.cancel(token);
                if(!disposed){unlockDevelopmentEditing();report(error==null?tr("review98.changed"):setError99(error));}return;
            }
            request.updates().forEach((id,artifact)->BackgroundAutoApply.acknowledgeManual(storage,id,artifact.inputs()));
            automaticRestartProject=request.updates().size()==1?request.updates().keySet().iterator().next():"set99";stagedDeploymentToken=token;autoRestartWaiting=true;
            message=tr("mod_restarting");appendLog(message);
        }));}catch(RuntimeException error){installingNativeMod=false;unlockDevelopmentEditing();report(setError99(error));}
    }
    private void nativeBuildReady(String id,com.fixmer.genesis.technology.scripts.GradleBuildRunner.Result result){
        String serverFolder=dedicatedFolder116;dedicatedFolder116=null;unlockDevelopmentEditing();
        if(project==null||!project.id().equals(id)||serverFolder==null&&(nativeTarget==null||!nativeTarget.current())){report(tr("launch_changed"));return;}
        if(result.status()!=com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS){report(tr("mod_build_failed"));return;}
        var jars=result.inventory().artifacts().stream().filter(a->a.kind().equals("mod")||a.kind().equals("mod_embedded")).toList();
        if(jars.size()!=1){report(tr("mod_choose_artifact"));return;}
        if(serverFolder!=null)serverArtifact66(id,jars.get(0),serverFolder);else stageNativeArtifact(id,jars.get(0));
    }
    public void exportTestedMod(){
        if(disposed||readOnly||project==null||pending!=null||javaRunning())return;String id=project.id();
        submit(()->com.fixmer.mared.technology.minecraft.NativeModWorkflow.exportConfirmed(id),result->{
            message=tr("mod_exported")+": "+result;appendLog(message);net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(result.toString());
        });
    }
    private void prepareAutomaticRestart() {
        if (disposed || restartCheckpointInFlight || pending != null) return;
        autoRestartWaiting = false; restartCheckpointInFlight = true;
        var drafts = snapshots(); var context = workspaceLoaded ? workspaceSnapshot() : null;
        String appliedProject = automaticRestartProject.isEmpty()?applies.project():automaticRestartProject;
        String deploymentToken=stagedDeploymentToken;
        automaticRestartProject="";stagedDeploymentToken="";
        var mc = net.minecraft.client.Minecraft.getInstance();
        var appliedWorld = mc.getSingleplayerServer();
        var owner = mc.player == null ? null : mc.player.getUUID();
        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    checkpoint(drafts);
                    if (context != null) new com.fixmer.genesis.technology.scripts.EditorWorkspaceState(storage.root()).save(context);
                } catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
            }, worker).whenComplete((ignored,error) -> mc.execute(() -> {
                restartCheckpointInFlight = false;
                if (error != null || !deploymentToken.isEmpty() && importCancellation.get()) {
                    com.fixmer.mared.technology.minecraft.NativeModWorkflow.cancel(deploymentToken);
                    if (!disposed) { unlockDevelopmentEditing(); report("Restart cancelled: editor checkpoint failed: " + (error==null?"cancelled":error.getMessage())); }
                    com.fixmer.mared.Mared.LOGGER.error("[Mared dev] Editor restart checkpoint failed or cancelled", error);
                    return;
                }
                com.fixmer.mared.dev.client.DevAutoRestart.afterSuccessfulApply(appliedProject,
                    () -> (deploymentToken.isEmpty() || !importCancellation.get()) && mc.getSingleplayerServer() == appliedWorld && mc.player != null && owner != null && owner.equals(mc.player.getUUID()))
                    .whenComplete((accepted,failure) -> mc.execute(() -> {
                        if(failure!=null||!Boolean.TRUE.equals(accepted))com.fixmer.mared.technology.minecraft.NativeModWorkflow.cancel(deploymentToken);
                        if (!disposed && (failure != null || !Boolean.TRUE.equals(accepted))) {
                            unlockDevelopmentEditing();
                            report(com.fixmer.mared.dev.client.DevActivationService.lastStatus);
                        }
                    }));
            }));
        } catch (RuntimeException error) {
            com.fixmer.mared.technology.minecraft.NativeModWorkflow.cancel(deploymentToken);
            restartCheckpointInFlight = false; unlockDevelopmentEditing(); report(error.toString());
        }
    }
    public void tick(){
        if(disposed)return;tickShared132();if(commandsMode133)commandRuns133.update(logsPane.panel);
        applies.poll(result->{message=tr(result.success()?"server_success":"server_failed")+" · "+result.project();lastRunOutput=message+"\n"+result.detail();appendLog(message,result.success()?com.fixmer.mared.services.logging.LogSettings.Level.INFO:com.fixmer.mared.services.logging.LogSettings.Level.ERROR,"server");appendLog(result.detail(),result.success()?com.fixmer.mared.services.logging.LogSettings.Level.INFO:com.fixmer.mared.services.logging.LogSettings.Level.ERROR,"server");if(result.success()&&result.operation()==ServerProjectApplies.Operation.APPLY){autoRestartWaiting=true;setReadOnly(true);appendLog("Dev mode: checkpointing editor before automatic client restart");}});
        String serverLog=ServerProjectApplies.drain();if(!serverLog.isEmpty())for(String line:serverLog.split("\\R"))appendLog(line,com.fixmer.mared.services.logging.LogSettings.Level.INFO,"server");
        String live=builds.drainOutput();if(!live.isEmpty()){buildLine.append(live);int newline;
            while((newline=buildLine.indexOf("\n"))>=0){appendLog(buildLine.substring(0,Math.min(newline,2048)).replace("\r",""));buildLine.delete(0,newline+1);}
            if(buildLine.length()>2048){appendLog(buildLine.substring(0,2048));buildLine.setLength(0);}
        }
        builds.poll(result->{
            if(buildProject.equals(remoteBuild152)){remoteBuild152="";if(result.status()==com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS)remoteReady152=buildProject;}
            boolean wasListing=listingTasks;
            if(listingTasks){taskCatalogs.remove(buildProject);rememberTaskReport(buildProject,result.status()+" · "+result.exitCode()+"\n"+result.output());
                if(result.status()==com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS)taskCatalogs.put(buildProject,com.fixmer.genesis.technology.scripts.GradleTasks.parse(result.output(),result.truncated()));
                listingTasks=false;
            }else rememberBuild(new BuildReport(buildProject,activeBuildTask,activeBuildDirectory,result));
            message=(wasListing?tr("tasks_report")+" · ":"")+tr("build_result."+result.status().name())+" · "+result.exitCode();lastRunOutput=message+"\n"+result.output();
            if(buildLine.length()>0){appendLog(buildLine.toString());buildLine.setLength(0);}appendLog(message,result.status()==com.fixmer.genesis.technology.scripts.GradleBuildRunner.Status.SUCCESS?com.fixmer.mared.services.logging.LogSettings.Level.INFO:com.fixmer.mared.services.logging.LogSettings.Level.ERROR,"gradle");
            if(nativeBuild){nativeBuild=false;nativeBuildReady(buildProject,result);}
            for(var artifact:result.artifacts()){String line=tr("artifact")+": "+artifact;appendLog(line);lastRunOutput+="\n"+line;}
        });
        runs.poll(result->{message=tr("result."+result.status().name())+" · "+result.phase()+" · "+result.exitCode();
            lastRunOutput=message+"\n"+result.output();appendLog(message,result.status()==com.fixmer.genesis.technology.scripts.JavaSourceRunner.Status.SUCCESS?com.fixmer.mared.services.logging.LogSettings.Level.INFO:com.fixmer.mared.services.logging.LogSettings.Level.ERROR,"java");
            if(!result.output().isEmpty()){int shown=0;for(String line:result.output().split("\\R",-1))if(!line.isEmpty()){if(shown++>=254){appendLog(tr("logs_truncated"));break;}appendLog(line,result.status()==com.fixmer.genesis.technology.scripts.JavaSourceRunner.Status.SUCCESS?com.fixmer.mared.services.logging.LogSettings.Level.INFO:com.fixmer.mared.services.logging.LogSettings.Level.ERROR,"java");}}});
        if(pending!=null&&pending.isDone()){
            var task=pending;var callback=completed;pending=null;completed=null;
            try{callback.accept(task.get());}catch(InterruptedException e){Thread.currentThread().interrupt();report(e.toString());}
            catch(ExecutionException e){if(restartCheckpointInFlight){restartCheckpointInFlight=false;setReadOnly(false);}taskPreparationFailed(e.getCause().toString());importing=false;delivering=false;buildPreparing=false;retryAfter=System.nanoTime()+5_000_000_000L;checkpointRequested=true;report(e.getCause().getMessage());}catch(RuntimeException e){if(restartCheckpointInFlight){restartCheckpointInFlight=false;setReadOnly(false);}taskPreparationFailed(e.toString());importing=false;delivering=false;buildPreparing=false;report(e.getMessage());}
        }
        if(!loadedCatalog){loadedCatalog=true;refresh();}
        if(!nativeApplyQueued.isEmpty()&&pending==null&&!javaRunning()){
            String requested=nativeApplyQueued;nativeApplyQueued="";
            if(project!=null&&project.id().equals(requested)&&(dedicatedFolder116!=null||nativeTarget!=null&&nativeTarget.current())){
                nativeBuild=true;setReadOnly(false);prepareGradle(false,true);if(nativeBuild){nativeUiLocked=true;setReadOnly(true);}
            }else {dedicatedFolder116=null;unlockDevelopmentEditing();report(tr("launch_changed"));}
        }
        if(!remoteReady152.isEmpty()&&pending==null&&!javaRunning())openRemoteReady152();
        if(autoRestartWaiting && pending==null) prepareAutomaticRestart();
        tickWatchApply();
        if(checkpointRequested&&!nativeUiLocked&&!restartCheckpointInFlight&&pending==null&&System.nanoTime()>=retryAfter&&System.nanoTime()-changedAt>500_000_000L){
            checkpointRequested=false;
            var snapshots=snapshots();if(!snapshots.isEmpty())submit(()->{checkpoint(snapshots);return snapshots;},result->{for(Snapshot s:snapshots)checkpointed.put(s.key,s.text);});
        }
    }
    private List<Snapshot> snapshots(){
        var list=new ArrayList<Snapshot>();documents.forEach((k,d)->{if(k.project.equals(sharedId132))return;String text=d.currentText();
            if(!text.equals(checkpointed.getOrDefault(k,d.lastSavedText)))list.add(new Snapshot(k,d.lastSavedText,text));});return list;
    }
    private void checkpoint(List<Snapshot> snapshots)throws Exception{for(Snapshot s:snapshots){
        String baseline=s.baseline;Recovery write=workerWrites.get(s.key);
        if(write!=null&&write.baseline().equals(baseline))try{if(storage.read(s.key.project,s.key.file).equals(write.text()))baseline=write.text();}catch(java.io.IOException ignored){ /* Retain draft with its old baseline if the source disappeared. */ }
        storage.checkpoint(s.key.project,s.key.file,baseline,s.text);workerWrites.remove(s.key);
    }}
    public void refresh(){
        if(sharedActive132()){shared132.operation132("VIEW",Map.of(),d->{if("OK".equals(d.get("result")))applyShared132();});return;}
        if(!workspaceLoaded){submit(this::loadWorkspace,result->restoreWorkspace((Startup)result));return;}
        Project selected=project;Set<String> expandedRoots=Set.copyOf(filesPane.openedProjects);
        submit(()->{Catalog catalog=storage.list();ProjectFiles snapshot=null;var trees=new HashMap<String,ProjectFileTree>();var errors=new ArrayList<>(catalog.errors());
            if(selected!=null)for(Project p:catalog.projects())if(p.id().equals(selected.id())){snapshot=projectFiles(p);break;}
            for(Project p:catalog.projects())if(expandedRoots.contains(p.id())&&(selected==null||!p.id().equals(selected.id()))){try{trees.put(p.id(),projectFiles(p).tree);}catch(java.io.IOException error){trees.put(p.id(),ProjectFileTree.of(List.of()));errors.add(p.id()+": "+error.getMessage());}}
            return new Refreshed(new Catalog(catalog.projects(),List.copyOf(errors)),snapshot,trees);
        },result->{Refreshed refreshed=(Refreshed)result;Catalog catalog=refreshed.catalog;projects=catalog.projects();filesPane.snapshots.putAll(refreshed.trees);
            if(selected!=null&&project!=null&&project.id().equals(selected.id())){
                if(refreshed.selected!=null)applyProjectFiles(refreshed.selected);
                else{project=null;files=List.of();gradleProject=false;importedGradle=false;filesPane.update("",ProjectFileTree.of(List.of()));}
            }
            filesPane.prune();
            message=catalog.errors().isEmpty()?tr(selected!=null&&project==null?"project_missing":"choose"):String.join("; ",catalog.errors());
        });
    }
    private Loaded loadDocument(Project p,String path)throws java.io.IOException{
        String text=storage.read(p.id(),path);Recovery recovery=null;String error="";
        try{recovery=storage.recovery(p.id(),path);}catch(Exception e){error=e.getMessage();}
        return new Loaded(p,path,text,recovery,error);
    }
    private String installDocument(Key key,Loaded loaded){
        var d=new DocumentSession();d.storageId=loaded.path;d.setInitialText(loaded.text);String notice=tr("opened");
        if(loaded.recovery!=null&&!loaded.recovery.text().equals(loaded.text)){
            d.setInitialText(loaded.recovery.text());d.lastSavedText=loaded.recovery.baseline();d.recomputeDirty();notice=d.lastSavedText.equals(loaded.text)?tr("recovered"):tr("conflict");
        }
        configureSyntax(key,d);documents.put(key,d);checkpointed.put(key,d.currentText());return loaded.recoveryError.isEmpty()?notice:tr("recovery_error")+": "+loaded.recoveryError;
    }
    private Startup loadWorkspace()throws java.io.IOException{if(commandsMode133)com.fixmer.genesis.technology.scripts.CommandProjects133.migrate(storage.root(),FMLPaths.CONFIGDIR.get().resolve("mared/commands"));
        var warnings=new ArrayList<String>();var closing=closingWorkspaces.get(storage.root());
        if(closing!=null)try{closing.get(30,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException("Workspace restore interrupted",interrupted);}catch(ExecutionException|TimeoutException error){warnings.add(tr("workspace_close_pending"));}
        Catalog catalog=storage.list();var state=com.fixmer.genesis.technology.scripts.EditorWorkspaceState.empty();
        try{state=new com.fixmer.genesis.technology.scripts.EditorWorkspaceState(storage.root()).load();}catch(Exception error){warnings.add(tr("workspace_invalid")+": "+error.getMessage());}
        var available=new HashMap<String,Project>();for(Project p:catalog.projects())available.put(p.id(),p);
        ProjectFiles selected=null;Project selectedProject=available.get(state.project());
        if(selectedProject!=null)try{selected=projectFiles(selectedProject);}catch(java.io.IOException error){warnings.add(tr("workspace_skipped")+": "+state.project()+" · "+error.getMessage());}
        else if(!state.project().isEmpty())warnings.add(tr("workspace_skipped")+": "+state.project());
        var loaded=new ArrayList<Loaded>();
        for(var d:state.documents())try{Project p=available.get(d.project());if(p==null)throw new java.io.IOException(tr("project_missing"));loaded.add(loadDocument(p,d.file()));}
        catch(java.io.IOException error){warnings.add(tr("workspace_skipped")+": "+d.project()+"/"+d.file()+" · "+error.getMessage());}
        return new Startup(catalog,state,selected,loaded,warnings);
    }
    private void restoreWorkspace(Startup startup){
        workspaceLoaded=true;projects=startup.catalog.projects();
        for(var saved:startup.state.views())if(projects.stream().anyMatch(p->p.id().equals(saved.project()))){var view=filesPane.new ViewState();view.expanded.addAll(saved.expanded());view.selectedFolder=saved.folder();view.selectedEntry=saved.entry();view.scroll=saved.scroll();filesPane.views.put(saved.project(),view);}
        int recovered=0,conflicts=0;
        for(Loaded loaded:startup.documents){Key key=new Key(loaded.project.id(),loaded.path);installDocument(key,loaded);
            if(loaded.recovery!=null&&!loaded.recovery.text().equals(loaded.text)){recovered++;if(!loaded.recovery.baseline().equals(loaded.text))conflicts++;}
            if(!loaded.recoveryError.isEmpty())appendLog(tr("recovery_error")+": "+key.project+"/"+key.file+" · "+loaded.recoveryError);
        }
        for(var saved:startup.state.documents())if(saved.selected()){Key key=new Key(saved.project(),saved.file());if(documents.containsKey(key))selectedDocuments.put(saved.project(),key);}
        if(startup.selected!=null){applyProjectFiles(startup.selected);var saved=startup.state.views().stream().filter(v->v.project().equals(project.id())).findFirst().orElse(null);
            if(saved!=null){filesPane.selectedEntry=filesPane.tree.containsFile(saved.entry())||filesPane.tree.directories().contains(saved.entry())?saved.entry():"";filesPane.view.selectedFolder=filesPane.tree.directories().contains(saved.folder())?saved.folder():"";filesPane.view.expanded.clear();for(String folder:saved.expanded())if(filesPane.tree.directories().contains(folder))filesPane.view.expanded.add(folder);filesPane.scroll=saved.scroll();filesPane.rebuild();}
        }
        message=startup.state.project().isEmpty()?tr("choose"):tr("workspace_restored")+" · "+documents.size();
        if(recovered>0)message+=" · "+tr("recovered")+": "+recovered;if(conflicts>0)message+=" · "+tr("conflict")+": "+conflicts;
        for(String warning:startup.catalog.errors())appendLog(warning);for(String warning:startup.warnings)appendLog(warning);
        if(!startup.warnings.isEmpty())message+=" · "+tr("workspace_warnings")+": "+startup.warnings.size();
    }
    private com.fixmer.genesis.technology.scripts.EditorWorkspaceState.State workspaceSnapshot(){
        var docs=new ArrayList<com.fixmer.genesis.technology.scripts.EditorWorkspaceState.Document>();for(Key key:documents.keySet())docs.add(new com.fixmer.genesis.technology.scripts.EditorWorkspaceState.Document(key.project,key.file,key.equals(active)||key.equals(selectedDocuments.get(key.project))));
        if(project!=null){filesPane.view.scroll=filesPane.scroll;filesPane.view.selectedEntry=filesPane.selectedEntry;}
        var views=new ArrayList<com.fixmer.genesis.technology.scripts.EditorWorkspaceState.View>();var entries=new ArrayList<>(filesPane.views.entrySet());int folderBudget=8192;
        for(int i=entries.size()-1;i>=Math.max(0,entries.size()-16);i--){var entry=entries.get(i);var view=entry.getValue();var expanded=new ArrayList<String>();for(String folder:view.expanded.stream().sorted().toList()){int cost=folder.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+64;if(expanded.size()<128&&cost<=folderBudget){expanded.add(folder);folderBudget-=cost;}}
            views.add(new com.fixmer.genesis.technology.scripts.EditorWorkspaceState.View(entry.getKey(),expanded,view.selectedFolder,view.selectedEntry,Math.max(0,Math.min(10000,view.scroll))));}
        Collections.reverse(views);docs.removeIf(d->d.project().equals(sharedId132));views.removeIf(v->v.project().equals(sharedId132));return new com.fixmer.genesis.technology.scripts.EditorWorkspaceState.State(sharedActive132()?localProject132:project==null?"":project.id(),docs,views);
    }
    private ProjectFiles projectFiles(Project p)throws java.io.IOException {
        List<String> paths=storage.entries(p.id());
        return new ProjectFiles(p,paths,p.language()==Language.JAVA?storage.javaMainClass(p.id()):"",p.language()==Language.JAVA&&storage.hasGradle(p.id()),storage.gradleTask(p.id()),ProjectFileTree.of(paths,storage.folders(p.id())),storage.importedGradle(p.id()),storage.watchApplyEnabled(p.id()),storage.workingDirectory(p.id()));
    }
    private void applyProjectFiles(ProjectFiles f){
        boolean changedProject=project==null||!project.id().equals(f.project.id());
        if(!changedProject&&!files.equals(f.files))invalidateBuild(f.project.id());
        if(changedProject){startScroll=0;revealTab=true;sourceWatcher.reset();}
        watchApply=f.watch;watchedDirectory=f.directory;
        project=f.project;files=f.files;gradleProject=f.gradle;gradleTask=f.task;importedGradle=f.imported;
        entryPoints.put(project.id(),f.mainClass);filesPane.update(project.id(),f.tree);
        if(active!=null&&!active.project.equals(project.id()))active=null;
        if(active==null){Key remembered=selectedDocuments.get(project.id());if(remembered!=null&&documents.containsKey(remembered))active=remembered;
            if(active==null)for(Key key:documents.keySet())if(key.project.equals(project.id()))active=key;
            if(active==null){editor.attachDocument(null,null,null);editor.setEditable(false);}else{var doc=documents.get(active);editor.attachDocument(doc.document,doc.history,doc.view);editor.setEditable(!readOnly);}
        }
        if(changedProject&&active!=null&&f.tree.containsFile(active.file))filesPane.selectedEntry=active.file;
    }
    private void choose(Project p){if(shared132!=null&&p.id().equals(sharedId132)){applyShared132();return;}if(pending!=null)return;submit(()->projectFiles(p),result->{applyProjectFiles((ProjectFiles)result);message=tr("choose_file");});}
    private void configureSyntax(Key key,DocumentSession doc){if(key.file.endsWith(".mr")||key.file.endsWith(".mcfunction")||commandsMode133&&key.file.endsWith(".txt")){doc.view.setSpanResolver(com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver.INSTANCE);return;}
        var language=com.fixmer.genesis.technology.editor.CodeSyntaxLexer.languageFor(key.file);
        boolean light=com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().light;
        if(doc.view.spanResolver() instanceof com.fixmer.mared.gui2.framework.components.editor.CodeSyntaxSpanResolver syntax&&syntax.language()==language)syntax.theme(light,text());
        else doc.view.setSpanResolver(new com.fixmer.mared.gui2.framework.components.editor.CodeSyntaxSpanResolver(language,light,text()));
    }
    private void activate(Key key){
        if(sharedActive132()&&key.project.equals(sharedId132)&&!shared132.registered132(key.file)){open(key.file);return;}
        startScroll=0;revealTab=true;active=key;selectedDocuments.put(key.project,key);syncPanes();
        if(preferences.scriptsAutoReveal)filesPane.reveal(key);if(splitVisible)rememberRight();requestFocus();
    }
    private void attachPane(MaredMultiLineEditBox widget,Key key){
        var doc=key==null?null:documents.get(key);
        if(doc!=null){detachedPanes.remove(widget);if(widget.document()!=doc.document)widget.attachDocument(doc.document,doc.history,doc.view);configureSyntax(key,doc);}
        else if(detachedPanes.add(widget))widget.attachDocument(null,null,null);
        boolean editable=!readOnly&&doc!=null&&(!sharedActive132()||shared132.writable132(key.file));if(widget.isEditable()!=editable)widget.setEditable(editable);
    }
    private void rememberRight(){
        Key right=panes.right();String value=right==null?"":right.project+":"+right.file;if(commandsMode133)preferences.commandsSplitFile133=value;else preferences.scriptsSplitFile133=value;
        if(!value.equals(com.fixmer.mared.MaredSettings.getLayoutSplitFileRight()))com.fixmer.mared.MaredSettings.setLayoutSplitFileRight(value);
    }
    private void syncPanes(){
        boolean requested=com.fixmer.mared.MaredSettings.isLayoutSplitEnabled();
        splitVisible=requested&&bounds.width()>=240;
        String current=project==null?"":project.id();
        if(!current.equals(splitProject)){
            panes.enabled(false);panes.activate(null);panes.openRight(null);panes.focus(false);splitProject=current;
        }
        panes.retain(documents.keySet());snippetSessions.keySet().retainAll(documents.keySet());
        panes.enabled(splitVisible);
        if(!Objects.equals(active,panes.active()))panes.activate(active);
        if(splitVisible&&panes.right()==null){
            String remembered=commandsMode133?preferences.commandsSplitFile133:preferences.scriptsSplitFile133;if(remembered==null||remembered.isEmpty())remembered=com.fixmer.mared.MaredSettings.getLayoutSplitFileRight();Key candidate=null;
            for(Key k:documents.keySet())if(k.project.equals(current)&&!k.equals(panes.left())){
                if(candidate==null)candidate=k;
                if((k.project+":"+k.file).equals(remembered)||k.file.equals(remembered)){candidate=k;break;}
            }
            if(candidate!=null){boolean focus=panes.rightFocused();panes.openRight(candidate);panes.focus(focus);}
        }
        active=panes.active();editor=panes.rightFocused()?rightEditor:leftEditor;
        attachPane(leftEditor,panes.left());attachPane(rightEditor,panes.right());
        leftEditor.setFocused(isFocused()&&editor==leftEditor);rightEditor.setFocused(isFocused()&&splitVisible&&editor==rightEditor);
        int width=Math.max(1,bounds.width()-4),height=Math.max(1,bounds.height()-36);
        int left=splitVisible?com.fixmer.genesis.technology.editor.EditorSplitState.divider(width,com.fixmer.mared.MaredSettings.getLayoutSplitRatio()):width;
        dividerX=bounds.x()+2+left;
        leftEditor.setX(bounds.x()+2);leftEditor.setY(bounds.y()+34);leftEditor.setWidth(left);leftEditor.setHeight(height);
        rightEditor.setX(dividerX+5);rightEditor.setY(bounds.y()+34);rightEditor.setWidth(Math.max(1,width-left-5));rightEditor.setHeight(height);
    }
    private void focusPane(boolean right){
        panes.focus(right);active=panes.active();editor=panes.rightFocused()?rightEditor:leftEditor;
        if(active!=null){selectedDocuments.put(active.project,active);if(preferences.scriptsAutoReveal)filesPane.reveal(active);}
        requestFocus();syncPanes();rememberRight();
    }
    private void openRight(String path){
        if(project==null||pending!=null)return;String id=project.id();Key previousLeft=panes.left();
        open(path,()->{
            Key key=new Key(id,path);if(!documents.containsKey(key))return;
            com.fixmer.mared.MaredSettings.setLayoutSplitEnabled(true);
            if(previousLeft!=null&&!previousLeft.equals(key)&&documents.containsKey(previousLeft)){panes.enabled(true);panes.focus(false);panes.activate(previousLeft);}
            panes.openRight(key);active=key;
            syncPanes();rememberRight();requestFocus();
        });
    }
    private void choosePaneDocument(int x,int y){
        if(project==null||pending!=null)return;var entries=new ArrayList<ContextMenuEntry>();
        for(Key key:documents.keySet())if(key.project.equals(project.id()))entries.add(ContextMenuEntry.of(key.file,()->activate(key)));
        if(entries.isEmpty())entries.add(ContextMenuEntry.disabled(tr("split.choose")));
        overlays.push(new ContextMenuOverlay(x,y,entries).category(accent()));
    }
    public void toggleSplit(){
        com.fixmer.mared.MaredSettings.setLayoutSplitEnabled(!com.fixmer.mared.MaredSettings.isLayoutSplitEnabled());
        // Collapse preserves the focused document; a small window retains the preference for when resized.
        if(!com.fixmer.mared.MaredSettings.isLayoutSplitEnabled()){panes.enabled(false);active=panes.active();}
        syncPanes();rememberRight();
    }
    public boolean canBrowseFiles(){return workspaceLoaded&&!disposed&&pending==null&&project!=null;}
    public boolean canRevealFile(){return canBrowseFiles()&&active!=null&&active.project.equals(project.id());}
    public void revealActiveFile(){if(canRevealFile())filesPane.reveal(active);}
    public void collapseFolders(){if(canBrowseFiles())filesPane.setExpanded(false);}
    public void expandFolders(){if(canBrowseFiles())filesPane.setExpanded(true);}
    private void open(String path){if(com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(path)){launchBlockbench146(path);return;}open(path,()->{});}
    private Runnable fileOpened147=()->{};
    public void fileOpened147(Runnable callback){fileOpened147=Objects.requireNonNull(callback);}
    private void open(String path,Runnable afterOpen){
        fileOpened147.run();
        if(project==null||pending!=null)return;Project p=project;Key key=new Key(p.id(),path);
        if(documents.containsKey(key)&&!sharedActive132()){activate(key);afterOpen.run();return;}
        if(sharedActive132()){var session=shared132;session.open132(path,documents.get(key),doc->{if(disposed||shared132!=session)return;doc.storageId=path;documents.put(key,doc);if(project!=null&&project.id().equals(key.project))activate(key);message=session.status132();afterOpen.run();});return;}
        if(documents.size()>=16){report(tr("open_limit"));afterOpen.run();return;}
        submit(()->loadDocument(p,path),result->{Loaded loaded=(Loaded)result;String notice=installDocument(key,loaded);activate(key);message=notice;afterOpen.run();});
    }
    public void browseImportFolder(){
        if(!canImportGradle())return;
        overlays.push(new DirectoryPickerOverlay(overlays,path->{String initial=path.getFileName()==null?"Project":path.getFileName().toString();overlays.push(new NameDialogOverlay(tr("import_name"),name->submit(()->projectFiles(storage.linkFolder133(name,path)),value->{var opened=(ProjectFiles)value;var list=new ArrayList<>(projects);list.removeIf(p->p.id().equals(opened.project.id()));list.add(opened.project);list.sort(Comparator.comparing(Project::id));projects=List.copyOf(list);applyProjectFiles(opened);message=tr("folder_opened133");}),accent(),false,name->SourceProjects.validProject(name)?null:tr("bad_project")).customInput().acceptLabel("mared.dialog.create").initialValue(initial));},accent()));
    }
    public void importGradleProject(){
        if(!canImportGradle())return;
        overlays.push(new NameDialogOverlay(tr("import_directory"),value->{
            java.nio.file.Path external=java.nio.file.Path.of(com.fixmer.genesis.technology.scripts.BuildTools.cleanPath(value));
            overlays.push(new NameDialogOverlay(tr("import_name"),name->{
                if(!canImportGradle()){report(tr("operation_busy"));return;}
                importCancellation.set(false);importing=true;importingProject=name;message=tr("import_started");appendLog(message+" · "+name);
                submit(()->{var imported=storage.importGradle(name,external,importCancellation::get);return new ImportedFiles(imported,projectFiles(imported.project()));},result->{
                    importing=false;var loaded=(ImportedFiles)result;var imported=loaded.imported;var snapshot=loaded.files;
                    var list=new ArrayList<>(projects);list.add(imported.project());list.sort(Comparator.comparing(Project::id));projects=List.copyOf(list);applyProjectFiles(snapshot);
                    message=tr("import_done")+" · "+imported.summary().files()+" · "+imported.summary().bytes();appendLog(message);
                    if(files.contains("build.gradle"))open("build.gradle");else if(files.contains("build.gradle.kts"))open("build.gradle.kts");else if(files.contains("settings.gradle"))open("settings.gradle");else if(files.contains("settings.gradle.kts"))open("settings.gradle.kts");
                });
            },accent(),false,name->SourceProjects.validProject(name)?null:tr("bad_project")).customInput().acceptLabel("mared.dialog.create"));
        },accent(),false,value->com.fixmer.genesis.technology.scripts.BuildTools.validAbsolutePath(value)?null:tr("absolute_path")).customInput().inputLabel("mared.dialog.path_label").acceptLabel("mared.dialog.continue").maxLength(2048));
    }
    /** Minecraft extension import — analysis + copy/link; lives outside the text editor. */
    public void importMinecraftMod(){
        if(!canImportGradle())return;
        overlays.push(new DirectoryPickerOverlay(overlays,path->{
            submit(()->minecraftImports.analyze(path),analysis->{
                var info=(com.fixmer.genesis.technology.minecraft.MinecraftProjectInfo)analysis;
                message=info.summary();appendLog(tr("minecraft_import_analysis")+" · "+info.summary());
                var entries=new ArrayList<ContextMenuEntry>();
                entries.add(ContextMenuEntry.disabled(info.summary()));
                entries.add(ContextMenuEntry.of(tr("minecraft_import_report"),()->overlays.push(new ScriptInfoOverlay(tr("minecraft_import_analysis"),com.fixmer.mared.technology.minecraft.MinecraftImportActions.analysisReport(info),accent()))));
                entries.add(ContextMenuEntry.divider());
                entries.add(ContextMenuEntry.of(tr("minecraft_import_copy"),()->completeMinecraftImport(path,info,true)));
                entries.add(ContextMenuEntry.of(tr("minecraft_import_link"),()->completeMinecraftImport(path,info,false)));
                entries.add(ContextMenuEntry.divider());
                entries.add(ContextMenuEntry.of(tr("minecraft_import_cancel"),()->{}));
                overlays.push(new ContextMenuOverlay(bounds.x()+24,bounds.y()+48,entries).category(accent()));
            });
        },accent()));
    }
    private void completeMinecraftImport(java.nio.file.Path path,com.fixmer.genesis.technology.minecraft.MinecraftProjectInfo info,boolean copy){
        String guess=info.modId().isEmpty()?path.getFileName().toString().replaceAll("[^A-Za-z0-9_-]","_"):info.modId();
        overlays.push(new NameDialogOverlay(tr("import_name"),name->{
            if(!canImportGradle()){report(tr("operation_busy"));return;}
            importCancellation.set(false);importing=true;importingProject=name;message=tr("minecraft_import_started");appendLog(message+" · "+name+" · "+(copy?"COPY":"LINK"));
            submit(()->{
                var imported=copy?minecraftImports.importCopy(name,path,importCancellation::get):minecraftImports.importLink(name,path,importCancellation::get);
                return new MinecraftImported(imported,projectFiles(imported.project()));
            },result->{
                importing=false;var loaded=(MinecraftImported)result;var imported=loaded.imported;var snapshot=loaded.files;
                var list=new ArrayList<>(projects);list.add(imported.project());list.sort(Comparator.comparing(Project::id));projects=List.copyOf(list);applyProjectFiles(snapshot);
                launchModes.put(imported.project().id(),LaunchMode.BUILD);
                if(!imported.info().suggestedRunTasks().isEmpty())gradleTask=imported.info().suggestedRunTasks().contains("build")?"build":imported.info().suggestedRunTasks().get(0);
                message=tr("minecraft_import_done")+" · "+imported.info().summary();appendLog(message+" · "+imported.workingRoot());
                overlays.push(new ScriptInfoOverlay(tr("minecraft_import_analysis"),com.fixmer.mared.technology.minecraft.MinecraftImportActions.analysisReport(imported.info()),accent()));
                if(files.contains("build.gradle"))open("build.gradle");else if(files.contains("build.gradle.kts"))open("build.gradle.kts");
                else if(files.contains("src/main/resources/META-INF/neoforge.mods.toml"))open("src/main/resources/META-INF/neoforge.mods.toml");
            });
        },accent(),false,name->SourceProjects.validProject(name)?null:tr("bad_project")).customInput().acceptLabel("mared.dialog.create").maxLength(48).initialValue(guess));
    }
    public void showMinecraftProjectInfo(){if(sharedActive132())return;
        if(project==null||pending!=null)return;String id=project.id();
        submit(()->{
            java.nio.file.Path root=storage.workingDirectory(id);
            return minecraftImports.analyze(root);
        },value->{
            var info=(com.fixmer.genesis.technology.minecraft.MinecraftProjectInfo)value;
            overlays.push(new ScriptInfoOverlay(tr("minecraft_project_info"),com.fixmer.mared.technology.minecraft.MinecraftImportActions.analysisReport(info),accent()));
        });
    }
    public boolean canApplyModule(){if(commandsMode133)return false;if(sharedActive132())return false;if(importedGradle)return canApplyNativeMod();return !readOnly&&!disposed&&project!=null&&project.language()==Language.JAVA&&pending==null&&!javaRunning()&&ServerProjectApplies.available();}
    public boolean canManageModule(boolean rollback){if(importedGradle)return false;if(!canApplyModule())return false;var status=ServerProjectApplies.status(project.id());return status!=null&&(!rollback||status.canRollback());}
    public void manageServerModule(boolean rollback){if(canManageModule(rollback)&&applies.manage(project.id(),rollback))message=tr("phase.apply");}
    public void showServerStatus(){if(sharedActive132())return;if(project==null)return;if(importedGradle){String id=project.id();submit(()->com.fixmer.mared.technology.minecraft.NativeModWorkflow.status(id),value->overlays.push(new ScriptInfoOverlay(tr("server_status"),value.toString(),accent())));return;}var status=ServerProjectApplies.status(project.id());overlays.push(new ScriptInfoOverlay(tr("server_status"),status==null?tr("server_not_running"):project.id()+"\n"+tr("server_generation")+": "+status.generation()+"\n"+tr("server_running")+": "+tr(status.running()?"yes":"no")+"\n"+tr("server_callbacks")+": "+status.callbacks()+"\n"+tr("server_can_rollback")+": "+tr(status.canRollback()?"yes":"no")+"\n"+status.error(),accent()));}
    public void exportServerApi(){if(sharedActive132())return;if(readOnly||project==null||pending!=null||javaRunning())return;String id=project.id();submit(()->storage.exportServerApi(id),value->{net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(value.toString());message=tr("server_api_copied")+": "+value;appendLog(message);refresh();});}
    public void applyServerModule(){
        if(importedGradle){applyMinecraftMod();return;}
        if(!canApplyModule()){report(tr("server_requires_local"));return;}String id=project.id();
        serverEntry(id,null);
    }
    private void serverEntry(String id,com.fixmer.genesis.technology.scripts.BuildArtifacts.Artifact artifact){
        String guess=moduleEntries.getOrDefault(id,"WorldModule");String path=filesPane.selectedEntry;
        if(artifact==null&&path.endsWith(".java")){guess=RepositoryPath.leaf(path).replaceFirst("\\.java$","");var doc=documents.get(new Key(id,path));if(doc!=null){var matcher=java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;").matcher(doc.currentText());if(matcher.find())guess=matcher.group(1)+"."+guess;}}
        overlays.push(new NameDialogOverlay(tr("server_entry")+" · "+id,value->{
            if(!canApplyModule()||!project.id().equals(id)){report(tr("launch_changed"));return;}
            if(artifact!=null){var report=buildReports.get(id);if(report==null||!report.result().inventory().artifacts().contains(artifact)){report(tr("artifact_result_changed"));return;}if(documents.entrySet().stream().anyMatch(e->e.getKey().project.equals(id)&&e.getValue().dirty)){report(tr("server_build_first"));return;}}
            var edits=new HashMap<String,Recovery>();for(var e:documents.entrySet())if(e.getKey().project.equals(id))edits.put(e.getKey().file,new Recovery(e.getValue().lastSavedText,e.getValue().currentText()));
            if(applies.start(id,value,edits,artifact)){if(!preferences.scriptsKeepLogs)clearLogs();moduleEntries.put(id,value);message=tr("phase.compile");appendLog(tr("server_applying")+" · "+id);}
        },accent(),false,value->com.fixmer.genesis.technology.scripts.JavaSourceRunner.validMainClass(value)?null:tr("bad_entry")).customInput().inputLabel("mared.dialog.class_label").acceptLabel("mared.dialog.apply").maxLength(192).initialValue(guess));
    }
    public void newServerModuleProject(){
        if(!workspaceLoaded||readOnly||disposed||pending!=null||javaRunning())return;
        overlays.push(new NameDialogOverlay(tr("new_server_module"),name->submit(()->projectFiles(storage.createServerModule(name)),value->{var snapshot=(ProjectFiles)value;var list=new ArrayList<>(projects);list.add(snapshot.project);list.sort(Comparator.comparing(Project::id));projects=List.copyOf(list);applyProjectFiles(snapshot);moduleEntries.put(snapshot.project.id(),"WorldModule");launchModes.put(snapshot.project.id(),LaunchMode.SERVER);open("src/WorldModule.java");message=tr("server_template_created");}),accent(),false,name->SourceProjects.validProject(name)?null:tr("bad_project")).customInput().acceptLabel("mared.dialog.create"));
    }
    public void newNeoForgeProject(){
        if(readOnly||!canConfigureBuildTools())return;
        overlays.push(new NameDialogOverlay(tr("new_neoforge"),id->{
            if(!canConfigureBuildTools()){report(tr("operation_busy"));return;}
            submit(()->projectFiles(storage.createNeoForge(id)),result->{
                ProjectFiles snapshot=(ProjectFiles)result;var list=new ArrayList<>(projects);list.add(snapshot.project);list.sort(Comparator.comparing(Project::id));projects=List.copyOf(list);
                applyProjectFiles(snapshot);message=tr("neoforge_created");appendLog(message);launchModes.put(snapshot.project.id(),LaunchMode.BUILD);
                open(com.fixmer.genesis.technology.scripts.NeoForgeProjectTemplate.entry(snapshot.project.id()),()->{
                overlays.push(new ContextMenuOverlay(bounds.x()+12,bounds.y()+42,List.of(
                    ContextMenuEntry.disabled(tr("neoforge_ready")),
                    item("build_gradle",canBuildGradle(),this::buildGradle),
                    item("project_path",true,this::copyProjectPath),
                    ContextMenuEntry.of(tr("continue_editing"),()->{}))).category(accent()));
                });
            });
        },accent(),false,id->com.fixmer.genesis.technology.scripts.NeoForgeProjectTemplate.validId(id)?null:tr("bad_mod_id")).inputLabel("mared.dialog.mod_id_label").maxLength(48));
    }
    private void newFolderProject133(){if(!workspaceLoaded||readOnly||pending!=null)return;overlays.push(new DirectoryPickerOverlay(overlays,parent->overlays.push(new NameDialogOverlay(tr("new_project"),name->submit(()->{Project made=storage.createFolder133(name,parent);if(commandsMode133)storage.createFile(name,"main.mcfunction","# Map commands\nsay Hello from MaRed\n");return projectFiles(made);},value->{var selected=(ProjectFiles)value;var catalog=new ArrayList<>(projects);catalog.add(selected.project);projects=List.copyOf(catalog);applyProjectFiles(selected);message=tr("folder_opened133");}),accent(),false,name->SourceProjects.validProject(name)?null:tr("bad_project")).customInput().acceptLabel("mared.dialog.create").initialValue("Project")),accent()));}
    public void newProject(){if(!commandsMode133&&!scripting145&&direction145!=com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.MODS){newContentProject145();return;}if(commandsMode133){newFolderProject133();return;}
        if(!workspaceLoaded||readOnly||disposed||pending!=null)return;
        overlays.push(new ContextMenuOverlay(bounds.x()+12,bounds.y()+42,List.of(
            ContextMenuEntry.disabled(tr("project_type")),
            item("new_neoforge",canConfigureBuildTools(),this::newNeoForgeProject),
            item("new_server_module",!javaRunning(),this::newServerModuleProject),
            item("new_empty_project",true,this::newEmptyProject),item("new_folder_project133",true,this::newFolderProject133),
            ContextMenuEntry.divider(),item("jdk_home",canConfigureBuildTools(),()->configureBuildTool("java_home")),
            item("wrapper_jar",canConfigureBuildTools(),()->configureBuildTool("wrapper_jar")))).category(accent()));
    }
    private void newEmptyProject(){
        if(!workspaceLoaded||readOnly||disposed||pending!=null)return;
        overlays.push(new NameDialogOverlay(tr("new_project"),name->submit(()->{var created=storage.createEmpty(name);if(commandsMode133)storage.createFile(name,"main.mcfunction","# Commands / map project\n# Put Minecraft commands here, without the leading slash.\nsay Hello from MaRed\n");return projectFiles(created);},result->{
            ProjectFiles f=(ProjectFiles)result;Project p=f.project;
            var list=new ArrayList<>(projects);list.add(p);list.sort(Comparator.comparing(Project::id));projects=List.copyOf(list);
            applyProjectFiles(f);active=null;editor.attachDocument(null,null,null);editor.setEditable(false);message=tr("empty_project_created");
        }),accent(),false,name->SourceProjects.validProject(name)?null:tr("bad_project")).customInput().acceptLabel("mared.dialog.create"));
    }
    public void chooseProjectMenu(){
        if(pending!=null||disposed)return;var entries=new ArrayList<ContextMenuEntry>();
        for(Project p:projects)entries.add(ContextMenuEntry.of(p.id(),()->choose(p)));
        if(entries.isEmpty())entries.add(ContextMenuEntry.disabled(tr("start.no_projects")));
        entries.add(ContextMenuEntry.divider());entries.add(ContextMenuEntry.of(tr("refresh"),this::refresh));
        overlays.push(new ContextMenuOverlay(bounds.x()+8,bounds.y()+38,entries).category(accent()));
    }
    public void openFileMenu(){
        if(!canBrowseFiles())return;var entries=new ArrayList<ContextMenuEntry>();
        for(String path:files)if(com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(path))entries.add(ContextMenuEntry.of(path,()->open(path)));
        if(entries.isEmpty())entries.add(ContextMenuEntry.disabled(tr("start.no_files")));
        overlays.push(new ContextMenuOverlay(bounds.x()+8,bounds.y()+38,entries).category(accent()));
    }
    private void renderStart(MaredRenderContext ctx){
        var g=ctx.graphics();var f=ctx.font();int x=bounds.x()+12,w=Math.max(1,Math.min(400,bounds.width()-24));
        int top=bounds.y()+40,y=top-startScroll;var hits=new ArrayList<StartHit>();
        g.enableScissor(bounds.x(),top,bounds.right(),bounds.bottom());
        try{
            g.drawString(f,f.plainSubstrByWidth(tr(project==null?"start.title":"start.project_title")+(project==null?"":" · "+project.id()),w),x,y,text(),false);y+=18;
            for(var line:f.split(net.minecraft.network.chat.Component.literal(tr(readOnly?"start.read_only":project==null?"start.intro":"start.project_intro")),w)){g.drawString(f,line,x,y,dim(),false);y+=12;}y+=10;
            if(!readOnly){
                if(project==null){y=startButton(ctx,hits,x,y,w,"new_project",pending==null,this::newProject);if(!commandsMode133&&!contentDirection145()){y=startButton(ctx,hits,x,y,w,"new_neoforge",canConfigureBuildTools(),this::newNeoForgeProject);y=startButton(ctx,hits,x,y,w,"import_minecraft",canImportGradle(),this::importMinecraftMod);}y=startButton(ctx,hits,x,y,w,"import_folder",canImportGradle(),this::browseImportFolder);}
                else{y=startButton(ctx,hits,x,y,w,"new_java_type",canManageFiles(),()->newJavaTypeMenu(bounds.x()+12,bounds.y()+42));y=startButton(ctx,hits,x,y,w,"new_file",canManageFiles(),this::newFile);y=startButton(ctx,hits,x,y,w,"new_folder",canManageFiles()&&!sharedActive132(),this::newFolder);if(importedGradle)y=startButton(ctx,hits,x,y,w,"minecraft_project_info",pending==null,this::showMinecraftProjectInfo);}
            }
            if(project!=null)y=startButton(ctx,hits,x,y,w,"start.open_file",canBrowseFiles(),this::openFileMenu);
            y=startButton(ctx,hits,x,y,w,"choose_project",pending==null&&!disposed,this::chooseProjectMenu);
            y+=8;for(var line:f.split(net.minecraft.network.chat.Component.literal(tr(project==null?"start.import_note":"start.folder_note")),w)){g.drawString(f,line,x,y,dim(),false);y+=12;}
        }finally{g.disableScissor();}
        startHits=List.copyOf(hits);startMaxScroll=Math.max(0,y+startScroll+8-bounds.bottom());startScroll=Math.max(0,Math.min(startScroll,startMaxScroll));
    }
    private int startButton(MaredRenderContext ctx,List<StartHit> hits,int x,int y,int w,String key,boolean enabled,Runnable action){
        var g=ctx.graphics();boolean hovered=ctx.mouseX()>=x&&ctx.mouseX()<x+w&&ctx.mouseY()>=y&&ctx.mouseY()<y+22;
        g.fill(x,y,x+w,y+22,enabled&&hovered?raised():panel());g.fill(x,y,x+1,y+22,enabled?accent():edge());
        g.drawString(ctx.font(),ctx.font().plainSubstrByWidth(tr(key),Math.max(0,w-16)),x+8,y+7,enabled?text():dim(),false);
        if(enabled&&y>=bounds.y()+40&&y+22<=bounds.bottom())hits.add(new StartHit(new MaredBounds(x,y,w,22),action));return y+26;
    }
    private ContextMenuEntry item(String key,boolean enabled,Runnable action){return enabled?ContextMenuEntry.of(tr(key),action):ContextMenuEntry.disabled(tr(key));}
    public void projectMenu(int x,int y){if(contentDirection145()||contentProject145()){contentMenu145(x,y,true);return;}if(commandsMode133){overlays.push(new ContextMenuOverlay(x,y,List.of(item("new_project",workspaceLoaded,this::newProject),item("open_project",true,this::chooseProjectMenu),item("import_folder",canImportGradle(),this::browseImportFolder),item("refresh",true,this::refresh),item("copy_project_path",project!=null,this::copyProjectPath))).category(accent()));return;}
        overlays.push(new ContextMenuOverlay(x,y,List.of(item("choose_project",pending==null&&!disposed,this::chooseProjectMenu),item("new_project",!readOnly&&pending==null,this::newProject),item("new_server_module",!readOnly&&pending==null&&!javaRunning(),this::newServerModuleProject),item("new_neoforge",!readOnly&&canConfigureBuildTools(),this::newNeoForgeProject),item("gradle_init",canConfigureGradle(),this::initializeGradle),item("import_minecraft",canImportGradle(),this::importMinecraftMod),item("import_folder",canImportGradle(),this::browseImportFolder),item("minecraft_project_info",project!=null&&pending==null,this::showMinecraftProjectInfo),ContextMenuEntry.divider(),item("export_java",!readOnly&&canExportJava(),this::exportJava),item("project_path",project!=null,this::copyProjectPath),item("refresh",pending==null,this::refresh),ContextMenuEntry.divider(),item("workflow",true,this::showWorkflow))).category(accent()));
    }
    private boolean canAssistEdit(){return !disposed&&!readOnly&&active!=null&&documents.containsKey(active)&&editor.isEditable();}
    private void assistedChange(){
        if(active==null)return;var doc=documents.get(active);if(doc==null)return;
        doc.view.resetBlink();doc.view.ensureCursorVisible(editor.getWidth(),editor.getHeight(),doc.document,net.minecraft.client.Minecraft.getInstance().font);changed();requestFocus();
    }
    public void codeToolsMenu(int x,int y){
        boolean open=active!=null&&documents.containsKey(active),edit=canAssistEdit();
        overlays.push(new ContextMenuOverlay(x,y,List.of(
            item("assist.replace",edit,()->replaceOptionsMenu(x,y)),item("assist.snippets",edit,()->snippetsMenu(x,y)),
            ContextMenuEntry.divider(),item("assist.jump",open,()->bracketAction(false)),item("assist.block",open,()->bracketAction(true)),
            item("assist.delete_lines",edit,this::deleteEditorLines),item("assist.trim",edit,this::trimEditorLines))).category(accent()));
    }
    public void replaceOptionsMenu(int x,int y){
        if(!canAssistEdit())return;
        overlays.push(new ContextMenuOverlay(x,y,List.of(
            ContextMenuEntry.of(tr("assist.match_case")+": "+(replaceMatchCase?"✓":"—"),()->{replaceMatchCase=!replaceMatchCase;replaceOptionsMenu(x,y);}),
            ContextMenuEntry.of(tr("assist.whole_word")+": "+(replaceWholeWord?"✓":"—"),()->{replaceWholeWord=!replaceWholeWord;replaceOptionsMenu(x,y);}),
            ContextMenuEntry.of(tr("assist.selected_only")+": "+(replaceSelectedOnly?"✓":"—"),()->{replaceSelectedOnly=!replaceSelectedOnly;replaceOptionsMenu(x,y);}),
            ContextMenuEntry.divider(),ContextMenuEntry.of(tr("assist.replace_start"),this::replacePrompt))).category(accent()));
    }
    private void replacePrompt(){
        if(!canAssistEdit())return;
        Key key=active;var doc=documents.get(key);int version=doc.document.contentVersion();
        if(replaceSelectedOnly&&!doc.document.hasSelection()){report(tr("assist.selection"));return;}
        String selected=doc.document.getSelectedText();String initial=!selected.isEmpty()&&selected.length()<=256&&!selected.contains("\n")?selected:lastReplaceQuery;
        overlays.push(new NameDialogOverlay(tr("assist.find")+" · "+key.file,query->{
            if(!assistTarget(key,doc,version))return;
            lastReplaceQuery=query;
            overlays.push(new NameDialogOverlay(tr("assist.replacement"),replacement->{
                if(!assistTarget(key,doc,version))return;lastReplacement=replacement;
                try{
                    var plan=com.fixmer.mared.gui2.framework.components.editor.CodeFindReplace.plan(doc.document,query,replacement,new com.fixmer.mared.gui2.framework.components.editor.CodeFindReplace.Options(replaceMatchCase,replaceWholeWord,replaceSelectedOnly));
                    int count=plan.matches().size();
                    if(count==0){report(tr("assist.no_matches"));return;}
                    replacementPreview(key,doc,version,plan);

                }catch(IllegalArgumentException bad){report(tr("assist."+bad.getMessage()));}
            },accent(),false,value->value.indexOf('\r')<0&&value.indexOf('\n')<0?null:tr("assist.replacement_invalid")).customInput().inputLabel("mared.dialog.replacement_label").preserveWhitespace().maxLength(4096).initialValue(lastReplacement));
        },accent(),false,value->!value.isEmpty()&&value.length()<=256&&!value.contains("\n")?null:tr("assist.query")).customInput().inputLabel("mared.dialog.query_label").preserveWhitespace().maxLength(256).initialValue(initial));
    }
    private void replacementPreview(Key key,DocumentSession doc,int version,com.fixmer.mared.gui2.framework.components.editor.CodeFindReplace.Plan plan){
        if(!assistTarget(key,doc,version))return;
        int count=plan.matches().size();
        var preview=new StringBuilder(tr("assist.preview_note")+"\n\n"+key.file+"\n"+tr("assist.find")+": ["+lastReplaceQuery+"]\n"+tr("assist.replacement")+": ["+plan.replacement()+"]\n"+tr("assist.matches")+": "+count+"\n\n");
        for(var match:plan.matches().subList(0,Math.min(30,count)))preview.append(match.line()+1).append(": ").append(match.preview()).append('\n');
        if(count>30)preview.append("…\n");
        overlays.push(new ScriptInfoOverlay(tr("assist.preview"),preview.toString(),accent()).continueWith(tr("assist.continue"),()->replacementActions(key,doc,version,plan)));
    }
    private void replacementActions(Key key,DocumentSession doc,int version,com.fixmer.mared.gui2.framework.components.editor.CodeFindReplace.Plan plan){
        if(!assistTarget(key,doc,version))return;
        overlays.push(new ContextMenuOverlay(bounds.x()+12,bounds.y()+42,List.of(
            ContextMenuEntry.disabled(tr("assist.matches")+": "+plan.matches().size()),
            ContextMenuEntry.of(tr("assist.preview"),()->replacementPreview(key,doc,version,plan)),
            ContextMenuEntry.of(tr("assist.replace_next"),()->applyReplacement(key,doc,version,plan,false)),
            ContextMenuEntry.of(tr("assist.replace_all")+" ("+plan.matches().size()+")",()->applyReplacement(key,doc,version,plan,true)))).category(accent()));
    }
    private boolean assistTarget(Key key,DocumentSession doc,int version){
        if(!canAssistEdit()||!key.equals(active)||documents.get(key)!=doc||doc.document.contentVersion()!=version){report(tr("assist.changed"));return false;}return true;
    }
    private void applyReplacement(Key key,DocumentSession doc,int version,com.fixmer.mared.gui2.framework.components.editor.CodeFindReplace.Plan plan,boolean all){
        if(!assistTarget(key,doc,version))return;
        try{snippetSessions.remove(key);boolean changed=plan.apply(doc.document,doc.history,all);if(changed){invalidateBuild(key.project);assistedChange();}message=tr(changed?"assist.replaced":"assist.unchanged");}
        catch(IllegalStateException stale){report(tr("assist.changed"));}
    }
    public void snippetsMenu(int x,int y){
        if(!canAssistEdit())return;Key key=active;var snippets=com.fixmer.mared.gui2.framework.components.editor.CodeSnippets.forPath(key.file);
        var rows=new ArrayList<ContextMenuEntry>();rows.add(ContextMenuEntry.disabled(tr("assist.snippet_hint")));
        for(var snippet:snippets)rows.add(ContextMenuEntry.of(tr("assist.snippet."+snippet.id()),()->{
            if(!canAssistEdit()||!key.equals(active))return;var doc=documents.get(key);
            try{var session=com.fixmer.mared.gui2.framework.components.editor.CodeSnippets.insert(doc.document,doc.history,snippet);if(!session.finished())snippetSessions.put(key,session);else snippetSessions.remove(key);invalidateBuild(key.project);assistedChange();message=tr("assist.snippet_hint");}
            catch(IllegalArgumentException bad){report(tr("assist.limit"));}
        }));
        if(snippets.isEmpty())rows.add(ContextMenuEntry.disabled(tr("assist.no_snippets")));
        overlays.push(new ContextMenuOverlay(x,y,rows).category(accent()));
    }
    private void bracketAction(boolean select){
        if(active==null||!documents.containsKey(active))return;var doc=documents.get(active);
        try{
            boolean found=select?com.fixmer.mared.gui2.framework.components.editor.CodeBracketNavigator.selectBlock(doc.document,active.file):com.fixmer.mared.gui2.framework.components.editor.CodeBracketNavigator.jump(doc.document,active.file);
            if(!found){report(tr("assist.no_bracket"));return;}
            snippetSessions.remove(active);message="";doc.view.resetBlink();doc.view.ensureCursorVisible(editor.getWidth(),editor.getHeight(),doc.document,net.minecraft.client.Minecraft.getInstance().font);requestFocus();
        }catch(IllegalArgumentException limit){report(tr("assist.limit"));}
    }
    private void deleteEditorLines(){
        if(!canAssistEdit())return;var doc=documents.get(active);snippetSessions.remove(active);
        if(com.fixmer.mared.gui2.framework.components.editor.EditorTextEdits.deleteLines(doc.document,doc.history)){message="";invalidateBuild(active.project);assistedChange();}
    }
    private void trimEditorLines(){
        if(!canAssistEdit())return;var doc=documents.get(active);snippetSessions.remove(active);
        if(com.fixmer.mared.gui2.framework.components.editor.EditorTextEdits.trimTrailing(doc.document,doc.history)){invalidateBuild(active.project);assistedChange();message=tr("assist.trimmed");}else message=tr("assist.unchanged");
    }
    public void searchMenu(int x,int y){
        boolean browse=canBrowseFiles();
        overlays.push(new ContextMenuOverlay(x,y,List.of(
            item("search_files",browse,this::searchFiles),item("search_text",active!=null&&pending==null,this::searchText),
            item("search_project_text",browse,this::searchProjectText),item("search_goto_line",active!=null&&pending==null,this::goToLine),
            item("search_open_files",browse,this::searchOpenFiles),ContextMenuEntry.divider(),item("assist.tools",active!=null,()->codeToolsMenu(x,y)))).category(accent()));
    }
    private ScriptSearchOverlay palette(ScriptSearchOverlay overlay,int mode){
        return overlay.modes(mode,List.of(
            new ScriptSearchOverlay.Mode(tr("search_files"),canBrowseFiles(),this::searchFiles),
            new ScriptSearchOverlay.Mode(tr("search_text"),active!=null&&pending==null,this::searchText),
            new ScriptSearchOverlay.Mode(tr("search_project_text"),canBrowseFiles(),this::searchProjectText),
            new ScriptSearchOverlay.Mode(tr("search_goto_line"),active!=null&&pending==null,this::goToLine),
            new ScriptSearchOverlay.Mode(tr("search_open_files"),canBrowseFiles(),this::searchOpenFiles)));
    }
    public void goToLine(String initial){goToLine();if(overlays.top() instanceof NameDialogOverlay dialog&&initial.matches("[0-9]{1,9}"))dialog.initialValue(initial);}
    public void goToLine(){
        if(disposed||pending!=null||active==null)return;Key key=active;var doc=documents.get(key);
        overlays.push(new NameDialogOverlay(tr("search_goto_line")+" · "+key.file,value->{
            if(disposed||pending!=null||!key.equals(active)){report(tr("search_changed"));return;}
            int line=Integer.parseInt(value)-1;if(line>=doc.document.lineCount()){report(tr("search_bad_line"));return;}
            doc.document.clearSelection();doc.document.setCursor(line,0);revealSearch(doc);
        },accent(),false,value->{try{int line=Integer.parseInt(value);return line>=1&&line<=doc.document.lineCount()?null:tr("search_bad_line");}catch(NumberFormatException bad){return tr("search_bad_line");}}).customInput().inputLabel("mared.dialog.line_label").acceptLabel("mared.dialog.go").maxLength(9).initialValue(Integer.toString(doc.document.cursorLine()+1)));
    }
    public void searchOpenFiles(){searchOpenFiles("");}
    public void searchOpenFiles(String initial){
        if(!canBrowseFiles())return;String id=project.id();
        List<String> paths=documents.keySet().stream().filter(k->k.project.equals(id)).map(k->k.file).sorted().toList();
        overlays.push(palette(new ScriptSearchOverlay(tr("search_open_files")+" · "+id,paths,"",null,initial,accent(),hit->{
            Key key=new Key(id,hit.path());if(disposed||pending!=null||project==null||!project.id().equals(id)||!documents.containsKey(key)){report(tr("search_changed"));return;}activate(key);
        }),4));
    }
    private void revealSearch(DocumentSession doc){
        doc.view.resetBlink();doc.view.ensureCursorVisible(editor.getWidth(),editor.getHeight(),doc.document,net.minecraft.client.Minecraft.getInstance().font);requestFocus();
    }
    public void searchProjectText(){searchProjectText("");}
    public void searchProjectText(String initial){if(sharedActive132()){overlays.push(new NameDialogOverlay(tr("search_project_text"),query->shared132.operation132("SEARCH126",Map.of("query",query,"matchCase","false"),answer->{var entries=new ArrayList<ContextMenuEntry>();for(String row:answer.getOrDefault("hits","").lines().toList()){String[] fields=row.split("\\|",3);if(fields.length==3)entries.add(ContextMenuEntry.of(fields[0]+":"+fields[1]+" · "+fields[2],()->openSharedFile132(fields[0],Integer.parseInt(fields[1]))));}if(entries.isEmpty())entries.add(ContextMenuEntry.disabled(tr("search_no_results")));overlays.push(new ContextMenuOverlay(bounds.x()+8,bounds.y()+38,entries).category(accent()));}),accent(),false).initialValue(initial));return;}
        if(!canBrowseFiles())return;String id=project.id();Project captured=project;
        var paths=files.stream().filter(com.fixmer.genesis.technology.scripts.GradleProjectImport::editable).toList();
        Map<String,String> drafts=new HashMap<>();documents.forEach((key,doc)->{if(key.project.equals(id))drafts.put(key.file,doc.currentText());});
        Map<String,String> scanned=new ConcurrentHashMap<>();
        overlays.push(palette(new ScriptSearchOverlay(tr("search_project_text")+" · "+id,(value,stop)->{
            scanned.clear();return com.fixmer.genesis.technology.scripts.ScriptSearch.project(paths,path->{
                String text=drafts.containsKey(path)?drafts.get(path):storage.read(id,path);text=text.replace("\r","");scanned.put(path,text);return text;
            },value,stop);
        },false,initial,accent(),hit->{
            if(disposed||pending!=null||project==null||!project.id().equals(id)){report(tr("search_changed"));return;}
            Key key=new Key(id,hit.path());String snapshot=scanned.get(hit.path());
            Runnable navigate=()->{var doc=documents.get(key);if(doc==null||snapshot==null||!snapshot.equals(doc.currentText())){report(tr("search_changed"));return;}
                activate(key);doc.document.clearSelection();doc.document.setCursor(hit.line(),hit.column());doc.document.startSelection(hit.line(),hit.column());
                doc.document.setCursor(hit.line(),hit.column()+hit.length());doc.document.extendSelection(doc.document.cursorLine(),doc.document.cursorCol());revealSearch(doc);
            };
            if(documents.containsKey(key))navigate.run();
            else {if(sharedActive132()){open(hit.path(),navigate);return;}
                if(documents.size()>=16){report(tr("open_limit"));return;}
                submit(()->loadDocument(captured,hit.path()),result->{if(project==null||!project.id().equals(id)){report(tr("search_changed"));return;}
                    Loaded loaded=(Loaded)result;installDocument(key,loaded);navigate.run();});}
        }),2));
    }
    public void searchFiles(){searchFiles("");}
    public void searchFiles(String initial) {
        if(disposed||pending!=null||project==null)return;
        String id=project.id();
        overlays.push(palette(new ScriptSearchOverlay(tr("search_files")+" · "+id,files,"",null,initial,accent(),hit->{
            if(disposed||pending!=null||project==null||!project.id().equals(id)){report(tr("search_changed"));return;}
            filesPane.reveal(new Key(id,hit.path()));
            if(!com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(hit.path())){report(tr("binary_file")+" · "+hit.path());return;}
            open(hit.path());requestFocus();
        }),0));
    }
    public void searchText(){searchText(null);}
    public void searchText(String initial) {
        if(disposed||pending!=null||active==null)return;
        Key key=active;var doc=documents.get(key);int version=doc.document.contentVersion();
        if(initial==null)initial=doc.document.getSelectedText();if(initial.length()>com.fixmer.genesis.technology.scripts.ScriptSearch.MAX_QUERY||initial.contains("\n"))initial="";
        overlays.push(palette(new ScriptSearchOverlay(tr("search_text")+" · "+key.file,List.of(),key.file,doc.currentText(),initial,accent(),hit->{
            if(disposed||pending!=null||!key.equals(active)||doc.document.contentVersion()!=version){report(tr("search_changed"));return;}
            doc.document.clearSelection();doc.document.setCursor(hit.line(),hit.column());
            doc.document.startSelection(hit.line(),hit.column());doc.document.setCursor(hit.line(),hit.column()+hit.length());
            doc.document.extendSelection(doc.document.cursorLine(),doc.document.cursorCol());
            doc.view.resetBlink();doc.view.ensureCursorVisible(editor.getWidth(),editor.getHeight(),doc.document,net.minecraft.client.Minecraft.getInstance().font);requestFocus();
        }),1));
    }
    public void fileMenu(int x,int y){
        boolean manage=canManageFiles();String target=filesPane.selectedEntry;
        overlays.push(new ContextMenuOverlay(x,y,List.of(
                item("new_file",manage,()->createHere(false)),
                item("new_folder",canCreateFolder(),()->createHere(true)),
                item("new_java_type",manage,()->newJavaTypeMenu(x,y)),
                ContextMenuEntry.divider(),
                item("save",!readOnly&&active!=null,this::save),
                item("save_all",!readOnly&&project!=null,this::saveAll),
                item("close",!readOnly&&active!=null,this::closeDocument),
                ContextMenuEntry.divider(),
                item("copy_entry",manage&&!sharedActive132()&&!target.isEmpty(),this::copySelected),
                item("paste_entry",canPasteEntry(),this::pasteEntry),
                item("rename",manage&&!target.isEmpty(),this::renameSelected),
                item("delete",manage&&!target.isEmpty(),this::deleteSelected),
                item("restore",manage&&lastTrash!=null&&project.id().equals(lastTrashProject),this::restoreLastDeleted),
                item("copy_path",project!=null&&!target.isEmpty(),()->net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(target)),
                item("copy_source",active!=null,()->net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(document().getValue())),
                ContextMenuEntry.divider(),
                ContextMenuEntry.of(tr("split.toggle"),this::toggleSplit),
                item("split.open_right",project!=null&&files.contains(target)&&com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(target),()->openRight(target)),
                item("assist.tools",active!=null,()->codeToolsMenu(x,y))
        )).category(accent()));
    }
    public void runMenu(int x,int y){if(contentProject145()){contentMenu145(x,y,false);return;}if(commandsMode133){overlays.push(new ContextMenuOverlay(x,y,List.of(item("launch_file",canLaunchPrimary(),this::launchPrimary),item("stop_java",commandRuns133.canStop(),this::stopPrimary),item("check",active!=null,this::checkCommands133),item("copy_output",true,()->commandRuns133.toggleAllLogs()))).category(accent()));return;}
        overlays.push(new ContextMenuOverlay(x,y,List.of(item("remote_test152",hasBuildReport()&&com.fixmer.mared.technology.server.setup.RemoteTestScreen152.available(),()->artifactMenu(x,y)),item(importedGradle?"mod_build_apply":"server_apply",canApplyModule(),this::applyServerModule),item("set99.apply_all",canApplyNativeMod(),this::reviewBuiltMods99),item("set99.remove",canApplyNativeMod(),this::removeManagedMod99),item(watchApply?"watch_on":"watch_off",importedGradle&&!readOnly&&pending==null&&!javaRunning(),this::toggleWatchApply),item(BackgroundAutoApply.selected(storage.root(),project==null?"":project.id())?"background.on":"background.off",importedGradle&&!readOnly&&pending==null&&!javaRunning(),this::toggleBackgroundApply),item("background.report",true,this::backgroundReport),item("background.pause",true,()->BackgroundAutoApply.pause()),item("background.resume",true,()->BackgroundAutoApply.resume()),item("background.retry",true,()->BackgroundAutoApply.retry()),item("background.stop",true,()->BackgroundAutoApply.stop()),item("dev_restart_client",NativeWorldTarget.capture()!=null&&!readOnly&&pending==null&&!javaRunning(),this::restartClientFromMenu),item("mod_export_tested",project!=null&&importedGradle&&pending==null&&!javaRunning()&&!readOnly,this::exportTestedMod),item("server_stop",canManageModule(false),()->manageServerModule(false)),item("server_rollback",canManageModule(true),()->manageServerModule(true)),item("server_status",project!=null&&(importedGradle||ServerProjectApplies.available()),this::showServerStatus),item("server_api_export",!readOnly&&project!=null&&pending==null&&!javaRunning(),this::exportServerApi),ContextMenuEntry.divider(),item("run_java",canRunJava(),this::runJava),(canBuildGradle()?ContextMenuEntry.of(tr("build_gradle")+" · "+gradleTask,this::buildGradle):ContextMenuEntry.disabled(tr("build_gradle")+" · "+gradleTask)),item("build_report",hasBuildReport(),()->buildReportMenu(x,y)),item("diagnostics",hasBuildReport(),()->diagnosticsMenu(x,y)),item("artifact_deliver_menu",hasBuildReport(),()->artifactMenu(x,y)),item("stop_java",nativeUiLocked||!readOnly&&javaRunning(),this::stopJava),ContextMenuEntry.divider(),item("tasks_load",canBuildGradle(),this::discoverGradleTasks),item("tasks_choose",project!=null&&taskCatalogs.containsKey(project.id()),this::chooseGradleTask),item("tasks_report",project!=null&&taskReports.containsKey(project.id()),this::showTaskReport),item("gradle_task",canConfigureGradle(),this::configureGradleTask),item("dependencies",canBuildGradle(),this::manageDependencies),item("build_timeout_minutes",canConfigureGradle(),()->configureBuildOption("build_timeout_minutes")),item("build_max_workers",canConfigureGradle(),()->configureBuildOption("build_max_workers")),item("entry_point",canRunJava(),this::configureEntryPoint),item("jdk_home",!readOnly&&canConfigureBuildTools(),()->configureBuildTool("java_home")),item("wrapper_jar",!readOnly&&canConfigureBuildTools(),()->configureBuildTool("wrapper_jar")),item("check_build_tools",!readOnly&&canConfigureBuildTools(),this::checkBuildTools),ContextMenuEntry.divider(),item("libraries_path",hasJavaProject(),this::copyLibrariesPath),item("resources_path",hasJavaProject(),this::copyResourcesPath),item("copy_output",hasRunOutput(),this::copyRunOutput))).category(accent()));
    }
    public void copyProjectPath(){if(sharedActive132()){net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(shared132.context132().get("title"));return;}if(project!=null){net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard((watchedDirectory==null?storage.root().resolve(project.id()):watchedDirectory).toString());message=tr("project_path_copied");}}
    public void copySelected(){
        if(sharedActive132())return;
        if(!canManageFiles()||filesPane.selectedEntry.isEmpty())return;
        if(entryBaselines(filesPane.selectedEntry)==null)return;copyProject=project.id();copyPath=filesPane.selectedEntry;message=tr("copy_ready")+" · "+copyPath;
    }
    public boolean canPasteEntry(){return !sharedActive132()&&canManageFiles()&&!copyPath.isEmpty()&&project.id().equals(copyProject);}
    public void pasteEntry(){
        if(!canPasteEntry())return;Project selected=project;String from=copyPath,parent=filesPane.newFileParent(),leaf=RepositoryPath.leaf(from);boolean directory=filesPane.tree.directories().contains(from);
        String suggested;if(directory)suggested=leaf+"_copy";else{int dot=leaf.lastIndexOf('.');suggested=dot>0?leaf.substring(0,dot)+"_copy"+leaf.substring(dot):dot==0?leaf.substring(1)+"_copy.txt":leaf+"_copy";}
        overlays.push(new NameDialogOverlay(tr("paste_entry")+" · /"+parent,name->{
            if(!canPasteEntry()||!project.id().equals(selected.id()))return;var baseline=entryBaselines(from);if(baseline==null)return;String to=name.contains("/")?name:com.fixmer.genesis.technology.storage.ProjectPath.child(parent,name);
            importCancellation.set(false);submit(()->{storage.copyEntry(selected.id(),from,to,baseline,importCancellation::get);return projectFiles(selected);},result->{applyProjectFiles((ProjectFiles)result);filesPane.selectedEntry=to;filesPane.view.selectedFolder=directory?to:RepositoryPath.parent(to);filesPane.view.expanded.addAll(ancestorsOf(to));filesPane.rebuild();for(int i=0;i<filesPane.rows.size();i++)if(filesPane.rows.get(i).path().equals(to)){filesPane.selectRow(i);break;}message=tr("pasted");});
        },accent(),false,name->com.fixmer.genesis.technology.storage.ProjectPath.valid(name)?null:tr("bad_file")).customInput().maxLength(256).initialValue(suggested));
    }
    /** Explorer creation asks for a name only; the clicked folder owns the destination. */
    private void createHere(boolean directory) {
        if(sharedActive132()){if(!directory)sharedNew132();return;}
        if(!canManageFiles())return;
        Project selected=project;
        String parent=filesPane.newFileParent();
        String title=tr(directory?"new_folder":"new_file")+" · "+selected.id()+":/"+(parent.isEmpty()?"":parent+"/");
        overlays.push(new NameDialogOverlay(title,name->{
            if(!canManageFiles()||!project.id().equals(selected.id()))return;
            String path=com.fixmer.genesis.technology.storage.ProjectPath.child(parent,name);
            submit(()->{
                if(directory)storage.createDirectory(selected.id(),path);
                else storage.createFile(selected.id(),path,!commandsMode133&&preferences.scriptsJavaSnippet?com.fixmer.genesis.technology.scripts.JavaFileTemplate.source(path):"");
                return projectFiles(selected);
            },result->{
                applyProjectFiles((ProjectFiles)result);
                filesPane.openedProjects.add(selected.id());
                filesPane.view.expanded.addAll(ancestorsOf(path));
                if(directory) {
                    filesPane.view.expanded.add(path);
                    filesPane.view.selectedFolder=path;
                    filesPane.selectedEntry=path;
                    filesPane.rebuild();
                    filesPane.ensureVisible(filesPane.position(selected.id(),path));
                    message=tr("folder_created");
                } else open(path);
            });
        },accent(),false,name->{
            if(!com.fixmer.genesis.technology.storage.ProjectPath.valid(name)||name.contains("/"))return tr("bad_file");
            String path=com.fixmer.genesis.technology.storage.ProjectPath.child(parent,name);
            return com.fixmer.genesis.technology.storage.ProjectPath.valid(path)&&(directory||com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(path))?null:tr("bad_file");
        }).customInput().maxLength(256));
    }
    public void newFolder(){
        if(sharedActive132())return;
        if(!canManageFiles())return;String id=project.id(),parent=filesPane.newFileParent();
        overlays.push(new NameDialogOverlay(tr("new_folder")+" · "+(parent.isEmpty()?tr("project_root"):parent),name->{
            if(!canManageFiles()||!project.id().equals(id))return;String path=name.contains("/")?name:com.fixmer.genesis.technology.storage.ProjectPath.child(parent,name);
            submit(()->{storage.createDirectory(id,path);return projectFiles(project);},result->{applyProjectFiles((ProjectFiles)result);filesPane.view.expanded.addAll(ancestorsOf(path));filesPane.view.expanded.add(path);filesPane.view.selectedFolder=path;filesPane.selectedEntry=path;filesPane.rebuild();message=tr("folder_created");});
        },accent(),false,name->com.fixmer.genesis.technology.storage.ProjectPath.valid(name)?null:tr("bad_project")).customInput().maxLength(256));
    }
    private List<String> ancestorsOf(String path){var list=new ArrayList<String>();String parent=RepositoryPath.parent(path);while(!parent.isEmpty()){list.add(parent);parent=RepositoryPath.parent(parent);}return list;}
    private Map<String,String> entryBaselines(String path){
        var baseline=new HashMap<String,String>();for(var e:documents.entrySet())if(e.getKey().project.equals(project.id())&&(e.getKey().file.equals(path)||e.getKey().file.startsWith(path+"/"))){if(e.getValue().dirty){report(tr("save_before_manage"));return null;}baseline.put(e.getKey().file,e.getValue().lastSavedText);}return baseline;
    }
    public void renameSelected(){
        if(sharedActive132()){sharedRename132();return;}
        if(!canManageFiles()||filesPane.selectedEntry.isEmpty())return;String id=project.id(),from=filesPane.selectedEntry,parent=RepositoryPath.parent(from);
        overlays.push(new NameDialogOverlay(tr("rename")+" · "+from,name->{
            if(!canManageFiles()||!project.id().equals(id))return;var baseline=entryBaselines(from);if(baseline==null)return;String to=name.contains("/")?name:com.fixmer.genesis.technology.storage.ProjectPath.child(parent,name);
            submit(()->{storage.renameEntry(id,from,to,baseline);workerWrites.keySet().removeIf(key->key.project.equals(id)&&(key.file.equals(from)||key.file.startsWith(from+"/")));try{return projectFiles(project);}catch(java.io.IOException error){return null;}},result->{
                var moved=new LinkedHashMap<Key,DocumentSession>();for(var entry:new ArrayList<>(documents.entrySet())){Key key=entry.getKey();if(key.project.equals(id)&&(key.file.equals(from)||key.file.startsWith(from+"/"))){Key next=new Key(id,to+key.file.substring(from.length()));documents.remove(key);checkpointed.remove(key);entry.getValue().storageId=next.file;moved.put(next,entry.getValue());if(key.equals(active))active=next;panes.rename(key,next);}}documents.putAll(moved);if(result!=null)applyProjectFiles((ProjectFiles)result);filesPane.selectedEntry=to;filesPane.view.selectedFolder=filesPane.tree.directories().contains(to)?to:RepositoryPath.parent(to);if(active!=null)activate(active);message=tr(result==null?"renamed_refresh":"renamed");
            });
        },accent(),false,name->com.fixmer.genesis.technology.storage.ProjectPath.valid(name)?null:tr("bad_project")).customInput().acceptLabel("mared.dialog.rename").maxLength(256).initialValue(RepositoryPath.leaf(from)));
    }
    public void deleteSelected(){
        if(sharedActive132()){sharedDelete132();return;}
        if(!canManageFiles()||filesPane.selectedEntry.isEmpty())return;String id=project.id(),path=filesPane.selectedEntry;
        overlays.push(new ConfirmDialogOverlay(tr("delete"),tr("delete_notice")+"\n"+path,()->{
            if(!canManageFiles()||!project.id().equals(id))return;var baseline=entryBaselines(path);if(baseline==null)return;
            submit(()->{var trash=storage.trashEntry(id,path,baseline);workerWrites.keySet().removeIf(key->key.project.equals(id)&&(key.file.equals(path)||key.file.startsWith(path+"/")));ProjectFiles snapshot=null;try{snapshot=projectFiles(project);}catch(java.io.IOException error){}return new Object[]{snapshot,trash};},result->{
                for(Key key:new ArrayList<>(documents.keySet()))if(key.project.equals(id)&&(key.file.equals(path)||key.file.startsWith(path+"/"))){documents.remove(key);checkpointed.remove(key);if(key.equals(active)){active=null;editor.attachDocument(null,null,null);editor.setEditable(false);}}
                Object[] data=(Object[])result;lastTrash=(java.nio.file.Path)data[1];lastTrashProject=id;if(data[0]!=null)applyProjectFiles((ProjectFiles)data[0]);filesPane.selectedEntry="";message=tr(data[0]==null?"trashed_refresh":"trashed")+" · "+data[1];appendLog(message);
            });
        },true,accent(),true,"mared.dialog.delete"));
    }
    public void restoreLastDeleted(){
        if(sharedActive132()){shared132.manage132(net.minecraft.client.Minecraft.getInstance().screen,2);return;}
        if(!canManageFiles()||lastTrash==null||!project.id().equals(lastTrashProject))return;java.nio.file.Path trash=lastTrash;Project selected=project;
        submit(()->{storage.restoreTrash(trash);try{return projectFiles(selected);}catch(java.io.IOException error){return null;}},result->{lastTrash=null;if(result!=null)applyProjectFiles((ProjectFiles)result);message=tr(result==null?"restored_refresh":"restored");});
    }
    private static String newFilePath(String name,String parent,boolean imported){
        if(name.contains("/")&&com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(name)||SourceProjects.validFile(name))return name;
        return parent.isEmpty()?name:parent+"/"+name;
    }
    private void newJavaTypeMenu(int x,int y){if(contentProject145()){newFile();return;}if(commandsMode133){newFile();return;}if(sharedActive132()){sharedNew132();return;}
        if(!canManageFiles())return;var entries=new ArrayList<ContextMenuEntry>();
        for(var kind:com.fixmer.genesis.technology.scripts.JavaFileTemplate.Kind.values())entries.add(ContextMenuEntry.of(kind.name().toLowerCase(java.util.Locale.ROOT),()->newJavaType(kind)));
        overlays.push(new ContextMenuOverlay(x,y,entries).category(accent()));
    }
    private void newJavaType(com.fixmer.genesis.technology.scripts.JavaFileTemplate.Kind kind){
        if(!canManageFiles())return;Project selected=project;boolean imported=importedGradle;
        String chosenParent=filesPane.newFileParent();
        final String parent=chosenParent.isEmpty()||imported&&!(chosenParent.equals("src/main/java")||chosenParent.startsWith("src/main/java/")||chosenParent.equals("src/test/java")||chosenParent.startsWith("src/test/java/"))?(imported?"src/main/java":"src"):chosenParent;
        overlays.push(new NameDialogOverlay(tr("new_java_type")+" · "+kind.name().toLowerCase(java.util.Locale.ROOT)+" · "+parent,name->{
            if(!canManageFiles()||!project.id().equals(selected.id()))return;
            String path=javaTypePath(name,parent);
            submit(()->{storage.createFile(selected.id(),path,com.fixmer.genesis.technology.scripts.JavaFileTemplate.source(path,kind));return projectFiles(selected);},result->{applyProjectFiles((ProjectFiles)result);open(path);});
        },accent(),false,name->{String path=javaTypePath(name,parent);return RepositoryPath.valid(path)&&(!imported||path.startsWith("src/main/java/")||path.startsWith("src/test/java/"))&&com.fixmer.genesis.technology.scripts.JavaFileTemplate.validTypePath(path)?null:tr("bad_java_type");}).customInput().inputLabel("mared.dialog.relative_path_label").acceptLabel("mared.dialog.create").maxLength(RepositoryPath.MAX_LENGTH));
    }
    private static String javaTypePath(String name,String parent){String value=name.endsWith(".java")?name:name+".java";return value.startsWith("src/")||value.startsWith("tests/")?value:parent+"/"+value;}
    public void newFile(){
        if(sharedActive132()){sharedNew132();return;}
        if(readOnly||disposed||pending!=null)return;if(project==null){newProject();return;}Project p=project;String parent=filesPane.newFileParent();boolean imported=importedGradle;
        overlays.push(new NameDialogOverlay(tr("new_file")+" · "+parent+"/file."+(commandsMode133?"mcfunction":p.language().extension),name->{String path=newFilePath(commandsMode133&&!name.contains(".")?name+".mcfunction":name,parent,imported);
            submit(()->{storage.createFile(p.id(),path,!commandsMode133&&preferences.scriptsJavaSnippet?com.fixmer.genesis.technology.scripts.JavaFileTemplate.source(path):"");return projectFiles(p);},result->{applyProjectFiles((ProjectFiles)result);open(path);});
        },accent(),false,name->com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(newFilePath(commandsMode133&&!name.contains(".")?name+".mcfunction":name,parent,imported))?null:tr("bad_file")).customInput().acceptLabel("mared.dialog.create").maxLength(RepositoryPath.MAX_LENGTH));
    }
    public void save(){
        if(sharedActive132()){shared132.flush132();return;}
        if(readOnly||active==null||pending!=null||disposed)return;Key key=active;if((builds.busy()||buildPreparing)&&key.project.equals(buildProject)){report(tr("save_during_build"));return;}var d=documents.get(key);String before=d.lastSavedText,text=d.currentText();
        submit(()->{storage.save(key.project,key.file,before,text);workerWrites.put(key,new Recovery(before,text));return text;},result->{if(!text.equals(before))invalidateBuild(key.project);d.lastSavedText=text;d.recomputeDirty();
            checkpointed.put(key,null);changedAt=0;retryAfter=0;checkpointRequested=true;message=tr("saved");});
    }
    public void closeDocument(){
        if(sharedActive132()){if(active!=null){shared132.flush132();documents.remove(active);active=null;editor.attachDocument(null,null,null);}return;}
        if(readOnly||active==null||pending!=null)return;Key key=active;var d=documents.get(key);Snapshot s=new Snapshot(key,d.lastSavedText,d.currentText());
        submit(()->{checkpoint(List.of(s));return null;},result->{documents.remove(key);checkpointed.remove(key);active=null;
            editor.attachDocument(null,null,null);editor.setEditable(false);message=tr("closed");});
    }
    @Override public void dispose(){
        if(disposed)return;commandRuns133.close();if(shared132!=null){shared132.flush132();shared132.releaseViews132();}disposed=true;sourceWatcher.close();importCancellation.set(true);com.fixmer.mared.technology.minecraft.NativeModWorkflow.cancel(stagedDeploymentToken);stagedDeploymentToken="";runs.close();applies.close();builds.close();rememberRight();com.fixmer.mared.MaredSettings.save();leftEditor.setFocused(false);rightEditor.setFocused(false);
        var snapshots=snapshots();var context=workspaceLoaded?workspaceSnapshot():null;
        var closing=new CompletableFuture<Void>();
        // Background builds cannot resume until recovery and workspace writes have completed.
        var knownProjects=documents.keySet().stream().map(k->k.project).collect(java.util.stream.Collectors.toSet());
        var dirtyProjects=documents.entrySet().stream().filter(e->e.getValue().dirty).map(e->e.getKey().project).collect(java.util.stream.Collectors.toSet());
        BackgroundAutoApply.editorClosed(this,closing,storage.root(),knownProjects,dirtyProjects);
        if(!snapshots.isEmpty()||context!=null){closingWorkspaces.put(storage.root(),closing);
            try{worker.execute(()->{
                Exception failure=null;
                try{if(!snapshots.isEmpty())checkpoint(snapshots);}catch(Exception error){failure=error;com.fixmer.mared.Mared.LOGGER.warn("[Scripts] recovery failed",error);}
                try{if(context!=null)new com.fixmer.genesis.technology.scripts.EditorWorkspaceState(storage.root()).save(context);}
                catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);com.fixmer.mared.Mared.LOGGER.warn("[Scripts] workspace context save failed",error);}
                if(failure==null)closing.complete(null);else closing.completeExceptionally(failure);
                closingWorkspaces.remove(storage.root(),closing);
            });}catch(RejectedExecutionException error){closing.completeExceptionally(error);closingWorkspaces.remove(storage.root(),closing);logger.accept("[Scripts] recovery/context queue unavailable");}
        }else closing.complete(null);
        worker.shutdown();
    }
    @Override public boolean focusable(){return true;}
    @Override public void onFocusGained(){leftEditor.setFocused(editor==leftEditor);rightEditor.setFocused(splitVisible&&editor==rightEditor);}
    @Override public void onFocusLost(){leftEditor.setFocused(false);rightEditor.setFocused(false);draggingDivider=false;}
    @Override public void layout(MaredBounds b){super.layout(b);syncPanes();}
    @Override protected void safeRender(MaredRenderContext ctx){
        syncPanes();leftEditor.setAccentColor(accent());rightEditor.setAccentColor(accent());if(active!=null){var syntaxDocument=documents.get(active);if(syntaxDocument!=null)configureSyntax(active,syntaxDocument);}var g=ctx.graphics();var font=ctx.font();
        var hits=new ArrayList<TabHit>();int tx=bounds.x()+4;
        var visibleTabs=new ArrayList<Key>();if(project!=null)for(Key key:documents.keySet())if(key.project.equals(project.id())&&(!sharedActive132()||files.contains(key.file)))visibleTabs.add(key);
        if(revealTab&&active!=null){int ai=visibleTabs.indexOf(active);if(ai>=0){tabOffset=ai;int used=0;for(int i=ai;i>=0;i--){Key candidate=visibleTabs.get(i);var d=documents.get(candidate);int tw=Math.min(150,Math.max(45,font.width(RepositoryPath.leaf(candidate.file)+(d.dirty?" *":""))+22))+2;used+=tw;if(used>bounds.width()-8)break;tabOffset=i;}}revealTab=false;}
        tabOffset=Math.max(0,Math.min(tabOffset,Math.max(0,visibleTabs.size()-1)));
        for(int i=tabOffset;i<visibleTabs.size();i++){Key key=visibleTabs.get(i);var doc=documents.get(key);String label=RepositoryPath.leaf(key.file)+(doc.dirty?" *":"");int tw=Math.min(150,Math.max(45,font.width(label)+22));if(tx+tw>bounds.right()-4)break;
            boolean chosen=key.equals(active);if(chosen)g.fill(tx,bounds.y()+1,tx+tw,bounds.y()+17,raised());
            g.drawString(font,font.plainSubstrByWidth(label,tw-20),tx+4,bounds.y()+4,chosen?text():dim(),false);
            if(!readOnly)g.drawString(font,"×",tx+tw-12,bounds.y()+4,dim(),false);hits.add(new TabHit(key,tx,tw));tx+=tw+2;
        }tabHits=List.copyOf(hits);
        if(visibleTabs.isEmpty())g.drawString(font,font.plainSubstrByWidth(tr("empty"),Math.max(0,bounds.width()-8)),bounds.x()+4,bounds.y()+4,dim(),false);
        String status=(readOnly?tr("read_only")+" · ":"")+(active==null?projectName():active.file)+(message.isEmpty()?"":" · "+message);
        if(splitVisible){
            for(int index=0;index<2;index++){
                Key key=index==0?panes.left():panes.right();var widget=index==0?leftEditor:rightEditor;
                String label=(index+1)+" · "+(key==null?tr("split.choose"):key.file+(documents.get(key).dirty?" *":""));
                g.drawString(font,font.plainSubstrByWidth(label,Math.max(0,widget.getWidth()-8)),widget.getX()+2,bounds.y()+19,widget==editor?accent():dim(),false);
            }
        }else g.drawString(font,font.plainSubstrByWidth(status,Math.max(0,bounds.width()-8)),bounds.x()+4,bounds.y()+19,dim(),false);
        startHits=List.of();
        if(splitVisible){
            if(panes.left()!=null)leftEditor.render(g,ctx.mouseX(),ctx.mouseY(),0);
            if(panes.right()!=null)rightEditor.render(g,ctx.mouseX(),ctx.mouseY(),0);
            g.fill(dividerX,bounds.y()+34,dividerX+5,bounds.bottom()-2,raised());
            var selected=panes.rightFocused()?rightEditor:leftEditor;
            g.fill(selected.getX(),bounds.y()+32,selected.getX()+selected.getWidth(),bounds.y()+33,accent());
            if(panes.right()==null)g.drawString(font,font.plainSubstrByWidth(tr("split.empty"),Math.max(0,rightEditor.getWidth()-8)),rightEditor.getX()+4,rightEditor.getY()+4,dim(),false);
            if(panes.left()==null)g.drawString(font,font.plainSubstrByWidth(tr("split.choose"),Math.max(0,leftEditor.getWidth()-8)),leftEditor.getX()+4,leftEditor.getY()+4,dim(),false);
        }else if(active!=null)editor.render(g,ctx.mouseX(),ctx.mouseY(),0);else renderStart(ctx);
        if(sharedActive132())shared132.renderPeers132(g);
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(!contains(x,y))return false;requestFocus();
        if(splitVisible&&y>=bounds.y()+34){
            if(button==0&&x>=dividerX&&x<dividerX+5){draggingDivider=true;return true;}
            focusPane(x>=dividerX+5);
        }
        if(y<bounds.y()+18){for(TabHit tab:tabHits)if(x>=tab.x&&x<tab.x+tab.width){if(button==0&&net.minecraft.client.gui.screens.Screen.hasAltDown()){openRight(tab.key.file);return true;}activate(tab.key);if(button==0&&!readOnly&&x>=tab.x+tab.width-16)closeDocument();else if(button==1)fileMenu((int)x,(int)y);return true;}return true;}
        if(active==null){if(splitVisible){if(button==0)choosePaneDocument((int)x,(int)y);return true;}if(button==0)for(StartHit hit:startHits)if(hit.bounds.contains(x,y)){hit.action.run();break;}return true;}
        editor.mouseClicked(x,y,button);return true;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(draggingDivider&&button==0){com.fixmer.mared.MaredSettings.setLayoutSplitRatio((float)((x-bounds.x()-2)/Math.max(1,bounds.width()-9)));syncPanes();return true;}
        return active!=null&&editor.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(draggingDivider){draggingDivider=false;return true;}return active!=null&&editor.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(contains(x,y)&&active==null&&y>=bounds.y()+34){startScroll=Math.max(0,Math.min(startScroll+(dy<0?24:dy>0?-24:0),startMaxScroll));return true;}if(contains(x,y)&&y<bounds.y()+18){tabOffset+=dy<0?1:dy>0?-1:0;return true;}if(splitVisible&&contains(x,y)&&y>=bounds.y()+34){var target=x>=dividerX+5?rightEditor:leftEditor;Key key=target==rightEditor?panes.right():panes.left();return key!=null&&target.mouseScrolled(x,y,dx,dy);}return contains(x,y)&&active!=null&&editor.mouseScrolled(x,y,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!isFocused())return false;
        if((mods&2)!=0&&key==92){toggleSplit();return true;}
        if((mods&2)!=0&&key==49){focusPane(false);return true;}
        if((mods&2)!=0&&key==50&&splitVisible){focusPane(true);return true;}
        if(active==null)return false;
        boolean ctrl=(mods&2)!=0,shift=(mods&1)!=0;
        if(ctrl&&key==74){if(shift)codeToolsMenu(bounds.x()+12,bounds.y()+42);else snippetsMenu(bounds.x()+12,bounds.y()+42);return true;}
        if(ctrl&&key==72){replaceOptionsMenu(bounds.x()+12,bounds.y()+42);return true;}
        if(ctrl&&key==77){bracketAction(shift);return true;}
        if(ctrl&&shift&&key==75){deleteEditorLines();return true;}
        if(key==256&&snippetSessions.remove(active)!=null){message="";return true;}
        if(key==258&&!ctrl&&(mods&4)==0&&canAssistEdit()){
            var session=snippetSessions.get(active);
            if(session!=null){var doc=documents.get(active);boolean moved=session.advance(doc.document,shift);if(session.finished()){snippetSessions.remove(active);message="";}if(moved){doc.view.resetBlink();doc.view.ensureCursorVisible(editor.getWidth(),editor.getHeight(),doc.document,net.minecraft.client.Minecraft.getInstance().font);return true;}}
        }
        if((mods&2)!=0&&key==47&&!readOnly&&editor.isEditable()){
            String prefix=commandsMode133?(active.file.endsWith(".mcfunction")?"#":"//"):com.fixmer.mared.gui2.framework.components.editor.EditorLineActions.commentPrefix(active.file);
            if(prefix.isEmpty())return false;
            var doc=documents.get(active);
            if(com.fixmer.mared.gui2.framework.components.editor.EditorLineActions.apply(doc.document,doc.history,com.fixmer.mared.gui2.framework.components.editor.EditorLineActions.Action.COMMENT,"",prefix)){doc.view.resetBlink();changed();}
            return true;
        }
        return editor.keyPressed(key,scan,mods);
    }
    @Override public boolean charTyped(char c,int mods){return !readOnly&&isFocused()&&active!=null&&editor.charTyped(c,mods);}
    private final class FilesPane extends MaredComponent {
        private static final int ROW_HEIGHT=20,MAX_VIEW_STATES=64,MAX_PROJECT_TREES=16;
        private record ExplorerRow(String project,String path,String label,int depth,boolean directory,boolean root) {}
        private final class ViewState {final Set<String> expanded=new HashSet<>();String selectedFolder="",selectedEntry="";int scroll;}
        private final LinkedHashMap<String,ViewState> views=new LinkedHashMap<>();
        private final LinkedHashMap<String,ProjectFileTree> snapshots=new LinkedHashMap<>();
        private final Set<String> openedProjects=new LinkedHashSet<>();
        private ViewState view=new ViewState();
        private ProjectFileTree tree=ProjectFileTree.of(List.of());
        private List<ProjectFileTree.Row> rows=List.of();
        private List<ExplorerRow> displayRows=List.of();
        private int scroll;
        private String selectedEntry="";
        private int count(){return displayRows.size()+1;}
        private int visibleRows(){return Math.max(1,Math.max(0,bounds.height()-18)/ROW_HEIGHT);}
        private void clampScroll(){scroll=Math.max(0,Math.min(scroll,Math.max(0,count()-visibleRows())));view.scroll=scroll;}
        private void prune(){var ids=new HashSet<String>();for(Project p:projects)ids.add(p.id());snapshots.keySet().retainAll(ids);openedProjects.retainAll(ids);rebuild();}
        private void rebuild(){
            rows=filtered145(tree).rows(view.expanded);var display=new ArrayList<ExplorerRow>();
            for(Project p:projects){display.add(new ExplorerRow(p.id(),"",p.id(),0,true,true));ProjectFileTree cached=snapshots.get(p.id());ViewState state=views.get(p.id());
                if(openedProjects.contains(p.id())&&cached!=null&&state!=null)for(var row:filtered145(cached).rows(state.expanded))display.add(new ExplorerRow(p.id(),row.path(),row.label(),row.depth()+1,row.directory(),false));
            }displayRows=List.copyOf(display);clampScroll();
        }
        private void update(String id,ProjectFileTree snapshot){
            view.scroll=scroll;view.selectedEntry=selectedEntry;
            if(id.isEmpty()){selectedEntry="";view=new ViewState();tree=snapshot;rebuild();return;}
            ViewState next=views.get(id);if(next==null){next=new ViewState();for(String folder:snapshot.directories())if(RepositoryPath.parent(folder).isEmpty())next.expanded.add(folder);}
            views.remove(id);views.put(id,next);
            if(!snapshots.containsKey(id))openedProjects.add(id);snapshots.remove(id);snapshots.put(id,snapshot);
            while(snapshots.size()>MAX_PROJECT_TREES){String oldest=snapshots.keySet().iterator().next();snapshots.remove(oldest);openedProjects.remove(oldest);}
            while(views.size()>MAX_VIEW_STATES){String oldest=views.keySet().iterator().next();views.remove(oldest);snapshots.remove(oldest);openedProjects.remove(oldest);}
            view=next;selectedEntry=next.selectedEntry;tree=snapshot;view.expanded.retainAll(tree.directories());
            if(!tree.directories().contains(view.selectedFolder))view.selectedFolder="";if(!tree.containsFile(selectedEntry)&&!tree.directories().contains(selectedEntry))selectedEntry="";rebuild();
        }
        private String newFileParent(){return view.selectedFolder;}
        private void setExpanded(boolean expanded){view.expanded.clear();if(expanded){view.expanded.addAll(tree.directories());if(project!=null)openedProjects.add(project.id());}rebuild();}
        private int position(String id,String path){for(int i=0;i<displayRows.size();i++){var row=displayRows.get(i);if(row.project.equals(id)&&row.path.equals(path))return i+1;}return -1;}
        private void ensureVisible(int position){if(position<0)return;if(position<scroll)scroll=position;else if(position>=scroll+visibleRows())scroll=position-visibleRows()+1;clampScroll();}
        private void reveal(Key key){
            if(project==null||!project.id().equals(key.project)||!tree.containsFile(key.file))return;selectedEntry=key.file;openedProjects.add(key.project);
            view.expanded.addAll(tree.ancestors(key.file));view.selectedFolder=RepositoryPath.parent(key.file);rebuild();ensureVisible(position(key.project,key.file));
        }
        @Override protected void safeRender(MaredRenderContext ctx){
            clampScroll();var f=ctx.font();var g=ctx.graphics();String hoverPath133="";java.util.List<com.fixmer.genesis.technology.workspace.live122.WorkspaceActivity152.Person> hoverPeople152=java.util.List.of();g.enableScissor(bounds.x(),bounds.y(),bounds.right(),Math.max(bounds.y(),bounds.bottom()-15));
            try{for(int position=scroll,end=Math.min(count(),scroll+visibleRows());position<end;position++){
                int y=bounds.y()+3+(position-scroll)*ROW_HEIGHT;if(position==0){g.drawString(f,tr("projects"),bounds.x()+4,y,dim(),false);continue;}
                var row=displayRows.get(position-1);boolean current=project!=null&&project.id().equals(row.project);int guide=current?(accent()&0xFFFFFF)|0x88000000:dim();
                int x=bounds.x()+4+Math.min(row.depth*13,Math.max(0,bounds.width()-45));boolean selected=current&&row.path.equals(selectedEntry);
                if(selected)g.fill(bounds.x()+2,y-2,bounds.right()-2,y+17,selected());
                ViewState state=views.get(row.project);boolean expanded=row.root?openedProjects.contains(row.project):state!=null&&state.expanded.contains(row.path);
                var document=documents.get(new Key(row.project,row.path));var people152=shared132!=null&&row.project.equals(sharedId132)?shared132.people152(row.path):java.util.List.<com.fixmer.genesis.technology.workspace.live122.WorkspaceActivity152.Person>of();int dot152=people152.isEmpty()?0:10;
                String label=(shared132!=null&&row.root&&row.project.equals(sharedId132)?shared132.context132().getOrDefault("title",row.label):row.label)+(document!=null&&document.dirty?" *":"");
                if(row.directory){g.fill(x+10,y+5,x+20,y+12,row.root?text():dim());g.fill(x+10,y+3,x+15,y+6,row.root?text():dim());g.drawString(f,expanded?"−":"+",x,y+2,dim(),false);}else{g.renderOutline(x+11,y+2,8,11,dim());g.fill(x+13,y+5,x+17,y+6,dim());g.fill(x+13,y+8,x+17,y+9,dim());}
                if(dot152!=0){int rgb=0xff000000|com.fixmer.genesis.technology.workspace.live122.WorkspaceActivity152.color(people152,System.nanoTime());g.fill(x+25,y+6,x+31,y+10,rgb);g.fill(x+26,y+5,x+30,y+11,rgb);if(contains(ctx.mouseX(),ctx.mouseY())&&ctx.mouseX()>=x+23&&ctx.mouseX()<x+34&&ctx.mouseY()>=y+2&&ctx.mouseY()<y+15)hoverPeople152=people152;}
                g.drawString(f,f.plainSubstrByWidth(label,Math.max(0,bounds.right()-x-26-dot152)),x+24+dot152,y+3,selected?text():row.root?text():dim(),false);
                if(contains(ctx.mouseX(),ctx.mouseY())&&ctx.mouseY()>=y&&ctx.mouseY()<y+ROW_HEIGHT)hoverPath133=(project!=null&&row.project.equals(project.id())&&watchedDirectory!=null?watchedDirectory.toString():row.project)+(row.path.isEmpty()?"":" / "+row.path);
            }}finally{g.disableScissor();}
            if(!hoverPeople152.isEmpty())g.renderComponentTooltip(f,hoverPeople152.stream().<net.minecraft.network.chat.Component>map(person->net.minecraft.network.chat.Component.literal(person.label()+" · "+person.tool()).withStyle(style->style.withColor(person.rgb()))).toList(),ctx.mouseX(),ctx.mouseY());
            else if(!hoverPath133.isEmpty())g.renderTooltip(f,net.minecraft.network.chat.Component.literal(hoverPath133),ctx.mouseX(),ctx.mouseY());
            if(bounds.height()>=18)g.drawString(f,f.plainSubstrByWidth(tr("target_folder")+": "+projectName()+":/"+view.selectedFolder,Math.max(0,bounds.width()-8)),bounds.x()+4,bounds.bottom()-12,dim(),false);
        }
        private void select(ExplorerRow row,boolean toggle,int button,int x,int y){
            if(pending!=null||disposed)return;Project target=projects.stream().filter(p->p.id().equals(row.project)).findFirst().orElse(null);if(target==null)return;
            boolean rootOpen=row.root&&(button!=0?openedProjects.contains(row.project):toggle?!openedProjects.contains(row.project):true);
            Runnable done=()->{selectedEntry=row.path;view.selectedFolder=row.directory?row.path:RepositoryPath.parent(row.path);
                if(row.root){if(rootOpen)openedProjects.add(row.project);else openedProjects.remove(row.project);rebuild();}
                else if(row.directory){if(button==0){if(!view.expanded.remove(row.path))view.expanded.add(row.path);rebuild();}}
                else if(button==0){if(com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(row.path))open(row.path);else{active=null;editor.attachDocument(null,null,null);editor.setEditable(false);message=tr("binary_file")+" · "+row.path;}}
                ensureVisible(position(row.project,row.path));if(button==1)fileMenu(x,y);
            };
            if(project!=null&&project.id().equals(row.project)){done.run();return;}
            if(shared132!=null&&target.id().equals(sharedId132)){applyShared132();done.run();return;}submit(()->projectFiles(target),result->{applyProjectFiles((ProjectFiles)result);done.run();});
        }
        @Override public boolean mouseClicked(double x,double y,int button){
            if(!contains(x,y))return false;requestFocus();if(y<bounds.y()+3||y>=bounds.bottom()-15)return true;clampScroll();int position=(int)Math.floor((y-bounds.y()-3)/ROW_HEIGHT)+scroll;
            if(position>0&&position<=displayRows.size()){var row=displayRows.get(position-1);select(row,row.root&&x<bounds.x()+22,button,(int)x,(int)y);}
            else if(button==1&&project!=null&&pending==null&&!disposed){selectedEntry="";view.selectedFolder="";fileMenu((int)x,(int)y);}
            return true;
        }
        @Override public boolean focusable(){return true;}
        private void selectRow(int index){if(index<0){selectedEntry="";view.selectedFolder="";}else{var row=rows.get(index);selectedEntry=row.path();view.selectedFolder=row.directory()?row.path():RepositoryPath.parent(row.path());}if(project!=null){openedProjects.add(project.id());rebuild();ensureVisible(position(project.id(),selectedEntry));}}
        @Override public boolean keyPressed(int key,int scan,int mods){
            if(!isFocused()||!canBrowseFiles())return false;
            int index=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).path().equals(selectedEntry))index=i;
            if(key==264||key==265){int current=position(project.id(),selectedEntry)-1;int next=Math.max(0,Math.min(displayRows.size()-1,current+(key==264?1:-1)));if(!displayRows.isEmpty())select(displayRows.get(next),false,2,0,0);return true;}
            if((mods&2)!=0&&(key==67||key==86)){if(key==67)copySelected();else pasteEntry();return true;}
            if(key==291){renameSelected();return true;}if(key==261){deleteSelected();return true;}
            if(key==263){if(index<0){openedProjects.remove(project.id());rebuild();}if(index>=0){var row=rows.get(index);if(row.directory()&&view.expanded.remove(row.path()))rebuild();else{String parent=RepositoryPath.parent(row.path());int next=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).path().equals(parent))next=i;selectRow(next);}}return true;}
            if(key==262||key==257){if(index<0){openedProjects.add(project.id());rebuild();}if(index>=0){var row=rows.get(index);if(row.directory()){view.expanded.add(row.path());rebuild();}else if(key==257&&com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(row.path()))open(row.path());}return true;}
            return false;
        }
        @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!contains(x,y))return false;scroll+=dy<0?3:dy>0?-3:0;clampScroll();return true;}
    }
    private final class LogsPane extends MaredComponent {
        private int revision=-1;
        private final com.fixmer.mared.gui2.framework.components.console.MaredLogPanel panel=new com.fixmer.mared.gui2.framework.components.console.MaredLogPanel(true){
            @Override protected int filterVersion(){return scriptFilterRevision;}
            @Override protected List<String> filterCategories(){return List.of("editor","java","gradle","server","tools");}
            @Override protected boolean levelEnabled(com.fixmer.mared.services.logging.LogSettings.Level level){return !preferences.scriptsHiddenLevels.contains(level.name());}
            @Override protected boolean categoryEnabled(String category){return !preferences.scriptsHiddenCategories.contains(category);}
            @Override protected boolean shouldShow(com.fixmer.mared.services.logging.LogEntry entry){return levelEnabled(entry.level)&&categoryEnabled(entry.category);}
            @Override protected void toggleLevel(com.fixmer.mared.services.logging.LogSettings.Level level){if(!preferences.scriptsHiddenLevels.remove(level.name()))preferences.scriptsHiddenLevels.add(level.name());scriptFilterRevision++;}
            @Override protected void toggleCategory(String category){if(!preferences.scriptsHiddenCategories.remove(category))preferences.scriptsHiddenCategories.add(category);scriptFilterRevision++;}
            @Override public void addStructured(com.fixmer.mared.services.logging.LogSettings.Level level,String category,String message){if(level==com.fixmer.mared.services.logging.LogSettings.Level.ERROR)appendLog(message,level,"editor");}
            @Override public void exportLog(){exportLogs(snapshot());}
        };
        @Override public boolean focusable(){return true;}
        LogsPane(){panel.setCompact(true);panel.setLocalEntries(List.of(),ScriptProjectEditor.this::clearLogs);}
        private void sync(){if(commandsMode133&&!commandRuns133.status().isEmpty()){commandRuns133.update(panel);return;}if(revision!=scriptLogRevision){panel.setLocalEntries(List.copyOf(scriptLogEntries),ScriptProjectEditor.this::clearLogs);revision=scriptLogRevision;}}
        @Override protected void safeRender(MaredRenderContext ctx){sync();panel.tickAutoScroll();panel.render(ctx.graphics(),ctx.font(),bounds.x(),bounds.y(),bounds.width(),bounds.height(),(int)ctx.mouseX(),(int)ctx.mouseY());}
        @Override public boolean mouseClicked(double x,double y,int button){if(!contains(x,y))return false;sync();requestFocus();var box=panel.searchBox();if(box!=null){box.setFocused(button==0&&box.isMouseOver(x,y));if(box.isFocused())return box.mouseClicked(x,y,button);}capturePointer(button);return panel.mouseClicked(x,y,button,bounds.x(),bounds.y(),bounds.width(),bounds.height(),net.minecraft.client.Minecraft.getInstance().font);}
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(draggingDivider&&button==0){com.fixmer.mared.MaredSettings.setLayoutSplitRatio((float)((x-bounds.x()-2)/Math.max(1,bounds.width()-9)));syncPanes();return true;}
        return panel.mouseDragged(x,y,button,dx,dy,bounds.x(),bounds.y(),bounds.width(),bounds.height(),net.minecraft.client.Minecraft.getInstance().font);}
        @Override public boolean mouseReleased(double x,double y,int button){if(hasPointerCapture(button))releasePointer();return panel.mouseReleased(x,y,button);}
        @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return contains(x,y)&&panel.mouseScrolled(x,y,dy,bounds.x(),bounds.y(),bounds.width(),bounds.height());}
        @Override public void onFocusLost(){var box=panel.searchBox();if(box!=null)box.setFocused(false);}
        @Override public boolean keyPressed(int key,int scan,int mods){sync();if(!isFocused())return false;if(key==256&&panel.isSearchOpen()){panel.closeSearch();return true;}if(key==256&&panel.isSettingsOpen()){panel.closeSettings();return true;}if((mods&2)!=0&&key==70){panel.setSearchOpen(true);panel.ensureSearchBox(net.minecraft.client.Minecraft.getInstance().font,bounds.x(),bounds.y(),bounds.width()).setFocused(true);return true;}var box=panel.searchBox();if(box!=null&&box.isFocused())return box.keyPressed(key,scan,mods);if((mods&2)!=0&&key==67){panel.copySelectedOrAll();return true;}if((mods&2)!=0&&key==65){panel.selectAll();return true;}return false;}
        @Override public boolean charTyped(char c,int mods){var box=panel.searchBox();return box!=null&&box.isFocused()&&box.charTyped(c,mods);}
    }
    private void exportLogs(List<com.fixmer.mared.services.logging.LogEntry> entries){
        if(disposed||pending!=null)return;var snapshot=List.copyOf(entries);submit(()->{
            java.nio.file.Path folder=storage.root().resolveSibling("script-logs");if(java.nio.file.Files.isSymbolicLink(folder))throw new java.io.IOException("Log export folder is a symbolic link");java.nio.file.Files.createDirectories(folder);
            java.nio.file.Path output=java.nio.file.Files.createTempFile(folder,"scripts-",".log");
            try {java.nio.file.Files.writeString(output,snapshot.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n")),java.nio.charset.StandardCharsets.UTF_8);return output;}
            catch(Exception error){java.nio.file.Files.deleteIfExists(output);throw error;}
        },value->{net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(value.toString());message=tr("logs_exported")+": "+value;appendLog(message);});
    }
}
