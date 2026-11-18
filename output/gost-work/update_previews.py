from pathlib import Path
from pdf2image import convert_from_path
from PIL import Image,ImageChops
from zipfile import ZipFile,ZIP_DEFLATED
B=Path('output/gost-work')
for name in ['preview04','preview05']:
 im=convert_from_path(str(B/'qa-compact'/(name+'.pdf')),dpi=180,poppler_path=r'C:\Users\fixme\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\poppler\Library\bin')[0].convert('RGB')
 # Crop only renderer page whitespace, like a range screenshot.
 bbox=ImageChops.difference(im,Image.new('RGB',im.size,'white')).getbbox()
 im=im.crop(bbox);im.save(B/'qa-compact'/(name+'.png'))
for name,part,img in [('04_Отчёт.docx','word/media/image1.png','preview04'),('05_Отчёт.docx','word/media/image2.png','preview05')]:
 f=B/'финальные_работы'/name
 with ZipFile(f) as z:items=[(i,z.read(i.filename)) for i in z.infolist()]
 with ZipFile(f,'w',ZIP_DEFLATED) as z:
  for i,data in items:z.writestr(i,(B/'qa-compact'/(img+'.png')).read_bytes() if i.filename==part else data)
 print(name,'updated screenshot',part)
