import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import type { SalesOrder } from './api'
import { orderPartnerOwnerText, salesOrderDetailToListItem } from './salesOrder'
import SalesOrderDetailCards from '../components/SalesOrderDetailCards'
import { buildSalesOrderTableColumns } from '../components/SalesOrderTableColumns'

describe('order partner owner snapshot', () => {
  it('distinguishes historical absence, unassigned and recorded employees', () => {
    expect(orderPartnerOwnerText('partner')).toBe('历史未记录')
    expect(orderPartnerOwnerText('partner', {})).toBe('提交时未分配')
    expect(orderPartnerOwnerText('partner', { employeeUserId: 7, employeeName: '提交时员工' })).toBe('提交时员工')
    expect(orderPartnerOwnerText('partner', { employeeUserId: 7 })).toBe('姓名未记录')
    expect(orderPartnerOwnerText('internal_new_media', { employeeName: '不能展示' })).toBe('—')
  })

  it.each(['partner', 'internal_new_media'])('projects the same snapshot in list and detail for %s', sourceType => {
    const order = { id: 1, status: 'effective', items: [], paymentVouchers: [], leadProfile: {
      leadNo: 'LD-TEST', submittedName: '测试', sourceType,
      partnerOwnerSnapshot: { employeeUserId: 7, employeeName: '提交时员工' }
    } } as unknown as SalesOrder
    const row = salesOrderDetailToListItem(order)
    expect(row.partnerOwnerSnapshot).toEqual(order.leadProfile?.partnerOwnerSnapshot)
    const column = buildSalesOrderTableColumns(() => {}).find(item => item.key === 'partnerOwnerSnapshot')!
    expect(column.render?.(undefined, row, 0, {} as never, {} as never)).toBe(sourceType === 'partner' ? '提交时员工' : '—')
    const html = renderToStaticMarkup(<SalesOrderDetailCards order={order} mode="mine" informationLayout="table"/>)
    expect(html.includes('兼职归属新媒体员工')).toBe(sourceType === 'partner')
    expect(html.includes('提交时员工')).toBe(sourceType === 'partner')
  })
})
