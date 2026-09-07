import { Alert, App, Avatar, Badge, Button, Drawer, Empty, Grid, Input, Segmented, Skeleton, Space, Tag, Typography } from 'antd'
import { BellOutlined, CheckOutlined, EyeOutlined, LinkOutlined, ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTheme } from '../components/Theme/ThemeContext'
import { api, AuthenticationError, type NotifyMessage } from '../services/api'
import { applyReadStatus, buildNotifyMessageCursorParams, buildNotifyMessagePageParams, type NotifyMessageView } from '../services/notifyMessage'
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
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
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
  if (!message) return <Empty description="选择一条消息" />
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
      <Typography.Paragraph>{message.templateSummary || '暂无摘要'}</Typography.Paragraph>
      <Typography.Text type="secondary">完整正文</Typography.Text>
      <Typography.Paragraph>{message.templateContent || '暂无正文'}</Typography.Paragraph>
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

function reconcileSelected(current: NotifyMessage | undefined, list: NotifyMessage[], tableLayout: boolean) {
  const matched = current ? list.find(item => item.id === current.id) : undefined
  if (tableLayout) return matched ?? current
  return matched ?? current ?? list[0]
}

export default function MessageInboxPage({ view }: { view: NotifyMessageView }) {
  const { message: toast } = App.useApp()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const screens = Grid.useBreakpoint()
  const { inboxLayoutMode } = useTheme()
  const isDesktop = typeof window === 'undefined'
    ? false
    : (screens.md ?? !window.matchMedia('(max-width: 768px)').matches)
  const useTableLayout = inboxLayoutMode === 'table' && isDesktop
  const shouldOpenDetailDrawer = !isDesktop || useTableLayout
  const { status } = useRealtime()
  const { unreadCount, refreshUnreadCount } = useNotifyMessages()
  const requestSequence = useRef(0)
  const [messages, setMessages] = useState<NotifyMessage[]>([])
  const [category, setCategory] = useState<NotifyMessageCategory>('all')
  const [keyword, setKeyword] = useState('')
  const [tablePage, setTablePage] = useState(1)
  const [tablePageSize, setTablePageSize] = useState(CURSOR_LIMIT)
  const [tableTotal, setTableTotal] = useState(0)
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
  const cursorRef = useRef<string | undefined>(undefined)

  const visibleMessages = messages

  const loadCursor = useCallback(async (append = false) => {
    const requestId = ++requestSequence.current
    if (append) setLoadingMore(true)
    else {
      setLoading(true)
      setCursor(undefined)
      cursorRef.current = undefined
      setHasMore(true)
    }
    try {
      const data = useTableLayout
        ? await api.myNotifyMessagePage(buildNotifyMessagePageParams(view, tablePage, tablePageSize, keyword, category))
        : await api.myNotifyMessageCursor(buildNotifyMessageCursorParams(view, append ? cursorRef.current : undefined, CURSOR_LIMIT, keyword, category))
      if (requestId !== requestSequence.current) return
      setTableTotal('total' in data ? data.total : 0)
      setMessages(current => useTableLayout
        ? data.list
        : append
        ? [...current, ...data.list.filter(item => !current.some(existing => existing.id === item.id))]
        : data.list)
      if ('nextCursor' in data) { setCursor(data.nextCursor); cursorRef.current = data.nextCursor; setHasMore(data.hasMore) }
      if (!append) setSelected(current => reconcileSelected(current, data.list, useTableLayout))
      setError('')
      setUnauthorized(false)
    } catch (loadError) {
      if (requestId !== requestSequence.current) return
      setUnauthorized(loadError instanceof AuthenticationError)
      setError(loadError instanceof Error ? loadError.message : '消息加载失败')
    } finally {
      if (requestId === requestSequence.current) {
        setLoading(false)
        setLoadingMore(false)
      }
    }
  }, [category, keyword, tablePage, tablePageSize, useTableLayout, view])

  useEffect(() => {
    setLeadActions({})
    setLeadActionProbe(undefined)
    setLeadProbeAttempt(0)
    void loadCursor(false)
  }, [loadCursor, view])

  useEffect(() => {
    setCategory('all')
  }, [view])

  useEffect(() => {
    if (useTableLayout) return
    if (selected && visibleMessages.some(item => item.id === selected.id)) return
    setSelected(visibleMessages[0])
  }, [selected, useTableLayout, visibleMessages])

  useEffect(() => {
    const messageId = Number(searchParams.get('messageId'))
    if (!Number.isFinite(messageId) || messageId <= 0) return
    let active = true
    void api.myNotifyMessage(messageId).then(item => {
      if (!active) return
      setSelected(item)
      setLeadProbeAttempt(current => current + 1)
      if (shouldOpenDetailDrawer) setDrawerOpen(true)
      void markRead(item)
    }).catch(() => toast.warning('消息不存在或当前账号无权查看'))
    // The URL-selected message is independent of the current page result.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    return () => { active = false }
  }, [searchParams, shouldOpenDetailDrawer])

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
    const node = loadMoreRef.current
    if (useTableLayout || !node || !hasMore || loading || loadingMore) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting) void loadCursor(true)
    }, { root: node.parentElement, rootMargin: '160px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasMore, loadCursor, loading, loadingMore, useTableLayout])

  useRealtimeEvent('notify-message-new', () => {
    void loadCursor(false)
  })

  const markRead = useCallback(async (item: NotifyMessage) => {
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
  }, [refreshUnreadCount, toast, useTableLayout, view])

  const openMessageDetail = useCallback((item: NotifyMessage) => {
    setSelected(item)
    setLeadProbeAttempt(current => current + 1)
    if (shouldOpenDetailDrawer) setDrawerOpen(true)
    void markRead(item)
  }, [markRead, shouldOpenDetailDrawer])

  const markAllRead = async () => {
    setMarkingAll(true)
    try {
      await api.markAllNotifyMessagesRead()
      const readTime = Date.now()
      setMessages(current => applyReadStatus(current, current.map(item => item.id), view, readTime))
      setSelected(current => current ? { ...current, readStatus: true, readTime } : current)
      if (view === 'unread') {
        setCursor(undefined)
        cursorRef.current = undefined
        setHasMore(false)
      }
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
  const canLoadMoreForCategory = category !== 'all' && visibleMessages.length === 0 && hasMore && !loading && !useTableLayout
  const tableColumns = useMemo<ProColumns<NotifyMessage>[]>(() => [
    {
      title: '发送人', key: 'sender',
      width: 130,
      ellipsis: true,
      render: (_, item) => senderName(item),
    },
    {
      title: '标题', key: 'title',
      width: 220,
      ellipsis: true,
      render: (_, item) => item.templateTitle || senderName(item),
    },
    {
      title: '摘要', key: 'summary', dataIndex: 'templateSummary',
      ellipsis: true,
      render: (_, item) => item.templateSummary || '暂无摘要',
    },
    {
      title: '分类', key: 'category',
      width: 96,
      render: (_, item) => notifyMessageCategoryLabel[notifyMessageCategoryOf(item)],
    },
    { title: '正文', key: 'content', dataIndex: 'templateContent', width: 260, ellipsis: true },
    { title: '模板类型', key: 'templateType', dataIndex: 'templateType', width: 100 },
    { title: '动作类型', key: 'actionType', dataIndex: 'actionType', width: 130, render: (_, item) => item.actionType || '-' },
    { title: '业务类型', key: 'bizType', dataIndex: 'bizType', width: 130, render: (_, item) => item.bizType || '-' },
    { title: '业务编号', key: 'bizId', dataIndex: 'bizId', width: 110, render: (_, item) => item.bizId ?? '-' },
    { title: '通知规则', key: 'notifyRuleId', dataIndex: 'notifyRuleId', width: 110, render: (_, item) => item.notifyRuleId ?? '-' },
    { title: '来源事件', key: 'sourceEventKey', dataIndex: 'sourceEventKey', width: 180, ellipsis: true, render: (_, item) => item.sourceEventKey || '-' },
    {
      title: '时间', key: 'createTime',
      dataIndex: 'createTime',
      width: 170,
      render: (_, item) => formatTimestamp(item.createTime),
    },
    {
      title: '状态', key: 'readStatus',
      dataIndex: 'readStatus',
      width: 92,
      align: 'center',
      render: (_, item) => <Tag color={item.readStatus ? 'default' : 'processing'}>{item.readStatus ? '已读' : '未读'}</Tag>,
    },
    { title: '阅读时间', key: 'readTime', dataIndex: 'readTime', width: 170, render: (_, item) => formatTimestamp(item.readTime) },
    {
      title: '操作', key: 'action', hideInSetting: true,
      width: 92,
      align: 'center',
      render: (_, item) => <Button type="link" size="small" icon={<EyeOutlined/>} onClick={() => openMessageDetail(item)}>详细</Button>,
    }
  ], [openMessageDetail])
  const openLead = useCallback((item: NotifyMessage) => {
    void executeNotifyMessageAction(item, {
      navigate,
      warn: toast.warning,
      refreshUnreadCount
    }).catch(openError => toast.error(openError instanceof Error ? openError.message : '打开客资失败'))
  }, [navigate, refreshUnreadCount, toast])
  const retryLoad = useCallback(() => {
    if (unauthorized) {
      window.location.reload()
      return
    }
    void loadCursor(false)
  }, [loadCursor, unauthorized])
  const errorAlert = error && <Alert
    className="business-inbox-error"
    type={unauthorized ? 'warning' : 'error'}
    showIcon
    message={unauthorized ? '登录状态已失效' : error}
    action={<Button size="small" icon={<ReloadOutlined/>} onClick={retryLoad}>
      {unauthorized ? '重新登录' : '重试'}
    </Button>}
  />

  return <section className={`workspace-page business-inbox-page message-center-page${useTableLayout ? ' message-center-table-page' : ''}`}>
    <header className="business-inbox-scope-bar message-center-scope-bar">
      <div className="message-center-heading">
        <Typography.Title level={4}>{pageTitle}</Typography.Title>
        <Space size={8}>
          <Badge status={status === 'open' ? 'success' : 'warning'} text={status === 'open' ? '实时连接' : '正在重连'}/>
          <Typography.Text type="secondary">未读 {unreadCount} 条</Typography.Text>
        </Space>
      </div>
      <Space>
        <Button icon={<ReloadOutlined/>} onClick={() => {
          setLeadActions({})
          setLeadProbeAttempt(value => value + 1)
          void loadCursor(false)
          void refreshUnreadCount()
        }}>刷新</Button>
        <Button type="primary" icon={<CheckOutlined/>} loading={markingAll} disabled={unreadCount === 0} onClick={() => void markAllRead()}>全部已读</Button>
      </Space>
    </header>
    <div className="message-inbox-category-bar">
      <Input.Search allowClear value={keyword} placeholder="搜索消息标题、摘要或正文" onSearch={value => { setKeyword(value); setTablePage(1) }} onChange={event => { if (!event.target.value) { setKeyword(''); setTablePage(1) } }} style={{ maxWidth: 360 }} />
      <Segmented
        block
        value={category}
        options={NOTIFY_MESSAGE_CATEGORY_ORDER.map(item => ({ label: notifyMessageCategoryLabel[item], value: item }))}
        onChange={value => setCategory(value as NotifyMessageCategory)}
      />
    </div>
    {errorAlert}
    {useTableLayout ? (
      <>
        <div className="message-inbox-table-shell">
          <ProTable<NotifyMessage>
            className="message-inbox-table"
            rowKey="id"
            loading={loading}
            dataSource={visibleMessages}
            search={false}
            options={{ density: true, fullScreen: true, setting: true, reload: () => void loadCursor(false) }}
            columnsState={{ persistenceKey: 'crm-message-table-columns', persistenceType: 'localStorage' }}
            pagination={{ current: tablePage, pageSize: tablePageSize, total: tableTotal, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showQuickJumper: true, onChange: (page, size) => { setTablePage(page); setTablePageSize(size) } }}
            size="middle"
            bordered
            scroll={{ x: 2100 }}
            locale={{ emptyText: <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
            rowClassName={(item) => [
              selected?.id === item.id ? 'active' : '',
              item.readStatus ? '' : 'unread'
            ].filter(Boolean).join(' ')}
            columns={tableColumns}
          />
          {!useTableLayout && !loading && <div ref={loadMoreRef} className="message-inbox-load-more">
            {loadingMore ? <Button type="text" size="small" loading>正在加载更多</Button> : error ? <Button type="text" size="small" icon={<ReloadOutlined/>} onClick={() => void loadCursor(true)}>重试加载</Button> : hasMore ? <Button type="text" size="small" onClick={() => void loadCursor(true)}>继续下滑加载</Button> : messages.length > 0 ? '已加载全部消息' : null}
          </div>}
        </div>
      </>
    ) : (
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
                onClick={() => openMessageDetail(item)}
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
              <Button size="small" icon={<ReloadOutlined/>} onClick={() => void loadCursor(true)}>加载更多</Button>
            </div>}
            {!loading && visibleMessages.length > 0 && <div ref={loadMoreRef} className="message-inbox-load-more">
              {loadingMore ? '加载中…' : hasMore ? '继续下滑加载' : '已加载全部消息'}
            </div>}
          </div>
        </aside>
        <main className="business-inbox-detail-pane message-inbox-detail-pane"><MessageDetail
          message={selected}
          leadAction={selected ? leadActions[selected.id] : null}
          leadActionLoading={leadActionProbe?.messageId === selected?.id && leadActionProbe?.status === 'loading'}
          businessAction={Boolean(selected && isNotifyBusinessActionCandidate(selected))}
          onOpenLead={openLead}
        /></main>
      </div>
    )}
    {!useTableLayout && <Drawer
      className="message-inbox-mobile-drawer"
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
    </Drawer>}
    {useTableLayout && <ResizableDetailDrawer
      desktopResizable
      className="message-inbox-table-drawer"
      title="消息详情"
      placement="right"
      open={drawerOpen}
      onClose={() => setDrawerOpen(false)}
      styles={{ body: { padding: 'var(--crm-pane-pad)' } }}
    >
      <MessageDetail
        message={selected}
        leadAction={selected ? leadActions[selected.id] : null}
        leadActionLoading={leadActionProbe?.messageId === selected?.id && leadActionProbe?.status === 'loading'}
        businessAction={Boolean(selected && isNotifyBusinessActionCandidate(selected))}
        onOpenLead={openLead}
      />
    </ResizableDetailDrawer>}
  </section>
}
