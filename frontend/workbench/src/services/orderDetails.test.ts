import { describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { performanceApi } from './salesPerformance'

describe('order detail transport', () => {
 it('preserves immutable entry constraints alongside all optional filters and pagination', async () => {
  const query = { scopeType: 'DEPT' as const, scopeId: 12, periodKey: 'last30', dimension: 'product', groupKey: 'p|历史商品', sourceKey: 'self', orderTypeKey: 'type', categoryKey: 'cat', channelKey: 'channel', productKey: 'p', keyword: 'DD-%_', pageNo: 2, pageSize: 20 }
  const response = { list: [], total: 0, asOf: Date.parse('2026-10-10T04:00:00Z'), summary: { amount: 100, orders: 1 }, filteredSummary: { amount: 0, orders: 0 } }
  const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: response } })
  try {
   const controller = new AbortController()
   expect(await performanceApi.orderDetails(query, controller.signal)).toEqual({ ...response, asOf: '2026-10-10T12:00:00' })
   expect(get).toHaveBeenCalledWith('/zsjos/sales-performance/order-details', { params: query, signal: controller.signal })
  } finally { get.mockRestore() }
 })
})
