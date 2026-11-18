#!/usr/bin/env python3
"""Read initial handoff96 history. It is not a live process check or permission to retry."""
from pathlib import Path
import argparse,json,uuid

def read(project,profile=None):
 root=Path(project).resolve();profiles=[Path(profile).resolve()] if profile else [root,root/'run/client'];found=[p/'config/mared/dev/handoff96-last.properties' for p in profiles if (p/'config/mared/dev/handoff96-last.properties').is_file()]
 if len(found)!=1:raise ValueError('Нужен один отчёт первой активации96. Укажи --profile с папкой игры.')
 path=found[0]
 if any(p.is_symlink() for p in [path,*path.parents]) or path.stat().st_size>4096:raise ValueError('Недопустимый отчёт.')
 with path.open('rb') as stream:data=stream.read(4097)
 if len(data)>4096:raise ValueError('Недопустимый отчёт.')
 p={}
 for line in data.decode('utf8').splitlines():
  if not line or line[0] in '#!':continue
  k,sep,v=line.partition('=')
  if not sep or k in p:raise ValueError('Повреждённый отчёт.')
  p[k]=v
 if p.get('schema')!='96' or p.get('phase') not in {'WAITING','COMMITTED','CONFIRMED','HOLD'}:raise ValueError('Нужен отчёт версии96.')
 if str(uuid.UUID(p.get('id','')))!=p['id'] or p.get('reason') not in {'awaiting_client_commit','awaiting_final_save_and_exit','final_save_and_exit_verified','handoff_not_confirmed'}:raise ValueError('Повреждённый отчёт.')
 return {'schema':96,'id':p['id'],'last_recorded_phase':p['phase'],'reason':p['reason'],'current_liveness':'not_checked','hint':'История первой активации. CONFIRMED относится к завершению исходной игры, а не к готовности нового мира. Отчёт не даёт разрешения на повторный запуск.'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',nargs='?',default='.');p.add_argument('--profile');a=p.parse_args()
 try:print(json.dumps(read(a.project,a.profile),ensure_ascii=True,indent=2));return 0
 except (OSError,ValueError) as error:print(str(error));return 2
if __name__=='__main__':raise SystemExit(main())
