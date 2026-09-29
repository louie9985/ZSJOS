import { Badge, Button, Empty, Typography } from 'antd'
import { LinkOutlined } from '@ant-design/icons'
import type { NotifyMessage, NotifyMessageCategoryOption } from '../services/api'
import { notifyMessageSenderName } from '../services/notifyMessage'
import { notifyMessageCategoryLabelOf } from '../services/notifyMessageCategory'
import { formatTimestamp } from '../services/time'
import MessageCategoryIcon from './MessageCategoryIcon'

type MessageDetailProps = {
  layout?: 'pane' | 'drawer'
  message?: NotifyMessage
  categories: NotifyMessageCategoryOption[]
  leadAction?: boolean
  leadActionLoading?: boolean
  businessAction?: boolean
  onOpenLead?: (message: NotifyMessage) => void
}

export function MessageBody({ message }: Pick<MessageDetailProps, 'message'>) {
  if (!message) return <Empty description="选择一条消息" />
  const sender = notifyMessageSenderName(message)
  return <article className="message-inbox-detail message-detail-main">
    <header className="message-detail-head">
      <MessageCategoryIcon category={message.category} className="message-category-icon" size={20}/>
      <Typography.Title level={4}>{message.templateTitle || sender}</Typography.Title>
    </header>
    <section className="business-inbox-card message-detail-section">
      <Typography.Text type="secondary">消息摘要</Typography.Text>
      <Typography.Paragraph>{message.templateSummary || '暂无摘要'}</Typography.Paragraph>
      <Typography.Text type="secondary">完整正文</Typography.Text>
      <Typography.Paragraph>{message.templateContent || '暂无正文'}</Typography.Paragraph>
    </section>
  </article>
}

export function MessageStatus({ message, categories, leadAction, leadActionLoading, businessAction, onOpenLead }: MessageDetailProps) {
  if (!message) return <Typography.Text type="secondary">选择消息后查看状态</Typography.Text>
  const sender = notifyMessageSenderName(message)
  const categoryLabel = notifyMessageCategoryLabelOf(categories, message.category)
  const actionLabel = leadAction ? '客资' : '业务'
  return <>
    <div className="message-status-heading">
      <Typography.Text type="secondary">阅读状态</Typography.Text>
      <Badge status={message.readStatus ? 'default' : 'processing'} text={message.readStatus ? '已读' : '未读'}/>
    </div>
    <dl className="message-status-fields">
      <div><dt>消息分类</dt><dd>{categoryLabel}</dd></div>
      <div><dt>发送人</dt><dd>{sender}</dd></div>
      <div><dt>发送时间</dt><dd>{formatTimestamp(message.createTime)}</dd></div>
      {message.readTime != null && <div><dt>阅读时间</dt><dd>{formatTimestamp(message.readTime)}</dd></div>}
    </dl>
    {(leadActionLoading || leadAction || businessAction) && <div className="message-status-actions">
      {leadActionLoading && <Button size="small" loading>查看{actionLabel}</Button>}
      {!leadActionLoading && (leadAction || businessAction) && <Button
        type="primary"
        block
        icon={<LinkOutlined/>}
        onClick={() => onOpenLead?.(message)}
      >查看{actionLabel}</Button>}
    </div>}
  </>
}

// 正文与状态共享详情面板底色；只有抽屉按自身容器宽度切换为单列。
export default function MessageDetail(props: MessageDetailProps) {
  return <div className={props.layout === 'pane' ? 'message-detail-pane-content' : 'message-detail-drawer-content'}>
    <div className="message-detail-layout">
      <MessageBody message={props.message}/>
      <aside className="message-detail-side" aria-label="消息状态"><MessageStatus {...props}/></aside>
    </div>
  </div>
}
