# -*- coding: utf-8 -*-
"""Real consumers, default complete data, synthetic HTTP and Chrome lifecycle checks."""
import json
import os
import re
import sys
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

ORIGIN = os.environ.get('ZSJOS_STUDENT_INFO_ORIGIN', 'http://127.0.0.1:5174')
OUTPUT = Path(gettempdir()) / 'zsjos-student-info-panel'
OUTPUT.mkdir(exist_ok=True)

def calls(page, kind=None):
    values = json.loads(page.locator('html').get_attribute('data-student-requests') or '[]')
    return [item for item in values if kind is None or item['kind'] == kind]

def blobs(page):
    return json.loads(page.locator('html').get_attribute('data-student-blobs') or '{"created":[],"revoked":[],"downloads":[]}')

def released(page):
    state = blobs(page)
    assert state['created'] and set(state['created']) <= set(state['revoked'])

def no_controls(panel):
    assert panel.get_by_role('button', name=re.compile('查看完整敏感字段|隐藏敏感信息')).count() == 0
    assert not any(text in panel.inner_text() for text in ['当前查看状态', '敏感信息已隐藏', '完整敏感字段已显示', '材料附件已查看'])

def full(page, case_id=7001):
    panel = page.locator('.student-info-panel')
    expect(panel.get_by_text(f'SYNTHETIC-FULL-ID-{case_id}', exact=True)).to_be_visible()
    expect(panel.get_by_text(f'SYNTHETIC-FULL-PHONE-{case_id}', exact=False)).to_be_visible()
    assert '服务端电话遮罩' not in panel.inner_text()
    assert page.locator('html').get_attribute('data-student-mask-was-rendered') is None, 'Masked projection must never flash before authorized complete data.'
    no_controls(panel)
    return panel

def masked(page):
    panel = page.locator('.student-info-panel')
    phone = panel.locator('.student-info-field').filter(has_text='联系电话').locator('dd')
    expect(phone).to_contain_text('服务端电话遮罩-7001-甲')
    assert phone.evaluate("el => Array.from(el.childNodes).filter(n => n.nodeType === Node.TEXT_NODE).map(n => n.textContent).join('').trim()") == '服务端电话遮罩-7001-甲'
    expect(panel.get_by_text('服务端证件遮罩-7001-乙', exact=True)).to_be_visible()
    assert 'SYNTHETIC-FULL-' not in panel.inner_text()
    no_controls(panel)
    return panel

