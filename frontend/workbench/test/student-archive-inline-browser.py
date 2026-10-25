# -*- coding: utf-8 -*-
"""Exercise actual archive components with isolated HTTP responses; no live writes."""
import copy
import base64
import json
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright

BASE = os.environ.get('ARCHIVE_INLINE_TEST_URL', 'http://127.0.0.1:5174')
OUT = Path(tempfile.mkdtemp(prefix='zsjos-student-archive-inline-'))
FILE_NAME = '合成证明材料与考试成绩确认文件用于验证长名称自动换行以及移动端布局不会横向溢出.pdf'


def field(key, label, kind='text', sensitive=False):
    return dict(key=key, label=label, type=kind, sensitive=sensitive, enabled=True,
                required=False, sort=1, group='basic', maxImages=9, maxSizeMb=100,
                allowedExtensions=['pdf', 'jpg', 'png'])


FIELDS = [field('name', '姓名'), field('job', '从事职业'),
          field('education_level', '现学历', 'dict'), field('residence', '居住地区', 'area'),
          field('proof', '证明附件', 'images'), field('phone', '手机号', sensitive=True),
          field('gender', '性别'), field('age', '年龄')]
for index, item in enumerate(FIELDS):
    item['sort'] = index
    item['group'] = {'job': 'career', 'education_level': 'education', 'proof': 'materials'}.get(item['key'], 'basic')
EXTRA_FIELDS = [dict(key=k, label=l, type=t, allowedExtensions=['pdf', 'jpg', 'png'])
                for k, l, t in [('channel', '对接渠道', 'text'),
                                ('registration_fee', '报名费', 'number'),
                                ('theory', '理论', 'composite'),
                                ('certificate_tracking_no', '证书邮寄单号', 'text')]]


