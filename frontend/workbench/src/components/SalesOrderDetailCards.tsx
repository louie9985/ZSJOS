import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Empty, Image, Space, Table, Tag, Typography } from 'antd'
import { CheckOutlined, CloseOutlined, CopyOutlined, EditOutlined, StopOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { api, type SalesOrder, type SalesOrderListItem } from '../services/api'
import { formatTimestamp } from '../services/time'
import { buildDictionaryLabelMap, canReviewSalesOrderTask, resolveDictionaryLabel, type DictionaryLoadState } from '../services/salesOrder'
import { DICT_TYPE } from '../constants'
import DetailFieldGrid from './DetailFieldGrid'

export const SALES_ORDER_STATUS_LABELS: Record<SalesOrder['status'], string> = {
  pending_approval: '待审核', revision_required: '已驳回待修改', effective: '已通过', superseded: '已被重提', terminated: '已终止'
}
export const SALES_ORDER_STATUS_COLORS: Record<SalesOrder['status'], string> = {
  pending_approval: 'gold', revision_required: 'red', effective: 'green', superseded: 'default', terminated: 'default'
}
export const SALES_ORDER_TASK_LABELS: Record<string, string> = {
  registrationReview: '报名履约中心', financeReview: '财务结算中心'
}
const TASK_STATUS_LABELS: Record<number, string> = { 1: '审批中', 2: '审批通过', 3: '审批不通过', 4: '已取消', 5: '已退回', 7: '审批通过中' }
const APPROVAL_NODE_STATUS_LABELS: Record<string, string> = { pending: '审批中', approved: '已通过', rejected: '已驳回', cancelled: '已取消' }
const APPROVAL_NODE_STATUS_COLORS: Record<string, string> = { pending: 'gold', approved: 'green', rejected: 'red', cancelled: 'default' }
const ORDER_DICTIONARY_TYPES = [
  DICT_TYPE.LEAD_CATEGORY,
  DICT_TYPE.LEAD_SOURCE_CHANNEL,
  DICT_TYPE.ORDER_STUDENT_NATURE,
  DICT_TYPE.ORDER_SERVICE_PERIOD,
  DICT_TYPE.ORDER_STUDENT_SOURCE,
  DICT_TYPE.ORDER_FEE_MODE,
  DICT_TYPE.ORDER_PAYMENT_METHOD
]

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }
  return <button type="button" className={`lead-field-copy-btn ${copied ? 'copied' : ''}`} onClick={copy} title="复制">
    <CopyOutlined/>
  </button>
}

