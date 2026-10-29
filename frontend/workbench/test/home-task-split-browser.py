# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 for width,height,tabs in ((2550,1275,True),(1920,900,True),(1440,800,False),(390,1000,True)):
  page=b.new_page(viewport={"width":width,"height":height})
  page.goto('http://127.0.0.1:5174/test/home-task-split.html' + ('?tabs' if tabs else ''))
  left=page.get_by_role('region',name='业务待办',exact=True)
  right=page.get_by_role('region',name='其他待办',exact=True)
  expect(left.get_by_text('今日跟进：1-0')).to_be_visible()
  expect(right.get_by_text('提交人协助：1-0')).to_be_visible()
  expect(page.get_by_text('已办',exact=True)).to_have_count(0)
  expect(page.get_by_text('无截止',exact=True)).to_have_count(0)
  if width>900:
   for panel in (left,right):
    pagination=panel.locator('.ant-pagination').bounding_box()
    assert pagination['y']+pagination['height']<=height, pagination
   content=page.locator('.content-layout > .ant-layout-content')
   assert content.evaluate('(el)=>el.scrollHeight<=el.clientHeight+1')
  right.locator('.ant-pagination-item-2').click()
  expect(right.get_by_text('提交人协助：2-0')).to_be_visible()
  expect(left.get_by_text('今日跟进：1-0')).to_be_visible()
  a=left.bounding_box();c=right.bounding_box()
  if width>900: assert abs(a['width']-c['width'])<2 and abs(a['y']-c['y'])<2
  else: assert c['y']>=a['y']+a['height']
  assert page.evaluate('document.documentElement.scrollWidth <= innerWidth+1')
  page.screenshot(path=f'C:/Users/EDY/AppData/Local/Temp/home-task-split-{width}.png',full_page=True)
  page.close()
 b.close()
print('PASS desktop/mobile split, pending-only, independent pagination, no overflow')
