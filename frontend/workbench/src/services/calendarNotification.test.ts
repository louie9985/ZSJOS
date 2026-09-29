import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, http } from './api'

describe('calendar employee candidate contract', () => {
  afterEach(() => vi.restoreAllMocks())

  it.each(['EXAM', 'COURSE'] as const)('requests a typed page for %s', async calendarType => {
    const page = { list: [{ id: 7, nickname: '测试员工', deptId: 31 }], total: 41 }
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: page } })
    const calendar = calendarType === 'EXAM' ? api.examCalendar : api.courseCalendar
    expect(await calendar.notifyUsers('测试', 3, 20)).toEqual(page)
    expect(get).toHaveBeenCalledWith('/zsjos/calendar-notification/users', {
      params: { calendarType, keyword: '测试', pageNo: 3, pageSize: 20 },
    })
  })

  it('propagates authorization failure rather than presenting an empty employee list', async () => {
    const denied = new Error('没有该日历的发送通知权限')
    vi.spyOn(http, 'get').mockRejectedValue(denied)
    await expect(api.courseCalendar.notifyUsers()).rejects.toBe(denied)
  })
})
