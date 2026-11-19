import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Skeleton, Typography } from 'antd'
import { api, BPM_OPERATION_BUTTON, type BpmTask, type FinanceApprovalEntry } from '../services/api'
import { salesOrderDetailToListItem } from '../services/salesOrder'
import SalesOrderDetailCards from './SalesOrderDetailCards'
import ApprovalComments from './bpm/ApprovalComments'
import SalesOrderApprovalHistory from './SalesOrderApprovalHistory'
import BpmApprovalActions from './bpm/BpmApprovalActions'
import type { ApprovalDraft } from './bpm/BpmApprovalDetail'
import { useApprovalResource } from './bpm/useApprovalResource'

export default function FinanceOrderApprovalDetail({
  taskId, orderId, done, entry, canUpdate, refreshVersion = 0, onSuccess, initialDraft, onDraft, onState
}: {
  taskId: string
  orderId?: number
  done: boolean
  entry: FinanceApprovalEntry
  canUpdate: boolean
  refreshVersion?: number
  onSuccess: (completed?: boolean) => void
  initialDraft?: ApprovalDraft
  onDraft?: (draft: Partial<ApprovalDraft>) => void
  onState?: (dirty: boolean, busy: boolean) => void
}) {
  const identity = JSON.stringify([taskId, orderId, done, refreshVersion])
  const order = useApprovalResource(identity, () => api.financeApprovalOrder(taskId, done, entry, orderId))
  const snapshot = order.data
  const approvalTask = snapshot?.approvalTask
  const canAct = canUpdate && !done && !order.loading && !order.error && approvalTask?.canAct === true
  const canSign = canAct && approvalTask?.canSign === true
  const users = useApprovalResource(JSON.stringify([taskId, entry, canSign]), () =>
    canSign ? api.financeApprovalSignCandidates(taskId, entry) : Promise.resolve([]))
  const [action, setAction] = useState({ dirty: false, busy: false })
  const [comment, setComment] = useState({ dirty: false, busy: false })
  const updateAction = useCallback((dirty: boolean, busy: boolean) => setAction({ dirty, busy }), [])
  const updateComment = useCallback((dirty: boolean, busy: boolean) => setComment({ dirty, busy }), [])
  const saveComment = useCallback((text: string) => onDraft?.({ comment: text }), [onDraft])
  useEffect(() => { onState?.(action.dirty || comment.dirty, action.busy || comment.busy) }, [action, comment, onState])

  if (order.error && !snapshot) return <Alert type="error" showIcon title={order.error} action={<Button onClick={order.reload}>重试</Button>}/>
  if (!snapshot || !approvalTask) return <Skeleton active paragraph={{ rows: 10 }}/>

  const task: BpmTask = {
    ...approvalTask,
    createTime: snapshot.taskCreateTime,
    endTime: snapshot.taskEndTime,
    reason: snapshot.taskReason,
    buttonsSetting: {
      ...approvalTask.buttonsSetting,
      [BPM_OPERATION_BUTTON.TRANSFER]: { enable: false },
      [BPM_OPERATION_BUTTON.DELEGATE]: { enable: false },
      [BPM_OPERATION_BUTTON.RETURN]: { enable: false },
      [BPM_OPERATION_BUTTON.COPY]: { enable: false },
      [BPM_OPERATION_BUTTON.ADD_SIGN]: {
        ...approvalTask.buttonsSetting?.[BPM_OPERATION_BUTTON.ADD_SIGN],
        enable: approvalTask.canSign && !users.loading && !users.error && !!users.data?.length,
      },
    },
  }
  const refresh = (completed?: boolean) => {
    if (!completed) void order.reload()
    onSuccess(completed)
  }
  return <SalesOrderDetailCards order={snapshot} informationLayout="table" mode={done ? 'approval-done' : 'approval-todo'}
    approvalContext={{ ...salesOrderDetailToListItem(snapshot), taskId, taskName: approvalTask.name, taskDefinitionKey: snapshot.approvalTask.taskDefinitionKey === 'financeReview' ? 'financeReview' : undefined,
      taskStatus: snapshot.taskStatus,
      taskReason: snapshot.taskReason, taskCreateTime: snapshot.taskCreateTime, taskEndTime: snapshot.taskEndTime }}
    reloadVouchers={async () => (await api.financeApprovalOrder(taskId, done, entry, orderId)).paymentVouchers}
    approvalSidebar={<>
      {order.error && <Alert type="error" showIcon title={order.error} action={<Button onClick={order.reload}>重试</Button>}/>}
      {!done && !canAct && !order.loading && <Alert type="info" showIcon title="当前任务不可由本人处理"/>}
      {!done && canUpdate && (approvalTask.canAct || action.dirty) && <BpmApprovalActions
        task={task} canUpdate={canAct && !comment.busy} users={users.data ?? []} inline rejectReasonRequired decisionOnly={!approvalTask.canSign}
        submitFinanceAction={entry === 'business' ? api.financeApprovalAction : undefined}
        initialDraft={initialDraft} onDraft={onDraft} onState={updateAction}
        onFailure={order.reload} onSuccess={refresh}/>}
      {canSign && users.error && <Alert type="error" showIcon title={users.error} action={<Button onClick={users.reload}>重试</Button>}/>}
      <Typography.Title level={5}>审批流程</Typography.Title>
      <SalesOrderApprovalHistory progress={snapshot.approvalProgress} comments={snapshot.approvalComments ?? []} incomplete={order.loading || !!order.error}/>
      <Typography.Title level={5}>流程评论</Typography.Title>
      <ApprovalComments taskId={taskId} comments={snapshot.approvalComments ?? []} loading={order.loading}
        canComment={canAct && !action.busy} onRefresh={order.reload} onState={updateComment}
        initialText={initialDraft?.comment} onDraft={saveComment} failed={!!order.error}
        submitComment={data => api.createFinanceApprovalComment(entry, data)}/>
    </>}/>
}
