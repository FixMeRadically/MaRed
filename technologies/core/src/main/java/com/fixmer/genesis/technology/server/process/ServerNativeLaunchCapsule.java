package com.fixmer.genesis.technology.server.process;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import static com.fixmer.genesis.technology.server.process.ServerNativeOwnerProtocol.Binding;

/** Frozen local launch data; PREPARED_SERVER is deliberately not runnable by native45. */
final class ServerNativeLaunchCapsule {
    static final int MAX_BYTES=262144,DIAGNOSTIC=1,PREPARED_SERVER=2;
    
    record Pin(String path,byte[] hash){Pin{hash=hash.clone();}@Override public byte[] hash(){return hash.clone();}}
    record Plan(int kind,Binding binding,int grace,int forceGrace,boolean orphanForce,int lease,String module,String cwd,List<String> arguments,Map<String,String> environment,List<Pin> pins){
        Plan{arguments=List.copyOf(arguments);var sorted=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);environment.forEach((k,v)->{if(sorted.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Duplicate environment");});environment=Collections.unmodifiableMap(sorted);pins=List.copyOf(pins);}
    }
    record Frozen(Path file,String sha,Plan plan){}
    static byte[] encode(Plan p)throws IOException {
        if(p.kind()!=1&&p.kind()!=2||p.grace()<100||p.grace()>300000||p.forceGrace()<100||p.forceGrace()>30000||p.lease()<1000||p.lease()>300000||p.arguments().isEmpty()||p.arguments().size()>128||p.environment().isEmpty()||p.environment().size()>32||p.pins().isEmpty()||p.pins().size()>17||!p.pins().get(0).path().equals(p.module()))throw new IOException("CAPSULE_LIMIT");
        long commandUnits=quotedUnits(p.module()),environmentUnits=1;for(String arg:p.arguments())commandUnits+=1+quotedUnits(arg);for(var entry:p.environment().entrySet())environmentUnits+=entry.getKey().length()+entry.getValue().length()+2;if(commandUnits>32760||environmentUnits>32760)throw new IOException("CAPSULE_WINDOWS_TEXT_LIMIT");
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.write(new byte[]{'G','J','A','V','A','4','5',0});i32(out,p.kind());i64(out,p.binding().epoch());i32(out,p.grace());i32(out,p.forceGrace());i32(out,p.orphanForce()?1:0);i32(out,p.lease());out.write(p.binding().run().toString().getBytes(StandardCharsets.US_ASCII));out.write(p.binding().nonce().getBytes(StandardCharsets.US_ASCII));i32(out,p.arguments().size());i32(out,p.environment().size());i32(out,p.pins().size());text(out,p.module(),false,4096);text(out,p.cwd(),false,4096);
        for(String arg:p.arguments())text(out,arg,true,4096);
        Set<String> allowed=Set.of("JAVA_HOME","PATH","SYSTEMROOT","TEMP","TMP","WINDIR","GENESIS_SERVER_SUPERVISED","GENESIS_SERVER_RUN_ID","GENESIS_SERVER_RUN_DIR");
        for(var e:p.environment().entrySet()){if(!allowed.contains(e.getKey().toUpperCase(Locale.ROOT)))throw new IOException("CAPSULE_ENVIRONMENT_NOT_ALLOWED");text(out,e.getKey(),false,128);text(out,e.getValue(),true,4096);}
        Set<String> seen=new HashSet<>();for(Pin pin:p.pins()){if(pin.hash().length!=32||!seen.add(pin.path().toUpperCase(Locale.ROOT)))throw new IOException("CAPSULE_PIN_INVALID");text(out,pin.path(),false,4096);out.write(pin.hash());}
        out.flush();if(bytes.size()>MAX_BYTES)throw new IOException("CAPSULE_LIMIT");return bytes.toByteArray();
    }
    private static long quotedUnits(String value){long units=2L+value.length(),slashes=0;for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='\\')slashes++;else{if(c=='"')units+=slashes+1;slashes=0;}}return units+slashes;}
    private static void text(DataOutputStream out,String value,boolean empty,int max)throws IOException {
        if(value==null||(!empty&&value.isEmpty())||value.length()>max)throw new IOException("CAPSULE_TEXT_LIMIT");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<32||Character.isLowSurrogate(c))throw new IOException("CAPSULE_TEXT_INVALID");if(Character.isHighSurrogate(c)&&(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i))))throw new IOException("CAPSULE_TEXT_INVALID");}
        i32(out,value.length());out.write(value.getBytes(StandardCharsets.UTF_16LE));
    }
    private static void i32(DataOutputStream out,int n)throws IOException{out.writeInt(Integer.reverseBytes(n));}
    private static void i64(DataOutputStream out,long n)throws IOException{out.writeLong(Long.reverseBytes(n));}
    static byte[] hash(Path file,long max)throws IOException {
        ServerFiles.safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("CAPSULE_FILE_MISSING");
        try{var sha=MessageDigest.getInstance("SHA-256");long total=0;try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;){total+=n;if(total>max)throw new IOException("CAPSULE_FILE_LIMIT");sha.update(b,0,n);}}return sha.digest();}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static void createRoot(Path root)throws IOException{ServerFiles.safe(root);if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("CAPSULE_DIRECTORY_EXISTS");Files.createDirectory(root);ServerFiles.privateMode(root,true);}
    static Frozen server(ServerProcessConfig config,Path root,Binding binding)throws IOException {
        root=root.toAbsolutePath().normalize();config.validateLaunch();ServerFiles.safe(config.workDir());ServerFiles.safe(config.javaHome());if(root.startsWith(config.workDir())||root.startsWith(config.javaHome()))throw new IOException("CAPSULE_DIRECTORY_INSIDE_RUNTIME");createRoot(root);var args=new ArrayList<String>();var pins=new ArrayList<Pin>();Path module=config.javaExecutable().toAbsolutePath().normalize();pins.add(new Pin(module.toString(),hash(module,32L*1024*1024)));
        var copied=new LinkedHashMap<Path,Path>();long total=0;
        for(String argument:config.arguments()){
            if(argument.startsWith("@")&&!argument.startsWith("@@")){
                Path original=ServerProcessConfig.resolve(config.workDir(),argument.substring(1));Path frozen=copied.get(original);
                if(frozen==null){if(copied.size()>=16)throw new IOException("CAPSULE_ARGFILE_LIMIT");byte[] before=hash(original,1024*1024);byte[] data=bounded(original,1024*1024);if(!MessageDigest.isEqual(before,digest(data))||!MessageDigest.isEqual(before,hash(original,1024*1024)))throw new IOException("CAPSULE_INPUT_CHANGED");total+=data.length;if(data.length>1024*1024||total>4L*1024*1024)throw new IOException("CAPSULE_ARGFILE_LIMIT");frozen=root.resolve("args-"+copied.size()+".txt");writeNew(frozen,data);copied.put(original,frozen);pins.add(new Pin(frozen.toString(),hash(frozen,1024*1024)));}
                args.add("@"+frozen);
            }else args.add(argument);
        }
        if(!MessageDigest.isEqual(pins.get(0).hash(),hash(module,32L*1024*1024)))throw new IOException("CAPSULE_JAVA_CHANGED");
        Plan plan=new Plan(PREPARED_SERVER,binding,(int)config.graceMs(),(int)config.forceGraceMs(),config.orphanForce(),120000,module.toString(),config.workDir().toString(),args,environment(module,root),pins);return publish(root,plan);
    }
    static Frozen probe(Path root,Path executable,byte[] code,String mode,boolean orphanForce,Binding binding)throws IOException {
        if(!Set.of("normal","ignore","flood","root-exit").contains(mode)||code.length<8||code.length>1024*1024)throw new IOException("CAPSULE_PROBE_INVALID");
        root=root.toAbsolutePath().normalize();executable=executable.toAbsolutePath().normalize();createRoot(root);
        String name="com.fixmer.genesis.technology.server.process.ServerNativeJavaProbe";Path classes=root.resolve("classes"),target=classes.resolve(name.replace('.','/')+".class");ServerFiles.directory(target.getParent());writeNew(target,code);
        Plan plan=new Plan(DIAGNOSTIC,binding,1000,3000,orphanForce,120000,executable.toString(),root.toString(),List.of("-cp",classes.toString(),name,mode,"--genesis-native-probe46"),environment(executable,root),List.of(new Pin(executable.toString(),hash(executable,32L*1024*1024)),new Pin(target.toString(),hash(target,1024*1024))));return publish(root,plan);
    }
    private static final String PROBE50="com.fixmer.genesis.technology.server.process.ServerNativeLifecycleProbe";
    static final List<String> LIFECYCLE50_CLASSES=List.of("ServerNativeLifecycleProbe","ServerLifecycleReporter","ServerLifecycleReporter$Stop","ServerLifecycleReporter$Save","ServerLifecycleState","ServerLifecycleState$Phase","ServerLifecycleState$Operation","ServerLifecycleState$Outcome","ServerLifecycleState$Snapshot","ServerLifecycleChannel","ServerLifecycleChannel$Request","ServerFiles","ServerFiles$1","ServerFiles$Lease");
    private static byte[] resource50(String name)throws IOException {
        try(var in=ServerNativeLaunchCapsule.class.getResourceAsStream(name+".class")){if(in==null)throw new IOException("LIFECYCLE50_CLASS_MISSING");byte[] data=in.readNBytes(256*1024+1);if(data.length<8||data.length>256*1024)throw new IOException("LIFECYCLE50_CLASS_LIMIT");return data;}
    }
    static void verifyLifecycleJar(Path jar)throws IOException {
        byte[] bytes=bounded(jar,1024*1024);Set<String> seen=new HashSet<>();int total=0;
        try(var in=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))){for(java.util.zip.ZipEntry entry;(entry=in.getNextEntry())!=null;){String prefix="com/fixmer/genesis/technology/server/process/",name=entry.getName();if(!name.startsWith(prefix)||!name.endsWith(".class"))throw new IOException("LIFECYCLE50_JAR_ENTRY");String simple=name.substring(prefix.length(),name.length()-6);if(!LIFECYCLE50_CLASSES.contains(simple)||!seen.add(simple))throw new IOException("LIFECYCLE50_JAR_ENTRY");byte[] data=in.readNBytes(256*1024+1);total+=data.length;if(data.length>256*1024||total>1024*1024||!Arrays.equals(data,resource50(simple)))throw new IOException("LIFECYCLE50_JAR_BYTES");}}
        if(seen.size()!=LIFECYCLE50_CLASSES.size())throw new IOException("LIFECYCLE50_JAR_INCOMPLETE");
    }
    static Frozen lifecycleProbe(Path root,Path executable,String mode,Binding binding,Path run)throws IOException {
        if(!Set.of("normal","save-fail","stop-crash","stall").contains(mode))throw new IOException("LIFECYCLE50_MODE");
        root=root.toAbsolutePath().normalize();executable=executable.toAbsolutePath().normalize();run=run.toAbsolutePath().normalize();
        if(!run.equals(root.getParent().resolve("state/runs").resolve(binding.run().toString())))throw new IOException("LIFECYCLE50_PRIVATE_RUN_REQUIRED");createRoot(root);
        var bytes=new ByteArrayOutputStream();try(var out=new JarOutputStream(bytes)){for(String name:LIFECYCLE50_CLASSES){var entry=new JarEntry("com/fixmer/genesis/technology/server/process/"+name+".class");entry.setTime(0);out.putNextEntry(entry);out.write(resource50(name));out.closeEntry();}}
        if(bytes.size()>1024*1024)throw new IOException("LIFECYCLE50_JAR_LIMIT");Path jar=root.resolve("probe50.jar");writeNew(jar,bytes.toByteArray());verifyLifecycleJar(jar);
        var env=new TreeMap<String,String>(environment(executable,root));env.put("GENESIS_SERVER_SUPERVISED","1");env.put("GENESIS_SERVER_RUN_ID",binding.run().toString());env.put("GENESIS_SERVER_RUN_DIR",run.toString());
        Plan plan=new Plan(DIAGNOSTIC,binding,1000,3000,false,120000,executable.toString(),root.toString(),List.of("-cp",jar.toString(),PROBE50,mode,"--genesis-native-lifecycle50"),env,List.of(new Pin(executable.toString(),hash(executable,32L*1024*1024)),new Pin(jar.toString(),hash(jar,1024*1024))));verifyDiagnosticShape(plan,run);return publish(root,plan);
    }
    static void verifyDiagnosticShape(Plan plan,Path run)throws IOException {
        Path root=Path.of(plan.cwd());var args=plan.arguments();if(plan.kind()!=DIAGNOSTIC||args.size()!=5||!args.get(0).equals("-cp")||plan.pins().size()!=2)throw new IOException("FIXED_DIAGNOSTIC_REQUIRED");
        if(args.get(2).equals(PROBE50)&&args.get(4).equals("--genesis-native-lifecycle50")){
            Path jar=root.resolve("probe50.jar"),expected=root.getParent().resolve("state/runs").resolve(plan.binding().run().toString());
            if(!expected.equals(run)||!args.get(1).equals(jar.toString())||!plan.pins().get(1).path().equals(jar.toString())||!Set.of("normal","save-fail","stop-crash","stall").contains(args.get(3))||!new HashSet<>(plan.environment().keySet()).equals(Set.of("JAVA_HOME","PATH","SystemRoot","WINDIR","TEMP","TMP","GENESIS_SERVER_SUPERVISED","GENESIS_SERVER_RUN_ID","GENESIS_SERVER_RUN_DIR"))||!"1".equals(plan.environment().get("GENESIS_SERVER_SUPERVISED"))||!plan.binding().run().toString().equals(plan.environment().get("GENESIS_SERVER_RUN_ID"))||!expected.toString().equals(plan.environment().get("GENESIS_SERVER_RUN_DIR")))throw new IOException("LIFECYCLE50_FIXED_SCOPE_REQUIRED");verifyLifecycleJar(jar);return;
        }
        String probe="com.fixmer.genesis.technology.server.process.ServerNativeJavaProbe";if(!args.get(1).equals(root.resolve("classes").toString())||!args.get(2).equals(probe)||!Set.of("normal","ignore","flood","root-exit").contains(args.get(3))||!args.get(4).equals("--genesis-native-probe46")||!plan.pins().get(1).path().equals(root.resolve("classes").resolve(probe.replace('.','/')+".class").toString()))throw new IOException("FIXED_PROBE46_REQUIRED");
    }
    static Map<String,String> environment(Path java,Path root)throws IOException {
        String system=System.getenv("SystemRoot");if(system==null)system=System.getProperty("os.name").startsWith("Windows")?"C:\\Windows":"/windows-model";
        Path tmp=root.resolve("temp");ServerFiles.directory(tmp);return Map.of("JAVA_HOME",java.getParent().getParent().toString(),"PATH",java.getParent()+";"+system+"\\System32","SystemRoot",system,"WINDIR",system,"TEMP",tmp.toString(),"TMP",tmp.toString());
    }
    static byte[] bounded(Path file,int max)throws IOException {
        ServerFiles.safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("CAPSULE_FILE_MISSING");
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] result=in.readNBytes(max+1);if(result.length>max)throw new IOException("CAPSULE_FILE_LIMIT");return result;}
    }
    static byte[] digest(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static Plan decode(byte[] bytes)throws IOException {
        if(bytes.length>MAX_BYTES)throw new IOException("CAPSULE_LIMIT");
        try{
            ByteBuffer in=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);byte[] magic=new byte[8];in.get(magic);if(!Arrays.equals(magic,new byte[]{'G','J','A','V','A','4','5',0}))throw new IOException("CAPSULE_MAGIC");
            int kind=in.getInt();long epoch=in.getLong();int grace=in.getInt(),force=in.getInt(),orphan=in.getInt(),lease=in.getInt();
            if(orphan!=0&&orphan!=1)throw new IOException("CAPSULE_FLAG");
            byte[] run=new byte[36],nonce=new byte[64];in.get(run);in.get(nonce);String id=new String(run,StandardCharsets.US_ASCII);if(!ServerFiles.canonicalUuid(id))throw new IOException("CAPSULE_BINDING");
            Binding binding=new Binding(UUID.fromString(id),epoch,new String(nonce,StandardCharsets.US_ASCII));int argc=in.getInt(),envc=in.getInt(),pinc=in.getInt();
            if(argc<1||argc>128||envc<1||envc>32||pinc<1||pinc>17)throw new IOException("CAPSULE_LIMIT");
            String module=readText(in,4096),cwd=readText(in,4096);var args=new ArrayList<String>();for(int i=0;i<argc;i++)args.add(readText(in,4096));
            var env=new LinkedHashMap<String,String>();for(int i=0;i<envc;i++)if(env.putIfAbsent(readText(in,128),readText(in,4096))!=null)throw new IOException("CAPSULE_ENVIRONMENT_DUPLICATE");
            var pins=new ArrayList<Pin>();for(int i=0;i<pinc;i++){String path=readText(in,4096);byte[] hash=new byte[32];in.get(hash);pins.add(new Pin(path,hash));}
            Plan plan=new Plan(kind,binding,grace,force,orphan==1,lease,module,cwd,args,env,pins);
            // Re-encoding enforces canonical order, text validity and no trailing bytes.
            if(!Arrays.equals(bytes,encode(plan)))throw new IOException("CAPSULE_NONCANONICAL");return plan;
        }catch(BufferUnderflowException|IllegalArgumentException invalid){throw new IOException("CAPSULE_INVALID",invalid);}
    }
    private static String readText(ByteBuffer in,int max)throws IOException {
        int n=in.getInt();if(n<0||n>max||n>in.remaining()/2)throw new IOException("CAPSULE_TEXT_LIMIT");byte[] bytes=new byte[n*2];in.get(bytes);
        try{return StandardCharsets.UTF_16LE.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(java.nio.charset.CharacterCodingException bad){throw new IOException("CAPSULE_TEXT_INVALID",bad);}
    }
    static Frozen verify(Path file,String expected)throws IOException {
        file=file.toAbsolutePath().normalize();if(expected==null||!expected.matches("[0-9a-f]{64}"))throw new IOException("CAPSULE_HASH_REQUIRED");
        byte[] bytes=bounded(file,MAX_BYTES);if(!MessageDigest.isEqual(digest(bytes),HexFormat.of().parseHex(expected)))throw new IOException("CAPSULE_HASH_MISMATCH");Plan plan=decode(bytes);
        long total=0;for(Pin pin:plan.pins()){Path path=Path.of(pin.path());if(!path.isAbsolute()||!path.normalize().equals(path))throw new IOException("CAPSULE_PIN_PATH");long limit=path.toString().equals(plan.module())?32L*1024*1024:1024*1024;ServerFiles.safe(path);total+=Files.size(path);if(total>36L*1024*1024||!MessageDigest.isEqual(pin.hash(),hash(path,limit)))throw new IOException("CAPSULE_PIN_CHANGED");}
        ServerFiles.safe(Path.of(plan.cwd()));if(!Files.isDirectory(Path.of(plan.cwd()),LinkOption.NOFOLLOW_LINKS))throw new IOException("CAPSULE_CWD_MISSING");return new Frozen(file,expected,plan);
    }
    static Frozen publish(Path root,Plan plan)throws IOException {byte[] bytes=encode(plan);Path file=root.resolve("launch.bin");writeNew(file,bytes);return new Frozen(file,HexFormat.of().formatHex(hash(file,MAX_BYTES)),plan);}
    private static void writeNew(Path file,byte[] bytes)throws IOException{try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){ServerFiles.privateMode(file,false);ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())channel.write(b);channel.force(true);}}
    static Map<String,Object> describe(Frozen frozen){var p=frozen.plan();var out=new LinkedHashMap<String,Object>();out.put("schema",45);out.put("kind",p.kind()==DIAGNOSTIC?"DIAGNOSTIC":"PREPARED_SERVER_DISABLED");out.put("capsule",frozen.file().toString());out.put("sha256",frozen.sha());out.put("arguments",p.arguments().size());out.put("pinnedFiles",p.pins().size());out.put("nativeGameLaunchAllowed",false);out.put("gameLaunched",false);out.put("pinnedScope","java-executable-and-argument-files-only");out.put("runtimeDependenciesVerified",false);out.put("installAuthority",false);return out;}
}
