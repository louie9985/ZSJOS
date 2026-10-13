# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright,expect
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [390,1440]:
  page=browser.new_page(viewport={'width':width,'height':1000})
  for query,label in [('courseId=1&future=1','创建员工'),('courseId=1&missing=1','课程已删除或不存在'),('courseId=1&forbidden=1','当前账号无权查看该课程'),('courseId=bad','课程通知链接无效')]:
   page.goto('http://127.0.0.1:5199/test/calendar-day-details.html?'+query)
   expect(page.get_by_text(label,exact=True)).to_be_visible()
   if label=='创建员工':
    expect(page.get_by_label('日程详细信息',exact=True)).to_be_visible()
    expect(page.locator('.calendar-day-card').first).to_have_attribute('aria-expanded','true')
   else:
    expect(page.get_by_label('日程详细信息',exact=True)).to_have_count(0)
    page.get_by_role('button',name='重试',exact=True).click()
    expect(page.get_by_text(label,exact=True)).to_be_visible()
  page.close();print('PASS',width,'automatic detail, deleted, forbidden, invalid, retry',flush=True)
 browser.close()
