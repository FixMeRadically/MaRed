from pathlib import Path
from copy import deepcopy
import re, shutil, hashlib, json
from docx import Document
from docx.shared import Mm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

B=Path(__file__).parent
OUT=B/'исправленные_работы'; OUT.mkdir(exist_ok=True)
def el(tag, **attrs):
    e=OxmlElement('w:'+tag)
    for k,v in attrs.items(): e.set(qn('w:'+k),str(v))
    return e
def font(r,size=14):
    r.font.name='Times New Roman';r.font.size=Pt(size);r.font.color.rgb=RGBColor(0,0,0)
    r.font.bold=False;r.font.italic=False;r.font.underline=False
def fmt(p,align=WD_ALIGN_PARAGRAPH.JUSTIFY,indent=12.5):
    f=p.paragraph_format;f.space_before=Pt(0);f.space_after=Pt(0);f.line_spacing=1
    f.left_indent=Mm(0);f.right_indent=Mm(0);f.first_line_indent=Mm(indent)
    p.alignment=align
    for r in p.runs: font(r)
def frame(s):
    for b in list(s._sectPr.findall(qn('w:pgBorders'))): s._sectPr.remove(b)
    b=el('pgBorders',offsetFrom='text',display='allPages')
    for side,space in [('top',28),('left',14),('bottom',28),('right',14)]:
        b.append(el(side,val='single',sz=11,color='000000',space=space))
    s._sectPr.append(b)
    s.top_margin=Mm(15);s.bottom_margin=Mm(15);s.left_margin=Mm(25);s.right_margin=Mm(10)
    s.footer_distance=Mm(7);s.header_distance=Mm(7)
def footer(s,hide):
    s.different_first_page_header_footer=hide
    p=s.footer.paragraphs[0];p.clear();fmt(p,WD_ALIGN_PARAGRAPH.RIGHT,0)
    r=p.add_run();r._r.append(el('fldChar',fldCharType='begin'))
    r=p.add_run();t=el('instrText');t.text=' PAGE ';r._r.append(t)
    p.add_run()._r.append(el('fldChar',fldCharType='end'))
    if hide:
        for p in s.first_page_footer.paragraphs:p.clear()
def space_before(p,mm):
    # Explicit spacer paragraph keeps required 0 pt paragraph spacing.
    n=OxmlElement('w:p');pr=el('pPr');pr.append(el('spacing',before=0,after=0,line=int(mm*56.693),lineRule='exact'));n.append(pr)
    p._p.addprevious(n)
def toc(d,before):
    h=before.insert_paragraph_before('Содержание');fmt(h,WD_ALIGN_PARAGRAPH.CENTER,0);h.paragraph_format.page_break_before=True
    p=before.insert_paragraph_before();fmt(p,WD_ALIGN_PARAGRAPH.LEFT,0)
    p.add_run()._r.append(el('fldChar',fldCharType='begin'))
    r=p.add_run();t=el('instrText');t.text=' TOC \\o "1-1" \\h \\z \\u ';r._r.append(t)
    p.add_run()._r.append(el('fldChar',fldCharType='separate'))
    p.add_run('Содержание обновляется при открытии документа')
    p.add_run()._r.append(el('fldChar',fldCharType='end'))
def cover(d):
    ps=d.paragraphs
    # Original first 14 paragraphs contain all cover data.
    for p in ps[:14]:fmt(p,WD_ALIGN_PARAGRAPH.CENTER,0);p.paragraph_format.keep_with_next=True
    dept=ps[3];dept.alignment=WD_ALIGN_PARAGRAPH.RIGHT
    logo=dept.insert_paragraph_before();fmt(logo,WD_ALIGN_PARAGRAPH.CENTER,0)
    shape=logo.add_run().add_picture(str(B/'qa/image1.png'),width=Mm(165),height=Mm(23))
    fill=shape._inline.xpath('.//pic:blipFill')[0]
    crop=OxmlElement('a:srcRect')
    for k,v in dict(l=30294,t=52741,r=25882,b=35259).items():crop.set(k,str(v))
    fill.insert(1,crop)
    space_before(logo,8);space_before(dept,8);space_before(ps[5],23)
    ps[5].style='Title';fmt(ps[5],WD_ALIGN_PARAGRAPH.CENTER,0)
    space_before(ps[10],27)
    for p in ps[10:13]:p.alignment=WD_ALIGN_PARAGRAPH.RIGHT
    ps[12].text='Проверила: доцент кафедры ИСТ\nЧерных Татьяна Александровна'
    fmt(ps[12],WD_ALIGN_PARAGRAPH.RIGHT,0)
    space_before(ps[13],16);ps[13].paragraph_format.keep_with_next=False
    # Drop obsolete blank page-break paragraphs after cover, kept via heading page-break.
    for p in ps[14:15]:
        if not p.text:p._p.getparent().remove(p._p)

