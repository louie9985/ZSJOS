import { Alert, Badge, Button, Drawer, Empty, Grid, List, Pagination, Skeleton, Space, Tag, Typography } from 'antd'
import { FileOutlined, ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type Announcement } from '../services/api'
import { formatTimestamp } from '../services/time'
import { useAnnouncements } from '../components/AnnouncementProvider'
import SafeRichText from '../components/SafeRichText'

const PAGE_SIZE = 20

function AnnouncementDetail({ item }: { item?: Announcement }) {
  if (!item) return <Empty description="请选择公告"/>
  return <article className="announcement-detail">
    <header>
      <Space wrap><Tag color="blue">{item.type === 1 ? '通知' : '公告'}</Tag>{!item.read && <Badge status="processing" text="未读"/>}</Space>
      <Typography.Title level={3}>{item.title}</Typography.Title>
      <Typography.Text type="secondary">发布于 {formatTimestamp(item.publishTime)}</Typography.Text>
    </header>
    <SafeRichText html={item.content || ''}/>
    {item.attachments.length > 0 && <section className="announcement-files">
      <Typography.Title level={5}>附件</Typography.Title>
      {item.attachments.map(file => file.downloadUrl
        ? <a key={file.infraFileId} href={file.downloadUrl} target="_blank" rel="noopener noreferrer" className="announcement-file">
          <FileOutlined/><span>{file.fileName}</span><small>{formatFileSize(file.fileSize)}</small>
        </a>
        : <div key={file.infraFileId} className="announcement-file unavailable">
          <FileOutlined/><span>{file.fileName}</span><small>文件不可用</small>
        </div>)}
    </section>}
  </article>
}

const formatFileSize = (size: number) => size >= 1024 * 1024
  ? `${(size / 1024 / 1024).toFixed(1)} MB`
  : `${Math.max(1, Math.round(size / 1024))} KB`

export default function AnnouncementCenterPage() {
  const screens = Grid.useBreakpoint()
  const [searchParams, setSearchParams] = useSearchParams()
  const { refresh: refreshSummary } = useAnnouncements()
  const [items, setItems] = useState<Announcement[]>([])
  const [selected, setSelected] = useState<Announcement>()
  const [pageNo, setPageNo] = useState(1)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)

  const openDetail = useCallback(async (id: number, mobile = !screens.md) => {
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
  }, [refreshSummary, screens.md, setSearchParams])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await api.announcementPage({ pageNo, pageSize: PAGE_SIZE })
      setItems(data.list)
      setTotal(data.total)
      setError('')
      const requestedId = Number(searchParams.get('announcementId'))
      const targetId = Number.isFinite(requestedId) && requestedId > 0 ? requestedId : data.list[0]?.id
      if (targetId) await openDetail(targetId, false)
      else setSelected(undefined)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '公告加载失败')
    } finally { setLoading(false) }
  }, [openDetail, pageNo, searchParams])

  useEffect(() => { void load() }, [pageNo])

  return <section className="workspace-page announcement-page">
    <div className="page-heading">
      <Typography.Title level={4}>通知公告</Typography.Title>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    <div className="announcement-layout">
      <aside className="announcement-list-pane">
        {loading ? <Skeleton active paragraph={{ rows: 8 }}/> : items.length === 0 ? <Empty description="暂无公告"/> : <>
          <List dataSource={items} renderItem={item => <button type="button" className={`announcement-list-item${selected?.id === item.id ? ' active' : ''}${item.read ? '' : ' unread'}`} onClick={() => void openDetail(item.id)}>
            <span className="announcement-list-title">{item.title}</span>
            <span className="announcement-list-meta"><Badge status={item.read ? 'default' : 'processing'}/>{formatTimestamp(item.publishTime)}</span>
          </button>}/>
          <Pagination simple current={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo}/>
        </>}
      </aside>
      <main className="announcement-detail-pane">{detailLoading ? <Skeleton active/> : <AnnouncementDetail item={selected}/>}</main>
    </div>
    <Drawer className="announcement-mobile-drawer" title="公告详情" placement="bottom" height="82vh" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
      {detailLoading ? <Skeleton active/> : <AnnouncementDetail item={selected}/>}
    </Drawer>
  </section>
}
