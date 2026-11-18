# Genesis123 validation — 2026-10-29

Environment: Windows, JDK21.0.12, Gradle8.14.5, NeoForge21.1.264, Minecraft1.21.1 API.

- Full `gradlew.bat build`: **BUILD SUCCESSFUL**, 107 tasks (77 executed,30 up-to-date).
- Final jar/test/Workspace/supervisor/editor/bootstrap run after caret/controller and compatibility
  adjustments: **BUILD SUCCESSFUL**,46 tasks.
- Final artifact rebuild after retaining persistent cache warnings: **BUILD SUCCESSFUL**,40 tasks.
- JUnit:277 tests,0 failures,0 errors,4 conditionally skipped (273 executed).
- WorkspaceRegressionChecks123:63 passed.
- SupervisorControlChecks123:19 passed, actual HTTP endpoint and authentication/replay checks.
- WorkspaceChecks121:1174 passed, real CRDT/journal/child-JVM checks.
- WorkspaceFlowChecks122:495 passed, real storage and three independent client JVMs.
- AuditEditorChecks111:116 passed; BootstrapIsolationChecks121:139 passed.
- Installer:36 checks passed on a temporary copy: preflight, application, idempotence, restoration,
  refusal to overwrite an unknown base or a later manual edit, and rollback after injected write failure.
- Patch contains21 changed/new files.1070 unchanged source/resource prerequisites are hash-checked.

The shipped mod embeds genesis-core0.1.39. The mod's internal version remains0.4.0;
the distributable filename carries the Genesis123 patch suffix. Replace the old mod rather than
leaving both files in mods. The standalone core JAR is not needed in mods.

Full source build logs are included under `validation/`. The final packaged JAR was rebuilt from
the final source files. Checksums are in `SHA256SUMS.txt`.

Limits: no Minecraft client/server game session, graphical input/render acceptance, actual world
restart, or real multi-device rollout was executed. Some existing tests explicitly use lifecycle,
TLS readiness and native Job fixtures; those results do not establish in-game operation.
Workspace export -> Gradle -> BuildSnapshot84 -> supervisor deployment remains disconnected.

`git diff --check` also reports a pre-existing trailing space in DevRestartWindow.java:66;
that unrelated user change was preserved. Gradle reports deprecated APIs/features; the build succeeds.
