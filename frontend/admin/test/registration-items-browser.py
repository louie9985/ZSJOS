from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 page=b.new_page(viewport={'width':1440,'height':1000})
 page.goto('http://127.0.0.1:5296/test/registration-items.html')
 expect(page.get_by_role('button',name='查看',exact=True)).to_have_count(2)
 page.get_by_role('button',name='查看',exact=True).first.click()
 page.get_by_role('button',name='结束本产品（无需交付）',exact=True).click()
 page.locator('.el-message-box textarea').fill('无需后续交付')
 page.locator('.el-message-box').get_by_role('button',name='确认',exact=True).click()
 expect(page.get_by_role('button',name='结束本产品（无需交付）',exact=True)).to_have_count(0)
 assert page.evaluate('window.registrationRows[0].status')=='ended'
 assert page.evaluate('window.registrationRows[1].status')=='pending'
 page.screenshot(path='frontend/workbench/tmp/registration-admin-1440.png',full_page=True)
 print('PASS Admin product display and independent end action')
 b.close()
