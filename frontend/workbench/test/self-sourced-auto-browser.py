# UTF-8. Real Chrome against synthetic transport fixtures; no shared database writes.
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjos-self-sourced-browser'
OUT.mkdir(exist_ok=True)
HINT = '提交后将自动生成首跟记录并判定有效，请确认已联系客户且有意向。'
REQUIRED = '请填写已联系客户及意向情况，作为首跟内容和判有效依据'
errors = []

def wb_open(page, query=''):
    page.goto('http://127.0.0.1:5193/test/self-sourced-auto.html' + query)
    expect(page.locator('#name')).to_be_visible()

def wb_base(page, remark='  已联系，有意向  '):
    page.locator('#name').fill('浏览器测试客户')
    page.locator('#mobile').fill('13800138000')
    page.locator('#regionPath').click()
    page.get_by_text('其他省份', exact=True).click()
    page.get_by_text('其他城市', exact=True).click()
    for field, label in [('sourceChannel','测试渠道'),('leadCategory','测试分类')]:
        page.locator('#'+field).click()
        page.locator('.ant-select-item-option-content').get_by_text(label,exact=True).click()
    page.locator('#remark').fill(remark)

def wb_confirm(page):
    page.get_by_role('button',name='下一步',exact=True).click()
    page.get_by_text('未明确课程',exact=True).click()
    page.get_by_role('button',name='添加未明确课程').click()
    page.get_by_role('button',name='下一步',exact=True).click()
    expect(page.get_by_role('button',name='提交客资')).to_be_visible()

def wb_submit(page):
    page.get_by_role('button',name='提交客资').click()
    page.get_by_role('button',name='确认执行',exact=True).click()

def admin_open(page, query=''):
    page.goto('http://127.0.0.1:5194/test/self-sourced-auto.html'+query)
    page.get_by_text('打开录单',exact=True).click()
    expect(page.get_by_role('dialog')).to_be_visible()

def admin_field(page,label):
    return page.locator('.el-form-item').filter(has=page.locator('.el-form-item__label').get_by_text(label,exact=True))

def admin_select(page,label,option):
    admin_field(page,label).locator('.el-select').click()
    page.locator('.el-select-dropdown:visible').get_by_text(option,exact=True).click()

def admin_base(page, remark='  已联系，有意向  '):
    admin_field(page,'客户姓名').locator('input').fill('浏览器测试客户')
    admin_field(page,'手机号').locator('input').fill('13800138000')
    admin_field(page,'客户地区').locator('input').click()
    page.get_by_text('其他省份',exact=True).click()
    page.get_by_text('其他城市',exact=True).click()
    for label, option in [('来源渠道','测试渠道'),('客资分类','测试分类'),('意向课程','测试课程'),('具体方案','测试方案')]:
        admin_select(page,label,option)
    admin_field(page,'备注').locator('textarea').fill(remark)

