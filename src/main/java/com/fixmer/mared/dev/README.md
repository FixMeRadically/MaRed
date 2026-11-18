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

### Initial activation96
Initial activation now creates a per-attempt signed intent and progress record,
bound to raw supervisor config, config/game paths, instance, request, integrated
world epoch, exact resume snapshot and source process/lease. The source keeps an
independent GameLease open through all shutdown hooks. The new owner rechecks the
intent before accepting commit and independently verifies final save + STOPPED,
normal game shutdown event, exit hook and actual source lease release before any
launch/deployment. A shutdown hook alone and PID disappearance are insufficient.
When native start metadata is unavailable, the signed held-lease identity is used;
lease release is still mandatory. This is cooperative local evidence, not a claim
to discover arbitrary launchers/processes or to obtain an external OS exit code.

The former bare --handoff-pid route is rejected. New source and owner code are
both required. Cancel before exit publishes cancellation, disarms the source and
releases only its preparation lease. A late/corrupt/failed/mismatched report fails
closed without force-closing the original process. Once exit is claimed, cancel
cannot revoke the replacement. The owner does not adopt the original PID.

History-only diagnostic: py .\tools\handoff_last96.py .
CONFIRMED means the original source evidence was verified; new-world readiness,
input and visual seamlessness are separate. Logs/intent/progress are retained for
review; there is no automatic replay of a dead source or old signed handoff.

This closes the previous initial save-attestation implementation gap, subject to
real Windows/JDK21/NeoForge/Minecraft acceptance. It does not complete the full
integrated package/recovery pipeline, LAN coordination or shared Workspace.

### In-game restart history97
Server panel -> Restart history, or local client command /mared dev history.
Overview, attempts and load-times tabs read bounded diagnostic projections only.
Refresh/F5, Copy/Ctrl+C, wheel/PageUp/PageDown/Home/End, Recovery and Back are provided.
The screen never starts a process, installs files, sends a network request, adopts
PID or decides recovery. Recovery opens the existing guarded91 review screen.

New owner97 records up to48 validated per-attempt/cycle diagnostic files in
config/mared/dev/history97. The view shows up to24 attempts and12 cycles. Only
known phase/reason enums, IDs and bounded numbers are exposed; clipboard uses at
most6 attempts/4 cycles with UTC timestamps, no argv/token/account/server/path dump.
Previous95/96 last reports remain visible as legacy snapshots. Old JSONL timings
are retained; new UI timing snapshots start with an updated owner97 launch.
Reading uses a bounded background queue and per-screen publication revision. Close
invalidates delayed results. Error/partial/empty/loading are separate. GUI sizes
below240x160 show a resize/GUI-scale hint plus Back rather than overlapping widgets.

History is not liveness or install authority. CONFIRMED refers to initial source
shutdown, DONE to process replacement; readiness markers are separate. Timing
phases overlap and pre-supervisor checkpoint time is not included. Unknown files
are not deleted; retention only removes validated own history records. New history
IO failure cannot grant or revoke lifecycle authority. Shared/linked profiles and
actual Windows/Minecraft GUI/input still require runtime acceptance.
