import { describe, expect, it } from 'vitest'
import type { StudentContactFormField } from './api'
import {
  mergePositioningJsonValues,
  parsePositioningJson,
  serializePositioningFormValues,
} from './positioningJsonImport'

const field = (key: string, type: StudentContactFormField['type'], extra: Partial<StudentContactFormField> = {}): StudentContactFormField => ({
  key, title: key, type, required: false, enabled: true, systemField: false, sort: 1, ...extra,
})

const context = {
  fields: [
    field('story', 'text', { maxLength: 5 }),
    field('score', 'number', { minValue: 0, maxValue: 100 }),
    field('enabled', 'checkbox'),
    field('category', 'radio', { dictType: 'category' }),
    field('tags', 'checkbox_group', { dictType: 'tags', minSelections: 1, maxSelections: 2 }),
    field('day', 'date'),
    field('time', 'datetime'),
    field('region', 'region'),
    field('proof', 'attachment'),
  ],
  dictionaryValues: { category: ['expert'], tags: ['a', 'b'] },
  areaCodes: [110101],
}

describe('positioning JSON import', () => {
  it('parses compatible values and keeps dictionary codes', () => {
    const result = parsePositioningJson(JSON.stringify({ story: '经历', score: 85, enabled: true, category: 'expert', tags: ['a'] }), context)
    expect(result.values).toEqual({ story: '经历', score: 85, enabled: true, category: 'expert', tags: ['a'] })
    expect(result.skipped).toEqual([])
  })

  it.each(['{', '[]', 'null'])('rejects an invalid root: %s', raw => {
    expect(() => parsePositioningJson(raw, context)).toThrow()
  })

  it('skips unknown keys, invalid types, labels and constraints independently', () => {
    const result = parsePositioningJson(JSON.stringify({
      unknown: 'x', story: 'too long', score: 101, enabled: 'true', category: '专家型', tags: [],
    }), context)
    expect(result.importable).toEqual([])
    expect(result.skipped.map(item => item.key)).toEqual(['unknown', 'story', 'score', 'enabled', 'category', 'tags'])
  })

  it('normalizes date, datetime and region code values', () => {
    const result = parsePositioningJson(JSON.stringify({ day: '2026-08-27', time: '2026-08-27T10:30:00+08:00', region: { code: '110101' } }), context)
    expect(result.values).toEqual({ day: '2026-08-27', time: '2026-08-27T10:30:00+08:00', region: { code: 110101 } })
    expect(parsePositioningJson(JSON.stringify({ day: '2026-02-30', time: '2026-02-30T25:70:00', region: { code: 999999 } }), context).skipped).toHaveLength(3)
  })

  it('rejects attachments and treats null as an explicit clear for ordinary fields', () => {
    const result = parsePositioningJson(JSON.stringify({ story: null, proof: 'base64-data', tags: [] }), context)
    expect(result.clearKeys).toEqual(['story'])
    expect(result.skipped.map(item => item.key)).toEqual(['proof', 'tags'])
  })

  it('merges only accepted fields and preserves omitted or skipped values', () => {
    const preview = parsePositioningJson(JSON.stringify({ story: null, score: 90, category: 'invalid' }), context)
    expect(mergePositioningJsonValues({ story: '旧值', score: 60, category: 'expert', enabled: true }, preview)).toEqual({
      score: 90, category: 'expert', enabled: true,
    })
  })

  it('serializes date controls and region paths for the existing draft API', () => {
    const date = { format: (pattern: string) => pattern === 'YYYY-MM-DD' ? '2026-08-27' : '' }
    const time = { format: (pattern: string) => pattern === 'YYYY-MM-DDTHH:mm:ss' ? '2026-08-27T10:30:00' : '' }
    expect(serializePositioningFormValues({ day: date, time, region: [110000, 110100, 110101], story: '保留' }, context.fields)).toEqual({
      day: '2026-08-27', time: '2026-08-27T10:30:00', region: { code: 110101 }, story: '保留',
    })
  })
})
