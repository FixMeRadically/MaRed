package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.storage.RepositoryPath;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Local source projects only. No compiler, class loader, script engine or network access. */
public final class SourceProjects {
    public static final int MAX_BYTES = 4 * 1024 * 1024, MAX_FILES = 2000;
    private static final String MANIFEST = "genesis-project.properties";
    public enum Language {
        JAVA("Java", "java", "Main.java", "public final class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, Genesis\");\n    }\n}\n"),
        PYTHON("Python", "py", "main.py", "def main():\n    print(\"Hello, Genesis\")\n\n\nif __name__ == \"__main__\":\n    main()\n"),
        RUST("Rust", "rs", "main.rs", "fn main() {\n    println!(\"Hello, Genesis\");\n}\n"),
        CPP("C++", "cpp", "main.cpp", "#include <iostream>\n\nint main() {\n    std::cout << \"Hello, Genesis\\n\";\n    return 0;\n}\n");
        public final String label, extension, entry, starter;
        Language(String label, String extension, String entry, String starter) { this.label=label;this.extension=extension;this.entry=entry;this.starter=starter; }
    }
    public record Project(String id, Language language) {}
    public record Catalog(List<Project> projects, List<String> errors) { public Catalog { projects=List.copyOf(projects);errors=List.copyOf(errors); } }
    public record Recovery(String baseline, String text) {}
    private final Path root;private boolean dependencyUpdate;
    /** When non-null, {@link #safe(Path)} binds to a linked Minecraft working tree instead of {@link #root}. */
    private Path temporaryBound;
    public SourceProjects(Path root) { this.root=Objects.requireNonNull(root).toAbsolutePath().normalize(); }
    public Path root() { return root; }
    private Path bound(){return temporaryBound!=null?temporaryBound:root;}
    private <T> T withBound(Path bound,CallableIO<T> action)throws IOException{
        Path previous=temporaryBound;temporaryBound=Objects.requireNonNull(bound).toAbsolutePath().normalize();
        try{return action.call();}finally{temporaryBound=previous;}
    }
    @FunctionalInterface private interface CallableIO<T>{T call()throws IOException;}
    public static boolean validProject(String id) { return com.fixmer.genesis.technology.storage.ProjectPath.valid(id)&&id.indexOf('/')<0&&!id.startsWith("."); }
    public static boolean validFile(String path) {
        if (!RepositoryPath.valid(path)) return false;
        if(Set.of("build.gradle","settings.gradle","gradle.properties","build.gradle.kts","settings.gradle.kts","gradlew","gradlew.bat","gradle/wrapper/gradle-wrapper.properties").contains(path))return true;
        if(!path.startsWith("src/")&&!path.startsWith("tests/"))return false;
        String leaf=RepositoryPath.leaf(path).toLowerCase(Locale.ROOT);
        return leaf.matches(".+\\.(java|py|rs|cpp|c|h|hpp|json|txt|md|toml|properties)");
    }
    private static IOException fail(String reason) { return new IOException(reason); }
    /**
     * Accepts paths under either the current bound (linked workspace) or the managed root.
     * Linked projects keep their genesis-project.properties under root/id while their
     * working tree lives outside; both must remain addressable during a withBound() block.
     */
    private void safe(Path path) throws IOException {
        Path bound=bound();
        Path normalized=path.normalize();
        boolean underBound=normalized.startsWith(bound);
        boolean underRoot=normalized.startsWith(root);
        if(!underBound&&!underRoot)throw fail("Outside project root");
        Path p=path.toAbsolutePath();
        while(p!=null){if(Files.isSymbolicLink(p))throw fail("Symbolic links are not supported: "+p.getFileName());p=p.getParent();}
        // Resolve existing ancestors as well: Windows junctions need not report isSymbolicLink.
        Path check=underBound?bound:root;
        if(Files.exists(check,LinkOption.NOFOLLOW_LINKS)){
            Path existing=normalized;
            while(existing!=null&&!Files.exists(existing,LinkOption.NOFOLLOW_LINKS))existing=existing.getParent();
            if(existing==null||!existing.toRealPath().startsWith(check.toRealPath()))
                throw fail("Resolved path escapes project root");
        }
    }
    private void directories(Path path) throws IOException { safe(path);Files.createDirectories(path);safe(path); }
    private Path projectPath(String id) throws IOException {
        if(!validProject(id)) throw fail("Invalid project name");
        Path p=root.resolve(id);safe(p);return p;
    }
    private static byte[] bytes(String text) throws IOException {
        if(text==null||text.length()>MAX_BYTES) throw fail("Source exceeds 4 MiB");
        try { ByteBuffer b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
            if(b.remaining()>MAX_BYTES)throw fail("Source exceeds 4 MiB");byte[] result=new byte[b.remaining()];b.get(result);return result;
        } catch(CharacterCodingException e) { throw fail("Invalid UTF-8 source"); }
    }
    private byte[] readBytes(Path path,int limit) throws IOException {
        safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw fail("Missing regular file: "+path.getFileName());
        try(InputStream in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) { byte[] b=in.readNBytes(limit+1);if(b.length>limit)throw fail("File size limit");return b; }
    }
    private static String decode(byte[] b) throws IOException {
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString().replace("\r\n","\n").replace('\r','\n');}
        catch(CharacterCodingException e){throw fail("Invalid UTF-8 source");}
    }
    public synchronized Project project(String id) throws IOException {
        if(!dependencyUpdate)DependencyJournal.recover(projectPath(id));
        Properties p=new Properties();
        try { p.load(new StringReader(decode(readBytes(projectPath(id).resolve(MANIFEST),4096)))); }
        catch(IllegalArgumentException e){throw fail("Malformed project metadata: "+id);}
        if(!"1".equals(p.getProperty("format")))throw fail("Unsupported project format: "+id);
        try { return new Project(id,Language.valueOf(p.getProperty("language",""))); }
        catch(IllegalArgumentException e){throw fail("Unsupported project language: "+id);}
    }
    public synchronized Catalog list() throws IOException {
        directories(root);var result=new ArrayList<Project>();var errors=new ArrayList<String>();
        try(var entries=Files.newDirectoryStream(root)) { int count=0;for(Path p:entries){
            if(++count>MAX_FILES)throw fail("Too many project entries");String id=p.getFileName().toString();
            if(id.startsWith("."))continue;
            try{if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p))throw fail("Not a regular project directory");result.add(project(id));}
            catch(IOException e){errors.add(id+": "+e.getMessage());}
        }}
        result.sort(Comparator.comparing(Project::id));return new Catalog(result,errors);
    }
    private void noAlias(Path path) throws IOException {
        Path parent=path.getParent();if(!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))return;
        try(var entries=Files.newDirectoryStream(parent)){for(Path p:entries)
            if(p.getFileName().toString().equalsIgnoreCase(path.getFileName().toString()))throw fail("Name already exists: "+path.getFileName());}
    }
    public synchronized Project create(String id,Language language) throws IOException {
        Objects.requireNonNull(language);directories(root);Path target=projectPath(id);noAlias(target);
        Path staging=Files.createTempDirectory(root,".create-");
        try {
            Files.createDirectory(staging.resolve("src"));Files.createDirectory(staging.resolve("libraries"));Files.createDirectory(staging.resolve("resources"));
            Files.writeString(staging.resolve(MANIFEST),"format=1\nlanguage="+language.name()+"\nmod_version=0.4.0\nproject_layout=shared\n",StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("src").resolve(language.entry),language.starter,StandardCharsets.UTF_8);
            safe(target);Files.move(staging,target);return new Project(id,language);
        } finally { if(Files.exists(staging,LinkOption.NOFOLLOW_LINKS))try(var paths=Files.walk(staging)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);} }
    }
    private boolean sharedLayout(String id)throws IOException{
        var properties=new Properties();properties.load(new StringReader(decode(readBytes(projectPath(id).resolve(MANIFEST),4096))));return "shared".equals(properties.getProperty("project_layout"));
    }
    /** Atomic server-module starter, including the exact standalone API JAR for Gradle use. */
    public synchronized Project createServerModule(String id)throws IOException {
        byte[] api=RuntimeModuleCompiler.apiJar();directories(root);Path target=projectPath(id);noAlias(target);
        Path staging=Files.createTempDirectory(root,".create-server-module-");
        try {
            Files.createDirectory(staging.resolve("src"));Files.createDirectory(staging.resolve("libraries"));Files.createDirectory(staging.resolve("resources"));
            Files.writeString(staging.resolve(MANIFEST),"format=1\nlanguage=JAVA\nmod_version=0.4.0\nproject_layout=shared\nworkspace=local_sources\n",StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("src/WorldModule.java"),com.fixmer.genesis.technology.server.ServerModuleTemplate.source(),StandardCharsets.UTF_8);
            Files.write(staging.resolve("libraries/genesis-server-api-v1.jar"),api);
            safe(target);Files.move(staging,target);return project(id);
        } finally {if(Files.exists(staging,LinkOption.NOFOLLOW_LINKS))try(var paths=Files.walk(staging)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    public synchronized Path exportServerApi(String id)throws IOException {
        Path path=projectDirectory(id).resolve("libraries/genesis-server-api-v1.jar");safe(path);directories(path.getParent());
        byte[] api=RuntimeModuleCompiler.apiJar();
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {if(!Arrays.equals(readBytes(path,1024*1024),api))throw fail("Existing API JAR differs; preserve or rename it first");return path;}
        noAlias(path);Path temporary=Files.createTempFile(path.getParent(),".server-api-",".tmp");
        try {Files.write(temporary,api);safe(path);noAlias(path);Files.move(temporary,path);return path;}
        finally {Files.deleteIfExists(temporary);}
    }
    public synchronized Project createEmpty(String id)throws IOException{
        directories(root);Path target=projectPath(id);noAlias(target);Path staging=Files.createTempDirectory(root,".create-");
        try{Files.writeString(staging.resolve(MANIFEST),"format=1\nlanguage=JAVA\nmod_version=0.4.0\nproject_layout=shared\n",StandardCharsets.UTF_8);safe(target);noAlias(target);Files.move(staging,target);return new Project(id,Language.JAVA);}
        finally{if(Files.exists(staging,LinkOption.NOFOLLOW_LINKS)){Files.deleteIfExists(staging.resolve(MANIFEST));Files.deleteIfExists(staging);}}
    }
    public record Imported(Project project,GradleProjectImport.Summary summary) {}
    public synchronized boolean importedGradle(String id)throws IOException {
        project(id);var properties=new Properties();try{properties.load(new StringReader(decode(readBytes(projectPath(id).resolve(MANIFEST),4096))));}
        catch(IllegalArgumentException bad){throw fail("Malformed project metadata");}
        String workspace=properties.getProperty("workspace","");
        return "imported_gradle".equals(workspace)||"linked_minecraft".equals(workspace);
    }
    public synchronized Imported importGradle(String id,Path external,java.util.function.BooleanSupplier cancelled)throws IOException {
        return importProject(id,external,cancelled,true);
    }
    public synchronized Imported importFolder(String id,Path external,java.util.function.BooleanSupplier cancelled)throws IOException {
        return importProject(id,external,cancelled,false);
    }
    /** Linked Minecraft workspace: Genesis stores metadata only; builds use the original path. */
    public synchronized Project createMinecraftLink(String id,Path external,com.fixmer.genesis.technology.minecraft.MinecraftProjectInfo info)throws IOException {
        if(external==null||!external.isAbsolute())throw fail("Select an absolute project directory");
        Objects.requireNonNull(info);
        Path real=external.toRealPath(),managed=root.toRealPath();
        if(real.startsWith(managed)||managed.startsWith(real))throw fail("Link a project outside the Genesis storage tree");
        directories(root);Path target=projectPath(id);noAlias(target);
        Path staging=Files.createTempDirectory(root,".link-minecraft-");
        try{
            var hints=com.fixmer.genesis.technology.minecraft.MinecraftWorkspaceCreator.manifestHints(info,com.fixmer.genesis.technology.minecraft.MinecraftImportMode.LINK,external);
            // Прямые слеши: Properties.load() не трогает '/', путь остаётся валидным на Windows.
            String normalizedPath = external.toAbsolutePath().normalize().toString().replace('\\', '/');
            hints.setProperty("linked_path", normalizedPath);

            StringBuilder manifest=new StringBuilder("format=1\nlanguage=JAVA\nmod_version=0.4.0\nproject_layout=shared\nworkspace=linked_minecraft\n");
            for(String key:hints.stringPropertyNames())manifest.append(key).append('=').append(hints.getProperty(key)).append('\n');
            Files.writeString(staging.resolve(MANIFEST),manifest.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            if(bytes(manifest.toString()).length>4096)throw fail("Project metadata size limit");
            safe(target);noAlias(target);Files.move(staging,target);return new Project(id,Language.JAVA);
        }finally{if(Files.exists(staging,LinkOption.NOFOLLOW_LINKS))try(var entries=Files.walk(staging)){for(Path p:entries.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    public synchronized boolean linkedMinecraft(String id)throws IOException {
        project(id);var properties=new Properties();try{properties.load(new StringReader(decode(readBytes(projectPath(id).resolve(MANIFEST),4096))));}
        catch(IllegalArgumentException bad){throw fail("Malformed project metadata");}
        return "linked_minecraft".equals(properties.getProperty("workspace"));
    }
    /** Build / browse root: linked Minecraft projects resolve to the original path. */
    public synchronized Path workingDirectory(String id)throws IOException {
        Path managed=projectDirectory(id);
        var properties=new Properties();try{properties.load(new StringReader(decode(readBytes(managed.resolve(MANIFEST),4096))));}
        catch(IllegalArgumentException bad){throw fail("Malformed project metadata");}
        if(!"linked_minecraft".equals(properties.getProperty("workspace")))return managed;
        String linked=properties.getProperty("linked_path","");
        if(linked.isEmpty())throw fail("Invalid linked Minecraft path");
        Path path;
        try { path=Path.of(linked).toAbsolutePath().normalize(); }
        catch (RuntimeException ex) { throw fail("Invalid linked Minecraft path: "+linked); }
        if(!path.isAbsolute())throw fail("Invalid linked Minecraft path: "+linked);
        if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path))throw fail("Linked Minecraft project is missing");
        Path real=path.toRealPath(),own=root.toRealPath();
        if(real.startsWith(own)||own.startsWith(real))throw fail("Linked Minecraft path collides with Genesis storage");
        return path;
    }
    private Imported importProject(String id,Path external,java.util.function.BooleanSupplier cancelled,boolean requireBuild)throws IOException {
        if(external==null||!external.isAbsolute())throw fail("Select an absolute project directory");
        Objects.requireNonNull(cancelled);directories(root);Path target=projectPath(id);noAlias(target);
        Path real=external.toRealPath(),managed=root.toRealPath();if(real.startsWith(managed)||managed.startsWith(real))throw fail("Import a project outside the Genesis storage tree");
        Path staging=Files.createTempDirectory(root,".import-");
        try{
            byte[] metadata=importMetadata(external);Language language=Language.JAVA;
            if(metadata!=null){if(requireBuild)throw fail("Use folder import for Genesis exports");var props=new Properties();
                try{props.load(new StringReader(decode(metadata)));if(!"1".equals(props.getProperty("format")))throw new IllegalArgumentException();language=Language.valueOf(props.getProperty("language",""));}
                catch(IllegalArgumentException bad){throw fail("Invalid exported Genesis metadata");}}
            var summary=GradleProjectImport.copy(external,staging,cancelled,requireBuild,!requireBuild);
            if(!Arrays.equals(metadata,importMetadata(external)))throw fail("Import metadata changed while copying");
            boolean gradle=Files.isRegularFile(staging.resolve("build.gradle"))||Files.isRegularFile(staging.resolve("build.gradle.kts"));if(gradle)language=Language.JAVA;
            Files.writeString(staging.resolve(MANIFEST),"format=1\nlanguage="+language.name()+"\nmod_version=0.4.0\nproject_layout=shared\nworkspace="+(gradle?"imported_gradle":"local_sources")+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            if(cancelled.getAsBoolean())throw fail("Import cancelled");safe(target);noAlias(target);Files.move(staging,target);
            return new Imported(new Project(id,language),summary);
        }finally{if(Files.exists(staging,LinkOption.NOFOLLOW_LINKS))try(var entries=Files.walk(staging)){for(Path p:entries.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    /** Creates a new, independent mod project. Existing projects are never converted or replaced. */
    public synchronized Project createNeoForge(String id)throws IOException {
        if(!NeoForgeProjectTemplate.validId(id))throw fail("Invalid or reserved mod id");
        directories(root);Path target=projectPath(id);noAlias(target);
        BuildTools.Settings tools=buildTools();
        byte[] wrapper,properties;
        try(var in=Files.newInputStream(tools.wrapperJar(),LinkOption.NOFOLLOW_LINKS)){wrapper=in.readNBytes(8*1024*1024+1);}
        try(var in=Files.newInputStream(tools.wrapperJar().resolveSibling("gradle-wrapper.properties"),LinkOption.NOFOLLOW_LINKS)){properties=in.readNBytes(64*1024+1);}
        if(wrapper.length>8*1024*1024||properties.length>64*1024)throw fail("Wrapper size limit");
        Path staging=Files.createTempDirectory(root,".create-");
        try{
            for(var entry:NeoForgeProjectTemplate.files(id).entrySet()){
                Path output=staging.resolve(entry.getKey());Files.createDirectories(output.getParent());
                Files.writeString(output,entry.getValue(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }
            Path jar=staging.resolve("gradle/wrapper/gradle-wrapper.jar");Files.createDirectories(jar.getParent());
            Files.write(jar,wrapper,StandardOpenOption.CREATE_NEW);Files.write(jar.resolveSibling("gradle-wrapper.properties"),properties,StandardOpenOption.CREATE_NEW);
            BuildTools.validate(new BuildTools.Settings(tools.javaHome(),jar));
            staging.resolve("gradlew").toFile().setExecutable(true);
            safe(target);noAlias(target);Files.move(staging,target);return new Project(id,Language.JAVA);
        }finally{if(Files.exists(staging,LinkOption.NOFOLLOW_LINKS))try(var entries=Files.walk(staging)){for(Path path:entries.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    private byte[] importMetadata(Path source)throws IOException{
        Path path=source.resolve(MANIFEST);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return null;
        try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] data=in.readNBytes(4097);if(data.length>4096)throw fail("Import metadata size limit");return data;}
    }
    private Path source(String id,String file) throws IOException {
        project(id);if(file.equalsIgnoreCase(MANIFEST)||!GradleProjectImport.editable(file))throw fail("Not an editable project path");
        if(linkedMinecraft(id))return linkedEntry(id,file);
        Path p=projectPath(id).resolve(file);safe(p);return p;
    }
    /**
     * Linked projects: workingDirectory(id) is computed ONCE, before withBound(), so the
     * second call inside the lambda never re-enters safe() with a mismatched bound.
     */
    public synchronized List<String> files(String id) throws IOException {
        project(id);
        if(linkedMinecraft(id)){
            Path wd=workingDirectory(id);
            return withBound(wd,()->GradleProjectImport.editableFiles(wd));
        }
        return GradleProjectImport.editableFiles(workingDirectory(id));
    }
    public synchronized List<String> entries(String id)throws IOException{
        project(id);
        if(linkedMinecraft(id)){
            Path wd=workingDirectory(id);
            return withBound(wd,()->GradleProjectImport.allFiles(wd));
        }
        return GradleProjectImport.allFiles(workingDirectory(id));
    }
    public synchronized byte[] asset(String id,String path)throws IOException{
        if(linkedMinecraft(id))return withBound(workingDirectory(id),()->readBytes(entryPath(id,path),GradleProjectImport.MAX_FILE_BYTES));
        return readBytes(entryPath(id,path),GradleProjectImport.MAX_FILE_BYTES);
    }
    public synchronized List<String> folders(String id)throws IOException{
        project(id);
        if(linkedMinecraft(id)){
            Path wd=workingDirectory(id);
            return withBound(wd,()->GradleProjectImport.folders(wd));
        }
        return GradleProjectImport.folders(workingDirectory(id));
    }
    private Path entryPath(String id,String path)throws IOException{
        project(id);if(!com.fixmer.genesis.technology.storage.ProjectPath.valid(path)||path.equalsIgnoreCase(MANIFEST)||path.toLowerCase(Locale.ROOT).startsWith(".build-tools"))throw fail("Invalid or protected project path");
        if(linkedMinecraft(id))return linkedEntry(id,path);
        Path result=projectPath(id).resolve(path);safe(result);return result;
    }
    private Path linkedEntry(String id,String path)throws IOException{
        Path rootDir=workingDirectory(id);
        Path result=rootDir.resolve(path).toAbsolutePath().normalize();
        if(!result.startsWith(rootDir.toAbsolutePath().normalize()))throw fail("Outside linked project root");
        for(Path p=result;p!=null;p=p.getParent()){
            if(p.equals(rootDir.toAbsolutePath().normalize()))break;
            if(Files.isSymbolicLink(p))throw fail("Symbolic links are not supported: "+p.getFileName());
        }
        if(Files.exists(result,LinkOption.NOFOLLOW_LINKS)&&!result.toRealPath().startsWith(rootDir.toRealPath()))throw fail("Resolved path escapes linked project root");
        return result;
    }
    public synchronized void createDirectory(String id,String folder)throws IOException {
        Path current=linkedMinecraft(id)?workingDirectory(id):projectPath(id);
        Path path=entryPath(id,folder);var created=new ArrayList<Path>();
        CallableIO<Void> action=()->{
            Path cursor=current;
            for(Path segment:cursor.relativize(path)){Path next=cursor.resolve(segment);safe(next);if(Files.exists(next,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.isDirectory(next,LinkOption.NOFOLLOW_LINKS))throw fail("Not a directory");
                try(var entries=Files.list(cursor)){for(Path entry:entries.toList())if(entry.getFileName().toString().equalsIgnoreCase(segment.toString())&&!entry.getFileName().toString().equals(segment.toString()))throw fail("Case alias");}
            }else{noAlias(next);Files.createDirectory(next);created.add(next);}cursor=next;}
            return null;
        };
        try{if(linkedMinecraft(id))withBound(current,action);else action.call();}
        catch(IOException bad){Collections.reverse(created);for(Path entry:created)try{Files.delete(entry);}catch(IOException rollback){bad.addSuppressed(rollback);}throw bad;}
    }
    /** Copies saved content; never saves drafts or replaces an existing entry. */
    public synchronized void copyEntry(String id,String from,String to,Map<String,String> expected)throws IOException{
        copyEntry(id,from,to,expected,()->false);
    }
    public synchronized void copyEntry(String id,String from,String to,Map<String,String> expected,java.util.function.BooleanSupplier stop)throws IOException{
        Objects.requireNonNull(expected);Objects.requireNonNull(stop);Path source=entryPath(id,from),target=entryPath(id,to);
        if(from.equals(to)||to.startsWith(from+"/"))throw fail("Cannot copy inside the source");
        if(!Files.exists(source,LinkOption.NOFOLLOW_LINKS))throw fail("Missing copy source");
        for(Path current=source;!current.equals(root);current=current.getParent()){
            String spelling=current.getFileName().toString();try(var siblings=Files.list(current.getParent())){for(Path sibling:siblings.toList())if(sibling.getFileName().toString().equalsIgnoreCase(spelling)&&!sibling.getFileName().toString().equals(spelling))throw fail("Copy source case alias");}
        }
        if(!Files.isDirectory(target.getParent(),LinkOption.NOFOLLOW_LINKS))throw fail("Destination parent must exist");
        String parent=RepositoryPath.parent(to);if(!parent.isEmpty())createDirectory(id,parent);noAlias(target);
        for(var e:expected.entrySet())if(!read(id,e.getKey()).equals(e.getValue()))throw fail("File changed outside Scripts: "+e.getKey());
        for(String file:files(id))if(file.equals(from)||file.startsWith(from+"/"))if(recovery(id,file)!=null)throw fail("Resolve recovered drafts before copying");
        if(stop.getAsBoolean())throw fail("Copy cancelled");boolean directory=Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS);Path staging=Files.createTempDirectory(root,".genesis-copy-");boolean published=false;
        try{Path data=staging.resolve("data");
            if(directory){Files.createDirectory(data);GradleProjectImport.copyContents(source,data,stop,from);}
            else{byte[] content=readBytes(source,GradleProjectImport.MAX_FILE_BYTES);Files.write(data,content,StandardOpenOption.CREATE_NEW);if(!Arrays.equals(content,readBytes(source,GradleProjectImport.MAX_FILE_BYTES)))throw fail("Copy source changed");}
            for(var e:expected.entrySet())if(!read(id,e.getKey()).equals(e.getValue()))throw fail("File changed outside Scripts: "+e.getKey());
            GradleProjectImport.validateAddition(projectPath(id),data,directory,to);
            if(stop.getAsBoolean())throw fail("Copy cancelled");safe(target);noAlias(target);Files.move(data,target);published=true;
        }finally{try(var paths=Files.walk(staging)){for(Path entry:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(entry);}catch(IOException cleanup){if(!published)throw cleanup;}}
    }
    public synchronized void renameEntry(String id,String from,String to,Map<String,String> expected)throws IOException {
        Path source=entryPath(id,from),target=entryPath(id,to);
        if(from.equals(to)||to.startsWith(from+"/"))throw fail("Invalid move destination");
        if(!Files.exists(source,LinkOption.NOFOLLOW_LINKS))throw fail("Missing project entry");
        for(var e:expected.entrySet())if(!read(id,e.getKey()).equals(e.getValue()))throw fail("File changed outside Scripts: "+e.getKey());
        for(String file:files(id))if(file.equals(from)||file.startsWith(from+"/")){
            if(recovery(id,file)!=null)throw fail("Save or resolve recovered drafts before renaming");
            String destination=to+file.substring(from.length());if(!GradleProjectImport.editable(destination))throw fail("Unsupported destination file type");
        }
        if(Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)&&GradleProjectImport.editable(from)&&!GradleProjectImport.editable(to))throw fail("Unsupported destination file type");
        if(!Files.isDirectory(target.getParent(),LinkOption.NOFOLLOW_LINKS))throw fail("Destination parent must exist");
        noAlias(target);safe(target);Files.move(source,target);
    }
    /** Reversible delete: entries go to local trash outside projects; no recursive permanent deletion. */
    public synchronized Path trashEntry(String id,String path,Map<String,String> expected)throws IOException{
        Path source=entryPath(id,path);if(!Files.exists(source,LinkOption.NOFOLLOW_LINKS))throw fail("Missing project entry");
        for(var e:expected.entrySet())if(!read(id,e.getKey()).equals(e.getValue()))throw fail("File changed outside Scripts: "+e.getKey());
        for(String file:files(id))if(file.equals(path)||file.startsWith(path+"/"))if(recovery(id,file)!=null)throw fail("Save or resolve recovered drafts before deletion");
        Path trash=root.resolve(".genesis-trash");directories(trash);Path target=Files.createTempDirectory(trash,id+"-");
        try{Files.writeString(target.resolve("origin.properties"),"project="+id+"\npath="+path+"\n",StandardCharsets.UTF_8);Files.move(source,target.resolve("data"));return target;}
        catch(IOException bad){Files.deleteIfExists(target.resolve("origin.properties"));Files.deleteIfExists(target);throw bad;}
    }
    public synchronized String restoreTrash(Path directory)throws IOException{
        Path trash=root.resolve(".genesis-trash"),item=directory.toAbsolutePath().normalize();
        if(!trash.equals(item.getParent()))throw fail("Invalid trash entry");safe(item);var properties=new Properties();
        try{properties.load(new StringReader(decode(readBytes(item.resolve("origin.properties"),4096))));}catch(IllegalArgumentException bad){throw fail("Invalid trash metadata");}
        String id=properties.getProperty("project",""),path=properties.getProperty("path","");Path target=entryPath(id,path),data=item.resolve("data");safe(data);
        if(!Files.exists(data,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(data))throw fail("Missing trash data");noAlias(target);
        String parent=RepositoryPath.parent(path);if(!parent.isEmpty())createDirectory(id,parent);safe(target);Files.move(data,target);
        try{Files.deleteIfExists(item.resolve("origin.properties"));Files.deleteIfExists(item);}catch(IOException ignored){ /* Restored data is already at its destination; residue is not a failed restore. */ }
        return id+"/"+path;
    }
    public synchronized String read(String id,String file) throws IOException {
        if(linkedMinecraft(id))return withBound(workingDirectory(id),()->decode(readBytes(source(id,file),MAX_BYTES)));
        return decode(readBytes(source(id,file),MAX_BYTES));
    }
    public synchronized String javaMainClass(String id)throws IOException {
        if(project(id).language()!=Language.JAVA)throw fail("Java entry point requires a Java project");
        Properties properties=new Properties();try{properties.load(new StringReader(decode(readBytes(projectPath(id).resolve(MANIFEST),4096))));}
        catch(IllegalArgumentException e){throw fail("Malformed project metadata");}
        return properties.getProperty("main_class","Main");
    }
    public synchronized void setJavaMainClass(String id,String mainClass)throws IOException {
        if(project(id).language()!=Language.JAVA)throw fail("Java entry point requires a Java project");if(!JavaSourceRunner.validMainClass(mainClass))throw fail("Invalid Java main class");
        Path path=projectPath(id).resolve(MANIFEST);String before=decode(readBytes(path,4096));
        var pattern=java.util.regex.Pattern.compile("(?m)^[ \t]*main_class[ \t]*[:=][^\r\n]*");var matcher=pattern.matcher(before);int count=0;while(matcher.find())count++;
        if(count>1)throw fail("Duplicate main_class configuration");
        String after=count==0?before+(before.endsWith("\n")?"":"\n")+"main_class="+mainClass+"\n":pattern.matcher(before).replaceFirst(java.util.regex.Matcher.quoteReplacement("main_class="+mainClass));
        if(bytes(after).length>4096)throw fail("Project metadata size limit");
        if(!decode(readBytes(path,4096)).equals(before))throw fail("Project metadata changed externally");atomic(path,bytes(after));
    }
    public synchronized String gradleTask(String id)throws IOException {
        project(id);String task=buildMetadata(decode(readBytes(projectPath(id).resolve(MANIFEST),4096))).getProperty("gradle_task","build");
        if(!GradleBuildRunner.validTask(task))throw fail("Invalid gradle_task in project metadata");return task;
    }
    public synchronized void setGradleTask(String id,String task)throws IOException {
        if(project(id).language()!=Language.JAVA)throw fail("Gradle task requires a Java project");if(!GradleBuildRunner.validTask(task))throw fail("Invalid Gradle task");
        setBuildMetadata(id,"gradle_task",task);
    }
    public synchronized GradleBuildRunner.Options buildOptions(String id)throws IOException{
        project(id);var props=buildMetadata(decode(readBytes(projectPath(id).resolve(MANIFEST),4096)));try{
            long minutes=Long.parseLong(props.getProperty("build_timeout_minutes","10"));if(minutes<1||minutes>1440)throw new IllegalArgumentException();
            return new GradleBuildRunner.Options(minutes*60_000L,Integer.parseInt(props.getProperty("build_max_workers","2")));
        }catch(IllegalArgumentException invalid){throw fail("Invalid build_timeout_minutes (1–1440) or build_max_workers (0–64) in project metadata");}
    }
    public synchronized void setBuildOption(String id,String key,int value)throws IOException{
        if(project(id).language()!=Language.JAVA)throw fail("Build settings require a Java project");
        if(!key.equals("build_timeout_minutes")&&!key.equals("build_max_workers"))throw fail("Unknown build setting");
        if(key.equals("build_timeout_minutes")?(value<1||value>1440):(value<0||value>64))throw fail("Build setting out of range");
        setBuildMetadata(id,key,Integer.toString(value));
    }
    private static boolean continued(String line){
        int end=line.length();while(end>0&&(line.charAt(end-1)=='\n'||line.charAt(end-1)=='\r'))end--;
        int slashes=0;while(end>0&&line.charAt(--end)=='\\')slashes++;return (slashes&1)!=0;
    }
    /** Parse with Java Properties semantics, including escaped keys and continued declarations. */
    private static Properties buildMetadata(String text)throws IOException{
        var props=new Properties(){@Override public synchronized Object put(Object key,Object value){
            if(Set.of("gradle_task","build_timeout_minutes","build_max_workers").contains(key)&&containsKey(key))throw new IllegalArgumentException("Duplicate build setting: "+key);
            return super.put(key,value);
        }};
        try{props.load(new StringReader(text));return props;}catch(IllegalArgumentException bad){throw fail("Malformed build metadata: "+bad.getMessage());}
    }
    private void setBuildMetadata(String id,String key,String value)throws IOException{
        Path path=projectPath(id).resolve(MANIFEST);String before=decode(readBytes(path,4096));
        buildMetadata(before);String[] lines=before.split("(?<=\n)|(?<=\r)(?!\n)",-1);var updated=new StringBuilder();boolean replaced=false;
        for(int i=0;i<lines.length;i++){
            var logical=new StringBuilder(lines[i]);String first=lines[i].stripLeading();boolean comment=first.startsWith("#")||first.startsWith("!");
            while(!comment&&continued(lines[i])&&i+1<lines.length)logical.append(lines[++i]);
            var declaration=new Properties();try{declaration.load(new StringReader(logical.toString()));}catch(IllegalArgumentException bad){throw fail("Malformed project metadata");}
            if(declaration.containsKey(key)){updated.append(key).append('=').append(value).append('\n');replaced=true;}else updated.append(logical);
        }
        if(!replaced){if(!updated.isEmpty()&&continued(lines[lines.length-1])&&!lines[lines.length-1].stripLeading().startsWith("#")&&!lines[lines.length-1].stripLeading().startsWith("!"))updated.append("\n\n");if(!updated.isEmpty()&&updated.charAt(updated.length()-1)!='\n')updated.append('\n');updated.append(key).append('=').append(value).append('\n');}
        String after=updated.toString();buildMetadata(after);
        if(bytes(after).length>4096)throw fail("Project metadata size limit");
        if(!decode(readBytes(path,4096)).equals(before))throw fail("Project metadata changed externally");atomic(path,bytes(after));
    }
    public static boolean directJavaSource(String path){return com.fixmer.genesis.technology.storage.ProjectPath.valid(path)&&path.endsWith(".java")&&!path.startsWith("tests/")&&!path.startsWith("src/test/")&&!path.startsWith("libraries/")&&!path.startsWith("resources/");}
    /** Bounded, read-only Java source snapshot. Editor overrides retain external-change checks. */
    public synchronized Map<String,String> javaSources(String id,Map<String,Recovery> edits)throws IOException {
        if(project(id).language()!=Language.JAVA)throw fail("Java execution requires a Java project");
        if(importedGradle(id))throw fail("Use Gradle tasks for imported projects");
        Objects.requireNonNull(edits);var result=new TreeMap<String,String>();long total=0;
        for(String path:files(id))if(directJavaSource(path)){
            if(result.size()>=JavaSourceRunner.MAX_SOURCES)throw fail("Java source count exceeds 512");
            String disk=read(id,path);Recovery edit=edits.get(path);String value=disk;
            if(edit!=null){if(!disk.equals(edit.baseline()))throw fail("Source changed outside Scripts: "+path);value=edit.text();}
            total+=bytes(value).length;if(total>JavaSourceRunner.MAX_SOURCE_BYTES)throw fail("Java project exceeds 16 MiB");result.put(path,value);
        }
        for(String path:edits.keySet())if(!result.containsKey(path))throw fail("Missing Java source: "+path);
        if(result.isEmpty())throw fail("No Java sources in project");return Map.copyOf(result);
    }
    /** Flat, bounded JAR snapshot. Nested folders and links are refused instead of silently ignored. */
    public synchronized Map<String,byte[]> javaLibraries(String id)throws IOException {
        if(project(id).language()!=Language.JAVA)throw fail("Java libraries require a Java project");
        Path dir=projectPath(id).resolve("libraries");safe(dir);
        if(!Files.exists(dir,LinkOption.NOFOLLOW_LINKS))return Map.of();
        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))throw fail("libraries must be a directory");
        var paths=new TreeMap<String,Path>();var names=new HashSet<String>();int count=0;
        try(var stream=Files.newDirectoryStream(dir)){for(Path entry:stream){
            if(++count>MAX_FILES)throw fail("Too many library directory entries");safe(entry);
            if(!Files.isRegularFile(entry,LinkOption.NOFOLLOW_LINKS))throw fail("libraries only supports regular files: "+entry.getFileName());
            String name=entry.getFileName().toString();if(!name.toLowerCase(Locale.ROOT).endsWith(".jar"))continue;
            if(!JavaSourceRunner.validLibrary(name)||!names.add(name.toLowerCase(Locale.ROOT)))throw fail("Invalid or duplicate library name: "+name);
            if(paths.size()>=JavaSourceRunner.MAX_LIBRARIES)throw fail("Java library count exceeds 32");paths.put(name,entry);
        }}
        var result=new TreeMap<String,byte[]>();long total=0;
        for(var entry:paths.entrySet()){
            long size=Files.size(entry.getValue());if(size<=0||size>JavaSourceRunner.MAX_LIBRARY_BYTES||total+size>JavaSourceRunner.MAX_LIBRARIES_BYTES)throw fail("Java library size limit (8 MiB each / 64 MiB total)");
            byte[] value=readBytes(entry.getValue(),JavaSourceRunner.MAX_LIBRARY_BYTES);total+=value.length;
            if(total>JavaSourceRunner.MAX_LIBRARIES_BYTES)throw fail("Java libraries exceed 64 MiB");result.put(entry.getKey(),value);
        }
        return Collections.unmodifiableMap(result);
    }
    public synchronized Path projectDirectory(String id)throws IOException {project(id);return projectPath(id);}
    public synchronized boolean hasGradle(String id)throws IOException {
        Path p=workingDirectory(id);for(String name:List.of("build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts")){Path file=p.resolve(name);if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return true;}return false;
    }
    /** Creates editable Java/application build files without moving existing sources. */
    public synchronized void initializeGradle(String id)throws IOException {
        if(project(id).language()!=Language.JAVA)throw fail("Gradle setup requires a Java project");Path p=projectPath(id);
        for(String name:List.of("build.gradle","settings.gradle","build.gradle.kts","settings.gradle.kts")){Path f=p.resolve(name);safe(f);if(Files.exists(f,LinkOption.NOFOLLOW_LINKS))throw fail("Build configuration already exists: "+name);noAlias(f);}
        String build="plugins { id 'java'; id 'application' }\nversion = '0.4.0'\nrepositories { mavenCentral() }\njava { toolchain { languageVersion = JavaLanguageVersion.of(21) } }\nsourceSets { main { java.srcDirs = ['src']; resources.srcDirs = ['resources'] }; test { java.srcDirs = ['tests'] } }\ndependencies { implementation fileTree(dir: 'libraries', includes: ['*.jar', '*.JAR']) }\n// Add testImplementation dependencies and test framework configuration here.\n def genesis = new Properties()\nfile('genesis-project.properties').withReader('UTF-8') { genesis.load(it) }\napplication { mainClass = genesis.getProperty('main_class', 'Main') }\ntasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8' }\ntasks.withType(Jar).configureEach { preserveFileTimestamps = false; reproducibleFileOrder = true }\njar { manifest { attributes 'Main-Class': genesis.getProperty('main_class', 'Main') } }\n";
        Map<String,String> content=new LinkedHashMap<>();content.put("settings.gradle","rootProject.name = '"+id+"'\n");content.put("build.gradle",build);var created=new ArrayList<Path>();
        try{for(var e:content.entrySet()){Path file=p.resolve(e.getKey());safe(file);try(var output=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){created.add(file);output.write(e.getValue().getBytes(StandardCharsets.UTF_8));}}}
        catch(IOException|RuntimeException bad){for(Path file:created)try{Files.deleteIfExists(file);}catch(IOException cleanup){bad.addSuppressed(cleanup);}throw bad;}
    }
    private Properties toolProperties()throws IOException {
        Path file=root.resolve(".build-tools.properties");safe(file);var props=new Properties(){@Override public synchronized Object put(Object key,Object value){
            if(Set.of("java_home","wrapper_jar").contains(key)&&containsKey(key))throw new IllegalArgumentException("Duplicate local build tool: "+key);return super.put(key,value);
        }};
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))try{props.load(new StringReader(decode(readBytes(file,16*1024))));}
        catch(IllegalArgumentException bad){throw fail("Malformed build tools configuration");}
        var result=new Properties();result.putAll(props);return result;
    }
    public synchronized String buildTool(String key)throws IOException {
        if(!Set.of("java_home","wrapper_jar").contains(key))throw fail("Unknown build tool");
        return toolProperties().getProperty(key,key.equals("java_home")?System.getProperty("java.home"):"");
    }
    /** Validates one tool without running it, then preserves the other local settings. */
    public synchronized void setBuildTool(String key,String value)throws IOException {
        if(!Set.of("java_home","wrapper_jar").contains(key)||!BuildTools.validAbsolutePath(value))throw fail("Use an absolute local path");
        value=(key.equals("java_home")?BuildTools.javaHome(value):BuildTools.wrapperJar(value)).toString();
        var props=toolProperties();props.setProperty(key,value);var out=new StringWriter();props.store(out,"Genesis local build tools; not exported");
        byte[] data=bytes(out.toString());if(data.length>16*1024)throw fail("Build tools configuration limit");
        directories(root);atomic(root.resolve(".build-tools.properties"),data);
    }
    /** Resolve Java independently: local execution and hot modules do not need Gradle. */
    public synchronized Path javaHome()throws IOException {
        String configured=toolProperties().getProperty("java_home");
        if(configured!=null)return BuildTools.javaHome(configured);
        String environment=System.getenv("JAVA_HOME");
        if(environment!=null)try{return BuildTools.javaHome(environment);}catch(IOException ignored){}
        Path client=Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
        // Some launchers include jdk.compiler but omit the standalone javac launcher.
        // Direct execution invokes the compiler module; Gradle still validates a full JDK.
        if(new JavaSourceRunner(client).available())return client;
        return BuildTools.javaHome(client.toString());
    }
    public synchronized BuildTools.Settings buildTools()throws IOException {
        Path java=javaHome();String wrapper=buildTool("wrapper_jar");
        if(!BuildTools.validAbsolutePath(wrapper))throw fail("Gradle Wrapper is not configured: select an existing gradle-wrapper.jar with gradle-wrapper.properties beside it, or a project containing gradle/wrapper. Java execution and server modules do not need Wrapper.");
        var settings=new BuildTools.Settings(java,BuildTools.wrapperJar(wrapper));BuildTools.validate(settings);return settings;
    }
    /** Imported projects use their own Wrapper if present, preserving their distribution version. */
    public synchronized BuildTools.Settings buildTools(String id)throws IOException {
        project(id);Path rootDir=workingDirectory(id);Path wrapper=rootDir.resolve("gradle/wrapper/gradle-wrapper.jar");
        Path properties=wrapper.resolveSibling("gradle-wrapper.properties");
        if(Files.exists(wrapper,LinkOption.NOFOLLOW_LINKS)||Files.exists(properties,LinkOption.NOFOLLOW_LINKS)){
            var tools=new BuildTools.Settings(javaHome(),wrapper);BuildTools.validate(tools);return tools;
        }
        return buildTools();
    }
    public synchronized Map<String,byte[]> javaResources(String id)throws IOException {
        if(project(id).language()!=Language.JAVA)throw fail("Java resources require a Java project");
        Path dir=projectPath(id).resolve("resources");safe(dir);
        if(!Files.exists(dir,LinkOption.NOFOLLOW_LINKS))return Map.of();
        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))throw fail("resources must be a directory");
        var paths=new TreeMap<String,Path>();var aliases=new HashSet<String>();int count=0;
        try(var stream=Files.walk(dir)){var entries=stream.iterator();while(entries.hasNext()){
            Path entry=entries.next();if(++count>MAX_FILES)throw fail("Too many resource directory entries");safe(entry);if(entry.equals(dir))continue;
            String name=dir.relativize(entry).toString().replace('\\','/');
            if(!com.fixmer.genesis.technology.storage.ProjectPath.valid(name)||!aliases.add(name.toLowerCase(Locale.ROOT)))throw fail("Invalid or duplicate resource path: "+name);
            if(Files.isDirectory(entry,LinkOption.NOFOLLOW_LINKS))continue;
            if(!Files.isRegularFile(entry,LinkOption.NOFOLLOW_LINKS)||!JavaSourceRunner.validResource(name))throw fail("Not a supported resource: "+name);
            if(paths.size()>=JavaSourceRunner.MAX_RESOURCES)throw fail("Resource count exceeds 512");paths.put(name,entry);
        }}
        var result=new TreeMap<String,byte[]>();long total=0;
        for(var e:paths.entrySet()){
            long size=Files.size(e.getValue());if(size>JavaSourceRunner.MAX_RESOURCE_BYTES||total+size>JavaSourceRunner.MAX_RESOURCES_BYTES)throw fail("Resource size limit (8 MiB each / 32 MiB total)");
            byte[] data=readBytes(e.getValue(),JavaSourceRunner.MAX_RESOURCE_BYTES);total+=data.length;if(total>JavaSourceRunner.MAX_RESOURCES_BYTES)throw fail("Resources exceed 32 MiB");result.put(e.getKey(),data);
        }
        return Collections.unmodifiableMap(result);
    }
    /** Delivers one fingerprinted binary outside project trees; never installs it into mods. */
    public synchronized Path deliverArtifact(String id,BuildArtifacts.Artifact artifact,java.util.function.BooleanSupplier cancelled)throws IOException{
        project(id);Path destination=root.resolve(".artifacts");directories(destination);return BuildArtifacts.deliver(projectPath(id),artifact,destination,cancelled);
    }
    /** Full source workspace export; drafts are never saved back to disk. */
    public synchronized Path exportProject(String id,Map<String,Recovery> edits)throws IOException {
        project(id);Objects.requireNonNull(edits);
        var overrides=new HashMap<String,GradleProjectImport.Edit>();
        for(var e:edits.entrySet())overrides.put(e.getKey(),new GradleProjectImport.Edit(e.getValue().baseline(),e.getValue().text()));
        Path exports=root.resolve(".exports");directories(exports);
        return GradleProjectImport.export(projectPath(id),exports,id,overrides,readBytes(projectPath(id).resolve(MANIFEST),4096));
    }
    /** Compatibility entry point; language-specific callers retain their original contract. */
    public synchronized Path exportJava(String id,Map<String,Recovery> edits)throws IOException {
        if(project(id).language()!=Language.JAVA)throw fail("Java export requires a Java project");
        return exportProject(id,edits);
    }
    public synchronized void createFile(String id,String file) throws IOException {
        createFile(id,file,"");
    }
    public synchronized void createFile(String id,String file,String initialText) throws IOException {
        byte[] content=bytes(initialText);
        Path p=source(id,file);String parent=RepositoryPath.parent(file);if(!parent.isEmpty())createDirectory(id,parent);
        CallableIO<Void> write=()->{noAlias(p);Files.write(p,content,StandardOpenOption.CREATE_NEW);return null;};
        if(linkedMinecraft(id))withBound(workingDirectory(id),write);else write.call();
    }
    public synchronized void validateDependencies(String id)throws IOException{var state=dependencyState(id);if(!state.files().containsKey(ProjectDependencies.CONFIG))return;var config=state.config();if(!state.files().containsKey(ProjectDependencies.SCRIPT))throw fail("Managed dependency script missing; reopen the dependency manager");ProjectDependencies.render(state,config);}
    public synchronized Map<String,byte[]> buildRuntimeLibraries(String id,BuildArtifacts.Artifact artifact,java.util.function.BooleanSupplier cancelled)throws IOException{
        Path p=projectDirectory(id);var result=new LinkedHashMap<String,byte[]>(javaLibraries(id));if(!artifact.buildId().isEmpty()&&!GradleOutputs.reported(p,artifact.buildId()))throw fail("Gradle runtime report unavailable; build again before server apply");if(!artifact.buildId().isEmpty())result.putAll(GradleOutputs.runtime(p,artifact.buildId(),artifact.path(),cancelled));return Collections.unmodifiableMap(result);
    }
    public synchronized ProjectDependencies.State dependencyState(String id)throws IOException{
        if(project(id).language()!=Language.JAVA)throw fail("Dependencies require a Java project");var files=new LinkedHashMap<String,String>();
        for(String name:ProjectDependencies.FILES){Path p=entryPath(id,name);if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))files.put(name,decode(readBytes(p,MAX_BYTES)));}
        return new ProjectDependencies.State(files);
    }
    /** Conflict checked multi-file update, restoring bytes on a local write failure. */
    public synchronized void setDependencies(String id,ProjectDependencies.State expected,ProjectDependencies.Config config)throws IOException{setDependencies(id,expected,config,()->false);}
    public synchronized void setDependencies(String id,ProjectDependencies.State expected,ProjectDependencies.Config config,java.util.function.BooleanSupplier cancelled)throws IOException{
        Objects.requireNonNull(cancelled);if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw fail("Dependency update cancelled");
        if(!dependencyState(id).equals(expected))throw fail("Dependency files changed externally; reopen dependency manager");
        if(config.localLibraries()){var jars=JavaSourceRunner.librarySnapshot(javaLibraries(id));long expanded=0;var token=new JavaSourceRunner.Cancellation();for(String name:jars.keySet())expanded+=JavaSourceRunner.verifyLibrary(entryPath(id,"libraries/"+name),128L*1024*1024-expanded,token);}
        var changes=ProjectDependencies.render(expected,config);var originals=new LinkedHashMap<String,byte[]>();var output=new LinkedHashMap<String,byte[]>();
        for(var e:changes.entrySet()){Path p=entryPath(id,e.getKey());if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))noAlias(p);originals.put(e.getKey(),Files.exists(p,LinkOption.NOFOLLOW_LINKS)?readBytes(p,MAX_BYTES):null);output.put(e.getKey(),bytes(e.getValue()));}
        var transaction=DependencyJournal.start(projectPath(id),originals,output);dependencyUpdate=true;var attempted=new ArrayList<String>();
        try{for(var e:output.entrySet()){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw fail("Dependency update cancelled");Path p=entryPath(id,e.getKey());byte[] current=Files.exists(p,LinkOption.NOFOLLOW_LINKS)?readBytes(p,MAX_BYTES):null;
            if(!Arrays.equals(current,originals.get(e.getKey())))throw fail("Dependency file changed during update: "+e.getKey());attempted.add(e.getKey());atomic(p,e.getValue());}if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw fail("Dependency update cancelled");if(config.localLibraries())directories(projectPath(id).resolve("libraries"));Files.delete(DependencyJournal.path(projectPath(id)));
        }catch(IOException|RuntimeException bad){Collections.reverse(attempted);for(String name:attempted)try{Path p=entryPath(id,name);byte[] current=Files.exists(p,LinkOption.NOFOLLOW_LINKS)?readBytes(p,MAX_BYTES):null;
            if(Arrays.equals(current,output.get(name))){byte[] old=originals.get(name);if(old==null)Files.deleteIfExists(p);else atomic(p,old);}
        }catch(IOException cleanup){bad.addSuppressed(cleanup);}try{DependencyJournal.recoverOwned(projectPath(id));}catch(IOException recovery){bad.addSuppressed(recovery);}throw bad;}finally{dependencyUpdate=false;transaction.close();}
    }
    private void atomic(Path target,byte[] data) throws IOException {
        safe(target);directories(target.getParent());Path temp=Files.createTempFile(target.getParent(),".write-",".tmp");
        try{Files.write(temp,data);try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}safe(target);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
    /** Optimistic conflict check; an unrelated OS writer must not race the final atomic move. */
    public synchronized void save(String id,String file,String expected,String text) throws IOException {
        byte[] output=bytes(text);Path p=source(id,file);
        if(!read(id,file).equals(expected))throw fail("File changed outside Scripts. Save refused; draft retained.");
        if(linkedMinecraft(id))withBound(workingDirectory(id),()->{atomic(p,output);return null;});
        else atomic(p,output);
    }
    private Path recoveryPath(String id,String file) throws IOException {
        source(id,file);
        try { String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((id+"/"+file).getBytes(StandardCharsets.UTF_8)));
            Path p=root.resolve(".genesis-recovery").resolve(key+".draft");safe(p);return p;
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    public synchronized void checkpoint(String id,String file,String baseline,String text) throws IOException {
        Path p=recoveryPath(id,file);if(text.equals(baseline)){Files.deleteIfExists(p);return;}
        byte[] a=bytes(baseline),b=bytes(text);var out=new ByteArrayOutputStream();
        try(var d=new DataOutputStream(out)){d.writeInt(1);d.writeInt(a.length);d.write(a);d.writeInt(b.length);d.write(b);}
        atomic(p,out.toByteArray());
    }
    public synchronized Recovery recovery(String id,String file) throws IOException {
        Path p=recoveryPath(id,file);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return null;
        try(var in=new DataInputStream(new ByteArrayInputStream(readBytes(p,MAX_BYTES*2+12)))){
            if(in.readInt()!=1)throw fail("Unsupported recovery format");String a=field(in),b=field(in);if(in.read()!=-1)throw fail("Invalid recovery record");return new Recovery(a,b);
        }
    }
    private static String field(DataInputStream in) throws IOException {int n=in.readInt();if(n<0||n>MAX_BYTES)throw fail("Recovery size limit");byte[] b=in.readNBytes(n);if(b.length!=n)throw fail("Incomplete recovery record");return decode(b);}
}