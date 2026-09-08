import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('product category lifecycle', () => {
  it('separates enabled and disabled category trees and keeps disabled actions permission-driven', () => {
    const page = readFileSync('src/pages/ConfigurationPages.tsx', 'utf8')
    const api = readFileSync('src/services/api.ts', 'utf8')

    expect(page).toContain('api.productCategoryTree(0)')
    expect(page).toContain('api.productCategoryTree(1)')
    expect(page).toContain('setCategories(enabledTree)')
    expect(page).toContain('setDisabledCategories(disabledTree)')
    expect(page).toContain('const categoryOptions = useMemo(() => categories.map(toCategoryOption), [categories])')
    expect(page).toContain("key: 'disabled-categories'")
    expect(page).toContain("permissions.includes('zsjos:product-category:update')")
    expect(page).toContain("permissions.includes('zsjos:product-category:status')")
    expect(page).toContain("permissions.includes('zsjos:product-category:delete')")
    expect(page).toContain('api.updateProductCategoryStatus')
    expect(page).toContain('api.deleteProductCategory')

    expect(api).toContain('productCategoryTree: async (status?: number)')
    expect(api).toContain('/zsjos/product/category/update-status')
    expect(api).toContain('/zsjos/product/category/delete')
  })
})
