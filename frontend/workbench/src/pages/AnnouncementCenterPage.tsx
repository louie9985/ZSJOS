import { Alert, Badge, Button, Empty, Grid, List, Pagination, Skeleton, Space, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type Announcement } from '../services/api'
import { formatTimestamp } from '../services/time'
import { useAnnouncements } from '../components/AnnouncementProvider'
import AnnouncementAttachmentIcon from '../components/AnnouncementAttachmentIcon'
import SafeRichText from '../components/SafeRichText'
import { useInboxTableLayout } from '../services/inboxLayout'
import { ProTable } from '@ant-design/pro-components'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'

const PAGE_SIZE = 20

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

  return <section className={`workspace-page announcement-page${useTableLayout ? ' announcement-table-page' : ''}`}>
    <div className="page-heading">
      <Typography.Title level={4}>通知公告</Typography.Title>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    {useTableLayout ? <ProTable<Announcement>
      className="announcement-table"
      rowKey="id"
      search={false}
      options={{ density: true, fullScreen: true, setting: true }}
      columnsState={{ persistenceKey: 'crm-announcement-table-columns', persistenceType: 'localStorage' }}
      loading={loading}
      dataSource={items}
      pagination={false}
      scroll={{ x: 760 }}
      locale={{ emptyText: <Empty description="暂无公告" /> }}
      columns={[
        { title: '标题', dataIndex: 'title' },
        { title: '类型', render: (_, item) => item.type === 1 ? '通知' : '公告', width: 100 },
        { title: '发布时间', dataIndex: 'publishTime', render: (_, item) => formatTimestamp(item.publishTime), width: 170 },
        { title: '阅读状态', render: (_, item) => item.read ? '已读' : <Tag color="processing">未读</Tag>, width: 100 },
        { title: '操作', width: 88, fixed: 'right', render: (_, item) => <Button type="link" onClick={() => void openDetail(item.id, true)}>详细</Button> }
      ]}
    /> : <div className="announcement-layout">
      <aside className="announcement-list-pane">
        {loading ? <Skeleton active paragraph={{ rows: 8 }}/> : items.length === 0 ? <Empty description="暂无公告"/> : <>
          <List dataSource={items} renderItem={item => <button type="button" className={`announcement-list-item${selected?.id === item.id ? ' active' : ''}${item.read ? '' : ' unread'}`} onClick={() => void openDetail(item.id)}>
            <span className="announcement-list-title">{item.highlighted && <Tag color="gold">高亮</Tag>}{item.title}</span>
            <span className="announcement-list-meta"><Badge status={item.read ? 'default' : 'processing'}/>{formatTimestamp(item.publishTime)}</span>
          </button>}/>
          <Pagination simple current={pageNo} pageSize={PAGE_SIZE} total={total} onChange={setPageNo}/>
        </>}
      </aside>
      <main className="announcement-detail-pane">{detailLoading ? <Skeleton active/> : <AnnouncementDetail item={selected}/>}</main>
    </div>}
    <ResizableDetailDrawer desktopResizable={useTableLayout} className="announcement-mobile-drawer" title="公告详情" placement={useTableLayout ? 'right' : 'bottom'} height={useTableLayout ? undefined : '82vh'} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
      {detailLoading ? <Skeleton active/> : <AnnouncementDetail item={selected}/>}
    </ResizableDetailDrawer>
  </section>
}
