import { Alert, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';
import type { ProfileEntry } from '../services/mediaAccountProfile';
import AttachmentCard from './AttachmentCard';

export function diagnosisHistoryData(entry: ProfileEntry): Record<string, unknown> | undefined {
  if (entry.kind !== 'DIAGNOSIS' || !entry.content) return undefined;
  try {
    const data: unknown = JSON.parse(entry.content);
    return data && typeof data === 'object' && !Array.isArray(data) ? data as Record<string, unknown> : undefined;
  } catch { return undefined; }
}

export function historyAction(entry: ProfileEntry, data = diagnosisHistoryData(entry)) {
  return entry.kind === 'DIAGNOSIS' ? (data?.previousEntryId != null ? '修订诊断' : '提交诊断') : '追加记录';
}

const valueText = (value: unknown): string | undefined => {
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (typeof value === 'number') return String(value);
  return typeof value === 'string' && value.trim() ? value : undefined;
};

// Match the account snapshot's field colors without deriving a status from its saved label.
const optionColors: Record<string, string> = {
  currentStageLabel: 'geekblue',
  accountStatusLabel: 'cyan',
  primaryProblemLabel: 'orange',
  secondaryProblemLabel: 'orange',
  cooperationLevelLabel: 'purple',
  reposition: 'cyan',
  changeDirectionLabel: 'cyan',
  trafficNormalLabel: 'cyan',
};

export type HistoryField = { label: string; value?: string; wide?: boolean; optionColor?: string };
export function Fields({ fields }: { fields: HistoryField[] }) {
  return <dl className="account-review-history-fields">{fields.map(({ label, value, wide, optionColor }) =>
    <div key={label} className={wide || (value?.length ?? 0) > 120 ? 'account-review-history-field is-wide' : 'account-review-history-field'}>
      <dt>{label}</dt><dd>{value == null ? <Typography.Text type="secondary">历史未留存</Typography.Text>
        : optionColor ? <Tag color={optionColor} className="account-value-tag account-review-history-option">{value}</Tag> : value}</dd>
    </div>)}</dl>;
}

export function Section({ title, children }: { title: string; children: ReactNode }) {
  return <section className="account-review-history-section"><h4>{title}</h4>{children}</section>;
}

export default function AccountReviewHistoryContent({ entry, extra }: { entry: ProfileEntry; extra?: ReactNode }) {
  const data = diagnosisHistoryData(entry);
  const requirements = data?.requirementSnapshot && typeof data.requirementSnapshot === 'object'
    ? data.requirementSnapshot as Record<string, unknown> : undefined;
  const source = data?.requirementSource && typeof data.requirementSource === 'object'
    ? data.requirementSource as Record<string, unknown> : undefined;
  const fields = (definitions: readonly (readonly [string, string])[], wide = false): HistoryField[] =>
    definitions.map(([key, label]) => ({ label, value: valueText(data?.[key]), wide, optionColor: optionColors[key] }));

  const cycleDays = entry.kind === 'DIAGNOSIS'
    ? ({ diagnosis_7d: 7, diagnosis_14d: 14, diagnosis_28d: 28, adjustment_28d: 28 } as Record<string, number>)[entry.fieldKey || ''] : undefined;
  return <article className="account-review-history-entry">
    <div className="account-review-history-entry-heading">
      <Typography.Text strong>{cycleDays ? `${cycleDays}天诊断` : entry.title}</Typography.Text>
      <Tag color={data?.previousEntryId != null ? 'orange' : 'blue'}>{historyAction(entry, data)}</Tag>
    </div>
    <div className="account-review-history-layout">
      <div className="account-review-history-main">
        {entry.kind === 'DIAGNOSIS' ? data ? <>
          <Section title="诊断概况"><Fields fields={fields([
            ['currentStageLabel', '当前阶段'], ['accountStatusLabel', '账号状态'],
            ['cooperationLevelLabel', '学员配合等级'], ['reposition', '是否重新定位'],
          ])} /></Section>
          <Section title="瓶颈与配合情况"><Fields fields={fields([
            ['primaryProblemLabel', '主要瓶颈'], ['secondaryProblemLabel', '次要瓶颈'],
            ['primaryProblemEvidence', '主要瓶颈证据'], ['secondaryProblemEvidence', '次要瓶颈证据'],
            ['cooperationEvidence', '配合等级证据'],
          ])} /></Section>
          <Section title="诊断结论与行动"><Fields fields={fields([
            ['conclusion', '一句话诊断结论'], ['improvementMeasures', '改进措施'], ['observedData', '重点观测数据'],
          ], true)} /></Section>
          {['changeDirectionLabel', 'directionSummary', 'trafficNormalLabel', 'highestLikes'].some(key => data[key] != null) &&
            <Section title="内容方向与流量"><Fields fields={fields([
              ['changeDirectionLabel', '是否需要换内容方向测试'], ['trafficNormalLabel', '流量是否正常'],
              ['directionSummary', '方向一句话概况'], ['highestLikes', '7天内最高点赞'],
            ])} /></Section>}
          <Section title="当次定位卡诊断要求">
            {requirements ? <Fields fields={[
              { label: '7天诊断要求', value: valueText(requirements.diagnosis_7d), wide: true },
              { label: '14天诊断要求', value: valueText(requirements.diagnosis_14d), wide: true },
              { label: '28天诊断要求', value: valueText(requirements.diagnosis_28d), wide: true },
            ].filter(field => !cycleDays || field.label === `${cycleDays}天诊断要求`)} /> : <Typography.Text type="secondary">历史版本未留存诊断要求</Typography.Text>}
          </Section>
        </> : <Alert type="warning" showIcon title="诊断记录格式无法读取，请联系管理员" />
          : <Section title="记录内容"><Typography.Paragraph className="account-review-history-text">{entry.content || '未填写正文'}</Typography.Paragraph>
            {!!entry.snapshots.length && <Fields fields={entry.snapshots.map(snapshot => ({
              label: snapshot.label, value: snapshot.displayValue,
              optionColor: ['select', 'multi_select', 'boolean'].includes(snapshot.type) ? 'cyan' : undefined,
            }))} />}
          </Section>}
        {extra && <Section title="周期统计">{extra}</Section>}
        {!!entry.files.length && <Section title="相关附件"><div className="account-review-history-attachments">
          {entry.files.map(file => <AttachmentCard key={file.id} name={file.name} load={async () => ({
            name: file.name, type: file.type, size: file.size,
            url: file.previewUrl && /^https?:\/\//i.test(file.previewUrl) ? file.previewUrl : undefined,
          })} />)}
        </div></Section>}
      </div>
      <aside className="account-review-history-aside" aria-label="记录信息">
        <Section title="记录信息"><Fields fields={[
          { label: '填写人', value: entry.operatedBy || '未记录填写人' },
          ...(data?.cycle != null && entry.fieldKey !== 'diagnosis_initial' ? [{ label: '诊断轮次', value: `第 ${valueText(data.cycle)} 轮` }] : []),
          ...(data?.week != null ? [{ label: '周诊断节点', value: `第 ${valueText(data.week)} 次` }] : []),
          ...(entry.kind === 'DIAGNOSIS' ? [{ label: '定位要求来源', value: source?.submissionNo != null ? `定位卡第 ${valueText(source.submissionNo)} 次提交` : '历史未留存' }] : []),
        ]} /></Section>
      </aside>
    </div>
  </article>;
}
