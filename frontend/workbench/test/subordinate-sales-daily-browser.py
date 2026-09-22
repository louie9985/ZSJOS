# UTF-8. Real Chrome with isolated component fixtures, no shared state writes.
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path('D:/ZSJ-OS-backups/subordinate-sales-daily')
out.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto('http://127.0.0.1:5174/test/subordinate-sales-daily.html')
    cards = page.locator('.subordinate-sales-item')
    expect(cards).to_have_count(4)
    expect(cards.nth(0)).to_contain_text('2 / 8')
    expect(cards.nth(1).locator('.subordinate-sales-item-title .ant-tag-success')).to_have_text('已完成')
    expect(cards.nth(2)).to_contain_text('已完成')
    expect(cards.nth(3)).to_contain_text('状态待更新')
    expect(cards.nth(0).locator('.subordinate-sales-daily-metric')).to_have_count(8)
    for width in [1440, 1024, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        cards.first.click()
        expect(cards.first).to_have_attribute('aria-pressed', 'true')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        assert cards.first.evaluate('(el) => el.scrollWidth <= el.clientWidth')
        height = cards.first.bounding_box()['height']
        assert height <= 290, f'card height {height} at {width}'
        assert cards.first.locator('.subordinate-sales-daily-metric').evaluate_all('(els) => els.every(el => el.scrollWidth <= el.clientWidth)')
        page.screenshot(path=str(out / f'{width}.png'), full_page=True)
        print(f'{width}: card height {height:.1f}, eight metrics visible, no horizontal overflow')
    cards.nth(1).click()
    expect(cards.first).to_have_attribute('aria-pressed', 'false')
    expect(cards.nth(1)).to_have_attribute('aria-pressed', 'true')
    assert not errors, errors
    browser.close()
