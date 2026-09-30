import type { ExamSchedule, PageResult } from './api'

/** Anchor each response once to a monotonic clock, independent of the computer's wall clock. */
export async function withExamClock(fetch: () => Promise<PageResult<ExamSchedule>>): Promise<PageResult<ExamSchedule>> {
  const started = performance.now()
  const page = await fetch()
  return { ...page, list: page.list.map(row => ({ ...row,
    reeditExpiresAt: row.reeditDeadline != null && row.serverTime != null
      ? started + Number(row.reeditDeadline) - Number(row.serverTime) : undefined
  })) }
}

export function isExamVisible(row: ExamSchedule, now: number): boolean {
  return row.recordStatus !== 'REVOKED' || (row.reeditExpiresAt != null && row.reeditExpiresAt > now)
}

export function reeditCountdown(row: ExamSchedule, now: number): string {
  const seconds = Math.max(0, Math.ceil(((row.reeditExpiresAt ?? now) - now) / 1000))
  return String(Math.floor(seconds / 60)).padStart(2, '0') + ':' + String(seconds % 60).padStart(2, '0')
}
