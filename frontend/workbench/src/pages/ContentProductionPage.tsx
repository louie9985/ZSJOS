import ResourceLinkInput from '../components/ResourceLinkInput'
import {
  CheckOutlined,
  EditOutlined,
  LinkOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import { ClipboardUploadButtons } from '../components/ClipboardPasteTarget'
import MaterialSelectorModal from '../components/MaterialSelectorModal'
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Empty,
  Form,
  Image,
  Input,
  List,
  Modal,
  Pagination,
  Select,
  Skeleton,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, api, type DictData, type MediaAccount, type MediaContent, type MediaContentVersion, type MediaContentVersionFile } from '../services/api'
import { type Material } from '../services/materialApi'
import { hasPermission } from '../services/managementAccess'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 20
const MAX_FILE_BYTES = 1024 * 1024 * 1024

type UploadedFile = {
  fileId: number
  name: string
  contentType: string
  size: number
  previewUrl?: string
}

type ContentFormValues = {
  titleSnapshot: string
  scriptText?: string
  purposeValue?: string
  formatValue?: string
  detailUrl?: string
  commentHook?: string
  referenceWorkUrl?: string
  leadResourceUrl?: string
  plannedPublishAt?: dayjs.Dayjs
}

type Option = { value: string; label: string }

/** 参考素材快照：与内容审核链路同一结构，审批时即使素材被改动仍可查看当时信息。 */
const toReferenceMaterials = (materials: Material[]) => materials.map(material => ({
  materialId: material.id,
  materialVersionId: material.currentVersion?.id || material.currentEffectiveVersionId,
  materialNo: material.materialNo,
  title: material.title,
  materialTypeName: material.materialTypeName,
  coverPreviewUrl: material.coverPreviewUrl,
}))

/** 把版本快照里的参考素材 JSON 回填为素材选择器可用的对象。 */
const parseReferenceMaterials = (json: string | undefined): Material[] => {
  if (!json) return []
  try {
    const raw = JSON.parse(json) as unknown
    const rows = Array.isArray(raw) ? raw : []
    return rows.map(item => {
      const ref = item && typeof item === 'object' ? item as Record<string, unknown> : {}
      return {
        id: Number(ref.materialId),
        materialNo: String(ref.materialNo || ''),
        title: String(ref.title || '素材'),
        materialTypeName: String(ref.materialTypeName || ''),
        coverPreviewUrl: typeof ref.coverPreviewUrl === 'string' ? ref.coverPreviewUrl : undefined,
      } as Material
    }).filter(material => Number.isFinite(material.id) && material.id > 0)
  } catch {
    return []
  }
}

const statusText: Record<string, string> = {
  topic: '选题',
  script: '脚本',
  in_production: '制作中',
  acceptance: '待审核',
  ready_to_publish: '待发布',
  published: '已发布',
  rejected: '已退回',
  revising: '修改中',
}

const versionStatusText: Record<string, string> = {
  DRAFT: '草稿',
  IN_APPROVAL: '审批中',
  EFFECTIVE: '已生效',
  REJECTED: '已驳回',
}

const errorText = (error: unknown) => error instanceof ApiError && error.code === 403
  ? '无权访问内容生产'
  : error instanceof Error ? error.message : '内容生产加载失败，请重试'

const parseFileSnapshot = (json: string | undefined, files: MediaContentVersionFile[] = []): UploadedFile[] => {
  if (!json) return []
  try {
    const raw = JSON.parse(json) as unknown
    const rows = Array.isArray(raw) ? raw : [raw]
    return rows.map(item => {
      const value = item && typeof item === 'object' ? item as Record<string, unknown> : {}
      const fileId = Number(value.id ?? value.fileId)
      const bound = files.find(file => file.infraFileId === fileId)
      return {
        fileId,
        name: String(value.name ?? bound?.originalName ?? '文件'),
        contentType: String(value.contentType ?? bound?.contentType ?? ''),
        size: Number(value.size ?? bound?.fileSize ?? 0),
        previewUrl: bound?.previewUrl || String(value.url ?? bound?.fileUrlSnapshot ?? ''),
      }
    }).filter(file => Number.isFinite(file.fileId) && file.fileId > 0)
  } catch {
    return []
  }
}

