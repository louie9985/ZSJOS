# -*- coding: utf-8 -*-
"""Real review component + Chrome, synthetic HTTP, desktop/mobile history states."""
import json
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

ORIGIN = os.environ.get('ZSJOS_REVIEW_HISTORY_ORIGIN', 'http://127.0.0.1:5174')
URL = ORIGIN + '/test/account-review-history.html'
OUTPUT = Path(gettempdir()) / 'zsjos-account-review-history'
OUTPUT.mkdir(exist_ok=True)


def requests(page):
    return json.loads(page.locator('html').get_attribute('data-history-requests') or '[]')


def close_dialog(page):
    page.get_by_role('dialog').locator('.ant-modal-close').click()
    expect(page.get_by_role('dialog')).to_have_count(0)


def field(page, entry, label):
    return entry.locator('.account-review-history-field').filter(has=page.get_by_text(label, exact=True))


def assert_option_background(tag):
    expect(tag).to_have_count(1)
    assert tag.evaluate('''el => {
        const fill = getComputedStyle(el).backgroundColor;
        return fill !== 'transparent' && fill !== 'rgba(0, 0, 0, 0)' && !fill.endsWith('/ 0)');
    }'''), 'Option tag must have a visible background.'


with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page_errors = []

    def new_page(query='', width=1440, height=1000):
        page = browser.new_page(viewport={'width': width, 'height': height})
        page.set_default_timeout(30000)
        page.on('pageerror', lambda error: page_errors.append(str(error)))
        # A local synthetic image response avoids external media or real attachments.
        page.route('**/history-test-image.png', lambda route: route.fulfill(
            content_type='image/svg+xml', body='<svg xmlns="http://www.w3.org/2000/svg" width="180" height="100"><rect width="180" height="100" fill="#dddddd"/><text x="20" y="55">Fixture evidence</text></svg>'))
        page.goto(URL + query, wait_until='domcontentloaded', timeout=60000)
        expect(page.get_by_test_id('summary-card')).to_be_visible()
        return page

    for width, height in [(1440, 1000), (390, 844)]:
        page = new_page(width=width, height=height)
        assert not requests(page), 'History must load only after user opens it.'
        card = page.get_by_test_id('summary-card')
        expect(card.locator('article')).to_have_count(0)
        expect(card.get_by_text('提交时指标快照', exact=True)).to_have_count(0)
        expect(card.get_by_role('button', name='填写诊断', exact=True)).to_be_visible()
        before = page.get_by_test_id('following-content').bounding_box()['y']
        page.get_by_role('button', name=re.compile('查看本项历史')).click()
        dialog = page.get_by_role('dialog')
        expect(dialog).to_be_visible()
        expect(dialog.get_by_text('7天账号数据诊断 · 历史记录', exact=True)).to_be_visible()
        expect(dialog.get_by_text('流程时间线', exact=True)).to_be_visible()
        expect(dialog.locator('.account-review-history-entry')).to_have_count(10)
        expect(dialog.get_by_text('第11条诊断结论', exact=True)).to_be_visible()
        expect(dialog.get_by_text('正常（保存时标签）', exact=True).first).to_be_visible()
        assert dialog.locator('.account-review-history-event').filter(has_text='提交诊断').count() >= 1
        assert dialog.locator('.account-review-history-event').filter(has_text='修订诊断').count() >= 1
        assert 'unrendered-current-stage-code' not in dialog.inner_text()
        assert '2026-10-22 09:00' in dialog.inner_text()
        assert '北京时间' in dialog.inner_text()
        assert '测试编导姓名快照' in dialog.inner_text()
        assert '0' in dialog.inner_text()
        assert requests(page)[-1] == {'accountId': 900001, 'fieldKey': 'diagnosis_7d', 'pageNo': 1, 'pageSize': 10}
        assert abs(page.get_by_test_id('following-content').bounding_box()['y'] - before) < 1
        assert page.get_by_test_id('summary-card').locator('.account-review-history-entry').count() == 0
        page.wait_for_timeout(350)
        assert dialog.bounding_box()['width'] <= width - 16
        assert dialog.locator('.ant-modal-body').evaluate('(el) => el.scrollWidth <= el.clientWidth + 1'), 'Modal body horizontal overflow'
        assert dialog.locator('.account-review-history-entry').first.evaluate('(el) => el.scrollWidth <= el.clientWidth + 1'), 'Record horizontal overflow'
        page.screenshot(path=str(OUTPUT / f'history-{width}-top.png'))
        first = dialog.locator('.account-review-history-entry').first
        for label in ['当前阶段', '账号状态', '学员配合等级', '是否重新定位', '主要瓶颈', '次要瓶颈', '是否需要换内容方向测试', '流量是否正常']:
            assert_option_background(field(page, first, label).locator('.account-review-history-option'))
        assert field(page, first, '一句话诊断结论').locator('.account-review-history-option').count() == 0
        zero = field(page, first, '7天内最高点赞')
        expect(zero.locator('dd')).to_have_text('0')
        assert zero.locator('.account-review-history-option').count() == 0
        missing = field(page, dialog.locator('.account-review-history-entry').nth(1), '学员配合等级')
        expect(missing.locator('dd')).to_have_text('历史未留存')
        assert missing.locator('.account-review-history-option').count() == 0
        assert first.get_by_text('诊断证据附件.png', exact=True).count() >= 1
        assert first.get_by_role('button', name=re.compile('下载')).count() >= 1
        assert first.get_by_role('button', name=re.compile('预览')).count() >= 1
        if width == 1440:
            dialog.locator('.account-review-history-event').nth(1).scroll_into_view_if_needed()
            page.screenshot(path=str(OUTPUT / 'history-1440-next-event.png'))
        dialog.locator('.ant-pagination-item-2').click()
        expect(dialog.locator('.account-review-history-entry')).to_have_count(1)
        expect(dialog.get_by_text('第1条诊断结论', exact=True)).to_be_visible()
        assert requests(page)[-1]['pageNo'] == 2
        page.screenshot(path=str(OUTPUT / f'history-{width}-page2.png'))
        close_dialog(page)
        page.get_by_role('button', name=re.compile('查看本项历史')).click()
        expect(page.get_by_role('dialog').locator('.account-review-history-entry')).to_have_count(10)
        assert requests(page)[-1]['pageNo'] == 1, 'Reopen resets to the first page.'
        close_dialog(page)
        page.get_by_role('button', name=re.compile('基于最新记录修订')).click()
        assert page.locator('html').get_attribute('data-revise-clicked') == 'true'
        page.close()

    for width, height in [(1440, 1000), (390, 844)]:
        page = new_page('?long-options', width=width, height=height)
        page.get_by_role('button', name=re.compile('查看本项历史')).click()
        dialog = page.get_by_role('dialog')
        expect(dialog.locator('.account-review-history-entry')).to_have_count(10)
        tag = field(page, dialog.locator('.account-review-history-entry').first, '当前阶段').locator('.account-review-history-option')
        assert_option_background(tag)
        assert tag.inner_text() == 'S2 历史阶段标签-' + 'LongSavedOptionLabel' * 10
        assert tag.evaluate('(el) => el.scrollWidth <= el.clientWidth + 1')
        assert tag.evaluate('(el) => el.getBoundingClientRect().height > parseFloat(getComputedStyle(el).lineHeight) * 2'), 'Long option must wrap across multiple lines.'
        assert dialog.locator('.ant-modal-body').evaluate('(el) => el.scrollWidth <= el.clientWidth + 1')
        tag.scroll_into_view_if_needed()
        page.wait_for_timeout(350)
        page.screenshot(path=str(OUTPUT / f'history-{width}-long-options.png'))
        page.close()

    page = new_page('?error')
    page.get_by_role('button', name=re.compile('查看本项历史')).click()
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_text('历史记录加载失败，请重试', exact=True)).to_be_visible()
    dialog.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(dialog.locator('.account-review-history-entry')).to_have_count(10)
    assert len(requests(page)) == 2
    page.close()

    page = new_page('?empty')
    page.get_by_role('button', name=re.compile('查看本项历史')).click()
    expect(page.get_by_role('dialog').get_by_text('暂无本项历史记录', exact=True)).to_be_visible()
    assert page.get_by_role('dialog').locator('.account-review-history-entry').count() == 0
    page.close()

    page = new_page('?denied')
    assert page.get_by_role('button', name=re.compile('查看本项历史')).count() == 0
    assert not requests(page)
    page.close()

    for switch, expected_field, expected_account in [
        ('切换账号', 'diagnosis_7d', 900002),
        ('切换项目', 'diagnosis_14d', 900001),
        ('撤销历史可见权限', None, None),
    ]:
        page = new_page('?slow')
        page.get_by_role('button', name=re.compile('查看本项历史')).click()
        expect(page.get_by_role('dialog').locator('.ant-spin')).to_be_visible()
        # Simulate a parent-controlled account/permission change while the modal is open.
        page.get_by_role('button', name=switch, exact=True).evaluate('(el) => el.click()')
        expect(page.get_by_role('dialog')).to_have_count(0)
        page.wait_for_timeout(1600)
        assert page.locator('.account-review-history-entry').count() == 0
        if expected_field:
            page.get_by_role('button', name=re.compile('查看本项历史')).click()
            expect(page.get_by_role('dialog').locator('.account-review-history-entry')).to_have_count(10)
            assert requests(page)[-1]['fieldKey'] == expected_field
            assert requests(page)[-1]['accountId'] == expected_account
            if expected_account == 900002:
                assert '第11条诊断结论' not in page.get_by_role('dialog').inner_text()
                assert '另一账号的历史结论' in page.get_by_role('dialog').inner_text()
        else:
            assert page.get_by_role('button', name=re.compile('查看本项历史')).count() == 0
        page.close()

    for width, height in [(1440, 1000), (390, 844)]:
        page = new_page('?weekly', width=width, height=height)
        for days, switch in [(7, None), (14, '切换项目'), (28, '切换28天项目')]:
            if switch:
                page.get_by_role('button', name=switch, exact=True).click()
            page.get_by_role('button', name=re.compile('查看本项历史')).click()
            dialog = page.get_by_role('dialog')
            first = dialog.locator('.account-review-history-entry').first
            expect(first.locator('.account-review-history-entry-heading').get_by_text(f'{days}天诊断', exact=True)).to_be_visible()
            expect(first.get_by_text('提交时指标快照', exact=True)).to_be_visible()
            expect(first.get_by_text('周期结算与当前成交进展', exact=True)).to_be_visible()
            expect(dialog.get_by_role('button', name='刷新当前指标', exact=True)).to_be_visible()
            expect(first.get_by_text('正在加载当前指标…', exact=True)).to_have_count(0)
            for other in [7, 14, 28]:
                expect(first.get_by_text(f'{other}天诊断要求', exact=True)).to_have_count(1 if other == days else 0)
                assert first.locator('h4').filter(has_text=f'{other}天周期统计').count() == (1 if other == days else 0)
            metric_label = f'{days}天发布条数' if days != 14 else '14天有效客资条数'
            rows = field(page, first, metric_label)
            expect(rows).to_have_count(2)
            assert_option_background(rows.first.locator('.account-review-history-option'))
            expect(rows.first.locator('dd')).to_have_text(str(days) if days != 14 else '8')
            if days > 7:
                expect(field(page, first, '当前客资转化率').locator('dd')).to_have_text('25.00%')
            headers = first.locator('h4').filter(has_text=f'{days}天周期统计')
            assert headers.evaluate('(el) => getComputedStyle(el).backgroundColor !== "rgba(0, 0, 0, 0)"')
            first.get_by_text('周期结算与当前成交进展', exact=True).scroll_into_view_if_needed()
            page.wait_for_timeout(350)
            assert dialog.locator('.ant-modal-body').evaluate('(el) => el.scrollWidth <= el.clientWidth + 1')
            page.screenshot(path=str(OUTPUT / f'history-{width}-weekly-{days}.png'))
            close_dialog(page)
        page.close()

    for query in ['?plain', '?malformed']:
        page = new_page(query)
        page.get_by_role('button', name=re.compile('查看本项历史')).click()
        expect(page.get_by_role('dialog').locator('.account-review-history-entry')).to_have_count(10)
        if query == '?plain':
            assert '仅保留原始文字的旧记录' in page.get_by_role('dialog').inner_text()
            first = page.get_by_role('dialog').locator('.account-review-history-entry').first
            for label in ['渠道选择', '优先级选择', '确认选择']:
                assert_option_background(field(page, first, label).locator('.account-review-history-option'))
            for label in ['记录说明', '未留存选项', '记录数量']:
                assert field(page, first, label).locator('.account-review-history-option').count() == 0
            expect(field(page, first, '未留存选项').locator('dd')).to_have_text('历史未留存')
            expect(field(page, first, '记录数量').locator('dd')).to_have_text('0')
        else:
            assert '{broken-json' not in page.get_by_role('dialog').inner_text()
            assert '无法' in page.get_by_role('dialog').inner_text()
        page.close()

    # Check the real account panel passes the configured field label to its shared history entry.
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.on('pageerror', lambda error: page_errors.append(str(error)))
    page.goto(ORIGIN + '/test/account-review-delivery.html?layout', wait_until='domcontentloaded', timeout=60000)
    history_buttons = page.get_by_role('button', name=re.compile('查看本项历史'))
    expect(history_buttons).to_have_count(3)
    history_buttons.last.click()
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_text('7天账号数据诊断 · 历史记录', exact=True)).to_be_visible()
    expect(dialog.locator('.account-review-history-entry')).to_have_count(1)
    page.wait_for_timeout(350)
    page.screenshot(path=str(OUTPUT / 'history-panel-entry-desktop.png'))
    close_dialog(page)
    expect(history_buttons).to_have_count(3)
    page.close()

    assert not page_errors, page_errors
    browser.close()

print('PASS modal-only history, timeline/snapshot labels, colored option tags/long labels/plain text/missing/zero, pagination/reopen, loading/empty/error/retry/permission, slow-response isolation, attachments, weekly metrics, plain/malformed history, 1440/390 layout', OUTPUT)
