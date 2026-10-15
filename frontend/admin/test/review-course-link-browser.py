# -*- coding: utf-8 -*-
import os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
out=Path(os.environ['TEMP'])/'review-fixes-browser';out.mkdir(exist_ok=True)
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [1440,390]:
  page=browser.new_page(viewport={'width':width,'height':960})
  page.goto('http://127.0.0.1:5275/test/review-course-link.html')
  page.get_by_role('button',name='打开课程通知',exact=True).click()
  expect(page.get_by_role('dialog').filter(has_text='课程详情')).to_be_visible()
  expect(page.get_by_role('dialog').filter(has_text='课程详情').get_by_text('通知课程',exact=True)).to_be_visible()
  assert 'courseId=7' in page.url
  page.wait_for_timeout(500)
  page.screenshot(path=str(out/f'admin-{width}.png'),full_page=True)
  page.goto('http://127.0.0.1:5275/test/review-course-link.html?courseId=bad')
  expect(page.get_by_text('课程通知链接无效',exact=True)).to_be_visible()
  assert not any(url.endswith('/bad') for url in page.evaluate('window.courseLinkFixture.requests'))
  page.goto('http://127.0.0.1:5275/test/review-course-link.html')
  page.wait_for_function('!!window.courseLinkFixture')
  page.evaluate('window.courseLinkFixture.fail=true; window.courseLinkFixture.go(7)')
  expect(page.locator('.el-alert').get_by_text('课程已删除或不可访问',exact=True)).to_be_visible()
  page.evaluate('window.courseLinkFixture.fail=false')
  page.get_by_role('button',name='重试定位').click()
  expect(page.get_by_role('dialog').filter(has_text='课程详情')).to_be_visible()
  page.close()
 browser.close()
print('PASS Admin notification route and course details 1440/390')