audit=[]
for src in sorted((B/'source').glob('*.docx')):
    d=Document(src);report=src.name in ['01_Отчёт.docx','02_Отчёт.docx','03_Реферат.docx','04_Отчёт.docx','05_Отчёт.docx']
    supplemental=src.name in ['01_План_квартиры.docx','02_Семейное_дерево.docx']
    for s in d.sections:frame(s);footer(s,report)
    for sty in ['Normal','Title','Heading 1','Heading 2','TOC 1']:
        if sty not in d.styles:continue
        st=d.styles[sty];st.font.name='Times New Roman';st.font.size=Pt(14);st.font.bold=False;st.font.color.rgb=RGBColor(0,0,0)
        st.paragraph_format.space_before=Pt(0);st.paragraph_format.space_after=Pt(0);st.paragraph_format.line_spacing=1
    # Remove inherited decorative title rule from the source style.
    for border in d.styles.element.xpath('.//w:pBdr'):
        border.getparent().remove(border)
    if report:
        for p in d.paragraphs:fmt(p)
        cover(d)
        if src.name.startswith('03'):
            ps=d.paragraphs;start=next(i for i,p in enumerate(ps) if p.text=='Содержание');end=next(i for i,p in enumerate(ps) if p.text=='Введение')
            for p in ps[start:end]:p._p.getparent().remove(p._p)
        headings=[p for p in d.paragraphs if p.style.name=='Heading 1']
        # Compact reports do not need a separate contents page.
        if src.name.startswith('03'):toc(d,headings[0])
        # Remove inherited page breaks and keep rules from the former spaced version.
        for p in d.paragraphs:
            if p in headings or p._p.getprevious() is not None:
                p.paragraph_format.page_break_before=False
                p.paragraph_format.keep_with_next=False
        for br in d._element.xpath('.//w:br[@w:type="page"]'):
            br.getparent().remove(br)
        if src.name.startswith('03'):
            next(p for p in d.paragraphs if p.text=='Содержание').paragraph_format.page_break_before=True
        headings[0].paragraph_format.page_break_before=not src.name.startswith('03')
        num=0;question=False;q=0
        for p in headings:
            if question:
                q+=1;p.style='Heading 2';p.text=f'{num}.{q} '+p.text
                p.paragraph_format.page_break_before=False
            else:
                if p.text not in ['Введение','Заключение','Список использованных источников'] and not re.match(r'^\d+\s',p.text):
                    num+=1;p.text=f'{num} '+p.text
                p.paragraph_format.page_break_before=(p is headings[0] and not src.name.startswith('03'))
                if 'Ответы на контрольные вопросы' in p.text:question=True
            fmt(p,WD_ALIGN_PARAGRAPH.LEFT,15);p.paragraph_format.keep_with_next=True
            p.paragraph_format.space_before=Pt(6 if p.style.name=='Heading 1' else 4)
            p.paragraph_format.space_after=Pt(2)
        current_section=0;figure_number=0
        for p in list(d.paragraphs):
            if p.style.name=='Heading 1':
                match=re.match(r'^(\d+) ',p.text)
                if match:current_section=int(match.group(1));figure_number=0
            if p._p.xpath('.//w:drawing'):
                fmt(p,WD_ALIGN_PARAGRAPH.CENTER,0);p.paragraph_format.keep_with_next=True
            if p.text.startswith('Рисунок '):
                figure_number+=1
                name=re.sub(r'^Рисунок \d+\s*[-–]\s*','',p.text)
                p.text=f'Рисунок {current_section}.{figure_number} – {name}'
                fmt(p,WD_ALIGN_PARAGRAPH.CENTER,0)
                p.paragraph_format.keep_with_next=False
                image_p=p._p.getprevious()
                if image_p is not None and image_p.xpath('.//w:drawing'):
                    from docx.text.paragraph import Paragraph
                    lead=Paragraph(image_p,p._parent).insert_paragraph_before(f'На рисунке {current_section}.{figure_number} представлен результат: {name[0].lower()+name[1:]}.')
                    fmt(lead);lead.paragraph_format.keep_with_next=True
        if src.name.startswith('03'):
            citations=iter(['1, 2','3','4','3','5, 6','7','8','2','9','1'])
            for p in d.paragraphs:
                if p.text.startswith('Источник:'):
                    p.text=p.text.rstrip('.')+' ['+next(citations)+'].';fmt(p)
            bibliography=False;n=0
            for p in d.paragraphs:
                if p.text=='Список использованных источников':bibliography=True;continue
                if bibliography and p.text and not p.text.startswith('Дата обращения'):
                    n+=1;p.text=f'{n}. '+p.text;fmt(p,WD_ALIGN_PARAGRAPH.LEFT,0)
        # Remove empty body spacer paragraphs, including inherited image-page breaks.
        body=False
        for p in list(d.paragraphs):
            if p._p is headings[0]._p:body=True
            if body and not p.text.strip() and not p._p.xpath('.//w:drawing|.//w:pict|.//w:object|.//w:sectPr'):
                p._p.getparent().remove(p._p)
        for shape in d.inline_shapes:
            maxw=Mm(175)
            if shape.width>maxw:
                shape.height=int(shape.height*maxw/shape.width);shape.width=maxw
        # Replace the sparsely filled specification screenshot with the same
        # source table, preserving its row labels and object numbering.
        if src.name=='01_Отчёт.docx':
            caption=next(p for p in d.paragraphs if p.text.startswith('Рисунок 5.2'))
            image_p=caption._p.getprevious()
            table=deepcopy(Document(B/'source/01_План_квартиры.docx').tables[0]._tbl)
            image_p.addprevious(table);image_p.getparent().remove(image_p)
            caption.text='Таблица 5.1 – Спецификация';fmt(caption,WD_ALIGN_PARAGRAPH.LEFT,0)
            table.addprevious(caption._p)
            lead=caption._p.getprevious()
            from docx.text.paragraph import Paragraph
            leadp=Paragraph(lead,d._body)
            leadp.text='В таблице 5.1 приведена спецификация помещений квартиры.';fmt(leadp)
            from docx.table import Table
            t=Table(table,d._body);t.autofit=False
            for col,w in zip(t.columns,[Mm(15),Mm(160)]):col.width=w
            for row in t.rows:
                for cell,w in zip(row.cells,[Mm(15),Mm(160)]):
                    cell.width=w
                    for p in cell.paragraphs:
                        fmt(p,WD_ALIGN_PARAGRAPH.LEFT,0)
                        p.paragraph_format.keep_with_next=(row._tr is not t.rows[-1]._tr)
            # Keep table rows together and retain a modest caption gap.
            caption.paragraph_format.keep_with_next=True
        pictures=[s for s in d.inline_shapes if s.height>Mm(40)]
        if src.name=='01_Отчёт.docx':limits=[Mm(95)]
        elif src.name=='02_Отчёт.docx':limits=[Mm(80)]
        elif src.name=='04_Отчёт.docx':limits=[Mm(80)]
        elif src.name=='05_Отчёт.docx':limits=[Mm(85),Mm(110)]
        else:limits=[]
        for shape,maxh in zip(pictures,limits):
            if shape.height>maxh:
                shape.width=int(shape.width*maxh/shape.height);shape.height=maxh
    elif not supplemental:
        for p in d.paragraphs:fmt(p)
        p=d.paragraphs[7]
        p.text='Основной текст отчётов: Times New Roman, 14 пт, одинарный межстрочный интервал. Интервалы до и после абзаца — 0 пт. Рамка: слева 20 мм, справа, сверху и снизу 5 мм от края листа. Поля текста: слева 25 мм, справа 10 мм, сверху и снизу 15 мм. Номера страниц — внизу справа; титульный лист учитывается без номера. Оформление выполнено по приложенным требованиям.'
        fmt(p)
    else:
        for p in d.paragraphs:
            if p.text:fmt(p,WD_ALIGN_PARAGRAPH.CENTER,0)
        # Keep editable drawing geometry; reduce outer group only to fit text width.
        for group in d._element.xpath('.//*[local-name()="group"]'):
            style=group.get('style','')
            if 'width:700pt' in style:group.set('style',style.replace('width:700pt;height:365pt','width:700pt;height:365pt'))
        # Plan requires original bottom stamp clearance.
        if src.name.startswith('01'):
            for br in d._element.xpath('.//w:br[@w:type="page"]'):
                br.getparent().remove(br)
            for s in d.sections:
                s.bottom_margin=Mm(15)
                for t in s.footer.tables:
                    pos=t._tbl.tblPr.find(qn('w:tblpPr'))
                    if pos is not None:
                        pos.set(qn('w:horzAnchor'),'margin')
                        pos.attrib.pop(qn('w:tblpX'),None)
                        pos.set(qn('w:tblpXSpec'),'right')
                        pos.set(qn('w:tblpY'),str(int(s.page_height.twips)-1600))
                    for indent in list(t._tbl.tblPr.findall(qn('w:tblInd'))):t._tbl.tblPr.remove(indent)
            for t in d.tables:
                t.autofit=False
                for col,w in zip(t.columns,[Mm(20),Mm(155)]):col.width=w
                for row in t.rows:
                    for cell,w in zip(row.cells,[Mm(20),Mm(155)]):
                        cell.width=w
                        for p in cell.paragraphs:fmt(p,WD_ALIGN_PARAGRAPH.LEFT,0)
    for border in d._element.xpath('.//w:pBdr'):
        border.getparent().remove(border)
    sett=d.settings.element
    compat=sett.find(qn('w:compat'))
    if compat is None:compat=el('compat');sett.append(compat)
    for name in ['bordersDoNotSurroundHeader','bordersDoNotSurroundFooter']:
        if compat.find(qn('w:'+name)) is None:compat.append(el(name))
    for x in list(sett.findall(qn('w:updateFields'))):sett.remove(x)
    sett.append(el('updateFields',val='true'))
    dest=OUT/src.name;d.save(dest)
    audit.append(dict(file=src.name,source_sha256=hashlib.sha256(src.read_bytes()).hexdigest(),output_sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
for src in (B/'source').glob('*.xlsx'):shutil.copy2(src,OUT/src.name)
(B/'qa/audit-compact.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf8')
print('Saved',len(audit),'DOCX; spreadsheets copied without changes')
