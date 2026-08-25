import { describe, expect, it } from 'vitest'
import { formatTimestamp } from './time'

describe('ZSJOS timestamp contract', () => {
  it('formats list timestamps to minutes in the product timezone', () => {
    expect(formatTimestamp(Date.UTC(2026, 7, 9, 6, 24, 52)))
      .toBe('2026-08-09 14:24')
  })

  it('supports date and second precision when the context requires it', () => {
    const timestamp = Date.UTC(2026, 7, 9, 6, 24, 52)
    expect(formatTimestamp(timestamp, '-', 'date')).toBe('2026-08-09')
    expect(formatTimestamp(timestamp, '-', 'second')).toBe('2026-08-09 14:24:52')
    expect(formatTimestamp('2026-08-09', '-', 'date')).toBe('2026-08-09')
    expect(formatTimestamp('2026-08-09 14:24:52', '-', 'second')).toBe('2026-08-09 14:24:52')
  })

  it('uses the requested empty text', () => {
    expect(formatTimestamp(undefined, '无截止时间')).toBe('无截止时间')
    expect(formatTimestamp(Number.NaN, '时间无效')).toBe('时间无效')
  })
})
