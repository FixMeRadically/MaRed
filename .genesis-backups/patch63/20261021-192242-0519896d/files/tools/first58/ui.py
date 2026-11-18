"""External cover for the isolated pilot. Tk main thread; controller runs off the UI thread."""
import math,queue,threading,time

def run_cover(operation):
 import tkinter as tk
 from tkinter import messagebox
 root=tk.Tk();root.withdraw();root.configure(bg='#12151e');root.overrideredirect(True);root.attributes('-topmost',True)
 canvas=tk.Canvas(root,bg='#12151e',highlightthickness=0);canvas.pack(fill='both',expand=True)
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
  except Exception as error:state['failure']=type(error).__name__;updates.put(('error','Стенд остановлен. Смотри report.json и журналы. Исходные профили не изменены.',None))
  finally:done.set()
 def leave(event=None):
  if messagebox.askyesno('Genesis','Остановить управляемый стенд? Будет запрошено сохранение сервера.',parent=root):cancel.set()
 root.bind('<Escape>',leave);root.protocol('WM_DELETE_WINDOW',leave)
 labels={'COVER':'Подготавливаем перезапуск…','SAVING_SERVER':'Сохраняем серверный мир…','PREPARING_GENERATION':'Готовим новую копию профилей…','STARTING_UPDATED_SERVER':'Запускаем обновлённый сервер…','STARTING_UPDATED_CLIENT':'Запускаем обновлённый клиент…','WORLD_READY':'Готово','DOWNLOADING':'Проверяем и получаем пакеты…'}
 def show():
  # First pilot uses one monitor; exact multi-monitor/window restoration remains separate work.
  root.geometry(f'{root.winfo_screenwidth()}x{root.winfo_screenheight()}+0+0');root.deiconify();root.lift();root.update_idletasks();state['visible']=True
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
     if message in {'WORLD_READY','RECONNECT_CANCELLED','CANCELLED','COMPLETE'}:
      root.withdraw();state['visible']=False
      if data:root.after(150,data.set)
  except queue.Empty:pass
  if state['visible']:
   canvas.delete('all');w=root.winfo_width();h=root.winfo_height();angle=(time.monotonic()-state['started'])*1.7
   for ring,size in [(0,42),(1,65),(2,90)]:
    points=[]
    for i in range(4):
     a=angle*(1 if ring%2==0 else -1)+i*math.pi/2;points.extend((w/2+math.cos(a)*size,h/2-30+math.sin(a)*size))
    canvas.create_polygon(points,fill='',outline=('#b6a2ff','#7160b8','#39384f')[ring],width=3-ring)
   canvas.create_text(w/2,h/2+90,text='GENESIS',font=('Fixedsys',26),fill='#eee8ff')
   canvas.create_text(w/2,h/2+135,text=state['message'],font=('TkFixedFont',13),fill='#b7bad0',width=max(200,w-80))
   canvas.create_text(w/2,h-40,text='Esc — остановить стенд',font=('TkFixedFont',10),fill='#73788e')
  if done.is_set() and updates.empty():root.destroy();return
  root.after(33,pump)
 threading.Thread(target=execute,daemon=True,name='first58-controller').start();pump();root.mainloop()
 if state['failure']:raise RuntimeError('Pilot failed: '+state['failure'])
