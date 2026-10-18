# MaRed development lifecycle

- `DevMode` — dev flag, process-local activation and diagnostic running marker.
- `DevPaths` — **all runtime paths and legacy migration**. Config root: `<gameDir>/config/mared/dev/`.
- `DevSessionState` — last successful world entry.
- `client/DevAutoRestart` — coordinator after a successful editor Apply; world/player checkpoint and one restart.
- `client/DevActivationService` — first bootstrap, identity confirmation, normal Minecraft exit.
- `client/DevRestartService` — HTTP request when already supervised.
- `client/DevSessionResume` — one local-world/remote-server return on a ready title screen.
- `client/DevQuickStart` — optional last-world fallback; never opens a world in a Screen constructor or while resource loading is active.
- `client/DevClientConfig`, `client/DevCommands` — client options and manual commands.
- `world/` — local-world config, player NBT, server-thread events.
- `storage/DevResumeStore` — JDK-only one-shot persistence, independently testable.

The external JVM controller lives in `com/fixmer/genesis/supervisor/`. It cannot import Minecraft, NeoForge or this package.
The editor integration is in `technology/scripts/ScriptProjectEditor` and `ServerProjectApplies`: checkpoint recovery documents first, then call the coordinator.
Read `docs/SUPERVISOR_STABLE_03_RU.md` for scope, tests and limitations. Runtime modules are not replayed after a client restart.
