@@ start|Getting started|Scripts: capability map|Built-in Help for Genesis / MaRed 0.4.0: projects, editing, Java execution and Gradle builds.|help guide manual capabilities version
## What Scripts does
Scripts is a source-code workspace. Create projects, import folders, organize files, edit text, save drafts, run standalone Java programs and build configured Gradle projects. This manual is available offline. Opening a page or copying an example never executes that example.

Java uses a real compiler and a separate JVM. A main program is different from a NeoForge mod. Minecraft APIs require the appropriate Gradle dependencies and lifecycle; the ordinary Java runner does not receive live game objects. MR belongs to Commands and is not a language or API of Scripts.

Python, Rust and C++ sources can be edited and exported, but their execution engines are not integrated. Genesis Java ServerModule can now be applied and replaced in a singleplayer world without reconnecting. Multiplayer/LAN, universal hot installation of ordinary mods, a breakpoint debugger, semantic Java language server and automatic refactoring remain absent.
::: flow|Create or import project → Edit sources → Choose launch mode → Read logs and diagnostics → Deliver artifact or export project
## Reading Help
The wide window has an article index and a reading pane. Search checks titles, summaries, keywords and complete article bodies, including code. All query words must match. Category and bookmark filters further restrict results; restore All and the unfiltered category when necessary.

Back and Forward retain navigation history and per-page scroll. Copy page copies the article; Copy code copies only the original source. Bookmarks and history last for this Genesis window, not across game restarts. The full Help window can be moved and resized. Dock right uses a compact panel; Open window restores the floating view. In a narrow panel, Contents switches index and article, and searching displays the index.

Process diagrams and the step viewer are illustrations, not Minecraft recordings or a video player. They never run code, save files or start builds.
=> first-run|Start with a working Java program
=> mod|Build a standalone NeoForge mod
=> limits|Current limitations

## Java and Gradle tool paths
Paste an absolute JDK path, including Windows backslashes and spaces. Explorer quotes are removed. A `bin` folder or `java.exe`/`javac.exe` path is normalized to the JDK root. Use a full JDK 21 or newer with Java, javac and a release file. Saved configuration takes priority; otherwise `JAVA_HOME`, then client Java is inspected. Missing or changed tools are checked again before use.

The saved JDK is used for both local Java execution and server module compilation. Neither needs Wrapper. Apply `WorldModule` through Apply on server: its entry is `onEnable`, with no `main` required. Run Java invokes `main(String[] args)` in a separate JVM and does not apply changes to the world.

Gradle needs a real `gradle-wrapper.jar` with adjacent `gradle-wrapper.properties`. Select the JAR, properties file, Wrapper folder or a project root containing `gradle/wrapper`. `gradlew.bat` is not the Wrapper JAR. The selected project's Wrapper takes priority over global configuration; a broken project Wrapper is reported rather than silently replaced. Select a Wrapper from a trusted working project if missing. Saving a path performs no downloads or execution. Task discovery/build can download the configured distribution and dependencies; network and compatibility failures are retained in the Gradle report.

@@ first-run|Getting started|Your first Java program|A complete route from a project to output in the logs.|hello main println first run java
::: tutorial|First Java run
## 1. Create the project
In Scripts, open Project and create a Java project with a unique name. Select its root in the explorer. Use a standalone Java project rather than an imported Gradle project for this lesson. Create Main.java in the root or src folder, or open the existing template file. Choose the destination folder before creating the file.
## 2. Paste this into Main.java
The public class name must match the filename. This example has no package declaration. Copy code preserves source line breaks even when the display wraps.
```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, Genesis");
    }
}
```
## 3. Launch
Select Main.java in the explorer. Open the arrow next to Run and choose Run Java file; choosing the mode does not execute anything. Press Run, confirm Main as the entry class, and wait. Ctrl+R follows the selected mode. All project sources are compiled, so an error in another class can also block execution.
## 4. Check output
Expand the bottom logs using the arrow at their boundary. Expect Hello, Genesis and a successful result. Resize the logs with the divider. Change the message and run again: open drafts participate in local Java execution. Save / Save all is still required to keep changes on disk.

Compiler unavailable means the client needs a complete JDK. Main class errors require checking the class name and package. Compile errors require fixing the source. Do not switch to Build mod for this small main example.
=> java|Entry classes and Java lifecycle
=> errors|Troubleshooting

@@ projects|Projects|Projects and workspace folders|Project roots, selection, multiple expanded trees and physical storage.|project folder root workspace path
## A project is a folder
Each project has its own sources, resources and configuration. Multiple roots can be expanded together. The root arrow expands or collapses; clicking the name selects it. The active project and its continuous indentation guides use the Logic accent, while other roots are neutral.

Opening a file in another expanded project switches project context first. Collapsing a root does not close documents or discard drafts. The project selected in the explorer determines the launch target; do not assume the last edited document is the target of every operation.
## Physical location
Working projects live in config/mared/script_projects under the Minecraft launch directory. Copy project path supplies the exact absolute location for your OS file manager. A development launch may use a run/config folder. genesis-project.properties contains Genesis project metadata; arbitrary changes can break project interpretation.
## Refresh and state
The explorer displays cached trees. Refresh rereads files after changes made by external tools; it is not a forced overwrite of drafts. Up to 16 project trees are cached; older roots collapse when that cache fills, without deleting documents.

