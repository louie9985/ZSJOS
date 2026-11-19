import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, http } from './api'

const order = {
  id: 15,
  taskId: 'sign-task',
  processInstanceId: 'round-2-process',
  approvalTask: { id: 'sign-task', processInstanceId: 'round-2-process' },
}

afterEach(() => vi.restoreAllMocks())

describe('finance order approval task identity', () => {
  it('uses the entry-specific endpoint with the same explicit task and history scope', async () => {
    const request = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: order } })
    await expect(api.financeApprovalOrder('sign-task', true, 'business', 15)).resolves.toBe(order)
    await expect(api.financeApprovalOrder('sign-task', true, 'bpm')).resolves.toBe(order)
    expect(request.mock.calls).toEqual([
      ['/zsjos/sales-order/approval/business-detail', { params: { taskId: 'sign-task', done: true } }],
      ['/zsjos/sales-order/approval/native-detail', { params: { taskId: 'sign-task', done: true } }],
    ])
  })

  it.each([
    { ...order, taskId: 'parent-task' },
    { ...order, approvalTask: { ...order.approvalTask, id: 'parent-task' } },
    { ...order, approvalTask: { ...order.approvalTask, processInstanceId: 'round-1-process' } },
    { ...order, id: 12 },
  ])('rejects a different task, process or business order', async result => {
    vi.spyOn(http, 'get').mockResolvedValue({ data: result })
    await expect(api.financeApprovalOrder('sign-task', true, 'business', 15)).rejects.toThrow('目标审批任务与订单不匹配')
  })

  it('routes business commands and comments to authorize only the selected task', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: true })
    const put = vi.spyOn(http, 'put').mockResolvedValue({ data: true })
    const comment = { taskId: 'sign-task', message: '已核对' }
    const sign = { taskId: 'sign-task', action: 'sign', type: 'before', userIds: [12], reason: '复核' } as const
    await api.createFinanceApprovalComment('bpm', comment)
    await api.financeApprovalAction({ ...sign, userIds: [...sign.userIds] })
    expect(post).toHaveBeenCalledWith('/zsjos/sales-order/approval/comment', comment, { params: { entry: 'bpm' } })
    expect(put).toHaveBeenCalledWith('/zsjos/sales-order/approval/native-action', sign)
  })
})
