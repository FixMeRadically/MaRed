#!/usr/bin/env python3
"""Read window, Windows foreground and real callback handoff diagnostics; changes nothing."""
from pathlib import Path
import argparse,json,re

def report(root):
    log=root/'run/client/logs/latest.log';source=root/'src/main/java/com/fixmer/mared/dev/DevTelemetry.java'
    result={'report':'restart_ui_v32','source_v32':source.is_file() and 'v32 loaded' in source.read_text(encoding='utf-8-sig'),'log_exists':log.is_file()}
    text=''
    if log.is_file():
        with log.open('rb') as stream:
            stream.seek(max(0,log.stat().st_size-4*1024*1024));text=stream.read(4*1024*1024).decode('utf-8',errors='replace')
    markers=list(re.finditer(r'\[Genesis restart\] v(28|30|32) loaded; supervisor=(true|false); transition=(true|false); controller=(28|30|32|legacy)',text))
    loaded=markers[-1] if markers else None
    if loaded:text=text[loaded.start():]  # Never merge an old launch's successful handoff with this one.
    result['loaded']=None if loaded is None else dict(zip(('client_version','supervisor','transition','controller'),loaded.groups()))
    states=re.findall(r'\[Genesis restart\] v(?:28|30|32) window: ([a-z_]{1,48}); hooks=(\d{1,12})',text)
    inputs=re.findall(r'\[Genesis restart\] v(?:30|32) input: ([a-z_]{1,48})[^\r\n]*',text)
    details=re.findall(r'\[Genesis restart\] v32 input: [^\r\n]{0,500}',text)
    result['window_states']=[{'state':state,'hooks':int(hooks)} for state,hooks in states[-30:]]
    result['input_states']=inputs[-30:];result['input_details']=details[-12:]
    result['handoff_settled']=any(x in {'handoff_settled','menu_settled'} for x in inputs)
    result['callbacks_observed']=sorted(set(re.findall(r'callback_observed; kind=(keyboard|mouse)',text)))
    result['input_confirmed']=None  # Native flags and delivered callbacks cannot prove that the player can move.
    if not loaded:result['hint']='No client load marker; build, fully exit the old game/supervisor, then launch again.'
    elif loaded.group(1)!='32':result['hint']='An older client is running; install/build patch 32 and fully restart it.'
    elif loaded.group(2)=='false':result['hint']='Ordinary launch; test an in-game supervised apply/restart.'
    elif 'user_switched_window' in inputs:result['hint']='Focus recovery stopped after a foreground-window change.'
    elif inputs and inputs[-1] in {'timeout','unavailable'}:result['hint']='Handoff did not settle; send this report and latest.log.'
    elif result['handoff_settled']:result['hint']='Flags settled. Test movement/looking without clicking; real callbacks are listed separately.'
    else:result['hint']='Wait for game/cover handoff, then run again.'
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path,nargs='?',default=Path('.'));a=p.parse_args()
    try:print(json.dumps(report(a.project.resolve()),ensure_ascii=False,indent=2))
    except (OSError,ValueError) as error:p.exit(1,'Cannot read restart UI diagnostics: '+str(error)+'\n')
