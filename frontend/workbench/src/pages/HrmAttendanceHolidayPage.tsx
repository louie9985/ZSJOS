import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Empty, Form, Modal, Pagination, Select, Space, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmAttendanceHoliday } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 15

function fmtDate(value?: number | null) {
  if (!value) return '-'
  return dayjs(value).format('YYYY-MM-DD')
}

/** 考勤节假日设置：维护法定节假日/调休关键日。 */
export default function HrmAttendanceHolidayPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmAttendanceHoliday[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [addOpen, setAddOpen] = useState(false)
  const [addLoading, setAddLoading] = useState(false)
  const [editing, setEditing] = useState<HrmAttendanceHoliday>()
  const [addForm] = Form.useForm<{ date: dayjs.Dayjs; type: number }>()

  const holidayType = useDict(HRM_DICT.HOLIDAY_TYPE)
  const canCreate = permissions.includes('hrm:attendance:holiday:create')
  const canUpdate = permissions.includes('hrm:attendance:holiday:update')
  const canDelete = permissions.includes('hrm:attendance:holiday:delete')

  const loadPage = useCallback(async (page: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.attendance.holiday.page({ pageNo: page, pageSize: PAGE_SIZE })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '节假日加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo) }, [loadPage, pageNo])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1) }, [loadPage])

  const handleAdd = async () => {
    const values = await addForm.validateFields()
    setAddLoading(true)
    try {
      if (editing) await api.hrm.attendance.holiday.update({ id: editing.id, date: values.date.valueOf(), type: values.type })
      else await api.hrm.attendance.holiday.create({ date: values.date.valueOf(), type: values.type })
      message.success(editing ? '已保存' : '已添加')
      setAddOpen(false); addForm.resetFields(); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '添加失败') }
    finally { setAddLoading(false) }
  }

  const handleDelete = (row: HrmAttendanceHoliday) => {
    Modal.confirm({ title: '删除节假日', content: '确定删除该日期吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.attendance.holiday.delete(row.id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      } })
  }

  const columns: ColumnsType<HrmAttendanceHoliday> = [
    { title: '日期', dataIndex: 'date', width: 200, render: fmtDate },
    { title: '日期类型', dataIndex: 'type', width: 160, render: (value?: number) => value != null ? (holidayType.labels[String(value)] || value) : '-' },
    { title: '操作', width: 140, align: 'center', render: (_, row) => <Space>{canUpdate && <Button type="link" size="small" onClick={() => { setEditing(row); addForm.setFieldsValue({ date: row.date ? dayjs(row.date) : dayjs(), type: row.type }); setAddOpen(true) }}>编辑</Button>}{canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}</Space> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无节假日"/>
        : <>
          <HrmProTable<HrmAttendanceHoliday> advanced persistenceKey="attendance-holiday" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-attendance-holiday-page">
    <div className="page-heading">
      <span className="hrm-muted">配置法定节假日与调休，影响考勤判定</span>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => { setEditing(undefined); addForm.resetFields(); setAddOpen(true) }}>添加日期</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title={editing ? '编辑节假日' : '添加节假日'} open={addOpen} onCancel={() => setAddOpen(false)} onOk={() => void handleAdd()}
      confirmLoading={addLoading} width="min(720px, 96vw)" destroyOnClose>
      <Form form={addForm} layout="vertical">
        <Form.Item name="date" label="日期" rules={[{ required: true, message: '请选择日期' }]}>
          <DatePicker style={{ width: '100%' }} placeholder="选择节假日日期"/>
        </Form.Item>
        <Form.Item name="type" label="日期类型" rules={[{ required: true, message: '请选择类型' }]}>
          <Select placeholder="请选择" loading={holidayType.loading}
            options={holidayType.items.map(item => ({ value: Number(item.value), label: item.label }))}/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
