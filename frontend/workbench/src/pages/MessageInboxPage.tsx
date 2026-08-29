import {
  Alert,
  App,
  Avatar,
  Badge,
  Button,
  Drawer,
  Empty,
  Grid,
  Segmented,
  Skeleton,
  Space,
  Tag,
  Typography
} from 'antd'
import {
  BellOutlined,
  CheckOutlined,
  ReloadOutlined,
  LinkOutlined
} from '@ant-design/icons'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api, AuthenticationError, type NotifyMessage } from '../services/api'
import { applyReadStatus, buildNotifyMessageCursorParams, type NotifyMessageView } from '../services/notifyMessage'
import {
  NOTIFY_MESSAGE_CATEGORY_ORDER,
  notifyMessageCategoryLabel,
  notifyMessageCategoryOf,
  type NotifyMessageCategory
} from '../services/notifyMessageCategory'
import { formatTimestamp } from '../services/time'
import { useNotifyMessages } from '../components/NotifyMessageProvider'
import { useRealtime, useRealtimeEvent } from '../components/RealtimeProvider'
import DetailFieldGrid from '../components/DetailFieldGrid'
import {
  executeNotifyMessageAction,
  classifyNotifyActionError,
  isNotifyBusinessActionCandidate,
  isNotifyLeadActionCandidate,
  resolveNotifyLeadAction,
  type NotifyLeadAction
} from '../services/notifyMessageAction'

const CURSOR_LIMIT = 20

type LeadActionProbe = { messageId: number; status: 'loading' | 'error' }

const senderName = (item: NotifyMessage) => item.templateNickname?.trim() || '系统消息'

function MessageDetail({
  message,
  leadAction,
  leadActionLoading,
  businessAction,
  onOpenLead
}: {
  message?: NotifyMessage
  leadAction?: NotifyLeadAction | null
  leadActionLoading?: boolean
  businessAction?: boolean
  onOpenLead?: (message: NotifyMessage) => void
}) {
  if (!message) return <Empty description="从左侧选择一条消息"/>
  const sender = senderName(message)
  return <article className="business-inbox-detail message-inbox-detail">
    <header className="business-inbox-detail-hero message-detail-hero">
      <Avatar size={44} icon={<BellOutlined/>}/>
      <div className="message-detail-heading">
        <Space size={8} wrap>
          <Typography.Title level={4}>{message.templateTitle || sender}</Typography.Title>
          <Tag color={message.readStatus ? 'default' : 'processing'}>{message.readStatus ? '已读' : '未读'}</Tag>
        </Space>
        <Typography.Text type="secondary">{formatTimestamp(message.createTime)}</Typography.Text>
      </div>
      {leadActionLoading && <Button size="small" loading>查看客资</Button>}
      {!leadActionLoading && leadAction && <Button
        size="small"
        type="primary"
        icon={<LinkOutlined/>}
        onClick={() => onOpenLead?.(message)}
      >查看客资</Button>}
      {!leadActionLoading && !leadAction && businessAction && <Button
        size="small"
        type="primary"
        icon={<LinkOutlined/>}
        onClick={() => onOpenLead?.(message)}
      >查看业务</Button>}
    </header>
    <section className="business-inbox-card message-detail-section">
      <Typography.Text type="secondary">消息摘要</Typography.Text>
      <Typography.Paragraph>{message.templateSummary}</Typography.Paragraph>
      <Typography.Text type="secondary">完整正文</Typography.Text>
      <Typography.Paragraph>{message.templateContent}</Typography.Paragraph>
    </section>
    <section className="business-inbox-card message-detail-meta-card">
      <DetailFieldGrid className="message-detail-meta" items={[
        { key: 'sender', label: '发送人', value: sender },
        { key: 'sentAt', label: '发送时间', value: formatTimestamp(message.createTime) },
        { key: 'readStatus', label: '阅读状态', value: message.readStatus ? '已读' : '未读' },
        ...(message.readTime ? [{ key: 'readAt', label: '阅读时间', value: formatTimestamp(message.readTime) }] : [])
      ]}/>
    </section>
  </article>
}

