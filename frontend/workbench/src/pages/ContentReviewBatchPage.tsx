import { locateContentError, submitContentReview, contentSaveError, contentResultUncertain, type ContentSavePhase } from '../services/contentReviewErrors'
import ContentApprovalDraft from '../components/ContentApprovalDraft'
import { restoreDraftAccounts, restoreDraftWorks } from '../services/contentReviewDraft'
import { prepareContentReviewWorks } from '../services/contentReviewAttachments'
import ResourceLinkInput from '../components/ResourceLinkInput'
import {
  ClockCircleOutlined,
  CloseCircleOutlined,
  LinkOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Empty,
  Form,
  Image,
  Input,
  List,
  Modal,
  Pagination,
  Radio,
  Select,
  Skeleton,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography
} from 'antd'
import dayjs from 'dayjs'
import { useLocation } from 'react-router-dom'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import DateTimeText from '../components/DateTimeText'
import BpmProcessPanel from '../components/bpm/BpmProcessPanel'
import { api, type SimpleUser } from '../services/api'
import { hasPermission } from '../services/managementAccess'
import {
  contentReviewApi,
  materialApi,
  type ContentReviewBatch,
  type ContentReviewCandidate,
  type ContentReviewItem,
} from '../services/materialApi'

const PAGE_SIZE = 20
const CANDIDATE_PAGE_SIZE = 10
const statusText: Record<string, string> = {
  DRAFT: '草稿',
  REVISION_DRAFT: '已有修订草稿',
  RESUBMITTED: '已重新提交',
  DIRECTOR_REVIEW: '编导审核',
  FINAL_REVIEW: '终审',
  COMPLETED: '已完成',
  NEED_MODIFY: '待修改',
  REJECTED: '已退回',
  CANCELLED: '已取消',
  RETURNED: '退回',
  READY_TO_PUBLISH: '待发布',
  PUBLISHED: '已发布',
  APPROVED: '通过'
}

const errorText = (error: unknown) => error instanceof Error ? error.message : '内容审核加载失败，请重试'

const snapshotText = (snapshot: Record<string, unknown>, key: string, fallback = '未记录') => {
  const value = snapshot[key]
  return value == null || value === '' ? fallback : String(value)
}

const httpsSnapshotUrl = (snapshot: Record<string, unknown>, key: string) => {
  const value = snapshot[key]
  return typeof value === 'string' && /^https:\/\//i.test(value) ? value : undefined
}

/** 审批只读取提交时的素材快照，因此这里不依赖素材库的实时状态。 */
const referenceMaterials = (item: ContentReviewItem) =>
  (Array.isArray(item.contentSnapshot.materialRefs) ? item.contentSnapshot.materialRefs : [])
    .map(ref => ref as Record<string, unknown>)
    .map(ref => ({
      materialId: String(ref.materialId ?? ''),
      materialNo: String(ref.materialNo || ''),
      title: String(ref.title || '素材'),
      coverPreviewUrl: typeof ref.coverPreviewUrl === 'string' ? ref.coverPreviewUrl : undefined,
    }))

/**
 * 账号档案区，每个账号一块。数据全部取自提交时冻结的 contextSnapshot.accountSnapshots，
 * 不实时查账号表：否则账号改名或换阶段会改写历史审批记录。
 */
function AccountProfiles({ batch }: { batch: ContentReviewBatch }) {
  const snapshots = (Array.isArray(batch.contextSnapshot.accountSnapshots)
    ? batch.contextSnapshot.accountSnapshots : []) as Array<Record<string, unknown>>
  if (!snapshots.length) return null
  const text = (value: unknown) => {
    const result = value === null || value === undefined ? '' : String(value).trim()
    return result || '—'
  }
  const problems = (value: unknown) => Array.isArray(value)
    ? value.map(problem => typeof problem === 'object' && problem
      ? String((problem as Record<string, unknown>).labelSnapshot
        ?? (problem as Record<string, unknown>).label ?? '')
      : String(problem)).filter(Boolean).join('、')
    : ''
  return <div className="content-review-account-profiles">{snapshots.map(snapshot => {
    const id = text(snapshot.id)
    const bottleneck = text(snapshot.bottleneckLabel) !== '—'
      ? text(snapshot.bottleneckLabel) : text(problems(snapshot.primaryProblems))
    return <dl className="content-review-account-profile" key={id}>
      <div className="content-review-account-heading">
        <Typography.Text strong>账号 {text(snapshot.accountNo)}</Typography.Text>
      </div>
      <div><dt>账号名称</dt><dd>{text(snapshot.nickname)}</dd></div>
      <div><dt>发布平台</dt><dd>{text(snapshot.platformLabel ?? snapshot.platformValue)}</dd></div>
      <div><dt>平台账号</dt><dd>{text(snapshot.platformAccountId)}</dd></div>
      <div><dt>当前期段</dt><dd>{text(snapshot.sStageLabel ?? snapshot.sStage)}</dd></div>
      <div><dt>账号状态</dt><dd>{text(snapshot.currentStatusLabel ?? snapshot.currentStatusValue)}</dd></div>
      {/* 只展示姓名：内部用户编号对审核人没有意义，服务端已按编号解析昵称。 */}
      <div><dt>责任运营人员</dt><dd>{text(snapshot.operatorName)}</dd></div>
      <div><dt>责任编导</dt><dd>{text(snapshot.directorName)}</dd></div>
      <div><dt>承接产品目标</dt><dd>{text(snapshot.productGoal)}</dd></div>
      <div><dt>主要产品形式</dt><dd>{text(snapshot.productFormLabel)}</dd></div>
      <div><dt>当前发布节奏</dt><dd>{text(snapshot.publishFrequency)}</dd></div>
      <div><dt>当前瓶颈</dt><dd>{bottleneck}</dd></div>
    </dl>
  })}</div>
}

