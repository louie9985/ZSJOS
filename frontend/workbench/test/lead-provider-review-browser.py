# UTF-8. Synthetic response verification, not authenticated backend acceptance.
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path('D:/ZSJ-OS-backups/lead-provider-review')
out.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto('http://127.0.0.1:5174/test/lead-provider-review.html')
    labels = ['验证新媒体 · 新媒体提供方', '名称待核实 · 兼职', '销售本人（未关联新媒体）',
              '教务本人（未关联新媒体）', '提供方待核实', '名称待核实 · 员工（身份待核实）', '历史提供方 · 身份待核实', '已隐藏 · 兼职']
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        for i, label in enumerate(labels):
            page.get_by_role('button', name=f'场景{i + 1}', exact=True).click()
            row = page.locator('.lead-profile-row').filter(has=page.get_by_text('提供方', exact=True))
            expect(row.locator('.lead-field-value')).to_have_text(label)
            expect(row).to_be_visible()
            expect(page.get_by_text('业务来源', exact=True)).to_be_visible()
            expect(page.get_by_text('来源人员', exact=True)).to_be_visible()
            if i == 0:
                expect(page.get_by_text('验证销售', exact=True)).to_be_visible()
                page.screenshot(path=str(out / f'provider-media-{width}.png'), full_page=True)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.screenshot(path=str(out / f'provider-{width}.png'), full_page=True)
    assert not errors, errors
    browser.close()
print('PASS: eight provider states at desktop and mobile widths')
