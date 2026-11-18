#!/usr/bin/env python3
"""Read the editor-assist source version and last game log. Does not build or change anything."""
from pathlib import Path
import argparse,json,re

def report(root):
 source=root/'src/main/java/com/fixmer/mared/technology/scripts/ScriptProjectEditor.java';text=source.read_text(encoding='utf-8-sig') if source.is_file() else ''
 log=root/'run/client/logs/latest.log';loaded=False
 if log.is_file():
  with log.open('rb') as stream:
   stream.seek(max(0,log.stat().st_size-4*1024*1024));loaded=b'[Genesis Scripts] editor assist v33 loaded' in stream.read(4*1024*1024)
 return {'report':'script_assist_v33','source_v33':bool(re.search(r'ASSIST_VERSION\s*=\s*33\b',text)),'editor_opened_with_v33':loaded,'helper_sources':{name:(root/f'src/main/java/com/fixmer/mared/gui2/framework/components/editor/{name}.java').is_file() for name in ['CodeFindReplace','CodeSnippets','CodeBracketNavigator','EditorTextEdits']},'hint':'Open Scripts after building and fully restarting the client. A load marker confirms editor creation; test the six actions in a disposable project.'}
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('project',type=Path,nargs='?',default=Path('.'));args=parser.parse_args()
 try:print(json.dumps(report(args.project.resolve()),ensure_ascii=False,indent=2))
 except OSError as e:parser.exit(1,str(e)+'\n')
