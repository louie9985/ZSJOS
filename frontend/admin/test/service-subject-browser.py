from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 for width in (1440,390):
  page=b.new_page(viewport={'width':width,'height':960})
  errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto('http://127.0.0.1:5296/test/service-subject.html')
  expect(page.get_by_role('button',name='详情',exact=True)).to_have_count(2)
  page.get_by_role('button',name='详情',exact=True).nth(1).click()
  drawer=page.locator('.el-drawer')
  expect(drawer).to_contain_text('课程2')
  expect(drawer).not_to_contain_text('课程1')
  page.screenshot(path=f'frontend/workbench/tmp/service-admin-{width}.png',full_page=True)
  assert not errors,errors
  print('PASS Admin single service',width,flush=True)
 b.close()
