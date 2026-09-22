import type { ProColumns } from '@ant-design/pro-components'
import type { TableColumnsType } from 'antd'

export const MIN_COLUMN_WIDTH = 32
export function clampColumnWidth(width: number) {
  return Math.max(MIN_COLUMN_WIDTH, Math.round(width))
}

export function parseColumnWidths(stored: string | null): Record<string, number> {
  try {
    const value: unknown = JSON.parse(stored || '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    return Object.fromEntries(Object.entries(value).filter((entry): entry is [string, number] =>
      typeof entry[1] === 'number' && Number.isFinite(entry[1])
    ).map(([key, width]) => [key, clampColumnWidth(width)]))
  } catch { return {} }
}

export function columnKey<T>(column: ProColumns<T>, index: number): string {
  return String(column.key ?? (Array.isArray(column.dataIndex) ? column.dataIndex.join(',') : column.dataIndex) ?? String(index))
}

function fieldValue(record: unknown, path: unknown): unknown {
  if (path === undefined || path === null) return undefined
  return (Array.isArray(path) ? path : [path]).reduce<unknown>((value, key) =>
    value !== null && typeof value === 'object' ? Reflect.get(value, key) : undefined, record)
}

/** Ant Table render receives raw values; ProTable passes formatted React nodes instead. */
export function fromNativeColumns<T extends object>(columns: TableColumnsType<T>): ProColumns<T>[] {
  return columns.map(column => {
    if ('children' in column) return { ...column, children: fromNativeColumns(column.children) } as ProColumns<T>
    const { render, ...rest } = column
    return {
      ...rest,
      ...(render ? { render: (_: unknown, record: T, index: number) => render(fieldValue(record, column.dataIndex), record, index) } : {}),
    } as ProColumns<T>
  })
}
