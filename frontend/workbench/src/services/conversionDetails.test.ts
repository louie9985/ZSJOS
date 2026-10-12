import { describe, expect, it, vi } from 'vitest'
import { http } from './api'
import { normalizePerformanceDates, performanceApi, conversionSummary, sourceReviewRequired } from './salesPerformance'

describe('conversion detail transport', () => {
 it('keeps independent scope, filters and pagination in the dedicated API', async () => {
  const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: { list: [], total: 0 } } })
  const query = { scopeType: 'DEPT' as const, scopeId: 12, periodKey: 'last30', conversionStatus: 'converted', populationKind: 'current', categoryKey: 'value:category', channelKey: 'value:channel', productKey: 'value:ref.value:name', keyword: 'DD-100', pageNo: 2, pageSize: 20 }
  const controller = new AbortController()
  try {
   expect(await performanceApi.conversionDetails(query, controller.signal)).toEqual({ list: [], total: 0 })
   expect(get).toHaveBeenCalledWith('/zsjos/sales-performance/conversion-details', { params: query, signal: controller.signal })
  } finally { get.mockRestore() }
 })
 it('normalizes qualifying order times and leaves JSON snapshots and IDs intact', () => {
  const timestamp = Date.parse('2026-10-10T04:00:00Z')
  expect(normalizePerformanceDates({ list: [{ leadId: 100, receivedAt: timestamp, order: { submittedAt: timestamp, amount: 1280, products: [{ snapshot: '{"name":"快照"}' }] } }] })).toEqual({ list: [{ leadId: 100, receivedAt: '2026-10-10T12:00:00', order: { submittedAt: '2026-10-10T12:00:00', amount: 1280, products: [{ snapshot: '{"name":"快照"}' }] } }] })
 })
 it('distinguishes review from zero population and supports older responses', () => {
  expect(sourceReviewRequired({})).toBe(false)
  expect(conversionSummary({ converted: 0, denominator: 0 })).toBe('成交客资 0 / 口径客资 0')
  expect(conversionSummary({ converted: null, denominator: null, sourceStatus: 'review_required', sourceReviewCount: 3 })).toBe('来源待核实，暂无法计算（3条客资）')
 })
})
