import { getTodoTask } from '@/api/bpm/task'
import type { NotifyMessageVO } from '@/api/system/notify/message'

export const isFeedbackUrgeNotification = (detail: NotifyMessageVO) =>
  detail.actionType === 'business_detail' && detail.sceneCode === 'zsjos.feedback.approval_urged'

export async function feedbackUrgeTaskTarget(detail: NotifyMessageVO) {
  const params = typeof detail.templateParams === 'string'
    ? JSON.parse(detail.templateParams)
    : detail.templateParams
  const taskId = params?.taskId
  if (typeof taskId !== 'string' || !taskId) throw new Error('催办消息缺少审批任务')
  // A notification remains readable after transfer or completion; only a live owned task can open for action.
  const task = await getTodoTask(taskId)
  const processId = task.processInstance?.id || task.processInstanceId
  if (task.id !== taskId || !processId) throw new Error('审批任务已失效')
  return { name: 'BpmProcessInstanceDetail', query: { id: processId, taskId } }
}