Supported selected-project and nested-folder context can be restored. The set of simultaneously expanded roots and the current launch mode are session state, not permanent project settings.
=> files|File and folder operations
=> drafts|Saving and recovery

@@ files|Projects|Files and folders|Create, rename, copy, delete and restore entries.|file folder rename delete restore copy paste
## Search and keyboard
The centered editable search field sits below menus and Run. Click the field and start typing. Five modes and matching files/results appear below the same field. Clicking a mode or pressing Tab changes the mode while retaining your query. Arrows select results, Enter opens, Esc closes.

- Ctrl+P: filename or relative path in the selected project. An empty query lists all cached files, up to 4096, in a scrollable list. Binary results are revealed in the explorer rather than opened as text.
- Ctrl+F: literal text in the current document, including its unsaved draft; the match is selected.
- Ctrl+Shift+F: text across the selected project. Open documents use current drafts; other files use saved text. Results show path, line and preview. Enter opens the file; changed text rejects stale navigation.
- Ctrl+G: an existing line in the current file, numbered from 1.
- Ctrl+O: filter the selected project's open documents and switch without saving drafts.

Text search is case-insensitive and literal, without regex or replacement. Nonempty queries are capped at 200 results; refine queries when capped. Project search is bounded to 4096 files and 8 MiB of text, runs on a worker and cancels on close. Read failures are reported while readable matches remain. Refresh updates externally changed file lists. Read-only navigation is supported.

Shift+arrows extends selection. Ctrl+Shift+Left/Right selects by words, Ctrl+Shift+Up/Down to a paragraph boundary. Shift+Home/End selects to the line boundary; Ctrl+Shift+Home/End to the document boundary. Plain Left/Right collapses selection to its edge. Ctrl+Shift+Z redoes an edit.

A new file with a valid Java class name receives public class named after the file. Standard src/main/java, src/test/java, src and tests roots infer package from subfolders. Custom sourceSets are not inferred. module-info.java, package-info.java and invalid class names remain empty. Existing and imported files are unchanged.

## Create and navigate
Select a project root or destination folder, then use File or its context menu to create a file or folder. Paths must remain inside the project. Java source uses .java; binary assets can appear in the tree without opening as text.

Right-click a project or folder → New file / New folder. Enter only a name (with an extension for a file, such as Block.java), not a full path. Creation uses that folder; the new file opens and a new folder appears in the tree. Right-click a file to create beside it, or empty space to create at the current project root.

Up/Down move selection, Left/Right collapse or expand, and Enter opens an editable file. Right-click establishes the context of the clicked row. Verify that context before a destructive or moving operation.
## Rename
F2 renames the selected entry. This is a filesystem operation, not Java refactoring. Renaming Main.java does not change its public class, imports, package declarations or entry class. Conflicts and draft-loss checks can reject the operation; resolve them explicitly rather than overwriting another file.
## Copy, delete and restore
Ctrl+C in the explorer prepares an entry copy; Ctrl+V pastes into the selected destination. This differs from copying selected source text. Copy path is a separate clipboard action. Copy preserves the original and refuses conflicting destinations. This entry buffer currently supports destinations within the same project, not cross-project paste.

Delete uses confirmation and reversible internal deletion. Restore does not replace occupied paths. Genesis deletion is not necessarily the Windows recycle bin. Verify restored entries before continuing.

Keep packages and resources organized. Unicode and spaces in paths remain subject to path validation; Java class and package names still follow Java rules.
=> multi-file|A two-class example

@@ drafts|Editor|Save, drafts and recovery|The difference between in-memory text, disk files and exported snapshots.|save drafts recovery conflict restore
::: flow|Edit document → In-memory draft → Save / Save all → Check external changes → Write source file
## Memory versus disk
Save writes the active document; Save all writes modified documents. Switching projects does not secretly save. Local Java runs use snapshots of open drafts. Gradle reads disk files, so the editor prepares and saves required changes before building. A save failure or conflict prevents that preparation from being reported as a successful new build.
## External edits
When disk content changed after opening, the saved baseline is checked. Preserve a copy of your draft, compare it with the external version, then decide explicitly which text should remain. Refreshing the tree is not a forced overwrite command. Closing a dirty document requires a save/discard decision; selecting another file does not discard it.
## Recovery
Recovery data and workspace context can help reopen work, but do not replace backups. A hard process termination may interrupt their write. Review recovered content and its baseline before saving. Up to 16 documents are open at once; close unused tabs and keep important work on disk.
=> export|Export drafts without confusing ZIP and JAR

## Scripts logs
The bottom log panel offers the same search, filters, text selection, copy, clear and export actions as Commands. Its entries and filters are local to Scripts. Categories are editor, java, gradle, server and tools; runtime failures have ERROR severity. Log filter changes do not change Commands filters. Clearing history leaves build reports, the last operation output and active server modules intact. Export creates a new UTF-8 .log file and copies its path; no previous export is replaced.

