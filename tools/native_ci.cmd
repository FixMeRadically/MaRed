@echo off
setlocal
rem Intended for a disposable GitHub-hosted Windows runner, not a game server.
set "GENESIS_NATIVE_VS="
for /f "usebackq tokens=*" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "GENESIS_NATIVE_VS=%%i"
if not defined GENESIS_NATIVE_VS (
  echo MSVC x64 build tools were not found.
  exit /b 1
)
call "%GENESIS_NATIVE_VS%\VC\Auxiliary\Build\vcvars64.bat"
if errorlevel 1 exit /b 1
py tools\native_containment.py . build
if errorlevel 1 exit /b 1
py tools\native_containment.py . selftest
if errorlevel 1 exit /b 1
py tools\native_containment.py . owner-demo
if errorlevel 1 exit /b 1
py tools\native_containment.py . java-demo
if errorlevel 1 goto native50_failed
py tools\native_evidence.py . seal --output build\genesis-native43\evidence-47
if errorlevel 1 goto native50_failed
set "GENESIS_NATIVE_EVIDENCE_SHA="
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 'build\genesis-native43\evidence-47\evidence.properties').Hash.ToLowerInvariant()"') do set "GENESIS_NATIVE_EVIDENCE_SHA=%%i"
if not defined GENESIS_NATIVE_EVIDENCE_SHA goto native50_failed
py tools\native_bound.py . demo --evidence build\genesis-native43\evidence-47 --sha256 %GENESIS_NATIVE_EVIDENCE_SHA%
if errorlevel 1 goto native50_failed
py tools\native_lifecycle.py . demo --evidence build\genesis-native43\evidence-47 --sha256 %GENESIS_NATIVE_EVIDENCE_SHA%
set "GENESIS_NATIVE_RESULT=%ERRORLEVEL%"
goto native50_finish
:native50_failed
set "GENESIS_NATIVE_RESULT=1"
:native50_finish
py tools\native_containment.py . status > build\genesis-native43\ci-status.json
if errorlevel 1 exit /b 1
py tools\native_evidence.py . status > build\genesis-native43\evidence-status.json
if errorlevel 1 set "GENESIS_NATIVE_RESULT=1"
py tools\native_bound.py . status > build\genesis-native43\bound-status.json
if errorlevel 1 set "GENESIS_NATIVE_RESULT=1"
py tools\native_lifecycle.py . status > build\genesis-native43\lifecycle-status.json
if errorlevel 1 set "GENESIS_NATIVE_RESULT=1"
exit /b %GENESIS_NATIVE_RESULT%
