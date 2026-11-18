@@ start|Start|Scripts workbench 0.5.0|Current project, server and deployment workflows.|0.5.0 start server apply help
Scripts uses the standard project editor. Genesis selects directions; the sidebar selects sections. Servers has a separate folder context and real configuration pages. Applying a project prepares a release automatically. Commands belongs to Maps.

=> creation145|Directions
=> server156|Server folder
=> configs156|Configuration editing
=> apply156|Apply and launch

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
Open Main.java in the active pane. Open the arrow next to Run and choose Run Java file; choosing the mode does not execute anything. Press Run, confirm Main as the entry class, and wait. Ctrl+R follows the selected mode. All project sources are compiled, so an error in another class can also block execution.
## 4. Check output
Expand the bottom logs using the arrow at their boundary. Expect Hello, Genesis and a successful result. Resize the logs with the divider. Change the message and run again: open drafts participate in local Java execution. Save / Save all is still required to keep changes on disk.

Compiler unavailable means the client needs a complete JDK. Main class errors require checking the class name and package. Compile errors require fixing the source. Do not switch to Build mod for this small main example.
=> java|Entry classes and Java lifecycle
=> errors|Troubleshooting

@@ projects|Projects|Ordinary folders and explorer|Opening a folder links it in place.|133 projects
Open folder uses the actual directory, without copying it into an archive or another workspace. File operations affect that directory. The explorer shows folder/file icons, indentation, selection and path tooltips. F2 renames, Delete moves entries to project trash, Ctrl+C / Ctrl+V copy within the project. Save dirty files before file operations. In the folder picker single-click selects, double-click or Enter opens, Ctrl+Enter confirms, Ctrl+L enters a path, Backspace goes up. Back/forward and new-folder controls are available. At a filesystem root, Up lists drives. ZIP transfer is an explicit separate action.
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

@@ launch|Build|Launch and apply actions|Available actions follow the current context.|0.5.0 run apply server java
For a Gradle mod with a selected server, Apply to selected server builds and prepares the release. Launch in the deployment page offers installation and continues after consent. Plain Java projects keep Run Java file. Ctrl+B builds; Ctrl+R invokes the currently labelled primary action. Inapplicable primary actions are hidden; advanced tools remain separate.

=> apply156|Deployment workflow
=> remote_test152|Server JAR only

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
Tools → Export current project files exports acknowledged shared files, including models, to config/mared/workspace-exports147 without building a mod. New shared projects retain their original product direction.
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

@@ import|Projects|Ordinary folders and explorer|Opening a folder links it in place.|133 import
Open folder uses the actual directory, without copying it into an archive or another workspace. File operations affect that directory. The explorer shows folder/file icons, indentation, selection and path tooltips. F2 renames, Delete moves entries to project trash, Ctrl+C / Ctrl+V copy within the project. Save dirty files before file operations. In the folder picker single-click selects, double-click or Enter opens, Ctrl+Enter confirms, Ctrl+L enters a path, Backspace goes up. Back/forward and new-folder controls are available. At a filesystem root, Up lists drives. ZIP transfer is an explicit separate action.
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

New mods use the official NeoForge 1.21.1 ModDevGradle MDK snapshot 3bca8f1e0756c001e4b71f266c328d1568d29363, plugin 2.0.148, Gradle 9.2.1 and Java 21. NeoForge is pinned to the MaRed baseline 21.1.264. The initial mod version is 1.0.0. The original wrapper, examples, Config, client events, resources, datagen and publishing are included. Creation needs no external wrapper and executes no build. Building needs a full JDK 21 and network access for first-time dependency downloads.
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

Libraries support compile/run, compile-only, or Jar-in-Jar embedding. Embedding supports Maven libraries without classifiers in NeoGradle and ModDevGradle projects. NeoGradle build invokes jarJar; install that output containing embedded libraries. ModDevGradle embeds dependencies in the main JAR.

For mod dependencies, enter mod ID, Maven coordinates, compatible version range, required/optional and client/server side. The manager updates neoforge.mods.toml. Required mods remain separate installed JARs. Optional dependencies are compile-only; your code must handle their absence.

