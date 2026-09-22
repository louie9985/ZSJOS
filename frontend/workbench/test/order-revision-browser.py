# UTF-8. Isolated browser fixtures, never accesses business APIs.
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
artifacts=Path('backend/yudao-module-zsjos/target/order-revision-browser')
artifacts.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    page=browser.new_page()
    for width in (1280,390):
        page.set_viewport_size({'width':width,'height':1000})
        for mode in ('offline','online','unknown','error'):
            page.goto('http://127.0.0.1:5189/test/order-revision.html?mode='+mode)
            if mode in ('unknown','error'):
                expect(page.get_by_text('成交配置加载失败')).to_be_visible()
                expect(page.get_by_role('button',name='重新提交审批',exact=True)).to_be_disabled()
            else:
                expect(page.get_by_text('线上已到账：' if mode=='online' else '线下已支付：',exact=False)).to_be_visible()
                amount=page.get_by_role('spinbutton').first
                if mode=='online':
                    expect(amount).to_be_disabled()
                    expect(page.get_by_role('button',name='添加成交课程')).to_be_disabled()
                    expect(page.get_by_role('button',name='重新选择课程')).to_be_disabled()
                else:
                    expect(amount).to_be_enabled()
                    amount.fill('800')
                    expect(page.get_by_text('订单总金额：¥800.00')).to_be_visible()
                    expect(page.get_by_role('button',name='重新选择课程')).to_be_enabled()
                expect(page.get_by_label('学员姓名',exact=True)).to_be_enabled()
                amount.scroll_into_view_if_needed()
                page.screenshot(path=str(artifacts/f'workbench-{mode}-{width}.png'),full_page=True,animations='disabled')
            print(f'Workbench {mode} {width}: PASS')
    page.set_viewport_size({'width':1280,'height':1000})
    for mode in ('online','offline','unknown'):
        page.goto('http://127.0.0.1:5190/test/order-revision.html?mode='+mode)
        page.get_by_role('button',name='查看',exact=True).first.click()
        page.get_by_role('button',name='补正并重新提交',exact=True).click()
        if mode=='unknown':
            expect(page.get_by_text('订单收款状态未加载，请刷新后重试')).to_be_visible()
        else:
            dialog=page.get_by_role('dialog',name='补正并重新提交',exact=True)
            if mode=='online': expect(dialog.get_by_role('spinbutton')).to_be_disabled()
            else: expect(dialog.get_by_role('spinbutton')).to_be_enabled()
            page.wait_for_timeout(400)
            page.screenshot(path=str(artifacts/f'admin-{mode}.png'),full_page=True,animations='disabled')
        print(f'Admin {mode}: PASS')
    browser.close()
