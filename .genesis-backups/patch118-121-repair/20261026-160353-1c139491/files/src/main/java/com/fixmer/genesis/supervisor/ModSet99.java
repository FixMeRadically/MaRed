package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Complete owned-mod revision. All mutating entry points are called under ModDeployment's OS lock.
 * Applying/restoring additionally requires the profile writer; journals never prove game exit. */
public final class ModSet99 {
 public static final int MAX_MODS=128;
 public record Input(String project,Path jar,String sha256) {
  public Input { project=projectId(project);jar=jar.toAbsolutePath().normalize();requireHash(sha256); }
 }
 public record Entry(String project,String modId,String version,String sha256) {
  public Entry { project=projectId(project);if(!modId.matches("[a-z][a-z0-9_]{1,63}")||Set.of("mared","genesis","genesis_core","minecraft","neoforge").contains(modId))throw new IllegalArgumentException("SET99_MOD_ID");requireHash(sha256);if(version==null||version.isBlank()||version.length()>256)throw new IllegalArgumentException("SET99_VERSION"); }
  public String file(){return "mared-dev-"+modId+".jar";}
 }
 public record Change(String action,String project,String modId,String beforeVersion,String afterVersion) {}
 public record Review(String profile,String revision,String priorRevision,String inventory,String catalogHash,
                      Map<String,String> loaded,List<Entry> entries,List<Change> changes) {
  public Review { loaded=Map.copyOf(loaded);entries=List.copyOf(entries);changes=List.copyOf(changes); }
 }
 private record Plan(List<Entry> before,List<Entry> after,Map<String,String> inventory,String catalogHash,boolean catalogExisted) {}
 private final Path game,root,mods;
 ModSet99(Path game){this.game=game.toAbsolutePath().normalize();root=this.game.resolve("config/mared/dev/deployments");mods=this.game.resolve("mods");}
 static boolean isSet(Properties p){return "99".equals(p.getProperty("setSchema"));}
 private static String projectId(String s){if(s==null||!s.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("SET99_PROJECT_ID");return s;}
 private static void requireHash(String s){if(s==null||!s.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("SET99_HASH");}
 private static IOException error(String code){return new IOException(code);}
 private static void safe(Path p)throws IOException {
  for(Path a=p.toAbsolutePath().normalize();a!=null;a=a.getParent())if(Files.exists(a,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(a)||!a.toRealPath().equals(a)))throw error("SET99_LINKED_PATH");
 }
 private Path pending(){return root.resolve("pending.properties");}private Path trial(){return root.resolve("trial.properties");}private Path catalog(){return root.resolve("catalog.properties");}
 private static String current(Path p)throws IOException {safe(p);return Files.exists(p,LinkOption.NOFOLLOW_LINKS)?ModDeployment.hash(p):"";}
 private static Properties read(Path p,int max)throws IOException {
  var result=new Properties();safe(p);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return result;
  if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>max)throw error("SET99_RECORD_LIMIT");
  // Reject duplicate keys: a damaged record cannot silently choose the last value.
  try(var reader=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
   Properties strict=new Properties(){@Override public synchronized Object put(Object key,Object value){if(containsKey(key))throw new IllegalArgumentException("duplicate");return super.put(key,value);}};
   strict.load(reader);result.putAll(strict);return result;
  }catch(IllegalArgumentException bad){throw error("SET99_RECORD_INVALID");}
 }
 private static void write(Path p,Properties properties)throws IOException {
  safe(p);Files.createDirectories(p.getParent());Path temp=Files.createTempFile(p.getParent(),".set99-",".tmp");
  try{try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){properties.store(out,"Genesis managed mod set99");}try(var c=FileChannel.open(temp,StandardOpenOption.WRITE)){c.force(true);}move(temp,p);}finally{Files.deleteIfExists(temp);}
 }
 private static void move(Path a,Path b)throws IOException {safe(a);safe(b);try{Files.move(a,b,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException no){Files.move(a,b,StandardCopyOption.REPLACE_EXISTING);}}
 private static void copy(Path a,Path b,String hash)throws IOException {
  safe(a);safe(b);Files.createDirectories(b.getParent());Path temp=Files.createTempFile(b.getParent(),".set99-",".part");
  try{Files.copy(a,temp,StandardCopyOption.REPLACE_EXISTING);if(!ModDeployment.hash(temp).equals(hash)||!ModDeployment.hash(a).equals(hash))throw error("SET99_COPY_CHANGED");try(var c=FileChannel.open(temp,StandardOpenOption.WRITE)){c.force(true);}move(temp,b);}finally{Files.deleteIfExists(temp);}
 }
 private Path dir(Properties p)throws IOException {String id=p.getProperty("transaction","");try{if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(IllegalArgumentException bad){throw error("SET99_TRANSACTION");}Path d=root.resolve("transactions").resolve(id);safe(d);return d;}
 private static List<Entry> sorted(Collection<Entry> entries){return entries.stream().sorted(Comparator.comparing(Entry::modId)).toList();}
 private static String digest(List<Entry> entries)throws IOException {
  try{var hash=MessageDigest.getInstance("SHA-256");try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),hash))){out.writeUTF("Genesis revision99");for(var e:sorted(entries)){out.writeUTF(e.project());out.writeUTF(e.modId());out.writeUTF(e.version());out.writeUTF(e.sha256());}}return HexFormat.of().formatHex(hash.digest());}catch(GeneralSecurityException impossible){throw new AssertionError(impossible);}
 }
 private Map<String,String> inventory()throws IOException {
  String before=DeploymentInventory98.capture(mods);var map=new TreeMap<String,String>();try(var stream=Files.newDirectoryStream(mods)){for(Path p:stream)if(p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))map.put(p.getFileName().toString(),ModDeployment.hash(p));}
  if(!before.equals(DeploymentInventory98.capture(mods)))throw error("SET99_INVENTORY_CHANGED");return Map.copyOf(map);
 }
 private List<Entry> owned(Properties known)throws IOException {
  var entries=new ArrayList<Entry>();try {
   for(String key:known.stringPropertyNames())if(key.endsWith(".sha256")&&!key.startsWith(".")){
    String id=key.substring(0,key.length()-7);entries.add(new Entry(known.getProperty(id+".project",""),id,known.getProperty(id+".version",""),known.getProperty(key)));
   }
   if(entries.size()>MAX_MODS||entries.stream().map(Entry::project).distinct().count()!=entries.size())throw error("SET99_CATALOG_INVALID");
   for(var e:entries)if(!known.getProperty("project."+e.project()+".modId","").equals(e.modId()))throw error("SET99_CATALOG_INVALID");
   return sorted(entries);
  }catch(IllegalArgumentException bad){throw error("SET99_CATALOG_INVALID");}
 }
 List<Entry> confirmed()throws IOException{return owned(read(catalog(),65536));}
 Review review(List<Input> inputs,Set<String> removals,Map<String,String> loaded)throws IOException {
  safe(root);safe(mods);Files.createDirectories(mods);
  if(Files.exists(pending())||Files.exists(trial())||Files.exists(root.resolve("world-review91.properties")))throw error("SET99_RECOVERY_OR_PENDING");
  if(inputs.size()>MAX_MODS||removals.size()>MAX_MODS||inputs.isEmpty()&&removals.isEmpty())throw error("SET99_EMPTY_OR_LIMIT");
  String catalogHash=current(catalog());var before=owned(read(catalog(),65536));var desired=new LinkedHashMap<String,Entry>();var sources=new HashMap<String,Path>();
  for(var e:before){if(!current(mods.resolve(e.file())).equals(e.sha256()))throw error("SET99_MANAGED_CHANGED");if(!e.version().equals(loaded.get(e.modId())))throw error("SET99_RUNTIME_DIFFERS");desired.put(e.project(),e);sources.put(e.modId(),mods.resolve(e.file()));}
  var seen=new HashSet<String>();
  for(String project:removals){try{projectId(project);}catch(IllegalArgumentException bad){throw error("SET99_PROJECT_ID");}if(desired.remove(project)==null)throw error("SET99_REMOVE_NOT_MANAGED");}
  for(Input input:inputs){
   if(!seen.add(input.project())||removals.contains(input.project()))throw error("SET99_DUPLICATE_PROJECT");
   if(!current(input.jar()).equals(input.sha256()))throw error("SET99_CANDIDATE_CHANGED");var meta=ModJarMetadata.read(input.jar());var old=desired.get(input.project());
   if(old!=null&&!old.modId().equals(meta.id()))throw error("SET99_MOD_ID_CHANGED");
   desired.put(input.project(),new Entry(input.project(),meta.id(),meta.version(),input.sha256()));sources.put(meta.id(),input.jar());
  }
  var after=sorted(desired.values());if(after.size()>MAX_MODS||after.stream().map(Entry::modId).distinct().count()!=after.size())throw error("SET99_DUPLICATE_MOD");
  var installed=inventory();Set<String> ownFiles=new HashSet<>();for(var e:before)ownFiles.add(e.file());
  // Retain all foreign files. An already loaded id outside this owned set is never adopted.
  Set<String> priorIds=new HashSet<>();for(var e:before)priorIds.add(e.modId());
  for(var e:after)if(!priorIds.contains(e.modId())&&(loaded.containsKey(e.modId())||installed.containsKey(e.file())))throw error("SET99_FOREIGN_MOD_CONFLICT");
  long total=0;for(var e:after){long size=Files.size(sources.get(e.modId()));if((total+=size)>2L*1024*1024*1024)throw error("SET99_SIZE_LIMIT");}
  var nextVersions=new HashMap<>(loaded);for(var e:before)nextVersions.remove(e.modId());for(var e:after)nextVersions.put(e.modId(),e.version());
  for(var e:after){Path source=sources.get(e.modId());var meta=ModJarMetadata.read(source);if(!meta.id().equals(e.modId())||!meta.version().equals(e.version())||!current(source).equals(e.sha256()))throw error("SET99_CANDIDATE_CHANGED");ModJarMetadata.compatible(meta,nextVersions,"CLIENT");}
  // Existing conservative duplicate scan is supplemented by all top-level metadata ids below.
  for(String name:installed.keySet())if(!ownFiles.contains(name)){
   var foreign=ModSetMetadata99.read(mods.resolve(name));
   for(var e:after)if(foreign.ids().contains(e.modId()))throw error("SET99_FOREIGN_MOD_CONFLICT");
   ModJarMetadata.compatible(new ModJarMetadata.Info("foreign","0",foreign.dependencies()),nextVersions,"CLIENT");
  }
  if(!current(catalog()).equals(catalogHash))throw error("SET99_CATALOG_CHANGED");
  var changes=new ArrayList<Change>();var oldByProject=new HashMap<String,Entry>();for(var e:before)oldByProject.put(e.project(),e);
  for(var e:after){var old=oldByProject.remove(e.project());if(old==null)changes.add(new Change("ADD",e.project(),e.modId(),"",e.version()));else if(!old.equals(e))changes.add(new Change("REPLACE",e.project(),e.modId(),old.version(),e.version()));}
  for(var e:sorted(oldByProject.values()))changes.add(new Change("REMOVE",e.project(),e.modId(),e.version(),""));
  if(changes.isEmpty())throw error("SET99_NO_CHANGES");
  return new Review(game.toString(),digest(after),digest(before),DeploymentInventory98.capture(mods),catalogHash,loaded,after,changes);
 }
 String stage(List<Input> inputs,Set<String> removals,Map<String,String> loaded,Review reviewed)throws IOException {
  if(!review(inputs,removals,loaded).equals(reviewed))throw error("SET99_REVIEW_CHANGED");
  var before=confirmed();var paths=new HashMap<String,Path>();for(var e:before)paths.put(e.project(),mods.resolve(e.file()));for(var input:inputs)paths.put(input.project(),input.jar());
  Properties journal=new Properties();journal.setProperty("setSchema","99");journal.setProperty("transaction",UUID.randomUUID().toString());journal.setProperty("modId","genesis_set");journal.setProperty("version",reviewed.revision());journal.setProperty("priorVersion",reviewed.priorRevision());journal.setProperty("sha256",reviewed.revision());journal.setProperty("priorHash",reviewed.priorRevision());journal.setProperty("project","set99");journal.setProperty("createdAt",Long.toString(System.currentTimeMillis()));
  pruneCompleted99();
  if(!Files.getFileStore(root).equals(Files.getFileStore(mods)))throw error("SET99_DIFFERENT_FILESYSTEM");
  long backupBytes=0,newBytes=0,maxScratch=0;for(var e:before){long size=Files.size(mods.resolve(e.file()));backupBytes+=size;maxScratch=Math.max(maxScratch,size);}for(var e:reviewed.entries()){long size=Files.size(paths.get(e.project()));newBytes+=size;maxScratch=Math.max(maxScratch,size);}
  if(Files.getFileStore(root).getUsableSpace()<backupBytes+2*newBytes+maxScratch+64L*1024*1024)throw error("SET99_SPACE_REQUIRED");
  Path d=dir(journal);Files.createDirectories(d);boolean published=false;
  try {
   // Do not prune unknown/active transactions. At the bound, require explicit maintenance.
   try(var dirs=Files.list(root.resolve("transactions"))){if(dirs.limit(65).count()>64)throw error("SET99_RETENTION_LIMIT");}
   var initialInventory=inventory();Plan plan=new Plan(before,reviewed.entries(),initialInventory,reviewed.catalogHash(),Files.exists(catalog()));
   for(var e:before)copy(mods.resolve(e.file()),d.resolve("before99").resolve(e.file()),e.sha256());
   for(var e:reviewed.entries())copy(paths.get(e.project()),d.resolve("after99").resolve(e.file()),e.sha256());
   if(plan.catalogExisted())copy(catalog(),d.resolve("catalog-before99.properties"),plan.catalogHash());
   writePlan(d.resolve("plan99.properties"),plan);journal.setProperty("plan99Sha",ModDeployment.hash(d.resolve("plan99.properties")));
   if(!review(inputs,removals,loaded).equals(reviewed))throw error("SET99_REVIEW_CHANGED");
   write(pending(),journal);published=true;return journal.getProperty("transaction");
  }finally{if(!published)deleteTree(d);}
 }
 private static void writeEntries(Properties p,String prefix,List<Entry> entries){p.setProperty(prefix+"count",Integer.toString(entries.size()));int n=0;for(var e:entries){String k=prefix+(n++)+".";p.setProperty(k+"project",e.project());p.setProperty(k+"id",e.modId());p.setProperty(k+"version",e.version());p.setProperty(k+"sha",e.sha256());}}
 private static List<Entry> readEntries(Properties p,String prefix)throws IOException {
  try{int count=Integer.parseInt(p.getProperty(prefix+"count"));if(count<0||count>MAX_MODS)throw new IllegalArgumentException();var list=new ArrayList<Entry>();for(int i=0;i<count;i++){String k=prefix+i+".";list.add(new Entry(p.getProperty(k+"project"),p.getProperty(k+"id"),p.getProperty(k+"version"),p.getProperty(k+"sha")));}if(list.stream().map(Entry::modId).distinct().count()!=count||list.stream().map(Entry::project).distinct().count()!=count)throw new IllegalArgumentException();return sorted(list);}catch(RuntimeException bad){throw error("SET99_PLAN_INVALID");}
 }
 private static void writePlan(Path file,Plan plan)throws IOException {
  Properties p=new Properties();p.setProperty("schema","99");p.setProperty("catalogHash",plan.catalogHash());p.setProperty("catalogExisted",Boolean.toString(plan.catalogExisted()));writeEntries(p,"before.",plan.before());writeEntries(p,"after.",plan.after());for(var e:plan.inventory().entrySet())p.setProperty("inventory."+e.getKey(),e.getValue());write(file,p);
 }
 private Plan plan(Properties journal)throws IOException {
  if(journal.containsKey("phase")&&(!Set.of("PREPARED","APPLIED","COMMITTED","RESTORED_REVIEW").contains(journal.getProperty("phase"))||!"true".equals(journal.getProperty("writer91"))||!Set.of("true","false").contains(journal.getProperty("worldRisk91"))||!Set.of("","INSTALLING","RESTORING").contains(journal.getProperty("setStep99",""))))throw error("SET99_RECORD_INVALID");
  Path file=dir(journal).resolve("plan99.properties");if(!current(file).equals(journal.getProperty("plan99Sha")))throw error("SET99_PLAN_CHANGED");Properties p=read(file,512*1024);if(!"99".equals(p.getProperty("schema"))||!Set.of("true","false").contains(p.getProperty("catalogExisted")))throw error("SET99_PLAN_INVALID");
  var map=new TreeMap<String,String>();for(String key:p.stringPropertyNames())if(key.startsWith("inventory.")){String name=key.substring(10);if(name.contains("/")||name.contains("\\")||!name.toLowerCase(Locale.ROOT).endsWith(".jar"))throw error("SET99_PLAN_INVALID");String hash=p.getProperty(key);try{requireHash(hash);}catch(IllegalArgumentException bad){throw error("SET99_PLAN_INVALID");}map.put(name,hash);}if(map.size()>512)throw error("SET99_PLAN_INVALID");
  var result=new Plan(readEntries(p,"before."),readEntries(p,"after."),Map.copyOf(map),p.getProperty("catalogHash",""),Boolean.parseBoolean(p.getProperty("catalogExisted")));
  if(!digest(result.before()).equals(journal.getProperty("priorHash"))||!digest(result.after()).equals(journal.getProperty("sha256")))throw error("SET99_PLAN_INVALID");return result;
 }
 private Map<String,String> expected(Plan p,boolean after){var map=new TreeMap<>(p.inventory());for(var e:p.before())map.remove(e.file());for(var e:p.after())map.remove(e.file());for(var e:after?p.after():p.before())map.put(e.file(),e.sha256());return Map.copyOf(map);}
 private void verifyCopies(Properties journal,Plan p)throws IOException {Path d=dir(journal);for(var e:p.before())if(!current(d.resolve("before99").resolve(e.file())).equals(e.sha256()))throw error("SET99_BACKUP_CHANGED");for(var e:p.after())if(!current(d.resolve("after99").resolve(e.file())).equals(e.sha256()))throw error("SET99_CANDIDATE_CHANGED");if(p.catalogExisted()&&!current(d.resolve("catalog-before99.properties")).equals(p.catalogHash()))throw error("SET99_BACKUP_CHANGED");}
 private Map<String,String> mixed(Plan p)throws IOException {
  var actual=inventory();var old=expected(p,false);var next=expected(p,true);var all=new TreeSet<String>();all.addAll(old.keySet());all.addAll(next.keySet());all.addAll(actual.keySet());
  for(String file:all){String value=actual.getOrDefault(file,"");if(!value.equals(old.getOrDefault(file,""))&&!value.equals(next.getOrDefault(file,"")))throw error("SET99_EXTERNAL_EDIT");}return actual;
 }
 private void writer()throws IOException {if(!ProfileLease92.ownsWriter92(game))throw error("SET99_PROFILE_WRITER_REQUIRED");}
 RecoveryView91 pendingView(Properties p)throws IOException {
  if(!isSet(p)||Files.exists(trial()))throw error("SET99_NO_PENDING");plan(p);return new RecoveryView91("PENDING99",revision(p,inventory()),p.getProperty("transaction"),"genesis_set",p.getProperty("version"),p.getProperty("priorVersion"),false);
 }
 private String ownerMessage99(Properties p){return "Genesis set owner99\n"+game+"\n"+p.getProperty("transaction")+"\n"+p.getProperty("plan99Sha")+"\n"+p.getProperty("boundConfig99")+"\n"+p.getProperty("boundInstance99");}
 private SupervisorConfig ownerConfig99()throws IOException {return SupervisorConfig.load(new String[]{"--config="+root.getParent().resolve("supervisor.properties")});}
 private void bindOwner99(Properties p)throws IOException {
  SupervisorConfig c=ownerConfig99();if(!OwnerLease94.owned(c.configPath())||!c.gameDir().equals(game))throw error("SET99_OWNER_REQUIRED");ColdOwner94.configUnchanged(c);
  p.setProperty("boundConfig99",c.configSha94());p.setProperty("boundInstance99",c.instanceId());p.setProperty("boundMac99",RecoveryAuth92.mac(c.token(),ownerMessage99(p)));
 }
 void verifyRecoveryOwner99(SupervisorConfig c,Properties p)throws IOException {
  writer();if(!OwnerLease94.owned(c.configPath())||!c.gameDir().equals(game)||!c.configPath().equals(root.getParent().resolve("supervisor.properties")))throw error("SET99_OWNER_REQUIRED");ColdOwner94.configUnchanged(c);
  if(!isSet(p)||!c.configSha94().equals(p.getProperty("boundConfig99"))||!c.instanceId().equals(p.getProperty("boundInstance99"))||!RecoveryAuth92.equal(p.getProperty("boundMac99"),RecoveryAuth92.mac(c.token(),ownerMessage99(p))))throw error("SET99_OWNER_BINDING_CHANGED");
  plan(p); // A bound hash of a now-missing/different generation is not a usable recovery plan.
 }
 String beforeLaunch(Properties journal)throws IOException {
  writer();Plan p=plan(journal);verifyCopies(journal,p);
  if(!inventory().equals(expected(p,false))||!current(catalog()).equals(p.catalogHash()))throw error("SET99_REVIEW_CHANGED");
  long age;try{age=System.currentTimeMillis()-Long.parseLong(journal.getProperty("createdAt"));}catch(RuntimeException bad){throw error("SET99_TIME");}if(age<0||age>30*60*1000L)throw error("SET99_EXPIRED");
  journal.setProperty("phase","PREPARED");journal.setProperty("writer91","true");journal.setProperty("worldRisk91","false");
  Path resume=root.getParent().resolve("resume.properties");if(Files.isRegularFile(resume)&&Files.size(resume)<=65536)copy(resume,dir(journal).resolve("resume.properties"),ModDeployment.hash(resume));
  // Conservative: a world-intent is already enough to require a choice after a failed trial.
  if("world".equals(read(resume,65536).getProperty("kind")))journal.setProperty("worldRisk91","true");
  journal.setProperty("startupProtocol101","101");journal.setProperty("worldAttempt101","false");journal.setProperty("abortStartup101","false");journal.setProperty("guardAck101","false");
  bindOwner99(journal);write(trial(),journal);Files.deleteIfExists(pending());
  install(journal,p,true);journal.setProperty("phase","APPLIED");write(trial(),journal);return journal.getProperty("transaction");
 }
 private void install(Properties journal,Plan p,boolean after)throws IOException {
  writer();verifyCopies(journal,p);Map<String,String> actual=mixed(p),goal=expected(p,after);var files=new TreeSet<String>();for(var e:p.before())files.add(e.file());for(var e:p.after())files.add(e.file());
  // PREPARED + immutable before/after snapshots make every individual step retryable.
  journal.setProperty("setStep99",after?"INSTALLING":"RESTORING");write(trial(),journal);
  for(String name:files){
   String expected=goal.getOrDefault(name,"");if(current(mods.resolve(name)).equals(expected))continue;
   // Recheck the entire mixed set before EACH replacement; do not overwrite foreign edits.
   mixed(p);if(expected.isEmpty())Files.delete(mods.resolve(name));else copy(dir(journal).resolve(after?"after99":"before99").resolve(name),mods.resolve(name),expected);
   checkpoint99(journal,name); // deterministic crash tests stop a separate JVM here; never activated by production properties.
  }
  if(!inventory().equals(goal))throw error("SET99_INSTALL_CHANGED");
 }
 private static void checkpoint99(Properties p,String name)throws IOException {java.util.function.Consumer<String> hook=testHook99.get();if(hook!=null)hook.accept(name);}
 static final ThreadLocal<java.util.function.Consumer<String>> testHook99=new ThreadLocal<>();
 void confirm(Properties journal,String token)throws IOException {
  if(!token.equals(journal.getProperty("transaction"))||!"APPLIED".equals(journal.getProperty("phase")))throw error("SET99_TRIAL_TOKEN");
  Plan p=plan(journal);verifyCopies(journal,p);if(!inventory().equals(expected(p,true)))throw error("SET99_CONFIRM_CHANGED");journal.setProperty("phase","COMMITTED");write(trial(),journal);finishCommit(journal);
 }
 void finishCommit(Properties journal)throws IOException {
  Plan p=plan(journal);verifyCopies(journal,p);if(!inventory().equals(expected(p,true)))throw error("SET99_COMMITTED_CHANGED");
  Properties known=read(catalog(),65536),goal=p.catalogExisted()?read(dir(journal).resolve("catalog-before99.properties"),65536):new Properties();
  for(var e:p.before()){for(String suffix:List.of(".sha256",".version",".project"))goal.remove(e.modId()+suffix);goal.remove("project."+e.project()+".modId");}
  for(var e:p.after()){goal.setProperty(e.modId()+".sha256",e.sha256());goal.setProperty(e.modId()+".version",e.version());goal.setProperty(e.modId()+".project",e.project());goal.setProperty("project."+e.project()+".modId",e.modId());}
  goal.setProperty(".set99.revision",journal.getProperty("sha256"));
  if(!current(catalog()).equals(p.catalogHash())&&!known.equals(goal))throw error("SET99_CATALOG_CHANGED");
  if(!known.equals(goal))write(catalog(),goal);
  report(journal,"MANUAL".equals(journal.getProperty("accept91"))?"MANUALLY_ACCEPTED91":"CONFIRMED");completed99(journal,"COMMITTED");clear(journal); // immutable generations retained for bounded explicit recovery, never auto-load old worlds.
 }
 private void clear(Properties journal)throws IOException {String token=journal.getProperty("transaction");if(token.equals(read(pending(),65536).getProperty("transaction")))Files.delete(pending());if(token.equals(read(trial(),65536).getProperty("transaction")))Files.delete(trial());}
 private void report(Properties p,String status)throws IOException {Properties result=new Properties();result.putAll(p);result.setProperty("status",status);result.setProperty("detail","Managed mod set; world files are never restored");write(root.resolve("last-result.properties"),result);}
 void cancel(Properties journal,String token)throws IOException {if(!token.equals(journal.getProperty("transaction")))return;if(Files.exists(trial()))throw error("SET99_TRIAL_ACTIVE");Files.deleteIfExists(pending());deleteTree(dir(journal));}
 boolean rollback(Properties journal)throws IOException {
  writer();if("COMMITTED".equals(journal.getProperty("phase"))){finishCommit(journal);return false;}
  if(!"RESTORE_REQUESTED".equals(journal.getProperty("recovery91"))&&(!"true".equals(journal.getProperty("writer91"))||"true".equals(journal.getProperty("worldRisk91"))))throw error("RECOVERY91_CHOICE_REQUIRED");
  Plan p=plan(journal);if(!current(catalog()).equals(p.catalogHash()))throw error("SET99_CATALOG_CHANGED");
  // Mark the world hold BEFORE changing files. A crash during restoration cannot enable auto world entry.
  hold();install(journal,p,false);
  Properties review=new Properties();review.putAll(journal);review.setProperty("phase","RESTORED_REVIEW");write(root.resolve("world-review91.properties"),review);report(journal,"ROLLED_BACK");completed99(journal,"RESTORED");clear(journal);return true;
 }
 private void hold()throws IOException {Properties hold=new Properties();hold.setProperty("id",UUID.randomUUID().toString());hold.setProperty("sourcePid",Long.toString(ProcessHandle.current().pid()));hold.setProperty("createdAt",Long.toString(System.currentTimeMillis()));hold.setProperty("kind","hold91");write(root.getParent().resolve("resume.properties"),hold);}
 private String revision(Properties journal,Map<String,String> actual)throws IOException {Properties p=new Properties();p.putAll(journal);for(var e:actual.entrySet())p.setProperty("actual."+e.getKey(),e.getValue());try{var hash=MessageDigest.getInstance("SHA-256");for(String key:new TreeSet<>(p.stringPropertyNames())){hash.update(key.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);hash.update(p.getProperty(key).getBytes(StandardCharsets.UTF_8));hash.update((byte)0);}return HexFormat.of().formatHex(hash.digest());}catch(GeneralSecurityException bad){throw new AssertionError(bad);}}
 RecoveryView91 view(Properties journal)throws IOException {
  Plan p=plan(journal);var actual=mixed(p);String phase=journal.getProperty("phase","");
  if(!Set.of("PREPARED","APPLIED","RESTORED_REVIEW").contains(phase))throw error("SET99_PHASE");
  if(phase.equals("RESTORED_REVIEW")&&!actual.equals(expected(p,false)))throw error("SET99_RESTORE_CHANGED");
  return new RecoveryView91(phase.equals("RESTORED_REVIEW")?phase:"RESTORE_REQUESTED".equals(journal.getProperty("recovery91"))?"RESTORE_REQUESTED":"CHOICE_REQUIRED",revision(journal,actual),journal.getProperty("transaction"),"genesis_set",journal.getProperty("version"),journal.getProperty("priorVersion"),phase.equals("APPLIED")&&actual.equals(expected(p,true))&&!Files.exists(root.resolve("world-review91.properties")));
 }
 void decide(Properties journal,String revision,String action)throws IOException {
  var v=view(journal);if(!v.revision().equals(revision))throw error("RECOVERY91_STALE");
  if(action.equals("ACK")&&v.phase().equals("RESTORED_REVIEW")){Files.delete(root.resolve("world-review91.properties"));return;}
  if(action.equals("KEEP")&&v.keepAllowed()){verifyCopies(journal,plan(journal));journal.setProperty("accept91","MANUAL");journal.setProperty("phase","COMMITTED");write(trial(),journal);finishCommit(journal);return;}
  if(action.equals("RESTORE")&&!v.phase().equals("RESTORED_REVIEW")){journal.setProperty("worldRisk91","true");journal.setProperty("recovery91","RESTORE_REQUESTED");write(trial(),journal);return;}throw error("RECOVERY91_ACTION");
 }
 void protect(Properties journal,String token)throws IOException {
  if(!token.equals(journal.getProperty("transaction"))||!"APPLIED".equals(journal.getProperty("phase"))||"RESTORE_REQUESTED".equals(journal.getProperty("recovery91"))||"true".equals(journal.getProperty("abortStartup101")))throw error("SET99_TRIAL_TOKEN");
  if(!inventory().equals(expected(plan(journal),true)))throw error("SET99_TRIAL_CHANGED");journal.setProperty("worldRisk91","true");if("101".equals(journal.getProperty("startupProtocol101")))journal.setProperty("worldAttempt101","true");write(trial(),journal);
 }
 String retry(Properties journal,String revision)throws IOException {
  writer();var v=view(journal);if(!v.revision().equals(revision)||!v.keepAllowed())throw error("RECOVERY91_STALE");hold();journal.remove("recovery91");journal.setProperty("abortStartup101","false");journal.setProperty("worldRisk91","true");journal.setProperty("retryAttempt92",UUID.randomUUID().toString());write(trial(),journal);return journal.getProperty("transaction");
 }
 boolean bind101(Properties p,String token,String launch,String nonce)throws IOException {
  if(!"101".equals(p.getProperty("startupProtocol101")))return false;
  if(!token.equals(p.getProperty("transaction"))||!"APPLIED".equals(p.getProperty("phase")))throw error("SET99_TRIAL_TOKEN");
  UUID.fromString(launch);if(!nonce.matches("[a-f0-9]{64}"))throw error("STARTUP101_NONCE");
  p.setProperty("guardLaunch101",launch);p.setProperty("guardNonce101",StartupAbort101.sha(nonce));p.setProperty("guardAck101","false");write(trial(),p);return true;
 }
 private boolean bound101(Properties p,String token,String launch,String nonce)throws IOException {
  plan(p);return "101".equals(p.getProperty("startupProtocol101"))&&"APPLIED".equals(p.getProperty("phase"))&&token.equals(p.getProperty("transaction"))&&launch.equals(p.getProperty("guardLaunch101"))&&StartupAbort101.sha(nonce).equals(p.getProperty("guardNonce101"));
 }
 boolean acknowledge101(Properties p,String token,String launch,String nonce)throws IOException {
  if(!bound101(p,token,launch,nonce)||!"false".equals(p.getProperty("worldAttempt101"))||!"false".equals(p.getProperty("abortStartup101")))return false;
  p.setProperty("guardAck101","true");write(trial(),p);return true;
 }
 boolean abort101(Properties p,String token,String launch,String nonce)throws IOException {
  if(!bound101(p,token,launch,nonce)||!"true".equals(p.getProperty("guardAck101"))||!"false".equals(p.getProperty("worldAttempt101")))return false;
  p.setProperty("abortStartup101","true");write(trial(),p);return true;
 }
 void verifyComplete(Properties journal)throws IOException {if(!inventory().equals(expected(plan(journal),true)))throw error("SET99_INCOMPLETE_TRIAL");}
 String describe(Properties journal,String project)throws IOException {
  Plan p=plan(journal);if(p.before().stream().noneMatch(e->e.project().equals(project))&&p.after().stream().noneMatch(e->e.project().equals(project)))return "";
  return "Managed set: "+journal.getProperty("status",journal.getProperty("phase","PENDING"))+" · "+journal.getProperty("sha256","")+"\nMods in revision: "+p.after().size()+"\n";
 }
 private void completed99(Properties journal,String status)throws IOException {
  Properties done=new Properties();done.putAll(journal);done.setProperty("done99",status);done.setProperty("doneAt99",Long.toString(System.currentTimeMillis()));write(dir(journal).resolve("done99.properties"),done);
 }
 private void pruneCompleted99()throws IOException {
  Path transactions=root.resolve("transactions");if(!Files.isDirectory(transactions))return;
  Set<String> active=new HashSet<>();for(Path f:List.of(pending(),trial(),root.resolve("world-review91.properties"),root.resolve("last-result.properties"))){String id=read(f,65536).getProperty("transaction","");if(!id.isEmpty())active.add(id);}
  var completed=new ArrayList<Properties>();int scanned=0,protectedCompleted=0;
  try(var dirs=Files.newDirectoryStream(transactions)){for(Path d:dirs){if(++scanned>64)throw error("SET99_RETENTION_LIMIT");if(active.contains(d.getFileName().toString())){if(Files.isRegularFile(d.resolve("done99.properties"),LinkOption.NOFOLLOW_LINKS))protectedCompleted++;continue;}
   Path marker=d.resolve("done99.properties");if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))continue;
   try{Properties p=read(marker,65536);if(!Set.of("COMMITTED","RESTORED").contains(p.getProperty("done99"))||!dir(p).equals(d)||!p.getProperty("doneAt99","").matches("[0-9]{1,16}"))continue;Plan plan=plan(p);verifyCopies(p,plan);
    Set<String> allowed=new HashSet<>(Set.of("plan99.properties","done99.properties","catalog-before99.properties","resume.properties","before99","after99"));for(var e:plan.before())allowed.add("before99/"+e.file());for(var e:plan.after())allowed.add("after99/"+e.file());boolean foreign=false;
    try(var paths=Files.walk(d)){for(Path file:paths.toList()){safe(file);String relative=d.relativize(file).toString().replace('\\','/');if(!relative.isEmpty()&&!allowed.contains(relative)){foreign=true;break;}}}if(!foreign)completed.add(p);
   }catch(IOException|RuntimeException unknown){/* Changed/foreign evidence is retained, not deleted. */}
  }}
  completed.sort(Comparator.comparingLong(p->Long.parseLong(p.getProperty("doneAt99"))));
  for(int i=0;i<completed.size()-Math.max(0,8-protectedCompleted);i++)deleteTree(dir(completed.get(i)));
 }
 private static void deleteTree(Path d)throws IOException {safe(d);if(!Files.exists(d))return;try(var paths=Files.walk(d)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList()){safe(p);Files.delete(p);}}}
}
