# -*- coding: utf-8 -*-
from pathlib import Path
import tempfile
from playwright.sync_api import sync_playwright, expect
out=Path(tempfile.gettempdir())/'zsjos-workflow-navigation'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in (1440,390):
  page=browser.new_page(viewport={'width':width,'height':900})
  errors=[]
  page.on('pageerror',lambda e: errors.append(str(e)))
  page.goto('http://127.0.0.1:5197/test/workflow-navigation.html')
  section=page.get_by_test_id('secondary')
  expect(section.get_by_text('待办任务',exact=True)).to_be_visible()
  expect(page.locator('.ant-badge-count')).to_have_count(3)
  section.locator('.ant-menu-submenu-title').click()
  expect(section.get_by_text('待办任务',exact=True)).not_to_be_visible()
  section.locator('.ant-menu-submenu-title').click()
  def update(values):
   page.evaluate('(v)=>{Object.assign(window.workflow,v);window.dispatchEvent(new Event("focus"))}',values)
  update({'count':101})
  expect(section.locator('.ant-badge-count').first).to_have_attribute('title','101')
  page.wait_for_timeout(350)
  page.screenshot(path=str(out/f'desktop-{width}.png'))
  update({'failure':500})
  expect(section.locator('.ant-badge-count').first).to_have_attribute('title','101')
  update({'failure':403})
  expect(page.locator('.ant-badge-count')).to_have_count(0)
  update({'failure':0,'count':0})
  expect(page.locator('.ant-badge-count')).to_have_count(0)
  update({'count':4})
  expect(section.locator('.ant-badge-count').first).to_have_attribute('title','4')
  page.evaluate('window.workflow.count=3')
  page.get_by_role('button',name='处理后刷新').click()
  expect(section.locator('.ant-badge-count').first).to_have_attribute('title','3')
  page.get_by_role('button',name='切换权限').click()
  expect(page.locator('.ant-badge-count')).to_have_count(0)
  before=page.evaluate('window.workflow.calls')
  page.evaluate('window.dispatchEvent(new Event("focus"))')
  page.wait_for_timeout(100)
  assert page.evaluate('window.workflow.calls')==before
  page.get_by_role('button',name='切换权限').click()
  expect(section.locator('.ant-badge-count').first).to_have_attribute('title','3')
  page.get_by_role('button',name='移动导航').click()
  drawer=page.get_by_role('dialog')
  expect(drawer.get_by_text('待办任务',exact=True)).to_be_visible()
  expect(drawer.locator('.ant-badge-count')).to_have_count(3)
  page.wait_for_timeout(350)
  page.screenshot(path=str(out/f'mobile-drawer-{width}.png'))
  assert not errors,errors
  print('PASS',width,'counts/default expansion/collapse/zero/99+/errors/permission/invalidation/mobile')
  page.close()
 browser.close()
print(out)

