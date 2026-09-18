# -*- coding: utf-8 -*-
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

BASE = 'http://127.0.0.1:5174/test/inbox-avatar-rail.html'
with sync_playwright() as p:
 browser = p.chromium.launch(channel='chrome', headless=True)
 context = browser.new_context(viewport={'width':1280,'height':900})
 context.add_init_script("if (!localStorage.getItem('zsjos_lead_inbox_unseen')) localStorage.setItem('zsjos_lead_inbox_unseen','[2,3]')")
 page = context.new_page()
 errors=[]
 page.on('pageerror', lambda e: errors.append(str(e)))
 for kind,label in [('lead','客资'),('student','学员')]:
  page.goto(BASE+'?page='+kind)
  items=page.locator('.inbox-avatar-layout .lead-inbox-item')
  expect(items.first).to_be_visible()
  expect(page.locator('.lead-inbox-detail-pane .lead-inbox-detail')).to_be_visible()
  collapse=page.get_by_role('button',name='收起'+label+'列表',exact=True).bounding_box()
  searchBox=page.locator('.inbox-avatar-layout .advanced-filter-toolbar .ant-input-affix-wrapper').bounding_box()
  filterBox=page.locator('.inbox-avatar-layout .advanced-filter-toolbar .ant-badge button').bounding_box()
  card=items.first.bounding_box()
  assert abs(collapse['y']-searchBox['y'])<2 and abs(filterBox['y']-searchBox['y'])<2
  assert abs(filterBox['x']+filterBox['width']-card['x']-card['width'])<2
  page.screenshot(path=str(Path(gettempdir())/f'{kind}-toolbar-expanded.png'), full_page=True)
  page.evaluate("window.detailBefore=document.querySelector('.lead-inbox-detail-pane .lead-inbox-detail'); window.callsBefore=railFixture.calls")
  page.get_by_role('button',name='收起'+label+'列表',exact=True).click()
  assert page.locator('.lead-inbox-list-pane').bounding_box()['width']==72
  assert page.evaluate("detailBefore===document.querySelector('.lead-inbox-detail-pane .lead-inbox-detail') && callsBefore===railFixture.calls")
  if kind=='lead': expect(page.locator('.inbox-avatar-unseen').first).to_be_visible()
  items.nth(1).focus()
  expect(page.get_by_role('tooltip').filter(has_text='TEST-')).to_be_visible()
  page.get_by_role('button',name='请求提交人协助' if kind=='lead' else '修改信息').first.click()
  editor=page.get_by_role('dialog').locator('textarea').first if kind=='lead' else page.get_by_role('dialog').locator('input').first
  editor.fill('未提交内容测试')
  before=page.evaluate('railFixture.calls')
  page.get_by_role('button',name='展开'+label+'列表',exact=True).evaluate('(e)=>e.click()')
  expect(editor).to_have_value('未提交内容测试')
  page.get_by_role('button',name='收起'+label+'列表',exact=True).evaluate('(e)=>e.click()')
  expect(editor).to_have_value('未提交内容测试')
  assert page.evaluate('railFixture.calls')==before
  page.reload(); expect(items.first).to_be_visible()
  page.screenshot(path=str(Path(gettempdir())/f'{kind}-avatar-desktop.png'), full_page=True)
  page.get_by_role('button',name='搜索与筛选'+label,exact=True).click()
  search=page.locator('.inbox-avatar-layout .advanced-filter-toolbar input')
  expect(search).to_be_focused()
  search.fill('测试'+label+'45'); search.press('Enter')
  expect(items).to_have_count(1)
  page.get_by_role('button',name='收起'+label+'列表',exact=True).click()
  page.get_by_role('button',name='搜索与筛选'+label,exact=True).click()
  expect(search).to_have_value('测试'+label+'45')
  page.locator('.inbox-avatar-layout .ant-input-clear-icon').click()
  expect(items).to_have_count(20)
  scroll=page.locator('.inbox-avatar-layout .lead-inbox-scroll')
  scroll.evaluate('(e)=>e.scrollTop=200'); top=scroll.evaluate('(e)=>e.scrollTop')
  page.get_by_role('button',name='收起'+label+'列表',exact=True).click()
  page.get_by_role('button',name='展开'+label+'列表',exact=True).click()
  assert scroll.evaluate('(e)=>e.scrollTop')==top
  page.get_by_role('button',name='收起'+label+'列表',exact=True).click()
  if kind=='student':
   page.get_by_role('button',name='下一页学员').click()
   expect(items.first).to_have_attribute('aria-label','测试学员21 · TEST-STUDENT-21')
  page.reload(); expect(page.get_by_role('button',name='展开'+label+'列表',exact=True)).to_be_visible()
  expect(items.first).to_be_visible()
  page.set_viewport_size({'width':390,'height':844})
  assert scroll.evaluate('(e)=>getComputedStyle(e).flexDirection')=='row'
  assert scroll.evaluate('(e)=>e.scrollWidth>e.clientWidth')
  assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
  if kind=='lead':
   scroll.evaluate('(e)=>e.scrollLeft=e.scrollWidth')
   expect(items).to_have_count(40)
   items.first.click()
   expect(page.locator('.ant-drawer:visible')).to_be_visible()
   page.locator('.ant-drawer:visible .ant-drawer-close').click()
  else: expect(page.locator('.lead-inbox-detail-pane')).to_be_visible()
  page.screenshot(path=str(Path(gettempdir())/f'{kind}-avatar-mobile.png'),full_page=True)
  page.get_by_role('button',name='展开'+label+'列表',exact=True).click()
  boxes=[page.get_by_role('button',name='收起'+label+'列表',exact=True).bounding_box(),page.locator('.inbox-avatar-layout .advanced-filter-toolbar .ant-input-affix-wrapper').bounding_box(),page.locator('.inbox-avatar-layout .advanced-filter-toolbar .ant-badge button').bounding_box()]
  assert max(b['y'] for b in boxes)-min(b['y'] for b in boxes)<2
  page.wait_for_function("""() => { const a=document.querySelector('.inbox-avatar-layout .advanced-filter-toolbar .ant-badge button').getBoundingClientRect(); const b=document.querySelector('.inbox-avatar-layout .lead-inbox-item').getBoundingClientRect(); return Math.abs(a.right-b.right)<2 }""")
  boxes[2]=page.locator('.inbox-avatar-layout .advanced-filter-toolbar .ant-badge button').bounding_box()
  card=items.first.bounding_box()
  assert abs(boxes[2]['x']+boxes[2]['width']-card['x']-card['width'])<2
  page.get_by_role('button',name='收起'+label+'列表',exact=True).click()
  page.set_viewport_size({'width':1280,'height':900})
  print('PASS',kind,'desktop/mobile, preference, search/clear, scroll, selection, pagination/loading, detail identity/no requests')
 assert not errors, errors
 for kind,label in [('lead','客资'),('student','学员')]:
  for mode in ['empty','error','denied']:
   page.goto(BASE+'?page='+kind+'&mode='+mode)
   if mode=='empty': expect(page.get_by_role('status')).to_contain_text('暂无')
   else:
    expect(page.get_by_role('button',name='查看列表错误')).to_be_visible()
    page.get_by_role('button',name='查看列表错误').click()
    expect(page.get_by_role('alert').first).to_contain_text('无权' if mode=='denied' else '失败')
    page.get_by_role('button',name='收起'+label+'列表',exact=True).click()
    if mode=='error':
     page.evaluate("railFixture.mode='success'")
     page.get_by_role('button',name='重试加载列表').click()
     expect(page.locator('.inbox-avatar-layout button.lead-inbox-item')).to_have_count(20)
  page.goto(BASE+'?page='+kind+'&delay=1500')
  expect(page.locator('.inbox-avatar-layout .ant-skeleton-avatar').first).to_be_visible()
  expect(page.locator('.inbox-avatar-layout button.lead-inbox-item')).to_have_count(20)
  print('PASS',kind,'draft preservation, loading/empty/error/denied/retry')
 page.goto(BASE+'?page=lead&mode=more-error')
 expect(page.locator('.inbox-avatar-layout button.lead-inbox-item')).to_have_count(20)
 page.locator('.lead-inbox-scroll').evaluate('(e)=>e.scrollTop=e.scrollHeight')
 expect(page.get_by_role('button',name='重试加载列表')).to_be_visible()
 page.evaluate("railFixture.mode='success'")
 page.get_by_role('button',name='重试加载列表').click()
 expect(page.locator('.inbox-avatar-layout button.lead-inbox-item')).to_have_count(40)
 print('PASS incremental load error and retry')
 # Each page persisted its own preference; table mode keeps the existing presentation.
 for kind in ['lead','student']:
  page.evaluate("localStorage.setItem('crm-theme',JSON.stringify({inboxLayoutMode:'table'}))")
  page.goto(BASE+'?page='+kind)
  expect(page.locator('.ant-table')).to_be_visible()
  expect(page.locator('.inbox-avatar-controls')).to_have_count(0)
 print('PASS table compatibility')
 browser.close()
