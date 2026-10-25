import { Alert, Button, Collapse, Form, Grid, InputNumber, Popover, Select, Space, Spin, Tooltip, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useEffect, useRef } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import type { DictData } from '../services/api';
import type { DiagnosisBatchCandidate, WeeklyDiagnosisPreview } from '../services/mediaAccountProfile';
import BusinessTable from './BusinessTable';
import { WeeklyDiagnosisMetrics } from './WeeklyDiagnosisMetrics';

export type DiagnosisAccountRow = {
  candidate: DiagnosisBatchCandidate;
  preview?: WeeklyDiagnosisPreview;
  loading?: boolean;
  error?: string;
};

export default function AccountDiagnosisAccounts({rows, candidates, candidatesLoading, candidatesError, pickerOpen,
  onPickerOpen, onReloadCandidates, onAdd, onRemove, onRetry, onLikesChange, initial, dicts, disabled}: {
  rows: DiagnosisAccountRow[]; candidates: DiagnosisBatchCandidate[]; candidatesLoading: boolean; candidatesError: string;
  pickerOpen: boolean; onPickerOpen: (open: boolean) => void; onReloadCandidates: () => void;
  onAdd: (candidate: DiagnosisBatchCandidate) => void; onRemove: (id: number) => void;
  onRetry: (id: number) => void; onLikesChange: (id: number, value: number) => void;
  initial: boolean; dicts: Record<string, DictData[]>; disabled: boolean;
}) {
  const screens = Grid.useBreakpoint();
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());
  useEffect(() => () => {timers.current.forEach(clearTimeout);}, []);
  const options = (key: string) => (dicts[key] || []).map(item => ({value: item.value, label: item.label}));
  const name = (row: DiagnosisAccountRow) => row.candidate.accountName || row.candidate.accountNo || '未命名账号';
  const select = (row: DiagnosisAccountRow, key: 'currentStage' | 'accountStatus', label: string, type: string) =>
    <Form.Item name={['accounts', String(row.candidate.accountId), key]} label={screens.md ? undefined : label}
      initialValue={row.candidate[key]} rules={[{required: true, message: `请选择${label}`}]}>
      <Select aria-label={`${name(row)}${label}`} options={options(type)} disabled={disabled} />
    </Form.Item>;
  const stage = (row: DiagnosisAccountRow) => select(row, 'currentStage', '当前阶段', 'zsjos_media_account_stage');
  const status = (row: DiagnosisAccountRow) => select(row, 'accountStatus', '账号状态', 'zsjos_media_account_current_status');
  const likes = (row: DiagnosisAccountRow) => row.preview?.node.parts.some(part => part.days === 7) ?
    <Form.Item name={['accounts', String(row.candidate.accountId), 'highestLikes']} label={screens.md ? undefined : '7天最高点赞'}
      rules={[{required: true, message: '请填写最高点赞量'}, {type: 'integer', min: 0, message: '请填写非负整数'}]}>
      <InputNumber aria-label={`${name(row)}最高点赞`} disabled={disabled} min={0} precision={0} max={Number.MAX_SAFE_INTEGER}
        onChange={value => {
          const id = row.candidate.accountId;
          clearTimeout(timers.current.get(id));
          if (value != null && Number.isSafeInteger(value) && Number(value) >= 0)
            timers.current.set(id, setTimeout(() => onLikesChange(id, Number(value)), 300));
        }} />
    </Form.Item> : <Typography.Text type="secondary">{row.loading ? '加载中…' : '本节点无需填写'}</Typography.Text>;
  const remove = (row: DiagnosisAccountRow) => <Tooltip title="仅从本次填写中移除，账号和待办保留">
    <Button type="text" danger disabled={disabled} icon={<DeleteOutlined />} aria-label={`移除${name(row)}`}
      onClick={() => {clearTimeout(timers.current.get(row.candidate.accountId)); onRemove(row.candidate.accountId);}} />
  </Tooltip>;
  const summary = (row: DiagnosisAccountRow) => <Space orientation="vertical" size={2}>
    <Typography.Text strong>{name(row)}</Typography.Text>
    <Typography.Text type="secondary">{row.candidate.platformLabel || '平台待填写'}</Typography.Text>
    {row.loading && <Spin size="small" />}
    {row.error && <Alert type="error" showIcon title={row.error} action={<Button size="small" disabled={disabled} onClick={() => onRetry(row.candidate.accountId)}>重试</Button>} />}
    {row.preview?.node.parts.find(part => part.days === 7)?.metric && <Typography.Text type="secondary">
      本周发布 {row.preview.node.parts.find(part => part.days === 7)?.metric.publishedCount ?? '无法统计'} 条
    </Typography.Text>}
  </Space>;
  const columns: ProColumns<DiagnosisAccountRow>[] = [
    {title: '账号', key: 'account', width: 190, ellipsis: false, render: (_, row) => summary(row)},
    {title: '当前阶段', key: 'stage', width: 180, ellipsis: false, render: (_, row) => stage(row)},
    {title: '账号状态', key: 'status', width: 190, ellipsis: false, render: (_, row) => status(row)},
    ...(!initial ? [{title: '7天最高点赞', key: 'likes', width: 170, ellipsis: false, render: (_: unknown, row: DiagnosisAccountRow) => likes(row)}] : []),
    {title: '', key: 'action', valueType: 'option', width: 48, render: (_, row) => remove(row)},
  ];
  const available = candidates.filter(candidate => !rows.some(row => row.candidate.accountId === candidate.accountId));
  return <section className="account-diagnosis-accounts">
    <div className="account-diagnosis-accounts-heading"><Typography.Title level={5}>账号明细（逐账号填写）</Typography.Title>
      <Typography.Text type="secondary" aria-live="polite">本次填写 {rows.length} 个账号</Typography.Text></div>
    {screens.md ? <BusinessTable<DiagnosisAccountRow> tableKey="diagnosis-account-details" mode="compact" resizable={false}
      rowKey={row => row.candidate.accountId} columns={columns} dataSource={rows} pagination={false} emptyText="点击下方加号添加要填写的账号" />
      : rows.map(row => <div className="account-diagnosis-mobile-row" key={row.candidate.accountId}>
        <div className="account-diagnosis-accounts-heading">{summary(row)}{remove(row)}</div>
        <div className="account-diagnosis-grid">{stage(row)}{status(row)}{!initial && likes(row)}</div>
      </div>)}
    {!screens.md && !rows.length && <Typography.Paragraph type="secondary">尚未添加账号，请点击下方加号添加</Typography.Paragraph>}
    <Popover trigger="click" open={pickerOpen} onOpenChange={onPickerOpen} placement="bottom" title="选择要添加的账号"
      content={<div className="account-diagnosis-account-picker">
        {candidatesLoading ? <Spin tip="正在加载账号…" /> : candidatesError ? <Alert type="error" showIcon title={candidatesError}
          action={<Button size="small" onClick={onReloadCandidates}>重试</Button>} /> : <Space orientation="vertical" className="account-diagnosis-candidates">
          {available.map(candidate => <Button block type="text" key={candidate.accountId} disabled={!candidate.eligible || disabled}
            onClick={() => onAdd(candidate)}><span>{candidate.accountName || candidate.accountNo || '未命名账号'} · {candidate.platformLabel || '平台待填写'}</span>
            <Typography.Text type="secondary">{candidate.eligible ? '＋ 添加' : candidate.reason}</Typography.Text></Button>)}
          {!available.some(candidate => candidate.eligible) && <Typography.Text type="secondary">暂无其他可填写账号</Typography.Text>}
        </Space>}
      </div>}>
      <Button type="dashed" block icon={<PlusOutlined />} aria-label="添加账号" className="account-diagnosis-add-account" disabled={disabled}>添加账号</Button>
    </Popover>
    {!initial && rows.some(row => row.preview) && <Collapse ghost size="small" items={rows.filter(row => row.preview).map(row => ({
      key: row.candidate.accountId, label: `${name(row)} · 查看诊断指标与统计口径`,
      children: <WeeklyDiagnosisMetrics node={row.preview!.node} />,
    }))} />}
  </section>;
}
