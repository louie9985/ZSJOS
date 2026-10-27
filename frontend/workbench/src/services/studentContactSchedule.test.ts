import { describe, it, expect } from 'vitest'
import { studentContactSchedule } from './studentContactSchedule'
import { FOLLOW_UP_TIME_SHORTCUTS } from './leadFollowUp'
describe('student contact scheduling', () => {
  it('uses only short choices for unsuccessful first contact', () => {
    const result = studentContactSchedule('student_first_contact', false, false, 120, 4320)
    expect(result.required).toBe(true); expect(result.timeout).toBe(120)
    expect(result.shortcuts.map(row => row.label)).toEqual(['+30 分钟', '+1 小时'])
  })
  it('uses 72h configured policy and seven shortcuts for unfinished plan', () => {
    for (const type of ['student_first_contact', 'student_study_plan']) {
      const result = studentContactSchedule(type, type === 'student_first_contact', false, 120, 4320)
      expect(result.required).toBe(true);expect(result.timeout).toBe(4320)
      expect(result.shortcuts.map(row => row.label)).toEqual(['+30 分钟', '+1 小时', '+1 天', '+2 天', '+3 天', '+7 天', '+30 天'])
    }
  })
  it('makes next time optional after plan completion, even on failed ordinary contact', () => {
    for (const [type, success, plan] of [['student_first_contact', true, true], ['student_study_plan', true, false], ['student_contact', false, false]] as const) {
      const result = studentContactSchedule(type, success, plan, 120, 4320)
      expect(result.required).toBe(false);expect(result.timeout).toBe(0);expect(result.shortcuts).toHaveLength(7)
    }
    expect(FOLLOW_UP_TIME_SHORTCUTS.some(row => row.key === '30-days')).toBe(true)
  })
})
