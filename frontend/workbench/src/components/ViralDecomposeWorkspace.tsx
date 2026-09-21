import { Alert, App, Button, Empty, Select, Space, Spin } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../services/api'
import { materialApi, type Material, type MaterialType } from '../services/materialApi'
import ViralAccountMaterialForm from './ViralAccountMaterialForm'
import ViralContentMaterialForm from './ViralContentMaterialForm'

export default function ViralDecomposeWorkspace({ code }: { code: 'viral_account' | 'viral_content' }) {
  const { message, modal } = App.useApp()
  const [type, setType] = useState<MaterialType>()
  const [dicts, setDicts] = useState<Record<string, Array<{ value: string; label: string }>>>({})
  const [drafts, setDrafts] = useState<Material[]>([])
  const [material, setMaterial] = useState<Material>()
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
      setEditing(false); setMaterial(undefined); dirty.current = false
      const rows = await readDrafts(current.id)
      if (run === generation.current) setDrafts(rows)
    } catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '草稿加载失败') }
    finally { if (run === generation.current) setLoading(false) }
  }, [code])
  useEffect(() => { void load(); return () => { generation.current++ } }, [load])
  const discard = async () => !dirty.current || await modal.confirm({ title: '放弃尚未保存的修改？', content: '已保存的草稿会保留。', okText: '放弃修改', cancelText: '继续填写' })
  const choose = async (id?: number) => {
    if (!await discard()) return
    const run = ++generation.current; setLoading(true); setError('')
    try {
      const next = id ? await materialApi.get(id) : undefined
      if (run !== generation.current) return
      if (next && (next.materialTypeId !== type?.id || next.currentVersion?.status !== 'DRAFT' || !next.availableActions.includes('UPDATE'))) throw new Error('草稿已变化或无权编辑，请刷新草稿列表')
      setMaterial(next); setEditing(true); setFormKey(value => value + 1); dirty.current = false
    } catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '草稿读取失败') }
    finally { if (run === generation.current) setLoading(false) }
  }
  const saved = async ({ submitted }: { materialId: number; submitted: boolean }) => {
    dirty.current = false
    message.success(submitted ? '已提交审批' : '草稿已保存，可继续填写')
    // Keep the editor mounted on draft save, including when refreshing the list fails.
    if (submitted) { setEditing(false); setMaterial(undefined) }
    try { if (type) setDrafts(await readDrafts(type.id)) }
    catch (cause) { setError(cause instanceof Error ? cause.message : '草稿已保存，列表刷新失败') }
  }
  const Editor = code === 'viral_content' ? ViralContentMaterialForm : ViralAccountMaterialForm
  return <section className="workspace-page standalone-viral-decompose">
    <Space wrap><Select aria-label="选择已保存草稿" placeholder="选择已保存草稿" value={material?.id} disabled={loading} style={{ minWidth: 240 }}
      options={drafts.map(row => ({ value: row.id, label: `${row.title} · ${row.materialNo}`, disabled: !row.availableActions.includes('UPDATE') }))} onChange={id => void choose(id)} />
      <Button disabled={loading || !type || !ready} onClick={() => void choose()}>新建拆解</Button>
      <Button disabled={loading} onClick={() => { void discard().then(allowed => { if (allowed) void load() }) }}>刷新草稿列表</Button></Space>
    {error && <Alert type="error" showIcon message={error} />}
    {editing && type ? <Spin spinning={loading}><Editor key={formKey} mode={material ? 'edit' : 'create'} type={type} material={material} dicts={dicts}
      onDirty={() => { dirty.current = true }} onClose={() => { void discard().then(allowed => { if (allowed) { setEditing(false); dirty.current = false } }) }} onSaved={saved}
      {...(code === 'viral_content' ? { showDraftNavigation: false } : {})} /></Spin> : loading ? <Spin /> : !type ? <Empty description="拆解模板尚未发布" /> : !error && <Empty description={drafts.length ? '请选择草稿继续填写，或新建拆解' : '暂无草稿，点击新建拆解开始填写'} />}
  </section>
}
