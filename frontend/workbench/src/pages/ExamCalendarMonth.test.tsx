import { isValidElement, type ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import dayjs from 'dayjs'
import { describe, expect, it, vi } from 'vitest'
import ExamCalendarMonth from './ExamCalendarMonth'
import { ExamCalendarLegend, scheduleStatusLabel } from './ExamCalendarPage'
import type { ExamSchedule } from '../services/api'

const exact = (id: number): ExamSchedule => ({ id, scheduleType: 'EXACT', exactDate: '2026-10-10',
  scheduleName: '完整考期名称' + id, remark: '备注只在详情显示', recordStatus: 'DRAFT', displayStatus: 'DRAFT', categoryPathSnapshot: [] })
const multi = (id: number): ExamSchedule => ({ ...exact(id), scheduleType: 'MULTI_DAY',
  startDate: '2026-10-09', endDate: '2026-10-12' })

function nodes(node: ReactNode): Array<{ type: unknown; props: Record<string, unknown> }> {
  if (Array.isArray(node)) return node.flatMap(nodes)
  if (!isValidElement<Record<string, unknown>>(node)) return []
  return [{ type: node.type, props: node.props }, ...nodes(node.props.children as ReactNode)]
}

describe('full-name exam month', () => {
  it.each([1, 3, 4, 10, 110])('renders all %i single-day records with no overflow entry or status tags', count => {
    const html = renderToStaticMarkup(<ExamCalendarMonth anchor={dayjs('2026-10-01')}
      exactRows={Array.from({ length: count }, (_, i) => exact(i))} multiDayRows={[]}
      onDay={() => {}} statusLabel={scheduleStatusLabel} />)
    expect(html.match(/class="exam-calendar-event /g)).toHaveLength(count)
    expect(html).not.toContain('另有')
    expect(html).not.toContain('ant-tag')
    expect(html).not.toContain('备注只在详情显示')
  })

  it('retains full long names in every cross-week segment without rendering extra detail text', () => {
    const rows = Array.from({ length: 6 }, (_, i) => ({ ...multi(i), scheduleName: '考试完整名称'.repeat(16) + i }))
    const html = renderToStaticMarkup(<ExamCalendarMonth anchor={dayjs('2026-10-01')}
      exactRows={[]} multiDayRows={rows} onDay={() => {}} statusLabel={scheduleStatusLabel} />)
    expect(html.match(/class="exam-multiDay-bar /g)).toHaveLength(12)
    for (const row of rows) expect(html.split('<span class="exam-multiDay-bar-label">' + row.scheduleName)).toHaveLength(3)
    expect(html).not.toContain('多日 +')
    expect(html).not.toContain('ant-tag')
    expect(html).not.toContain('exam-countdown')
  })

  it('activates each date/event/segment once and stops bubbling to its day cell', () => {
    const onDay = vi.fn()
    const tree = ExamCalendarMonth({ anchor: dayjs('2026-10-01'), exactRows: [exact(1)], multiDayRows: [multi(2)], onDay, statusLabel: scheduleStatusLabel })
    const elements = nodes(tree)
    const date = elements.find(node => node.props['aria-label'] === '2026年10月10日考期安排')!
    const event = elements.find(node => String(node.props.className).startsWith('exam-calendar-event '))!
    const segment = elements.find(node => node.type === 'button' && node.props['data-date'] === '2026-10-10')!
    for (const node of [date, event, segment]) {
      onDay.mockClear()
      const stopPropagation = vi.fn()
      ;(node.props.onClick as (event: unknown) => void)({ stopPropagation })
      expect(stopPropagation).toHaveBeenCalledOnce()
      expect(onDay).toHaveBeenCalledOnce()
      expect(onDay.mock.calls[0][0].format('YYYY-MM-DD')).toBe('2026-10-10')
    }
  })

  it('explains only publication colors and the independent multi-day type', () => {
    const html = renderToStaticMarkup(<ExamCalendarLegend />)
    for (const label of ['已撤销', '已发布', '草稿', '多日考试']) expect(html).toContain(label)
    for (const status of ['revoked', 'published', 'draft']) expect(html).toContain('tone-' + status)
  })
  it.each(['PUBLISHED', 'UPCOMING', 'IN_PROGRESS', 'ENDED'] as const)('uses published color and accessible label for %s in both exam types', displayStatus => {
    const single = { ...exact(1), recordStatus: 'PUBLISHED' as const, displayStatus }
    const range = { ...multi(2), recordStatus: 'PUBLISHED' as const, displayStatus }
    const html = renderToStaticMarkup(<ExamCalendarMonth anchor={dayjs('2026-10-01')}
      exactRows={[single]} multiDayRows={[range]} onDay={() => {}} statusLabel={scheduleStatusLabel} />)
    expect(html.match(/exam-status-tone tone-published/g)).toHaveLength(3)
    for (const tone of ['ended', 'upcoming', 'in_progress']) expect(html).not.toContain('tone-' + tone)
    for (const label of ['即将开始', '正在进行', '已结束']) expect(html).not.toContain(label)
    expect(html).toContain('已发布')
  })

})
