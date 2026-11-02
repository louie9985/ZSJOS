# -*- coding: utf-8 -*-
"""Real page components, isolated HTTP fixtures; never reads or writes live business data."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright
BASE=os.environ.get('HOME_METRIC_TEST_BASE_URL','http://127.0.0.1:5199')
ARTIFACTS=Path(__file__).resolve().parent
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True)
 for width,layout in [(1440,'table'),(1440,'master-detail'),(390,'table')]:
  page=browser.new_page(viewport={'width':width,'height':900})
  errors=[]
  page.on('pageerror',lambda error:errors.append(str(error)))
  page.goto(BASE+'/test/home-metric.html?layout='+layout,wait_until='domcontentloaded')
  page.get_by_role('button',name='12.50%').first.click()
  page.get_by_text('KZ-TEST-001').first.wait_for()
  body=page.locator('.home-metric-business-modal .ant-modal-body')
  assert body.locator('.lead-management-page').count()==1
  assert '统计时间' not in body.inner_text()
  assert 'A / B' not in body.inner_text()
  if width==1440 and layout=='table': assert body.locator('.lead-management-table').count()==1
  else: assert body.locator('.lead-inbox-layout').count()==1
  assert page.evaluate("metricRequests.some(r=>r.url.endsWith('/source-page')&&r.query.contextId==='fixture-context'&&r.query.operand==='A')")
  assert not page.evaluate("metricRequests.some(r=>r.url==='/zsjos/lead/page'||r.url==='/zsjos/lead/search-page')")
  assert 'metricContext=fixture-context' in page.url
  page.screenshot(path=str(ARTIFACTS/f'home-metric-business-{width}-{layout}.png'),full_page=True)
  page.reload()
  page.get_by_text('KZ-TEST-001').first.wait_for()
  page.locator('.home-metric-business-modal .ant-modal-close').click()
  assert 'metricContext' not in page.url
  assert not errors,errors
  page.close()
 for source in ['order','student','registration','lead_appeal','duplicate_review','media_student','subordinate_sales','education_team','cashback','withdrawal','bpm_todo','business_todo']:
  page=browser.new_page(viewport={'width':1440,'height':900})
  errors=[]
  page.on('pageerror',lambda error:errors.append(str(error)))
  page.goto(BASE+'/test/home-metric.html?source='+source,wait_until='domcontentloaded')
  page.get_by_role('button',name='12.50%').first.click()
  if source not in ['bpm_todo','business_todo']:
   page.wait_for_function("metricRequests.some(r=>r.url.endsWith('/source-page')&&r.query.source===new URLSearchParams(location.search).get('source'))")
  else:
   page.wait_for_timeout(1500)
  assert not errors,(source,errors)
  assert 'metricContext=fixture-context' in page.url, (source,page.url)
  if source in ['subordinate_sales','education_team']:
   assert page.evaluate("metricRequests.filter(r=>r.url.endsWith('/source-page')).every(r=>r.query.query.accountStatus===undefined)")
  print('PASS source:',source,flush=True)
  page.close()
 browser.close()
 print('PASS: desktop table/master-detail, mobile, scoped page request, deep-link reload, close')
