import { Alert, App, Button, Image, Modal, Space, Tag, Typography } from 'antd'
import { BellOutlined, ClockCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, type PendingLead } from '../services/api'
import { protocolDisplayLabel, resolvedDisplayLabel } from '../services/leadManagement'
import { LEAD_DISPATCH_MODE_LABELS } from '../constants'
import { formatTimestamp } from '../services/time'
import {
  ASSIGNMENT_REFRESH_RETRY_DELAYS_MS,
  formatCountdown,
  hasPendingLead,
  remainingSecondsAt,
  shouldFocusAssignmentEvent,
  shouldShowAssignmentModal,
  sortPendingLeads
} from '../services/leadAssignment'
import { markLeadUnseen } from '../services/leadInboxUnseen'
import { NameAvatar } from './LeadDetailOverview'
import { useOverlayCoordinator } from './OverlayCoordinator'
import { useRealtime, useRealtimeEvent } from './RealtimeProvider'

const FALLBACK_POLL_MS = 15_000

export default function LeadAssignmentHost({ canAccept, onCountChange, openRequest }: {
  canAccept: boolean
  onCountChange: (count: number) => void
  openRequest: number
}) {
  const { message } = App.useApp()
  const { businessOverlayCount } = useOverlayCoordinator()
  const { status: realtimeStatus } = useRealtime()
  const mountedRef = useRef(true)
  const requestSequence = useRef(0)
  const targetedRefreshSequence = useRef(0)
  const [pending, setPending] = useState<PendingLead[]>([])
  const [deferred, setDeferred] = useState<Set<number>>(new Set())
  const [error, setError] = useState('')
  const [processing, setProcessing] = useState(false)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [focusLeadId, setFocusLeadId] = useState<number>()

  const loadPending = useCallback(async (): Promise<PendingLead[] | undefined> => {
    if (!canAccept) return undefined
    const requestId = ++requestSequence.current
    try {
      const items = await api.myPendingLeads()
      if (!mountedRef.current || requestId !== requestSequence.current) return undefined
      setPending(items)
      setElapsedSeconds(0)
      setError('')
      return items
    } catch (loadError) {
      if (!mountedRef.current || requestId !== requestSequence.current) return undefined
      setError(loadError instanceof Error ? loadError.message : '待接客资加载失败')
      return undefined
    }
  }, [canAccept])

  const refreshForLead = useCallback(async (leadId: number) => {
    const refreshId = ++targetedRefreshSequence.current
    setFocusLeadId(leadId)
    setDeferred(ids => { const next = new Set(ids); next.delete(leadId); return next })
    for (const delay of ASSIGNMENT_REFRESH_RETRY_DELAYS_MS) {
      if (delay > 0) await new Promise(resolve => window.setTimeout(resolve, delay))
      if (!mountedRef.current || refreshId !== targetedRefreshSequence.current) return
      const items = await loadPending()
      if (!mountedRef.current || refreshId !== targetedRefreshSequence.current) return
      if (items && hasPendingLead(items, leadId)) return
    }
  }, [loadPending])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      requestSequence.current++
      targetedRefreshSequence.current++
    }
  }, [])

  useEffect(() => {
    if (!canAccept) {
      setPending([])
      setError('')
      return
    }
    const refreshVisible = () => { if (document.visibilityState === 'visible') void loadPending() }
    const pollTimer = window.setInterval(() => void loadPending(), FALLBACK_POLL_MS)
    window.addEventListener('focus', refreshVisible)
    document.addEventListener('visibilitychange', refreshVisible)
    void loadPending()
    return () => {
      window.clearInterval(pollTimer)
      window.removeEventListener('focus', refreshVisible)
      document.removeEventListener('visibilitychange', refreshVisible)
    }
  }, [canAccept, loadPending])
  useEffect(() => {
    if (realtimeStatus === 'open') void loadPending()
  }, [loadPending, realtimeStatus])
  useRealtimeEvent('zsjos_lead_assignment', realtime => {
    const content = realtime.content as { leadId?: unknown; eventType?: unknown } | null
    const leadId = Number(content?.leadId)
    if (Number.isFinite(leadId) && shouldFocusAssignmentEvent(content?.eventType)) {
      void refreshForLead(leadId)
      return
    }
    void loadPending()
  })
  useEffect(() => {
    const focus = (event: Event) => {
      const leadId = Number((event as CustomEvent<{ leadId?: number }>).detail?.leadId)
      if (!Number.isFinite(leadId)) return
      void refreshForLead(leadId)
    }
    window.addEventListener('zsjos-open-lead-assignment', focus)
    return () => window.removeEventListener('zsjos-open-lead-assignment', focus)
  }, [refreshForLead])

  useEffect(() => {
    if (!pending.some(item => item.remainingSeconds != null)) return
    const timer = window.setInterval(() => setElapsedSeconds(value => value + 1), 1000)
    return () => window.clearInterval(timer)
  }, [pending])

  const ordered = useMemo(() => sortPendingLeads(pending, elapsedSeconds), [elapsedSeconds, pending])
  useEffect(() => {
    onCountChange(ordered.length)
    setDeferred(ids => new Set([...ids].filter(id => ordered.some(item => item.id === id))))
  }, [onCountChange, ordered])

  const expiredCount = pending.length - ordered.length
  useEffect(() => { if (expiredCount > 0) void loadPending() }, [expiredCount, loadPending])

  const current = ordered.find(item => item.id === focusLeadId && !deferred.has(item.id))
    ?? ordered.find(item => !deferred.has(item.id))
  const blocked = businessOverlayCount > 0
  const reminderLead = ordered[0]
  const showReminder = ordered.length > 0 && (blocked || !current || deferred.has(reminderLead.id))
  useEffect(() => {
    if (!openRequest || blocked || !reminderLead) return
    setDeferred(ids => { const next = new Set(ids); next.delete(reminderLead.id); return next })
  }, [blocked, openRequest, reminderLead])

  const handle = async (action: 'accept' | 'reject') => {
    if (!current) return
    setProcessing(true)
    try {
      if (action === 'accept') await api.acceptLead(current.id)
      else await api.rejectLead(current.id)
      setDeferred(ids => { const next = new Set(ids); next.delete(current.id); return next })
      // 标记要在刷新之前：客资列表页监听该事件自行拉取，标记晚到会漏掉高亮
      if (action === 'accept') markLeadUnseen(current.id)
      await loadPending()
      if (action === 'accept') {
        message.success('接单成功，首次跟进任务已经开始计时')
      } else {
        message.success('已拒绝，客资将继续派发')
      }
    } catch (handleError) {
      message.error(handleError instanceof Error ? handleError.message : '派单已被处理')
      await loadPending()
    } finally { setProcessing(false) }
  }

  if (!canAccept) return null
  const countdown = current ? remainingSecondsAt(current, elapsedSeconds) : undefined

  return <>
    {error && <Alert className="pending-load-error" type="error" showIcon title={error}
      action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void loadPending()}>重试</Button>} />}
    {showReminder && reminderLead && <div className="pending-reminder" role="status">
      <Space wrap>
        <BellOutlined />
        <Typography.Text strong>{ordered.length} 条客资待接</Typography.Text>
        {reminderLead.remainingSeconds != null && <Tag color="warning" icon={<ClockCircleOutlined/>}>
          {formatCountdown(remainingSecondsAt(reminderLead, elapsedSeconds))}
        </Tag>}
        {!blocked && <Button size="small" onClick={() => setDeferred(ids => {
          const next = new Set(ids); next.delete(reminderLead.id); return next
        })}>查看</Button>}
      </Space>
    </div>}
    <Modal
      open={shouldShowAssignmentModal(Boolean(current), businessOverlayCount)}
      className="lead-assignment-modal"
      width={640}
      title={<span className="assignment-modal-title">
        <BellOutlined />
        {current?.dispatchMode === 'auto' ? '新客资待接单' : '指定客资待接单'}
        {ordered.length > 1 && <Tag bordered={false} color="blue">共 {ordered.length} 条</Tag>}
      </span>}
      closable={false}
      mask={{ closable: false }}
      keyboard={false}
      destroyOnHidden
      footer={current && <div className="lead-assignment-actions">
        {current.rejectable
          ? <Button size="large" danger loading={processing} onClick={() => void handle('reject')}>不接单</Button>
          : <Button size="large" disabled={!current.deferrable}
            onClick={() => setDeferred(ids => new Set(ids).add(current.id))}>稍后接单</Button>}
        <Button className="assignment-accept-btn" size="large" type="primary" loading={processing}
          onClick={() => void handle('accept')}>接单</Button>
      </div>}
    >
      {current && <>
        {countdown != null && <div className={countdown <= 10 ? 'assignment-countdown critical' : 'assignment-countdown'} role="timer">
          <ClockCircleOutlined />
          <span className="assignment-countdown-value">{formatCountdown(countdown)}</span>
          <span className="assignment-countdown-hint">内处理，超时后自动派给下一位销售</span>
        </div>}
        <LeadDetails lead={current} />
      </>}
    </Modal>
  </>
}

