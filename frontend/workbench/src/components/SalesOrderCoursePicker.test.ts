import { describe, expect, it } from 'vitest'
import { selectedSkuAttrValues } from './SalesOrderCoursePicker'
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
