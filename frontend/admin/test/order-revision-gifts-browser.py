"""Production Admin revision page with isolated transport; no real business writes."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(os.environ['TEMP']) / 'exception-order-gifts-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        failures = []
        page.on('pageerror', lambda error: failures.append(str(error)))
        def open_revision(scenario='default'):
            page.goto(f'http://127.0.0.1:5298/test/order-revision-gifts.html?orderId=1&scenario={scenario}')
            page.get_by_role('button', name='补正并重新提交', exact=True).click()
            dialog = page.get_by_role('dialog').filter(has=page.get_by_role('button', name='重新提交', exact=True))
            return dialog

        for scenario in ('default', 'legacy'):
            dialog = open_revision(scenario)
            expect(dialog.get_by_text('历史分类 / 历史礼品名称', exact=True)).to_be_visible()
            expect(dialog.get_by_label('礼品邮寄地址', exact=True)).to_have_value('原礼品邮寄地址')
            expect(dialog.get_by_role('button', name='重新提交', exact=True)).to_be_enabled()
            page.screenshot(path=str(out / f'{scenario}-{width}.png'), full_page=True)
            dialog.get_by_role('button', name='重新提交', exact=True).click()
            page.wait_for_function('window.orderGiftFixture.submissions.length === 1')
            payload = page.evaluate('window.orderGiftFixture.submissions[0]')
            assert payload['giftItems'] == ['old'] and payload['giftShippingAddress'] == '原礼品邮寄地址'

        dialog = open_revision()
        dialog.locator('.el-select__wrapper').click()
        page.get_by_role('option', name='新礼品', exact=True).click()
        page.keyboard.press('Escape')
        dialog.get_by_label('礼品邮寄地址', exact=True).fill('')
        dialog.get_by_role('button', name='重新提交', exact=True).click()
        expect(page.get_by_text('选择礼品后请填写邮寄地址，最多 1000 个字符', exact=True)).to_be_visible()
        assert page.evaluate('window.orderGiftFixture.submissions.length') == 0
        dialog.get_by_label('礼品邮寄地址', exact=True).fill('编辑后的地址')
        dialog.get_by_role('button', name='重新提交', exact=True).click()
        page.wait_for_function('window.orderGiftFixture.submissions.length === 1')
        assert page.evaluate('window.orderGiftFixture.submissions[0].giftItems') == ['old', 'new']

        dialog = open_revision()
        dialog.locator('.el-tag__close').click()
        dialog.get_by_role('button', name='重新提交', exact=True).click()
        page.wait_for_function('window.orderGiftFixture.submissions.length === 1')
        assert page.evaluate('window.orderGiftFixture.submissions[0].giftItems') == []

        open_revision('invalid')
        expect(page.get_by_text('历史礼品信息无法读取，请先核实订单后再补正', exact=True)).to_be_visible()
        assert page.get_by_role('button', name='重新提交', exact=True).count() == 0
        assert page.evaluate('window.orderGiftFixture.submissions.length') == 0

        dialog = open_revision('failure')
        expect(dialog.get_by_text('礼品目录加载失败，可保留或移除原礼品，新增礼品请重试。', exact=True)).to_be_visible()
        page.evaluate('window.orderGiftFixture.fail=false')
        dialog.get_by_role('button', name='重试加载礼品', exact=True).click()
        expect(dialog.locator('.el-alert').filter(has_text='礼品目录加载失败')).to_have_count(0)

        dialog = open_revision('forbidden')
        expect(dialog.get_by_text('无权访问礼品目录，可保留或移除原礼品。', exact=True)).to_be_visible()
        dialog.get_by_role('button', name='重新提交', exact=True).click()
        page.wait_for_function('window.orderGiftFixture.submissions.length === 1')
        assert page.evaluate('window.orderGiftFixture.submissions[0].giftItems') == ['old']

        dialog = open_revision('empty')
        expect(dialog.get_by_text('暂无可选礼品', exact=True)).to_be_visible()
        dialog = open_revision('loading')
        expect(dialog.get_by_role('button', name='重新提交', exact=True)).to_be_disabled()
        expect(dialog.get_by_role('button', name='重新提交', exact=True)).to_be_enabled()
        assert not failures, failures
        page.close()
    browser.close()
print('PASS Admin production revision: history/edit/clear/corrupt/loading/empty/error/retry/forbidden at 1440/390')
