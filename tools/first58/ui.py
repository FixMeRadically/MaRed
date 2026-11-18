"""External cover for the isolated pilot. Tk main thread; controller runs off the UI thread."""
import queue,threading,time
from .core65 import CorePainter
from .diagnostics63 import PilotFailure

def run_cover(operation,profile=None,stop_message=None):
 import tkinter as tk
 from tkinter import messagebox
 root=tk.Tk();root.title('Genesis');root.withdraw();root.configure(bg='#050510')
 canvas=tk.Canvas(root,bg='#050510',highlightthickness=0);canvas.pack(fill='both',expand=True)
 painter=CorePainter(profile)
 updates=queue.Queue();cancel=threading.Event();done=threading.Event();state={'message':'Genesis','visible':False,'failure':None,'started':time.monotonic()}
 def notify(message):
  acknowledgement=threading.Event() if message in {'COVER','WORLD_READY','BASELINE_READY'} else None;updates.put(('message',message,acknowledgement))
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
  if messagebox.askyesno('Genesis',stop_message or 'Остановить управляемый стенд? Будет запрошено сохранение сервера.',parent=root):cancel.set()
 def minimize(event=None):
  root.iconify()
  return 'break'
 if stop_message is None:
  root.bind('<Escape>',leave);root.protocol('WM_DELETE_WINDOW',leave)
 else:
  root.bind('<Escape>',minimize);root.protocol('WM_DELETE_WINDOW',minimize)
 labels={'RETURNING_PREVIOUS_CLIENT':'Возвращаем прежнюю версию клиента…','COVER':'Подготавливаем перезапуск…','SAVING_SERVER':'Сохраняем серверный мир…','PREPARING_GENERATION':'Готовим новую копию профилей…','STARTING_UPDATED_SERVER':'Запускаем обновлённый сервер…','STARTING_UPDATED_CLIENT':'Запускаем обновлённый клиент…','WORLD_READY':'Готово','DOWNLOADING':'Проверяем и получаем пакеты…'}
 def show():
  if state['visible']:return
  width=min(760,root.winfo_screenwidth()-48)
  height=min(480,root.winfo_screenheight()-96)
  x=(root.winfo_screenwidth()-width)//2
  y=(root.winfo_screenheight()-height)//2
  root.geometry(f'{width}x{height}+{x}+{y}')
  root.deiconify();state['visible']=True
 def pump():
  try:
   while True:
    kind,message,data=updates.get_nowait()
    if kind=='ask':
     event,result=data;result['continue']=messagebox.askyesno('Genesis',message,parent=root);event.set()
    elif kind=='error':
     root.withdraw();state['visible']=False;messagebox.showerror('Genesis',message,parent=root)
    else:
     state['message']=labels.get(message,message)
     if message=='COVER':show();root.after(100,data.set)
     if message in {'WORLD_READY','BASELINE_READY','RECONNECT_CANCELLED','CANCELLED','COMPLETE'}:
      root.withdraw();state['visible']=False
      if data:root.after(150,data.set)
  except queue.Empty:pass
  if state['visible'] and root.state() in {'normal','zoomed'}:
   painter.draw(canvas,canvas.winfo_width(),canvas.winfo_height(),time.monotonic()-state['started'],state['message'])
  if done.is_set() and updates.empty():root.destroy();return
  root.after(16 if state['visible'] and root.state() in {'normal','zoomed'} else 50,pump)
 threading.Thread(target=execute,daemon=True,name='first58-controller').start();pump();root.mainloop()
 if state['failure']:raise RuntimeError('Pilot failed: '+state['failure'])
