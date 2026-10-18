# JDK-only supervisor

`Supervisor` is the stable entry point. It runs in a separate JVM, from a private resource built by the mod's build.gradle. There is no second Gradle project.

- `Supervisor` owns child lifecycle. All launch/stop/restart operations run on one worker. EOF is not shutdown.
- `ManagedChildProcess` owns the Process, child stdin/output and remembered descendants. Never kill unrelated JVMs or delete game locks.
- `SupervisorConfig` parses UTF-8 properties relative to the file, preserving command.N argument boundaries.
- `SupervisorHttpServer` authenticates loopback requests and enqueues work; waiting on a game exit must not block HTTP status requests.
- `SupervisorLog` owns serialized writes, bounded ring buffer and file rotation.

Keep this package independent of Minecraft/NeoForge. Client bootstrap, world persistence and editor triggers belong in `com/fixmer/mared/dev/`.
`verification/check-supervisor.py` exercises the actual classes with child JVMs. Re-run on Windows before claiming cmd.exe/tree behavior is verified there.