Click logs to focus them. Ctrl+F searches logs, Ctrl+A selects visible entries and Ctrl+C copies selected text or all visible entries. When the log search field has focus, those keys edit that field. Esc closes log search/filter UI. The compact header uses E (export), X (clear), C (copy), / (search), F (filters). Long lines are clipped to the viewport visually; copying/export preserves stored text. History is bounded by the configured capacity, and runner output remains bounded independently.

@@ editor|Editor|Workspace and navigation|Panels, document tabs, focus and visual settings.|workspace editor panels resize search undo settings
## Panels and context
The Logic rail and project explorer are on the left, code in the center, logs at the bottom, and Help in a separate window or right panel. Drag dividers to allocate space; collapsing a panel does not delete its content. Compact widths can move menus into overflow.

The rail uses distinct silhouettes for Commands, Scripts, Events, Blueprint, AI, Data, Network and Sandbox. Category colors apply consistently to related UI, while the functionality of each tab remains distinct.

Document tabs select open text; explorer selection determines project and filesystem targets. Select the source in the explorer before a file run. Text editing provides selection, clipboard operations, undo/redo and available find/replace menus. Shortcuts depend on focus: Ctrl+C can copy source, prepare a file copy, or copy a Help article.
## Settings and IDE limits
Common Genesis visual settings affect appearance. The settings icon opens the existing full settings window for Scripts, with panels/appearance, theme, editor, logs, Java/Gradle, shortcuts and About sections. Save applies the draft; Cancel and Esc discard it. Commands tab selection, execution parameters and log filters are not shown here. JDK/Wrapper actions save current settings and return to the editor before opening the path dialog. Scripts settings control automatic explorer reveal, new Java class templates, retaining logs between launches, opening logs on launch, and history capacity (256/1024/2048 lines). JDK/Wrapper paths and tool checks are also reachable there. These preferences and log filters are stored for later editor sessions. Commands execution settings do not configure Java. Syntax colors and interface hints do not replace the compiler. Full Java language-server analysis, semantic refactoring, import organization and breakpoint debugging are absent.
=> launch|Launch modes
=> help-tools|Help controls

@@ java|Java|Java: main, packages and execution|A real compiler and separate process, without an automatic Minecraft classpath.|java JVM main package compiler classpath
## Entry class
Provide public static void main(String[] args). Enter the qualified class name, such as demo.Main, rather than src/demo/Main.java. Do not add .class, paths or command-line arguments to the entry field. File mode guesses a simple package and filename; verify that guess. Its temporary entry does not rewrite the project's configured entry class.
## Execution snapshot
The runner collects supported project sources and open drafts, validates local JARs and resources, compiles into a temporary workspace and launches the selected main. Editing after launch does not change the running process. Errors in other project sources can block compilation. For individual functions, write a small main or use configured Gradle tests.
::: flow|Sources and drafts → Validate libraries / resources → Compile Java → Separate JVM main → Output and result in logs
## Minecraft and process rights
The ordinary runner does not inherit the Minecraft classpath or receive a live world, player or server. Minecraft imports need a suitable Gradle project. System.out is local output, not a chat message or server command. Java executes with the user's process permissions. Time, heap and output limits protect the editor but are not an OS filesystem/network sandbox.
=> libraries|Libraries and resources
=> limits|Runner limits

@@ multi-file|Java|Example: two classes and a package|A small complete project with a check that can fail visibly.|example Calculator demo Main package sources
## Structure
Create src/demo in a standalone Java project and add these two files. Both declare package demo. The runner includes supported .java files, excluding tests/, src/test/, libraries/ and resources/. Gradle projects use their configured sourceSets instead.
## src/demo/Calculator.java
```java
package demo;
public final class Calculator {
    private Calculator() {}
    public static int sum(int a, int b) {
        return a + b;
    }
}
```
## src/demo/Main.java
```java
package demo;
public class Main {
    public static void main(String[] args) {
        int result = Calculator.sum(2, 3);
        if (result != 5) {
            throw new IllegalStateException("Unexpected sum: " + result);
        }
        System.out.println("sum = " + result);
    }
}
```
## Verify
Select Main.java, choose file mode and confirm demo.Main. Expect sum = 5. Altering Calculator's result makes the check throw an exception. This is a small logic check, not integrated JUnit or a debugger. If the class is missing, check packages, filenames, source inclusion and compiler errors.
=> errors|Troubleshooting

@@ libraries|Java|Libraries and resources|Local runner inputs versus Gradle dependencies.|libraries jar dependency resources classpath
## Local dependencies
For standalone Java projects, put ready-made JARs into libraries at the project root. Copy libraries path gives the exact destination. Refresh and run again. Libraries are supplied to both compilation and execution.

The runner does not resolve Maven coordinates, fetch transitive dependencies, load arbitrary native libraries or run Java agents. Add required compatible JARs explicitly. Manifest Class-Path and unsafe paths cannot bypass validation. A library compiled for a newer Java can fail at runtime.
## Resources
Files in the root resources folder become classpath resources. Create resources/message.txt in UTF-8 and read /message.txt as follows:
```java
import java.nio.charset.StandardCharsets;
public class Main {
    public static void main(String[] args) throws Exception {
        try (var in = Main.class.getResourceAsStream("/message.txt")) {
            if (in == null) throw new IllegalStateException("message.txt missing");
            System.out.println(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
```
## Gradle projects
Declare dependencies and repositories in build.gradle / build.gradle.kts and configure sourceSets. Conventional resources go in src/main/resources. Imported Gradle projects do not automatically receive the ordinary runner libraries classpath. A library JAR is not itself a Minecraft mod; mod metadata and lifecycle still need configuration.
=> gradle|Build tools

