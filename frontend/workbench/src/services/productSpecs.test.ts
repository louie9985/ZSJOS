import { describe, expect, it } from 'vitest'
import { catalogSpecs, productSpecs, productSpecText } from './productSpecs'
import { catalogSpecs as adminCatalogSpecs } from '../../../admin/src/utils/productSpecs'
import { catalogSpecs as h5CatalogSpecs } from '../../../h5/src/utils/productSpecs'

const attrs = [
  { attrKey: 'place', attrName: '考试地点', values: [{ value: 'bj', label: '北京' }] },
  { attrKey: 'level', attrName: '考试等级', values: [{ value: '2', label: '二级' }] }
]
describe('named product specifications across runtimes', () => {
  it.each([catalogSpecs, adminCatalogSpecs, h5CatalogSpecs])('uses named values and configured ordering', resolve => {
    expect(resolve({ level: '2', place: 'bj' }, attrs).map(spec => spec.attrName)).toEqual(['考试地点', '考试等级'])
    expect(resolve({ level: '2' }, attrs).map(spec => spec.label)).toEqual(['二级'])
    expect(resolve({}, attrs)).toEqual([])
  })
  it('keeps snapshot labels without relabelling history', () => {
    const specs = catalogSpecs({ level: '2' }, attrs)
    expect(productSpecText({ specs, selectedAttrValues: '{"level":"new"}' })).toBe('考试等级：二级')
  })
  it.each([catalogSpecs, adminCatalogSpecs, h5CatalogSpecs])('retains removed fields after configured fields', resolve => {
    const result = resolve({ removed: 'same', level: '2', another: 'same' }, attrs)
    expect(result.map(spec => spec.attrKey)).toEqual(['level', 'removed', 'another'])
    expect(result.slice(1).every(spec => spec.labelMissing)).toBe(true)
    expect(result.slice(1).map(spec => spec.label)).toEqual(['same', 'same'])
  })
  it('keeps equal values belonging to different fields and marks legacy labels', () => {
    expect(productSpecs({ selectedAttrValues: '{"first":"same","second":"same"}' })).toHaveLength(2)
    expect(productSpecText({ attrValues: { level: '2' } })).toBe('level：2（历史标签缺失）')
  })
  it('does not invent specs for missing or malformed legacy payloads', () => {
    expect(productSpecs({ selectedAttrValues: 'invalid' })).toEqual([])
    expect(productSpecs({ selectedAttrValues: 'null' })).toEqual([])
  })
})
