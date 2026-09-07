import { Alert, Badge, Button, Empty, Grid, Input, List, Skeleton, Space, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type Announcement } from '../services/api'
import { formatTimestamp } from '../services/time'
import { useAnnouncements } from '../components/AnnouncementProvider'
import AnnouncementAttachmentIcon from '../components/AnnouncementAttachmentIcon'
import SafeRichText from '../components/SafeRichText'
import { useInboxTableLayout } from '../services/inboxLayout'
import { ProTable } from '@ant-design/pro-components'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'

const CURSOR_LIMIT = 20

function AnnouncementDetail({ item }: { item?: Announcement }) {
  if (!item) return <Empty description="请选择公告"/>
  return <article className="announcement-detail">
    <header>
      <Space wrap><Tag color="blue">{item.type === 1 ? '通知' : '公告'}</Tag>{!item.read && <Badge status="processing" text="未读"/>}</Space>
      <Typography.Title level={3}>{item.title}</Typography.Title>
      <Typography.Text type="secondary">发布于 {formatTimestamp(item.publishTime)} {item.highlighted && <Tag color="gold">高亮中</Tag>}</Typography.Text>
    </header>
    <SafeRichText html={item.content || ''}/>
    {item.attachments.length > 0 && <section className="announcement-files">
      <Typography.Title level={5}>附件</Typography.Title>
      {item.attachments.map(file => file.downloadUrl
        ? <a key={file.infraFileId} href={file.downloadUrl} target="_blank" rel="noopener noreferrer" className="announcement-file">
          <AnnouncementAttachmentIcon name={file.fileName} mimeType={file.mimeType}/><span>{file.fileName}</span><small>{formatFileSize(file.fileSize)}</small>
        </a>
        : <div key={file.infraFileId} className="announcement-file unavailable">
          <AnnouncementAttachmentIcon name={file.fileName} mimeType={file.mimeType}/><span>{file.fileName}</span><small>文件不可用</small>
        </div>)}
    </section>}
  </article>
}

const formatFileSize = (size: number) => size >= 1024 * 1024
  ? `${(size / 1024 / 1024).toFixed(1)} MB`
  : `${Math.max(1, Math.round(size / 1024))} KB`

function announcementText(content?: string) {
  if (!content) return '-'
  if (typeof DOMParser === 'undefined') return content
  return new DOMParser().parseFromString(content, 'text/html').body.textContent?.trim() || '-'
}

