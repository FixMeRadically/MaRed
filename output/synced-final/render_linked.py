from pathlib import Path
from pdf2image import convert_from_path
from PIL import Image,ImageDraw
B=Path('output/synced-final/qa-linked')
for p in B.glob('*.pdf'):
 pages=convert_from_path(str(p),dpi=105,poppler_path=r'C:\Users\fixme\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\poppler\Library\bin')
 thumbs=[]
 for i,im in enumerate(pages):
  im.save(B/(p.stem+f'-{i+1}.png'));im.thumbnail((390,550));thumbs.append(im.copy())
 sheet=Image.new('RGB',(1200,1160),'#bbb');draw=ImageDraw.Draw(sheet)
 for j,im in enumerate(thumbs):
  x=(j%3)*400;y=(j//3)*580;sheet.paste(im,(x,y+22));draw.text((x+6,y+5),str(j+1),fill='black')
 sheet.save(B/(p.stem+'-contact.jpg'))
