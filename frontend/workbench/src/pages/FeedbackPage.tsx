import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert, Badge, Button, Descriptions, Empty, Form, Image, Input, Modal, Segmented,
  Select, Skeleton, Space, Spin, Tag, Timeline, Typography, message
} from 'antd'
import {
  BugOutlined, BulbOutlined, CustomerServiceOutlined, MessageOutlined,
  PlusOutlined, ReloadOutlined, SendOutlined
} from '@ant-design/icons'
import FeedbackDynamicForm, {
  FeedbackAttachmentInput,
  normalizeFeedbackInitialValues,
  serializeFeedbackFormValues
} from '../components/FeedbackDynamicForm'
import {
  feedbackApi,
  type FeedbackAttachment,
  type FeedbackField,
  type FeedbackForm,
  type FeedbackPortal,
  type FeedbackRecord,
  type FeedbackStatus,
  type FeedbackType
} from '../services/feedbackApi'
import { formatTimestamp } from '../services/time'
import { useSearchParams } from 'react-router-dom'
import ResizableDrawer from '../components/ResizableDrawer'
import { FEEDBACK_DETAIL_DRAWER_WIDTH_STORAGE_KEY } from '../constants'

const PAGE_SIZE = 10
const TYPE_META: Record<FeedbackType, { label: string; icon: React.ReactNode; permission: string }> = {
  REQUIREMENT: { label: '需求', icon: <BulbOutlined/>, permission: 'zsjos:feedback:requirement:create' },
  BUG: { label: 'BUG', icon: <BugOutlined/>, permission: 'zsjos:feedback:bug:create' },
  SUPPORT: { label: '技术支持', icon: <CustomerServiceOutlined/>, permission: 'zsjos:feedback:support:create' }
}
const STATUS_META: Record<FeedbackStatus, { label: string; color: string }> = {
  APPROVING: { label: '审批中', color: 'processing' },
  APPROVAL_REJECTED: { label: '审批驳回', color: 'error' },
  WAITING: { label: '待处理', color: 'default' },
  IN_PROGRESS: { label: '处理中', color: 'warning' },
  COMPLETED: { label: '已完成', color: 'success' }
}

function hasPermission(permissions: string[], permission: string) {
  return permissions.includes('*:*:*') || permissions.includes(permission)
}

function fileValues(value: unknown): FeedbackAttachment[] {
  return Array.isArray(value) && value.every(item => item && typeof item === 'object' && 'id' in item)
    ? value as FeedbackAttachment[]
    : []
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object' && !Array.isArray(value)) {
    const snapshot = value as Record<string, unknown>
    return String(snapshot.label ?? snapshot.value ?? '-')
  }
  if (Array.isArray(value)) return value.map(displayValue).join('、')
  return String(value)
}

function AttachmentLinks({ items = [] }: { items?: FeedbackAttachment[] }) {
  if (!items.length) return null
  const images = items.filter(isImageAttachment)
  const files = items.filter(item => !isImageAttachment(item))
  return <div className="feedback-file-links">
    {images.length > 0 && <Image.PreviewGroup><div className="feedback-detail-image-grid">
      {images.map(item => item.url
        ? <Image key={item.id} width={88} height={72} src={item.url} alt={item.name || `图片 ${item.id}`} />
        : <span className="feedback-detail-image-missing" key={item.id}>{item.name || `图片 ${item.id}`}</span>)}
    </div></Image.PreviewGroup>}
    {files.map(item => item.url
      ? <Typography.Link key={item.id} href={item.url} target="_blank">{item.name || `附件 ${item.id}`}</Typography.Link>
      : <span key={item.id}>{item.name || `附件 ${item.id}`}</span>)}
  </div>
}

function isImageAttachment(item: FeedbackAttachment) {
  const type = item.type?.toLowerCase() || ''
  const name = item.name?.toLowerCase() || ''
  return type.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(name)
}