class Fixture:
    def __init__(self, read_only=False, sensitive=True):
        self.read_only, self.sensitive = read_only, sensitive
        self.revision, self.extra_revision = 0, 0
        self.values = {'name': '合成学员', 'job': '原职业', 'education_level': 'old',
                       'residence': [10, 11], 'phone': '已隐藏', 'gender': '女', 'age': '25'}
        self.extra_values = {'channel': {'text': '原渠道', 'links': [], 'attachmentIds': []}}
        self.uploads, self.images, self.extra_attachments = [], [], []
        self.saves, self.extra_saves, self.unexpected = [], [], []
        self.fail_save, self.fail_extra_save, self.fail_detail = False, False, False
        self.delay_edit, self.pending = False, []
        self.requests = []
        self.fail_sensitive = False
        self.delay_sensitive = False
        self.delay_image = False

    def detail(self, relation, sensitive=False):
        values = dict(self.values)
        values['name'] = '另一报名' if relation == 3 else values['name']
        values['education_level'] = '历史学历标签' if values['education_level'] == 'old' else '服务器新学历'
        values['residence'] = '合成省 / 合成市'
        values['phone'] = f'完整合成联系电话-{relation}' if sensitive else '服务端遮罩联系电话'
        return dict(personId=1, serviceRelationId=relation, productName=f'合成课程{relation}',
                    revision=self.revision, canMaintain=not self.read_only,
                    information=dict(status='SUBMITTED', fields=FIELDS, values=values,
                                     images=self.images, canReadSensitive=self.sensitive, canExport=False),
                    history=[dict(revision=1, operatorUserId=1, operatorName='合成操作人',
                                  modifiedAt='2026-10-22 10:00:00', reason='历史维护原因')])

    def extra(self):
        return dict(revision=self.extra_revision, visibilityRevision=0, canMaintain=not self.read_only,
                    canReadAttachments=self.sensitive, canConfigureVisibility=not self.read_only,
                    fields=EXTRA_FIELDS, values=self.extra_values, attachments=self.extra_attachments,
                    educationVisibility={f['key']: True for f in EXTRA_FIELDS}, history=[])

    @staticmethod
    def respond(route, data=None, code=0, msg=''):
        route.fulfill(json={'code': code, 'data': data, 'msg': msg})

    def route(self, route):
        request = route.request
        path = urlparse(request.url).path
        if path.endswith('/system/area/tree'):
            return self.respond(route, [dict(id=10, name='合成省', children=[dict(id=11, name='合成市'), dict(id=12, name='另一个市')])])
        match = re.search(r'/student/service/(\d+)/archive(.*)$', path)
        if not match:
            self.unexpected.append(path)
            return self.respond(route, code=500, msg='未配置测试接口')
        relation, suffix = int(match[1]), match[2]
        self.requests.append((relation, request.method, suffix))
        if request.method == 'GET':
            if suffix in ('', '/sensitive'):
                if self.fail_detail:
                    self.fail_detail = False
                    return self.respond(route, code=403, msg='档案读取失败')
                if suffix == '/sensitive' and self.fail_sensitive:
                    self.fail_sensitive = False
                    return self.respond(route, code=403, msg='完整档案读取被拒绝')
                result = self.detail(relation, suffix == '/sensitive')
                if suffix == '/sensitive' and self.delay_sensitive:
                    self.delay_sensitive = False
                    self.pending.append((route, result))
                    return
                return self.respond(route, result)
            if suffix == '/edit':
                data = dict(revision=self.revision, values=dict(self.values), images=self.images,
                            options={'education_level': [dict(value='old', label='历史学历标签'), dict(value='new', label='服务器新学历')]})
                if self.delay_edit:
                    self.delay_edit = False
                    self.pending.append((route, data))
                    return
                return self.respond(route, data)
            if suffix == '/extra':
                return self.respond(route, self.extra())
            if suffix.startswith('/extra/attachments/'):
                return route.fulfill(content_type='application/pdf', body=valid_pdf())
            if suffix.startswith('/images/'):
                image_id = int(suffix.split('/')[-1])
                if self.delay_image:
                    self.delay_image = False
                    self.pending.append((route, 'image'))
                    return
                return route.fulfill(content_type='application/pdf' if image_id == 902 else 'image/png', body=valid_pdf() if image_id == 902 else PNG)
        if request.method == 'POST' and suffix in ('/images', '/extra/attachments'):
            attachment = dict(id=100 + len(self.uploads), fieldKey='proof' if suffix == '/images' else 'theory',
                              name=FILE_NAME, side='', contentType='application/pdf', size=16)
            self.uploads.append(attachment)
            return self.respond(route, attachment)
        if request.method == 'POST' and suffix in ('', '/extra'):
            body = request.post_data_json
            saves = self.extra_saves if suffix else self.saves
            saves.append(copy.deepcopy(body))
            assert body['reason'] == '' and body['idempotencyKey']
            assert len(body['changes']) == 1
            if suffix:
                if self.fail_extra_save:
                    self.fail_extra_save = False
                    return self.respond(route, code=1900090011, msg='版本冲突，请重试')
                assert body['revision'] == self.extra_revision
                self.extra_values.update(body['changes'])
                self.extra_attachments = [item for item in self.uploads if item['fieldKey'] == 'theory']
                self.extra_revision += 1
                return self.respond(route, self.extra_revision)
            if self.fail_save:
                self.fail_save = False
                return self.respond(route, code=1900090011, msg='版本冲突，请重试')
            assert body['revision'] == self.revision
            for key, value in body['changes'].items():
                if key == 'proof':
                    self.images = [item for item in self.uploads if item['id'] in value]
                else:
                    self.values[key] = value
            self.revision += 1
            return self.respond(route, self.revision)
        self.unexpected.append(path)
        self.respond(route, code=500, msg='未配置测试请求')


def button(scope, label):
    return scope.get_by_role('button', name=re.compile(r'\s*'.join(label) + r'$'))


