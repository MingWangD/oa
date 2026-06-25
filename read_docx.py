from docx import Document

doc = Document('docs/司法鉴定系统使用手册.docx')
for p in doc.paragraphs:
    if '收到委托' in p.text or '登记' in p.text or '必填' in p.text or '金额' in p.text or '紧急' in p.text:
        print(p.text)
