"""Server owner78: HTTPS is an owned thread, never a client-launched detached process."""
from .dedicated77 import Owner77
from .https78 import Manager78
from .bridge67 import write
from .dedicated75 import independent_owner

class Owner78(Owner77):
 https_factory=None
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs);self.https78=None
 def health(self,check_control=True):
  super().health(check_control)
  if self.https78:self.https78.tick()
 def run(self):
  if (self.root/'started74').exists():raise ValueError('DEDICATED77_USE_RECOVERY')
  independent_owner()
  write(self.root/'owner78',self.key,dict(schema='78',run=self.s['run']));self.https78=(self.https_factory or Manager78)(self.project,self.root,self.s['java'])
  try:
   self.https78.tick(True);return super().run()
  finally:self.https78.close()
