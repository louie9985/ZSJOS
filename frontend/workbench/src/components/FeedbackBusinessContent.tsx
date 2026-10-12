import type { ReactNode } from 'react'
import { Empty, Typography } from 'antd'
import AttachmentCard, { type AttachmentResource } from './AttachmentCard'

export type FeedbackContentField = { key: string; label: string; value?: ReactNode; wide?: boolean; attachments?: AttachmentResource[] }

export default function FeedbackBusinessContent({ fields }: { fields: FeedbackContentField[] }) {
  if (!fields.length) return <Empty description="暂无业务内容"/>
  return <div className="feedback-business-fields">{fields.map(field => <section key={field.key}
    className={`feedback-business-field${field.wide || field.attachments ? ' feedback-business-field-wide' : ''}`}>
    <Typography.Text strong>{field.label}</Typography.Text>
    {field.attachments ? <div className="feedback-business-attachments">{field.attachments.length
      ? field.attachments.map((file, index) => <AttachmentCard key={`${index}:${file.url || ''}`} name={file.name} load={async () => file}/>)
      : <Typography.Text type="secondary">暂无附件</Typography.Text>}</div>
      : <div className="feedback-business-value">{field.value ?? '-'}</div>}
  </section>)}</div>
}
