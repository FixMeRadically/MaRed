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

### Restart flow95
Apply and manual client restart now share one server-thread checkpoint and one
cancellable exit gate. Normal final Minecraft saving remains authoritative; the
checkpoint does not replace the final save/stop/normal-process-exit90 proof.
Automatic periodic/full-player-NBT restore is retired. Existing snapshot files
remain on disk for explicit recovery; login never applies them automatically.

A new managed supervisor exports protocol95 and its instance. Signed
prepare/commit/cancel/status requests bind instance, current launch, integrated
epoch (or no active world), resume ticket, request UUID and a short-lived grant.
Prepare alone never closes the game. Commit atomically publishes a shutdown file
bound to the exact client attempt. A stale file cannot create a fresh checkpoint.
Esc cancels the own Core overlay before normal exit is claimed. Once exit has
begun, cancellation cannot revoke its committed replacement. Invalid context,
save failure or uncertain final exit keeps the restart from launching a new JVM.

Initial unmanaged-to-managed activation shares the cancellable client checkpoint
and owns only its new bootstrap process. That first activation still uses the
existing handoff/PID-wait contract; independent final integrated save attestation
for the initial handoff remains work. This is not the dedicated pair updater
ported into singleplayer or a LAN update coordinator. Windows/native trees,
actual mixin delivery, focus and visual seamlessness still need game acceptance.

History-only diagnostic: py .\tools\restart_flow95.py .
Use --profile with the game directory if ambiguous. An existing94 owner cannot
load95 by changing files: close the game normally before the first new build/run.
