import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Form, Input, InputNumber, Modal, Select, Space, Spin, Table, Tabs, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type AssignmentUser, type LeadDuplicateReview, type LeadDuplicateReviewDecision } from '../services/api'
import { formatTimestamp } from '../services/time'
import DeferredAttachmentPicker from '../components/DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import type { LeadAttachment } from '../services/api'

type ResultType = LeadDuplicateReviewDecision['resultType']
const labels: Record<ResultType, string> = {
  new_person: '非重复，创建新客户', reuse_person: '复用客户并创建主客资',
  reactivate_lead: '激活无效或关闭客资', notify_owner: '提醒所属销售'
}

export default function LeadDuplicateReviewPage({ permissions }: { permissions: string[] }) {
  const [status, setStatus] = useState<'pending' | 'completed'>('pending')
  const [items, setItems] = useState<LeadDuplicateReview[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<LeadDuplicateReview>()
  const [processing, setProcessing] = useState<LeadDuplicateReview>()
  const [saving, setSaving] = useState(false)
  const [sales, setSales] = useState<AssignmentUser[]>([])
  const [form] = Form.useForm<LeadDuplicateReviewDecision>()
  const [files, setFiles] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const canProcess = permissions.includes('zsjos:lead-duplicate-review:process')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setItems((await api.duplicateReviewPage(status)).list) }
    catch (cause) { setItems([]); setError(cause instanceof Error ? cause.message : '复核队列加载失败') }
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

  const submit = async () => {
    if (!processing) return
    const values = await form.validateFields()
    setSaving(true)
    try {
      const uploaded = await uploadDeferredFiles(files, api.uploadDuplicateReviewAttachment, setFiles)
      if (uploaded.failed) { message.error('复核附件上传失败，请修正后重试'); return }
      await api.decideDuplicateReview(processing.id, { ...values,
        attachments: uploaded.items.map(item => ({ infraFileId: item.uploaded!.infraFileId })), idempotencyKey: crypto.randomUUID() })
      message.success('复核结论已提交'); setProcessing(undefined); await load()
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '复核提交失败'); await load() }
    finally { setSaving(false) }
  }

  return <section className="workspace-page">
    <div className="workspace-page-heading">
      <div><Typography.Title level={3}>重复客资复核</Typography.Title><Typography.Text type="secondary">公共队列按提交时间处理，结论提交后不可覆盖</Typography.Text></div>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    <Tabs activeKey={status} onChange={key => setStatus(key as typeof status)} items={[{ key: 'pending', label: '待处理' }, { key: 'completed', label: '已处理' }]}/>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} 
    <Spin spinning={loading}><Table rowKey="id" dataSource={items} pagination={false} locale={{ emptyText: <Empty description="暂无复核任务"/> }} scroll={{ x: 920 }} columns={[
      { title: '任务', dataIndex: 'id', width: 90, render: id => `#${id}` },
      { title: '命中规则', dataIndex: 'matchRules', render: value => { try { return <Space wrap>{JSON.parse(value).map((rule: string) => <Tag key={rule}>{rule}</Tag>)}</Space> } catch { return '规则快照异常' } } },
      { title: '提交时间', dataIndex: 'createTime', width: 180, render: value => formatTimestamp(value) },
      { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={value === 'pending' ? 'processing' : 'success'}>{value === 'pending' ? '待处理' : '已处理'}</Tag> },
      { title: '结论', dataIndex: 'resultType', width: 200, render: value => value ? labels[value as ResultType] : '-' },
      { title: '操作', key: 'actions', fixed: 'right', width: 170, render: (_, row) => <Space><Button size="small" onClick={() => setSelected(row)}>详情</Button>{row.status === 'pending' && canProcess && <Button size="small" type="primary" onClick={() => void openProcess(row)}>处理</Button>}</Space> }
    ]}/></Spin>
    <Drawer open={Boolean(selected)} title="复核任务详情" width={640} onClose={() => setSelected(undefined)}>
      {!parsed ? <Alert type="error" message="任务快照无法解析"/> : <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Descriptions bordered column={1} size="small" items={Object.entries(parsed.submission).map(([key, value]) => ({ key, label: key, children: typeof value === 'object' ? JSON.stringify(value) : String(value ?? '-') }))}/>
        <div><Typography.Title level={5}>候选对象</Typography.Title><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{JSON.stringify(parsed.candidates, null, 2)}</pre></div>
      </Space>}
    </Drawer>
    <Modal open={Boolean(processing)} title={`处理复核任务 #${processing?.id}`} okText="提交结论" confirmLoading={saving} onOk={() => void submit()} onCancel={() => setProcessing(undefined)} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="resultType" label="复核结论" rules={[{ required: true }]}><Select options={Object.entries(labels).map(([value, label]) => ({ value, label }))}/></Form.Item>
        {resultType === 'reuse_person' && <Form.Item name="matchedPersonId" label="客户编号" rules={[{ required: true }]}><InputNumber min={1} style={{ width: '100%' }}/></Form.Item>}
        {(resultType === 'reactivate_lead' || resultType === 'notify_owner') && <Form.Item name="matchedLeadId" label="客资编号" rules={[{ required: true }]}><InputNumber min={1} style={{ width: '100%' }}/></Form.Item>}
        {resultType === 'reactivate_lead' && <Form.Item name="selectedSalesUserId" label="归属销售" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={sales.map(user => ({ value: user.id, label: `${user.nickname}${user.deptName ? ` · ${user.deptName}` : ''}` }))}/></Form.Item>}
        <Form.Item name="opinion" label="复核意见" rules={[{ required: true, whitespace: true }, { max: 2000 }]}><Input.TextArea rows={4} maxLength={2000} showCount/></Form.Item>
        <Form.Item label="复核附件"><DeferredAttachmentPicker value={files} onChange={setFiles} accept="image/jpeg,image/png,image/webp" disabled={saving}/></Form.Item>
      </Form>
    </Modal>
  </section>
}
