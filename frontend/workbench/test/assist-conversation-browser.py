# UTF-8. Real Chrome, synthetic API responses; no shared state writes.
from pathlib import Path
import re
from playwright.sync_api import sync_playwright, expect
OUT = Path('D:/ZSJ-OS-backups/assist-conversation')
OUT.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1100})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.route('**/test/assist-image.png', lambda r: r.fulfill(content_type='image/svg+xml', body='<svg xmlns="http://www.w3.org/2000/svg" width="120" height="90"><rect width="120" height="90" fill="lightblue"/></svg>'))
    page.route('**/test/assist-file.pdf', lambda r: r.fulfill(content_type='application/pdf', body=b'%PDF-1.4\n%%EOF'))
    page.goto('http://127.0.0.1:5174/test/assist-conversation.html')
    for value in ['申请备注完整保留', '第一行问题', '实际回复人', '申请资料.png', '回复资料.pdf']:
        expect(page.get_by_text(value, exact=False).first).to_be_visible()
    expect(page.locator('.is-request .lead-assist-message-meta').first).to_contain_text('发起销售')
    expect(page.locator('.is-response .lead-assist-message-meta')).to_contain_text('2026-09-22 11:00')
    assert page.locator('.lead-assist-bubble dd').first.evaluate('(e) => getComputedStyle(e).whiteSpace') == 'pre-wrap'
    page.screenshot(path=str(OUT / 'desktop.png'), full_page=True)
    page.get_by_role('button', name='预览图片：申请资料.png').click()
    expect(page.get_by_role('dialog')).to_be_visible()
    page.get_by_role('dialog').get_by_role('button', name=re.compile('关\\s*闭')).click()
    expect(page.get_by_role('dialog')).not_to_be_visible()
    page.set_viewport_size({'width': 390, 'height': 844})
    expect(page.get_by_text('申请备注完整保留')).to_be_visible()
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.screenshot(path=str(OUT / 'mobile.png'), full_page=True)
    page.get_by_role('button', name='填写回复').click()
    dialog = page.get_by_role('dialog')
    dialog.get_by_placeholder('填写协助备注').fill('回复第一行\n回复第二行')
    dialog.locator('input[type=file]').set_input_files({'name': '补充.png', 'mimeType': 'image/png', 'buffer': b'test'})
    page.evaluate('assistFixture.replyError = true')
    dialog.get_by_role('button', name='发送回复').click()
    expect(page.get_by_text('回复保存失败，请重试')).to_be_visible()
    expect(dialog.get_by_placeholder('填写协助备注')).to_have_value('回复第一行\n回复第二行')
    first_key = page.evaluate('assistFixture.payload.idempotencyKey')
    page.evaluate('assistFixture.replyError = false')
    dialog.get_by_role('button', name='发送回复').click()
    expect(dialog).not_to_be_visible()
    expect(page.locator('.lead-assist-bubble dd').filter(has_text='回复第一行')).to_be_visible()
    assert page.evaluate('assistFixture.uploads') == 1
    assert page.evaluate('assistFixture.payload.attachments') == [{'infraFileId': 33}]
    assert page.evaluate('assistFixture.payload.idempotencyKey') == first_key
    page.locator('.ant-pagination-item-2').click()
    expect(page.get_by_text('下一页申请')).to_be_visible()
    page.evaluate('assistFixture.error = "无权查看协助历史"')
    page.get_by_role('button', name=re.compile('刷\\s*新')).click()
    expect(page.get_by_text('无权查看协助历史')).to_be_visible()
    expect(page.locator('.lead-assist-conversation')).to_have_count(0)
    page.evaluate('assistFixture.error = ""; assistFixture.empty = true')
    page.get_by_role('button', name=re.compile('重\\s*试')).click()
    expect(page.get_by_text('暂无协助历史')).to_be_visible()
    page.goto('http://127.0.0.1:5174/test/assist-conversation.html?readonly')
    expect(page.get_by_text('等待协助回复')).to_be_visible()
    expect(page.get_by_role('button', name='填写回复')).to_have_count(0)
    assert not errors, errors
    browser.close()
print('PASS: desktop/mobile, full forms, attachment preview, reply retry/upload reference, pagination, denied/empty/read-only')
