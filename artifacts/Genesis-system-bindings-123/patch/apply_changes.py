#!/usr/bin/env python3
"""Genesis123 patch: complete preflight, exact backups, rollback and idempotence."""
from pathlib import Path, PurePosixPath
import argparse
import datetime
import hashlib
import json
import os
import tempfile
import uuid

HERE = Path(__file__).resolve().parent

def digest(data): return hashlib.sha256(data).hexdigest()
def canonical(data): return digest(data.replace(b'\r\n', b'\n'))
def target(root, relative):
    parts = PurePosixPath(relative)
    if parts.is_absolute() or '..' in parts.parts or '\\' in relative or ':' in relative:
        raise ValueError('Unsafe manifest path')
    path = root.joinpath(*parts.parts)
    for ancestor in (path, *path.parents):
        if ancestor.is_symlink() or ancestor.exists() and getattr(ancestor.lstat(), 'st_file_attributes', 0) & 0x400:
            raise ValueError('Linked path refused: ' + relative)
    return path

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.genesis59-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        Path(name).unlink(missing_ok=True)

def restore(root, backup):
    backup=backup.resolve()
    manifest=json.loads((backup/'backup.json').read_text(encoding='utf-8'))
    if manifest['project'] != str(root):raise ValueError('Backup belongs to a different project')
    pending=[]
    for entry in manifest['files']:
        path=target(root,entry['path']);current=path.read_bytes() if path.is_file() else None
        original=(backup/'files'/entry['path']).read_bytes() if entry['existed'] else None
        if original is not None and digest(original)!=entry['before']:raise ValueError('Backup changed: '+entry['path'])
        if current==original:continue
        if current is None or digest(current)!=entry['after']:raise ValueError('File changed since patch; restore refused: '+entry['path'])
        pending.append((path,original))
    for path,original in pending:
        if original is None:path.unlink()
        else:write(path,original)
    print('Restored:',len(pending),'files')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project',type=Path);parser.add_argument('--check',action='store_true');parser.add_argument('--diagnose',action='store_true');parser.add_argument('--restore',type=Path)
    args=parser.parse_args();root=args.project.absolute()
    target(root,'build.gradle')
    if args.restore:
        restore(root,args.restore);return
    if not (root/'build.gradle').is_file():raise ValueError('Select the MaRed project root')
    manifest=json.loads((HERE/'manifest.json').read_text(encoding='utf-8'))
    pending=[];current=0;issues=[];observed=[];rows=[]
    for entry in manifest['files']:
        path=target(root,entry['path']);payload=(HERE/'files'/entry['path']).read_bytes()
        if digest(payload)!=entry['sha256']:raise ValueError('Patch123 payload changed: '+entry['path'])
        if path.exists() and not path.is_file():raise ValueError('Not a regular file: '+entry['path'])
        before=path.read_bytes() if path.is_file() else None;observed.append((path,before))
        actual=canonical(before) if before is not None else None
        versions=[v for v,h in entry['versions'].items() if h==actual]
        status='CURRENT' if before is not None and actual==canonical(payload) else 'KNOWN_'+','.join(versions) if versions else 'NEW_FILE' if before is None and entry['allow_missing'] else 'MISSING_BASE' if before is None else 'UNKNOWN'
        rows.append(dict(path=entry['path'],status=status,sha256=actual,utf8_bom=bool(before and before.startswith(b"\xef\xbb\xbf"))))
        if status in {'MISSING_BASE','UNKNOWN'}:issues.append(rows[-1]);continue
        if status=='CURRENT':current+=1;continue
        pending.append((entry['path'],path,before,payload))
    for entry in manifest.get('required',[]):
        path=target(root,entry['path']);before=path.read_bytes() if path.is_file() else None;observed.append((path,before));actual=canonical(before) if before is not None else None
        if actual!=entry['sha256']:
            row=dict(path=entry['path'],status='WORKSPACE122_PREREQUISITE',sha256=actual);issues.append(row);rows.append(row)
    if args.diagnose:
        print(json.dumps(dict(report='Genesis123',pending=len(pending),current=current,issues=len(issues),files=rows),indent=2,ensure_ascii=True))
        if issues:raise SystemExit(2)
        return
    if issues:
        for row in issues:print(row['status']+': '+row['path']+'; SHA256='+str(row['sha256']))
        raise ValueError('Unknown or missing base; no files changed. Use --diagnose. Do not force overwrite.')
    print('Preflight OK:',len(pending),'pending;',current,'current files; workspace122 prerequisites verified')
    if args.check:return
    if not pending:print('Already current; no files changed.');return
    backup_parent=target(root,'.genesis-backups/patch123')
    backup_parent.mkdir(parents=True,exist_ok=True)
    backup=backup_parent/(datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8])
    backup.mkdir()
    entries=[]
    for relative,path,before,payload in pending:
        if before is not None:
            saved=backup/'files'/relative;saved.parent.mkdir(parents=True,exist_ok=True);saved.write_bytes(before)
        entries.append({'path':relative,'existed':before is not None,'before':digest(before) if before is not None else None,'after':digest(payload)})
    (backup/'backup.json').write_text(json.dumps({'patch':123,'project':str(root),'files':entries},indent=2),encoding='utf-8')
    # Recheck every source before the first mutation (editors/builds may have changed it).
    for path,before in observed:
        actual=path.read_bytes() if path.is_file() else None
        if actual!=before:raise ValueError('Source changed after preflight; no files changed: '+str(path.relative_to(root)))
    applied=[]
    try:
        for relative,path,before,payload in pending:
            applied.append((path,before,payload))
            write(path,payload)
        for relative,path,before,payload in pending:
            if path.read_bytes()!=payload:raise ValueError('Post-write verification failed: '+relative)
    except BaseException:
        failures=[]
        for path,before,payload in reversed(applied):
            try:
                if before is None:path.unlink(missing_ok=True)
                else:write(path,before)
            except OSError:failures.append(str(path))
        if failures:print('Rollback incomplete; recover from backup:',backup)
        raise
    print('Applied patch123:',len(pending),'files')
    print('Backup:',backup)

if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError) as error:
        raise SystemExit('Genesis123: '+str(error))
