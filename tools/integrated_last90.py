#!/usr/bin/env python3
"""Compact local-world restart evidence. No launch arguments, credentials or world content."""
from pathlib import Path
import argparse,json,re

def report(project):
 root=Path(project).resolve();candidates=[root/'config/mared/dev',root/'run/client/config/mared/dev'];dev=next((p for p in candidates if p.is_dir()),candidates[-1])
 result={'report':'integrated90','source90':(root/'src/main/java/com/fixmer/mared/dev/client/DevIntegratedRestart90.java').is_file(),'game_markers':[],'hold_seen_in_log':False,'hint':'Markers describe code evidence only; verify saved changes and world return in the game.'}
 for path,key in [(dev.parent.parent.parent/'logs/latest.log','game'),(dev/'logs/supervisor.log','supervisor')]:
  try:
   if path.is_symlink() or path.stat().st_size>16*1024*1024:continue
   text=path.read_text(encoding='utf8',errors='replace')
   if key=='game':result['game_markers']=re.findall(r'\[Genesis integrated90\] (ACTIVE|SAVED|STOPPED|FAILED|CHECKPOINT_CONFIRMED)\b',text)[-24:]
   else:result['hold_seen_in_log']='INTEGRATED90_HOLD' in text
  except OSError:pass
 return result
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',nargs='?',default='.');a=p.parse_args();print(json.dumps(report(a.project),ensure_ascii=False,indent=2))
