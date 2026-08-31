import { describe, expect, it } from 'vitest'
import { normalizeDrawerWidth, readDrawerWidth } from './ResizableDrawer'

describe('ResizableDrawer', () => {
  it('applies each drawer minimum while respecting the viewport maximum', () => {
    expect(normalizeDrawerWidth(300, 1200, 420)).toBe(420)
    expect(normalizeDrawerWidth(700, 1200, 420)).toBe(700)
    expect(normalizeDrawerWidth(1400, 1200, 420)).toBe(1104)
  })

  it('reads a drawer-specific persisted width', () => {
    expect(readDrawerWidth({ getItem: () => 'not-a-number' }, 'advanced', 1200, 420)).toBeUndefined()
    expect(readDrawerWidth({ getItem: () => '560' }, 'advanced', 1200, 420)).toBe(560)
  })
})
