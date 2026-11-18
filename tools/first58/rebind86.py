"""Rebind public pre-login installs only to a freshly downloaded, verified identical-code candidate."""
from pathlib import Path
import copy,json,shutil,subprocess,tempfile,time,uuid
from .common import safe,sha,atomic
from .runtime import core_classpath
from .install70 import policy,files

def call(w):
 cmd=[w.spec['clientCommand'][0],'-cp',str(core_classpath(w.folder)),'com.fixmer.genesis.technology.server.remote.BootstrapRenewal86','refresh',w.r['cache'],w.r['candidate'],'1.21.1',w.r['neoforge']]
 with tempfile.TemporaryFile() as out,tempfile.TemporaryFile() as err:
  child=subprocess.Popen(cmd,stdin=subprocess.DEVNULL,stdout=out,stderr=err,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),close_fds=True)
  try:
   end=time.monotonic()+660
   while child.poll() is None:
    if w.cancelled():raise InterruptedError('REMOTE70_CANCELLED')
    if time.monotonic()>end:raise ValueError('BOOTSTRAP86_REFRESH_FAILED')
    time.sleep(.2)
   if w.cancelled():raise InterruptedError('REMOTE70_CANCELLED')
   out.seek(0);data=out.read(65537)
   if child.returncode:
    err.seek(0);code=err.read(4097).decode('utf8','replace').strip();raise ValueError(code if code in {'BOOTSTRAP86_CONTENT_CHANGED','BOOTSTRAP86_CONTEXT_CHANGED','BOOTSTRAP86_ROLLBACK'} else 'BOOTSTRAP86_REFRESH_FAILED')
   if len(data)>65536:raise ValueError('BOOTSTRAP86_REFRESH_FAILED')
   return json.loads(data.decode('utf8'))
  finally:
   if child.poll() is None:child.kill();child.wait(timeout=5) # This exact verification/download helper, never the game.

def refresh(worker):
 w=worker;candidate=safe(w.r['candidate'])
 if w.config is not None or not (candidate/'bootstrap76.cfg').is_file() or (w.folder/'control73.cfg').exists() or (w.root/'control73.cfg').exists():raise ValueError('BOOTSTRAP86_PRIVATE_ROUND')
 from .bootstrap76 import Gate76
 gate=getattr(w,'gate73',None)
 if gate is not None and type(gate) is not Gate76:raise ValueError('BOOTSTRAP86_PRIVATE_ROUND')
 if files(w.source/'mods')!=w.baseline:raise ValueError('REMOTE70_SOURCE_CHANGED')
 data=call(w)
 if set(data)!={'candidate','game','distribution','endpoint','release','archiveSha256','files','installAuthority'} or data['installAuthority'] is not False:raise ValueError('BOOTSTRAP86_REFRESH_FAILED')
 new=safe(data.pop('candidate'));info=data
 if str(uuid.UUID(new.name))!=new.name:raise ValueError('BOOTSTRAP86_REFRESH_FAILED')
 if new.parent!=safe(Path(w.r['cache'])/'downloads') or not (new/'bootstrap76.cfg').is_file() or any(info[k]!=w.info[k] for k in ('game','distribution','endpoint')) or sorted(info['files'],key=lambda x:x['path'])!=sorted(w.info['files'],key=lambda x:x['path']):raise ValueError('BOOTSTRAP86_CONTENT_CHANGED')
 if policy(w.source/'mods',new/'client.zip',info,bool(w.spec.get('devSnapshot')))!=w.approved:raise ValueError('BOOTSTRAP86_CONTENT_CHANGED')
 from .bootstrap76 import Gate76
 trial=copy.copy(w);trial.r=dict(w.r,candidate=str(new));trial.info=info;next_gate=Gate76(trial);next_gate.prepare()
 if gate is not None:
  if next_gate.last<gate.last or next_gate.last==gate.last and gate.expected and next_gate.expected!=gate.expected:raise ValueError('BOOTSTRAP86_CONTEXT_CHANGED')
  if gate.expected and any(next_gate.expected[k]!=gate.expected[k] for k in ('owner','generation','game','endpoint','distribution','signer','inventory')):raise ValueError('BOOTSTRAP86_CONTEXT_CHANGED')
 with tempfile.TemporaryDirectory(prefix='.candidate86-',dir=safe(w.root)) as directory:
  part=Path(directory)/'client.zip'
  shutil.copyfile(safe(new/'client.zip'),part)
  if sha(part,300*1024*1024)!=info['archiveSha256'] or files(w.source/'mods')!=w.baseline:raise ValueError('REMOTE70_PACKAGE_CHANGED')
  part.replace(safe(w.archive))
 w.r=dict(w.r,candidate=str(new));w.info=info
 next_gate.w=w;w.gate73=next_gate;return info
