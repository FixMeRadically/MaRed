"""Real helper child JVMs with controlled contaminated parent environment; no game process."""
from pathlib import Path
import argparse,json,os,subprocess,tempfile

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classpath-file',type=Path,required=True);a=p.parse_args();cp=os.pathsep.join(json.loads(a.classpath_file.read_text(encoding='utf-8-sig')))
 def quote(v):
  if any(c in v for c in '\r\n\0'):raise ValueError('Invalid Java argument')
  return '"'+v.replace('\\','\\\\').replace('"','\\"')+'"'
 with tempfile.TemporaryDirectory(prefix='Genesis integration116 ') as temp:
  f=Path(temp)/'test.args';f.write_text('-cp\n'+quote(cp)+'\ncom.fixmer.mared.IntegrationChecks116\n',encoding='utf-8');env=dict(os.environ);env.update(GENESIS_TEST116='must-not-pass',JAVA_TOOL_OPTIONS='-Dserver116.parent=true',PYTHONPATH='must-not-pass',PYTHONHOME='must-not-pass',PYTHONSTARTUP='must-not-pass',PYTHONINSPECT='must-not-pass')
  for k in ['JDK_JAVA_OPTIONS','_JAVA_OPTIONS']:env.pop(k,None)
  result=subprocess.run([a.java,'@'+str(f)],env=env,capture_output=True,text=True,timeout=45)
  if result.returncode:raise AssertionError(result.stdout+result.stderr)
  print(result.stdout.strip());print('Integration116 controlled parent -> real child JVM isolation passed; no Minecraft.')
if __name__=='__main__':main()
