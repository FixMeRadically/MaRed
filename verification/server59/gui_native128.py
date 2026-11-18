"""Real NeoForge GUI acceptance in a new workspace world; never starts/stops the user's server."""
from pathlib import Path
import ctypes
from ctypes import wintypes
import hashlib
import io
import json
import shutil
import socket
import sys
import time
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from server104 import prepare
from first58.inplace104 import Owner104


def main():
    workspace=Path(__file__).resolve().parents[2]
    original=Path(sys.argv[1]).resolve()
    java=Path(sys.argv[2]).resolve()
    fixture=workspace/'verification/server128-gui'
    if fixture.exists():raise RuntimeError('Test directory already exists; preserve it for inspection')
    fixture.mkdir()
    shutil.copytree(original/'libraries',fixture/'libraries')
    (fixture/'run.bat').write_text('@echo off\njava @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.264/win_args.txt %*\npause\n')
    (fixture/'user_jvm_args.txt').write_text('-Xms128M\n-Xmx1024M\n')
    (fixture/'eula.txt').write_bytes((original/'eula.txt').read_bytes())
    with socket.socket() as reservation:
        reservation.bind(('127.0.0.1',0));port=reservation.getsockname()[1]
    (fixture/'server.properties').write_text('server-ip=127.0.0.1\nserver-port='+str(port)+'\nonline-mode=false\nlevel-name=world128\nview-distance=2\nsimulation-distance=2\nmax-tick-time=60000\nmotd=Genesis GUI128 isolated test\n')
    mod=(workspace/'build/libs/mared-0.4.0.jar').read_bytes()
    with zipfile.ZipFile(io.BytesIO(mod)) as jar:bundle=jar.read('genesis/server59/controller.zip')
    digest=lambda b:hashlib.sha256(b).hexdigest()
    app=fixture/'.genesis/server104/app'/digest((digest(bundle)+digest(mod)).encode())
    app.mkdir(parents=True)
    with zipfile.ZipFile(io.BytesIO(bundle)) as archive:
        for item in archive.infolist():
            target=(app/item.filename).resolve()
            if not target.is_relative_to(app.resolve()):raise RuntimeError('Archive path')
        archive.extractall(app)
    (app/'mared.jar').write_bytes(mod)
    prepare(fixture,java)
    owner=Owner104(fixture,app,java)
    try:
        owner.bootstrap();owner.start_game()
        job=owner.job;job.status()
        job.k.GetProcessId.argtypes=[wintypes.HANDLE];job.k.GetProcessId.restype=wintypes.DWORD
        pid=int(job.k.GetProcessId(job.server_process124))
        if not pid:raise RuntimeError('Owned Java PID unavailable')
        (fixture/'gui128-ready.json').write_text(json.dumps(dict(javaPid=pid,port=port,profile=str(fixture))))
        print('GUI128 actual NeoForge READY; owned Java PID='+str(pid)+'; isolated port='+str(port),flush=True)
        deadline=time.monotonic()+120
        while not (fixture/'gui128-release').exists() and time.monotonic()<deadline:
            if owner.all_exited():raise RuntimeError('Server exited before GUI observation')
            owner.report();time.sleep(.25)
        owner.stop_game();owner.report('STOPPED')
        print('GUI128 STOPPED_SAVED and empty Job confirmed; user server untouched',flush=True)
    finally:
        owner.close()


if __name__=='__main__':main()
