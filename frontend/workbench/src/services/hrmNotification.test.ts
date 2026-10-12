import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http, type NotifyMessage } from './api'
import { executeNotifyMessageAction, isNotifyBusinessActionCandidate } from './notifyMessageAction'

const notification = { id: 51, actionType: 'business_detail', bizType: 'hrm_employee', bizId: 7, readStatus: true } as NotifyMessage
describe('HRM message access boundary', () => {
  beforeEach(() => vi.restoreAllMocks())
  it('checks the employee API before navigating to the existing embed entry', async () => {
    vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: { id: 7 } } })
    const deps = { navigate: vi.fn(), warn: vi.fn(), refreshUnreadCount: vi.fn() }
    expect(isNotifyBusinessActionCandidate(notification)).toBe(true)
    await executeNotifyMessageAction(notification, deps)
    expect(http.get).toHaveBeenCalledWith('/hrm/employee/get', { params: { id: 7 } })
    expect(deps.navigate).toHaveBeenCalledWith('/hrm/employee/list?employeeId=7')
  })
  it('keeps denied or unavailable employees in message detail', async () => {
    vi.spyOn(http, 'get').mockRejectedValue(new Error('forbidden'))
    const deps = { navigate: vi.fn(), warn: vi.fn(), refreshUnreadCount: vi.fn() }
    await executeNotifyMessageAction(notification, deps)
    expect(deps.navigate).toHaveBeenCalledWith(expect.stringContaining('messageId=51'))
    expect(deps.warn).toHaveBeenCalled()
  })
})
