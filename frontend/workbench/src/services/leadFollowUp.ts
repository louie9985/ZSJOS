import dayjs, { type Dayjs } from 'dayjs'

export type FollowUpTimeShortcut = {
  key: string
  label: string
  amount: number
  unit: 'minute' | 'day'
}

export const FOLLOW_UP_TIME_SHORTCUTS: FollowUpTimeShortcut[] = [
  { key: '30-minutes', label: '+30 分钟', amount: 30, unit: 'minute' },
  { key: '1-day', label: '+1 天', amount: 1, unit: 'day' },
  { key: '2-days', label: '+2 天', amount: 2, unit: 'day' },
  { key: '3-days', label: '+3 天', amount: 3, unit: 'day' },
  { key: '5-days', label: '+5 天', amount: 5, unit: 'day' },
  { key: '7-days', label: '+7 天', amount: 7, unit: 'day' },
  { key: '14-days', label: '+14 天', amount: 14, unit: 'day' },
  { key: '25-days', label: '+25 天', amount: 25, unit: 'day' },
  { key: '30-days', label: '+30 天', amount: 30, unit: 'day' },
]

export const applyFollowUpTimeShortcut = (
  shortcut: FollowUpTimeShortcut,
  now: Dayjs = dayjs(),
) => now.add(shortcut.amount, shortcut.unit)

export const appendQuickNote = (current: string, note: string) => current.trim() ? `${current.trim()} ${note}` : note

export const addFollowUpDays = (now: Date, days: number) => {
  const result = new Date(now)
  result.setDate(result.getDate() + days)
  return result
}

export const shouldBlockLeadSwitch = (dirty: boolean) => dirty

export type LeadDetailMode = 'all' | 'submitter' | 'owner' | 'manager-readonly' | 'student-readonly'
export type LeadDetailTab = 'overview' | 'follow-ups' | 'orders' | 'appeals' | 'complaints' | 'flow-history' | 'submitter-feedback' | 'student-info'

const LEAD_DETAIL_TABS: LeadDetailTab[] = ['overview', 'follow-ups', 'orders', 'appeals', 'complaints', 'flow-history', 'submitter-feedback', 'student-info']

export const parseLeadDetailTab = (value?: string | null): LeadDetailTab | undefined =>
  LEAD_DETAIL_TABS.includes(value as LeadDetailTab) ? value as LeadDetailTab : undefined

export const detailTabsFromProjection = (tabs?: LeadDetailTab[]): LeadDetailTab[] =>
  tabs?.length ? Array.from(new Set(tabs)) : ['overview']

export const resolveVisibleLeadDetailTabs = (
  baseTabs: LeadDetailTab[] | undefined,
  projectedTabs: LeadDetailTab[] | undefined,
): LeadDetailTab[] => baseTabs
  ? Array.from(new Set([
      ...baseTabs,
      ...(projectedTabs?.includes('student-info') ? ['student-info' as const] : []),
    ]))
  : detailTabsFromProjection(projectedTabs)

export const shouldShowLeadOrderTab = (tabs?: LeadDetailTab[]) => detailTabsFromProjection(tabs).includes('orders')

export const defaultLeadDetailTab = (openFollowUp: boolean): LeadDetailTab =>
  openFollowUp ? 'follow-ups' : 'overview'

export const resolveLeadDetailTab = (tabs: LeadDetailTab[], requested?: LeadDetailTab): LeadDetailTab =>
  requested && tabs.includes(requested) ? requested : 'overview'

/* ==================== 跟进记录蛇形时间线 ==================== */

const SNAKE_BREAKPOINT_TWO_COL = 500
const SNAKE_BREAKPOINT_THREE_COL = 800

/** Column count for the snake grid at a given container width. */
export const snakeColumnsForWidth = (width: number) =>
  width <= SNAKE_BREAKPOINT_TWO_COL ? 1 : width <= SNAKE_BREAKPOINT_THREE_COL ? 2 : 3

/** Splits records into rows of `cols`; the trailing row may be short. */
export const chunkSnakeRows = <T,>(items: T[], cols: number): T[][] => {
  if (cols < 1) return items.length ? [items] : []
  const rows: T[][] = []
  for (let index = 0; index < items.length; index += cols) rows.push(items.slice(index, index + cols))
  return rows
}

/**
 * A reversed row reads right-to-left, so its trailing card sits in column 1 and
 * the next row's connector must drop from there; a forward row ends in the last
 * column. Single-column grids never reverse.
 */
export const snakeRowReversed = (rowIndex: number, cols: number) => cols > 1 && rowIndex % 2 === 1

/** Local method/result filter over the pages loaded so far. */
export const filterFollowUps = <T extends { method: string; result: string }>(
  records: T[], method?: string, result?: string
) => records.filter(record => (!method || record.method === method) && (!result || record.result === result))
