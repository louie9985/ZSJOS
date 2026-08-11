import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Avatar, Button, Descriptions, Drawer, Empty, Image, Input, List, Modal, Skeleton, Space, Table, Tabs, Tag, Typography, message } from 'antd'
import { CheckOutlined, CloseOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type SalesOrder } from '../services/api'
import { formatTimestamp } from '../services/time'

const statusLabel: Record<string, string> = { pending_approval: '待会签', revision_required: '待补正', effective: '已生效' }
const taskLabel: Record<string, string> = { registrationReview: '报名履约中心', financeReview: '财务结算中心' }

export default function SalesOrderApprovalPage() {
  const [handled, setHandled] = useState(false)
  const [items, setItems] = useState<SalesOrder[]>([])
  const [selected, setSelected] = useState<SalesOrder>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [decision, setDecision] = useState<'approve' | 'reject'>()
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { const result = await api.salesOrderApprovalInbox(handled, { pageNo: 1, pageSize: 100 }); setItems(result.list); setSelected(current => result.list.find(item => item.id === current?.id) || result.list[0]) }
    catch (loadError) { setItems([]); setSelected(undefined); setError(loadError instanceof Error ? loadError.message : '成交审批加载失败') }
    finally { setLoading(false) }
  }, [handled])
  useEffect(() => { void load() }, [load])

  const submitDecision = async () => {
    if (!selected?.taskId || !reason.trim() || !decision) { message.warning('请填写审批意见'); return }
    setSaving(true)
    try { await api.decideSalesOrder(selected.id, decision, { taskId: selected.taskId, reason: reason.trim() }); message.success(decision === 'approve' ? '已通过' : '已驳回并退回销售补正'); setDecision(undefined); setReason(''); await load() }
    catch (saveError) { message.error(saveError instanceof Error ? saveError.message : '审批失败') }
    finally { setSaving(false) }
  }

  const detail = useMemo(() => selected ? <div className="message-detail-card">
    <div className="message-detail-header"><div><Typography.Title level={4}>{selected.studentName}</Typography.Title><Typography.Text type="secondary">{selected.orderNo} · 第 {selected.approvalRoundNo} 轮</Typography.Text></div><Tag color={selected.status === 'effective' ? 'green' : selected.status === 'revision_required' ? 'red' : 'gold'}>{statusLabel[selected.status]}</Tag></div>
    <Descriptions column={{ xs: 1, md: 2 }} layout="vertical" size="small">
      <Descriptions.Item label="购买方">{selected.buyerName}</Descriptions.Item><Descriptions.Item label="学员性质">{selected.studentNature}</Descriptions.Item>
      <Descriptions.Item label="联系方式">{[selected.studentMobile, selected.studentWechatId].filter(Boolean).join(' / ')}</Descriptions.Item><Descriptions.Item label="所在省市">{selected.provinceName} / {selected.cityName}</Descriptions.Item>
      <Descriptions.Item label="商定考试时间">{selected.agreedExamTime || '-'}</Descriptions.Item><Descriptions.Item label="开通班种">{selected.classType || '-'}</Descriptions.Item>
      <Descriptions.Item label="服务周期">{selected.servicePeriod}</Descriptions.Item><Descriptions.Item label="学生来源">{selected.studentSource}</Descriptions.Item>
      <Descriptions.Item label="客户付款时间">{formatTimestamp(selected.customerPaidAt)}</Descriptions.Item><Descriptions.Item label="订单总金额">¥{Number(selected.totalAmount).toFixed(2)}</Descriptions.Item>
      <Descriptions.Item label="缴费方式">{selected.feeMode}</Descriptions.Item><Descriptions.Item label="支付方式">{selected.paymentMethod}</Descriptions.Item>
      <Descriptions.Item label="订单备注" span={2}>{selected.remark || '-'}</Descriptions.Item><Descriptions.Item label="学生特殊要求" span={2}>{selected.studentSpecialRequirements || '-'}</Descriptions.Item>
      <Descriptions.Item label="教材邮递联系" span={2}>{selected.materialDeliveryContact || '-'}</Descriptions.Item>
    </Descriptions>
    <Typography.Title level={5}>成交课程</Typography.Title>
    <Table rowKey="id" size="small" pagination={false} dataSource={selected.items} columns={[
      { title: '课程', render: (_, item) => [item.categoryPath?.join(' / '), item.productName, item.skuName].filter(Boolean).join(' / ') },
      { title: '实际成交金额', width: 150, render: (_, item) => `¥${Number(item.actualAmount).toFixed(2)}` }
    ]}/>
    <Typography.Title level={5}>缴费凭证</Typography.Title>
    {selected.paymentVouchers.length ? <Space wrap>{selected.paymentVouchers.map(file => file.contentType === 'application/pdf'
      ? <Button key={file.infraFileId} href={file.fileUrl} target="_blank">{file.originalName}</Button>
      : <Image key={file.infraFileId} width={88} height={88} src={file.fileUrl} alt={file.originalName}/>)}</Space> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无缴费凭证"/>}
    {!handled && selected.taskId && <Space style={{ marginTop: 20 }}><Button type="primary" icon={<CheckOutlined/>} onClick={() => setDecision('approve')}>通过</Button><Button danger icon={<CloseOutlined/>} onClick={() => setDecision('reject')}>驳回</Button></Space>}
  </div> : <Empty description="请选择成交订单"/>, [handled, selected])

  return <section className="workspace-page message-inbox-page">
    <header className="message-inbox-header"><Typography.Title level={3}>成交审批</Typography.Title><Tabs activeKey={handled ? 'done' : 'todo'} onChange={key => setHandled(key === 'done')} items={[{ key: 'todo', label: '待处理' }, { key: 'done', label: '已处理' }]}/><Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button></header>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} 
    <div className="message-inbox-layout"><aside className="message-inbox-list-pane"><div className="message-inbox-list">{loading ? <Skeleton active paragraph={{ rows: 8 }}/> : items.length ? <List dataSource={items} renderItem={item => <button type="button" className={`message-inbox-item${selected?.id === item.id ? ' active' : ''}`} onClick={() => { setSelected(item); setDrawerOpen(true) }}><div className="message-inbox-item-main"><Avatar>{item.studentName.slice(0, 1)}</Avatar><div className="message-inbox-item-copy"><div className="message-inbox-item-title"><strong>{item.studentName}</strong><Tag>{taskLabel[item.taskDefinitionKey || ''] || '成交会签'}</Tag></div><span>{item.orderNo}</span><span>¥{Number(item.totalAmount).toFixed(2)} · {item.items.length} 个课程</span></div></div><div className="message-inbox-item-meta"><span>{formatTimestamp(item.submittedAt)}</span></div></button>}/> : <Empty description="暂无成交审批"/>}</div></aside><main className="message-inbox-detail-pane">{detail}</main></div>
    <Drawer className="message-inbox-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="成交订单详情" placement="right" width="100%">{detail}</Drawer>
    <Modal title={decision === 'approve' ? '通过成交订单' : '驳回成交订单'} open={Boolean(decision)} confirmLoading={saving} onCancel={() => setDecision(undefined)} onOk={() => void submitDecision()} okText="提交审批"><Input.TextArea rows={5} maxLength={1000} showCount value={reason} onChange={event => setReason(event.target.value)} placeholder="审批意见（必填）"/></Modal>
  </section>
}
