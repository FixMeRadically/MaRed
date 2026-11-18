"""Java argfile grammar and frozen MOD_CLASSES snapshots for a Windows dev launch.
No shell evaluation; no debugger reuse; original build outputs are never written.
"""
from pathlib import Path
import hashlib,os,re,shutil
from .common import safe,sha,clone,atomic

MAX_FILES=30000
MAX_BYTES=256*1024*1024
ROOT='.genesis-dev62'

def argfile_tokens(text):
    if len(text.encode('utf-8'))>1024*1024:raise ValueError('Java argument file is too large')
    out=[];word=[];quote=None;started=False;i=0
    while i<len(text):
        c=text[i];i+=1
        if quote:
            if c==quote:quote=None;continue
            if c=='\\' and i<len(text):
                c=text[i];i+=1
                if c in '\r\n':
                    if c=='\r' and i<len(text) and text[i]=='\n':i+=1
                    while i<len(text) and text[i] in ' \t\f':i+=1
                    continue
                c={'n':'\n','r':'\r','t':'\t','f':'\f'}.get(c,c)
            word.append(c);continue
        if c=='#':
            word=[];started=False
            while i<len(text) and text[i] not in '\r\n':i+=1
            continue
        if c in ('"',"'"):quote=c;started=True;continue
        if c.isspace():
            if started:out.append(''.join(word));word=[];started=False
            continue
        word.append(c);started=True
    if quote:raise ValueError('Unterminated quote in Java argument file')
    if started:out.append(''.join(word))
    if len(out)>256 or any('\x00' in value for value in out):raise ValueError('Java argument file limit')
    return out

def expand_argfiles(argv,cwd=None):
    out=[argv[0]]
    for value in argv[1:]:
        if value.startswith('@@'):raise ValueError('Escaped @ launch arguments are not supported')
        if not value.startswith('@'):out.append(value);continue
        path=Path(value[1:])
        if not path.is_absolute():
            if cwd is None:raise ValueError('Relative Java argument file requires the running JVM working directory')
            path=Path(cwd)/path
        path=safe(path)
        if not path.is_file() or path.stat().st_size>1024*1024:raise ValueError('Java argument file is missing or too large')
        values=argfile_tokens(path.read_text(encoding='utf-8-sig'))
        if any(v.startswith('@') for v in values):raise ValueError('Nested Java argument files are not supported')
        out.extend(values)
    if len(out)>256:raise ValueError('Too many expanded launch arguments')
    return [v for v in out if v!='-Xdebug' and not v.startswith('-agentlib:jdwp=')]

def dev_groups(value):
    if not isinstance(value,str) or len(value)>32768:raise ValueError('Invalid MOD_CLASSES data')
    groups={}
    for item in value.split(';'):
        if not item:continue
        if item.count('%%')!=1:raise ValueError('Unsupported MOD_CLASSES format')
        group,path=item.split('%%',1)
        if not re.fullmatch('[A-Za-z][A-Za-z0-9_.-]{0,60}',group):raise ValueError('Invalid MOD_CLASSES group')
        p=Path(path)
        if not p.is_absolute() or not safe(p).is_dir():raise ValueError('Dev output directory is missing')
        groups.setdefault(group,[])
        if str(safe(p)) not in groups[group]:groups[group].append(str(safe(p)))
    if len(groups)>16 or sum(map(len,groups.values()))>32:raise ValueError('Too many dev output directories')
    return groups

def files(root):
    root=safe(root);out={};total=0;nodes=0
    for directory,dirs,names in os.walk(root,followlinks=False):
        directory=safe(directory)
        nodes+=1+len(names)+len(dirs)
        if nodes>120000:raise ValueError('Dev snapshot directory limit')
        if len(directory.relative_to(root).parts)>32:raise ValueError('Dev snapshot depth limit')
        for name in dirs:safe(directory/name)
        for name in names:
            path=safe(directory/name)
            if not path.is_file():raise ValueError('Special file in dev output')
            relative=path.relative_to(root).as_posix()
            if '\n' in relative or '\r' in relative or '=' in relative:raise ValueError('Unsupported dev resource filename')
            total+=path.stat().st_size
            if len(out)>=MAX_FILES or total>MAX_BYTES:raise ValueError('Dev snapshot exceeds 30000 files / 256 MiB')
            out[relative]=sha(path,64*1024*1024)
    return out

def tree_hash(root):
    entries=files(root)
    return hashlib.sha256(''.join(k+'='+v+'\n' for k,v in sorted(entries.items())).encode('utf-8')).hexdigest()

