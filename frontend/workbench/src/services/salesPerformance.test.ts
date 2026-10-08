import { describe,it,expect } from 'vitest'
import { periodRange,percent,money,normalizePerformanceDates,averageFormula,conversionRule,type Metric } from './salesPerformance'
describe('performance periods',()=>{
 it('week crosses year and rolling seven includes today',()=>{const now=new Date(2026,0,1,12);expect(periodRange('week',now).start).toBe('2025-12-29');expect(periodRange('last7',now).start).toBe('2025-12-26')})
 it('calendar quarter begins on first day',()=>expect(periodRange('quarter',new Date(2026,8,22)).start).toBe('2026-07-01'))
 it('cumulative requests actual available beginning',()=>expect(periodRange('all').cumulative).toBe(true))
 it('missing ratios and money differ from zero',()=>{expect(percent(null)).toBe('—');expect(percent(0)).toBe('0.00%');expect(money(null)).toBe('—')})
})

describe('performance HTTP timestamps', () => {
 it('normalizes nested metrics and display times in Shanghai without changing numeric business data', () => {
  const timestamp = Date.parse('2026-08-31T16:00:00Z')
  const actual = { start: timestamp, end: timestamp + 86400000, amount: 1000, orders: 2 }
  const result = normalizePerformanceDates({asOf: timestamp, attributionAvailableSince: null,
   targets: [{actual, target: {periodStart: '2026-09-01'}}], trend: [actual],
   averageTrends: {all: [actual]}, contributionMetrics: [{actual}],
   list: [{occurredAt: timestamp}], revisions: [{at: timestamp}], start: '2026-09-01'})
  const metric = {start: '2026-09-01T00:00:00', end: '2026-09-02T00:00:00', amount: 1000, orders: 2}
  expect(result).toEqual({asOf: '2026-09-01T00:00:00', attributionAvailableSince: null,
   targets: [{actual: metric, target: {periodStart: '2026-09-01'}}], trend: [metric],
   averageTrends: {all: [metric]}, contributionMetrics: [{actual: metric}],
   list: [{occurredAt: '2026-09-01T00:00:00'}], revisions: [{at: '2026-09-01T00:00:00'}], start: '2026-09-01'})
  expect(actual.start).toBe(timestamp)
 })
 it('preserves string fixtures, empty and null responses and handles epoch zero', () => {
  expect(normalizePerformanceDates({start:'2026-09-01T00:00:00',end:null,rows:[]})).toEqual({start:'2026-09-01T00:00:00',end:null,rows:[]})
  expect(normalizePerformanceDates({at:0})).toEqual({at:'1970-01-01T08:00:00'})
  expect(normalizePerformanceDates(null)).toBeNull()
 })
 it('rejects invalid timestamps before rendering', () => {
  expect(() => normalizePerformanceDates({start:NaN})).toThrow('业绩数据时间格式无效')
 })
})


describe('performance calculation presentation',()=>{
 it('uses eligible average inputs rather than unfiltered totals',()=>{
  const metric={amount:100.01,orders:2,averageAmount:100,averageOrders:1,average:100} as Metric
  expect(averageFormula(metric)).toContain('¥100.00 ÷ 1 笔 = ¥100.00')
  expect(averageFormula(metric)).not.toContain('100.01')
 })
 it('distinguishes monthly validity from rolling receipt cohorts',()=>{
  expect(conversionRule('last90')).toContain('不限60日')
  expect(conversionRule('lastMonth')).toContain('往期接收60日内')
 })
 it('normalizes drilldown deadlines and comparison bounds',()=>{
  const time=Date.parse('2026-09-28T01:00:00Z')
  expect(normalizePerformanceDates({dueAt:time,receivedAt:time,previousStart:time,previousEnd:time})).toEqual({dueAt:'2026-09-28T09:00:00',receivedAt:'2026-09-28T09:00:00',previousStart:'2026-09-28T09:00:00',previousEnd:'2026-09-28T09:00:00'})
 })
})

it('states the inclusive per-order conversion threshold for every period',()=>{
 for(const key of ['month','lastMonth','last7','last30','last60','last90']) {
  expect(conversionRule(key)).toContain('≥1280元')
  expect(conversionRule(key)).toContain('不跨订单累计')
  expect(conversionRule(key)).toContain('未达标的期间有效客资仍在分母')
 }
})
