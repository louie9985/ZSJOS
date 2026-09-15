import { DeleteOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import { Alert, Button, Collapse, Input, Select, Space, Spin, Typography, Upload } from 'antd'
import { useMemo, useState } from 'react'
import { materialApi, type Material, type MaterialFieldDefinition, type MaterialSaveRequest, type MaterialType } from '../services/materialApi'
import { ClipboardUploadButtons } from './ClipboardPasteTarget'

type DictOption = { value: string; label: string }
type Mode = 'create' | 'edit' | 'view'

export const VIRAL_ACCOUNT_SECTION_ORDER = [
  ['ACCOUNT_DETAIL', '账号详情'],
  ['DIRECTOR_ANALYSIS', '编导拆解'],
  ['BUILD_SUGGESTION', '搭建建议']
] as const

type ViralAccountSectionKey = (typeof VIRAL_ACCOUNT_SECTION_ORDER)[number][0]

export type ViralAccountFieldRun = {
  key: string
  group?: string
  fields: MaterialFieldDefinition[]
}

export type ViralAccountLayout = {
  sections: Array<{ key: ViralAccountSectionKey; label: string; runs: ViralAccountFieldRun[] }>
  unassignedFields: MaterialFieldDefinition[]
}

const presentText = (value?: string) => value && value !== 'null' ? value : undefined

const fieldRuns = (fields: MaterialFieldDefinition[]): ViralAccountFieldRun[] => {
  const runs: ViralAccountFieldRun[] = []
  fields.forEach((field, index) => {
    const group = presentText(field.group)
    const previous = runs.at(-1)
    if (previous && previous.group === group) {
      previous.fields.push(field)
      return
    }
    runs.push({ key: `${group || 'fields'}-${index}`, group, fields: [field] })
  })
  return runs
}

export function buildViralAccountLayout(input: MaterialFieldDefinition[]): ViralAccountLayout {
  const fields = input.map((field, index) => ({ field, index }))
    .sort((left, right) => (left.field.sort ?? Number.MAX_SAFE_INTEGER) - (right.field.sort ?? Number.MAX_SAFE_INTEGER)
      || left.index - right.index)
    .map(item => item.field)
  const knownSections = new Map<ViralAccountSectionKey, MaterialFieldDefinition[]>(
    VIRAL_ACCOUNT_SECTION_ORDER.map(([key]) => [key, []])
  )
  const unassignedFields: MaterialFieldDefinition[] = []
  fields.forEach(field => {
    const section = presentText(field.section) as ViralAccountSectionKey | undefined
    const target = section ? knownSections.get(section) : undefined
    if (target) target.push(field)
    else unassignedFields.push(field)
  })
  return {
    sections: VIRAL_ACCOUNT_SECTION_ORDER.map(([key, label]) => ({
      key,
      label,
      runs: fieldRuns(knownSections.get(key) || [])
    })).filter(section => section.runs.length),
    unassignedFields
  }
}

function empty(value: unknown) {
  return value == null || (typeof value === 'string' && value.trim() === '') || (Array.isArray(value) && value.length === 0)
}

function dictionaryText(value: unknown, snapshot: unknown, options: DictOption[]) {
  const snapshotValues = Array.isArray(snapshot) ? snapshot : snapshot == null ? [] : [snapshot]
  const values = Array.isArray(value) ? value : value == null ? [] : [value]
  return values.map((item, index) => {
    const saved = snapshotValues[index]
    if (saved && typeof saved === 'object' && 'label' in saved) return String((saved as { label: unknown }).label)
    return options.find(option => option.value === String(item))?.label || String(item)
  }).join('、') || '未填写'
}

function validateField(field: MaterialFieldDefinition, value: unknown, required: boolean, path: string): string {
  if (required && empty(value)) return `请填写${path}`
  if (empty(value)) return ''
  if (field.type === 'https-link') {
    try {
      const url = new URL(String(value).trim())
      if (url.protocol !== 'https:' || !url.hostname) return `${path}必须是 HTTPS 链接`
    } catch { return `${path}链接格式不正确` }
  }
  if (field.type === 'repeat-group') {
    if (!Array.isArray(value)) return `${path}格式不正确`
    if (field.minCount != null && value.length < field.minCount) return `${path}至少需要 ${field.minCount} 行`
    if (field.maxCount != null && value.length > field.maxCount) return `${path}最多 ${field.maxCount} 行`
    for (let i = 0; i < value.length; i += 1) {
      const row = value[i]
      if (!row || typeof row !== 'object' || Array.isArray(row)) return `${path}第 ${i + 1} 行格式不正确`
      for (const child of field.children || []) {
        const issue = validateField(child, (row as Record<string, unknown>)[child.key], required && Boolean(child.required), `${path}第 ${i + 1} 行的${child.label}`)
        if (issue) return issue
      }
    }
  }
  return ''
}

function FieldEditor({ field, value, dicts, onChange, readonly, snapshot }: {
  field: MaterialFieldDefinition
  value: unknown
  dicts: Record<string, DictOption[]>
  onChange: (value: unknown) => void
  readonly: boolean
  snapshot?: unknown
}) {
  if (field.type === 'repeat-group') {
    const rows = Array.isArray(value) ? value as Array<Record<string, unknown>> : Array.from({ length: field.initialCount || field.minCount || 0 }, () => Object.fromEntries((field.children || []).map(child => [child.key, child.type === 'dict-multi' ? [] : ''])))
    const snapshotRows = Array.isArray(snapshot) ? snapshot as Array<Record<string, unknown>> : []
    return <div className="viral-repeat-editor">
      <Typography.Title level={5} className="viral-repeat-editor-title">{field.label}</Typography.Title>
      {rows.map((row, index) => <div className="viral-repeat-editor-row" key={`${field.key}-${index}`}>
        <div className="viral-repeat-editor-row-head"><Typography.Text strong>第 {index + 1} 行</Typography.Text>
          {!readonly && <Button type="text" danger icon={<DeleteOutlined />} aria-label="删除此行"
            onClick={() => onChange(rows.filter((_, rowIndex) => rowIndex !== index))} />}</div>
        {(field.children || []).map(child => <FieldEditor key={child.key} field={child}
          value={row[child.key]} dicts={dicts} readonly={readonly}
          snapshot={snapshotRows[index]?.[child.key]}
          onChange={childValue => onChange(rows.map((item, rowIndex) => rowIndex === index
            ? { ...item, [child.key]: childValue } : item))} />)}
      </div>)}
      {!readonly && <Button type="dashed" block icon={<PlusOutlined />} onClick={() => onChange([
        ...rows, Object.fromEntries((field.children || []).map(child => [child.key, child.type === 'dict-multi' ? [] : '']))
      ])}>新增一行</Button>}
    </div>
  }
  const label = <span>{field.label}{field.required && <Typography.Text type="danger"> *</Typography.Text>}</span>
  if (readonly) return <div className="viral-field-readonly"><Typography.Text type="secondary">{label}</Typography.Text>
    <Typography.Paragraph className="viral-field-value">{field.type === 'dict-single' || field.type === 'dict-multi'
      ? dictionaryText(value, snapshot, dicts[field.dictType || ''] || [])
      : Array.isArray(value) ? value.join('、') : String(value ?? '未填写')}</Typography.Paragraph></div>
  if (field.type === 'dict-single' || field.type === 'dict-multi') {
    return <label className="viral-field"><Typography.Text type="secondary">{label}</Typography.Text>
      <Select className="viral-field-control" mode={field.type === 'dict-multi' ? 'multiple' : undefined}
        value={value as string | string[] | undefined} options={dicts[field.dictType || ''] || []}
        onChange={onChange} placeholder={`请选择${field.label}`} allowClear /></label>
  }
  const multiline = field.type === 'textarea' || field.type === 'rich-text'
  return <label className="viral-field"><Typography.Text type="secondary">{label}</Typography.Text>
    {multiline ? <Input.TextArea className="viral-field-control" value={String(value ?? '')} placeholder={field.placeholder} rows={4}
      maxLength={field.maxLength} showCount onChange={event => onChange(event.target.value)} />
      : <Input className="viral-field-control" value={String(value ?? '')} placeholder={field.placeholder} maxLength={field.maxLength}
        onChange={event => onChange(event.target.value)} />}</label>
}

export default function ViralAccountMaterialForm({ mode, type, material, dicts, onClose, onSaved, titleFieldKey = 'account_name', coverLabel = '账号主页截图', coverRequiredMessage = '请上传账号主页截图', sectionLabels, submitAllowed = true }: {
  mode: Mode
  type: MaterialType
  material?: Material
  dicts: Record<string, DictOption[]>
  onClose: () => void
  onSaved: () => void
  titleFieldKey?: string
  coverLabel?: string
  coverRequiredMessage?: string
  sectionLabels?: Partial<Record<ViralAccountSectionKey, string>>
  submitAllowed?: boolean
}) {
  const fields = useMemo(() => type.currentSchema?.fields || material?.currentVersion?.fields || [], [material, type])
  const initialValues = material?.currentVersion?.values || {}
  const initialSnapshots = material?.currentVersion?.dictSnapshot || {}
  const [values, setValues] = useState<Record<string, unknown>>(JSON.parse(JSON.stringify(initialValues)))
  const [coverFileId, setCoverFileId] = useState<number | undefined>(material?.coverFileId || material?.currentVersion?.coverFileId)
  const [coverPreviewUrl, setCoverPreviewUrl] = useState(material?.coverPreviewUrl || material?.currentVersion?.coverPreviewUrl)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [persistedMaterialId, setPersistedMaterialId] = useState<number>()
  const readonly = mode === 'view'
  const layout = useMemo(() => buildViralAccountLayout(fields), [fields])
  const update = (key: string, value: unknown) => setValues(current => ({ ...current, [key]: value }))
  const persist = async (submitApproval: boolean) => {
    const problem = fields.map(field => validateField(field, values[field.key], submitApproval && Boolean(field.required), field.label)).find(Boolean)
    if (problem) return setError(problem as string)
    if (submitApproval && !coverFileId) return setError(coverRequiredMessage)
    setSaving(true); setError('')
    try {
      const request: MaterialSaveRequest = {
        materialTypeId: type.id,
        title: String(values[titleFieldKey] || '').trim() || undefined,
        coverFileId,
        values
      }
      let materialId: number
      const existingId = mode === 'edit' && material ? material.id : persistedMaterialId
      if (existingId) {
        const current = await materialApi.get(existingId)
        request.expectedMaterialVersion = current.version
        await materialApi.update(existingId, request)
        materialId = existingId
      } else {
        materialId = await materialApi.create(request)
        setPersistedMaterialId(materialId)
      }
      if (submitApproval) {
        const saved = await materialApi.get(materialId)
        await materialApi.submit(materialId, saved.version)
      }
      onSaved()
    } catch (cause) { setError(cause instanceof Error ? cause.message : '保存失败') }
    finally { setSaving(false) }
  }
  const submit = async () => { if (!readonly) await persist(true) }
  const saveDraft = async () => { if (!readonly) await persist(false) }
  const uploadCover = async (file: File) => {
    setUploading(true); setError('')
    try { const result = await materialApi.uploadCover(file); setCoverFileId(result.fileId); setCoverPreviewUrl(result.previewUrl) }
    catch (cause) { setError(cause instanceof Error ? cause.message : '截图上传失败') }
    finally { setUploading(false) }
  }
  const fieldGrid = (runFields: MaterialFieldDefinition[]) => <div className="viral-account-field-grid">{runFields.map(field => <div key={field.key} className={field.type === 'textarea' || field.type === 'rich-text' || field.type === 'repeat-group' ? 'viral-account-field-wide' : ''}>
    <FieldEditor field={field} value={values[field.key]} snapshot={initialSnapshots[field.key]} dicts={dicts} readonly={readonly} onChange={value => update(field.key, value)} />
  </div>)}</div>
  const actions = readonly
    ? <Button onClick={onClose}>关闭</Button>
    : <><Button onClick={onClose}>取消</Button><Button loading={saving} onClick={() => void saveDraft()}>保存草稿</Button>{submitAllowed && <Button type="primary" loading={saving} onClick={() => void submit()}>提交审批</Button>}</>
  return <div className="viral-account-form">
    {error && <Alert type="error" showIcon message={error} closable onClose={() => setError('')} />}
    <div className="viral-account-grid">
      <aside className="viral-account-screenshot">
        <Typography.Title level={5}>{coverLabel}</Typography.Title>
        <div className="viral-account-screenshot-body">
          <div className="viral-account-screenshot-frame">
            {coverPreviewUrl
              ? <img src={coverPreviewUrl} alt={coverLabel} />
              : <div className="viral-account-screenshot-empty">
                  <div className="viral-shot-phone"><i className="avatar" /><i className="bar" /><i className="bar" /><span className="grid"><span /><span /><span /><span /></span></div>
                  <Typography.Text type="secondary">暂无截图</Typography.Text>
                </div>}
          </div>
          <div className="viral-account-side-actions">
            {!readonly && !coverFileId && <ClipboardUploadButtons disabled={uploading} canPaste={() => !uploading} onFiles={files => { const file = files[0]; if (file) void uploadCover(file) }}><Upload accept="image/*" maxCount={1} showUploadList={false} beforeUpload={file => { void uploadCover(file); return false }}><Button block icon={<UploadOutlined />} loading={uploading}>上传图片</Button></Upload></ClipboardUploadButtons>}
            {!readonly && coverFileId && <Button danger block icon={<DeleteOutlined />} disabled={uploading} onClick={() => { setCoverFileId(undefined); setCoverPreviewUrl(undefined) }}>删除图片</Button>}
            <Space className="viral-account-actions" direction="vertical" size={8}>{actions}</Space>
          </div>
        </div>
      </aside>
      {layout.sections.map(section => <section className="viral-account-section" key={section.key}>
        <Typography.Title level={5}>{sectionLabels?.[section.key] || section.label}</Typography.Title>
        <div className="viral-account-section-body">{section.runs.map(run => run.group
          ? <Collapse key={run.key} defaultActiveKey={[run.key]} ghost items={[{
              key: run.key,
              label: run.fields[0]?.stageCode
                ? (dicts.zsjos_media_account_stage || []).find(option => option.value === run.fields[0].stageCode)?.label || run.fields[0].stageCode
                : run.group,
              children: fieldGrid(run.fields)
            }]} />
          : <div key={run.key}>{fieldGrid(run.fields)}</div>)}</div>
      </section>)}
      {layout.unassignedFields.length > 0 && <section className="viral-account-section viral-account-unassigned">
        <Alert type="warning" showIcon message="模板待完善" description="以下字段未配置有效页面模块，已临时显示在此处。" />
        {fieldGrid(layout.unassignedFields)}
      </section>}
    </div>
    {saving && <Spin fullscreen />}
  </div>
}
