import {
  CheckCircleOutlined,
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
import { useCallback, useEffect, useMemo, useState } from 'react'
import DateTimeText from '../components/DateTimeText'
import { ApiError, api } from '../services/api'
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

const errorText = (error: unknown) => error instanceof ApiError && error.code === 403
  ? '无权访问内容审核'
  : error instanceof Error ? error.message : '内容审核加载失败，请重试'

const snapshotText = (snapshot: Record<string, unknown>, key: string, fallback = '未记录') => {
  const value = snapshot[key]
  return value == null || value === '' ? fallback : String(value)
}

const httpsSnapshotUrl = (snapshot: Record<string, unknown>, key: string) => {
  const value = snapshot[key]
  return typeof value === 'string' && /^https:\/\//i.test(value) ? value : undefined
}

function ReviewFiles({ item }: { item: ContentReviewItem }) {
  if (!item.files?.length) return null
  return <div className="content-review-media">{item.files.map(file => {
    const label = file.fieldKey === 'cover' ? '封面' : '成品'
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

function DecisionEditor({ batch, item, stage, onSaved }: {
  batch: ContentReviewBatch
  item: ContentReviewItem
  stage: 'director' | 'final'
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

  const save = async () => {
    if (!batch.currentTaskId || !decision) return message.warning('请选择审核结论')
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
    {stage === 'final' && decision === 'APPROVED' && <span className="content-review-collect">收录素材库
      <Switch checked={collect} onChange={setCollect} /></span>}
    <Input.TextArea rows={2} maxLength={2000} value={comment} onChange={event => setComment(event.target.value)}
      placeholder={decision === 'RETURNED' ? '填写退回原因' : '审核意见'} />
    <Button type="primary" loading={loading} onClick={() => void save()}>保存本条</Button>
  </div>
}

function DraftEditDialog({ batch, open, onClose, onSaved }: { batch: ContentReviewBatch; open: boolean; onClose: () => void; onSaved: (id: number) => void }) {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [purposeOptions, setPurposeOptions] = useState<Array<{ value: string; label: string }>>([])
  const [formatOptions, setFormatOptions] = useState<Array<{ value: string; label: string }>>([])
  const [referenceOptions, setReferenceOptions] = useState<Array<{ value: number; label: string }>>([])
  const [uploading, setUploading] = useState<number>()
  useEffect(() => {
    if (!open) return
    void Promise.all([api.dictDataByType('zsjos_content_purpose'), api.dictDataByType('zsjos_content_format'), materialApi.referenceTargets({ pageNo: 1, pageSize: 50 })]).then(([purpose, format, references]) => {
      setPurposeOptions(purpose.map(item => ({ value: item.value, label: item.label })))
      setFormatOptions(format.map(item => ({ value: item.value, label: item.label })))
      setReferenceOptions(references.list.map(item => ({ value: item.contentVersionId, label: `${item.title} · ${item.contentNo}` })))
    })
  }, [open])
  useEffect(() => {
    if (!open) return
    const snapshots = Array.isArray(batch.contextSnapshot.accountSnapshots) ? batch.contextSnapshot.accountSnapshots : []
    form.setFieldsValue({ accountSnapshots: Object.fromEntries(snapshots.map((snapshot: Record<string, unknown>) => [String(snapshot.id), snapshot])), works: batch.items.map(item => ({
      sourceContentId: item.contentId,
      sourceVersionId: item.contentVersionId,
      title: item.contentSnapshot.titleSnapshot || item.contentSnapshot.title,
      scriptText: item.contentSnapshot.scriptText,
      plannedPublishAt: item.contentSnapshot.plannedPublishAt ? dayjs(String(item.contentSnapshot.plannedPublishAt)) : undefined,
      purposeValue: item.contentSnapshot.purposeValue,
      purposeLabelSnapshot: item.contentSnapshot.purposeLabelSnapshot,
      formatValue: item.contentSnapshot.formatValue,
      formatLabelSnapshot: item.contentSnapshot.formatLabelSnapshot,
      detailUrl: item.contentSnapshot.detailUrl,
      leadResourceUrl: item.contentSnapshot.leadResourceUrl,
      commentHook: item.contentSnapshot.commentHook,
      referenceContentVersionId: item.contentSnapshot.referenceContentVersionId,
      coverFileId: item.files?.find(file => file.fieldKey === 'cover')?.infraFileId
    })) })
  }, [batch, form, open])
  const accountSnapshotRows = (Array.isArray(batch.contextSnapshot.accountSnapshots) ? batch.contextSnapshot.accountSnapshots : []) as Array<Record<string, unknown>>
  const save = async () => {
    try {
      const values = await form.validateFields()
      setLoading(true)
      const id = await contentReviewApi.saveStudentDraft(batch.id, {
        studentPersonId: batch.studentPersonId!,
        accountIds: batch.accountIds?.length ? batch.accountIds : [batch.accountId],
        accountSnapshots: values.accountSnapshots,
        works: values.works.map((work: Record<string, unknown>) => ({ ...work, plannedPublishAt: work.plannedPublishAt ? (work.plannedPublishAt as dayjs.Dayjs).format('YYYY-MM-DDTHH:mm:ss') : undefined }))
      })
      message.success('草稿已保存')
      onClose(); onSaved(id)
    } catch (cause) { if (!(cause as { errorFields?: unknown }).errorFields) message.error(errorText(cause)) }
    finally { setLoading(false) }
  }
  return <Modal title="编辑内容审批草稿" open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={loading} width="min(760px, calc(100vw - 32px))">
    <Form form={form} layout="vertical">
      <Typography.Text strong>账号资料快照</Typography.Text>
      <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>{accountSnapshotRows.map(snapshot => { const id = String(snapshot.id); return <Card key={id} size="small" title={`账号 ${id}`}>
        <Form.Item name={['accountSnapshots', id, 'nickname']} label="账号名称"><Input /></Form.Item>
        <Form.Item name={['accountSnapshots', id, 'platformLabel']} label="发布平台"><Input /></Form.Item>
        <Form.Item name={['accountSnapshots', id, 'sStageLabel']} label="当前期段"><Input /></Form.Item>
        <Form.Item name={['accountSnapshots', id, 'currentStatusLabel']} label="账号状态"><Input /></Form.Item>
        <Form.Item name={['accountSnapshots', id, 'productGoal']} label="承接产品目标"><Input /></Form.Item>
        <Form.Item name={['accountSnapshots', id, 'productFormLabel']} label="主要产品形式"><Input /></Form.Item>
        <Form.Item name={['accountSnapshots', id, 'publishFrequency']} label="当前发布节奏"><Input /></Form.Item>
        <Form.Item name={['accountSnapshots', id, 'bottleneckLabel']} label="当前瓶颈"><Input /></Form.Item>
        <Form.Item name={['accountSnapshots', id, 'operatorName']} label="责任运营人员"><Input /></Form.Item>
      </Card>})}</Space>
      <Form.List name="works">{(fields, { add, remove, move }) => <Space direction="vertical" style={{ width: '100%' }}>{fields.map((field, index) => <Card key={field.key} size="small" title={<Space>作品 {index + 1}<Button size="small" disabled={index === 0} onClick={() => move(index, index - 1)}>上移</Button><Button size="small" disabled={index === fields.length - 1} onClick={() => move(index, index + 1)}>下移</Button>{fields.length > 1 && <Button danger size="small" onClick={() => remove(field.name)}>删除</Button>}</Space>}>
        <Form.Item {...field} name={[field.name, 'coverFileId']} label="作品封面图" rules={[{ required: true, message: '请上传封面图' }]}><Space><Button loading={uploading === index} onClick={() => document.getElementById(`draft-cover-${batch.id}-${field.key}`)?.click()}>上传封面</Button><input id={`draft-cover-${batch.id}-${field.key}`} type="file" accept="image/*" hidden onChange={async event => { const file = event.target.files?.[0]; if (!file) return; setUploading(index); try { const uploaded = await api.mediaContent.uploadVersionFile(file); form.setFieldValue(['works', field.name, 'coverFileId'], uploaded.fileId); message.success('封面已上传') } catch (cause) { message.error(errorText(cause)) } finally { setUploading(undefined); event.target.value = '' } }} /></Space></Form.Item>
        <Form.Item {...field} name={[field.name, 'title']} label="发布标题" rules={[{ required: true, message: '请输入标题' }]}><Input /></Form.Item>
        <Form.Item {...field} name={[field.name, 'scriptText']} label="正文文稿" rules={[{ required: true, message: '请输入正文' }]}><Input.TextArea rows={6} /></Form.Item>
        <Form.Item {...field} name={[field.name, 'purposeValue']} label="作品目的" rules={[{ required: true, message: '请选择作品目的' }]}><Select options={purposeOptions} onChange={value => form.setFieldValue(['works', field.name, 'purposeLabelSnapshot'], purposeOptions.find(item => item.value === value)?.label)} /></Form.Item>
        <Form.Item {...field} name={[field.name, 'formatValue']} label="作品形式" rules={[{ required: true, message: '请选择作品形式' }]}><Select options={formatOptions} onChange={value => form.setFieldValue(['works', field.name, 'formatLabelSnapshot'], formatOptions.find(item => item.value === value)?.label)} /></Form.Item>
        <Form.Item {...field} name={[field.name, 'detailUrl']} label="作品详情"><Input /></Form.Item>
        <Form.Item {...field} name={[field.name, 'leadResourceUrl']} label="引流资料链接"><Input /></Form.Item>
        <Form.Item {...field} name={[field.name, 'referenceContentVersionId']} label="参考作品"><Select allowClear showSearch optionFilterProp="label" options={referenceOptions} /></Form.Item>
        <Form.Item {...field} name={[field.name, 'plannedPublishAt']} label="预计发布时间" rules={[{ required: true, message: '请选择预计发布时间' }]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
        {(['purposeLabelSnapshot', 'formatLabelSnapshot', 'commentHook'] as const).map(key => <Form.Item key={key} name={[field.name, key]} hidden />)}
      </Card>)}<Button type="dashed" block onClick={() => add({})}>新增作品</Button></Space>}</Form.List>
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
  const [completeReason, setCompleteReason] = useState('本级逐条结论已完成')
  const [completeLoading, setCompleteLoading] = useState(false)
  const [publishItem, setPublishItem] = useState<ContentReviewItem>()
  const [draftEditOpen, setDraftEditOpen] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [historyRows, setHistoryRows] = useState<ContentReviewBatch[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [publishForm] = Form.useForm<{ platformUrl: string; publishedAt: dayjs.Dayjs }>()

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
      const nextId = result.list.some(item => item.id === preferredId) ? preferredId : result.list[0]?.id
      if (nextId) await loadDetail(nextId)
      else { setSelectedId(undefined); setSelected(undefined) }
    } catch (cause) {
      setRows([]); setSelected(undefined); setSelectedId(undefined); setError(errorText(cause))
    } finally { setLoading(false) }
  }, [keyword, loadDetail, mine, status])

  useEffect(() => { void load(1) }, [keyword, status, mine])

  const submitBatch = async () => {
    if (!selected) return
    try {
      await contentReviewApi.submit(selected.id, selected.version)
      message.success('批次已提交')
      await load(page, selected.id)
    } catch (cause) { message.error(errorText(cause)) }
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
    setHistoryOpen(true); setHistoryLoading(true)
    try { setHistoryRows(await contentReviewApi.history(selected.id)) }
    catch (cause) { message.error(errorText(cause)); setHistoryRows([]) }
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
    if (!selected || !completeStage || !selected.currentTaskId || !completeReason.trim()) return
    setCompleteLoading(true)
    try {
      const data = { expectedVersion: selected.version, taskId: selected.currentTaskId, reason: completeReason.trim() }
      if (completeStage === 'director') await contentReviewApi.completeDirector(selected.id, data)
      else await contentReviewApi.completeFinal(selected.id, data)
      message.success(completeStage === 'director' ? '编导审核已完成' : '终审结论已统一落地')
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
      if (cause instanceof Error) message.error(errorText(cause))
    } finally { setCompleteLoading(false) }
  }

  const stage = selected?.availableActions.includes('DIRECTOR_DECIDE') ? 'director'
    : selected?.availableActions.includes('FINAL_DECIDE') ? 'final' : undefined
  const canCreate = hasPermission(permissions, 'zsjos:content-review:create')
  const canSeeAll = hasPermission(permissions, 'zsjos:content-review:query-all')

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
              {selected.availableActions.includes('SUBMIT') && <><Button icon={<ReloadOutlined />} onClick={() => setDraftEditOpen(true)}>编辑并保存草稿</Button><Button type="primary" icon={<SendOutlined />} onClick={() => void submitBatch()}>提交审批</Button></>}
              {selected.availableActions.includes('CANCEL') && <Button danger icon={<CloseCircleOutlined />} onClick={cancelBatch}>取消批次</Button>}
              {selected.availableActions.includes('DIRECTOR_COMPLETE') && <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => setCompleteStage('director')}>完成编导审核</Button>}
              {selected.availableActions.includes('FINAL_COMPLETE') && <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => setCompleteStage('final')}>完成终审</Button>}
            </Space></div>
            <div className="content-review-meta">
              <span>当前节点：{selected.currentStage || '无'}</span>
              <span>提交时间：<DateTimeText value={selected.submittedAt} /></span>
              <span>内容数量：{selected.items.length}</span>
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
              <div className="content-review-snapshot">
                <p><strong>选题：</strong>{snapshotText(item.contentSnapshot, 'topicSnapshot', snapshotText(item.contentSnapshot, 'topic'))}</p>
                <p><strong>脚本或正文：</strong>{snapshotText(item.contentSnapshot, 'scriptText')}</p>
                <p><strong>作品目的：</strong>{snapshotText(item.contentSnapshot, 'purposeLabelSnapshot', snapshotText(item.contentSnapshot, 'purposeValue'))}</p>
                <p><strong>作品形式：</strong>{snapshotText(item.contentSnapshot, 'formatLabelSnapshot', snapshotText(item.contentSnapshot, 'formatValue'))}</p>
                <p><strong>预计发布时间：</strong>{snapshotText(item.contentSnapshot, 'plannedPublishAt')}</p>
                {httpsSnapshotUrl(item.contentSnapshot, 'detailUrl') && <p><strong>作品详情：</strong><a href={httpsSnapshotUrl(item.contentSnapshot, 'detailUrl')} target="_blank" rel="noreferrer"><LinkOutlined /> 打开详情</a></p>}
                <p><strong>评论区钩子：</strong>{snapshotText(item.contentSnapshot, 'commentHook')}</p>
                <p><strong>引流资料：</strong>{httpsSnapshotUrl(item.contentSnapshot, 'leadResourceUrl')
                  ? <a href={httpsSnapshotUrl(item.contentSnapshot, 'leadResourceUrl')} target="_blank" rel="noreferrer"><LinkOutlined /> 打开链接</a>
                  : snapshotText(item.contentSnapshot, 'leadResourceUrl')}</p>
                {httpsSnapshotUrl(item.contentSnapshot, 'deliverableUrl') && <p><strong>成品外链：</strong>
                  <a href={httpsSnapshotUrl(item.contentSnapshot, 'deliverableUrl')} target="_blank" rel="noreferrer"><LinkOutlined /> 打开链接</a></p>}
              </div>
              <ReviewFiles item={item} />
              {stage && (stage === 'director' || item.directorDecision === 'APPROVED') && <DecisionEditor batch={selected} item={item} stage={stage}
                onSaved={() => void loadDetail(selected.id)} />}
              {selected.availableActions.includes('REGISTER_PUBLISH') && item.resultStatus === 'READY_TO_PUBLISH'
                && <Button icon={<ClockCircleOutlined />} onClick={() => setPublishItem(item)}>登记发布</Button>}
              {item.publishedPlatformUrl && <a href={item.publishedPlatformUrl} target="_blank" rel="noreferrer">查看已发布内容</a>}
            </section>)}</div>
          </> : <Empty description="从左侧选择一个审核批次" />}
      </main>
    </div>
    <CreateBatchDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreated={id => void load(1, id)} />
    {selected?.studentPersonId && <DraftEditDialog batch={selected} open={draftEditOpen} onClose={() => setDraftEditOpen(false)} onSaved={id => void load(page, id)} />}
    <Modal title={completeStage === 'director' ? '完成编导审核' : '完成终审'} open={Boolean(completeStage)}
      onCancel={() => setCompleteStage(undefined)} onOk={() => void complete()} confirmLoading={completeLoading}>
      <Input.TextArea rows={4} maxLength={2000} value={completeReason} onChange={event => setCompleteReason(event.target.value)} />
    </Modal>
    <Modal title="登记发布结果" open={Boolean(publishItem)} onCancel={() => { setPublishItem(undefined); publishForm.resetFields() }}
      onOk={() => void publish()} confirmLoading={completeLoading}>
      <Form form={publishForm} layout="vertical">
        <Form.Item name="platformUrl" label="平台链接" rules={[
          { required: true, message: '请输入平台链接' },
          { type: 'url', message: '请输入有效链接' },
          { pattern: /^https:\/\//i, message: '平台链接必须使用 HTTPS' }
        ]}><Input /></Form.Item>
        <Form.Item name="publishedAt" label="发布时间" rules={[{ required: true, message: '请选择发布时间' }]}
          initialValue={dayjs()}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
      </Form>
    </Modal>
    <Modal title="审批历史轮次" open={historyOpen} onCancel={() => setHistoryOpen(false)} footer={null}>
      {historyLoading ? <Skeleton active /> : historyRows.length ? <List dataSource={historyRows} renderItem={row => <List.Item actions={[<Button type="link" onClick={() => { setHistoryOpen(false); void loadDetail(row.id) }}>查看</Button>] }>
        <List.Item.Meta title={`${row.batchNo} · ${statusText[row.status] || row.status}`} description={`作品 ${row.items.length} 条 · ${row.finalizedAt || row.submittedAt || '未提交'}`} />
      </List.Item>} /> : <Empty description="暂无历史轮次" />}
    </Modal>
  </section>
}
