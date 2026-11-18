"""Fresh private readiness for JOIN-only proofs; never extends an install offer."""
import time
from .dedicated81 import Owner81
from .bridge67 import write

class Owner82(Owner81):
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs);self.serial82=0;write(self.root/'owner82',self.key,dict(schema='82',run=self.s['run']))
 def withdraw82(self):
  try:(self.root/'admission82.local').unlink(missing_ok=True)
  except OSError:pass # Short proof TTL and independent fresh owner/runtime checks still apply.
 def report(self,phase=None,reason=''):
  super().report(phase,reason)
  if not hasattr(self,'serial82'):return
  file=self.root/'admission82.local'
  try:
   state,fresh,error=self.snapshot81();p=self.pending
   if self.stop_requested or self.closing81 or not self.external or not self.commit81 or not p or not fresh or not state or not state['admission'] or not self.target_ready81() or self.phase not in {'ACTIVE_UPDATED','WAITING_THRESHOLD'}:self.withdraw82();return
   self.serial82+=1
   write(file,self.key,dict(schema='82',run=self.s['run'],round=self.commit81['round'],source=str(p['sourceGen']),target=str(p['targetGen']),label=p['label'],release=self.commit81['release'],archive=self.commit81['client'],serverInventory=self.commit81['newInventory'],serial=str(self.serial82),controlSequence=str(state['serial']),at=str(int(time.time()*1000))))
  except Exception:self.withdraw82()
 def run(self):
  try:return super().run()
  finally:self.withdraw82()
