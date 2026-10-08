# -*- coding: utf-8 -*-
"""Real Chrome and production components, isolated transport and retained screenshots."""
import base64
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

URL=os.environ.get('EXAM_ATTACHMENT_TEST_URL','http://127.0.0.1:5223')+'/test/exam-attachments.html'
OUT=Path(tempfile.mkdtemp(prefix='exam-attachments-browser-'))
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aM9sAAAAASUVORK5CYII=')

def open_day(page):
    page.get_by_role('button',name=re.compile('单日附件考试，')).click()
    dialog=page.get_by_role('dialog').filter(has=page.get_by_text('当天单日考试',exact=True))
    expect(dialog).to_be_visible()
    page.wait_for_timeout(350)
    return dialog

def edit(page):
    open_day(page)
    page.locator('.exam-day-detail').get_by_role('button',name=re.compile('编辑$')).first.click()
    return page.get_by_role('dialog',name='编辑考期',exact=True)

def counters(page): return page.evaluate('window.attachmentFixture.counters()')

with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    def new_page(state='success',width=1440):
        page=browser.new_page(viewport={'width':width,'height':1000})
        page.route('**/test/fixture-exam.png',lambda route:route.fulfill(body=PNG,content_type='image/png'))
        page.route('**/test/fixture-exam.pdf',lambda route:route.fulfill(body=b'%PDF-1.4\n%%EOF',content_type='application/pdf'))
        page.goto(URL+'?state='+state)
        return page
    for width in [1440,390]:
        page=new_page(width=width)
        errors=[]; page.on('pageerror',lambda error:errors.append(str(error)))
        open_day(page)
        expect(page.get_by_text('官方通知.png',exact=True).first).to_be_visible()
        if width == 390:
            assert page.locator('.exam-day-detail .attachment-card').first.bounding_box()['width'] > 250
        page.get_by_role('button',name='预览图片：官方通知.png').first.click()
        preview=page.get_by_role('dialog',name='官方通知.png',exact=True)
        expect(preview).to_be_visible(); preview.get_by_role('button',name=re.compile('关\s*闭')).click()
        page.locator('.exam-day-detail').get_by_role('button',name=re.compile('编辑$')).first.click()
        editor=page.get_by_role('dialog',name='编辑考期',exact=True)
        editor.locator('input[type=file]').set_input_files([
            {'name':'新官方通知.png','mimeType':'image/png','buffer':PNG},
            {'name':'说明.docx','mimeType':'application/vnd.openxmlformats-officedocument.wordprocessingml.document','buffer':b'fixture'}])
        expect(editor.get_by_text('新官方通知.png',exact=True)).to_be_visible()
        expect(editor.get_by_text('说明.docx',exact=True)).to_be_visible()
        editor.screenshot(path=str(OUT/f'editor-{width}.png'))
        editor.get_by_role('button',name='保存草稿',exact=True).click()
        expect(editor).not_to_be_visible()
        assert counters(page)['uploadCount']==2
        page.reload(); open_day(page)
        expect(page.get_by_text('说明.docx',exact=True).first).to_be_visible()
        assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
        page.screenshot(path=str(OUT/f'detail-{width}.png'))
        page.locator('.exam-day-detail').get_by_role('button',name=re.compile('编辑$')).first.click()
        editor=page.get_by_role('dialog',name='编辑考期',exact=True)
        for name in ['官方通知.png','考试安排.pdf','新官方通知.png','说明.docx']:
            editor.get_by_role('button',name='删除'+name,exact=True).click()
        editor.get_by_role('button',name='保存草稿',exact=True).click(); expect(editor).not_to_be_visible()
        assert page.evaluate('window.attachmentFixture.rows()[0].attachments.length')==0
        assert not errors,errors
        page.close(); print('PASS desktop/mobile upload/save/reload/preview/remove',width,flush=True)
    for state in ['upload-error','save-error','publish-error']:
        page=new_page(state)
        editor=edit(page)
        editor.locator('input[type=file]').set_input_files({'name':'新增通知.png','mimeType':'image/png','buffer':PNG})
        button='保存并发布' if state=='publish-error' else '保存草稿'
        editor.get_by_role('button',name=button,exact=True).click()
        expect(page.get_by_text(re.compile('模拟.*失败')).first).to_be_visible()
        expect(editor).to_be_visible()
        if state=='upload-error': assert counters(page)['saveCount']==0
        editor.get_by_role('button',name=button,exact=True).click(); expect(editor).not_to_be_visible()
        assert counters(page)['uploadCount']==(2 if state=='upload-error' else 1)
        page.close(); print('PASS failure retry without reupload',state,flush=True)
    page=new_page('readonly'); open_day(page)
    expect(page.get_by_text('官方通知.png',exact=True).first).to_be_visible()
    expect(page.locator('.exam-day-detail').get_by_role('button',name=re.compile('编辑$'))).to_have_count(0)
    page.close()
    page=new_page('read-error'); open_day(page)
    expect(page.get_by_text('模拟读取失败',exact=True)).to_be_visible()
    page.get_by_role('button',name='重试附件',exact=True).first.click()
    expect(page.get_by_text('模拟读取失败',exact=True)).to_have_count(0)
    page.close()
    page=new_page('cancel'); editor=edit(page)
    editor.locator('input[type=file]').set_input_files({'name':'不要上传.png','mimeType':'image/png','buffer':PNG})
    editor.get_by_role('button',name=re.compile('取\s*消')).click()
    assert counters(page)['uploadCount']==0
    page.close()
    page=new_page('reedit'); open_day(page)
    page.locator('.exam-day-detail').get_by_role('button',name=re.compile('撤销$')).first.click()
    page.locator('.ant-popconfirm').get_by_role('button',name=re.compile('确\s*定')).click()
    expect(page.locator('.exam-day-detail')).not_to_be_visible()
    open_day(page)
    page.locator('.exam-day-detail').get_by_role('button',name=re.compile('重新编辑$')).click()
    editor=page.get_by_role('dialog',name='重新编辑考期',exact=True)
    expect(editor.get_by_text('官方通知.png',exact=True)).to_be_visible()
    expect(editor.get_by_text('考试安排.pdf',exact=True)).to_be_visible()
    editor.get_by_role('button',name='保存草稿',exact=True).click(); expect(editor).not_to_be_visible()
    assert page.evaluate('window.attachmentFixture.rows().find(r=>r.id===3).attachments.length')==2
    assert counters(page)['uploadCount']==0
    page.close(); print('PASS reedit retains attachments without reupload',flush=True)
    page=new_page('create')
    page.get_by_role('button',name=re.compile('新增考期$')).click()
    editor=page.get_by_role('dialog',name='新增考期',exact=True)
    editor.get_by_label('考期名称',exact=True).fill('剪贴板通知考试')
    # Stub the OS boundary; do not overwrite the user's actual clipboard.
    page.evaluate('''png => {
      const blob = new Blob([Uint8Array.from(atob(png), c=>c.charCodeAt(0))], {type:'image/png'});
      Object.defineProperty(navigator.clipboard, 'read', {value: async()=>[{types:['image/png'],getType:async()=>blob}]});
    }''',base64.b64encode(PNG).decode())
    editor.get_by_role('button',name='上传剪贴板截图',exact=True).click()
    expect(editor.get_by_text(re.compile('截图-.*png'))).to_be_visible()
    editor.get_by_role('button',name='保存并发布',exact=True).click(); expect(editor).not_to_be_visible()
    assert page.evaluate('window.attachmentFixture.rows().find(r=>r.id===3).attachments.length')==1
    assert counters(page)['publishCount']==1
    page.get_by_role('button',name=re.compile('多日考试安排$')).click()
    expect(page.locator('.ant-drawer').get_by_text('考试安排.pdf',exact=True)).to_be_visible()
    page.close(); print('PASS create/clipboard/publish and multi-day drawer',flush=True)
    browser.close()
print('Screenshots:',OUT,flush=True)
