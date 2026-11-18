from pathlib import Path
import tempfile
import re,subprocess
r=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="mared-audit144-parser-") as temp:
    w=Path(temp)
    p=r/'src/main/java/com/fixmer/mared/commands/engine/MaredScriptParser.java';s=p.read_text();paths=list((r/'technologies/core/src/main/java').rglob('*.java'))+[p,r/'src/test/java/com/fixmer/mared/ParserChecks144.java']
    imports=re.findall(r'import (com.fixmer.mared.commands.[\w.]+);',s)
    for fq in imports+['com.fixmer.mared.commands.engine.MaredScriptCommand']:
     package,name=fq.rsplit('.',1)
     if name=='MaredKeyNames':body='public record ParsedKey(int key){} public static ParsedKey parseAny(String s){return new ParsedKey(0);}'
     elif name=='MaredScriptCommand':body=''
     else:body='public '+name+'(Object... args){}'+(' public enum Mode{DEFAULT,ADD,REPLACE,CLEAR,HOLD,RELEASE}' if name=='MaredBindCommand' else '')
     text='package '+package+';public class '+name+(' extends com.fixmer.mared.commands.engine.MaredScriptCommand' if name not in ['MaredKeyNames','MaredScriptCommand'] else '')+'{'+body+'}'
     dest=w/(name+'.java');dest.write_text(text);paths.append(dest)
    p=subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(w/'classes'),*map(str,paths)],capture_output=True,text=True);(w/'compile.log').write_text(p.stdout+p.stderr);print(p.stderr);p.check_returncode()
    p=subprocess.run(['java','-Xmx128m','-cp',str(w/'classes'),'com.fixmer.mared.ParserChecks144'],capture_output=True,text=True);(w/'results.log').write_text(p.stdout+p.stderr);print(p.stdout,p.stderr);p.check_returncode()
