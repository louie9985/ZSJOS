import os
BASE=os.environ.get('HOME_METRIC_TEST_BASE_URL','http://127.0.0.1:5188')
from pathlib import Path
from playwright.sync_api import sync_playwright
ARTIFACTS = Path(__file__).resolve().parent
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True)
 for width in [1440,390]:
  page=browser.new_page(timezone_id="America/Los_Angeles",viewport={"width":width,"height":900})
  page.goto(BASE+'/test/home-metric.html')
  page.get_by_role('button',name='12.50%').first.wait_for()
  assert page.locator('.home-summary-region .home-stat-card').count()==12
  if width==1440:
   dimensions=page.locator('.home-summary-region').evaluate("e=>[getComputedStyle(e).gridTemplateColumns.split(' ').length,getComputedStyle(e).gridTemplateRows.split(' ').length]")
   assert dimensions==[6,2],dimensions
   assert page.locator('.home-stat-card').evaluate_all("""cards=>cards.every(card=>{const r=card.getBoundingClientRect();return [...card.querySelectorAll('button')].every(b=>{const x=b.getBoundingClientRect();return x.top>=r.top&&x.bottom<=r.bottom+1&&x.right<=r.right+1})})""")
   page.screenshot(path=str(ARTIFACTS/'home-metric-grid-1440.png'),full_page=True)
  assert '旧版名称' not in page.locator('.home-summary-region').inner_text()
  page.get_by_role('button',name='12.50%').first.click()
  page.get_by_text('KZ-TEST-001').wait_for()
  assert page.locator('.home-summary-region > .home-stat-card').count()==12
  page.get_by_text('2026-10-01 00:00 至 2026-11-01 00:00',exact=True).wait_for()
  assert '统计时间：2026-10-29 16:00' in page.locator('body').inner_text()
  assert 'metricContext=fixture-context' in page.url
  page.reload()
  page.get_by_text('KZ-TEST-001').wait_for()
  assert page.locator('.home-summary-region > .home-stat-card').count()==12
  page.get_by_text('2026-10-01 00:00 至 2026-11-01 00:00',exact=True).wait_for()
  assert '统计时间：2026-10-29 16:00' in page.locator('body').inner_text()
  page.wait_for_timeout(400)
  page.screenshot(path=str(ARTIFACTS / f'home-metric-{width}.png'),full_page=True)
  page.get_by_role('button',name='Close',exact=True).click()
  assert 'metricContext' not in page.url
  page.close()
 page=browser.new_page()
 for status,label in [('FORBIDDEN','暂无权限'),('DIVIDE_BY_ZERO','除数为零，无法计算'),('TIMEOUT','查询超时')]:
  page.goto(BASE+'/test/home-metric.html?status='+status)
  button=page.get_by_role('button',name=label,exact=True).first
  button.wait_for()
  assert button.is_disabled()
 page.goto(BASE+'/test/home-metric.html?empty=1')
 page.get_by_text('暂无已配置指标').wait_for()
 page.goto(BASE+'/test/home-metric.html?count=3&summaryError=1')
 page.get_by_role('button',name='12.50%').first.wait_for()
 assert page.locator('.home-summary-region > .home-stat-card').count()==12
 assert page.locator('.home-summary-region .placeholder').count()==9
 assert page.locator('.home-dashboard-grid > *').count()==5
 page.locator('.home-business-panel').get_by_text('汇总加载失败',exact=True).first.wait_for()
 browser.close()
 print('desktop/mobile deep link, refresh, details and close passed')
