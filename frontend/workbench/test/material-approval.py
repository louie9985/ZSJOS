from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    page.goto('http://127.0.0.1:5174/test/material-approval.html')
    expect(page.get_by_text('爆款账号审批与爆款内容审批')).to_be_visible()
    expect(page.get_by_text('爆款账号拆解', exact=True)).to_have_count(2)
    expect(page.get_by_text('爆款内容拆解', exact=True)).to_have_count(2)
    page.get_by_role('row').filter(has_text='爆款账号拆解验收 1').get_by_role('button', name='审批').click()
    expect(page.get_by_role('dialog')).to_be_visible()
    expect(page.get_by_role('dialog').locator('.material-approval-detail')).to_be_visible()
    expect(page.get_by_role('dialog').locator('.material-approval-visual img')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_role('link', name='主页链接（新标签页打开）')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_role('link', name='最火作品链接（新标签页打开）')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_text('本次处理记录', exact=True)).to_have_count(0)
    page.screenshot(path=str(Path(gettempdir()) / 'material-approval-1440.png'))
    page.keyboard.press('Escape')
    expect(page.get_by_role('dialog')).to_have_count(0)
    page.set_viewport_size({'width': 390, 'height': 844})
    page.get_by_role('row').filter(has_text='爆款内容拆解验收 1').get_by_role('button', name='审批').click()
    expect(page.get_by_role('dialog')).to_be_visible()
    expect(page.get_by_role('dialog').locator('.material-approval-detail')).to_be_visible()
    page.screenshot(path=str(Path(gettempdir()) / 'material-approval-390.png'))
    assert page.locator('body').evaluate('(body) => body.scrollWidth <= window.innerWidth')
    browser.close()
    print('PASS: combined approval types, wide detail modal, visual rail, links, mobile layout')
