import { Alert, App, Badge, Button, Empty, Popover, Skeleton, Tooltip } from 'antd'
import { BellOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import { useState, type UIEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import type { NotifyMessage } from '../services/api'
import { notifyMessageSenderName } from '../services/notifyMessage'
import { executeNotifyMessageAction } from '../services/notifyMessageAction'
import { notifyMessageCategoryLabelOf, useNotifyMessageCategories } from '../services/notifyMessageCategory'
import { formatTimestamp } from '../services/time'
import { useNotifyMessageFeed } from '../services/useNotifyMessageFeed'
import MessageCategoryIcon from './MessageCategoryIcon'
import { useNotifyMessages } from './NotifyMessageProvider'

export default function MessageCenter() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const { unreadCount, loading: countLoading, error: countError, refreshUnreadCount } = useNotifyMessages()
  const { categories } = useNotifyMessageCategories()
  const [open, setOpen] = useState(false)
  // 弹窗打开才拉取未读列表；enabled 由关闭转为打开时 hook 会自行加载，
  // 这里不再显式 reload，避免同一次打开发出两个请求。
  const feed = useNotifyMessageFeed({ view: 'unread', enabled: open })
  const title = countError ? `消息中心：${countError}` : '消息中心'

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
  }

  const handleListScroll = (event: UIEvent<HTMLDivElement>) => {
    const target = event.currentTarget
    const nearBottom = target.scrollHeight - target.scrollTop - target.clientHeight < 80
    if (nearBottom && feed.hasMore && !feed.loading && !feed.loadingMore) void feed.loadMore()
  }

  const openMessage = (item: NotifyMessage) => {
    setOpen(false)
    void executeNotifyMessageAction(item, {
      navigate,
      warn: message.warning,
      refreshUnreadCount
    }).catch(actionError => {
      message.error(actionError instanceof Error ? actionError.message : '消息打开失败')
    })
  }

  const openAllMessages = () => {
    setOpen(false)
    navigate(APP_ROUTES.ALL_MESSAGES)
  }

  const content = <div className="message-center-popup">
    <div className="message-center-popup-list" aria-live="polite" onScroll={handleListScroll}>
      {feed.loading && feed.messages.length === 0
        ? <div className="message-center-popup-skeleton"><Skeleton active avatar paragraph={{ rows: 3 }}/></div>
        : feed.error && feed.messages.length === 0
          ? <div className="message-center-popup-state"><Alert
              type="error"
              showIcon
              message="未读消息加载失败"
              description={feed.error}
              action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void feed.reload()}>重试</Button>}
            /></div>
          : feed.messages.length === 0
            ? <div className="message-center-popup-state"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无未读消息"/></div>
            : <>
              {feed.messages.map(item => {
                const sender = notifyMessageSenderName(item)
                return <button
                  key={item.id}
                  type="button"
                  className="message-center-popup-item"
                  onClick={() => openMessage(item)}
                >
                  <span className="message-center-popup-icon"><MessageCategoryIcon category={item.category} size={18}/></span>
                  <span className="message-center-popup-copy">
                    <strong>{item.templateTitle || sender}</strong>
                    <span>{item.templateSummary || '暂无摘要'}</span>
                    <time>{notifyMessageCategoryLabelOf(categories, item.category)} · {formatTimestamp(item.createTime)}</time>
                  </span>
                </button>
              })}
              <div className="message-center-popup-load-more">
                {feed.error
                  ? <Button size="small" icon={<ReloadOutlined/>} onClick={() => void feed.loadMore()}>加载失败，重试</Button>
                  : feed.loadingMore
                    ? '加载中...'
                    : feed.hasMore ? '继续下滑加载' : '已加载全部未读消息'}
              </div>
            </>}
    </div>
    <div className="message-center-popup-footer">
      <Button type="link" icon={<EyeOutlined/>} onClick={openAllMessages}>查看全部</Button>
    </div>
  </div>

  return <Popover
    classNames={{ root: 'message-center-popover' }}
    content={content}
    title={<span className="message-center-popup-title">未读消息<Badge count={unreadCount} size="small" overflowCount={99}/></span>}
    trigger="click"
    placement="bottomRight"
    open={open}
    onOpenChange={handleOpenChange}
  >
    <Tooltip title={title}>
      <Badge count={unreadCount} size="small" overflowCount={99}>
        <Button
          type="text"
          aria-label="消息中心"
          loading={countLoading && unreadCount === 0}
          icon={<BellOutlined/>}
        />
      </Badge>
    </Tooltip>
  </Popover>
}
