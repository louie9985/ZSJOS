import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Pagination, Select, Skeleton, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmLeaveItem } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, LEAVE_APPROVAL_STATUS, LEAVE_APPROVAL_STATUS_COLORS, LEAVE_APPROVAL_STATUS_LABELS } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

const APPROVAL_STATUS_OPTIONS = Object.values(LEAVE_APPROVAL_STATUS).map(value => ({
  value, label: LEAVE_APPROVAL_STATUS_LABELS[value]
}))

/** 管理端请假记录：全员请假查询与详情查看。审批在 BPM 流程中完成，此处只读。 */
export default function HrmLeavePage() {
  const [items, setItems] = useState<HrmLeaveItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [filterType, setFilterType] = useState<string>()
  const [filterStatus, setFilterStatus] = useState<number>()

  const [detail, setDetail] = useState<HrmLeaveItem>()
  const [detailLoading, setDetailLoading] = useState(false)

  const leaveType = useDict(HRM_DICT.LEAVE_TYPE)

  const loadPage = useCallback(async (page: number, type?: string, approvalStatus?: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.attendance.leave.page({ pageNo: page, pageSize: PAGE_SIZE, type, approvalStatus })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '请假记录加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, filterType, filterStatus) }, [loadPage, pageNo, filterType, filterStatus])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, filterType, filterStatus) }, [loadPage, filterType, filterStatus])

  const openDetail = async (row: HrmLeaveItem) => {
    setDetail(row); setDetailLoading(true)
    try { setDetail(await api.hrm.attendance.leave.get(row.id)) }
    catch (e) { message.error(e instanceof Error ? e.message : '详情加载失败') }
    finally { setDetailLoading(false) }
  }

  const columns: ColumnsType<HrmLeaveItem> = [
    { title: '员工', dataIndex: 'employeeName', width: 110, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 140, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '请假类型', dataIndex: 'type', width: 110, render: (value: string) => leaveType.labels[value] || value },
    { title: '开始时间', dataIndex: 'startTime', width: 170, render: fmtTime },
    { title: '结束时间', dataIndex: 'endTime', width: 170, render: fmtTime },
    { title: '天数', dataIndex: 'day', width: 80, align: 'right', render: (value: number) => `${value} 天` },
    {
      title: '审批状态', dataIndex: 'approvalStatus', width: 110, align: 'center',
      render: (value?: number) => value != null
        ? <Tag color={LEAVE_APPROVAL_STATUS_COLORS[value] || 'default'}>{LEAVE_APPROVAL_STATUS_LABELS[value] || value}</Tag>
        : '-'
    },
    { title: '申请时间', dataIndex: 'createTime', width: 170, render: fmtTime },
    {
      title: '操作', width: 90, align: 'center', fixed: 'right',
      render: (_, row) => <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
    }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无请假记录"/>
        : <>
          <HrmProTable<HrmLeaveItem> advanced persistenceKey="leave" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1300 }} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-leave-page">
    <div className="page-heading">
      <Space wrap>
        <Select allowClear placeholder="请假类型" value={filterType} onChange={value => { setFilterType(value); setPageNo(1) }}
          style={{ width: 140 }} loading={leaveType.loading}
          options={leaveType.items.map(item => ({ value: item.value, label: item.label }))}/>
        <Select allowClear placeholder="审批状态" value={filterStatus} onChange={value => { setFilterStatus(value); setPageNo(1) }}
          style={{ width: 130 }} options={APPROVAL_STATUS_OPTIONS}/>
      </Space>
      <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
    </div>
    {leaveType.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`请假类型字典加载失败：${leaveType.error}`} action={<Button size="small" onClick={leaveType.reload}>重试</Button>}/>}
    <div className="hrm-table-area">{content}</div>

    <Drawer title="请假详情" width="min(760px, 96vw)" open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detailLoading && !detail ? <Skeleton active paragraph={{ rows: 8 }}/> : detail && <Descriptions column={1} bordered size="small" items={[
        { key: 'employee', label: '员工', children: `${detail.employeeName || '-'}${detail.jobNumber ? `（${detail.jobNumber}）` : ''}` },
        { key: 'dept', label: '部门', children: detail.deptName || '-' },
        { key: 'post', label: '职位', children: detail.postName || '-' },
        { key: 'type', label: '请假类型', children: leaveType.labels[detail.type] || detail.type },
        { key: 'start', label: '开始时间', children: fmtTime(detail.startTime) },
        { key: 'end', label: '结束时间', children: fmtTime(detail.endTime) },
        { key: 'day', label: '请假天数', children: `${detail.day} 天` },
        { key: 'reason', label: '请假事由', children: detail.reason || '-' },
        { key: 'remark', label: '备注', children: detail.remark || '-' },
        {
          key: 'status', label: '审批状态',
          children: detail.approvalStatus != null
            ? <Tag color={LEAVE_APPROVAL_STATUS_COLORS[detail.approvalStatus] || 'default'}>{LEAVE_APPROVAL_STATUS_LABELS[detail.approvalStatus] || detail.approvalStatus}</Tag>
            : '-'
        },
        { key: 'approvalTime', label: '审批时间', children: fmtTime(detail.approvalTime) },
        { key: 'approvalReason', label: '审批意见', children: detail.approvalReason || '-' },
        { key: 'createTime', label: '申请时间', children: fmtTime(detail.createTime) }
      ]}/>}
    </Drawer>
  </section>
}
