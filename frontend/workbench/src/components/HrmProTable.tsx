import { ProTable } from '@ant-design/pro-components'
import type { ProColumns, ProTableProps } from '@ant-design/pro-components'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'

type HrmProTableProps<T extends Record<string, any>> = Omit<ProTableProps<T, Record<string, never>>, 'columns'> & {
  columns?: ColumnsType<T>
  /** 管理主列表开启刷新、列设置、密度、全屏和当前页快捷搜索。 */
  advanced?: boolean
  onReload?: () => void | Promise<void>
  persistenceKey?: string
}

function matchesKeyword(row: object, keyword: string) {
  const normalized = keyword.trim().toLocaleLowerCase()
  if (!normalized) return true
  return Object.values(row).some(value => {
    if (typeof value !== 'string' && typeof value !== 'number') return false
    return String(value).toLocaleLowerCase().includes(normalized)
  })
}

function rawValue<T extends Record<string, any>>(row: T, dataIndex: unknown) {
  if (Array.isArray(dataIndex)) {
    return dataIndex.reduce<unknown>((value, key) => value == null ? undefined : (value as Record<PropertyKey, unknown>)[key], row)
  }
  if (typeof dataIndex === 'string' || typeof dataIndex === 'number') return row[dataIndex]
}

function adaptColumns<T extends Record<string, any>>(columns?: ColumnsType<T>): ProColumns<T>[] | undefined {
  return columns?.map(column => {
    const children = 'children' in column && column.children ? adaptColumns(column.children) : undefined
    const render = column.render
    return {
      ...column,
      ...(children ? { children } : {}),
      ...(render ? {
        render: (_dom: React.ReactNode, row: T, index: number) => render(
          rawValue(row, 'dataIndex' in column ? column.dataIndex : undefined), row, index
        )
      } : {})
    } as ProColumns<T>
  })
}

function HrmProTableInner<T extends Record<string, any>>({
  advanced = false,
  onReload,
  persistenceKey,
  dataSource,
  options,
  columnsState,
  search,
  cardProps = false,
  className,
  ...props
}: HrmProTableProps<T>) {
  const [keyword, setKeyword] = useState('')
  const visibleRows = useMemo(
    () => keyword && dataSource ? dataSource.filter(row => matchesKeyword(row, keyword)) : dataSource,
    [dataSource, keyword]
  )
  const compatibleColumns = useMemo(() => adaptColumns(props.columns), [props.columns])
  const advancedOptions = advanced ? {
    density: true,
    fullScreen: true,
    reload: onReload ? () => { void onReload() } : false,
    setting: { draggable: true, checkable: true, checkedReset: true },
    search: {
      allowClear: true,
      placeholder: '搜索当前页',
      onSearch: (value: string) => { setKeyword(value); return true },
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => {
        if (!event.target.value) setKeyword('')
      }
    }
  } : false

  return <ProTable<T, Record<string, never>>
    {...props}
    className={`hrm-pro-table${advanced ? ' advanced' : ''}${className ? ` ${className}` : ''}`}
    columns={compatibleColumns}
    dataSource={visibleRows}
    cardProps={cardProps}
    search={search ?? false}
    options={options ?? advancedOptions}
    columnsState={columnsState ?? (persistenceKey ? {
      persistenceKey: `zsjos-hrm-table-${persistenceKey}`,
      persistenceType: 'localStorage'
    } : undefined)}
  />
}

const HrmProTable = Object.assign(HrmProTableInner, { Summary: ProTable.Summary })

export default HrmProTable
