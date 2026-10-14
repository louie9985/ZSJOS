import re
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 for width,layout in [(1440,'inbox'),(1440,'table'),(390,'inbox')]:
  page=b.new_page(viewport={'width':width,'height':960})
  errors=[]
  page.on('pageerror',lambda e: errors.append(str(e)))
  page.goto('http://127.0.0.1:5297/test/service-subject.html?layout='+layout)
  page.wait_for_timeout(2000)
  if layout=='table':
   row=page.locator('tr.ant-table-row').filter(has_text='课程2');expect(row).to_have_count(1);row.click()
  else:
   items=page.locator('#student-avatar-list button.lead-inbox-item');expect(items).to_have_count(2);items.nth(1).click()
  expect(page.get_by_text('当前服务：课程2 · 班级2',exact=True).filter(visible=True)).to_have_count(1)
  page.get_by_role("button",name=re.compile("接\\s*收")).filter(visible=True).first.click()
  page.locator(".ant-modal-confirm").get_by_role("button",name=re.compile("接\\s*收")).click()
  page.wait_for_function("subjectFixture.writes.length === 1")
  assert page.evaluate("subjectFixture.writes[0]") == 2
  page.screenshot(path=f'frontend/workbench/tmp/service-subject-{width}-{layout}.png',full_page=True)
  assert not errors,errors
  print('PASS',width,layout,flush=True)
  page.close()
 page=b.new_page(viewport={"width":1440,"height":960})
 page.goto("http://127.0.0.1:5297/test/service-subject.html?legacy=1")
 items=page.locator("#student-avatar-list button.lead-inbox-item")
 expect(items).to_have_count(2)
 expect(page.get_by_text("当前服务：",exact=False)).to_have_count(0)
 items.nth(1).click()
 expect(page.get_by_text("当前服务：课程2 · 班级2",exact=True)).to_be_visible()
 page.evaluate("subjectFixture.delay=700")
 items.first.click();items.nth(1).click()
 page.wait_for_timeout(1000)
 expect(page.get_by_text("当前服务：课程2 · 班级2",exact=True)).to_be_visible()
 page.evaluate("subjectFixture.fail=true")
 items.first.click()
 expect(page.get_by_text("服务读取失败",exact=True)).to_be_visible()
 expect(page.get_by_role("button",name=re.compile("接\\s*收"))).to_have_count(0)
 print("PASS legacy choice, delayed detail and denied/error action isolation",flush=True)
 b.close()

