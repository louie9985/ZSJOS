# -*- coding: utf-8 -*-
"""Class display in real Chrome with isolated fixture transport; no business writes."""
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('CLASS_CARD_BASE', 'http://127.0.0.1:5237/test/delivery-class-groups.html')
OUT = Path(gettempdir()) / 'zsjos-class-card-display'
OUT.mkdir(exist_ok=True)


def card_named(page, name):
    return page.locator('.delivery-class-card').filter(has=page.get_by_role('heading', name=name, exact=True))


def group_named(page, name):
    return page.locator('.delivery-group-block').filter(has=page.locator('.delivery-group-card').get_by_role('heading', name=re.compile(name)))


def assert_no_overflow(page):
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), 'Page overflows horizontally'
    bad = page.locator('.delivery-class-card-head').evaluate_all('''heads => heads.flatMap(head => {
      const box = head.getBoundingClientRect();
      return [...head.children].filter(el => {
        const r = el.getBoundingClientRect();
        return r.left < box.left - 1 || r.right > box.right + 1;
      }).map(el => el.textContent);
    })''')
    assert not bad, f'Card heading content overflows: {bad}'


def assert_title(card, name):
    title = card.get_by_role('heading', name=name, exact=True)
    expect(title).to_be_visible()
    assert title.inner_text() == name
    geometry = title.evaluate('''el => ({
      scrollWidth: el.scrollWidth, clientWidth: el.clientWidth,
      scrollHeight: el.scrollHeight, clientHeight: el.clientHeight,
      clamp: getComputedStyle(el).webkitLineClamp,
    })''')
    assert geometry['scrollWidth'] <= geometry['clientWidth'] + 1, geometry
    assert geometry['scrollHeight'] <= geometry['clientHeight'] + 1, geometry
    assert geometry['clamp'] in ('none', '0'), geometry
    teacher = card.get_by_text(re.compile('班主任[：:]'))
    expect(teacher).to_be_visible()
    assert title.bounding_box()['y'] + title.bounding_box()['height'] <= teacher.bounding_box()['y'] + 1


def assert_system_card(card, class_no):
    expect(card).to_have_class(re.compile(r'\bdelivery-class-system\b'))
    expect(card.get_by_text('系统班级', exact=True)).to_be_visible()
    expect(card.get_by_text('考期', exact=True)).to_have_count(0)
    expect(card.get_by_text('项目未记录', exact=True)).to_have_count(0)
    expect(card.get_by_text('系统班项目快照', exact=True)).to_have_count(0)
    expect(card.get_by_text(class_no, exact=True)).to_have_count(0)
    expect(card.get_by_role('button', name='查看学员', exact=True)).to_be_visible()
    for action in ('编辑', '调整归组', '结课'):
        expect(card.get_by_role('button', name=action, exact=True)).to_have_count(0)


