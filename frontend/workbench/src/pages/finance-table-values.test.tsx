import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import type { ComponentProps } from 'react'
import BusinessTable from '../components/BusinessTable'
import { buildSalesOrderTableColumns } from '../components/SalesOrderTableColumns'
import type { SalesOrderListItem } from '../services/api'
import { CashbackPage, WithdrawalPage } from './ManagementPages'

const fixture = vi.hoisted(() => ({ rows: [] as object[] }))
vi.mock('../components/BusinessTable', async importOriginal => {
  const actual = await importOriginal<typeof import('../components/BusinessTable')>()
  return { default: (props: ComponentProps<typeof BusinessTable> & { tableKey: string }) =>
    <actual.default {...props} dataSource={fixture.rows} pagination={false} /> }
})

describe('finance lists through the real ProTable renderer', () => {
  it.each([1250.75, 0])('renders withdrawal amount %s and Chinese lifecycle state', amount => {
    fixture.rows = [{ id: 1, withdrawalNo: 'TEST-W', applicationAmount: amount, status: 'approved', submittedAt: '2026-09-21T10:30:00' }]
    const html = renderToStaticMarkup(<WithdrawalPage permissions={['zsjos:withdrawal:finance-query']} />)
    expect(html).toContain(`¥${amount.toFixed(2)}`)
    expect(html).toContain('待打款')
    expect(html).toContain('2026-09-21 10:30')
    expect(html).not.toContain('NaN')
    expect(html).not.toContain('[object Object]')
  })
  it('does not invent zero for missing or invalid withdrawal amounts', () => {
    fixture.rows = [{ id: 1, applicationAmount: undefined }, { id: 2, applicationAmount: 'invalid' }]
    const html = renderToStaticMarkup(<WithdrawalPage permissions={['zsjos:withdrawal:finance-query']} />)
    expect(html).not.toContain('NaN')
    expect(html).not.toContain('¥0.00')
  })
  it('renders cashback type, rate, amounts, status and timestamps from raw fields', () => {
    fixture.rows = [{ id: 1, cashbackNo: 'TEST-C', type: 'valid', baseAmount: 200, rateSnapshot: 0.15, amount: 30, status: 'available', generatedAt: '2026-09-21T10:30:00' }]
    const html = renderToStaticMarkup(<CashbackPage permissions={['zsjos:cashback:finance-query']} />)
    for (const value of ['有效返现', '¥200.00', '15%', '¥30.00', '可提现', '2026-09-21 10:30']) expect(html).toContain(value)
    expect(html).not.toContain('NaN')
  })
  it('renders the shared finance order columns without coercing formatted React nodes', () => {
    fixture.rows = [{ id: 1, orderNo: 'TEST-O', orderType: 'first_purchase', totalAmount: 256.5, approvalRoundNo: 2, taskStatus: 0, submittedAt: '2026-09-21T10:30:00' }]
    const html = renderToStaticMarkup(<BusinessTable<SalesOrderListItem> tableKey="finance-regression" columns={buildSalesOrderTableColumns(() => {})} rowKey="id" />)
    for (const value of ['首购', '¥256.50', '第 2 轮', '待审批', '2026-09-21']) expect(html).toContain(value)
    expect(html).not.toContain('NaN')
    expect(html).not.toContain('[object Object]')
  })
})