with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    page=browser.new_page(viewport={'width':1440,'height':1000})
    page.on('pageerror',lambda error:errors.append(str(error)))
    for width in [1440,390]:
        page.set_viewport_size({'width':width,'height':1000 if width==1440 else 844})
        wb_open(page)
        expect(page.get_by_text(HINT,exact=True)).to_be_visible()
        wb_base(page,'   ')
        page.get_by_role('button',name='下一步',exact=True).click()
        expect(page.get_by_text(REQUIRED,exact=True)).to_be_visible()
        page.locator('#remark').fill('  已联系，有意向  ')
        wb_confirm(page)
        expect(page.get_by_text('不安排',exact=True)).to_be_visible()
        page.screenshot(path=str(OUT/f'workbench-{width}.png'),full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth+2')
        wb_submit(page)
        expect(page.get_by_text('客资已判有效',exact=True).last).to_be_visible()
        req=page.evaluate('window.autoFixture.requests.at(-1)')
        assert req['remark']=='已联系，有意向' and 'selfSourcedNextFollowUpAt' not in req

        admin_open(page)
        expect(page.get_by_text(HINT,exact=True)).to_be_visible()
        admin_base(page,'   ')
        page.get_by_role('button',name='确认提交',exact=True).click()
        expect(page.get_by_text(REQUIRED,exact=True)).to_be_visible()
        admin_field(page,'备注').locator('textarea').fill('  已联系，有意向  ')
        admin_field(page,'下次跟进').locator('input').first.focus()
        admin_field(page,'下次跟进').locator('input').first.press('Tab')
        expect(page.get_by_text(REQUIRED,exact=True)).not_to_be_visible()
        page.locator('.el-overlay-dialog').evaluate('(element) => { element.scrollTop = 0 }')
        page.screenshot(path=str(OUT/f'admin-{width}.png'),full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth+2')
        page.get_by_role('button',name='确认提交',exact=True).click()
        expect(page.get_by_text('客资已判有效，已生成首跟记录和商机',exact=True)).to_be_visible()
        req=page.evaluate('window.autoFixture.requests.at(-1)')
        assert req['remark']=='已联系，有意向' and 'selfSourcedNextFollowUpAt' not in req

    page.set_viewport_size({'width':1440,'height':1000})
    wb_open(page)
    wb_base(page);wb_confirm(page)
    page.locator('#selfSourcedNextFollowUpAt').fill('2020-01-01 10:00')
    page.locator('#selfSourcedNextFollowUpAt').press('Enter')
    page.get_by_role('button',name='提交客资').click()
    expect(page.get_by_text('下次跟进时间必须晚于当前时间',exact=True)).to_be_visible()
    page.locator('#selfSourcedNextFollowUpAt').fill('2099-01-01 10:00')
    page.locator('#selfSourcedNextFollowUpAt').press('Enter')
    page.get_by_role('button',name='上一步',exact=True).click()
    page.get_by_role('button',name='上一步',exact=True).click()
    page.locator('#newMediaProviderUserId').click()
    page.locator('.ant-select-item-option-content').get_by_text('测试提供方',exact=True).click()
    expect(page.get_by_text(HINT,exact=True)).not_to_be_visible()
    page.locator('#remark').fill('')
    page.get_by_role('button',name='下一步',exact=True).click()
    page.get_by_role('button',name='下一步',exact=True).click()
    wb_submit(page)
    expect(page.get_by_text('客资提交成功',exact=True).last).to_be_visible()
    assert 'selfSourcedNextFollowUpAt' not in page.evaluate('window.autoFixture.requests.at(-1)')

    admin_open(page);admin_base(page)
    date=admin_field(page,'下次跟进').locator('input').first
    date.fill('2020-01-01 10:00:00');date.press('Enter')
    page.get_by_role('button',name='确认提交',exact=True).click()
    expect(page.get_by_text('下次跟进时间必须晚于当前时间',exact=True)).to_be_visible()
    date.fill('2099-01-01 10:00:00');date.press('Enter')
    admin_select(page,'新媒体提供方','测试提供方')
    expect(page.get_by_text(HINT,exact=True)).not_to_be_visible()
    admin_field(page,'备注').locator('textarea').fill('')
    page.get_by_role('button',name='确认提交',exact=True).click()
    expect(page.get_by_text('客资已提交',exact=True)).to_be_visible()
    assert 'selfSourcedNextFollowUpAt' not in page.evaluate('window.autoFixture.requests.at(-1)')

    for outcome, wb_title, admin_title in [('review_pending','疑似重复，等待管理员审核','疑似重复，待复核；尚未新建或判有效'),('activated','客资已存在，已激活提醒','客资已存在，已激活提醒')]:
        wb_open(page,'?outcome='+outcome);wb_base(page);wb_confirm(page);wb_submit(page)
        expect(page.get_by_text(wb_title,exact=True).last).to_be_visible()
        expect(page.get_by_text('客资已判有效',exact=True).last).not_to_be_visible()
        admin_open(page,'?outcome='+outcome);admin_base(page)
        page.get_by_role('button',name='确认提交',exact=True).click()
        expect(page.get_by_text(admin_title,exact=True)).to_be_visible()

    wb_open(page);wb_base(page);wb_confirm(page)
    page.locator('#selfSourcedNextFollowUpAt').fill('2099-01-01 10:00')
    page.locator('#selfSourcedNextFollowUpAt').press('Enter')
    page.evaluate('window.autoFixture.fail=true')
    wb_submit(page)
    expect(page.get_by_text('模拟提交失败',exact=True).last).to_be_visible()
    page.get_by_role('button',name='知道了').click()
    expect(page.locator('.ant-modal-wrap:visible')).to_have_count(0)
    page.evaluate('window.autoFixture.fail=false')
    wb_submit(page)
    expect(page.get_by_text('客资已判有效',exact=True).last).to_be_visible()
    reqs=page.evaluate('window.autoFixture.requests')
    assert len(reqs)==2 and reqs[0]['idempotencyKey']==reqs[1]['idempotencyKey']
    assert isinstance(reqs[1]['selfSourcedNextFollowUpAt'],int)

    admin_open(page);admin_base(page)
    date=admin_field(page,'下次跟进').locator('input').first
    date.fill('2099-01-01 10:00:00');date.press('Enter')
    page.evaluate('window.autoFixture.fail=true')
    page.get_by_role('button',name='确认提交',exact=True).click()
    expect(page.get_by_text('模拟提交失败',exact=True).last).to_be_visible()
    page.evaluate('window.autoFixture.fail=false')
    page.get_by_role('button',name='确认提交',exact=True).click()
    expect(page.get_by_text('客资已判有效，已生成首跟记录和商机',exact=True)).to_be_visible()
    reqs=page.evaluate('window.autoFixture.requests')
    assert len(reqs)==2 and reqs[0]['idempotencyKey']==reqs[1]['idempotencyKey']
    assert isinstance(reqs[1]['selfSourcedNextFollowUpAt'],int)

    wb_open(page,'?education');expect(page.get_by_text(HINT,exact=True)).not_to_be_visible()
    admin_open(page,'?education');expect(page.get_by_text(HINT,exact=True)).not_to_be_visible()
    for width in [1440,390]:
        page.set_viewport_size({'width':width,'height':844})
        page.goto('http://127.0.0.1:5193/test/self-sourced-auto.html?history')
        expect(page.get_by_text('销售自拓录单自动生成',exact=True)).to_be_visible()
        expect(page.locator('.chart-total-number')).to_have_text('1')
        expect(page.get_by_text('联系方式：录单时其他方式',exact=True)).to_be_visible()
        expect(page.get_by_text('录单人：测试销售',exact=True)).to_be_visible()
        expect(page.get_by_text('分类：录单分类',exact=True)).to_be_visible()
        page.screenshot(path=str(OUT/f'history-{width}.png'),full_page=True)
    assert not errors, errors
    browser.close()
print('PASS: both frontend forms at 1440/390, required remark, optional time, provider switch, review/activation/education, history and manual charts')
