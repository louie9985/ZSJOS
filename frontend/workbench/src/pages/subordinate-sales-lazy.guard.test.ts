import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./SubordinateSalesPage.tsx', import.meta.url), 'utf8')
const mainSource = readFileSync(new URL('../main.tsx', import.meta.url), 'utf8')
const todayTasksSource = readFileSync(new URL('./TodayTasksPage.tsx', import.meta.url), 'utf8')
const dispatchControlSource = readFileSync(new URL('../components/SalesDispatchStatusControl.tsx', import.meta.url), 'utf8')

describe('subordinate sales lazy-list contract', () => {
  it('uses the shared append sentinel pattern and no pagination control', () => {
    expect(source).toContain('new IntersectionObserver')
    expect(source).toContain('rootMargin: "240px 0px"')
    expect(source).toContain('appendSubordinateSalesRows')
    expect(source).toContain('subordinate-sales-load-sentinel')
    expect(source).not.toContain('<Pagination')
  })

  it('guards the server-owned one-click command with its permission', () => {
    expect(source).toContain('zsjos:subordinate-sales:pause-all')
    expect(source).toContain('api.pauseAllSubordinateDispatch()')
    expect(source).toContain('包括停用账号')
  })

  it('shares one dispatch-status provider between the header and home page', () => {
    expect(mainSource).toContain('<SalesDispatchStatusProvider')
    expect(dispatchControlSource).toContain('useSalesDispatchStatus()')
    expect(mainSource).toContain('<SalesDispatchStatusAlert />')
    expect(todayTasksSource).not.toContain('SalesDispatchStatusAlert')
    expect(dispatchControlSource).toContain("color={status?.mode === 'accepting' ? 'success' : 'error'}")
    expect(dispatchControlSource).toContain("color={pageActive ? 'processing' : 'error'}")
  })
})
