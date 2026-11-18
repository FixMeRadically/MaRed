#!/usr/bin/env python3
"""Read restart95 history; never initiates a restart or treats history as live state."""
from pathlib import Path
import argparse,json,re,uuid

def read(project,profile=None):
 root=Path(project).resolve();profiles=[Path(profile).resolve()] if profile else [root,root/'run/client']
 found=[p/'config/mared/dev/restart95-last.properties' for p in profiles if (p/'config/mared/dev/restart95-last.properties').is_file()]
 if len(found)!=1:raise ValueError('Нужен один отчёт перезапуска95. Укажи --profile с папкой игры.')
 path=found[0]
 if any(p.is_symlink() for p in [path,*path.parents]) or path.stat().st_size>4096:raise ValueError('Недопустимый отчёт.')
 values={}
 for line in path.read_text(encoding='utf8').splitlines():
  if not line or line[0] in '#!':continue
  key,sep,value=line.partition('=')
  if not sep or key in values:raise ValueError('Повреждённый отчёт.')
  values[key]=value
 if values.get('schema')!='95' or values.get('state') not in {'PREPARED','COMMITTED','CANCELLED','EXPIRED','DONE','HOLD'}:raise ValueError('Нужен отчёт версии95.')
 for key in ('request','source_launch'):
  if str(uuid.UUID(values.get(key,'')))!=values[key]:raise ValueError('Повреждённый отчёт.')
 reason=values.get('reason','')
 if not re.fullmatch('[a-z_]{0,64}',reason):raise ValueError('Повреждённый отчёт.')
 return {'schema':95,'request':values['request'],'source_launch':values['source_launch'],'last_recorded_state':values['state'],'reason':reason,'current_liveness':'not_checked','hint':'История последней попытки. DONE подтверждает путь supervisor, но не визуальную бесшовность или состояние загруженного мира.'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',nargs='?',default='.');p.add_argument('--profile');a=p.parse_args()
 try:print(json.dumps(read(a.project,a.profile),ensure_ascii=True,indent=2));return 0
 except (OSError,ValueError) as e:print(str(e));return 2
if __name__=='__main__':raise SystemExit(main())
