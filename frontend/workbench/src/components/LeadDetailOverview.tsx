import { useEffect, useMemo, useState } from 'react'
import { Alert, Empty, Image, Tag, Typography } from 'antd'
import {
  CopyOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  PauseCircleOutlined
} from '@ant-design/icons'
import { api, type LeadFollowUp, type ManagedLead, type ManagedLeadProduct, type MyStudent, type StudentContactContext, type StudentContactRecord } from '../services/api'
import { formatTimestamp } from '../services/time'
import {
  LEAD_DISPATCH_MODE_LABELS,
  LEAD_QUALIFICATION_STATUS_LABELS,
  LEAD_FOLLOW_UP_STATUS_LABELS
} from '../constants'
import { protocolDisplayLabel } from '../services/leadManagement'
import LeadFollowUpCharts from './LeadFollowUpCharts'

/* ========== 确定性彩色首字头像 ========== */

const AVATAR_PALETTE = [
  { bg: '#e6f4ff', color: '#1677ff' },
  { bg: '#f6ffed', color: '#52c41a' },
  { bg: '#fff7e6', color: '#fa8c16' },
  { bg: '#fff1f0', color: '#f5222d' },
  { bg: '#f9f0ff', color: '#722ed1' },
  { bg: '#e6fffb', color: '#13c2c2' },
  { bg: '#fff0f6', color: '#eb2f96' },
  { bg: '#fcffe6', color: '#a0d911' },
] as const

function hashName(name: string): number {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = ((hash << 5) - hash + name.charCodeAt(i)) | 0
  }
  return Math.abs(hash)
}

function NameAvatar({ name, size = 44 }: { name: string; size?: number }) {
  const palette = AVATAR_PALETTE[hashName(name) % AVATAR_PALETTE.length]
  return (
    <div
      className="lead-name-avatar"
      style={{ width: size, height: size, background: palette.bg, color: palette.color }}
    >
      {name.slice(0, 1)}
    </div>
  )
}

/* ========== 行内复制按钮 ========== */

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }
  return (
    <button type="button" className={`lead-field-copy-btn ${copied ? 'copied' : ''}`} onClick={copy} title="复制">
      <CopyOutlined />
    </button>
  )
}

/* ========== 时效进度 ========== */

function useNowTick(intervalMs = 60_000) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return now
}

function DeadlineIndicator({ label, deadline, completedAt }: { label: string; deadline?: number; completedAt?: number }) {
  const now = useNowTick()
  if (!deadline) return null

  if (completedAt) {
    return (
      <div className="lead-deadline-item completed">
        <div className="lead-deadline-title"><ClockCircleOutlined />{label}</div>
        <div className="lead-deadline-row">
          <span className="lead-deadline-field-label">状态</span>
          <span className="lead-deadline-time">已完成</span>
        </div>
        <div className="lead-deadline-row">
          <span className="lead-deadline-field-label">完成时间</span>
          <span className="lead-deadline-date">{formatTimestamp(completedAt)}</span>
        </div>
      </div>
    )
  }

  const remaining = deadline - now
  const isOverdue = remaining < 0
  const isUrgent = remaining > 0 && remaining < 4 * 60 * 60 * 1000 // < 4 hours

  let statusClass = 'normal'
  if (isOverdue) statusClass = 'overdue'
  else if (isUrgent) statusClass = 'urgent'

  const formatRemaining = () => {
    if (isOverdue) return '已超时'
    const hours = Math.floor(remaining / (1000 * 60 * 60))
    const minutes = Math.floor((remaining % (1000 * 60 * 60)) / (1000 * 60))
    if (hours >= 24) {
      const days = Math.floor(hours / 24)
      return `剩余 ${days} 天 ${hours % 24} 小时`
    }
    return `剩余 ${hours} 小时 ${minutes} 分钟`
  }

  return (
    <div className={`lead-deadline-item ${statusClass}`}>
      <div className="lead-deadline-title"><ClockCircleOutlined />{label}</div>
      <div className="lead-deadline-row">
        <span className="lead-deadline-field-label">剩余时间</span>
        <span className="lead-deadline-time">{formatRemaining()}</span>
      </div>
      <div className="lead-deadline-row">
        <span className="lead-deadline-field-label">截止时间</span>
        <span className="lead-deadline-date">{formatTimestamp(deadline)}</span>
      </div>
    </div>
  )
}