const fileSnapshot = (files: UploadedFile[]) => files.length
  ? JSON.stringify(files.map(file => ({ id: file.fileId, name: file.name, contentType: file.contentType, size: file.size })))
  : undefined

const fileLabel = (file: UploadedFile) => `${file.name} · ${(file.size / 1024 / 1024).toFixed(1)} MB`

function FilePreview({ file }: { file: UploadedFile }) {
  if (file.contentType.startsWith('image/') && file.previewUrl) {
    return <Image width={96} height={64} preview={{ src: file.previewUrl }} src={file.previewUrl} alt={file.name} />
  }
  if (file.contentType.startsWith('video/') && file.previewUrl) {
    return <video className="content-production-video-thumb" src={file.previewUrl} controls preload="metadata" />
  }
  return <LinkOutlined />
}

/** 作品封面：与内容审核页同一版式，放在字段表格左栏。 */
function VersionCover({ version }: { version: MediaContentVersion }) {
  const cover = (version.files || []).find(file => file.fieldKey === 'cover' && file.previewUrl
    && file.contentType.startsWith('image/'))
  return <div className="content-production-version-cover">
    {cover
      ? <Image src={cover.previewUrl} alt={`作品封面图：${cover.originalName}`} />
      : <Typography.Text type="secondary">暂无封面图</Typography.Text>}
    <span>作品封面图</span>
  </div>
}

/** 参考素材：与内容审核页同一版式，展示提交时的素材快照。 */
function VersionReferenceMaterials({ version }: { version: MediaContentVersion }) {
  const materials = parseReferenceMaterials(version.materialRefsJson)
  if (!materials.length) return null
  return <div><Typography.Text strong>参考素材</Typography.Text>
    <div className="content-production-media">{materials.map(material => <div className="content-production-media-item" key={material.id}>
      {material.coverPreviewUrl
        ? <Image width={128} height={84} src={material.coverPreviewUrl} alt={`参考素材：${material.title}`} />
        : null}
      <span>{material.title} · {material.materialNo}</span>
    </div>)}</div>
  </div>
}

function CreateContentDialog({ open, onClose, onCreated }: {
  open: boolean
  onClose: () => void
  onCreated: (id: number) => void
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<{ accountId: number; title: string; topic?: string; contentClassValue: string }>()
  const [accounts, setAccounts] = useState<MediaAccount[]>([])
  const [classes, setClasses] = useState<DictData[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    setError('')
    setLoading(true)
    Promise.all([api.mediaAccount.page({ pageNo: 1, pageSize: 100 }), api.dictDataByType('zsjos_content_class')])
      .then(([accountResult, classResult]) => { setAccounts(accountResult.list); setClasses(classResult) })
      .catch(cause => setError(errorText(cause)))
      .finally(() => setLoading(false))
  }, [form, open])

  const submit = async () => {
    try {
      const values = await form.validateFields()
      setLoading(true)
      const id = await api.mediaContent.create({ ...values, contentClassLabelSnapshot: classes.find(item => item.value === values.contentClassValue)?.label || '' })
      message.success('内容已创建，请继续保存内容版本')
      onCreated(id)
      onClose()
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields) setError(errorText(cause))
    } finally { setLoading(false) }
  }

  return <Modal title="创建内容" open={open} onCancel={onClose} onOk={() => void submit()} okText="创建内容" confirmLoading={loading} width="min(620px, calc(100vw - 32px))">
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => setError('')}>关闭</Button>} />}
    {loading && !accounts.length ? <Skeleton active /> : <Form form={form} layout="vertical">
      <Form.Item name="accountId" label="第三方账号" rules={[{ required: true, message: '请选择账号' }]}>
        <Select showSearch optionFilterProp="label" options={accounts.map(account => ({ value: account.id, label: `${account.nickname || account.accountNo} · ${account.accountNo}` }))} />
      </Form.Item>
      <Form.Item name="title" label="内容标题" rules={[{ required: true, max: 255, message: '请输入内容标题' }]}><Input maxLength={255} /></Form.Item>
      <Form.Item name="topic" label="选题说明"><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
      <Form.Item name="contentClassValue" label="内容分类" rules={[{ required: true, message: '请选择内容分类' }]}>
        <Select options={classes.map(item => ({ value: item.value, label: item.label }))} />
      </Form.Item>
    </Form>}
  </Modal>
}

