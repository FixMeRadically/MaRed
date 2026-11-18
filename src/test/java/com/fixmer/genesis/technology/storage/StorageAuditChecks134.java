package com.fixmer.genesis.technology.storage;

import com.fixmer.genesis.technology.scripts.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Regression scenarios discovered by the first file audit; uses isolated temporary folders. */
public final class StorageAuditChecks134 {
    private static int passed, failed, skipped;
    private static final class Unavailable extends Exception{Unavailable(String reason){super(reason);}}
    private static Path root;
    @FunctionalInterface interface Scenario { void run() throws Exception; }
    private static void check(String name, Scenario scenario) {
        try { scenario.run(); passed++; System.out.println("PASS " + name); }
        catch (Unavailable unavailable){skipped++;System.out.println("SKIP "+name+": "+unavailable.getMessage());}
        catch (Throwable error) { failed++; System.out.println("FAIL " + name + ": " + error); }
    }
    private static void require(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
    private static void rejects(Scenario scenario) throws Exception {
        try { scenario.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid operation was accepted");
    }
    private static Path external(String name) throws IOException { return Files.createDirectories(root.resolve(name)); }
    private static SourceProjects catalog(String name) { return new SourceProjects(root.resolve(name)); }
    private static void writeManifest(Path path, Properties properties) throws IOException {
        var text = new StringWriter(); properties.store(text, "test"); Files.writeString(path, text.toString());
    }
    private static void createLink140(Path link,Path target)throws Exception{
        try{Files.createSymbolicLink(link,target);return;}
        catch(FileSystemException|UnsupportedOperationException|SecurityException denied){
            if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))throw denied;
            if((link.toString()+target).matches(".*[&|<>^%!\"].*"))throw new Unavailable("Junction fixture path needs unsupported shell quoting; protection was NOT tested");
            Process process=new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",link.toString(),target.toString()).redirectErrorStream(true).start();
            if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Junction fixture timed out");}
            if(process.exitValue()!=0||!Files.isDirectory(link))throw new Unavailable("OS does not allow symlink/junction fixture; protection was NOT tested");
        }
    }
    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("mared-storage-audit134-");
        try {
            check("linked export contains source and draft, not only link metadata", () -> {
                var projects = catalog("export-catalog"); Path folder = external("export-folder");
                Files.writeString(folder.resolve("main.mcfunction"), "say saved\n");
                projects.linkFolder133("Map", folder);
                Path zip = projects.exportProject("Map", Map.of("main.mcfunction", new SourceProjects.Recovery("say saved\n", "say draft\n")));
                try (var archive = new ZipFile(zip.toFile())) {
                    require(archive.getEntry("main.mcfunction") != null, "Source missing from export");
                    require(new String(archive.getInputStream(archive.getEntry("main.mcfunction")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).equals("say draft\n"), "Draft missing");
                    Properties p = new Properties(); p.load(new InputStreamReader(archive.getInputStream(archive.getEntry("genesis-project.properties")), java.nio.charset.StandardCharsets.UTF_8));
                    require(!p.containsKey("linked_path"), "Export leaks original machine path");
                    require(!p.getProperty("workspace", "").startsWith("linked_"), "Export still points at original tree");
                }
                require(Files.readString(folder.resolve("main.mcfunction")).equals("say saved\n"), "Export saved draft to original");
            });
            check("missing previous folder does not block linking another folder", () -> {
                var projects = catalog("stale-catalog"); Path old = external("removed-folder"); projects.linkFolder133("Old", old); Files.delete(old);
                Path fresh = external("fresh-folder"); projects.linkFolder133("New", fresh);
                require(projects.workingDirectory("New").equals(fresh), "New link unavailable");
            });
            check("relative linked_path metadata is rejected", () -> {
                var projects = catalog("relative-catalog"); Path folder = external("relative-folder"); projects.linkFolder133("Map", folder);
                Path metadata = projects.root().resolve("Map/genesis-project.properties"); Properties p = new Properties(); p.load(new StringReader(Files.readString(metadata)));
                p.setProperty("linked_path", Path.of("").toAbsolutePath().relativize(folder).toString()); writeManifest(metadata, p);
                rejects(() -> projects.workingDirectory("Map"));
            });
            check("trash preserves whitespace following dots in a valid path", () -> {
                var projects = catalog("trash-catalog"); projects.createEmpty("Map"); projects.createFile("Map", "a. note.mr", "say before");
                Path trash = projects.trashEntry("Map", "a. note.mr", Map.of()); projects.restoreTrash(trash);
                require(projects.read("Map", "a. note.mr").equals("say before"), "Wrong restored path");
            });
            check("trash rejects directory/project metadata mismatch", () -> {
                var projects = catalog("trash-owner-catalog"); Path a = external("trash-a"), b = external("trash-b");
                projects.linkFolder133("A", a); projects.linkFolder133("B", b); projects.createFile("A", "file.mr", "from A");
                Path trash = projects.trashEntry("A", "file.mr", Map.of()); Properties p = new Properties(); p.setProperty("project", "B"); p.setProperty("path", "file.mr"); writeManifest(trash.resolve("origin.properties"), p);
                rejects(() -> projects.restoreTrash(trash)); require(Files.exists(trash.resolve("data")), "Trash data lost"); require(!Files.exists(b.resolve("file.mr")), "Moved into unrelated project");
            });
            check("junction/symlink ancestor cannot be used by createFile", () -> {
                var projects = catalog("links-catalog"); projects.createEmpty("Map"); Path outside = external("outside");
                createLink140(projects.root().resolve("Map/escape"),outside); rejects(() -> projects.createFile("Map", "escape/file.mr", "data")); require(!Files.exists(outside.resolve("file.mr")), "Escaped root");
            });
            check("project paths reject Windows device aliases and trailing segment spaces", () -> {
                for (String path : List.of("a /file.mr", "CON .txt", "COM1 .txt")) require(!ProjectPath.valid(path), "Accepted " + path);
                require(ProjectPath.valid("папка с пробелами/file.mr"), "Valid Unicode name rejected");
            });
            check("Commands migration recovers committed project after missing marker", () -> {
                Path legacy = root.resolve("legacy"); var old = new TextRepository(legacy); old.create("main", "say original"); Path destination = root.resolve("migration-catalog");
                CommandProjects133.migrate(destination, legacy); Files.delete(destination.resolve(".legacy-commands133"));
                var projects = new SourceProjects(destination); projects.save("LegacyCommands", "main.mr", "say original", "say edited"); CommandProjects133.migrate(destination, legacy);
                require(projects.list().projects().size() == 1, "Duplicated committed migration"); require(projects.read("LegacyCommands", "main.mr").equals("say edited"), "Replaced edits");
            });
            check("Commands migration preserves a tree containing only empty folders", () -> {
                Path legacy=root.resolve("empty-legacy"),destination=root.resolve("empty-migration");var old=new TextRepository(legacy);
                old.createDirectory("empty");old.createDirectory("empty/nested");CommandProjects133.migrate(destination,legacy);
                require(new SourceProjects(destination).folders("LegacyCommands").contains("empty/nested"),"Empty folder tree was discarded");
                require(old.directoryExists("empty/nested"),"Original folder removed");
            });
            check("archive write/read round trip supplies implicit parent folders", () -> {
                Path archive = root.resolve("implicit.zip"); ProjectArchive.write(archive, new ProjectArchive.Plan(Map.of("folder/main", "say hello"), Set.of(), 0));
                var plan = ProjectArchive.read(archive); require(plan.directories().contains("folder"), "Implicit folder missing");
                var repository = new TextRepository(root.resolve("archive-import")); repository.createBatch(plan.documents(), plan.directories()); require(repository.read("folder/main").equals("say hello"), "Roundtrip failed");
            });
            check("archive rejects conflicts with implicit parent case aliases", () -> {
                Path archive = root.resolve("aliases.zip"); rejects(() -> ProjectArchive.write(archive, new ProjectArchive.Plan(Map.of("A/first", "x", "a/second", "y"), Set.of(), 0)));
                require(!Files.exists(archive), "Published archive that cannot roundtrip");
            });
            check("batch preview detects collisions inside a new batch", () -> {
                var repository = new TextRepository(root.resolve("batch")); var documents = Map.of("same", "first", "SAME", "second");
                require(!repository.previewBatch(documents, Set.of()).isEmpty(), "Preview missed case alias"); rejects(() -> repository.createBatch(documents, Set.of())); require(repository.list().isEmpty(), "Batch partially published");
            });
            check("batch preview detects file versus implicit directory collision", () -> {
                var repository = new TextRepository(root.resolve("batch-types"));
                require(!repository.previewBatch(Map.of("node", "x", "node.txt/child", "y"), Set.of("node.txt")).isEmpty(), "Preview missed physical file/directory conflict");
            });
            check("linked folder Gradle initialization writes the actual folder", () -> {
                var projects=catalog("gradle-catalog");Path folder=external("gradle-folder");projects.linkFolder133("Java",folder);projects.initializeGradle("Java");
                require(Files.exists(folder.resolve("build.gradle")),"Build files only exist in private catalog");
                require(!Files.readString(folder.resolve("build.gradle")).contains("file('genesis-project.properties')"),"Generated build reads nonexistent private metadata");
                require(!Files.exists(projects.root().resolve("Java/build.gradle")),"Shadow build exists");
            });
            check("linked folder libraries and resources use the actual folder", () -> {
                var projects=catalog("runtime-catalog");Path folder=external("runtime-folder");projects.linkFolder133("Java",folder);
                Files.createDirectories(folder.resolve("resources"));Files.writeString(folder.resolve("resources/message.txt"),"hello");
                require(projects.javaResources("Java").containsKey("message.txt"),"Linked resources silently ignored");
                Files.createDirectories(folder.resolve("libraries"));Files.write(folder.resolve("libraries/test.jar"),new byte[]{1,2,3});
                require(projects.javaLibraries("Java").containsKey("test.jar"),"Linked libraries silently ignored");
            });
            check("linked folder dependency state reads actual build files", () -> {
                var projects=catalog("dependency-catalog");Path folder=external("dependency-folder");projects.linkFolder133("Java",folder);
                Files.writeString(folder.resolve("build.gradle"),"plugins { id 'java' }\n");
                require(projects.dependencyState("Java").files().containsKey("build.gradle"),"Linked dependency files inaccessible");
                require(projects.importedGradle("Java"),"Linked Gradle project treated as direct Java sources");
            });
            check("linked dependency writes and cancellation stay in the actual folder", () -> {
                var projects=catalog("dependency-write-catalog");Path folder=external("dependency-write-folder");projects.linkFolder133("Java",folder);
                Files.writeString(folder.resolve("build.gradle"),"plugins { id 'java' }\n");
                var config=new ProjectDependencies.Config(List.of(ProjectDependencies.Dependency.library(ProjectDependencies.Mode.LIBRARY,"org.example:library:1.0")),List.of(),false);
                var original=projects.dependencyState("Java");int[] calls={0};
                rejects(()->projects.setDependencies("Java",original,config,()->++calls[0]>=4));
                require(projects.dependencyState("Java").equals(original),"Cancelled external update left partial dependency files");
                projects.setDependencies("Java",original,config);
                require(Files.exists(folder.resolve(ProjectDependencies.CONFIG)),"Dependency config written into shadow catalog");
                require(!Files.exists(projects.root().resolve("Java/"+ProjectDependencies.CONFIG)),"Private catalog contains dependency files");
                require(!Files.exists(folder.resolve(".genesis-dependency-update")),"Completed journal not removed");
            });
            check("Minecraft link metadata escapes multiline values and creates a fresh catalog", () -> {
                Path folder=external("minecraft-folder");
                var structure=new com.fixmer.genesis.technology.minecraft.ProjectStructure(folder,false,false,List.of(),List.of(),List.of(),List.of(),List.of(),false,false);
                String main="Main\nworkspace=local_sources\nlinked_path=relative";
                var info=new com.fixmer.genesis.technology.minecraft.MinecraftProjectInfo(structure,com.fixmer.genesis.technology.minecraft.ProjectType.JAVA,com.fixmer.genesis.technology.minecraft.MinecraftLoader.UNKNOWN,"demo","Demo","1","",List.of(),main,"","21","",List.of(),null,List.of(),List.of(),null);
                var projects=catalog("minecraft-fresh-catalog");projects.createMinecraftLink("Demo",folder,info);
                require(projects.linkedMinecraft("Demo"),"Value injected a metadata property");
                require(projects.workingDirectory("Demo").equals(folder),"Metadata newline changed linked root");
                require(projects.javaMainClass("Demo").equals(main),"Metadata value did not roundtrip");
            });
            check("linked dependency crash journal is recovered from the actual folder", () -> {
                var projects=catalog("journal-catalog");Path folder=external("journal-folder");projects.linkFolder133("Java",folder);
                byte[] before="plugins { id 'java' }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),after="plugins { id 'java' }\n// partial update\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Files.write(folder.resolve("build.gradle"),after);
                try(var out=new DataOutputStream(Files.newOutputStream(folder.resolve(".genesis-dependency-update")))){
                    out.writeInt(0x47445431);out.writeInt(2);out.writeUTF("build.gradle");out.writeInt(before.length);out.write(before);out.writeInt(after.length);out.write(after);
                    out.writeUTF(ProjectDependencies.CONFIG);out.writeInt(-1);out.writeInt(0);
                }
                projects.project("Java");require(Arrays.equals(before,Files.readAllBytes(folder.resolve("build.gradle"))),"Partial update was not rolled back");
                require(!Files.exists(folder.resolve(".genesis-dependency-update")),"Recovered journal remains");
            });
            check("archive export refuses overwrite and keeps original bytes", () -> {
                Path archive=root.resolve("no-overwrite.zip");var plan=new ProjectArchive.Plan(Map.of("main","say first"),Set.of(),9);ProjectArchive.write(archive,plan);
                byte[] before=Files.readAllBytes(archive);rejects(()->ProjectArchive.write(archive,plan));require(Arrays.equals(before,Files.readAllBytes(archive)),"Existing archive changed");
            });
            check("recovery and failed save preserve the original file and draft", () -> {
                var projects=catalog("draft-catalog");projects.createEmpty("Map");projects.createFile("Map","main.mr","base");projects.checkpoint("Map","main.mr","base","draft");
                Files.writeString(projects.root().resolve("Map/main.mr"),"external");rejects(()->projects.save("Map","main.mr","base","draft"));
                require(projects.read("Map","main.mr").equals("external"),"External edit overwritten");require(projects.recovery("Map","main.mr").text().equals("draft"),"Draft lost");
            });
            check("copy folder respects destination context when counting build folders", () -> {
                var projects = catalog("copy-catalog"); projects.createEmpty("Map"); projects.createDirectory("Map", "src/build"); projects.createFile("Map", "src/build/test.mr", "say test");
                rejects(() -> projects.copyEntry("Map", "src", "copied", Map.of())); require(!Files.exists(projects.root().resolve("Map/copied")), "Published invisible copied subtree");
            });
        } finally { try (var files = Files.walk(root)) { for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
        System.out.println("StorageAudit134 scenarios passed=" + passed + " failed=" + failed+" skipped="+skipped);
        if (failed > 0) throw new AssertionError("Audit regressions: " + failed);
    }
}
