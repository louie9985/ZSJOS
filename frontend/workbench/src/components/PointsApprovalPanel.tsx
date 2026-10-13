import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Skeleton, Space, Tag, Timeline, Typography, message } from 'antd'
import { BellOutlined, ReloadOutlined } from '@ant-design/icons'
import { pointsApi, type PointsApproval } from '../services/pointsRedemptionApi'
import { createIdempotencyKey } from '../services/idempotency'
import { formatTimestamp } from '../services/time'
import { bpmStatusColor, bpmStatusLabel } from './bpm/bpmStatus'

export default function PointsApprovalPanel({ id, canUrge }: { id: number; canUrge: boolean }) {
  const [data, setData] = useState<PointsApproval>(), [loading, setLoading] = useState(true)
  const [error, setError] = useState(''), [saving, setSaving] = useState(false), [now, setNow] = useState(Date.now())
  const generation = useRef(0), attempt = useRef(''), pending = useRef(false)
  const load = useCallback(async () => {
    const seq = ++generation.current; setLoading(true); setError(''); setData(undefined)
    try { const result = await pointsApi.approval(id); if (seq === generation.current) setData(result) }
    catch (e) { if (seq === generation.current) setError(e instanceof Error ? e.message : '审批流程加载失败') }
    finally { if (seq === generation.current) setLoading(false) }
  }, [id])
  useEffect(() => { attempt.current = ''; void load(); return () => { ++generation.current } }, [load])
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer) }, [])
  const cooling = !!data?.nextUrgeAt && data.nextUrgeAt > now
  const urge = async () => {
    if (pending.current) return
    pending.current = true; setSaving(true)
    attempt.current ||= createIdempotencyKey()
    try { await pointsApi.urge(id, attempt.current); attempt.current = ''; message.success('催办已提交，将提醒当前审批人') }
    catch (e) { message.error(e instanceof Error ? e.message : '催办失败，请重试') }
    finally { await load(); pending.current = false; setSaving(false) }
  }
  return <section aria-label="积分审批流程">
    <Space wrap><Typography.Title level={5}>审批流程</Typography.Title>
      <Button size="small" icon={<ReloadOutlined />} disabled={saving} loading={loading} onClick={() => void load()}>刷新流程</Button></Space>
    {loading ? <Skeleton active paragraph={{ rows: 3 }} /> : error ? <Alert type="error" showIcon title={error} action={<Button onClick={() => void load()}>重试</Button>} />
      : data?.availability === 'UNAVAILABLE' ? <Alert type="warning" showIcon title={data.unavailableReason} action={<Button onClick={() => void load()}>重试</Button>} /> : data?.progress && <>
        <Typography.Paragraph><Tag color={bpmStatusColor(data.progress.status)}>{bpmStatusLabel(data.progress.status)}</Tag><strong>当前审批人：</strong>{data.progress.status === 1
          ? data.progress.currentTasks.map(t => `${t.name} · ${t.assigneeName || '待分配'}`).join('；') || '暂无可处理任务'
          : '审批已结束'}</Typography.Paragraph>
        {data.progress.nodes.length ? <Timeline items={data.progress.nodes.map(node => ({
          color: node.status === 2 ? 'green' : node.status === 3 ? 'red' : 'blue',
          children: <div><Space wrap><Typography.Text strong>{node.name}</Typography.Text><Tag color={bpmStatusColor(node.status)}>{bpmStatusLabel(node.status)}</Tag></Space>
            {node.tasks.map(task => <div key={task.id}><Space wrap><span>{task.assigneeName || task.ownerName || '未记录处理人'}</span><Tag>{bpmStatusLabel(task.status)}</Tag></Space>
              <Typography.Paragraph type="secondary">到达：{formatTimestamp(task.createTime)}{task.endTime && ` · 处理：${formatTimestamp(task.endTime)}`}</Typography.Paragraph>
              {task.reason && <Typography.Paragraph>审批意见：{task.reason}</Typography.Paragraph>}</div>)}
            {!node.tasks.length && node.candidates.length > 0 && <Typography.Text type="secondary">候选审批人：{node.candidates.map(u => u.name || '未知用户').join('、')}</Typography.Text>}
          </div>
        }))} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批记录" />}
        <Space wrap>{canUrge && data.canUrge && <Button icon={<BellOutlined />} disabled={cooling} loading={saving} onClick={() => void urge()}>{cooling ? '催办冷却中' : '催办'}</Button>}
          {canUrge && data.canUrge && cooling && <Typography.Text type="secondary">可再次催办：{formatTimestamp(data.nextUrgeAt)}</Typography.Text>}</Space>
      </>}
  </section>
}
