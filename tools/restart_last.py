#!/usr/bin/env python3
"""Compact actual restart timings; no launch arguments, credentials or environment dumps."""
from pathlib import Path
import argparse,json

def last_report(path):
 path=Path(path)
 if path.is_dir():
  candidates=[path/'timings.jsonl',path/'config/mared/dev/logs/timings.jsonl',path/'run/client/config/mared/dev/logs/timings.jsonl']
  path=next((p for p in candidates if p.is_file()),None)
 if path is None or not path.is_file():raise ValueError('timings.jsonl not found')
 if path.stat().st_size>8*1024*1024:raise ValueError('timings file exceeds 8 MiB')
 cycles={}
 for line in path.read_text(encoding='utf-8').splitlines():
  try:
   event=json.loads(line);cycle=event['cycleId'];phase=event['phase'];value=event['valueMs'];elapsed=event['cycleMs']
   if not isinstance(value,(int,float)) or not isinstance(elapsed,(int,float)) or not 0<=value<=86_400_000 or not 0<=elapsed<=86_400_000:continue
   cycles.setdefault(cycle,{'kind':event.get('cycleKind','unknown'),'events':{}})['events'][phase]=event
  except (ValueError,KeyError,TypeError):continue
 ready=[c for c in cycles.values() if any(p in c['events'] for p in ['world_ready','title_ready'])]
 if not ready:raise ValueError('No completed restart/world entry recorded yet')
 cycle=ready[-1];e=cycle['events'];r=e.get('world_ready',e.get('title_ready'));out={'cycle_kind':cycle['kind'],'total_ms':r['cycleMs'],'launch_mode':'unrecorded','fast_launch_reason':'unrecorded','shutdown_confirmation':'unrecorded'}
 for phase in e:
  if phase.startswith('shutdown_via_'):out['shutdown_confirmation']=phase.removeprefix('shutdown_via_')
  if phase.startswith('fast_'):
   out['fast_launch_reason']=phase.removeprefix('fast_')
   changes=e[phase].get('changedInputs',[])
   if isinstance(changes,list):out['fast_launch_changed_inputs']=[s for s in changes[:8] if isinstance(s,str) and len(s)<=240]
  if phase.startswith('cache_capture_'):out['launch_cache_capture']=phase.removeprefix('cache_capture_')
  if phase in ['launch_direct_cache','launch_direct_config','launch_gradle']:out['launch_mode']=phase.removeprefix('launch_')
 out['startup_profile_requested']='startup_profile_requested' in e
 def value(p):return e[p]['valueMs'] if p in e else None
 out['presentation_total_ms']=value('scene_total');out['shutdown_ms']=value('shutdown');out['prepare_ms_parallel']=value('prepare');out['spawn_ms']=value('spawn');out['launcher_gap_ms']=value('launcher_gap');out['jvm_to_mod_loaded_ms']=value('mod_loaded')
 load=value('world_loading');mod=value('mod_loaded');world=value('world_ready')
 out['resources_and_menu_ms']=load-mod if load is not None and mod is not None and load>=mod else None
 resource=value('resources_ready')
 out['mod_to_resources_ready_ms']=resource-mod if resource is not None and mod is not None and resource>=mod else None
 out['mared_setup_ms']={k:value('mared_'+k) for k in ['threading','persistent','plugins','setup']}
 out['maintenance_ms']={k:value('startup_'+k) for k in ['launch_cache','font']}
 out['world_load_ms']=world-load if world is not None and load is not None and world>=load else None
 return out

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('path',nargs='?',default='.');a=p.parse_args()
 try:print(json.dumps(last_report(a.path),ensure_ascii=False,indent=2))
 except (ValueError,OSError) as e:raise SystemExit(str(e))
if __name__=='__main__':main()
