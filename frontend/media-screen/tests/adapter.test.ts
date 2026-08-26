import assert from 'node:assert/strict';
import test from 'node:test';
import { adaptHistory, adaptMaintenance, adaptStats } from '../src/adapter.ts';
import { applyDevelopmentMock } from '../src/mock.ts';

test('完整新媒体实时响应映射今日汇总、部门成员、陪跑和当日走势', () => {
  const model = adaptStats({
    tenantId: 9,
    updatedAt: '2026-08-25T12:00:00+08:00',
    refreshIntervalSeconds: 5,
    partTimeIncluded: true,
    summary: { today: 18, week: 80, monthTotal: 128, monthEffective: 46 },
    departments: [
      {
        name: '新媒体一部',
        subtitle: '主管 甲',
        metrics: { today: 12, week: 55, monthTotal: 90, monthEffective: 34 },
        members: [
          { name: '成员甲', today: 7, week: 30, monthTotal: 50, monthEffective: 20 },
          { name: '成员乙', today: 5, week: 25, monthTotal: 40, monthEffective: 14 },
        ],
      },
    ],
    partTimeCompanionDepartment: {
      name: '兼职陪跑',
      subtitle: '有兼职员工',
      metrics: { today: 6, week: 25, monthTotal: 38, monthEffective: 12 },
      members: [
        {
          name: '成员甲',
          today: 6,
          week: 25,
          monthTotal: 38,
          monthEffective: 12,
          partTimers: [{ name: '兼职甲', today: 6, week: 25, monthTotal: 38, monthEffective: 12 }],
        },
      ],
    },
    todayStar: { name: '成员甲', deptName: '新媒体一部', today: 7, yesterday: 5, rankToday: 1, rankYesterday: 2 },
    yesterdayChampion: { name: '成员乙', deptName: '新媒体一部', count: 8 },
    trend: { today: [0, 2, 7, 18], yesterday: [0, 3, 6, 12], stepMinutes: 10 },
    series: { submitted: [12, 18], valid: [4, 6] },
  });

  assert.deepEqual(model.summary, {
    state: 'real',
    value: { today: 18, week: 80, monthTotal: 128, monthEffective: 46 },
  });
  assert.equal(model.departments.state, 'real');
  assert.equal(model.departments.value?.[0]?.name, '新媒体一部');
  assert.equal(model.departments.value?.[0]?.members[1]?.monthEffective, 14);
  assert.equal(model.partTimeCompanionDepartment.value?.members[0]?.partTimers?.[0]?.name, '兼职甲');
  assert.equal(model.todayStar.value?.today, 7);
  assert.equal(model.yesterdayChampion.value?.count, 8);
  assert.deepEqual(model.trend.value, { today: [0, 2, 7, 18], yesterday: [0, 3, 6, 12], stepMinutes: 10 });
  assert.deepEqual(model.series.value, { submitted: [12, 18], valid: [4, 6] });
});

test('完整接口真实空数据保持 empty', () => {
  const model = adaptStats({
    summary: { today: 0, week: 0, monthTotal: 0, monthEffective: 0 },
    departments: [],
    partTimeCompanionDepartment: null,
    todayStar: null,
    yesterdayChampion: null,
    trend: { today: [], yesterday: [], stepMinutes: 10 },
    series: { submitted: [], valid: [] },
  });
  assert.equal(model.summary.state, 'real');
  assert.equal(model.departments.state, 'empty');
  assert.equal(model.partTimeCompanionDepartment.state, 'empty');
  assert.equal(model.todayStar.state, 'empty');
  assert.equal(model.trend.state, 'empty');
  assert.equal(model.series.state, 'empty');
});

test('后端第一版旧扁平字段不再冒充新媒体实时数据', () => {
  const model = adaptStats({
    totalLeads: 128,
    departmentRanking: [{ name: '销售转化一部', leadCount: 80, rank: 1 }],
    memberRanking: [{ name: '销售专员', leadCount: 32, rank: 1 }],
    todayStar: { name: '销售专员', leadCount: 32, rank: 1 },
    partTimer: { enabled: true, items: [{ name: '销售专员', leadCount: 32, rank: 1 }] },
    trend: [{ date: '2026-08-25', leadCount: 18 }],
  });
  assert.equal(model.summary.state, 'unsupported');
  assert.equal(model.departments.state, 'unsupported');
  assert.equal(model.partTimeCompanionDepartment.state, 'unsupported');
  assert.equal(model.todayStar.state, 'unsupported');
  assert.equal(model.trend.state, 'unsupported');
});

