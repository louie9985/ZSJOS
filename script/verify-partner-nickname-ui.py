"""UTF-8: real Chromium UI acceptance with synthetic API responses; no live account writes."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT = Path(__file__).resolve().parents[1] / 'output/partner-nickname'
OUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (390, 1280):
        page = browser.new_page(viewport={'width': width, 'height': 900})
        profile = dict(name='邀请姓名', nickname=None, mobile='13800000000', sex=0)
        writes = []
        state = {'fail': False}
        def api(route):
            path = route.request.url.split('?')[0]
            data = {}
            if path.endswith('/auth/activate'):
                data = dict(userId=1, accessToken='synthetic-only', refreshToken='synthetic-only', clientId='zsjos-mobile')
            elif path.endswith('/auth/permission-info'):
                data = dict(user=dict(id=1, nickname=profile['name']), permissions=[], roles=[])
            elif path.endswith('/profile/get'):
                if state['fail']:
                    route.fulfill(status=500, content_type='application/json', body='{}'); return
                data = profile
            elif path.endswith('/profile/update'):
                writes.append(route.request.post_data_json)
                profile.update(writes[-1])
                data = True
            elif path.endswith('/partner/me'):
                data = dict(id=1, partnerNo='TEST', status='enabled', **profile)
            elif path.endswith('/leaderboard/config'):
                data = dict(enabled=True, enabledTypes=['lead_count'], defaultType='lead_count', defaultPeriod='month', pageSize=20,
                            maskName=True, typeOptions=[dict(key='lead_count', label='提交客资', valueLabel='客资数', valueUnit='count', ruleText='测试')])
            elif path.endswith('/leaderboard'):
                members = [dict(partnerId=1, displayName=profile['nickname'], rank=1, value=3, isMe=True),
                           dict(partnerId=2, displayName='未设置昵称', rank=2, value=1, isMe=False)]
                data = dict(period='month', type='lead_count', typeLabel='提交客资', valueUnit='count', list=members, top3=members,
                            myRank=members[0], nearbyRanks=members, total=2, pageNo=1, pageSize=20)
            elif 'unread' in path:
                data = 0
            route.fulfill(content_type='application/json; charset=utf-8', body=json.dumps(dict(code=0, data=data), ensure_ascii=False))
        page.route('**/part-api/**', api)
        page.goto('http://localhost:10086/login?redirect=/profile')
        page.get_by_text('首次使用？激活账号', exact=True).click()
        page.get_by_placeholder('请输入手机号').fill('13800000000')
        page.get_by_placeholder('设置新密码').fill('Synthetic123')
        page.get_by_placeholder('确认新密码').fill('Synthetic123')
        page.get_by_placeholder('请输入邀请码').fill('TEST1234')
        page.get_by_role('button', name='激活并登录', exact=True).click()
        page.get_by_role('button', name='同意并继续').click()
        expect(page.get_by_text('完善个人信息', exact=True)).to_be_visible()
        expect(page.get_by_placeholder('请输入姓名')).to_have_value('邀请姓名')
        expect(page.get_by_text('头像地址', exact=True)).to_have_count(0)
        expect(page.get_by_text('请使用真实姓名，方便后续返现财务审核', exact=True)).to_be_visible()
        page.get_by_role('button', name='保存并进入').click()
        assert not writes
        page.get_by_placeholder('请输入姓名').fill('真实姓名')
        page.get_by_placeholder('用于排行榜展示').fill('榜单星光')
        expect(page.locator('.van-toast')).to_be_hidden(timeout=5000)
        page.screenshot(path=str(OUT / f'completion-{width}.png'), full_page=True)
        page.get_by_role('button', name='保存并进入').click()
        expect(page.get_by_text('真实姓名', exact=True)).to_be_visible()
        assert writes[-1]['name'] == '真实姓名' and writes[-1]['nickname'] == '榜单星光'
        page.goto('http://localhost:10086/leaderboard')
        expect(page.get_by_text('榜单星光（我）', exact=True).first).to_be_visible()
        expect(page.get_by_text('未设置昵称', exact=True).first).to_be_visible()
        assert '真实姓名' not in page.locator('body').inner_text()
        page.screenshot(path=str(OUT / f'leaderboard-{width}.png'), full_page=True)
        state['fail'] = True
        page.goto('http://localhost:10086/profile/edit')
        expect(page.get_by_text('个人资料加载失败')).to_be_visible()
        state['fail'] = False
        page.get_by_role('button', name='重新加载').click()
        expect(page.get_by_placeholder('请输入姓名')).to_have_value('真实姓名')
        page.get_by_placeholder('用于排行榜展示').fill('仅改昵称')
        page.get_by_role('button', name='保存', exact=True).click()
        page.wait_for_timeout(300)
        assert writes[-1]['name'] == '真实姓名' and writes[-1]['nickname'] == '仅改昵称'
        page.close()
        print('PASS activation completion, independent fields, real-name hint, profile name, leaderboard nickname/unset, retry:', width)
    browser.close()