/* ========== 意向产品卡片 ========== */

function ProductCard({ product }: { product: ManagedLeadProduct }) {
  return (
    <div className={`lead-product-card ${product.primary ? 'primary' : ''}`}>
      <div className="lead-product-card-header">
        {product.primary && <Tag color="green" bordered={false}>主意向</Tag>}
        <span className="lead-product-name">{product.spuName || '未明确课程'}</span>
      </div>
      {product.skuName && <span className="lead-product-sku">{product.skuName}</span>}
      {product.price != null && (
        <span className="lead-product-price">¥{Number(product.price).toFixed(2)}</span>
      )}
    </div>
  )
}

/* ========== 最近跟进 ========== */

function LatestFollowUp({ leadId }: { leadId: number }) {
  const [record, setRecord] = useState<LeadFollowUp | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    api.leadFollowUpPage(leadId, { pageNo: 1, pageSize: 1 })
      .then(page => setRecord(page.list[0] || null))
      .catch(() => setRecord(null))
      .finally(() => setLoading(false))
  }, [leadId])

  if (loading) return <div className="lead-section-block"><Typography.Text type="secondary">加载中...</Typography.Text></div>
  if (!record) return (
    <div className="lead-latest-followup">
      <div className="lead-section-header">
        <Typography.Text strong>最近跟进</Typography.Text>
      </div>
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无跟进记录" />
    </div>
  )

  return (
    <div className="lead-latest-followup">
      <div className="lead-section-header">
        <Typography.Text strong>最近跟进</Typography.Text>
        <Typography.Text type="secondary">{formatTimestamp(record.occurredAt)}</Typography.Text>
      </div>
      <div className="lead-latest-followup-body">
        <div className="lead-latest-followup-tags">
          <Tag>{record.methodLabel}</Tag>
          <Tag color="blue">{record.resultLabel}</Tag>
          {record.firstInAssignment && <Tag color="green">首次跟进</Tag>}
        </div>
        {record.remark && (
          <Typography.Paragraph
            className="lead-latest-followup-remark"
            ellipsis={{ rows: 2, expandable: 'collapsible', symbol: (expanded: boolean) => expanded ? '收起' : '展开' }}
          >
            {record.remark}
          </Typography.Paragraph>
        )}
        {record.nextFollowUpAt && (
          <Typography.Text type="secondary" className="lead-latest-followup-next">
            下次跟进：{formatTimestamp(record.nextFollowUpAt)}
          </Typography.Text>
        )}
      </div>
    </div>
  )
}

/* ========== 流转时间线 ========== */

export function buildLeadFlowEvents(lead: ManagedLead): Array<{ time?: number; label: string; detail?: string }> {
    const list: Array<{ time?: number; label: string; detail?: string }> = []
    if (lead.submittedAt) {
      list.push({
        time: lead.submittedAt,
        label: '提交客资',
        detail: lead.sourceUserName ? `提交人：${lead.sourceUserName}` : undefined
      })
    }
    if (lead.dispatchMode) {
      list.push({
        time: lead.submittedAt,
        label: protocolDisplayLabel(LEAD_DISPATCH_MODE_LABELS, lead.dispatchMode, '分配'),
        detail: lead.ownerUserName ? `归属：${lead.ownerUserName}` : undefined
      })
    }
    if (lead.currentAssignmentFirstFollowUpAt) {
      list.push({ time: lead.currentAssignmentFirstFollowUpAt, label: '首次跟进' })
    }
    if (lead.qualifiedAt) {
      list.push({
        time: lead.qualifiedAt,
        label: lead.qualificationStatus === 'valid' ? '判定有效' : '判定无效',
        detail: lead.qualifiedByUserName ? `操作人：${lead.qualifiedByUserName}` : undefined
      })
    }
    if (lead.suspendedAt) {
      list.push({ time: lead.suspendedAt, label: '挂起' })
    }
    if (lead.salesOrderSubmittedAt) {
      list.push({ time: lead.salesOrderSubmittedAt, label: '录入成交' })
    }
    if (lead.convertedAt) {
      list.push({ time: lead.convertedAt, label: '成交转化' })
    }
    if (lead.closedAt) {
      list.push({ time: lead.closedAt, label: '关闭', detail: lead.closeReason || undefined })
    }
    return list.sort((a, b) => (b.time || 0) - (a.time || 0))
}

