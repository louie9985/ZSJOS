import {
  Alert,
  App,
  Avatar,
  Badge,
  Button,
  Drawer,
  Empty,
  Grid,
  Pagination,
  Skeleton,
  Space,
  Tag,
  Typography
} from 'antd'
import {
  BellOutlined,
  CheckOutlined,
  ReloadOutlined
} from '@ant-design/icons'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, AuthenticationError, type NotifyMessage } from '../services/api'
import { applyReadStatus, buildNotifyMessagePageParams, type NotifyMessageView } from '../services/notifyMessage'
import { formatTimestamp } from '../services/time'
import { useNotifyMessages } from '../components/NotifyMessageProvider'
import { useRealtime, useRealtimeEvent } from '../components/RealtimeProvider'

const DEFAULT_PAGE_SIZE = 20

const senderName = (item: NotifyMessage) => item.templateNickname?.trim() || '系统消息'

function MessageDetail({ message }: { message?: NotifyMessage }) {
  if (!message) return <Empty description="从左侧选择一条消息"/>
  const sender = senderName(message)
  return <article className="message-inbox-detail">
    <header className="message-detail-hero">
      <Avatar size={44} icon={<BellOutlined/>}/>
      <div className="message-detail-heading">
        <Space size={8} wrap>
          <Typography.Title level={4}>{message.templateTitle || sender}</Typography.Title>
          <Tag color={message.readStatus ? 'default' : 'processing'}>{message.readStatus ? '已读' : '未读'}</Tag>
        </Space>
        <Typography.Text type="secondary">{formatTimestamp(message.createTime)}</Typography.Text>
      </div>
    </header>
    <section className="message-detail-section">
      <Typography.Text type="secondary">消息摘要</Typography.Text>
      <Typography.Paragraph>{message.templateSummary}</Typography.Paragraph>
      <Typography.Text type="secondary">完整正文</Typography.Text>
      <Typography.Paragraph>{message.templateContent}</Typography.Paragraph>
    </section>
    <dl className="message-detail-meta">
      <div><dt>发送人</dt><dd>{sender}</dd></div>
      <div><dt>发送时间</dt><dd>{formatTimestamp(message.createTime)}</dd></div>
      <div><dt>阅读状态</dt><dd>{message.readStatus ? '已读' : '未读'}</dd></div>
      {message.readTime && <div><dt>阅读时间</dt><dd>{formatTimestamp(message.readTime)}</dd></div>}
    </dl>
  </article>
}

