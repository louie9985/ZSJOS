# -*- coding: utf-8 -*-
"""Real Chromium, production page, synthetic API transport, no live mutations."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(__file__).resolve().parents[3] / 'output'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto('http://127.0.0.1:5174/test/material-review.html')
        expect(page.get_by_role('button', name='修订审核中', exact=False)).to_be_visible()
        expect(page.get_by_text('审核人：审核员甲、审核员乙')).to_be_visible()
        page.screenshot(path=str(out / f'material-review-mine-{width}.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'horizontal overflow'
        page.get_by_role('combobox', name='审核状态').click()
        page.locator('.ant-select-item-option-content').filter(has_text='待审核／审批中').click()
        expect(page.locator('html')).to_have_attribute('data-filter', 'IN_APPROVAL')
        expect(page.locator('.material-library-item')).to_have_count(1)
        page.get_by_role('button', name='修订审核中', exact=False).click()
        expect(page.locator('.resource-link-card')).to_have_count(2)
        expect(page.get_by_role('link', name='example.com/home', exact=False)).to_have_attribute('href', 'https://example.com/home')
        page.wait_for_function("Math.abs(document.querySelector('.ant-drawer-content-wrapper').getBoundingClientRect().right - innerWidth) < 2")
        page.screenshot(path=str(out / f'material-review-detail-{width}.png'), full_page=True, animations='disabled')
        page.get_by_role('button', name='版本记录').click()
        expect(page.locator('.ant-list-item')).to_contain_text('V2')
        page.get_by_role('dialog', name='素材版本').locator('.ant-drawer-close').click()
        page.get_by_role('button', name='撤回审批', exact=True).click()
        expect(page.get_by_role('button', name='确认撤回')).to_be_disabled()
        page.get_by_role('textbox', name='撤回原因').fill('补充拆解依据')
        page.get_by_role('button', name='确认撤回').click()
        expect(page.locator('html')).to_have_attribute('data-cancelled', 'true')
        expect(page.get_by_role('button', name='继续编辑')).to_be_visible()
        page.get_by_role('button', name='继续编辑').click()
        page.get_by_role('button', name='提交审批').click()
        expect(page.locator('html')).to_have_attribute('data-resubmitted', '2')
        page.goto('http://127.0.0.1:5174/test/material-review.html')
        page.get_by_role('button', name='已通过拆解', exact=False).click()
        page.get_by_role('button', name='修改并重新送审').click()
        page.get_by_role('button', name='提交审批').click()
        expect(page.locator('html')).to_have_attribute('data-resubmitted', '3')
        page.get_by_role('tab', name='全部', exact=True).click()
        expect(page.locator('.material-library-item')).to_have_count(4)
        page.screenshot(path=str(out / f'material-review-browse-{width}.png'), full_page=True)
    for query in ['?no-cancel', '?no-action']:
        page.goto('http://127.0.0.1:5174/test/material-review.html' + query)
        page.get_by_role('button', name='修订审核中', exact=False).click()
        expect(page.get_by_role('button', name='撤回审批', exact=True)).to_have_count(0)
    page.goto('http://127.0.0.1:5174/test/material-review.html?cancel-error')
    page.get_by_role('button', name='修订审核中', exact=False).click()
    page.get_by_role('button', name='撤回审批', exact=True).click()
    page.get_by_role('textbox', name='撤回原因').fill('补充说明')
    page.get_by_role('button', name='确认撤回').click()
    expect(page.get_by_text('该流程配置不允许撤回')).to_be_visible()
    expect(page.get_by_role('textbox', name='撤回原因')).to_have_value('补充说明')
    page.get_by_role('button', name='确认撤回').click()
    expect(page.locator('html')).to_have_attribute('data-cancelled', 'true')
    assert not errors, errors
    browser.close()
print('PASS: desktop/mobile, links, revision filter, cancel reason/retry/permission, draft and published resubmission')
