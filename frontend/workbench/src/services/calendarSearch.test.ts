import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { calendarSearchApi, calendarIntervalEnd, CalendarResultExpired, findCalendarPage, locationDate, matchingExcerpt, searchPresentation } from './calendarSearch'

afterEach(() => vi.restoreAllMocks())
describe('calendar search', () => {
  it('keeps each business endpoint and forwards scope, dates, cancellation and paging', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: { list: [], total: 0 } } })
    const query = { keyword: '%_中文', sort: 'nearest' as const, pageNo: 2, pageSize: 20, rangeStart: '2026-01-01' }
    const signal = new AbortController().signal
    await calendarSearchApi.personal(query, { readScope: 'USER', targetUserId: 17 }, signal)
    expect(get).toHaveBeenLastCalledWith('/zsjos/personal-calendar/search', { params: { ...query, readScope: 'USER', targetUserId: 17 }, signal })
    await calendarSearchApi.exam(query, 'DRAFT', signal)
    expect(get).toHaveBeenLastCalledWith('/zsjos/exam-calendar/search', { params: { ...query, recordStatus: 'DRAFT' }, signal })
    await calendarSearchApi.media(query, { directorUserId: 18 }, signal)
    expect(get).toHaveBeenLastCalledWith('/zsjos/media-account/calendar/search', { params: { ...query, directorUserId: 18 }, signal })
    await calendarSearchApi.course(query, signal)
    expect(get).toHaveBeenLastCalledWith('/zsjos/course-calendar/search', { params: query, signal })
    await calendarSearchApi.lead(query, signal)
    expect(get).toHaveBeenLastCalledWith('/zsjos/lead-follow-up-calendar/search', { params: query, signal })
  })
  it('locates an ongoing interval today and otherwise its start', () => {
    expect(locationDate('2026-09-01', '2026-10-30', '2026-10-08')).toBe('2026-10-08')
    expect(locationDate('2025-09-01', '2025-10-30', '2026-10-08')).toBe('2025-09-01')
    expect(locationDate('2027-01-01', '2027-01-02', '2026-10-08')).toBe('2027-01-01')
  })
  it('does not locate a midnight-exclusive end on an unoccupied day', () => {
    expect(calendarIntervalEnd('2027-01-01T09:00:00', '2027-01-02T00:00:00')).toBe('2027-01-01')
    expect(calendarIntervalEnd('2027-01-02T00:00:00', '2027-01-02T00:00:00')).toBe('2027-01-02')
  })
  it('finds a target beyond page one without loading subsequent pages', async () => {
    const fetch = vi.fn(async (n: number) => ({ list: [{ id: n }], total: 30 }))
    const result = await findCalendarPage(fetch, row => row.id === 3)
    expect(result.pageNo).toBe(3); expect(result.accumulated).toHaveLength(3); expect(fetch).toHaveBeenCalledTimes(3)
  })
  it('rejects expired results and cancelled searches', async () => {
    await expect(findCalendarPage(async () => ({ list: [], total: 0 }), () => false)).rejects.toBeInstanceOf(CalendarResultExpired)
    const controller = new AbortController(); controller.abort()
    const fetch = vi.fn()
    await expect(findCalendarPage(fetch, () => true, controller.signal)).rejects.toThrow()
    expect(fetch).not.toHaveBeenCalled()
  })
  it('shows matching text deep inside a remark as plain text', () => {
    expect(matchingExcerpt('前'.repeat(300) + '%_<script>内容', '%_')).toContain('%_<script>内容')
  })
  it('never substitutes an internal Lead ID for its business number', () => {
    const row = { lead: { id: 8911, submittedName: '测试姓名' }, deadline: Date.parse('2026-10-07T20:00:00Z'), canReadFollowUp: false }
    const result = searchPresentation.lead(row as Parameters<typeof searchPresentation.lead>[0])
    expect(result.title).toBe('测试姓名'); expect(result.start).toBe('2026-10-08')
  })
})