/** 作品封面：放在卡片左栏，与右侧字段表格并排，对齐纸质审核表的版式。 */
function ItemCover({ item }: { item: ContentReviewItem }) {
  const cover = item.files?.find(file => file.fieldKey === 'cover' && file.previewUrl
    && file.contentType.startsWith('image/'))
  return <div className="content-review-item-cover">
    {cover
      ? <Image src={cover.previewUrl} alt={`作品封面图：${cover.originalName}`} />
      : <Typography.Text type="secondary">暂无封面图</Typography.Text>}
    <span>作品封面图</span>
  </div>
}

/** 成品文件。封面已在卡片左栏单独展示，这里不再重复。 */
function ReviewFiles({ item }: { item: ContentReviewItem }) {
  const files = item.files?.filter(file => file.fieldKey !== 'cover') ?? []
  if (!files.length) return null
  return <div className="content-review-media">{files.map(file => {
    const label = '审核附件'
    if (file.contentType.startsWith('image/') && file.previewUrl) {
      return <div className="content-review-media-item" key={file.id}>
        <Image width={128} height={84} src={file.previewUrl} alt={`${label}：${file.originalName}`} />
        <span>{label} · {file.originalName}</span>
      </div>
    }
    if (file.contentType.startsWith('video/') && file.previewUrl) {
      return <div className="content-review-media-item" key={file.id}>
        <video src={file.previewUrl} controls preload="metadata" aria-label={`${label}：${file.originalName}`} />
        <span>{label} · {file.originalName}</span>
      </div>
    }
    return file.previewUrl
      ? <a key={file.id} href={file.previewUrl} target="_blank" rel="noreferrer">{label} · {file.originalName}</a>
      : <Typography.Text key={file.id}>{label} · {file.originalName}</Typography.Text>
  })}</div>
}

function CreateBatchDialog({ open, onClose, onCreated }: {
  open: boolean
  onClose: () => void
  onCreated: (id: number) => void
}) {
  const { message } = App.useApp()
  const [contents, setContents] = useState<ContentReviewCandidate[]>([])
  const [selected, setSelected] = useState<ContentReviewCandidate[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const selectedAccount = selected[0]?.accountId

  const loadCandidates = useCallback(async (targetPage: number, search: string) => {
    setLoading(true)
    setError('')
    try {
      const result = await contentReviewApi.candidates({
        pageNo: targetPage,
        pageSize: CANDIDATE_PAGE_SIZE,
        keyword: search || undefined
      })
      setContents(result.list)
      setTotal(result.total)
      setPage(targetPage)
    } catch (cause) {
      setContents([])
      setTotal(0)
      setError(errorText(cause))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!open) return
    setSelected([])
    setKeywordInput('')
    setKeyword('')
    setError('')
    void loadCandidates(1, '')
  }, [loadCandidates, open])

  const toggle = (content: ContentReviewCandidate, checked: boolean) => {
    setError('')
    if (!checked) {
      setSelected(current => current.filter(item => item.contentVersionId !== content.contentVersionId))
      return
    }
    if (selected.length >= 20) return setError('一个批次最多选择 20 条内容')
    setSelected(current => [...current, content])
  }

  const create = async () => {
    const contentVersionIds = selected.map(item => item.contentVersionId)
    if (!contentVersionIds.length) return setError('请选择 1 至 20 条完整内容版本')
    setLoading(true)
    setError('')
    try {
      const id = await contentReviewApi.create(contentVersionIds)
      message.success('审核批次已创建')
      onCreated(id)
      onClose()
    } catch (cause) {
      setError(errorText(cause))
    } finally {
      setLoading(false)
    }
  }

  return <Modal title="创建审核批次" open={open} onCancel={onClose} onOk={() => void create()}
    okText="创建批次" confirmLoading={loading} width="min(820px, calc(100vw - 32px))">
    {error && <Alert type="error" showIcon message={error} />}
    <div className="content-review-picker-toolbar">
      <Input.Search allowClear value={keywordInput} onChange={event => setKeywordInput(event.target.value)}
        onSearch={value => { const search = value.trim(); setKeyword(search); void loadCandidates(1, search) }}
        placeholder="搜索内容编号或标题" />
      <Typography.Text type="secondary">已选 {selected.length}/20</Typography.Text>
    </div>
    {loading && !contents.length ? <Skeleton active /> : <List className="content-review-picker" dataSource={contents}
      locale={{ emptyText: '暂无可选内容' }} renderItem={content => {
        const checked = selected.some(item => item.contentVersionId === content.contentVersionId)
        const disabled = !checked && selectedAccount != null && selectedAccount !== content.accountId
        return <List.Item>
          <Checkbox checked={checked} disabled={disabled} onChange={event => toggle(content, event.target.checked)}>
            <span className="content-review-picker-copy"><strong>{content.title}</strong>
              <span>{content.contentNo} · 账号 {content.accountId} · 当前 V{content.currentVersionNo}</span></span>
          </Checkbox>
        </List.Item>
      }} />}
    {total > CANDIDATE_PAGE_SIZE && <Pagination simple current={page} pageSize={CANDIDATE_PAGE_SIZE}
      total={total} onChange={value => void loadCandidates(value, keyword)} />}
  </Modal>
}

function DecisionEditor({ batch, item, stage, onSaved, onDirtyChange }: {
  batch: ContentReviewBatch
  item: ContentReviewItem
  stage: 'director' | 'final'
  onDirtyChange: (id: number, dirty: boolean) => void
  onSaved: () => void
}) {
  const { message } = App.useApp()
  const existingDecision = stage === 'director' ? item.directorDecision : item.finalDecision
  const existingComment = stage === 'director' ? item.directorComment : item.finalComment
  const [decision, setDecision] = useState<'APPROVED' | 'RETURNED' | undefined>(existingDecision)
  const [comment, setComment] = useState(existingComment || '')
  const [collect, setCollect] = useState(Boolean(item.collectMaterial))
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setDecision(existingDecision)
    setComment(existingComment || '')
    setCollect(Boolean(item.collectMaterial))
  }, [existingComment, existingDecision, item.collectMaterial])

  useEffect(() => {
    onDirtyChange(item.id, decision !== existingDecision || comment !== (existingComment || '')
      || stage === 'final' && collect !== Boolean(item.collectMaterial))
  }, [item.id, decision, existingDecision, comment, existingComment, stage, collect, item.collectMaterial, onDirtyChange])
  useEffect(() => () => onDirtyChange(item.id, false), [item.id, onDirtyChange])

  const save = async () => {
    if (!batch.currentTaskId) return message.warning('当前审核任务已变化，请刷新待办后确认')
    if (!decision) return message.warning('请选择审核结论')
    if (decision === 'RETURNED' && !comment.trim()) return message.warning('退回时必须填写原因')
    setLoading(true)
    try {
      const data = { taskId: batch.currentTaskId, expectedVersion: item.version, decision, comment: comment.trim() || undefined }
      if (stage === 'director') await contentReviewApi.directorDecision(batch.id, item.id, data)
      else await contentReviewApi.finalDecision(batch.id, item.id, { ...data, collectMaterial: decision === 'APPROVED' && collect })
      message.success('本条结论已保存')
      onSaved()
    } catch (cause) {
      message.error(errorText(cause))
    } finally {
      setLoading(false)
    }
  }

  return <div className="content-review-decision">
    <Radio.Group value={decision} onChange={event => setDecision(event.target.value)} optionType="button"
      options={[{ value: 'APPROVED', label: '通过' }, { value: 'RETURNED', label: '退回' }]} />
    <Input.TextArea rows={2} maxLength={2000} value={comment} onChange={event => setComment(event.target.value)}
      placeholder={decision === 'RETURNED' ? '填写退回原因（必填）' : '审核意见（可选）'} />
    <Button type="primary" loading={loading} onClick={() => void save()}
      disabled={!decision}>{existingDecision ? '更新本条结论' : '保存本条结论'}</Button>
    {stage === 'final' && decision === 'APPROVED' && <span className="content-review-collect">收录素材库
      <Switch checked={collect} onChange={setCollect} /></span>}
  </div>
}