@@ launch|Build|The Run arrow and modes|Explicit targets: Gradle task, Java main or singleplayer server application.|run build debug server mode arrow
## Build mod
Runs the saved Gradle task of the explorer-selected project. Selecting a mode or task does not execute it. Press Run or Ctrl+R afterwards. Required changes are saved before build preparation succeeds. A label cannot turn an arbitrary source into a mod. Check operation status for the actual project and task if you switch context during a build.
## Run Java file
Available for a selected .java file in a standalone Java project. Confirm its main class, then compile the project and run that entry in a separate JVM. Drafts participate without mandatory save. Imported Gradle projects use Gradle tasks instead. A stale entry dialog cannot silently launch a newly selected project/file.
## Apply in world
Runs a Genesis ServerModule in the current singleplayer world without LAN. This is a separate Java contract, not main or @Mod. Standalone projects compile sources and drafts; imported Gradle projects select a JAR from a successful build. Building alone does not activate code.
=> server-first|First world application
## Stop
Cancels the currently managed Java, Gradle, import or artifact-delivery operation. Preparation also checks cancellation. Partial Gradle outputs may remain on disk after cancellation; do not treat them as a successful new build.
=> tasks|Gradle tasks
=> artifacts|Build artifacts

@@ gradle|Build|Gradle tools and Wrapper|Choose the build JDK, preserve project configuration and diagnose first-build setup.|gradle jdk wrapper java_home tools build.gradle

## Build stability
A Gradle root may contain settings.gradle or settings.gradle.kts only, with build files in subprojects. Task paths support numeric, underscore and Unicode names. The Wrapper is checked again immediately before execution. The launcher uses the selected JDK and passes org.gradle.java.home explicitly. Project gradle/gradle-daemon-jvm.properties criteria take precedence for the daemon JVM; the report records their presence. Toolchains and JDK provisioning follow project configuration.

Run and the full Java/Gradle settings expose selected-project limits: 1–1440 minutes and 0–64 workers. Defaults are 10 minutes and 2 workers; 0 leaves worker count to Gradle. Values persist in genesis-project.properties. Cancellation/timeout terminate the process; another build can then start.

Saving files of the building project is blocked until Gradle completes or stops; editing drafts is still allowed. Saving changed sources invalidates the old build report, requiring a new build before applying a JAR. Preparation failure or cancellation replaces previous success and does not offer stale artifacts. Reports include project, launcher JDK, Wrapper, task, limits and termination reason.
## Java environments
The client and local Java runner use the client's Java environment. Gradle tools can use a separately configured JDK; changing it does not replace the running client's JVM. The included NeoForge template requires a full JDK 21. Supply its directory containing bin/java and bin/javac, not java.exe.

In build-tool configuration, specify absolute paths to the JDK home and gradle-wrapper.jar. gradlew.bat is not the Wrapper JAR. Validation checks WrapperMain and distributionUrl properties. Do not save surrounding quotes as part of a path.
## Project Wrapper
Imported projects use their own gradle/wrapper files. A broken own Wrapper is not silently replaced with a global one. Fix that project's files. Projects without their own Wrapper need explicitly configured valid tools under the applicable project rules. Preserve the project's build.gradle, settings.gradle, repositories, toolchain and plugin compatibility.
## Initial build
Downloading Gradle, plugins and dependencies can require network access. TLS, proxy and repository errors are not main-class errors. Read the exact failure in logs. The editor invokes Wrapper via the chosen Java with project-dir and bounded workers. Even tasks --all evaluates real build configuration and can execute code or download dependencies.
=> tasks|Discover and choose tasks
=> mod|NeoForge starter

@@ tasks|Build|Gradle tasks and modules|Discover tasks --all, filter and save a task without executing it.|tasks module build test runClient
## Discover
Open the task picker or refresh tasks from the run menu. The editor separately runs tasks --all and shows parsed groups, descriptions and paths. These come from the project rather than hardcoded templates. Choosing and confirming a row saves the task; Run executes it later. Discovery does not replace the previous build report or artifact snapshot.
## Pick the correct path
build targets the root; module:build or :module:build targets an existing module. test needs a configured test task. runClient launches a separate development client only when the project's plugins define it; it is not a reload of the current world.

The field accepts one valid task path, without spaces, free CLI flags or shell chains. Define a custom Gradle task for complex workflows. Dynamic rules are not automatically concrete listed tasks; output and result counts are bounded.
## Missing tasks
Check project selection, plugins and the result of tasks --all. A failed refresh does not verify old task data against the new configuration. Filtering finds listed tasks; it cannot create absent modules or fix build scripts.
=> diagnostics|Build diagnostics

@@ diagnostics|Build|Logs and source diagnostics|Find the actual failure and navigate only to validated project sources.|diagnostic error warning stacktrace line column
## Status and cause
Open bottom logs and the build report/diagnostics. Note the requested project and task. BUILD FAILED is the outcome; the substantive cause normally appears earlier. Deprecated API warnings are not automatically a failed build.

