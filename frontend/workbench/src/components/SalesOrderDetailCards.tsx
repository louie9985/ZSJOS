import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Image, Space, Table, Tag, Timeline, Typography } from 'antd'
import { CheckOutlined, CloseOutlined, CopyOutlined, EditOutlined, StopOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { api, type SalesOrder, type SalesOrderApprovalStatus, type SalesOrderListItem, type SalesOrderSupervisorApproval, type SalesOrderSupervisorConfirmation } from '../services/api'
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

type ApprovalNode = {
  key: 'registration' | 'finance'
  title: string
  description: string
  approval?: SalesOrderApprovalStatus
  supervisorConfirmation?: SalesOrderSupervisorConfirmation
  supervisorApproval?: SalesOrderSupervisorApproval
  active: boolean
}

type ApprovalStatus = keyof typeof APPROVAL_NODE_STATUS_LABELS | 'not_started'

const SUPERVISOR_STATUS_LABELS: Record<string, string> = {
  pending: '等待确认', confirmed: '已确认', rejected: '已驳回', cancelled: '已取消'
}

const SUPERVISOR_STATUS_COLORS: Record<string, string> = {
  pending: 'gold', confirmed: 'green', rejected: 'red', cancelled: 'default'
}

function ApprovalStatusTag({ status, supervisor = false }: { status: ApprovalStatus | string; supervisor?: boolean }) {
  const labels = supervisor ? SUPERVISOR_STATUS_LABELS : { ...APPROVAL_NODE_STATUS_LABELS, not_started: '待处理' }
  const colors = supervisor ? SUPERVISOR_STATUS_COLORS : { ...APPROVAL_NODE_STATUS_COLORS, not_started: 'default' }
  return <Tag color={colors[status] || 'default'}>{labels[status] || '未知状态'}</Tag>
}

function approvalTimelineColor(status: ApprovalStatus) {
  if (status === 'approved') return 'green'
  if (status === 'rejected') return 'red'
  if (status === 'pending') return 'blue'
  return 'gray'
}

function SalesOrderApprovalRail({ nodes }: { nodes: ApprovalNode[] }) {
  const items = nodes.map(node => {
    const status: ApprovalStatus = node.approval?.status || (node.active ? 'pending' : 'not_started')
    const supervisorStatus = node.supervisorApproval?.status || node.supervisorConfirmation?.status
    const supervisorReviewer = node.supervisorApproval?.supervisorUserName
      || (node.supervisorConfirmation?.status === 'confirmed' ? '销售主管' : undefined)
    return {
      color: approvalTimelineColor(status),
      content: <article className={`sales-order-approval-node${node.active ? ' active' : ''}`}>
        <div className="sales-order-approval-node-heading">
          <Typography.Text strong>{node.title}</Typography.Text>
          <ApprovalStatusTag status={status}/>
        </div>
        {node.active && <Typography.Text className="sales-order-approval-current">当前节点</Typography.Text>}
        <Typography.Text type="secondary" className="sales-order-approval-node-description">{node.description}</Typography.Text>
        <div className="sales-order-approval-node-meta">
          <span>审批人：{node.approval?.reviewerUserName || (node.active ? '待处理' : '-')}</span>
          <span>{node.approval?.endTime ? `完成时间：${formatTimestamp(node.approval.endTime)}` : node.approval?.createTime ? `开始时间：${formatTimestamp(node.approval.createTime)}` : ''}</span>
        </div>
        {supervisorStatus && <div className="sales-order-supervisor-signoff">
          <div className="sales-order-supervisor-signoff-heading">
            <Typography.Text strong>销售主管会签</Typography.Text>
            <ApprovalStatusTag status={supervisorStatus} supervisor/>
          </div>
          <div className="sales-order-approval-node-meta">
            <span>申请人：{node.supervisorConfirmation?.requesterUserName || '-'}</span>
            <span>确认人：{supervisorReviewer || '-'}</span>
            <span>{node.supervisorConfirmation?.decidedAt ? `确认时间：${formatTimestamp(node.supervisorConfirmation.decidedAt)}` : node.supervisorConfirmation?.requestedAt ? `申请时间：${formatTimestamp(node.supervisorConfirmation.requestedAt)}` : ''}</span>
          </div>
          {(node.supervisorConfirmation?.requestReason || node.supervisorConfirmation?.decisionReason) && <Typography.Text type="secondary" className="sales-order-supervisor-signoff-reason">{node.supervisorConfirmation.decisionReason || node.supervisorConfirmation.requestReason}</Typography.Text>}
        </div>}
      </article>
    }
  })
  return <section className="sales-order-approval-rail" aria-label="审批流程">
    <div className="sales-order-section-heading">
      <div>
        <Typography.Text strong>审批流程</Typography.Text>
        <Typography.Text type="secondary">会签节点状态</Typography.Text>
      </div>
    </div>
    <Timeline className="sales-order-approval-track" items={items}/>
  </section>
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
  const approvalNodes: ApprovalNode[] = [
    {
      key: 'registration', title: '报名履约中心', description: '报名资料与履约条件审核',
      approval: order.registrationApproval,
      supervisorConfirmation: order.registrationSupervisorConfirmation,
      supervisorApproval: order.supervisorApproval?.center === 'registration' ? order.supervisorApproval : undefined,
      active: task.taskDefinitionKey === 'registrationReview'
    },
    {
      key: 'finance', title: '财务结算中心', description: '付款信息与结算条件审核',
      approval: order.financeApproval,
      supervisorConfirmation: order.financeSupervisorConfirmation,
      supervisorApproval: order.supervisorApproval?.center === 'finance' ? order.supervisorApproval : undefined,
      active: task.taskDefinitionKey === 'financeReview'
    }
  ]
  const leadProfile = order.leadProfile
  const sourceDispatchTag = leadProfile?.sourceType === 'internal_new_media' && leadProfile.dispatchMode === 'auto'
    ? { label: '自动分配', color: 'blue' as const }
    : leadProfile?.sourceType === 'internal_new_media' && leadProfile.dispatchMode === 'specified'
      ? { label: '指定派单', color: 'orange' as const }
      : undefined
  const hasApprovalActions = Boolean((mode === 'mine' && (
    ((order.status === 'revision_required' || order.status === 'terminated') && order.canRevise && onRevise)
    || (order.canTerminate && onTerminate)
  )) || (mode === 'approval-todo' && canReview))
  const statusAlert = (() => {
    if (order.status === 'revision_required') return {
      type: 'error' as const, title: '订单已驳回，等待补正',
      description: order.decisionReason || '审批人未填写可展示的驳回原因'
    }
    if (order.status === 'terminated') return {
      type: 'warning' as const, title: '订单审批已终止', description: order.terminationReason || '未记录终止原因'
    }
    if (order.status === 'superseded') return {
      type: 'info' as const, title: '该订单已被重提', description: '历史订单和审批结果已保留，请查看接续的新订单。'
    }
    if (order.status === 'effective') return {
      type: 'success' as const, title: '订单审批已通过',
      description: order.effectiveAt ? `通过时间：${formatTimestamp(order.effectiveAt)}` : '双中心审批已完成'
    }
    return {
      type: 'info' as const, title: supervisorPending ? '销售主管会签中' : '订单待审核',
      description: supervisorPending
        ? supervisorConfirmation.requestReason || '等待销售主管确认'
        : `等待${SALES_ORDER_TASK_LABELS[task.taskDefinitionKey || ''] || '审批中心'}处理`
    }
  })()
  return <div className="sales-order-detail">
    <div className="sales-order-detail-hero">
      <div className="sales-order-detail-heading">
        <Space wrap><Typography.Title level={4}>{order.studentName}</Typography.Title><Tag color={SALES_ORDER_STATUS_COLORS[order.status]}>{SALES_ORDER_STATUS_LABELS[order.status]}</Tag></Space>
        <Typography.Text type="secondary">{order.orderNo} · 第 {order.approvalRoundNo || 1} 轮</Typography.Text>
      </div>
    </div>
    {dictionaryState === 'error' && <Alert type="warning" showIcon title="详情字典标签加载失败" description="订单与客资原始编码未展示，请重试加载业务标签。"
      action={<Button size="small" onClick={() => void loadDictionaries()}>重试</Button>}/>}
    <div className="sales-order-detail-layout">
      <main className="sales-order-detail-main">
        <section className="sales-order-information">
      {leadProfile && <section className="sales-order-info-block sales-order-info-block-wide">
        <div className="sales-order-section-heading"><Typography.Text strong>客户档案</Typography.Text></div>
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
      <section className="sales-order-info-block">
        <div className="sales-order-section-heading"><Typography.Text strong>订单概览</Typography.Text></div>
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
      </section>
      <section className="sales-order-info-block">
        <div className="sales-order-section-heading"><Typography.Text strong>学员资料</Typography.Text></div>
        <DetailFieldGrid items={[
          { key: 'buyer', label: '购买方', value: order.buyerName },
          { key: 'student', label: '学员姓名', value: order.studentName },
          { key: 'nature', label: '学员性质', value: order.studentNatureLabelSnapshot || label(DICT_TYPE.ORDER_STUDENT_NATURE, order.studentNature) },
          { key: 'mobile', label: '手机号', value: order.studentMobile },
          { key: 'wechat', label: '微信号', value: order.studentWechatId },
          { key: 'region', label: '所在地区', value: [order.provinceName, order.cityName].filter(Boolean).join(' / ') }
        ]}/>
      </section>
      <section className="sales-order-info-block sales-order-info-block-wide">
        <div className="sales-order-section-heading"><Typography.Text strong>成交与付款</Typography.Text><Typography.Text className="sales-order-total-amount">¥{Number(order.totalAmount).toFixed(2)}</Typography.Text></div>
        <DetailFieldGrid items={[
          { key: 'paidAt', label: '客户付款时间', value: formatTimestamp(order.customerPaidAt) },
          { key: 'feeMode', label: '缴费方式', value: order.feeModeLabelSnapshot || label(DICT_TYPE.ORDER_FEE_MODE, order.feeMode) },
          { key: 'paymentMethod', label: '支付方式', value: order.paymentMethodLabelSnapshot || label(DICT_TYPE.ORDER_PAYMENT_METHOD, order.paymentMethod) },
          { key: 'examTime', label: '商定考试时间', value: order.agreedExamTime },
          { key: 'classType', label: '开通班种', value: order.classType },
          { key: 'servicePeriod', label: '服务周期', value: order.servicePeriodLabelSnapshot || label(DICT_TYPE.ORDER_SERVICE_PERIOD, order.servicePeriod) },
          { key: 'studentSource', label: '学生来源', value: order.studentSourceLabelSnapshot || label(DICT_TYPE.ORDER_STUDENT_SOURCE, order.studentSource) }
        ]}/>
      </section>
      {order.repurchaseReason && <section className="sales-order-info-block sales-order-info-block-wide">
        <div className="sales-order-section-heading"><Typography.Text strong>复购说明</Typography.Text></div>
        <Typography.Paragraph className="sales-order-long-text">{order.repurchaseReason}</Typography.Paragraph>
      </section>}
        </section>
        <section className="sales-order-content-section">
          <div className="sales-order-section-heading"><Typography.Text strong>成交课程</Typography.Text></div>
          <Table rowKey="id" size="small" pagination={false} dataSource={order.items} columns={[
            { title: '课程', render: (_, item) => [item.categoryPath?.join(' / '), item.productName, item.skuName].filter(Boolean).join(' / ') || '-' },
            { title: '实际成交金额', width: 150, render: (_, item) => `¥${Number(item.actualAmount).toFixed(2)}` }
          ]}/>
        </section>
        <section className="sales-order-bottom-grid">
          <section className="sales-order-content-section">
            <div className="sales-order-section-heading"><Typography.Text strong>备注与服务</Typography.Text></div>
            <DetailFieldGrid columns={1} items={[
              { key: 'remark', label: '订单备注', value: order.remark },
              { key: 'requirements', label: '学生特殊要求', value: order.studentSpecialRequirements },
              { key: 'delivery', label: '教材邮递联系', value: order.materialDeliveryContact }
            ]}/>
          </section>
          <section className="sales-order-content-section">
            <div className="sales-order-section-heading"><Typography.Text strong>缴费凭证</Typography.Text></div>
            {order.paymentVouchers.length ? <Image.PreviewGroup><div className="sales-order-vouchers">{order.paymentVouchers.map(file => file.contentType === 'application/pdf'
              ? <Button key={file.infraFileId} href={file.fileUrl} target="_blank">{file.originalName}</Button>
              : <Image key={file.infraFileId} width={88} height={88} src={file.fileUrl} alt={file.originalName}/>)}</div></Image.PreviewGroup>
              : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无缴费凭证"/>}
          </section>
        </section>
      </main>
      <aside className="sales-order-approval-sidebar" aria-label="审批状态">
        <Alert className="sales-order-approval-status" showIcon {...statusAlert}/>
        {hasApprovalActions && <Space wrap className="sales-order-approval-actions">
          {mode === 'mine' && (order.status === 'revision_required' || order.status === 'terminated') && order.canRevise && onRevise && <Button type="primary" icon={<EditOutlined/>} onClick={onRevise}>修改并重新提交</Button>}
          {mode === 'mine' && order.canTerminate && onTerminate && <Button danger icon={<StopOutlined/>} onClick={onTerminate}>终止审批</Button>}
          {mode === 'approval-todo' && canReview && <><Button type="primary" icon={<CheckOutlined/>} onClick={onApprove}>通过</Button><Button danger icon={<CloseOutlined/>} onClick={onReject}>驳回</Button>{onRequestSupervisor && order.canRequestSupervisorConfirmation && <Button icon={<UserSwitchOutlined/>} onClick={onRequestSupervisor}>申请主管确认</Button>}</>}
        </Space>}
        <SalesOrderApprovalRail nodes={approvalNodes}/>
      </aside>
    </div>
  </div>
}
