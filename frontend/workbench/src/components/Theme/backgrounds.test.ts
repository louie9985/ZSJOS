import { describe, expect, it } from 'vitest'
import { BACKGROUND_METAS, type BackgroundKey } from '../../constants'

/**
 * Ensure every background gradient is low-chroma and properly typed.
 */

/**
 * Chroma = (max - min) in 0–1 RGB space.
 * Better than HSL saturation for near-white colors where HSL inflates values.
 */
function hexChroma(hex: string): number {
  const r = parseInt(hex.slice(1, 3), 16) / 255
  const g = parseInt(hex.slice(3, 5), 16) / 255
  const b = parseInt(hex.slice(5, 7), 16) / 255
  return Math.max(r, g, b) - Math.min(r, g, b)
}

/** Extract all hex colors from a CSS gradient string */
function extractHexColors(gradient: string): string[] {
  return [...gradient.matchAll(/#[0-9a-fA-F]{6}/g)].map(m => m[0])
}

describe('BACKGROUND_METAS consistency', () => {
  it('has at least one light and one dark background', () => {
    const light = BACKGROUND_METAS.filter(b => b.dark === false)
    const dark = BACKGROUND_METAS.filter(b => b.dark === true)
    expect(light.length).toBeGreaterThanOrEqual(3)
    expect(dark.length).toBeGreaterThanOrEqual(2)
  })

  it('every non-theme item has a dark field', () => {
    const missing = BACKGROUND_METAS
      .filter(b => b.key !== 'theme')
      .filter(b => b.dark === undefined)
    expect(missing.map(b => b.key)).toEqual([])
  })

  it('every item has a unique key matching BackgroundKey type', () => {
    const keys = BACKGROUND_METAS.map(b => b.key)
    expect(new Set(keys).size).toBe(keys.length)
    // Type assertion: if this compiles, the keys are all valid BackgroundKey
    const _typeCheck: BackgroundKey[] = keys
    expect(_typeCheck).toBeDefined()
  })

  it('light gradients have moderate chroma (< 20%)', () => {
    const offenders: string[] = []
    for (const bg of BACKGROUND_METAS.filter(b => b.dark === false)) {
      const colors = extractHexColors(bg.value ?? '')
      for (const color of colors) {
        const chroma = hexChroma(color)
        if (chroma > 0.20) offenders.push(`${bg.key}: ${color} chroma=${(chroma * 100).toFixed(1)}%`)
      }
    }
    expect(offenders).toEqual([])
  })

  it('dark gradients are genuinely dark (lightness < 25%)', () => {
    const offenders: string[] = []
    for (const bg of BACKGROUND_METAS.filter(b => b.dark === true)) {
      const colors = extractHexColors(bg.value ?? '')
      for (const color of colors) {
        const r = parseInt(color.slice(1, 3), 16) / 255
        const g = parseInt(color.slice(3, 5), 16) / 255
        const b2 = parseInt(color.slice(5, 7), 16) / 255
        const l = (Math.max(r, g, b2) + Math.min(r, g, b2)) / 2
        if (l > 0.25) offenders.push(`${bg.key}: ${color} lightness=${(l * 100).toFixed(1)}%`)
      }
    }
    expect(offenders).toEqual([])
  })
})
