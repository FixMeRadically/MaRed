"""Owner85 with explicit88 remote ACL capability. No changes to world/save/COMMIT gates."""
import zipfile
from .dedicated85 import Owner85
from . import access88
class Owner88(Owner85):
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs)
  profile=self.root/('generations/server-'+str(self.s.get('initialGeneration',0)));adapters=0
  for jar in (profile/'mods').glob('*.jar'):
   with zipfile.ZipFile(jar) as z:
    if 'com/fixmer/mared/Mared.class' in z.namelist():
     if 'com/fixmer/mared/technology/server/remote/ServerAccessPayloads88.class' not in z.namelist():raise ValueError('ACCESS88_INSTALL_SERVER_BOOTSTRAP')
     adapters+=1
  if adapters!=1:raise ValueError('ACCESS88_INSTALL_SERVER_BOOTSTRAP')
  access88.initialize(self.root,self.key,self.s['run'])
