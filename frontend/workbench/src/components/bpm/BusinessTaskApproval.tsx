import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Skeleton } from 'antd'
import { api, type BpmTask } from '../../services/api'
import ContentReviewBatchPage from '../../pages/ContentReviewBatchPage'
import MaterialApprovalPage from '../../pages/MaterialApprovalPage'
import LeadAppealReviewDetail from '../LeadAppealReviewDetail'
import type { EmbeddedApproval } from './embeddedApproval'

export function hasBusinessApproval(task?: BpmTask) {
  return ['zsjos_lead_appeal_review', 'zsjos_production_content_review', 'zsjos_viral_account_review', 'zsjos_viral_content_review']
    .includes(task?.processDefinitionKey || '')
}

/** Never fall back to generic decisions when the business view cannot be loaded. */
export default function BusinessTaskApproval({ task, permissions, done, onState, onComplete }: {
  task: BpmTask
  permissions: string[]
} & Pick<EmbeddedApproval, 'done' | 'onState' | 'onComplete'>) {
  const [query, setQuery] = useState<EmbeddedApproval['query']>()
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const appealReview = task.processDefinitionKey === 'zsjos_lead_appeal_review'
  const state = useRef({ dirty: false, busy: false })
  const onDirtyChange = useCallback((dirty: boolean) => { state.current.dirty = dirty; onState(dirty, state.current.busy) }, [onState])
  const onBusyChange = useCallback((busy: boolean) => { state.current.busy = busy; onState(state.current.dirty, busy) }, [onState])
  useEffect(() => () => onState(false, false), [onState])
  const contentReview = task.processDefinitionKey === 'zsjos_production_content_review'
  const retry = useCallback(() => setAttempt(value => value + 1), [])
  useEffect(() => {
    let cancelled = false
    setQuery(undefined); setError('')
    const locate = async () => {
      if (contentReview) {
        const card = await api.bpmApprovalBusinessDetail(task.id, done ? 'done' : 'todo')
        const batchId = Number(card?.query?.batchId)
        if (!Number.isSafeInteger(batchId) || batchId <= 0) throw new Error(card?.message || '无法定位内容审核批次，请重试或联系管理员')
        return { batchId }
      }
      const target = await api.bpmBusinessTaskTarget(task.id, done ? 'done' : 'todo')
      if (!target.supported) throw new Error(target.message || '审批任务暂不可用')
      if (appealReview) {
        if (target.bizType !== 'lead_appeal'
          || !Number.isSafeInteger(Number(target.query.appealId)) || Number(target.query.appealId) <= 0) {
          throw new Error('申诉任务定位不匹配，请刷新重试')
        }
        return target.query
      }
      if (target.bizType !== 'material' || String(target.query.taskId) !== task.id) throw new Error('审批任务定位不匹配，请刷新重试')
      if (!Number.isSafeInteger(Number(target.query.versionId)) || Number(target.query.versionId) <= 0) throw new Error('素材审批版本不可用，请刷新重试')
      return target.query
    }
    void locate().then(value => { if (!cancelled) setQuery(value) })
      .catch(cause => { if (!cancelled) setError(cause instanceof Error ? cause.message : '业务审批加载失败') })
    return () => { cancelled = true }
  }, [task.id, contentReview, appealReview, done, attempt])
  if (error) return <Alert type="error" showIcon message={error} action={<Button onClick={retry}>重试</Button>} />
  if (!query) return <Skeleton active paragraph={{ rows: 10 }} />
  if (appealReview) return <div className="bpm-business-approval"><LeadAppealReviewDetail
    item={{ id: Number(query.appealId), taskId: task.id }} readOnly={done}
    onDirtyChange={onDirtyChange} onBusyChange={onBusyChange} onChanged={onComplete} /></div>
  const embedded = { query, taskId: task.id, done, onState, onComplete }
  return <div className="bpm-business-approval">{contentReview
    ? <ContentReviewBatchPage permissions={permissions} embedded={embedded} />
    : <MaterialApprovalPage permissions={permissions} embedded={embedded} />}</div>
}
