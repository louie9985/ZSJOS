# UTF-8. Actual Workbench pages with synthetic transport, desktop/mobile and failure paths.
import base64
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect
OUT=Path(gettempdir())/'zsjos-supervisor-overturn';OUT.mkdir(exist_ok=True)
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS3sAAAAASUVORK5CYII=')
def open_detail(page,entry,width,denied=False):
    page.set_viewport_size({'width':width,'height':1000})
    page.goto(f'http://127.0.0.1:5201/test/supervisor-lead-overturn.html?entry={entry}&leadId=1'+('&denied=1' if denied else ''))
    if entry=='subordinate':
        page.locator('.subordinate-sales-item').click()
        page.get_by_role('tab',name='名下客资',exact=True).click()
        page.get_by_role('button',name='改判测试客资',exact=True).click()
    expect(page.get_by_text('KZ-OV-TEST',exact=True).first).to_be_visible()
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    page=browser.new_page();page.set_default_timeout(15000)
    errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    for entry in ('management','subordinate'):
        for width in (1440,390):
            open_detail(page,entry,width)
            page.get_by_role('button',name=re.compile('改判有效$')).click()
            dialog=page.get_by_role('dialog',name='主管直接改判有效')
            dialog.get_by_role('button',name='确认改判有效').click()
            expect(dialog.get_by_text('请填写改判理由',exact=True)).to_be_visible()
            dialog.locator('textarea').fill('已核实客户意向')
            page.screenshot(path=str(OUT/f'{entry}-{width}.png'),full_page=True)
            page.evaluate("overturnFixture.fail='客资归属或无效判定已变化，请刷新后重试'")
            dialog.get_by_role('button',name='确认改判有效').click()
            expect(dialog.get_by_text('客资归属或无效判定已变化，请刷新后重试',exact=True)).to_be_visible()
            expect(dialog.locator('textarea')).to_have_value('已核实客户意向')
            first_key=page.evaluate('overturnFixture.posts[0].idempotencyKey')
            page.evaluate("overturnFixture.fail=''")
            dialog.get_by_role('button',name='确认改判有效').click()
            expect(dialog).not_to_be_visible()
            assert page.evaluate('overturnFixture.posts[1].idempotencyKey')==first_key
            expect(page.get_by_role('button',name=re.compile('改判有效$'))).to_have_count(0)
            page.get_by_role('tab',name='流转记录',exact=True).click()
            expect(page.get_by_text('主管直接改判有效',exact=True)).to_be_visible()
            assert page.evaluate('overturnFixture.reads')>=2
            if entry=='subordinate':assert page.evaluate('overturnFixture.salesReads')>=2
            print('PASS real page:',entry,width,flush=True)
        open_detail(page,entry,1440,True)
        expect(page.get_by_role('button',name=re.compile('改判有效$'))).to_have_count(0)
    open_detail(page,'management',1440)
    page.get_by_role('button',name=re.compile('改判有效$')).click()
    dialog=page.get_by_role('dialog',name='主管直接改判有效')
    dialog.locator('textarea').fill('已核实客户意向')
    dialog.locator('input[type=file]').set_input_files({'name':'evidence.png','mimeType':'image/png','buffer':PNG})
    page.evaluate('overturnFixture.uploadFail=true')
    dialog.get_by_role('button',name='确认改判有效').click()
    expect(dialog.get_by_text('有图片上传失败，请重试失败项',exact=True)).to_be_visible()
    assert page.evaluate('overturnFixture.posts.length')==0
    page.evaluate('overturnFixture.uploadFail=false')
    dialog.get_by_role('button',name='确认改判有效').click()
    expect(dialog).not_to_be_visible()
    assert page.evaluate('overturnFixture.posts[0].attachments')==[{'infraFileId':100}]
    assert not errors,errors
    print('PASS upload failure/retry, no premature command, denied entries, no page errors',flush=True)
    browser.close()
