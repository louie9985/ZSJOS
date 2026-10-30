from pathlib import Path
from playwright.sync_api import sync_playwright
out=Path(__file__).parent
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True)
 for width in [1440,390]:
  page=browser.new_page(viewport={'width':width,'height':900})
  page.goto('http://127.0.0.1:5190/test/home-fixed-todos.html')
  page.get_by_text('逾期 2',exact=True).wait_for()
  assert page.get_by_text('今日 3',exact=True).count()==1
  assert page.get_by_text('未来 4',exact=True).count()==1
  assert page.locator('.ant-statistic-content-value').all_text_contents()==['9','5','7']
  page.screenshot(path=str(out/f'home-fixed-todos-{width}.png'),full_page=True)
  page.get_by_role('button',name='审批待办').click()
  assert '/bpm/task/todo' in page.url
  page.close()
 page=browser.new_page()
 page.goto('http://127.0.0.1:5190/test/home-fixed-todos.html?denied=1')
 page.get_by_text('暂无权限',exact=True).first.wait_for()
 assert page.locator('body').get_attribute('data-bpm-requests') is None
 assert page.get_by_role('button',name='审批待办').count()==0
 page.goto('http://127.0.0.1:5190/test/home-fixed-todos.html?error=1')
 page.get_by_text('审批加载失败',exact=True).wait_for()
 assert page.locator('.ant-statistic-content-value').count()==0
 browser.close()
 print('PASS: desktop/mobile, 9+5+7 separation, approval route, permission denial, failure not zero')
