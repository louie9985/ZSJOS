# -*- coding: utf-8 -*-
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
OUT = Path(__file__).resolve().parents[1] / 'tmp/stage-duration-browser'
OUT.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto('http://127.0.0.1:5189/test/stage-duration.html')
        expect(page.get_by_text('已维持 0 天', exact=True)).to_have_count(2)
        expect(page.get_by_text('已维持 12 天', exact=True)).to_have_count(2)
        expect(page.get_by_text('阶段时长未记录', exact=True)).to_have_count(2)
        tag = page.get_by_text('已维持 12 天', exact=True).first
        expect(tag).to_have_class(__import__('re').compile('ant-tag-blue'))
        expect(tag).to_have_attribute('aria-label', __import__('re').compile('根据诊断历史恢复'))
        if width == 1440:
            tag.hover()
            expect(page.get_by_role('tooltip')).to_contain_text('截至本轮提交时：2026-10-21 00:01:00')
            page.mouse.move(0, 0)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'horizontal overflow'
        # Browser clock changes cannot advance frozen days.
        page.clock.install(time=__import__('datetime').datetime(2030, 1, 1))
        page.get_by_role('button', name='刷新账号资料').click()
        expect(page.get_by_text('已维持 12 天', exact=True)).to_have_count(2)
        page.screenshot(path=str(OUT / f'{width}.png'), full_page=True)
        assert not errors, errors
        page.close()
    browser.close()
print('Chrome PASS: desktop/mobile, recorded/recovered/missing, tooltip, preview refresh, frozen clock, no overflow')
