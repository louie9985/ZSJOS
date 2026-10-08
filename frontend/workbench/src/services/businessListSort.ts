import type { ProColumns } from '@ant-design/pro-components'
import type { TableProps } from 'antd'

export type BusinessSort = { sortField?: string; sortOrder?: 'ascend' | 'descend' }

// Only columns registered by the owning page can emit backend sort fields.
export function sortableColumns<T extends Record<string, unknown>>(columns: ProColumns<T>[], sort: BusinessSort): ProColumns<T>[] {
  return columns.map(column => {
    const field = String(column.key || column.dataIndex || '')
    if (!field || field === 'action' || column.valueType === 'option') return column
    return { ...column, key: field, sorter: true, sortOrder: sort.sortField === field ? sort.sortOrder : null }
  })
}

export function readTableSort<T>(sorter: Parameters<NonNullable<TableProps<T>['onChange']>>[2]): BusinessSort {
  const active = Array.isArray(sorter) ? sorter[0] : sorter
  return active.order && active.columnKey ? { sortField: String(active.columnKey), sortOrder: active.order } : {}
}

export function sortChoices<T>(columns: ProColumns<T>[]): { field: string; label: string }[] {
  return columns.flatMap(column => {
    const field = String(column.key || column.dataIndex || '')
    return field && field !== 'action' && typeof column.title === 'string' ? [{ field, label: column.title }] : []
  })
}
