import { describe, expect, it } from 'vitest'
import { appendSubordinateSalesRows, formatCurrency, receiveStatusLabel, summarizeBatchResult, todayStatusLabel } from './subordinateSales'
import type { SubordinateSales } from './api'

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

  it('appends lazy pages without duplicating sales users', () => {
    const row = (userId: number, name: string) => ({ userId, name } as SubordinateSales)
    expect(appendSubordinateSalesRows(
      [row(1, '销售甲'), row(2, '旧名称')],
      [row(2, '销售乙'), row(3, '销售丙')]
    )).toEqual([row(1, '销售甲'), row(2, '销售乙'), row(3, '销售丙')])
  })
})
