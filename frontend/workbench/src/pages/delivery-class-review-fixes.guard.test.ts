import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync('src/pages/DeliveryClassPage.tsx', 'utf8')

describe('delivery class review fixes', () => {
  it('uses the established service-relation route state for student deep links', () => {
    expect(source).toContain("navigate(APP_ROUTES.MY_STUDENTS, { state: { serviceRelationId: row.serviceRelationId } })")
    expect(source).not.toContain('?serviceRelationId=')
  })

  it('paginates class students and retries the current detail request', () => {
    expect(source).toContain('studentTotal')
    expect(source).toContain('onChange: page => void loadStudents(selected, page)')
    expect(source).toContain('loadStudents(selected, studentPageNo)')
  })

  it('clears transfer options and blocks submit after an option-loading failure', () => {
    expect(source).toContain("setTransferLoading(true); setTransferError(''); setTransferOptions([])")
    expect(source).toContain('disabled: transferLoading || Boolean(transferError)')
    expect(source).toContain('disabled={transferLoading || Boolean(transferError)}')
  })
})
