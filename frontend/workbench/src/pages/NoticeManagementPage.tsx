import { noticeAudienceText } from '../services/noticeManagement'
import { Alert, App, Button, Input, Modal, Select, Space, Spin } from 'antd'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import BusinessTable from '../components/BusinessTable'
import DateTimeText from '../components/DateTimeText'
import NoticeEditorDialog from '../components/NoticeEditorDialog'
import NoticeManagementDetail from '../components/NoticeManagementDetail'
import NoticeShareDialog from '../components/NoticeShareDialog'
import { ApiError } from '../services/api'
import { noticeActions, noticeManagement, noticePermission, NOTICE_STATUSES, type ManagedNotice, type NoticeStatus } from '../services/noticeManagement'

const ROW_ACTIONS = [
  { key: 'view', label: '查看' },
  { key: 'share', label: '对外分享' },
  { key: 'edit', label: '编辑' },
  { key: 'publish', label: '发布' },
  { key: 'offline', label: '下线' },
  { key: 'copy', label: '复制草稿' },
  { key: 'delete', label: '删除' },
] as const

export default function NoticeManagementPage({ permissions }: { permissions: string[] }) {
  const { message, modal } = App.useApp()
  const [actionWidth, setActionWidth] = useState(80)
  const actionGroups = useRef(new Map<number, HTMLSpanElement>())
  const [rows, setRows] = useState<ManagedNotice[]>([])
  const [total, setTotal] = useState(0)
  const [query, setQuery] = useState<{ pageNo: number; pageSize: number; title?: string; publishStatus?: NoticeStatus }>({ pageNo: 1, pageSize: 20 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [unauthorized, setUnauthorized] = useState(false)
  const [detail, setDetail] = useState<ManagedNotice>()
  const [detailId, setDetailId] = useState<number>()
  const [detailError, setDetailError] = useState('')
  const [detailLoading, setDetailLoading] = useState(false)
  const [editor, setEditor] = useState<{ initial?: ManagedNotice }>()
  const [sharing, setSharing] = useState<ManagedNotice>()
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const generation = useRef(0)
  const detailGeneration = useRef(0)
  const allowed = noticePermission(permissions, 'query')
  const canShare = allowed && noticePermission(permissions, 'share')
  const rowActions = (row: ManagedNotice) => {
    const available = { ...noticeActions(permissions, row.publishStatus), view: allowed, share: canShare && row.publishStatus === 'PUBLISHED' }
    return ROW_ACTIONS.filter(action => available[action.key])
  }
  useLayoutEffect(() => {
    // Intrinsic group widths include themed button padding/borders; cell width must not constrain them.
    const measure = () => {
      const widths = [...actionGroups.current.values()].map(group => {
        const cell = group.closest('td')
        if (!cell) return 0
        const style = getComputedStyle(cell)
        return Math.ceil(group.getBoundingClientRect().width + parseFloat(style.paddingLeft) + parseFloat(style.paddingRight))
      })
      setActionWidth(Math.max(80, ...widths))
    }
    measure()
    const observer = new ResizeObserver(measure)
    actionGroups.current.forEach(group => {
      observer.observe(group)
      const cell = group.closest('td')
      if (cell) observer.observe(cell)
    })
    return () => observer.disconnect()
  })
  const load = useCallback(async () => {
    const request = ++generation.current
    if (!allowed) { setRows([]); setLoading(false); setUnauthorized(true); return }
    setLoading(true)
    try { const data = await noticeManagement.page(query); if (request !== generation.current) return; setRows(data.list); setTotal(data.total); setError(''); setUnauthorized(false) }
    catch (cause) { if (request !== generation.current) return; setRows([]); setError(cause instanceof Error ? cause.message : '公告管理加载失败'); setUnauthorized(cause instanceof ApiError && cause.code === 403) }
    finally { if (request === generation.current) setLoading(false) }
  }, [allowed, query])
  useEffect(() => { void load(); return () => { generation.current += 1 } }, [load])
  const view = async (id: number) => {
    const request = ++detailGeneration.current
    setDetailId(id); setDetail(undefined); setDetailError(''); setDetailLoading(true)
    try { const item = await noticeManagement.get(id); if (request === detailGeneration.current) setDetail(item) }
    catch (cause) { if (request === detailGeneration.current) setDetailError(cause instanceof Error ? cause.message : '公告详情加载失败') }
    finally { if (request === detailGeneration.current) setDetailLoading(false) }
  }
  const openShare = async (row: ManagedNotice) => {
    if (busyRef.current || !canShare || row.publishStatus !== 'PUBLISHED') return
    busyRef.current = true; setBusy(true)
    try {
      const item = await noticeManagement.get(row.id)
      if (item.publishStatus !== 'PUBLISHED') { void load(); throw new Error('公告状态已变化，只有已发布公告可以对外分享') }
      setSharing(item)
    } catch (cause) { void message.error(cause instanceof Error ? cause.message : '公告加载失败，请重试') }
    finally { busyRef.current = false; setBusy(false) }
  }
  const act = async (row: ManagedNotice, action: 'edit' | 'publish' | 'offline' | 'copy' | 'delete') => {
    if (busyRef.current || !noticeActions(permissions, row.publishStatus)[action]) return
    busyRef.current = true; setBusy(true)
    try {
      if ((action === 'publish' || action === 'offline' || action === 'delete') && !await modal.confirm({ title: `确认${{ publish: '发布', offline: '下线', delete: '删除' }[action]}“${row.title}”？`, content: action === 'publish' ? '发布后内容不可直接修改。' : action === 'delete' ? '只删除这条草稿，删除后不可恢复。' : undefined, okText: '确认', cancelText: '取消' })) return
      if (action === 'edit') {
        const item = await noticeManagement.get(row.id)
        if (item.publishStatus !== 'DRAFT') throw new Error('公告状态已变化，只有草稿可以编辑')
        setEditor({ initial: item })
      } else if (action === 'copy') {
        const id = await noticeManagement.copy(row.id)
        void message.success('已复制为草稿')
        if (noticePermission(permissions, 'update')) setEditor({ initial: await noticeManagement.get(id) })
      } else { await noticeManagement[action](row.id); void message.success('操作成功') }
    } catch (cause) { void message.error(cause instanceof Error ? cause.message : '操作失败') }
    finally { busyRef.current = false; setBusy(false); void load() }
  }
  return <>
    <BusinessTable<ManagedNotice> tableKey="announcement-management" rowKey="id" loading={loading} dataSource={rows} error={error} unauthorized={unauthorized || !allowed} onReload={() => void load()}
      filters={<Space wrap><Input.Search allowClear placeholder="搜索标题、来源部门或发布人" onSearch={title => setQuery(current => ({ ...current, pageNo: 1, title: title || undefined }))} />
        <Select allowClear placeholder="全部发布状态" style={{ width: 160 }} value={query.publishStatus} options={Object.entries(NOTICE_STATUSES).map(([value, label]) => ({ value, label }))} onChange={publishStatus => setQuery(current => ({ ...current, pageNo: 1, publishStatus }))} /></Space>}
      actions={noticePermission(permissions, 'create') && <Button type="primary" onClick={() => setEditor({})}>新建公告</Button>}
      pagination={{ current: query.pageNo, pageSize: query.pageSize, total, showSizeChanger: true, onChange: (pageNo, pageSize) => setQuery(current => ({ ...current, pageNo: current.pageSize === pageSize ? pageNo : 1, pageSize })) }} scroll={{ x: 1100 }}
      columns={[
        { title: '公告标题', dataIndex: 'title', width: 260 },
        { title: '文章来源', dataIndex: 'sourceDeptName', width: 160, render: value => value || '未记录' },
        { title: '发布人', dataIndex: 'publisherName', width: 110, render: (value, row) => row.publishStatus === 'DRAFT' ? '待发布' : value || '未记录' },
        { title: '接收部门/人员', width: 230, ellipsis: true, render: (_, row) => noticeAudienceText(row) },
        { title: '发布状态', dataIndex: 'publishStatus', render: (_, row) => NOTICE_STATUSES[row.publishStatus] },
        { title: '附件数量', key: 'attachments', render: (_, row) => row.attachments?.length || 0 },
        { title: '发布时间', dataIndex: 'publishTime', render: (_, row) => <DateTimeText value={row.publishTime} /> },
        { title: '高亮截止时间', dataIndex: 'highlightUntil', render: (_, row) => <DateTimeText value={row.highlightUntil} /> },
        { title: '操作', key: 'action', width: actionWidth, fixed: 'right', hideInSetting: true, render: (_, row) => <span
          ref={element => { if (element) actionGroups.current.set(row.id, element); else actionGroups.current.delete(row.id) }}
          style={{ display: 'inline-block', width: 'max-content' }}><Space wrap={false} size={0}>
          {rowActions(row).map(({ key, label }) => <Button key={key} type="link" danger={key === 'delete'} disabled={key !== 'view' && busy}
            onClick={() => { if (key === 'view') void view(row.id); else if (key === 'share') void openShare(row); else void act(row, key) }}>{label}</Button>)}
        </Space></span> }
      ]} />
    <Modal open={detailId != null} title="公告详情" width={800} footer={null} onCancel={() => { detailGeneration.current++; setDetailId(undefined); setDetail(undefined) }}>
      {detailLoading ? <Spin /> : detailError ? <Alert type="error" title={detailError} action={<Button onClick={() => detailId != null && void view(detailId)}>重试</Button>} /> : detail && <NoticeManagementDetail notice={detail} />}
    </Modal>
    {sharing && canShare && <NoticeShareDialog notice={sharing} onClose={() => setSharing(undefined)} />}
    {editor && <NoticeEditorDialog initial={editor.initial} permissions={permissions} onClose={() => setEditor(undefined)} onChanged={() => void load()} />}
  </>
}