export default function AnnouncementCenterPage() {
  const screens = Grid.useBreakpoint()
  const [searchParams, setSearchParams] = useSearchParams()
  const { refresh: refreshSummary } = useAnnouncements()
  const [items, setItems] = useState<Announcement[]>([])
  const [tablePage, setTablePage] = useState(1)
  const [tablePageSize, setTablePageSize] = useState(CURSOR_LIMIT)
  const [tableTotal, setTableTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState<Announcement>()
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const loadMoreRef = useRef<HTMLDivElement>(null)
  const cursorRef = useRef<string | undefined>(undefined)
  const { useTableLayout } = useInboxTableLayout()

  const openDetail = useCallback(async (id: number, mobile = useTableLayout || !screens.md) => {
    setDetailLoading(true)
    try {
      const detail = await api.announcement(id)
      if (!detail.read) await api.markAnnouncementRead(id)
      setSelected({ ...detail, read: true })
      setItems(current => current.map(item => item.id === id ? { ...item, read: true } : item))
      await refreshSummary()
      setSearchParams({ announcementId: String(id) }, { replace: true })
      if (mobile) setDrawerOpen(true)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '公告详情加载失败')
    } finally { setDetailLoading(false) }
  }, [refreshSummary, screens.md, setSearchParams, useTableLayout])

  const load = useCallback(async (append = false) => {
    if (append) setLoadingMore(true)
    else { setLoading(true); cursorRef.current = undefined; setHasMore(true) }
    try {
      const data = useTableLayout
        ? await api.announcementPage({ pageNo: tablePage, pageSize: tablePageSize, keyword: keyword || undefined })
        : await api.announcementCursor({ ...(append && cursorRef.current ? { cursor: cursorRef.current } : {}), limit: CURSOR_LIMIT, keyword: keyword || undefined })
      setTableTotal('total' in data ? data.total : 0)
      setItems(current => useTableLayout ? data.list : append ? [...current, ...data.list.filter(item => !current.some(existing => existing.id === item.id))] : data.list)
      if ('nextCursor' in data) { cursorRef.current = data.nextCursor; setHasMore(data.hasMore) }
      setError('')
      if (!append) {
        const requestedId = Number(searchParams.get('announcementId'))
        const targetId = Number.isFinite(requestedId) && requestedId > 0 ? requestedId : data.list[0]?.id
        if (targetId) await openDetail(targetId, false)
        else setSelected(undefined)
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '公告加载失败')
    } finally { setLoading(false); setLoadingMore(false) }
  }, [keyword, openDetail, searchParams, tablePage, tablePageSize, useTableLayout])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    const node = loadMoreRef.current
    if (useTableLayout || !node || !hasMore || loading || loadingMore) return
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) void load(true) }, { root: node.parentElement, rootMargin: '160px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasMore, load, loading, loadingMore, useTableLayout])

  return <section className={`workspace-page announcement-page${useTableLayout ? ' announcement-table-page' : ''}`}>
    <div className="page-heading">
      <Typography.Title level={4}>通知公告</Typography.Title>
      <Space><Input.Search allowClear value={keyword} placeholder="搜索公告标题或正文" onSearch={value => { setKeyword(value); setTablePage(1) }} onChange={event => { if (!event.target.value) { setKeyword(''); setTablePage(1) } }} style={{ width: 260 }}/><Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button></Space>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    {useTableLayout ? <>
    <ProTable<Announcement>
      className="announcement-table"
      rowKey="id"
      search={false}
      options={{ density: true, fullScreen: true, setting: true }}
      columnsState={{ persistenceKey: 'crm-announcement-table-columns', persistenceType: 'localStorage' }}
      loading={loading}
      dataSource={items}
      pagination={{ current: tablePage, pageSize: tablePageSize, total: tableTotal, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showQuickJumper: true, onChange: (page, size) => { setTablePage(page); setTablePageSize(size) } }}
      scroll={{ x: 1900 }}
      locale={{ emptyText: <Empty description="暂无公告" /> }}
      columns={[
        { title: '标题', dataIndex: 'title' },
        { title: '类型', render: (_, item) => item.type === 1 ? '通知' : '公告', width: 100 },
        { title: '正文', dataIndex: 'content', width: 360, ellipsis: true, render: value => announcementText(value as string | undefined) },
        { title: '高亮状态', dataIndex: 'highlighted', width: 110, render: value => value ? <Tag color="gold">高亮中</Tag> : '普通' },
        { title: '高亮截止时间', dataIndex: 'highlightUntil', render: (_, item) => formatTimestamp(item.highlightUntil), width: 170 },
        { title: '发布时间', dataIndex: 'publishTime', render: (_, item) => formatTimestamp(item.publishTime), width: 170 },
        { title: '阅读状态', render: (_, item) => item.read ? '已读' : <Tag color="processing">未读</Tag>, width: 100 },
        { title: '阅读时间', dataIndex: 'readTime', render: (_, item) => formatTimestamp(item.readTime), width: 170 },
        { title: '附件数量', width: 100, render: (_, item) => `${item.attachments.length} 个` },
        { title: '附件', width: 280, ellipsis: true, render: (_, item) => item.attachments.map(file => file.fileName).join('；') || '-' },
        { title: '操作', width: 88, fixed: 'right', hideInSetting: true, render: (_, item) => <Button type="link" onClick={() => void openDetail(item.id, true)}>详细</Button> }
      ]}
    />
    </> : <div className="announcement-layout">
      <aside className="announcement-list-pane">
        {loading ? <Skeleton active paragraph={{ rows: 8 }}/> : items.length === 0 ? <Empty description="暂无公告"/> : <>
          <List dataSource={items} renderItem={item => <button type="button" className={`announcement-list-item${selected?.id === item.id ? ' active' : ''}${item.read ? '' : ' unread'}`} onClick={() => void openDetail(item.id)}>
            <span className="announcement-list-title">{item.highlighted && <Tag color="gold">高亮</Tag>}{item.title}</span>
            <span className="announcement-list-meta"><Badge status={item.read ? 'default' : 'processing'}/>{formatTimestamp(item.publishTime)}</span>
          </button>}/>
          <div ref={loadMoreRef} className="announcement-load-more">
            {loadingMore ? '正在加载更多' : error ? <Button type="text" size="small" icon={<ReloadOutlined/>} onClick={() => void load(true)}>重试加载</Button> : hasMore ? '继续下滑加载' : items.length > 0 ? '已加载全部公告' : null}
          </div>
        </>}
      </aside>
      <main className="announcement-detail-pane">{detailLoading ? <Skeleton active/> : <AnnouncementDetail item={selected}/>}</main>
    </div>}
    <ResizableDetailDrawer desktopResizable={useTableLayout} className="announcement-mobile-drawer" title="公告详情" placement={useTableLayout ? 'right' : 'bottom'} height={useTableLayout ? undefined : '82vh'} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
      {detailLoading ? <Skeleton active/> : <AnnouncementDetail item={selected}/>}
    </ResizableDetailDrawer>
  </section>
}
