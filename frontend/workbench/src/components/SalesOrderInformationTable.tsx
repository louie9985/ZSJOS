import { Tag } from 'antd'
import type { DetailFieldGridProps, DetailFieldItem } from './DetailFieldGrid'
import '../styles/components/sales-order-information-table.css'

function taggedValue(item: DetailFieldItem) {
  const value = item.value
  if (value === null || value === undefined || value === '') return '-'
  if (value === '历史未记录') return <Tag>{value}</Tag>
  const colors: Record<string, string> = {
    orderType: 'blue', classType: 'purple', nature: 'cyan', feeMode: 'blue',
    paymentMethod: 'cyan', servicePeriod: 'purple', studentSource: 'blue'
  }
  if (item.key === 'round') return <Tag>{value}</Tag>
  if (colors[item.key]) return <Tag color={colors[item.key]}>{value}</Tag>
  return value
}

/** Opt-in presentation for order management; retains the shared field projection. */
export default function SalesOrderInformationTable({ items, columns = 2 }: DetailFieldGridProps) {
  return <dl className={`sales-order-information-table columns-${columns}`}>
    {items.map(item => <div key={item.key} className={`sales-order-information-cell${item.span === 2 ? ' span-2' : ''}`}>
      <dt>{item.label}</dt><dd>{taggedValue(item)}</dd>
    </div>)}
  </dl>
}
