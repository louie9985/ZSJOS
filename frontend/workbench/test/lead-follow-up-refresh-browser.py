# UTF-8. Real Chrome against isolated component fixture; no business writes.
import re
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={"width":1600,"height":1100})
    page.set_default_timeout(8000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/admin-api/**', lambda route: route.fulfill(json={"code":0,"data":[]}))
    page.goto('http://127.0.0.1:5197/test/lead-follow-up-refresh.html?leadId=1&tab=follow-ups')
    management = page.locator('.lead-management-page')
    expect(management.get_by_role('tab', name='跟进记录 (0)')).to_be_visible()
    draft = management.get_by_label('跟进备注', exact=True)
    draft.fill('尚未提交的草稿')
    # An external write without an event must be picked up by manual refresh.
    page.evaluate("refreshFixture.records.push({id:1,leadId:1,remark:'外部新增记录',occurredAt:Date.now(),images:[]})")
    management.locator('.lead-filter-actions button').click()
    expect(management.get_by_text('外部新增记录', exact=True)).to_be_visible()
    expect(management.get_by_role('tab', name='跟进记录 (1)')).to_be_visible()
    expect(draft).to_have_value('尚未提交的草稿')
    # Keep management mounted but hidden, as the real page host does.
    page.get_by_role('button', name='切换测试页面').click()
    page.locator('.lead-calendar-count').click()
    page.get_by_role('button', name='填写跟进记录').click()
    dialog = page.get_by_role('dialog', name='新增跟进', exact=True)
    dialog.get_by_role('radiogroup', name='跟进方式').locator('label').first.click()
    dialog.get_by_role('radiogroup', name='跟进结果').locator('label').first.click()
    dialog.locator('textarea').fill('日历新增记录')
    dialog.get_by_role('button', name=re.compile('提交')).click()
    expect(dialog).not_to_be_visible()
    expect(management.get_by_role('tab', name='跟进记录 (2)', include_hidden=True)).to_have_count(1)
    page.get_by_role('dialog').get_by_role('button', name='Close', exact=True).click()
    page.get_by_role('button', name='切换测试页面').click()
    expect(management.get_by_text('日历新增记录', exact=True)).to_be_visible()
    expect(draft).to_have_value('尚未提交的草稿')
    # Failed refresh is actionable and retry retrieves an empty result/count.
    page.evaluate('refreshFixture.fail = true')
    management.locator('.lead-filter-actions button').click()
    expect(management.get_by_text('测试记录加载失败', exact=True)).to_be_visible()
    expect(draft).to_have_value('尚未提交的草稿')
    page.evaluate('refreshFixture.fail = false; refreshFixture.records = []')
    management.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(management.get_by_role('tab', name='跟进记录 (0)')).to_be_visible()
    expect(management.get_by_text('暂无跟进记录', exact=True)).to_be_visible()
    expect(draft).to_have_value('尚未提交的草稿')
    assert page.evaluate('refreshFixture.posts') == 1
    assert not errors, errors
    print('PASS: manual refresh; calendar submission across retained pages; counts; draft preservation; error/retry/empty; no page errors')
    # Table toolbar refresh uses the same invalidation even while the detail drawer is closed.
    page.goto('http://127.0.0.1:5197/test/lead-follow-up-refresh.html?leadId=1&tab=follow-ups&layout=table')
    expect(page.get_by_role('tab', name='跟进记录 (0)')).to_be_visible()
    page.get_by_role('button', name='关闭', exact=True).click()
    page.evaluate("refreshFixture.records.push({id:1,leadId:1,remark:'表格刷新记录',occurredAt:Date.now(),images:[]})")
    before = page.evaluate('refreshFixture.reads')
    page.locator('.ant-pro-table-list-toolbar [aria-label="reload"]').click()
    page.wait_for_function('refreshFixture.reads > ' + str(before))
    page.get_by_role('button', name=re.compile(r'详\s*细')).click()
    expect(page.get_by_role('tab', name='跟进记录 (1)')).to_be_visible()
    expect(page.get_by_text('表格刷新记录', exact=True)).to_be_visible()
    assert not errors, errors
    print('PASS: table toolbar refresh updates retained drawer history/count')
    browser.close()
