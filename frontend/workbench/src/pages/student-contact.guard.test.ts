import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const page = fs.readFileSync(path.resolve(__dirname, 'RegistrationPages.tsx'), 'utf8')

describe('student contact shortcuts', () => {
  it('keeps quick notes configurable and exposes every supported next-contact shortcut', () => {
    expect(page).toContain('draft.quickNotes')
    expect(page).toContain('新增快捷备注')
    expect(page).toContain('student-contact-time-shortcuts')
    expect(page).toContain('[1, 2, 3, 5, 7, 14, 30]')
  })
})