export default function SalesOrderDetailCards({ order, approvalContext, mode, onApprove, onReject, onRequestSupervisor, onRevise, onTerminate }: {
  order: SalesOrder
  approvalContext?: SalesOrderListItem
  mode: 'mine' | 'approval-todo' | 'approval-done'
  onApprove?: () => void
  onReject?: () => void
  onRequestSupervisor?: () => void
  onRevise?: () => void
  onTerminate?: () => void
}) {
  const [dictionaryState, setDictionaryState] = useState<DictionaryLoadState>('loading')
  const [dictionaryLabels, setDictionaryLabels] = useState<Record<string, Map<string, string>>>({})
  const loadDictionaries = useCallback(async () => {
    setDictionaryState('loading')
    try {
      const results = await Promise.all(ORDER_DICTIONARY_TYPES.map(type => api.dictDataByType(type)))
      setDictionaryLabels(Object.fromEntries(ORDER_DICTIONARY_TYPES.map((type, index) => [type, buildDictionaryLabelMap(results[index])])))
      setDictionaryState('ready')
    } catch {
      setDictionaryLabels({})
      setDictionaryState('error')
    }
  }, [])
  useEffect(() => { void loadDictionaries() }, [loadDictionaries])
  const label = (type: string, value?: string) => resolveDictionaryLabel(value, dictionaryLabels[type] || new Map(), dictionaryState)
  const task = approvalContext || order
  const canReview = approvalContext ? canReviewSalesOrderTask(order, approvalContext) : false
  const supervisorConfirmation = task.taskDefinitionKey === 'registrationReview'
    ? order.registrationSupervisorConfirmation : task.taskDefinitionKey === 'financeReview' ? order.financeSupervisorConfirmation : undefined
  const supervisorPending = supervisorConfirmation?.status === 'pending'
  const approvalRows = [
    { key: 'registration', center: '报名履约中心', approval: order.registrationApproval },
    { key: 'finance', center: '财务中心', approval: order.financeApproval },
    ...(order.supervisorApproval ? [{ key: 'supervisor', center: '销售主管', approval: {
      status: order.supervisorApproval.status === 'confirmed' ? 'approved' : order.supervisorApproval.status,
      reviewerUserName: order.supervisorApproval.supervisorUserName,
      createTime: order.supervisorApproval.requestedAt,
      endTime: order.supervisorApproval.decidedAt
    } }] : [])
  ]
  const leadProfile = order.leadProfile
  const sourceDispatchTag = leadProfile?.sourceType === 'internal_new_media' && leadProfile.dispatchMode === 'auto'
    ? { label: '自动分配', color: 'blue' as const }
    : leadProfile?.sourceType === 'internal_new_media' && leadProfile.dispatchMode === 'specified'
      ? { label: '指定派单', color: 'orange' as const }
      : undefined
  return <div className="sales-order-detail">
    <div className="sales-order-detail-hero">
      <div className="sales-order-detail-heading">
        <Space wrap><Typography.Title level={4}>{order.studentName}</Typography.Title><Tag color={SALES_ORDER_STATUS_COLORS[order.status]}>{SALES_ORDER_STATUS_LABELS[order.status]}</Tag></Space>
        <Typography.Text type="secondary">{order.orderNo} · 第 {order.approvalRoundNo || 1} 轮</Typography.Text>
      </div>
      <Space wrap className="sales-order-detail-actions">
        {mode === 'mine' && (order.status === 'revision_required' || order.status === 'terminated') && order.canRevise && onRevise && <Button type="primary" icon={<EditOutlined/>} onClick={onRevise}>修改并重新提交</Button>}
        {mode === 'mine' && order.canTerminate && onTerminate && <Button danger icon={<StopOutlined/>} onClick={onTerminate}>终止审批</Button>}
        {mode === 'approval-todo' && canReview && <><Button type="primary" icon={<CheckOutlined/>} onClick={onApprove}>通过</Button><Button danger icon={<CloseOutlined/>} onClick={onReject}>驳回</Button>{onRequestSupervisor && order.canRequestSupervisorConfirmation && <Button icon={<UserSwitchOutlined/>} onClick={onRequestSupervisor}>申请主管确认</Button>}</>}
      </Space>
    </div>
    {supervisorPending && <Alert type="info" showIcon message="销售主管加签审批中" description={supervisorConfirmation.requestReason}/>}
    {dictionaryState === 'error' && <Alert type="warning" showIcon message="详情字典标签加载失败" description="订单与客资原始编码未展示，请重试加载业务标签。"
      action={<Button size="small" onClick={() => void loadDictionaries()}>重试</Button>}/>}
    {order.status === 'revision_required' && <Alert type="error" showIcon message="订单已驳回，等待补正" description={order.decisionReason || '审批人未填写可展示的驳回原因'}/>}
    {order.status === 'terminated' && <Alert type="warning" showIcon message="订单审批已终止"
      description={order.terminationReason || '未记录终止原因'}/>}
    {order.status === 'superseded' && <Alert type="info" showIcon message="该订单已被重提" description="历史订单和审批结果已保留，请查看接续的新订单。"/>}
    {leadProfile && <section className="lead-card sales-order-lead-card">
      <div className="lead-card-header"><Typography.Text strong>客户档案</Typography.Text></div>
      <div className="lead-profile-fields">
        <div className="lead-profile-row"><span className="lead-field-label">姓名</span><span className="lead-field-value">{leadProfile.submittedName}</span></div>
        <div className="lead-profile-row"><span className="lead-field-label">客资编号</span><span className="lead-field-value">{leadProfile.leadNo}</span></div>
        <div className="lead-profile-row"><span className="lead-field-label">手机号</span>{leadProfile.submittedMobile
          ? <span className="lead-field-copyable"><span className="lead-field-value">{leadProfile.submittedMobile}</span><CopyButton value={leadProfile.submittedMobile}/></span>
          : <span className="lead-field-value lead-field-empty">未填写</span>}</div>
        <div className="lead-profile-row"><span className="lead-field-label">微信号</span>{leadProfile.submittedWechatId
          ? <span className="lead-field-copyable"><span className="lead-field-value">{leadProfile.submittedWechatId}</span><CopyButton value={leadProfile.submittedWechatId}/></span>
          : <span className="lead-field-value lead-field-empty">未填写</span>}</div>
      </div>
      <div className="lead-profile-meta">
        <div className="lead-profile-row"><span className="lead-field-label">来源</span><span className="lead-field-value lead-source-value"><span className="lead-source-label">{leadProfile.sourceLabel || '来源未配置'}</span>{sourceDispatchTag && <Tag color={sourceDispatchTag.color}>{sourceDispatchTag.label}</Tag>}</span></div>
        <div className="lead-profile-row"><span className="lead-field-label">提交人</span><span className="lead-field-value">{leadProfile.sourceUserName || '-'}</span></div>
        <div className="lead-profile-row"><span className="lead-field-label">所属销售</span><span className="lead-field-value">{leadProfile.ownerUserName || '暂未分配'}</span></div>
        <div className="lead-profile-row"><span className="lead-field-label">分类</span><span className="lead-field-value">{leadProfile.leadCategoryLabelSnapshot || label(DICT_TYPE.LEAD_CATEGORY, leadProfile.leadCategory)}</span></div>
        <div className="lead-profile-row"><span className="lead-field-label">渠道</span><span className="lead-field-value">{leadProfile.sourceChannelLabelSnapshot || label(DICT_TYPE.LEAD_SOURCE_CHANNEL, leadProfile.sourceChannel)}</span></div>
        <div className="lead-profile-row"><span className="lead-field-label">地区</span><span className="lead-field-value">{[leadProfile.provinceName, leadProfile.cityName].filter(Boolean).join(' / ') || '-'}</span></div>
      </div>
    </section>}
    <div className="sales-order-card-grid">
      <Card size="small" title="审批状态" className="sales-order-card sales-order-card-wide">
        <Table rowKey="key" size="small" pagination={false} scroll={{ x: 640 }} dataSource={approvalRows} columns={[
          { title: '审核中心', dataIndex: 'center' },
          { title: '审核结果', render: (_, row) => <Tag color={APPROVAL_NODE_STATUS_COLORS[row.approval?.status || '']}>{APPROVAL_NODE_STATUS_LABELS[row.approval?.status || ''] || '-'}</Tag> },
          { title: '审核人', render: (_, row) => row.approval?.reviewerUserName || '-' },
          { title: '审核时间', render: (_, row) => formatTimestamp(row.approval?.endTime) }
        ]}/>
      </Card>
      <Card size="small" title="订单与审批" className="sales-order-card">
        <DetailFieldGrid items={[
          { key: 'orderNo', label: '订单号', value: order.orderNo },
          { key: 'orderType', label: '订单类型', value: order.orderType === 'repurchase' ? '复购' : '首购' },
          { key: 'round', label: '审批轮次', value: `第 ${order.approvalRoundNo || 1} 轮` },
          { key: 'status', label: '当前状态', value: SALES_ORDER_STATUS_LABELS[order.status] },
          { key: 'center', label: '当前中心', value: SALES_ORDER_TASK_LABELS[task.taskDefinitionKey || ''] || '-' },
          { key: 'submittedAt', label: '提交时间', value: formatTimestamp(order.submittedAt) },
          { key: 'effectiveAt', label: '通过时间', value: formatTimestamp(order.effectiveAt) },
          ...(mode === 'approval-done' ? [
            { key: 'taskStatus', label: '处理结果', value: task.taskStatus == null ? '-' : TASK_STATUS_LABELS[task.taskStatus] || `状态 ${task.taskStatus}` },
            { key: 'taskEndTime', label: '处理时间', value: formatTimestamp(task.taskEndTime) },
            { key: 'taskReason', label: '审批意见', value: task.taskReason || '-', span: 2 as const }
          ] : [])
        ]}/>
      </Card>
      {order.repurchaseReason && <Card size="small" title="复购说明" className="sales-order-card"><Typography.Text>{order.repurchaseReason}</Typography.Text></Card>}
      <Card size="small" title="学员资料" className="sales-order-card">
        <DetailFieldGrid items={[
          { key: 'buyer', label: '购买方', value: order.buyerName },
          { key: 'student', label: '学员姓名', value: order.studentName },
          { key: 'nature', label: '学员性质', value: order.studentNatureLabelSnapshot || label(DICT_TYPE.ORDER_STUDENT_NATURE, order.studentNature) },
          { key: 'mobile', label: '手机号', value: order.studentMobile },
          { key: 'wechat', label: '微信号', value: order.studentWechatId },
          { key: 'region', label: '所在地区', value: [order.provinceName, order.cityName].filter(Boolean).join(' / ') }
        ]}/>
      </Card>
      <Card size="small" title="成交及付款" className="sales-order-card">
        <DetailFieldGrid items={[
          { key: 'amount', label: '订单总金额', value: `¥${Number(order.totalAmount).toFixed(2)}` },
          { key: 'paidAt', label: '客户付款时间', value: formatTimestamp(order.customerPaidAt) },
          { key: 'feeMode', label: '缴费方式', value: order.feeModeLabelSnapshot || label(DICT_TYPE.ORDER_FEE_MODE, order.feeMode) },
          { key: 'paymentMethod', label: '支付方式', value: order.paymentMethodLabelSnapshot || label(DICT_TYPE.ORDER_PAYMENT_METHOD, order.paymentMethod) },
          { key: 'examTime', label: '商定考试时间', value: order.agreedExamTime },
          { key: 'classType', label: '开通班种', value: order.classType },
          { key: 'servicePeriod', label: '服务周期', value: order.servicePeriodLabelSnapshot || label(DICT_TYPE.ORDER_SERVICE_PERIOD, order.servicePeriod) },
          { key: 'studentSource', label: '学生来源', value: order.studentSourceLabelSnapshot || label(DICT_TYPE.ORDER_STUDENT_SOURCE, order.studentSource) }
        ]}/>
      </Card>
      <Card size="small" title="成交课程" className="sales-order-card sales-order-card-wide">
        <Table rowKey="id" size="small" pagination={false} dataSource={order.items} columns={[
          { title: '课程', render: (_, item) => [item.categoryPath?.join(' / '), item.productName, item.skuName].filter(Boolean).join(' / ') || '-' },
          { title: '实际成交金额', width: 150, render: (_, item) => `¥${Number(item.actualAmount).toFixed(2)}` }
        ]}/>
      </Card>
      <Card size="small" title="备注与服务信息" className="sales-order-card">
        <DetailFieldGrid columns={1} items={[
          { key: 'remark', label: '订单备注', value: order.remark },
          { key: 'requirements', label: '学生特殊要求', value: order.studentSpecialRequirements },
          { key: 'delivery', label: '教材邮递联系', value: order.materialDeliveryContact }
        ]}/>
      </Card>
      <Card size="small" title="缴费凭证" className="sales-order-card">
        {order.paymentVouchers.length ? <Image.PreviewGroup><div className="sales-order-vouchers">{order.paymentVouchers.map(file => file.contentType === 'application/pdf'
          ? <Button key={file.infraFileId} href={file.fileUrl} target="_blank">{file.originalName}</Button>
          : <Image key={file.infraFileId} width={88} height={88} src={file.fileUrl} alt={file.originalName}/>)}</div></Image.PreviewGroup>
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无缴费凭证"/>}
      </Card>
    </div>
  </div>
}