def begin(page, label, extra=False):
    page.get_by_role('button', name='修改' + label, exact=True).click()
    group = page.get_by_role('group', name=('修改' if extra else '编辑') + label, exact=True)
    expect(group).to_be_visible()
    expect(page.get_by_role('dialog')).to_have_count(0)
    expect(page.get_by_text('维护原因', exact=True)).to_have_count(0)
    return group


def no_overflow(page):
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), page.evaluate('({width:innerWidth,scroll:document.documentElement.scrollWidth})')


def no_editor_button_overflow(group):
    bounds = group.bounding_box()
    assert bounds is not None
    right = bounds['x'] + bounds['width']
    for item in group.locator('button:visible').all():
        button_bounds = item.bounding_box()
        assert button_bounds is not None
        assert button_bounds['x'] + button_bounds['width'] <= right + 1, {
            'button': item.inner_text(), 'buttonBounds': button_bounds, 'editorBounds': bounds,
        }


def open_page(browser, width, fixture):
    page = browser.new_page(viewport={'width': width, 'height': 1100})
    page.set_default_timeout(10000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.add_init_script('''
      window.archiveBlobs = { created: [], revoked: [] };
      const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL);
      URL.createObjectURL = blob => { const url = create(blob); window.archiveBlobs.created.push(url); return url; };
      URL.revokeObjectURL = url => { window.archiveBlobs.revoked.push(url); return revoke(url); };
    ''')
    page.route('**/admin-api/**', fixture.route)
    page.goto(BASE + '/test/student-archive-inline.html', wait_until='domcontentloaded', timeout=30000)
    return page, errors


PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=')


def valid_pdf():
    stream = 'BT /F1 20 Tf 30 160 Td (Student archive PDF verified) Tj ET'
    objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
               '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 220] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
               '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
               f'<< /Length {len(stream)} >>\nstream\n{stream}\nendstream']
    source, offsets = '%PDF-1.4\n', [0]
    for index, value in enumerate(objects, 1):
        offsets.append(len(source))
        source += f'{index} 0 obj\n{value}\nendobj\n'
    xref = len(source)
    source += 'xref\n0 6\n0000000000 65535 f \n' + ''.join(f'{offset:010} 00000 n \n' for offset in offsets[1:])
    return (source + f'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n').encode('ascii')


