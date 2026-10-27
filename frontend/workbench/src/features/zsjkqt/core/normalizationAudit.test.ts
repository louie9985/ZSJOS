import { describe, expect, it } from 'vitest'
import { classifyDay, reclassifyDaysForSchedule } from './dayClassifier'
import { recalculateSummary } from './summary'
import { attendanceWorkSchedule, DEFAULT_WORK_SCHEDULE, validateDepartmentScheduleSettings } from './departmentSchedules'
import { buildAttendanceProject, buildProjectFromExportedReport, mergeRebuiltProjectWithReviewState, applyReimportMonthlyParameters } from './reviewBuilder'
import { refreshSourceSummaryReviewIssue, SOURCE_SUMMARY_REVIEW_MESSAGE } from './sourceSummaryReview'
import { dayHasReviewIssue, dayHasManualReviewReminder } from './reviewIssues'
import { ATTENDANCE_STATUSES, type AttendanceStatus, type DayReview, type AttendanceMonth } from './models'

const schedule = attendanceWorkSchedule(DEFAULT_WORK_SCHEDULE)
const custom = attendanceWorkSchedule({ morning: { start: '09:00', end: '12:00' }, afternoon: { start: '13:00', end: '18:00' } })
const options = { restDays: [], paidRestDays: [], shouldAttendanceDays: 21, payrollDays: 23 }
function classified(rawDetail: string, workSchedule = schedule): DayReview {
  const { result, issues } = classifyDay(rawDetail, '合成员工', 1, false, workSchedule)
  return { day: 1, rawDetail, systemMorning: result.morning, systemAfternoon: result.afternoon,
    finalMorning: result.morning, finalAfternoon: result.afternoon, attendanceMorning: result.attendanceMorning,
    attendanceAfternoon: result.attendanceAfternoon, workMorning: result.workMorning, workAfternoon: result.workAfternoon,
    morningNote: '', afternoonNote: '', modified: false, issues }
}
function summary(day: DayReview, sourceSummaries: Record<string, unknown> = {}, workSchedule = schedule) {
  return recalculateSummary({ days: [day], sourceSummaries, ...options, entryDate: '', workSchedule })
}
function corrected(day: DayReview, patch: Partial<DayReview>): DayReview {
  return refreshSourceSummaryReviewIssue({ ...day, ...patch, modified: true }, '合成员工')
}
function month(rawDetail: string): AttendanceMonth {
  return { year: 2026, month: 6, startDate: '2026-06-01', endDate: '2026-06-30', sourceFileHash: 'synthetic-audit',
    employees: [{ name: '合成员工', account: 'audit', department: '', rule: '', position: '', jobNo: '', summaries: {}, dayDetails: { 1: rawDetail } }] }
}

