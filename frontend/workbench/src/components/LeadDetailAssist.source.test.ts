import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const detail = readFileSync(new URL('./LeadDetail.tsx', import.meta.url), 'utf8')
const api = readFileSync(new URL('../services/api.ts', import.meta.url), 'utf8')
const todayTasks = readFileSync(new URL('../pages/TodayTasksPage.tsx', import.meta.url), 'utf8')

describe('lead submitter assistance request', () => {
  it('keeps the projected action available in normal and manager detail modes', () => {
    expect(detail).toContain("item.code === 'REQUEST_SUBMITTER_ASSIST'")
    expect(detail).toContain("actions.has('REQUEST_SUBMITTER_ASSIST')")
    expect(detail).toContain("label: '请求提交人协助'")
  })

  it('requires the two business text fields and uploads optional attachments before submit', () => {
    expect(detail).toContain('遇到的问题')
    expect(detail).toContain('希望协助方式')
    expect(detail).toContain('if (!assistProblem.trim() || !assistExpected.trim())')
    expect(detail).toContain('uploadDeferredFiles(assistAttachments, api.uploadLeadAttachment')
    expect(detail).toContain('api.requestLeadSubmitterAssist')
  })

  it('keeps the API and todo deep-link protocol aligned', () => {
    expect(api).toContain('/submitter-assist-request`')
    expect(api).toContain('"OPEN_LEAD_SUBMITTER_ASSIST"')
    expect(todayTasks).toContain("task.actionCode === 'OPEN_LEAD_SUBMITTER_ASSIST'")
  })
})
