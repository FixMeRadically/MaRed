from pathlib import Path
import pdfplumber
for name in ['03_Реферат','01_Отчёт']:
 with pdfplumber.open(Path('output/gost-work/готовые_работы')/(name+'.pdf')) as d:
  for i in [0,1,6]:
   p=d.pages[i]
   print(name,i+1,[(round(x['x0'],1),round(x['top'],1),round(x['x1'],1),round(x['bottom'],1)) for x in p.rects if x['height']>500 or x['width']>400])
