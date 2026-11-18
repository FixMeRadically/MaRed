#!/usr/bin/env python3
"""Read the last profile-admission diagnostic. It is not proof that a profile is now free."""
from pathlib import Path
import argparse,json

def read(project,profile=None):
 root=Path(project).resolve()
 profiles=[Path(profile).resolve()] if profile else [root,root/'run/client']
 paths=[p/'config/mared/dev/profile-admission-last93.properties' for p in profiles]
 found=[p for p in paths if p.is_file()]
 if len(found)!=1:raise ValueError('Нужен один отчёт. Укажи --profile с папкой игры либо сначала запусти новую сборку93.')
 path=found[0]
 if path.is_symlink() or path.stat().st_size>4096:raise ValueError('Недопустимый файл отчёта.')
 values={}
 for line in path.read_text(encoding='utf8').splitlines():
  if not line or line[0] in '#!':continue
  key,sep,value=line.partition('=')
  if not sep or key in values:raise ValueError('Повреждённый отчёт.')
  values[key]=value
 if values.get('schema')!='93' or values.get('phase') not in {'before_main','mod_constructor_only'}:raise ValueError('Нужен отчёт версии93.')
 import re
 if not all(re.fullmatch('[0-9]{1,20}',values.get(k,'')) for k in ('pid','recorded_ms')):raise ValueError('Повреждённый отчёт.')
 launch=values.get('launch','')
 if launch!='unmanaged' and not re.fullmatch('[a-f0-9-]{36}',launch):raise ValueError('Повреждённый отчёт.')
 return {'schema':93,'phase':values['phase'],'last_launch':launch,'pid_at_recording':int(values['pid']),'recorded_ms':int(values['recorded_ms']),'current_liveness':'not_checked','hint':'Последняя запись; не доказывает, что профиль свободен. before_main = ранняя защита; mod_constructor_only = только после загрузки MaRed.'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',nargs='?',default='.');p.add_argument('--profile');a=p.parse_args()
 try:print(json.dumps(read(a.project,a.profile),ensure_ascii=True,indent=2));return 0
 except (OSError,ValueError) as e:print(str(e));return 2
if __name__=='__main__':raise SystemExit(main())
