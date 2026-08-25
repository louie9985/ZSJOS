import { describe, expect, it } from 'vitest'
import { buildEamTree, filterCategoryTree, findCategory, previewAssetCode, pruneExtFields } from './eam'
import type { EamCategory } from './api'

const category = (id: number, parentId: number, name: string, code: string): EamCategory => ({
  id, parentId, name, code, sort: 0, status: 0, managementMode: 1, unit: '个'
})

describe('buildEamTree', () => {
  it('nests children under their parent', () => {
    const tree = buildEamTree([category(1, 0, 'IT', 'IT'), category(2, 1, '笔记本', 'NB')])
    expect(tree).toHaveLength(1)
    expect(tree[0].children.map(node => node.id)).toEqual([2])
  })

  it('treats nodes whose parent is absent as roots', () => {
    // 后端按权限过滤后可能只返回子集，此时子节点不应被丢弃
    const tree = buildEamTree([category(2, 99, '笔记本', 'NB')])
    expect(tree.map(node => node.id)).toEqual([2])
  })
})

describe('findCategory', () => {
  const tree = buildEamTree([category(1, 0, 'IT', 'IT'), category(2, 1, '笔记本', 'NB')])

  it('finds a nested node', () => {
    expect(findCategory(tree, 2)?.name).toBe('笔记本')
  })

  it('returns undefined without an id', () => {
    expect(findCategory(tree, undefined)).toBeUndefined()
  })
})

describe('filterCategoryTree', () => {
  const tree = buildEamTree([
    category(1, 0, 'IT设备', 'IT'),
    category(2, 1, '笔记本', 'NB'),
    category(3, 0, '办公家具', 'FUR')
  ])

  it('keeps the ancestor chain of a matched child', () => {
    const filtered = filterCategoryTree(tree, '笔记本')
    expect(filtered).toHaveLength(1)
    expect(filtered[0].id).toBe(1)
    expect(filtered[0].children.map(node => node.id)).toEqual([2])
  })

  it('matches on code as well as name', () => {
    expect(filterCategoryTree(tree, 'FUR').map(node => node.id)).toEqual([3])
  })

  it('returns the input untouched for a blank keyword', () => {
    expect(filterCategoryTree(tree, '  ')).toBe(tree)
  })
})

describe('previewAssetCode', () => {
  const now = new Date('2026-08-24T00:00:00Z')

  it('joins prefix, category code, date and serial', () => {
    expect(previewAssetCode({ prefix: 'AS', useCategoryCode: true, dateFormat: 'yyyy', serialLength: 4, separator: '-', currentSerial: 7 }, 'IT', now))
      .toBe('AS-IT-2026-0008')
  })

  it('falls back to XX when the category has no code', () => {
    expect(previewAssetCode({ useCategoryCode: true, serialLength: 3, separator: '-' }, undefined, now)).toBe('XX-001')
  })

  it('supports the year-month format', () => {
    expect(previewAssetCode({ useCategoryCode: false, dateFormat: 'yyyyMM', serialLength: 2, separator: '/' }, undefined, now)).toBe('202608/01')
  })
})

describe('pruneExtFields', () => {
  it('drops values whose key is no longer defined on the category', () => {
    expect(pruneExtFields({ kept: 1, dropped: 2 }, ['kept'])).toEqual({ kept: 1 })
  })

  it('returns an empty object when nothing is allowed', () => {
    expect(pruneExtFields({ a: 1 }, [])).toEqual({})
  })
})
