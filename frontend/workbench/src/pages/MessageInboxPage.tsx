import BusinessTable from '../components/BusinessTable'
import { Alert, App, Badge, Button, Empty, Input, Segmented, Skeleton, Space, Tag, Typography } from 'antd'
import { CheckOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useInboxTableLayout } from '../services/inboxLayout'
import { api, AuthenticationError, type NotifyMessage, type NotifyMessageCategoryOption } from '../services/api'
import {
  applyReadStatus,
  buildNotifyMessagePageParams,
  notifyMessageSenderName,
  type NotifyMessageView
} from '../services/notifyMessage'
import { notifyMessageCategoryLabelOf, useNotifyMessageCategories } from '../services/notifyMessageCategory'
import { formatTimestamp } from '../services/time'
import { useNotifyMessages } from '../components/NotifyMessageProvider'
import { useRealtime, useRealtimeEvent } from '../components/RealtimeProvider'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'
import MessageDetail from '../components/MessageDetail'
import MessageCategoryIcon from '../components/MessageCategoryIcon'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'
import { useNotifyMessageFeed } from '../services/useNotifyMessageFeed'
import { type ProColumns } from '@ant-design/pro-components'
import {
  executeNotifyMessageAction,
  classifyNotifyActionError,
  isNotifyBusinessActionCandidate,
  isNotifyLeadActionCandidate,
  resolveNotifyLeadAction,
  type NotifyLeadAction
} from '../services/notifyMessageAction'

const CURSOR_LIMIT = 20
const ALL_CATEGORY = 'all'

type LeadActionProbe = { messageId: number; status: 'loading' | 'error' }

function reconcileSelected(current: NotifyMessage | undefined, list: NotifyMessage[], tableLayout: boolean) {
  const matched = current ? list.find(item => item.id === current.id) : undefined
  if (tableLayout) return matched ?? current
  return matched ?? current ?? list[0]
}

