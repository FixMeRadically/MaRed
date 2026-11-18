#!/usr/bin/env python3
"""Review/seal current Windows diagnostic reports. Never grants launch/install authority."""
from pathlib import Path
import argparse,hashlib,json,os,sys,uuid
import native_containment as native
FILES=('build.json','attempt.json','selftest.json','owner-attempt.json','owner-demo.json','java-attempt.json','java-demo.json')
def sha(data):return hashlib.sha256(data).hexdigest()
def canonical(value):return (json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=True)+'\n').encode('ascii')
def identity(value):return isinstance(value,str) and str(uuid.UUID(value))==value and value!='00000000-0000-0000-0000-000000000000'
def bounded(path):
    if path.is_symlink() or not path.is_file():raise ValueError('Ordinary evidence file required')
    with path.open('rb') as source:data=source.read(native.MAX_REPORT+1)
    if len(data)>native.MAX_REPORT:raise ValueError('Oversized evidence file')
    return data
def current(project,output):
    before={name:bounded(output/name) for name in FILES}
    if set(before)!=set(FILES):raise ValueError('Seven bounded ordinary report files are required')
    data={name:native.decode_json(before[name].decode('utf-8')) for name in FILES}
    build=native.validate_build(data['build.json']);host=build['host']
    if not host.startswith('Windows-') or any(x in host.upper() for x in ('TEST','MODEL','MOCK')):raise ValueError('Windows runtime provenance missing')
    c_hash=native.source_fingerprint(project);binary=native.digest(output/'genesis-job43.exe');java_hash=native.java46_hash(project);owner_hash=native.owner_java_hash(project)
    if build['source_sha256']!=c_hash or build['binary_sha256']!=binary:raise ValueError('Build source/binary is stale')
    mark=data['attempt.json'];job=data['selftest.json']
    if set(mark)!={'schema','id','state'} or type(mark['schema']) is not int or mark['schema']!=43 or mark['state']!='COMPLETED' or not identity(mark['id']):raise ValueError('Job attempt incomplete')
    if set(job)!={'schema','attempt','host','completed_unix_ms','source_sha256','binary_sha256','native'} or type(job['schema']) is not int or job['schema']!=43 or job['attempt']!=mark['id'] or job['host']!=host or type(job['completed_unix_ms']) is not int or not 0<job['completed_unix_ms']<2**63 or job['source_sha256']!=c_hash or job['binary_sha256']!=binary or not native.validate_native(job['native']):raise ValueError('Job result failed/stale')
    for prefix,version,expected_java,validate in (('owner',44,owner_hash,native.validate_owner_result),('java',46,java_hash,native.validate_java46)):
        marker=data[prefix+'-attempt.json'];report=data[prefix+'-demo.json']
        if set(marker)!={'schema','state','attempt'} or type(marker['schema']) is not int or marker['schema']!=version or marker['state']!='COMPLETED' or not identity(marker['attempt']):raise ValueError(prefix+' attempt incomplete')
        if set(report)!={'schema','attempt','source_sha256','java_sha256','binary_sha256','result','installAuthority'} or type(report['schema']) is not int or report['schema']!=version or report['attempt']!=marker['attempt'] or report['source_sha256']!=c_hash or report['java_sha256']!=expected_java or report['binary_sha256']!=binary or report['installAuthority'] is not False:raise ValueError(prefix+' result failed/stale')
        validate(report['result'])
    # Re-read every report and executable/source after review: no mixed report set.
    if any(before[name]!=bounded(output/name) for name in FILES) or c_hash!=native.source_fingerprint(project) or binary!=native.digest(output/'genesis-job43.exe') or java_hash!=native.java46_hash(project) or owner_hash!=native.owner_java_hash(project):raise ValueError('Inputs changed during evidence review')
    return {'schema':47,'state':'READY_FOR_REVIEW','runtime_schema':46,'source_sha256':c_hash,'binary_sha256':binary,'java_sha256':java_hash,'owner_java_sha256':owner_hash,'host_sha256':sha(host.encode()),'job_attempt':mark['id'],'owner_attempt':data['owner-attempt.json']['attempt'],'java_attempt':data['java-attempt.json']['attempt'],'compiler':build['compiler'],'files':{name:sha(before[name]) for name in FILES},'installAuthority':False,'productionLaunchAllowed':False,'authorizationVerified':False},before

def review(project,output):
    try:result,_=current(project,output);return result
    except (OSError,ValueError,KeyError,TypeError) as bad:return {'schema':47,'state':'BLOCKED','reason':str(bad),'installAuthority':False,'productionLaunchAllowed':False,'authorizationVerified':False}

def safe_new(path):
    path=path.absolute()
    for ancestor in (path,*path.parents):
        if ancestor.is_symlink() or getattr(ancestor,'is_junction',lambda:False)():raise ValueError('Linked evidence paths refused')
    if path.exists():raise ValueError('Evidence directory must be NEW')
    if not path.parent.is_dir():raise ValueError('Evidence parent missing')
    return path

def write_new(path,data):
    with path.open('xb') as out:out.write(data);out.flush();os.fsync(out.fileno())

def seal(project,output,destination):
    if sys.platform!='win32':raise ValueError('Evidence seal requires Windows; no files changed')
    result,raw=current(project,output);destination=safe_new(destination)
    # Complete marker is written last. A failed operation is never reused/overwritten.
    destination.mkdir(mode=0o700);(destination/'raw').mkdir(mode=0o700)
    for name in FILES:write_new(destination/'raw'/name,raw[name])
    encoded=canonical(result);write_new(destination/'evidence.json',encoded)
    second,raw2=current(project,output)
    if result!=second or raw!=raw2:raise ValueError('Evidence changed during sealing; partial directory is not valid')
    fields={'schema':'47','runtime.schema':'46','state':'READY_FOR_REVIEW','source.sha256':result['source_sha256'],'binary.sha256':result['binary_sha256'],'java.sha256':result['java_sha256'],'owner.java.sha256':result['owner_java_sha256'],'host.sha256':result['host_sha256'],'job.attempt':result['job_attempt'],'owner.attempt':result['owner_attempt'],'java.attempt':result['java_attempt'],'compiler':result['compiler'],'evidence.sha256':sha(encoded),'install.authority':'false','production.launch.allowed':'false','authorization.verified':'false'}
    fields.update({'raw.'+name+'.sha256':result['files'][name] for name in FILES})
    properties=''.join(k+'='+fields[k]+'\n' for k in sorted(fields));write_new(destination/'evidence.properties',properties.encode('ascii'))
    return {'schema':47,'state':'SEALED_FOR_REVIEW','directory':str(destination),'sha256':sha(properties.encode('ascii')),'installAuthority':False,'productionLaunchAllowed':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('project',type=Path);parser.add_argument('action',choices=('status','seal'),nargs='?',default='status');parser.add_argument('--output',type=Path);args=parser.parse_args();project,output=native.paths(args.project)
    if args.action=='status':result=review(project,output)
    else:
        if args.output is None:raise ValueError('seal requires --output NEW_DIRECTORY')
        if sys.platform!='win32':raise ValueError('Evidence seal requires Windows; no files changed')
        with native.owner_lock(output):result=seal(project,output,args.output)
    print(json.dumps(result,ensure_ascii=False,indent=2));return 0 if result['state']!='BLOCKED' else 2
if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,KeyError,TypeError) as bad:raise SystemExit('Native evidence: '+str(bad))