function VersionEditor({ content, initial, purposeOptions, formatOptions, onSaved, onCancel }: {
  content: MediaContent
  initial?: MediaContentVersion
  purposeOptions: Option[]
  formatOptions: Option[]
  onSaved: () => void
  onCancel: () => void
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<ContentFormValues>()
  const [cover, setCover] = useState<UploadedFile[]>([])
  const [materials, setMaterials] = useState<Material[]>([])
  const [pickerOpen, setPickerOpen] = useState(false)
  const [uploading, setUploading] = useState(false)
  const coverInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    form.setFieldsValue({
      titleSnapshot: initial?.titleSnapshot || content.title,
      scriptText: initial?.scriptText,
      purposeValue: initial?.purposeValue,
      formatValue: initial?.formatValue,
      detailUrl: initial?.detailUrl,
      commentHook: initial?.commentHook,
      referenceWorkUrl: initial?.referenceWorkUrl,
      leadResourceUrl: initial?.leadResourceUrl,
      plannedPublishAt: initial?.plannedPublishAt ? dayjs(initial.plannedPublishAt) : undefined,
    })
    setCover(parseFileSnapshot(initial?.coverSnapshotJson, initial?.files.filter(file => file.fieldKey === 'cover')))
    setMaterials(parseReferenceMaterials(initial?.materialRefsJson))
  }, [content, form, initial])

  const upload = async (files: Iterable<File> | null) => {
    const selected = Array.from(files || [])
    if (!selected.length) return
    if (selected.length > 1) return message.warning('封面只能上传 1 个文件')
    setUploading(true)
    try {
      const file = selected[0]
      if (file.size > MAX_FILE_BYTES) throw new Error('文件不能超过 1GB')
      const result = await api.mediaContent.uploadVersionFile(file)
      setCover([{ fileId: result.fileId, name: result.name, contentType: result.contentType, size: result.size, previewUrl: result.previewUrl }])
    } catch (cause) { message.error(errorText(cause)) }
    finally { setUploading(false) }
  }
  const save = async () => {
    try {
      const values = await form.validateFields()
      setUploading(true)
      await api.mediaContent.createVersion({
        contentId: content.id,
        titleSnapshot: values.titleSnapshot.trim(),
        scriptText: values.scriptText?.trim() || undefined,
        coverSnapshotJson: fileSnapshot(cover),
        materialRefsJson: materials.length ? JSON.stringify(toReferenceMaterials(materials)) : undefined,
        purposeValue: values.purposeValue || undefined,
        purposeLabelSnapshot: purposeOptions.find(item => item.value === values.purposeValue)?.label || undefined,
        formatValue: values.formatValue || undefined,
        formatLabelSnapshot: formatOptions.find(item => item.value === values.formatValue)?.label || undefined,
        detailUrl: values.detailUrl?.trim() || undefined,
        commentHook: values.commentHook?.trim() || undefined,
        referenceWorkUrl: values.referenceWorkUrl?.trim() || undefined,
        leadResourceUrl: values.leadResourceUrl?.trim() || undefined,
        plannedPublishAt: values.plannedPublishAt?.format('YYYY-MM-DDTHH:mm:ss'),
      })
      message.success('内容版本已保存')
      onSaved()
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields) message.error(errorText(cause))
    } finally { setUploading(false) }
  }

  return <section className="content-production-editor">
    <div className="content-production-editor-heading"><div><Typography.Title level={5}>保存新版本</Typography.Title><Typography.Text type="secondary">服务端按当前内容状态记录版本</Typography.Text></div><Space><Button onClick={onCancel}>取消</Button><Button type="primary" icon={<SaveOutlined />} loading={uploading} onClick={() => void save()}>保存版本</Button></Space></div>
    <Form form={form} layout="vertical">
      <div className="content-production-upload-grid">
        <ClipboardUploadButtons disabled={uploading || cover.length > 0} canPaste={() => !uploading && cover.length === 0} onFiles={files => void upload(files)}><Typography.Text strong>作品封面图</Typography.Text><div className="content-production-upload-row">
          <input ref={coverInput} hidden type="file" accept="image/*" onChange={event => { void upload(event.target.files); event.target.value = '' }} />
          <Button icon={<UploadOutlined />} loading={uploading} onClick={() => coverInput.current?.click()}>上传封面图</Button>
          {cover.map(file => <div className="content-production-uploaded" key={file.fileId}><FilePreview file={file} /><span>{fileLabel(file)}</span><Button type="text" danger onClick={() => setCover([])}>移除</Button></div>)}
        </div></ClipboardUploadButtons>
      </div>
      <div className="content-production-form-grid">
        <Form.Item name="plannedPublishAt" label="预计发布时间"><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
        <Form.Item name="titleSnapshot" label="发布标题" rules={[{ required: true, whitespace: true, message: '请输入发布标题' }]}><Input maxLength={255} showCount /></Form.Item>
      </div>
      <div className="content-production-form-grid">
        <Form.Item name="purposeValue" label="作品目的"><Select allowClear options={purposeOptions} /></Form.Item>
        <Form.Item name="formatValue" label="作品形式"><Select allowClear options={formatOptions} /></Form.Item>
      </div>
      <Form.Item name="scriptText" label="正文文稿"><Input.TextArea rows={9} maxLength={20000} showCount /></Form.Item>
      <Form.Item name="detailUrl" label="作品详情"><ResourceLinkInput placeholder="可填写链接，或由审批详情页直接查看" /></Form.Item>
      <Form.Item name="leadResourceUrl" label="引流资料链接"><ResourceLinkInput placeholder="可点击下载的资料链接" /></Form.Item>
      <Form.Item name="commentHook" label="评论区钩子"><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
      <Form.Item name="referenceWorkUrl" label="参考作品链接" extra="直接填写参考作品的链接，可留空。"><ResourceLinkInput placeholder="https:// 参考作品链接" allowClear /></Form.Item>
      <Form.Item label="参考素材" extra="从素材库浏览并多选参考素材，审批人可在审批详情中查看。">
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Button onClick={() => setPickerOpen(true)}>素材浏览 · 选择参考素材</Button>
          {materials.map(material => <Card key={material.id} size="small">
            <Space align="start" size={10} style={{ width: '100%' }}>
              {material.coverPreviewUrl
                ? <img src={material.coverPreviewUrl} alt={material.title} style={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 4 }} />
                : <div style={{ width: 56, height: 56, borderRadius: 4, background: 'var(--crm-bg-sunken)' }} />}
              <span style={{ flex: 1 }}>
                <Typography.Text strong>{material.title}</Typography.Text><br />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>{material.materialNo} · {material.materialTypeName}</Typography.Text>
              </span>
              <Button danger type="text" size="small" onClick={() => setMaterials(current => current.filter(item => item.id !== material.id))}>移除</Button>
            </Space>
          </Card>)}
        </Space>
      </Form.Item>
    </Form>
    <MaterialSelectorModal open={pickerOpen} onCancel={() => setPickerOpen(false)} defaultSelected={materials} onConfirm={selected => { setMaterials(selected); setPickerOpen(false) }} />
  </section>
}

