import { describe, expect, it } from 'vitest'
import { formatCurrency, receiveStatusLabel, summarizeBatchResult, todayStatusLabel } from './subordinateSales'

describe('subordinate sales display helpers', () => {
  it('keeps receive and today statuses binary', () => {
    expect(receiveStatusLabel({ canReceiveNewLeads: true })).toBe('可接收')
    expect(receiveStatusLabel({ canReceiveNewLeads: false })).toBe('不可接收')
    expect(todayStatusLabel('completed')).toBe('已完成')
    expect(todayStatusLabel('incomplete')).toBe('未完成')
  })

  it('summarizes partial success without hiding failures', () => {
    expect(summarizeBatchResult({ successCount: 2, failureCount: 1, items: [] })).toBe('成功 2 条，失败 1 条')
  })

  it('formats deal amount as CNY', () => {
    expect(formatCurrency(1234.5)).toContain('1,234.50')
  })
})
