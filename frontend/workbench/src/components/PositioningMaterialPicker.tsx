import { Alert, Button, Checkbox, Empty, Input, Modal, Pagination, Select, Space, Spin, Typography } from 'antd'
import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { api, type DictData } from '../services/api'
import { materialApi, type Material, type MaterialVersion } from '../services/materialApi'
import type { ProfileField } from '../services/mediaAccountProfile'
const MaterialFields = lazy(() => import('../pages/MaterialLibraryPage').then(module => ({ default: module.MaterialFields })))

export default function PositioningMaterialPicker({ field, value, snapshots = [], disabled, onChange }: {
  field: ProfileField; value: number[]; snapshots?: MaterialVersion[]; disabled: boolean; onChange: (ids: number[]) => void
}) {
  const [open, setOpen] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [rows, setRows] = useState<Material[]>([]), [total, setTotal] = useState(0), [page, setPage] = useState(1)
  const [selected, setSelected] = useState<number[]>([])
  const [known, setKnown] = useState<Record<number, string>>({})
  const [keyword, setKeyword] = useState(''), [platform, setPlatform] = useState<string>(), [stage, setStage] = useState<string>()
  const [dicts, setDicts] = useState<Record<string, DictData[]>>({})
  const [preview, setPreview] = useState<MaterialVersion>()
  const generation = useRef(0)
  useEffect(() => () => { generation.current++ }, [])
  const load = async (next: number, filters = { keyword, platform, stage }) => {
    const run = ++generation.current; setBusy(true); setError('')
    try {
      const [types, platforms, stages] = await Promise.all([materialApi.types(), api.dictDataByType('zsjos_account_platform'), api.dictDataByType('zsjos_media_account_stage')])
      const type = types.find(item => item.code === field.materialTypeCode && item.status === 0)
      if (!type) throw new Error('未找到可用的素材类型，请检查素材库配置')
      const result = await materialApi.page({ pageNo: next, pageSize: 10, materialTypeId: type.id, status: 'EFFECTIVE', keyword: filters.keyword, platform: filters.platform, accountStage: filters.stage })
      if (run !== generation.current) return
      setDicts({ platform: platforms, stage: stages }); setRows(result.list); setTotal(result.total); setPage(next)
      setKnown(previous => ({ ...previous, ...Object.fromEntries(result.list.filter(row => row.currentEffectiveVersionId).map(row => [row.currentEffectiveVersionId!, row.title])) }))
    } catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '素材加载失败') }
    finally { if (run === generation.current) setBusy(false) }
  }
  const show = async (id: number) => {
    setError('')
    try { setPreview(await materialApi.version(id)) }
    catch (cause) { setError(cause instanceof Error ? cause.message : '素材预览失败') }
  }
  return <Space direction="vertical" style={{ width: '100%' }}>
    {value.map(id => <Space key={id} wrap><Button type="link" onClick={() => void show(id)}>{known[id] || snapshots.find(row => row.id === id)?.title || '查看参考素材'}</Button>
      {!disabled && <Button size="small" onClick={() => onChange(value.filter(item => item !== id))}>移除</Button>}</Space>)}
    {!disabled && <Button onClick={() => { setSelected(value); setKeyword(''); setPlatform(field.defaultPlatform); setStage(field.defaultAccountStage); setOpen(true); void load(1, { keyword: '', platform: field.defaultPlatform, stage: field.defaultAccountStage }) }}>选择素材</Button>}
    <Typography.Text type="secondary">已选 {value.length} 项{field.recommendedCount ? `，建议 ${field.recommendedCount} 项（不限制提交）` : ''}</Typography.Text>
    {error && !open && <Alert type="error" message={error} />}
    <Modal open={open} title={field.label} width="min(1000px, calc(100vw - 32px))" onCancel={() => { generation.current++; setOpen(false) }} onOk={() => { onChange(selected); setOpen(false) }} okText="确认选择">
      <Space wrap>
        <Input aria-label="素材搜索" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="搜索素材" />
        <Select aria-label="素材平台" allowClear placeholder="全部平台" value={platform} style={{ minWidth: 150 }} onChange={setPlatform} options={(dicts.platform || []).map(item => ({ value: item.value, label: item.label }))} />
        <Select aria-label="适用阶段" allowClear placeholder="全部阶段" value={stage} style={{ minWidth: 150 }} onChange={setStage} options={(dicts.stage || []).map(item => ({ value: item.value, label: item.label }))} />
        <Button onClick={() => void load(1)}>筛选</Button>
      </Space>
      {error ? <Alert type="error" message={error} action={<Button onClick={() => void load(page)}>重试</Button>} /> : busy ? <Spin /> : !rows.length ? <Empty description="没有符合条件的素材，可调整筛选" /> : rows.filter(row => row.currentEffectiveVersionId).map(row => <div key={row.id}>
        <Checkbox checked={selected.includes(row.currentEffectiveVersionId!)} onChange={event => setSelected(previous => event.target.checked ? [...new Set([...previous, row.currentEffectiveVersionId!])] : previous.filter(id => id !== row.currentEffectiveVersionId))}>{row.title}</Checkbox>
        <Button type="link" onClick={() => void show(row.currentEffectiveVersionId!)}>预览完整内容</Button>
      </div>)}
      <Pagination current={page} total={total} pageSize={10} showSizeChanger={false} onChange={next => void load(next)} />
      <Typography.Text>已选 {selected.length} 项</Typography.Text>
    </Modal>
    <Modal open={Boolean(preview)} title={preview?.title} width="min(1100px, calc(100vw - 32px))" footer={null} onCancel={() => setPreview(undefined)}>
      {preview && <Suspense fallback={<Spin />}><MaterialFields version={preview} /></Suspense>}
    </Modal>
  </Space>
}