describe('异常转正常：已成立的回归契约', () => {
  it.each(ATTENDANCE_STATUSES)('%s 全日人工改出勤：恢复一天且清除按日统计的异常', status => {
    const day = classified('')
    day.systemMorning = day.systemAfternoon = status
    const fixed = corrected(day, { finalMorning: '出勤', finalAfternoon: '出勤' })
    expect(summary(fixed)).toMatchObject({ actualAttendanceDays: 1, missingCardCount: null, absenceDays: null,
      lateText: null, latePunishment: null, earlyText: null, sickLeaveDays: null, injuryLeaveDays: null,
      funeralLeaveDays: null, marriageLeaveDays: null, maternityLeaveDays: null })
    expect(dayHasReviewIssue(fixed)).toBe(false)
    expect(dayHasManualReviewReminder(fixed)).toBe(true)
  })

  it.each([
    '正常;缺卡(08:30)', '正常;缺卡(18:00)', '正常;缺卡(08:30);缺卡(18:00)',
    '正常;迟到20分钟-08:50', '正常;早退30分钟-17:30',
    '正常;wifi异常-08:30', '正常;地点异常-18:00', '未知格式', '旷工480分钟',
    '正常;缺卡(18:00);加班2小时（18:00-20:00）',
  ])('%s 经完整分类再改出勤，重复计算和草稿序列化不回退', raw => {
    const before = classified(raw)
    const fixed = corrected(before, { finalMorning: '出勤', finalAfternoon: '出勤' })
    const expected = summary(fixed)
    expect(expected).toMatchObject({ actualAttendanceDays: 1, missingCardCount: null, lateText: null, earlyText: null, absenceDays: null })
    expect(summary(JSON.parse(JSON.stringify(fixed)))).toEqual(expected)
    expect(summary(fixed)).toEqual(expected)
    expect(fixed.rawDetail).toBe(raw)
  })

  it.each([schedule, custom])('只改一半缺卡，保留另一半并按班次占比恢复', workSchedule => {
    const before = classified('缺卡(08:30);缺卡(18:00)', workSchedule)
    const morning = corrected(before, { finalMorning: '出勤' })
    expect(summary(morning, {}, workSchedule)).toMatchObject({ missingCardCount: 1,
      actualAttendanceDays: Number((workSchedule.workHalves.morning.hours / workSchedule.standardWorkHours).toFixed(2)) })
    const afternoon = corrected(before, { finalAfternoon: '出勤' })
    expect(summary(afternoon, {}, workSchedule)).toMatchObject({ missingCardCount: 1,
      actualAttendanceDays: Number((workSchedule.workHalves.afternoon.hours / workSchedule.standardWorkHours).toFixed(2)) })
  })

  it.each(['调休', '调休假'])('全天事假改%s：8小时转移一次，撤销后恢复原汇总', label => {
    const before = classified('事假8小时（08:30-18:00）')
    const source = { '事假(小时)': 8, [`${label}(小时)`]: 2 }
    const fixed = corrected(before, { finalMorning: '调休', finalAfternoon: '调休' })
    expect(summary(fixed, source)).toMatchObject({ personalLeaveHours: null, compLeaveHours: 10, actualAttendanceDays: 1 })
    expect(summary(corrected(fixed, { finalMorning: '事假', finalAfternoon: '事假' }), source)).toEqual(summary(before, source))
  })

  it('跨午休事假改调休：只转工作交集，不加入午休', () => {
    const before = classified('事假5.5小时（10:00-17:00）')
    expect(summary(corrected(before, { finalMorning: '调休', finalAfternoon: '调休' }), { '事假(小时)': 5.5 }))
      .toMatchObject({ personalLeaveHours: null, compLeaveHours: 5.5, actualAttendanceDays: 0.69 })
  })

  it.each(['事假', '调休', '年假', '加班'] as AttendanceStatus[])('%s 改出勤：保留源小时并提示人工核对（既有契约）', status => {
    const before = classified(`${status}8小时（08:30-18:00）`)
    const fixed = corrected(before, { finalMorning: '出勤', finalAfternoon: '出勤' })
    expect(summary(fixed, { '事假(小时)': 8, '调休假(小时)': 8, '年假(小时)': 8, '加班时长(小时)': 8 }))
      .toMatchObject({ personalLeaveHours: 8, compLeaveHours: 8, annualLeaveHours: 8, overtimeHours: 8 })
    expect(fixed.issues.some(issue => issue.message === SOURCE_SUMMARY_REVIEW_MESSAGE)).toBe(true)
  })

  it('午后迟到修正不清除上午迟到，全部修正再清除处罚', () => {
    const before = classified('正常;迟到20分钟-08:50;迟到10分钟-13:40')
    expect(summary(corrected(before, { finalAfternoon: '出勤' }))).toMatchObject({ lateText: '20分钟', latePunishment: '是：总时长20分钟>15分钟' })
    expect(summary(corrected(before, { finalMorning: '出勤', finalAfternoon: '出勤' }))).toMatchObject({ lateText: null, latePunishment: null })
  })

  it('修改月参数保留日修订和手工汇总、撤销确认，重复重建不累计', () => {
    const source = month('正常;缺卡(08:30)')
    const project = buildAttendanceProject(source, options)
    const employee = project.employees[0]
    employee.days[0] = corrected(employee.days[0], { finalMorning: '出勤', morningNote: '已核实' })
    employee.summary.actualAttendanceDays = 18.75
    employee.manualSummaryOverrides.actualAttendanceDays = true
    employee.confirmed = true
    const rebuilt = buildAttendanceProject(source, { ...options, payrollDays: 22 })
    const merged = mergeRebuiltProjectWithReviewState(rebuilt, project)
    expect(merged.employees[0]).toMatchObject({ confirmed: false, summary: { actualAttendanceDays: 18.75, payrollDays: 22 } })
    expect(merged.employees[0].days[0]).toMatchObject({ finalMorning: '出勤', morningNote: '已核实', modified: true })
    expect(mergeRebuiltProjectWithReviewState(rebuilt, merged).employees).toEqual(merged.employees)
  })

  it('重新选择部门班次保留已修正缺卡', () => {
    const fixed = corrected(classified('正常;缺卡(08:30)'), { finalMorning: '出勤' })
    const days = reclassifyDaysForSchedule([fixed], custom, '合成员工')
    expect(days[0].finalMorning).toBe('出勤')
    expect(summary(days[0], {}, custom).actualAttendanceDays).toBe(1)
  })

  it('正式报表续改按原值导入，月参数仅覆盖应出勤/计薪字段', () => {
    const original = { ...summary(classified('正常')), actualAttendanceDays: 18.75, personalLeaveHours: 2, remark: '合成人工核对' }
    const project = buildProjectFromExportedReport({ year: 2026, month: 6,
      employees: [{ name: '合成员工', department: '', sourceIndex: 1, days: [{ day: 1, morning: '缺卡', afternoon: '出勤' }], summary: original }] })
    expect(project.employees[0].summary).toEqual(original)
    expect(applyReimportMonthlyParameters(project, options).employees[0].summary)
      .toEqual({ ...original, shouldAttendanceDays: 21, payrollDays: 23 })
  })

  it.each([
    ['病假', 'sickLeaveDays'], ['工伤', 'injuryLeaveDays'], ['丧假', 'funeralLeaveDays'],
    ['婚假', 'marriageLeaveDays'], ['产假', 'maternityLeaveDays'],
  ] as const)('%s 仅修正上午，不清掉下午假期天数', (status, field) => {
    const before = classified(`${status}8小时（08:30-18:00）`)
    expect(summary(corrected(before, { finalMorning: '出勤' }))[field]).toBe(0.56)
  })

  it.each(['陪产假', '产检假', '带薪'] as const)('%s 当前不计入实出勤（口径疑点，不视为批准）', status => {
    const day = classified('')
    day.systemMorning = day.finalMorning = status
    day.systemAfternoon = day.finalAfternoon = status
    expect(summary(day).actualAttendanceDays).toBeNull()
    expect(summary(day).maternityLeaveDays).toBeNull()
  })

  it.each([
    [1, 15, null], [1, 16, '是：总时长16分钟>15分钟'],
    [2, 7, null], [3, 5, '是：迟到次数3次≥3次'],
    [3, 6, '是：迟到次数3次≥3次；总时长18分钟>15分钟'],
  ])('迟到处罚边界：%i次、每次%i分钟', (count, minutes, expected) => {
    const days = Array.from({ length: Number(count) }, (_, index) => ({ ...classified(`正常;迟到${minutes}分钟`), day: index + 1 }))
    const calculate = (items: DayReview[]) => recalculateSummary({ days: items, sourceSummaries: {}, ...options, entryDate: '' })
    expect(calculate(days).latePunishment).toBe(expected)
    expect(calculate(days.map(day => corrected(day, { finalMorning: '出勤' }))).latePunishment).toBeNull()
  })

  it.each(['08:30-12:00', '13:30-18:00'])('带明确半天时段的事假改调休仅转移该半天：%s', range => {
    const morning = range.startsWith('08')
    const hours = morning ? 3.5 : 4.5
    const before = classified(`正常;事假${hours}小时（${range}）`)
    const fixed = corrected(before, morning ? { finalMorning: '调休' } : { finalAfternoon: '调休' })
    expect(summary(fixed, { '事假(小时)': hours })).toMatchObject({ personalLeaveHours: null, compLeaveHours: hours, actualAttendanceDays: 1 })
  })

  it('没有正常打卡依据的短调休仅按已知小时计，不凭空补全天', () => {
    const before = classified('调休假1小时（09:00-10:00）')
    expect(summary(before).actualAttendanceDays).toBe(0.13)
  })

  it('仅改备注不会转移假期小时；恢复系统状态后核对提示移除', () => {
    const before = classified('事假8小时（08:30-18:00）')
    expect(summary(corrected(before, { morningNote: '已核对' }), { '事假(小时)': 8 }))
      .toEqual(summary(before, { '事假(小时)': 8 }))
    const fixed = corrected(before, { finalMorning: '调休' })
    const reverted = corrected(fixed, { finalMorning: '事假' })
    expect(reverted.issues.some(issue => issue.message === SOURCE_SUMMARY_REVIEW_MESSAGE)).toBe(false)
  })

  it.each([schedule, custom])('短事假改调休保留正常工时，包含加班覆盖；撤销恢复', workSchedule => {
    for (const overtime of ['', ';加班2小时（18:00-20:00）']) {
      const before = classified(`正常;事假1小时（14:00-15:00）${overtime}`, workSchedule)
      const source = { '事假(小时)': 1, '调休假(小时)': 2 }
      const fixed = corrected(before, { finalAfternoon: '调休' })
      expect(summary(fixed, source, workSchedule)).toMatchObject({ actualAttendanceDays: 1, personalLeaveHours: null, compLeaveHours: 3 })
      expect(summary(fixed, source, workSchedule)).toEqual(summary(JSON.parse(JSON.stringify(fixed)), source, workSchedule))
      expect(summary(corrected(fixed, { finalAfternoon: before.systemAfternoon }), source, workSchedule)).toEqual(summary(before, source, workSchedule))
    }
  })

  it('缺少正常底稿的短事假改调休不补造全天出勤', () => {
    const before = classified('事假1小时（09:00-10:00）')
    expect(summary(corrected(before, { finalMorning: '调休' }), { '事假(小时)': 1 }))
      .toMatchObject({ actualAttendanceDays: 0.13, compLeaveHours: 1, personalLeaveHours: null })
  })

  it.each([schedule, custom])('全天旷工任一半天纠正只保留另一半，全天纠正清零', workSchedule => {
    for (const raw of ['旷工480分钟', '旷工8小时（08:30-18:00）', '旷工']) {
      const before = classified(raw, workSchedule)
      for (const half of ['morning', 'afternoon'] as const) {
        const fixed = corrected(before, half === 'morning' ? { finalMorning: '出勤' } : { finalAfternoon: '出勤' })
        const remaining = half === 'morning' ? 'afternoon' : 'morning'
        expect(summary(fixed, {}, workSchedule).absenceDays).toBe(Number((workSchedule.workHalves[remaining].hours / workSchedule.standardWorkHours).toFixed(2)))
        expect(summary(corrected(fixed, { finalMorning: '出勤', finalAfternoon: '出勤' }), {}, workSchedule).absenceDays).toBeNull()
        expect(summary(corrected(fixed, { finalMorning: '旷工', finalAfternoon: '旷工' }), {}, workSchedule)).toEqual(summary(before, {}, workSchedule))
      }
    }
  })

  it.each([1, 2, 3, 8])('无时段合成边界：全天转换 %i 小时只迁移一次', hours => {
    const before = classified(`事假${hours}小时`)
    const source = { '事假(小时)': hours, '调休假(小时)': 2 }
    const fixed = corrected(before, { finalMorning: '调休', finalAfternoon: '调休' })
    expect(summary(fixed, source)).toMatchObject({ personalLeaveHours: null, compLeaveHours: hours + 2, actualAttendanceDays: Number((hours / 8).toFixed(2)) })
    expect(summary(JSON.parse(JSON.stringify(fixed)), source)).toEqual(summary(fixed, source))
    expect(summary(corrected(fixed, { finalMorning: '事假', finalAfternoon: '事假' }), source)).toEqual(summary(before, source))
  })

  it('无时段整日调休反向改事假同样只迁移一次', () => {
    const before = classified('调休假2小时')
    expect(summary(corrected(before, { finalMorning: '事假', finalAfternoon: '事假' }), { '调休假(小时)': 2 }))
      .toMatchObject({ compLeaveHours: null, personalLeaveHours: 2, actualAttendanceDays: null })
  })
})

