import {
  BookOutlined,
  CloseOutlined,
  EditOutlined,
  HeartFilled,
  HeartOutlined,
  HistoryOutlined,
  LikeFilled,
  LikeOutlined,
  LinkOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Checkbox,
  Descriptions,
  Drawer,
  Empty,
  Image,
  Input,
  List,
  Modal,
  Select,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Tooltip,
  Typography
} from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import DateTimeText from '../components/DateTimeText'
import SafeRichText from '../components/SafeRichText'
import ViralAccountMaterialForm from '../components/ViralAccountMaterialForm'
import ViralContentMaterialForm from '../components/ViralContentMaterialForm'
import { api, ApiError, type DictData } from '../services/api'
import { hasPermission } from '../services/managementAccess'
import { materialApprovalApi } from '../services/materialApprovalApi'
import {
  materialApi,
  type Material,
  type MaterialFieldDefinition,
  type MaterialRecommendationAccount,
  type MaterialReferenceField,
  type MaterialReferenceTarget,
  type MaterialVersion,
  type MaterialType
} from '../services/materialApi'

const PAGE_SIZE = 20
type ViewKey = 'recommendation' | 'all' | 'favorite' | 'mine'

const statusLabel: Record<string, string> = {
  DRAFT: '草稿',
  IN_APPROVAL: '审批中',
  EFFECTIVE: '已生效',
  REJECTED: '已驳回',
  DISABLED: '已停用'
}
const sourceLabel: Record<string, string> = {
  MANUAL: '手工创建',
  IMPORT: '表格导入',
  CONTENT_REVIEW: '内容收录'
}

const errorText = (error: unknown) => error instanceof ApiError && error.code === 403
  ? '无权访问素材库'
  : error instanceof Error ? error.message : '素材加载失败，请重试'

function displaySnapshot(value: unknown): string {
  if (value == null || value === '') return '未填写'
  if (Array.isArray(value)) return value.map(displaySnapshot).filter(Boolean).join('、') || '未填写'
  if (typeof value === 'object') {
    const object = value as Record<string, unknown>
    if (object.label != null) return String(object.label)
    return Object.values(object).map(displaySnapshot).filter(item => item !== '未填写').join('、') || '未填写'
  }
  return String(value)
}

function nestedValue(source: unknown, path: string[]) {
  let current = source
  for (const key of path) {
    if (current == null || typeof current !== 'object' || Array.isArray(current)) return undefined
    current = (current as Record<string, unknown>)[key]
  }
  return current
}

function fieldFiles(version: MaterialVersion, key: string, groupIndex = -1) {
  return version.files.filter(file => file.fieldKey === key && file.groupIndex === groupIndex)
}

function FileValue({ version, fieldKey, groupIndex = -1 }: {
  version: MaterialVersion
  fieldKey: string
  groupIndex?: number
}) {
  const files = fieldFiles(version, fieldKey, groupIndex)
  if (!files.length) return <Typography.Text type="secondary">未上传</Typography.Text>
  return <div className="material-file-list">{files.map(file => {
    if (file.contentType.startsWith('image/') && file.previewUrl) {
      return <Image key={file.id} width={96} height={72} src={file.previewUrl} alt={file.name} className="material-file-image" />
    }
    if (file.contentType.startsWith('video/') && file.previewUrl) {
      return <video key={file.id} src={file.previewUrl} controls preload="metadata" className="material-file-video" />
    }
    return file.previewUrl
      ? <a key={file.id} href={file.previewUrl} target="_blank" rel="noreferrer">{file.name}</a>
      : <Typography.Text key={file.id}>{file.name}</Typography.Text>
  })}</div>
}

function FieldValue({ field, version, value, snapshot, path, groupIndex = -1 }: {
  field: MaterialFieldDefinition
  version: MaterialVersion
  value: unknown
  snapshot: unknown
  path: string
  groupIndex?: number
}) {
  if (['image', 'video', 'attachment'].includes(field.type)) {
    return <FileValue version={version} fieldKey={path} groupIndex={groupIndex} />
  }
  if (field.type === 'rich-text') {
    return value ? <SafeRichText html={String(value)} /> : <Typography.Text type="secondary">未填写</Typography.Text>
  }
  if (field.type === 'https-link' && value) {
    return <a href={String(value)} target="_blank" rel="noreferrer">{String(value)}</a>
  }
  if (['dict-single', 'dict-multi', 'employee', 'department'].includes(field.type)) {
    return <Typography.Text>{displaySnapshot(snapshot)}</Typography.Text>
  }
  return <Typography.Text className="material-field-text">{displaySnapshot(value)}</Typography.Text>
}

