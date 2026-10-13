# -*- coding: utf-8 -*-
import os,tempfile,json,datetime
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
BASE=os.environ.get('WORKBENCH_TEST_URL','http://127.0.0.1:5198')
OUT=Path(tempfile.mkdtemp(prefix='calendar-views-'))
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [390,1440]:
  for dark in [False,True]:
   for scene in ['personal','course']:
    context=browser.new_context(viewport={'width':width,'height':1000})
    context.add_init_script("localStorage.setItem('crm-theme',JSON.stringify({preset:'"+('default-dark' if dark else 'default-light')+"'}))")
    page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto(BASE+'/test/calendar-colors.html?grid=1&scene='+scene)
    expect(page.locator('.ant-segmented').get_by_text('年',exact=True)).to_have_count(0)
    expect(page.get_by_text('租户内统一课程安排',exact=True)).to_have_count(0)
    expect(page.get_by_text('统一颜色为建议标注，课程可自选底色。',exact=True)).to_have_count(0)
    page.locator('.ant-segmented').get_by_text('周',exact=True).click()
    grid=page.get_by_label('周时间表')
    expect(grid.locator('.calendar-time-column')).to_have_count(7)
    dates=grid.locator('.calendar-time-column').evaluate_all('(els)=>els.map(e=>e.dataset.date)')
    assert datetime.date.fromisoformat(dates[0]).weekday()==0
    ranges=json.loads(page.evaluate("sessionStorage.getItem('last-range')"))
    assert ranges['rangeStart'][:10]==dates[0]
    assert (datetime.datetime.fromisoformat(ranges['rangeEnd'])-datetime.datetime.fromisoformat(ranges['rangeStart'])).days==7
    long=grid.locator('.calendar-time-event').filter(has_text='重叠长安排')
    short=grid.locator('.calendar-time-event').filter(has_text='重叠短安排')
    expect(long).to_have_count(1);expect(short).to_have_count(1)
    boxes=[x.bounding_box() for x in [long,short]]
    assert abs(boxes[0]['width']-boxes[1]['width'])<2
    assert boxes[1]['x']>=boxes[0]['x']+boxes[0]['width']-2
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'),scene
    if width==390:assert grid.evaluate('(e)=>e.scrollWidth>e.clientWidth')
    page.screenshot(path=str(OUT/f'{scene}-week-{width}-{dark}.png'),full_page=True)
    old=dates[0];page.get_by_label('下一周',exact=True).click()
    expect(grid.locator('.calendar-time-column').first).not_to_have_attribute('data-date',old)
    page.get_by_role('button',name='今天',exact=True).click()
    page.locator('.ant-segmented').get_by_text('日',exact=True).click()
    grid=page.get_by_label('日时间表');expect(grid.locator('.calendar-time-column')).to_have_count(1)
    if scene=='personal':expect(grid.locator('.calendar-all-day').get_by_text('全天安排')).to_be_visible()
    expect(grid.locator('.calendar-now-line')).to_have_count(1)
    grid.locator('.calendar-time-slot').nth(28).click()
    editor=page.get_by_role('dialog',name='新建日程' if scene=='personal' else '新增课程安排',exact=True)
    expect(editor).to_be_visible()
    values=editor.locator('.ant-picker-range input').evaluate_all('(els)=>els.map(e=>e.value)')
    assert '14:00' in values[0] and '15:00' in values[1],values
    editor.locator('.ant-modal-close').click()
    grid.locator('.calendar-time-event').filter(has_text='重叠长安排').click()
    expect(page.get_by_role('dialog')).to_be_visible()
    page.get_by_role('dialog').locator('.ant-modal-close').click()
    page.wait_for_timeout(350)
    page.screenshot(path=str(OUT/f'{scene}-day-{width}-{dark}.png'),full_page=True)
    page.locator('.ant-segmented').get_by_text('月',exact=True).click()
    expect(page.locator('.ant-picker-calendar')).to_be_visible()
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.goto(BASE+'/test/calendar-colors.html?grid=1&readonly=1&scene='+scene)
    page.locator('.ant-segmented').get_by_text('日',exact=True).click()
    expect(page.locator('.calendar-time-slot').first).to_be_disabled()
    assert not errors,errors
    print('PASS',scene,width,dark,flush=True);context.close()
 browser.close()
print('Screenshots',OUT)