export default function MessageInboxPage({ view }: { view: NotifyMessageView }) {
  const { message: toast } = App.useApp()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const screens = Grid.useBreakpoint()
  const { status } = useRealtime()
  const { unreadCount, refreshUnreadCount } = useNotifyMessages()
  const requestSequence = useRef(0)
  const [messages, setMessages] = useState<NotifyMessage[]>([])
  const [category, setCategory] = useState<NotifyMessageCategory>('all')
  const [selected, setSelected] = useState<NotifyMessage>()
  const [cursor, setCursor] = useState<string>()
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [unauthorized, setUnauthorized] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [markingAll, setMarkingAll] = useState(false)
  const [leadActions, setLeadActions] = useState<Record<number, NotifyLeadAction | null>>({})
  const [leadActionProbe, setLeadActionProbe] = useState<LeadActionProbe>()
  const [leadProbeAttempt, setLeadProbeAttempt] = useState(0)
  const loadMoreRef = useRef<HTMLDivElement>(null)
  const visibleMessages = useMemo(
    () => messages.filter(item => category === 'all' || notifyMessageCategoryOf(item) === category),
    [category, messages]
  )

  const load = useCallback(async (append = false) => {
    const requestId = ++requestSequence.current
    if (append) setLoadingMore(true)
    else { setLoading(true); setCursor(undefined); setHasMore(true) }
    try {
      const data = await api.myNotifyMessageCursor(buildNotifyMessageCursorParams(view, append ? cursor : undefined, CURSOR_LIMIT))
      if (requestId !== requestSequence.current) return
      setMessages(current => append ? [...current, ...data.list.filter(item => !current.some(existing => existing.id === item.id))] : data.list)
      setCursor(data.nextCursor)
      setHasMore(data.hasMore)
      setSelected(current => append ? current : data.list.find(item => item.id === current?.id) ?? current ?? data.list[0])
      setError('')
      setUnauthorized(false)
    } catch (loadError) {
      if (requestId !== requestSequence.current) return
      setUnauthorized(loadError instanceof AuthenticationError)
      setError(loadError instanceof Error ? loadError.message : '消息加载失败')
    } finally {
      if (requestId === requestSequence.current) { setLoading(false); setLoadingMore(false) }
    }
  }, [cursor, view])

  useEffect(() => { void load() }, [view])
  useEffect(() => { setCategory('all') }, [view])
  useEffect(() => {
    if (selected && visibleMessages.some(item => item.id === selected.id)) return
    setSelected(visibleMessages[0])
  }, [selected, visibleMessages])
  useEffect(() => {
    const node = loadMoreRef.current
    if (!node || !hasMore || loading || loadingMore) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting) void load(true)
    }, { root: node.parentElement, rootMargin: '160px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasMore, load, loading, loadingMore])
  useEffect(() => {
    const messageId = Number(searchParams.get('messageId'))
    if (!Number.isFinite(messageId) || messageId <= 0) return
    void api.myNotifyMessage(messageId).then(item => {
      setMessages(current => current.some(existing => existing.id === item.id) ? current : [item, ...current])
      setSelected(item)
      setCategory(notifyMessageCategoryOf(item))
      if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
      void markRead(item)
    }).catch(() => toast.warning('消息不存在或当前账号无权查看'))
  // The URL-selected message is independent of the current page result.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams])
  useEffect(() => {
    const item = selected
    if (!item || !isNotifyLeadActionCandidate(item)) {
      setLeadActionProbe(undefined)
      return
    }
    if (Object.prototype.hasOwnProperty.call(leadActions, item.id)) {
      setLeadActionProbe(undefined)
      return
    }
    let active = true
    setLeadActionProbe({ messageId: item.id, status: 'loading' })
    void resolveNotifyLeadAction(item).then(action => {
      if (active) setLeadActions(current => ({ ...current, [item.id]: action }))
    }).catch(error => {
      if (!active) return
      const kind = classifyNotifyActionError(error)
      if (kind === 'forbidden' || kind === 'missing') {
        setLeadActions(current => ({ ...current, [item.id]: null }))
        return
      }
      setLeadActionProbe({ messageId: item.id, status: 'error' })
    })
    return () => { active = false }
  }, [leadActions, leadProbeAttempt, selected])
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
      await refreshUnreadCount()
    } catch (markError) {
      toast.error(markError instanceof Error ? markError.message : '标记已读失败')
    }
  }

  const selectMessage = (item: NotifyMessage) => {
    setSelected(item)
    setLeadProbeAttempt(current => current + 1)
    if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
    void markRead(item)
  }

  const openLead = (item: NotifyMessage) => {
    void executeNotifyMessageAction(item, {
      navigate,
      warn: toast.warning,
      refreshUnreadCount
    }).catch(openError => toast.error(openError instanceof Error ? openError.message : '打开客资失败'))
  }

  const markAllRead = async () => {
    setMarkingAll(true)
    try {
      await api.markAllNotifyMessagesRead()
      const readTime = Date.now()
      setMessages(current => applyReadStatus(current, current.map(item => item.id), view, readTime))
      setSelected(current => current ? { ...current, readStatus: true, readTime } : current)
      if (view === 'unread') { setCursor(undefined); setHasMore(false) }
      await refreshUnreadCount()
      toast.success('全部消息已标记为已读')
    } catch (markError) {
      toast.error(markError instanceof Error ? markError.message : '全部标记已读失败')
    } finally {
      setMarkingAll(false)
    }
  }

  const emptyText = category === 'all'
    ? (view === 'unread' ? '暂无未读消息' : '暂无消息')
    : `${notifyMessageCategoryLabel[category]}暂无消息`
  const pageTitle = view === 'unread' ? '未读消息' : '全部消息'
  const canLoadMoreForCategory = category !== 'all' && visibleMessages.length === 0 && hasMore && !loading
  const errorAlert = error && <Alert
    className="business-inbox-error"
    type={unauthorized ? 'warning' : 'error'}
    showIcon
    message={unauthorized ? '登录状态已失效' : error}
    action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => unauthorized ? window.location.reload() : void load()}>
      {unauthorized ? '重新登录' : '重试'}
    </Button>}
  />

  return <section className="workspace-page business-inbox-page message-center-page">
    <header className="business-inbox-scope-bar message-center-scope-bar">
      <div className="message-center-heading">
        <Typography.Title level={4}>{pageTitle}</Typography.Title>
        <Space size={8}>
          <Badge status={status === 'open' ? 'success' : 'warning'} text={status === 'open' ? '实时连接' : '正在重连'}/>
          <Typography.Text type="secondary">未读 {unreadCount} 条</Typography.Text>
        </Space>
      </div>
      <Space><Button icon={<ReloadOutlined/>} onClick={() => { void load(); void refreshUnreadCount(); setLeadActions({}); setLeadProbeAttempt(value => value + 1) }}>刷新</Button><Button type="primary" icon={<CheckOutlined/>} loading={markingAll} disabled={unreadCount === 0} onClick={() => void markAllRead()}>全部已读</Button></Space>
    </header>
    <div className="message-inbox-category-bar">
      <Segmented
        block
        value={category}
        options={NOTIFY_MESSAGE_CATEGORY_ORDER.map(item => ({ label: notifyMessageCategoryLabel[item], value: item }))}
        onChange={value => setCategory(value as NotifyMessageCategory)}
      />
    </div>
    {errorAlert}
    <div className="business-inbox-layout">
      <aside className="business-inbox-list-pane">
        <div className="business-inbox-scroll message-center-list" aria-label={`${pageTitle}列表`}>
          {loading ? <div className="message-inbox-skeleton"><Skeleton active paragraph={{ rows: 8 }}/></div> : visibleMessages.length ? visibleMessages.map(item => {
            const active = selected?.id === item.id
            const sender = senderName(item)
            return <button
              key={item.id}
              type="button"
              className={`business-inbox-item message-center-item${active ? ' active' : ''}${item.readStatus ? '' : ' unread'}`}
              onClick={() => selectMessage(item)}
            >
              <div className="business-inbox-item-main">
                <Avatar size={38}>{sender.slice(0, 1)}</Avatar>
                <div className="business-inbox-item-copy message-center-item-copy">
                  <div className="business-inbox-item-title">
                    <strong>{item.templateTitle || sender}</strong>
                    {!item.readStatus && <Tag color="processing">未读</Tag>}
                  </div>
                  <span className="message-center-item-summary">{item.templateSummary}</span>
                </div>
              </div>
              <div className="business-inbox-item-meta">
                <Badge status={item.readStatus ? 'default' : 'processing'}/>
                <span>{formatTimestamp(item.createTime)}</span>
              </div>
            </button>
          }) : !error && <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} />}
          {canLoadMoreForCategory && <div className="message-inbox-load-more">
            <Button size="small" icon={<ReloadOutlined/>} onClick={() => void load(true)}>加载更多</Button>
          </div>}
          {!loading && visibleMessages.length > 0 && <div ref={loadMoreRef} className="message-inbox-load-more">
            {loadingMore ? '加载中…' : hasMore ? '继续下滑加载' : '已加载全部消息'}
          </div>}
        </div>
      </aside>
      <main className="business-inbox-detail-pane"><MessageDetail
        message={selected}
        leadAction={selected ? leadActions[selected.id] : null}
        leadActionLoading={leadActionProbe?.messageId === selected?.id && leadActionProbe?.status === 'loading'}
        businessAction={Boolean(selected && isNotifyBusinessActionCandidate(selected))}
        onOpenLead={openLead}
      /></main>
    </div>
    <Drawer
      className="business-inbox-mobile-drawer message-inbox-mobile-drawer"
      title="消息详情"
      placement="bottom"
      height="78vh"
      open={drawerOpen}
      onClose={() => setDrawerOpen(false)}
    >
      <MessageDetail
        message={selected}
        leadAction={selected ? leadActions[selected.id] : null}
        leadActionLoading={leadActionProbe?.messageId === selected?.id && leadActionProbe?.status === 'loading'}
        businessAction={Boolean(selected && isNotifyBusinessActionCandidate(selected))}
        onOpenLead={openLead}
      />
    </Drawer>
  </section>
}
