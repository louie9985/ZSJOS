import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Avatar, Button, Empty, Form, Input, Modal, Select, Skeleton, Space, Tabs, Tag, Typography, message } from 'antd'
import { api, type AdvancedFilterGroup, type LeadDuplicateReview, type LeadDuplicateReviewDecision } from '../services/api'
import { formatTimestamp } from '../services/time'
import DeferredAttachmentPicker from '../components/DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import type { LeadAttachment } from '../services/api'
import { createIdempotencyKey } from '../services/idempotency'
import DetailFieldGrid from '../components/DetailFieldGrid'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'
import { useInboxTableLayout } from '../services/inboxLayout'
import { ProTable } from '@ant-design/pro-components'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'

type ResultType = LeadDuplicateReviewDecision['resultType']
type DuplicateCandidate = { personId: number; leadId?: number; leadNo?: string; personName: string; leadStatus?: string }
const labels: Record<ResultType, string> = {
  allow_flow: '放行，进入正式客资流程',
  close_duplicate: '确认重复，关闭本次提交'
}
const legacyResultLabels: Record<string, string> = {
  strong_rejected: '强重复拦截',
  suspected_created: '疑似重复待确认',
  allowed: '已放行',
  closed: '已关闭',
  auto_closed: '自动关闭',
  new_person: '旧记录：新建客户',
  reuse_person: '旧记录：复用客户',
  reactivate_lead: '旧记录：激活客资',
  notify_owner: '旧记录：提醒所属销售'
}
const duplicateFlagLabels: Record<string, string> = {
  none: '未发现重复', strong_duplicate: '强重复', suspected_duplicate: '疑似重复'
}
const resultLabel = (value?: string) => value ? (labels[value as ResultType] ?? legacyResultLabels[value] ?? value) : value

type DuplicateReviewSnapshot = {
  submission: Record<string, unknown>
  rules: unknown[]
  candidates: unknown[]
}

function parseReviewSnapshot(item: LeadDuplicateReview): DuplicateReviewSnapshot | undefined {
  try {
    const submission = JSON.parse(item.submissionSnapshot)
    const rules = JSON.parse(item.matchRules)
    const candidates = JSON.parse(item.candidateSnapshot)
    return {
      submission: submission && typeof submission === 'object' && !Array.isArray(submission) ? submission : {},
      rules: Array.isArray(rules) ? rules : [],
      candidates: Array.isArray(candidates) ? candidates : []
    }
  } catch { return undefined }
}

function snapshotValue(snapshot: DuplicateReviewSnapshot | undefined, keys: string[]) {
  const value = keys.map(key => snapshot?.submission[key]).find(item => typeof item === 'string' && item.trim())
  return typeof value === 'string' ? value : '-'
}

