import { Alert, App, Button, Input, Modal, Select, Space, Spin } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import BusinessTable from '../components/BusinessTable'
import DateTimeText from '../components/DateTimeText'
import NoticeEditorDialog from '../components/NoticeEditorDialog'
import NoticeManagementDetail from '../components/NoticeManagementDetail'
import { ApiError } from '../services/api'
import { noticeActions, noticeManagement, noticePermission, NOTICE_STATUSES, type ManagedNotice, type NoticeStatus } from '../services/noticeManagement'

export default function NoticeManagementPage({ permissions }: { permissions: string[] }) {
  const { message, modal } = App.useApp()
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
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const generation = useRef(0)
  const detailGeneration = useRef(0)
  const allowed = noticePermission(permissions, 'query')
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
      filters={<Space wrap><Input.Search allowClear placeholder="搜索公告标题" onSearch={title => setQuery(current => ({ ...current, pageNo: 1, title: title || undefined }))} />
        <Select allowClear placeholder="全部发布状态" style={{ width: 160 }} value={query.publishStatus} options={Object.entries(NOTICE_STATUSES).map(([value, label]) => ({ value, label }))} onChange={publishStatus => setQuery(current => ({ ...current, pageNo: 1, publishStatus }))} /></Space>}
      actions={noticePermission(permissions, 'create') && <Button type="primary" onClick={() => setEditor({})}>新建公告</Button>}
      pagination={{ current: query.pageNo, pageSize: query.pageSize, total, showSizeChanger: true, onChange: (pageNo, pageSize) => setQuery(current => ({ ...current, pageNo: current.pageSize === pageSize ? pageNo : 1, pageSize })) }} scroll={{ x: 1100 }}
      columns={[
        { title: '公告标题', dataIndex: 'title', width: 260 },
        { title: '发布状态', dataIndex: 'publishStatus', render: (_, row) => NOTICE_STATUSES[row.publishStatus] },
        { title: '附件数量', key: 'attachments', render: (_, row) => row.attachments?.length || 0 },
        { title: '发布时间', dataIndex: 'publishTime', render: (_, row) => <DateTimeText value={row.publishTime} /> },
        { title: '高亮截止时间', dataIndex: 'highlightUntil', render: (_, row) => <DateTimeText value={row.highlightUntil} /> },
        { title: '操作', key: 'action', width: 300, fixed: 'right', hideInSetting: true, render: (_, row) => { const actions = noticeActions(permissions, row.publishStatus); return <Space wrap size={0}>
          <Button type="link" onClick={() => void view(row.id)}>查看</Button>
          {actions.edit && <Button type="link" disabled={busy} onClick={() => void act(row, 'edit')}>编辑</Button>}
          {actions.publish && <Button type="link" disabled={busy} onClick={() => void act(row, 'publish')}>发布</Button>}
          {actions.offline && <Button type="link" disabled={busy} onClick={() => void act(row, 'offline')}>下线</Button>}
          {actions.copy && <Button type="link" disabled={busy} onClick={() => void act(row, 'copy')}>复制草稿</Button>}
          {actions.delete && <Button type="link" danger disabled={busy} onClick={() => void act(row, 'delete')}>删除</Button>}
        </Space> } }
      ]} />
    <Modal open={detailId != null} title="公告详情" width={800} footer={null} onCancel={() => { detailGeneration.current++; setDetailId(undefined); setDetail(undefined) }}>
      {detailLoading ? <Spin /> : detailError ? <Alert type="error" title={detailError} action={<Button onClick={() => detailId != null && void view(detailId)}>重试</Button>} /> : detail && <NoticeManagementDetail notice={detail} />}
    </Modal>
    {editor && <NoticeEditorDialog initial={editor.initial} permissions={permissions} onClose={() => setEditor(undefined)} onChanged={() => void load()} />}
  </>
}