Supported Java/Gradle positions can navigate to a source and line. Windows paths, Unicode and nested files are handled subject to project-root validation. Outside paths, traversal, URLs and invalid positions do not open arbitrary computer files. Such entries can still be useful as text.
## Limits
This parser is not a Java language server and cannot understand every plugin's format. Diagnostic counts and captured output are bounded. For truncated logs, reproduce a specific issue or run a focused task. Java runner output includes compiler errors and process stack traces. A printed message does not guarantee success: the process can fail afterwards.
=> errors|Error, cause and next action

@@ artifacts|Build|Artifacts and standalone JARs|Find outputs, obtain a verified copy and distinguish a mod from other archives.|artifact jar zip sha256 build libs distributions standalone
::: flow|Successful requested task → Discover build/libs and distributions → Size and SHA-256 snapshot → Select artifact → Deliver verified copy
## Outputs
After a successful build, inspect artifacts from build/libs and build/distributions in the root and supported nested modules. Cache files are not scanned as build outputs. sources.jar and javadoc.jar are not installable mods. A successful test task may create no JAR and leave older outputs in build; discovery is not proof of which task originally created each file.
## Delivery
The selected artifact is checked against path, size and SHA-256 before delivery. A changed file is refused instead of publishing a stale snapshot. Build or check again. Delivery creates a separate file under .artifacts in workspace storage and can copy its path. Previous deliveries are not silently overwritten; cancellation does not publish a partial copy.
## Independence
A normal correctly configured NeoForge mod can work without MaRed if its code and dependencies do not require MaRed/Genesis. The starter has no mandatory editor dependency. A plain Java library or source ZIP is not automatically such a mod. Verify game/loader versions and dependencies, test in another client, install via mods and restart. Hot installation into the current world is absent.
=> mod|Starter-to-mod workflow
=> export|Export an editable project

@@ import|Projects|Import folders and Gradle projects|Create a working copy without modifying the source folder.|import folder gradle copy
## Choose the root
In Project, choose the suitable import operation and select the original directory. Choose the root containing configuration and sources, not a single .java file or build folder. Gradle roots contain build.gradle or build.gradle.kts plus their related settings.

Import creates a copy in Genesis storage. The original folder is not a live linked workspace and does not receive subsequent edits. Coordinate changes with a teammate through exported copies and explicit version comparison.
## Preservation and checks
Supported structure, sources, binary resources and hidden project files are preserved within import rules. External links and caches are not a way to mount arbitrary computer directories. Oversized projects, symlinks, invalid paths or changing contents can cause refusal. Stop external source writers before retrying; do not publish a mixed-version project.
## Next steps
Select the imported root, edit and save. For Gradle, configure JDK, verify the own Wrapper, list tasks and choose a build target. A generic folder without Gradle is a file project; .java files do not guarantee a main and arbitrary JAR locations do not declare dependencies.
=> gradle|Configure imported Gradle projects
=> export|Move edits back outside

@@ export|Projects|Export: sources, drafts and JARs|Transfer an editable project without mistaking its ZIP for a built mod.|export zip drafts backup jar sources
## Project ZIP
Export creates a ZIP of the selected project with supported layout, configuration and binary resources. Open drafts can overlay the exported snapshot without saving those source files to disk. Check the resulting path and unpack a copy before handing it to someone else.

Source ZIP is not a mod JAR. Build a Gradle task and select a verified artifact for binary delivery. For editable handoff, preserve configuration, Wrapper and necessary dependency information according to project licenses.
::: flow|Drafts plus project files → Export snapshot → Project ZIP → Another editor → Separate build if needed
## Different operations
Save writes the working source. Export creates a separate archive. Java run compiles a temporary snapshot. Gradle produces outputs according to configuration. Artifact delivery copies a verified output. None automatically synchronizes edits back into the pre-import source directory. Compare versions before unpacking over another person's changes.
=> artifacts|Deliver an installable JAR

@@ mod|Build|First standalone NeoForge mod|Starter, source edit, Gradle build and independent installation.|neoforge mod minecraft standalone template
## Create the starter
Choose NeoForge project creation in Project and provide a unique mod id: 2–48 lowercase letters/digits/underscores, starting with a letter. minecraft, neoforge, mared and javafml are reserved. The template includes build.gradle, settings.gradle, gradle.properties, metadata and ExampleMod.java.

Included pins are NeoForge 21.1.235, userdev plugin 7.1.38 and Java toolchain 21; these describe this template rather than claiming latest versions. The starter version is 0.4.0. Creation needs configured tools and a valid Wrapper and does not itself run a build.
## Edit the entrypoint
Open src/main/java/genesis/generated/mod_<id>/ExampleMod.java. Change the constructor log message and save. Do not replace @Mod with an ordinary main: the loader has its own entry lifecycle. That log message appears when the built mod is loaded, not during compilation.
## Build and verify
Check JDK 21 and Wrapper, discover tasks, select build and Build mod mode, then run. Resolve source and metadata failures. Select the main JAR from artifacts, obtain a verified copy and test in a separate compatible Minecraft/NeoForge client. Put it in mods and restart that client. MaRed is unnecessary unless you explicitly added a dependency.
## Larger projects
Organize packages/modules and configure resources and tests in Gradle. The small standalone runner is not a replacement for a game's full classpath. Current Scripts lacks semantic Java analysis, automatic refactoring, debugger and universal hot runtime for ordinary @Mod JARs needed for a full large-project IDE experience.
=> tasks|Modules and testing tasks
=> boundaries|Server and live-code boundaries