The libraries toggle connects local JARs to Gradle and NeoGradle/ModDevGradle runs, creates the folder and validates archives. Local JARs are not embedded. Maven dependencies use Gradle, not direct Run Java.

The manager stores genesis-dependencies.properties and genesis-dependencies.gradle and adds managed blocks to build.gradle and metadata. Export includes them. Save open build files first. Conflicts and manual changes to managed blocks are refused. Automatic mod metadata supports a single mod declaration; mod dependencies and embedding support NeoGradle and ModDevGradle.

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

@@ boundaries|Help|Scripts, Commands and services|Shared editor, distinct service permissions.|133 boundaries
Commands uses the same project/editor component as Scripts, including folders, tabs, syntax colors, recovery, search/replace, snippets and split view. Create a map project or open a real folder. The default file is main.mcfunction. Ctrl+R executes the active document through the existing MaRed executor; Ctrl+Shift+R stops. F7 validates file structure and MR syntax without executing. Results are in Logs. Server permissions apply. Commands does not expose JDK, Gradle, NeoForge build or mod installation. Running a file does not install or export a datapack. View → Command reference opens the live MC command tree and documented MR commands.

Server opens server folder and process controls. Supervisor opens activation, managed restart and restart history. WorkSpace opens collaboration. These screens contain no cross-navigation to adjacent global sections. Back returns to the workbench, where another white global control can be chosen. Server management rights and shared-project roles are separate.
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

@@ server-first|Server modules|Apply Java in a singleplayer world|Create a module, see a world change and replace it with a managed client restart in development mode.|ServerModule apply world runtime hot reload
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
        context.broadcast("Applied with a managed client restart in development mode!");
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

Embed accepts a compatible range separately from the preferred Maven version. Library and local JAR modes are not automatic release embedding. Optional mod integration can be tested with genesis.optionalMods=true in gradle.properties; test with the property absent too. The manager supports explicit Groovy NeoGradle/ModDevGradle declarations and a single mod ID (including ${mod_id} from the official MDK gradle.properties); other Gradle projects may be built directly but require their own dependency DSL.

Server bundles load verified nested libraries, use a scoped context classloader and prevalidate staged actions. Supported reversible actions are compensated; failures involving irreversible actions explicitly report PARTIAL_APPLY. The constructor is included in the elapsed activation check. Elapsed-time checks do not forcibly interrupt an infinite Java callback. Runtime Java is trusted code, and nativeServer bypasses staging. Dedicated/LAN live deployment and arbitrary @Mod activation remain separate capabilities; install an ordinary compatible JAR and restart the server. Fixed input/archive limits are reported rather than promising unlimited complexity.


## Project creation and code editing

New project offers a NeoForge mod, a server module, or an empty project. The NeoForge starter includes source, metadata and Gradle Wrapper 9.2.1. Its first-build action appears after the entry source opens; Ctrl+B also builds. The first build can download dependencies.

The tree context menu provides New Java type: class, interface, enum or record. Enter a path relative to the selected Java package, such as helpers/Example; the package follows the path.

In source text: Tab indents, Shift+Tab outdents, Ctrl+D duplicates a line/block, Alt+Up/Down moves it, Ctrl+/ toggles line comments for supported file extensions. Each operation uses one undo step. JSON commenting is disabled. F8/Shift+F8 navigate located errors and warnings from the last build.


## Source syntax colours

Syntax colours are enabled automatically by extension: Java, Python, Rust, C/C++, JavaScript, Groovy/Gradle, Kotlin, JSON/JSONC, TOML and properties. Dark and light themes use separate palettes. Keywords are blue, control flow purple, strings orange, comments green, numbers pale green, types teal, and calls/annotations yellow, with adjusted shades for light backgrounds.

Multiline strings/comments, escapes, tab switching, theme changes, restored documents and undo/redo are supported. This is lexical highlighting: type/call colours use local heuristics without project dependency analysis. String interpolation and regex literals are not parsed separately. Unknown extensions remain plain.