function FeedbackCard({ item, onClick, disabled = false }: { item: FeedbackRecord; onClick: () => void; disabled?: boolean }) {
  return <button type="button" className="feedback-record-card" disabled={disabled} onClick={onClick}>
    <div className="feedback-record-topline">
      <Space size={8} wrap>
        <Tag>{TYPE_META[item.feedbackType].label}</Tag>
        <span className="feedback-record-no">{item.feedbackNo}</span>
        {item.unread && <Badge status="processing" text="未读"/>}
      </Space>
      <Tag color={STATUS_META[item.status].color}>{STATUS_META[item.status].label}</Tag>
    </div>
    <strong className="feedback-record-title">{item.title}</strong>
    <div className="feedback-record-meta">
      <span>处理人：{item.assigneeName || '待分派'}</span>
      <span>{formatTimestamp(item.lastActivityAt)}</span>
    </div>
    <p>{item.latestReplySummary || '暂无回复'}</p>
  </button>
}

function ValueDescriptions({ fields = [], values = {} }: { fields?: FeedbackField[]; values?: Record<string, unknown> }) {
  return <Descriptions column={1} bordered size="small" items={fields.map(field => ({
    key: field.key,
    label: field.label,
    children: fileValues(values[field.key]).length
      ? <AttachmentLinks items={fileValues(values[field.key])}/>
      : displayValue(values[field.key])
  }))}/>
}

