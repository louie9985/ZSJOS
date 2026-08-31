import { describe, expect, it } from 'vitest'
import { theme } from 'antd'
import type { ConfigProviderProps } from 'antd'
import { withDensity } from './ThemeProvider'

/**
 * Density/font-scale overlay. The critical property is that `algorithm` is
 * *appended* to: the 11 hook presets each express their palette through
 * algorithm, so replacing it would silently strip their colours.
 */
const algorithmsOf = (config: ConfigProviderProps) => {
  const value = config.theme?.algorithm
  if (!value) return []
  return Array.isArray(value) ? value : [value]
}

describe('withDensity', () => {
  it('keeps a preset single-function algorithm and appends compact', () => {
    const preset: ConfigProviderProps = { theme: { algorithm: theme.darkAlgorithm } }
    const result = withDensity(preset, 'compact', 'default')
    const algorithms = algorithmsOf(result)
    expect(algorithms).toContain(theme.darkAlgorithm)
    expect(algorithms).toContain(theme.compactAlgorithm)
    expect(algorithms).toHaveLength(2)
  })

  it('keeps a preset algorithm array intact', () => {
    const preset: ConfigProviderProps = {
      theme: { algorithm: [theme.darkAlgorithm, theme.defaultAlgorithm] }
    }
    const result = withDensity(preset, 'compact', 'default')
    const algorithms = algorithmsOf(result)
    expect(algorithms.slice(0, 2)).toEqual([theme.darkAlgorithm, theme.defaultAlgorithm])
    expect(algorithms[2]).toBe(theme.compactAlgorithm)
  })

  it('does not add compactAlgorithm for loose or default density', () => {
    for (const density of ['loose', 'default'] as const) {
      const result = withDensity({ theme: { algorithm: theme.darkAlgorithm } }, density, 'default')
      expect(algorithmsOf(result), density).not.toContain(theme.compactAlgorithm)
    }
  })

  it('leaves algorithm absent when the preset defines none', () => {
    const result = withDensity({ theme: { token: { colorPrimary: '#f00' } } }, 'default', 'default')
    expect(result.theme?.algorithm).toBeUndefined()
  })

  it('injects the font size matching the scale', () => {
    expect(withDensity({}, 'default', 'small').theme?.token?.fontSize).toBe(12)
    expect(withDensity({}, 'default', 'default').theme?.token?.fontSize).toBe(13)
    expect(withDensity({}, 'default', 'large').theme?.token?.fontSize).toBe(14)
  })

  it('preserves preset tokens and non-theme config while overlaying', () => {
    const preset: ConfigProviderProps = {
      componentSize: 'small',
      theme: { token: { colorPrimary: '#ed4192', borderRadius: 2 } }
    }
    const result = withDensity(preset, 'compact', 'large')
    expect(result.componentSize).toBe('small')
    expect(result.theme?.token?.colorPrimary).toBe('#ed4192')
    expect(result.theme?.token?.borderRadius).toBe(2)
    expect(result.theme?.token?.fontSize).toBe(14)
  })

  it('does not mutate the input config', () => {
    const preset: ConfigProviderProps = { theme: { algorithm: theme.darkAlgorithm } }
    withDensity(preset, 'compact', 'large')
    expect(preset.theme?.algorithm).toBe(theme.darkAlgorithm)
    expect(preset.theme?.token).toBeUndefined()
  })
})
