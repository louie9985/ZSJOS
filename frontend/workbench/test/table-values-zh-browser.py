# UTF-8. Existing Playwright + local Chrome; all API responses come from the isolated fixture.
import json
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.gettempdir()) / 'zsjos-table-values-zh'
out.mkdir(parents=True, exist_ok=True)
base = os.environ.get('TABLE_TEST_BASE', 'http://localhost:5175')
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(base + '/test/table-values-zh.html')
    export = page.locator('[data-table-key="export-task-page-1"]')
    for label in ['财务订单', '返现', '提现', '未知类型', '未知状态', '—']:
        expect(export.get_by_text(label, exact=True).first).to_be_visible()
    expect(export).not_to_contain_text('future_type')
    page.screenshot(path=str(out / 'export-desktop.png'), full_page=True)
    page.set_viewport_size({'width': 390, 'height': 844})
    expect(export.get_by_text('财务订单', exact=True)).to_be_visible()
    assert export.locator('.ant-table-body').evaluate('(e) => e.scrollWidth > e.clientWidth')
    export.locator('.ant-table-body').evaluate('(e) => { e.scrollLeft = 160 }')
    page.screenshot(path=str(out / 'export-mobile.png'), full_page=True)

    page.set_viewport_size({'width': 1440, 'height': 1000})
    page.get_by_role('button', name='审计验收', exact=True).click()
    audit = page.locator('[data-table-key="management-pages-1"]')
    for label in ['管理端', '业务操作', '成功']:
        expect(audit.get_by_text(label, exact=True)).to_be_visible()
    # Choose a Chinese option and assert that the API still receives the protocol code.
    page.locator('.ant-select').filter(has_text=re.compile('^结果$')).click()
    page.locator('.ant-select-dropdown:visible').get_by_text('失败', exact=True).click()
    page.get_by_role('button', name=re.compile(r'^查\s*询$')).click()
    expect(audit.get_by_text('失败', exact=True)).to_be_visible()
    assert json.loads(page.locator('#request-state').inner_text())['resultStatus'] == 'FAILURE'
    page.screenshot(path=str(out / 'audit-desktop.png'), full_page=True)

    page.get_by_role('button', name='通知验收', exact=True).click()
    notify = page.locator('[data-table-key="management-pages-9"]')
    expect(page.get_by_role('alert')).to_contain_text('通知场景加载失败')
    expect(notify).to_contain_text('角色名称加载失败')
    expect(notify).not_to_contain_text('owner')
    page.get_by_role('button', name=re.compile(r'重\s*试$')).click()
    expect(notify).to_contain_text('服务负责人')
    expect(notify).to_contain_text('测试通知场景')
    expect(notify).to_contain_text('站内信（含实时提醒）')
    expect(page.get_by_role('alert')).to_have_count(0)
    page.screenshot(path=str(out / 'notify-desktop.png'), full_page=True)
    page.set_viewport_size({'width': 390, 'height': 844})
    page.screenshot(path=str(out / 'notify-mobile.png'), full_page=True)

    page.get_by_role('button', name='计划验收', exact=True).click()
    plan = page.locator('[data-table-key="configuration-pages-10"]')
    for label in ['测试计划类型', '季度', '草稿', '已发布']:
        expect(plan.get_by_text(label, exact=True)).to_be_attached()
    page.screenshot(path=str(out / 'plan-mobile.png'), full_page=True)
    page.set_viewport_size({'width': 1440, 'height': 1000})
    page.screenshot(path=str(out / 'plan-desktop.png'), full_page=True)
    assert not errors, errors
    browser.close()
print(json.dumps({'result': 'passed', 'widths': [1440, 390], 'screenshots': str(out), 'consoleErrors': errors}, ensure_ascii=False))
