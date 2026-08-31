import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync(new URL('./ForcedFormProvider.tsx', import.meta.url), 'utf8')

describe('forced form gate visibility guard', () => {
  it('keeps background pending polling silent until a real pending form exists', () => {
    expect(source).toContain('const open = Boolean(current)')
    expect(source).not.toContain('const open = Boolean(current) || loading')
    expect(source).not.toContain('const open = Boolean(current) || Boolean(error)')
  })
})
