import { filterSelectDisplayProps } from "../components/filterSelectDisplay"
import MaterialCover from '../components/MaterialCover'
import MaterialSearchContent, { MaterialHighlight } from '../components/MaterialSearchContent'
import ResourceLink from '../components/ResourceLink'
import {
  AppstoreOutlined,
  BarsOutlined,
  BookOutlined,
  CloseOutlined,
  HeartFilled,
  HeartOutlined,
  HistoryOutlined,
  LikeFilled,
  LikeOutlined,
  LinkOutlined,
  MoreOutlined,
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
  Dropdown,
  Empty,
  Image,
  Input,
  List,
  Modal,
  Select,
  Segmented,
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
import { DICT_TYPE, STORAGE_KEYS } from '../constants'
import SafeRichText from '../components/SafeRichText'
import BpmProcessPanel from '../components/bpm/BpmProcessPanel'
import ViralAccountMaterialForm from '../components/ViralAccountMaterialForm'
import ViralContentMaterialForm from '../components/ViralContentMaterialForm'
import { api, ApiError, type DictData } from '../services/api'
import { materialLifecycleApi } from '../services/materialLifecycleApi'
import { hasPermission } from '../services/managementAccess'
import {
  materialApi,
  type Material,
  type MaterialFieldDefinition,
  type MaterialReferenceField,
  type MaterialReferenceTarget,
  type MaterialVersion,
  type MaterialType
} from '../services/materialApi'

const PAGE_SIZE = 20
type ViewKey = 'all' | 'favorite' | 'mine'
type LayoutMode = 'large' | 'compact'

const statusLabel: Record<string, string> = {
  DRAFT: '草稿／待提交',
  IN_APPROVAL: '待审核／审批中',
  EFFECTIVE: '已通过',
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
    return <ResourceLink href={String(value)} variant="resource" />
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

export default function MaterialLibraryPage({ permissions = [] }: { permissions?: string[]; management?: boolean }) {
  const [searchParams] = useSearchParams()
  const { message } = App.useApp()
  const [layoutMode, setLayoutMode] = useState<LayoutMode>(() => {
    try { return localStorage.getItem(STORAGE_KEYS.MATERIAL_BROWSE_LAYOUT) === 'compact' ? 'compact' : 'large' }
    catch { return 'large' }
  })
  const changeLayout = (value: LayoutMode) => {
    setLayoutMode(value)
    try { localStorage.setItem(STORAGE_KEYS.MATERIAL_BROWSE_LAYOUT, value) }
    catch { /* Browsing remains available when storage is disabled. */ }
  }
  const [view, setView] = useState<ViewKey>(searchParams.get('view') === 'mine' ? 'mine' : 'all')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [searchRevision, setSearchRevision] = useState(0)
  const searchMaterials = (value: string) => {
    setKeywordInput(value.trim()); setKeyword(value.trim())
    setSearchRevision(current => current + 1)
  }
  const [materialTypeId, setMaterialTypeId] = useState<number>()
  const [accountType, setAccountType] = useState<string>()
  const [profession, setProfession] = useState<string>()
  const [accountStage, setAccountStage] = useState<string>()
  const [versionStatus, setVersionStatus] = useState<MaterialVersion['status']>()
  const [types, setTypes] = useState<MaterialType[]>([])
  const [rows, setRows] = useState<Material[]>([])
  const [selectedId, setSelectedId] = useState<number>()
  const [selected, setSelected] = useState<Material>()
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
  const [versionsLoading, setVersionsLoading] = useState(false)
  const [versionsError, setVersionsError] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelError, setCancelError] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Material>()
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const deletingRef = useRef(false)
  const [dicts, setDicts] = useState<Record<string, Array<{ value: string; label: string }>>>({})
  const [dictError, setDictError] = useState('')
  const [dictLoading, setDictLoading] = useState(false)
  const loadMoreRef = useRef<HTMLDivElement>(null)
  const [hasMore, setHasMore] = useState(true)
  const listRequestRef = useRef(0)
  const detailRequestRef = useRef(0)

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
    const dictTypes = ['zsjos_account_platform', 'zsjos_viral_content_type', DICT_TYPE.PERSONA_TYPE, DICT_TYPE.MATERIAL_PROFESSION, DICT_TYPE.MEDIA_ACCOUNT_STAGE, DICT_TYPE.MEDIA_ACCOUNT_PRIMARY_PROBLEM]
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

  useEffect(() => {
    void loadMetadata()
    void loadDictionaries()
  }, [loadDictionaries, loadMetadata])

  const loadDetail = useCallback(async (id: number) => {
    const requestId = ++detailRequestRef.current
    setSelectedId(id)
    setSelected(undefined)
    setDetailLoading(true)
    setDetailError('')
    try {
      const result = await (view === 'mine' ? materialApi.get(id) : materialApi.browse(id))
      if (requestId === detailRequestRef.current) setSelected(result)
    } catch (cause) {
      if (requestId !== detailRequestRef.current) return
      setSelected(undefined)
      setDetailError(errorText(cause))
    } finally {
      if (requestId === detailRequestRef.current) setDetailLoading(false)
    }
  }, [view])

  const consumedMaterialLink = useRef<string | undefined>(undefined)
  useEffect(() => {
    const link = searchParams.get('materialId') || ''
    if (consumedMaterialLink.current === link) return
    consumedMaterialLink.current = link
    const materialId = Number(link)
    if (materialId) { setFormOpen(true); void loadDetail(materialId) }
  }, [loadDetail, searchParams])

  const load = useCallback(async (targetPage = 1, preferredId?: number) => {
    const requestId = ++listRequestRef.current
    setLoading(true)
    setError('')
    try {
      const result = await (view === 'mine' ? materialApi.page : materialApi.browsePage)({
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
        keyword: keyword || undefined,
        materialTypeId,
        accountType,
        profession,
        accountStage,
        favorite: view === 'favorite' || undefined,
        mine: view === 'mine' || undefined,
        sortBy: view === 'all' || view === 'mine' ? 'createTime' : undefined,
        versionStatus: view === 'mine' ? versionStatus : undefined
      })
      if (requestId !== listRequestRef.current) return
      setRows(targetPage === 1 ? result.list : current => [...current, ...result.list.filter(item => !current.some(existing => existing.id === item.id))])
      setTotal(result.total)
      setPage(targetPage)
      setHasMore(targetPage * PAGE_SIZE < result.total && result.list.length > 0)
      if (preferredId && result.list.some(item => item.id === preferredId)) await loadDetail(preferredId)
      else if (targetPage === 1) { setSelectedId(undefined); setSelected(undefined) }
    } catch (cause) {
      if (requestId !== listRequestRef.current) return
      if (targetPage === 1) { setRows([]); setSelected(undefined); setSelectedId(undefined) }
      setError(errorText(cause))
    } finally {
      if (requestId === listRequestRef.current) setLoading(false)
    }
  }, [keyword, loadDetail, materialTypeId, accountType, profession, accountStage, view, versionStatus, searchRevision])

  useEffect(() => { setRows([]); setHasMore(false); void load(1) }, [load])
  useEffect(() => {
    const target = loadMoreRef.current
    if (!target) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting && hasMore && !loading && !error) void load(page + 1)
    }, { rootMargin: '480px' })
    observer.observe(target)
    return () => observer.disconnect()
  }, [hasMore, loading, load, page, error])

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
    setVersionsLoading(true)
    setVersionsError('')
    try { setVersions(await (view === 'mine' ? materialApi.versions(selected.id) : materialApi.browseVersions(selected.id))) }
    catch (cause) { setVersionsError(errorText(cause)) }
    finally { setVersionsLoading(false) }
  }

  const viralType = types.find(type => type.code === 'viral_account')
  const viralContentType = types.find(type => type.code === 'viral_content')
  const displayedTypeCode = types.find(type => type.id === selected?.materialTypeId)?.code
  const onFormSaved = async () => {
    setFormOpen(false)
    setEditing(false)
    await load(1, selectedId)
  }

  const currentVersion = selected?.currentVersion
  const openMaterial = async (id: number) => {
    setEditing(false)
    setFormOpen(true)
    await loadDetail(id)
  }
  const canCancel = view === 'mine' && currentVersion?.status === 'IN_APPROVAL'
    && Boolean(currentVersion.processInstanceId) && selected?.availableActions.includes('CANCEL')
    && hasPermission(permissions, 'bpm:process-instance:cancel')
  const canEdit = view === 'mine' && currentVersion?.status !== 'IN_APPROVAL'
    && selected?.availableActions.includes('UPDATE') && hasPermission(permissions, 'zsjos:material:update')
  const canDelete = (material: Material) => view === 'mine'
    && material.availableActions.includes('DELETE_DRAFT') && hasPermission(permissions, 'zsjos:material:delete-draft')
  const requestDelete = (material: Material) => {
    if (!canDelete(material) || deletingRef.current || (formOpen && editing)) return
    setDeleteError(''); setDeleteTarget(material)
  }
  const deleteDraft = async () => {
    if (!deleteTarget || !canDelete(deleteTarget) || deletingRef.current) return
    deletingRef.current = true; setDeleting(true); setDeleteError('')
    try {
      await materialApi.deleteDraft(deleteTarget.id, deleteTarget.version)
      // Invalidate in-flight reads before refreshing so an old response cannot restore a deleted draft.
      listRequestRef.current += 1; detailRequestRef.current += 1
      setRows(current => current.filter(item => item.id !== deleteTarget.id))
      setSelected(undefined); setSelectedId(undefined); setDetailLoading(false)
      setFormOpen(false); setEditing(false); setVersionsOpen(false); setVersions([])
      setDeleteTarget(undefined)
      message.success('草稿已删除')
      await load(1)
    } catch (cause) { setDeleteError(errorText(cause)) }
    finally { deletingRef.current = false; setDeleting(false) }
  }
  const cancelApproval = async () => {
    if (!canCancel || !currentVersion?.processInstanceId || !selectedId || !cancelReason.trim() || cancelling) return
    setCancelling(true); setCancelError('')
    try {
      await materialLifecycleApi.cancel(currentVersion.processInstanceId, cancelReason.trim())
      setCancelOpen(false); setEditing(false)
      await load(1, selectedId)
      // A status filter can exclude the newly restored draft; keep its detail available.
      await loadDetail(selectedId)
    } catch (cause) { setCancelError(errorText(cause)) }
    finally { setCancelling(false) }
  }
  return <section className={`workspace-page material-library-page material-library-${layoutMode}${view === 'mine' ? ' material-library-mine' : ''}`}>
    <header className="material-library-filter-shell">
      <div className="material-library-toolbar">
        <Tabs className="material-library-view-tabs" activeKey={view} onChange={key => {
          detailRequestRef.current += 1
          setSelected(undefined); setSelectedId(undefined); setFormOpen(false)
          setVersionsOpen(false); setVersions([])
          setView(key as ViewKey)
        }} items={[
          { key: 'all', label: '全部' },
          { key: 'favorite', label: '收藏' },
          { key: 'mine', label: '我的素材' }
        ]} />
        <Input.Search className="material-library-search" allowClear value={keywordInput} onChange={event => setKeywordInput(event.target.value)}
          onSearch={searchMaterials} placeholder="搜索素材" aria-label="搜索素材" title="搜索素材编号、标题、摘要或可检索字段" />
        {[
          { label: '适配账号类型', dictType: DICT_TYPE.PERSONA_TYPE, value: accountType, onChange: setAccountType },
          { label: '适配业务定位', dictType: DICT_TYPE.MATERIAL_PROFESSION, value: profession, onChange: setProfession },
          { label: '适配账号期段', dictType: DICT_TYPE.MEDIA_ACCOUNT_STAGE, value: accountStage, onChange: setAccountStage }
        ].map(filter => <Select key={filter.dictType} className="material-library-adaptation-select" aria-label={filter.label}
          allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={filter.label} value={filter.value} onChange={filter.onChange}
          loading={dictLoading} disabled={dictLoading || !dicts[filter.dictType]}
          options={dicts[filter.dictType] || []} classNames={{ popup: { root: 'material-library-filter-options' } }}
          {...filterSelectDisplayProps} labelRender={({ label }) => <span className="material-library-filter-label">{label}</span>} />)}
        <Select className="material-library-select" allowClear placeholder="素材类型" value={materialTypeId} onChange={setMaterialTypeId}
          loading={metadataLoading} disabled={metadataLoading || Boolean(metadataError)}
          options={types.map(type => ({ value: type.id, label: type.name }))}  {...filterSelectDisplayProps}/>
        <Segmented<LayoutMode> className="material-library-layout-switch" aria-label="素材布局" value={layoutMode} onChange={changeLayout}
          options={[
            { value: 'large', label: <span title="大图布局" aria-label="大图布局"><AppstoreOutlined /><span className="material-layout-label">大图</span></span> },
            { value: 'compact', label: <span title="紧凑布局" aria-label="紧凑布局"><BarsOutlined /><span className="material-layout-label">紧凑</span></span> }
          ]} />
        <Dropdown trigger={['click']} menu={{ items: [
          { key: 'clear', label: '清空适配筛选', disabled: !accountType && !profession && !accountStage,
            onClick: () => { setAccountType(undefined); setProfession(undefined); setAccountStage(undefined) } },
          { key: 'refresh', label: '刷新素材', icon: <ReloadOutlined />, onClick: () => void load(1, selectedId) }
        ] }}><Button aria-label="更多素材操作" className="material-library-actions" icon={<MoreOutlined />} /></Dropdown>
      </div>
    </header>
    {view === 'mine' && <div className="material-mine-filters">
      <Select aria-label="审核状态" allowClear placeholder="全部审核状态" value={versionStatus} onChange={setVersionStatus}
        options={(['DRAFT', 'IN_APPROVAL', 'EFFECTIVE', 'REJECTED'] as const).map(value => ({ value, label: statusLabel[value] }))} />
      <Typography.Text type="secondary">按当前修订版本筛选；新版本审核期间，旧生效版本仍可浏览。</Typography.Text>
    </div>}
    {metadataError && <Alert type="error" showIcon message={`素材类型加载失败：${metadataError}`}
      action={<Button size="small" onClick={() => void loadMetadata()}>重试</Button>} />}
    {dictError && <Alert type="error" showIcon message={dictError}
      action={<Button size="small" loading={dictLoading} onClick={() => void loadDictionaries()}>重试</Button>} />}
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(rows.length ? page + 1 : 1)}>重试</Button>} />}
    <div className="material-library-layout">
      <aside className="material-library-list-pane">
        <div className="material-library-scroll">
          {loading && !rows.length ? <Skeleton active paragraph={{ rows: 10 }} /> : rows.length ? rows.map(item =>
            <article key={item.id} className={`material-library-item${selectedId === item.id ? ' active' : ''}`}>
              <button type="button" className="material-library-open" aria-label={`查看素材：${item.currentVersion?.title || item.title}`}
                onClick={() => void openMaterial(item.id)}><MaterialCover src={item.coverPreviewUrl} /></button>
              <div className="material-library-item-copy">
                <button type="button" className="material-library-title" onClick={() => void openMaterial(item.id)}>
                  <strong><MaterialHighlight text={item.currentVersion?.title || item.title} keyword={keyword} /></strong>
                </button>
                <span className="material-card-status"><Tag color={item.currentVersion?.status === 'IN_APPROVAL' ? 'processing' : item.currentVersion?.status === 'REJECTED' ? 'error' : item.currentVersion?.status === 'EFFECTIVE' ? 'success' : 'default'}>{statusLabel[item.currentVersion?.status || item.status]}</Tag>
                  </span>
                <span>{item.materialTypeName} · {item.materialNo}</span>
                {view === 'mine' ? <>
                  <span>审核人：{item.currentVersion?.status === 'IN_APPROVAL' ? item.currentVersion.pendingApproverNames?.join('、') || '等待流程分配' : '—'}</span>
                  {item.currentVersion?.submittedAt && <span>最近提交：<DateTimeText value={item.currentVersion.submittedAt} /></span>}
                  {item.currentVersion?.rejectionReason && <span>驳回原因：{item.currentVersion.rejectionReason}</span>}
                  {item.currentVersion?.effectiveAt && <span>生效时间：<DateTimeText value={item.currentVersion.effectiveAt} /></span>}
                  {canDelete(item) && <Button type="link" danger disabled={deleting || (formOpen && editing)}
                    onClick={() => requestDelete(item)}>删除草稿</Button>}
                </> : <span><LikeOutlined /> {item.likeCount}　<LinkOutlined /> {item.referenceCount}</span>}
                <MaterialSearchContent material={item} keyword={keyword} onSearch={searchMaterials}
                  onLocate={() => void openMaterial(item.id)} />
              </div>
            </article>) : !error && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无素材" />}
        </div>
        <div ref={loadMoreRef} className="material-library-load-more">{loading && rows.length ? '加载中...' : hasMore ? '' : rows.length ? `已加载全部 ${total} 条素材` : ''}</div>
      </aside>
    </div>
    <ReferenceDialog material={selected} open={referenceOpen} onClose={() => setReferenceOpen(false)}
      onSuccess={() => { if (selectedId) void loadDetail(selectedId) }} />
    <Drawer title="素材版本" open={versionsOpen} onClose={() => setVersionsOpen(false)} width="min(720px, 100vw)">
      {versionsError ? <Alert type="error" showIcon title={versionsError} action={<Button onClick={() => void openVersions()}>重试</Button>} />
        : <List loading={versionsLoading} dataSource={versions} locale={{ emptyText: '暂无版本记录' }} renderItem={item => <List.Item>
        <List.Item.Meta title={<Space>V{item.versionNo}<Tag>{statusLabel[item.status] || item.status}</Tag></Space>}
          description={<Space direction="vertical" size={0}>
            <span>{item.title}</span>
            <span>{item.rejectionReason || (item.effectiveAt ? <DateTimeText value={item.effectiveAt} /> : '尚未生效')}</span>
          </Space>} />
      </List.Item>} />}
    </Drawer>
    <Modal title="确认删除草稿？" open={Boolean(deleteTarget)} zIndex={1200} okText="确认删除" cancelText="取消"
      confirmLoading={deleting} okButtonProps={{ danger: true, disabled: !deleteTarget || !canDelete(deleteTarget) }}
      cancelButtonProps={{ disabled: deleting }} closable={!deleting} keyboard={!deleting} mask={{ closable: false }}
      onCancel={() => { if (!deletingRef.current) setDeleteTarget(undefined) }} onOk={() => void deleteDraft()}>
      {deleteTarget && <p>将删除“{deleteTarget.title}”（{deleteTarget.materialNo}），删除后无法在草稿箱恢复。</p>}
      {deleteError && <Alert type="error" showIcon message={deleteError} />}
    </Modal>
    <Modal title="撤回审批" open={cancelOpen} okText="确认撤回" cancelText="取消"
      confirmLoading={cancelling} okButtonProps={{ disabled: !cancelReason.trim() }}
      cancelButtonProps={{ disabled: cancelling }} closable={!cancelling} mask={{ closable: false }}
      onCancel={() => setCancelOpen(false)} onOk={() => void cancelApproval()}>
      <Input.TextArea aria-label="撤回原因" placeholder="请填写撤回原因" value={cancelReason} disabled={cancelling}
        onChange={event => setCancelReason(event.target.value)} />
      {cancelError && <Alert type="error" showIcon message={cancelError} />}
    </Modal>
    <Drawer
      title="查看爆款拆解"
      open={formOpen} onClose={() => setFormOpen(false)} width="min(1480px, 100vw)" destroyOnClose>
      {detailLoading ? <Skeleton active /> : detailError
        ? <Alert type="error" showIcon message={detailError} action={<Button onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>} />
        : <>
      {selected && <div className="material-layout-summary">
        <div className="material-layout-summary-info"><Tag>{statusLabel[currentVersion?.status || selected.status]}</Tag><span>负责人：{selected.ownerName || '未记录'}</span><span>当前版本：V{currentVersion?.versionNo || '-'}</span>
          {view === 'mine' && currentVersion?.status === 'IN_APPROVAL' && <span>审核人：{currentVersion.pendingApproverNames?.join('、') || '等待流程分配'}</span>}
          {view === 'mine' && currentVersion?.rejectionReason && <span>驳回原因：{currentVersion.rejectionReason}</span>}
          {view === 'mine' && selected.currentEffectiveVersionId && selected.currentDraftVersionId && <span>旧生效版本继续可用</span>}
        </div>
        <div className="material-layout-summary-actions">
          {canCancel && <Button onClick={() => { setCancelReason(''); setCancelError(''); setCancelOpen(true) }}>撤回审批</Button>}
          {canDelete(selected) && !editing && <Button danger disabled={deleting} onClick={() => requestDelete(selected)}>删除草稿</Button>}
          {canEdit && !editing && <Button type="primary" onClick={() => setEditing(true)}>{currentVersion?.status === 'DRAFT' ? '继续编辑' : '修改并重新送审'}</Button>}
          <Button icon={<HistoryOutlined />} onClick={() => void openVersions()}>版本记录</Button>
        </div>
      </div>}
      {view === 'mine' && currentVersion?.status === 'IN_APPROVAL' && currentVersion.processInstanceId
        && hasPermission(permissions, 'bpm:process-instance:query') && <BpmProcessPanel
          compact decisionOnly loadComments={hasPermission(permissions, 'bpm:task:query')} allowDecision={false} canUpdate={false} users={[]}
          processInstanceId={currentVersion.processInstanceId} />}
      {displayedTypeCode === 'viral_content'
        ? viralContentType && <ViralContentMaterialForm key={`${selected?.id}-${editing}`} mode={editing && canEdit ? 'edit' : 'view'} type={viralContentType}
          material={selected} dicts={dicts} submitAllowed={hasPermission(permissions, 'zsjos:material:submit')} onClose={() => setFormOpen(false)} onSaved={() => void onFormSaved()} />
        : displayedTypeCode === 'viral_account' && viralType && <ViralAccountMaterialForm key={`${selected?.id}-${editing}`} mode={editing && canEdit ? 'edit' : 'view'} type={viralType}
          material={selected} dicts={dicts} submitAllowed={hasPermission(permissions, 'zsjos:material:submit')} onClose={() => setFormOpen(false)} onSaved={() => void onFormSaved()} />}
      </>}
    </Drawer>
  </section>
}