export default function MessageInboxPage({ view }: { view: NotifyMessageView }) {
  const { message: toast } = App.useApp()
  const [searchParams] = useSearchParams()
  const screens = Grid.useBreakpoint()
  const { status } = useRealtime()
  const { unreadCount, refreshUnreadCount } = useNotifyMessages()
  const requestSequence = useRef(0)
  const [messages, setMessages] = useState<NotifyMessage[]>([])
  const [selected, setSelected] = useState<NotifyMessage>()
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [unauthorized, setUnauthorized] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [markingAll, setMarkingAll] = useState(false)

  const load = useCallback(async () => {
    const requestId = ++requestSequence.current
    setLoading(true)
    try {
      const data = await api.myNotifyMessagePage(buildNotifyMessagePageParams(view, pageNo, pageSize))
      if (requestId !== requestSequence.current) return
      setMessages(data.list)
      setTotal(data.total)
      setSelected(current => data.list.find(item => item.id === current?.id) ?? current ?? data.list[0])
      setError('')
      setUnauthorized(false)
    } catch (loadError) {
      if (requestId !== requestSequence.current) return
      setUnauthorized(loadError instanceof AuthenticationError)
      setError(loadError instanceof Error ? loadError.message : '消息加载失败')
    } finally {
      if (requestId === requestSequence.current) setLoading(false)
    }
  }, [pageNo, pageSize, view])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    const messageId = Number(searchParams.get('messageId'))
    if (!Number.isFinite(messageId) || messageId <= 0) return
    void api.myNotifyMessage(messageId).then(item => {
      setSelected(item)
      if (!screens.md) setDrawerOpen(true)
      void markRead(item)
    }).catch(() => toast.warning('消息不存在或当前账号无权查看'))
  // The URL-selected message is independent of the current page result.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams])
  useEffect(() => {
    if (screens.md) setDrawerOpen(false)
  }, [screens.md])
  useRealtimeEvent('notify-message-new', () => { void load() })

  const markRead = async (item: NotifyMessage) => {
    if (item.readStatus) return
    try {
      await api.markNotifyMessagesRead([item.id])
      const readTime = Date.now()
      setMessages(current => applyReadStatus(current, [item.id], view, readTime))
      setSelected(current => current?.id === item.id
        ? { ...current, readStatus: true, readTime }
        : current)
      setTotal(current => view === 'unread' ? Math.max(0, current - 1) : current)
      if (view === 'unread' && messages.length === 1 && pageNo > 1) setPageNo(pageNo - 1)
      await refreshUnreadCount()
    } catch (markError) {
      toast.error(markError instanceof Error ? markError.message : '标记已读失败')
    }
  }

  const selectMessage = (item: NotifyMessage) => {
    setSelected(item)
    if (!screens.md) setDrawerOpen(true)
    void markRead(item)
  }

  const markAllRead = async () => {
    setMarkingAll(true)
    try {
      await api.markAllNotifyMessagesRead()
      const readTime = Date.now()
      setMessages(current => applyReadStatus(current, current.map(item => item.id), view, readTime))
      setSelected(current => current ? { ...current, readStatus: true, readTime } : current)
      if (view === 'unread') setTotal(0)
      await refreshUnreadCount()
      toast.success('全部消息已标记为已读')
    } catch (markError) {
      toast.error(markError instanceof Error ? markError.message : '全部标记已读失败')
    } finally {
      setMarkingAll(false)
    }
  }

  const changePage = (page: number, size: number) => {
    setSelected(undefined)
    setPageNo(page)
    setPageSize(size)
  }

  const emptyText = view === 'unread' ? '暂无未读消息' : '暂无消息'
  const pageTitle = view === 'unread' ? '未读消息' : '全部消息'
  const errorAlert = error && <Alert
    className="message-inbox-error"
    type={unauthorized ? 'warning' : 'error'}
    showIcon
    message={unauthorized ? '登录状态已失效' : error}
    action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => unauthorized ? window.location.reload() : void load()}>
      {unauthorized ? '重新登录' : '重试'}
    </Button>}
  />

  return <section className="message-inbox-page">
    <header className="message-inbox-header">
      <div>
        <Typography.Title level={4}>{pageTitle}</Typography.Title>
        <Space size={8}>
          <Badge status={status === 'open' ? 'success' : 'warning'} text={status === 'open' ? '实时连接' : '正在重连'}/>
          <Typography.Text type="secondary">未读 {unreadCount} 条</Typography.Text>
        </Space>
      </div>
      <Space>
        <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
        <Button type="primary" icon={<CheckOutlined/>} loading={markingAll} disabled={unreadCount === 0} onClick={() => void markAllRead()}>全部已读</Button>
      </Space>
    </header>
    {errorAlert}
    <div className="message-inbox-layout">
      <aside className="message-inbox-list-pane">
        <div className="message-inbox-list" aria-label={`${pageTitle}列表`}>
          {loading ? <div className="message-inbox-skeleton"><Skeleton active paragraph={{ rows: 8 }}/></div> : messages.length ? messages.map(item => {
            const active = selected?.id === item.id
            const sender = senderName(item)
            return <button
              key={item.id}
              type="button"
              className={`message-inbox-item${active ? ' active' : ''}${item.readStatus ? '' : ' unread'}`}
              onClick={() => selectMessage(item)}
            >
              <div className="message-inbox-item-main">
                <Avatar size={38}>{sender.slice(0, 1)}</Avatar>
                <div className="message-inbox-item-copy">
                  <div className="message-inbox-item-title">
                    <strong>{item.templateTitle || sender}</strong>
                    {!item.readStatus && <Tag color="processing">未读</Tag>}
                  </div>
                  <span>{item.templateSummary}</span>
                </div>
              </div>
              <div className="message-inbox-item-meta">
                <Badge status={item.readStatus ? 'default' : 'processing'}/>
                <span>{formatTimestamp(item.createTime)}</span>
              </div>
            </button>
          }) : !error && <Empty description={emptyText}/>} 
        </div>
        {total > 0 && <div className="message-inbox-pagination">
          <Pagination
            current={pageNo}
            pageSize={pageSize}
            total={total}
            size="small"
            showSizeChanger
            showLessItems
            pageSizeOptions={[10, 20, 50]}
            onChange={changePage}
          />
        </div>}
      </aside>
      <main className="message-inbox-detail-pane"><MessageDetail message={selected}/></main>
    </div>
    <Drawer
      className="message-inbox-mobile-drawer"
      title="消息详情"
      placement="bottom"
      height="78vh"
      open={drawerOpen}
      onClose={() => setDrawerOpen(false)}
    >
      <MessageDetail message={selected}/>
    </Drawer>
  </section>
}
