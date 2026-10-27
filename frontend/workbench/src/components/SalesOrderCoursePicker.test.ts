import { describe, expect, it } from 'vitest'
import { selectedSkuAttrValues, resolveOrderSku, selectableOrderAttrValues } from './SalesOrderCoursePicker'
import type { LeadCatalog } from '../services/api'

const catalog: LeadCatalog = {
  categoryTree: [],
  spus: [],
  skus: [
    { spuRef: 'course-a', skuRef: 'sku-a', skuName: '周末班', price: 100, attrValues: { classType: 'weekend', level: 'beginner' } },
  ],
}

describe('selectedSkuAttrValues', () => {
  it('restores all attributes when the form writes the selected SKU back', () => {
    expect(selectedSkuAttrValues(catalog, 'course-a::sku-a')).toEqual({ classType: 'weekend', level: 'beginner' })
  })

  it('returns an empty selection for an empty or unknown course value', () => {
    expect(selectedSkuAttrValues(catalog)).toEqual({})
    expect(selectedSkuAttrValues(catalog, 'course-a::missing')).toEqual({})
  })
})

describe('sales SKU availability', () => {
  const choices: LeadCatalog = {
    categoryTree: [],
    spus: [{ spuRef: 'course-a', spuName: '课程', categoryId: 1, categoryName: '分类', categoryPath: [], attrs: [
      { attrKey: 'level', attrName: '等级', required: true, values: [{ value: 'a', label: '甲' }, { value: 'b', label: '乙' }, { value: 'disabled', label: '停用' }] },
      { attrKey: 'classType', attrName: '班型', required: true, values: [{ value: 'x', label: 'X' }, { value: 'y', label: 'Y' }] },
    ] }],
    skus: [
      { spuRef: 'course-a', skuRef: 'ax', skuName: '甲X', price: 100, attrValues: { level: 'a', classType: 'x' } },
      { spuRef: 'course-a', skuRef: 'by', skuName: '乙Y', price: 200, attrValues: { level: 'b', classType: 'y' } },
      { spuRef: 'other', skuRef: 'other', skuName: '其他课程', price: 300, attrValues: { level: 'disabled' } },
    ],
  }
  it('excludes values supported only by missing SKUs or another course', () => {
    expect(selectableOrderAttrValues(choices, 'course-a', 'level').map(v => v.value)).toEqual(['a', 'b'])
    expect(selectableOrderAttrValues({ ...choices, skus: [] }, 'course-a', 'level')).toEqual([])
  })
  it('resolves a complete combination and clears invalid or partial combinations', () => {
    expect(resolveOrderSku(choices, 'course-a', { level: 'a', classType: 'x' })?.skuRef).toBe('ax')
    expect(resolveOrderSku(choices, 'course-a', { level: 'b', classType: 'x' })).toBeUndefined()
    expect(resolveOrderSku(choices, 'course-a', { level: 'a' })).toBeUndefined()
    expect(resolveOrderSku(choices, 'course-a', { level: 'disabled', classType: 'x' })).toBeUndefined()
  })
  it('does not guess a SKU when more than one combination matches', () => {
    const optional = { ...choices, spus: choices.spus.map(spu => ({ ...spu, attrs: spu.attrs.map(attr => ({ ...attr, required: false })) })) }
    expect(resolveOrderSku(optional, 'course-a', {})).toBeUndefined()
  })
})
