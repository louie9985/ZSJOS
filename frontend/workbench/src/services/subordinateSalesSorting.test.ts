import { describe, expect, it, vi } from 'vitest'
import { api, http } from './api'

describe('subordinate lead server sorting', () => {
  it.each(['ascend', 'descend'] as const)('keeps %s sorting and filters across GET/POST pagination', async sortOrder => {
    const result = { list: [{ id: 21 }, { id: 3 }], total: 42 }
    const response = { data: { code: 0, data: result } }
    const get = vi.spyOn(http, 'get').mockResolvedValue(response)
    const post = vi.spyOn(http, 'post').mockResolvedValue(response)
    const params = { pageNo: 2, pageSize: 20, sortField: 'submittedAt' as const, sortOrder, keyword: '测试', recycledOnly: true }
    try {
      expect(await api.subordinateSalesLeads(5, params)).toEqual(result)
      expect(get).toHaveBeenCalledWith('/zsjos/subordinate-sales/5/leads', { params })
      const advanced = { ...params, advancedFilter: { logic: 'AND' as const, conditions: [], groups: [] } }
      expect(await api.subordinateSalesLeads(5, advanced)).toEqual(result)
      expect(post).toHaveBeenCalledWith('/zsjos/subordinate-sales/5/leads/search-page', advanced)
      await api.subordinateSalesLeads(5, { pageNo: 1, pageSize: 20 })
      expect(get).toHaveBeenLastCalledWith('/zsjos/subordinate-sales/5/leads', { params: { pageNo: 1, pageSize: 20 } })
    } finally { vi.restoreAllMocks() }
  })
})
