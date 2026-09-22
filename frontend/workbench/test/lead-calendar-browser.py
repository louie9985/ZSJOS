# UTF-8. Real Chrome, synthetic API fixtures only; no shared writes.
import datetime as dt
import re
from pathlib import Path
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright, expect

OUT = Path('D:/ZSJ-OS-backups/lead-calendar-browser')
OUT.mkdir(parents=True, exist_ok=True)
day = dt.date.today().isoformat()
stamp = int(dt.datetime.now().timestamp() * 1000)
state = dict(saved=False, fail_days=False, fail_cards=False, card_queries=[], day_queries=[], posts=[], days_calls=0)
errors = []

def lead(i):
    return dict(id=i, leadNo=f'KZ-TEST-{i:03}', submittedName=f'测试客资{i}', submittedMobile='13800000000', submittedWechatId=f'calendar_test_{i}',
                status='valid', assignmentStatus='owned', ownerUserId=7, submittedAt=stamp, relationTypes=['owner'],
                leadCategory='S' if i == 2 else 'A', leadCategoryLabelSnapshot='历史S类名称' if i == 2 else '历史A类名称', salesStage='contacted', salesStageLabelSnapshot='已触达',
                overviewVisible=True, visibleTabs=['overview'], availableActions=[dict(code='ADD_FOLLOW_UP', enabled=i != 3)], attachments=[], intendedProducts=[])

def handler(route):
    req = route.request
    parsed = urlparse(req.url)
    path, q = parsed.path, parse_qs(parsed.query)
    data = []
    if path.endswith('/lead-follow-up-calendar/days'):
        state['days_calls'] += 1
        state['day_queries'].append(q)
        if state['fail_days']:
            route.fulfill(json=dict(code=403, msg='没有日历访问权限')); return
        data = [dict(date=day, count=2 if state['saved'] else 3)]
    elif path.endswith('/lead-follow-up-calendar/cards'):
        state['card_queries'].append(q)
        if state['fail_cards']:
            route.fulfill(json=dict(code=500, msg='当天客资加载失败')); return
        ids = [2,3] if state['saved'] else [1,2,3]
        if q.get('sort') == ['category']: ids = sorted(ids, key=lambda x: x != 2)
        if q.get('direction') == ['desc']: ids.reverse()
        if q.get('start') != [day]: ids = []
        data = dict(total=len(ids), list=[dict(lead=lead(i), deadline=stamp+i*60000, canReadFollowUp=i != 3,
            lastFollowUp=None if i==3 else dict(id=i, leadId=i, occurredAt=stamp-86400000, operatorName='测试销售', methodLabel='电话', resultLabel='取得联系', remark='已沟通课程安排，待确认报名时间。', images=[])) for i in ids])
    elif path.endswith('/lead/get'): data = lead(int(q['id'][0]))
    elif '/follow-ups' in path and req.method == 'POST':
        state['posts'].append(req.post_data_json); state['saved'] = True; data = dict(id=99)
    elif 'dict-data' in path:
        entries = {'zsjos_lead_category':[('S','S类'),('A','A类')], 'zsjos_lead_sales_stage':[('contacted','已触达'),('intent_customer','意向客户')],
                   'zsjos_lead_follow_up_method':[('phone','电话')], 'zsjos_lead_follow_up_result':[('connected','取得联系')]}
        data = [dict(id=i+1, value=v, label=l, dictType=kind, sort=i, status=0) for kind,values in entries.items() for i,(v,l) in enumerate(values)]
    elif path.endswith('/page'): data = dict(list=[], total=0)
    route.fulfill(json=dict(code=0, data=data))