export default function ContentProductionPage({ permissions = [] }: { permissions?: string[] }) {
  const { message } = App.useApp()
  const [rows, setRows] = useState<MediaContent[]>([])
  const [selectedId, setSelectedId] = useState<number>()
  const [selected, setSelected] = useState<MediaContent>()
  const [versions, setVersions] = useState<MediaContentVersion[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [purposes, setPurposes] = useState<DictData[]>([])
  const [formats, setFormats] = useState<DictData[]>([])

  useEffect(() => {
    Promise.all([api.dictDataByType('zsjos_content_purpose'), api.dictDataByType('zsjos_content_format')])
      .then(([purposeRows, formatRows]) => { setPurposes(purposeRows); setFormats(formatRows) })
      .catch(() => { setPurposes([]); setFormats([]) })
  }, [])

  const purposeOptions = useMemo(() => purposes.map(item => ({ value: item.value, label: item.label })), [purposes])
  const formatOptions = useMemo(() => formats.map(item => ({ value: item.value, label: item.label })), [formats])

  const currentVersion = useMemo(() => selected && versions.find(version => version.versionNo === selected.currentVersionNo), [selected, versions])

  const loadDetail = useCallback(async (id: number) => {
    setSelectedId(id)
    setDetailLoading(true)
    setDetailError('')
    try {
      const [content, versionRows] = await Promise.all([api.mediaContent.get(id), api.mediaContent.versions(id)])
      setSelected(content)
      setVersions(versionRows)
    } catch (cause) { setSelected(undefined); setVersions([]); setDetailError(errorText(cause)) }
    finally { setDetailLoading(false) }
  }, [])

  const load = useCallback(async (targetPage = 1, preferredId?: number) => {
    setLoading(true)
    setError('')
    try {
      const result = await api.mediaContent.page({ pageNo: targetPage, pageSize: PAGE_SIZE, keyword: keyword || undefined })
      setRows(result.list)
      setTotal(result.total)
      setPage(targetPage)
      const nextId = result.list.some(item => item.id === preferredId) ? preferredId : result.list[0]?.id
      if (nextId) await loadDetail(nextId)
      else { setSelectedId(undefined); setSelected(undefined); setVersions([]) }
    } catch (cause) { setRows([]); setSelected(undefined); setVersions([]); setSelectedId(undefined); setError(errorText(cause)) }
    finally { setLoading(false) }
  }, [keyword, loadDetail])

  useEffect(() => { void load(1) }, [keyword, load])

  const refreshSelected = async () => { if (selectedId) await loadDetail(selectedId); await load(page, selectedId) }

  const action = async (name: string) => {
    if (!selected) return
    try {
      const id = selected.id
      if (name === 'COMPLETE_TOPIC') await api.mediaContent.completeTopic(id, selected.version)
      else if (name === 'SUBMIT_PRODUCTION') await api.mediaContent.submitProduction(id, selected.version)
      else if (name === 'SUBMIT_ACCEPTANCE') await api.mediaContent.submitAcceptance(id, selected.version)
      else if (name === 'START_CONTENT_REVISION') await api.mediaContent.startRevision(id, selected.version)
      else if (name === 'RESUBMIT_PRODUCTION') await api.mediaContent.resubmitProduction(id, selected.version)
      else return
      message.success('状态已更新')
      await refreshSelected()
    } catch (cause) { message.error(errorText(cause)) }
  }

  const canCreate = hasPermission(permissions, 'zsjos:content:create')
  const canEdit = hasPermission(permissions, 'zsjos:content:edit')
  const canEditCurrent = canEdit && Boolean(selected) && (!currentVersion?.frozenAt
    || (selected?.status === 'revising' && currentVersion?.reviewDecision === 'rejected'))

  return <section className="workspace-page content-production-page">
    <header className="content-production-filter-shell"><div><Typography.Title level={4}>内容生产</Typography.Title><Typography.Text type="secondary">{total} 条内容</Typography.Text></div><div className="content-production-toolbar">
      <Input.Search allowClear value={keywordInput} onChange={event => setKeywordInput(event.target.value)} onSearch={value => setKeyword(value.trim())} placeholder="搜索内容编号或标题" />
      <Tooltip title="刷新"><Button icon={<ReloadOutlined />} onClick={() => void load(page, selectedId)} /></Tooltip>
      {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建内容</Button>}
    </div></header>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(page)}>重试</Button>} />}
    <div className="content-production-layout">
      <aside className="content-production-list-pane"><div className="content-production-scroll">
        {loading && !rows.length ? <Skeleton active /> : rows.length ? rows.map(row => <button type="button" key={row.id} className={`content-production-list-item${row.id === selectedId ? ' active' : ''}`} onClick={() => void loadDetail(row.id)}>
          <span><strong>{row.title || row.contentNo}</strong><Tag>{statusText[row.status] || row.status}</Tag></span><span>{row.contentNo} · 当前 V{row.currentVersionNo || 0}</span>
        </button>) : !error && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内容" />}
      </div>{total > PAGE_SIZE && <Pagination simple current={page} pageSize={PAGE_SIZE} total={total} onChange={value => void load(value)} />}</aside>
      <main className="content-production-detail-pane">{detailLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>} /> : selected ? <>
        <div className="content-production-heading"><div><Space><Tag>{statusText[selected.status] || selected.status}</Tag><Typography.Text>{selected.contentNo}</Typography.Text></Space><Typography.Title level={4}>{selected.title}</Typography.Title><Typography.Text type="secondary">账号 {selected.accountId} · 当前版本 V{selected.currentVersionNo || 0}</Typography.Text></div><Space wrap>
          {canEditCurrent && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>保存新版本</Button>}
          {selected.availableActions.map(name => <Button key={name} type={name === 'SUBMIT_ACCEPTANCE' ? 'primary' : undefined} icon={<CheckOutlined />} onClick={() => void action(name)}>{({ COMPLETE_TOPIC: '完成选题', SUBMIT_PRODUCTION: '提交制作', SUBMIT_ACCEPTANCE: '提交审核', START_CONTENT_REVISION: '开始修改', RESUBMIT_PRODUCTION: '重新提交' } as Record<string, string>)[name] || name}</Button>)}
        </Space></div>
        {editing && <VersionEditor content={selected} initial={currentVersion} purposeOptions={purposeOptions} formatOptions={formatOptions} onSaved={() => { setEditing(false); void refreshSelected() }} onCancel={() => setEditing(false)} />}
        <section className="content-production-current"><div className="content-production-section-heading"><Typography.Title level={5}>当前版本</Typography.Title><Typography.Text type="secondary">{currentVersion ? `V${currentVersion.versionNo} · ${versionStatusText[currentVersion.reviewDecision ? (currentVersion.reviewDecision === 'approved' ? 'EFFECTIVE' : 'REJECTED') : 'DRAFT'] || currentVersion.stage}` : '尚未保存版本'}</Typography.Text></div>{currentVersion ? <>
          {/* 字段顺序与内容审核页一致，运营填写时看到的排布就是编导审核时的排布。 */}
          <div className="content-production-version-body">
            <VersionCover version={currentVersion} />
            <dl className="content-production-fields">
              <dt>预计发布时间</dt><dd>{formatTimestamp(currentVersion.plannedPublishAt) || '—'}</dd>
              <dt>作品目的</dt><dd>{currentVersion.purposeLabelSnapshot || currentVersion.purposeValue || '—'}</dd>
              <dt>作品形式</dt><dd>{currentVersion.formatLabelSnapshot || currentVersion.formatValue || '—'}</dd>
              <dt>发布标题</dt><dd>{currentVersion.titleSnapshot || '—'}</dd>
              <dt>选题</dt><dd>{currentVersion.topicSnapshot || '—'}</dd>
              <dt>正文文稿</dt><dd>{currentVersion.scriptText || '暂无脚本或正文'}</dd>
              <dt>作品详情</dt><dd>{currentVersion.detailUrl
                ? <a href={currentVersion.detailUrl} target="_blank" rel="noreferrer">打开详情</a> : '—'}</dd>
              <dt>引流资料链接</dt><dd>{currentVersion.leadResourceUrl
                ? <a href={currentVersion.leadResourceUrl} target="_blank" rel="noreferrer">打开链接</a> : '—'}</dd>
              <dt>评论区钩子</dt><dd>{currentVersion.commentHook || '—'}</dd>
              <dt>参考作品链接</dt><dd>{currentVersion.referenceWorkUrl
                ? <a href={currentVersion.referenceWorkUrl} target="_blank" rel="noreferrer">打开参考作品</a> : '—'}</dd>
            </dl>
          </div>
          <VersionReferenceMaterials version={currentVersion} />
        </> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={canEdit ? '点击“保存新版本”开始填写内容' : '暂无内容版本'} />}</section>
        <section className="content-production-history"><Typography.Title level={5}>版本历史</Typography.Title><List size="small" dataSource={versions} locale={{ emptyText: '暂无版本' }} renderItem={version => <List.Item><div><strong>V{version.versionNo}</strong><span className="content-production-history-meta">{statusText[version.stage] || version.stage} · {formatTimestamp(version.submittedAt)}</span></div><Space>{version.reviewDecision && <Tag color={version.reviewDecision === 'approved' ? 'success' : 'error'}>{version.reviewDecision === 'approved' ? '审核通过' : '审核退回'}</Tag>}{version.frozenAt && <Tag>已冻结</Tag>}</Space></List.Item>} /></section>
      </> : <Empty description="从左侧选择一条内容" />}</main>
    </div>
    <CreateContentDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreated={id => void load(1, id)} />
  </section>
}
