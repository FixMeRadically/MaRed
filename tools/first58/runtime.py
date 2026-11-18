"""Find the controller's own core, either bundled or in a source checkout."""
from pathlib import Path
from .common import safe

ARTIFACT = 'com/fixmer/genesis/technology/server/firstlaunch/FirstLaunchArtifacts.class'

def core_classpath(project):
    project = safe(project)
    jar = safe(project / 'runtime/core.jar')
    if jar.is_file():
        import zipfile
        with zipfile.ZipFile(jar) as archive:
            for name in (ARTIFACT, ARTIFACT.replace('FirstLaunchArtifacts', 'FirstLaunchProbe')):
                if name not in archive.namelist():
                    raise ValueError('Bundled controller core is incomplete; rebuild MaRed.')
        return jar
    classes = safe(project / 'technologies/core/build/classes/java/main')
    if (classes / ARTIFACT).is_file():
        return classes
    raise ValueError('Controller core is missing. Build MaRed, then reopen server setup.')
