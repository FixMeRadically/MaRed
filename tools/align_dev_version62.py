#!/usr/bin/env python3
"""Align NeoGradle and generated VSCode launch versions. No downloads or process control."""
from pathlib import Path
import argparse,hashlib,json,re,uuid
from first58.common import safe,atomic

def changes(root,version):
    if not re.fullmatch(r'21\.1\.\d+',version):raise ValueError('Expected exact NeoForge 21.1.x version')
    root=safe(root);result=[]
    props=safe(root/'gradle.properties');data=props.read_bytes();text=data.decode('utf-8-sig')
    pattern=r'(?m)^(neo_version\s*=\s*)21\.1\.\d+([ \t]*\r?$)'
    if len(re.findall(pattern,text))!=1:raise ValueError('Expected one neo_version entry in gradle.properties; no files changed')
    modified=re.sub(pattern,lambda m:m[1]+version+m[2],text)
    encoded=(b'\xef\xbb\xbf' if data.startswith(b'\xef\xbb\xbf') else b'')+modified.encode('utf-8')
    if encoded!=data:result.append((props,data,encoded))
    launch=safe(root/'.vscode/launch.json')
    if launch.is_file():
        before=launch.read_bytes()
        try:value=json.loads(before.decode('utf-8-sig'))
        except (ValueError,UnicodeError):raise ValueError('VSCode launch.json must be valid JSON for automatic alignment; no files changed') from None
        altered=False
        for config in value.get('configurations',[]):
            args=config.get('args')
            if not isinstance(args,list):continue
            for i in range(len(args)-1):
                if args[i]=='--fml.neoForgeVersion' or args[i]=='--version' and isinstance(args[i+1],str) and re.fullmatch(r'21\.1\.\d+',args[i+1]):
                    if args[i+1]!=version:args[i+1]=version;altered=True
        if altered:result.append((launch,before,(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')))
    return result

def align(root,version):
    root=safe(root);pending=changes(root,version)
    if not pending:return None
    backup=safe(root/'.genesis-backups/dev-version62'/uuid.uuid4().hex)
    backup.mkdir(parents=True)
    for path,before,after in pending:
        saved=backup/path.relative_to(root);saved.parent.mkdir(parents=True,exist_ok=True);saved.write_bytes(before)
    # Both files are checked before mutation, and original bytes are retained for rollback.
    for path,before,after in pending:
        if path.read_bytes()!=before:raise ValueError('Configuration changed during alignment; no files changed')
    written=[]
    try:
        for path,before,after in pending:written.append((path,before));atomic(path,after)
    except BaseException:
        for path,before in reversed(written):atomic(path,before)
        raise
    return backup

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);p.add_argument('--neoforge',required=True);a=p.parse_args()
    backup=align(a.project,a.neoforge)
    print('Dev NeoForge version:',a.neoforge)
    print('Already aligned.' if backup is None else 'Backup: '+str(backup))
    print('Refresh the Gradle project in VSCode. For the first test, gradlew.bat runClient uses the updated SDK directly.')
if __name__=='__main__':
    try:main()
    except (OSError,ValueError) as error:raise SystemExit('Genesis62 version alignment: '+str(error))