// These are executable unresolved findings, not successful acceptance cases.
// it.fails requires the intended assertion to fail; remove it only when the rule is approved and fixed.
describe('审查发现回归（仅 it.fails 标记的项目尚未修复）', () => {
  it('A01 正常底稿短事假改调休后应恢复全天，而非丢失其余工作时间', () => {
    const before = classified('正常;事假1小时（09:00-10:00）')
    expect(summary(corrected(before, { finalMorning: '调休' }), { '事假(小时)': 1 }).actualAttendanceDays).toBe(1)
  })
  it('A02 全日旷工只纠正上午后，剩余旷工应为下午4.5小时', () => {
    expect(summary(corrected(classified('旷工480分钟'), { finalMorning: '出勤' })).absenceDays).toBe(0.56)
  })
  it.fails('A03 两种假别分别在上午下午时不能忽略其中一种', () => {
    const day = classified('事假3.5小时（08:30-12:00）;调休假4.5小时（13:30-18:00）')
    expect([day.systemMorning, day.systemAfternoon]).toEqual(['事假', '调休'])
  })
  it('A04 全天状态无定位的2小时事假改调休不能转出4小时', () => {
    const before = classified('事假2小时')
    expect(summary(corrected(before, { finalMorning: '调休', finalAfternoon: '调休' }), { '事假(小时)': 2 }).compLeaveHours).toBe(2)
  })
  it.fails('A05 上午外出不应自动认可下午地点异常', () => {
    const day = classified('正常;外出1小时（09:00-10:00）;地点异常-18:00')
    expect(day.systemAfternoon).toBe('异常')
  })
  it.fails('A06 中文括号缺卡必须识别', () => {
    expect(classified('正常;缺卡（08:30）').systemMorning).toBe('缺卡')
  })
  it.fails('A07 无时段缺卡不能静默变为空白且无警告', () => {
    const day = classified('缺卡')
    expect(day.issues.length).toBeGreaterThan(0)
  })
  it.fails('A08 上午早退应保留上午时段并统计分钟', () => {
    const day = classified('正常;早退30分钟-11:30')
    expect([day.systemMorning, day.systemAfternoon, summary(day).earlyText]).toEqual(['早退', '出勤', '30分钟'])
  })
  it.fails('A09 加班覆盖下午迟到时仍应保留迟到统计', () => {
    expect(summary(classified('正常;迟到20分钟-13:50;加班2小时（18:00-20:00）')).lateText).toBe('20分钟')
  })
  it.fails('A10 前端班次校验应拒绝25点而非等后端拒绝保存', () => {
    expect(validateDepartmentScheduleSettings({ default: { morning: { start: '25:00', end: '26:00' }, afternoon: { start: '27:00', end: '28:00' } }, departments: {} }).length).toBeGreaterThan(0)
  })
})