def choose(page, control, label):
    control.click()
    page.locator('.ant-select-dropdown:visible').get_by_text(label, exact=True).click()

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    context = browser.new_context(viewport=dict(width=1600,height=1100), permissions=['clipboard-read','clipboard-write'])
    page = context.new_page()
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.route('**/admin-api/**', handler)
    page.goto('http://127.0.0.1:5174/test/lead-calendar.html')
    count = page.locator('.lead-calendar-count')
    expect(count).to_have_text('3')
    initial_start = state['day_queries'][-1]['start']
    page.get_by_role('button',name='下个月',exact=True).click()
    expect(count).not_to_be_visible()
    assert state['day_queries'][-1]['start'] != initial_start
    page.get_by_role('button',name=re.compile(r'今\s*天')).click()
    expect(count).to_have_text('3')
    assert state['day_queries'][-1]['start'] == initial_start
    expect(page.get_by_text('测试客资1', exact=True)).not_to_be_visible()
    count.click()
    dialog = page.get_by_role('dialog', name=re.compile('待跟进客资'))
    expect(dialog.locator('.lead-calendar-card')).to_have_count(3)
    expect(dialog.locator('.lead-calendar-card h5').first).to_have_text('测试客资1')
    page.mouse.click(3,3)
    expect(dialog).to_be_visible()
    expect(dialog.get_by_text('暂无跟进记录查看权限')).to_be_visible()
    expect(dialog.locator('.lead-calendar-card').nth(2).get_by_role('button',name='填写跟进记录')).to_be_disabled()
    sizes = dialog.locator('.lead-calendar-actions').first.locator('button').evaluate_all('(buttons)=>buttons.map(b=>b.getBoundingClientRect().width)')
    assert abs(sizes[0]-sizes[1])<1
    dialog.locator('.ant-typography-copy').first.click()
    assert page.evaluate('navigator.clipboard.readText()') == '13800000000'
    dialog.locator('.ant-typography-copy').nth(1).click()
    assert page.evaluate('navigator.clipboard.readText()') == 'calendar_test_1'
    choose(page, dialog.get_by_role('combobox',name='客资排序字段'), '客资分类等级')
    expect(dialog.locator('.lead-calendar-card h5').first).to_have_text('测试客资2')
    assert state['card_queries'][-1]['sort']==['category']
    choose(page, dialog.get_by_role('combobox',name='排序方向'), '倒序')
    expect(dialog.locator('.lead-calendar-card h5').first).to_have_text('测试客资3')
    choose(page, dialog.get_by_role('combobox',name='客资排序字段'), '跟进截止时间')
    choose(page, dialog.get_by_role('combobox',name='排序方向'), '最早优先')
    expect(dialog.locator('.lead-calendar-card h5').first).to_have_text('测试客资1')
    expect(page.locator('.ant-select-dropdown:visible')).to_have_count(0)
    page.screenshot(path=str(OUT/'cards-desktop.png'),full_page=True)
    dialog.locator('.lead-calendar-card').first.get_by_role('button',name='查看详情').click()
    detail = page.get_by_role('dialog',name='客资详情',exact=True)
    expect(detail.get_by_text('测试客资1',exact=True).first).to_be_visible()
    expect(dialog).to_be_visible()
    detail.get_by_role('button',name='Close',exact=True).click()
    dialog.locator('.lead-calendar-card').first.get_by_role('button',name='填写跟进记录').click()
    follow = page.get_by_role('dialog',name='新增跟进',exact=True)
    choose(page, follow.get_by_label('跟进方式',exact=True), '电话')
    choose(page, follow.get_by_label('跟进结果',exact=True), '取得联系')
    follow.get_by_label('跟进备注',exact=True).fill('日历快捷跟进验收')
    follow.get_by_role('button',name='+1 天',exact=True).click()
    follow.get_by_role('button',name='提交跟进',exact=True).click()
    page.get_by_role('button',name='确认执行',exact=True).click()
    expect(follow).not_to_be_visible()
    expect(dialog.locator('.lead-calendar-card')).to_have_count(2)
    expect(count).to_have_text('2')
    assert len(state['posts'])==1 and state['posts'][0]['remark']=='日历快捷跟进验收'
    assert state['days_calls']>=2
    state['fail_cards']=True
    dialog.get_by_role('button',name=re.compile('刷新')).click()
    expect(dialog.get_by_text('当天客资加载失败',exact=True)).to_be_visible()
    state['fail_cards']=False
    dialog.locator('.ant-alert button').click()
    expect(dialog.locator('.lead-calendar-card')).to_have_count(2)
    page.set_viewport_size(dict(width=390,height=844))
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
    assert dialog.evaluate('(d)=>d.scrollWidth<=d.clientWidth')
    page.screenshot(path=str(OUT/'cards-mobile.png'),full_page=True)
    dialog.get_by_role('button',name='Close',exact=True).click()
    expect(dialog).not_to_be_visible()
    page.screenshot(path=str(OUT/'calendar-mobile.png'),full_page=True)
    state['fail_days']=True
    page.locator('#root').get_by_role('button',name=re.compile('刷新')).click()
    expect(page.get_by_text('没有日历访问权限',exact=True)).to_be_visible()
    state['fail_days']=False
    page.locator('.ant-alert button').click()
    expect(count).to_have_text('2')
    # Empty date opens an empty, closable drilldown rather than retaining the preceding date's cards.
    page.locator('.ant-picker-cell-in-view').filter(has_not=page.locator('.lead-calendar-count')).first.click()
    expect(page.get_by_text('当天暂无待跟进客资',exact=True)).to_be_visible()
    page.goto('http://127.0.0.1:5174/test/lead-calendar.html?denied')
    expect(page.get_by_text('无权查看销售客资跟进日历，请联系管理员配置权限')).to_be_visible()
    assert not errors, errors
    browser.close()
print('PASS real Chrome desktop/mobile: counts, cards, mask, copy, sorting, nested detail, follow-up save+refresh, permissions, errors/retry and empty date')
