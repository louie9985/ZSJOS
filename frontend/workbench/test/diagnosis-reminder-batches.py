# -*- coding: utf-8 -*-
"""Exercise the real reminder modal against the 500-ID acknowledgement contract."""
from playwright.sync_api import sync_playwright, expect


def run_case(browser, count, fail_once=False):
    page = browser.new_page()
    url = 'http://127.0.0.1:5174/test/account-review-delivery.html?reminders'
    setup = """
      await import('/src/components/DiagnosisReminder.tsx');
      const service = performance.getEntriesByType('resource').find(entry =>
        new URL(entry.name).pathname === '/src/services/mediaAccountProfile.ts');
      const { diagnosisApi } = await import(service.name);
      window.batches = [];
      const count = Number(new URL(location.href).searchParams.get('count'));
      let fail = new URL(location.href).searchParams.has('fail');
      diagnosisApi.reminders = async () => Array.from({length: count}, (_, i) => ({
        taskId: i + 1, accountId: 1, title: '诊断', dueAt: 1, payload: {}
      }));
      diagnosisApi.acknowledge = async ids => {
        window.batches.push(ids);
        if (ids.length > 500) throw Error('Too many IDs');
        await new Promise(resolve => setTimeout(resolve, 150));
        if (fail && ids[0] === 501) { fail = false; throw Error('测试失败'); }
        return true;
      };
      await import('/test/account-review-delivery.tsx');
    """

    def serve_fixture(route):
        response = route.fetch()
        html = response.text().replace('src="/test/account-review-delivery.tsx">',
                                       '>' + setup)
        route.fulfill(response=response, body=html)

    page.route('**/test/account-review-delivery.html?*', serve_fixture)
    page.goto(f'{url}&count={count}' + ('&fail' if fail_once else ''))
    modal = page.get_by_role('dialog')
    expect(modal).to_be_visible()
    if fail_once:
        expect(modal.get_by_text('测试失败')).to_be_visible()
    modal.get_by_role('button', name='稍后处理').click()
    expect(modal).to_be_hidden()
    ids = list(range(1, count + 1))
    batches = [ids[i:i + 500] for i in range(0, count, 500)]
    if fail_once:
        batches.insert(1, batches[1])
    assert page.evaluate('window.batches') == batches
    page.close()


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='chrome')
    for size in (500, 501, 1001):
        run_case(browser, size, fail_once=size == 1001)
    browser.close()
