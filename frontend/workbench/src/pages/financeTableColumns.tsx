import { Button, Tag } from 'antd'
import type { ProColumns } from '@ant-design/pro-components'
import type { AdvancedFilterField } from '../services/api'
import type { Cashback } from '../services/managementApi'
import { formatTimestamp } from '../services/time'

function finiteNumber(value: unknown): number | undefined {
  if (typeof value !== 'number' && typeof value !== 'string') return undefined
  if (typeof value === 'string' && !value.trim()) return undefined
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}

export function money(value: unknown) {
  const number = finiteNumber(value)
  return number === undefined ? '-' : `¥${number.toFixed(2)}`
}

function percentage(value: unknown) {
  const number = finiteNumber(value)
  return number === undefined ? '-' : `${Number((number * 100).toFixed(8))}%`
}


// ProTable render's first argument is a display node; calculations require the raw row.
export const createCashbackColumns = (types: AdvancedFilterField['options'], statuses: AdvancedFilterField['options'], onDetail?: (id: number) => void): ProColumns<Cashback>[] => [
  { title: '返现单号', dataIndex: 'cashbackNo', fixed: 'left', render: (_, row) => onDetail ? <Button type="link" onClick={() => onDetail(row.id)}>{row.cashbackNo}</Button> : row.cashbackNo },
  { title: '返现受益人', dataIndex: 'beneficiaryName', render: (_, row) => row.beneficiaryName || '-' },
  { title: '归属合作方', dataIndex: 'partnerName', hideInTable: !onDetail },
  { title: '类型', dataIndex: 'type', render: (_, row) => types.find(item => item.value === row.type)?.label || '类型暂不可用' },
  { title: '客户／学员', key: 'customer', hideInTable: !onDetail, render: (_, row) => row.source?.studentName || row.source?.customerName || '-' },
  { title: '来源单据', key: 'source', hideInTable: !onDetail, render: (_, row) => row.source?.orderNo || row.source?.leadNo || (row.source?.orderAccess === 'denied' || row.source?.leadAccess === 'denied' ? '无权查看来源信息' : '历史来源信息缺失') },
  { title: '产品', dataIndex: 'productNameSnapshot' },
  { title: '返现基数', dataIndex: 'baseAmount', render: (_, row) => row.type === 'valid' ? '不适用' : money(row.baseAmount) },
  { title: '比例', dataIndex: 'rateSnapshot', render: (_, row) => row.type === 'valid' ? '不适用' : percentage(row.rateSnapshot) },
  { title: '金额', dataIndex: 'amount', render: (_, row) => money(row.amount) },
  { title: '状态', dataIndex: 'status', render: (_, row) => <Tag>{statuses.find(item => item.value === row.status)?.label || '状态暂不可用'}</Tag> },
  { title: '生成时间', dataIndex: 'generatedAt', render: (_, row) => formatTimestamp(row.generatedAt) },
  { title: '可用时间', dataIndex: 'availableAt', render: (_, row) => formatTimestamp(row.availableAt) },
]
