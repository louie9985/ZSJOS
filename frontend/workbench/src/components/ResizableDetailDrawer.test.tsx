import { describe, expect, it } from 'vitest'
import { normalizeDetailDrawerWidth, readDetailDrawerWidth } from './ResizableDetailDrawer'

describe('ResizableDetailDrawer', () => {
  it('keeps persisted widths within desktop viewport limits', () => {
    expect(normalizeDetailDrawerWidth(400, 1200)).toBe(640)
    expect(normalizeDetailDrawerWidth(900, 1200)).toBe(900)
    expect(normalizeDetailDrawerWidth(1400, 1200)).toBe(1104)
  })

  it('ignores malformed persisted widths', () => {
    expect(readDetailDrawerWidth({ getItem: () => 'not-a-number' }, 1200)).toBeUndefined()
    expect(readDetailDrawerWidth({ getItem: () => '900' }, 1200)).toBe(900)
  })
})
