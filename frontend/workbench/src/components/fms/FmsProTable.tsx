import { ProTable, type ProColumns, type ProTableProps } from '@ant-design/pro-components'
import type { TableProps } from 'antd'

/**
 * FMS 统一表格入口：保留 ProTable 的完整能力，同时兼容现有 Ant Design 列定义。
 * 默认开启刷新、密度、列设置和全屏工具栏，具体页面仍可覆盖 options 或传入 false。
 */
export type FmsProTableProps<T extends object> = Omit<ProTableProps<T, Record<string, unknown>>, 'columns'> & {
  columns?: TableProps<T>['columns']
}

export default function FmsProTable<T extends object>({
  columns,
  options,
  search = false,
  cardProps = false,
  ...props
}: FmsProTableProps<T>) {
  return (
    <ProTable<T, Record<string, unknown>>
      {...props}
      search={search}
      cardProps={cardProps}
      options={options === false ? false : {
        reload: true,
        density: true,
        setting: true,
        fullScreen: true,
        ...(options || {})
      }}
      columns={columns as ProColumns<T>[]}
    />
  )
}