function LatestStudentContact({ records }: { records: StudentContactRecord[] }) {
  const record = records[0]
  if (!record) return (
    <div className="lead-latest-followup">
      <div className="lead-section-header"><Typography.Text strong>最近联系</Typography.Text></div>
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无联系记录" />
    </div>
  )
  return (
    <div className="lead-latest-followup">
      <div className="lead-section-header">
        <Typography.Text strong>最近联系</Typography.Text>
        <Typography.Text type="secondary">{formatTimestamp(new Date(record.submittedAt).getTime())}</Typography.Text>
      </div>
      <div className="lead-latest-followup-body">
        <div className="lead-latest-followup-tags">
          <Tag>{record.successful ? '已联系' : '未联系成功'}</Tag>
          {record.operatorUserName && <Tag color="blue">{record.operatorUserName}</Tag>}
        </div>
        <Typography.Paragraph className="lead-latest-followup-remark" ellipsis={{ rows: 2, expandable: 'collapsible' }}>
          {record.remark}
        </Typography.Paragraph>
        {record.nextContactAt && <Typography.Text type="secondary" className="lead-latest-followup-next">下次联系：{formatTimestamp(new Date(record.nextContactAt).getTime())}</Typography.Text>}
      </div>
    </div>
  )
}

export function leadSourceDispatchTag(lead: Pick<ManagedLead, 'sourceType' | 'dispatchMode'>):
  { label: string; color: 'blue' | 'orange' } | undefined {
  if (lead.sourceType !== 'internal_new_media') return
  if (lead.dispatchMode === 'auto') return { label: '自动分配', color: 'blue' }
  if (lead.dispatchMode === 'specified') return { label: '指定派单', color: 'orange' }
}

