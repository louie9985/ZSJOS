import dayjs, { type Dayjs } from 'dayjs'

export type ClassDatePreset = 'all' | 'month' | 'next' | '30' | '90' | 'custom'
export function classDateRange(preset: ClassDatePreset, now = new Date()): [string, string] | undefined {
  const today = dayjs(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now))
  let start: Dayjs, end: Dayjs
  switch (preset) {
    case 'month': start = today.startOf('month'); end = today.endOf('month'); break
    case 'next': start = today.add(1, 'month').startOf('month'); end = start.endOf('month'); break
    case '30': start = today; end = today.add(29, 'day'); break
    case '90': start = today; end = today.add(89, 'day'); break
    default: return undefined
  }
  return [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
}
