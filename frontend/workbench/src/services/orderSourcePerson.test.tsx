import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { orderSourcePersonText, salesOrderDetailToListItem } from './salesOrder'
import type { SalesOrder } from './api'
import SalesOrderDetailCards from '../components/SalesOrderDetailCards'
import { buildSalesOrderTableColumns } from '../components/SalesOrderTableColumns'

describe('order source evidence', () => {
  it('distinguishes explicit no-provider evidence from missing historical names', () => {
    expect(orderSourcePersonText('sales_self_sourced', undefined, true)).toBe('销售本人自拓（未关联新媒体）')
    expect(orderSourcePersonText('education_self_sourced', undefined, true)).toBe('教务本人自拓（未关联新媒体）')
    expect(orderSourcePersonText('sales_self_sourced')).toBe('历史未记录')
    expect(orderSourcePersonText('sales_self_sourced', undefined, false)).toBe('历史未记录')
    expect(orderSourcePersonText('sales_self_sourced', '快照姓名', false)).toBe('快照姓名')
    expect(orderSourcePersonText('partner', '兼职姓名', true)).toBe('兼职姓名')
  })
  it('renders consistent source evidence in detail and list projections', () => {
    const order = { id: 1, orderNo: 'TEST', status: 'effective', items: [], paymentVouchers: [],
      leadProfile: { leadNo: 'TEST-LEAD', submittedName: '合成客户', sourceType: 'sales_self_sourced', selfSourcedWithoutProvider: true }
    } as unknown as SalesOrder
    expect(renderToStaticMarkup(<SalesOrderDetailCards order={order} mode="mine" />)).toContain('销售本人自拓（未关联新媒体）')
    const row = salesOrderDetailToListItem(order)
    expect(row.leadSelfSourcedWithoutProvider).toBe(true)
    const column = buildSalesOrderTableColumns(() => {}).find(col => col.dataIndex === 'leadSourceUserName')!
    expect(column.render?.(undefined, row, 0, {} as never, {} as never)).toBe('销售本人自拓（未关联新媒体）')
  })
})