export default function LeadDuplicateReviewPage({ permissions }: { permissions: string[] }) {
  const [status, setStatus] = useState<'pending' | 'completed'>('pending')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [items, setItems] = useState<LeadDuplicateReview[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<LeadDuplicateReview>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [processing, setProcessing] = useState<LeadDuplicateReview>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<LeadDuplicateReviewDecision>()
  const [files, setFiles] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const canProcess = permissions.includes('zsjos:lead-duplicate-review:process')
  const { useTableLayout } = useInboxTableLayout()
  const reviewSnapshots = useMemo(
    () => new Map(items.map(item => [item.id, parseReviewSnapshot(item)])),
    [items]
  )

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const next = (await api.duplicateReviewPage({ status, pageNo: 1, pageSize: 100, keyword: keyword || undefined, advancedFilter })).list
      setItems(next)
      setSelected(current => next.find(item => item.id === current?.id) ?? next[0])
    }
    catch (cause) { setItems([]); setSelected(undefined); setError(cause instanceof Error ? cause.message : '复核队列加载失败') }
    finally { setLoading(false) }
  }, [advancedFilter, keyword, status])
  useEffect(() => { void load() }, [load])

  const openProcess = async (row: LeadDuplicateReview) => {
    setProcessing(row); form.resetFields(); setFiles([])
  }
  const resultType = Form.useWatch('resultType', form)
  const parsed = useMemo(() => {
    if (!selected) return undefined
    return parseReviewSnapshot(selected)
  }, [selected])
  const processingCandidates = useMemo<DuplicateCandidate[]>(() => {
    if (!processing) return []
    try { return JSON.parse(processing.candidateSnapshot) as DuplicateCandidate[] }
    catch { return [] }
  }, [processing])

  const submit = async () => {
    if (!processing) return
    const values = await form.validateFields()
    setSaving(true)
    try {
      const uploaded = await uploadDeferredFiles(files, api.uploadDuplicateReviewAttachment, setFiles)
      if (uploaded.failed) { message.error('复核附件上传失败，请修正后重试'); return }
      await api.decideDuplicateReview(processing.id, { ...values,
        attachments: uploaded.items.map(item => ({ infraFileId: item.uploaded!.infraFileId })), idempotencyKey: createIdempotencyKey() })
      message.success('复核结论已提交'); setProcessing(undefined); await load()
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '复核提交失败'); await load() }
    finally { setSaving(false) }
  }

  const selectReview = (item: LeadDuplicateReview) => {
    setSelected(item)
    if (useTableLayout || window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
  }

  const detail = !selected ? <Empty description="从左侧选择一条复核任务"/>
    : !parsed ? <Alert type="error" message="任务快照无法解析"/>
      : <article className="message-inbox-detail duplicate-review-detail">
        <header className="message-detail-hero">
          <Avatar size={42}>复</Avatar>
          <div className="message-detail-heading">
            <Typography.Title level={4}>复核任务 #{selected.id}</Typography.Title>
            <Space wrap>
              <Tag color={selected.status === 'pending' ? 'processing' : 'success'}>{selected.status === 'pending' ? '待处理' : '已处理'}</Tag>
              {selected.resultType && <Tag>{resultLabel(selected.resultType)}</Tag>}
              {selected.duplicateResult && <Tag>{resultLabel(selected.duplicateResult)}</Tag>}
            </Space>
          </div>
          {selected.status === 'pending' && canProcess && <Button type="primary" onClick={() => void openProcess(selected)}>处理</Button>}
        </header>
        <section className="lead-card duplicate-review-section">
          <Typography.Title level={5}>提交快照</Typography.Title>
          <DetailFieldGrid columns={1} items={Object.entries(parsed.submission).map(([key, value]) => ({
            key,
            label: key,
            value: typeof value === 'object' ? JSON.stringify(value) : String(value ?? '-')
          }))}/>
        </section>
        <section className="lead-card duplicate-review-section">
          <Typography.Title level={5}>候选对象</Typography.Title>
          <pre className="duplicate-review-snapshot">{JSON.stringify(parsed.candidates, null, 2)}</pre>
        </section>
      </article>

  return <section className={`workspace-page message-inbox-page duplicate-review-page${useTableLayout ? ' business-inbox-table-page' : ''}`}>
    <header className="message-inbox-header">
      <div><Typography.Title level={4}>重复客资复核</Typography.Title><Typography.Text type="secondary">公共队列按提交时间处理，结论提交后不可覆盖</Typography.Text></div>
      <Tabs activeKey={status} onChange={key => setStatus(key as typeof status)} items={[{ key: 'pending', label: '待处理' }, { key: 'completed', label: '已处理' }]}/>
    </header>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} 
    {useTableLayout ? <ProTable<LeadDuplicateReview>
      className="business-inbox-table"
      rowKey="id"
      search={false}
      options={{ density: true, fullScreen: true, setting: true }}
      columnsState={{ persistenceKey: 'crm-lead-duplicate-review-table-columns', persistenceType: 'localStorage' }}
      loading={loading}
      dataSource={items}
      pagination={false}
      scroll={{ x: 2600 }}
      locale={{ emptyText: <Empty description="暂无复核任务" /> }}
      columns={[
        { title: '提交姓名', width: 140, render: (_, item) => snapshotValue(reviewSnapshots.get(item.id), ['submittedName', 'name', 'studentName']) },
        { title: '手机号', width: 150, render: (_, item) => snapshotValue(reviewSnapshots.get(item.id), ['submittedMobile', 'mobile', 'studentMobile']) },
        { title: '微信号', width: 150, render: (_, item) => snapshotValue(reviewSnapshots.get(item.id), ['submittedWechatId', 'wechatId', 'studentWechatId']) },
        { title: '状态', render: (_, item) => <Tag color={item.status === 'pending' ? 'processing' : 'success'}>{item.status === 'pending' ? '待处理' : '已处理'}</Tag>, width: 110 },
        { title: '重复标记', dataIndex: 'duplicateFlag', width: 140, render: value => value ? duplicateFlagLabels[String(value)] || value : '-' },
        { title: '结论', render: (_, item) => resultLabel(item.duplicateResult || item.resultType) || '-' },
        { title: '主规则', dataIndex: 'primaryRuleCode', width: 180, ellipsis: true, render: value => value || '-' },
        { title: '命中规则', width: 260, ellipsis: true, render: (_, item) => reviewSnapshots.get(item.id)?.rules.map(rule => typeof rule === 'string' ? rule : JSON.stringify(rule)).join('；') || '-' },
        { title: '候选对象', width: 110, render: (_, item) => `${reviewSnapshots.get(item.id)?.candidates.length || 0} 个` },
        { title: '复核意见', dataIndex: 'reviewOpinion', width: 260, ellipsis: true, render: value => value || '-' },
        { title: '创建时间', dataIndex: 'createTime', render: (_, item) => formatTimestamp(item.createTime), width: 170 },
        { title: '复核时间', dataIndex: 'reviewedAt', render: (_, item) => formatTimestamp(item.reviewedAt), width: 170 },
        { title: '操作', width: 88, fixed: 'right', hideInSetting: true, render: (_, item) => <Button type="link" onClick={() => selectReview(item)}>详细</Button> }
      ]}
    /> : <div className="message-inbox-layout">
      <aside className="message-inbox-list-pane">
        <div className="message-inbox-toolbar"><AdvancedFilterToolbar scene="duplicate_review" pageKey="lead_duplicate_review" placeholder="搜索姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/></div>
        <div className="message-inbox-list" aria-label="重复客资复核列表">
          {loading ? <div className="message-inbox-skeleton"><Skeleton active paragraph={{ rows: 8 }}/></div>
            : items.length ? items.map(item => {
              let rules: string[] = []
              try { rules = JSON.parse(item.matchRules) as string[] } catch { rules = ['规则快照异常'] }
              return <button key={item.id} type="button" className={`message-inbox-item${selected?.id === item.id ? ' active' : ''}`} onClick={() => selectReview(item)}>
                <div className="message-inbox-item-main">
                  <Avatar>{String(item.id).slice(-2)}</Avatar>
                  <div className="message-inbox-item-copy">
                    <div className="message-inbox-item-title"><strong>复核任务 #{item.id}</strong><Tag color={item.status === 'pending' ? 'processing' : 'success'}>{item.status === 'pending' ? '待处理' : '已处理'}</Tag></div>
                    <span>{rules.join('、')}</span>
                    {(item.duplicateResult || item.resultType) && <span>{resultLabel(item.duplicateResult || item.resultType)}</span>}
                  </div>
                </div>
                <div className="message-inbox-item-meta"><span>{formatTimestamp(item.createTime)}</span></div>
              </button>
            }) : !error && <Empty description="暂无复核任务"/>}
        </div>
      </aside>
      <main className="message-inbox-detail-pane">{detail}</main>
    </div>}
    <ResizableDetailDrawer desktopResizable={useTableLayout} className="message-inbox-mobile-drawer" open={drawerOpen} title="复核任务详情" placement="right" width="100%" onClose={() => setDrawerOpen(false)}>{detail}</ResizableDetailDrawer>
    <Modal open={Boolean(processing)} title={`处理复核任务 #${processing?.id}`} okText="提交结论" confirmLoading={saving} onOk={() => void submit()} onCancel={() => setProcessing(undefined)} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="resultType" label="复核结论" rules={[{ required: true }]}><Select options={Object.entries(labels).map(([value, label]) => ({ value, label }))}/></Form.Item>
        {resultType === 'close_duplicate' && processingCandidates.length > 0 && <Alert type="warning" showIcon message="确认关闭后不会创建客资，也不会进入分配或业绩统计" />}
        {resultType === 'allow_flow' && <Alert type="info" showIcon message="放行后将按原提交快照创建正式客资，并继续进入分配或销售跟进流程" />}
        <Form.Item name="opinion" label="复核意见" rules={[{ required: true, whitespace: true }, { max: 2000 }]}><Input.TextArea rows={4} maxLength={2000} showCount/></Form.Item>
        <Form.Item label="复核附件"><DeferredAttachmentPicker value={files} onChange={setFiles} accept="image/jpeg,image/png,image/webp" disabled={saving}/></Form.Item>
      </Form>
    </Modal>
  </section>
}
