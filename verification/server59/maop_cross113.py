"""Actual Java codec/HMAC bytes consumed by the Python owner policy gate. No Minecraft command dispatch."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,tempfile
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import access88,maop113
from first58.bridge67 import read

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java',required=True);group=parser.add_mutually_exclusive_group(required=True);group.add_argument('--classpath');group.add_argument('--classpath-file',type=Path);args=parser.parse_args();count=0
 def ok(value):
  nonlocal count
  count+=1
  if not value:raise AssertionError('cross113 '+str(count))
 with tempfile.TemporaryDirectory(prefix='Genesis Maop cross113 ') as temp:
  root=Path(temp).resolve()
  classpath=args.classpath if args.classpath is not None else os.pathsep.join(json.loads(args.classpath_file.read_text(encoding='utf-8-sig')))
  def quote(value):
   if any(c in value for c in '\x00\r\n'):raise ValueError('invalid Java argument')
   return '"'+value.replace('\\','\\\\').replace('"','\\"')+'"'
  launch=root/'launch.args';launch.write_text('-cp\n'+quote(classpath)+'\ncom.fixmer.mared.MaopChecks113\n'+quote(str(root))+'\n',encoding='utf-8');launch.chmod(0o600)
  java=subprocess.run([args.java,'@'+str(launch)],capture_output=True,text=True,timeout=20)
  ok(java.returncode==0)
  if java.returncode:raise AssertionError(java.stdout+java.stderr)
  _,subjects=maop113.policy(root/'permissions113.txt');actor='80000000-0000-0000-0000-000000000001';ok(subjects[actor]=='ADMIN');ok(subjects['10000000-0000-0000-0000-000000000001']=='WORKSPACE')
  # rights113 uses the public73 canonical map encoding; bridge body decoding is identical, without MAC.
  import base64
  rights={}
  for line in (root/'rights113').read_text().splitlines():
   key,value=line.split('=',1);rights[key]=set(base64.urlsafe_b64decode(value+'='*((-len(value))%4)).decode().split(','))
  for role,values in maop113.RIGHTS.items():ok(rights[role]==values)
  key=b'k'*32;run='30000000-0000-0000-0000-000000000001';owner=root/'owner';access88.initialize(owner,key,run);packet=read(root/'request113',key);access88.consume(owner,key,run,packet,1700000000000);ok('ACCEPTED' in read(owner/'access88.state',key)['audit'])
  bound=root/'profile/world/genesis/maop113/permissions113.txt';body='GENESIS-MAOP113\nrevision=3\n';bound.write_text(body+'sha256='+hashlib.sha256(body.encode()).hexdigest()+'\n',encoding='ascii')
  try:access88.consume(owner,key,run,packet,1700000000000);raise AssertionError('revoked Java root permit accepted')
  except ValueError as error:ok('REVOKED' in str(error))
 print('Maop113 actual Java-to-Python canonical-role/HMAC/consume/revocation checks passed:',count,'; no Minecraft runtime.')
if __name__=='__main__':main()
