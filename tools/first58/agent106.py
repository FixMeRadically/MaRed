"""Only the exact HMAC-pinned Genesis bootstrap option is allowed, not arbitrary agents."""
from .common import safe,sha
from .bridge67 import read
OPTION='-javaagent:.genesis/server104/boot106/agent.jar'
def without_approved_agent(profile,text):
 lines=text.splitlines();found=[line for line in lines if line.strip()==OPTION]
 if not found:return text
 if len(found)!=1:raise ValueError('SERVER106_AGENT_DUPLICATE')
 profile=safe(profile);root=safe(profile/'.genesis/server104')
 with safe(root/'owner74.key').open('rb') as source:key=source.read(33)
 if len(key)!=32:raise ValueError('SERVER106_AGENT_KEY')
 d=read(root/'agent106',key)
 if set(d)!={'schema','profile','sha','coreSha'} or d['schema']!='106' or d['profile']!=str(profile) or sha(root/'boot106/agent.jar',64*1024*1024)!=d['sha'] or sha(root/'boot106/core.jar',64*1024*1024)!=d['coreSha']:raise ValueError('SERVER106_AGENT_BINDING')
 return '\n'.join(line for line in lines if line.strip()!=OPTION)
