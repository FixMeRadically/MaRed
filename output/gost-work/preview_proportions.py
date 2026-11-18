from pathlib import Path
from docx import Document
from PIL import Image
B=Path('output/gost-work')
for name,part,img in [('04_Отчёт.docx','/word/media/image1.png','preview04'),('05_Отчёт.docx','/word/media/image2.png','preview05')]:
 f=B/'финальные_работы'/name;d=Document(f)
 for s in d.inline_shapes:
  rel=s._inline.xpath('.//a:blip')[0].get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed')
  if str(d.part.related_parts[rel].partname)==part:
   im=Image.open(B/'qa-compact'/(img+'.png'));s.width=int(s.height*im.width/im.height)
 d.save(f)
