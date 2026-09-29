# UTF-8. Isolated real-browser checks; synthetic responses, no live financial writes.
from pathlib import Path
import tempfile,json
from playwright.sync_api import sync_playwright,expect
out=Path(tempfile.gettempdir())/'cashback-search-browser';out.mkdir(exist_ok=True)
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for frontend,base in [('workbench','http://localhost:5175'),('admin','http://localhost:80')]:
  page=browser.new_page(viewport={'width':1440,'height':1000}); errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(base+'/test/cashback-search.html?read-only')
  expect(page.get_by_text('CB-TEST-001',exact=True).first).to_be_visible()
  expect(page.get_by_role('columnheader',name='兼职姓名',exact=True)).to_be_visible()
  search=page.get_by_placeholder('返现编号 / 客资编号 / 姓名',exact=True)
  search.fill('  搜索兼职  ');search.press('Enter')
  page.wait_for_function("cashbackFixture.calls.some(x => x.body.keyword === '搜索兼职')")
  expect(page.get_by_text('CB-TEST-002',exact=True).first).to_be_visible()
  expect(page.get_by_text('CB-TEST-001',exact=True)).to_have_count(0)
  if frontend=='workbench': page.locator('.ant-pagination-item-2').click()
  else: page.locator('.el-pager .number').filter(has_text='2').click()
  page.wait_for_function("cashbackFixture.calls.at(-1).body.pageNo === 2")
  expect(page.get_by_text('CB-TEST-012',exact=True).first).to_be_visible()
  search.fill('不存在姓名');search.press('Enter')
  page.wait_for_function("cashbackFixture.calls.at(-1).body.keyword === '不存在姓名' && cashbackFixture.calls.at(-1).body.pageNo === 1")
  expect(page.get_by_text('CB-TEST-012',exact=True)).to_have_count(0)
  search.fill('');search.press('Enter')
  expect(page.get_by_text('CB-TEST-001',exact=True).first).to_be_visible()
  search.fill('CB-TEST-021');search.press('Enter')
  expect(page.get_by_text('CB-TEST-021',exact=True).first).to_be_visible()
  page.screenshot(path=str(out/(frontend+'-desktop.png')),full_page=True)
  page.set_viewport_size({'width':390,'height':844})
  expect(search).to_be_visible()
  page.screenshot(path=str(out/(frontend+'-mobile.png')),full_page=True)
  assert not errors,errors
  print(frontend,'PASS: column, trimmed keyword, pagination, empty, reset, number search, desktop/mobile')
  page.close()
 browser.close()
print('Screenshots:',out)
