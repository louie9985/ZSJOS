import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, App, Button, Input, Modal, Space, Tabs, Tag } from 'antd'
import BusinessTable from '../components/BusinessTable'
import DateTimeText from '../components/DateTimeText'
import VersionEditor from '../components/versionUpdates/VersionEditor'
import VersionContent from '../components/versionUpdates/VersionContent'
import VersionRecipients from '../components/versionUpdates/VersionRecipients'
import { versionUpdates, VERSION_UPDATE_PERMISSION, type VersionUpdate } from '../services/versionUpdates'
import { hasPermission } from '../services/managementAccess'
import { useBusinessOverlay } from '../components/OverlayCoordinator'
import '../components/versionUpdates/version-updates.css'

export default function VersionUpdatesPage({ permissions }: { permissions: string[] }) {
  const { modal, message } = App.useApp()
  const [rows, setRows] = useState<VersionUpdate[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [editor, setEditor] = useState<VersionUpdate | 'new'>()
  const [detail, setDetail] = useState<VersionUpdate>()
  const [busy, setBusy] = useState(false)
  const request = useRef(0)
  const command = useRef(false)
  const can = (action: string) => hasPermission(permissions, VERSION_UPDATE_PERMISSION + action)
  useBusinessOverlay(!!detail)
  const load = useCallback(async () => {
    const current = ++request.current
    setLoading(true); setError('')
    try { const result = await versionUpdates.page({ pageNo: page, pageSize: 20, keyword }); if (current === request.current) { setRows(result.list); setTotal(result.total) } }
    catch (cause) { if (current === request.current) setError(cause instanceof Error ? cause.message : '版本更新加载失败') }
    finally { if (current === request.current) setLoading(false) }
  }, [page, keyword])
  useEffect(() => { void load(); return () => { request.current++ } }, [load])
  const open = async (id: number, edit = false) => {
    if (command.current) return
    command.current = true; setBusy(true); setError('')
    try { const result = await versionUpdates.get(id); if (edit) setEditor(result); else setDetail(result) }
    catch (cause) { setError(cause instanceof Error ? cause.message : '详情加载失败') }
    finally { command.current = false; setBusy(false) }
  }
  const transition = async (row: VersionUpdate, action: 'publish' | 'offline') => {
    if (command.current) return
    command.current = true; setBusy(true)
    try {
      if (!await modal.confirm({ title: action === 'publish' ? '发布此版本更新？' : '下线此版本更新？', content: action === 'publish' ? '发布后固定接收名单和正文，并向接收人员弹窗。' : '下线后停止弹窗，并从接收人员的更新记录中撤下。', okText: '确定', cancelText: '取消' })) return
      await versionUpdates[action](row.id); void message.success(action === 'publish' ? '版本更新已发布' : '版本更新已下线'); await load()
    } catch (cause) { setError(cause instanceof Error ? cause.message : '操作失败') }
    finally { command.current = false; setBusy(false) }
  }
  return <section className="version-update-page">
    {error && <Alert type="error" showIcon title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    <BusinessTable<VersionUpdate> tableKey="version-updates" rowKey="id" scroll={{ x: 1000 }} dataSource={rows} loading={loading}
      headerTitle="版本更新发布" actions={can('create') ? <Button type="primary" onClick={() => setEditor('new')}>新建版本更新</Button> : undefined}
      filters={<Input.Search allowClear placeholder="搜索更新标题" onSearch={value => { setKeyword(value); setPage(1) }} />}
      pagination={{ current: page, pageSize: 20, total, onChange: setPage }} columns={[
        { title: '更新标题', dataIndex: 'title', render: (_, row) => <Button type="link" disabled={busy} onClick={() => void open(row.id)}>{row.title}</Button> },
        { title: '状态', render: (_, row) => <Tag>{({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' })[row.status]}</Tag> },
        { title: '发布人', dataIndex: 'publisherName' }, { title: '发布时间', render: (_, row) => <DateTimeText value={row.publishTime} /> },
        { title: '知悉人数 / 接收人数', render: (_, row) => row.status === 'DRAFT' ? '发布后统计' : `${row.acknowledgedCount ?? 0} / ${row.recipientCount ?? 0}` },
        { title: '操作', valueType: 'option', render: (_, row) => <Space>
          {row.status === 'DRAFT' && can('update') && <Button type="link" disabled={busy} onClick={() => void open(row.id, true)}>编辑</Button>}
          {row.status === 'DRAFT' && can('publish') && <Button type="link" disabled={busy} onClick={() => void transition(row, 'publish')}>发布</Button>}
          {row.status === 'PUBLISHED' && can('offline') && <Button type="link" disabled={busy} onClick={() => void transition(row, 'offline')}>下线</Button>}
        </Space> }
      ]} />
    {editor && <VersionEditor initial={editor === 'new' ? undefined : editor} onClose={() => setEditor(undefined)} onSaved={() => void load()} />}
    <Modal open={!!detail} title="版本更新详情" width="min(1000px, calc(100vw - 24px))" onCancel={() => setDetail(undefined)} footer={<Button onClick={() => setDetail(undefined)}>关闭</Button>} destroyOnHidden>
      {detail && <Tabs items={[
        { key: 'content', label: '更新内容', children: <VersionContent record={detail} reload={() => versionUpdates.get(detail.id)} /> },
        { key: 'recipients', label: '知悉情况', children: <VersionRecipients id={detail.id} /> }
      ]} />}
    </Modal>
  </section>
}
