# UTF-8. Production page with synthetic HTTP responses; no database writes.
from pathlib import Path
import re
from playwright.sync_api import sync_playwright, expect

OUT = Path(__file__).resolve().parents[1] / 'node_modules/.cache/content-review-detail'
OUT.mkdir(parents=True, exist_ok=True)
URL = 'http://127.0.0.1:5174/test/content-review-detail.html'

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1600, 'height': 1000})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))

    def image(route):
        portrait = 'landscape' not in route.request.url
        w, h = (300, 480) if portrait else (640, 320)
        route.fulfill(content_type='image/svg+xml', body=f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}"><rect width="100%" height="100%" fill="#c4d8cf"/><circle cx="{w/2}" cy="{h/2}" r="80" fill="#f3e9d1"/><text x="20" y="40" font-size="24">参考示例</text></svg>')

    page.route('**/test/review-assets/*.svg', image)
    page.route('**/test/review-assets/*.pdf', lambda r: r.fulfill(content_type='application/pdf', body=b'%PDF-1.4\n%%EOF'))
    page.route('**/test/review-assets/*.docx', lambda r: r.fulfill(content_type='application/octet-stream', body=b'fixture'))
    page.goto(URL)
    pane = page.locator('.content-review-detail-pane')
    expect(pane.get_by_text('一份健康早餐的搭配方法', exact=True).first).to_be_visible()
    expect(pane.locator('.content-review-heading .content-review-account-link')).to_have_count(0)
    expect(pane.locator('details')).to_have_attribute('open', '')
    expect(pane.locator('.content-review-account-profile dd > .ant-tag')).to_have_count(9)
    expect(pane.locator('.content-review-summary-fields .ant-tag').first).to_have_text('2026-09-27 09:30')
    expect(pane.locator('.content-review-resource-links > div')).to_have_count(3)
    expect(pane.locator('.content-review-resource-links')).to_contain_text('作品详情链接')
    expect(pane.locator('.content-review-resource-links .ant-typography-secondary')).to_have_count(3)
    expect(pane.locator('.attachment-card')).to_have_count(6)
    history = pane.get_by_role('button', name='查看历史轮次')
    rail = pane.locator('.content-review-process-column')
    assert abs(history.bounding_box()['width'] - rail.bounding_box()['width']) < 2
    assert history.bounding_box()['y'] < rail.locator('.ant-collapse').bounding_box()['y']
    assets = pane.locator('.content-review-assets > section')
    assert abs(assets.nth(0).bounding_box()['y'] - assets.nth(1).bounding_box()['y']) < 2
    assert pane.locator('.content-review-reference img').evaluate_all('(els) => els.every(e => getComputedStyle(e).objectFit === "contain")')
    page.screenshot(path=str(OUT / 'desktop.png'))
    assets.first.scroll_into_view_if_needed()
    page.screenshot(path=str(OUT / 'attachments.png'))

    # Failed signed URL refresh recovers through the protected batch get.
    card = pane.locator('.attachment-card').first
    expect(card.get_by_role('button', name=re.compile(r'预览$'))).to_be_enabled()
    page.evaluate('window.reviewDetailFixture.attachmentError = true')
    card.get_by_role('button', name=re.compile(r'预览$')).click()
    expect(card.get_by_text('附件地址刷新失败（模拟）')).to_be_visible()
    page.evaluate('window.reviewDetailFixture.attachmentError = false')
    card.get_by_role('button', name='重试附件', exact=True).click()
    expect(card.get_by_role('button', name=re.compile(r'预览$'))).to_be_enabled()
    card.get_by_role('button', name=re.compile(r'预览$')).click()
    expect(page.get_by_role('dialog')).to_be_visible()
    expect(page.get_by_role('dialog').locator('img')).to_be_visible()
    page.get_by_role('dialog').get_by_role('button', name=re.compile(r'关\s*闭$')).click()

    # Other supported preview types and document download use the same card contract.
    video_card = pane.locator('.attachment-card').filter(has_text='视频.mp4')
    video_card.get_by_role('button', name=re.compile(r'预览$')).click()
    expect(page.get_by_role('dialog').locator('video')).to_have_attribute('src', re.compile(r'video.mp4$'))
    page.get_by_role('dialog').get_by_role('button', name=re.compile(r'关\s*闭$')).click()
    pdf_card = pane.locator('.attachment-card').filter(has_text='审核.pdf')
    pdf_card.get_by_role('button', name=re.compile(r'预览$')).click()
    expect(page.get_by_role('dialog').locator('iframe')).to_have_attribute('src', re.compile(r'review.pdf$'))
    page.get_by_role('dialog').get_by_role('button', name=re.compile(r'关\s*闭$')).click()
    document_card = pane.locator('.attachment-card').filter(has_text='说明.docx')
    expect(document_card.get_by_role('button', name=re.compile(r'预览$'))).to_have_count(0)
    with page.expect_download() as download:
        document_card.get_by_role('button', name=re.compile(r'下载$')).click()
    assert download.value.suggested_filename == '说明.docx'

    # History error and empty states remain distinct, then retry the real page flow.
    page.evaluate('window.reviewDetailFixture.historyError = true')
    history.click()
    expect(page.get_by_role('dialog').get_by_text('历史轮次加载失败（模拟）')).to_be_visible()
    page.evaluate('window.reviewDetailFixture.historyError = false; window.reviewDetailFixture.historyEmpty = true')
    page.get_by_role('dialog').get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.get_by_text('暂无历史轮次', exact=True)).to_be_visible()
    page.get_by_role('dialog').get_by_role('button', name='Close', exact=True).click()
    page.evaluate('window.reviewDetailFixture.historyEmpty = false')
    opinion = pane.locator('.content-review-decision textarea')
    opinion.fill('尚未保存的意见')
    history.click()
    page.get_by_role('dialog').get_by_role('button', name='查看详情').first.click()
    expect(page.locator('.ant-modal-confirm-title').filter(has_text='有尚未保存的审核意见')).to_be_visible()
    page.get_by_role('button', name='继续审核', exact=True).click()
    expect(opinion).to_have_value('尚未保存的意见')
    page.get_by_role('dialog').get_by_role('button', name='查看详情').first.click()
    page.get_by_role('button', name='放弃并切换', exact=True).click()
    expect(pane.get_by_text('历史轮次 · 只读', exact=True)).to_be_visible()
    expect(pane.locator('.content-review-decision')).to_have_count(0)
    expect(pane.get_by_text('历史选题', exact=True)).to_be_visible()
    expect(pane.locator('.content-review-resource-links a')).to_have_count(4)
    expect(pane.locator('.content-review-resource-links .resource-link-card')).to_have_count(4)
    expect(pane.get_by_role('button', name='复制链接')).to_have_count(4)
    expect(pane.locator('.content-review-process-column').get_by_text('审批流程', exact=True)).to_be_visible()
    pane.get_by_role('button', name='返回原批次').click()
    expect(pane.locator('.content-review-decision')).to_have_count(1)

    # If access is revoked between listing and opening, keep a way back to the source batch.
    history.click()
    page.evaluate('window.reviewDetailFixture.detailDenied = true')
    page.get_by_role('dialog').get_by_role('button', name='查看详情').first.click()
    expect(pane.get_by_text('无权访问内容审核', exact=True)).to_be_visible()
    page.evaluate('window.reviewDetailFixture.detailDenied = false')
    pane.get_by_role('button', name='返回原批次').click()
    expect(pane.locator('.content-review-decision')).to_have_count(1)

    # Actual available pane width drives responsive placement.
    for width in [1024, 390]:
        page.set_viewport_size({'width': width, 'height': 900})
        if width == 390:
            page.locator('.content-review-list-item').first.click()
        expect(pane).to_be_visible()
        pane.evaluate('(e) => e.scrollTop = 0')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
        assert pane.evaluate('(e) => e.scrollWidth <= e.clientWidth + 1')
        page.screenshot(path=str(OUT / f'width-{width}.png'))
        if width == 390:
            assert assets.nth(1).bounding_box()['y'] > assets.nth(0).bounding_box()['y']
            assets.first.scroll_into_view_if_needed()
            page.screenshot(path=str(OUT / 'mobile-assets.png'))
    assert not errors, errors
    browser.close()
print('PASS: desktop/mobile layout, tags, mapping, protected attachment retry, history error/empty/dirty/read-only/return; synthetic transport.')
