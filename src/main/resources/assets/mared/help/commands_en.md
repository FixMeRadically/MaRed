@@ start|Start|Commands workbench 0.5.0|Current project, server and deployment workflows.|0.5.0 start server apply help
Commands uses the standard project editor. Genesis selects directions; the sidebar selects sections. Servers has a separate folder context and real configuration pages. Applying a project prepares a release automatically. Commands belongs to Maps.

=> creation145|Directions
=> server156|Server folder
=> configs156|Configuration editing
=> apply156|Apply and launch

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

@@ drafts|Editor|Save, drafts and recovery|The difference between in-memory text, disk files and exported snapshots.|save drafts recovery conflict restore
::: flow|Edit document → In-memory draft → Save / Save all → Check external changes → Write source file
## Memory versus disk
Save writes the active document; Save all writes modified documents. Switching projects does not secretly save. Local Java runs use snapshots of open drafts. Gradle reads disk files, so the editor prepares and saves required changes before building. A save failure or conflict prevents that preparation from being reported as a successful new build.
## External edits
When disk content changed after opening, the saved baseline is checked. Preserve a copy of your draft, compare it with the external version, then decide explicitly which text should remain. Refreshing the tree is not a forced overwrite command. Closing a dirty document requires a save/discard decision; selecting another file does not discard it.
## Recovery
Recovery data and workspace context can help reopen work, but do not replace backups. A hard process termination may interrupt their write. Review recovered content and its baseline before saving. Up to 16 documents are open at once; close unused tabs and keep important work on disk.

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

@@ help-tools|Support|Help controls and shortcuts|Every action in this window and compact panel.|help ctrl f copy bookmarks dock shortcuts
## Search and filters
Ctrl+F focuses a query of up to 160 characters. Search is offline and checks the whole manual. Ctrl+A selects the query for replacement by the next input; Backspace removes that selection or the last character. Ctrl+V inserts bounded clipboard text without control characters. Enter opens the first filtered result.

Categories cycle through the groups and the unfiltered list. Bookmarks restricts the index to starred pages; All restores all pages. Clear query and filters when no result appears.
## Reading
Back/Forward navigate history; Bookmark toggles the current page. Copy page and Ctrl+C with Help focus copy the whole article. Copy code copies only a source block. Internal links open related topics without an external browser.

Wheel over the index scrolls the index, over the article scrolls the article. Home/End move to article start/end, PageUp/PageDown move a page, Up/Down move a smaller amount when search does not capture typing. Esc lets Genesis close Help.
## Floating and docked
Drag the full-window title and resizing borders. Dock right uses the same reader, preserving its current page. A narrow panel uses Contents to show the index; selecting a page returns to reading. Searching also displays the index. Display wraps do not alter copied code. Bookmarks, query and history are window-session state. Diagrams and steps explain operations; they are not execution controls.

@@ split|Editor|Two files side by side|Independent editor groups and focus.|split windows panes focus
Ctrl+\ toggles two panes, using the existing global split-editor setting. Right-click a file → Open on the right, or Alt+click a tab. Ctrl+1 and Ctrl+2 focus the left/right group; clicking its text also changes focus. Typing, saving and searching use the focused pane. Ctrl+Alt+S saves all open documents. Drag the divider to resize.
Each pane retains its document history, selection, scrolling and syntax colors. Selecting an already visible file focuses its existing pane, so two widgets never share its mutable view. Below 240 GUI pixels the editor falls back to one pane, retaining the split preference. These are panes inside Minecraft, not separate desktop windows.


@@ assist|Editor|Code tools 33|Find/replace, snippet fields and editing commands.|replace snippets brackets block lines whitespace
Ctrl+Shift+J opens code tools for the focused pane; Search and the file menu also provide access. Ctrl+H configures literal replacement: match case, whole identifier words and selection only. Enter find/replacement text; an empty replacement deletes matches and surrounding spaces are preserved. Preview shows the count and first 30 original excerpts. Continue chooses the next match from the cursor (wrapping to the start) or all matches. Esc cancels. Replace all is one undo step. A changed/switched document rejects stale previews. There are no regular expressions or project-wide replacements.
Ctrl+J selects Java/Python/JavaScript snippets. Edit the selected field and use Tab/Shift+Tab to visit fields. The final Tab moves to the ending cursor; Esc ends field mode. Selection can be wrapped by a template. Fields are independent, without semantic renaming or import insertion. Editing outside the active field cancels field navigation at the next Tab. Snippet insertion is one undo step; later typing uses ordinary history.
Ctrl+M jumps to a matching ()[]{} bracket at/just before the cursor. Ctrl+Shift+M selects its enclosing contents and expands to an outer pair on repetition. Strings/comments follow the colour lexer, not a compiler. Ctrl+Shift+K deletes the current/selected lines, excluding a final row selected only through column zero. Trim trailing whitespace removes ending spaces/tabs throughout the focused file, preserving indentation, line breaks and the final newline. Text edits are undoable.
Find/replace and snippet input: 1 MiB; at most 5000 replacement matches and 2 MiB output. Brackets: 20000 rows, 32768 characters per row, 100000 brackets, with a token overflow guard. Ordinary editing remains available beyond these feature limits.

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

@@ first155|Build|Automatic initial release|No manual release names or staging folders.|0.5.0 156 server apply configuration
Apply to selected server creates a random release name and imports complete current client/server mod sets once. Successors use the verified signed baseline and replace the project JAR by modId. Preview and signing are performed by the owner-local pipeline. Configure reachable game/distribution endpoints in Servers if needed. Leave both PEM fields blank to generate a publisher-rooted certificate and key automatically. Public domain names and routing are not generated.

@@ remote_test152|Build|Server JAR only|An explicit narrower route.|0.5.0 156 server apply configuration
Apply server JAR only is shown for a supported connected server and permissions. It builds/uploads one server JAR and requests restart; it does not distribute a client mod set. Full automatic publication applies to a locally selected managed server folder. Device authentication protects offline server administration, but does not migrate the legacy complete remote offline client installer.

@@ remote154|Build|Contextual launch actions|Show applicable actions.|0.5.0 156 server apply configuration
The launch menu exposes applicable Gradle build, selected-server deployment, connected server JAR, Java file and single-player actions. Additional actions retain advanced tools. A plain Java file is not a NeoForge mod. Shared builds still require server-approved source state and participant permissions.

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

