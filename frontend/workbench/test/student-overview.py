# -*- coding: utf-8 -*-
"""Exercise production page with synthetic transport; never write live data."""
from pathlib import Path
import re
from playwright.sync_api import sync_playwright, expect

BASE = 'http://127.0.0.1:5174/test/student-overview.html'
OUTPUT = Path(__file__).resolve().parents[3] / 'output/student-overview-acceptance'
OUTPUT.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1600, 'height': 1000})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/test/material-cover.svg', lambda route: route.fulfill(content_type='image/svg+xml', body='<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360"><rect width="480" height="360" fill="#e0edff"/><rect x="60" y="40" width="140" height="280" rx="12" fill="#6d99d6"/><path d="M240 100h180M240 140h150M240 180h160" stroke="#7393bc" stroke-width="12"/></svg>'))
    image_state = {'fail': False}
    page.route('**/test/overview-image.png*', lambda route: route.abort() if image_state['fail'] else route.fulfill(headers={'Cache-Control': 'no-store'}, content_type='image/svg+xml', body='<svg xmlns="http://www.w3.org/2000/svg" width="640" height="320"><rect width="640" height="320" fill="#bdd5e7"/><circle cx="320" cy="160" r="80" fill="#4989bd"/></svg>'))
    def load(query=''):
        page.goto(BASE + query)
        expect(page.locator('.student-overview-grid')).to_be_visible()
        expect(page.locator('.student-overview-flow').get_by_text('待运营复核', exact=True)).to_be_visible()

    load()
    expect(page.locator('.student-overview-status').get_by_text('兼职账号已开通')).to_be_visible()
    expect(page.locator('.student-overview-background').get_by_text('示例单位', exact=True)).to_be_visible()
    box = page.locator('.student-overview-grid').evaluate('e=>{const a=e.querySelector(".student-overview-main").getBoundingClientRect(),b=e.querySelector(".student-overview-aside").getBoundingClientRect();return {a:a.width,b:b.width,gap:b.left-a.right}}')
    # A 9:3 grid includes 8 vs 2 internal gutters in each spanning item.
    assert abs((box['a'] + box['gap']) / (box['b'] + box['gap']) - 3) < .01, box
    expect(page.locator('.positioning-reading-group h4').first).to_have_text('账号基础')
    expect(page.get_by_text('历史专业甲、历史专业乙', exact=True)).to_be_visible()
    expect(page.locator('.attachment-card')).to_have_count(4)
    before = len(page.evaluate('overviewFixture.calls'))
    with page.expect_download() as downloaded:
        page.locator('.attachment-card').first.get_by_role('button', name=re.compile(r'下\s*载')).click()
    assert downloaded.value.suggested_filename.endswith('.md')
    assert len(page.evaluate('overviewFixture.calls')) > before
    expect(page.locator('.positioning-reading-field-name').first).to_have_text('账号名称建议：')
    short_fields = page.locator('.positioning-reading-group').first.locator('.positioning-reading-field')
    assert abs(short_fields.nth(0).bounding_box()['y'] - short_fields.nth(1).bounding_box()['y']) < 2
    assert float(short_fields.first.locator('strong').first.evaluate('e=>getComputedStyle(e).fontSize').replace('px','')) >= 13
    assert page.locator('.positioning-feedback').first.bounding_box()['y'] < short_fields.first.bounding_box()['y']
    expect(page.locator('.resource-link-card a').first).to_have_attribute('href', 'https://example.com/reference')
    page.get_by_role('button', name='预览图片：确认图片.png', exact=True).click()
    expect(page.get_by_role('dialog')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_role('img', name='确认图片.png', exact=True)).to_be_visible()
    page.wait_for_timeout(350)  # Finish the modal transition before visual capture.
    page.screenshot(path=str(OUTPUT / 'image-preview.png'))
    with page.expect_download() as picture:
        page.get_by_role('button', name=re.compile('下载原文件')).click()
    assert picture.value.suggested_filename.endswith('.png')
    page.keyboard.press('Escape')
    expect(page.get_by_role('dialog')).not_to_be_visible()
    expect(page.locator('.attachment-type-icon.kind-document')).to_have_count(3)
    expect(page.locator('.positioning-reading-field-hint')).to_have_count(0)
    expect(page.get_by_text('参考提示原文', exact=True)).to_have_count(0)
    expect(page.locator('.positioning-feedback .positioning-evidence').get_by_text('当前版本确认凭证.md', exact=True)).to_be_visible()
    platforms = page.locator('.positioning-plan-platform .positioning-plan-item')
    stages = page.locator('.positioning-plan-stage .positioning-plan-item')
    expect(platforms).to_have_count(4)
    expect(stages).to_have_count(7)
    platform_boxes = [platforms.nth(i).bounding_box() for i in range(4)]
    assert max(b['y'] for b in platform_boxes) - min(b['y'] for b in platform_boxes) < 2
    stage_boxes = [stages.nth(i).bounding_box() for i in range(7)]
    stage_grid = page.locator('.positioning-plan-stage .positioning-plan-grid').bounding_box()
    assert abs(stage_boxes[0]['width'] - stage_grid['width']) < 2
    assert stage_boxes[0]['y'] < stage_boxes[1]['y'] < stage_boxes[4]['y']
    for row in (stage_boxes[1:4], stage_boxes[4:7]):
        assert max(b['y'] for b in row) - min(b['y'] for b in row) < 2
    expect(stages.first.locator('.positioning-material-card')).to_have_count(0)
    expect(page.locator('.positioning-material-card')).to_have_count(10)
    expect(platforms.nth(1).locator('.positioning-material-cover img')).to_be_visible()
    expect(platforms.nth(2).get_by_text('暂无封面', exact=True)).to_be_visible()
    platforms.first.scroll_into_view_if_needed()
    page.screenshot(path=str(OUTPUT / 'platform-grid.png'))
    stages.first.scroll_into_view_if_needed()
    page.screenshot(path=str(OUTPUT / 'stage-grid.png'))
    platforms.first.get_by_role('button', name='预览参考内容', exact=False).click()
    expect(page.get_by_role('dialog').get_by_text('当前版本的素材内容', exact=True)).to_be_visible()
    page.wait_for_timeout(350)
    page.screenshot(path=str(OUTPUT / 'material-preview.png'))
    page.keyboard.press('Escape')
    expect(page.get_by_role('dialog')).not_to_be_visible()
    expect(page.get_by_text('运营意见必须保留', exact=True)).to_be_visible()
    expect(page.get_by_text('学员意见也必须保留', exact=True)).to_be_visible()
    page.locator('.student-overview-main > .ant-space .ant-collapse-header').first.click()
    expect(page.get_by_text('历史版本专属名称', exact=True)).to_be_visible()
    expect(page.get_by_text('历史版本 · 只读', exact=True)).to_be_visible()
    history = page.locator('.ant-collapse-panel')
    expect(history.locator('.positioning-feedback .positioning-evidence').get_by_text('历史版本确认凭证.md', exact=True)).to_be_visible()
    history.locator('.positioning-material-card').first.get_by_role('button', name='预览参考内容', exact=False).click()
    expect(page.get_by_role('dialog').get_by_text('历史版本的素材内容', exact=True)).to_be_visible()
    page.keyboard.press('Escape')
    expect(page.get_by_role('dialog')).not_to_be_visible()
    expect(page.locator('.student-overview-flow').get_by_text('第 3 次提交', exact=True)).to_be_visible()
    assert page.locator('.ant-collapse-panel').get_by_role('button', name='复核通过', exact=True).count() == 0
    page.get_by_role('button', name='复核通过', exact=True).click()
    expect(page.get_by_text('正在处理第 3 次提交。', exact=False)).to_be_visible()
    page.get_by_role('button', name='确认通过', exact=True).click()
    expect(page.get_by_text('确认复核通过此定位卡？', exact=True).first).not_to_be_visible()
    writes = page.evaluate('overviewFixture.writes')
    assert len(writes) == 1 and '/101/' in writes[0]['url'], writes
    page.get_by_role('button', name='完整资料', exact=True).click()
    expect(page.get_by_text('完整学员资料', exact=True)).to_be_visible()
    page.keyboard.press('Escape')
    expect(page.get_by_text('完整学员资料', exact=True)).not_to_be_visible()
    # Make sure all profile content can be read despite the original page's scrolling container.
    page.get_by_text('示例院校', exact=True).scroll_into_view_if_needed()
    expect(page.get_by_text('示例院校', exact=True)).to_be_visible()
    load()
    page.screenshot(path=str(OUTPUT / 'desktop.png'))
    for width in (1280, 960, 768, 390, 360):
        page.set_viewport_size({'width': width, 'height': 900})
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
        if width <= 960:
            main = page.locator('.student-overview-main').bounding_box()
            aside = page.locator('.student-overview-aside').bounding_box()
            assert aside['y'] < main['y'], width
    page.set_viewport_size({'width': 390, 'height': 900})
    page.screenshot(path=str(OUTPUT / 'mobile.png'))
    mobile_stages = page.locator('.positioning-plan-stage .positioning-plan-item')
    for i in range(1, 7):
        assert mobile_stages.nth(i).bounding_box()['y'] > mobile_stages.nth(i - 1).bounding_box()['y']
    mobile_stages.nth(1).scroll_into_view_if_needed()
    page.screenshot(path=str(OUTPUT / 'mobile-stage.png'))
    page.get_by_role('button', name='完整资料', exact=True).click()
    assert page.locator('.ant-drawer-section').bounding_box()['width'] <= 390
    page.keyboard.press('Escape')
    expect(page.get_by_text('完整学员资料', exact=True)).not_to_be_visible()
    page.get_by_role('button', name='预览图片：确认图片.png', exact=True).click()
    dialog = page.get_by_role('dialog')
    expect(dialog).to_be_visible()
    # Force a genuine failing image request; reusing an already-decoded URL can hit memory cache.
    image_state['fail'] = True
    dialog.get_by_role('img', name='确认图片.png', exact=True).evaluate("image => image.src='/test/overview-image.png?fail=1'")
    expect(dialog.get_by_text('图片加载失败，请重试附件', exact=True)).to_be_visible()
    before_retry = len(page.evaluate('overviewFixture.calls'))
    image_state['fail'] = False
    dialog.get_by_role('button', name='重试附件', exact=True).click()
    expect(dialog.get_by_text('图片加载失败，请重试附件', exact=True)).not_to_be_visible()
    preview_image = dialog.get_by_role('img', name='确认图片.png', exact=True)
    expect(preview_image).to_be_visible()
    page.wait_for_function('()=>{const i=document.querySelector(".ant-modal img");return i && i.complete && i.naturalWidth > 0}')
    assert len(page.evaluate('overviewFixture.calls')) > before_retry
    assert preview_image.bounding_box()['width'] <= 390
    page.wait_for_timeout(350)
    page.screenshot(path=str(OUTPUT / 'mobile-image-preview.png'))
    page.keyboard.press('Escape')
    expect(dialog).not_to_be_visible()
    page.set_viewport_size({'width': 1600, 'height': 1000})
    load('?mode=material-error')
    first_material = page.locator('.positioning-material-card').first
    expect(first_material.get_by_text('参考素材读取失败', exact=True)).to_be_visible()
    page.evaluate("overviewFixture.mode='success'")
    first_material.get_by_role('button', name=re.compile(r'^重\s*试$')).click()
    expect(first_material.locator('.positioning-material-cover img')).to_be_visible()
    first_material.locator('.positioning-material-cover img').dispatch_event('error')
    expect(first_material.get_by_text('封面加载失败', exact=True)).to_be_visible()
    first_material.get_by_role('button', name='重试封面', exact=False).click()
    expect(first_material.locator('.positioning-material-cover img')).to_be_visible()
    for mode, text in [('unopened', '未开通'), ('partner-error', '兼职状态读取失败'), ('unsubmitted', '尚未提交学员信息'), ('profile-error', '学员资料读取失败'), ('profile-denied', '无权读取此学员资料'), ('no-actions', '当前暂无可执行操作')]:
        load('?mode=' + mode)
        expect(page.get_by_text(text, exact=True)).to_be_visible()
        if mode == 'partner-error':
            page.evaluate("overviewFixture.mode='success'")
            page.get_by_role('button', name='重试加载兼职状态', exact=True).click()
            expect(page.get_by_text('兼职账号已开通', exact=True)).to_be_visible()
        if mode == 'profile-error':
            page.evaluate("overviewFixture.mode='success';overviewFixture.delay=800")
            page.locator('.student-overview-background').get_by_role('button', name=re.compile(r'重\s*试')).click()
            expect(page.locator('.student-overview-background .ant-skeleton')).to_be_visible()
            expect(page.get_by_text('示例单位', exact=True)).to_be_visible()
        if mode == 'no-actions':
            expect(page.get_by_role('button', name='复核通过', exact=True)).to_have_count(0)
    for query in ('?no-profile-permission', '?mode=context-denied'):
        load(query)
        expect(page.get_by_text('暂无学员资料查看权限', exact=True)).to_be_visible()
        assert not any('/student-info-form/' in url for url in page.evaluate('overviewFixture.calls'))
    assert not errors, errors
    browser.close()
    print('PASS: production page, 9:3/mobile, complete fields/references/files/history, current command target, profile/partner states, permissions and retry; no page errors.')
