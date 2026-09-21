import { Alert, Button, Empty, Result, Space, Typography } from 'antd'
import type { TableProps } from 'antd'
import { ProTable } from '@ant-design/pro-components'
import type { ProColumns, ProTableProps } from '@ant-design/pro-components'
import type { ReactNode } from 'react'
import { columnKey, fromNativeColumns } from './columns'
import { useColumnWidths } from './useColumnWidths'

type BaseProps = {
  tableKey: string
  mode?: 'full' | 'compact'
  filters?: ReactNode
  actions?: ReactNode
  batchActions?: ReactNode
  onReload?: () => void | Promise<unknown>
  error?: string
  unauthorized?: boolean
  emptyText?: ReactNode
  /** Keeps existing users' column-width preferences during migration. */
  widthPersistenceKey?: string
  resizable?: boolean
}
type ProProps<T extends object> = BaseProps & ProTableProps<T, Record<string, unknown>> & { columnMode?: 'pro' }
type NativeProps<T extends object> = BaseProps & TableProps<T> & { columnMode: 'native' }
export type BusinessTableProps<T extends object> = ProProps<T> | NativeProps<T>

export default function BusinessTable<T extends object>(props: NativeProps<T>): ReactNode
export default function BusinessTable<T extends object>(props: ProProps<T>): ReactNode
export default function BusinessTable<T extends object>(props: BusinessTableProps<T>): ReactNode {
  const {
    tableKey, mode = 'full', filters, actions, batchActions, onReload, error, unauthorized,
    emptyText = '暂无数据', widthPersistenceKey, resizable = true, columnMode,
    columns: source = [], className, pagination, scroll, locale, ...rest
  } = props
  // Both entry forms converge here. Native render callbacks must retain their raw-value contract.
  const proProps = rest as ProTableProps<T, Record<string, unknown>>
  const sourceColumns = columnMode === 'native' ? fromNativeColumns(source as NonNullable<TableProps<T>['columns']>) : source as ProColumns<T>[]
  const { widths, headerProps } = useColumnWidths(widthPersistenceKey || `crm-table:${tableKey}:widths`)
  function prepare(columns: ProColumns<T>[], prefix = ''): ProColumns<T>[] {
    return columns.map((column, index) => {
      const key = `${prefix}${columnKey(column, index)}`
      if (column.children) return { ...column, key, children: prepare(column.children, `${key}.`) }
      const width = widths[key] ?? (typeof column.width === 'number' ? column.width : 160)
      const isAction = column.key === 'action' || column.valueType === 'option'
      return {
        ...column, key, width,
        ellipsis: column.ellipsis ?? !isAction,
        ...(isAction ? { fixed: column.fixed ?? 'right', hideInSetting: true, className: [column.className, 'business-table-action-cell'].filter(Boolean).join(' ') } : {}),
        onHeaderCell: resizable && !isAction ? col => {
          const original = column.onHeaderCell?.(col) || {}
          const resizeProps = headerProps(key, width)
          return {
            ...original, ...resizeProps,
            className: [original.className, resizeProps.className].filter(Boolean).join(' '),
            onPointerDownCapture: event => { resizeProps.onPointerDownCapture?.(event); if (!event.defaultPrevented) original.onPointerDownCapture?.(event) },
            onClickCapture: event => { resizeProps.onClickCapture?.(event); if (!event.defaultPrevented) original.onClickCapture?.(event) },
            onPointerMove: event => { original.onPointerMove?.(event); resizeProps.onPointerMove?.(event) },
            onPointerLeave: event => { original.onPointerLeave?.(event); resizeProps.onPointerLeave?.(event) },
          }
        } : column.onHeaderCell,
      }
    })
  }
  const columns = prepare(sourceColumns)
  function totalWidth(items: ProColumns<T>[]): number {
    return items.reduce((sum, column) => sum + (column.children ? totalWidth(column.children) : Number(column.width)), 0)
  }
  const selectedCount = props.rowSelection && props.rowSelection.selectedRowKeys?.length || 0
  const toolbar = filters || actions || batchActions ? () => [
    <div className="business-table-toolbar" key="business-toolbar">
      {batchActions && <Space className="business-table-batch"><Typography.Text type="secondary">已选 {selectedCount} 条</Typography.Text>{batchActions}</Space>}
      {filters && <div className="business-table-filters">{filters}</div>}
      {actions && <Space wrap className="business-table-actions">{actions}</Space>}
    </div>,
  ] : proProps.toolBarRender
  const defaults = { density: true, fullScreen: true, setting: true, reload: onReload ? () => { void onReload() } : false as const }
  const options = mode === 'compact' ? false : { ...defaults, ...(proProps.options || {}) }
  return <div className={['business-table', `business-table--${mode}`, className].filter(Boolean).join(' ')} data-table-key={tableKey}>
    {unauthorized ? <Result status="403" title="无权查看此表格" subTitle={error} /> : <>
      {error && <Alert className="business-table-error" type="error" showIcon title="加载失败" description={error}
        action={onReload && <Button size="small" onClick={() => { void onReload() }}>重试</Button>} />}
      <ProTable<T, Record<string, unknown>>
        {...proProps}
        search={false}
        cardBordered={false}
        cardProps={mode === 'compact' ? { styles: { body: { padding: 0 } } } : undefined}
        columns={columns}
        options={options}
        toolBarRender={mode === 'compact' && !toolbar ? false : toolbar}
        columnsState={proProps.columnsState || (mode === 'full' ? { persistenceKey: `crm-table:${tableKey}:columns`, persistenceType: 'localStorage' } : undefined)}
        pagination={pagination === false ? false : { defaultPageSize: 20, showSizeChanger: pagination?.current === undefined, pageSizeOptions: [20, 50, 100], ...pagination }}
        scroll={{ ...scroll, x: Math.max(totalWidth(columns) + (props.rowSelection ? 48 : 0), typeof scroll?.x === 'number' ? scroll.x : 0) }}
        locale={{ ...locale, emptyText: error ? '数据加载失败，请重试' : locale?.emptyText ?? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} /> }}
        tableAlertRender={false}
        tableAlertOptionRender={false}
      />
    </>}
  </div>
}
