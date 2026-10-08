import { describe, expect, it, vi } from 'vitest'
import { api, http } from './api'
import { managementApi } from './managementApi'

describe('sorting query transport', () => {
  it('preserves sorting and filters on ordinary and advanced queries without local reordering', async () => {
    const calls: { url?: string; params: Record<string, unknown> }[] = []
    const response = { data: { code: 0, data: { list: [{ id: 2 }, { id: 1 }], total: 2, hasMore: false } } }
    vi.spyOn(http, 'get').mockImplementation(async (url, config) => { calls.push({ url, params: config?.params as Record<string, unknown> }); return response })
    vi.spyOn(http, 'post').mockImplementation(async (url, params) => { calls.push({ url, params: params as Record<string, unknown> }); return response })
    try {
      const sort = { sortField: 'name', sortOrder: 'ascend' as const, pageNo: 2, pageSize: 20 }
      const advancedFilter = { logic: 'AND' as const, conditions: [], groups: [] }
      for (const query of [sort, { ...sort, advancedFilter }]) {
        await api.myStudents(query)
        await api.managementSalesOrderPage({ ...query, sortField: 'studentName' })
        await api.managementSalesOrderCursor({ ...query, sortField: 'studentName', cursor: 'opaque-token' })
        await managementApi.cashbacks(false, { ...query, sortField: 'amount' })
        await managementApi.withdrawals(false, { ...query, sortField: 'applicationAmount' })
      }
      expect(calls).toHaveLength(10)
      expect(calls.every(call => call.params.sortOrder === 'ascend')).toBe(true)
      expect(calls.slice(5).every(call => call.params.advancedFilter)).toBe(true)
      expect(calls[2].params.cursor).toBe('opaque-token')
      expect(calls[7].url).toBe('/zsjos/sales-order/management-search-cursor')
      expect(calls[8].url).toBe('/zsjos/cashback/search-page')
      expect(calls[9].url).toBe('/zsjos/withdrawal/search-page')
    } finally { vi.restoreAllMocks() }
  })
})
