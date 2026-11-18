#!/usr/bin/env python3
"""Read-only local recovery diagnostics. Never prints argv, tokens, project/world paths or file contents."""
from pathlib import Path
import argparse,json,re

def record(path):
 if not path.exists():return {}
 if path.is_symlink() or not path.is_file() or path.stat().st_size>65536:raise ValueError('UNREADABLE')
 values={}
 for raw in path.read_text(encoding='utf8').splitlines():
  if not raw.strip() or raw.lstrip().startswith(('#','!')):continue
  key,sep,value=raw.partition('=')
  if not sep:raise ValueError('UNREADABLE')
  if key in values:raise ValueError('UNREADABLE')
  values[key]=value
 return values

def report(project):
 root=Path(project).resolve();dirs=[root/'config/mared/dev/deployments',root/'run/client/config/mared/dev/deployments'];data=next((p for p in dirs if p.is_dir()),dirs[-1]);out={'report':'local_recovery91','source91':(root/'src/main/java/com/fixmer/genesis/supervisor/RecoveryView91.java').is_file(),'trial':'NONE','world_risk':False,'decision':'NONE','restored_review':False,'last_result':'NONE','runtime_verified':False}
 try:
  for parent in (data,*data.parents):
   if parent.is_symlink():raise ValueError('UNREADABLE')
  trial=record(data/'trial.properties');review=record(data/'world-review91.properties');last=record(data/'last-result.properties')
  if trial:
   out['trial']=trial.get('phase') if trial.get('phase') in {'PREPARED','APPLIED','COMMITTED'} else 'UNKNOWN';out['world_risk']=trial.get('worldRisk91')=='true';out['decision']='RESTORE_REQUESTED' if trial.get('recovery91')=='RESTORE_REQUESTED' else 'NONE'
  out['restored_review']=review.get('phase')=='RESTORED_REVIEW'
  if last:out['last_result']=last.get('status') if last.get('status') in {'CONFIRMED','MANUALLY_ACCEPTED91','ROLLED_BACK','CANCELLED'} else 'UNKNOWN'
  out['read_status']='OK'
 except (OSError,ValueError,UnicodeError):out['read_status']='UNREADABLE'
 return out
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',nargs='?',default='.');a=p.parse_args();print(json.dumps(report(a.project),ensure_ascii=False,indent=2))
