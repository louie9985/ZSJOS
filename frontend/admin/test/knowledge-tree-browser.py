"""Real Chromium integration of production knowledge-tree components with synthetic records."""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

URL = 'http://127.0.0.1:5306/test/knowledge-tree.html'
KEY = 'pms:knowledge-library:sidebar-width'
OUT = Path(os.environ['TEMP']) / 'knowledge-tree-browser'
OUT.mkdir(exist_ok=True)
results = []


def sidebar_width(page):
    return round(page.locator('.knowledge-workspace-sidebar').bounding_box()['width'])


def saved(page):
    return page.evaluate('(key) => localStorage.getItem(key)', KEY)


def drag(page, delta):
    box = page.locator('.knowledge-workspace-resizer').bounding_box()
    x, y = box['x'] + 4, box['y'] + 80
    page.mouse.move(x, y)
    page.mouse.down()
    page.mouse.move(x + delta, y, steps=5)
    page.mouse.up()


with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1920, 1440, 1280, 900, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(URL)
        expect(page.locator('.knowledge-tree-label')).to_have_count(5)
        page.wait_for_timeout(150)
        expect(page.locator('.el-tree-node.is-current')).to_have_count(1)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
        labels = page.locator('.knowledge-tree-label')
        full_name = labels.nth(2).inner_text()
        assert '三级' in full_name
        tooltip = page.locator('.pms-knowledge-tree-name-tooltip:visible')
        labels.nth(2).hover()
        page.wait_for_timeout(100)
        expect(tooltip).to_have_count(0)
        expect(tooltip).to_have_count(1, timeout=2500)
        expect(tooltip).to_have_text(full_name)
        tip_box = tooltip.bounding_box()
        assert tip_box['width'] <= min(480, width - 32) + 1
        assert tip_box['x'] >= -1 and tip_box['x'] + tip_box['width'] <= width + 1
        page.screenshot(path=str(OUT / f'knowledge-tree-tooltip-{width}.png'), full_page=True)
        page.mouse.move(0, 0)
        expect(tooltip).to_have_count(0)
        labels.nth(4).hover()
        page.wait_for_timeout(500)
        expect(tooltip).to_have_count(0)
        # Focus the actual Element Plus treeitem, not a separately focusable label.
        node = labels.nth(2).locator('xpath=ancestor::*[@role="treeitem"][1]')
        node.focus()
        expect(tooltip).to_have_text(full_name)
        page.get_by_role('button', name='切换应用侧栏', exact=True).focus()
        expect(tooltip).to_have_count(0)
        labels.nth(3).hover()
        expect(tooltip).to_have_text('A' * 160 + '.docx')
        labels.nth(1).hover()
        if labels.nth(1).evaluate('(el) => el.scrollWidth > el.clientWidth'):
            expect(tooltip).to_have_text(labels.nth(1).inner_text())
        else:
            page.wait_for_timeout(500)
            expect(tooltip).to_have_count(0)
        page.mouse.move(0, 0)
        expect(tooltip).to_have_count(0)

        if width > 900:
            resizer = page.locator('.knowledge-workspace-resizer')
            expect(resizer).to_have_count(1)
            assert sidebar_width(page) == 320
            assert resizer.bounding_box()['width'] == 8
            resizer.focus()
            resizer.press('ArrowRight')
            assert sidebar_width(page) == 336 and saved(page) == '336'
            resizer.press('ArrowLeft')
            assert sidebar_width(page) == 320
            clicks = page.locator('#clicks').inner_text()
            maximum = int(resizer.get_attribute('aria-valuemax'))
            drag(page, 1000)
            assert sidebar_width(page) == maximum
            assert page.locator('.knowledge-workspace-content').bounding_box()['width'] >= 480
            assert page.locator('#clicks').inner_text() == clicks
            assert page.evaluate('document.body.style.userSelect') == ''
            # Window constraints and application shell changes preserve the preference.
            page.set_viewport_size({'width': 1000, 'height': 1000})
            page.wait_for_timeout(180)
            assert saved(page) == str(maximum)
            expect(resizer).to_have_count(0)
            page.set_viewport_size({'width': width, 'height': 1000})
            page.wait_for_timeout(180)
            assert sidebar_width(page) == maximum
            page.get_by_role('button', name='切换应用侧栏', exact=True).click()
            assert saved(page) == str(maximum)
            page.get_by_role('button', name='切换知识库', exact=True).click()
            page.get_by_role('button', name='切换知识库', exact=True).click()
            expect(resizer).to_have_attribute('aria-valuenow', str(maximum))
            page.reload()
            expect(resizer).to_have_attribute('aria-valuenow', str(maximum))
            drag(page, -1000)
            assert sidebar_width(page) == 240 and saved(page) == '240'
            resizer.dblclick()
            assert sidebar_width(page) == 320 and saved(page) == '320'
            resizer.press('ArrowRight')
            resizer.press('Home')
            assert saved(page) == '320'
            # Cancel while captured: restore previous width and existing selection style.
            page.evaluate("document.body.style.userSelect = 'text'")
            box = resizer.bounding_box()
            page.mouse.move(box['x'] + 4, box['y'] + 80)
            page.mouse.down()
            page.mouse.move(box['x'] + 84, box['y'] + 80)
            assert page.evaluate('document.body.style.userSelect') == 'none'
            assert saved(page) == '320'
            resizer.dispatch_event('pointercancel', {'pointerId': 1})
            page.mouse.up()
            assert sidebar_width(page) == 320 and saved(page) == '320'
            assert page.evaluate('document.body.style.userSelect') == 'text'
            # Loss of window focus and component removal also clean up the drag.
            for cleanup in ('blur', 'unmount'):
                box = resizer.bounding_box()
                page.mouse.move(box['x'] + 4, box['y'] + 80)
                page.mouse.down()
                page.mouse.move(box['x'] + 84, box['y'] + 80)
                if cleanup == 'blur':
                    page.evaluate("window.dispatchEvent(new Event('blur'))")
                else:
                    page.get_by_role('button', name='切换知识库', exact=True).evaluate('(el) => el.click()')
                page.mouse.up()
                assert page.evaluate('document.body.style.userSelect') == 'text'
                if cleanup == 'unmount':
                    page.get_by_role('button', name='切换知识库', exact=True).click()
            page.get_by_role('button', name='窄工作区', exact=True).click()
            expect(resizer).to_have_count(0)
            page.get_by_role('button', name='窄工作区', exact=True).click()
            expect(resizer).to_have_attribute('aria-valuenow', '320')
        else:
            expect(page.locator('.knowledge-workspace-resizer')).to_have_count(0)
            workspace = page.locator('.knowledge-library-workspace').bounding_box()
            assert sidebar_width(page) == round(workspace['width'])

        page.get_by_role('button', name='重命名', exact=True).click()
        labels.nth(2).hover()
        expect(tooltip).to_have_text('重命名后的完整名称' * 12)
        # Actions keep their reserved space and keyboard focus makes them visible.
        action_button = labels.nth(2).locator('xpath=..').get_by_role('button', name='内容操作')
        action_button.focus()
        assert action_button.evaluate('(el) => getComputedStyle(el).opacity') == '1'
        action_button.click()
        page.get_by_role('menuitem', name='重命名', exact=True).click()
        expect(page.locator('#action')).to_have_text('rename')
        page.screenshot(path=str(OUT / f'knowledge-tree-{width}.png'), full_page=True)
        page.get_by_role('button', name='只读', exact=True).click()
        expect(page.get_by_role('button', name='内容操作')).to_have_count(0)
        page.get_by_role('button', name='空目录', exact=True).click()
        expect(page.get_by_text('暂无目录或文档', exact=True)).to_be_visible()
        assert not errors, errors
        results.append({'width': width, 'result': 'PASS'})
        page.close()

    # Width changes and shorter names remeasure overflow rather than leaving stale tooltips.
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.goto(URL + '?scenario=medium')
    label = page.locator('.knowledge-tree-label').nth(2)
    tooltip = page.locator('.pms-knowledge-tree-name-tooltip:visible')
    label.hover()
    expect(tooltip).to_have_text(label.inner_text())
    drag(page, 240)
    label.hover()
    page.wait_for_timeout(500)
    expect(tooltip).to_have_count(0)
    drag(page, -240)
    label.hover()
    expect(tooltip).to_have_text(label.inner_text())
    page.get_by_role('button', name='缩短名称', exact=True).click()
    label.hover()
    page.wait_for_timeout(500)
    expect(tooltip).to_have_count(0)
    page.close()

    for stored in ('bad', '0', '239', '561', 'Infinity'):
        page = browser.new_page(viewport={'width': 1440, 'height': 1000})
        page.goto(URL)
        page.evaluate('([key, value]) => localStorage.setItem(key, value)', [KEY, stored])
        page.reload()
        expect(page.locator('.knowledge-workspace-resizer')).to_have_attribute('aria-valuenow', '320')
        page.close()
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.add_init_script("Storage.prototype.getItem = Storage.prototype.setItem = function(){ throw new Error('storage disabled') }")
    page.goto(URL)
    resizer = page.locator('.knowledge-workspace-resizer')
    expect(resizer).to_have_attribute('aria-valuenow', '320')
    resizer.press('ArrowRight')
    expect(resizer).to_have_attribute('aria-valuenow', '336')
    browser.close()

(OUT / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
print('PASS: production knowledge tree at 1920/1440/1280/900/390; tooltips, focus, resize, storage, cancellation, rename, permissions and empty state')
print('Screenshots:', OUT)
