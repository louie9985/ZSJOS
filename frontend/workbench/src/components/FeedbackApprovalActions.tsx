import { useEffect, useState } from 'react'
import { Alert, Button, Skeleton, Typography } from 'antd'
import { ApiError, type BpmTask } from '../services/api'
import { feedbackApi, type FeedbackApprovalTask } from '../services/feedbackApi'
import BpmApprovalActions from './bpm/BpmApprovalActions'

const TASK_NOT_EXISTS = 1_009_005_002

/** 轮次进度只提供候选任务；必须再由 BPM 核实当前登录人的实时任务归属。 */
export default function FeedbackApprovalActions({ tasks, onSuccess }: {
  tasks: FeedbackApprovalTask[]
  onSuccess: () => void
}) {
  const [current, setCurrent] = useState<BpmTask[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let cancelled = false
    setCurrent([]); setError(''); setLoading(true)
    void Promise.all(tasks.map(async task => {
      try {
        const result = await feedbackApi.approvalTask(task.id)
        return result.id === task.id && result.status === 1 ? result : undefined
      } catch (cause) {
        // 其他人的任务、已结束或已转办任务不授予操作；网络和权限错误单独展示。
        if (cause instanceof ApiError && cause.code === TASK_NOT_EXISTS) return undefined
        throw cause
      }
    })).then(result => {
      if (!cancelled) setCurrent(result.filter((task): task is BpmTask => task !== undefined))
    }).catch(cause => {
      if (!cancelled) setError(cause instanceof Error ? cause.message : '审批操作加载失败')
    }).finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [tasks, attempt])

  return <section className="feedback-task-actions" aria-label="需求审批操作">
    <Typography.Title level={5}>审批操作</Typography.Title>
    {loading ? <Skeleton active paragraph={{ rows: 1 }}/> : error
      ? <Alert type="error" showIcon message={error} action={<Button onClick={() => setAttempt(value => value + 1)}>重试</Button>}/>
      : current.length ? current.map(task => <BpmApprovalActions key={task.id} task={task}
        canUpdate decisionOnly users={[]} onSuccess={onSuccess}/> )
        : <Typography.Text type="secondary">当前没有可由你处理的审批任务</Typography.Text>}
  </section>
}
