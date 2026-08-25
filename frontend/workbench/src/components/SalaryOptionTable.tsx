import HrmProTable from './HrmProTable'
import { Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { HrmSalaryOption } from '../services/api'
import { fmtAmount } from '../services/hrm'

/** 摊平一层 children，父项在前、子项缩进跟随，保持后端给定的 sort 顺序 */
type FlatOption = HrmSalaryOption & { key: string; depth: number }

function flatten(options: HrmSalaryOption[], depth = 0, prefix = ''): FlatOption[] {
  return options.flatMap((option, index) => {
    const key = `${prefix}${index}`
    const self: FlatOption = { ...option, key, depth }
    return option.children?.length ? [self, ...flatten(option.children, depth + 1, `${key}-`)] : [self]
  })
}

/**
 * 工资项明细表。员工端工资条与管理端发放记录共用，
 * 因此不接权限参数，纯展示。
 */
export default function SalaryOptionTable({ options, typeLabels }: {
  options?: HrmSalaryOption[]
  typeLabels?: Record<string, string>
}) {
  const rows = flatten(options || [])

  const columns: ColumnsType<FlatOption> = [
    {
      title: '工资项', dataIndex: 'name',
      render: (value: string | undefined, row) => <span style={{ paddingInlineStart: row.depth * 16 }}>{value || '-'}</span>
    },
    {
      title: '类型', dataIndex: 'type', width: 110,
      render: (value?: number) => value != null ? (typeLabels?.[String(value)] || value) : '-'
    },
    {
      title: '金额', dataIndex: 'value', width: 130, align: 'right',
      render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-'
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (value?: string) => value || '-' }
  ]

  return <HrmProTable<FlatOption> rowKey="key" size="small" columns={columns} dataSource={rows} pagination={false}/>
}
