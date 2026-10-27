# -*- coding: utf-8 -*-
from pathlib import Path
import re
from playwright.sync_api import sync_playwright, expect
OUT = Path('D:/ZSJ-OS-backups/lead-task-indicators'); OUT.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    errors = []
    for layout in ['split', 'table']:
        for width in [1440, 390]:
            page = browser.new_page(viewport={'width': width, 'height': 1000})
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.route('**/admin-api/**', lambda route: route.fulfill(json={'code': 0, 'data': []}))
            page.goto(f'http://127.0.0.1:5174/test/lead-task-indicators.html?layout={layout}')
            marker = page.locator('.lead-task-indicator')
            expect(marker).to_have_count(2)
            if layout == 'split' or width < 768:
                expect(page.locator('.lead-inbox-item').first).to_have_attribute('aria-label', re.compile('.*KZ-TEST-2.*'))
                cards = page.locator('.lead-inbox-item')
                expect(cards).to_have_count(3)
                expect(page.locator('.lead-inbox-item.has-pending-tasks')).to_have_count(2)
                first = page.locator('.lead-inbox-item[aria-label*="KZ-TEST-1"]')
                second = page.locator('.lead-inbox-item[aria-label*="KZ-TEST-2"]')
                expect(page.locator('.lead-inbox-item[aria-label*="KZ-TEST-2"]')).to_have_class(re.compile('.*active.*'))
                expect(first).to_have_class(re.compile('.*unseen.*'))
                expect(second).to_contain_text('已逾期：客资判定')
                second.click()
                if width == 390:
                    page.get_by_role('button', name='关闭', exact=True).click()
                expect(second).not_to_have_class(re.compile('.*unseen.*'))
                expect(second).to_have_class(re.compile('.*has-pending-tasks.*'))
                border = second.locator('.lead-task-border')
                assert border.evaluate("e => getComputedStyle(e, '::before').animationName") == 'lead-task-border-flow'
                before = border.evaluate("e => getComputedStyle(e, '::before').transform")
                page.wait_for_timeout(200)
                assert before != border.evaluate("e => getComputedStyle(e, '::before').transform")
                phases = border.evaluate("""e => {
                    const animation = e.getAnimations({subtree: true}).find(a => a.animationName === 'lead-task-border-flow');
                    animation.pause();
                    const at = time => { animation.currentTime = time; const m = new DOMMatrixReadOnly(getComputedStyle(e, '::before').transform); return [m.a, m.b, m.c, m.d, m.e, m.f]; };
                    const result = [at(0), at(2999), at(3000), at(3001)];
                    animation.play(); return result;
                }""")
                assert all(abs(a - b) < 0.001 for a, b in zip(phases[0], phases[2])), phases
                assert all(abs(a - b) < 0.01 for a, b in zip(phases[1], phases[3])), phases
                page.emulate_media(reduced_motion='reduce')
                expect(border).to_be_hidden()
                assert border.evaluate("e => getComputedStyle(e, '::before').animationName") == 'none'
                page.emulate_media(reduced_motion='no-preference')
                page.screenshot(path=str(OUT / f'{layout}-{width}.png'), full_page=True)
                page.get_by_role('button', name='收起客资列表').click()
                expect(page.locator('.lead-inbox-item.has-pending-tasks')).to_have_count(2)
                expect(second).to_have_attribute('aria-label', re.compile('.*客资判定.*'))
                expect(second.locator('.lead-task-border')).to_be_visible()
                box = second.locator('.lead-task-border').bounding_box()
                size = second.locator('.lead-task-border').evaluate("e => parseFloat(getComputedStyle(e, '::before').width)")
                assert size >= max(box['width'], box['height'])
            else:
                expect(page.locator('.lead-task-indicator.is-compact')).to_have_count(2)
                expect(page.locator('tbody tr.ant-table-row').first).to_contain_text('KZ-TEST-2')
                page.locator('.lead-task-indicator[aria-label^="待处理 2"]').focus()
                tooltip = page.get_by_role('tooltip').last
                expect(tooltip).to_contain_text('首次跟进')
                expect(tooltip).to_contain_text('已逾期：客资判定')
                expect(tooltip).to_contain_text('截止')
                page.screenshot(path=str(OUT / f'{layout}-{width}.png'), full_page=True)
            page.evaluate('taskFixture.completed = true; taskFixture.invalidate()')
            expect(marker).to_have_count(0)
            if layout == 'split' or width < 768:
                expect(page.locator('.lead-inbox-item').first).to_have_attribute('aria-label', re.compile('.*KZ-TEST-3.*'))
            else:
                expect(page.locator('tbody tr.ant-table-row').first).to_contain_text('KZ-TEST-3')
            expect(page.locator('.lead-inbox-item.has-pending-tasks')).to_have_count(0)
            expect(page.locator('.lead-task-border')).to_have_count(0)
            page.evaluate('taskFixture.completed = false; taskFixture.invalidate()')
            if layout == 'split' or width < 768:
                expect(page.locator('.lead-inbox-item.has-pending-tasks')).to_have_count(2)
            else:
                expect(marker).to_have_count(2)
            if layout == 'split' or width < 768:
                page.get_by_role('button', name='展开客资列表').focus()
                page.keyboard.press('Enter')
            page.evaluate('taskFixture.fail = true; taskFixture.invalidate()')
            expect(page.get_by_text('测试列表读取失败', exact=True).first).to_be_visible()
            page.evaluate('taskFixture.fail = false; taskFixture.invalidate()')
            expect(page.get_by_text('测试列表读取失败', exact=True)).to_have_count(0)
            print(f'PASS {layout} {width}: reasons, selection, animation/reduced motion, refresh/clear/recovery')
            page.close()
    assert not errors, errors
    browser.close()