@@ split|Editor|Two files side by side|Independent editor groups and focus.|split windows panes focus
Ctrl+\ toggles two panes, using the existing global split-editor setting. Right-click a file → Open on the right, or Alt+click a tab. Ctrl+1 and Ctrl+2 focus the left/right group; clicking its text also changes focus. Typing, saving and searching use the focused pane. Ctrl+Alt+S saves all open documents. Drag the divider to resize.
Each pane retains its document history, selection, scrolling and syntax colors. Selecting an already visible file focuses its existing pane, so two widgets never share its mutable view. Below 240 GUI pixels the editor falls back to one pane, retaining the split preference. These are panes inside Minecraft, not separate desktop windows.


@@ assist|Editor|Code tools 33|Find/replace, snippet fields and editing commands.|replace snippets brackets block lines whitespace
Ctrl+Shift+J opens code tools for the focused pane; Search and the file menu also provide access. Ctrl+H configures literal replacement: match case, whole identifier words and selection only. Enter find/replacement text; an empty replacement deletes matches and surrounding spaces are preserved. Preview shows the count and first 30 original excerpts. Continue chooses the next match from the cursor (wrapping to the start) or all matches. Esc cancels. Replace all is one undo step. A changed/switched document rejects stale previews. There are no regular expressions or project-wide replacements.
Ctrl+J selects Java/Python/JavaScript snippets. Edit the selected field and use Tab/Shift+Tab to visit fields. The final Tab moves to the ending cursor; Esc ends field mode. Selection can be wrapped by a template. Fields are independent, without semantic renaming or import insertion. Editing outside the active field cancels field navigation at the next Tab. Snippet insertion is one undo step; later typing uses ordinary history.
Ctrl+M jumps to a matching ()[]{} bracket at/just before the cursor. Ctrl+Shift+M selects its enclosing contents and expands to an outer pair on repetition. Strings/comments follow the colour lexer, not a compiler. Ctrl+Shift+K deletes the current/selected lines, excluding a final row selected only through column zero. Trim trailing whitespace removes ending spaces/tabs throughout the focused file, preserving indentation, line breaks and the final newline. Text edits are undoable.
Find/replace and snippet input: 1 MiB; at most 5000 replacement matches and 2 MiB output. Brackets: 20000 rows, 32768 characters per row, 100000 brackets, with a token overflow guard. Ordinary editing remains available beyond these feature limits.

@@ interface|Getting started|Interface actions|Folder selection, run targets and dialog actions.|interface folder path run keyboard cancel
## Select a folder
Click a folder to open it. Select this folder confirms the currently open folder, not the highlighted row. ↑/↓ selects a row; Enter opens it; Ctrl+Enter confirms the open folder. Backspace opens the parent. In Drives view, open a drive before confirming.
Ctrl+L opens full-path entry. Windows paths with spaces and quotes from Copy as path are accepted. Ctrl+C copies the current path. After an access error, open the parent or enter another path.
## Run target
Run Gradle task executes the selected task, which need not be build. Run Java file uses the active document and runs main in a separate JVM; compilation includes Java sources from the entire project. Switching tabs changes the Java run file.
Build mod and restart and Apply module and restart explicitly restart the client. Applying requires a single-player world without LAN. Building alone does not install the mod.
## Dialogs
New files use Create, renaming uses Rename, settings use Save. Find and replacement prompts use Next. An empty replacement deletes matching text.
The underlined confirmation button is activated by Enter. Tab or ←/→ changes it; Esc cancels. Destructive dialogs initially select Cancel. Scroll long messages with the wheel or ↑/↓. Input errors remain visible; Ctrl+Shift+C copies their complete text.
## Help and unfinished sections
Open window displays help inside Genesis; Dock right returns it to the side panel. This does not create a native OS window. The introduction distinguishes available tools from categories whose editors are not connected. Its content scrolls in small windows while navigation buttons remain visible.
=> launch|Run modes
=> projects|Projects and working folders

@@ workspace133|Collaboration|Shared WorkSpace|The normal Scripts / Commands editor with collaboration enabled.|133 workspace133
Use the white global control to open six management actions: project, members and rights, history, personal settings, remove sharing, advanced. Choose an existing Scripts or Commands project. Joining opens the normal corresponding workbench, its explorer and documents; no second local Scripts project is created. Removing sharing keeps the owner folder.