## Project dependency manager
Run → Project dependencies, or Settings → Tools → Project dependencies, opens the wizard. Requires Groovy build.gradle. Use fixed Maven coordinates group:artifact:version[:classifier]. Add or remove HTTPS Maven repositories in the same manager. Gradle resolves dependencies on the next build.

Libraries support compile/run, compile-only, or Jar-in-Jar embedding. Embedding supports Maven libraries without classifiers in NeoGradle projects. Build also invokes jarJar; install its output containing embedded libraries. A separate ordinary JAR may also exist in build/libs.

For mod dependencies, enter mod ID, Maven coordinates, compatible version range, required/optional and client/server side. The manager updates neoforge.mods.toml. Required mods remain separate installed JARs. Optional dependencies are compile-only; your code must handle their absence.

The libraries toggle connects local JARs to Gradle and NeoGradle runs, creates the folder and validates archives. Local JARs are not embedded. Maven dependencies use Gradle, not direct Run Java.

The manager stores genesis-dependencies.properties and genesis-dependencies.gradle and adds managed blocks to build.gradle and metadata. Export includes them. Save open build files first. Conflicts and manual changes to managed blocks are refused. Automatic mod metadata supports a single mod declaration; mod dependencies and embedding require NeoGradle.

@@ errors|Support|Troubleshooting: cause and action|Check context, entry class, tools and outcome in that order.|faq error compiler missing conflict build failed
## Run disabled
Check explorer project, a valid mode for its type, selected Java source for file mode, absence of another busy operation and writable Scripts context. Other Logic tabs show a read-only project context. World application requires a valid Java module and singleplayer server without LAN.
## Compiler unavailable
Local execution needs jdk.compiler in the client's Java environment. Start the game on a complete JDK. Configuring a separate Gradle JDK does not add a compiler to the already running runner.
## Main class or method missing
Use demo.Main, verify package/case and public static void main(String[] args). Do not enter a source path. Make sure the file is a supported source in this project rather than a resource or imported Gradle source in the wrong runner.
## Missing symbols or packages
Check source files, imports, local JARs and their dependencies. Minecraft imports need proper Gradle dependencies and mode. Renaming a file does not rename its Java class.
## NoClassDefFoundError / UnsupportedClassVersionError
A runtime dependency is missing or compiled for a newer Java. Add compatible dependencies or correct the toolchain. A JAR in an arbitrary folder is not necessarily on the classpath.
## Invalid Wrapper / JDK
Supply the full JDK directory and gradle-wrapper.jar file. Repair an imported project's own Wrapper and properties. gradlew.bat is not a Wrapper JAR. Do not include quotes in stored path values.
## BUILD FAILED / tests failing
Read the first real error and available diagnostics. Fix genuinely failing behavior instead of deleting a test. A future-Gradle deprecation warning does not prove it caused the current failure.
## Project changed during copying/exporting
Stop external content writers, save and retry. Consistency checks prevent mixed-version snapshots. If no writer exists and refusal persists, preserve the complete error and paths for investigation.
## Save conflict
Preserve the draft separately, compare disk text and explicitly choose the final version. Repeated Save clicks are not conflict resolution.
## Successful task but no JAR
test/tasks may not package a mod. Check task, output layout and module. Ordinary Java run does not export an installable mod; avoid treating an old build/libs file as the new result.
## TIMEOUT / OUTPUT_LIMIT / cancellation
Remove infinite loops or continuous println output. Use a small check or an appropriately configured Gradle application for larger execution. Cancellation is not success.
=> limits|Exact current bounds

@@ limits|Support|Current limits and missing systems|Concrete boundaries for planning work on this version.|limits 512 memory timeout debugger lsp
## Local Java
Up to 512 sources, 4 MiB per source and 16 MiB total. Up to 32 local JARs, 8 MiB each and 64 MiB total, with archive-content checks. Up to 512 resources, 8 MiB each and 32 MiB total. Paths are validated; external traversal and symlinks are refused.

Default limits: compilation 20 seconds, execution 10 seconds, output 64 KiB and JVM heap 128 MiB. Heap is not total process memory. These limits are not an OS sandbox for untrusted code.
## Gradle and artifacts
Default Gradle operation timeout is 10 minutes; captured output is bounded to 256 Ki characters, retaining a tail and truncation marker. Up to 512 tasks, one valid path of at most 192 characters without arbitrary flags. Diagnostics are bounded too.

Artifact discovery has depth 16, 10000 visited entries and 128 results; up to 256 MiB per artifact and 512 MiB total. These bounds do not promise every giant project will fit.
## UI and absent features
Up to 16 open documents and 16 cached project trees. Help queries are bounded to 160 characters; navigation history and bookmarks to 64 for this window. Russian UI loads Russian Help; other languages use English.

