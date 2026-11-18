#!/usr/bin/env python3
"""Recorded source integration inventory, not a Minecraft/runtime acceptance test."""
from pathlib import Path,PurePosixPath
import argparse,json,sys

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);p.add_argument('--output',type=Path);a=p.parse_args();root=a.project.resolve();source=root/'docs/INTEGRATION_CATALOG_116.json'
 if not source.is_file() or source.stat().st_size>262144:raise ValueError('Integration116 catalog missing or oversized')
 d=json.loads(source.read_text(encoding='utf-8-sig'))
 if d.get('schema')!=116:raise ValueError('Integration116 schema mismatch')
 missing=[];counts={}
 for item in d['items']:
  counts[item['state']]=counts.get(item['state'],0)+1
  for entry in item['evidence']:
   p=PurePosixPath(entry)
   if p.is_absolute() or '..' in p.parts or '\\' in entry or ':' in entry:raise ValueError('Unsafe source reference')
   path=root.joinpath(*p.parts)
   if not path.is_file() or path.is_symlink():missing.append(dict(feature=item['id'],file=entry))
 report=dict(schema=116,scope='SOURCE_INVENTORY_ONLY',minecraft_tested=False,all_existing_integration_complete=d['all_existing_integration_complete'],counts=counts,missing_evidence=missing,blocking_connections=d['blocking_connections']);text=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
 if a.output:a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(text,encoding='utf-8')
 else:sys.stdout.write(text)
 if missing:raise SystemExit(2)
if __name__=='__main__':
 try:main()
 except (OSError,ValueError,KeyError) as failed:raise SystemExit('Integration116: '+str(failed))