export default function FeedbackPage({ permissions }: { permissions: string[] }) {
  const [searchParams] = useSearchParams()
  const [view, setView] = useState<'home' | 'records'>('home')
  const [portal, setPortal] = useState<FeedbackPortal>()
  const [portalLoading, setPortalLoading] = useState(true)
  const [portalError, setPortalError] = useState('')
  const [records, setRecords] = useState<FeedbackRecord[]>([])
  const [recordTotal, setRecordTotal] = useState(0)
  const [recordPage, setRecordPage] = useState(1)
  const [recordLoading, setRecordLoading] = useState(false)
  const [recordError, setRecordError] = useState('')
  const [typeFilter, setTypeFilter] = useState<FeedbackType>()
  const [statusFilter, setStatusFilter] = useState<FeedbackStatus>()
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [detail, setDetail] = useState<FeedbackRecord>()
  const [detailId, setDetailId] = useState<number>()
  const [editorForm] = Form.useForm<Record<string, unknown>>()
  const [editor, setEditor] = useState<{ mode: 'create' | 'resubmit'; form: FeedbackForm; record?: FeedbackRecord }>()
  const [editorLoading, setEditorLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [replyOpen, setReplyOpen] = useState(false)
  const [replyContent, setReplyContent] = useState('')
  const [replyAttachments, setReplyAttachments] = useState<FeedbackAttachment[]>([])
  const [surveyOpen, setSurveyOpen] = useState(false)
  const [surveyForm] = Form.useForm<Record<string, unknown>>()
  const canRead = hasPermission(permissions, 'zsjos:feedback:read')

  const loadPortal = useCallback(async () => {
    setPortalLoading(true)
    setPortalError('')
    try { setPortal(await feedbackApi.portal()) }
    catch (cause) { setPortalError(cause instanceof Error ? cause.message : '反馈首页加载失败') }
    finally { setPortalLoading(false) }
  }, [])

  const loadRecords = useCallback(async (pageNo: number, append = false) => {
    setRecordLoading(true)
    setRecordError('')
    try {
      const result = await feedbackApi.myPage({ pageNo, pageSize: PAGE_SIZE, feedbackType: typeFilter, status: statusFilter })
      setRecords(current => append ? [...current, ...result.list] : result.list)
      setRecordTotal(result.total)
      setRecordPage(pageNo)
    } catch (cause) {
      if (!append) setRecords([])
      setRecordError(cause instanceof Error ? cause.message : '我的记录加载失败')
    } finally { setRecordLoading(false) }
  }, [statusFilter, typeFilter])

  useEffect(() => { void loadPortal() }, [loadPortal])
  useEffect(() => { if (view === 'records') void loadRecords(1) }, [loadRecords, view])

  const refreshAll = async () => {
    await Promise.all([loadPortal(), view === 'records' ? loadRecords(1) : Promise.resolve()])
  }

  const loadDetail = useCallback(async (id: number) => {
    setDetailLoading(true)
    setDetailError('')
    try {
      let next = await feedbackApi.detail(id)
      if (next.unread && hasPermission(permissions, 'zsjos:feedback:read')) {
        await feedbackApi.markRead(id, next.version)
        next = await feedbackApi.detail(id)
      }
      setDetail(next)
    } catch (cause) {
      setDetail(undefined)
      setDetailError(cause instanceof Error ? cause.message : '反馈详情加载失败')
    } finally { setDetailLoading(false) }
  }, [permissions])

  useEffect(() => {
    if (!canRead) return
    const id = Number(searchParams.get('feedbackId'))
    if (!Number.isSafeInteger(id) || id <= 0) return
    setDetailId(id)
    setDetailOpen(true)
    void loadDetail(id).then(() => loadPortal())
  }, [canRead, loadDetail, loadPortal, searchParams])

  const openDetail = async (id: number) => {
    if (!hasPermission(permissions, 'zsjos:feedback:read')) return
    setDetailId(id)
    setDetailOpen(true)
    await loadDetail(id)
    await refreshAll()
  }

  const openCreate = async (type: FeedbackType) => {
    setEditorLoading(true)
    try {
      const form = await feedbackApi.form(type)
      if (!form.open) return message.warning(form.unavailableReason || '该类型暂未开放')
      editorForm.resetFields()
      setEditor({ mode: 'create', form })
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '表单加载失败') }
    finally { setEditorLoading(false) }
  }

  const openResubmit = async () => {
    if (!detail) return
    setEditorLoading(true)
    try {
      const form = await feedbackApi.form('REQUIREMENT')
      editorForm.setFieldsValue(normalizeFeedbackInitialValues(form.fields, detail.values))
      setEditor({ mode: 'resubmit', form, record: detail })
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '当前需求表单加载失败') }
    finally { setEditorLoading(false) }
  }

  const submitEditor = async () => {
    if (!editor) return
    const raw = await editorForm.validateFields()
    const values = serializeFeedbackFormValues(editor.form.fields, raw)
    setSaving(true)
    try {
      if (editor.mode === 'create') {
        await feedbackApi.create(editor.form.feedbackType, editor.form.configVersion, values)
        message.success('反馈已提交')
      } else if (editor.record) {
        await feedbackApi.resubmit(editor.record.id, editor.record.version, editor.form.configVersion, values)
        message.success('需求已重新提交')
      }
      setEditor(undefined)
      editorForm.resetFields()
      await refreshAll()
      if (detailId) await loadDetail(detailId)
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '提交失败') }
    finally { setSaving(false) }
  }

  const submitReply = async () => {
    if (!detail || !replyContent.trim()) return message.warning('请填写回复内容')
    setSaving(true)
    try {
      await feedbackApi.reply(detail.id, detail.version, replyContent.trim(), replyAttachments.map(item => item.id))
      message.success('回复已发送')
      setReplyOpen(false)
      setReplyContent('')
      setReplyAttachments([])
      await Promise.all([loadDetail(detail.id), refreshAll()])
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '回复失败') }
    finally { setSaving(false) }
  }

  const submitSurvey = async () => {
    if (!detail?.survey) return
    const values = await surveyForm.validateFields()
    setSaving(true)
    try {
      await feedbackApi.submitSurvey(detail.id, detail.version, values)
      message.success('满意度已提交')
      setSurveyOpen(false)
      await Promise.all([loadDetail(detail.id), refreshAll()])
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '满意度提交失败') }
    finally { setSaving(false) }
  }

  const visibleEntries = useMemo(() => portal?.entries.filter(entry => hasPermission(permissions, TYPE_META[entry.feedbackType].permission)) || [], [permissions, portal])

  return <section className="workspace-page feedback-page">
    <header className="feedback-page-header">
      <div>
        <Typography.Title level={3}>需求与反馈</Typography.Title>
        <Typography.Text type="secondary">AI 应用部门</Typography.Text>
      </div>
      <Space>
        <Segmented value={view} onChange={value => setView(value as typeof view)} options={[{ label: '首页', value: 'home' }, { label: '我的记录', value: 'records' }]}/>
        <Button icon={<ReloadOutlined/>} aria-label="刷新" title="刷新" onClick={() => void refreshAll()}/>
      </Space>
    </header>

    {view === 'home' ? <>
      {portalError && <Alert type="error" showIcon message={portalError} action={<Button onClick={() => void loadPortal()}>重试</Button>}/>}
      {portalLoading ? <Skeleton active paragraph={{ rows: 6 }}/> : <>
        <div className="feedback-entry-grid">
          {visibleEntries.map(entry => <button
            type="button"
            className="feedback-entry-card"
            key={entry.feedbackType}
            disabled={!entry.open || editorLoading}
            onClick={() => void openCreate(entry.feedbackType)}
          >
            <span className={`feedback-entry-icon feedback-entry-icon-${entry.feedbackType.toLowerCase()}`}>{TYPE_META[entry.feedbackType].icon}</span>
            <span className="feedback-entry-copy"><strong>{entry.title}</strong><span>{entry.open ? entry.description : entry.unavailableReason || '暂未开放'}</span></span>
            <PlusOutlined/>
          </button>)}
        </div>
        <div className="feedback-section-heading"><Typography.Title level={4}>最近反馈</Typography.Title><Button type="link" onClick={() => setView('records')}>查看全部</Button></div>
        <div className="feedback-record-grid">
          {portal?.recent.length ? portal.recent.map(item => <FeedbackCard key={item.id} item={item} disabled={!canRead} onClick={() => void openDetail(item.id)}/>) : <Empty description="暂无反馈记录"/>}
        </div>
      </>}
    </> : <>
      <div className="feedback-filter-bar">
        <Select allowClear value={typeFilter} placeholder="全部类型" style={{ width: 150 }} onChange={setTypeFilter} options={Object.entries(TYPE_META).map(([value, meta]) => ({ value, label: meta.label }))}/>
        <Select allowClear value={statusFilter} placeholder="全部状态" style={{ width: 150 }} onChange={setStatusFilter} options={Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.label }))}/>
      </div>
      {recordError && <Alert type="error" showIcon message={recordError} action={<Button onClick={() => void loadRecords(1)}>重试</Button>}/>}
      <div className="feedback-record-grid">
        {!records.length && recordLoading ? <Spin/> : records.length ? records.map(item => <FeedbackCard key={item.id} item={item} disabled={!canRead} onClick={() => void openDetail(item.id)}/>) : !recordError && <Empty description="暂无符合条件的反馈"/>}
      </div>
      {records.length < recordTotal && <div className="feedback-load-more"><Button loading={recordLoading} onClick={() => void loadRecords(recordPage + 1, true)}>加载更多</Button></div>}
    </>}

    <Modal
      open={Boolean(editor)}
      title={editor?.mode === 'resubmit' ? '修改并重提需求' : editor ? TYPE_META[editor.form.feedbackType].label : ''}
      width={640}
      confirmLoading={saving}
      okText="提交"
      destroyOnHidden
      onCancel={() => setEditor(undefined)}
      onOk={() => void submitEditor()}
    >
      {editor && <FeedbackDynamicForm form={editorForm} fields={editor.form.fields}/>}
    </Modal>

    <ResizableDrawer open={detailOpen} onClose={() => setDetailOpen(false)} width="min(720px, 100vw)" defaultSize={720} minSize={560} storageKey={FEEDBACK_DETAIL_DRAWER_WIDTH_STORAGE_KEY} title={detail?.feedbackNo || '反馈详情'}>
      {detailError && <Alert type="error" showIcon message={detailError} action={<Button onClick={() => detailId && void loadDetail(detailId)}>重试</Button>}/>}
      {detailLoading ? <Skeleton active paragraph={{ rows: 8 }}/> : detail && <div className="feedback-detail">
        <div className="feedback-detail-title"><div><Typography.Title level={4}>{detail.title}</Typography.Title><Typography.Text type="secondary">{formatTimestamp(detail.createTime)}</Typography.Text></div><Tag color={STATUS_META[detail.status].color}>{STATUS_META[detail.status].label}</Tag></div>
        <Descriptions bordered size="small" column={1} items={[
          { key: 'type', label: '类型', children: TYPE_META[detail.feedbackType].label },
          { key: 'assignee', label: '当前处理人', children: detail.assigneeName || '待分派' },
          { key: 'activity', label: '最后更新', children: formatTimestamp(detail.lastActivityAt) }
        ]}/>
        {detail.rejectReason && <Alert className="feedback-detail-alert" type="error" showIcon message="审批驳回" description={detail.rejectReason}/>}
        <Typography.Title level={5}>提交内容</Typography.Title>
        <ValueDescriptions fields={detail.fields} values={detail.values}/>
        {detail.completedResult && <section className="feedback-detail-section"><Typography.Title level={5}>处理结果</Typography.Title><p>{detail.completedResult}</p><AttachmentLinks items={detail.resultAttachments}/></section>}
        <section className="feedback-detail-section"><Typography.Title level={5}>沟通记录</Typography.Title>{detail.replies?.length ? <Timeline items={detail.replies.map(reply => ({ children: <div><div><strong>{reply.authorName || '未知用户'}</strong><Tag className="feedback-author-tag">{reply.authorType === 'EMPLOYEE' ? '员工' : '处理人员'}</Tag></div><p>{reply.content}</p><AttachmentLinks items={reply.attachments}/><Typography.Text type="secondary">{formatTimestamp(reply.createTime)}</Typography.Text></div> }))}/> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无沟通记录"/>}</section>
        {detail.survey && <section className="feedback-detail-section"><Typography.Title level={5}>满意度</Typography.Title>{detail.survey.status === 'PENDING' ? <Alert type="info" showIcon message="等待评价"/> : <ValueDescriptions fields={detail.survey.fields} values={detail.survey.values}/>}</section>}
        <div className="feedback-detail-actions">
          {detail.canResubmit && hasPermission(permissions, 'zsjos:feedback:requirement:create') && <Button onClick={() => void openResubmit()}>修改并重提</Button>}
          {detail.canReply && hasPermission(permissions, 'zsjos:feedback:reply-self') && <Button icon={<MessageOutlined/>} onClick={() => setReplyOpen(true)}>回复</Button>}
          {detail.canSubmitSurvey && hasPermission(permissions, 'zsjos:feedback:survey:submit') && <Button type="primary" onClick={() => { surveyForm.resetFields(); setSurveyOpen(true) }}>满意度评价</Button>}
        </div>
      </div>}
    </ResizableDrawer>

    <Modal open={replyOpen} title="回复反馈" okText="发送" confirmLoading={saving} onCancel={() => setReplyOpen(false)} onOk={() => void submitReply()}>
      <Input.TextArea value={replyContent} onChange={event => setReplyContent(event.target.value)} rows={5} maxLength={5000} showCount placeholder="请输入回复内容"/>
      <div className="feedback-modal-files"><FeedbackAttachmentInput value={replyAttachments} onChange={setReplyAttachments}/></div>
    </Modal>

    <Modal open={surveyOpen} title="满意度评价" okText="提交评价" confirmLoading={saving} destroyOnHidden onCancel={() => setSurveyOpen(false)} onOk={() => void submitSurvey()}>
      {detail?.survey && <FeedbackDynamicForm form={surveyForm} fields={detail.survey.fields}/>}
    </Modal>
    {editorLoading && <div className="feedback-global-loading"><Spin/></div>}
  </section>
}