with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto(BASE + '?state=display')
        expect(page.get_by_role('heading', name='班级管理', exact=True)).to_be_visible()
        page.get_by_role('button', name='测试卡片布局', exact=True).click()
        names = page.evaluate('Object.fromEntries(classGroupsFixture.rows.map(r => [r.id, r.className]))')

        pending = card_named(page, '待分班')
        assert_system_card(pending, 'PENDING')
        expect(pending.get_by_text('193 条服务', exact=True)).to_be_visible()
        expect(pending.get_by_text(re.compile('班主任'))).to_have_count(0)
        independent = card_named(page, names['200'])
        assert_title(independent, names['200'])
        expect(independent.get_by_text('BJ200', exact=True)).to_be_visible()
        expect(independent.get_by_text('项目未记录', exact=True)).to_have_count(0)
        no_date = card_named(page, names['201'])
        expect(no_date).not_to_have_class(re.compile(r'\bdelivery-class-system\b'))
        expect(no_date.get_by_text('未设置', exact=True)).to_be_visible()
        expect(card_named(page, names['202']).get_by_text('职业考证-营养健康', exact=True)).to_be_visible()
        assert pending.bounding_box()['height'] < independent.bounding_box()['height']

        for name, ids in (('未确定考期', (300, 301)), ('异常结业', (400, 401))):
            group = group_named(page, name)
            root_card = group.locator('.delivery-group-card')
            expect(root_card).to_have_class(re.compile(r'\bdelivery-class-system\b'))
            expect(root_card.get_by_text('系统分组', exact=True)).to_be_visible()
            expect(root_card.get_by_text(re.compile(r'^考期[：:]'))).to_have_count(0)
            expect(root_card.get_by_text('项目未记录', exact=True)).to_have_count(0)
            root_card.get_by_role('button', name='展开', exact=True).click()
            expect(group.locator('.delivery-class-card')).to_have_count(2)
            for index, class_id in enumerate(ids):
                child = group.locator('.delivery-class-card').nth(index)
                assert_system_card(child, f'BJ{class_id}')
                expect(child.get_by_text(re.compile('班主任[：:]'))).to_be_visible()

        ordinary_group = group_named(page, '批量测试')
        ordinary_group.locator('.delivery-group-card').get_by_role('button', name='展开', exact=True).click()
        grouped = card_named(page, names['100'])
        assert_title(grouped, names['100'])
        assert_no_overflow(page)
        page.screenshot(path=str(OUT / f'cards-{width}.png'), full_page=True)

        # Buttons retain their actions and do not trigger the card's student navigation.
        independent.get_by_role('button', name='编辑', exact=True).click()
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_label('班级名称', exact=True)).to_have_value(names['200'])
        dialog.get_by_role('button', name=re.compile(r'取\s*消')).click()
        expect(dialog).not_to_be_visible()
        independent.get_by_role('button', name='调整归组', exact=True).click()
        expect(dialog).to_be_visible()
        dialog.get_by_role('button', name=re.compile(r'取\s*消')).click()
        expect(dialog).not_to_be_visible()
        pending.get_by_role('button', name='查看学员', exact=True).click()
        expect(page.get_by_role('button', name='返回班级列表', exact=True)).to_be_visible()
        assert page.evaluate('history.state.usr.classId') == 1
        page.get_by_role('button', name='返回班级列表', exact=True).click()
        expect(card_named(page, names['200'])).to_be_visible()
        card_named(page, names['200']).get_by_role('heading', name=names['200'], exact=True).click()
        expect(page.get_by_role('button', name='返回班级列表', exact=True)).to_be_visible()
        assert page.evaluate('history.state.usr.classId') == 200
        page.get_by_role('button', name='返回班级列表', exact=True).click()

        if width == 1440:
            page.get_by_role('button', name='测试表格布局', exact=True).click()
            table = page.locator('[data-table-key="delivery-class-management"]')
            expect(table).to_be_visible()
            system_rows = table.locator('tr.delivery-class-system-row')
            expect(system_rows).to_have_count(7)
            for row in system_rows.all():
                cells = row.locator('td')
                for index in (1, 3, 5):
                    assert cells.nth(index).inner_text().strip() in ('', '—'), cells.nth(index).inner_text()
                expect(row.get_by_text('项目未记录', exact=True)).to_have_count(0)
            normal_row = table.locator('tr.ant-table-row').filter(has=page.get_by_text('BJ200', exact=True))
            expect(normal_row.get_by_text(names['200'], exact=True)).to_be_visible()
            expect(normal_row.get_by_role('button', name='编辑', exact=True)).to_be_visible()
            expect(normal_row.get_by_role('button', name='结课', exact=True)).to_be_visible()
            normal_row.get_by_role('button', name='编辑', exact=True).click()
            expect(dialog.get_by_label('班级名称', exact=True)).to_have_value(names['200'])
            dialog.get_by_role('button', name=re.compile(r'取\s*消')).click()
            expect(dialog).not_to_be_visible()
            assert_no_overflow(page)
            page.screenshot(path=str(OUT / 'table-1440.png'), full_page=True)

        assert not errors, errors
        assert page.evaluate("classGroupsFixture.calls.every(c => !['put', 'post', 'delete'].includes(c.method))")
        print(f'PASS class display, complete names, system simplification and retained actions at {width}', flush=True)
        page.close()
    browser.close()
print(f'Screenshots: {OUT}', flush=True)