def pdf_preview(page, panel, width):
    panel.locator('.attachment-card').filter(has_text='合成补充证明材料.pdf').get_by_role('button', name=re.compile('预览')).click()
    frame = page.locator('iframe[title="合成补充证明材料.pdf"]')
    expect(frame).to_be_visible()
    assert frame.get_attribute('src').startswith('blob:')
    data = frame.evaluate('''async el => {
        const r = await fetch(el.src), b = await r.blob(), s = await b.text();
        return {ok:r.ok,type:b.type,size:b.size,header:s.slice(0,8),page:s.includes('/Count 1'),eof:s.trimEnd().endsWith('%%EOF')};
    }''')
    assert data['ok'] and data['type'] == 'application/pdf' and data['size'] > 500
    assert data['header'] == '%PDF-1.4' and data['page'] and data['eof']
    assert not any(f.url.startswith('chrome-error:') for f in page.frames)
    assert page.get_by_role('dialog').last.locator('.ant-alert-error').count() == 0
    page.wait_for_timeout(1500)  # Native Chrome extension viewer renders outside the application DOM.
    assert any(f.url.startswith('chrome-extension:') for f in page.frames)
    page.screenshot(path=str(OUTPUT / f'default-full-pdf-{width}.png'))
    return frame

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    errors = []
    def open_panel(query='', width=1440, height=1000):
        page = browser.new_page(viewport={'width':width,'height':height}, accept_downloads=True)
        page.set_default_timeout(30000)
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(ORIGIN + '/test/student-info-panel.html' + query, wait_until='domcontentloaded', timeout=60000)
        page.get_by_role('button', name='完整资料' if 'overview' in query else '查看学员资料', exact=True).click()
        panel = page.locator('.student-info-panel')
        expect(panel).to_be_visible()
        return page, panel
    def close_main(page, overview=False, programmatic=False):
        control = page.locator('.ant-drawer-close' if overview else '.student-info-dialog .ant-modal-close')
        control.evaluate('(el)=>el.click()') if programmatic else control.click()
        expect(page.locator('.student-info-panel')).to_have_count(0)
    def refresh(page, programmatic=False):
        control = page.locator('.student-info-panel').get_by_role('button', name=re.compile(r'刷\s*新'))
        control.evaluate('(el)=>el.click()') if programmatic else control.click()

    if '--pdf-only' in sys.argv:
        for width,height in [(1440,1000),(390,844)]:
            page,_ = open_panel(width=width,height=height)
            panel = full(page)
            assert not calls(page,'image')
            frame = pdf_preview(page,panel,width)
            close_main(page,programmatic=True)
            expect(frame).to_have_count(0)
            released(page)
            page.close()
        assert not errors,errors
        browser.close()
        print('PASS default complete data, valid native PDF preview and close cleanup at 1440/390.',OUTPUT)
        raise SystemExit(0)

    for overview in [False,True]:
        for width,height in [(1440,1000),(390,844)]:
            page,_ = open_panel('?overview' if overview else '',width,height)
            panel = full(page)
            assert [x['kind'] for x in calls(page)] == (['detail','detail','sensitive'] if overview else ['detail','sensitive'])
            expect(panel.locator('.student-info-main > .student-info-section')).to_have_count(4)
            expect(panel.locator('.attachment-card')).to_have_count(3)
            assert '末尾完整正文标记。' in panel.inner_text()
            assert panel.locator('.student-info-option').count() == 3
            tag = panel.locator('.student-info-option').filter(has_text='LongSavedEducationLabel')
            assert tag.inner_text().endswith('LongSavedEducationLabel'*5)
            assert tag.evaluate('(el)=>el.scrollWidth<=el.clientWidth+1')
            assert tag.evaluate("el=>!['transparent','rgba(0, 0, 0, 0)'].includes(getComputedStyle(el).backgroundColor)")
            expect(panel.locator('.student-info-field').filter(has_text='所在地区').locator('.student-info-option')).to_have_text('合成省 / 示例市 / 测试区')
            assert panel.evaluate('(el)=>el.scrollWidth<=el.clientWidth+1')
            page.wait_for_timeout(350)
            entry = 'overview' if overview else 'registration'
            page.screenshot(path=str(OUTPUT/f'default-full-{entry}-{width}-top.png'))
            page.locator('.ant-drawer-body' if overview else '.student-info-dialog .ant-modal-body').evaluate('(el)=>el.scrollTop=el.scrollHeight')
            page.screenshot(path=str(OUTPUT/f'default-full-{entry}-{width}-attachments.png'))
            refresh(page); full(page)
            assert [x['kind'] for x in calls(page)][-2:] == ['detail','sensitive']
            assert not calls(page,'image')
            close_main(page,overview)
            page.get_by_role('button',name='完整资料' if overview else '查看学员资料',exact=True).click()
            full(page); page.close()

    for overview in [False,True]:
        page,_ = open_panel('?denied'+('&overview' if overview else ''))
        panel = masked(page)
        assert not calls(page,'sensitive') and not calls(page,'image')
        assert not panel.locator('.attachment-card-actions button').count()
        assert panel.get_by_role('button',name=re.compile('导出')).count()==1
        page.close()
    page,panel = open_panel('?empty')
    expect(panel.get_by_text('暂无已提交的学员信息',exact=True)).to_be_visible()
    assert not calls(page,'sensitive') and not panel.locator('.student-info-field').count()
    page.close()
    page,_ = open_panel('?no-export')
    assert not full(page).get_by_role('button',name=re.compile('导出')).count()
    page.close()

    for scenario,message in [('detail-error','合成资料读取失败，请重试'),('sensitive-error','合成完整敏感字段读取被拒绝')]:
        page,panel = open_panel('?'+scenario)
        expect(panel.get_by_text(message,exact=True)).to_be_visible()
        assert not panel.locator('.student-info-field').count()
        assert page.locator('html').get_attribute('data-student-mask-was-rendered') is None
        panel.get_by_role('button',name=re.compile(r'^重\s*试$')).click()
        full(page); page.close()
    for trigger,message in [('下一次资料读取失败','合成资料读取被拒绝'),('下一次完整资料读取失败','合成完整敏感字段读取被拒绝')]:
        page,_ = open_panel()
        panel = full(page)
        page.get_by_role('button',name=trigger,exact=True).evaluate('(el)=>el.click()')
        refresh(page)
        expect(panel.get_by_text(message,exact=True)).to_be_visible()
        assert not panel.locator('.student-info-field').count() and not panel.locator('.attachment-card').count()
        assert 'SYNTHETIC-FULL-' not in panel.inner_text() and '服务端电话遮罩' not in panel.inner_text()
        panel.get_by_role('button',name=re.compile(r'^重\s*试$')).click()
        full(page); page.close()

    for kind in ['detail','sensitive']:
        for action in ['close','switch','deny']:
            for overview in [False,True]:
                page,panel = open_panel(f'?slow-{kind}'+('&overview' if overview else ''))
                page.wait_for_function('(a)=>JSON.parse(document.documentElement.dataset.studentRequests||"[]").filter(x=>x.kind===a.kind).length>=a.count',arg={'kind':kind,'count':2 if kind=='detail' and overview else 1})
                assert not panel.locator('.student-info-field').count()
                assert '服务端电话遮罩' not in panel.inner_text()
                if action=='close': close_main(page,overview)
                else:
                    page.get_by_role('button',name='切换报名' if action=='switch' else '撤销资料读取权限',exact=True).evaluate('(el)=>el.click()')
                    expect(page.locator('.student-info-panel')).to_have_count(0)
                page.wait_for_timeout(1600)
                assert not page.locator('.student-info-panel').count()
                assert 'SYNTHETIC-FULL-' not in page.locator('body').inner_text()
                if action!='deny':
                    page.get_by_role('button',name='完整资料' if overview else '查看学员资料',exact=True).click()
                    full(page,7002 if action=='switch' else 7001)
                page.close()
    for trigger in ['下一次资料读取延迟','下一次完整资料读取延迟']:
        page,_ = open_panel()
        panel = full(page)
        page.get_by_role('button',name=trigger,exact=True).evaluate('(el)=>el.click()')
        refresh(page)
        expect(panel.locator('.student-info-field')).to_have_count(0)
        full(page); page.close()

    page,_ = open_panel()
    panel = full(page)
    panel.locator('.attachment-card').filter(has_text='合成报名材料正面.png').get_by_role('button',name=re.compile('预览')).click()
    expect(page.get_by_role('dialog')).to_have_count(2)
    image = page.get_by_role('dialog').last.locator('.ant-image img').first
    expect(image).to_be_visible()
    page.wait_for_function('(img)=>img.complete&&img.naturalWidth>0',arg=image.element_handle())
    refresh(page,programmatic=True)
    expect(page.get_by_role('dialog')).to_have_count(1)
    full(page); released(page)
    frame = pdf_preview(page,panel,1440)
    close_main(page,programmatic=True)
    expect(frame).to_have_count(0)
    released(page)
    page.get_by_role('button',name='查看学员资料',exact=True).click()
    panel = full(page)
    with page.expect_download() as download:
        panel.locator('.attachment-card').filter(has_text='合成补充证明材料.pdf').get_by_role('button',name=re.compile('下载')).click()
    assert download.value.suggested_filename=='合成补充证明材料.pdf'
    refresh(page); full(page); released(page)
    with page.expect_download() as download:
        panel.get_by_role('button',name=re.compile('导出')).click()
    assert download.value.suggested_filename=='学员信息.xlsx'
    page.close()

    for command,action in [('预览','refresh'),('下载','close'),('预览','switch')]:
        page,_ = open_panel('?slow-image')
        panel = full(page)
        panel.locator('.attachment-card').filter(has_text='合成报名材料正面.png').get_by_role('button',name=re.compile(command)).click()
        assert len(calls(page,'image'))==1
        if action=='refresh': refresh(page); full(page)
        elif action=='close': close_main(page)
        else:
            page.get_by_role('button',name='切换报名',exact=True).evaluate('(el)=>el.click()')
            expect(page.locator('.student-info-panel')).to_have_count(0)
            page.get_by_role('button',name='查看学员资料',exact=True).click()
            full(page,7002)
        page.wait_for_timeout(1600)
        released(page)
        assert not blobs(page)['downloads']
        assert not page.locator('.student-info-panel img[src^="blob:"]').count()
        assert page.get_by_role('dialog').count()==(0 if action=='close' else 1)
        page.close()
    page,_ = open_panel('?image-error')
    panel = full(page)
    card = panel.locator('.attachment-card').filter(has_text='合成报名材料正面.png')
    card.get_by_role('button',name=re.compile('预览')).click()
    expect(card.get_by_text('合成附件暂时不可读取',exact=True)).to_be_visible()
    card.get_by_role('button',name='重试附件',exact=True).click()
    card.get_by_role('button',name=re.compile('预览')).click()
    expect(page.get_by_role('dialog')).to_have_count(2)
    assert len(calls(page,'image'))==2
    close_main(page,programmatic=True); released(page); page.close()
    assert not errors,errors
    browser.close()
print('PASS default authorized full data/no masked flash/no visibility controls; modal/drawer 1440/390; denied/empty/error/retry; two-step close/refresh/switch/revocation isolation; lazy PNG/PDF previews/downloads/blob cleanup; export.',OUTPUT)
