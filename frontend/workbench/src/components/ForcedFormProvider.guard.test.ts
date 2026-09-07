import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceNotToContainTokens, expectSourceToContainTokens } from '../test/sourceGuard'

const source = readFileSync(new URL('./ForcedFormProvider.tsx', import.meta.url), 'utf8')

describe('forced form gate visibility guard', () => {
  it('keeps background pending polling silent until a real pending form exists', () => {
    expectSourceToContainTokens(source, 'const open = Boolean(current)')
    expectSourceNotToContainTokens(source, 'const open = Boolean(current) || loading')
    expectSourceNotToContainTokens(source, 'const open = Boolean(current) || Boolean(error)')
  })
})
