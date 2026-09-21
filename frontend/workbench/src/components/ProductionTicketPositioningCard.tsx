import { Empty, Space, Tag, Typography } from 'antd'
import type { ReactNode } from 'react'
import type { PositioningTicketSnapshot, PositioningFile, StudentContactFormField } from '../services/api'
import { api } from '../services/api'
import { formatTimestamp } from '../services/time'
import AttachmentCard from './AttachmentCard'
import PositioningCardFields from './PositioningCardFields'
import DetailFieldGrid, { type DetailFieldItem } from './DetailFieldGrid'

const LEGACY_SECTION_TITLES: Array<[keyof PositioningTicketSnapshot, string]> = [
  ['layer1', '定位基础内容'], ['layer2', '定位策略'], ['formula', '推荐公式'],
  ['feasibility', '可行性评估'], ['contentForm', '内容形式'], ['compliance', '合规说明'],
]

const labelSnapshot = (value: unknown): string | undefined => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
  const label = (value as { labelSnapshot?: unknown }).labelSnapshot
  return label == null || label === '' ? undefined : String(label)
}

export const formatPositioningSnapshotValue = (value: unknown, dictSnapshot?: unknown): string => {
  if (Array.isArray(dictSnapshot)) {
    const labels = dictSnapshot.map(labelSnapshot).filter((item): item is string => Boolean(item))
    if (labels.length) return labels.join('、')
  } else {
    const label = labelSnapshot(dictSnapshot)
    if (label) return label
  }
  if (value == null || value === '') return '未填写'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'string' || typeof value === 'number') return String(value)
  if (Array.isArray(value)) return value.map(item => formatPositioningSnapshotValue(item)).filter(item => item !== '未填写').join('、') || '未填写'
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    for (const key of ['displayValue', 'labelSnapshot', 'label', 'name', 'fileName', 'code']) if (record[key] != null && record[key] !== '') return String(record[key])
    return Object.values(record).map(item => formatPositioningSnapshotValue(item)).filter(item => item !== '未填写').join('、') || '未填写'
  }
  return String(value)
}

const legacySections = (snapshot: PositioningTicketSnapshot) => LEGACY_SECTION_TITLES.flatMap(([key, title]) => {
  const section = snapshot[key]
  if (!section || typeof section !== 'object' || Array.isArray(section)) return []
  const items: DetailFieldItem[] = Object.entries(section as Record<string, unknown>).map(([fieldKey, value]) => ({ key: `${String(key)}-${fieldKey}`, label: fieldKey, value: formatPositioningSnapshotValue(value) }))
  return items.length ? [{ key: String(key), title, items }] : []
})

function snapshotAttachment(snapshot: PositioningTicketSnapshot, field: StudentContactFormField, submissionId?: number) {
  const files = snapshot.dict?.[field.key]
  if (!Array.isArray(files)) return <Typography.Text type="secondary">未上传附件</Typography.Text>
  if (!files.length) return <Typography.Text type="secondary">未上传附件</Typography.Text>
  if (!snapshot.cardId) return <Typography.Text type="secondary">附件暂不可读取</Typography.Text>
  return <div className="positioning-file-grid">{files.map((item, index) => {
    const file = item as PositioningFile
    return <AttachmentCard key={`${snapshot.cardId}-${submissionId}-${file.id || index}`} name={file.name || `附件 ${index + 1}`} load={() => api.positioningCard.snapshotAttachment(snapshot.cardId!, file.id, submissionId)} />
  })}</div>
}

export default function ProductionTicketPositioningCard({ snapshot, title = '已确认定位卡', submissionId }: { snapshot?: PositioningTicketSnapshot; title?: ReactNode; submissionId?: number }) {
  if (!snapshot) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无定位卡快照" />
  const fields = (snapshot.fields || []).filter(field => field.enabled !== false).sort((left, right) => (left.sort || 0) - (right.sort || 0))
  const sections = legacySections(snapshot)
  return <section className="production-positioning-card">
    <header className="production-positioning-heading"><Typography.Title level={5}>{title}</Typography.Title><Space size="small" wrap>{snapshot.submissionNo != null && <Tag>第 {snapshot.submissionNo} 次提交</Tag>}{snapshot.submittedAt && <Typography.Text type="secondary">{formatTimestamp(snapshot.submittedAt)}</Typography.Text>}{snapshot.professionalRisk && <Tag color="warning">存在专业风险</Tag>}</Space></header>
    {fields.length > 0 && <PositioningCardFields fields={fields} render={field => field.type === 'attachment' ? snapshotAttachment(snapshot, field, submissionId) : <Typography.Text className="production-positioning-value">{formatPositioningSnapshotValue(snapshot.values?.[field.key], snapshot.dict?.[field.key])}</Typography.Text>} />}
    {sections.map(section => <section className="production-positioning-section" key={section.key}><Typography.Text strong>{section.title}</Typography.Text><DetailFieldGrid columns={2} items={section.items} /></section>)}
    {!fields.length && !sections.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="定位卡暂无可展示内容" />}
  </section>
}
