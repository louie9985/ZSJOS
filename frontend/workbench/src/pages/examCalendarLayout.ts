import type { Dayjs } from 'dayjs'
import type { ExamSchedule } from '../services/api'

export const calendarWindow = (anchor: Dayjs) => {
  const start = anchor.startOf('month').startOf('week')
  return { start, end: start.add(41, 'day') }
}

export const schedulesForRecordStatus = (rows: ExamSchedule[], status?: ExamSchedule['recordStatus']) =>
  rows.filter(row => !status || row.recordStatus === status)

export const coversExamDay = (row: ExamSchedule, date: string) =>
  row.scheduleType === 'EXACT' ? row.exactDate === date
    : Boolean(row.startDate && row.endDate && row.startDate <= date && date <= row.endDate)

export type MultiDaySegment = {
  schedule: ExamSchedule
  startColumn: number
  endColumn: number
  lane: number
  continuesBefore: boolean
  continuesAfter: boolean
}

export function layoutMultiDayWeek(rows: ExamSchedule[], weekStart: Dayjs) {
  const days = Array.from({ length: 7 }, (_, i) => weekStart.add(i, 'day').format('YYYY-MM-DD'))
  const laneEnds: number[] = []
  const segments: MultiDaySegment[] = []
  const candidates = rows.filter(row => row.scheduleType === 'MULTI_DAY' && row.startDate && row.endDate
    && row.startDate <= row.endDate && row.startDate <= days[6] && row.endDate >= days[0])
    .sort((a, b) => a.startDate!.localeCompare(b.startDate!)
      || b.endDate!.localeCompare(a.endDate!) || a.id - b.id)
  for (const schedule of candidates) {
    const covered = days.map((day, i) => coversExamDay(schedule, day) ? i : -1).filter(i => i >= 0)
    const startColumn = covered[0], endColumn = covered[covered.length - 1]
    let lane = laneEnds.findIndex(end => end < startColumn)
    if (lane === -1) lane = laneEnds.length
    laneEnds[lane] = endColumn
    segments.push({ schedule, startColumn, endColumn, lane,
      continuesBefore: schedule.startDate! < days[0],
      continuesAfter: schedule.endDate! > days[6] })
  }
  return { segments, laneCount: laneEnds.length }
}
