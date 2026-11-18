#!/usr/bin/env python3
"""Local operator launcher for the standalone server supervisor (patch36). No game updates."""
from pathlib import Path
import argparse,os,shutil,subprocess,sys,tempfile,uuid

def java_home(value):
    if value:
        p=Path(value.strip().strip('"')).expanduser().resolve()
        if p.is_file():p=p.parent
        if p.name.lower()=='bin':p=p.parent
    elif os.environ.get('JAVA_HOME'):p=Path(os.environ['JAVA_HOME'].strip('"')).resolve()
    else:
        found=shutil.which('java')
        if not found:raise ValueError('Java not found. Use --java-home with your JDK 21 folder.')
        p=Path(found).resolve().parent.parent
    exe=p/'bin'/('java.exe' if os.name=='nt' else 'java')
    if not exe.is_file():raise ValueError('Java executable not found in selected Java home.')
    return p,exe

def prop(value):
    value=str(value)
    if any(c in value for c in ['\0','\n','\r']):raise ValueError('Newlines and NUL are not permitted in paths.')
    return value.replace('\\','\\\\').replace('=','\\=').replace(':','\\:')

def configure(args,home):
    if not args.server_dir:raise ValueError('configure requires --server-dir.')
    server=Path(args.server_dir).expanduser().resolve()
    if not server.is_dir():raise ValueError('Server folder does not exist.')
    if args.args_file:
        arguments=Path(args.args_file).expanduser().resolve()
        if not arguments.is_file():raise ValueError('NeoForge argument file does not exist.')
    else:
        name='win_args.txt' if os.name=='nt' else 'unix_args.txt'
        candidates=sorted((server/'libraries/net/neoforged/neoforge').glob('*/'+name))
        if len(candidates)!=1:raise ValueError('Expected one installed NeoForge argument file; provide --args-file explicitly.')
        arguments=candidates[0].resolve()
    output=Path(args.output).expanduser().resolve() if args.output else server/'genesis-supervisor.properties'
    if not output.parent.is_dir():raise ValueError('Configuration output parent folder does not exist.')
    values={'name':'Genesis dedicated server','workDir':server,'stateDir':server/'.genesis-server','javaHome':home,'control.port':'0','start.onBoot':'true','restart.onCrash':'false','restart.maxAttempts':'3','restart.delayMs':'1000','stop.graceMs':'60000','stop.forceGraceMs':'3000','orphan.force':'false','log.maxBytes':str(16*1024*1024)}
    launch=[]
    if (server/'user_jvm_args.txt').is_file():launch.append('@'+str(server/'user_jvm_args.txt'))
    launch.extend(['@'+str(arguments),'nogui'])
    values.update({'arg.'+str(i):v for i,v in enumerate(launch)})
    with output.open('x',encoding='utf-8',newline='\n') as f:
        f.write('# Genesis server process supervisor. Trusted local administrator configuration.\n')
        for key,value in values.items():f.write(key+'='+prop(value)+'\n')
    print('Configuration created:',output)
    print('No process was started. Review the configuration, then run it explicitly.')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project',type=Path)
    parser.add_argument('command',choices=['configure','demo','run','status','logs','token','start','stop','restart','shutdown','force-stop','resolve','checkpoint','prepare-stop','prepare-restart','cancel-prepare'])
    parser.add_argument('target',nargs='?',help='Configuration path, or NEW demo directory')
    parser.add_argument('--java-home');parser.add_argument('--server-dir');parser.add_argument('--args-file');parser.add_argument('--output');parser.add_argument('--confirm',action='store_true')
    args=parser.parse_args();project=args.project.resolve();home,java=java_home(args.java_home)
    if args.command=='configure':configure(args,home);return 0
    classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/process/ServerSupervisorCli.class').is_file():raise ValueError('Supervisor classes are missing. Run gradlew.bat :genesis-core:classes in the project first.')
    resource=project/'technologies/core/build/resources/main'
    if not (resource/'com/fixmer/genesis/server-console/index.html').is_file():resource=project/'technologies/core/src/main/resources'
    classpath=os.pathsep.join([str(classes),str(resource)])
    if args.command=='demo':target=Path(args.target).resolve() if args.target else Path(tempfile.gettempdir())/('Genesis-server-demo-36-'+uuid.uuid4().hex[:12])
    else:
        if not args.target:raise ValueError('This command requires a supervisor configuration path.')
        target=Path(args.target).expanduser().resolve()
    cmd=[str(java),'-cp',classpath,'com.fixmer.genesis.technology.server.process.ServerSupervisorCli',args.command,str(target)]
    if args.confirm:cmd.append('--confirm')
    try:return subprocess.call(cmd,cwd=project)
    except KeyboardInterrupt:
        print('\nStopped waiting for supervisor. Guardian handles graceful server shutdown; check status before starting another instance.',file=sys.stderr);return 130

if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError) as bad:raise SystemExit('Server supervisor: '+str(bad))
