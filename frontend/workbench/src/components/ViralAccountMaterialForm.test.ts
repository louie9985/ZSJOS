import { describe, expect, it } from 'vitest'
import type { MaterialFieldDefinition } from '../services/materialApi'
import { buildViralAccountLayout } from './ViralAccountMaterialForm'

const field = (key: string, sort: number, section?: string, group?: string): MaterialFieldDefinition => ({
  key,
  label: key,
  type: 'text',
  sort,
  section: section as MaterialFieldDefinition['section'],
  group
})

describe('buildViralAccountLayout', () => {
  it('keeps template order while grouping only adjacent fields', () => {
    const layout = buildViralAccountLayout([
      field('ungrouped-last', 50, 'DIRECTOR_ANALYSIS'),
      field('group-a-2', 30, 'DIRECTOR_ANALYSIS', 'A'),
      field('group-b', 40, 'DIRECTOR_ANALYSIS', 'B'),
      field('group-a-1', 20, 'DIRECTOR_ANALYSIS', 'A'),
      field('ungrouped-first', 10, 'DIRECTOR_ANALYSIS')
    ])

    const director = layout.sections.find(section => section.key === 'DIRECTOR_ANALYSIS')
    expect(director?.runs.map(run => ({ group: run.group, keys: run.fields.map(item => item.key) }))).toEqual([
      { group: undefined, keys: ['ungrouped-first'] },
      { group: 'A', keys: ['group-a-1', 'group-a-2'] },
      { group: 'B', keys: ['group-b'] },
      { group: undefined, keys: ['ungrouped-last'] }
    ])
  })

  it('keeps invalid and empty sections visible in the fallback', () => {
    const layout = buildViralAccountLayout([
      field('missing', 10),
      field('string-null', 20, 'null'),
      field('unknown', 30, 'OTHER'),
      field('known', 40, 'BUILD_SUGGESTION')
    ])

    expect(layout.unassignedFields.map(item => item.key)).toEqual(['missing', 'string-null', 'unknown'])
    expect(layout.sections.find(section => section.key === 'BUILD_SUGGESTION')?.runs[0].fields[0].key).toBe('known')
  })
})
