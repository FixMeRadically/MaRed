"""External cover for the isolated pilot. Tk main thread; controller runs off the UI thread."""
import queue,threading,time
from .core65 import CorePainter
from .diagnostics63 import PilotFailure

def run_cover(operation,profile=None):
 import tkinter as tk
 from tkinter import messagebox
 root=tk.Tk();root.title('Genesis — перезапуск Minecraft');root.withdraw();root.configure(bg='#12151e');root.overrideredirect(True);root.attributes('-topmost',False)
 canvas=tk.Canvas(root,bg='#12151e',highlightthickness=0);canvas.pack(fill='both',expand=True)
 painter=CorePainter(profile)
 updates=queue.Queue();cancel=threading.Event();done=threading.Event();state={'message':'Genesis','visible':False,'failure':None,'started':time.monotonic()}
 def notify(message):
  acknowledgement=threading.Event() if message in {'COVER','WORLD_READY'} else None;updates.put(('message',message,acknowledgement))
  if acknowledgement and not acknowledgement.wait(4):raise RuntimeError('Restart cover did not acknowledge presentation')
 def ask(message):
  result={};event=threading.Event();updates.put(('ask',message,(event,result)))
  if not event.wait(300):return False
  return result.get('continue',False)
 def execute():
  try:operation(notify,cancel.is_set,ask)
  except Exception as error:
   state['failure']=type(error).__name__
   message=error.display if isinstance(error,PilotFailure) else 'Стенд остановлен ('+type(error).__name__+'). Пришли report.json и controller-events63.json из папки опыта.'
   updates.put(('error',message,None))
  finally:done.set()
 def leave(event=None):
  if messagebox.askyesno('Genesis','Остановить управляемый стенд? Будет запрошено сохранение сервера.',parent=root):cancel.set()
 root.bind('<Escape>',leave);root.protocol('WM_DELETE_WINDOW',leave)
 labels={'COVER':'Подготавливаем перезапуск…','SAVING_SERVER':'Сохраняем серверный мир…','PREPARING_GENERATION':'Готовим новую копию профилей…','STARTING_UPDATED_SERVER':'Запускаем обновлённый сервер…','STARTING_UPDATED_CLIENT':'Запускаем обновлённый клиент…','WORLD_READY':'Готово','DOWNLOADING':'Проверяем и получаем пакеты…'}
 def show():
  # First pilot uses one monitor; exact multi-monitor/window restoration remains separate work.
  root.geometry(f'{root.winfo_screenwidth()}x{root.winfo_screenheight()}+0+0');root.attributes('-topmost',True);root.deiconify();root.lift();root.update_idletasks();state['visible']=True
 def pump():
  try:
   while True:
    kind,message,data=updates.get_nowait()
    if kind=='ask':
     event,result=data;result['continue']=messagebox.askyesno('Genesis',message,parent=root);event.set()
    elif kind=='error':
     root.withdraw();root.attributes('-topmost',False);state['visible']=False;messagebox.showerror('Genesis',message,parent=root)
    else:
     state['message']=labels.get(message,message)
     if message=='COVER':show();root.after(100,data.set)
     if message in {'WORLD_READY','RECONNECT_CANCELLED','CANCELLED','COMPLETE'}:
      root.withdraw();root.attributes('-topmost',False);state['visible']=False
      if data:root.after(150,data.set)
  except queue.Empty:pass
  if state['visible']:
   painter.draw(canvas,root.winfo_width(),root.winfo_height(),time.monotonic()-state['started'],state['message'])
  if done.is_set() and updates.empty():root.destroy();return
  root.after(33,pump)
 threading.Thread(target=execute,daemon=True,name='first58-controller').start();pump();root.mainloop()
 if state['failure']:raise RuntimeError('Pilot failed: '+state['failure'])