No breakpoint debugger, Java language server, semantic refactoring, Python/Rust/C++ runtime, universal hot NeoForge loader, multiplayer code installation or automatic live synchronization of two external folders. Scripts has its own preferences; Commands execution settings remain separate. Adding a library or choosing a label does not implement those systems.
=> boundaries|Minecraft and server boundaries

@@ boundaries|Support|Minecraft, servers and other tabs|Shared files, local processes and singleplayer runtime boundaries.|server network apply hot reload commands MR permissions
## Other Logic tabs
A shared project can be shown through a read-only file view. It reads saved content; a Scripts draft is not yet a new disk version for that view. This is shared project context, not a complete binding of arbitrary AI settings to arbitrary Java methods. MR and Minecraft command catalogs belong to Commands, not the Scripts language/API.
## Server execution
Local main runs on the client computer and receives neither operator rights nor the remote server process. Gradle builds files. A normal mod is installed on the required sides according to loader rules. A future multiplayer runtime needs permissions, transport and compatibility checks. Applying a ServerModule in singleplayer without leaving the world is available; remote-server downloads/application are not.
## Running another person's project
Reading Help and editing sources do not execute examples. Java run, Gradle build and tasks --all execute real code; Gradle configuration may act before any selected task runs. Review projects and libraries first. Execution bounds do not restrict every process permission.

For collaboration, share an export and tool versions and verify an independent build. Multiplayer editing, server application and a stable public library API are not completed by this Help stage.

@@ help-tools|Support|Help controls and shortcuts|Every action in this window and compact panel.|help ctrl f copy bookmarks dock shortcuts
## Search and filters
Ctrl+F focuses a query of up to 160 characters. Search is offline and checks the whole manual. Ctrl+A selects the query for replacement by the next input; Backspace removes that selection or the last character. Ctrl+V inserts bounded clipboard text without control characters. Enter opens the first filtered result.

Categories cycle through the groups and the unfiltered list. Bookmarks restricts the index to starred pages; All restores all pages. Clear query and filters when no result appears.
## Reading
Back/Forward navigate history; Bookmark toggles the current page. Copy page and Ctrl+C with Help focus copy the whole article. Copy code copies only a source block. Internal links open related topics without an external browser.

Wheel over the index scrolls the index, over the article scrolls the article. Home/End move to article start/end, PageUp/PageDown move a page, Up/Down move a smaller amount when search does not capture typing. Esc lets Genesis close Help.
## Floating and docked
Drag the full-window title and resizing borders. Dock right uses the same reader, preserving its current page. A narrow panel uses Contents to show the index; selecting a page returns to reading. Searching also displays the index. Display wraps do not alter copied code. Bookmarks, query and history are window-session state. Diagrams and steps explain operations; they are not execution controls.
=> start|Return to the capability map

@@ server-first|Server modules|Apply Java in a singleplayer world|Create a module, see a world change and replace it without reconnecting.|ServerModule apply world runtime hot reload
## First application
Enter singleplayer without opening LAN. In Scripts → Project, create a server Java module. Its src/WorldModule.java opens and Apply in world becomes the launch mode. The project includes the exact API JAR under libraries.

Press Run and confirm WorldModule. A separate javac compiles the snapshot; onEnable executes on the integrated server thread. The starter sets overworld noon, broadcasts and subscribes to ticks. Open drafts participate without an automatic Save.
::: flow|Java sources and libraries → Separate javac process → Immutable classes/resources → Validate current singleplayer world → Server-thread onEnable and activation
## Minimal source
```java
import com.fixmer.genesis.technology.server.ServerModule;
import com.fixmer.genesis.technology.server.ServerContext;

public final class WorldModule implements ServerModule {
    @Override public void onEnable(ServerContext context) {
        context.setDayTime("minecraft:overworld", 6000);
        context.broadcast("Applied without reconnecting!");
    }
}
```
## Change it live
Replace 6000 with 18000 and apply again: the same world changes to night. Closing Genesis does not stop an active module. If the game pauses the integrated server while a menu is open, periodic world ticks resume with the world.

Compilation or onEnable failure is reported and retains the previous working version. The class needs a public no-argument constructor and ServerModule. A plain main or @Mod entry is different. Constructors and static initializers execute during loading too; avoid world changes there.

Run provides status, Stop and previous-code restoration. Status shows generation, activity and callbacks. Stop deletes both retained snapshots; world exit also releases the runtime. A context must not be reused in another thread or world.
=> server-api|Full API and execution rules
The combined callback budget is 20 ms per tick. Remaining modules defer to the next tick with a rotating start, so onTick is not guaranteed every tick under overload. Time limits are checked after a function returns.

=> server-versions|Versions, JARs and limitations

@@ server-api|Server modules|ServerModule and ServerContext API|State, world changes, ticks and resource ownership.|API broadcast setBlock setDayTime nativeServer state onTick
## Lifecycle
onEnable receives the context. onDisable runs on replacement, Stop, callback failure and world closure. The context is revoked first: use onDisable for resource cleanup, not new world mutations. Managed subscriptions are removed automatically; external threads and native NeoForge subscriptions remain your responsibility.

