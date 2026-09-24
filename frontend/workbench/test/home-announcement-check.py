# UTF-8. Run from repository root; existing local Vite at 5174 is used for integration.
from pathlib import Path
import json
import sys
from playwright.sync_api import sync_playwright, expect

OUT = Path(__file__).resolve().parents[3] / 'output'

def contrast(pair):
    def luminance(s):
        rgb = [float(x) / 255 for x in s[s.index('(')+1:s.index(')')].split(',')[:3]]
        values = [x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in rgb]
        return sum(x*w for x, w in zip(values, [.2126, .7152, .0722]))
    a, b = map(luminance, pair)
    return (max(a, b) + .05) / (min(a, b) + .05)

with sync_playwright() as p:
    browser = p.chromium.launch()
    errors = []
    if '--integration-only' not in sys.argv:
        page = browser.new_page(viewport={'width': 1280, 'height': 1000})
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto((OUT / 'announcement-demo.html').as_uri(), wait_until='domcontentloaded')
        expect(page.locator('.home-announcement-item')).to_have_count(5)
        matrix = []
        for preset in page.locator('[aria-label="UI主题"] option').evaluate_all('(es)=>es.map(e=>e.value)'):
            page.select_option('[aria-label="UI主题"]', preset)
            page.wait_for_timeout(120)
            pairs = page.evaluate('''()=>['.home-announcement-pin','.home-announcement-unread','.home-announcement-count'].map(s=>{const c=getComputedStyle(document.querySelector(s));return [c.color,c.backgroundColor]})''')
            ratios = [round(contrast(pair), 2) for pair in pairs]
            assert min(ratios) >= 4.5, (preset, pairs, ratios)
            matrix.append({'theme': preset, 'contrast': ratios})
            page.screenshot(path=str(OUT / f'announcement-demo-theme-{preset}.png'), full_page=True)
        page.select_option('[aria-label="UI主题"]', 'default-light')
        pin_before = page.locator('.home-announcement-pin').first.evaluate('(e)=>getComputedStyle(e).color')
        page.locator('[aria-label="主题主色"]').fill('#ed4192')
        assert page.locator('.home-announcement-pin').first.evaluate('(e)=>getComputedStyle(e).color') == pin_before
        page.locator('[aria-label="玻璃背景"]').click()
        page.select_option('[aria-label="密度"]', 'compact')
        page.select_option('[aria-label="字号"]', 'large')
        for width in [1280, 390, 320]:
            page.set_viewport_size({'width': width, 'height': 1000})
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
        page.screenshot(path=str(OUT / 'announcement-demo-mobile-glass.png'), full_page=True)
        page.locator('[aria-label="玻璃背景"]').click()
        page.select_option('[aria-label="密度"]', 'default')
        page.select_option('[aria-label="字号"]', 'default')
        page.set_viewport_size({'width': 1280, 'height': 1000})
        page.locator('[aria-label="主题主色"]').fill('#1677ff')
        # Continuous behaviour must survive the old two-cycle limit.
        page.wait_for_timeout(31000)
        assert page.locator('.home-announcement-unread i').first.evaluate('(e)=>e.getAnimations({subtree:true}).some(a=>a.playState==="running" && a.effect.getTiming().iterations===Infinity)')
        a = page.locator('.home-announcement-unread i').first.evaluate('(e)=>getComputedStyle(e,"::after").transform')
        page.wait_for_timeout(400)
        assert a != page.locator('.home-announcement-unread i').first.evaluate('(e)=>getComputedStyle(e,"::after").transform')
        page.locator('[aria-label="持续动画"]').click()
        assert page.locator('.home-announcement-unread i').first.evaluate('(e)=>getComputedStyle(e,"::after").animationName') == 'none'
        page.locator('[aria-label="持续动画"]').click()
        page.emulate_media(reduced_motion='reduce')
        assert page.locator('.home-announcement-pin .anticon').first.evaluate('(e)=>getComputedStyle(e).animationName') == 'none'
        page.emulate_media(reduced_motion='no-preference')
        page.evaluate('Object.defineProperty(document,"hidden",{configurable:true,value:true});document.dispatchEvent(new Event("visibilitychange"))')
        assert page.locator('.home-announcement-pin .anticon').first.evaluate('(e)=>getComputedStyle(e).animationPlayState') == 'paused'
        page.evaluate('Object.defineProperty(document,"hidden",{configurable:true,value:false});document.dispatchEvent(new Event("visibilitychange"))')
        page.evaluate('window.scrollTo(0,document.body.scrollHeight)')
        page.wait_for_timeout(150)
        # IntersectionObserver is separately exercised by placing the panel outside the viewport.
        page.locator('.home-announcement-panel').evaluate('(e)=>e.style.marginTop="1500px"')
        page.evaluate('window.scrollTo(0,0)')
        page.wait_for_timeout(150)
        assert page.locator('.home-announcement-item').first.get_attribute('data-offscreen') == 'true'
        page.locator('.home-announcement-panel').evaluate('(e)=>e.style.marginTop=""')
        page.get_by_role('button', name='模拟新公告', exact=True).click()
        expect(page.locator('.home-announcement-incoming')).to_be_visible()
        expect(page.locator('.home-announcement-item')).to_have_count(5)
        page.locator('.home-announcement-incoming').click()
        expect(page.locator('.home-announcement-list .ant-skeleton')).to_be_visible()
        expect(page.locator('.home-announcement-item')).to_have_count(6)
        page.get_by_role('button', name='重置演示', exact=True).click()
        page.select_option('[aria-label="验证场景"]', 'read')
        page.locator('[data-announcement-id="1"]').click()
        expect(page.get_by_text('阅读确认失败，仍保留未读')).to_be_visible()
        expect(page.locator('[data-announcement-id="1"]')).to_have_class('home-announcement-item unread highlighted')
        page.get_by_role('button', name='重试阅读确认', exact=True).click()
        page.get_by_role('button', name='关闭详情', exact=True).click()
        expect(page.locator('[data-announcement-id="1"] .home-announcement-unread')).to_have_count(0)
        expect(page.locator('[data-announcement-id="1"] .home-announcement-pin')).to_have_count(1)
        for id in [3, 4]:
            page.locator(f'[data-announcement-id="{id}"]').click()
            page.get_by_role('button', name='关闭详情', exact=True).click()
        expect(page.locator('.home-announcement-count')).to_have_count(0)
        page.get_by_role('button', name='重置演示', exact=True).click()
        page.select_option('[aria-label="验证场景"]', 'empty')
        expect(page.get_by_text('暂无公告', exact=True)).to_be_visible()
        page.get_by_role('button', name='重置演示', exact=True).click()
        page.screenshot(path=str(OUT / 'announcement-demo-desktop.png'), full_page=True)
        page.set_viewport_size({'width': 390, 'height': 844})
        page.screenshot(path=str(OUT / 'announcement-demo-mobile.png'), full_page=True)
        assert not errors, errors
        print('PASS demo: 13 themes/contrast, primary independence, glass/density/font, 1280/390/320, 31s motion, off/reduced/background/offscreen, read failure/retry/zero, incoming', flush=True)
        (OUT / 'announcement-demo-contrast.json').write_text(json.dumps(matrix, ensure_ascii=False, indent=2), encoding='utf-8')

    # Browser context and synthetic token exist only in this isolated test browser.
    live = browser.new_page(viewport={'width': 1280, 'height': 1000})
    live.add_init_script("localStorage.setItem('ACCESS_TOKEN','isolated-fixture');")
    live.on('pageerror', lambda e: errors.append(str(e)))
    live.route('**/admin-api/**', lambda route: route.abort())
    live.route('**/*alipayobjects.com/**', lambda route: route.abort())
    live.goto('http://localhost:5174/test/home-announcement-integration.html', wait_until='domcontentloaded')
    expect(live.locator('.home-announcement-count')).to_have_text('未读 2')
    live.get_by_role('button', name='发布事件', exact=True).click()
    expect(live.locator('.home-announcement-incoming')).to_be_visible()
    expect(live.locator('.home-announcement-item')).to_have_count(4)
    live.locator('.home-announcement-incoming').click()
    expect(live.locator('.home-announcement-item')).to_have_count(5)
    expect(live.locator('.home-announcement-incoming')).to_have_count(0)
    live.get_by_role('button', name='汇总失败', exact=True).click()
    live.get_by_role('button', name='刷新公告', exact=True).click()
    expect(live.get_by_text('未读数更新失败，当前显示上次结果')).to_be_visible()
    live.get_by_role('button', name='恢复接口', exact=True).click()
    live.get_by_role('button', name='重试未读数', exact=True).click()
    expect(live.get_by_text('未读数更新失败，当前显示上次结果')).to_have_count(0)
    live.get_by_role('button', name='列表失败', exact=True).click()
    live.get_by_role('button', name='刷新公告', exact=True).click()
    expect(live.get_by_text('列表加载失败（隔离验证）')).to_be_visible()
    live.get_by_role('button', name='恢复接口', exact=True).click()
    live.locator('.home-announcement-panel .ant-alert button').click()
    expect(live.get_by_text('列表加载失败（隔离验证）')).to_have_count(0)
    live.get_by_role('button', name='阅读失败', exact=True).click()
    live.locator('[data-announcement-id="1"]').click()
    expect(live.get_by_text('阅读确认失败（隔离验证）')).to_be_visible()
    live.get_by_role('button', name='返回首页', exact=True).click()
    expect(live.locator('[data-announcement-id="1"] .home-announcement-unread')).to_have_count(1)
    live.get_by_role('button', name='恢复接口', exact=True).click()
    live.locator('[data-announcement-id="1"]').click()
    expect(live.get_by_text('隔离测试内容', exact=True).last).to_be_visible()
    live.locator('.ant-drawer-close').click()
    live.get_by_role('button', name='返回首页', exact=True).click()
    expect(live.locator('[data-announcement-id="1"] .home-announcement-unread')).to_have_count(0)
    expect(live.locator('[data-announcement-id="1"] .home-announcement-pin')).to_have_count(1)
    live.get_by_role('button', name='切换权限', exact=True).click()
    expect(live.get_by_text('暂无公告查看权限')).to_be_visible()
    expect(live.locator('.home-announcement-count')).to_have_count(0)
    live.goto('http://localhost:5174/test/home-announcement-integration.html?failure=summary', wait_until='domcontentloaded')
    expect(live.get_by_text('未读数暂不可用')).to_be_visible()
    expect(live.locator('.home-announcement-count')).to_have_count(0)
    assert not errors, errors
    print('PASS integration: real providers/list/event/manual refresh, first/stale summary failures, list retry, actual detail read failure/success, permission gate', flush=True)
    browser.close()
