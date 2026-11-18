"""Compact native progress cover for a managed Minecraft restart."""

class CorePainter:
 def __init__(self,profile=None):
  self.last_frame=None

 def draw(self,canvas,w,h,seconds,message):
  if w<100 or h<100:return
  key=(w,h,message)
  if self.last_frame!=key:
   self.last_frame=key
   canvas.delete('all')
   canvas.configure(bg='#0e1219')
   width=min(580,w-40)
   left=(w-width)/2
   top=max(18,(h-196)/2)
   canvas.create_rectangle(left,top,left+width,top+196,fill='#171e29',outline='#303c4d')
   canvas.create_text(left+24,top+24,text='Обновление Minecraft',anchor='nw',
                      font=('TkDefaultFont',16,'bold'),fill='#f0f3f8')
   canvas.create_text(left+24,top+70,text=message,anchor='nw',width=int(width-48),
                      font=('TkDefaultFont',11),fill='#c5d2e2')
   canvas.create_line(left+24,top+147,left+width-24,top+147,fill='#304257',width=3)
   canvas.create_line(left+24,top+147,left+24,top+147,fill='#6bd4cb',width=3,tags='activity')
   canvas.create_text(left+24,top+167,text='Esc — свернуть',anchor='nw',
                      font=('TkDefaultFont',9),fill='#93a6bb')
  width=min(580,w-40)
  left=(w-width)/2+24
  y=max(18,(h-196)/2)+147
  travel=max(0,width-104)
  phase=(seconds%2)/2
  x=left+travel*(1-abs(2*phase-1))
  canvas.coords('activity',x,y,x+56,y)