@@ invites133|Collaboration|Invitations and access requests|Explicit consent before file access.|133 invites133
Plus invites a player; minus removes a member; the adjacent control edits permissions. Notifications appear at the top right. F9 opens the inbox. The recipient sees the actual role and restrictions before accepting, then downloads permitted files and enters the normal editor. The owner receives a join notice. Accepting an access request creates an invitation, which still needs recipient consent. Requests and invitations expire after 15 minutes.

@@ rights133|Collaboration|Roles and restrictions|The server checks every protected operation.|133 rights133
The owner manages membership. Other roles are Maintainer, Developer, Reviewer and Viewer. Permission checkboxes restrict reading, editing, code erasure, file selection, file operations, history, restore, build and publication. Panels can be hidden globally or under a path. Checkboxes cannot exceed the role. Revoked write rights block server edits and preserve local drafts. Project access does not grant Minecraft OP rights.

@@ history133|Collaboration|History and line diffs|Filter by file, author and action.|133 history133
Select a history entry to see numbered added lines in green with + and removed lines in red with −. There are older pages, changed-lines-only mode and horizontal scrolling. Binary files have no text diff. Restore needs permission and confirmation; an intervening revision requires inspecting the change again.

@@ presence133|Collaboration|Cursors and activity|Peers appear in the ordinary explorer and editor.|133 presence133
File activity lists participants. Open files show peer cursors and selections. Personal settings provide a saturation/value field, hue slider, RGB and HEX input, colored presets and preview. Save applies the preference; cancel discards it. Disconnection blocks edits and preserves queued operations and drafts for recovery.

@@ folders133|Projects|Ordinary folders and explorer|Opening a folder links it in place.|133 folders133
Open folder uses the actual directory, without copying it into an archive or another workspace. File operations affect that directory. The explorer shows folder/file icons, indentation, selection and path tooltips. F2 renames, Delete moves entries to project trash, Ctrl+C / Ctrl+V copy within the project. Save dirty files before file operations. In the folder picker single-click selects, double-click or Enter opens, Ctrl+Enter confirms, Ctrl+L enters a path, Backspace goes up. Back/forward and new-folder controls are available. At a filesystem root, Up lists drives. ZIP transfer is an explicit separate action.

@@ keys133|Editor|Scripts and Commands shortcuts|Shared editor, shared everyday actions.|133 keys133
Ctrl+N new file; Ctrl+S save; Ctrl+Alt+S save all modified open files. Ctrl+F text search; Ctrl+Shift+F project search; Ctrl+P file search; Ctrl+O open-document search; Ctrl+G go to line. Ctrl+H replace; Ctrl+J snippets; Ctrl+Shift+J code tools. Tab / Shift+Tab navigate snippet fields. Ctrl+/ comments; Ctrl+Shift+K removes lines; Ctrl+M matching bracket; Ctrl+Shift+M block selection. Ctrl+Z / Ctrl+Y undo/redo. Ctrl+\ splits; Ctrl+1 / Ctrl+2 focus panes. Ctrl+R runs; Ctrl+Shift+R stops. Commands F7 checks structure and MR syntax without execution. Scripts F8 navigates build diagnostics. F6 cycles pane focus; F9 opens invitations. Focused search and help fields own their input.

@@ commands133|Commands|Map projects in Commands|The same editor without mod build tools.|133 commands133
Commands uses the same project/editor component as Scripts, including folders, tabs, syntax colors, recovery, search/replace, snippets and split view. Create a map project or open a real folder. The default file is main.mcfunction. Ctrl+R executes the active document through the existing MaRed executor; Ctrl+Shift+R stops. F7 validates file structure and MR syntax without executing. Results are in Logs. Server permissions apply. Commands does not expose JDK, Gradle, NeoForge build or mod installation. Running a file does not install or export a datapack. View → Command reference opens the live MC command tree and documented MR commands.