function AssignmentField({ label, value }: { label: string; value?: string }) {
  return <div className="assignment-field">
    <span className="assignment-field-label">{label}</span>
    <span className={value ? 'assignment-field-value' : 'assignment-field-value lead-field-empty'}>{value || '未填写'}</span>
  </div>
}

export function LeadDetails({ lead }: { lead: PendingLead }) {
  const region = [lead.provinceName, lead.cityName].filter(Boolean).join(' / ')
  return <div className="assignment-sheet">
    <div className="assignment-sheet-identity">
      <NameAvatar name={lead.maskedName || '客'} size={44} />
      <div className="assignment-sheet-identity-info">
        <Typography.Text strong className="assignment-sheet-name">{lead.maskedName || '未填写姓名'}</Typography.Text>
        <Space size={4} wrap>
          <Tag color={lead.dispatchMode === 'auto' ? 'blue' : 'purple'} bordered={false}>
            {protocolDisplayLabel(LEAD_DISPATCH_MODE_LABELS, lead.dispatchMode, '待接')}
          </Tag>
          <Tag bordered={false}>{resolvedDisplayLabel(lead.sourceChannelLabel, lead.sourceChannel)}</Tag>
          <Tag bordered={false}>{resolvedDisplayLabel(lead.leadCategoryLabel, lead.leadCategory)}</Tag>
        </Space>
      </div>
    </div>

    <div className="assignment-sheet-fields">
      <AssignmentField label="客资编号" value={lead.leadNo} />
      <AssignmentField label="手机号" value={lead.maskedMobile} />
      <AssignmentField label="微信号" value={lead.maskedWechatId} />
      <AssignmentField label="地区" value={region} />
      <AssignmentField label="提交时间" value={formatTimestamp(lead.submittedAt)} />
    </div>

    <div className="assignment-sheet-block">
      <span className="lead-field-label">意向课程</span>
      {lead.intendedProducts.length
        ? <div className="assignment-sheet-products">
          {lead.intendedProducts.map(name => <Tag key={name}
            color={name === lead.primaryIntendedProduct ? 'green' : undefined}
            bordered={name !== lead.primaryIntendedProduct}>{name}</Tag>)}
        </div>
        : <span className="assignment-field-value lead-field-empty">未填写</span>}
    </div>

    {lead.remark && <div className="assignment-sheet-block">
      <span className="lead-field-label">备注</span>
      <Typography.Paragraph className="assignment-sheet-remark"
        ellipsis={{ rows: 3, expandable: 'collapsible', symbol: (expanded: boolean) => expanded ? '收起' : '展开' }}>
        {lead.remark}
      </Typography.Paragraph>
    </div>}

    {lead.attachmentUrls.length > 0 && <div className="assignment-sheet-block">
      <span className="lead-field-label">附件</span>
      <div className="assignment-sheet-attachments">
        <Image.PreviewGroup>
          {lead.attachmentUrls.map(url => <Image key={url} width={64} height={64} src={url} />)}
        </Image.PreviewGroup>
      </div>
    </div>}
  </div>
}
