# UTF-8. Synthetic transport; real React/Vue form controls, no business database writes.
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

out = Path(gettempdir()) / 'zsjos-order-contacts'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    page.set_default_timeout(15000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for width in (1440, 390):
        page.set_viewport_size({'width': width, 'height': 1000})
        for mode in ('fresh', 'draft', 'locked', 'blank'):
            page.goto(f'http://127.0.0.1:5195/test/order-contacts.html?mode={mode}')
            mobile = page.get_by_label('手机号', exact=True)
            wechat = page.get_by_label('微信号', exact=True)
            expect(mobile).to_have_value('' if mode == 'blank' else '13800138000')
            expect(wechat).to_have_value({'fresh': 'lead-wx', 'draft': 'draft-wx', 'locked': '', 'blank': ''}[mode])
            expect(page.get_by_label('教材邮递地址', exact=True)).to_have_count(1)
            if mode == 'locked':
                expect(mobile).to_be_disabled()
                expect(wechat).to_be_enabled()
                expect(page.get_by_role('spinbutton').first).to_be_disabled()
                page.get_by_role('button', name='提交审批', exact=True).click()
                expect(page.get_by_text('请填写微信号', exact=True)).to_be_visible()
                wechat.fill(' completed-wx ')
                page.locator('input[type=file]').set_input_files({'name': 'voucher.pdf', 'mimeType': 'application/pdf', 'buffer': b'%PDF-1.4 test'})
                page.get_by_role('button', name='提交审批', exact=True).click()
                page.get_by_role('button', name='确认执行', exact=True).click()
                page.wait_for_function('contactFixture.orders.length === 1')
                request = page.evaluate('contactFixture.orders[0]')
                assert request['studentMobile'] == '13800138000' and request['studentWechatId'] == 'completed-wx'
            if mode == 'draft':
                mobile.fill('  ')
                page.get_by_role('button', name='提交审批', exact=True).click()
                expect(page.get_by_text('请填写手机号', exact=True)).to_be_visible()
                mobile.fill('13900139000')
                wechat.fill('  ')
                page.get_by_role('button', name='提交审批', exact=True).click()
                expect(page.get_by_text('请填写微信号', exact=True)).to_be_visible()
                wechat.fill('user-wx')
                page.get_by_role('button', name='刷新客资资料', exact=True).evaluate('(button) => button.click()')
                expect(wechat).to_have_value('user-wx')
                expect(mobile).to_have_value('13900139000')
            page.screenshot(path=str(out / f'workbench-{mode}-{width}.png'), full_page=True)
        for app, port in (('workbench', 5195), ('admin', 5196)):
            page.goto(f'http://127.0.0.1:{port}/test/repurchase-customer.html')
            if app == 'admin': page.get_by_role('button', name='打开复购').click()
            page.get_by_label('客户姓名', exact=True).fill('测试客户')
            page.get_by_label('手机号', exact=True).fill('13800138000')
            page.get_by_role('button', name='校验客户').click()
            expect(page.get_by_text('请补齐手机号和微信号后重新校验，再填写复购订单', exact=True)).to_be_visible()
            page.get_by_label('微信号', exact=True).fill('new-wx')
            page.get_by_role('button', name='校验客户').click()
            expect(page.get_by_text('已识别客户，本次订单归属当前录单人', exact=True)).to_be_visible()
            if app == 'workbench':
                page.get_by_role('button', name='确认客户，填写复购订单').click()
                expect(page.get_by_role('dialog').get_by_label('微信号', exact=True)).to_have_value('new-wx')
            else:
                expect(page.get_by_label('学员姓名', exact=True)).to_have_value('测试客户')
            page.screenshot(path=str(out / f'{app}-repurchase-{width}.png'), full_page=True)
        for mode in ('offline', 'online'):
            page.goto(f'http://127.0.0.1:5195/test/order-revision.html?mode={mode}')
            expect(page.get_by_label('手机号', exact=True)).to_have_value('13800138000')
            expect(page.get_by_label('微信号', exact=True)).to_have_value('lead-wx')
            page.get_by_label('微信号', exact=True).fill('  ')
            page.get_by_role('button', name='重新提交审批', exact=True).click()
            expect(page.get_by_text('请填写微信号', exact=True)).to_be_visible()
            page.screenshot(path=str(out / f'workbench-revision-{mode}-{width}.png'), full_page=True)
            page.goto(f'http://127.0.0.1:5196/test/order-revision.html?mode={mode}')
            page.get_by_role('button', name='查看', exact=True).first.click()
            page.get_by_role('button', name='补正并重新提交', exact=True).click()
            dialog = page.get_by_role('dialog', name='补正并重新提交', exact=True)
            box = dialog.bounding_box()
            assert box and box['x'] >= 0 and box['x'] + box['width'] <= width + 1, box
            expect(dialog.get_by_label('手机号', exact=True)).to_have_value('13900139000')
            expect(dialog.get_by_label('微信号', exact=True)).to_have_value('lead-wx')
            dialog.get_by_label('手机号', exact=True).fill(' ')
            dialog.get_by_role('button', name='重新提交', exact=True).click()
            expect(page.get_by_text('请填写手机号', exact=True)).to_be_visible()
            dialog.get_by_label('手机号', exact=True).fill('13900139000')
            dialog.get_by_label('微信号', exact=True).fill(' ')
            dialog.get_by_role('button', name='重新提交', exact=True).click()
            expect(page.get_by_text('请填写微信号', exact=True)).to_be_visible()
            page.screenshot(path=str(out / f'admin-revision-{mode}-{width}.png'), full_page=True)
    assert not errors, errors
    browser.close()
print('PASS: dual required fields, Lead/draft restoration, user input retention, locked draft completion, both preflight forms, 1440/390')
