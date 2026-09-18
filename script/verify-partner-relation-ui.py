"""UTF-8. Both configuration consumers with synthetic API contracts; no account writes."""
import json
from playwright.sync_api import sync_playwright,expect
SCENE=dict(id=10,name='兼职客资指定分配',code='partner_lead_specified_assignment',sourceType='partner',sourceLabel='兼职',targetLabel='接单人员',sourcePostCode=None,targetEligibilityType='permission',targetPermissionCode='zsjos:lead:accept',status=0)
PERMISSIONS=['zsjos:user-relation:query','zsjos:user-relation:update','zsjos:user-relation-scene:query','zsjos:user-relation-scene:update','zsjos:user-relation:log-query']
ROW=dict(id=7,nickname='测试兼职',status=0,targetUsers=[],validTargetCount=0,invalidTargetCount=0,salesUsers=[])
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for client in ('admin','workbench'):
  page=browser.new_page(viewport=dict(width=1440,height=1000)); writes=[]
  def api(route):
   path=route.request.url.split('?')[0];data=[]
   if path.endswith('/auth/get-permission-info'): data=dict(user=dict(id=1,nickname='测试管理员',deptId=1),roles=[],permissions=PERMISSIONS,menus=[dict(id=1,parentId=0,name='用户关系',path='/system/user-relation',component='zsjos/userRelation/index',componentName='ZsjosUserRelationScene',type=2,visible=True,keepAlive=True)])
   elif path.endswith('/scene/simple-list'):data=[SCENE]
   elif path.endswith('/scene/page'):data=dict(list=[SCENE],total=1)
   elif path.endswith('/relation/page'):data=dict(list=[ROW],total=1)
   elif path.endswith('/target/simple-list'):data=[dict(id=8,nickname='测试接单人员',status=0),dict(id=9,nickname='另一接单人员',status=0)]
   elif path.endswith('/relation/save'):writes.append(route.request.post_data_json);data=True
   elif path.endswith('/log/page'):data=dict(list=[],total=0)
   elif 'unread-count' in path:data=0
   route.fulfill(content_type='application/json',body=json.dumps(dict(code=0,data=data),ensure_ascii=False))
  page.on('pageerror',lambda error:print('UI ERROR',str(error),flush=True))
  page.route('**/admin-api/**',api)
  if client=='admin':
   page.add_init_script("localStorage.setItem('ACCESS_TOKEN','synthetic-fixture');localStorage.setItem('TENANT_ID','1')")
   page.goto('http://localhost/user-relation/data/partner_lead_specified_assignment')
   expect(page.get_by_text('测试兼职',exact=True).first).to_be_visible(timeout=20000)
   page.get_by_role('button',name='配置',exact=True).click()
   page.get_by_text('教务承接',exact=True).click()
   page.locator('.candidate-row').filter(has_text='测试接单人员').click()
   expect(page.get_by_role('checkbox',name='另一接单人员',exact=False)).to_be_disabled()
   page.screenshot(path='D:/ZSJ-OS/output/partner-assignment/admin-config.png',full_page=True)
   page.get_by_role('button',name='保存配置',exact=True).click()
   page.get_by_role('button',name='确认执行',exact=True).click()
  else:
   html="""<!doctype html><html><head><meta charset='UTF-8'></head><body><div id='root'></div><script type='module'>
import RefreshRuntime from '/@react-refresh';RefreshRuntime.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;
const React=await import('/node_modules/.vite/deps/react.js');const ReactDOM=await import('/node_modules/.vite/deps/react-dom_client.js');const createRoot=(ReactDOM.default||ReactDOM).createRoot;const {UserRelationPage}=await import('/src/pages/ManagementPages.tsx');
createRoot(document.getElementById('root')).render((React.default||React).createElement(UserRelationPage,{permissions:PERMISSIONS}));
</script></body></html>""".replace('PERMISSIONS',json.dumps(PERMISSIONS))
   page.route('**/test/partner-relation-acceptance',lambda r:r.fulfill(content_type='text/html',body=html))
   page.goto('http://localhost:5174/test/partner-relation-acceptance')
   expect(page.get_by_text('兼职客资指定分配',exact=True)).to_be_visible(timeout=20000)
   page.get_by_role('button',name='关系数据',exact=True).click()
   page.get_by_label('来源用户',exact=True).click();page.get_by_title('测试兼职',exact=True).click();page.keyboard.press('Escape')
   page.get_by_label('目标用户',exact=True).click();page.get_by_title('测试接单人员',exact=True).click();page.keyboard.press('Escape')
   page.get_by_text('教务承接',exact=True).click()
   page.screenshot(path='D:/ZSJ-OS/output/partner-assignment/workbench-config.png',full_page=True)
   page.get_by_role('button',name='保存关系',exact=True).click()
  page.wait_for_timeout(300)
  assert writes and writes[-1]['ownerIdentity']=='education' and writes[-1]['sourceUserIds']==[7] and writes[-1]['targetUserIds']==[8],writes
  print('PASS',client,'typed Partner source, single receiver, education identity payload')
  page.close()
 browser.close()
