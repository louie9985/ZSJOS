import { Alert, Button, Card, Descriptions, Empty, Image, Space, Table, Tag, Typography } from 'antd'
import { CheckOutlined, CloseOutlined, EditOutlined, StopOutlined, UserSwitchOutlined } from '@ant-design/icons'
import type { SalesOrder, SalesOrderListItem } from '../services/api'
import { formatTimestamp } from '../services/time'
import { canReviewSalesOrderTask } from '../services/salesOrder'

export const SALES_ORDER_STATUS_LABELS: Record<SalesOrder['status'], string> = {
  pending_approval: '待审核', revision_required: '已驳回待修改', effective: '已通过', terminated: '已终止'
}
export const SALES_ORDER_STATUS_COLORS: Record<SalesOrder['status'], string> = {
  pending_approval: 'gold', revision_required: 'red', effective: 'green', terminated: 'default'
}
export const SALES_ORDER_TASK_LABELS: Record<string, string> = {
  registrationReview: '报名履约中心', financeReview: '财务结算中心'
}
const TASK_STATUS_LABELS: Record<number, string> = { 1: '审批中', 2: '审批通过', 3: '审批不通过', 4: '已取消', 5: '已退回', 7: '审批通过中' }
const APPROVAL_NODE_STATUS_LABELS: Record<string, string> = { pending: '审批中', approved: '已通过', rejected: '已驳回', cancelled: '已取消' }
const APPROVAL_NODE_STATUS_COLORS: Record<string, string> = { pending: 'gold', approved: 'green', rejected: 'red', cancelled: 'default' }

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
  const task = approvalContext || order
  const canReview = approvalContext ? canReviewSalesOrderTask(order, approvalContext) : false
  const supervisorConfirmation = task.taskDefinitionKey === 'registrationReview'
    ? order.registrationSupervisorConfirmation : task.taskDefinitionKey === 'financeReview' ? order.financeSupervisorConfirmation : undefined
  const supervisorPending = supervisorConfirmation?.status === 'pending'
  const approvalRows = [
    { key: 'registration', center: '报名履约中心', approval: order.registrationApproval },
    { key: 'finance', center: '财务中心', approval: order.financeApproval }
  ]
  return <div className="sales-order-detail">
    <div className="sales-order-detail-hero">
      <div className="sales-order-detail-heading">
        <Space wrap><Typography.Title level={4}>{order.studentName}</Typography.Title><Tag color={SALES_ORDER_STATUS_COLORS[order.status]}>{SALES_ORDER_STATUS_LABELS[order.status]}</Tag></Space>
        <Typography.Text type="secondary">{order.orderNo} · 第 {order.approvalRoundNo || 1} 轮</Typography.Text>
      </div>
      <Space wrap className="sales-order-detail-actions">
        {mode === 'mine' && (order.status === 'revision_required' || order.status === 'terminated') && order.canRevise && onRevise && <Button type="primary" icon={<EditOutlined/>} onClick={onRevise}>修改并重新提交</Button>}
        {mode === 'mine' && order.canTerminate && onTerminate && <Button danger icon={<StopOutlined/>} onClick={onTerminate}>终止审批</Button>}
        {mode === 'approval-todo' && canReview && !supervisorPending && <><Button type="primary" icon={<CheckOutlined/>} onClick={onApprove}>通过</Button><Button danger icon={<CloseOutlined/>} onClick={onReject}>驳回</Button>{onRequestSupervisor && order.canRequestSupervisorConfirmation && <Button icon={<UserSwitchOutlined/>} onClick={onRequestSupervisor}>申请主管确认</Button>}</>}
      </Space>
    </div>
    {supervisorPending && <Alert type="info" showIcon message={`${supervisorConfirmation.requesterUserName || '审批人'}已申请主管审批`} description={supervisorConfirmation.requestReason}/>}
    {order.status === 'revision_required' && <Alert type="error" showIcon message="订单已驳回，等待补正" description={order.decisionReason || '审批人未填写可展示的驳回原因'}/>}
    {order.status === 'terminated' && <Alert type="warning" showIcon message="订单审批已终止"
      description={order.terminationReason || '未记录终止原因'}/>}
    <div className="sales-order-card-grid">
      <Card size="small" title="双中心审批状态" className="sales-order-card sales-order-card-wide">
        <Table rowKey="key" size="small" pagination={false} scroll={{ x: 640 }} dataSource={approvalRows} columns={[
          { title: '审核中心', dataIndex: 'center' },
          { title: '审核结果', render: (_, row) => <Tag color={APPROVAL_NODE_STATUS_COLORS[row.approval?.status || '']}>{APPROVAL_NODE_STATUS_LABELS[row.approval?.status || ''] || '-'}</Tag> },
          { title: '审核人', render: (_, row) => row.approval?.reviewerUserName || '-' },
          { title: '审核时间', render: (_, row) => formatTimestamp(row.approval?.endTime) }
        ]}/>
      </Card>
      <Card size="small" title="订单与审批" className="sales-order-card">
        <Descriptions column={{ xs: 1, sm: 2 }} layout="vertical" size="small" colon={false}>
          <Descriptions.Item label="订单号">{order.orderNo}</Descriptions.Item><Descriptions.Item label="订单类型">{order.orderType === 'repurchase' ? '复购' : '首购'}</Descriptions.Item>
          <Descriptions.Item label="审批轮次">第 {order.approvalRoundNo || 1} 轮</Descriptions.Item>
          <Descriptions.Item label="当前状态">{SALES_ORDER_STATUS_LABELS[order.status]}</Descriptions.Item><Descriptions.Item label="当前中心">{SALES_ORDER_TASK_LABELS[task.taskDefinitionKey || ''] || '-'}</Descriptions.Item>
          <Descriptions.Item label="提交时间">{formatTimestamp(order.submittedAt)}</Descriptions.Item><Descriptions.Item label="通过时间">{formatTimestamp(order.effectiveAt)}</Descriptions.Item>
          {mode === 'approval-done' && <><Descriptions.Item label="处理结果">{task.taskStatus == null ? '-' : TASK_STATUS_LABELS[task.taskStatus] || `状态 ${task.taskStatus}`}</Descriptions.Item><Descriptions.Item label="处理时间">{formatTimestamp(task.taskEndTime)}</Descriptions.Item><Descriptions.Item label="审批意见" span={2}>{task.taskReason || '-'}</Descriptions.Item></>}
        </Descriptions>
      </Card>
      {order.repurchaseReason && <Card size="small" title="复购说明" className="sales-order-card"><Typography.Text>{order.repurchaseReason}</Typography.Text></Card>}
      <Card size="small" title="学员资料" className="sales-order-card">
        <Descriptions column={{ xs: 1, sm: 2 }} layout="vertical" size="small" colon={false}>
          <Descriptions.Item label="购买方">{order.buyerName || '-'}</Descriptions.Item><Descriptions.Item label="学员姓名">{order.studentName}</Descriptions.Item>
          <Descriptions.Item label="学员性质">{order.studentNature || '-'}</Descriptions.Item><Descriptions.Item label="手机号">{order.studentMobile || '-'}</Descriptions.Item>
          <Descriptions.Item label="微信号">{order.studentWechatId || '-'}</Descriptions.Item><Descriptions.Item label="所在地区">{[order.provinceName, order.cityName].filter(Boolean).join(' / ') || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card size="small" title="成交及付款" className="sales-order-card">
        <Descriptions column={{ xs: 1, sm: 2 }} layout="vertical" size="small" colon={false}>
          <Descriptions.Item label="订单总金额">¥{Number(order.totalAmount).toFixed(2)}</Descriptions.Item><Descriptions.Item label="客户付款时间">{formatTimestamp(order.customerPaidAt)}</Descriptions.Item>
          <Descriptions.Item label="缴费方式">{order.feeMode || '-'}</Descriptions.Item><Descriptions.Item label="支付方式">{order.paymentMethod || '-'}</Descriptions.Item>
          <Descriptions.Item label="商定考试时间">{order.agreedExamTime || '-'}</Descriptions.Item><Descriptions.Item label="开通班种">{order.classType || '-'}</Descriptions.Item>
          <Descriptions.Item label="服务周期">{order.servicePeriod || '-'}</Descriptions.Item><Descriptions.Item label="学生来源">{order.studentSource || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card size="small" title="成交课程" className="sales-order-card sales-order-card-wide">
        <Table rowKey="id" size="small" pagination={false} dataSource={order.items} columns={[
          { title: '课程', render: (_, item) => [item.categoryPath?.join(' / '), item.productName, item.skuName].filter(Boolean).join(' / ') || '-' },
          { title: '实际成交金额', width: 150, render: (_, item) => `¥${Number(item.actualAmount).toFixed(2)}` }
        ]}/>
      </Card>
      <Card size="small" title="备注与服务信息" className="sales-order-card">
        <Descriptions column={1} layout="vertical" size="small" colon={false}>
          <Descriptions.Item label="订单备注">{order.remark || '-'}</Descriptions.Item><Descriptions.Item label="学生特殊要求">{order.studentSpecialRequirements || '-'}</Descriptions.Item><Descriptions.Item label="教材邮递联系">{order.materialDeliveryContact || '-'}</Descriptions.Item>
        </Descriptions>
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
