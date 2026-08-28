import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { APP_ROUTES, RENDERABLE_APP_ROUTES } from '../constants'
import { canQueryBpmTasks } from '../pages/TodayTasksPage'

const source = readFileSync(new URL('../pages/TodayTasksPage.tsx', import.meta.url), 'utf8')

describe('today task permissions', () => {
  it('does not load BPM tasks for a business-task-only user', () => {
    expect(canQueryBpmTasks(['zsjos:business-task:query'])).toBe(false)
  })

  it('shows the BPM todo summary only when the backend grants BPM task query', () => {
    expect(canQueryBpmTasks(['zsjos:business-task:query', 'bpm:task:query'])).toBe(true)
  })

  it('keeps BPM approval handling in the workflow todo route', () => {
    expect(source).toContain('未完成审批')
    expect(source).toContain('APP_ROUTES.BPM_TODO')
    expect(source).not.toContain('approveBpmTask')
    expect(source).not.toContain('rejectBpmTask')
    expect(RENDERABLE_APP_ROUTES.has(APP_ROUTES.BPM_TODO)).toBe(true)
  })
})
