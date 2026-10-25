// UTF-8. Real review/history components with isolated HTTP; no business data is read or written.
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { App, Button, Card, Space, Typography } from 'antd';
import AccountReviewRecord from '../src/components/AccountReviewRecord';
import { WeeklyDiagnosisHistory } from '../src/components/WeeklyDiagnosisMetrics';
import ThemeProvider from '../src/components/Theme/ThemeProvider';
import { http } from '../src/services/api';
import type { ProfileEntry, WeeklyDiagnosisNode } from '../src/services/mediaAccountProfile';
import '../src/styles/index.css';

const options = new URLSearchParams(location.search);
const dueAt = Date.parse('2026-10-22T00:00:00+08:00');
const node: WeeklyDiagnosisNode = {
  key: 'fixture-node', roundKey: 'fixture-round', week: 4, dueAt, taskIds: [710, 711, 712],
  parts: [7, 14, 28].map((days, i) => ({
    templateType: `diagnosis_${days}d` as 'diagnosis_7d', cycle: 28 / days, days,
    from: dueAt - days * 86400000, to: dueAt, taskId: 710 + i,
    metric: {
      state: 'SETTLED', asOf: dueAt + 3600000, settledAt: dueAt + 1000,
      publishedCount: days, missingPublishTimeCount: 0, validLeads: 8, deals: 2,
      conversionRate: 0.25, highestLikes: 0, likesCoverage: 4,
      publicationExplanation: '按固定周期内已提交审核的作品去重统计。',
      conversionExplanation: '周期有效客资已固定，成交进展持续更新。',
    },
  })),
};
const text = '诊断说明：围绕专业知识持续提供准确的案例与用户反馈。'.repeat(7);
function makeEntry(id: number, accountId = 900001, fieldKey = 'diagnosis_7d'): ProfileEntry {
  return {
    id: id + (accountId - 900001) * 100,
    kind: options.has('plain') ? 'RECORD' : 'DIAGNOSIS', fieldKey,
    title: fieldKey === 'diagnosis_14d' ? '14天验证指标诊断' : '7天账号数据诊断',
    operatedAt: dueAt - (11 - id) * 86400000 + 9 * 3600000,
    operatedBy: '测试编导姓名快照', resultVersion: id,
    snapshots: options.has('plain') ? [
      { key: 'channel', label: '渠道选择', type: 'multi_select', value: ['channel-a', 'channel-b'], displayValue: '历史渠道甲、历史渠道乙' },
      { key: 'priority', label: '优先级选择', type: 'select', value: 'normal', displayValue: '历史普通等级' },
      { key: 'confirmed', label: '确认选择', type: 'boolean', value: false, displayValue: '否' },
      { key: 'note', label: '记录说明', type: 'textarea', value: 'plain', displayValue: '普通正文应保留平面文字' },
      { key: 'missing', label: '未留存选项', type: 'select', value: 'old-code' },
      { key: 'count', label: '记录数量', type: 'number', value: 0, displayValue: '0' },
    ] : [],
    files: id === 11 ? [{ id: 81, name: '诊断证据附件.png', type: 'image/png', size: 128, previewUrl: `${location.origin}/history-test-image.png` }] : [],
    content: options.has('malformed') && id === 11 ? '{broken-json' : options.has('plain') ? '仅保留原始文字的旧记录\n附件与填写人照常展示。' : JSON.stringify({
      cycle: Math.ceil(id / 2), previousEntryId: id % 2 === 0 ? id - 1 : undefined,
      currentStageLabel: options.has('long-options') ? `S2 历史阶段标签-${'LongSavedOptionLabel'.repeat(10)}` : 'S2 历史阶段标签', accountStatusLabel: '正常（保存时标签）',
      currentStage: 'unrendered-current-stage-code', accountStatus: 'unrendered-account-status-code',
      primaryProblemLabel: '案例证据不足', primaryProblemEvidence: text,
      secondaryProblemLabel: '内容节奏', secondaryProblemEvidence: '需要按计划持续输出。',
      cooperationLevelLabel: id === 10 ? undefined : 'B级（每周4–8小时）', cooperationEvidence: '每周沟通一次。',
      conclusion: accountId === 900002 ? '另一账号的历史结论' : `第${id}条诊断结论`,
      improvementMeasures: `补充具体案例\n${'LongUnbrokenEvidence'.repeat(13)}`,
      observedData: '有效咨询量、完播率与互动量', reposition: false,
      changeDirectionLabel: '否', directionSummary: '专业知识与实际案例相结合', trafficNormalLabel: '是', highestLikes: 0,
      requirementSource: { submissionNo: 2, syncedAt: '2026-10-01T10:00:00' },
      requirementSnapshot: { diagnosis_7d: '检查账号及流量是否正常', diagnosis_14d: '检查评论区与私信咨询', diagnosis_28d: '持续观察客资转化表现' },
      ...(options.has('weekly') && id === 11 ? { weeklyNode: node } : {}),
    }),
  };
}

