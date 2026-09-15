import { DeleteOutlined, EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Input, Modal, Select, Space, Spin, Table, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { api, type DictData, type StudentContactFormField } from '../services/api'
import { materialApi, type Material, type MaterialVersion } from '../services/materialApi'
import { MaterialFields } from '../pages/MaterialLibraryPage'
import { DICT_TYPE } from '../constants'

type Filters = { keyword?: string; platform?: string; accountStage?: string; accountType?: string; profession?: string }
const filterDefinitions = [
  { key: 'platform', title: '平台', dict: 'zsjos_account_platform' },
  { key: 'accountStage', title: '适用阶段', dict: DICT_TYPE.MEDIA_ACCOUNT_STAGE },
  { key: 'accountType', title: '账号定位', dict: DICT_TYPE.PERSONA_TYPE },
  { key: 'profession', title: '专业定位', dict: DICT_TYPE.MATERIAL_PROFESSION },
] as const

export default function PositioningCardMaterialPicker({ field, value = [], onChange, disabled = false, canQuery }: {
  field: StudentContactFormField; value?: number[]; onChange?: (value: number[]) => void; disabled?: boolean; canQuery: boolean
}) {
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<number[]>([])
  const [filters, setFilters] = useState<Filters>({})
  const [page, setPage] = useState(1), [total, setTotal] = useState(0)
  const [rows, setRows] = useState<Material[]>([])
  const [versions, setVersions] = useState<Record<number, MaterialVersion>>({})
  const [dicts, setDicts] = useState<Record<string, DictData[]>>({})
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [retry, setRetry] = useState(0)
  const [preview, setPreview] = useState<number>(), [previewError, setPreviewError] = useState('')
  const run = useRef(0)
  useEffect(() => {
    if (!open || !canQuery) return
    const id = ++run.current
    setBusy(true); setError('')
    void (async () => {
      const [types, dictionaries] = await Promise.all([materialApi.types(), Promise.all(filterDefinitions.map(async f => [f.dict, await api.dictDataByType(f.dict)] as const))])
      const type = types.find(item => item.code === field.materialTypeCode && item.status === 0)
      if (!type) throw new Error('模板配置的素材类型不存在或已停用')
      const result = await materialApi.page({ ...filters, materialTypeId: type.id, status: 'EFFECTIVE', pageNo: page, pageSize: 10 })
      if (run.current !== id) return
      setDicts(Object.fromEntries(dictionaries)); setRows(result.list); setTotal(result.total)
    })().catch(cause => { if (run.current === id) setError(cause instanceof Error ? cause.message : '素材加载失败') })
      .finally(() => { if (run.current === id) setBusy(false) })
    return () => { ++run.current }
  }, [open, canQuery, field.materialTypeCode, filters, page, retry])
  useEffect(() => {
    if (!canQuery || !value.length) return
    let active = true
    void Promise.allSettled(value.filter(id => !versions[id]).map(async id => [id, await materialApi.version(id)] as const)).then(results => {
      if (!active) return
      const loaded = results.flatMap(result => result.status === 'fulfilled' ? [result.value] : [])
      if (loaded.length) setVersions(current => ({ ...current, ...Object.fromEntries(loaded) }))
    })
    return () => { active = false }
  }, [value, canQuery, versions])
  const showPreview = async (id: number) => {
    setPreview(id); setPreviewError('')
    try { const version = await materialApi.version(id); setVersions(current => ({ ...current, [id]: version })) }
    catch (cause) { setPreviewError(cause instanceof Error ? cause.message : '预览加载失败') }
  }
  const begin = () => {
    setSelected([...value]); setFilters({ platform: field.defaultPlatform, accountStage: field.defaultStage }); setPage(1); setOpen(true)
  }
  useEffect(() => { if (!open) setSelected([...value]) }, [value, open])
  return <Space orientation="vertical" style={{ width: '100%', minWidth: 0 }}>
    {value.map(id => <Space key={id} wrap>
      <Typography.Text>{versions[id]?.title || '已关联素材版本'}</Typography.Text>
      <Button aria-label="预览素材" icon={<EyeOutlined />} disabled={!canQuery} onClick={() => void showPreview(id)} />
      {!disabled && <Button aria-label="移除素材" icon={<DeleteOutlined />} onClick={() => onChange?.(value.filter(item => item !== id))} />}
    </Space>)}
    {!disabled && <Button icon={<PlusOutlined />} disabled={!canQuery} onClick={begin}>选择素材</Button>}
    {!canQuery && <Typography.Text type="secondary">无素材查询权限</Typography.Text>}
    {field.recommendedCount && <Typography.Text type="secondary">建议 {field.recommendedCount} 份，已选 {value.length} 份</Typography.Text>}
    <Modal title={field.title} open={open} width="min(1000px, calc(100vw - 24px))" onCancel={() => setOpen(false)} onOk={() => { onChange?.(selected); setOpen(false) }} okText={`确认选择（${selected.length}）`}>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search aria-label="搜索素材" placeholder="搜索素材" allowClear onSearch={keyword => { setFilters(current => ({ ...current, keyword })); setPage(1) }} />
        {filterDefinitions.map(f => <Select key={f.key} aria-label={f.title} placeholder={f.title} allowClear showSearch optionFilterProp="label" style={{ width: 170, maxWidth: '100%' }}
          disabled={field.filterAdjustable === false && (f.key === 'platform' || f.key === 'accountStage')} value={filters[f.key]}
          options={(dicts[f.dict] || []).map(item => ({ value: item.value, label: item.label }))}
          onChange={next => { setFilters(current => ({ ...current, [f.key]: next })); setPage(1) }} />)}
      </Space>
      {error ? <Alert type="error" showIcon message={error} action={<Button icon={<ReloadOutlined />} onClick={() => setRetry(n => n + 1)}>重试</Button>} /> : <Table<Material>
        rowKey={row => row.currentEffectiveVersionId!} size="small" loading={busy} dataSource={rows.filter(row => row.currentEffectiveVersionId)}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合筛选条件的素材" /> }}
        rowSelection={{ selectedRowKeys: selected, preserveSelectedRowKeys: true, onChange: keys => setSelected(keys.map(Number)) }}
        columns={[{ title: '素材', dataIndex: 'title', render: (title: string) => <span style={{ overflowWrap: 'anywhere' }}>{title}</span> },
          { title: '预览', width: 64, render: (_, row) => <Button aria-label="预览完整素材" icon={<EyeOutlined />} onClick={() => void showPreview(row.currentEffectiveVersionId!)} /> }]}
        pagination={{ current: page, total, pageSize: 10, showSizeChanger: false, onChange: setPage, simple: true }} />}
    </Modal>
    <Modal title={preview ? versions[preview]?.title || '素材预览' : '素材预览'} open={preview !== undefined} footer={null} onCancel={() => setPreview(undefined)} width="min(960px, calc(100vw - 24px))">
      {previewError ? <Alert type="error" message={previewError} action={<Button onClick={() => preview && void showPreview(preview)}>重试</Button>} /> : preview && versions[preview] ? <MaterialFields version={versions[preview]} /> : <Spin />}
    </Modal>
  </Space>
}
