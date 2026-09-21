import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('draft recovery entry wiring', () => {
  const page = readFileSync('src/pages/MediaStudentsPage.tsx', 'utf8')
  it('loads the original card template and a fresh precheck context on every open', () => {
    expect(page).toContain('publishedTemplate(existingCard?.templateId)')
    const precheck = page.slice(page.indexOf("if (type === 'precheck')"), page.indexOf("if (type === 'positioning') {\n      positioningDraft"))
    expect(precheck).toContain('await api.studentContactContext(selectedService.serviceRelationId)')
    expect(precheck).not.toContain('let activeContext = directorContext')
  })
  it('retains a created batch identity before submission and uses saved draft revisions', () => {
    expect(page).toContain('contentDraft.current = { id, fingerprint }')
    expect(page).toContain('contentReviewApi.saveStudentDraft(contentDraft.current.id, draftRequest)')
    expect(page.indexOf('contentDraft.current = { id, fingerprint }')).toBeLessThan(page.indexOf('await contentReviewApi.submit(batch.id, batch.version)'))
    expect(page).toContain('StudentContentDraftPicker')
  })
})
