import { Skeleton, Typography } from 'antd'
import type { BpmApprovalDetailCard, BpmApprovalField } from '../../services/api'
import AttachmentGrid from './AttachmentGrid'
import DetailFieldGrid, { type DetailFieldItem } from '../DetailFieldGrid'

export type BusinessContentCardProps = {
  card?: BpmApprovalDetailCard | null
  loading?: boolean
}

/**
 * 审批详情里的业务内容卡。
 *
 * 审批中心不认识任何业务域：卡片只渲染后端下发的"分组 + 标签/值 + 附件"，
 * 因此新增流程不需要改这里。无内容时返回 null，由调用方退回通用展示。
 *
 * 刻意不使用 antd Descriptions —— 员工端统一用 DetailFieldGrid 渲染字段。
 */
export default function BusinessContentCard({ card, loading }: BusinessContentCardProps) {
  if (loading) {
    return <Skeleton active paragraph={{ rows: 4 }}/>
  }
  if (!card) return null
  if (card.message) {
    return <Typography.Text type="secondary">{card.message}</Typography.Text>
  }
  const groups = card.groups ?? []
  if (!card.title && groups.length === 0) return null

  return <div className="business-content-card">
    {card.title && <div className="business-content-card-heading">
      <Typography.Title level={5}>{card.title}</Typography.Title>
      {card.statusText && <Typography.Text type="secondary">{card.statusText}</Typography.Text>}
    </div>}
    {groups.map((group, index) => {
      const fields = group.fields ?? []
      // 附件字段单独渲染：它们不是"标签：值"，而是一组缩略图/链接。
      const attachments = fields.filter(field => (field.attachments?.length ?? 0) > 0)
      const plain = fields.filter(field => (field.attachments?.length ?? 0) === 0)
      const items: DetailFieldItem[] = plain.map((field, fieldIndex) => ({
        key: `${group.title ?? 'group'}-${index}-${field.label}-${fieldIndex}`,
        label: field.label,
        value: field.value,
        span: field.span === 2 ? 2 : 1
      }))
      if (items.length === 0 && attachments.length === 0) return null
      return <section key={`${group.title ?? 'group'}-${index}`} className="business-content-group">
        {group.title && <Typography.Text type="secondary">{group.title}</Typography.Text>}
        {items.length > 0 &&
          <DetailFieldGrid columns={group.span ? 1 : 2} items={items}/>}
        {attachments.map((field, fieldIndex) => (
          <AttachmentField key={`${field.label}-${fieldIndex}`} field={field}/>
        ))}
      </section>
    })}
  </div>
}

/** 附件字段：字段名做小标题，下面铺缩略图与下载链接。 */
function AttachmentField({ field }: { field: BpmApprovalField }) {
  return <div className="business-content-attachment">
    {field.label && <Typography.Text type="secondary">{field.label}</Typography.Text>}
    <AttachmentGrid attachments={field.attachments}/>
  </div>
}
