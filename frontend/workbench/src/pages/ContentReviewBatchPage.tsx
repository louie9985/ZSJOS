import { locateContentError, submitContentReview, contentSaveError, contentResultUncertain, type ContentSavePhase } from '../services/contentReviewErrors'
import ContentApprovalDraft from '../components/ContentApprovalDraft'
import ContentReviewWorkDetail from '../components/ContentReviewWorkDetail'
import { restoreDraftAccounts, restoreDraftWorks } from '../services/contentReviewDraft'
import { prepareContentReviewWorks } from '../services/contentReviewAttachments'
import ResourceLinkInput from '../components/ResourceLinkInput'
import ContentReviewInbox, { type ReviewFilters } from '../components/ContentReviewInbox'
import { contentReviewCategories, reviewAccounts } from '../services/contentReviewQuery'
import { useWorkbenchPageGuard, useWorkbenchPageNavigation } from '../components/WorkbenchPageNavigation'
import { APP_ROUTES } from '../constants'
import {
  ClockCircleOutlined,
  ArrowLeftOutlined,
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
  Tabs,
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
  type ContentReviewAccount,
} from '../services/materialApi'

const PAGE_SIZE = 20
const CANDIDATE_PAGE_SIZE = 10
const statusText: Record<string, string> = {
  DRAFT: '草稿',
  REVISION_DRAFT: '已有修订草稿',
  RESUBMITTED: '已重新提交',
  DIRECTOR_REVIEW: '编导审核',
  FINAL_REVIEW: '终审',
  COMPLETED: '待发布',
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

/**
 * 账号档案区，每个账号一块。数据全部取自提交时冻结的 contextSnapshot.accountSnapshots，
 * 不实时查账号表：否则账号改名或换阶段会改写历史审批记录。
 */
export function AccountProfiles({ batch, accountLink }: { batch: ContentReviewBatch; accountLink: (account: ContentReviewAccount) => React.ReactNode }) {
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
  const badge = (value: unknown, color: string) => <Tag color={text(value) === '—' ? undefined : color}>{text(value)}</Tag>
  return <div className="content-review-account-profiles">{snapshots.map(snapshot => {
    const id = text(snapshot.id)
    const bottleneck = text(snapshot.bottleneckLabel) !== '—'
      ? text(snapshot.bottleneckLabel) : text(problems(snapshot.primaryProblems))
    return <dl className="content-review-account-profile" key={id}>
      <div className="content-review-account-heading">
        <Tag color="blue">{accountLink(reviewAccounts(batch).find(account => String(account.accountId) === id) || { accountName: text(snapshot.nickname) })}</Tag>
        {badge(snapshot.platformLabel, 'cyan')}
      </div>
      <div><dt>责任运营</dt><dd>{badge(snapshot.operatorName ? `${snapshot.operatorName}${snapshot.operatorNameResolved === true ? '（现用姓名）' : ''}` : undefined, 'blue')}</dd></div>
      <div><dt>责任编导</dt><dd>{badge(snapshot.directorName ? `${snapshot.directorName}${snapshot.directorNameResolved === true ? '（现用姓名）' : ''}` : undefined, 'purple')}</dd></div>
      <div><dt>当前期段</dt><dd>{badge(snapshot.sStageLabel ?? snapshot.stageLabelSnapshot ?? snapshot.sStage, 'geekblue')}</dd></div>
      <div><dt>账号状态</dt><dd>{badge(snapshot.currentStatusLabel ?? snapshot.currentStatusLabelSnapshot ?? snapshot.currentStatusValue, 'cyan')}</dd></div>
      <div className="content-review-profile-background"><details open><summary>运营背景与账号信息</summary><dl>
        <div><dt>平台账号</dt><dd>{badge(snapshot.platformAccountId, 'cyan')}</dd></div>
        <div><dt>承接产品目标</dt><dd>{badge(snapshot.productGoal, 'cyan')}</dd></div>
        <div><dt>主要产品形式</dt><dd>{badge(snapshot.productFormLabel, 'cyan')}</dd></div>
        <div><dt>当前发布节奏</dt><dd>{badge(snapshot.publishFrequency, 'cyan')}</dd></div>
        <div><dt>当前瓶颈</dt><dd>{badge(bottleneck, 'orange')}</dd></div>
      </dl></details></div>
    </dl>
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
  const navigation = useWorkbenchPageNavigation()
  const [rows, setRows] = useState<ContentReviewBatch[]>([])
  const [selectedId, setSelectedId] = useState<number>()
  const [selected, setSelected] = useState<ContentReviewBatch>()
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState('PENDING')
  const [filters, setFilters] = useState<ReviewFilters>({})
  const [mobileDetail, setMobileDetail] = useState(false)
  const [compactProcess, setCompactProcess] = useState(false)
  const detailPane = useRef<HTMLDivElement>(null)
  const listRun = useRef(0)
  const detailRun = useRef(0)
  const selectedIdRef = useRef<number | undefined>(undefined)
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
  const [historyOriginId, setHistoryOriginId] = useState<number>()
  const historyRun = useRef(0)
  const [publishForm] = Form.useForm<{ platformUrl: string; publishedAt: dayjs.Dayjs }>()

  const confirmChange = (action: () => void) => {
    if (!Object.values(dirtyItems).some(Boolean)) { action(); return }
    modal.confirm({ title: '有尚未保存的审核意见', content: '切换批次或查询条件会放弃当前未保存意见。', okText: '放弃并切换', cancelText: '继续审核', onOk: action })
  }
  useWorkbenchPageGuard(APP_ROUTES.CONTENT_REVIEW, async destination => {
    if (destination || !Object.values(dirtyItems).some(Boolean)) return true
    return new Promise(resolve => modal.confirm({ title: '关闭审批页并放弃未保存意见？', okText: '放弃并关闭', cancelText: '继续审核', onOk: () => resolve(true), onCancel: () => resolve(false) }))
  })
  useEffect(() => {
    const node = detailPane.current
    if (!node) return
    const observer = new ResizeObserver(entries => { const width = entries[0]?.contentRect.width; if (width) setCompactProcess(width <= 900) })
    observer.observe(node)
    return () => { observer.disconnect(); listRun.current++; detailRun.current++ }
  }, [])

  const location = useLocation()
  const linkedBatchId = Number(new URLSearchParams(location.search).get('batchId')) || undefined
  const loadDetail = useCallback(async (id: number, historyOrigin?: number) => {
    setHistoryOriginId(historyOrigin)
    const run = ++detailRun.current
    selectedIdRef.current = id
    setSelectedId(id)
    setDetailLoading(true)
    setDetailError('')
    try { const result = await contentReviewApi.get(id); if (run === detailRun.current) { setSelected(result); detailPane.current?.scrollTo(0, 0) } }
    catch (cause) { if (run === detailRun.current) { setSelected(undefined); setDetailError(errorText(cause)) } }
    finally { if (run === detailRun.current) setDetailLoading(false) }
  }, [])

  const load = useCallback(async (targetPage = 1, preferredId?: number) => {
    const run = ++listRun.current
    detailRun.current++
    setLoading(true)
    setError('')
    try {
      const result = await contentReviewApi.page({ pageNo: targetPage, pageSize: PAGE_SIZE,
        keyword: keyword || undefined, mine, ...filters,
        statuses: category === 'PENDING' && filters.stage ? [filters.stage] : contentReviewCategories.find(item => item.key === category)?.statuses })
      if (run !== listRun.current) return
      setRows(result.list)
      setTotal(result.total)
      setPage(targetPage)
      const nextId = preferredId ?? linkedBatchId ?? result.list[0]?.id
      if (nextId) await loadDetail(nextId)
      else { setSelectedId(undefined); selectedIdRef.current = undefined; setSelected(undefined); setDetailLoading(false) }
    } catch (cause) {
      if (run !== listRun.current) return
      setRows([]); setSelected(undefined); setSelectedId(undefined); setError(errorText(cause))
      setDetailLoading(false)
    } finally { if (run === listRun.current) setLoading(false) }
  }, [keyword, loadDetail, mine, category, filters, linkedBatchId])

  useEffect(() => { void load(1) }, [load])
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
    const run = ++historyRun.current
    setHistoryOpen(true); setHistoryLoading(true); setHistoryError(''); setHistoryRows([])
    try { const result = await contentReviewApi.history(historyOriginId ?? selected.id); if (run === historyRun.current) setHistoryRows(result) }
    catch (cause) { if (run === historyRun.current) setHistoryError(errorText(cause)) }
    finally { if (run === historyRun.current) setHistoryLoading(false) }
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

  const stage = historyOriginId ? undefined : selected?.availableActions.includes('DIRECTOR_DECIDE') ? 'director'
    : selected?.availableActions.includes('FINAL_DECIDE') ? 'final' : undefined
  const canCreate = hasPermission(permissions, 'zsjos:content-review:create')
  const canSeeAll = hasPermission(permissions, 'zsjos:content-review:query-all')
  const canUpdateTask = !historyOriginId && hasPermission(permissions, 'bpm:task:update')

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

  const accountLink = (account: ContentReviewAccount) => {
    const name = account.accountName || '未记录账号名称'
    if (!selected?.studentPersonId || !account.accountId || !navigation?.canOpen(APP_ROUTES.MEDIA_STUDENTS)) return name
    const href = `${APP_ROUTES.MEDIA_STUDENTS}?personId=${selected.studentPersonId}&accountId=${account.accountId}`
    return <a className="content-review-account-link" href={href} onClick={event => { event.preventDefault(); void navigation.open(href) }}><span>{name}</span><LinkOutlined /></a>
  }

  return <section className="workspace-page content-review-page">
    <header className="content-review-filter-shell">
      <div><Typography.Title level={4}>内容审核</Typography.Title>
        <Typography.Text type="secondary">查看内容，给出反馈，推进审批</Typography.Text></div>
      <div className="content-review-toolbar">
        <Tooltip title="刷新"><Button aria-label="刷新内容审核" icon={<ReloadOutlined />} onClick={() => confirmChange(() => { if (historyOriginId && selectedId) void loadDetail(selectedId, historyOriginId); else void load(page, selectedId) })} /></Tooltip>
        {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建批次</Button>}
      </div>
    </header>
    <Tabs className="content-review-status-tabs" activeKey={category} items={contentReviewCategories.map(({ key, label }) => ({ key, label }))}
      onChange={value => confirmChange(() => { setCategory(value); setFilters(current => ({ ...current, stage: undefined })); setMobileDetail(false) })} />
    <div className={`content-review-layout${mobileDetail ? ' content-review-show-detail' : ''}`}>
      <aside className="content-review-list-pane">
        <ContentReviewInbox rows={rows} selectedId={selectedId} loading={loading} error={error} total={total}
          keyword={keyword} category={category} filters={filters} mine={mine} canSeeAll={canSeeAll} statusText={statusText}
          onSearch={value => confirmChange(() => setKeyword(value))} onFilters={value => confirmChange(() => setFilters(value))}
          onMine={value => confirmChange(() => setMine(value))} onReset={() => confirmChange(() => { setKeyword(''); setFilters({}); setMine(false) })}
          onRetry={() => void load(page)} onSelect={id => { if (id === selectedId) { setMobileDetail(true); return }; confirmChange(() => { setMobileDetail(true); void loadDetail(id) }) }} />
        {total > PAGE_SIZE && <Pagination simple current={page} pageSize={PAGE_SIZE} total={total}
          onChange={value => confirmChange(() => void load(value))} />}
      </aside>
      <main ref={detailPane} className="content-review-detail-pane">
        <Button className="content-review-mobile-back" icon={<ArrowLeftOutlined />} onClick={() => setMobileDetail(false)}>返回收件箱</Button>
        {detailLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : detailError
          ? <Alert type="error" showIcon message={detailError} action={<Space>{selectedId && <Button size="small" onClick={() => void loadDetail(selectedId, historyOriginId)}>重试</Button>}{historyOriginId && <Button size="small" onClick={() => void loadDetail(historyOriginId)}>返回原批次</Button>}</Space>} />
          : selected ? <>
            {historyOriginId && <Alert className="content-review-history-notice" type="info" showIcon message="历史轮次 · 只读" action={<Button onClick={() => confirmChange(() => void loadDetail(historyOriginId))}>返回原批次</Button>} />}
            <div className="content-review-heading"><div>
              <Typography.Title level={4}>{selected.studentName || '未记录学员姓名'} · 内容审核</Typography.Title>
            </div><Tag color="processing">{statusText[selected.status] || selected.status}</Tag></div>
            <div className="content-review-meta">
              <span>当前阶段：{statusText[selected.status] || '未记录'}</span>
              <span>提交时间：<DateTimeText value={selected.submittedAt} /></span>
              <Tag color="blue">{selected.items.length} 条内容</Tag>
              <Typography.Text type="secondary">审批编号：{selected.batchNo}</Typography.Text>
            </div>
            {!historyOriginId && selected.availableActions.some(action => ['SUBMIT', 'RESUBMIT', 'CANCEL'].includes(action)) && <div className="content-review-detail-actions">
              <Space wrap>
              {!historyOriginId && selected.availableActions.includes('SUBMIT') && <><Button icon={<ReloadOutlined />} onClick={() => setDraftEditOpen(true)}>编辑并保存草稿</Button><Button type="primary" loading={submitting} icon={<SendOutlined />} onClick={() => void submitBatch()}>提交审批</Button></>}
              {!historyOriginId && selected.availableActions.includes('RESUBMIT') && <Button type="primary" onClick={() => setDraftEditOpen(true)}>修改后重新提交</Button>}
              {!historyOriginId && selected.availableActions.includes('CANCEL') && <Button danger icon={<CloseCircleOutlined />} onClick={cancelBatch}>取消批次</Button>}
              {/* 「完成编导审核 / 完成终审」推进 BPM 任务，已移至下方审批流程面板的动作栏。 */}
            </Space></div>}
            {/*
              工作区分两栏：左侧是待审内容流，右侧固定轨承载审批流程与交卷动作。
              右轨 sticky，滚内容时流程状态和提交按钮始终可见；窄屏前置并默认折叠流程。
            */}
            <div className="content-review-workspace">
            <div className="content-review-content-column">
            <div className="content-review-section-heading"><Typography.Text strong>账号资料</Typography.Text><Typography.Text type="secondary">本轮审批快照</Typography.Text></div>
            <AccountProfiles batch={selected} accountLink={accountLink} />
            {/* 分节标题：让"账号档案 → 逐条审核 → 交卷"的顺序在页面上可见。 */}
            <div className="content-review-section-heading">
              <Typography.Text strong>待审内容</Typography.Text>
              <Typography.Text type="secondary">
                共 {selected.items.length} 条{stage ? '，逐条保存后在审批流程区提交' : ''}
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
              <ContentReviewWorkDetail key={`${selected.id}:${item.id}`} item={item} batchId={selected.id} />
              {stage && (stage === 'director' || item.directorDecision === 'APPROVED') && <DecisionEditor batch={selected} item={item} stage={stage}
                onDirtyChange={updateDirty}
                onSaved={() => { const id = selected.id; void contentReviewApi.get(id).then(value => { if (selectedIdRef.current === id) setSelected(value) }).catch(cause => message.error(errorText(cause))) }} />}
              {!historyOriginId && selected.availableActions.includes('REGISTER_PUBLISH') && item.resultStatus === 'READY_TO_PUBLISH'
                && <Button icon={<ClockCircleOutlined />} onClick={() => setPublishItem(item)}>登记发布</Button>}
              {item.publishedPlatformUrl && <a href={item.publishedPlatformUrl} target="_blank" rel="noreferrer">查看已发布内容</a>}
            </section>)}</div>
            </div>
            <aside className="content-review-process-column">
              <Button block icon={<ClockCircleOutlined />} onClick={() => void showHistory()}>查看历史轮次</Button>
              <BpmProcessPanel
                compact={compactProcess}
                actionsOutside
                processInstanceId={selected.processInstanceId}
                taskId={selected.currentTaskId}
                users={taskUsers}
                canUpdate={canUpdateTask}
                allowDecision={false}
                decisionOnly
                businessAdvance={historyOriginId ? undefined : advanceAction}
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
        <Form.Item name="publishedAt" label="发布时间" extra="请填写当前时间之后的时间，过去的时间无法登记发布结果。" rules={[{ required: true, message: '请选择发布时间' }, { validator: (_, value: dayjs.Dayjs | undefined) => !value || value.isAfter(dayjs(), 'minute') ? Promise.resolve() : Promise.reject(new Error('发布时间必须晚于当前时间，请重新选择未来时间')) }]}
          initialValue={dayjs().add(1, 'minute')}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
      </Form>
    </Modal>
    <Modal title="审批历史轮次" open={historyOpen} onCancel={() => setHistoryOpen(false)} footer={null}>
      {historyLoading ? <Skeleton active /> : historyError ? <Alert type="error" showIcon message={historyError} action={<Button onClick={() => void showHistory()}>重试</Button>} /> : historyRows.length ? <List dataSource={historyRows} renderItem={(row, index) => <List.Item actions={[<Button key="view" type="link" onClick={() => confirmChange(() => { setHistoryOpen(false); void loadDetail(row.id, row.id === (historyOriginId ?? selected?.id) ? undefined : historyOriginId ?? selected?.id) })}>查看详情</Button>] }>
        <List.Item.Meta title={<Space wrap><Typography.Text strong>轮次记录 {index + 1}</Typography.Text><Tag>{statusText[row.status] || row.status}</Tag><Typography.Text type="secondary">{row.batchNo}</Typography.Text></Space>} description={<span>作品 {row.items.length} 条 · <DateTimeText value={row.finalizedAt || row.submittedAt} emptyText="未提交" /></span>} />
      </List.Item>} /> : <Empty description="暂无历史轮次" />}
    </Modal>
  </section>
}

