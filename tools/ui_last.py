#!/usr/bin/env python3
"""Read-only usability patch marker; does not claim visual or input verification."""
from pathlib import Path
import argparse,json,re
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('project',type=Path,nargs='?',default=Path('.'));args=parser.parse_args();root=args.project.resolve()
    source=root/'src/main/java/com/fixmer/mared/technology/scripts/ScriptProjectEditor.java'
    log=root/'run/client/logs/latest.log'
    source_ok=source.is_file() and re.search(r'UX_VERSION\s*=\s*34\s*;',source.read_text(encoding='utf-8')) is not None
    tail=''
    if log.is_file():
        with log.open('rb') as f:
            f.seek(max(0,log.stat().st_size-2*1024*1024));tail=f.read().decode('utf-8',errors='replace')
    print(json.dumps({'report':'ui_usability_v34','source_v34':source_ok,'log_exists':log.is_file(),'editor_opened_with_v34':'[Genesis UI] usability v34 loaded' in tail,'hint':'The marker confirms loaded code, not visual quality or physical input. Open Scripts after a full rebuild/relaunch.'},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