The API has no MR, Commands or Minecraft-class dependency. API_VERSION is 1. Context methods belong to the server thread and current session. Worker-thread calls and expired contexts are refused. nativeServer returns MinecraftServer as Object; a Gradle module with Minecraft dependencies may cast it.
## Methods
owner is the local player's UUID; worldSession identifies the session and generation the applied version.
log writes up to 2048 characters to Scripts; broadcast sends up to 512 characters to the world.
setDayTime targets an existing dimension. setWeather affects overworld, duration 1–1200000 ticks, thunder requires rain.
setBlock takes dimension, coordinates and an existing block id such as minecraft:stone. It uses default block state and an already loaded chunk. Invalid bounds, unknown identifiers and unloaded chunks are refused; no chunk generation is performed.
state reads/writes strings; null removes a key. Up to 128 keys of 128 characters and values of 4096 characters. State survives code replacement in this session but is not saved to disk.
onTick accepts LongConsumer; afterTicks accepts delay and Runnable. Both return a closeable Subscription. Up to 256 callbacks/tasks per module; delays 1–1200000 ticks.
## Periodic work
```java
import com.fixmer.genesis.technology.server.*;
public final class WorldModule implements ServerModule {
    private ServerContext.Subscription task;
    public void onEnable(ServerContext context) {
        task = context.onTick(tick -> {
            if (tick % 200 == 0) context.log("Ten seconds of running world ticks");
        });
        context.afterTicks(40, () -> context.broadcast("Two seconds later"));
    }
    public void onDisable() {
        if (task != null) task.close();
    }
}
```
## Rights and limits
Modules execute inside the game's JVM with its process rights, not an OS sandbox. Infinite loops, System.exit, unmanaged threads and direct nativeServer changes can hang or terminate the game. There is no safe forced termination of arbitrary Java here.

Returning onEnable calls are checked against 200 ms; returning periodic callbacks against 10 ms per module per tick. Exceptions/overruns disable managed callbacks. Timing does not interrupt a stuck thread. Design explicit handoff for longer background work; a worker cannot directly use ServerContext.
=> server-versions|JARs and rollback boundaries

@@ server-versions|Server modules|Versions, Gradle JARs and limitations|Retained code snapshots, Gradle deployment and the scope of hot application.|rollback version jar neoforge runtime API
## Replacement
Each project retains its active bundle and one previous successful bundle. Each activation uses a new classloader/instance. Old managed callbacks stop, onDisable runs and its loader closes. Older snapshots are deleted.

Restore previous code uses a fresh instance with current string state, not historical world/state rollback, and runs onEnable again. Failure retains the current version. Session state ends on world exit.

Managed onEnable changes are accumulated before successful initialization/session checks. Flushing to the world is not a transaction: already performed changes cannot be undone if a later write fails. nativeServer mutations bypass staging entirely.
## Gradle
Run → Export API JAR to libraries writes the exact contract and copies its path; different existing content is preserved. Add compileOnly files('libraries/genesis-server-api-v1.jar') to dependencies. Minecraft APIs still require the project's appropriate Gradle dependencies.

Create a public ServerModule under src/main/java, save and build. Imported Gradle projects choose a main JAR from their last successful build, then the qualified entry class. Artifact size/hash are checked and a separate runtime snapshot is loaded. Save/rebuild dirty sources first. If disk sources changed after build, rebuild again: application uses the selected JAR, not those newer sources.

The runtime does not run @Mod registration, mixins/coremods, new static registries or normal mod installation. Ordinary mods still use normal installation/restart. Dependencies unavailable in the host need compatible JARs under libraries or suitable self-contained packaging; automatic Maven runtime resolution is absent. Protected host/API packages use the parent loader.
## Bounds and networking
Up to 16 project modules in one world. Source preparation inherits Java snapshot bounds; compilation has 20 seconds and 64 KiB output. A ready JAR is at most 64 MiB with archive-entry checks. Snapshots are temporary and source files are not rescanned every tick.

Multiplayer/LAN, remote code downloads, network permissions and client synchronization remain disabled. Host/API/permission seams are separate for a future transport; no network installation handler exists now. Merely joining another server does not download or execute these modules.

## Build integrity update 8.12.2
Gradle outputs are captured per invocation, including cached archives and custom destinations. The report also snapshots runtime libraries for server JAR application. A help/test task does not automatically certify every old archive in build/libs. Delivery checks input identity; rebuild after changing sources or configuration. Managed dependency updates have a recovery journal and a project lock.

Embed accepts a compatible range separately from the preferred Maven version. Library and local JAR modes are not automatic release embedding. Optional mod integration can be tested with genesis.optionalMods=true in gradle.properties; test with the property absent too. The manager supports literal Groovy NeoGradle declarations and a single literal mod ID; other Gradle projects may be built directly but require their own dependency DSL.

Server bundles load verified nested libraries, use a scoped context classloader and prevalidate staged actions. Supported reversible actions are compensated; failures involving irreversible actions explicitly report PARTIAL_APPLY. The constructor is included in the elapsed activation check. Elapsed-time checks do not forcibly interrupt an infinite Java callback. Runtime Java is trusted code, and nativeServer bypasses staging. Dedicated/LAN live deployment and arbitrary @Mod activation remain separate capabilities; install an ordinary compatible JAR and restart the server. Fixed input/archive limits are reported rather than promising unlimited complexity.
