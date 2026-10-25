# -*- coding: utf-8 -*-
"""Current-source synthetic regression suite; all HTTP ends at fixtures, no live data."""
import os,re,json,subprocess,tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
REPO=Path(__file__).resolve().parents[3]
os.chdir(REPO)
ROOT=Path(tempfile.mkdtemp(prefix='zsjos-review-browser-'))
BUILD_JS='const fs=require(\'fs\'),path=require(\'path\'),esbuild=require(\'./frontend/workbench/node_modules/esbuild\');\nconst base=process.env.REVIEW_FIXTURE_ROOT;\nconst entries=[[\'material\',\'material-browse-search.tsx\',s=>s.replace(\'<MemoryRouter>\',\'<MemoryRouter initialEntries={["/?view=mine&materialId=2"]}>\')],[\'approval\',\'unified-business-approval.tsx\',s=>s.replaceAll(\'if (state.failLocate)\',\'if (state.failLocate || state.completed)\')],[\'subordinate\',\'subordinate-lead-management.tsx\',s=>s],[\'groups\',\'delivery-class-groups.tsx\',s=>s],[\'permissions\',\'material-browse-search.tsx\',s=>s.replace(\'<MemoryRouter>\',\'<MemoryRouter initialEntries={["/?view=mine"]}>\').replace("\'bpm:process-instance:cancel\'","\'bpm:process-instance:cancel\',\'bpm:process-instance:query\'").replace(\'title: `Personal ${status}`\',"processInstanceId:\'synthetic-process\',title: `Personal ${status}`").replace(\'else throw new Error(`Unexpected fixture request: ${url}`)\',"else if(url.endsWith(\'/get-approval-detail\')) {document.documentElement.dataset.bpmSeen=\'true\';data={processDefinition:{},processInstance:{status:1},activityNodes:[]}} else if(url.includes(\'/bpm/comment/\')) {document.documentElement.dataset.commentSeen=\'true\';throw new Error(\'unauthorized comment request\')} else throw new Error(`Unexpected fixture request: ${url}`)")]];\n(async()=>{for(const [name,file,adapt] of entries){const out=path.join(base,name);fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,\'index.html\'),\'<html><head><meta charset="utf-8"><link rel="stylesheet" href="/bundle.css"></head><body><div id="root"></div><script type="module" src="/bundle.js"></script></body></html>\');await esbuild.build({stdin:{contents:adapt(fs.readFileSync(path.join(\'frontend/workbench/test\',file),\'utf8\')),resolveDir:path.resolve(\'frontend/workbench/test\'),loader:\'tsx\'},bundle:true,jsx:\'automatic\',format:\'esm\',outfile:path.join(out,\'bundle.js\'),loader:{\'.png\':\'dataurl\',\'.svg\':\'dataurl\',\'.woff2\':\'dataurl\',\'.woff\':\'dataurl\'},define:{\'process.env.NODE_ENV\':\'"production"\',\'import.meta.env\':\'{}\'},logLevel:\'error\'});}})().catch(e=>{console.error(e);process.exitCode=1});'
subprocess.run(['node','-'],input=BUILD_JS,text=True,encoding='utf8',cwd=REPO,env={**os.environ,'REVIEW_FIXTURE_ROOT':str(ROOT)},check=True)
print('Evidence:',ROOT,flush=True)
def attach(page,name):
 def fulfill(route):
  p=route.request.url.split('.local/',1)[-1].split('?',1)[0];file=ROOT/name/('index.html' if not p else p)
  if file.is_file() and file.parent==ROOT/name:route.fulfill(path=str(file),content_type={'.css':'text/css','.html':'text/html'}.get(file.suffix,'application/javascript')+'; charset=utf-8')
  else:route.fulfill(status=404,body='isolated fixture only')
 page.route('http://fix.local/**',fulfill)
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in (1440,390):
  page=browser.new_page(viewport={'width':width,'height':1000});attach(page,'material');page.goto('http://fix.local/?publication')
  drawer=page.get_by_role('dialog');expect(drawer).to_be_visible();drawer.get_by_role('button',name='Close',exact=True).click();expect(drawer).not_to_be_visible();page.get_by_role('tab').nth(0).click();page.wait_for_timeout(300);expect(drawer).not_to_be_visible();assert page.locator('html').get_attribute('data-detail-request')=='/zsjos/material/2';page.close()
  print('PASS deep-link remains consumed on tab switch',width,flush=True)
 for width in (1440,390):
  page=browser.new_page(viewport={'width':width,'height':1000});attach(page,'permissions');page.goto('http://fix.local/?publication');page.get_by_role('button',name='Personal IN_APPROVAL',exact=True).click();page.wait_for_function("document.documentElement.dataset.bpmSeen==='true'");assert not page.locator('html').get_attribute('data-comment-seen');page.close();print('PASS process-only permission does not request task comments',width,flush=True)
 page=browser.new_page(viewport={'width':1600,'height':1000});attach(page,'subordinate');page.goto('http://fix.local/?dirty=1');page.get_by_text('测试销售5',exact=True).click();page.get_by_role('tab',name='名下客资').click();table=page.locator('.lead-management-table');expect(table).to_be_visible();table.get_by_role('button',name=re.compile(r'详\s*细')).first.click();dialog=page.get_by_role('dialog');dialog.get_by_label('跟进备注',exact=True).fill('UNSAVED FIXTURE');dialog.get_by_role('button',name='关闭',exact=True).click();dialogs=[]
 page.on('dialog',lambda d:(dialogs.append(d.type),d.dismiss()));page.locator('.subordinate-toolbar .ant-segmented-item').nth(1).click();assert dialogs==['confirm'];expect(table).to_be_visible();table.get_by_role('button',name=re.compile(r'详\s*细')).first.click();expect(page.get_by_role('dialog').get_by_label('跟进备注',exact=True)).to_have_value('UNSAVED FIXTURE');page.close();print('PASS cancelled top filter retains unsaved detail',flush=True)
 page=browser.new_page(viewport={'width':1440,'height':1000});attach(page,'approval');page.goto('http://fix.local/');editor=page.locator('.content-review-decision');expect(editor).to_be_visible();editor.get_by_text('通过',exact=True).click();editor.locator('textarea').fill('fixture');editor.get_by_role('button',name='保存本条结论').click();finish=page.locator('.bpm-business-approval').get_by_role('button',name=re.compile('通过本次审批'));expect(finish).to_be_enabled();finish.click();page.locator('.ant-modal textarea').fill('complete fixture');page.locator('.ant-modal').get_by_role('button',name=re.compile(r'确\s*定')).click();expect(page.get_by_text('本次操作已完成',exact=True)).to_be_visible();page.wait_for_timeout(400);before=page.evaluate('businessApprovalFixture.requests.length');page.get_by_role('button',name='下一条待办',exact=True).click();page.wait_for_timeout(200);assert page.evaluate('businessApprovalFixture.requests.length')>before;expect(page.get_by_text('正在提交，请稍候',exact=True)).not_to_be_visible();page.close();print('PASS next task available after successful completion and failed done lookup',flush=True)
 browser.close()
# Existing full grouping scenarios, routed to a bundle of the current source without a live service.
_review_root=ROOT/'groups'
def fulfill(route):
 name=route.request.url.split('.local/',1)[-1].split('?',1)[0];file=_review_root/('index.html' if not name else name)
 if file.is_file() and file.parent==_review_root:route.fulfill(path=str(file),content_type={'.css':'text/css','.html':'text/html'}.get(file.suffix,'application/javascript')+'; charset=utf-8')
 else:route.fulfill(status=404,body='isolated fixture only')
source=Path('frontend/workbench/test/delivery-class-groups-browser.py').read_text(encoding='utf8').replace('http://127.0.0.1:5224/test/delivery-class-groups.html','http://fix.local/').replace("OUT = Path(os.environ.get('TEMP','.'))/'zsjos-class-groups-browser'", "OUT = ROOT/'group-screenshots'")
source=re.sub(r'(?m)^([ \t]*)page=browser.new_page\(([^\n]+)\)$',lambda m:m.group(0)+'\n'+m.group(1)+"page.route('http://fix.local/**',fulfill)",source)
exec(compile(source,'current-source-class-group-fixture','exec'))
