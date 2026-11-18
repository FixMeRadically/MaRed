"""Differential tests against the real JDK launcher, not a second Python parser."""
from pathlib import Path
import base64, itertools, subprocess, sys, tempfile
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.launch62 import argfile_tokens
with tempfile.TemporaryDirectory(prefix='argfile165-') as directory:
 root=Path(directory)
 source=root/'Args165.java'
 source.write_text('import java.util.Base64; import java.nio.charset.StandardCharsets; public class Args165 {public static void main(String[] args){for(String a:args)System.out.println(Base64.getEncoder().encodeToString(a.getBytes(StandardCharsets.UTF_8)));}}')
 subprocess.run(['java','com.sun.tools.javac.Main',str(source)],check=True,capture_output=True)
 cases=['a\u00a0b','a\x0bb','"abc"x#comment\n next','"abc\\\n\n def"']
 pieces=['abc','"abc"','abc"def"','"abc"x','""',"'abc'",'"C:\\\\path with spaces"','"Я"']
 for first,sep,last in itertools.product(pieces,[' ','\t','# comment\n ','\r\n'],['next','"next value"']):cases.append(first+sep+last)
 errors=[]
 for case in cases:
  path=root/'launch.args';path.write_text('-cp\n"'+str(root).replace('\\','\\\\')+'"\nArgs165\n'+case,encoding='utf8')
  r=subprocess.run(['java','@'+str(path)],capture_output=True,check=True,timeout=10)
  expected=[base64.b64decode(line).decode() for line in r.stdout.splitlines()]
  actual=argfile_tokens(case)
  if actual!=expected:errors.append((case,actual,expected))
 for case,actual,expected in errors:print(repr(case),repr(actual),repr(expected))
 print('JDK differential cases:',len(cases),'mismatches:',len(errors))
 if errors:raise SystemExit(1)
