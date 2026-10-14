from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 for width in (1440,390):
  page=b.new_page(viewport={'width':width,'height':960})
  page.goto('http://127.0.0.1:5297/test/registration-item-tasks.html')
  items=page.locator('.lead-inbox-item')
  expect(items).to_have_count(3)
  if width == 390:
   expect(items.first).to_contain_text('履约产品1')
   page.screenshot(path='frontend/workbench/tmp/registration-items-390.png',full_page=True)
   print('PASS mobile item list; detail actions remain desktop-only')
   continue
  items.first.click()
  page.get_by_role('button',name='完成报名履约',exact=True).click()
  expect(items).to_have_count(2)
  expect(items.first).to_contain_text('履约产品2')
  items.first.click()
  page.get_by_role('button',name='结束本产品（无需交付）',exact=True).click()
  page.get_by_role('dialog').get_by_role('textbox').fill('无需后续交付')
  page.get_by_role('button',name='确认结束',exact=True).click()
  expect(items).to_have_count(1)
  expect(items.first).to_contain_text('履约产品3')
  page.locator('.lead-inbox-group-tabs').get_by_role('tab',name='已处理',exact=True).click()
  expect(items).to_have_count(2)
  expect(items.nth(1)).to_contain_text('已结束')
  page.screenshot(path=f'frontend/workbench/tmp/registration-items-{width}.png',full_page=True)
  print('PASS item isolation and completed/ended groups',width)
 b.close()
