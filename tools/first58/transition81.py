"""Bounded progress diagnostics. No synthetic receipts, cohort changes or elapsed-time authority."""
import time

class Progress81:
 def __init__(self,clock=time.monotonic):
  self.clock=clock;self.started=clock();self.changed=self.started;self.signature=None;self.ack_at=None;self.last_ack='none'
 def acknowledge(self,request):self.ack_at=self.clock();self.last_ack=request
 def view(self,stage,state,fresh,error,target_ready):
  now=self.clock();signature=(stage,state.get('installed',0),state.get('joined',0))
  if signature!=self.signature:self.signature=signature;self.changed=now
  elapsed=max(0,int((now-self.started)*1000));idle=max(0,int((now-self.changed)*1000));total=int(state.get('total',0));installed=int(state.get('installed',0));joined=int(state.get('joined',0))
  return dict(schema=81,stage=stage,elapsedMs=elapsed,idleMs=idle,stalled=idle>=30000,fresh=fresh,controlError=error,total=total,installed=installed,joined=joined,missingInstall=max(0,total-installed),missingJoin=max(0,total-joined),targetReady=target_ready,canContinue=stage in {'WAITING_ALL_INSTALLED','WAITING_THRESHOLD','ACTIVE_UPDATED'},canClose=bool(target_ready and fresh and state.get('admission')),lastAck=self.last_ack,ackAgeMs=-1 if self.ack_at is None else max(0,int((now-self.ack_at)*1000)))

def validate(state,fence,previous,can_admit):
 if state.get('phase')!='COMMITTED' or any(str(state.get(k))!=str(fence[k]) for k in ('round','release','client')):raise ValueError('DEDICATED81_CONTROL_BINDING')
 for name in ('serial','total','needed','installed','joined'):
  if type(state.get(name)) is not int:raise ValueError('DEDICATED81_CONTROL_COUNTS')
 total=int(fence['total'])
 if not 1<=total<=64 or state['total']!=total or state['needed']!=(total+3)//4 or not 0<=state['joined']<=state['installed']<=total or type(state.get('admission')) is not bool:raise ValueError('DEDICATED81_CONTROL_COUNTS')
 if state['admission'] and not can_admit:raise ValueError('DEDICATED81_EARLY_ADMISSION')
 if state['serial']<1 or previous and (state['serial']<=previous['serial'] or state['installed']<previous['installed'] or state['joined']<previous['joined'] or previous['admission'] and not state['admission']):raise ValueError('DEDICATED81_CONTROL_ROLLBACK')
 return state
