// Synthetic UI acceptance only; no real student records or service mutations.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');

const account = (id, nickname, platformLabel, accountNo) => ({
  id, nickname, platformLabel, accountNo, stage: 'positioning', currentStatusLabelSnapshot: '正常',
  primaryProblems: [], version: 1, availableActions: [], detailSnapshots: [], taskLine: [],
});
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    const student = { personId: 900001, personNo: 'TEST-001', name: '示例学员', mobile: '', services: [{ serviceRelationId: 900001, status: 'active', version: 1 }] };
    let created = false;
    const initialAccounts = [
      account(1, '健康小站', '抖音', 'DY-1'),
      account(2, '健康小站', '小红书', 'XHS-2'),
      account(3, undefined, '视频号', 'WX-3'),
      ...Array.from({ length: 8 }, (_, index) => account(index + 4, `矩阵账号${index + 1}`, '抖音', `DY-${index + 4}`)),
    ];
    const detail = () => ({
      student,
      accounts: created ? [...initialAccounts, account(777, '新账号', '抖音', 'DY-777')] : initialAccounts,
      positioningCards: [], positioningDrafts: [], contents: [], productionTickets: [], operationTimeline: [], studentTaskLine: [],
      pendingStats: { accountCount: 0, positioningCount: 0, contentCount: 0, productionCount: 0 },
    });
    await page.route('**/admin-api/**', route => {
      const url = route.request().url();
      let data;
      if (url.includes('/zsjos/media-account/create')) {
        const command = route.request().postDataJSON();
        assert.equal(command.studentPersonId, 900001);
        assert.equal(command.serviceRelationId, 900001);
        assert.equal(command.version, 1);
        assert.equal(typeof command.idempotencyKey, 'string');
        created = true; data = 777;
      }
      else if (url.includes('/zsjos/media-account-field-config/published')) data = { id: 1, version: 1, fields: [] };
      else if (url.includes('/system/dict-data/simple-list')) data = [{ dictType: 'zsjos_account_platform', value: 'douyin', label: '抖音' }];
      else if (url.includes('/zsjos/media-students/page')) data = { list: [student], total: 1 };
      else if (/\/zsjos\/media-students\/\d+/.test(url)) data = detail();
      else if (url.includes('/contact-context')) data = { serviceRelationId: 900001, version: 1, availableActions: ['EDIT_BASIC_INFO', 'UPDATE_EXAM_DATE', 'VIEW_POSITIONING_INTERVIEW', 'ASSIGN_OPERATOR', 'CREATE_MEDIA_ACCOUNT'], directorStage: 'positioning_interview_completed', contentDirectorUserName: '责任编导', ownerUserName: '学习规划师', careerPlannerUserName: '职业规划师', deliveryStages: [] };
      else if (url.includes('/positioning-interview')) data = { studentPersonId: 900001, serviceRelationId: 900001, status: 'completed', version: 1, templateId: 1, templateVersionId: 1, fields: [], items: [], attachments: [{ fileId: 1, fileName: '访谈稿.docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', fileSize: 1024 }], statusOptions: {}, availableActions: ['VIEW_POSITIONING_INTERVIEW'] };
      else if (url.includes('/collaborator-candidates')) data = [{ id: 800001, nickname: '运营一号' }];
      else data = [];
      return route.fulfill({ json: { code: 0, data } });
    });
    await page.goto('http://127.0.0.1:5188/test/positioning-interview.html?overview');
    await page.getByRole('tab', { name: '概览', exact: true }).waitFor();
    assert.equal(await page.locator('.media-students-item .ant-avatar').count(), 1);
    assert.equal(await page.locator('.lead-detail-hero-avatar').count(), 1);
    assert.equal(await page.locator('.media-students-toolbar input').count(), 1);
    assert.equal(await page.getByRole('tab', { name: /健康小站 · 抖音/ }).count(), 1);
    assert.equal(await page.getByRole('tab', { name: /健康小站 · 小红书/ }).count(), 1);
    assert.equal(await page.getByRole('tab', { name: /视频号 · WX-3/ }).count(), 1);
    assert.ok(await page.getByRole('tab').count() >= 12);
    assert.equal(await page.locator('.ant-tabs-nav-more').count(), 1);

    const toolbar = page.getByRole('tabpanel').locator('.lead-action-toolbar');
    assert.equal(await toolbar.count(), 1);
    assert.equal(await toolbar.getByRole('button', { name: '修改信息' }).count(), 1);
    assert.equal(await toolbar.getByRole('button', { name: '修改考试时间' }).count(), 1);
    assert.equal(await toolbar.getByRole('button', { name: '查看定位访谈' }).count(), 1);
    assert.equal(await toolbar.getByRole('button', { name: '指派运营' }).count(), 1);

    const openToolbarAction = async name => {
      const button = toolbar.getByRole('button', { name });
      if (await button.count()) return button.click();
      await toolbar.getByRole('button', { name: '更多' }).click();
      return page.getByRole('menuitem', { name }).click();
    };
    await openToolbarAction('新增账号');
    const createDialog = page.getByRole('dialog', { name: '新增第三方账号' });
    await createDialog.getByRole('combobox').click();
    await createDialog.getByRole('combobox').press('ArrowDown');
    await createDialog.getByRole('combobox').press('Enter');
    await createDialog.getByRole('button', { name: '确 定' }).click();
    await page.getByRole('tab', { name: '新账号', exact: true }).waitFor();
    assert.equal(await page.getByRole('tab', { name: '新账号', exact: true }).getAttribute('aria-selected'), 'true');
    assert.equal(await page.getByText('账号状态维护', { exact: true }).count(), 1);
    assert.equal(await page.getByText('账号定位卡', { exact: true }).count(), 1);
    await page.getByText('内容生产历史', { exact: true }).waitFor();
    await page.locator('.ant-skeleton').waitFor({ state: 'detached' });
    await page.getByText('账号状态维护', { exact: true }).waitFor();
    await page.getByText('账号定位卡', { exact: true }).waitFor();
    await page.getByText('内容生产历史', { exact: true }).waitFor();
    await page.screenshot({ path: 'frontend/workbench/node_modules/.cache/interview-ui/account-tab-desktop.png' });

    await page.getByRole('tab', { name: '概览', exact: true }).click({ force: true });
    await page.getByText('学员档案', { exact: true }).waitFor();
    assert.equal(await page.getByText('学员档案', { exact: true }).count(), 1);
    await page.screenshot({ path: 'frontend/workbench/node_modules/.cache/interview-ui/accounts-desktop.png' });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.locator('.lead-detail-hero-avatar').count(), 1);
    assert.equal(await page.locator('.media-students-profile-card .lead-field-value').evaluateAll(items => items.every(item => item.scrollWidth <= item.clientWidth)), true);
    await page.screenshot({ path: 'frontend/workbench/node_modules/.cache/interview-ui/accounts-mobile.png' });
    assert.deepEqual(errors, []);
    console.log('PASS: original shell, unified actions, dynamic account tabs, account creation routing, tab overflow, and mobile overview.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
