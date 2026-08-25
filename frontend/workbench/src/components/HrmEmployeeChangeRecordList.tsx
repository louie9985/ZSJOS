import HrmProTable from './HrmProTable'
import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Skeleton, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { api, type HrmEmployeeChangeRecord } from '../services/api'
import { CHANGE_REASON_LABELS, CHANGE_TYPE_LABELS } from '../services/hrm'

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }
function changed(oldValue?: string, newValue?: string) {
  if (!oldValue && !newValue) return '-'
  return `${oldValue || '-'} → ${newValue || '-'}`
}

export default function HrmEmployeeChangeRecordList({ employeeId }: { employeeId: number }) {
  const [items, setItems] = useState<HrmEmployeeChangeRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setItems(await api.hrm.employee.changeRecord.list(employeeId)) }
    catch (e) { setError(e instanceof Error ? e.message : '异动记录加载失败') }
    finally { setLoading(false) }
  }, [employeeId])
  useEffect(() => { void load() }, [load])

  const columns: ColumnsType<HrmEmployeeChangeRecord> = [
    { title: '异动类型', dataIndex: 'type', width: 100, render: (value?: number) => value != null ? <Tag>{CHANGE_TYPE_LABELS[value] || value}</Tag> : '-' },
    { title: '异动原因', dataIndex: 'reason', width: 120, render: (value?: number) => value != null ? (CHANGE_REASON_LABELS[value] || value) : '-' },
    { title: '部门', width: 190, render: (_, row) => changed(row.oldDeptName, row.newDeptName) },
    { title: '职位', width: 190, render: (_, row) => changed(row.oldPostName, row.newPostName) },
    { title: '职级', width: 150, render: (_, row) => changed(row.oldPostLevel, row.newPostLevel) },
    { title: '直属上级', width: 190, render: (_, row) => changed(row.oldLeaderEmployeeName, row.newLeaderEmployeeName) },
    { title: '生效时间', dataIndex: 'effectTime', width: 160, render: fmtTime },
    { title: '备注', dataIndex: 'remark', width: 180, ellipsis: true, render: (value?: string) => value || '-' }
  ]

  if (loading && !items.length) return <Skeleton active paragraph={{ rows: 6 }}/>
  if (error) return <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
  if (!items.length) return <Empty description="暂无异动记录"/>
  return <HrmProTable<HrmEmployeeChangeRecord> size="small" rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1200 }}/>
}
