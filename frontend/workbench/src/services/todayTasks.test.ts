import { describe, expect, it } from 'vitest'
import { canQueryBpmTasks } from '../pages/TodayTasksPage'

describe('today task permissions', () => {
  it('does not load BPM tasks for a business-task-only user', () => {
    expect(canQueryBpmTasks(['zsjos:business-task:query'])).toBe(false)
  })

  it('loads BPM tasks only when the backend grants BPM task query', () => {
    expect(canQueryBpmTasks(['zsjos:business-task:query', 'bpm:task:query'])).toBe(true)
  })
})
