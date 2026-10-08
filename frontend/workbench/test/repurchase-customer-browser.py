# UTF-8. Isolated real-component browser checks; synthetic transport, no business writes.
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect
OUT=Path(gettempdir())/'zsjos-repurchase-customer';OUT.mkdir(exist_ok=True)
errors=[]
def open_page(page,app,width=1440):
 page.set_viewport_size({'width':width,'height':950})
 page.goto('http://127.0.0.1:'+('5195' if app=='workbench' else '5196')+'/test/repurchase-customer.html')
 if app=='admin':page.get_by_role('button',name='打开复购').click()
 page.get_by_label('客户姓名',exact=True).fill('测试客户')
 page.get_by_label('手机号',exact=True).fill('13800138000')
def check(page):
 page.get_by_role('button',name='校验客户').click()
 expect(page.get_by_text('已识别客户，本次订单归属当前录单人',exact=True)).to_be_visible()
def admin_select(page,label,text='测试选项'):
 field=page.locator('.el-form-item').filter(has=page.locator('.el-form-item__label',has_text=label)).first
 control=field.get_by_role('combobox')
 field.locator('.el-select').click()
 page.locator('[id="'+control.get_attribute('aria-controls')+'"]').get_by_role('option',name=text,exact=True).click()
def wb_select(page,dialog,label):
 control=dialog.get_by_label(label,exact=True)
 control.click()
 control.press('ArrowDown')
 control.press('Enter')
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 page=browser.new_page()
 page.set_default_timeout(10000)
 page.on('pageerror',lambda error:errors.append(str(error)))
 for app in ['workbench','admin']:
  for width in [1440,390]:
   open_page(page,app,width)
   expect(page.get_by_label('复购说明' if app=='workbench' else '复购原因',exact=True)).to_have_count(0)
   check(page)
   page.screenshot(path=str(OUT/f'{app}-identity-{width}.png'),full_page=True)
   page.get_by_label('客户姓名',exact=True).fill('变更客户')
   expect(page.get_by_text('已识别客户，本次订单归属当前录单人',exact=True)).to_have_count(0)
   page.evaluate("repurchaseFixture.status='IDENTITY_CONFLICT'")
   page.get_by_role('button',name='校验客户').click()
   expect(page.get_by_text('身份冲突或客户暂不可复购',exact=True)).to_be_visible()
   if app=='workbench':expect(page.get_by_role('button',name='确认客户，填写复购订单')).to_have_count(0)
   else:expect(page.get_by_role('button',name='提交复购',exact=True)).to_be_disabled()
  for status in ['MULTIPLE_MATCH','REPURCHASE_BLOCKED','NO_MATCH']:
   open_page(page,app)
   page.evaluate('(status)=>repurchaseFixture.status=status',status)
   page.get_by_role('button',name='校验客户').click()
   expect(page.get_by_text('未找到系统客户，将创建客户主档' if status=='NO_MATCH' else '身份冲突或客户暂不可复购',exact=True)).to_be_visible()
  open_page(page,app)
  page.evaluate('repurchaseFixture.fail=true')
  page.get_by_role('button',name='校验客户').click()
  expect(page.get_by_text('模拟网络错误',exact=True).last).to_be_visible()
  page.evaluate('repurchaseFixture.fail=false');check(page)
  page.get_by_label('客户姓名',exact=True).fill('新客户')
  page.evaluate('repurchaseFixture.delay=700')
  page.get_by_role('button',name='校验客户').click()
  page.get_by_label('手机号',exact=True).fill('13800138001')
  page.wait_for_timeout(850)
  expect(page.get_by_text('已识别客户，本次订单归属当前录单人',exact=True)).to_have_count(0)
 # Complete Vue submission and retry with the same key.
 open_page(page,'admin');check(page)
 for label in ['学员性质','服务周期','学员来源','缴费方式','支付方式']:admin_select(page,label)
 page.get_by_label('客户地区',exact=True).click()
 page.locator('.el-cascader-panel:visible').get_by_text('其他省份',exact=True).click()
 page.locator('.el-cascader-panel:visible').get_by_text('其他城市',exact=True).click()
 admin_select(page,'课程','测试课程');admin_select(page,'具体方案','测试方案')
 page.get_by_label('复购原因',exact=True).fill('再次购买课程')
 page.locator('input[type=file]').set_input_files({'name':'voucher.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4 synthetic'})
 expect(page.get_by_text('已上传',exact=True)).to_be_visible()
 for width in [1440,390]:
  page.set_viewport_size({'width':width,'height':950})
  page.screenshot(path=str(OUT/f'admin-order-{width}.png'),full_page=True)
  page.get_by_role('button',name='提交复购',exact=True).scroll_into_view_if_needed()
  page.screenshot(path=str(OUT/f'admin-footer-{width}.png'),full_page=True)
 page.set_viewport_size({'width':1440,'height':950})
 page.evaluate('repurchaseFixture.submitFail=true')
 page.get_by_role('button',name='提交复购',exact=True).click()
 expect(page.get_by_text('模拟提交失败',exact=True).last).to_be_visible()
 page.evaluate('repurchaseFixture.submitFail=false')
 page.get_by_role('button',name='提交复购',exact=True).click()
 expect(page.get_by_text('复购订单已提交',exact=True)).to_be_visible()
 orders=page.evaluate('repurchaseFixture.orders')
 assert len(orders)==2 and orders[0]['order']['idempotencyKey']==orders[1]['order']['idempotencyKey']
 assert orders[1]['expectedPersonId']==10
 assert orders[1]['order']['paymentVouchers']==[{'infraFileId':100}]
 # Complete React submission through the shared editor.
 open_page(page,'workbench');check(page)
 page.get_by_role('button',name='确认客户，填写复购订单').click()
 dialog=page.get_by_role('dialog')
 expect(dialog.get_by_label('学员姓名',exact=True)).to_be_disabled()
 dialog.get_by_label('复购说明',exact=True).fill('再次购买课程')
 for label in ['学员性质','服务周期','学生来源','缴费方式','支付方式']:wb_select(page,dialog,label)
 dialog.get_by_label('所在省市',exact=True).click()
 page.locator('.ant-cascader-dropdown:visible').get_by_text('其他省份',exact=True).click()
 page.locator('.ant-cascader-dropdown:visible').get_by_text('其他城市',exact=True).click()
 course=dialog.locator('.sales-order-course-picker')
 course.get_by_role('combobox').nth(0).click()
 page.locator('.ant-cascader-dropdown:visible').get_by_text('测试分类',exact=True).click()
 course.get_by_role('combobox').nth(1).click()
 page.locator('.ant-select-dropdown:visible').get_by_text('测试课程',exact=True).click()
 course.get_by_role('combobox').nth(2).click()
 page.locator('.ant-select-dropdown:visible').get_by_text('测试方案',exact=False).click()
 dialog.get_by_label('实际成交金额 1',exact=True).fill('100')
 dialog.locator('input[type=file]').set_input_files({'name':'voucher.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4 synthetic'})
 for width in [1440,390]:
  page.set_viewport_size({'width':width,'height':950})
  page.screenshot(path=str(OUT/f'workbench-order-{width}.png'),full_page=True)
  dialog.get_by_role('button',name='提交审批').scroll_into_view_if_needed()
  page.screenshot(path=str(OUT/f'workbench-footer-{width}.png'),full_page=True)
 page.set_viewport_size({'width':1440,'height':950})
 page.evaluate('repurchaseFixture.submitFail=true')
 dialog.get_by_role('button',name='提交审批').click()
 page.get_by_role('button',name='确认执行',exact=True).click()
 expect(page.get_by_text('模拟提交失败',exact=True).last).to_be_visible()
 page.evaluate('repurchaseFixture.submitFail=false')
 dialog.get_by_role('button',name='提交审批').click()
 page.get_by_role('button',name='确认执行',exact=True).click()
 expect(page.get_by_text('复购订单已提交审批',exact=True)).to_be_visible()
 orders=page.evaluate('repurchaseFixture.orders')
 assert len(orders)==2 and orders[0]['order']['idempotencyKey']==orders[1]['order']['idempotencyKey']
 assert orders[0]['order']['purchaseIntentId']==orders[1]['order']['purchaseIntentId']==55
 assert orders[1]['expectedPersonId']==10
 assert orders[1]['order']['paymentVouchers']==[{'infraFileId':100}]
 page.goto('http://127.0.0.1:5195/test/repurchase-customer.html?denied')
 expect(page.get_by_text('当前账号没有复购录单权限',exact=True)).to_be_visible()
 assert not errors,errors
 browser.close()
print('PASS: both clients 1440/390; early checks, conflict/blocked/no-match, retry, stale responses, full submission, stable idempotency and draft, permission state')