export function DraftEditDialog({ batch, open, onClose, onSaved }: { batch: ContentReviewBatch; open: boolean; onClose: () => void; onSaved: (id: number) => void }) {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const activeDraftId = useRef<number | undefined>(undefined)
  const activeDraftVersion = useRef<number | undefined>(undefined)
  const saveLock = useRef(false)
  const [saveError, setSaveError] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const [purposeOptions, setPurposeOptions] = useState<Array<{ value: string; label: string }>>([])
  const [formatOptions, setFormatOptions] = useState<Array<{ value: string; label: string }>>([])
  const [optionsLoading, setOptionsLoading] = useState(false)
  const [optionsError, setOptionsError] = useState('')
  const restoredAccounts = useMemo(() => restoreDraftAccounts(batch), [batch])
  const loadOptions = useCallback(async () => {
    setOptionsLoading(true); setOptionsError('')
    api.invalidateDictDataCache()
    try {
      const [purpose, format] = await Promise.all([api.dictDataByType('zsjos_content_purpose'), api.dictDataByType('zsjos_content_format')])
      setPurposeOptions(purpose.map(item => ({ value: item.value, label: item.label })))
      setFormatOptions(format.map(item => ({ value: item.value, label: item.label })))
    } catch (cause) { setOptionsError(errorText(cause)) }
    finally { setOptionsLoading(false) }
  }, [])
  useEffect(() => { if (open) void loadOptions() }, [open, loadOptions])
  useEffect(() => {
    if (!open) return
    activeDraftId.current = batch.status === 'DRAFT' ? batch.id : undefined
    activeDraftVersion.current = batch.version
    setSaveError(''); setUncertain(false)
    form.resetFields()
    form.setFieldsValue({ accountIds: restoredAccounts.accountIds, accountSnapshots: restoredAccounts.accountSnapshots, works: restoreDraftWorks(batch) })
  }, [batch, form, open, restoredAccounts])
  const save = async (submitAfterSave: boolean) => {
    if (saveLock.current || loading || optionsLoading || optionsError || uncertain) return
    saveLock.current = true
    let saved = false
    let phase: ContentSavePhase = 'prepare'
    setSaveError('')
    try {
      await form.validateFields()
      setLoading(true)
      const current = activeDraftId.current ? await contentReviewApi.get(activeDraftId.current) : undefined
      if (current && current.status !== 'DRAFT') {
        setUncertain(true)
        throw new Error('本轮状态已变化，请保留当前输入并查询审批状态')
      }
      const values = form.getFieldsValue(true)
      const works = await prepareContentReviewWorks(values.works, (index, field, items) => form.setFieldValue(['works', index, field], items))
      const request = {
        studentPersonId: batch.studentPersonId!,
        accountIds: batch.accountIds?.length ? batch.accountIds : [batch.accountId],
        accountSnapshots: values.accountSnapshots,
        expectedVersion: activeDraftVersion.current,
        works: works.map((work: Record<string, unknown>) => ({ ...work, plannedPublishAt: work.plannedPublishAt ? (work.plannedPublishAt as dayjs.Dayjs).format('YYYY-MM-DDTHH:mm:ss') : undefined }))
      }
      phase = 'save'
      const id = current
        ? await contentReviewApi.saveStudentDraft(current.id, request)
        : await contentReviewApi.resubmitFromStudent(batch.id, request)
      activeDraftId.current = id
      saved = true
      phase = 'refresh'
      const savedBatch = await contentReviewApi.get(id)
      activeDraftVersion.current = savedBatch.version
      savedBatch.items.forEach((item, index) => {
        form.setFieldValue(['works', index, 'sourceContentId'], item.contentId)
        form.setFieldValue(['works', index, 'sourceVersionId'], item.contentVersionId)
      })
      phase = 'submit'
      if (submitAfterSave) await submitContentReview(id, savedBatch.version)
      message.success(submitAfterSave ? '已提交审批' : '修改已保存')
      onClose(); onSaved(id)
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields) {
        locateContentError(form, cause)
        setSaveError(contentSaveError(cause, saved, phase))
        setUncertain(current => current || contentResultUncertain(cause, phase))
      }
    } finally { setLoading(false); saveLock.current = false }
  }
  return <Modal title="编辑内容审批草稿" open={open} maskClosable={false} onCancel={() => { if (!loading) onClose() }} closable={!loading} keyboard={!loading} cancelButtonProps={{ disabled: loading }} footer={[<Button key="cancel" disabled={loading} onClick={onClose}>取消</Button>, <Button key="save" disabled={loading || uncertain || optionsLoading || Boolean(optionsError)} onClick={() => void save(false)}>保存修改</Button>, <Button key="submit" type="primary" loading={loading} disabled={loading || uncertain || optionsLoading || Boolean(optionsError)} onClick={() => void save(true)}>{batch.status === 'DRAFT' ? '提交审批' : '重新提交审批'}</Button>]} width="min(1100px, calc(100vw - 32px))" styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }}>
    <Form form={form} layout="vertical" disabled={loading || optionsLoading}>
      {saveError && <Alert type="error" showIcon message={saveError} description={uncertain ? '请先保留填写内容，关闭后从草稿列表或历史轮次查询最新状态，再继续操作。' : undefined} />}
      {batch.items.some(item => item.directorComment || item.finalComment) && <Alert type="info" showIcon
        message="上一轮审核意见" description={batch.items.map((item, index) => <div key={item.id}>
          作品 {index + 1}：{[item.directorComment && `编导：${item.directorComment}`, item.finalComment && `终审：${item.finalComment}`].filter(Boolean).join('；') || '本条无意见'}
        </div>)} />}
      <Typography.Paragraph type="secondary">保存修改不会发起审批；点击重新提交审批会保存修改并直接发起新一轮审批。明确失败时可修正后重试；结果待确认时先查询批次状态。</Typography.Paragraph>
      {optionsError && <Alert type="error" showIcon message={optionsError} action={<Button onClick={() => void loadOptions()}>重试</Button>} />}
      <ContentApprovalDraft optionsLoading={optionsLoading} onReloadOptions={() => void loadOptions()} disabled={loading || optionsLoading || Boolean(optionsError)} lockAccounts accounts={restoredAccounts.accounts} purposeOptions={purposeOptions} formatOptions={formatOptions} />
    </Form>
  </Modal>
}

