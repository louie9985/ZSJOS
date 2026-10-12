import type { ReactNode } from 'react'
import { Tag, Typography } from 'antd'
import type { LeadAppeal } from '../services/api'
import { appealStageLabels, appealStatusLabels, groupAppealCycles } from '../services/leadAppealReview'
import { invalidReasonSnapshotLabel } from '../services/leadManagement'
import { formatTimestamp } from '../services/time'

type EvidenceField = 'evidence' | 'invalidEvidenceSnapshot' | 'decisionEvidence'
const statusColor = (item: LeadAppeal) => item.status === 'overturned' ? 'success' : item.status === 'upheld' ? 'warning' : item.status === 'withdrawn' ? 'default' : 'processing'
const field = (label: string, value: ReactNode) => <div className="appeal-record-field"><span>{label}：</span><div>{value}</div></div>

export default function LeadAppealProcess({ items, targetId, attachments, decision }: {
  items: LeadAppeal[]; targetId?: number
  attachments: (item: LeadAppeal, field: EvidenceField) => ReactNode
  decision: (item: LeadAppeal) => ReactNode
}) {
  const groups = groupAppealCycles(items).map(group => ({ ...group, items: [...group.items].sort((a, b) => a.roundNo - b.roundNo || a.id - b.id) }))
  const title = (group: typeof groups[number]) => group.current ? '当前无效判定' : group.key === '0' ? '未关联判定的历史记录' : `历史判定 · 首轮提交 ${formatTimestamp(group.items[0].submittedAt)}`
  const evidence = (item: LeadAppeal, key: EvidenceField, label: string) => <div className="appeal-record-evidence"><Typography.Text type="secondary">{label}：</Typography.Text>{item[key]?.length ? attachments(item, key) : <Typography.Text type="secondary">未提供</Typography.Text>}</div>
  return <div className="appeal-review-layout">
    <main className="appeal-review-main">{groups.map(group => <section key={group.key} className="appeal-cycle" aria-label={title(group)}>
      <div className="appeal-cycle-heading"><Typography.Title level={5}>{title(group)}</Typography.Title><Typography.Text type="secondary">全部轮次已展开</Typography.Text></div>
      {group.items.map(item => <article key={item.id} className={`appeal-round${item.id === targetId ? ' is-target' : ''}`} aria-label={`第 ${item.roundNo} 轮 · ${appealStageLabels[item.reviewStage]}`}>
        <header className="appeal-round-heading"><Typography.Text strong>第 {item.roundNo} 轮 · {appealStageLabels[item.reviewStage]}</Typography.Text><Tag color={statusColor(item)}>{appealStatusLabels[item.status]}</Tag></header>
        {/* Keep each round's actual snapshot, including uncorrelated legacy records, rather than merging potentially different evidence. */}
        <section className="appeal-original-conclusion"><Typography.Text strong>原无效结论</Typography.Text>
          {field('无效原因', invalidReasonSnapshotLabel(item.invalidReasonSnapshot))}
          {field('备注', item.invalidDescriptionSnapshot || '未记录说明')}
          {evidence(item, 'invalidEvidenceSnapshot', '判定附件')}
        </section>
        <div className="appeal-material-comparison">
          <section><Typography.Text strong>申诉材料</Typography.Text>
            <div className="appeal-record-meta">申请人：{item.applicantUserName || '未提供'} · {formatTimestamp(item.submittedAt)}</div>
            {field('申诉理由', item.reason || '未提供')}
            {evidence(item, 'evidence', '申诉附件')}
          </section>
          <section><Typography.Text strong>{item.status.endsWith('_reviewing') ? '处理状态' : '审批结果'}</Typography.Text>
            <div className="appeal-record-meta">{item.status.endsWith('_reviewing') ? '审批人' : '处理人'}：{item.reviewerUserName || '未提供'}{item.decidedAt ? ` · ${formatTimestamp(item.decidedAt)}` : ''}</div>
            {field('审批意见', item.decisionReason || (item.status.endsWith('_reviewing') ? '本轮正在处理中，当前仅可查看。' : '未记录审批意见'))}
            {evidence(item, 'decisionEvidence', '审批附件')}
            {item.status === 'upheld' && <Typography.Paragraph type="secondary">{item.roundNo === 3 ? '本次无效判定申诉已终审。' : '本轮已结束，符合条件的申请人可手动发起下一轮申诉。'}</Typography.Paragraph>}
          </section>
        </div>
        {decision(item)}
      </article>)}
    </section>)}</main>
    <aside className="appeal-review-history" aria-label="审批流程"><Typography.Title level={5}>审批流程</Typography.Title>
      {groups.map(group => <section className="appeal-flow-cycle" key={group.key}><Typography.Text strong>{title(group)}</Typography.Text>
        <ol className="appeal-flow-list">{group.items.flatMap(item => [
          <li key={`${item.id}-submit`}><Typography.Text strong>发起第 {item.roundNo} 轮申诉</Typography.Text><div className="appeal-record-meta">{item.applicantUserName || '未提供'} · {formatTimestamp(item.submittedAt)}</div></li>,
          <li key={`${item.id}-review`} data-pending={item.status.endsWith('_reviewing')}><Typography.Text strong>{appealStageLabels[item.reviewStage]}</Typography.Text> <Tag color={statusColor(item)}>{appealStatusLabels[item.status]}</Tag><div className="appeal-record-meta">{item.reviewerUserName || '处理人未提供'}{item.decidedAt ? ` · ${formatTimestamp(item.decidedAt)}` : ''}</div><Typography.Paragraph>{item.decisionReason || (item.status.endsWith('_reviewing') ? '尚未产生审批意见' : appealStatusLabels[item.status])}</Typography.Paragraph></li>
        ])}</ol>
      </section>)}
    </aside>
  </div>
}
