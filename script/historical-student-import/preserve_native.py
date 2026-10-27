#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Restore source-native XLSX formatting after verified artifact-tool authoring.

Artifact export changes border/column encodings and empty-string reference cells.
Retain the source package, copying only the already-authored exception values and
review metadata into original row XML. No openpyxl workbook authoring is used.
"""
import argparse
from copy import deepcopy
import json
from pathlib import Path
import re
import xml.etree.ElementTree as E
import zipfile

import openpyxl

NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
N = {'m': NS}
E.register_namespace('', NS)


def cell(ref, value, style=None):
    node = E.Element('{' + NS + '}c', {'r': ref, 't': 'inlineStr'})
    if style is not None:
        node.set('s', style)
    E.SubElement(E.SubElement(node, '{' + NS + '}is'), '{' + NS + '}t').text = str(value)
    return node


def restore(plan_path, output):
    plan = json.loads(plan_path.read_text(encoding='utf-8'))
    authored = openpyxl.load_workbook(output)
    source = openpyxl.load_workbook(plan['source'])
    replacements = {}
    with zipfile.ZipFile(plan['source']) as original:
        reason_style='1'
        if plan.get('comparisonRows'):
            styles=E.fromstring(original.read('xl/styles.xml'))
            xfs=styles.find('m:cellXfs',N)
            wrapped=deepcopy(xfs[1]);alignment=wrapped.find('m:alignment',N)
            if alignment is None:alignment=E.SubElement(wrapped,'{'+NS+'}alignment')
            alignment.set('wrapText','1');alignment.set('vertical','center')
            reason_style=str(len(xfs));xfs.append(wrapped);xfs.set('count',str(len(xfs)))
            raw_styles=original.read('xl/styles.xml').decode('utf-8')
            raw_styles=re.sub(r'(<cellXfs\b[^>]*\bcount=")\d+("[^>]*>)',lambda m:m.group(1)+str(len(xfs))+m.group(2),raw_styles,count=1)
            raw_styles=raw_styles.replace('</cellXfs>',E.tostring(wrapped,encoding='unicode')+'</cellXfs>')
            replacements['xl/styles.xml']=raw_styles.encode('utf-8')
        for index, name in enumerate(source.sheetnames[:3], 1):
            exceptions = sorted((r for r in plan['exceptions'] if r['sheet'] == name), key=lambda r: (r.get('reviewOrder',r['row']),r['row']))
            for i, r in enumerate(exceptions, 2):
                actual=[authored[name].cell(i,j).value for j in range(1,48)]
                actual[-1]=str(actual[-1])
                assert actual == [r['reason'], *r['values'], r['sheet'], str(r['row'])]
            part = f'xl/worksheets/sheet{index}.xml'
            tree = E.fromstring(original.read(part))
            data = tree.find('m:sheetData', N)
            rows = {int(r.attrib['r']): r for r in data}
            header = deepcopy(rows[1])
            style = next(c.attrib.get('s') for c in header if c.attrib['r'] == 'AR1')
            for c in header:
                c.set('r', openpyxl.utils.get_column_letter(openpyxl.utils.column_index_from_string(re.sub(r'\d+', '', c.attrib['r'])) + 1) + '1')
            header.insert(0, cell('A1', '原因', style))
            for col, title in zip(('AT', 'AU'), ('来源工作表', '原始行号')):
                header.append(cell(col + '1', title, style))
            for r in list(data):
                data.remove(r)
            data.append(header)
            for i, record in enumerate(exceptions, 2):
                row = deepcopy(rows[record['row']])
                row.set('r', str(i))
                for c in row:
                    c.set('r', openpyxl.utils.get_column_letter(openpyxl.utils.column_index_from_string(re.sub(r'\d+', '', c.attrib['r'])) + 1) + str(i))
                row.insert(0, cell('A' + str(i), record['reason'], reason_style))
                if plan.get('comparisonRows'):row.set('ht','100');row.set('customHeight','1')
                for col, value in zip(('AT', 'AU'), (record['sheet'], record['row'])):
                    row.append(cell(col + str(i), value, '1'))
                data.append(row)
            tree.find('m:dimension', N).set('ref', f'A1:AU{len(exceptions) + 1}')
            columns = tree.find('m:cols', N)
            for column in list(columns):
                lo, hi = int(column.get('min')), int(column.get('max'))
                if lo > 44:
                    columns.remove(column)
                    continue
                column.set('min', str(lo + 1))
                column.set('max', str(min(hi, 44) + 1))
            for col, width in ((1, 65), (46, 32), (47, 12)):
                E.SubElement(columns, '{' + NS + '}col', {'min': str(col), 'max': str(col), 'width': str(width), 'customWidth': '1'})
            for pane in tree.findall('m:sheetViews/m:sheetView/m:pane', N):
                pane.set('xSplit', '8')
                pane.set('topLeftCell', 'I2')
            replacements[part] = E.tostring(tree, encoding='utf-8', xml_declaration=True)
        if plan.get('comparisonRows'):
            tree=E.fromstring(replacements['xl/worksheets/sheet1.xml'])
            data=tree.find('m:sheetData',N)
            for row in list(data)[1:]:data.remove(row)
            source_rows={}
            for index,name in enumerate(source.sheetnames[:3],1):
                raw=E.fromstring(original.read(f'xl/worksheets/sheet{index}.xml'))
                source_rows[name]={int(r.get('r')):r for r in raw.find('m:sheetData',N)}
            for i,r in enumerate(plan['comparisonRows'],2):
                row=deepcopy(source_rows[r['sheet']][r['row']]);row.set('r',str(i));row.set('ht','100');row.set('customHeight','1')
                for c in row:
                    oldcol=openpyxl.utils.column_index_from_string(re.sub(r'\d+','',c.get('r')))
                    c.set('r',openpyxl.utils.get_column_letter(oldcol+1)+str(i))
                row.insert(0,cell('A'+str(i),r['reason'],reason_style))
                row.append(cell('AT'+str(i),r['sheet'],'1'));row.append(cell('AU'+str(i),r['row'],'1'));data.append(row)
            tree.find('m:dimension',N).set('ref',f"A1:AU{len(plan['comparisonRows'])+1}")
            replacements['xl/worksheets/sheet7.xml']=E.tostring(tree,encoding='utf-8',xml_declaration=True)
            # Preserve namespace declarations referenced by mc:Ignorable attribute values.
            raw_book=original.read('xl/workbook.xml').decode('utf-8')
            assert 'xmlns:r=' in raw_book
            replacements['xl/workbook.xml']=raw_book.replace('</sheets>','<sheet name="重复记录对比" sheetId="7" r:id="rIdExceptionReview"/></sheets>').encode('utf-8')
            rels=E.fromstring(original.read('xl/_rels/workbook.xml.rels'))
            E.SubElement(rels,'{http://schemas.openxmlformats.org/package/2006/relationships}Relationship',{'Id':'rIdExceptionReview','Type':'http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet','Target':'worksheets/sheet7.xml'})
            E.register_namespace('','http://schemas.openxmlformats.org/package/2006/relationships')
            replacements['xl/_rels/workbook.xml.rels']=E.tostring(rels,encoding='utf-8',xml_declaration=True)
            types=E.fromstring(original.read('[Content_Types].xml'))
            E.SubElement(types,'{http://schemas.openxmlformats.org/package/2006/content-types}Override',{'PartName':'/xl/worksheets/sheet7.xml','ContentType':'application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml'})
            E.register_namespace('','http://schemas.openxmlformats.org/package/2006/content-types')
            replacements['[Content_Types].xml']=E.tostring(types,encoding='utf-8',xml_declaration=True)
            E.register_namespace('',NS)
        part = 'xl/worksheets/sheet4.xml'
        tree = E.fromstring(original.read(part))
        for row in tree.find('m:sheetData', N):
            n = int(row.attrib['r'])
            if 2 <= n <= 9:
                for c in list(row):
                    col = re.sub(r'\d+', '', c.attrib['r'])
                    value = authored['填写说明'][col + str(n)].value
                    row.remove(c)
                    row.append(cell(col + str(n), value, c.attrib.get('s')))
        replacements[part] = E.tostring(tree, encoding='utf-8', xml_declaration=True)
        temp = output.with_suffix('.native.tmp')
        with zipfile.ZipFile(temp, 'x', zipfile.ZIP_DEFLATED) as out:
            for item in original.infolist():
                out.writestr(item, replacements.get(item.filename, original.read(item.filename)))
            for name,content in replacements.items():
                if name not in original.namelist():out.writestr(name,content)
    authored.close()
    source.close()
    temp.replace(output)
    result = openpyxl.load_workbook(output)
    source = openpyxl.load_workbook(plan['source'])
    total = 0
    for name in source.sheetnames[:3]:
        a, b = source[name], result[name]
        exceptions = sorted((r for r in plan['exceptions'] if r['sheet'] == name), key=lambda r: (r.get('reviewOrder',r['row']),r['row']))
        for col in range(1, 45):
            x, y = a.cell(1, col), b.cell(1, col + 1)
            assert x.value == y.value and x._style == y._style
            def width(sheet, index):
                for dimension in sheet.column_dimensions.values():
                    if dimension.min <= index <= dimension.max:
                        return dimension.width
                return sheet.sheet_format.defaultColWidth
            assert width(a, col) == width(b, col + 1)
        assert b['A1'].value == '原因'
        for i, r in enumerate(exceptions, 2):
            assert [b.cell(i, j).value for j in range(1, 48)] == [r['reason'], *r['values'], r['sheet'], str(r['row'])]
        assert b.max_row == len(exceptions) + 1
        total += len(exceptions)
    with zipfile.ZipFile(plan['source']) as a, zipfile.ZipFile(output) as b:
        for part in ('xl/worksheets/sheet5.xml', 'xl/worksheets/sheet6.xml'):
            assert a.read(part) == b.read(part)
        assert b.testzip() is None
    assert total == len(plan['exceptions'])
    if plan.get('comparisonRows'):
        sheet=result['重复记录对比']
        assert sheet.max_row==len(plan['comparisonRows'])+1
        for i,r in enumerate(plan['comparisonRows'],2):
            assert [sheet.cell(i,j).value for j in range(1,48)]==[r['reason'],*r['values'],r['sheet'],str(r['row'])]
    print(json.dumps({'verifiedExceptionRows': total, 'headerStyles': 'exact source styles', 'referenceSheets': 'byte-identical', 'zip': 'valid'}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    restore(a.plan, a.output)
