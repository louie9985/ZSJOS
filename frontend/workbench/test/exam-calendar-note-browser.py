# -*- coding: utf-8 -*-
"""Real Chromium UI, isolated transport; no production data or account changes."""
import base64
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

URL=os.environ.get('EXAM_NOTE_TEST_URL','http://127.0.0.1:5208')+'/test/exam-calendar-note.html'
OUT=Path(tempfile.mkdtemp(prefix='exam-note-browser-'))
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aM9sAAAAASUVORK5CYII=')

with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width in [1440,768,390]:
        page=browser.new_page(viewport={'width':width,'height':960})
        errors=[];page.on('pageerror',lambda error:errors.append(str(error)))
        page.route('**/test/exam-note-fixture-image/*',lambda route:route.fulfill(body=PNG,content_type='image/png'))
        page.goto(URL)
        side=page.get_by_role('complementary',name='日历说明')
        expect(side.get_by_text('无需考试',exact=True)).to_be_visible()
        geometry=page.evaluate('''() => { const a=document.querySelector('.exam-note-calendar').getBoundingClientRect(), b=document.querySelector('.exam-note-panel').getBoundingClientRect(); return { main:a.width, aside:b.width, above:b.bottom<=a.top+1, columns:getComputedStyle(document.querySelector('.exam-note-calendar')).gridColumn, overflow:document.documentElement.scrollWidth>innerWidth } }''')
        assert not geometry['overflow'],geometry
        if width>768: assert geometry['columns']=='span 9' and geometry['main']>geometry['aside']*2.8,geometry
        else: assert geometry['above'],geometry
        page.screenshot(path=str(OUT/f'layout-{width}.png'),full_page=True)
        side.get_by_role('button',name='放大查看').click()
        view=page.get_by_role('dialog',name='日历说明',exact=True)
        expect(view.locator('table')).to_be_visible()
        assert view.bounding_box()['width']<=width
        view.get_by_role('button',name=re.compile(r'关\s*闭')).click()
        side.get_by_role('button',name=re.compile(r'编\s*辑')).click()
        edit=page.get_by_role('dialog',name='编辑日历说明')
        editor=edit.locator('[data-slate-editor]')
        expect(editor).to_be_visible();expect(editor.locator('table')).to_be_visible()
        page.wait_for_timeout(400);editor.focus();page.keyboard.press('Control+End');page.keyboard.press('Enter');page.keyboard.type(' new note')
        edit.get_by_role('button',name=re.compile(r'预\s*览')).click()
        expect(edit.get_by_role('region',name='说明预览')).to_contain_text('new note')
        page.screenshot(path=str(OUT/f'editor-{width}.png'))
        edit.get_by_role('button',name=re.compile(r'保\s*存')).click()
        expect(edit).not_to_be_visible();expect(side).to_contain_text('new note')
        page.get_by_role('button',name='下一月').click()
        expect(side).to_contain_text('new note')
        page.reload();expect(side).to_contain_text('new note')
        assert not errors,errors
        page.close()
        print('PASS geometry/edit/preview/save/month/refresh',width,flush=True)
    for state in ['read-only','denied','empty','load-error','save-error','conflict','upload-error']:
        page=browser.new_page(viewport={'width':1440,'height':960})
        errors=[];page.on('pageerror',lambda error:errors.append(str(error)))
        page.route('**/test/exam-note-fixture-image/*',lambda route:route.fulfill(body=PNG,content_type='image/png'))
        page.goto(URL+'?state='+state)
        side=page.locator('.exam-note-panel')
        if state=='denied':expect(side).to_contain_text('无权查看考期说明')
        elif state=='read-only':
            expect(side.get_by_role('button',name=re.compile(r'编\s*辑'))).to_have_count(0)
            side.get_by_role('button',name='放大查看').click();expect(page.get_by_role('dialog')).to_be_visible()
        elif state=='empty':expect(side).to_contain_text('暂无说明')
        elif state=='load-error':
            expect(side).to_contain_text('说明加载失败');side.get_by_role('button',name=re.compile(r'重\s*试')).click();expect(side).to_contain_text('无需考试')
        else:
            side.get_by_role('button',name=re.compile(r'编\s*辑')).click()
            edit=page.get_by_role('dialog',name='编辑日历说明');editor=edit.locator('[data-slate-editor]')
            expect(editor).to_be_visible()
            page.wait_for_timeout(400);editor.focus();page.keyboard.press('Control+End');page.keyboard.type(' retained draft')
            if state=='upload-error':
                editor.locator('p').first.click();page.keyboard.press('End')
                expect(edit.locator('button[data-menu-key=uploadImage]')).not_to_have_class(re.compile('disabled'))
                with page.expect_file_chooser() as chooser: edit.locator('button[data-menu-key=uploadImage]').click()
                chooser.value.set_files({'name':'image.png','mimeType':'image/png','buffer':PNG})
                expect(edit.get_by_role('button',name=re.compile(r'保\s*存'))).to_be_disabled()
                expect(edit.get_by_role('button',name='重试上传')).to_be_visible()
                edit.get_by_role('button',name='重试上传').click()
                expect(editor.locator('img')).to_have_count(1)
                expect(edit.get_by_role('button',name=re.compile(r'保\s*存'))).to_be_enabled()
                editor.locator('p').first.click();page.keyboard.press('End')
                editor.evaluate('''(element, bytes) => {
                    const transfer=new DataTransfer();transfer.items.add(new File([new Uint8Array(bytes)],'pasted.png',{type:'image/png'}));
                    element.dispatchEvent(new ClipboardEvent('paste',{clipboardData:transfer,bubbles:true,cancelable:true}));
                }''',list(PNG))
                expect(editor.locator('img')).to_have_count(2)
            else:
                edit.get_by_role('button',name=re.compile(r'保\s*存')).click()
                expect(editor).to_contain_text('retained draft')
                if state=='conflict':
                    edit.get_by_role('button',name='加载最新内容对照').click()
                    view=page.get_by_role('dialog',name='日历说明',exact=True)
                    expect(view).to_contain_text('其他人刚保存的内容');view.get_by_role('button',name=re.compile(r'关\s*闭')).click()
                else:expect(edit).to_contain_text('保存失败')
            edit.get_by_role('button',name=re.compile(r'保\s*存')).click();expect(edit).not_to_be_visible()
            expect(side).to_contain_text('retained draft')
            if state=='upload-error':
                expect(side.locator('img')).to_have_count(2)
                expect(side.locator('img').first).to_have_js_property('naturalWidth',1)
                page.screenshot(path=str(OUT/'saved-images.png'),full_page=True)
        assert not errors,errors
        page.close();print('PASS state',state,flush=True)
    page=browser.new_page();page.goto(URL)
    side=page.locator('.exam-note-panel');side.get_by_role('button',name=re.compile(r'编\s*辑')).click()
    edit=page.get_by_role('dialog',name='编辑日历说明');editor=edit.locator('[data-slate-editor]')
    expect(editor).to_be_visible();page.wait_for_timeout(400);editor.focus();page.keyboard.press('Control+Home');page.keyboard.type('cancelled content')
    edit.get_by_role('button',name=re.compile(r'取\s*消')).click()
    page.get_by_role('button',name='放弃修改').click();expect(edit).not_to_be_visible();expect(side).not_to_contain_text('cancelled content')
    side.get_by_role('button',name=re.compile(r'编\s*辑')).click();expect(editor).to_be_visible();page.wait_for_timeout(400);editor.focus();page.keyboard.press('Control+A');page.keyboard.press('Backspace')
    edit.get_by_role('button',name=re.compile(r'保\s*存')).click();expect(side).to_contain_text('暂无说明')
    checks=page.evaluate('''async () => {
      const api=await import('/src/services/examCalendarNote.ts'), rich=await import('/src/components/ExamNoteRichText.tsx');
      const images=[{fileId:12,url:'https://example.com/image?signature=temporary'}];
      const saved=api.noteSaveHtml(api.noteDisplayHtml('<p>说明</p><img src="exam-note-image:12">',images),images);
      const safe=rich.safeExamNoteHtml('<svg onload="x"></svg><script>x</script><p onclick="x" style="position:fixed;color:red">正文</p><a href="javascript:x">危险</a><img src="data:text/html,x">');
      let rejected=0;for(const html of ['字'.repeat(5001),'<img src="https://other/image">','<!--'+'字'.repeat(70000)+'-->']) { try{api.noteSaveHtml(html,[])}catch{rejected++} }
      return {saved,safe,rejected};
    }''')
    assert 'exam-note-image:12' in checks['saved'] and 'signature' not in checks['saved']
    assert checks['rejected']==3 and all(x not in checks['safe'] for x in ['script','onclick','fixed','svg','data:text'])
    page.close();print('PASS cancel/clear/content-limits/sanitizer/stable-image-roundtrip',flush=True)
    page=browser.new_page();page.goto(URL+'?notice=1')
    expect(page.locator('[data-slate-editor]')).to_contain_text('公告原内容')
    page.get_by_role('button',name='替换公告').click();expect(page.locator('[data-slate-editor]')).to_contain_text('新公告内容')
    expect(page.locator('button[data-menu-key=uploadVideo]')).to_have_count(1)
    print('PASS notice adapter',flush=True)
    browser.close()
print('Screenshots:',OUT,flush=True)
