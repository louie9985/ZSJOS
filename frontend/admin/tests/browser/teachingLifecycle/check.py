# -*- coding: utf-8 -*-
"""Admin production component and API with an isolated request adapter."""
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect
OUT = Path(gettempdir()) / 'zsjos-teaching-service-lifecycle'
OUT.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for width in (1440, 390):
        page.set_viewport_size({'width': width, 'height': 900})
        page.goto('http://127.0.0.1:5203')
        page.get_by_role('button', name='关闭服务', exact=True).click()
        dialog = page.get_by_role('dialog')
        dialog.locator('textarea').fill('学员确认关闭')
        dialog.get_by_role('button', name=re.compile('确.*认')).click()
        expect(page.get_by_text(re.compile('已关闭 · 异常结业'))).to_be_visible()
        assert page.evaluate('teachingAdminFixture.requests[0].url') == '/zsjos/student/service/1/close'
        page.get_by_role('button', name='恢复服务', exact=True).click()
        dialog = page.get_by_role('dialog')
        expect(dialog.locator('.el-select')).to_contain_text('原服务班')
        dialog.locator('textarea').fill('学员确认恢复')
        page.wait_for_timeout(400)
        page.screenshot(path=str(OUT / f'admin-restore-{width}.png'), full_page=True)
        dialog.get_by_role('button', name=re.compile('确.*认')).click()
        expect(page.get_by_role('button', name='关闭服务', exact=True)).to_be_visible()
        assert page.evaluate('teachingAdminFixture.requests[1].data.targetClassId') == 10
    for width in (1440, 390):
        page.set_viewport_size({'width': width, 'height': 900})
        page.goto('http://127.0.0.1:5203?closed')
        page.evaluate('teachingAdminFixture.holdTargets = true')
        page.get_by_role('button', name='恢复服务', exact=True).click()
        page.wait_for_function('teachingAdminFixture.targetRequests.length === 1')
        page.locator('.el-dialog:visible .el-dialog__footer button').first.click()
        expect(page.get_by_role('dialog')).not_to_be_visible()
        page.get_by_role('button', name='恢复服务', exact=True).click()
        page.wait_for_function('teachingAdminFixture.targetRequests.length === 2')
        page.evaluate('teachingAdminFixture.targetRequests[1](teachingAdminFixture.targets)')
        page.locator('.el-dialog:visible .el-select').click()
        page.get_by_role('option', name='新服务班 · BJ11 · 新规划师', exact=True).click()
        page.evaluate('teachingAdminFixture.targetRequests[0](teachingAdminFixture.targets)')
        expect(page.locator('.el-dialog:visible .el-select')).to_contain_text('新服务班')
        page.locator('.el-dialog:visible textarea').fill('按新选择恢复')
        page.locator('.el-dialog:visible .el-dialog__footer button').last.click()
        page.wait_for_function('teachingAdminFixture.requests.length === 1')
        assert page.evaluate('teachingAdminFixture.requests[0].data.targetClassId') == 11
    page.goto('http://127.0.0.1:5203?fail')
    page.get_by_role('button', name='关闭服务', exact=True).click()
    dialog = page.get_by_role('dialog')
    dialog.locator('textarea').fill('保留失败重试原因')
    dialog.get_by_role('button', name=re.compile('确.*认')).click()
    expect(page.get_by_text('审批取消失败，请重试')).to_be_visible()
    expect(dialog.locator('textarea')).to_have_value('保留失败重试原因')
    dialog.get_by_role('button', name=re.compile('确.*认')).click()
    expect(dialog).not_to_be_visible()
    assert page.evaluate('teachingAdminFixture.requests[0].data.idempotencyKey === teachingAdminFixture.requests[1].data.idempotencyKey')
    page.goto('http://127.0.0.1:5203?denied')
    expect(page.get_by_role('button', name='关闭服务', exact=True)).to_have_count(0)
    page.set_viewport_size({'width': 1440, 'height': 1000})
    page.goto('http://127.0.0.1:5203?classes')
    row = page.locator('.el-table__body tr').filter(has_text='EX20')
    expect(row).to_contain_text('规划师')
    expect(row.get_by_role('button', name='编辑', exact=True)).to_have_count(0)
    row.click()
    expect(page.locator('.el-drawer').get_by_text('已关闭', exact=True)).to_be_visible()
    expect(page.locator('.el-drawer').get_by_role('button', name='直接调班', exact=True)).to_have_count(0)
    assert not errors, errors
    browser.close()
    print('PASS Admin close/restore transport, default target, reasons, denied/retry and 1440/390 layouts')
