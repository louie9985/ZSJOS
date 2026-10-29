import { Alert, App, Button, ConfigProvider, Empty, Modal, Select, Space, Spin } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError } from '../services/api'
import { materialApi, type Material, type MaterialType } from '../services/materialApi'
import ViralAccountMaterialForm from './ViralAccountMaterialForm'
import ViralContentMaterialForm from './ViralContentMaterialForm'
import { APP_ROUTES } from '../constants'
import { useWorkbenchPageGuard } from './WorkbenchPageNavigation'
import { useRetainedPageActive } from '../layouts/RetainedReviewRoutes'
import BusinessTable from './BusinessTable'
import DateTimeText from './DateTimeText'

export default function ViralDecomposeWorkspace({ code }: { code: 'viral_account' | 'viral_content' }) {
  const { message, modal } = App.useApp()
  const active = useRetainedPageActive()
  const activeRef = useRef(active); activeRef.current = active
  const popupHost = useRef<HTMLElement>(null)
  const busy = useRef(false)
  const [busyState, setBusyState] = useState(false)
  const onBusyChange = (value: boolean) => { busy.current = value; setBusyState(value) }
  const [type, setType] = useState<MaterialType>()
  const [dicts, setDicts] = useState<Record<string, Array<{ value: string; label: string }>>>({})
  const [drafts, setDrafts] = useState<Material[]>([])
  const [material, setMaterial] = useState<Material>()
  const [editorMaterialId, setEditorMaterialId] = useState<number>()
  const [managerOpen, setManagerOpen] = useState(false)
  const [managerLoading, setManagerLoading] = useState(false)
  const [managerError, setManagerError] = useState('')
  const [managerUnauthorized, setManagerUnauthorized] = useState(false)
  const [deletingId, setDeletingId] = useState<number>()
  const [deleteTarget, setDeleteTarget] = useState<Material>()
  const deleting = useRef(false), managerGeneration = useRef(0)
  const [editing, setEditing] = useState(false), [formKey, setFormKey] = useState(0)
  const [ready, setReady] = useState(false)
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  const dirty = useRef(false), generation = useRef(0)
  const readDrafts = async (typeId: number) => {
    const result: Material[] = []
    for (let pageNo = 1; ; pageNo++) {
      const page = await materialApi.page({ pageNo, pageSize: 100, materialTypeId: typeId, mine: true, status: 'DRAFT' })
      result.push(...page.list)
      if (!page.list.length || pageNo * 100 >= page.total) return result
    }
  }
  const load = useCallback(async () => {
    const run = ++generation.current; setLoading(true); setError(''); setReady(false)
    try {
      const types = await materialApi.types()
      const current = types.find(item => item.code === code)
      if (run !== generation.current) return
      setType(current)
      if (!current) return
      const names = ['zsjos_account_platform', 'zsjos_persona_type', 'zsjos_material_profession', 'zsjos_media_account_stage',
        ...(code === 'viral_content' ? ['zsjos_viral_content_type', 'zsjos_media_account_primary_problem'] : [])]
      const options = await Promise.all(names.map(async name => [name, await api.dictDataByType(name)] as const))
      if (run !== generation.current) return
      setReady(true); setDicts(Object.fromEntries(options.map(([name, entries]) => [name, entries.map(({ value, label }) => ({ value, label }))])))
      setEditing(false); setMaterial(undefined); setEditorMaterialId(undefined); dirty.current = false
      const rows = await readDrafts(current.id)
      if (run === generation.current) setDrafts(rows)
    } catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '草稿加载失败') }
    finally { if (run === generation.current) setLoading(false) }
  }, [code])
  useEffect(() => { void load(); return () => { generation.current++; managerGeneration.current++ } }, [load])
  const discard = async () => {
    if (busy.current) { message.warning('正在保存或上传，请完成后再关闭或更换草稿'); return false }
    if (deleting.current) { message.warning('正在删除草稿，请完成后再关闭或更换草稿'); return false }
    return !dirty.current || await modal.confirm({ title: '放弃尚未保存的修改？', content: '已保存的草稿会保留。', okText: '放弃修改', cancelText: '继续填写' })
  }
  const path = code === 'viral_account' ? APP_ROUTES.VIRAL_ACCOUNT_DECOMPOSE : APP_ROUTES.VIRAL_CONTENT_DECOMPOSE
  // Activating this tab preserves its editor. Only destruction needs confirmation.
  useWorkbenchPageGuard(path, async destination => destination !== undefined || await discard())
  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (dirty.current || busy.current || deleting.current) { event.preventDefault(); event.returnValue = '' }
    }
    window.addEventListener('beforeunload', beforeUnload)
    return () => window.removeEventListener('beforeunload', beforeUnload)
  }, [])
  const choose = async (id?: number) => {
    if (!await discard()) return
    const run = ++generation.current; setLoading(true); setError('')
    try {
      const next = id ? await materialApi.get(id) : undefined
      if (run !== generation.current) return
      if (next && (next.materialTypeId !== type?.id || next.currentVersion?.status !== 'DRAFT' || !next.availableActions.includes('UPDATE'))) throw new Error('草稿已变化或无权编辑，请刷新草稿列表')
      setMaterial(next); setEditorMaterialId(next?.id); setEditing(true); setFormKey(value => value + 1); dirty.current = false
    } catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '草稿读取失败') }
    finally { if (run === generation.current) setLoading(false) }
  }
  const saved = async ({ materialId, submitted, hasUnsavedChanges = false }: { materialId: number; submitted: boolean; hasUnsavedChanges?: boolean }) => {
    dirty.current = hasUnsavedChanges
    setEditorMaterialId(submitted ? undefined : materialId)
    if (activeRef.current) message.success(submitted ? '已提交审批' : '草稿已保存，可继续填写')
    // Keep the editor mounted on draft save, including when refreshing the list fails.
    if (submitted) { setEditing(false); setMaterial(undefined) }
    const run = generation.current
    try { if (type) { const rows = await readDrafts(type.id); if (run === generation.current) setDrafts(rows) } }
    catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '草稿已保存，列表刷新失败') }
  }
  const refreshManager = async () => {
    if (!type) return
    const run = ++managerGeneration.current
    setManagerLoading(true); setManagerError(''); setManagerUnauthorized(false)
    try {
      const rows = await readDrafts(type.id)
      if (run === managerGeneration.current) setDrafts(rows)
    } catch (cause) {
      if (run === managerGeneration.current) {
        setManagerError(cause instanceof Error ? cause.message : '草稿加载失败')
        setManagerUnauthorized(cause instanceof ApiError && cause.code === 403)
      }
    } finally { if (run === managerGeneration.current) setManagerLoading(false) }
  }
  const deleteDraft = async (row: Material) => {
    if (deleting.current || busy.current || !row.availableActions.includes('DELETE_DRAFT')) return
    deleting.current = true; setDeletingId(row.id)
    try {
      await materialApi.deleteDraft(row.id, row.version)
      setDrafts(rows => rows.filter(item => item.id !== row.id))
      if (editorMaterialId === row.id) {
        setEditing(false); setMaterial(undefined); setEditorMaterialId(undefined)
        dirty.current = false; setFormKey(value => value + 1)
      }
      setDeleteTarget(undefined)
      message.success('草稿已删除')
      await refreshManager()
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '草稿删除失败，请重试')
    } finally { deleting.current = false; setDeletingId(undefined) }
  }
  const Editor = code === 'viral_content' ? ViralContentMaterialForm : ViralAccountMaterialForm
  return <ConfigProvider getPopupContainer={() => popupHost.current || document.body}><section ref={popupHost} className="workspace-page standalone-viral-decompose">
    <Space wrap><Select aria-label="选择已保存草稿" placeholder="选择已保存草稿" value={editorMaterialId} disabled={loading || busyState || deletingId !== undefined} style={{ minWidth: 240 }}
      options={drafts.map(row => ({ value: row.id, label: `${row.title} · ${row.materialNo}`, disabled: !row.availableActions.includes('UPDATE') }))} onChange={id => void choose(id)} />
      <Button disabled={loading || busyState || !type || !ready} onClick={() => void choose()}>新建拆解</Button>
      <Button disabled={loading || busyState} onClick={() => { void discard().then(allowed => { if (allowed) void load() }) }}>刷新草稿列表</Button>
      <Button disabled={loading || busyState || !type} onClick={() => { setManagerOpen(true); void refreshManager() }}>草稿管理</Button></Space>
    <Modal title={code === 'viral_content' ? '爆款内容草稿管理' : '账号拆解草稿管理'} open={managerOpen && active}
      getContainer={() => popupHost.current || document.body} width={800} footer={null}
      closable={deletingId === undefined} keyboard={deletingId === undefined} mask={{ closable: false }} onCancel={() => setManagerOpen(false)}>
      <BusinessTable<Material> tableKey="viral-decompose-drafts" mode="compact" rowKey="id"
        loading={managerLoading} dataSource={managerError ? [] : drafts} error={managerError} unauthorized={managerUnauthorized}
        onReload={refreshManager} emptyText="暂无草稿" pagination={{ defaultPageSize: 10, showSizeChanger: false }}
        actions={undefined}
        columns={[
          { title: '草稿标题', dataIndex: 'title', width: 280, ellipsis: false },
          { title: '素材编号', dataIndex: 'materialNo', width: 190 },
          { title: '更新时间', key: 'updateTime', width: 180, render: (_, row) => <DateTimeText value={row.updateTime} /> },
          { title: '操作', key: 'action', width: 90, render: (_, row) => row.availableActions.includes('DELETE_DRAFT')
            ? <Button type="link" danger aria-label="删除草稿" disabled={managerLoading || deletingId !== undefined} onClick={() => setDeleteTarget(row)}>删除</Button> : '—' },
        ]} />
    </Modal>
    <Modal title="确认删除草稿？" open={deleteTarget !== undefined && managerOpen && active}
      getContainer={() => popupHost.current || document.body} okText="确认删除" cancelText="取消" okButtonProps={{ danger: true, 'aria-label': '确认删除草稿' }}
      confirmLoading={deletingId !== undefined} cancelButtonProps={{ disabled: deletingId !== undefined }}
      closable={deletingId === undefined} keyboard={deletingId === undefined} mask={{ closable: false }}
      onCancel={() => setDeleteTarget(undefined)} onOk={() => { if (deleteTarget) void deleteDraft(deleteTarget) }}>
      {deleteTarget && <p>将删除“{deleteTarget.title}”（{deleteTarget.materialNo}），删除后无法在草稿箱恢复。
        {editing && editorMaterialId === deleteTarget.id && '当前编辑器也会关闭，尚未保存的修改将一并丢弃。'}</p>}
    </Modal>
    {error && <Alert type="error" showIcon message={error} />}
    {editing && type ? <Spin spinning={loading}><Editor key={formKey} mode={material ? 'edit' : 'create'} type={type} material={material} dicts={dicts}
      onBusyChange={onBusyChange} onDirty={() => { dirty.current = true }} onClose={() => { void discard().then(allowed => { if (allowed) { setEditing(false); dirty.current = false } }) }} onSaved={saved}
      {...(code === 'viral_content' ? { showDraftNavigation: false } : {})} /></Spin> : loading ? <Spin /> : !type ? <Empty description="拆解模板尚未发布" /> : !error && <Empty description={drafts.length ? '请选择草稿继续填写，或新建拆解' : '暂无草稿，点击新建拆解开始填写'} />}
  </section></ConfigProvider>
}
