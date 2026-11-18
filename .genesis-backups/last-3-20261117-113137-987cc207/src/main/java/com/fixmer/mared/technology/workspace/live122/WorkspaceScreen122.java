package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.genesis.technology.editor.CodeSyntaxLexer;
import com.fixmer.genesis.technology.workspace.live122.*;import com.fixmer.genesis.technology.workspace.collaboration121.*;import com.fixmer.mared.gui2.framework.screen.MaredScreen;import com.fixmer.mared.gui2.framework.components.editor.*;import com.fixmer.mared.technology.workspace.*;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.*;import net.minecraft.client.gui.screens.*;import net.minecraft.network.chat.Component;import java.util.*;import java.util.concurrent.*;
/** Real adapter for the live backend. Durable bursts remain editable while disk and network acknowledgments are pending. */
public final class WorkspaceScreen122 extends MaredScreen {
 private WorkspaceClient112 legacyBrowse;private final Screen previous;private final WorkspaceClient122 client=new WorkspaceClient122();private final WorkspaceClient122 caretClient151=new WorkspaceClient122(true);private final CaretView151 caretView151=new CaretView151(System::nanoTime);private final CaretSchedule151 caretSchedule151=new CaretSchedule151();private final WorkspaceClient122 pointerClient149=new WorkspaceClient122(true);private final PointerView149 pointerView149=new PointerView149(System::nanoTime);private final UUID presence=UUID.randomUUID();private UUID actor;private String project="",store="",active="",secondary="",notice="";private Map<String,String> meta=Map.of();private boolean split,updating,ioBusy,catalogLoaded,syncOther;private long edited,poll,sequence,cacheAt;private int left,w,color=0xA879F5,colorRevision;private EditBox name,path;private String savedName="",savedPath="";private final MaredMultiLineEditBox[] boxes=new MaredMultiLineEditBox[2];private final Draft[] displayed=new Draft[2];private final LinkedHashMap<String,Draft> drafts=new LinkedHashMap<>();
 private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"Genesis-workspace122-cache");t.setDaemon(true);return t;});
 private static final class Draft {
  final String path;final EditorDocument doc;final EditorHistory history;final EditorView view;final com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession session132;
  WorkspaceReplica122 replica;String epoch,text,peers="",stamp="",checkpointTarget148="";long checkpointVersion148=-1;boolean editable,blocked,leaseNeeded,accessBlocked,resync130;boolean canErase131=true,staging148;long capturedAt,peersAt,retryAfter;
  Draft(String path,WorkspaceReplica122 replica,String epoch){this(path,replica,epoch,new com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession());}
  Draft(String path,WorkspaceReplica122 replica,String epoch,com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession session){this.path=path;this.replica=replica;this.epoch=epoch;session132=session;session132.storageId=path;doc=session.document;history=session.history;view=session.view;text=replica.text();view.setSpanResolver(new CodeSyntaxSpanResolver(CodeSyntaxLexer.languageFor(path),false,0xFFDDDDDD));}
  String projectionSource,projectionTarget;CaretSchedule151.Projection projection;
  boolean dirty(){return !text.equals(replica.text());}
 }

 private boolean integrated132;private final Map<String,com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession> preferred132=new HashMap<>();private final Map<String,java.util.function.Consumer<com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession>> opening132=new HashMap<>();
 public WorkspaceScreen122(Screen previous){super(tr("title"));this.previous=previous;client.events130(this::event130);}public static Component tr(String key,Object... args){return Component.translatable("mared.workspace122."+key,args);}
 private Button button(String key,int x,int y,int width,Runnable fn){var button=addRenderableWidget(WorkspaceButton131.builder(tr(key),b->fn.run()).bounds(x,y,Math.max(1,width),20).build());button.setTooltip(Tooltip.create(tr(key)));String panels=meta.getOrDefault("panels131","");if(meta.containsKey("panels131")&&(key.equals("section_members")&&!Arrays.asList(panels.split(",")).contains("MEMBERS")||key.equals("section_history")&&!Arrays.asList(panels.split(",")).contains("HISTORY")))button.active=false;return button;}
 private boolean idle(){return client.connected()&&!client.busy()&&!ioBusy&&!sourceBusy148;}private boolean settled(){return drafts.values().stream().noneMatch(d->d.dirty()||d.replica.pending());}private String rev(){return meta.getOrDefault("revision","0");}
 private WorkspaceCache122 cache(){return new WorkspaceCache122(Minecraft.getInstance().gameDirectory.toPath().resolve("config/mared/workspace-drafts122"));}
 @Override protected void init(){client.attach();if(integrated132)return;if(name!=null)savedName=name.getValue();if(path!=null)savedPath=path.getValue();client.attach();w=Math.max(1,Math.min(1120,width-20));left=(width-w)/2;Arrays.fill(boxes,null);Arrays.fill(displayed,null);if(width<480||height<270){button("back",left,height-28,w,this::onClose);return;}
  int sectionWidth=(w-8)/3;
  button("section_project",left,28,sectionWidth,this::projectSection);
  button("section_members",left+sectionWidth+4,28,sectionWidth,()->viewPanel131("MEMBERS",d->minecraft.setScreen(new WorkspaceMembers122(this,d))));
  button("section_history",left+2*(sectionWidth+4),28,w-2*(sectionWidth+4),this::history);
  button("section_personal",left,54,sectionWidth,()->{if(available()&&actor!=null)minecraft.setScreen(new WorkspaceColor124(this,color,this::saveAppearance));});
  button("section_delete",left+sectionWidth+4,54,sectionWidth,this::removeWorkspace);
  button("section_advanced",left+2*(sectionWidth+4),54,w-2*(sectionWidth+4),this::advanced);
  path=new EditBox(font,left,0,w,20,tr("path"));path.setMaxLength(240);path.setValue(savedPath);
  button("error_details133",left,148,Math.min(300,w),()->minecraft.setScreen(new WorkspaceDiagnostics133(this,sourceDetail132)));
  button("back",left,height-30,w,this::onClose);
  if(project.isEmpty()&&!catalogLoaded&&!client.busy()){catalogLoaded=true;send("LIST",Map.of(),d->{if(project.isEmpty())meta=d;});}
 }
 private int sidebarScroll131;private int sidebarWidth131;private int section131=-1;private long sourcePoll;private boolean sourceError;private String sourceDetail132="";private int background130,edit130;private String initialFile130="";private final WorkspaceSchedule130 schedule130=new WorkspaceSchedule130();
 private boolean appearancePending152,appearanceBack152,appearanceError152;
 public boolean colorSaving152(){return appearancePending152;}public boolean colorError152(){return appearanceError152;}
 void backFromPersonal131(){if(appearancePending152){appearanceBack152=true;return;}if(project.isEmpty()&&previous instanceof WorkspaceHome131){client.detach();minecraft.setScreen(previous);}else minecraft.setScreen(this);}
 public void startSection131(int section){section131=section;}
 public void adopt131(Map<String,String> data){opened(data);if("true".equals(data.get("colorSaved152")))applyAppearance(Integer.parseInt(data.get("color152")));else if(actor!=null)loadAppearance(actor);}
 private void requestJoin131(){WorkspaceSocial131.call("DISCOVER131","",Map.of(),d->{var items=new ArrayList<WorkspacePickerScreen112.Item>();for(String row:d.getOrDefault("discover131","").lines().toList()){String[] p=row.split("\\|",3);if(p.length==3)items.add(new WorkspacePickerScreen112.Item(p[1]+" · "+p[2],()->WorkspaceSocial131.call("REQUEST_JOIN131",p[0],Map.of(),answer->notice=answer.getOrDefault("result","INVALID"))));}choose("request_join131",items);});}
 private void section131(){int section=section131;section131=-1;if(section>0&&section!=3&&project.isEmpty()){
  send("LIST",Map.of(),d->{var items=new ArrayList<WorkspacePickerScreen112.Item>();for(String row:d.getOrDefault("projects","").lines().toList()){String[] p=row.split("\\|",2);if(p.length==2)items.add(new WorkspacePickerScreen112.Item(p[1],()->client.send(WorkspaceClient122.request("VIEW",p[0],Map.of()),a->{if("OK".equals(a.get("result"))){opened(a);section131=section;}})));}if(items.isEmpty()){notice="NO_WORKSPACE131";projectSection();}else choose("projects",items);});return;
 }
 switch(section){case 0->projectSection();case 1->viewPanel131("MEMBERS",d->minecraft.setScreen(new WorkspaceMembers122(this,d)));case 2->history();case 3->minecraft.setScreen(new WorkspaceColor124(this,color,this::saveAppearance));case 4->removeWorkspace();case 5->advanced();default->{}}
 }
 private WorkspaceScripts129 scriptSources(){return scriptSources("COMMANDS".equals(meta.get("mode133")));}
 private WorkspaceScripts129 scriptSources(boolean commands){return new WorkspaceScripts129(
  com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.workspace(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(commands?"mared/command_projects":"mared/script_projects")),
  net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("mared/workspace-script-bindings129"));}
 private String failureCode(Exception e){com.fixmer.mared.Mared.LOGGER.error("[WorkSpace] Scripts synchronization failed",e);String code=e.getMessage();sourceError=true;sourceDetail132=WorkspaceDiagnostics133.report(e);return Set.of("SOURCE_CHANGED","SOURCE_DRAFT","TEXT_LIMIT","LIMIT","IMPORT126_PACKET_LIMIT","SOURCE_BINDING_MISSING","SOURCE_PATH","SOURCE_IO","FIRST58_PATH_ALIAS","FIRST58_PRIVATE_ACL_REQUIRED").contains(code==null?"":code)?code:"SOURCE_ERROR";}
 boolean panelAllowed131(String panel){return !meta.containsKey("panels131")||Arrays.asList(meta.get("panels131").split(",")).contains(panel);}
 private void viewPanel131(String panel,java.util.function.Consumer<Map<String,String>> action){viewSection(d->{if(!d.containsKey("panels131")||Arrays.asList(d.get("panels131").split(",")).contains(panel))action.accept(d);else notice="DENIED";});}
 private void viewSection(java.util.function.Consumer<Map<String,String>> action){if(!readyForFileAction()||project.isEmpty())return;send("VIEW",Map.of(),d->{if("OK".equals(d.get("result")))action.accept(d);});}
 private void projectSection(){
  if(!readyForFileAction())return;var items=new ArrayList<WorkspacePickerScreen112.Item>();
  items.add(new WorkspacePickerScreen112.Item(tr("share_script").getString(),this::scripts));
  items.add(new WorkspacePickerScreen112.Item(tr("projects").getString(),this::projects));items.add(new WorkspacePickerScreen112.Item(tr("inbox131").getString(),WorkspaceSocial131::openInbox));items.add(new WorkspacePickerScreen112.Item(tr("request_join131").getString(),this::requestJoin131));choose("section_project",items);
 }
 private void scripts(){
  if(!readyForFileAction())return;ioBusy=true;
  IO.execute(()->{try{var scriptProjects=scriptSources(false).projects();var commandProjects=scriptSources(true).projects();minecraft.execute(()->{ioBusy=false;var items=new ArrayList<WorkspacePickerScreen112.Item>();for(var p:scriptProjects)items.add(new WorkspacePickerScreen112.Item("Scripts / "+p.id(),()->confirm("share_script",()->shareScript(p.id(),false))));for(var p:commandProjects)items.add(new WorkspacePickerScreen112.Item("Commands / "+p.id(),()->confirm("share_script",()->shareScript(p.id(),true))));if(items.isEmpty())notice="SOURCE_EMPTY";choose("share_script",items);});}catch(Exception e){minecraft.execute(()->{ioBusy=false;notice=failureCode(e);});}});
 }
 private void shareScript(String source){shareScript(source,false);}
 private void shareScript(String source,boolean commands){
  if(!readyForFileAction())return;ioBusy=true;var storage=scriptSources(commands);
  IO.execute(()->{try{String packed=WorkspaceProject126.pack(storage.snapshot(source));String direction147=commands?"maps":storage.direction147(source);minecraft.execute(()->{ioBusy=false;send(commands?"CREATE_COMMAND133":"CREATE_SCRIPT",Map.of("source",source,"data",packed,"direction147",direction147),d->{
   if(!"OK".equals(d.get("result")))return;opened(d);ioBusy=true;UUID account=actor,room=UUID.fromString(project);String fingerprint=store;
   IO.execute(()->{String error="";try{if(!storage.bound(account,fingerprint,room)&&!WorkspaceRelease121.sha(packed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(d.get("scriptHash129")))throw new java.io.IOException("SOURCE_CHANGED");storage.bind(account,fingerprint,room,source,packed);}catch(Exception e){error=failureCode(e);}String result=error;minecraft.execute(()->{ioBusy=false;notice=result;sourceError=!result.isEmpty();if(result.isEmpty()){
     ioBusy=true;IO.execute(()->{try{var models=storage.models147(source);minecraft.execute(()->{
      var channel=new com.fixmer.mared.technology.blockbench.WorkspaceModelClient147(d,this::modelMetadata147);
      channel.seed(models).whenComplete((ignored,uploadError147)->{channel.close();ioBusy=false;if(uploadError147!=null){notice="MODEL_SYNC147";sourceError=true;sourceDetail132=WorkspaceDiagnostics133.report(uploadError147 instanceof Exception failure?failure:new Exception(uploadError147));com.fixmer.mared.Mared.LOGGER.error("Workspace model upload failed",uploadError147);return;}
       if(!client.connected())notice="DISCONNECTED";else send("VIEW",Map.of(),fresh->{if("OK".equals(fresh.get("result"))&&WorkspaceIntegrated132.tryEnter135(fresh,previous))client.detach();});
      });
     });}catch(Exception seedError147){minecraft.execute(()->{ioBusy=false;notice=failureCode(seedError147);});}});
    }});});
  });});}catch(Exception e){minecraft.execute(()->{ioBusy=false;notice=failureCode(e);});}});
 }
 private void syncSource(Runnable done){syncSource(done,()->{});}
 private boolean bindingChecked132,sourceBusy148;private String sourceRevision148="";
 private void syncSource(Runnable done,Runnable failed){
  if(!available()||project.isEmpty()||sourceBusy148)return;
  if(meta.getOrDefault("scriptHash129","").isEmpty()||!String.valueOf(actor).equals(meta.get("owner"))){done.run();return;}
  if(!settled()){notice="SYNCING130";failed.run();return;}
  if(!bindingChecked132){
   send("SOURCE_BASE132",Map.of(),base->{if(!"OK".equals(base.get("result"))){failed.run();return;}var storage=scriptSources();UUID account=actor,room=UUID.fromString(project);String fingerprint=store,source=base.get("source132");sourceBusy148=true;
    sourceIo148(()->{String error="";try{if(!storage.bound(account,fingerprint,room))storage.bind(account,fingerprint,room,source,base.get("data"));}catch(Exception e){error=failureCode(e);}String result=error;minecraft.execute(()->{sourceBusy148=false;sourceError=!result.isEmpty();if(sourceError){notice=result;failed.run();}else{bindingChecked132=true;syncSource(done,failed);}});},failed);
   });return;
  }
  send("SOURCE_PULL",Map.of(),d->{if(!"OK".equals(d.get("result"))){sourceError=true;notice=d.getOrDefault("result","SOURCE_ERROR");failed.run();return;}UUID account=actor,room=UUID.fromString(project);String fingerprint=store;var storage=scriptSources();sourceBusy148=true;
   sourceIo148(()->{String error="";try{storage.sync(account,fingerprint,room,d.get("data"));}catch(Exception e){error=failureCode(e);}String result=error;minecraft.execute(()->{sourceBusy148=false;sourceError=!result.isEmpty();if(sourceError){notice=result;failed.run();}else{sourceRevision148=d.getOrDefault("revision","");notice="";sourceDetail132="";done.run();}});},failed);
  });
 }

 private void sourceIo148(Runnable action,Runnable failed){try{IO.execute(action);}catch(RejectedExecutionException error){sourceBusy148=false;sourceError=true;notice="CACHE_ERROR";failed.run();}}

 private void removeWorkspace(){
  if(!readyForFileAction()||project.isEmpty())return;
  if(!String.valueOf(actor).equals(meta.get("owner"))){notice="DENIED";return;}
  confirm("remove_workspace",()->syncSource(this::deleteShared,()->confirm("remove_unsynced",this::deleteShared)));
 }
 private void deleteShared(){String removed132=project;send("REMOVE_WORKSPACE",Map.of("rev",rev()),d->{if(!"OK".equals(d.get("result")))return;if(WorkspaceIntegrated132.matches132(removed132))WorkspaceIntegrated132.leave132();drafts.clear();active=secondary=project=store="";meta=Map.of();catalogLoaded=false;sourceError=false;notice="";rebuild132();});}

 private void advanced(){
  var items=new ArrayList<WorkspacePickerScreen112.Item>();
  items.add(new WorkspacePickerScreen112.Item(tr("review").getString(),()->viewPanel131("RELEASES",d->minecraft.setScreen(new WorkspaceReview122(this,d)))));
  items.add(new WorkspacePickerScreen112.Item(tr("tools126").getString(),()->viewPanel131("CODE",d->minecraft.setScreen(new WorkspaceTools126(this,d)))));

  items.add(new WorkspacePickerScreen112.Item(tr("sync_source").getString(),()->{sourceError=false;syncSource(()->{});}));
  items.add(new WorkspacePickerScreen112.Item(tr("back").getString(),this::onClose));choose("section_advanced",items);
 }
 private void rebuild132(){
  if(!integrated132){rebuildWidgets();return;}
  for(var entry:new ArrayList<>(opening132.entrySet())){var draft=drafts.get(entry.getKey());if(draft!=null){bindSession132(draft);opening132.remove(entry.getKey());entry.getValue().accept(draft.session132);}}
 }
 private void bindSession132(Draft draft){if(!draft.replica.pending()&&!draft.dirty())draft.session132.lastSavedText=draft.text;draft.session132.recomputeDirty();}
 public void integrate132(Map<String,String> data){integrated132=true;pointerClient149.attach();caretClient151.attach();init(Minecraft.getInstance(),Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight());adopt131(data);initialFile130="";}
 public Map<String,String> context132(){return meta;}
 public void modelMetadata147(Map<String,String> data){if(project.equals(data.get("project"))){var next=new TreeMap<>(meta);next.putAll(data);meta=Map.copyOf(next);}}
 public boolean registered132(String path){return drafts.containsKey(path);}
 public void releaseTransport132(){client.detach();}
 public boolean connected132(){return client.connected();}
 public boolean settled132(){return !sourceBusy148&&settled();}
 public boolean writable132(String file){var d=drafts.get(file);return d!=null&&client.connected()&&!ioBusy&&!client.blocksEdits()&&d.editable&&!d.blocked&&!d.leaseNeeded&&d.replica.pendingCount130()<32;}
 public String status132(){String code=!client.connected()?client.result:notice.isEmpty()?(settled()?"LIVE130":"SYNCING130"):notice;return (project.isEmpty()?"":tr("role."+meta.getOrDefault("role","VIEWER")).getString()+" · ")+tr("status."+code).getString()+(client.editConfirmationMillis149()<0?"":" · "+tr("confirmation149",client.editConfirmationMillis149()).getString())+(!sourceDetail132.isEmpty()?" · "+sourceDetail132:"");}
 public List<String> files132(){return meta.getOrDefault("files","").lines().map(row->row.split("\\|",3)).filter(p->p.length==3&&(p[1].equals("TEXT")||com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(p[0]))).map(p->p[0]).toList();}
 public void open132(String file,com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession existing,java.util.function.Consumer<com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession> ready){if(existing!=null)preferred132.put(file,existing);open132(file,ready);}
 public void open132(String file,java.util.function.Consumer<com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession> ready){
  var draft=drafts.get(file);if(draft!=null){ready.accept(draft.session132);return;}opening132.put(file,ready);send("OPEN",Map.of("path",file),d->{if("OK".equals(d.get("result")))file(d,true,false);else opening132.remove(file);});
 }
 public void changed132(String path){var d=drafts.get(path);if(d==null)return;String next=d.doc.getValue();if(!d.canErase131&&!WorkspaceColorMath131.insertionOnly(d.text,next)){d.doc.setValue(d.text);d.history.clear();notice="ERASE_DENIED131";}else d.text=next;d.session132.recomputeDirty();}
 public void display132(String first,MaredMultiLineEditBox left,String second,MaredMultiLineEditBox right){boxes[0]=left;boxes[1]=right;displayed[0]=drafts.get(first);displayed[1]=drafts.get(second);if(!first.isEmpty())active=first;for(int i=0;i<2;i++)if(boxes[i]!=null&&displayed[i]!=null)bind(i);}
 public void renderPeers132(GuiGraphics graphics){peerCursors(graphics);renderPointers148(graphics);}
 public java.util.List<WorkspaceActivity152.Person> people152(String file){
  String peers="true".equals(meta.get("caret151"))?caretView151.rows():drafts.values().stream().max(Comparator.comparingLong(d->d.peersAt)).map(d->d.peers).orElse("");
  if(actor!=null&&Minecraft.getInstance().screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen&&!active.isEmpty())peers+="\n"+actor+"|"+active+"|"+Minecraft.getInstance().getUser().getName()+"|"+color+"|"+WorkspaceCodec122.id(CollaborativeDocument121.ROOT)+"|"+WorkspaceCodec122.id(CollaborativeDocument121.ROOT);
  return WorkspaceActivity152.people(file,peers,meta.getOrDefault("modelPeers152",""));
 }
 public String activity132(String file){String models=String.join(", ",meta.getOrDefault("modelPeers147","").lines().map(row->row.split("\\|",-1)).filter(row->row.length==3&&row[2].equals(file)).map(row->row[1]+" · Blockbench").toList());return models.isEmpty()?activity131().getOrDefault(file,""):models;}
 public void flush132(){for(var draft:drafts.values()){changed132(draft.path);capture130(draft);}saveAll();}
 public void detach132(){peerMotion.clear();caretView151.clear();flush132();client.detach();pointerClient149.detach();caretClient151.detach();Arrays.fill(boxes,null);Arrays.fill(displayed,null);}
 public void releaseViews132(){mousePath148="";Arrays.fill(boxes,null);Arrays.fill(displayed,null);}
 public void manage132(Screen back,int section){var screen=new WorkspaceScreen122(back);screen.init(Minecraft.getInstance(),Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight());screen.adopt131(meta);screen.startSection131(section);Minecraft.getInstance().setScreen(screen);}
 public void operation132(String op,Map<String,String> fields,java.util.function.Consumer<Map<String,String>> done){send(op,fields,done);}
 private void bind(int slot){var box=boxes[slot];var d=displayed[slot];if(box==null)return;updating=true;try{if(d==null){box.setEditable(false);return;}int l=d.doc.cursorLine(),c=d.doc.cursorCol();if(!d.doc.getValue().equals(d.text)){d.doc.setValue(d.text);d.doc.setCursor(l,c);d.history.clear();}if(box.document()!=d.doc)box.attachDocument(d.doc,d.history,d.view);box.setWriteEnabled(client.connected()&&!ioBusy&&!client.blocksEdits()&&d.editable&&!d.blocked&&!d.leaseNeeded&&d.replica.pendingCount130()<32);}finally{updating=false;}}
 private void refresh(Draft d){if(integrated132){updating=true;try{if(!d.doc.getValue().equals(d.text)){d.doc.setValue(d.text);d.history.clear();}bindSession132(d);}finally{updating=false;}}for(int n=0;n<2;n++)if(displayed[n]==d)bind(n);}
 private String jumpPath126="";private int jumpLine126;
 void openAt126(String file,int line){if(!integrated132){if(WorkspaceIntegrated132.tryEnter135(meta,previous))WorkspaceIntegrated132.openAt132(file,line);return;}jumpPath126=file;jumpLine126=line;minecraft.setScreen(this);send("OPEN",Map.of("path",file),d->{file(d,true,false);jump126();});}
 private void jump126(){var d=drafts.get(jumpPath126);if(d!=null&&active.equals(jumpPath126)){d.doc.setCursor(Math.max(0,Math.min(d.doc.lineCount()-1,jumpLine126-1)),0);for(var box:boxes)if(box!=null&&box.document()==d.doc){d.view.ensureCursorVisible(box.getWidth(),box.getHeight(),d.doc,font);box.setFocused(true);}jumpPath126="";}}
 String transportState126(){return client.result;}
 void attachClient(){client.attach();}public void tickClient(){tick();}boolean available(){return client.connected()&&!ioBusy&&!sourceBusy148;}
 void send(String op,Map<String,String> fields,java.util.function.Consumer<Map<String,String>> done){client.send(WorkspaceClient122.request(op,Set.of("APPEARANCE152","LIST","CREATE","CREATE_SCRIPT","CREATE_COMMAND133").contains(op)?"":project,fields),d->{if(d.getOrDefault("result","").equals("OK")){if(d.containsKey("appearance152")){var profile=new TreeMap<>(meta);profile.put("appearance152",d.get("appearance152"));profile.put("colorSaved152",d.getOrDefault("colorSaved152","false"));profile.put("color152",colorRevision==0?d.getOrDefault("color152",Integer.toString(color)):Integer.toString(color));meta=Map.copyOf(profile);}if(d.containsKey("actor")){UUID reported=UUID.fromString(d.get("actor"));if(actor==null){int[] palette={0xA879F5,0xF28482,0x80CBC4,0xFFD166,0x7CB5FF};color=palette[Math.floorMod(reported.hashCode(),palette.length)];loadAppearance(reported);}actor=reported;if("true".equals(d.get("colorSaved152"))&&colorRevision==0)applyAppearance(Integer.parseInt(d.get("color152")));}if(d.containsKey("store"))store=d.get("store");if(d.containsKey("revision")){var merged=new TreeMap<>(meta);merged.putAll(d);merged.remove("data");meta=Map.copyOf(merged);}}else notice=d.getOrDefault("result","INVALID");done.accept(d);});if(client.blocksEdits())lockWrites();}
 private WorkspaceAppearance124 appearance(){return new WorkspaceAppearance124(Minecraft.getInstance().gameDirectory.toPath().resolve("config/mared/workspace-colors124"));}
 private void applyAppearance(int rgb){color=rgb;if(meta.containsKey("appearance152")){var updated=new TreeMap<>(meta);updated.put("color152",Integer.toString(rgb));meta=Map.copyOf(updated);}caretSchedule151.retry();pointerStamp149="";for(var box:boxes)if(box!=null)box.setAccentColor(0xFF000000|color);}
 private void loadAppearance(UUID account){
  if("true".equals(meta.get("colorSaved152"))){applyAppearance(Integer.parseInt(meta.get("color152")));return;}
  int revision=colorRevision,fallback=color;var preferences=appearance();
  try{IO.execute(()->{try{int rgb=preferences.load(account,fallback);Minecraft.getInstance().execute(()->{if(account.equals(actor)&&revision==colorRevision&&!"true".equals(meta.get("colorSaved152"))){if("true".equals(meta.get("appearance152")))saveAppearance(rgb);else applyAppearance(rgb);}});}catch(Exception failure){Minecraft.getInstance().execute(()->notice="PREFERENCE_ERROR");}});}catch(RejectedExecutionException ignored){notice="PREFERENCE_ERROR";}
 }
 private void saveAppearance(int rgb){
  colorRevision++;int chosenRevision152=colorRevision;applyAppearance(rgb);
  var integrated=WorkspaceIntegrated132.current132();if(integrated!=null&&integrated!=this&&Objects.equals(actor,integrated.actor)){integrated.colorRevision++;integrated.applyAppearance(rgb);}
  if("true".equals(meta.get("appearance152"))){appearancePending152=true;appearanceError152=false;send("APPEARANCE152",Map.of("color",Integer.toString(rgb)),answer->{if(chosenRevision152==colorRevision)appearancePending152=false;if(!"OK".equals(answer.get("result"))){notice="PREFERENCE_ERROR";appearanceError152=true;appearanceBack152=false;}else if(chosenRevision152==colorRevision){var changed=new TreeMap<>(meta);changed.put("colorSaved152","true");changed.put("color152",Integer.toString(rgb));meta=Map.copyOf(changed);if(integrated!=null&&integrated.color==rgb){var activeProfile=new TreeMap<>(integrated.meta);activeProfile.put("colorSaved152","true");activeProfile.put("color152",Integer.toString(rgb));integrated.meta=Map.copyOf(activeProfile);}if(appearanceBack152){appearanceBack152=false;backFromPersonal131();}}});}
  UUID account=actor;var preferences=appearance();if(account==null)return;
  try{IO.execute(()->{try{preferences.save(account,rgb);}catch(Exception failure){Minecraft.getInstance().execute(()->notice="PREFERENCE_ERROR");}});}catch(RejectedExecutionException ignored){notice="PREFERENCE_ERROR";}
 }
 private void opened(Map<String,String> d){if(!d.containsKey("project"))return;if(!project.isEmpty()&&!project.equals(d.get("project")))client.send(WorkspaceClient122.request("LEAVE",project,Map.of()),ignored->{});saveAll();project=d.get("project");store=d.get("store");if(d.containsKey("actor"))actor=UUID.fromString(d.get("actor"));meta=d;drafts.clear();active=secondary="";sourcePoll=0;sourceError=false;bindingChecked132=false;sourceRevision148="";initialFile130=d.getOrDefault("files","").lines().map(row->row.split("\\|",3)).filter(parts->parts.length==3&&parts[1].equals("TEXT")).map(parts->parts[0]).sorted(Comparator.comparing((String file)->!file.startsWith("src/"))).findFirst().orElse("");rebuild132();}
 private void choose(String title,List<WorkspacePickerScreen112.Item> items){minecraft.setScreen(new WorkspacePickerScreen112(this,tr(title),items,this::tickClient,project.isEmpty()&&previous instanceof WorkspaceHome131?this::onClose:null));}
 private void legacyProjects(){var legacy=new WorkspaceClient112();legacyBrowse=legacy;legacy.attach();if(!legacy.connected()){legacy.detach();legacyBrowse=null;notice="UNAVAILABLE";return;}legacy.send(WorkspaceClient112.request("LIST","","","","",0,0,"","",""),d->{legacy.detach();legacyBrowse=null;var choices=new ArrayList<WorkspacePickerScreen112.Item>();choices.add(new WorkspacePickerScreen112.Item(tr("legacy_open").getString(),()->minecraft.setScreen(new WorkspaceScreen112(this))));for(String row:d.getOrDefault("projects","").split("\n")){String[] p=row.split("\\|",3);if(client.connected()&&p.length==3&&p[1].equals("OWNER"))choices.add(new WorkspacePickerScreen112.Item(tr("migrate",p[2]).getString(),()->confirm("migrate_title",()->client.send(WorkspaceClient122.request("IMPORT",p[0],Map.of()),a->{if(a.getOrDefault("result","").equals("OK"))opened(a);else notice=a.getOrDefault("result","INVALID");}))) );}choose("legacy",choices);});}
 private void projects(){if(!available()||!settled())return;send("LIST",Map.of(),d->{var items=new ArrayList<WorkspacePickerScreen112.Item>();for(String row:d.getOrDefault("projects","").split("\n")){String[] p=row.split("\\|",2);if(p.length==2)items.add(new WorkspacePickerScreen112.Item(p[1],()->client.send(WorkspaceClient122.request("VIEW",p[0],Map.of()),a->{if(a.getOrDefault("result","").equals("OK"))WorkspaceSocial131.resume131(a,this);else notice=a.getOrDefault("result","INVALID");})));}choose("projects",items);});}
 private void files(){if(!available()||project.isEmpty())return;send("VIEW",Map.of(),d->{var items=new ArrayList<WorkspacePickerScreen112.Item>();for(String row:d.getOrDefault("files","").split("\n")){String[] p=row.split("\\|",3);if(p.length==3&&p[1].equals("TEXT"))items.add(new WorkspacePickerScreen112.Item(p[0],()->send("OPEN",Map.of("path",p[0]),a->file(a,true,false))));}choose("files",items);});}
 private void grant(Draft d,Map<String,String> a){if(a.containsKey("lease"))d.replica.grant(a.get("lease"),Long.parseLong(a.get("first")),Long.parseLong(a.get("last")));}
 private void file(Map<String,String> a,boolean select,boolean discard){if(a.containsKey("pointers148")){pointers148=a.get("pointers148");pointersAt148=System.nanoTime();pointerView149.snapshot(pointers148);}if("true".equals(a.get("unchanged148"))){var d=drafts.get(a.get("path"));if(d!=null&&d.epoch.equals(a.get("epoch"))){d.peers=a.getOrDefault("peers","");d.peersAt=System.nanoTime();d.editable=Boolean.parseBoolean(a.get("editable"));d.canErase131=Boolean.parseBoolean(a.get("canErase131"));if(d.accessBlocked){d.blocked=d.accessBlocked=false;d.retryAfter=0;}}return;}if(!a.containsKey("document"))return;String p=a.get("path");try{var snapshot=WorkspaceCodec122.document(a.get("document"));Draft old=drafts.get(p);if(old!=null&&!discard){old.resync130=false;if(!old.epoch.equals(a.get("epoch"))){if(!old.dirty()&&!old.replica.pending()){file(a,select,true);return;}old.blocked=true;notice="STALE_FILE";return;}boolean prepared=capture130(old);var mark=mark(old);if(prepared){old.replica.merge(snapshot);old.text=old.replica.text();}old.peers=a.getOrDefault("peers","");old.peersAt=System.nanoTime();old.canErase131=Boolean.parseBoolean(a.getOrDefault("canErase131","true"));old.editable=Boolean.parseBoolean(a.get("editable"));if(old.accessBlocked){old.blocked=old.accessBlocked=false;old.retryAfter=0;}grant(old,a);refresh(old);if(prepared)restoreMark(old,mark);if(select&&!active.equals(p)){secondary=active;active=p;rebuild132();}return;}
   if(old==null&&drafts.size()>=(integrated132?16:8)){var evict=drafts.values().stream().filter(tab->!tab.path.equals(active)&&!tab.path.equals(secondary)&&!tab.dirty()&&!tab.replica.pending()).findFirst().orElse(null);if(evict==null){notice="TAB_LIMIT";return;}saveAll();drafts.remove(evict.path);}var requestedNative132=preferred132.remove(p);var native132=requestedNative132!=null?requestedNative132:integrated132&&old!=null?old.session132:null;String nativeTarget132=native132==null?null:native132.currentText();boolean nativeDirty132=native132!=null&&native132.dirty;Draft fresh=new Draft(p,new WorkspaceReplica122(actor,snapshot),a.get("epoch"),native132==null?new com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession():native132);if(nativeDirty132){fresh.text=nativeTarget132;if(!native132.lastSavedText.equals(fresh.replica.text())){fresh.blocked=true;notice="SOURCE_CHANGED";}}fresh.peers=a.getOrDefault("peers","");fresh.peersAt=System.nanoTime();fresh.canErase131=Boolean.parseBoolean(a.getOrDefault("canErase131","true"));fresh.editable=Boolean.parseBoolean(a.get("editable"));grant(fresh,a);String projectId=project,fingerprint=store;UUID account=actor;ioBusy=true;
   IO.execute(()->{Map<String,String> saved=null;Exception problem=null;try{if(!discard)saved=cache().load(account,fingerprint,UUID.fromString(projectId),p);}catch(Exception e){problem=e;}var value=saved;var failure=problem;Minecraft.getInstance().execute(()->{ioBusy=false;if(!client.connected()||!project.equals(projectId)||!store.equals(fingerprint))return;try{if(value!=null){var recovered=WorkspaceReplica122.recover(account,value,fresh.epoch,snapshot);fresh.replica=recovered.replica();fresh.text=recovered.target();fresh.epoch=recovered.epoch();fresh.blocked=recovered.stale();if(fresh.blocked)notice="STALE_FILE";else grant(fresh,a);}if(failure!=null){fresh.blocked=true;notice="CACHE_ERROR";}}catch(Exception e){fresh.blocked=true;notice="CACHE_ERROR";}drafts.put(p,fresh);fresh.doc.setValue(fresh.text);fresh.session132.lastSavedText=snapshotText132(snapshot);fresh.session132.recomputeDirty();if(select||active.isEmpty()){if(!active.equals(p))secondary=active;active=p;}rebuild132();});});
  }catch(Exception e){ioBusy=false;notice="INVALID";}}
 private static String snapshotText132(CollaborativeDocument121.Snapshot snapshot){return new CollaborativeDocument121(snapshot).text();}
 private Map<String,String> checkpoint(Draft d)throws Exception{var out=new TreeMap<>(d.replica.checkpoint(d.text));out.put("epoch",d.epoch);return out;}
 private void saveAll(){if(actor==null||project.isEmpty()||store.isEmpty())return;for(Draft d:drafts.values())try{if(d.checkpointVersion148==d.replica.version148()&&d.checkpointTarget148.equals(d.text))continue;var value=checkpoint(d);String stamp=value.toString();if(stamp.equals(d.stamp))continue;UUID account=actor,id=UUID.fromString(project);String fingerprint=store;IO.execute(()->{try{cache().save(account,fingerprint,id,d.path,value);}catch(Exception e){Minecraft.getInstance().execute(()->{notice="CACHE_ERROR";d.stamp="";d.checkpointVersion148=-1;});}});d.stamp=stamp;d.checkpointVersion148=d.replica.version148();d.checkpointTarget148=d.text;}catch(Exception e){notice="CACHE_ERROR";}}
 private void lockWrites(){for(var box:boxes)if(box!=null)box.setWriteEnabled(false);}
 private void submit(Draft d){if(d.staging148)return;try{if(!capture130(d)||d.leaseNeeded){send("LEASE",Map.of("path",d.path),a->{if(a.containsKey("lease")){grant(d,a);d.leaseNeeded=false;capture130(d);}else problem130(d,a.getOrDefault("result","INVALID"));});return;}if(!d.replica.pending())return;var pending=d.replica.first();var q=new TreeMap<>(WorkspaceClient122.request("EDIT",project,Map.of("path",d.path,"lease",pending.lease(),"batch",pending.batch())));q.put("request",pending.request().toString());if("true".equals(meta.get("sync148")))q.put("compact148","true");var value=checkpoint(d);UUID account=actor,id=UUID.fromString(project);String fingerprint=store,p=project;d.staging148=true;
   try{IO.execute(()->{Exception failure=null;try{cache().save(account,fingerprint,id,d.path,value);}catch(Exception e){failure=e;}var error=failure;Minecraft.getInstance().execute(()->{d.staging148=false;if(error!=null){d.blocked=true;notice="CACHE_ERROR";return;}if(!project.equals(p)||!store.equals(fingerprint)||drafts.get(d.path)!=d)return;client.send(q,a->{if(!a.getOrDefault("result","").equals("OK")){problem130(d,a.getOrDefault("result","INVALID"));return;}try{if(!capture130(d))return;if(!d.epoch.equals(a.get("epoch")))throw new IllegalStateException();var mark=mark(d);d.replica.acknowledge(pending.request(),WorkspaceCodec122.document(a.get("document")));d.text=d.replica.text();d.peers=a.getOrDefault("peers","");d.peersAt=System.nanoTime();var merged148=new TreeMap<>(meta);merged148.putAll(a);meta=Map.copyOf(merged148);refresh(d);restoreMark(d,mark);saveAll();}catch(Exception e){d.blocked=true;notice="STALE_FILE";}});});});}catch(RejectedExecutionException e){d.staging148=false;throw e;}
  }catch(Exception e){d.blocked=true;notice="LOCAL_LIMIT";}}
 private void confirm(String title,Runnable action){minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)action.run();},tr(title.equals("compact_confirm")?"compact":title),tr(title.equals("compact_confirm")?"compact_confirm":"confirm")));}
 private boolean readyForFileAction(){
  if(!available()||!settled()){notice="WAITING";return false;}return true;
 }
 private void actions(){
  if(active.isEmpty())return;
  String file=active;var draft=drafts.get(file);if(draft==null)return;
  var items=new ArrayList<WorkspacePickerScreen112.Item>();
  items.add(new WorkspacePickerScreen112.Item(tr("copy").getString(),()->minecraft.keyboardHandler.setClipboard(draft.text)));
  items.add(new WorkspacePickerScreen112.Item(tr("server_copy").getString(),()->confirm("server_copy",()->send("OPEN",Map.of("path",file),a->file(a,true,true)))));
  items.add(new WorkspacePickerScreen112.Item(tr("close_file").getString(),()->confirm("close_file",()->{
   saveAll();drafts.remove(file);active=drafts.keySet().stream().findFirst().orElse("");if(secondary.equals(file))secondary="";rebuild132();
  })));
  items.add(new WorkspacePickerScreen112.Item(tr("delete").getString(),()->{
   if(!readyForFileAction())return;
   send("DELETE_REVIEW",Map.of("path",file,"rev",rev()),a->{
    if(a.containsKey("ticket"))confirm("delete",()->send("DELETE",Map.of("path",file,"ticket",a.get("ticket"),"rev",a.get("revision")),r->{
     if(r.getOrDefault("result","").equals("OK")){draft.blocked=true;notice="STALE_FILE";}
    }));
   });
  }));
  items.add(new WorkspacePickerScreen112.Item(tr("rename").getString(),()->{
   if(!readyForFileAction())return;
   String target=path.getValue();
   confirm("rename",()->send("RENAME",Map.of("path",file,"target",target,"rev",rev()),a->{
    if(!a.getOrDefault("result","").equals("OK"))return;
    saveAll();drafts.remove(file);active=drafts.keySet().stream().findFirst().orElse("");if(secondary.equals(file))secondary="";rebuild132();
    send("OPEN",Map.of("path",target),answer->file(answer,true,false));
   }));
  }));
  if("true".equals(meta.get("history124"))&&Set.of("OWNER","MAINTAINER").contains(meta.getOrDefault("role","")))
   items.add(new WorkspacePickerScreen112.Item(tr("compact").getString(),()->{
    if(readyForFileAction())confirm("compact_confirm",()->send("COMPACT",Map.of("path",file,"rev",rev()),a->{
     if(a.getOrDefault("result","").equals("OK"))send("OPEN",Map.of("path",file),r->file(r,true,true));
    }));
   }));
  choose("actions",items);
 }
 private void history(){viewPanel131("HISTORY",d->minecraft.setScreen(new WorkspaceHistory131(this,d)));}
 private void history(String file,String before){
  var fields=new TreeMap<String,String>();fields.put("path",file);if(!before.isEmpty())fields.put("before",before);
  send("HISTORY",fields,a->{
   if(!a.getOrDefault("result","").equals("OK"))return;
   var items=new ArrayList<WorkspacePickerScreen112.Item>();
   var restorable=Set.copyOf(a.getOrDefault("restorable","").lines().toList());
   for(String row:a.getOrDefault("history","").split("\n")){
    String[] p=row.split("\\|",3);if(p.length!=3)continue;
    items.add(new WorkspacePickerScreen112.Item("r"+p[0]+" · "+p[2]+" · "+p[1],()->{
     var actions=new ArrayList<WorkspacePickerScreen112.Item>();
     actions.add(new WorkspacePickerScreen112.Item(tr("view_diff").getString(),()->send("HISTORY_DIFF",Map.of("path",file,"history",p[0]),d->{if("OK".equals(d.get("result"))){var diff=new TreeMap<>(d);diff.put("revision",p[0]);minecraft.setScreen(new WorkspaceDiffScreen126(this,diff));}})));
     if(restorable.contains(p[0]))actions.add(new WorkspacePickerScreen112.Item(tr("restore").getString(),()->confirm("restore",()->send("RESTORE",Map.of("path",file,"history",p[0],"rev",a.get("revision")),d->{if("OK".equals(d.get("result")))send("OPEN",Map.of("path",file),r->file(r,true,true));}))));
     choose("history",actions);
    }));
   }
   String next=a.getOrDefault("historyBefore","0");
   if(!next.equals("0"))items.add(new WorkspacePickerScreen112.Item(tr("older_history").getString(),()->history(file,next)));
   choose("history",items);
  });
 }
 private boolean capture130(Draft d){
  if(!d.dirty())return true;
  try{if(!d.replica.prepare(d.text)){d.leaseNeeded=true;return false;}d.leaseNeeded=false;d.capturedAt=System.nanoTime();return true;}
  catch(Exception failure){d.blocked=true;notice="LOCAL_LIMIT";return false;}
 }
 private void problem130(Draft d,String result){
  notice=result;if(result.equals("INVALID"))d.resync130=true;if(result.equals("DENIED"))d.accessBlocked=true;else if(Set.of("MISSING","NOT_TEXT","STALE_FILE","LEASE").contains(result))d.accessBlocked=false;if(Set.of("DENIED","MISSING","NOT_TEXT","STALE_FILE","LEASE").contains(result)){d.blocked=true;d.editable=false;}
  else d.retryAfter=System.nanoTime()+2_000_000_000L;
 }
 private void event130(Map<String,String> data){
  if(!project.equals(data.get("project")))return;
  if("DENIED".equals(data.get("result"))){pointerView149.snapshot("");caretView151.clear();for(Draft d:drafts.values()){d.blocked=d.accessBlocked=true;d.editable=false;d.retryAfter=System.nanoTime()+2_000_000_000L;}notice="DENIED";lockWrites();saveAll();return;}
  String kind=data.get("event130");
  if(data.containsKey("caretRemove151"))caretView151.remove(data.get("caretRemove151"));
  if(data.containsKey("caretDelta151")){try{caretView151.delta(data.get("caretDelta151"),Long.parseLong(data.get("caretSequence151")),data.get("caretSession151"));}catch(RuntimeException ignored){}return;}
  if("pointer149".equals(kind)){pointerView149.delta(data.getOrDefault("pointerDelta149",""));return;}
  if("presence".equals(kind)){if(data.containsKey("pointers148")){pointers148=data.get("pointers148");pointersAt148=System.nanoTime();pointerView149.snapshot(pointers148);}long now=System.nanoTime();for(Draft d:drafts.values()){d.peers=data.getOrDefault("peers","");d.peersAt=now;}return;}
  if("catalog".equals(kind)){metadata130(data);return;}
  if("document".equals(kind)){
   var draft=drafts.get(data.get("path"));if(draft==null)return;
   if(!draft.epoch.equals(data.get("epoch"))&&!draft.dirty()&&!draft.replica.pending())file(data,false,true);else file(data,false,false);
  }
 }
 private void metadata130(Map<String,String> data){
  if(data.containsKey("pointers148"))pointerView149.snapshot(data.get("pointers148"));
  if(!"OK".equals(data.get("result"))){if("DENIED".equals(data.get("result")))event130(Map.of("project",project,"result","DENIED"));else notice=data.getOrDefault("result","INVALID");return;}
  var merged=new TreeMap<>(meta);merged.putAll(data);merged.remove("event130");meta=Map.copyOf(merged);
  if(!data.containsKey("files"))return;var visible=new HashSet<String>();for(String row:data.get("files").split("\n")){String[] fields=row.split("\\|",3);if(fields.length==3)visible.add(fields[0]);}
  for(Draft d:drafts.values())if(!visible.contains(d.path)){d.blocked=d.accessBlocked=true;d.editable=false;notice="STALE_FILE";}
 }
 @Override public void tick(){
  client.tick();if(integrated132){pointerClient149.tick();caretClient151.tick();}
  if(legacyBrowse!=null){legacyBrowse.tick();if(!legacyBrowse.busy()){notice=legacyBrowse.result;legacyBrowse.detach();legacyBrowse=null;}}
  long now=System.nanoTime();
  for(Draft d:drafts.values()){
   if(integrated132)changed132(d.path);
   if(now-d.peersAt>6_000_000_000L)d.peers="";
   if(!d.blocked&&d.editable&&d.dirty()&&now-d.capturedAt>=50_000_000L)capture130(d);
  }
  for(int n=0;n<2;n++)if(boxes[n]!=null&&displayed[n]!=null){Draft d=displayed[n];boxes[n].setWriteEnabled(client.connected()&&!ioBusy&&!client.blocksEdits()&&d.editable&&!d.blocked&&!d.leaseNeeded&&d.replica.pendingCount130()<32);}
  if(integrated132){caret151(now);pointer149(now);}
  if(now-cacheAt>500_000_000L){cacheAt=now;saveAll();}
  if(idle()&&actor!=null&&section131>=0&&minecraft.screen==this){section131();return;}
  if(!idle()||project.isEmpty())return;
  if(integrated132&& !opening132.isEmpty())rebuild132();
  if(!integrated132)return;
  if(!initialFile130.isEmpty()){String first=initialFile130;initialFile130="";send("OPEN",Map.of("path",first),a->file(a,true,false));return;}
  int focused=boxes[1]!=null&&boxes[1].isFocused()?1:0;Draft primary=displayed[focused];
  var edits=drafts.values().stream().filter(d->!d.staging148&&!d.blocked&&d.editable&&now>=d.retryAfter&&(d.replica.pending()||d.dirty()||d.leaseNeeded)).toList();
  var background=drafts.values().stream().filter(d->(d!=primary||d.accessBlocked||d.resync130)&&(!d.blocked||d.accessBlocked)&&now>=d.retryAfter).toList();
  var lane=schedule130.next(now,!edits.isEmpty(),primary!=null&&!primary.blocked&&!primary.resync130&&now>=primary.retryAfter&&(!"true".equals(meta.get("caret151"))||now-caretRecovery151>=1_200_000_000L),!background.isEmpty(),true,settled()&&!sourceError&&!rev().equals(sourceRevision148)&&!meta.getOrDefault("scriptHash129","").isEmpty()&&String.valueOf(actor).equals(meta.get("owner")));
  if(lane==null)return;
  switch(lane){
   case EDIT->submit(edits.get(Math.floorMod(edit130++,edits.size())));
   case PRESENCE->{caretRecovery151=now;if("true".equals(meta.get("caret151")))send("SYNC",known148(primary),a->{if("OK".equals(a.get("result")))file(a,false,false);else problem130(primary,a.getOrDefault("result","INVALID"));});else poll130(primary);}
   case BACKGROUND->{Draft d=background.get(Math.floorMod(background130++,background.size()));send("SYNC",known148(d),a->{if("OK".equals(a.get("result")))file(a,false,false);else problem130(d,a.getOrDefault("result","INVALID"));});}
   case CATALOG->send("VIEW",Map.of(),this::metadata130);
   case SOURCE->syncSource(()->{});
  }
 }
 private void poll130(Draft d){
  if(actor==null||d==null)return;
  capture130(d);var ids=d.replica.document().visibleIds();int[] selection=d.doc.orderedSelection();
  int cp=selection==null?cursor(d.doc):offset(d.doc,selection[0],selection[1]);int end=selection==null?cp:offset(d.doc,selection[2],selection[3]);
  var anchor=cp<=0||ids.isEmpty()?CollaborativeDocument121.ROOT:ids.get(Math.min(cp,ids.size())-1);
  var selected=end<=0||ids.isEmpty()?CollaborativeDocument121.ROOT:ids.get(Math.min(end,ids.size())-1);
  // Pending local IDs are not known by the server until EDIT is acknowledged. Use confirmed anchors.
  anchor=d.replica.confirmedAnchor130(anchor);selected=d.replica.confirmedAnchor130(selected);
  var fields=new TreeMap<>(Map.of("path",d.path,"anchor",WorkspaceCodec122.id(anchor),"selection",WorkspaceCodec122.id(selected),"color",Integer.toString(color),"sequence",Long.toString(++sequence),"session",presence.toString(),"editing",Boolean.toString(d.dirty()||d.replica.pending())));
  fields.putAll(known148(d));if(!"true".equals(meta.get("pointer149")))mouseFields148(d,fields);send("POLL",fields,a->{if("OK".equals(a.get("result")))file(a,false,false);else problem130(d,a.getOrDefault("result","INVALID"));});
 }
 private List<String[]> sidebarFiles131(){return meta.getOrDefault("files","").lines().map(row->row.split("\\|",3)).filter(row->row.length==3).toList();}
 private Map<String,String> activity131(){var latest=drafts.values().stream().max(Comparator.comparingLong(d->d.peersAt)).orElse(null);var result=new HashMap<String,String>();if(latest==null)return result;for(String row:("true".equals(meta.get("caret151"))?caretView151.rows():latest.peers).lines().toList()){String[] fields=row.split("\\|",6);if(fields.length==6){String name=fields[0].equals(String.valueOf(actor))?tr("you131").getString():fields[2];result.merge(fields[1],name,(first,next)->first+", "+next);}}return result;}
 private void sidebar131(GuiGraphics g,int mx,int my){
  if(project.isEmpty()||sidebarWidth131==0)return;int bottom=height-46;g.fill(left,122,left+sidebarWidth131,bottom,com.fixmer.mared.technology.editor.GenesisEditorVisuals.panel());g.drawString(font,tr("files"),left+8,129,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(),false);var files=sidebarFiles131();var activity=activity131();int rows=Math.max(1,(bottom-148)/28);sidebarScroll131=Math.min(Math.max(0,sidebarScroll131),Math.max(0,files.size()-rows));
  for(int i=0;i<rows&&sidebarScroll131+i<files.size();i++){String[] file=files.get(sidebarScroll131+i);int y=148+i*28;boolean hover=mx>=left&&mx<left+sidebarWidth131&&my>=y&&my<y+28;if(file[0].equals(active)||hover)g.fill(left+2,y-2,left+sidebarWidth131-2,y+25,file[0].equals(active)?com.fixmer.mared.technology.editor.GenesisEditorVisuals.selected():com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised());String label=file[0].substring(file[0].lastIndexOf('/')+1);g.drawString(font,font.plainSubstrByWidth(label,sidebarWidth131-14),left+7,y,file[1].equals("TEXT")?com.fixmer.mared.technology.editor.GenesisEditorVisuals.text():0xFF97A4B6,false);String detail=activity.getOrDefault(file[0],file[0].contains("/")?file[0].substring(0,file[0].lastIndexOf('/')):"");g.drawString(font,font.plainSubstrByWidth(detail,sidebarWidth131-14),left+7,y+12,activity.containsKey(file[0])?0xFF91DAB0:0xFF8090A6,false);if(hover)g.renderTooltip(font,Component.literal(file[0]+(activity.containsKey(file[0])?" · "+activity.get(file[0]):"")),mx,my);}
 }
 @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&available()&&mx>=left&&mx<left+sidebarWidth131&&my>=148&&my<height-46){var files=sidebarFiles131();int index=sidebarScroll131+(int)(my-148)/28;if(index<files.size()){String[] file=files.get(index);if(file[1].equals("TEXT"))send("OPEN",Map.of("path",file[0]),d->file(d,true,false));return true;}}return super.mouseClicked(mx,my,button);}
 @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){if(mx>=left&&mx<left+sidebarWidth131&&my>=122&&my<height-46){sidebarScroll131=Math.max(0,sidebarScroll131+(int)(-dy*3));return true;}return super.mouseScrolled(mx,my,dx,dy);}
 private record Mark(CollaborativeDocument121.Id caret,CollaborativeDocument121.Id start,CollaborativeDocument121.Id end){}
 private static int offset(EditorDocument d,int line,int col){return d.codePointOffset(line,col);}
 private static int cursor(EditorDocument d){return offset(d,d.cursorLine(),d.cursorCol());}
 private static CollaborativeDocument121.Id at(Draft d,int cp){var ids=d.replica.document().visibleIds();return cp<=0||ids.isEmpty()?CollaborativeDocument121.ROOT:ids.get(Math.min(cp,ids.size())-1);}
 private static Mark mark(Draft d){int[] sel=d.doc.orderedSelection();return new Mark(at(d,cursor(d.doc)),sel==null?null:at(d,offset(d.doc,sel[0],sel[1])),sel==null?null:at(d,offset(d.doc,sel[2],sel[3])));}
 private static int[] point(Draft d,CollaborativeDocument121.Id id){int cp=d.replica.document().position(id),line=0;for(;line<d.doc.lineCount()-1;line++){String s=d.doc.line(line);int n=s.codePointCount(0,s.length());if(cp<=n)break;cp-=n+1;}String s=d.doc.line(line);return new int[]{line,s.offsetByCodePoints(0,Math.max(0,Math.min(cp,s.codePointCount(0,s.length()))))};}
 private static void restoreMark(Draft d,Mark mark){try{int[] p=point(d,mark.caret);d.doc.setCursor(p[0],p[1]);if(mark.start!=null){int[] a=point(d,mark.start),b=point(d,mark.end);d.doc.startSelection(a[0],a[1]);d.doc.extendSelection(b[0],b[1]);}}catch(Exception ignored){}}
 @Override public void removed(){saveAll();}@Override public boolean isPauseScreen(){return false;}@Override public void onClose(){if(available()&&settled()&&!sourceError&&!project.isEmpty()&&!meta.getOrDefault("scriptHash129","").isEmpty()&&String.valueOf(actor).equals(meta.get("owner"))){syncSource(this::closeAfterSync);return;}closeAfterSync();}private void closeAfterSync(){if(WorkspaceIntegrated132.matches132(project)){client.detach();minecraft.setScreen(previous instanceof WorkspaceHome131?previous:WorkspaceIntegrated132.shell132(previous));return;}saveAll();if(!settled())confirm("back",()->{client.detach();minecraft.setScreen(previous);});else if(available()&&!project.isEmpty())send("LEAVE",Map.of(),d->{client.detach();minecraft.setScreen(previous);});else{client.detach();minecraft.setScreen(previous);}}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){
  g.fill(0,0,width,height,com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg());g.drawCenteredString(font,title,width/2,8,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());super.render(g,mx,my,dt);
  g.drawString(font,font.plainSubstrByWidth(meta.getOrDefault("title","")+" · "+status132(),Math.max(1,w)),left,106,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(),false);
  if(!sourceDetail132.isEmpty()){int y=184;for(var line:font.split(Component.literal(sourceDetail132),Math.max(1,w))){if(y>=height-45)break;g.drawString(font,line,left,y,com.fixmer.mared.technology.editor.GenesisEditorVisuals.danger(),false);y+=font.lineHeight+3;}}
 }

 private String pointers148="";private long pointersAt148;private String mousePath148="";private int mouseLine148,mouseCol148,mouseDx148,mouseDy148;
 private Map<String,String> known148(Draft d){if(!"true".equals(meta.get("sync148")))return Map.of("path",d.path);return Map.of("path",d.path,"epoch148",d.epoch,"known148",Long.toString(d.replica.confirmedRevision148()));}
 private void mouseFields148(Draft d,Map<String,String> fields){
  if(!"true".equals(meta.get("sync148")))return;
  fields.put("mouse148","");fields.put("dx148","0");fields.put("dy148","0");
  if(!d.path.equals(mousePath148))return;
  try{int cp=offset(d.doc,Math.min(mouseLine148,d.doc.lineCount()-1),mouseCol148);var id=d.replica.confirmedAnchor130(at(d,CaretSchedule151.project(d.doc.getValue(),d.replica.text(),cp)));fields.put("mouse148",WorkspaceCodec122.id(id));fields.put("dx148",Integer.toString(mouseDx148));fields.put("dy148",Integer.toString(mouseDy148));}catch(RuntimeException ignored){}
 }
 private long caretRecovery151;
 private void caret151(long now){
  if(actor==null||project.isEmpty()||!"true".equals(meta.get("caret151"))||caretClient151.busy()||client.blocksEdits())return;
  if(!(Minecraft.getInstance().screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen)||!Minecraft.getInstance().isWindowActive())return;
  int focus=boxes[1]!=null&&boxes[1].isFocused()?1:0;Draft d=displayed[focus];if(d==null||d.blocked||d.resync130)return;
  int[] selection=d.doc.orderedSelection();int cp=selection==null?cursor(d.doc):offset(d.doc,selection[0],selection[1]);int end=selection==null?cp:offset(d.doc,selection[2],selection[3]);
  String local=d.doc.getValue(),replica=d.replica.text();
  var anchor=d.replica.confirmedAnchor130(at(d,CaretSchedule151.project(local,replica,cp)));
  var selected=d.replica.confirmedAnchor130(at(d,CaretSchedule151.project(local,replica,end)));
  var fields=new TreeMap<>(Map.of("path",d.path,"epoch151",d.epoch,"anchor",WorkspaceCodec122.id(anchor),"selection",WorkspaceCodec122.id(selected),"color",Integer.toString(color),"editing",Boolean.toString(d.dirty()||d.replica.pending())));
  if(!caretSchedule151.take(now,fields.toString()))return;
  fields.put("sequence",Long.toString(++sequence));fields.put("session",presence.toString());
  caretClient151.send(WorkspaceClient122.request("CARET151",project,fields),answer->{if(!"OK".equals(answer.get("result")))caretSchedule151.retry();});
 }
 private long pointerSequence149,pointerAt149;private String pointerStamp149="";
 private void pointer149(long now){
  if(actor==null||project.isEmpty()||!"true".equals(meta.get("pointer149"))||(!"true".equals(meta.get("pointerStream154"))&&pointerClient149.busy())||client.blocksEdits()||now-pointerAt149<50_000_000L)return;
  if(!(Minecraft.getInstance().screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen)||!Minecraft.getInstance().isWindowActive())mousePath148="";
  Draft draft=drafts.get(mousePath148.isEmpty()?active:mousePath148);if(draft==null||draft.blocked)return;
  var fields=new TreeMap<String,String>();mouseFields148(draft,fields);fields.put("path",draft.path);fields.put("color",Integer.toString(color));
  String stamp=fields.toString();if(stamp.equals(pointerStamp149)&&now-pointerAt149<500_000_000L)return;
  pointerAt149=now;pointerStamp149=stamp;fields.put("session",presence.toString());fields.put("sequence",Long.toString(++pointerSequence149));
  if("true".equals(meta.get("pointerStream154"))){fields.put("stream154","true");if(!pointerClient149.stream154(WorkspaceClient122.request("POINTER149",project,fields)))pointerStamp149="";}
  else pointerClient149.send(WorkspaceClient122.request("POINTER149",project,fields),answer->{if(!"OK".equals(answer.get("result")))pointerStamp149="";});
 }
 private void renderPointers148(GuiGraphics g){
  mousePath148="";var mc=Minecraft.getInstance();
  if(!(mc.screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen))return;
  double mx=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getScreenWidth(),my=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getScreenHeight();
  var pointers=pointerView149.visible();
  for(int n=0;n<2;n++){Draft d=displayed[n];var box=boxes[n];if(d==null||box==null)continue;
   if(mc.isWindowActive()&&mx>=box.getX()&&mx<box.getX()+box.getWidth()&&my>=box.getY()&&my<box.getY()+box.getHeight()){
    var pos=d.view.pixelToPos(mx,my,box.getX(),box.getY(),box.getWidth(),box.getHeight(),d.doc,font);var pixel=d.view.positionPixel148(pos[0],pos[1],box.getX(),box.getY(),box.getWidth(),d.doc,font);
    if(pixel!=null){mousePath148=d.path;mouseLine148=pos[0];mouseCol148=pos[1];mouseDx148=Math.max(-64,Math.min(64,(int)mx-pixel[0]));mouseDy148=Math.max(0,Math.min(16,(int)my-pixel[1]));}
   }
   if(d.blocked||d.accessBlocked)continue;
   for(var pointer:pointers){if(pointer.actor().equals(String.valueOf(actor))||!pointer.path().equals(d.path))continue;
    try{var pt=pointerPoint154(d,WorkspaceCodec122.id(pointer.anchor()));var px=d.view.positionPixel148(pt[0],pt[1],box.getX(),box.getY(),box.getWidth(),d.doc,font);if(px==null)continue;
     String surface=n+"/"+box.getX()+"/"+box.getY()+"/"+box.getWidth()+"/"+box.getHeight()+"/"+d.view.scrollLine();var smooth=pointerView149.position(pointer.actor(),n,surface,px[0]+pointer.dx(),px[1]+pointer.dy());int x=smooth[0],y=smooth[1],rgb=0xff000000|pointer.rgb();
     g.enableScissor(box.getX()+1,box.getY()+1,box.getX()+box.getWidth()-1,box.getY()+box.getHeight()-1);
     try{for(var run:com.fixmer.genesis.technology.editor.PointerGlyph149.RUNS){int ink=run.ink()==1?0xff101010:run.ink()==2?rgb:0xfffafafa;g.fill(x+run.x(),y+run.y(),x+run.x()+run.length(),y+run.y()+1,ink);}g.fill(x+19,y+23,x+24+font.width(pointer.label()),y+35,0xd0202020);g.drawString(font,pointer.label(),x+21,y+25,rgb,false);}finally{g.disableScissor();}
    }catch(RuntimeException ignored){}
   }
  }
 }
 private static int[] pointerPoint154(Draft d,CollaborativeDocument121.Id anchor){
  return d.doc.codePointPosition(projection(d).map(d.replica.document().position(anchor)));
 }

 private static CaretSchedule151.Projection projection(Draft d){
  String source=d.replica.text(),target=d.doc.getValue();
  if(d.projection==null||d.projectionSource!=source||d.projectionTarget!=target){
   d.projection=CaretSchedule151.between(source,target);
   d.projectionSource=source;d.projectionTarget=target;
  }
  return d.projection;
 }

 private final LinkedHashMap<String,PointerView149.Motion> peerMotion=new LinkedHashMap<>(64,.75f,true);
 private void peerCursors(GuiGraphics g){
  long now=System.nanoTime();
  for(int n=0;n<2;n++){
   Draft d=displayed[n];var box=boxes[n];
   boolean separate="true".equals(meta.get("caret151"));
   if(d==null||box==null||d.blocked||d.accessBlocked||!separate&&now-d.peersAt>6_000_000_000L)continue;
   var mapping=projection(d);
   String surface=project+"|"+d.path+"|"+d.epoch+"|"+d.view.scrollLine()+"|"+box.getX()+"|"+box.getY()+"|"+box.getWidth()+"|"+box.getHeight();
   for(String row:(separate?caretView151.rows():d.peers).split("\n")){
    String[] fields=row.split("\\|",6);
    if(fields.length!=6||fields[0].equals(String.valueOf(actor))||!fields[1].equals(d.path))continue;
    try{
     int a=mapping.map(d.replica.document().position(WorkspaceCodec122.id(fields[4])));
     int b=mapping.map(d.replica.document().position(WorkspaceCodec122.id(fields[5])));
     var motion=peerMotion.computeIfAbsent(n+"|"+fields[0],key->new PointerView149.Motion());
     if(peerMotion.size()>128)peerMotion.pollFirstEntry();
     d.view.renderPeer122(g,font,box.getX(),box.getY(),box.getWidth(),box.getHeight(),d.doc,a,b,
      Integer.parseInt(fields[3]),fields[2],(x,y)->motion.position(surface,x,y,now,.05));
    }catch(RuntimeException ignored){}
   }
  }
 }
}
