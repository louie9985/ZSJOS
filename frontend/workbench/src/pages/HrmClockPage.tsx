import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Empty, Form, Input, Modal, Pagination, Select, Skeleton, Space, Tag, message } from 'antd'
import { DeleteOutlined, DownloadOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmClockItem, type HrmClockSave } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { downloadBlob } from '../services/download'

const PAGE_SIZE = 10

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

type ClockFormValues = Omit<HrmClockSave, 'clockTime' | 'attendanceTime'> & {
  clockTime?: dayjs.Dayjs
  attendanceTime?: dayjs.Dayjs
}

/** 管理端打卡记录：全员打卡查询与补卡维护。 */
export default function HrmClockPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmClockItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [filterType, setFilterType] = useState<number>()
  const [filterStatus, setFilterStatus] = useState<number>()

  const [editOpen, setEditOpen] = useState(false)
  const [editLoading, setEditLoading] = useState(false)
  const [editForm] = Form.useForm<ClockFormValues>()
  const [editing, setEditing] = useState<HrmClockItem>()
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [exporting, setExporting] = useState(false)

  const clockType = useDict(HRM_DICT.CLOCK_TYPE)
  const clockStatus = useDict(HRM_DICT.CLOCK_STATUS)
  const clockSource = useDict(HRM_DICT.CLOCK_SOURCE)

  const canCreate = permissions.includes('hrm:attendance:clock:create')
  const canUpdate = permissions.includes('hrm:attendance:clock:update')
  const canDelete = permissions.includes('hrm:attendance:clock:delete')
  const canExport = permissions.includes('hrm:attendance:clock:export')

  const loadPage = useCallback(async (page: number, type?: number, status?: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.attendance.clock.page({ pageNo: page, pageSize: PAGE_SIZE, type, status })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '打卡记录加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, filterType, filterStatus) }, [loadPage, pageNo, filterType, filterStatus])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, filterType, filterStatus) }, [loadPage, filterType, filterStatus])

  const openEdit = (row?: HrmClockItem) => {
    setEditing(row)
    editForm.setFieldsValue(row
      ? {
        id: row.id, employeeId: row.employeeId, type: row.type as number,
        clockTime: row.clockTime ? dayjs(row.clockTime) : undefined,
        attendanceTime: row.attendanceTime ? dayjs(row.attendanceTime) : undefined,
        sourceType: row.sourceType, status: row.status, address: row.address, remark: row.remark
      }
      : { id: undefined, employeeId: undefined, type: undefined as unknown as number, clockTime: undefined, attendanceTime: undefined, sourceType: undefined, status: undefined, address: undefined, remark: undefined })
    setEditOpen(true)
  }

  const handleSave = async () => {
    const values = await editForm.validateFields()
    setEditLoading(true)
    try {
      const payload: HrmClockSave = {
        ...values,
        clockTime: values.clockTime?.valueOf(),
        attendanceTime: values.attendanceTime?.valueOf()
      }
      if (editing) await api.hrm.attendance.clock.update({ ...payload, id: editing.id })
      else await api.hrm.attendance.clock.create(payload)
      message.success(editing ? '已保存' : '补卡成功')
      setEditOpen(false); editForm.resetFields(); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setEditLoading(false) }
  }

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除', content: '确定要删除该打卡记录吗？删除后月度考勤汇总会随之变化。', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.attendance.clock.delete(id); message.success('已删除'); setSelectedIds(current => current.filter(item => item !== id)); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const handleBatchDelete = () => {
    if (!selectedIds.length) { message.warning('请选择需要删除的打卡记录'); return }
    Modal.confirm({ title: '批量删除打卡记录', content: `确定删除已选择的 ${selectedIds.length} 条打卡记录吗？月度考勤汇总会随之变化。`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try {
          await api.hrm.attendance.clock.deleteList(selectedIds)
          message.success('已批量删除'); setSelectedIds([]); reload()
        } catch (e) { message.error(e instanceof Error ? e.message : '批量删除失败'); throw e }
      } })
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      await downloadBlob('/hrm/attendance/clock/export-excel', `打卡记录-${dayjs().format('YYYYMMDD')}.xls`, {
        type: filterType, status: filterStatus
      })
    } catch (e) { message.error(e instanceof Error ? e.message : '导出失败') }
    finally { setExporting(false) }
  }

  const columns: ColumnsType<HrmClockItem> = [
    { title: '员工', dataIndex: 'employeeName', width: 110, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 140, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '打卡时间', dataIndex: 'clockTime', width: 170, render: fmtTime },
    { title: '应打卡时间', dataIndex: 'attendanceTime', width: 170, render: fmtTime },
    { title: '类型', dataIndex: 'type', width: 90, render: (value?: number) => value != null ? (clockType.labels[String(value)] || value) : '-' },
    { title: '来源', dataIndex: 'sourceType', width: 100, render: (value?: number) => value != null ? (clockSource.labels[String(value)] || value) : '-' },
    { title: '状态', dataIndex: 'status', width: 110, align: 'center', render: (value?: number) => value != null ? <Tag>{clockStatus.labels[String(value)] || value}</Tag> : '-' },
    { title: '打卡地点', dataIndex: 'address', width: 180, ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: '操作', width: 130, align: 'center', fixed: 'right',
      render: (_, row) => <Space size="small">
        {canUpdate && <Button type="link" size="small" onClick={() => openEdit(row)}>编辑</Button>}
        {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row.id)}>删除</Button>}
      </Space>
    }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无打卡记录"/>
        : <>
          <HrmProTable<HrmClockItem> advanced persistenceKey="clock" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1400 }} loading={loading}
            rowSelection={canDelete ? { selectedRowKeys: selectedIds, onChange: keys => setSelectedIds(keys.map(Number)) } : undefined}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-clock-page">
    <div className="page-heading">
      <Space wrap>
        <Select allowClear placeholder="打卡类型" value={filterType} onChange={value => { setFilterType(value); setPageNo(1) }}
          style={{ width: 130 }} loading={clockType.loading} options={clockType.options}/>
        <Select allowClear placeholder="打卡状态" value={filterStatus} onChange={value => { setFilterStatus(value); setPageNo(1) }}
          style={{ width: 130 }} loading={clockStatus.loading} options={clockStatus.options}/>
      </Space>
      <Space>
        {selectedIds.length > 0 && canDelete && <Button danger icon={<DeleteOutlined/>} onClick={handleBatchDelete}>批量删除（{selectedIds.length}）</Button>}
        {canExport && <Button icon={<DownloadOutlined/>} loading={exporting} onClick={() => void handleExport()}>导出</Button>}
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => openEdit()}>补卡登记</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {clockType.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`打卡类型字典加载失败：${clockType.error}`} action={<Button size="small" onClick={clockType.reload}>重试</Button>}/>}
    <div className="hrm-table-area">{content}</div>

    <Modal title={editing ? '编辑打卡记录' : '补卡登记'} open={editOpen} onCancel={() => setEditOpen(false)}
      onOk={handleSave} confirmLoading={editLoading} width="min(840px, 96vw)" destroyOnClose>
      <Form form={editForm} layout="vertical">
        <Form.Item name="employeeId" label="员工编号" rules={[{ required: !editing, message: '请输入员工编号' }]}>
          <Input placeholder="请输入员工编号" disabled={!!editing}/>
        </Form.Item>
        <Form.Item name="type" label="打卡类型" rules={[{ required: true, message: '请选择打卡类型' }]}>
          <Select placeholder="请选择" loading={clockType.loading} options={clockType.options}/>
        </Form.Item>
        <Form.Item name="clockTime" label="打卡时间" rules={[{ required: true, message: '请选择打卡时间' }]}>
          <DatePicker showTime style={{ width: '100%' }}/>
        </Form.Item>
        <Form.Item name="attendanceTime" label="应打卡时间">
          <DatePicker showTime style={{ width: '100%' }} placeholder="留空则由后端按班次推算"/>
        </Form.Item>
        <Form.Item name="status" label="打卡状态">
          <Select allowClear placeholder="留空则由后端判定" loading={clockStatus.loading} options={clockStatus.options}/>
        </Form.Item>
        <Form.Item name="address" label="打卡地点">
          <Input placeholder="选填"/>
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} placeholder="建议注明补卡原因"/>
        </Form.Item>
        <Alert message="补卡会影响该员工当月的考勤汇总与考勤扣款，请谨慎操作" type="warning" showIcon/>
      </Form>
    </Modal>
  </section>
}
