# -*- coding: utf-8 -*-
"""Isolated real-Chrome class grouping flows. Start dedicated Vite on 5224."""
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = 'http://127.0.0.1:5224/test/delivery-class-groups.html'
OUT = Path(os.environ.get('TEMP','.'))/'zsjos-class-groups-browser'
OUT.mkdir(exist_ok=True)

def ready(page, state=''):
    page.goto(BASE+('?state='+state if state else ''))
    expect(page.get_by_role('heading',name='班级管理',exact=True)).to_be_visible()

def card_group(page,name):
    return page.locator('.delivery-group-block').filter(has=page.get_by_role('heading',name=re.compile(name)))

def open_group(page,name):
    group=card_group(page,name)
    group.get_by_role('button',name='展开',exact=True).click()
    return group

def select_option(page,selector,label):
    selector.click()
    page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter(has_text=label).click()

with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width in (1440,390):
        page=browser.new_page(viewport={'width':width,'height':1000})
        errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        ready(page);page.get_by_role('button',name='测试卡片布局').click()
        expect(page.locator('.delivery-group-card')).to_have_count(4)
        group=open_group(page,'批量测试')
        expect(group.locator('.delivery-class-card')).to_have_count(12)
        group.get_by_role('button',name=re.compile('加载更多小班')).click()
        expect(group.locator('.delivery-class-card')).to_have_count(15)
        page.screenshot(path=str(OUT/f'cards-{width}.png'),full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
        group.locator('.delivery-class-card').first.get_by_role('button',name='查看学员',exact=True).click()
        page.get_by_role('button',name='返回班级列表',exact=True).click()
        group=card_group(page,'批量测试')
        expect(group.locator('.delivery-class-card')).to_have_count(15)
        if width==1440:
            page.get_by_role('button',name='测试表格布局').click()
            table=page.locator('[data-table-key="delivery-class-management"]')
            expect(table.locator('.delivery-group-child-row')).to_have_count(13)
            table.get_by_role('button',name=re.compile('加载更多小班')).click()
            expect(table.locator('.delivery-group-child-row')).to_have_count(16)
            expect(table.get_by_text('BJ114',exact=True)).to_be_visible()
            page.evaluate('window.scrollTo(0,0)')
            page.screenshot(path=str(OUT/'table-1440.png'),full_page=True)
            page.get_by_role('button',name='测试卡片布局').click()
            expect(card_group(page,'批量测试').locator('.delivery-class-card')).to_have_count(12)
        # Parent addition defaults reference date, preserves child independence.
        card_group(page,'批量测试').get_by_role('button',name='添加小班',exact=True).click()
        dialog=page.get_by_role('dialog')
        expect(dialog.get_by_label('班级名称',exact=True)).to_have_value('批量测试')
        expect(dialog.get_by_placeholder('选择日期',exact=True)).to_have_value('2026-12-01')
        dialog.get_by_role('button',name=re.compile(r'取\s*消')).click()
        expect(dialog).not_to_be_visible()
        # Batch defaults a new parent; a failed command preserves the complete form.
        page.get_by_role('button',name=re.compile('创建班级$')).click()
        dialog=page.get_by_role('dialog');dialog.get_by_label('班级名称',exact=True).fill('新批量组')
        date=dialog.get_by_placeholder('选择日期',exact=True);date.fill('2026-12-09');date.press('Enter')
        dialog.get_by_label('班主任',exact=True).click()
        page.locator('.ant-select-item-option').filter(has_text='测试规划师甲').click()
        page.locator('.ant-select-item-option').filter(has_text='测试规划师乙').click()
        dialog.get_by_label('班级名称',exact=True).click()
        expect(dialog.get_by_label('父级名称',exact=True)).to_have_value('新批量组')
        page.evaluate('window.classGroupsFixture.failSave=true')
        dialog.get_by_role('button',name=re.compile('确认创建 2')).click()
        expect(page.get_by_text('班主任不可用，请重试',exact=True)).to_be_visible()
        expect(dialog.get_by_label('父级名称',exact=True)).to_have_value('新批量组')
        page.evaluate('window.classGroupsFixture.failSave=false')
        dialog.get_by_role('button',name=re.compile('确认创建 2')).evaluate('(el)=>{el.click();el.click()}')
        assert page.evaluate("classGroupsFixture.calls.filter(c=>c.url.endsWith('/create-classes')).length")==2
        expect(dialog).not_to_be_visible()
        expect(card_group(page,'新批量组')).to_have_count(1)
        payload=page.evaluate("window.classGroupsFixture.calls.filter(c=>c.url.endsWith('/create-classes')).at(-1).data")
        assert payload['groupMode']=='NEW' and payload['newGroup']['name']=='新批量组'
        # Empty group creation and deletion have separate controls.
        page.get_by_role('button',name='创建班级组',exact=True).click()
        dialog=page.get_by_role('dialog');dialog.get_by_label('班级组名称').fill('新增空组')
        dialog.get_by_role('button',name=re.compile(r'确\s*定')).evaluate('(el)=>{el.click();el.click()}')
        expect(dialog).not_to_be_visible();expect(card_group(page,'新增空组')).to_have_count(1)
        empty=card_group(page,'新增空组');empty.get_by_role('button',name='删除空组',exact=True).click()
        page.get_by_role('button',name=re.compile(r'确\s*定')).click()
        expect(card_group(page,'新增空组')).to_have_count(0)
        # Manual historical regroup with an explicit preview and retry preservation.
        card_group(page,'空组测试').get_by_role('button',name='整理小班',exact=True).click()
        dialog=page.get_by_role('dialog')
        search=dialog.get_by_placeholder('搜索班级名称或编号');search.fill('BJ200');search.press('Enter')
        expect(dialog.get_by_text('BJ200',exact=True)).to_be_visible()
        dialog.get_by_role('checkbox',name='Select row 1',exact=True).check()
        dialog.get_by_role('button',name=re.compile('核对调整$')).click()
        confirm=page.get_by_role('dialog').filter(has=page.get_by_text('确认调整班级归属',exact=True))
        expect(confirm.get_by_text(re.compile('独立班级 → 空组测试'))).to_be_visible()
        page.evaluate('window.classGroupsFixture.failMove=true')
        confirm.get_by_role('button',name=re.compile('确认调整$')).click()
        expect(page.get_by_text('班级已变化，请刷新后重试',exact=True)).to_be_visible()
        dialog=page.get_by_role('dialog');expect(dialog.get_by_text('已选 1 个班级')).to_be_visible()
        page.evaluate('window.classGroupsFixture.failMove=false')
        dialog.get_by_role('button',name=re.compile('核对调整$')).click()
        page.get_by_role('dialog').filter(has=page.get_by_text('确认调整班级归属',exact=True)).get_by_role('button',name=re.compile('确认调整$')).click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert page.evaluate('window.classGroupsFixture.rows.find(r=>r.id===200).groupId')==2
        # Search matches a child number and automatically expands its parent.
        search=page.get_by_placeholder(re.compile('班级名称|名称.*编号')).first
        search.fill('BJ114')
        expect(page.locator('.delivery-class-card').get_by_text('BJ114',exact=True)).to_be_visible()
        page.screenshot(path=str(OUT/f'search-{width}.png'),full_page=True)
        page.locator('.delivery-class-card').get_by_role('button',name='查看学员',exact=True).click()
        page.get_by_role('button',name='返回班级列表',exact=True).click()
        expect(page.get_by_placeholder('班级名称 / 编号')).to_have_value('BJ114')
        expect(page.locator('.delivery-class-card').get_by_text('BJ114',exact=True)).to_be_visible()
        page.evaluate('window.classGroupsFixture.delayChildren=true')
        search=page.get_by_placeholder('班级名称 / 编号')
        search.fill('BJ100');page.wait_for_timeout(350);search.fill('BJ114')
        expect(page.locator('.delivery-class-card').get_by_text('BJ114',exact=True)).to_be_visible()
        page.wait_for_timeout(900)
        expect(page.locator('.delivery-class-card').get_by_text('BJ100',exact=True)).to_have_count(0)
        assert not errors,errors
        page.close()
        print('PASS card/table/12+ children/create/default/retry/manual move/search',width,flush=True)

    page=browser.new_page(viewport={'width':1440,'height':1000})
    ready(page);page.get_by_role('button',name='测试表格布局').click()
    page.evaluate("classGroupsFixture.rows.push({...classGroupsFixture.rows.find(r=>r.id===200),id:499,classNo:'BJ499',groupId:2});for(let i=500;i<508;i++)classGroupsFixture.rows.push({...classGroupsFixture.rows.find(r=>r.id===200),id:i,classNo:'BJ'+i})")
    page.get_by_role('button',name='刷新',exact=True).click()
    page.locator('.ant-pagination-item-2').click()
    expect(page.get_by_text('BJ507',exact=True)).to_be_visible()
    page.get_by_role('button',name='结课',exact=True).click()
    page.get_by_role('button',name=re.compile(r'确\s*定')).click()
    expect(page.get_by_text('BJ200',exact=True)).to_be_visible()
    expect(page.locator('.ant-pagination-item-1')).to_have_class(re.compile('ant-pagination-item-active'))
    page.close();print('PASS last-page completion clamps page',flush=True)

    page=browser.new_page(viewport={'width':1440,'height':1000})
    ready(page,'root-error');expect(page.get_by_text('班级组查询失败，请重试',exact=True)).to_be_visible()
    page.evaluate('window.classGroupsFixture.failRoot=false');page.get_by_role('button',name=re.compile(r'重\s*试')).click()
    expect(page.locator('.delivery-group-card')).to_have_count(4)
    page.close()
    page=browser.new_page(viewport={'width':1440,'height':1000})
    ready(page,'child-error');group=open_group(page,'批量测试');expect(group.get_by_text('小班读取失败，请重试',exact=True)).to_be_visible()
    page.evaluate('window.classGroupsFixture.failChild=false');group.get_by_role('button',name=re.compile(r'重\s*试')).click();expect(group.locator('.delivery-class-card')).to_have_count(12)
    page.close()
    page=browser.new_page(viewport={'width':390,'height':1000})
    ready(page,'readonly');expect(page.get_by_role('button',name=re.compile('创建班级$'))).to_have_count(0)
    expect(page.get_by_role('button',name='整理小班',exact=True)).to_have_count(0)
    group=open_group(page,'批量测试');expect(group.get_by_role('button',name='编辑',exact=True)).to_have_count(0)
    expect(group.get_by_role('button',name='调整归组',exact=True)).to_have_count(0)
    page.close()
    page=browser.new_page(viewport={'width':1440,'height':1000})
    ready(page);group=open_group(page,'批量测试');expect(group.locator('.delivery-class-card')).to_have_count(12)
    page.evaluate('window.classGroupsFixture.denyRoot=true')
    page.get_by_role('button',name='刷新',exact=True).click()
    expect(page.get_by_text('无权查询班级组',exact=True)).to_be_visible()
    expect(page.locator('.delivery-class-card')).to_have_count(0)
    expect(page.locator('.delivery-group-card')).to_have_count(0)
    expect(page.get_by_role('button',name=re.compile(r'重\s*试'))).to_have_count(0)
    page.close();browser.close()
    print('PASS root/child independent retry and read-only permissions',flush=True)