@@ command-format133|Commands|Minecraft lines and MR blocks|Only a leading { starts MR.|133 command-format133
Outside MR blocks each significant line is a Minecraft command; an optional leading slash is stripped. SNBT braces inside a Minecraft command do not start MR. A leading { starts an MR block. All MR syntax is parsed before execution. F7 checks structure and MR; Minecraft command validity and permissions are checked by the game server. # and // are comments outside blocks. #persistent on the first line enables persistent resources. Stop releases resources; switching connections prevents sending old commands into another world. Use Command reference → MR for control flow, functions, events, timers, actions and their parameters.

@@ migration133|Commands|Existing Commands and recovery|Legacy saved sources remain intact.|133 migration133
On first opening Commands, saved config/mared/commands files are copied once into a LegacyCommands project under config/mared/command_projects. Folder structure is retained and .mr is appended to filenames. A free project name is selected on collision. Originals are neither modified nor deleted. This is a one-time migration, not ongoing two-way synchronization; existing command bindings can keep using originals. Unsaved old-editor drafts remain available through View → Legacy Commands and drafts. Use View → Return to Commands projects to return to the new editor.


Migration shortens overlong names/paths and resolves physical filename/folder collisions without replacing content. The original-to-new mapping is retained in .genesis/migration-paths135.properties in the new project. A retry recovers an already committed migration. Commands files accept a UTF-8 BOM and LF, CRLF or CR line endings.

@@ services133|Services|Separate server and supervisor screens|Each global control has its own destination.|133 services133
Server opens server folder and process controls. Supervisor opens activation, managed restart and restart history. WorkSpace opens collaboration. These screens contain no cross-navigation to adjacent global sections. Back returns to the workbench, where another white global control can be chosen. Server management rights and shared-project roles are separate.

@@ sync-errors133|Help|Actual synchronization errors|Readable details, copy and an explicit log path.|133 sync-errors133
WorkSpace has an Error details control with exception text, stack, Copy and Open log folder. The dedicated client file is logs/mared-workspace.log. Empty project title and default Viewer text can indicate failure before joining. Errors are displayed at that stage as well. Allowed dotfiles include .gitignore, .gitattributes, .editorconfig, .gitkeep and .github. Spaces and Unicode project paths are accepted within safe path rules. Traversal and arbitrary hidden directories remain blocked. The shared text limit is 16384 Unicode code points, supporting standard gradlew scripts longer than the former 8192 limit. Creation snapshots still allow at most 64 text files and 20000 compressed bytes. Conflicting local drafts are preserved. Owner files update while its connected client services the session; offline changes remain on the server.


@@ reliability142|Help|Run failures and persisted logs|assert, wait, scope and shared-project limitations.|142 reliability142
A failed assert marks the run as failed and cancels timers, listeners and actions owned by that run. Previously scheduled work owned by the failed run must not continue.

wait accepts finite nonnegative durations. Units: ticks/t, ms, seconds/s, minutes/m. Unknown units, NaN, Infinity and duration overflow are errors. Large repeat counts must not wrap into small counts.

scope=self or scope=all is a separate final token outside quotes. Example: print "Hello" scope=self. Inside a quoted string, "hello scope=all" remains ordinary message text.

The in-memory journal retains up to 2000 entries. The persisted log keeps the newest entries within 8 MiB; omitted older entries are reported by a warning at the beginning of the saved log. Long messages are limited to 4096 characters with a truncation mark. Available entries can be exported separately.

Shared projects currently synchronize text files. Binary PNG/JAR/audio resources and empty folders are not transferred. Shared folder creation and tree copying are not implemented; inactive actions are disabled. These limitations are distinct from a complete project synchronization.

@@ runtime143|Help|Function calls and Commands ordering|Parameters, map/filter/reduce, cancellation and mixed files.|143 runtime143
map, filter, forEach and reduce inherit the caller's variables and user functions. Callback bindings stay private, so a nested map does not replace its outer $v or $i. Lists and objects remain shared references; mutating the object itself inside a function can still be visible to the caller.

reduce requires two distinct local names without $: [1,2,3].reduce("acc","item","$acc + $item"). global.*, self and world are rejected. Function parameters are restored even if executor creation fails, including restoring an absent parameter to absence.

A mixed Commands file waits for the preceding foreground MR block, including wait, before advancing. Later MR blocks in the same run retain variables and function definitions. Background every/on handlers do not block advancement. In singleplayer, ordinary commands execute on the server thread before the next MR block. In multiplayer, sending a command does not acknowledge completion on the remote server.

Multiplayer MR receives the local player data available to the client. This does not grant additional server permissions. The scenario still requires acceptance testing with a matching game client and server.

Each complete map/filter/forEach/reduce operation has a cooperative 100 ms budget checked between items together with cancellation. A blocking external call cannot be preempted by this check. Explicit run cancellation by itself is not an execution failure.

Shared Workspaces can start from empty Scripts or Commands projects and accept files afterwards. The binary-resource and empty-folder transfer limitations described above still apply.

@@ creation145|Directions|Workbench 0.5.0|Genesis directions and their sections.|0.5.0 156 server apply configuration
Genesis selects Mods, Datapacks, Maps, Servers, Shaders, Modpacks or Resourcepacks. The sidebar switches sections of that direction. Semantic icons replace the repeated short list. Servers now has only management, mod configurations and server settings, all bound to the selected installed server folder. Scripts, extensions and a separate Supervisor tab are removed from Servers.

@@ blockbench146|Projects|Blockbench Desktop and shared Workspace|Installed application without embedded Chromium.|blockbench models animations textures bbmodel workspace desktop
# Connect desktop Blockbench
Models and Animations open the connector for your installed Blockbench application. MaRed no longer bundles MCEF or downloads Chromium at Minecraft startup. Remove a separately installed MCEF JAR from client mods if no other mod needs it. Blockbench must be installed and runs in a separate window.
Choose a relative .bbmodel path in the current project. Existing models have a fixed path. Launch detects the application or uses a complete executable path, without additional arguments.
Copy the connector file path. In Blockbench use Plugins → Load Plugin from File and select mared_desktop148.js. Then Tools → Connect to MaRed: paste the connection code copied from MaRed. New Blockbench versions may ask for network permission. This connector uses only 127.0.0.1. The code expires when the connector screen closes; do not share it.
# Shared models
Edit operations, Undo and Redo send .bbmodel snapshots after a 250 ms pause, with embedded images. The server verifies permissions and the previous file version. Conflicts keep local work rather than overwriting it. The model limit is 4 MiB. Keep the MaRed connector screen open until synchronization finishes.
Remote versions are checked every 500 ms; actual latency depends on server, network, size and the transport queue. Incoming models do not replace local edits. Save a local copy through Blockbench and reconnect after a conflict. Selecting another Blockbench project does not rebind the MaRed file.
Individual vertex operations are not merged; concurrent edits to the same model can conflict. Blockbench 3D mouse pointers are not synchronized. Imports, exports and plugins run in the installed application. Separately exported files are not automatically imported into Workspace.
# Code and pointers
Text carets and selections remain available. Mouse pointers over file text in the normal MaRed editor are also shared. Document positions account for the receiver's wrapping and scrolling. Pointers are not shared over menus, the file tree or Blockbench's 3D view. Stale pointers expire and file permissions are enforced.
Draft persistence and copying acknowledged code to disk run without disabling typing. Unchanged documents use compact replies; changed documents and recovery still use snapshots. Update every participant and the server for the new protocol.
# Navigation
Choose global directions in Genesis. The left rail contains sections of the current direction, Workspace, help and settings. Wide screens show section labels; narrow screens show numbers with tooltips. The header shows the current direction and section.

# Transfer improvements and mouse pointers
Patch 149 removes the old browser screen, adapter, browser script and MCEF dependency declaration. A separate MCEF JAR still starts independently if you or another mod keep it installed.
Participants see a white mouse arrow with their personal colour outline and name over file text. Coordinates use a separate transient channel, up to 20 updates per second, with stale samples replaced by newer ones. Stationary pointers send fewer updates. Text carets remain available. Menus and Blockbench's 3D viewport do not share mouse pointers.
Models use optional lossless compression negotiated with the server. Original bytes, hash and permissions are checked before storage. Already compressed textures can still take longer than simple geometry. Large JSON parsing, encoding and compression run in the background. Large code pastes use batches of up to 256 characters.
Status shows measured edit acknowledgment time, from transmission to server reply. This is neither a pure network ping nor delivery time to every participant. It excludes local draft persistence and time waiting to send. No constant latency is guaranteed across all networks and workloads.
Reload mared_desktop148.js in Blockbench after updating. It supports compact save acknowledgments, permission changes and safe reconnect/timeout handling. Workspace journal format remains compatible.

# Reconnects and lost replies
Patch 150 assigns each desktop connection a generation and a fresh session identifier. Deferred messages from an old connection cannot be delivered to the new app. Reconnect refreshes the source model version while keeping local app edits protected. Reload mared_desktop148.js after updating.
Lost model chunk replies are retried a bounded number of times. A short-lived commit receipt allows retrying the same completed save without another journal revision. Receipts are bound to player, project and path; current permissions are checked again. A server restart or expired receipt can leave the outcome unknown; keep a local copy and reconnect.
Code edit acknowledgments are accepted only if exact inserted characters and deletion tombstones are present in the confirmed document. Unacknowledged work remains recoverable.

## Text caret and direct navigation — update 151
Text carets and selections use short independent messages instead of waiting behind document edits, models or file transfers. Changed positions are sampled at up to 20Hz, stationary positions use a 500ms heartbeat, and older samples are rejected. This is a send cadence, not a network or server latency guarantee.
Remote carets remain visible during local typing. Positions are projected through unprepared local text; a position inside a concurrently replaced fragment is temporarily approximate. Mouse pointers use a separate channel.
Server controls, server tools and Supervisor render directly inside the workbench. Specific settings forms return to the workbench. The general Tools section shows its scrollable actions immediately instead of a button opening a second catalog. Hidden code editor controls do not receive server panel input.

@@ environment152|Settings|Custom colors, sidebar and participants|Rail width, direction palettes and file presence.|152 colors rail icons workspace cursor
## Custom colors, sidebar and participants — 152
Drag the left rail boundary to resize it independently of the project tree. Width is persisted and constrained to available window space. Section icons are restored.
Settings → Custom colors provides a separate accent for every direction, plus optional background, panel, raised surface, text, muted text and border colors. The HSV/RGB/HEX picker edits a settings draft; the main Settings Save commits it. Reset restores theme surfaces and default direction accents.
Workspace cursor color is stored by the server for your account and propagated to the active editor. Existing local colors migrate when no server preference exists. A single dot before a file name cycles participants' colors every 1.5 seconds; hover to see names and tools.
@@ remote_test152|Build|Server JAR only|An explicit narrower route.|0.5.0 156 server apply configuration
Apply server JAR only is shown for a supported connected server and permissions. It builds/uploads one server JAR and requests restart; it does not distribute a client mod set. Full automatic publication applies to a locally selected managed server folder. Device authentication protects offline server administration, but does not migrate the legacy complete remote offline client installer.

@@ devices153|Server|Offline server administration|Device trust without mandatory online-mode.|153 offline online-mode device Maop trust key
## Device verification
The management panel and remote JAR test no longer require online-mode=true. In offline mode, MaRed creates a local key for this server address and player name. Different addresses/accounts use separate keys. The private key never enters network fields.
The Server section shows state, role and fingerprint. Use Copy device trust command, verify the fingerprint, and run maop trust PLAYER FINGERPRINT in the real console of that server. Trust does not grant a role: grant the appropriate role separately, for example maop PLAYER maintainer. Revoke with maop untrust PLAYER FINGERPRINT. Vanilla OP, RCON and execute-as cannot grant device trust.
Update clients, server MaRed and the running controller to 153. Offline administration stays disabled with an old controller. A different address or player name may require another approval. Keys are stored in config/mared/devices153 and must not be shared.
## Signed operations
Offline panel operations and JAR application requests bind signatures to the connection, exact fields, request ID and counter. A duplicate obtains the original result; an evicted result is not blindly executed again. Applying also binds the chosen artifact hash, confirmed target and generation. The controller rechecks current device trust and role.
Use panel controls. Legacy unsigned player chat administration is not the offline route; the real console remains available. The older client reinstall/restart publication pipeline retains its ONLINE/LOCAL_OFFLINE modes and is not migrated to the new device scheme yet.
Signatures protect MaRed actions; they do not encrypt all offline Minecraft traffic or replace player identity in every other Minecraft subsystem.

@@ remote154|Build|Contextual launch actions|Show applicable actions.|0.5.0 156 server apply configuration
The launch menu exposes applicable Gradle build, selected-server deployment, connected server JAR, Java file and single-player actions. Additional actions retain advanced tools. A plain Java file is not a NeoForge mod. Shared builds still require server-approved source state and participant permissions.

@@ display155|Servers|Embedded button labels|Correct clip translation in embedded screens.|155 buttons server Supervisor clipping
Embedded server, Supervisor and server-tool screens use a translated pose. In 155, text scissors and the scissor hit-test receive the same offset, including scroll. Standalone screens retain unchanged coordinates. The deliberately empty idle status translation remains; server button labels are not empty.

@@ first155|Build|Automatic initial release|No manual release names or staging folders.|0.5.0 156 server apply configuration
Apply to selected server creates a random release name and imports complete current client/server mod sets once. Successors use the verified signed baseline and replace the project JAR by modId. Preview and signing are performed by the owner-local pipeline. Configure reachable game/distribution endpoints in Servers if needed. Leave both PEM fields blank to generate a publisher-rooted certificate and key automatically. Public domain names and routing are not generated.

@@ server156|Servers|Selected server folder|A server context independent of Scripts projects.|0.5.0 156 server apply configuration
Choose an installed folder with server.properties. The remembered folder scopes management, mod configurations and server settings. Management has start/stop, deployment status and one-time connection setup. Supervision remains automatic inside the existing controller, which does not adopt unrelated process IDs. Settings do not imply universal hot reload. Restart management after changing port, authentication mode or bind address.

@@ configs156|Servers|Configurations and settings|Real files, search, parameter edits and backups.|0.5.0 156 server apply configuration
Mod configurations scan config, defaultconfigs and the selected world serverconfig. Groups use folder/filename prefixes rather than claiming perfect modId mapping. Voicechat remains here and is not duplicated as a Minecraft setting. Server settings discover server.properties and access lists. Simple properties/INI/CFG/TOML and JSON scalars use parameter lists; booleans toggle on click. Complex YAML, JSON arrays and multiline TOML use the raw file editor. Unsaved edits persist as file-bound drafts when leaving the screen. Saves compare the original hash and create a backup; symlinks and scope escapes are refused. Full third-party configuration schema validation is not claimed.

@@ apply156|Build|Apply and launch|Automatic publication for a locally managed server.|0.5.0 156 server apply configuration
Select the managed server folder and configure reachable endpoint addresses if needed. Leave both PEM fields blank for automatic certificate generation. Preparation starts the managed server automatically if it is stopped. Save the project and Apply to selected server: Gradle build, verified snapshot, automatic release label, complete initial side sets and signed successor are prepared. If multiple mod artifacts exist, choose the correct JAR. The remembered project side can be client, server or both. Launch connects the owner to the ready server if necessary and authorizes the existing participant offer and commits only after accepting participants prepare. Then the existing controller handles save/stop/install/restart/readiness. Uncertain commits are not blindly replayed. Job receipts live under automatic156. This full client/server automation is owner-local; remote-only JAR application and legacy offline client rollout retain their documented limits.

For gradlew runClient/IDE, the client input uses the packaged MaRed JAR and standalone JARs reported by the NeoForge loaded-mod list. An empty run/mods is allowed. Build MaRed itself with gradlew jar first. Other development mods backed only by class directories or virtual nested paths require standalone packaging. Temporary input does not install files into the active run/mods. Authoring from IDE does not add automatic installation or restart of the IDE/Gradle session; use a normal managed client to validate client rollout.

HTTPS 159: MaRed generates a private publisher-rooted certificate and server key outside published files. Connection-local trust retains hostname, validity and signed-package checks; it never changes JVM/system trust defaults. First contact is through the selected game connection and the existing publisher approval; approved pins persist across restarts. All participants need this updated MaRed. Porthole port forwarding also needs the HTTPS TCP port (8443 by default). Public routing and full remote-host rollout are not added by this fix.

@@ changes156|Version|Changes in 0.5.0|Server context, automatic publication and semantic icons.|0.5.0 156 server apply configuration
MaRed is now 0.5.0. Minecraft, NeoForge and core compatibility versions are unchanged. Server pages edit real scoped files, per-release staging and labels are automatic, icons distinguish sections, and Scripts/Commands help includes the current server workflow. Passing a build does not prove arbitrary mod compatibility or native lifecycle acceptance.

