# UTF-8. Isolated synthetic responses through the existing page fixture.
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

out = Path(gettempdir()) / 'media-lead-layout-20261028'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for target, label in [('unset', '完成率 — · 目标未设置'), ('0', '完成率 — · 目标为 0'), ('20', '完成率 65.0%'), ('10', '完成率 130.0%')]:
        page.goto('http://127.0.0.1:5174/test/media-lead.html?summaryTarget=' + target)
        card = page.locator('.media-lead-overview')
        expect(card.locator('.media-lead-summary-card')).to_have_count(2)
        expect(card.locator('.media-lead-completion-label')).to_contain_text(label)
        expect(card.locator('.media-lead-current-period')).to_have_count(2)
        expect(card.locator('.media-lead-current-period td:first-child')).to_have_text(['今日', '本月'])
        expect(card.get_by_text('重点', exact=True)).to_have_count(0)
        expect(card.locator('details')).not_to_have_attribute('open', '')
        card.locator('summary').focus()
        page.keyboard.press('Enter')
        expect(card.locator('details')).to_have_attribute('open', '')
        expect(card.get_by_text('成交须有关联', exact=False)).to_be_visible()
        page.keyboard.press('Enter')
        expect(card.locator('details')).not_to_have_attribute('open', '')
        widths = card.locator('.media-lead-summary-card').evaluate_all('(els) => els.map(el => el.getBoundingClientRect().width)')
        assert abs(widths[0] - widths[1]) < 1, widths
        background = card.locator('.media-lead-current-period td').first.evaluate('(el) => getComputedStyle(el).backgroundColor')
        assert background not in ['rgba(0, 0, 0, 0)', 'transparent']
        page.screenshot(path=str(out / ('desktop-' + target + '.png')))
    page.set_viewport_size({'width': 390, 'height': 844})
    page.goto('http://127.0.0.1:5174/test/media-lead.html?summaryTarget=unset')
    expect(page.locator('.media-lead-summary-value').first).to_have_text('未设置')
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.screenshot(path=str(out / 'mobile.png'))
    page.locator('.media-lead-methodology summary').click()
    expect(page.locator('.media-lead-methodology')).to_have_attribute('open', '')
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    assert not errors, errors
    browser.close()
print('PASS: summary target states, equal cards, highlighted rows, keyboard disclosure, desktop/mobile; screenshots:', out)
