# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright, expect

output = 'C:/Users/EDY/.codex/visualizations/2026/10/28/01a18116-5acf-77f3-b5b0-0b27750ebb50/'
with sync_playwright() as pw:
    browser = pw.chromium.launch(headless=True, executable_path='C:/Users/EDY/AppData/Local/ms-playwright/chromium_headless_shell-1223/chrome-headless-shell-win64/chrome-headless-shell.exe')
    page = browser.new_page(viewport={'width': 1000, 'height': 850}, device_scale_factor=1)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto('http://localhost:5174/test/lead-sort-preview.html')
    expect(page.locator('.preview-count')).to_contain_text('筛选结果 2 条')
    expect(page.locator('.advanced-filter-tags')).to_contain_text('示例员工')
    page.screenshot(path=output + 'lead-sort-real-desktop.png', full_page=True)
    page.get_by_role('combobox', name='客资排序方向').click()
    page.get_by_title('升序 ↑', exact=True).click()
    expect(page.locator('.lead-inbox-item').first).to_contain_text('示例客户 B')
    page.get_by_role('button', name='清空全部').click()
    expect(page.locator('.preview-count')).to_contain_text('全部客资 3 条')
    page.get_by_role('combobox', name='客资排序字段').click()
    page.get_by_title('默认排序', exact=True).click()
    expect(page.get_by_role('combobox', name='客资排序方向')).to_be_disabled()
    page.get_by_role('button', name='高级筛选', exact=True).click()
    expect(page.get_by_text('高级筛选', exact=True)).to_be_visible()
    page.locator('.advanced-filter-footer button').nth(1).click()
    page.set_viewport_size({'width': 375, 'height': 850})
    page.reload()
    expect(page.locator('.preview-count')).to_contain_text('筛选结果 2 条')
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Horizontal overflow'
    page.screenshot(path=output + 'lead-sort-real-mobile.png', full_page=True)
    for width in [1440, 375]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.route('**/admin-api/**', lambda route: route.fulfill(json={'code': 0, 'data': []}))
        page.goto('http://localhost:5174/test/lead-follow-up-refresh.html?layout=split')
        expect(page.locator('.lead-inbox-result-count')).to_contain_text('当前结果 1 条')
        page.evaluate("""async () => { const {api} = await import('/src/services/api.ts'); const original = api.allLeadPage; window.sortQueries=[]; api.allLeadPage = q => { window.sortQueries.push(q); return original(q) } }""")
        page.get_by_role('combobox', name='客资排序字段').click()
        page.get_by_title('最近活动时间', exact=True).click()
        page.wait_for_function("sortQueries.some(r => r.sortField === 'lastActivityAt' && r.sortOrder === 'descend')")
        page.get_by_role('combobox', name='客资排序方向').click()
        page.get_by_title('升序 ↑', exact=True).click()
        page.wait_for_function("sortQueries.some(r => r.sortField === 'lastActivityAt' && r.sortOrder === 'ascend')")
        expect(page.locator('.lead-inbox-result-count')).to_contain_text('已加载 1 条')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Page horizontal overflow'
        page.screenshot(path=output + f'lead-sort-integrated-{width}.png', full_page=True)
    assert not errors, errors
    browser.close()
    print('PASS: real components, sort direction/default, filter clear/drawer, desktop/mobile, no page errors')
