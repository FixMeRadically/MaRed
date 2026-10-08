"""Tests actual executor/control/parser sources with a small host stub, not Minecraft integration."""
from pathlib import Path
import subprocess,tempfile,os
root=Path(__file__).resolve().parents[1]
jdk=os.environ.get("JAVA_HOME")
java=str(Path(jdk)/"bin/java") if jdk else "java"
javac=str(Path(jdk)/"bin/javac") if jdk else "javac"
source=root/"src/main/java/com/fixmer/mared/commands"
paths=list((root/"technologies/core/src/main/java").rglob("*.java"))+list((root/"verification/stubs").rglob("*.java"))
paths += [source/p for p in ["engine/MaredScriptCommand.java","engine/MaredScriptExecutor.java","engine/MaredScriptRunner.java","events/MaredEventRegistry.java","control/MaredCallCommand.java","control/MaredIfCommand.java","control/MaredBreakCommand.java","events_cmd/MaredWaitUntilCommand.java","expr/MaredExpr.java"]]
paths += [root/"verification/RuntimeRegressionTest.java"]
with tempfile.TemporaryDirectory() as temp:
 subprocess.run([javac,"-encoding","UTF-8","-d",temp,*map(str,paths)],check=True)
 subprocess.run([java,"-cp",temp,"RuntimeRegressionTest"],check=True)
