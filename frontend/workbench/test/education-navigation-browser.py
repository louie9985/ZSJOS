# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright,expect
mode={'delivery':True,'error':False}
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True);page=browser.new_page()
 def handler(route):
  if mode['error']:route.fulfill(json={'code':0,'data':{}})
  else:route.fulfill(json={'code':0,'data':{'delivery':mode['delivery']}})
 page.route('**/admin-api/zsjos/education-team/navigation-context',handler)
 base='http://127.0.0.1:5174/test/education-navigation.html'
 for width in [1440,390]:
  page.set_viewport_size({'width':width,'height':900});page.goto(base+'?path=/zsjos/subordinate-sales')
  expect(page.get_by_role('link',name='下属员工',exact=True)).to_have_count(0);expect(page.get_by_role('link',name='教务团队',exact=True)).to_be_visible();expect(page.get_by_text('无权打开',exact=True)).to_be_visible()
 page.goto(base+'?path=/zsjos/education-team');expect(page.get_by_text('允许打开',exact=True)).to_be_visible()
 page.goto(base+'?ungranted=1');expect(page.get_by_role('link')).to_have_count(0);expect(page.get_by_text('暂无授权入口',exact=True)).to_be_visible()
 mode['delivery']=False;page.goto(base+'?path=/zsjos/subordinate-sales');expect(page.get_by_role('link',name='下属员工',exact=True)).to_be_visible();expect(page.get_by_text('允许打开',exact=True)).to_be_visible()
 mode['error']=True;page.reload();expect(page.get_by_text('教务导航归属暂时无法确认，请重试',exact=True)).to_be_visible();expect(page.get_by_role('link')).to_have_count(0)
 mode['error']=False;page.get_by_role('button',name='重试').click();expect(page.get_by_role('link',name='下属员工',exact=True)).to_be_visible()
 browser.close()
print('PASS: delivery old entry and URL removed, education only, ungranted fails closed, sales preserved, invalid context/retry, desktop/mobile')