test('富接口字段缺失时标记 unsupported 而不是补零', () => {
  const model = adaptStats({ departments: [{ name: '新媒体一部', members: [] }] });
  assert.equal(model.summary.state, 'unsupported');
  assert.equal(model.departments.state, 'unsupported');
  assert.equal(model.series.state, 'unsupported');
});

test('维护状态只映射公开字段', () => {
  assert.deepEqual(
    adaptMaintenance({ tenantId: 9, maintenanceEnabled: true, checkedAt: '2026-08-25 12:01:00' }),
    { enabled: true, checkedAt: '2026/08/25 12:01:00' },
  );
});

test('远程毫秒时间戳和 ISO 时间统一格式化为北京时间', () => {
  const stats = adaptStats({ generatedAt: Date.UTC(2026, 7, 25, 4, 0, 0), totalLeads: 1 });
  assert.equal(stats.generatedAt, '2026/08/25 12:00:00');
  assert.deepEqual(
    adaptMaintenance({ maintenanceEnabled: false, checkedAt: '2026-08-25T16:00:00+08:00' }),
    { enabled: false, checkedAt: '2026/08/25 16:00:00' },
  );
});

test('历史 available=false 永远返回无快照', () => {
  assert.deepEqual(
    adaptHistory({ available: false, snapshotDate: '2026-08-24', totalLeads: 999 }),
    {
      available: false,
      snapshotDate: '2026-08-24',
      snapshotCreatedAt: null,
      totalLeads: null,
      summary: { today: null, week: null, monthTotal: null, monthEffective: null },
      mainRanking: [],
      partTimerRanking: [],
    },
  );
});

test('历史 available=true 映射汇总、主榜和兼职榜', () => {
  const history = adaptHistory({
    available: true,
    snapshotDate: '2026-08-24',
    totalLeads: 12,
    summary: { today: 3, week: 9, monthTotal: 12, monthEffective: 7 },
    departments: [
      {
        name: '新媒体一部',
        members: [{ name: '成员甲', leadCount: 3, rank: 1, today: 3, week: 9, monthTotal: 12, monthEffective: 7 }],
      },
    ],
    partTimeCompanionDepartment: {
      name: '兼职陪跑',
      metrics: { today: 2, week: 5, monthTotal: 11, monthEffective: 4 },
      members: [{ name: '陪跑甲', today: 2, week: 5, monthTotal: 11, monthEffective: 4 }],
    },
  });
  assert.equal(history.available, true);
  assert.equal(history.summary.monthEffective, 7);
  assert.equal(history.mainRanking[0]?.departmentName, '新媒体一部');
  assert.equal(history.partTimerRanking[0]?.name, '陪跑甲');
  assert.equal(history.partTimerRanking[0]?.week, 5);
  assert.equal(history.partTimerRanking[0]?.monthTotal, 11);
  assert.equal(history.partTimerRanking[0]?.monthEffective, 4);
});

test('开发 Mock 只填 unsupported，并提供三部门和兼职陪跑布局', () => {
  const source = adaptStats({});
  const model = applyDevelopmentMock(source, true, true);
  assert.equal(model.summary.state, 'mock');
  assert.equal(model.departments.state, 'mock');
  assert.equal(model.departments.value?.map((item) => item.name).join(','), '新媒体一部,新媒体二部,新媒体三部');
  assert.equal(model.partTimeCompanionDepartment.state, 'mock');
  assert.equal(model.partTimeCompanionDepartment.value?.members[0]?.week, 17);
  assert.equal(model.trend.state, 'mock');
});

test('开发 Mock 不覆盖真实空数据', () => {
  const source = adaptStats({
    summary: { today: 0, week: 0, monthTotal: 0, monthEffective: 0 },
    departments: [],
    partTimeCompanionDepartment: null,
    todayStar: null,
    yesterdayChampion: null,
    trend: { today: [], yesterday: [], stepMinutes: 10 },
    series: { submitted: [], valid: [] },
  });
  const model = applyDevelopmentMock(source, true);
  assert.equal(model.summary.state, 'real');
  assert.equal(model.departments.state, 'empty');
  assert.equal(model.partTimeCompanionDepartment.state, 'empty');
  assert.equal(model.trend.state, 'empty');
});

test('关闭 Mock 时模型保持不变', () => {
  const source = adaptStats({});
  assert.strictEqual(applyDevelopmentMock(source, false), source);
});