function FlowTimeline({ lead }: { lead: ManagedLead }) {
  const events = useMemo(() => buildLeadFlowEvents(lead), [lead])

  if (events.length === 0) return null

  return (
    <div className="lead-flow-timeline">
      <div className="lead-section-header">
        <Typography.Text strong>客资流转</Typography.Text>
      </div>
      <div className="lead-flow-timeline-track">
        {events.map((event, index) => (
          <div key={index} className="lead-flow-node">
            <div className="lead-flow-dot" />
            {index < events.length - 1 && <div className="lead-flow-line" />}
            <div className="lead-flow-node-content">
              <span className="lead-flow-node-label">{event.label}</span>
              {event.detail && <span className="lead-flow-node-detail">{event.detail}</span>}
              {event.time && <span className="lead-flow-node-time">{formatTimestamp(event.time)}</span>}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

/* ========== 备注与附件 ========== */

export function RemarksAndAttachments({ lead }: { lead: ManagedLead }) {
  const remarks = lead.remarkHistory ?? (lead.remark ? [{ id: 'legacy-current', kind: 'legacy', content: lead.remark, occurredAt: undefined, operatorName: undefined }] : [])
  const hasRemark = Boolean(remarks.length || lead.invalidDescription || lead.remarkHistoryIncomplete)
  const hasAttachments = Boolean(lead.attachments?.length)
  if (!hasRemark && !hasAttachments) return (
    <div className="lead-remarks-attachments">
      <div className="lead-section-header">
        <Typography.Text strong>备注与附件</Typography.Text>
      </div>
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无备注或附件" />
    </div>
  )

  return (
    <div className="lead-remarks-attachments">
      <div className="lead-section-header">
        <Typography.Text strong>备注与附件</Typography.Text>
      </div>
      {lead.remarkHistoryIncomplete && <Alert type="warning" showIcon title="部分历史备注无法还原" />}
      {remarks.map(remark => (
        <div className="lead-remark-block" key={remark.id}>
          <Typography.Text type="secondary" className="lead-remark-label">
            {remark.kind === 'submission' ? '提交备注' : remark.kind === 'supplement' ? '补充备注' : '历史备注'}
            {remark.operatorName && ` · ${remark.operatorName}`}
            {remark.occurredAt && ` · ${formatTimestamp(remark.occurredAt)}`}
          </Typography.Text>
          <Typography.Paragraph
            className="lead-remark-text"
            style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}
            ellipsis={{ rows: 3, expandable: 'collapsible', symbol: (expanded: boolean) => expanded ? '收起' : '展开' }}
          >
            {remark.content}
          </Typography.Paragraph>
        </div>
      ))}
      {lead.invalidDescription && (
        <div className="lead-remark-block">
          <Typography.Text type="secondary" className="lead-remark-label">无效原因</Typography.Text>
          <div>
            {lead.invalidReasonLabelSnapshot && <Tag color="red">{lead.invalidReasonLabelSnapshot}</Tag>}
            <Typography.Paragraph
              className="lead-remark-text"
              ellipsis={{ rows: 3, expandable: 'collapsible', symbol: (expanded: boolean) => expanded ? '收起' : '展开' }}
            >
              {lead.invalidDescription}
            </Typography.Paragraph>
          </div>
        </div>
      )}
      {hasAttachments && (
        <div className="lead-attachment-thumbs">
          <Image.PreviewGroup>
            {lead.attachments!.map(file => (
              <Image
                key={file.id}
                className="lead-attachment-thumb"
                src={file.fileUrl}
                alt={file.originalName}
                width={72}
                height={72}
              />
            ))}
          </Image.PreviewGroup>
        </div>
      )}
    </div>
  )
}

/* ========== 右侧警告提示 ========== */

function AsideAlerts({ lead }: { lead: ManagedLead }) {
  const alerts: Array<{ tone: 'warning' | 'danger' | 'info'; icon: React.ReactNode; title: string; detail: string }> = []
  if (lead.operationalStatus === 'suspended') {
    alerts.push({
      tone: 'warning',
      icon: <PauseCircleOutlined />,
      title: '客资已挂起',
      detail: '当前只能查看，需由销售主管恢复、转派、回收或释放。'
    })
  }
  if (lead.assignmentStatus === 'recycle_pending') {
    alerts.push({
      tone: 'warning',
      icon: <PauseCircleOutlined />,
      title: '客资待回收处理',
      detail: '当前等待销售主管恢复、转派、回收或释放。'
    })
  }
  if (lead.status === 'invalid') {
    alerts.push({
      tone: 'danger',
      icon: <CloseCircleOutlined />,
      title: '客资已判无效',
      detail: [lead.invalidReasonLabelSnapshot || (lead.invalidReason ? '标签未配置' : undefined), lead.invalidDescription].filter(Boolean).join('：')
    })
  }
  if (lead.handlingStage === 'first_follow_pending' && lead.currentAssignmentFirstFollowUpDeadlineAt) {
    alerts.push({
      tone: 'info',
      icon: <ExclamationCircleOutlined />,
      title: '待完成首次跟进',
      detail: `截止 ${formatTimestamp(lead.currentAssignmentFirstFollowUpDeadlineAt)}`
    })
  }
  if (lead.handlingStage === 'qualification_pending' && lead.qualificationDeadlineAt) {
    alerts.push({
      tone: 'info',
      icon: <ExclamationCircleOutlined />,
      title: '待完成有效性判定',
      detail: `截止 ${formatTimestamp(lead.qualificationDeadlineAt)}`
    })
  }
  if (alerts.length === 0) return null
  return (
    <div className="lead-aside-alerts">
      {alerts.map((alert, index) => (
        <div key={index} className={`lead-alert-strip tone-${alert.tone}`}>
          <span className="lead-alert-left">
            <span className="lead-alert-icon">{alert.icon}</span>
            <span className="lead-alert-title">{alert.title}</span>
          </span>
          {alert.detail && <span className="lead-alert-detail">{alert.detail}</span>}
        </div>
      ))}
    </div>
  )
}

/* ========== 状态卡：Pipeline 进度 ========== */

const PIPELINE_STEPS = [
  { key: 'submitted', label: '提交' },
  { key: 'following', label: '跟进' },
  { key: 'qualified', label: '判定' },
  { key: 'deal_entered', label: '录入成交' },
  { key: 'converted', label: '成交转化' },
] as const

export function pipelineStepIndex(lead: ManagedLead): number {
  // Current stage follows the server's lifecycle projection; timestamps remain historical events.
  if (lead.followUpStatus === 'won' || lead.status === 'won') return 4
  if (
    lead.followUpStatus === 'deal_pending_approval' ||
    lead.activeSalesOrderStatus === 'pending_approval' ||
    lead.activeSalesOrderStatus === 'revision_required'
  ) return 3
  if (lead.qualifiedAt || lead.qualificationStatus === 'valid' || lead.qualificationStatus === 'invalid') return 2
  if (lead.currentAssignmentFirstFollowUpAt || lead.followUpStatus) return 1
  return 0
}

function LeadStatusPipeline({ lead }: { lead: ManagedLead }) {
  const currentIndex = pipelineStepIndex(lead)
  return (
    <div className="lead-status-pipeline">
      {PIPELINE_STEPS.map((step, index) => {
        let state: 'done' | 'current' | 'future'
        if (index < currentIndex) state = 'done'
        else if (index === currentIndex) state = 'current'
        else state = 'future'
        return (
          <div key={step.key} className={`lead-status-node ${state}`}>
            <div className="lead-status-dot" />
            {index < PIPELINE_STEPS.length - 1 && <div className="lead-status-connector" />}
            <span className="lead-status-step-label">{step.label}</span>
          </div>
        )
      })}
    </div>
  )
}

const STUDENT_STEPS = [
  { key: 'student_first_contact', label: '首联' },
  { key: 'student_study_plan', label: '制定学习计划' },
  { key: 'student_contact', label: '督学' },
  { key: 'student_exam', label: '考试' },
] as const

function StudentTaskPipeline({ context, records }: { context: StudentContactContext; records: StudentContactRecord[] }) {
  const completedTypes = new Set(records.map(record => record.contactType))
  const deliveryStages = context.deliveryStages || []
  if (deliveryStages.length) return <div className="lead-status-pipeline lead-status-pipeline-delivery">
    {deliveryStages.map((step, index) => <div key={step.code} className={`lead-status-node ${step.status === 'pending' ? 'future' : step.status}`}>
      <div className="lead-status-dot" />
      {index < deliveryStages.length - 1 && <div className="lead-status-connector" />}
      <span className="lead-status-step-label">{step.label}</span>
    </div>)}
  </div>
  const currentIndex = STUDENT_STEPS.findIndex(step => step.key === context.currentTask?.type)
  return <div className="lead-status-pipeline">
    {STUDENT_STEPS.map((step, index) => {
      const state = completedTypes.has(step.key) || (currentIndex >= 0 && index < currentIndex) ? 'done' : index === currentIndex ? 'current' : 'future'
      return <div key={step.key} className={`lead-status-node ${state}`}>
        <div className="lead-status-dot" />
        {index < STUDENT_STEPS.length - 1 && <div className="lead-status-connector" />}
        <span className="lead-status-step-label">{step.label}</span>
      </div>
    })}
  </div>
}

function StudentTaskLabels({ context }: { context: StudentContactContext }) {
  const task = context.currentTask
  const taskLabel = STUDENT_STEPS.find(step => step.key === task?.type)?.label || '暂无待办'
  const items: Array<{ label: string; value: string; color: StatusColor }> = [
    { label: '交付阶段', value: context.deliveryStageLabel || '首次联系', color: 'blue' },
    { label: '当前任务', value: taskLabel, color: task?.overdue ? 'red' : task ? 'blue' : 'gray' },
    { label: '任务状态', value: task?.overdue ? '已逾期' : task ? '待处理' : '暂无', color: task?.overdue ? 'red' : task ? 'orange' : 'gray' },
    { label: '服务接收', value: context.acceptanceStatus === 'accepted' ? '已接收' : '待接收', color: context.acceptanceStatus === 'accepted' ? 'green' : 'orange' },
  ]
  return <div className="lead-status-labels">{items.map(item => <div key={item.label} className={`lead-status-label-item color-${item.color}`}><span className="lead-status-label-name">{item.label}</span><span className="lead-status-label-value">{item.value}</span></div>)}</div>
}

/* ========== 状态卡：色条标签墙 ========== */

type StatusColor = 'green' | 'blue' | 'orange' | 'red' | 'gray'

function qualificationColor(status?: string): StatusColor {
  if (status === 'valid') return 'green'
  if (status === 'invalid') return 'red'
  return 'orange'
}

function followUpColor(status?: string): StatusColor {
  if (!status) return 'gray'
  if (status === 'converted') return 'green'
  return 'blue'
}

function operationalColor(status?: string): StatusColor {
  if (status === 'suspended') return 'orange'
  return 'green'
}

function LeadStatusLabels({ lead }: { lead: ManagedLead }) {
  const items: { label: string; value: string; color: StatusColor }[] = [
    {
      label: '有效性判定',
      value: protocolDisplayLabel(LEAD_QUALIFICATION_STATUS_LABELS, lead.qualificationStatus, '未知'),
      color: qualificationColor(lead.qualificationStatus),
    },
    {
      label: '跟进状态',
      value: lead.followUpStatus
        ? protocolDisplayLabel(LEAD_FOLLOW_UP_STATUS_LABELS, lead.followUpStatus, '未知')
        : '暂无',
      color: followUpColor(lead.followUpStatus),
    },
    {
      label: '运营状态',
      value: lead.operationalStatus === 'suspended' ? '已挂起' : '正常',
      color: operationalColor(lead.operationalStatus),
    },
  ]

  return (
    <div className="lead-status-labels">
      {items.map(item => (
        <div key={item.label} className={`lead-status-label-item color-${item.color}`}>
          <span className="lead-status-label-name">{item.label}</span>
          <span className="lead-status-label-value">{item.value}</span>
        </div>
      ))}
    </div>
  )
}

/* ========== 主导出：概览 ========== */

export type StudentOverviewContext = { service: MyStudent['services'][number]; contactContext: StudentContactContext; contactRecords: StudentContactRecord[] }

export type LeadOverviewSlots = {
  latestActivity?: React.ReactNode
  timeline?: React.ReactNode
  taskStatus?: React.ReactNode
  mainBeforeColumns?: React.ReactNode
  sidebarBeforeStatus?: React.ReactNode
}

export function studentProfileIdentity(lead?: ManagedLead, student?: MyStudent) {
  return {
    name: student?.name || lead?.submittedName || '未填写姓名',
    mobile: student?.mobile || lead?.submittedMobile,
    wechatId: student?.wechatId || lead?.submittedWechatId,
    numberLabel: lead ? '客资编号' : '学员编号',
    number: lead?.leadNo || student?.personNo || '未记录'
  }
}

export default function LeadDetailOverview({ lead, student, categoryLabel, channelLabel, showFollowUp, toolbar, studentContext, studentService, hideProviderOwner, slots }: {
  lead?: ManagedLead
  student?: MyStudent
  categoryLabel: (value?: string) => string
  channelLabel: (value?: string) => string
  showFollowUp: boolean
  /** 操作工具条（使用 OverflowToolbar 渲染） */
  toolbar?: React.ReactNode
  studentContext?: StudentOverviewContext
  studentService?: MyStudent['services'][number]
  hideProviderOwner?: boolean
  slots?: LeadOverviewSlots
}) {
  const sourceDispatchTag = lead ? leadSourceDispatchTag(lead) : undefined
  const service = studentContext?.service || studentService
  const identity = studentProfileIdentity(lead, student)
  return (
    <div className="lead-detail-overview-v2">
      <div className="lead-overview-grid">
        {/* 左侧主区 9 列 */}
        <div className="lead-overview-main">
          {slots?.mainBeforeColumns}
          {/* 二级布局 8:4 */}
          <div className="lead-overview-main-inner">
            {/* 8 列：客户档案 + 下方小卡片 */}
            <div className="lead-overview-col-primary">
              {/* 满宽：客户档案 */}
              <section className="lead-card">
                <div className="lead-card-header">
                  <Typography.Text strong>客户档案</Typography.Text>
                </div>
                <div className="lead-profile-fields">
                  <div className="lead-profile-row">
                    <span className="lead-field-label">姓名</span>
                    <span className="lead-field-value">{identity.name}</span>
                  </div>
                  <div className="lead-profile-row">
                    <span className="lead-field-label">{identity.numberLabel}</span>
                    <span className="lead-field-value">{identity.number}</span>
                  </div>
                  <div className="lead-profile-row">
                    <span className="lead-field-label">手机号</span>
                    {identity.mobile
                      ? <span className="lead-field-copyable"><span className="lead-field-value">{identity.mobile}</span><CopyButton value={identity.mobile} /></span>
                      : <span className="lead-field-value lead-field-empty">未填写</span>
                    }
                  </div>
                  <div className="lead-profile-row">
                    <span className="lead-field-label">微信号</span>
                    {identity.wechatId
                      ? <span className="lead-field-copyable"><span className="lead-field-value">{identity.wechatId}</span><CopyButton value={identity.wechatId} /></span>
                      : <span className="lead-field-value lead-field-empty">未填写</span>
                    }
                  </div>
                </div>
                {studentContext && !lead && <div className="lead-profile-meta">
                  <div className="lead-profile-row"><span className="lead-field-label">客资</span><span className="lead-field-value lead-field-empty">未关联客资</span></div>
                </div>}
                {lead && <div className="lead-profile-meta">
                  <div className="lead-profile-row">
                    <span className="lead-field-label">来源</span>
                    <span className="lead-field-value lead-source-value">
                      <span className="lead-source-label">{lead.sourceLabel || '来源未配置'}</span>
                      {sourceDispatchTag && <Tag color={sourceDispatchTag.color}>{sourceDispatchTag.label}</Tag>}
                    </span>
                  </div>
                  <div className="lead-profile-row">
                    <span className="lead-field-label">提交人</span>
                    <span className="lead-field-value">{lead.sourceUserName || '-'}</span>
                  </div>
                  {!hideProviderOwner && <div className="lead-profile-row">
                    <span className="lead-field-label">提供方</span>
                    <span className="lead-field-value">{lead.providerOwnerNameSnapshot || '-'}</span>
                  </div>}
                  <div className="lead-profile-row">
                    <span className="lead-field-label">所属销售</span>
                    <span className="lead-field-value">{lead.ownerUserName || '暂未分配'}</span>
                  </div>
                  <div className="lead-profile-row">
                    <span className="lead-field-label">分类</span>
                    <span className="lead-field-value">{lead.leadCategoryLabelSnapshot?.trim() || categoryLabel(lead.leadCategory)}</span>
                  </div>
                  <div className="lead-profile-row">
                    <span className="lead-field-label">渠道</span>
                    <span className="lead-field-value">{lead.sourceChannelLabelSnapshot || "历史未记录"}</span>
                  </div>
                  <div className="lead-profile-row">
                    <span className="lead-field-label">地区</span>
                    <span className="lead-field-value">{[lead.provinceName, lead.cityName].filter(Boolean).join(' / ') || '-'}</span>
                  </div>
                </div>}
              </section>

              {/* 6:6 等分 */}
              <div className="lead-overview-row-half">
                <section className="lead-card">
                  <div className="lead-card-header">
                    <Typography.Text strong>{service ? '成交产品' : '意向产品'}</Typography.Text>
                  </div>
                  {service ? (
                    <div className="lead-product-list">
                      <div className="lead-product-card primary">
                        <div className="lead-product-card-header"><Tag color="green" bordered={false}>已成交</Tag><span className="lead-product-name">{service.courseName || service.skuName || '课程服务'}</span></div>
                        {service.skuName && service.skuName !== service.courseName && <span className="lead-product-sku">{service.skuName}</span>}
                        {service.orderNo && <span className="lead-product-sku">订单号：{service.orderNo}</span>}
                      </div>
                    </div>
                  ) : lead?.intendedProducts?.length ? (
                    <div className="lead-product-list">
                      {[...lead!.intendedProducts!].sort((a, b) => (b.primary ? 1 : 0) - (a.primary ? 1 : 0)).map(product => (
                        <ProductCard key={product.id} product={product} />
                      ))}
                    </div>
                  ) : (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无意向产品" />
                  )}
                </section>

                {(slots?.latestActivity || studentContext || lead) && <section className="lead-card">
                  {slots?.latestActivity || (studentContext ? <LatestStudentContact records={studentContext.contactRecords} /> : lead ? <LatestFollowUp leadId={lead.id} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无最近联系" />)}
                </section>}
              </div>

              {/* 满宽：备注与附件 */}
              <section className="lead-card">
                {lead ? <RemarksAndAttachments lead={lead} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未关联客资，暂无备注或附件" />}
              </section>
            </div>

            {/* 4 列：时效进度 + 客资流转 */}
            <div className="lead-overview-col-secondary">
              {studentContext && <section className="lead-card">
                <div className="lead-card-header"><Typography.Text strong>联系任务时效</Typography.Text></div>
                {studentContext.contactContext.currentTask?.dueAt ? <div className="lead-deadlines"><DeadlineIndicator label="下次联系截止" deadline={new Date(studentContext.contactContext.currentTask.dueAt).getTime()} /></div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待处理联系任务" />}
              </section>}
              {studentContext && (
                <section className="lead-card">
                  <div className="lead-card-header">
                    <Typography.Text strong>时效进度</Typography.Text>
                  </div>
                  <StudentTaskPipeline context={studentContext.contactContext} records={studentContext.contactRecords} />
                </section>
              )}

              {slots?.timeline && <section className="lead-card">
                {slots.timeline}
              </section>}
            </div>
          </div>
        </div>

        {/* 右侧边栏 3 列：提示 → 工具条 → 状态卡 → 跟进图表 */}
        <aside className="lead-overview-aside">
          {/* 磨砂工具条 */}
          {toolbar}
          {slots?.sidebarBeforeStatus}
          {/* 状态卡：Pipeline + 色条标签墙 */}
          <section className="lead-card lead-status-card">
            <div className="lead-status-card-body">
              {slots?.taskStatus || (studentContext
                ? <><StudentTaskPipeline context={studentContext.contactContext} records={studentContext.contactRecords} /><div className="lead-status-divider" /><StudentTaskLabels context={studentContext.contactContext} /></>
                : lead ? <><LeadStatusPipeline lead={lead} /><div className="lead-status-divider" /><LeadStatusLabels lead={lead} /></>
                : null)}
            </div>
          </section>
        </aside>
      </div>
    </div>
  )
}

export { NameAvatar }
