import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Alert, App, Button, Empty, Select, Skeleton, Space, Tag, Timeline, Typography } from 'antd'
import { BellOutlined, ReloadOutlined } from '@ant-design/icons'
import { feedbackApi, type FeedbackApproval, type FeedbackField } from '../services/feedbackApi'
import { createIdempotencyKey } from '../services/idempotency'
import { formatTimestamp } from '../services/time'
import { ApiError } from '../services/api'
import { bpmStatusColor, bpmStatusLabel } from './bpm/bpmStatus'

export default function FeedbackApprovalPanel({ id, approver = false, readOnly, canUrge, renderValues, onUrged }: {
  id: number; approver?: boolean; readOnly: boolean; canUrge: boolean
  renderValues: (fields: FeedbackField[], values: Record<string, unknown>) => ReactNode
  onUrged: () => void
}) {
  const { message } = App.useApp()
  const [roundNo, setRoundNo] = useState<number>()
  const [data, setData] = useState<FeedbackApproval>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [now, setNow] = useState(Date.now())
  const sequence = useRef(0)
  const command = useRef<{ key: string; version: number; roundNo: number } | undefined>(undefined)
  const load = useCallback(async () => {
    const seq = ++sequence.current
    setLoading(true); setError(''); setData(undefined)
    try {
      const result = await feedbackApi.approval(id, roundNo, approver)
      if (seq === sequence.current) setData(result)
    } catch (cause) { if (seq === sequence.current) setError(cause instanceof Error ? cause.message : '审批流程加载失败') }
    finally { if (seq === sequence.current) setLoading(false) }
  }, [id, roundNo, approver])
  useEffect(() => { void load(); return () => { sequence.current++ } }, [load])
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 1000); return () => window.clearInterval(timer) }, [])
  const next = data?.nextUrgeAt
  const cooling = next != null && next > now
  const urge = async () => {
    if (!data || saving) return
    setSaving(true)
    const request = command.current ?? { key: createIdempotencyKey(), version: data.version, roundNo: data.roundNo }
    command.current = request
    try {
      await feedbackApi.urge(id, request.version, request.roundNo, request.key)
      command.current = undefined
      message.success('催办已提交，将通过消息中心提醒当前审批人')
      await load(); onUrged()
    } catch (cause) {
      if (cause instanceof ApiError) command.current = undefined
      message.error(cause instanceof Error ? cause.message : '催办失败，请重试')
      await load()
    } finally { setSaving(false) }
  }
  return <section className="feedback-detail-section feedback-approval-panel">
    <Space wrap className="feedback-approval-heading">
      <Typography.Title level={5}>审批流程</Typography.Title>
      {data && <Select aria-label="审批轮次" value={data.roundNo} disabled={saving} onChange={value => { command.current = undefined; setRoundNo(value) }}
        options={data.rounds.map(round => ({ value: round.roundNo, label: `第 ${round.roundNo} 轮${round.roundNo === data.latestRoundNo ? '（最新）' : ''}` }))}/>}
      <Button aria-label="刷新流程" size="small" icon={<ReloadOutlined/>} disabled={saving} loading={loading} onClick={() => void load()}>刷新流程</Button>
    </Space>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>}/>}
    {loading ? <Skeleton active paragraph={{ rows: 4 }}/> : data && <>
      {data.availability !== 'AVAILABLE' && <Alert type={data.availability === 'NOT_REQUIRED' ? 'info' : 'warning'} showIcon message={data.unavailableReason}
        action={data.availability === 'UNAVAILABLE' ? <Button onClick={() => void load()}>重试</Button> : undefined}/>}
      {data.progress && <>
        <Typography.Paragraph><strong>当前审批人：</strong>{data.progress.status === 1
          ? data.progress.currentTasks.map(task => `${task.name} · ${task.assigneeName || '待分配'}`).join('；') || '暂无可处理任务'
          : '本轮审批已结束'}</Typography.Paragraph>
        {data.progress.nodes.length ? <Timeline items={data.progress.nodes.map(node => ({
          color: node.status === 2 ? 'green' : node.status === 3 ? 'red' : 'blue',
          children: <div><Space wrap><Typography.Text strong>{node.name}</Typography.Text><Tag color={bpmStatusColor(node.status)}>{bpmStatusLabel(node.status)}</Tag></Space>
            {node.tasks.map(task => <div key={task.id} className="feedback-approval-task">
              <Space wrap><span>{task.assigneeName || task.ownerName || '待分配'}</span>{task.parentTaskId && <Tag>加签</Tag>}<Tag>{bpmStatusLabel(task.status)}</Tag></Space>
              <div>到达：{formatTimestamp(task.createTime)}{task.endTime && ` · 处理：${formatTimestamp(task.endTime)}`}</div>
              {task.reason && <div className="feedback-approval-reason">审批意见：{task.reason}</div>}
            </div>)}
            {!node.tasks.length && node.candidates.length > 0 && <Typography.Text type="secondary">预计节点，候选审批人：{node.candidates.map(user => user.name || '未知用户').join('、')}</Typography.Text>}
          </div>
        }))}/> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批记录"/>}
      </>}
      <Space wrap>
        {!readOnly && canUrge && data.canUrge && <Button aria-label={cooling ? '催办冷却中' : '催办'} icon={<BellOutlined/>} loading={saving} disabled={cooling} onClick={() => void urge()}>{cooling ? '催办冷却中' : '催办'}</Button>}
        {data.lastUrgedAt && <Typography.Text type="secondary">上次催办：{formatTimestamp(data.lastUrgedAt)}</Typography.Text>}
        {cooling && <Typography.Text type="secondary">可再次催办：{formatTimestamp(data.nextUrgeAt)}</Typography.Text>}
      </Space>
      <Typography.Title level={5}>第 {data.roundNo} 轮提交内容</Typography.Title>
      {renderValues(data.fields, data.values)}
    </>}
  </section>
}
