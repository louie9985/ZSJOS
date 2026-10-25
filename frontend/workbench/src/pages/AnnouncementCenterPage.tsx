import BusinessTable from '../components/BusinessTable'
import { Alert, Badge, Button, Empty, Grid, Input, List, Result, Skeleton, Space, Tabs, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type Announcement } from '../services/api'
import { formatTimestamp } from '../services/time'
import { useAnnouncements } from '../components/AnnouncementProvider'
import NoticeAttachments from '../components/NoticeAttachments'
import SafeRichText from '../components/SafeRichText'
import { useInboxTableLayout } from '../services/inboxLayout'

import ResizableDetailDrawer from '../components/ResizableDetailDrawer'
import NoticeManagementPage from './NoticeManagementPage'
import { noticePermission, noticeView, noticeAudienceText } from '../services/noticeManagement'

const CURSOR_LIMIT = 20

export function AnnouncementDetail({ item }: { item?: Announcement }) {
  if (!item) return <Empty description="请选择公告"/>
  return <article className="announcement-detail">
    <header>
      <Space wrap><Tag color="blue">{item.type === 1 ? '通知' : '公告'}</Tag>{!item.read && <Badge status="processing" text="未读"/>}</Space>
      <Typography.Title level={3}>{item.title}</Typography.Title>
      <Typography.Text type="secondary">发布于 {formatTimestamp(item.publishTime)} {item.highlighted && <Tag color="gold">高亮中</Tag>}</Typography.Text>
      <div className="notice-origin-meta">
        <span>文章来源：{item.sourceDeptName || '未记录'}</span>
        <span>发布人：{item.publisherName || '未记录'}</span>
        <span>接收部门/人员：{noticeAudienceText(item)}</span>
      </div>
    </header>
    <SafeRichText announcementTables html={item.content || ''}/>
    {item.attachments.length > 0 && <section className="announcement-files">
      <Typography.Title level={5}>附件</Typography.Title>
      <NoticeAttachments key={item.id} files={item.attachments} reload={async () => (await api.announcement(item.id)).attachments} />
    </section>}
  </article>
}

function announcementText(content?: string) {
  if (!content) return '-'
  if (typeof DOMParser === 'undefined') return content
  return new DOMParser().parseFromString(content, 'text/html').body.textContent?.trim() || '-'
}

export function DisplayedAnnouncement({ item, onRead }: { item: Announcement; onRead: (id: number) => void }) {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const root = useRef<HTMLDivElement>(null)
  const onReadRef = useRef(onRead)
  onReadRef.current = onRead
  useEffect(() => {
    if (item.read) return
    let cancelled = false
    let started = false
    let painted = false
    const acknowledge = async () => {
      if (!painted || started || cancelled || document.visibilityState !== 'visible' || !root.current?.getClientRects().length) return
      started = true; setBusy(true); setError('')
      try {
        await api.markAnnouncementRead(item.id)
        if (!cancelled) onReadRef.current(item.id)
      } catch (cause) {
        if (!cancelled) setError(cause instanceof Error ? '已读记录失败：' + cause.message : '已读记录失败，请重试')
      } finally { if (!cancelled) setBusy(false) }
    }
    // Two frames allow the committed detail to be painted; hidden/preloaded details do not acknowledge.
    let secondFrame = 0
    const frame = requestAnimationFrame(() => { secondFrame = requestAnimationFrame(() => { painted = true; void acknowledge() }) })
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) void acknowledge() })
    if (root.current) observer.observe(root.current)
    const visible = () => { if (document.visibilityState === 'visible') void acknowledge() }
    document.addEventListener('visibilitychange', visible)
    return () => { cancelled = true; cancelAnimationFrame(frame); cancelAnimationFrame(secondFrame); observer.disconnect(); document.removeEventListener('visibilitychange', visible) }
  }, [item.id, item.read, attempt])
  return <div ref={root}>
    {error && <Alert type="warning" showIcon title={error} action={<Button loading={busy} onClick={() => setAttempt(value => value + 1)}>重试记录</Button>} />}
    <AnnouncementDetail item={item} />
  </div>
}

export default function AnnouncementCenterPage({ permissions }: { permissions: string[] }) {
  const [params, setParams] = useSearchParams()
  const view = noticeView(permissions, params.get('view'), params.has('announcementId'))
  if (view === 'denied') return <Result status="403" title="无权查看公告" subTitle="请联系管理员配置相应的公告权限" />
  const tabs = [
    ...(noticePermission(permissions, 'read') ? [{ key: 'mine', label: '我的公告' }] : []),
    ...(noticePermission(permissions, 'query') ? [{ key: 'manage', label: '公告管理' }] : [])
  ]
  return <section className={`workspace-page announcement-center${view === 'mine' ? ' announcement-center-reader' : ''}`}>
    <Tabs activeKey={view} items={tabs} onChange={key => setParams(key === 'manage' ? { view: 'manage' } : {})} />
    {view === 'mine' ? <MyAnnouncements /> : <NoticeManagementPage permissions={permissions} />}
  </section>
}

