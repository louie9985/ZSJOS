import { Alert, App, Button, Descriptions, Image, Modal, Space, Tag, Typography } from 'antd'
import { BellOutlined, ClockCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, type PendingLead } from '../services/api'
import { resolvedDisplayLabel } from '../services/leadManagement'
import {
  ASSIGNMENT_REFRESH_RETRY_DELAYS_MS,
  formatCountdown,
  hasPendingLead,
  remainingSecondsAt,
  shouldFocusAssignmentEvent,
  shouldShowAssignmentModal,
  sortPendingLeads
} from '../services/leadAssignment'
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
      title={current?.dispatchMode === 'auto' ? '新客资待接单' : '指定客资待接单'}
      closable={false}
      maskClosable={false}
      keyboard={false}
      destroyOnHidden
      footer={current && <Space className="lead-assignment-actions">
        {current.rejectable
          ? <><Button danger loading={processing} onClick={() => void handle('reject')}>不接单</Button><Button type="primary" loading={processing} onClick={() => void handle('accept')}>接单</Button></>
          : <><Button disabled={!current.deferrable} onClick={() => setDeferred(ids => new Set(ids).add(current.id))}>稍后接单</Button><Button type="primary" loading={processing} onClick={() => void handle('accept')}>接单</Button></>}
      </Space>}
    >
      {current && <>
        {countdown != null && <Alert className="assignment-countdown" type="warning" showIcon icon={<ClockCircleOutlined/>}
          title={<>请在 <strong>{formatCountdown(countdown)}</strong> 内处理，超时后将自动派给下一位销售</>} />}
        <LeadDetails lead={current} />
      </>}
    </Modal>
  </>
}

export function LeadDetails({ lead }: { lead: PendingLead }) {
  return <Descriptions column={1} size="small" bordered>
    <Descriptions.Item label="客户姓名">{lead.maskedName || '-'}</Descriptions.Item>
    <Descriptions.Item label="手机号">{lead.maskedMobile || '-'}</Descriptions.Item>
    <Descriptions.Item label="微信号">{lead.maskedWechatId || '-'}</Descriptions.Item>
    <Descriptions.Item label="地区">{lead.provinceName} / {lead.cityName}</Descriptions.Item>
    <Descriptions.Item label="意向课程"><Space wrap>{lead.intendedProducts.map(name => <Tag key={name} color={name === lead.primaryIntendedProduct ? 'blue' : undefined}>{name}</Tag>)}</Space></Descriptions.Item>
    <Descriptions.Item label="来源渠道">{resolvedDisplayLabel(lead.sourceChannelLabel, lead.sourceChannel)}</Descriptions.Item>
    <Descriptions.Item label="客资分类">{resolvedDisplayLabel(lead.leadCategoryLabel, lead.leadCategory)}</Descriptions.Item>
    <Descriptions.Item label="备注">{lead.remark || '-'}</Descriptions.Item>
    {lead.attachmentUrls.length > 0 && <Descriptions.Item label="附件"><Image.PreviewGroup>{lead.attachmentUrls.map(url => <Image key={url} width={64} height={64} src={url} />)}</Image.PreviewGroup></Descriptions.Item>}
  </Descriptions>
}