export default function MessageInboxPage({ view }: { view: NotifyMessageView }) {
  const { message: toast } = App.useApp()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { isDesktop, useTableLayout } = useInboxTableLayout()
  const shouldOpenDetailDrawer = !isDesktop || useTableLayout
  const { status } = useRealtime()
  const { unreadCount, refreshUnreadCount } = useNotifyMessages()
  const { categories } = useNotifyMessageCategories()
  const requestSequence = useRef(0)
  const [keyword, setKeyword] = useState('')
  // 独立的输入框状态：直接绑定 keyword 会在受控与非受控之间跳变，导致输入被重置。
  const [searchText, setSearchText] = useState('')
  const [category, setCategory] = useState<string>(ALL_CATEGORY)
  const [tableMessages, setTableMessages] = useState<NotifyMessage[]>([])
  const [tablePage, setTablePage] = useState(1)
  const [tablePageSize, setTablePageSize] = useState(CURSOR_LIMIT)
  const [tableTotal, setTableTotal] = useState(0)
  const [tableLoading, setTableLoading] = useState(true)
  const [tableError, setTableError] = useState('')
  const [tableUnauthorized, setTableUnauthorized] = useState(false)
  const [selected, setSelected] = useState<NotifyMessage>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [markingAll, setMarkingAll] = useState(false)
  const [leadActions, setLeadActions] = useState<Record<number, NotifyLeadAction | null>>({})
  const [leadActionProbe, setLeadActionProbe] = useState<LeadActionProbe>()
  const [leadProbeAttempt, setLeadProbeAttempt] = useState(0)
  const loadMoreRef = useRef<HTMLDivElement>(null)

  const feed = useNotifyMessageFeed({
    view, keyword, category, limit: CURSOR_LIMIT, enabled: !useTableLayout
  })
  const visibleMessages = useTableLayout ? tableMessages : feed.messages

  const loadTable = useCallback(async () => {
    const requestId = ++requestSequence.current
    setTableLoading(true)
    try {
      const data = await api.myNotifyMessagePage(
        buildNotifyMessagePageParams(view, tablePage, tablePageSize, keyword, category))
      if (requestId !== requestSequence.current) return
      setTableMessages(data.list)
      setTableTotal(data.total)
      setTableError('')
      setTableUnauthorized(false)
      setSelected(current => reconcileSelected(current, data.list, true))
    } catch (loadError) {
      if (requestId !== requestSequence.current) return
      setTableUnauthorized(loadError instanceof AuthenticationError)
      setTableError(loadError instanceof Error ? loadError.message : '消息加载失败')
    } finally {
      if (requestId === requestSequence.current) setTableLoading(false)
    }
  }, [category, keyword, tablePage, tablePageSize, view])

  const reload = useCallback(() => {
    if (useTableLayout) void loadTable()
    else void feed.reload()
  }, [feed, loadTable, useTableLayout])

  useEffect(() => {
    if (!useTableLayout) {
      setTableMessages([])
      setSelected(undefined)
      return
    }
    setLeadActions({})
    setLeadActionProbe(undefined)
    void loadTable()
  }, [loadTable, useTableLayout, view])

  useEffect(() => {
    setCategory(ALL_CATEGORY)
    setKeyword('')
    setSearchText('')
    setTablePage(1)
  }, [view])

  useEffect(() => {
    setSelected(undefined)
    setDrawerOpen(false)
  }, [category, keyword, view])

  // 未读消息打开后会从列表移除，但正在阅读的正文与状态不能跳到下一条。
  // 换视图或筛选条件时清空选中项，新结果加载完成后再选首条。
  useEffect(() => {
    if (useTableLayout || feed.loading) return
    if (selected && (visibleMessages.some(item => item.id === selected.id)
      || (view === 'unread' && selected.readStatus))) return
    setSelected(visibleMessages[0])
  }, [feed.loading, selected, useTableLayout, view, visibleMessages])

  const markRead = useCallback(async (item: NotifyMessage) => {
    if (item.readStatus) return
    try {
      await api.markNotifyMessagesRead([item.id])
      const readTime = Date.now()
      setSelected(current => current?.id === item.id ? { ...current, readStatus: true, readTime } : current)
      if (useTableLayout) {
        setTableMessages(current => applyReadStatus(current, [item.id], view, readTime))
        // 未读视图下该行会被移出列表，总数必须同步，否则分页与数据不一致。
        if (view === 'unread') setTableTotal(current => Math.max(0, current - 1))
      } else {
        feed.setMessages(current => applyReadStatus(current, [item.id], view, readTime))
      }
      await refreshUnreadCount()
    } catch (markError) {
      toast.error(markError instanceof Error ? markError.message : '标记已读失败')
    }
  }, [feed, refreshUnreadCount, toast, useTableLayout, view])

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
    // 深链选中的消息独立于当前页结果，不应因列表加载而重新触发。
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
    if (useTableLayout || !node || !feed.hasMore || feed.loading || feed.loadingMore) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting) void feed.loadMore()
    }, { root: node.parentElement, rootMargin: '160px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [feed, useTableLayout])

  useRealtimeEvent('notify-message-new', () => { reload() })

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
      if (useTableLayout) {
        const ids = tableMessages.map(item => item.id)
        setTableMessages(current => applyReadStatus(current, ids, view, readTime))
        if (view === 'unread') setTableTotal(0)
      } else {
        feed.setMessages(current => applyReadStatus(current, current.map(item => item.id), view, readTime))
        if (view === 'unread') feed.stopLoadingMore()
      }
      setSelected(current => current ? { ...current, readStatus: true, readTime } : current)
      await refreshUnreadCount()
      toast.success('全部消息已标记为已读')
    } catch (markError) {
      toast.error(markError instanceof Error ? markError.message : '全部标记已读失败')
    } finally {
      setMarkingAll(false)
    }
  }

  const categoryLabel = (key?: string | null) => notifyMessageCategoryLabelOf(categories, key)
  const emptyText = category === ALL_CATEGORY
    ? (view === 'unread' ? '暂无未读消息' : '暂无消息')
    : `${categoryLabel(category)}暂无消息`
  const canLoadMoreForCategory = category !== ALL_CATEGORY && visibleMessages.length === 0
    && feed.hasMore && !feed.loading && !useTableLayout

  const categoryOptions = useMemo(() => categories.map(item => ({
    value: item.key,
    label: <Space size={4}><MessageCategoryIcon category={item.key} size={14}/>{item.label}</Space>
  })), [categories])

  const tableColumns = useMemo<ProColumns<NotifyMessage>[]>(() => [
    { title: '发送人', key: 'sender', width: 130, ellipsis: true, render: (_, item) => notifyMessageSenderName(item) },
    {
      title: '标题', key: 'title', width: 220, ellipsis: true,
      render: (_, item) => item.templateTitle || notifyMessageSenderName(item),
    },
    { title: '摘要', key: 'summary', dataIndex: 'templateSummary', ellipsis: true, render: (_, item) => item.templateSummary || '暂无摘要' },
    {
      title: '分类', key: 'category', width: 110,
      // 分类由服务端下发，客户端只做展示映射，不再重新判断。
      render: (_, item) => <Space size={4}>
        <MessageCategoryIcon category={item.category} size={14}/>
        {categoryLabel(item.category)}
      </Space>,
    },
    { title: '正文', key: 'content', dataIndex: 'templateContent', width: 260, ellipsis: true },
    {
      title: '时间', key: 'createTime', dataIndex: 'createTime', width: 170,
      render: (_, item) => formatTimestamp(item.createTime),
    },
    {
      title: '状态', key: 'readStatus', dataIndex: 'readStatus', width: 92, align: 'center',
      render: (_, item) => <Tag color={item.readStatus ? 'default' : 'processing'}>{item.readStatus ? '已读' : '未读'}</Tag>,
    },
    {
      title: '阅读时间', key: 'readTime', dataIndex: 'readTime', width: 170,
      render: (_, item) => formatTimestamp(item.readTime),
    },
    {
      title: '操作', key: 'action', hideInSetting: true, width: 92, align: 'center',
      render: (_, item) => <Button type="link" size="small" icon={<EyeOutlined/>} onClick={() => openMessageDetail(item)}>详细</Button>,
    }
  ], [categories, openMessageDetail])

  const openLead = useCallback((item: NotifyMessage) => {
    void executeNotifyMessageAction(item, {
      navigate, warn: toast.warning, refreshUnreadCount
    }).catch(openError => toast.error(openError instanceof Error ? openError.message : '打开客资失败'))
  }, [navigate, refreshUnreadCount, toast])

  const error = useTableLayout ? tableError : feed.error
  const unauthorized = useTableLayout ? tableUnauthorized : feed.unauthorized
  const retryLoad = useCallback(() => {
    if (unauthorized) {
      window.location.reload()
      return
    }
    reload()
  }, [reload, unauthorized])
  const errorAlert = error && <Alert
    className="business-inbox-error"
    type={unauthorized ? 'warning' : 'error'}
    showIcon
    message={unauthorized ? '登录状态已失效' : error}
    action={<Button size="small" icon={<ReloadOutlined/>} onClick={retryLoad}>
      {unauthorized ? '重新登录' : '重试'}
    </Button>}
  />

  const searchControl = <Input.Search
    allowClear
    className="message-inbox-search"
    value={searchText}
    placeholder="搜索消息标题、摘要或正文"
    onSearch={value => { setKeyword(value); setTablePage(1) }}
    onChange={event => {
      setSearchText(event.target.value)
      if (!event.target.value) { setKeyword(''); setTablePage(1) }
    }}
  />

  const detailProps = {
    message: selected,
    categories,
    leadAction: Boolean(selected && isNotifyLeadActionCandidate(selected)) || Boolean(selected && leadActions[selected.id]?.kind),
    leadActionLoading: leadActionProbe?.messageId === selected?.id && leadActionProbe?.status === 'loading',
    businessAction: Boolean(selected && isNotifyBusinessActionCandidate(selected)),
    onOpenLead: openLead
  }

  return <section className={`workspace-page business-inbox-page message-inbox-view-page${useTableLayout ? ' business-inbox-table-page' : ''}`}>
    <header className="business-inbox-scope-bar">
      <div className="business-inbox-scope-row">
        {/* 「全部消息」与「未读消息」是两个同级菜单、指向同一组件，仅靠菜单高亮无法分辨，
            因此页内保留视图标题。 */}
        <Typography.Title level={4} className="message-inbox-view-title">{view === 'unread' ? '未读消息' : '全部消息'}</Typography.Title>
        {useTableLayout && searchControl}
        <Segmented
          value={category}
          options={categoryOptions}
          onChange={value => { setCategory(value as string); setTablePage(1) }}
        />
        <Space size={8}>
          <Badge status={status === 'open' ? 'success' : 'warning'} text={status === 'open' ? '实时连接' : '正在重连'}/>
          {/* 这是账号级未读总数，两个视图下都相同，须与当前列表区分开。 */}
          <Typography.Text type="secondary">未读总数 {unreadCount} 条</Typography.Text>
        </Space>
        <Space className="message-inbox-scope-actions">
          <Button icon={<ReloadOutlined/>} onClick={() => {
            setLeadActions({})
            setLeadProbeAttempt(value => value + 1)
            reload()
            void refreshUnreadCount()
          }}>刷新</Button>
          <IrreversiblePopconfirm
            action="全部标记已读"
            disabled={unreadCount === 0}
            description="将把当前账号的全部未读消息标记为已读，包含当前分类与搜索条件之外的消息，且无法撤回。"
            onConfirm={() => void markAllRead()}
          >
            <Button type="primary" icon={<CheckOutlined/>} loading={markingAll} disabled={unreadCount === 0}>全部已读</Button>
          </IrreversiblePopconfirm>
        </Space>
      </div>
    </header>
    {errorAlert}
    {useTableLayout ? (
      <BusinessTable<NotifyMessage> tableKey="message-inbox-page-1"
        className="business-inbox-table"
        rowKey="id"
        loading={tableLoading}
        dataSource={visibleMessages}
        error={tableError}
        unauthorized={tableUnauthorized}
        onReload={() => void loadTable()}
        columnsState={{ persistenceKey: 'crm-message-table-columns', persistenceType: 'localStorage' }}
        pagination={{ current: tablePage, pageSize: tablePageSize, total: tableTotal, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showQuickJumper: true, onChange: (page, size) => { setTablePage(page); setTablePageSize(size) } }}
        size="middle"
        scroll={{ x: 1600 }}
        locale={{ emptyText: <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
        rowClassName={(item) => [
          selected?.id === item.id ? 'active' : '',
          item.readStatus ? '' : 'unread'
        ].filter(Boolean).join(' ')}
        columns={tableColumns}
      />
    ) : (
      <div className="business-inbox-layout">
          <aside className="business-inbox-list-pane">
            <div className="business-inbox-scroll" aria-label={`${view === 'unread' ? '未读消息' : '全部消息'}列表`}>
              <div className="message-inbox-list-search">{searchControl}</div>
              {feed.loading ? <div className="message-inbox-skeleton"><Skeleton active paragraph={{ rows: 8 }}/></div> : visibleMessages.length ? visibleMessages.map(item => {
                const active = selected?.id === item.id
                const sender = notifyMessageSenderName(item)
                return <button
                  key={item.id}
                  type="button"
                  className={`business-inbox-item message-center-item${active ? ' active' : ''}${item.readStatus ? '' : ' unread'}`}
                  onClick={() => openMessageDetail(item)}
                >
                  <div className="business-inbox-item-main">
                    <span className="message-center-item-icon"><MessageCategoryIcon category={item.category} size={18}/></span>
                    <div className="business-inbox-item-copy message-center-item-copy">
                      <div className="business-inbox-item-title">
                        <strong>{item.templateTitle || sender}</strong>
                        {!item.readStatus && <Tag color="processing">未读</Tag>}
                      </div>
                      <span className="message-center-item-summary">{item.templateSummary}</span>
                    </div>
                  </div>
                  <div className="business-inbox-item-meta">
                    <Tag>{categoryLabel(item.category)}</Tag>
                    <span>{formatTimestamp(item.createTime)}</span>
                  </div>
                </button>
              }) : !feed.error && <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} />}
              {canLoadMoreForCategory && <div className="message-inbox-load-more">
                <Button size="small" icon={<ReloadOutlined/>} onClick={() => void feed.loadMore()}>加载更多</Button>
              </div>}
              {!feed.loading && visibleMessages.length > 0 && <div ref={loadMoreRef} className="message-inbox-load-more">
                {feed.loadingMore ? '加载中…' : feed.hasMore ? '继续下滑加载' : '已加载全部消息'}
              </div>}
            </div>
          </aside>
          <main className="business-inbox-detail-pane"><MessageDetail {...detailProps} layout="pane"/></main>
      </div>
    )}
    {/* 与申诉/投诉/查重/BPM 一致：单个抽屉，窄屏或表格模式下经共享类显示。 */}
    <ResizableDetailDrawer
      desktopResizable={useTableLayout}
      className="business-inbox-mobile-drawer"
      title="消息详情"
      placement="right"
      width="100%"
      open={drawerOpen}
      onClose={() => setDrawerOpen(false)}
    >
      <MessageDetail {...detailProps}/>
    </ResizableDetailDrawer>
  </section>
}