function MyAnnouncements() {
  const screens = Grid.useBreakpoint()
  const [searchParams, setSearchParams] = useSearchParams()
  const { refresh: refreshSummary, confirmRead } = useAnnouncements()
  const [items, setItems] = useState<Announcement[]>([])
  const [tablePage, setTablePage] = useState(1)
  const [tablePageSize, setTablePageSize] = useState(CURSOR_LIMIT)
  const [tableTotal, setTableTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [searchText, setSearchText] = useState('')
  const [selected, setSelected] = useState<Announcement>()
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [selectedId, setSelectedId] = useState<number>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const loadMoreRef = useRef<HTMLDivElement>(null)
  const cursorRef = useRef<string | undefined>(undefined)
  const { useTableLayout } = useInboxTableLayout()
  const requestedAnnouncementId = searchParams.get('announcementId')
  const paramsRef = useRef(searchParams)
  const detailGeneration = useRef(0)
  const listGeneration = useRef(0)
  const listBusy = useRef(false)
  const activeDetailId = useRef<number | undefined>(undefined)
  const detailPaneRef = useRef<HTMLElement>(null)
  const detailOptionsRef = useRef({ screens, useTableLayout, setSearchParams })
  detailOptionsRef.current = { screens, useTableLayout, setSearchParams }
  paramsRef.current = searchParams

  const openDetail = useCallback(async (id: number, mobile?: boolean) => {
    const options = detailOptionsRef.current
    const request = ++detailGeneration.current
    activeDetailId.current = id
    setSelectedId(id)
    setDetailError('')
    setDetailLoading(true)
    setSelected(undefined)
    const desktop = options.screens.md ?? window.matchMedia('(min-width: 768px)').matches
    setDrawerOpen(mobile ?? (options.useTableLayout || !desktop))
    detailPaneRef.current?.scrollTo({ top: 0 })
    if (paramsRef.current.get('announcementId') !== String(id)) {
      const nextParams = new URLSearchParams(paramsRef.current)
      nextParams.set('announcementId', String(id))
      options.setSearchParams(nextParams, { replace: true })
    }
    try {
      const detail = await api.announcement(id)
      if (request !== detailGeneration.current) return
      setSelected(detail)
    } catch (loadError) {
      if (request === detailGeneration.current) setDetailError(loadError instanceof Error ? loadError.message : '公告详情加载失败')
    } finally { if (request === detailGeneration.current) setDetailLoading(false) }
  }, [])
  const onRead = useCallback((id: number) => {
    confirmRead(id)
    setSelected(current => current?.id === id ? { ...current, read: true } : current)
    setItems(current => current.map(item => item.id === id ? { ...item, read: true } : item))
    void refreshSummary()
  }, [confirmRead, refreshSummary])
  useEffect(() => () => { detailGeneration.current++; listGeneration.current++ }, [])

  // URL changes select only the detail; they must not reset the cursor or list scroll.
  useEffect(() => {
    const id = Number(requestedAnnouncementId)
    if (Number.isFinite(id) && id > 0 && activeDetailId.current !== id) void openDetail(id)
  }, [requestedAnnouncementId, openDetail])

  const load = useCallback(async (append = false) => {
    if (append && listBusy.current) return
    const request = ++listGeneration.current
    listBusy.current = true
    setError('')
    if (append) setLoadingMore(true)
    else { setLoading(true); setLoadingMore(false); cursorRef.current = undefined; setHasMore(true) }
    try {
      const data = useTableLayout
        ? await api.announcementPage({ pageNo: tablePage, pageSize: tablePageSize, keyword: keyword || undefined })
        : await api.announcementCursor({ ...(append && cursorRef.current ? { cursor: cursorRef.current } : {}), limit: CURSOR_LIMIT, keyword: keyword || undefined })
      if (request !== listGeneration.current) return
      setTableTotal('total' in data ? data.total : 0)
      setItems(current => useTableLayout ? data.list : append ? [...current, ...data.list.filter(item => !current.some(existing => existing.id === item.id))] : data.list)
      if ('hasMore' in data) { cursorRef.current = data.nextCursor; setHasMore(data.hasMore) }
      setError('')
      if (!append) {
        const options = detailOptionsRef.current
        const desktop = options.screens.md ?? window.matchMedia('(min-width: 768px)').matches
        if (!activeDetailId.current && desktop && !useTableLayout && data.list[0]) void openDetail(data.list[0].id, false)
      }
    } catch (loadError) {
      if (request === listGeneration.current) setError(loadError instanceof Error ? loadError.message : '公告加载失败')
    } finally {
      if (request === listGeneration.current) { listBusy.current = false; setLoading(false); setLoadingMore(false) }
    }
  }, [keyword, openDetail, tablePage, tablePageSize, useTableLayout])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    const node = loadMoreRef.current
    if (useTableLayout || !node || !hasMore || loading || loadingMore || error) return
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) void load(true) }, { root: node.parentElement, rootMargin: '160px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasMore, load, loading, loadingMore, useTableLayout, error])

  const detail = detailLoading ? <Skeleton active/> : detailError ? <Alert type="error" showIcon title={detailError} action={<Button onClick={() => selectedId && void openDetail(selectedId)}>重试</Button>}/> : selected ? <DisplayedAnnouncement key={selected.id} item={selected} onRead={onRead} /> : <AnnouncementDetail />

  return <section className={`announcement-page${useTableLayout ? ' announcement-table-page' : ''}`}>
    <div className="page-heading">
      <Typography.Title level={4}>通知公告</Typography.Title>
      <Space wrap><Input.Search allowClear value={searchText} placeholder="搜索标题、来源部门或发布人" onSearch={value => { setKeyword(value); setTablePage(1) }} onChange={event => { setSearchText(event.target.value); if (!event.target.value) { setKeyword(''); setTablePage(1) } }} style={{ width: 260 }}/><Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button></Space>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    {useTableLayout ? <>
    <BusinessTable<Announcement> tableKey="announcement-center-page-1"
      className="announcement-table"
      rowKey="id"

      columnsState={{ persistenceKey: 'crm-announcement-table-columns', persistenceType: 'localStorage' }}
      loading={loading}
      dataSource={items}
      pagination={{ current: tablePage, pageSize: tablePageSize, total: tableTotal, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showQuickJumper: true, onChange: (page, size) => { setTablePage(page); setTablePageSize(size) } }}
      scroll={{ x: 1900 }}
      locale={{ emptyText: <Empty description="暂无公告" /> }}
      columns={[
        { title: '标题', dataIndex: 'title' },
        { title: '类型', render: (_, item) => item.type === 1 ? '通知' : '公告', width: 100 },
        { title: '正文', dataIndex: 'content', width: 360, ellipsis: true, render: (_, item) => announcementText(item.content) },
        { title: '高亮状态', dataIndex: 'highlighted', width: 110, render: (_, item) => item.highlighted ? <Tag color="gold">高亮中</Tag> : '普通' },
        { title: '高亮截止时间', dataIndex: 'highlightUntil', render: (_, item) => formatTimestamp(item.highlightUntil), width: 170 },
        { title: '文章来源', dataIndex: 'sourceDeptName', width: 160, render: value => value || '未记录' },
        { title: '发布人', dataIndex: 'publisherName', width: 110, render: value => value || '未记录' },
        { title: '接收部门/人员', width: 230, ellipsis: true, render: (_, item) => noticeAudienceText(item) },
        { title: '发布时间', dataIndex: 'publishTime', render: (_, item) => formatTimestamp(item.publishTime), width: 170 },
        { title: '阅读状态', render: (_, item) => item.read ? '已读' : <Tag color="processing">未读</Tag>, width: 100 },
        { title: '阅读时间', dataIndex: 'readTime', render: (_, item) => formatTimestamp(item.readTime), width: 170 },
        { title: '附件数量', width: 100, render: (_, item) => `${item.attachments.length} 个` },
        { title: '附件', width: 280, ellipsis: true, render: (_, item) => item.attachments.map(file => file.fileName).join('；') || '-' },
        { key: 'action', title: '操作', width: 88, fixed: 'right', hideInSetting: true, render: (_, item) => <Button type="link" onClick={() => void openDetail(item.id, true)}>详细</Button> }
      ]}
    />
    </> : <div className="announcement-layout">
      <aside className="announcement-list-pane">
        {loading ? <Skeleton active paragraph={{ rows: 8 }}/> : items.length === 0 ? <Empty description="暂无公告"/> : <>
          <List dataSource={items} renderItem={item => <button type="button" className={`announcement-list-item${selectedId === item.id ? ' active' : ''}${item.read ? '' : ' unread'}`} onClick={() => void openDetail(item.id)}>
            <span className="announcement-list-title">{item.highlighted && <Tag color="gold">高亮</Tag>}{item.title}</span>
            <span className="notice-origin-meta">
              <span>文章来源：{item.sourceDeptName || '未记录'}</span><span>发布人：{item.publisherName || '未记录'}</span>
              <span>接收部门/人员：{noticeAudienceText(item)}</span>
            </span>
            <span className="announcement-list-meta"><Badge status={item.read ? 'default' : 'processing'}/>{formatTimestamp(item.publishTime)}</span>
          </button>}/>
          <div ref={loadMoreRef} className="announcement-load-more">
            {loadingMore ? '正在加载更多' : error ? <Button type="text" size="small" icon={<ReloadOutlined/>} onClick={() => void load(true)}>重试加载</Button> : hasMore ? '继续下滑加载' : items.length > 0 ? '已加载全部公告' : null}
          </div>
        </>}
      </aside>
      <main ref={detailPaneRef} className="announcement-detail-pane">{screens.md && !drawerOpen ? detail : null}</main>
    </div>}
    <ResizableDetailDrawer desktopResizable={useTableLayout} className="announcement-mobile-drawer" title="公告详情" placement={useTableLayout ? 'right' : 'bottom'} height={useTableLayout ? undefined : '82vh'} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
      {drawerOpen ? detail : null}
    </ResizableDetailDrawer>
  </section>
}
