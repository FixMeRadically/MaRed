#!/usr/bin/env python3
"""Read-only core source/resource diagnosis. Does not infer runtime classpath from source presence."""
from pathlib import Path,PurePosixPath
import argparse,json,re,sys

def inspect(project):
 root=Path(project).absolute();core=root/'technologies/core';contract=core/'foundation117.json'
 if not contract.is_file() or contract.stat().st_size>262144:raise ValueError('GENESIS117_FOUNDATION_MISSING')
 d=json.loads(contract.read_text(encoding='utf-8-sig'))
 if d.get('schema')!=117:raise ValueError('GENESIS117_FOUNDATION_SCHEMA')
 missing=[];bad_package=[];linked=[]
 for kind in ('sources','resources'):
  for name in d[kind]:
   rel=PurePosixPath(name)
   if rel.is_absolute() or '..' in rel.parts or '\\' in name or ':' in name:raise ValueError('GENESIS117_UNSAFE_REFERENCE')
   p=core.joinpath(*rel.parts)
   if any(x.is_symlink() or x.exists() and getattr(x.lstat(),'st_file_attributes',0)&0x400 for x in (p,*p.parents)):linked.append(name);continue
   if not p.is_file():missing.append(name);continue
   if kind=='sources':
    expected='.'.join(rel.parts[3:-1]);text=p.read_text(encoding='utf-8-sig');match=re.search(r'^\s*package\s+([A-Za-z0-9_.]+)\s*;',text,re.M)
    if not match or match.group(1)!=expected:bad_package.append(name)
 duplicates=[];owners={};actual=list((core/'src/main/java').rglob('*.java'))
 if len(actual)>4096:raise ValueError('GENESIS117_SOURCE_LIMIT')
 for path in actual:
  if any(x.is_symlink() or x.exists() and getattr(x.lstat(),'st_file_attributes',0)&0x400 for x in (path,*path.parents)):
   name=str(path.relative_to(core))
   if name not in linked:linked.append(name)
   continue
  if path.stat().st_size>4*1024*1024:raise ValueError('GENESIS117_SOURCE_LIMIT')
  match=re.search(r'^\s*package\s+([A-Za-z0-9_.]+)\s*;',path.read_text(encoding='utf-8-sig'),re.M)
  if match:
   identity=match.group(1)+'.'+path.stem
   if identity in owners:duplicates.append([owners[identity],str(path.relative_to(core))])
   else:owners[identity]=str(path.relative_to(core))
 return dict(schema=117,scope='SOURCE_ONLY',expected_sources=len(d['sources']),actual_java_files=len(list((core/'src/main/java').rglob('*.java'))),missing=missing,bad_package=bad_package,linked=linked,duplicate_source_identities=duplicates,gradle_classpath_checked=False,source_complete=not(missing or bad_package or linked or duplicates))
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);a=p.parse_args();d=inspect(a.project);print(json.dumps(d,ensure_ascii=False,indent=2));return 0 if d['source_complete'] else 2
if __name__=='__main__':
 try:raise SystemExit(main())
 except (OSError,ValueError,KeyError) as failed:raise SystemExit('Core117: '+str(failed))