let failures = options.has('error') ? 1 : 0;
const requests: { accountId: number; fieldKey: string; pageNo: number; pageSize: number }[] = [];
http.defaults.adapter = async config => {
  const url = config.url || '';
  let data: unknown;
  if (url.endsWith('/history')) {
    const accountId = Number(url.match(/media-account\/(\d+)/)?.[1]);
    const { fieldKey = '', pageNo = 1, pageSize = 10 } = config.params || {};
    requests.push({ accountId, fieldKey, pageNo, pageSize });
    document.documentElement.dataset.historyRequests = JSON.stringify(requests);
    await new Promise(resolve => setTimeout(resolve, options.has('slow') && accountId === 900001 ? 1400 : 120));
    if (failures-- > 0) throw new Error('历史记录加载失败，请重试');
    const all = Array.from({ length: 11 }, (_, i) => makeEntry(11 - i, accountId, fieldKey));
    data = options.has('empty') ? { list: [], total: 0 } : { list: all.slice((pageNo - 1) * 10, pageNo * 10), total: all.length };
  } else if (url.endsWith('/entry-metrics')) data = { node, metricToken: 'fixture-metric-token' };
  else throw new Error(`Unexpected isolated fixture request: ${url}`);
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } };
};

function Fixture() {
  const [accountId, setAccountId] = useState(900001);
  const [fieldKey, setFieldKey] = useState('diagnosis_7d');
  const [canHistory, setCanHistory] = useState(!options.has('denied'));
  const title = fieldKey === 'diagnosis_14d' ? '14天验证指标诊断' : '7天账号数据诊断';
  return <main style={{ padding: 16 }}>
    <Space wrap style={{ marginBottom: 16 }}>
      <Button onClick={() => setAccountId(900002)}>切换账号</Button>
      <Button onClick={() => setFieldKey('diagnosis_14d')}>切换项目</Button>
      <Button onClick={() => setFieldKey('adjustment_28d')}>切换28天项目</Button>
      <Button onClick={() => setCanHistory(false)}>撤销历史可见权限</Button>
    </Space>
    <Card title={title} style={{ maxWidth: 460 }} data-testid="summary-card">
      <Typography.Paragraph>卡片保留填写及历史查看入口。</Typography.Paragraph>
      <AccountReviewRecord accountId={accountId} fieldKey={fieldKey} title={title}
        latest={makeEntry(11, accountId, fieldKey)} canHistory={canHistory}
        action={<Button>填写诊断</Button>}
        onRevise={() => { document.documentElement.dataset.reviseClicked = 'true'; }}
        extra={entry => {
          if (!options.has('weekly') || entry.id % 100 !== 11) return null;
          return <WeeklyDiagnosisHistory accountId={accountId} entryId={entry.id} fieldKey={entry.fieldKey} snapshot={node} />;
        }} />
    </Card>
    <Typography.Paragraph data-testid="following-content">原页面后续内容保持位置</Typography.Paragraph>
  </main>;
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture /></App></ThemeProvider>);