export function MaterialFields({ version }: { version: MaterialVersion }) {
  if (!version.fields.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内容字段" />
  const groups = [version.fields.slice(0, 1), version.fields.slice(1, 4), version.fields.slice(4, 8), version.fields.slice(8)]
  const titles = ['账号主截图', '账号详情', '编导拆解', '搭建建议']
  return <div className="material-four-column-layout">{groups.map((group, groupIndex) => <section className="material-detail-column" key={titles[groupIndex]}>
    <Typography.Title level={5}>{titles[groupIndex]}</Typography.Title><div className="material-field-grid">{group.map(field => {
    const value = version.values[field.key]
    const snapshot = version.dictSnapshot[field.key]
    if (field.type === 'repeat-group') {
      const rows = Array.isArray(value) ? value : []
      const snapshots = Array.isArray(snapshot) ? snapshot : []
      return <section className="material-field material-field-wide" key={field.key}>
        <Typography.Text type="secondary">{field.label}</Typography.Text>
        {rows.length ? <div className="material-repeat-list">{rows.map((row, index) => <div className="material-repeat-row" key={`${field.key}-${index}`}>
          {(field.children || []).map(child => <div className="material-repeat-field" key={child.key}>
            <Typography.Text type="secondary">{child.label}</Typography.Text>
            <FieldValue field={child} version={version}
              value={nestedValue(row, [child.key])}
              snapshot={nestedValue(snapshots[index], [child.key])}
              path={`${field.key}.${child.key}`} groupIndex={index} />
          </div>)}
        </div>)}</div> : <Typography.Text type="secondary">未填写</Typography.Text>}
      </section>
    }
    return <section className={`material-field${field.type === 'rich-text' ? ' material-field-wide' : ''}`} key={field.key}>
      <Typography.Text type="secondary">{field.label}</Typography.Text>
      <FieldValue field={field} version={version} value={value} snapshot={snapshot} path={field.key} />
    </section>
  })}</div></section>)}</div>
}

type SourceOption = { value: string; label: string; type: MaterialFieldDefinition['type'] }
type ReferencePreview = {
  fingerprint: string
  before: Record<string, unknown>
  after: Record<string, unknown>
}

const referenceFingerprint = (materialVersionId: number, targetContentVersionId: number,
  fields: MaterialReferenceField[]) => JSON.stringify({ materialVersionId, targetContentVersionId, fields })

function flattenSources(fields: MaterialFieldDefinition[], prefix = '', labelPrefix = ''): SourceOption[] {
  return fields.flatMap(field => {
    const key = prefix ? `${prefix}.${field.key}` : field.key
    const label = labelPrefix ? `${labelPrefix} / ${field.label}` : field.label
    return field.type === 'repeat-group'
      ? flattenSources(field.children || [], key, label)
      : [{ value: key, label, type: field.type }]
  })
}

function ReferenceDialog({ material, open, onClose, onSuccess }: {
  material?: Material
  open: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const { message } = App.useApp()
  const version = material?.currentVersion
  const [referenceTargets, setReferenceTargets] = useState<MaterialReferenceTarget[]>([])
  const [targetVersionId, setTargetVersionId] = useState<number>()
  const [targetLoading, setTargetLoading] = useState(false)
  const [targetError, setTargetError] = useState('')
  const [selectedSources, setSelectedSources] = useState<string[]>([])
  const [targets, setTargets] = useState<Record<string, string>>({})
  const [actions, setActions] = useState<Record<string, MaterialReferenceField['action']>>({})
  const [preview, setPreview] = useState<ReferencePreview>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const previewRequestRef = useRef(0)
  const targetRequestRef = useRef(0)
  const targetSearchTimerRef = useRef<number | undefined>(undefined)
  const sources = useMemo(() => version ? [
    { value: 'title', label: '素材标题', type: 'text' as const },
    { value: 'summary', label: '素材摘要', type: 'textarea' as const },
    ...(version.coverFileId ? [{ value: '__cover__', label: '素材封面', type: 'image' as const }] : []),
    ...flattenSources(version.fields)
  ] : [], [version])

  const targetOptions = (source?: SourceOption) => {
    if (!source) return []
    if (source.type === 'image') return [
      { value: 'coverSnapshotJson', label: '封面' },
      { value: 'deliverableSnapshotJson', label: '成品预览' }
    ]
    if (source.type === 'video') return [{ value: 'deliverableSnapshotJson', label: '成品预览' }]
    if (source.type === 'attachment' || source.type === 'repeat-group') return []
    if (source.type === 'https-link') return [
      { value: 'leadResourceUrl', label: '引流资料链接' },
      { value: 'scriptText', label: '脚本或正文' }
    ]
    return [
      { value: 'titleSnapshot', label: '标题' },
      { value: 'topicSnapshot', label: '选题' },
      { value: 'scriptText', label: '脚本或正文' }
    ]
  }

  const fields = useMemo<MaterialReferenceField[]>(() => selectedSources.map(sourceField => ({
    sourceField,
    targetField: targets[sourceField],
    action: actions[sourceField] || 'REPLACE'
  })).filter(field => field.targetField), [actions, selectedSources, targets])
  const fingerprint = version && targetVersionId
    ? referenceFingerprint(version.id, targetVersionId, fields)
    : ''
  const fingerprintRef = useRef(fingerprint)
  fingerprintRef.current = fingerprint
  const previewCurrent = Boolean(preview && preview.fingerprint === fingerprint)

  const clearPreview = () => {
    previewRequestRef.current += 1
    setPreview(undefined)
    setLoading(false)
  }

  const loadReferenceTargets = useCallback(async (keyword = '') => {
    const requestId = ++targetRequestRef.current
    setTargetLoading(true)
    setTargetError('')
    try {
      const result = await materialApi.referenceTargets({
        pageNo: 1,
        pageSize: 50,
        keyword: keyword.trim() || undefined
      })
      if (targetRequestRef.current === requestId) setReferenceTargets(result.list)
    } catch (cause) {
      if (targetRequestRef.current === requestId) {
        setReferenceTargets([])
        setTargetError(errorText(cause))
      }
    } finally {
      if (targetRequestRef.current === requestId) setTargetLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!open) return
    setError('')
    setTargetError('')
    clearPreview()
    setTargetVersionId(undefined)
    setSelectedSources([])
    setTargets({})
    setActions({})
    setReferenceTargets([])
    void loadReferenceTargets()
    return () => {
      if (targetSearchTimerRef.current) window.clearTimeout(targetSearchTimerRef.current)
      targetRequestRef.current += 1
    }
  }, [loadReferenceTargets, open, material?.id])

  const validate = () => {
    if (!targetVersionId) return '请选择生产内容草稿'
    if (!fields.length) return '请选择至少一个引用字段'
    if (new Set(fields.map(field => field.targetField)).size !== fields.length) return '同一目标字段只能选择一次'
    return ''
  }

  const runPreview = async () => {
    if (!material || !version) return
    const problem = validate()
    if (problem) return setError(problem)
    const requestId = ++previewRequestRef.current
    const requestedFingerprint = referenceFingerprint(version.id, targetVersionId!, fields)
    setLoading(true)
    setError('')
    setPreview(undefined)
    try {
      const result = await materialApi.previewReference(material.id, version.id, targetVersionId!, fields)
      if (previewRequestRef.current === requestId && fingerprintRef.current === requestedFingerprint) {
        setPreview({ ...result, fingerprint: requestedFingerprint })
      }
    } catch (cause) {
      if (previewRequestRef.current === requestId) setError(errorText(cause))
    } finally {
      if (previewRequestRef.current === requestId) setLoading(false)
    }
  }

  const submit = async () => {
    if (!material || !version) return
    const problem = validate()
    if (problem) return setError(problem)
    if (!previewCurrent) return setError('请先完成与当前选择一致的逐字段预览')
    setLoading(true)
    setError('')
    try {
      await materialApi.reference(material.id, version.id, targetVersionId!, fields)
      message.success('素材已引用到生产内容草稿')
      onSuccess()
      onClose()
    } catch (cause) {
      setError(errorText(cause))
    } finally {
      setLoading(false)
    }
  }

  return <Modal title="引用素材" width="min(900px, calc(100vw - 32px))" open={open} onCancel={onClose}
    okText="确认引用" onOk={() => void submit()} confirmLoading={loading}
    okButtonProps={{ disabled: !previewCurrent }}>
    <div className="material-reference-form">
      {error && <Alert type="error" showIcon message={error} />}
      {targetError && <Alert type="error" showIcon message={`生产内容候选加载失败：${targetError}`}
        action={<Button size="small" onClick={() => void loadReferenceTargets()}>重试</Button>} />}
      <div className="material-reference-targets">
        <Select showSearch filterOption={false} placeholder="搜索并选择生产内容草稿"
          value={targetVersionId} loading={targetLoading}
          notFoundContent={targetLoading ? '加载中...' : '暂无可引用的生产内容草稿'}
          onSearch={value => {
            if (targetSearchTimerRef.current) window.clearTimeout(targetSearchTimerRef.current)
            targetSearchTimerRef.current = window.setTimeout(() => void loadReferenceTargets(value), 250)
          }}
          onChange={value => { clearPreview(); setTargetVersionId(value) }}
          options={referenceTargets.map(item => ({
            value: item.contentVersionId,
            label: `${item.contentNo} · ${item.title} · V${item.versionNo}`
          }))} />
      </div>
      <List dataSource={sources} locale={{ emptyText: '当前素材没有可引用字段' }} renderItem={source => {
        const checked = selectedSources.includes(source.value)
        const media = source.type === 'image' || source.type === 'video'
        return <List.Item className="material-reference-row">
          <Checkbox checked={checked} onChange={event => {
            clearPreview()
            setSelectedSources(current => event.target.checked
              ? [...current, source.value]
              : current.filter(item => item !== source.value))
            if (event.target.checked && !targets[source.value]) {
              const firstTarget = targetOptions(source)[0]?.value
              if (firstTarget) setTargets(current => ({ ...current, [source.value]: firstTarget }))
              setActions(current => ({ ...current, [source.value]: media ? 'APPEND' : 'REPLACE' }))
            }
          }}>{source.label}</Checkbox>
          <Select disabled={!checked} value={targets[source.value]} placeholder="目标字段"
            options={targetOptions(source)} onChange={value => {
              clearPreview()
              setTargets(current => ({ ...current, [source.value]: value }))
            }} />
          <Select disabled={!checked} value={actions[source.value]} options={media
            ? [{ value: 'APPEND', label: '追加' }]
            : source.type === 'https-link' && targets[source.value] === 'leadResourceUrl'
              ? [{ value: 'REPLACE', label: '替换' }]
              : [{ value: 'REPLACE', label: '替换' }, { value: 'APPEND', label: '追加' }]}
            onChange={value => { clearPreview(); setActions(current => ({ ...current, [source.value]: value })) }} />
        </List.Item>
      }} />
      <Button icon={<SearchOutlined />} onClick={() => void runPreview()} loading={loading}>逐字段预览</Button>
      {preview && <Descriptions bordered size="small" column={1} items={Object.keys(preview.after).map(key => ({
        key,
        label: key,
        children: <div className="material-reference-preview"><span>{displaySnapshot(preview.before[key])}</span><strong>{displaySnapshot(preview.after[key])}</strong></div>
      }))} />}
    </div>
  </Modal>
}

export default function MaterialLibraryPage({ permissions = [], management = false }: { permissions?: string[]; management?: boolean }) {
  const [searchParams] = useSearchParams()
  const { message } = App.useApp()
  const [view, setView] = useState<ViewKey>(management ? 'all' : 'recommendation')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [materialTypeId, setMaterialTypeId] = useState<number>()
  const [accountId, setAccountId] = useState<number>()
  const [types, setTypes] = useState<MaterialType[]>([])
  const [accounts, setAccounts] = useState<MaterialRecommendationAccount[]>([])
  const [accountLoading, setAccountLoading] = useState(false)
  const [accountError, setAccountError] = useState('')
  const [rows, setRows] = useState<Material[]>([])
  const [selectedId, setSelectedId] = useState<number>()
  const [selected, setSelected] = useState<Material>()
  const [approvalTaskId, setApprovalTaskId] = useState<string>()
  const [approvalVersionId, setApprovalVersionId] = useState<number>()
  const [approvalAction, setApprovalAction] = useState<'approve' | 'reject'>()
  const [approvalReason, setApprovalReason] = useState('')
  const [approvalSaving, setApprovalSaving] = useState(false)
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [metadataError, setMetadataError] = useState('')
  const [metadataLoading, setMetadataLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [referenceOpen, setReferenceOpen] = useState(false)
  const [versionsOpen, setVersionsOpen] = useState(false)
  const [versions, setVersions] = useState<MaterialVersion[]>([])
  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'edit' | 'view'>('create')
  const [formTypeCode, setFormTypeCode] = useState<'viral_account' | 'viral_content'>('viral_account')
  const [dicts, setDicts] = useState<Record<string, Array<{ value: string; label: string }>>>({})
  const [dictError, setDictError] = useState('')
  const [dictLoading, setDictLoading] = useState(false)
  const loadMoreRef = useRef<HTMLDivElement>(null)
  const [hasMore, setHasMore] = useState(true)
  const accountRequestRef = useRef(0)
  const accountSearchTimerRef = useRef<number | undefined>(undefined)

  const loadMetadata = useCallback(async () => {
    setMetadataLoading(true)
    setMetadataError('')
    try {
      const typeRows = await materialApi.types()
      setTypes(typeRows.filter(type => type.status === 0))
    } catch (cause) {
      setTypes([])
      setMetadataError(errorText(cause))
    } finally {
      setMetadataLoading(false)
    }
  }, [])

  const loadDictionaries = useCallback(async () => {
    const dictTypes = ['zsjos_account_platform', 'zsjos_viral_content_type', 'zsjos_persona_type', 'zsjos_material_profession', 'zsjos_media_account_stage', 'zsjos_media_account_primary_problem']
    setDictLoading(true)
    setDictError('')
    const results = await Promise.allSettled(dictTypes.map(dictType => api.dictDataByType(dictType)))
    const next: Record<string, Array<{ value: string; label: string }>> = {}
    const failed: string[] = []
    results.forEach((result, index) => {
      const dictType = dictTypes[index]
      if (result.status === 'fulfilled') next[dictType] = result.value.map((item: DictData) => ({ value: item.value, label: item.label }))
      else failed.push(dictType)
    })
    setDicts(next)
    if (failed.length) setDictError(`字典加载失败：${failed.join('、')}`)
    setDictLoading(false)
  }, [])

  const loadAccounts = useCallback(async (keyword = '') => {
    const requestId = ++accountRequestRef.current
    setAccountLoading(true)
    setAccountError('')
    try {
      const result = await materialApi.recommendationAccounts(keyword.trim() || undefined)
      if (accountRequestRef.current === requestId) setAccounts(result)
    } catch (cause) {
      if (accountRequestRef.current === requestId) {
        setAccounts([])
        setAccountError(errorText(cause))
      }
    } finally {
      if (accountRequestRef.current === requestId) setAccountLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadMetadata()
    void loadAccounts()
    void loadDictionaries()
    return () => {
      if (accountSearchTimerRef.current) window.clearTimeout(accountSearchTimerRef.current)
      accountRequestRef.current += 1
    }
  }, [loadAccounts, loadDictionaries, loadMetadata])

  const loadDetail = useCallback(async (id: number) => {
    setSelectedId(id)
    setDetailLoading(true)
    setDetailError('')
    try {
      setSelected(await materialApi.get(id))
    } catch (cause) {
      setSelected(undefined)
      setDetailError(errorText(cause))
    } finally {
      setDetailLoading(false)
    }
  }, [])

  useEffect(() => {
    const materialId = Number(searchParams.get('materialId'))
    const taskId = searchParams.get('taskId') || undefined
    const versionId = Number(searchParams.get('versionId')) || undefined
    if (materialId) void loadDetail(materialId)
    setApprovalTaskId(taskId)
    setApprovalVersionId(versionId)
  }, [loadDetail, searchParams])

  const load = useCallback(async (targetPage = 1, preferredId?: number) => {
    if (view === 'recommendation' && !accountId) {
      setRows([])
      setSelected(undefined)
      setSelectedId(undefined)
      setTotal(0)
      setPage(1)
      setError('')
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      const result = await materialApi.page({
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
        keyword: keyword || undefined,
        materialTypeId,
        accountId: view === 'recommendation' ? accountId : undefined,
        recommendation: view === 'recommendation' || undefined,
        favorite: view === 'favorite' || undefined,
        mine: view === 'mine' || undefined
      })
      setRows(targetPage === 1 ? result.list : current => [...current, ...result.list.filter(item => !current.some(existing => existing.id === item.id))])
      setTotal(result.total)
      setPage(targetPage)
      setHasMore(targetPage * PAGE_SIZE < result.total && result.list.length > 0)
      if (preferredId && result.list.some(item => item.id === preferredId)) await loadDetail(preferredId)
      else if (targetPage === 1) { setSelectedId(undefined); setSelected(undefined) }
    } catch (cause) {
      if (targetPage === 1) { setRows([]); setSelected(undefined); setSelectedId(undefined) }
      setError(errorText(cause))
    } finally {
      setLoading(false)
    }
  }, [accountId, keyword, loadDetail, materialTypeId, view])

  useEffect(() => { void load(1) }, [view, keyword, materialTypeId, accountId])
  useEffect(() => {
    const target = loadMoreRef.current
    if (!target) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting && hasMore && !loading) void load(page + 1)
    }, { rootMargin: '480px' })
    observer.observe(target)
    return () => observer.disconnect()
  }, [hasMore, loading, load, page])

  const interact = async (kind: 'like' | 'favorite') => {
    if (!selected) return
    try {
      const result = kind === 'like'
        ? await materialApi.toggleLike(selected.id)
        : await materialApi.toggleFavorite(selected.id)
      setSelected(current => current ? {
        ...current,
        [kind === 'like' ? 'liked' : 'favorited']: result.active,
        [kind === 'like' ? 'likeCount' : 'favoriteCount']: result.count
      } : current)
      setRows(current => current.map(item => item.id === selected.id ? {
        ...item,
        [kind === 'like' ? 'liked' : 'favorited']: result.active,
        [kind === 'like' ? 'likeCount' : 'favoriteCount']: result.count
      } : item))
      if (view === 'favorite' && kind === 'favorite' && !result.active) void load(page)
    } catch (cause) {
      message.error(errorText(cause))
    }
  }

  const openVersions = async () => {
    if (!selected) return
    setVersionsOpen(true)
    setVersions([])
    try { setVersions(await materialApi.versions(selected.id)) }
    catch (cause) { message.error(errorText(cause)) }
  }

  const viralType = types.find(type => type.code === 'viral_account')
  const viralContentType = types.find(type => type.code === 'viral_content')
  const toggleCategoryFilter = (type: MaterialType | undefined, checked: boolean) => {
    setMaterialTypeId(checked ? type?.id : undefined)
  }
  const openCreate = () => {
    if (!viralType) return message.warning('爆款账号模板尚未发布')
    setSelected(undefined)
    setFormTypeCode('viral_account')
    setFormMode('create')
    setFormOpen(true)
  }
  const openContentCreate = () => {
    if (!viralContentType) return message.warning('爆款内容模板尚未发布')
    setSelected(undefined); setFormTypeCode('viral_content'); setFormMode('create'); setFormOpen(true)
  }
  const openEdit = () => {
    if (!selected || !viralType || !viralContentType) return
    if (![viralType.id, viralContentType.id].includes(selected.materialTypeId)) return
    setFormTypeCode(selected.materialTypeId === viralContentType.id ? 'viral_content' : 'viral_account')
    setFormMode('edit')
    setFormOpen(true)
  }
  const openView = () => {
    if (!selected || !viralType || selected.materialTypeId !== viralType.id) return
    setFormTypeCode('viral_account')
    setFormMode('view')
    setFormOpen(true)
  }
  const canUpdate = selected?.availableActions.includes('UPDATE') && hasPermission(permissions, 'zsjos:material:update')
  const onFormSaved = async () => {
    setFormOpen(false)
    await load(page, selectedId)
  }

  const currentVersion = selected?.currentVersion
  const canApprove = Boolean(approvalTaskId && approvalVersionId && selected &&
    permissions.includes('zsjos:material-approval:approve'))
  const canReject = Boolean(approvalTaskId && approvalVersionId && selected &&
    permissions.includes('zsjos:material-approval:reject'))
  const decideApproval = async () => {
    if (!approvalAction || !approvalTaskId || !approvalVersionId || !approvalReason.trim()) return
    setApprovalSaving(true)
    try {
      await materialApprovalApi.decide(approvalAction, approvalVersionId, approvalTaskId, approvalReason.trim())
      message.success(approvalAction === 'approve' ? '审批已通过' : '素材已驳回')
      setApprovalAction(undefined); setApprovalReason('')
      await loadDetail(selectedId || selected?.id || 0)
    } catch (cause) { message.error(errorText(cause)) }
    finally { setApprovalSaving(false) }
  }
  const openMaterial = async (id: number) => {
    setFormMode('view')
    setFormOpen(true)
    await loadDetail(id)
  }
  return <section className="workspace-page material-library-page">
    <header className="material-library-filter-shell">
      <div className="material-library-toolbar">
        <Tabs className="material-library-view-tabs" activeKey={view} onChange={key => setView(key as ViewKey)} items={[
          { key: 'recommendation', label: '推荐' },
          { key: 'all', label: '全部' },
          { key: 'favorite', label: '收藏' },
          { key: 'mine', label: '我的素材' }
        ]} />
        <div className="material-library-category-checks">
          <Checkbox checked={materialTypeId === viralType?.id} disabled={!viralType}
            onChange={event => toggleCategoryFilter(viralType, event.target.checked)}>爆款账号</Checkbox>
          <Checkbox checked={materialTypeId === viralContentType?.id} disabled={!viralContentType}
            onChange={event => toggleCategoryFilter(viralContentType, event.target.checked)}>爆款内容</Checkbox>
        </div>
        <Input.Search className="material-library-search" allowClear value={keywordInput} onChange={event => setKeywordInput(event.target.value)}
          onSearch={value => setKeyword(value.trim())} placeholder="搜索标题、摘要或内容" />
        <Select className="material-library-select" allowClear placeholder="素材类型" value={materialTypeId} onChange={setMaterialTypeId}
          loading={metadataLoading} disabled={metadataLoading || Boolean(metadataError)}
          options={types.map(type => ({ value: type.id, label: type.name }))} />
        {view === 'recommendation' && <Select className="material-library-select" allowClear showSearch filterOption={false} placeholder="搜索账号画像"
          loading={accountLoading}
          value={accountId} onChange={setAccountId} options={accounts.map(account => ({
            value: account.id,
            label: account.nickname || account.accountNo
          }))} notFoundContent={accountLoading ? '加载中...' : '暂无可用账号'}
          onSearch={value => {
            if (accountSearchTimerRef.current) window.clearTimeout(accountSearchTimerRef.current)
            accountSearchTimerRef.current = window.setTimeout(() => void loadAccounts(value), 250)
          }} />}
        <Tooltip title="刷新"><Button className="material-library-refresh" icon={<ReloadOutlined />} onClick={() => void load(page, selectedId)} /></Tooltip>
        {hasPermission(permissions, 'zsjos:material:create') && <Space className="material-library-actions"><Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>创建爆款账号</Button><Button type="primary" icon={<PlusOutlined />} onClick={openContentCreate}>创建爆款内容</Button></Space>}
      </div>
    </header>
    {metadataError && <Alert type="error" showIcon message={`素材类型加载失败：${metadataError}`}
      action={<Button size="small" onClick={() => void loadMetadata()}>重试</Button>} />}
    {dictError && <Alert type="error" showIcon message={dictError}
      action={<Button size="small" loading={dictLoading} onClick={() => void loadDictionaries()}>重试</Button>} />}
    {view === 'recommendation' && accountError && <Alert type="error" showIcon
      message={`账号候选加载失败：${accountError}`}
      action={<Button size="small" onClick={() => void loadAccounts()}>重试</Button>} />}
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(page)}>重试</Button>} />}
    <div className="material-library-layout">
      <aside className="material-library-list-pane">
        <div className="material-library-scroll">
          {loading && !rows.length ? <Skeleton active paragraph={{ rows: 10 }} /> : rows.length ? rows.map(item =>
            <button type="button" key={item.id} className={`material-library-item${selectedId === item.id ? ' active' : ''}`}
              onClick={() => void openMaterial(item.id)}>
              <span className="material-library-cover">{item.coverPreviewUrl
                ? <img src={item.coverPreviewUrl} alt="" loading="lazy" /> : <BookOutlined />}</span>
              <span className="material-library-item-copy">
                <strong>{item.title}</strong>
                <span>{item.materialTypeName} · {item.materialNo}</span>
                <span><LikeOutlined /> {item.likeCount}　<LinkOutlined /> {item.referenceCount}</span>
              </span>
            </button>) : !error && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={view === 'recommendation' && !accountId ? '请选择账号查看推荐素材' : '暂无素材'} />}
          <div ref={loadMoreRef} className="material-library-load-more">{loading && rows.length ? '加载中...' : hasMore ? '' : rows.length ? '已加载全部素材' : ''}</div>
        </div>
      </aside>
    </div>
    <ReferenceDialog material={selected} open={referenceOpen} onClose={() => setReferenceOpen(false)}
      onSuccess={() => { if (selectedId) void loadDetail(selectedId) }} />
    <Drawer title="素材版本" open={versionsOpen} onClose={() => setVersionsOpen(false)} width="min(720px, 100vw)">
      <List dataSource={versions} locale={{ emptyText: '暂无版本记录' }} renderItem={item => <List.Item>
        <List.Item.Meta title={<Space>V{item.versionNo}<Tag>{statusLabel[item.status] || item.status}</Tag></Space>}
          description={<Space direction="vertical" size={0}>
            <span>{item.title}</span>
            <span>{item.rejectionReason || (item.effectiveAt ? <DateTimeText value={item.effectiveAt} /> : '尚未生效')}</span>
          </Space>} />
      </List.Item>} />
    </Drawer>
    <Modal open={Boolean(approvalAction)} title={approvalAction === 'approve' ? '通过素材审批' : '驳回素材'} confirmLoading={approvalSaving}
      onCancel={() => !approvalSaving && setApprovalAction(undefined)} onOk={() => void decideApproval()}>
      <Input.TextArea value={approvalReason} onChange={event => setApprovalReason(event.target.value)} maxLength={1000} rows={4} placeholder="请填写审批意见" />
    </Modal>
    {(viralType || viralContentType) && <Drawer
      title={formMode === 'create' ? (formTypeCode === 'viral_content' ? '创建爆款内容拆解' : '创建爆款账号拆解') : formMode === 'edit' ? '编辑爆款拆解' : '查看爆款拆解'}
      open={formOpen} onClose={() => setFormOpen(false)} width="min(1480px, 100vw)" destroyOnClose>
      {formMode === 'view' && selected && <div className="material-layout-summary">
        <div className="material-layout-summary-info"><span>负责人：{selected.ownerName || '未记录'}</span><span>当前版本：V{selected.currentVersion?.versionNo || '-'}</span><span>调用量：{selected.referenceCount}</span><span>点赞量：{selected.likeCount}</span></div>
        {(canApprove || canReject) && <div className="material-layout-summary-actions">
          {canApprove && <Button type="primary" onClick={() => setApprovalAction('approve')}>通过审批</Button>}
          {canReject && <Button danger onClick={() => setApprovalAction('reject')}>驳回审批</Button>}
        </div>}
      </div>}
      {formTypeCode === 'viral_content'
        ? viralContentType && <ViralContentMaterialForm key={`${formMode}-${selected?.id || 'new'}`} mode={formMode} type={viralContentType}
          material={selected} dicts={dicts} submitAllowed={hasPermission(permissions, 'zsjos:material:submit')} onClose={() => setFormOpen(false)} onSaved={() => void onFormSaved()} />
        : viralType && <ViralAccountMaterialForm key={`${formMode}-${selected?.id || 'new'}`} mode={formMode} type={viralType}
          material={selected} dicts={dicts} submitAllowed={hasPermission(permissions, 'zsjos:material:submit')} onClose={() => setFormOpen(false)} onSaved={() => void onFormSaved()} />}
    </Drawer>}
  </section>
}
