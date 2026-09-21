import { MemoryRouter } from 'react-router-dom'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { MaterialFieldDefinition, MaterialType } from '../services/materialApi'
import ViralContentMaterialForm from './ViralContentMaterialForm'
import ViralAccountMaterialForm, { buildViralAccountLayout, hasMaterialDraftContent } from './ViralAccountMaterialForm'

const field = (key: string, sort: number, section?: string, group?: string): MaterialFieldDefinition => ({
  key,
  label: key,
  type: 'text',
  sort,
  section: section as MaterialFieldDefinition['section'],
  group
})

describe('material draft content', () => {
  it.each([undefined, null, '', ' \n\t', [], {}, [{ text: ' ', choices: [] }]])('rejects empty value %j', value => {
    expect(hasMaterialDraftContent(value)).toBe(false)
  })
  it.each(['部分内容', 0, false, ['selected'], [{ text: '拆解内容' }]])('retains meaningful value %j', value => {
    expect(hasMaterialDraftContent(value)).toBe(true)
  })
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


describe('unavailable template', () => {
  it.each([undefined, { fields: [] }])('shows an actionable error without save controls', currentSchema => {
    const html = renderToStaticMarkup(createElement(ViralAccountMaterialForm, {
      mode: 'create', type: { id: 1, currentSchema } as MaterialType, dicts: {},
      onClose: () => {}, onSaved: () => {}, onRetry: () => {}
    }))
    expect(html).toContain('拆解模板不可用')
    expect(html).toContain('重试')
    expect(html).not.toContain('保存草稿')
    expect(html).not.toContain('提交审批')
    expect(html).not.toContain('viral-account-screenshot')
  })
})


describe('available template', () => {
  it.each([ViralAccountMaterialForm, ViralContentMaterialForm])('renders all sections from backend fields', component => {
    const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(component, {
      mode: 'create', type: { id: 1, currentSchema: { fields: [
        field('account_name', 10, 'ACCOUNT_DETAIL'),
        field('analysis', 20, 'DIRECTOR_ANALYSIS'),
        field('advice', 30, 'BUILD_SUGGESTION')
      ] } } as MaterialType, dicts: {}, onClose: () => {}, onSaved: () => {}
    })))
    expect(html.match(/class="viral-account-section"/g)).toHaveLength(3)
    expect(html).toContain('viral-account-screenshot')
    expect(html).toContain('保存草稿')
    expect(html).not.toContain('拆解模板不可用')
  })
})


describe('reopening an existing material draft', () => {
  it('uses the saved draft schema after a newer template is published', () => {
    const html = renderToStaticMarkup(createElement(ViralAccountMaterialForm, {
      mode: 'edit', type: { id: 1, currentSchema: { fields: [field('new_field', 1, 'ACCOUNT_DETAIL')] } } as MaterialType,
      material: { id: 7, currentVersion: { status: 'DRAFT', fields: [field('saved_field', 1, 'ACCOUNT_DETAIL')], values: { saved_field: '已保存的内容' } } } as unknown as import('../services/materialApi').Material,
      dicts: {}, onClose: () => {}, onSaved: () => {}
    }))
    expect(html).toContain('saved_field')
    expect(html).toContain('已保存的内容')
    expect(html).not.toContain('new_field')
  })
})
