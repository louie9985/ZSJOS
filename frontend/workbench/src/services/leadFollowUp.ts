export const appendQuickNote = (current: string, note: string) => current.trim() ? `${current.trim()} ${note}` : note

export const addFollowUpDays = (now: Date, days: number) => {
  const result = new Date(now)
  result.setDate(result.getDate() + days)
  return result
}

export const shouldBlockLeadSwitch = (dirty: boolean) => dirty

export type LeadDetailMode = 'all' | 'submitter' | 'owner' | 'manager-readonly'
export type LeadDetailTab = 'overview' | 'follow-ups' | 'orders' | 'appeals' | 'complaints'

export const detailTabsForMode = (mode: LeadDetailMode): LeadDetailTab[] => {
  const base: LeadDetailTab[] = ['overview', 'follow-ups']
  if (mode === 'owner') return [...base, 'orders']
  if (mode === 'submitter') return [...base, 'appeals']
  if (mode === 'manager-readonly') return [...base, 'appeals', 'complaints', 'orders']
  return base
}

export const shouldShowLeadOrderTab = (mode: LeadDetailMode) => detailTabsForMode(mode).includes('orders')

export const defaultLeadDetailTab = (openFollowUp: boolean): LeadDetailTab =>
  openFollowUp ? 'follow-ups' : 'overview'

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
