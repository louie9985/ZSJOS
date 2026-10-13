# -*- coding: utf-8 -*-
import os,tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
BASE=os.environ.get('WORKBENCH_TEST_URL','http://127.0.0.1:5199')
OUT=Path(tempfile.mkdtemp(prefix='calendar-day-details-'))
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [390,1440]:
  for personal in [False,True]:
   page=browser.new_page(viewport={'width':width,'height':1000})
   errors=[]
   page.on('pageerror',lambda e:errors.append(str(e)))
   for readonly in [False,True]:
    query=('personal=1&' if personal else '')+('readonly=1' if readonly else '')
    page.goto(BASE+'/test/calendar-day-details.html?'+query)
    expect(page.locator('.page-heading')).to_be_visible()
    summary=page.locator('.personal-calendar-event' if personal else '.course-calendar-summary')
    expect(summary.first).to_be_visible()
    assert summary.locator('button').count()==0
    summary.first.click()
    page.wait_for_timeout(400)
    cards=page.locator('.calendar-day-card')
    expect(cards).to_have_count(3)
    assert cards.locator('button').count()==0
    assert page.get_by_label('日程详细信息',exact=True).count()==0
    title=cards.first.locator('strong')
    assert title.inner_text().count('完整课程名称验证')==5
    assert title.evaluate('(e)=>e.scrollHeight <= e.clientHeight+1')
    assert title.evaluate('(e)=>getComputedStyle(e).whiteSpace')=='normal'
    expect(cards.first).to_contain_text('09:00 - 09:05')
    if width>768:
     boxes=[cards.nth(i).bounding_box() for i in range(3)]
     assert all(boxes[i]['x']+boxes[i]['width']<=boxes[i+1]['x']+1 for i in range(2))
    cards.first.click()
    details=page.get_by_label('日程详细信息',exact=True)
    expect(details).to_be_visible()
    expect(details.get_by_text('创建员工',exact=True)).to_be_visible()
    expect(details.get_by_role('link')).to_have_attribute('href','https://example.com/course?id=1')
    if not personal:expect(details.locator('.attachment-card')).to_have_count(1)
    for action in (['编辑日程','删除日程'] if personal else ['编辑课程','删除课程','发送通知']):
     expect(details.get_by_role('button',name=action,exact=True)).to_have_count(0 if readonly else 1)
    page.wait_for_timeout(400)
    page.screenshot(path=str(OUT/f'{"personal" if personal else "course"}-{width}-{readonly}.png'),full_page=True)
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
    if not readonly:
     if not personal:
      details.get_by_role('button',name='发送通知',exact=True).click()
      notification=page.get_by_role('dialog',name='发送日历通知',exact=True)
      expect(notification).to_be_visible()
      notification.locator('.ant-modal-close').click()
      cards.first.click()
      expect(details).to_be_visible()
     details.get_by_role('button',name='编辑日程' if personal else '编辑课程',exact=True).click()
     expect(page.get_by_role('dialog',name='编辑日程' if personal else '编辑课程安排',exact=True)).to_be_visible()
     if not personal:
      page.get_by_role('dialog',name='编辑课程安排',exact=True).locator('.ant-modal-close').click()
      cards.first.click()
      details.get_by_role('button',name='删除课程',exact=True).click()
      page.locator('.ant-popconfirm .ant-btn-primary').click()
      expect(cards).to_have_count(2)
    else:
     details.get_by_role('button',name='关闭日程详情',exact=True).click()
     expect(details).not_to_be_visible()
    assert not errors,errors
   print('PASS',width,'personal' if personal else 'course','full titles/time/details/actions/readonly/links/attachments',flush=True)
   page.close()
 browser.close()
print('Screenshots',OUT)
