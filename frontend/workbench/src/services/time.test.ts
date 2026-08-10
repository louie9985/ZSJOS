import { describe, expect, it } from 'vitest'
import { formatTimestamp } from './time'

describe('ZSJOS timestamp contract', () => {
  it('formats epoch milliseconds in the product timezone', () => {
    expect(formatTimestamp(Date.UTC(2026, 7, 9, 6, 24, 52)))
      .toBe('2026-08-09 14:24:52')
  })

  it('uses the requested empty text', () => {
    expect(formatTimestamp(undefined, '无截止时间')).toBe('无截止时间')
    expect(formatTimestamp(Number.NaN, '时间无效')).toBe('时间无效')
  })
})
