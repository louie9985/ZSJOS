import { Alert, App, Avatar, Badge, Button, Empty, Popover, Skeleton, Tooltip } from 'antd'
import { BellOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import { useCallback, useRef, useState, type UIEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, type NotifyMessage } from '../services/api'
import { buildNotifyMessageCursorParams } from '../services/notifyMessage'
import { executeNotifyMessageAction } from '../services/notifyMessageAction'
import { formatTimestamp } from '../services/time'
import { useNotifyMessages } from './NotifyMessageProvider'

export default function MessageCenter() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const requestSequence = useRef(0)
  const { unreadCount, loading: countLoading, error: countError, refreshUnreadCount } = useNotifyMessages()
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<NotifyMessage[]>([])
  const [cursor, setCursor] = useState<string>()
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const title = countError ? `消息中心：${countError}` : '消息中心'

  const loadUnreadMessages = useCallback(async (append = false) => {
    const requestId = ++requestSequence.current
    if (append) setLoadingMore(true)
    else {
      setLoading(true)
      setMessages([])
      setCursor(undefined)
      setHasMore(true)
    }
    try {
      const data = await api.myNotifyMessageCursor(buildNotifyMessageCursorParams('unread', append ? cursor : undefined))
      if (requestId !== requestSequence.current) return
      setMessages(current => append
        ? [...current, ...data.list.filter(item => !current.some(existing => existing.id === item.id))]
        : data.list)
      setCursor(data.nextCursor)
      setHasMore(data.hasMore)
      setError('')
    } catch (loadError) {
      if (requestId !== requestSequence.current) return
      setError(loadError instanceof Error ? loadError.message : '未读消息加载失败')
    } finally {
      if (requestId === requestSequence.current) {
        setLoading(false)
        setLoadingMore(false)
      }
    }
  }, [cursor])

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (nextOpen) void loadUnreadMessages(false)
  }

  const handleListScroll = (event: UIEvent<HTMLDivElement>) => {
    const target = event.currentTarget
    const nearBottom = target.scrollHeight - target.scrollTop - target.clientHeight < 80
    if (nearBottom && hasMore && !loading && !loadingMore) void loadUnreadMessages(true)
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
      {loading && messages.length === 0
        ? <div className="message-center-popup-skeleton"><Skeleton active avatar paragraph={{ rows: 3 }}/></div>
        : error && messages.length === 0
          ? <div className="message-center-popup-state"><Alert
              type="error"
              showIcon
              message="未读消息加载失败"
              description={error}
              action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void loadUnreadMessages(false)}>重试</Button>}
            /></div>
          : messages.length === 0
            ? <div className="message-center-popup-state"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无未读消息"/></div>
            : <>
              {messages.map(item => {
                const sender = item.templateNickname?.trim() || '系统消息'
                return <button
                  key={item.id}
                  type="button"
                  className="message-center-popup-item"
                  onClick={() => openMessage(item)}
                >
                  <Avatar size={36} icon={<BellOutlined/>}/>
                  <span className="message-center-popup-copy">
                    <strong>{item.templateTitle || sender}</strong>
                    <span>{item.templateSummary || '暂无摘要'}</span>
                    <time>{formatTimestamp(item.createTime)}</time>
                  </span>
                </button>
              })}
              <div className="message-center-popup-load-more">
                {error
                  ? <Button size="small" icon={<ReloadOutlined/>} onClick={() => void loadUnreadMessages(true)}>加载失败，重试</Button>
                  : loadingMore
                    ? '加载中...'
                    : hasMore ? '继续下滑加载' : '已加载全部未读消息'}
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
