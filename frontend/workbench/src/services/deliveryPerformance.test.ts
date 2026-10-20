import { beforeEach, describe, expect, it, vi } from 'vitest'
const mock = vi.hoisted(() => ({get: vi.fn(), put: vi.fn()}))
vi.mock('./api', () => ({http: mock, unwrap: (value: unknown) => value}))
import { deliveryPerformanceApi } from './deliveryPerformance'
import { performanceApi } from './salesPerformance'
beforeEach(() => {mock.get.mockReset().mockResolvedValue([]); mock.put.mockReset().mockResolvedValue(true)})
describe('independent delivery performance API', () => {
 it('routes statistics, detail and targets only to delivery endpoints', async () => {
  await deliveryPerformanceApi.tree(); await deliveryPerformanceApi.orderDetails({scopeType:'DEPT',scopeId:1051});
  await deliveryPerformanceApi.conversionDetails({scopeType:'DEPT',scopeId:1051}); await deliveryPerformanceApi.targets('month','2026-10-01');
  for (const call of mock.get.mock.calls) expect(call[0]).toContain('/zsjos/delivery-performance')
  await deliveryPerformanceApi.save([]); expect(mock.put.mock.calls[0][0]).toBe('/zsjos/delivery-performance-target/batch')
 })
 it('retains sales endpoints independently', async () => {
  await performanceApi.tree();expect(mock.get.mock.calls[0][0]).toBe('/zsjos/sales-performance/tree')
 })
})

it('keeps unclassified history read-only under each domain endpoint',async()=>{
 await performanceApi.unclassifiedHistory(2,20)
 await deliveryPerformanceApi.unclassifiedHistory(2,20)
 expect(mock.get.mock.calls.map(x=>x[0])).toEqual(['/zsjos/sales-performance/unclassified-history','/zsjos/delivery-performance/unclassified-history'])
 expect(mock.get.mock.calls[0][1].params).toMatchObject({pageNo:2,pageSize:20})
 expect(mock.put).not.toHaveBeenCalled()
})
