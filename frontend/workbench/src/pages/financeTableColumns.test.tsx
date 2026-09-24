import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import type { ReactNode } from 'react'
import { createCashbackColumns, money } from './financeTableColumns'
import type { Cashback } from '../services/managementApi'

const cashbackColumns = createCashbackColumns([{ value: 'valid', label: '有效返现' }, { value: 'deal', label: '成交返现' }], [{ value: 'pending_settlement', label: '待结算' }])
const row: Cashback = { id: 1, cashbackNo: 'CB-test', type: 'valid', status: 'pending_settlement', beneficiaryUserId: 1, productNameSnapshot: '测试产品', baseAmount: 1000, rateSnapshot: 0.075, amount: 75, generatedAt: Date.UTC(2026, 8, 23, 2), availableAt: Date.UTC(2026, 8, 24, 2) }
function renderField(field: string, record = row) {
  const column = cashbackColumns.find(item => item.dataIndex === field)!
  // Reproduce ProTable's formatted-node argument, which cannot be treated as a number/string.
  const result = column.render!(<span>formatted</span>, record, 0, undefined, { type: 'table' })
  return renderToStaticMarkup(<>{result as ReactNode}</>)
}

describe('finance table raw response rendering', () => {
  it('uses raw amounts and rates despite a formatted first argument', () => {
    expect(renderField('baseAmount')).toBe('¥1000.00')
    expect(renderField('amount')).toBe('¥75.00')
    expect(renderField('rateSnapshot')).toBe('7.5%')
    expect(renderField('rateSnapshot', { ...row, rateSnapshot: 0 })).toBe('0%')
  })
  it('renders lifecycle labels and Shanghai timestamps from the record', () => {
    expect(renderField('type')).toBe('有效返现')
    expect(renderField('type', { ...row, type: 'deal' })).toBe('成交返现')
    expect(renderField('status')).toContain('待结算')
    expect(renderField('generatedAt')).toBe('2026-09-23 10:00')
    expect(renderField('availableAt')).toBe('2026-09-24 10:00')
  })
  it('renders authoritative labels without assuming known values', () => {
    const columns = createCashbackColumns([{ value: 'valid', label: '服务端类型名称' }], [])
    const type = columns.find(item => item.dataIndex === 'type')!
    expect(type.render!(null, row, 0, undefined, { type: 'table' })).toBe('服务端类型名称')
    const empty = createCashbackColumns([], []).find(item => item.dataIndex === 'type')!
    expect(empty.render!(null, row, 0, undefined, { type: 'table' })).toBe('valid')
  })
  it('keeps absent/invalid values distinct from zero', () => {
    for (const value of [undefined, null, '', ' ', 'invalid', NaN, Infinity, {}, <span>1</span>]) expect(money(value)).toBe('-')
    expect(money(0)).toBe('¥0.00')
    expect(money('75.50')).toBe('¥75.50')
    expect(renderField('baseAmount', { ...row, baseAmount: undefined })).toBe('-')
    expect(renderField('rateSnapshot', { ...row, rateSnapshot: undefined })).toBe('-')
    expect(renderField('availableAt', { ...row, availableAt: undefined })).toBe('-')
  })
})
