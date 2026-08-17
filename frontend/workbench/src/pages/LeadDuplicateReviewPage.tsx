import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Avatar, Button, Descriptions, Drawer, Empty, Form, Input, InputNumber, Modal, Select, Skeleton, Space, Tabs, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type AssignmentUser, type LeadDuplicateReview, type LeadDuplicateReviewDecision } from '../services/api'
import { formatTimestamp } from '../services/time'
import DeferredAttachmentPicker from '../components/DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import type { LeadAttachment } from '../services/api'
import EmployeeSelect from '../components/EmployeeSelect'
import { createIdempotencyKey } from '../services/idempotency'

type ResultType = LeadDuplicateReviewDecision['resultType']
type DuplicateCandidate = { personId: number; leadId?: number; leadNo?: string; personName: string; leadStatus?: string }
const labels: Record<ResultType, string> = {
  new_person: '非重复，创建新客户', reuse_person: '复用客户并创建主客资',
  reactivate_lead: '激活无效或关闭客资', notify_owner: '提醒所属销售'
}
const resultLabel = (value?: string) => value && value in labels ? labels[value as ResultType] : value

export default function LeadDuplicateReviewPage({ permissions }: { permissions: string[] }) {
  const [status, setStatus] = useState<'pending' | 'completed'>('pending')
  const [items, setItems] = useState<LeadDuplicateReview[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<LeadDuplicateReview>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [processing, setProcessing] = useState<LeadDuplicateReview>()
  const [saving, setSaving] = useState(false)
  const [sales, setSales] = useState<AssignmentUser[]>([])
  const [form] = Form.useForm<LeadDuplicateReviewDecision>()
  const [files, setFiles] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const canProcess = permissions.includes('zsjos:lead-duplicate-review:process')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const next = (await api.duplicateReviewPage(status)).list
      setItems(next)
      setSelected(current => next.find(item => item.id === current?.id) ?? next[0])
    }
    catch (cause) { setItems([]); setSelected(undefined); setError(cause instanceof Error ? cause.message : '复核队列加载失败') }
    finally { setLoading(false) }
  }, [status])
  useEffect(() => { void load() }, [load])

  const openProcess = async (row: LeadDuplicateReview) => {
    setProcessing(row); form.resetFields(); setFiles([])
    try { setSales(await api.duplicateReviewSalesCandidates()) }
    catch (cause) { setSales([]); message.error(cause instanceof Error ? cause.message : '销售候选加载失败') }
  }
  const resultType = Form.useWatch('resultType', form)
  const parsed = useMemo(() => {
    if (!selected) return undefined
    try { return { submission: JSON.parse(selected.submissionSnapshot), rules: JSON.parse(selected.matchRules), candidates: JSON.parse(selected.candidateSnapshot) } }
    catch { return undefined }
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
    if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
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
            </Space>
          </div>
          {selected.status === 'pending' && canProcess && <Button type="primary" onClick={() => void openProcess(selected)}>处理</Button>}
        </header>
        <section className="message-detail-section">
          <Typography.Title level={5}>提交快照</Typography.Title>
          <Descriptions bordered column={1} size="small" items={Object.entries(parsed.submission).map(([key, value]) => ({ key, label: key, children: typeof value === 'object' ? JSON.stringify(value) : String(value ?? '-') }))}/>
        </section>
        <section className="message-detail-section">
          <Typography.Title level={5}>候选对象</Typography.Title>
          <pre className="duplicate-review-snapshot">{JSON.stringify(parsed.candidates, null, 2)}</pre>
        </section>
      </article>

  return <section className="workspace-page message-inbox-page duplicate-review-page">
    <header className="message-inbox-header">
      <div><Typography.Title level={4}>重复客资复核</Typography.Title><Typography.Text type="secondary">公共队列按提交时间处理，结论提交后不可覆盖</Typography.Text></div>
      <Tabs activeKey={status} onChange={key => setStatus(key as typeof status)} items={[{ key: 'pending', label: '待处理' }, { key: 'completed', label: '已处理' }]}/>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </header>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} 
    <div className="message-inbox-layout">
      <aside className="message-inbox-list-pane">
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
                    {item.resultType && <span>{resultLabel(item.resultType)}</span>}
                  </div>
                </div>
                <div className="message-inbox-item-meta"><span>{formatTimestamp(item.createTime)}</span></div>
              </button>
            }) : !error && <Empty description="暂无复核任务"/>}
        </div>
      </aside>
      <main className="message-inbox-detail-pane">{detail}</main>
    </div>
    <Drawer className="message-inbox-mobile-drawer" open={drawerOpen} title="复核任务详情" placement="right" width="100%" onClose={() => setDrawerOpen(false)}>{detail}</Drawer>
    <Modal open={Boolean(processing)} title={`处理复核任务 #${processing?.id}`} okText="提交结论" confirmLoading={saving} onOk={() => void submit()} onCancel={() => setProcessing(undefined)} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="resultType" label="复核结论" rules={[{ required: true }]}><Select options={Object.entries(labels).map(([value, label]) => ({ value, label }))}/></Form.Item>
        {resultType === 'reuse_person' && <Form.Item name="matchedPersonId" label="客户编号" rules={[{ required: true }]}><InputNumber min={1} style={{ width: '100%' }}/></Form.Item>}
        {(resultType === 'reactivate_lead' || resultType === 'notify_owner') && <Form.Item name="matchedLeadId" label="客资编号" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={processingCandidates.filter(item => item.leadId).map(item => ({ value: item.leadId!, label: `${item.leadNo || '编号不可用'} · ${item.personName} · ${item.leadStatus || '未知状态'}` }))}/></Form.Item>}
        {resultType === 'reactivate_lead' && <Form.Item name="selectedSalesUserId" label="归属销售" rules={[{ required: true }]}><EmployeeSelect users={sales} showSearch optionFilterProp="label" /></Form.Item>}
        <Form.Item name="opinion" label="复核意见" rules={[{ required: true, whitespace: true }, { max: 2000 }]}><Input.TextArea rows={4} maxLength={2000} showCount/></Form.Item>
        <Form.Item label="复核附件"><DeferredAttachmentPicker value={files} onChange={setFiles} accept="image/jpeg,image/png,image/webp" disabled={saving}/></Form.Item>
      </Form>
    </Modal>
  </section>
}
