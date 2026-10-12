# UTF-8. Both frontend source-entry contracts, using isolated synthetic APIs.
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out=Path('D:/ZSJ-OS-backups/source-entry-browser');out.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for app,port in [('workbench',5174),('admin',5183)]:
        page=browser.new_page();errors=[]
        page.on('pageerror',lambda e:errors.append(str(e)))
        for entry in ['sales','education']:
            for width in [1440,390]:
                page.set_viewport_size({'width':width,'height':1000})
                page.goto(f'http://127.0.0.1:{port}/test/source-entry.html?{entry}')
                expect(page.get_by_text('业务来源：本人自拓，无新媒体引流',exact=True)).to_be_visible()
                if app=='workbench':
                    page.get_by_role('combobox').filter(has_not=page.locator('[disabled]'))
                    page.locator('#newMediaProviderUserId').click()
                    page.get_by_title('验证新媒体',exact=True).click()
                else:
                    page.locator('.el-form-item').filter(has=page.get_by_text('新媒体提供方',exact=True)).locator('.el-select').click()
                    page.get_by_role('option',name='验证新媒体',exact=True).click()
                expect(page.get_by_text('业务来源：新媒体引流，由本人代录',exact=True)).to_be_visible()
                page.wait_for_timeout(500)
                page.screenshot(path=str(out/f'{app}-{entry}-{width}.png'),full_page=True)
        assert not errors,(app,errors)
        page.close()
    browser.close()
print('PASS both frontend entry types and provider selection at desktop/mobile widths')
