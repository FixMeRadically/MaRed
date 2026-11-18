from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from lxml import etree as E
import re,json
B=Path(__file__).parent
OUT=B/'финальные_работы';OUT.mkdir(exist_ok=True)
mapping=str.maketrans({'→':'->','⇒':'->','➜':'->','➝':'->','⟶':'->','←':'<-','«':'"','»':'"','“':'"','”':'"','„':'"','‟':'"','‘':'"','’':'"','(': '"',')':'"'})
def normalize(text):
    return text.translate(mapping).replace(';',',')
changes=[]
for f in (B/'исправленные_работы').iterdir():
    if f.suffix not in ['.docx','.xlsx']:continue
    with ZipFile(f) as src,ZipFile(OUT/f.name,'w',ZIP_DEFLATED) as dest:
        for item in src.infolist():
            data=src.read(item.filename)
            if item.filename.endswith('.xml'):
                root=E.fromstring(data);changed=False
                for node in root.iter():
                    if E.QName(node).localname!='t' or not node.text:continue
                    old=node.text
                    # Only visible text nodes are edited. Formula elements,
                    # field instructions, hyperlink targets and references stay intact.
                    new=normalize(old)
                    if old!=new:
                        node.text=new;changed=True
                        changes.append({'file':f.name,'part':item.filename,'old':old,'new':new})
                if changed:data=E.tostring(root,xml_declaration=True,encoding='UTF-8',standalone=True)
            dest.writestr(item,data)
(B/'qa-compact/punctuation-changes.json').write_text(json.dumps(changes,ensure_ascii=False,indent=2),encoding='utf8')
print('Changed visible text strings:',len(changes))
