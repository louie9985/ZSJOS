# -*- coding: utf-8 -*-
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(os.environ['TEMP']) / 'material-value-tags'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto('http://localhost:5174/test/material-value-tags.html')
        stages = page.locator('.viral-field-readonly').filter(has_text='适配账号期段')
        expect(stages.locator('.ant-tag')).to_have_text(['S2 冷启动', 'S3 内容验证', 'S4 咨询验证', 'S5 客资验证', 'S6 稳定增长'])
        colors = stages.locator('.ant-tag').evaluate_all('(tags) => tags.map(tag => getComputedStyle(tag).backgroundColor)')
        assert len(set(colors)) == 5, colors
        expect(page.locator('.viral-field-readonly').filter(has_text='账号ID').locator('.ant-tag')).to_have_text('27247641852')
        expect(page.locator('.viral-field-readonly').filter(has_text='爆款类型').locator('.ant-tag')).to_have_count(3)
        expect(page.locator('.viral-field-readonly').filter(has_text='适配业务定位').locator('.ant-tag')).to_have_count(2)
        expect(page.get_by_text('历史标签未记录', exact=True)).to_be_visible()
        expect(page.get_by_text('当前名称不可替代历史', exact=True)).to_have_count(0)
        expect(page.locator('.viral-field-readonly').filter(has_text='空选项').get_by_text('未填写')).to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        stages.scroll_into_view_if_needed()
        page.screenshot(path=str(out / f'tags-{width}.png'))
    assert not errors, errors
    browser.close()
print(f'PASS: individual colored snapshot tags, account ID, punctuation, missing/empty and wrapping at desktop/mobile; {out}')