def snapshot(spec,root,core):
    """Copy all mod output roots, merge identical duplicates, pin core; return rewritten argv."""
    root=safe(root);root.mkdir()
    groups=dev_groups(spec['devModClasses'])
    if not groups:raise ValueError('forgeclientdev requires exported MOD_CLASSES')
    remap={};roots=[];mared_found=False
    for group,sources in sorted(groups.items()):
        target=root/'mods'/group;target.mkdir(parents=True)
        merged={}
        for source in sources:
            source=safe(source);before=files(source)
            for name,digest in before.items():
                if name in merged:
                    if merged[name]!=digest:raise ValueError('Conflicting compiled resources in MOD_CLASSES')
                    continue
                dest=target/name;dest.parent.mkdir(parents=True,exist_ok=True)
                with (source/name).open('rb') as src,dest.open('xb') as out:shutil.copyfileobj(src,out,1024*1024)
                if sha(dest,64*1024*1024)!=digest:raise ValueError('Dev output changed during copy')
                merged[name]=digest
            if files(source)!=before:raise ValueError('Dev output changed during copy; stop compilation and retry')
            remap[str(source)]='{profile}/'+ROOT+'/mods/'+group
        if (target/'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchClient58.class').is_file():mared_found=True
        # Keep a nested JarJar copy consistent with the controller's newly built core.
        for nested in target.glob('META-INF/jarjar/genesis-core-*.jar'):
            shutil.copyfile(core,nested)
        roots.append((group,'mods/'+group))
    if not mared_found:raise ValueError('Dev snapshot has no compiled MaRed client adapter')
    shutil.copyfile(core,root/'core.jar')
    atomic(root/'roots.v62',''.join(k+'='+v+'\n' for k,v in roots).encode('ascii'),new=True)
    def rewrite(path):
        text=str(path);normalized=text.replace('\\','/')
        for source,target in remap.items():
            if os.path.normcase(os.path.normpath(text))==os.path.normcase(os.path.normpath(source)):return target
        if re.search(r'(^|/)genesis-core-[^/]+\.jar$',normalized) or normalized.endswith('/technologies/core/build/classes/java/main'):
            return '{profile}/'+ROOT+'/core.jar'
        if not Path(text).is_absolute():raise ValueError('Dev classpath contains a relative entry')
        if not safe(text).exists():raise ValueError('Dev classpath entry is missing')
        return text
    argv=list(spec['clientCommand']);legacy=None
    for i,value in enumerate(argv):
        if value.startswith('-DlegacyClassPath.file='):
            path=safe(value.split('=',1)[1])
            if path.stat().st_size>1024*1024:raise ValueError('Dev legacy classpath file limit')
            entries=path.read_text(encoding='utf-8-sig').strip().split(';')
            legacy=[rewrite(p) for p in entries if p]
            argv[i]='-DlegacyClassPath.file={profile}/client-legacy62.txt'
        if value in {'-cp','-classpath','--class-path','-p','--module-path'}:
            if i+1>=len(argv):raise ValueError('Incomplete dev classpath argument')
            argv[i+1]=';'.join(rewrite(p) for p in argv[i+1].split(';') if p)
    # Bound the aggregate, not just each source output directory.
    digest=tree_hash(root)
    return {'hash':digest,'roots':roots,'argv':argv,'legacy':legacy}

def verify_profile(profile,expected):
    if tree_hash(Path(profile)/ROOT)!=expected:raise ValueError('Frozen dev snapshot changed')

def runtime(profile,spec):
    profile=safe(profile);expected=spec.get('devSnapshot')
    if not expected:return {}
    verify_profile(profile,expected['hash'])
    if expected.get('legacy') is not None:
        text=';'.join(v.replace('{profile}',str(profile)) for v in expected['legacy'])
        atomic(profile/'client-legacy62.txt',text.encode('utf-8'))
    return {'MOD_CLASSES':';'.join(group+'%%'+str(profile/ROOT/relative) for group,relative in expected['roots'])}

def wrap_long(argv,profile):
    import subprocess
    if len(subprocess.list2cmdline(argv))<28000:return argv
    if any(v.startswith('@') for v in argv[1:]):raise ValueError('Cannot wrap a nested Java argument file')
    def quote(v):return '"'+v.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','\\r').replace('\t','\\t')+'"'
    path=safe(Path(profile)/'client-launch62.args')
    atomic(path,('\n'.join(quote(v) for v in argv[1:])+'\n').encode('utf-8'))
    return [argv[0],'@'+str(path)]
