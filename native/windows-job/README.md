# Genesis native diagnostics and evidence47

The C runtime remains46:43 Job fixtures,44 native fixture IPC and46 fixed Java
probe. Production Minecraft kind2 and LIVE installation are still disabled.
See NATIVE_JAVA_RU.txt in the source patch for runtime details and limits.

Windows x64 developer environment with Windows SDK/MSVC and JDK17+ on PATH:

```powershell
py .\tools\native_containment.py . build
py .\tools\native_containment.py . selftest
py .\tools\native_containment.py . owner-demo
py .\tools\native_containment.py . java-demo
py .\tools\native_evidence.py . status
```

`native_evidence.py . seal --output NEW_DIRECTORY` preserves the current seven
runtime reports, their hashes and a canonical review manifest. Status is
read-only. A seal is local operator review material, not remote attestation or
an installation/launch capability. Failed/pending/stale reports are blocked.
The Java native_review CLI checks integrity, not current Windows readiness.

The new NativeLaunchIntent API prepares a one-use profile/handoff/capsule/
evidence/helper binding and can record owner PID/start/image and observed exit.
It does not spawn or stop any process and is NOT wired into the runtime yet.
Only fixed diagnostic kind1 is admitted. Unknown/crash requires review.
The existing guardian remains in use. Normal Gradle build does not execute C.

Manual-only Windows Server2022/2025 CI runs runtime46 diagnostics and seals
review material. Upload whitelist18 metadata files; no executable, private
capsule, nonce, world or logs. No Windows CI was executed during Linux work.
Actual OS/BCrypt/Job evidence and save/stop/admission barriers remain required
before production. Job Objects do not isolate external-service/WMI writers.
Read PLAN_FULL_RU.txt checkpoint22 and NATIVE_BINDING_RU.txt in the patch archive.

## Bound diagnostic integration (patch 48)

`tools/native_bound.py . demo --evidence NEW_SEALED_BUNDLE --sha256 MANIFEST_SHA`
compiles the complete headless Java set and runs fixed generated probes through
approved diagnostic handoff, intent47, retained native Process and Session46.
It requires fresh 43/44/46 runtime reports and seal47 after the Session change.
`status` is read-only; no implicit build, launch or native backend activation.
The manual Windows workflow now runs graceful, cancel-before-START and explicit
force cases under paths with spaces/Unicode, and preserves bound attempt/report.
The fixture parent and guardian are one JVM. It is not a production server test.
BoundSession retains its Process and lease if close times out; no automatic kill.
All production launch / installation authority remains disabled. No Windows SDK,
Job kernel, BCrypt or CI results are claimed from the Linux verification host.

## SAVE/STOP join (patch 49)

BoundSession now has an explicit diagnostic SAVE/STOP42 path: fresh root
identity/readiness, matching request ACK, native EMPTY/root exit, retained owner
exit and bound receipt are checked together. StopReview is one-use and can
only record review-required, never grant IDLE/installation. Kind2 remains off.
The fixed Probe48 is not a Minecraft server and receives no fabricated world
save ACK. Real Reporter integration is covered by separate JVM verification
fixtures; owner/Job are test peers there. CI43/44/46/48 is not a49 world-save
proof. Native C/H is unchanged. Real Windows/NeoForge tests remain necessary.

## Native lifecycle harness (patch 50)

Native owner now accepts an additional fixed diagnostic LifecycleProbe JAR,
with the same kind1-only gate and two pins (java.exe/JAR). Exact classpath,
class/marker/modes, supervised UUID and private sibling run directory are
required; reparse/non-directory run paths fail before Job/child. Jar pin is
limited to1MiB. Legacy46 remains; kind2/production stays disabled.
`tools/native_lifecycle.py . demo --evidence BUNDLE --sha256 HASH` invokes real
Reporter42/barrier49 on Windows through metadata-only save/stop callbacks.
Four expected scenarios: graceful, save failure, stop crash, stalled cancel.
Manual Windows CI now runs it after bound48 and saves three more top-level
reports. No private capsules/nonces uploaded. Real Windows/SDK/BCrypt/CI was
not run on the Linux verification host. Harness success is not a world-save
proof, game update or production installation permit. Rebuild helper/reports
and reseal evidence after50. Fixed JAR producer verifies14 exact class resources.
