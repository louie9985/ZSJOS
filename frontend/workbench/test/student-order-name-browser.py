# -*- coding: utf-8 -*-
import os,re
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
out=Path(os.environ['TEMP'])/'zsjos-student-name-snapshot-20261021';out.mkdir(exist_ok=True)
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 for width in (1440,390):
  page=b.new_page(viewport={'width':width,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  for missing in (False,True):
   page.goto('http://127.0.0.1:5193/test/student-order-name.html'+('?missing=1' if missing else ''))
   rows=page.locator('#student-avatar-list button.lead-inbox-item');expect(rows).to_have_count(2);rows.nth(1).click()
   hero=page.locator('.lead-detail-hero').filter(visible=True);expect(hero).to_contain_text('未填写姓名' if missing else '独立服务快照姓名')
   expect(page.locator('.lead-detail-overview-v2').filter(visible=True)).not_to_contain_text('客资原姓名')
   page.get_by_role('button',name=re.compile('修改信息$')).filter(visible=True).click()
   dialog=page.get_by_role('dialog',name='修改学员信息').filter(visible=True)
   name=dialog.locator('.ant-form-item').filter(has=page.locator('label',has_text=re.compile('^姓名$'))).locator('input')
   expect(name).to_have_attribute('readonly','');expect(name).to_have_value('' if missing else '独立服务快照姓名')
   dialog.get_by_label('修改原因').fill('联系方式核验');dialog.get_by_role('button',name=re.compile('保\s*存')).click()
   expect(dialog).not_to_be_visible();writes=page.evaluate('sidebarFixture.writes');assert writes[-1]['id']==2 and 'name' not in writes[-1]['data'],writes
   page.screenshot(path=str(out/f'workbench-{width}-{missing}.png'),full_page=True)
  assert not errors,errors;page.close();print('PASS Workbench course/missing/readonly/save',width,flush=True)
 for width in (1440,390):
  page=b.new_page(viewport={'width':width,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto('http://127.0.0.1:5317/test/student-order-name.html')
  rows=page.locator('.el-table__body tr');expect(rows).to_have_count(3);expect(rows.nth(1)).to_contain_text('独立服务快照姓名');expect(rows.nth(2)).to_contain_text('学员姓名未补全')
  page.get_by_role('button',name='详情',exact=True).nth(1).click();drawer=page.locator('.el-drawer').filter(visible=True);expect(drawer).to_contain_text('独立服务快照姓名')
  page.wait_for_timeout(400)
  page.screenshot(path=str(out/f'admin-{width}.png'),full_page=True)
  assert not errors,errors;page.close();print('PASS Admin course/missing',width,flush=True)
 b.close()
