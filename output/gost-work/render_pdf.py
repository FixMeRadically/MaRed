from pathlib import Path
import pypdfium2 as pdfium
from pdf2image import convert_from_path
from PIL import Image,ImageDraw
B=Path(__file__).parent
for p in (B/'готовые_работы').glob('*.pdf'):
 d=pdfium.PdfDocument(p);out=B/'qa'/p.stem;out.mkdir(exist_ok=True)
 thumbs=[]
 pages=convert_from_path(str(p),dpi=100,poppler_path=r'C:\Users\fixme\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\poppler\Library\bin')
 for i,im0 in enumerate(pages):
  page=d[i]
  q=out/f'page-{i+1}.png';im0.save(q)
  im=Image.open(q).convert('RGB');im.thumbnail((390,550));thumbs.append(im)
  text=page.get_textpage().get_text_range(); print(p.stem,i+1,len(text),text[:65].replace('\n',' | '))
 for start in range(0,len(thumbs),6):
  sheet=Image.new('RGB',(1200,1160),'#bbbbbb');draw=ImageDraw.Draw(sheet)
  for j,im in enumerate(thumbs[start:start+6]):
   x=(j%3)*400;y=(j//3)*580;sheet.paste(im,(x,y+22));draw.text((x+5,y+5),str(start+j+1),fill='black')
  sheet.save(out/f'contact-{start//6+1}.jpg')