def verify_display(browser):
    for width in (1440, 390):
        fixture = Fixture()
        fixture.images = [dict(id=901, fieldKey='proof', name='合成报名证明.png', contentType='image/png', size=len(PNG), side=''),
                          dict(id=902, fieldKey='proof', name=FILE_NAME, contentType='application/pdf', size=len(valid_pdf()), side='')]
        fixture.extra_values['theory'] = dict(text='考试成绩已记录', links=[], attachmentIds=[903])
        fixture.extra_attachments = [dict(id=903, fieldKey='theory', name='合成考试证明.pdf', contentType='application/pdf', size=len(valid_pdf()), side='')]
        page, errors = open_page(browser, width, fixture)
        expect(page.get_by_text('完整合成联系电话-2', exact=True)).to_be_visible()
        assert [suffix for _, method, suffix in fixture.requests if method == 'GET' and suffix in ('', '/sensitive')] == ['', '/sensitive']
        expect(page.get_by_role('button', name=re.compile('查看完整敏感|隐藏敏感|查看材料附件'))).to_have_count(0)
        expect(page.locator('.archive-registration-content .student-info-main > .student-info-section')).to_have_count(4)
        for tag in page.locator('.student-info-option').all():
            assert tag.evaluate("el => !['transparent','rgba(0, 0, 0, 0)'].includes(getComputedStyle(el).backgroundColor)")
        expect(page.locator('.attachment-card')).to_have_count(3)
        assert not any('/images/' in suffix or '/extra/attachments/' in suffix for _, _, suffix in fixture.requests)
        no_overflow(page)
        page.screenshot(path=str(OUT / f'archive-display-{width}.png'), full_page=True)
        # Refresh and save must retain automatic full disclosure.
        first_card = page.locator('.student-archive-card').first
        button(first_card, '刷新').click()
        expect(page.get_by_text('完整合成联系电话-2', exact=True)).to_be_visible()
        editor = begin(page, '从事职业')
        editor.get_by_role('textbox', name='从事职业', exact=True).fill('默认完整读取后维护')
        button(editor, '保存').click()
        expect(editor).to_have_count(0)
        expect(page.get_by_text('完整合成联系电话-2', exact=True)).to_be_visible()
        image_card = page.locator('.attachment-card').filter(has_text='合成报名证明.png')
        button(image_card, '预览').click()
        preview = page.get_by_role('dialog').last
        image = preview.locator('img').first
        expect(image).to_be_visible()
        page.wait_for_function('(image) => image.complete && image.naturalWidth > 0', arg=image.element_handle())
        page.screenshot(path=str(OUT / f'archive-image-{width}.png'))
        preview.locator('.ant-modal-close').click()
        pdf_card = page.locator('.archive-registration-content .attachment-card').filter(has_text=FILE_NAME)
        with page.expect_download() as download:
            button(pdf_card, '下载').click()
        assert download.value.suggested_filename == FILE_NAME
        button(pdf_card, '预览').click()
        pdf = page.get_by_role('dialog').last.locator('iframe')
        expect(pdf).to_be_visible()
        assert pdf.get_attribute('src').startswith('blob:')
        page.wait_for_timeout(700)
        page.screenshot(path=str(OUT / f'archive-pdf-{width}.png'))
        page.get_by_role('dialog').last.locator('.ant-modal-close').click()
        # Old preview bytes must be released when the exact course service changes.
        button(image_card, '预览').click()
        expect(page.get_by_role('dialog').last.locator('img')).to_be_visible()
        page.get_by_role('button', name='切换报名', exact=True).evaluate('(el) => el.click()')
        expect(page.get_by_text('完整合成联系电话-3', exact=True)).to_be_visible()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert page.evaluate('window.archiveBlobs.created.every(url => window.archiveBlobs.revoked.includes(url))')
        assert not errors, errors
        assert not fixture.unexpected, fixture.unexpected
        page.close()

    fixture = Fixture(sensitive=False)
    page, errors = open_page(browser, 390, fixture)
    expect(page.get_by_text('服务端遮罩联系电话', exact=True)).to_be_visible()
    assert not any(suffix == '/sensitive' for _, _, suffix in fixture.requests)
    expect(page.get_by_role('button', name=re.compile('查看完整敏感|隐藏敏感'))).to_have_count(0)
    page.close()

    fixture = Fixture()
    fixture.fail_sensitive = True
    page, errors = open_page(browser, 1440, fixture)
    expect(page.get_by_text('完整档案读取被拒绝', exact=True)).to_be_visible()
    expect(page.get_by_text('服务端遮罩联系电话', exact=True)).to_have_count(0)
    button(page, '重试').click()
    expect(page.get_by_text('完整合成联系电话-2', exact=True)).to_be_visible()
    page.close()

    fixture = Fixture()
    fixture.delay_sensitive = True
    page, errors = open_page(browser, 1440, fixture)
    page.wait_for_timeout(150)
    expect(page.get_by_text('服务端遮罩联系电话', exact=True)).to_have_count(0)
    page.get_by_role('button', name='切换报名', exact=True).click()
    expect(page.get_by_text('完整合成联系电话-3', exact=True)).to_be_visible()
    for route, data in fixture.pending:
        fixture.respond(route, data)
    page.wait_for_timeout(150)
    expect(page.get_by_text('完整合成联系电话-2', exact=True)).to_have_count(0)
    assert not errors, errors
    page.close()
    fixture = Fixture()
    fixture.images = [dict(id=901, fieldKey='proof', name='延迟合成材料.png', contentType='image/png', size=len(PNG), side='')]
    fixture.delay_image = True
    page, errors = open_page(browser, 390, fixture)
    expect(page.get_by_text('完整合成联系电话-2', exact=True)).to_be_visible()
    button(page.locator('.attachment-card').filter(has_text='延迟合成材料.png'), '预览').click()
    page.wait_for_timeout(150)
    assert len(fixture.pending) == 1
    page.get_by_role('button', name='切换报名', exact=True).click()
    expect(page.get_by_text('完整合成联系电话-3', exact=True)).to_be_visible()
    for route, _ in fixture.pending:
        route.fulfill(content_type='image/png', body=PNG)
    page.wait_for_timeout(150)
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert page.evaluate('window.archiveBlobs.created.length > 0 && window.archiveBlobs.created.every(url => window.archiveBlobs.revoked.includes(url))')
    assert not errors, errors
    page.close()
    print('PASS grouped archive/default full on open/refresh/save, no toggle, lazy protected cards, PNG/PDF preview/download, blob cleanup, denied permissions/retry and stale service isolation')


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='chrome', headless=True)
    try:
        verify_display(browser)
        for width in (1440, 390):
            fixture = Fixture()
            page, errors = open_page(browser, width, fixture)
            edit_button = page.get_by_role('button', name='修改从事职业', exact=True)
            expect(edit_button).to_be_enabled()
            page.mouse.move(0, 0)
            assert edit_button.evaluate('e => getComputedStyle(e).opacity') == '1'
            expect(page.get_by_role('button', name='修改年龄', exact=True)).to_have_count(0)
            expect(page.get_by_role('button', name='修改性别', exact=True)).to_have_count(0)
            page.screenshot(path=str(OUT / f'display-{width}.png'), full_page=True)
            group = begin(page, '从事职业')
            group.get_by_role('textbox', name='从事职业', exact=True).fill('取消的内容')
            expect(page.get_by_role('button', name='修改姓名', exact=True)).to_be_disabled()
            button(group, '取消').click()
            assert not fixture.saves
            expect(page.get_by_text('原职业', exact=True)).to_be_visible()
            group = begin(page, '从事职业')
            group.get_by_role('textbox', name='从事职业', exact=True).fill('更新职业')
            no_overflow(page)
            page.screenshot(path=str(OUT / f'registration-edit-{width}.png'), full_page=True)
            fixture.fail_save = True
            button(group, '保存').click()
            expect(group.get_by_role('textbox', name='从事职业', exact=True)).to_have_value('更新职业')
            expect(page.get_by_text('版本冲突，请重试', exact=True)).to_be_visible()
            button(group, '保存').click()
            expect(group).to_have_count(0)
            expect(page.get_by_text('更新职业', exact=True)).to_be_visible()
            assert fixture.saves[0] == fixture.saves[1]
            group = begin(page, '现学历')
            group.get_by_role('combobox').click()
            page.get_by_text('服务器新学历', exact=True).click()
            button(group, '保存').click()
            expect(group).to_have_count(0)
            assert fixture.saves[-1]['changes'] == {'education_level': 'new'}
            group = begin(page, '居住地区')
            group.get_by_role('combobox').click()
            page.get_by_text('另一个市', exact=True).click()
            button(group, '保存').click()
            expect(group).to_have_count(0)
            assert fixture.saves[-1]['changes'] == {'residence': [10, 12]}
            group = begin(page, '证明附件')
            group.locator('input[type=file]').set_input_files({'name': '合成证明材料.pdf', 'mimeType': 'application/pdf', 'buffer': b'%PDF-1.4 fixture'})
            expect(group.get_by_text(FILE_NAME, exact=True)).to_be_visible()
            expect(button(group, '保存')).to_be_enabled()
            page.wait_for_timeout(350)  # Let the Ant Design loading-icon exit animation finish for screenshots.
            no_overflow(page)
            no_editor_button_overflow(group)
            page.screenshot(path=str(OUT / f'registration-attachment-{width}.png'), full_page=True)
            button(group, '保存').click()
            expect(group).to_have_count(0)
            assert fixture.saves[-1]['changes'] == {'proof': [100]}
            group = begin(page, '对接渠道', extra=True)
            group.get_by_role('textbox', name='对接渠道', exact=True).fill('应取消')
            button(group, '取消').click()
            assert not fixture.extra_saves
            expect(page.get_by_text('原渠道', exact=True)).to_be_visible()
            group = begin(page, '理论', extra=True)
            group.get_by_role('textbox', name='理论', exact=True).fill('理论考试通过')
            group.get_by_role('textbox', name='理论链接', exact=True).fill(' https://example.com/exam \n')
            group.locator('input[type=file]').set_input_files({'name': '合成证明材料.pdf', 'mimeType': 'application/pdf', 'buffer': b'%PDF-1.4 fixture'})
            expect(group.get_by_text(FILE_NAME, exact=True)).to_be_visible()
            expect(button(group, '保存')).to_be_enabled()
            page.wait_for_timeout(350)
            expect(page.get_by_role('button', name='修改对接渠道', exact=True)).to_be_disabled()
            expect(page.get_by_role('button', name=re.compile('字段可见性$'))).to_be_disabled()
            no_overflow(page)
            no_editor_button_overflow(group)
            page.screenshot(path=str(OUT / f'exam-edit-{width}.png'), full_page=True)
            fixture.fail_extra_save = True
            button(group, '保存').click()
            expect(page.get_by_text('版本冲突，请重试', exact=True)).to_be_visible()
            expect(group.get_by_role('textbox', name='理论', exact=True)).to_have_value('理论考试通过')
            button(group, '保存').click()
            expect(group).to_have_count(0)
            assert fixture.extra_saves[0] == fixture.extra_saves[1]
            assert fixture.extra_saves[-1]['changes']['theory'] == {'text': '理论考试通过', 'links': ['https://example.com/exam'], 'attachmentIds': [101]}
            no_overflow(page)
            assert not errors, errors
            assert not fixture.unexpected, fixture.unexpected
            page.close()
            print(f'PASS {width}: persistent icons, inline save/cancel, errors/retry, dictionary/area, attachments, exam composite, no overflow')

        fixture = Fixture(read_only=True, sensitive=False)
        page, errors = open_page(browser, 390, fixture)
        expect(page.get_by_text('合成学员', exact=True)).to_be_visible()
        expect(page.locator('.archive-field-edit')).to_have_count(0)
        assert not errors
        page.close()
        fixture = Fixture(sensitive=False)
        page, errors = open_page(browser, 1440, fixture)
        expect(page.get_by_role('button', name='修改从事职业', exact=True)).to_be_enabled()
        expect(page.get_by_role('button', name='修改手机号', exact=True)).to_have_count(0)
        expect(page.get_by_role('button', name='修改证明附件', exact=True)).to_have_count(0)
        group = begin(page, '理论', extra=True)
        expect(group.get_by_role('button', name='上传附件', exact=True)).to_be_disabled()
        button(group, '取消').click()
        fixture.delay_edit = True
        page.get_by_role('button', name='修改从事职业', exact=True).click()
        page.get_by_role('button', name='切换报名', exact=True).click()
        expect(page.get_by_text('另一报名', exact=True)).to_be_visible()
        assert len(fixture.pending) == 1
        for route, data in fixture.pending:
            fixture.respond(route, data)
        page.wait_for_timeout(300)
        expect(page.get_by_role('group', name='编辑从事职业', exact=True)).to_have_count(0)
        expect(page.get_by_text('另一报名', exact=True)).to_be_visible()
        assert not errors
        page.close()
        fixture = Fixture()
        fixture.fail_detail = True
        page, errors = open_page(browser, 1440, fixture)
        expect(page.get_by_text('档案读取失败', exact=True)).to_be_visible()
        button(page, '重试').click()
        expect(page.get_by_text('合成学员', exact=True)).to_be_visible()
        assert not errors
        page.close()
        print('PASS read-only, sensitive controls, late edit response isolation and detail retry')
        print('Evidence: ' + str(OUT))
    finally:
        browser.close()