export default function ContentReviewBatchPage({ permissions = [] }: { permissions?: string[] }) {
  const { message, modal } = App.useApp()
  const [rows, setRows] = useState<ContentReviewBatch[]>([])
  const [selectedId, setSelectedId] = useState<number>()
  const [selected, setSelected] = useState<ContentReviewBatch>()
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string>()
  const [mine, setMine] = useState(false)
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [completeStage, setCompleteStage] = useState<'director' | 'final'>()
  const [dirtyItems, setDirtyItems] = useState<Record<number, boolean>>({})
  const updateDirty = useCallback((id: number, dirty: boolean) => setDirtyItems(current =>
    Boolean(current[id]) === dirty ? current : { ...current, [id]: dirty }), [])
  const [completeDecision, setCompleteDecision] = useState<'APPROVED' | 'RETURNED'>('APPROVED')
  const [completeReason, setCompleteReason] = useState('本级逐条结论已完成')
  const [completeLoading, setCompleteLoading] = useState(false)
  const [publishItem, setPublishItem] = useState<ContentReviewItem>()
  const [draftEditOpen, setDraftEditOpen] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [historyRows, setHistoryRows] = useState<ContentReviewBatch[]>([])
  const [taskUsers, setTaskUsers] = useState<SimpleUser[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const submitLock = useRef(false)
  const [publishForm] = Form.useForm<{ platformUrl: string; publishedAt: dayjs.Dayjs }>()

  const location = useLocation()
  const linkedBatchId = Number(new URLSearchParams(location.search).get('batchId')) || undefined
  const loadDetail = useCallback(async (id: number) => {
    setSelectedId(id)
    setDetailLoading(true)
    setDetailError('')
    try { setSelected(await contentReviewApi.get(id)) }
    catch (cause) { setSelected(undefined); setDetailError(errorText(cause)) }
    finally { setDetailLoading(false) }
  }, [])

  const load = useCallback(async (targetPage = 1, preferredId?: number) => {
    setLoading(true)
    setError('')
    try {
      const result = await contentReviewApi.page({ pageNo: targetPage, pageSize: PAGE_SIZE,
        keyword: keyword || undefined, status, mine })
      setRows(result.list)
      setTotal(result.total)
      setPage(targetPage)
      const nextId = preferredId ?? linkedBatchId ?? result.list[0]?.id
      if (nextId) await loadDetail(nextId)
      else { setSelectedId(undefined); setSelected(undefined) }
    } catch (cause) {
      setRows([]); setSelected(undefined); setSelectedId(undefined); setError(errorText(cause))
    } finally { setLoading(false) }
  }, [keyword, loadDetail, mine, status, linkedBatchId])

  useEffect(() => { void load(1) }, [keyword, status, mine, linkedBatchId])
  useEffect(() => {
    // 加签、转办、委派、抄送需要人员候选；无处理权限时不请求。
    if (!hasPermission(permissions, 'bpm:task:update')) return
    let cancelled = false
    void api.simpleUsers()
      .then(list => { if (!cancelled) setTaskUsers(list) })
      .catch(() => { if (!cancelled) setTaskUsers([]) })
    return () => { cancelled = true }
  }, [permissions])

  const submitBatch = async () => {
    if (!selected || submitLock.current) return
    submitLock.current = true; setSubmitting(true)
    try {
      await submitContentReview(selected.id, selected.version)
      message.success('批次已提交')
      await load(page, selected.id)
    } catch (cause) { message.error(contentSaveError(cause, true)) }
    finally { submitLock.current = false; setSubmitting(false) }
  }

  const saveDraft = async () => {
    if (!selected || !selected.studentPersonId) return
    try {
      const works = selected.items.map(item => {
        const snapshot = item.contentSnapshot || {}
        const cover = item.files?.find(file => file.fieldKey === 'cover')
        return {
          coverFileId: cover?.infraFileId,
          purposeValue: snapshot.purposeValue,
          purposeLabelSnapshot: snapshot.purposeLabelSnapshot,
          formatValue: snapshot.formatValue,
          formatLabelSnapshot: snapshot.formatLabelSnapshot,
          title: snapshot.titleSnapshot || snapshot.title,
          scriptText: snapshot.scriptText,
          detailUrl: snapshot.detailUrl,
          leadResourceUrl: snapshot.leadResourceUrl,
          commentHook: snapshot.commentHook,
          referenceContentVersionId: snapshot.referenceContentVersionId,
          plannedPublishAt: snapshot.plannedPublishAt
        }
      })
      const id = await contentReviewApi.saveStudentDraft(selected.id, {
        studentPersonId: selected.studentPersonId,
        accountIds: selected.accountIds?.length ? selected.accountIds : [selected.accountId],
        works
      })
      message.success('草稿已保存为新版本')
      await load(page, id)
    } catch (cause) { message.error(errorText(cause)) }
  }

  const showHistory = async () => {
    if (!selected) return
    setHistoryOpen(true); setHistoryLoading(true); setHistoryError('')
    try { setHistoryRows(await contentReviewApi.history(selected.id)) }
    catch (cause) { setHistoryError(errorText(cause)); setHistoryRows([]) }
    finally { setHistoryLoading(false) }
  }

  const cancelBatch = () => {
    if (!selected) return
    modal.confirm({
      title: '取消审核批次',
      content: '取消后，批次中的内容版本会立即释放，可重新选择组批。',
      okText: '确认取消',
      okButtonProps: { danger: true },
      cancelText: '保留批次',
      onOk: async () => {
        try {
          await contentReviewApi.cancel(selected.id, selected.version)
          message.success('批次已取消')
          await load(page)
        } catch (cause) {
          message.error(errorText(cause))
        }
      }
    })
  }

  const complete = async () => {
    if (!selected || !completeStage || completeLoading) return
    if (!selected.currentTaskId) { message.warning('当前审核任务已变化，请刷新待办后确认'); return }
    if (!completeReason.trim()) { message.warning('请填写本轮审核意见'); return }
    setCompleteLoading(true)
    try {
      const data = { expectedVersion: selected.version, taskId: selected.currentTaskId, decision: completeDecision, reason: completeReason.trim() }
      if (completeStage === 'director') await contentReviewApi.completeDirector(selected.id, data)
      else await contentReviewApi.completeFinal(selected.id, data)
      message.success(completeDecision === 'RETURNED' ? '本批次已退回运营修改' : completeStage === 'director' ? '编导已通过，进入终审' : '终审已通过，内容待发布')
      setCompleteStage(undefined)
      await load(page, selected.id)
    } catch (cause) { message.error(errorText(cause)) }
    finally { setCompleteLoading(false) }
  }

  const publish = async () => {
    if (!selected || !publishItem) return
    try {
      const values = await publishForm.validateFields()
      setCompleteLoading(true)
      await contentReviewApi.registerPublished(selected.id, publishItem.id, {
        platformUrl: values.platformUrl.trim(),
        publishedAt: values.publishedAt.format('YYYY-MM-DDTHH:mm:ss'),
        expectedContentVersion: publishItem.contentRecordVersion
      })
      message.success('发布结果已登记')
      setPublishItem(undefined)
      publishForm.resetFields()
      await load(page, selected.id)
    } catch (cause) {
      locateContentError(publishForm, cause)
      if (cause instanceof Error) message.error(errorText(cause))
    } finally { setCompleteLoading(false) }
  }

  const stage = selected?.availableActions.includes('DIRECTOR_DECIDE') ? 'director'
    : selected?.availableActions.includes('FINAL_DECIDE') ? 'final' : undefined
  const canCreate = hasPermission(permissions, 'zsjos:content-review:create')
  const canSeeAll = hasPermission(permissions, 'zsjos:content-review:query-all')
  const canUpdateTask = hasPermission(permissions, 'bpm:task:update')

  /**
   * 本级"交卷"动作。后端 requireDirectorDecisions / requireFinalDecisions 会再校验一次，
   * 这里的进度与禁用只是提前告知，避免填不齐就提交后才报错。
   */
  const advanceAction = useMemo(() => {
    if (!selected || !stage) return undefined
    const items = selected.items
    const requiredItems = stage === 'director' ? items : items.filter(item => item.directorDecision === 'APPROVED')
    const pending = requiredItems.filter(item => !(stage === 'director' ? item.directorDecision : item.finalDecision))
    const returned = items.some(item => item.directorDecision === 'RETURNED'
      || stage === 'final' && item.finalDecision === 'RETURNED')
    const prefix = stage === 'director' ? 'DIRECTOR' : 'FINAL'
    const pendingReason = Object.values(dirtyItems).some(Boolean) ? '存在未保存的逐条修改，请先保存'
      : pending.length ? `还有 ${pending.length} 条内容未保存结论` : undefined
    const trigger = (decision: 'APPROVED' | 'RETURNED') => {
      setCompleteDecision(decision)
      setCompleteReason('')
      setCompleteStage(stage)
    }
    return {
      label: stage === 'director' ? '通过本次审批' : '通过终审',
      progress: `已完成 ${requiredItems.length - pending.length}/${requiredItems.length} 条结论`,
      disabledReason: pendingReason || (returned ? '存在不通过内容，请退回运营修改'
        : !selected.availableActions.includes(`${prefix}_APPROVE`) ? '当前任务不允许通过，请刷新确认' : undefined),
      onTrigger: () => trigger('APPROVED'),
      alternative: {
        label: '退回运营修改',
        disabledReason: pendingReason || (!returned ? '至少一条内容不通过时才可整批退回'
          : !selected.availableActions.includes(`${prefix}_RETURN`) ? '当前任务不允许退回，请刷新确认' : undefined),
        onTrigger: () => trigger('RETURNED')
      }
    }
  }, [selected, stage, dirtyItems])

  return <section className="workspace-page content-review-page">
    <header className="content-review-filter-shell">
      <div><Typography.Title level={4}>内容审核</Typography.Title>
        <Typography.Text type="secondary">{total} 个批次</Typography.Text></div>
      <div className="content-review-toolbar">
        <Input.Search allowClear value={keywordInput} onChange={event => setKeywordInput(event.target.value)}
          onSearch={value => setKeyword(value.trim())} placeholder="搜索批次编号" />
        <Select allowClear value={status} onChange={setStatus} placeholder="审核状态" options={Object.entries(statusText)
          .filter(([key]) => ['DRAFT', 'DIRECTOR_REVIEW', 'FINAL_REVIEW', 'COMPLETED', 'NEED_MODIFY', 'PUBLISHED', 'REJECTED', 'CANCELLED'].includes(key))
          .map(([value, label]) => ({ value, label }))} />
        {canSeeAll && <Checkbox checked={mine} onChange={event => setMine(event.target.checked)}>只看我的</Checkbox>}
        <Tooltip title="刷新"><Button icon={<ReloadOutlined />} onClick={() => void load(page, selectedId)} /></Tooltip>
        {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建批次</Button>}
      </div>
    </header>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(page)}>重试</Button>} />}
    <div className="content-review-layout">
      <aside className="content-review-list-pane">
        <div className="content-review-scroll">{loading && !rows.length ? <Skeleton active /> : rows.length ? rows.map(item =>
          <button type="button" key={item.id} className={`content-review-list-item${item.id === selectedId ? ' active' : ''}`}
            onClick={() => void loadDetail(item.id)}>
            <span><strong>{item.batchNo}</strong><Tag>{statusText[item.status] || item.status}</Tag></span>
            <span>账号 {item.accountId} · {item.items?.length || 0} 条内容</span>
            <span>{item.directorName ? `责任编导：${item.directorName}` : '尚未提交'}</span>
          </button>) : !error && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审核批次" />}</div>
        {total > PAGE_SIZE && <Pagination simple current={page} pageSize={PAGE_SIZE} total={total}
          onChange={value => void load(value)} />}
      </aside>
      <main className="content-review-detail-pane">
        {detailLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : detailError
          ? <Alert type="error" showIcon message={detailError} action={selectedId
            ? <Button size="small" onClick={() => void loadDetail(selectedId)}>重试</Button> : undefined} />
          : selected ? <>
            <div className="content-review-heading"><div>
              <Space><Tag>{statusText[selected.status] || selected.status}</Tag><Typography.Text>{selected.batchNo}</Typography.Text></Space>
              <Typography.Title level={4}>{selected.studentPersonId ? `学员 ${selected.studentPersonId} · ` : ''}账号 {(selected.accountIds?.length ? selected.accountIds.join('、') : selected.accountId)} 的内容审核</Typography.Title>
              <Typography.Text type="secondary">运营：{selected.operatorName || selected.operatorUserId}　编导：{selected.directorName || '提交时确定'}</Typography.Text>
            </div><Space wrap>
              {selected.studentPersonId && <Button onClick={() => void showHistory()}>查看历史轮次</Button>}
              {selected.availableActions.includes('SUBMIT') && <><Button icon={<ReloadOutlined />} onClick={() => setDraftEditOpen(true)}>编辑并保存草稿</Button><Button type="primary" loading={submitting} icon={<SendOutlined />} onClick={() => void submitBatch()}>提交审批</Button></>}
              {selected.availableActions.includes('RESUBMIT') && <Button type="primary" onClick={() => setDraftEditOpen(true)}>修改后重新提交</Button>}
              {selected.availableActions.includes('CANCEL') && <Button danger icon={<CloseCircleOutlined />} onClick={cancelBatch}>取消批次</Button>}
              {/* 「完成编导审核 / 完成终审」推进 BPM 任务，已移至下方审批流程面板的动作栏。 */}
            </Space></div>
            <div className="content-review-meta">
              <span>当前节点：{selected.currentStage || '无'}</span>
              <span>提交时间：<DateTimeText value={selected.submittedAt} /></span>
              <span>内容数量：{selected.items.length}</span>
            </div>
            {/*
              工作区分两栏：左侧是待审内容流，右侧固定轨承载审批流程与交卷动作。
              右轨 sticky，滚内容时流程状态和提交按钮始终可见；窄屏折到内容下方。
            */}
            <div className="content-review-workspace">
            <div className="content-review-content-column">
            <AccountProfiles batch={selected} />
            {/* 分节标题：让"账号档案 → 逐条审核 → 交卷"的顺序在页面上可见。 */}
            <div className="content-review-section-heading">
              <Typography.Text strong>待审内容</Typography.Text>
              <Typography.Text type="secondary">
                共 {selected.items.length} 条{stage ? '，逐条给出结论后在右侧提交' : ''}
              </Typography.Text>
            </div>
            <div className="content-review-items">{selected.items.map(item => <section className="content-review-item" key={item.id}>
              <div className="content-review-item-heading"><div>
                <Typography.Text strong>{snapshotText(item.contentSnapshot, 'titleSnapshot', snapshotText(item.contentSnapshot, 'title'))}</Typography.Text>
                <span>{snapshotText(item.contentSnapshot, 'contentNo')} · V{snapshotText(item.contentSnapshot, 'contentVersionNo')}</span>
              </div><Space wrap>
                {item.directorDecision && <Tag color={item.directorDecision === 'APPROVED' ? 'success' : 'error'}>编导：{statusText[item.directorDecision] || item.directorDecision}</Tag>}
                {item.finalDecision && <Tag color={item.finalDecision === 'APPROVED' ? 'success' : 'error'}>终审：{statusText[item.finalDecision] || item.finalDecision}</Tag>}
                {item.resultStatus && <Tag>{statusText[item.resultStatus] || item.resultStatus}</Tag>}
              </Space></div>
              {/*
                字段按栅格分组：短字段多列并排压缩高度，长文本与链接独占整行。
                顺序仍对齐纸质审核表：发布计划 → 定位 → 正文 → 各类链接。
              */}
              <div className="content-review-item-body">
                <ItemCover item={item} />
                <div className="content-review-field-groups">
                  <dl className="content-review-fields content-review-fields-compact">
                    <div className="content-review-field"><dt>预计发布时间</dt>
                      <dd>{snapshotText(item.contentSnapshot, 'plannedPublishAt')}</dd></div>
                    <div className="content-review-field"><dt>作品目的</dt>
                      <dd>{snapshotText(item.contentSnapshot, 'purposeLabelSnapshot', snapshotText(item.contentSnapshot, 'purposeValue'))}</dd></div>
                    <div className="content-review-field"><dt>作品形式</dt>
                      <dd>{snapshotText(item.contentSnapshot, 'formatLabelSnapshot', snapshotText(item.contentSnapshot, 'formatValue'))}</dd></div>
                    <div className="content-review-field"><dt>选题</dt>
                      <dd>{snapshotText(item.contentSnapshot, 'topicSnapshot', snapshotText(item.contentSnapshot, 'topic'))}</dd></div>
                    <div className="content-review-field content-review-field-wide"><dt>发布标题</dt>
                      <dd>{snapshotText(item.contentSnapshot, 'titleSnapshot', snapshotText(item.contentSnapshot, 'title'))}</dd></div>
                    <div className="content-review-field content-review-field-wide"><dt>正文文稿</dt>
                      <dd className="content-review-field-text">{snapshotText(item.contentSnapshot, 'scriptText')}</dd></div>
                    <div className="content-review-field content-review-field-wide"><dt>评论区钩子</dt>
                      <dd>{snapshotText(item.contentSnapshot, 'commentHook')}</dd></div>
                  </dl>
                  {/* 链接单独成组：审核时通常连续打开核对，聚在一起比夹在字段表里更好点。 */}
                  <dl className="content-review-fields content-review-fields-links">
                    <div className="content-review-field"><dt>作品详情</dt><dd>{httpsSnapshotUrl(item.contentSnapshot, 'detailUrl')
                      ? <a href={httpsSnapshotUrl(item.contentSnapshot, 'detailUrl')} target="_blank" rel="noreferrer"><LinkOutlined /> 打开详情</a>
                      : snapshotText(item.contentSnapshot, 'detailUrl')}</dd></div>
                    <div className="content-review-field"><dt>成品外链</dt><dd>{httpsSnapshotUrl(item.contentSnapshot, 'deliverableUrl')
                      ? <a href={httpsSnapshotUrl(item.contentSnapshot, 'deliverableUrl')} target="_blank" rel="noreferrer"><LinkOutlined /> 打开链接</a>
                      : snapshotText(item.contentSnapshot, 'deliverableUrl')}</dd></div>
                    <div className="content-review-field"><dt>引流资料链接</dt><dd>{httpsSnapshotUrl(item.contentSnapshot, 'leadResourceUrl')
                      ? <a href={httpsSnapshotUrl(item.contentSnapshot, 'leadResourceUrl')} target="_blank" rel="noreferrer"><LinkOutlined /> 打开链接</a>
                      : snapshotText(item.contentSnapshot, 'leadResourceUrl')}</dd></div>
                    <div className="content-review-field"><dt>参考作品链接</dt><dd>{httpsSnapshotUrl(item.contentSnapshot, 'referenceWorkUrl')
                      ? <a href={httpsSnapshotUrl(item.contentSnapshot, 'referenceWorkUrl')} target="_blank" rel="noreferrer"><LinkOutlined /> 打开参考作品</a>
                      : snapshotText(item.contentSnapshot, 'referenceWorkUrl')}</dd></div>
                  </dl>
                </div>
              </div>
              {(item.contentSnapshot.materialRefs as Array<Record<string, unknown>> | undefined)?.length
                ? <div><Typography.Text strong>参考素材</Typography.Text>
                  <div className="content-review-media">{referenceMaterials(item).map(reference => <div className="content-review-media-item" key={reference.materialId}>
                    {reference.coverPreviewUrl
                      ? <Image width={128} height={84} src={reference.coverPreviewUrl} alt={`参考素材：${reference.title}`} />
                      : null}
                    <span>{reference.title} · {reference.materialNo}</span>
                  </div>)}</div></div>
                : null}
              <ReviewFiles item={item} />
              {stage && (stage === 'director' || item.directorDecision === 'APPROVED') && <DecisionEditor batch={selected} item={item} stage={stage}
                onDirtyChange={updateDirty}
                onSaved={() => { void contentReviewApi.get(selected.id).then(setSelected).catch(cause => message.error(errorText(cause))) }} />}
              {selected.availableActions.includes('REGISTER_PUBLISH') && item.resultStatus === 'READY_TO_PUBLISH'
                && <Button icon={<ClockCircleOutlined />} onClick={() => setPublishItem(item)}>登记发布</Button>}
              {item.publishedPlatformUrl && <a href={item.publishedPlatformUrl} target="_blank" rel="noreferrer">查看已发布内容</a>}
            </section>)}</div>
            </div>
            <aside className="content-review-process-column">
              <BpmProcessPanel
                processInstanceId={selected.processInstanceId}
                taskId={selected.currentTaskId}
                users={taskUsers}
                canUpdate={canUpdateTask}
                allowDecision={false}
                decisionOnly
                businessAdvance={advanceAction}
                onActionSuccess={() => void loadDetail(selected.id)}
              />
            </aside>
            </div>
          </> : <Empty description="从左侧选择一个审核批次" />}
      </main>
    </div>
    <CreateBatchDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreated={id => void load(1, id)} />
    {selected?.studentPersonId && <DraftEditDialog batch={selected} open={draftEditOpen} onClose={() => setDraftEditOpen(false)} onSaved={id => void load(page, id)} />}
    <Modal title={completeDecision === 'RETURNED' ? '退回运营修改' : completeStage === 'director' ? '通过本次审批' : '通过终审'} open={Boolean(completeStage)}
      onCancel={() => setCompleteStage(undefined)} onOk={() => void complete()} confirmLoading={completeLoading} okButtonProps={{ disabled: !completeReason.trim() }}>
      {completeDecision === 'RETURNED' && <Alert type="warning" showIcon message="本批次全部退回运营修改，结束本轮审批。原内容、附件和审核意见保留，修改后重新提交编导。" />}
      <Input.TextArea placeholder={completeDecision === 'RETURNED' ? '整批退回原因（必填）' : '本轮审核意见（必填）'} rows={4} maxLength={2000} value={completeReason} onChange={event => setCompleteReason(event.target.value)} />
    </Modal>
    <Modal title="登记发布结果" open={Boolean(publishItem)} onCancel={() => { setPublishItem(undefined); publishForm.resetFields() }}
      onOk={() => void publish()} confirmLoading={completeLoading}>
      <Form form={publishForm} layout="vertical">
        <Form.Item name="platformUrl" label="平台链接" rules={[
          { required: true, message: '请输入平台链接' },
          { type: 'url', message: '请输入有效链接' },
          { pattern: /^https:\/\//i, message: '平台链接必须使用 HTTPS' }
        ]}><ResourceLinkInput /></Form.Item>
        <Form.Item name="publishedAt" label="发布时间" rules={[{ required: true, message: '请选择发布时间' }]}
          initialValue={dayjs()}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
      </Form>
    </Modal>
    <Modal title="审批历史轮次" open={historyOpen} onCancel={() => setHistoryOpen(false)} footer={null}>
      {historyLoading ? <Skeleton active /> : historyError ? <Alert type="error" message={historyError} action={<Button onClick={() => void showHistory()}>重试</Button>} /> : historyRows.length ? <List dataSource={historyRows} renderItem={row => <List.Item actions={[<Button type="link" onClick={() => { setHistoryOpen(false); void loadDetail(row.id) }}>查看</Button>] }>
        <List.Item.Meta title={`${row.batchNo} · ${statusText[row.status] || row.status}`} description={`作品 ${row.items.length} 条 · ${row.finalizedAt || row.submittedAt || '未提交'}`} />
      </List.Item>} /> : <Empty description="暂无历史轮次" />}
    </Modal>
  </section>
}

