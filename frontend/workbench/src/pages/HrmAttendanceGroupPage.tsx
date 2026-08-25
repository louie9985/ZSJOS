import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Form, Input, InputNumber, Modal, Pagination, Select, Space, Switch, Tag, TimePicker, message } from 'antd'
import { PlusOutlined, MinusCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmAttendanceGroup, type HrmAttendanceShift } from '../services/api'
import type { ColumnsType } from 'antd/es/table'
import DeptTreeSelect from '../components/DeptTreeSelect'
import HrmEmployeePicker from '../components/HrmEmployeePicker'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

type ShiftFormValue = {
  weeks?: number[]; startTime?: unknown; endTime?: unknown
  clockInStartTime?: unknown; clockInEndTime?: unknown
  clockOutStartTime?: unknown; clockOutEndTime?: unknown
  restStartTime?: unknown; restEndTime?: unknown; excludeRestTime?: boolean
}

type GroupFormValues = {
  name: string; rest?: boolean; defaultStatus?: boolean
  deptIds?: number[]; employeeIds?: number[]
  shifts?: ShiftFormValue[]
}

const WEEK_OPTIONS = [
  { value: 1, label: '一' }, { value: 2, label: '二' }, { value: 3, label: '三' },
  { value: 4, label: '四' }, { value: 5, label: '五' }, { value: 6, label: '六' }, { value: 7, label: '日' }
]

function fmtTime(value?: string | null) { return value ? value.slice(0, 5) : '-' }

/** 后端时间字符串 ↔ dayjs（TimePicker 需要）。 */
function timeToDayjs(value?: string | null) {
  return value ? dayjs(`2000-01-01T${value}`) : undefined
}
function dayjsToString(value: unknown) {
  if (!value) return undefined
  return dayjs.isDayjs(value) ? value.format('HH:mm:ss') : String(value)
}

/** 考勤组设置：列表 + 详情（班次/扣款规则）+ 新建/编辑。班次用 Form.List 管理。 */
export default function HrmAttendanceGroupPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmAttendanceGroup[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [detail, setDetail] = useState<HrmAttendanceGroup>()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmAttendanceGroup>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<GroupFormValues>()

  const canCreate = permissions.includes('hrm:attendance:group:create')
  const canUpdate = permissions.includes('hrm:attendance:group:update')
  const canDelete = permissions.includes('hrm:attendance:group:delete')

  const loadPage = useCallback(async (page: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.attendance.group.page({ pageNo: page, pageSize: PAGE_SIZE })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '考勤组加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo) }, [loadPage, pageNo])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1) }, [loadPage])

  const openForm = async (row?: HrmAttendanceGroup) => {
    if (row) {
      const detail = await api.hrm.attendance.group.get(row.id!).catch(() => row)
      setEditing(detail)
      form.setFieldsValue({
        name: detail.name, rest: detail.rest, defaultStatus: detail.defaultStatus,
        deptIds: detail.deptIds || [], employeeIds: detail.employeeIds || [],
        shifts: (detail.shifts || []).map(shift => ({
          weeks: shift.weeks, excludeRestTime: shift.excludeRestTime,
          startTime: timeToDayjs(shift.startTime), endTime: timeToDayjs(shift.endTime),
          clockInStartTime: timeToDayjs(shift.clockInStartTime), clockInEndTime: timeToDayjs(shift.clockInEndTime),
          clockOutStartTime: timeToDayjs(shift.clockOutStartTime), clockOutEndTime: timeToDayjs(shift.clockOutEndTime),
          restStartTime: timeToDayjs(shift.restStartTime), restEndTime: timeToDayjs(shift.restEndTime)
        }))
      })
    } else {
      setEditing(undefined)
      form.resetFields()
    }
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const payload: HrmAttendanceGroup = {
        name: values.name, rest: values.rest, defaultStatus: values.defaultStatus,
        deptIds: values.deptIds, employeeIds: values.employeeIds,
        shifts: values.shifts?.map(shift => ({
          weeks: shift.weeks || [],
          startTime: dayjsToString(shift.startTime)!,
          endTime: dayjsToString(shift.endTime)!,
          clockInStartTime: dayjsToString(shift.clockInStartTime)!,
          clockInEndTime: dayjsToString(shift.clockInEndTime)!,
          clockOutStartTime: dayjsToString(shift.clockOutStartTime)!,
          clockOutEndTime: dayjsToString(shift.clockOutEndTime)!,
          restStartTime: dayjsToString(shift.restStartTime),
          restEndTime: dayjsToString(shift.restEndTime),
          excludeRestTime: shift.excludeRestTime ?? false
        }))
      }
      if (editing) await api.hrm.attendance.group.update({ ...payload, id: editing.id })
      else await api.hrm.attendance.group.create(payload)
      message.success(editing ? '已保存' : '已创建')
      setFormOpen(false); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const handleDelete = (row: HrmAttendanceGroup) => {
    Modal.confirm({ title: '删除考勤组', content: `确定删除「${row.name}」吗？`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.attendance.group.delete(row.id!); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      } })
  }

  const columns: ColumnsType<HrmAttendanceGroup> = [
    { title: '考勤组名称', dataIndex: 'name', width: 180, render: (value: string) => value },
    { title: '默认为', dataIndex: 'defaultStatus', width: 90, align: 'center', render: (value?: boolean) => value ? <Tag color="success">默认</Tag> : <Tag>普通</Tag> },
    { title: '法定休息', dataIndex: 'rest', width: 90, align: 'center', render: (value?: boolean) => value ? <Tag>是</Tag> : <Tag>否</Tag> },
    { title: '班次', width: 120, align: 'center', render: (_, row) => `${row.shifts?.length || 0} 个` },
    { title: '适用对象', width: 200, render: (_, row) => {
      const count = (row.deptNames?.length || 0) + (row.employeeNames?.length || 0)
      return count ? <Tag>{count} 个部门/员工</Tag> : <span className="hrm-muted">全部</span>
    } },
    { title: '操作', width: 150, align: 'center', render: (_, row) => <Space size="small">
      <Button type="link" size="small" onClick={() => setDetail(row)}>详情</Button>
      {canUpdate && <Button type="link" size="small" onClick={() => void openForm(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  const shiftColumns: ColumnsType<HrmAttendanceShift> = [
    { title: '工作日', dataIndex: 'weeks', width: 120, render: (value: number[]) => value?.length ? value.map(w => WEEK_OPTIONS.find(o => o.value === w)?.label).join('、') : '-' },
    { title: '上班', dataIndex: 'startTime', width: 90, render: fmtTime },
    { title: '下班', dataIndex: 'endTime', width: 90, render: fmtTime },
    { title: '上班打卡', width: 180, render: (_, row) => `${row.clockInStartTime} ~ ${row.clockInEndTime}` },
    { title: '休息', width: 130, render: (_, row) => row.restStartTime && row.restEndTime ? `${row.restStartTime} ~ ${row.restEndTime}` : '-' }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无考勤组"/>
        : <>
          <HrmProTable<HrmAttendanceGroup> advanced persistenceKey="attendance-group" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-attendance-group-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openForm()}>新增考勤组</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.name || '考勤组'} width="min(840px, 96vw)" open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detail && <>
        <Descriptions className="hrm-summary" size="small" column={2} bordered items={[
          { key: 'name', label: '名称', children: detail.name },
          { key: 'default', label: '默认为', children: detail.defaultStatus ? <Tag color="success">默认</Tag> : <Tag>普通</Tag> },
          { key: 'rest', label: '法定休息', children: detail.rest ? '是' : '否' },
          { key: 'scope', label: '适用对象', children: (detail.deptNames?.length || detail.employeeNames?.length) ? `${detail.deptNames?.length || 0} 部门 / ${detail.employeeNames?.length || 0} 员工` : '全部' }
        ]}/>
        <h4 className="hrm-drawer-subtitle">班次</h4>
        {detail.shifts?.length ? <HrmProTable rowKey="weeks" size="small" columns={shiftColumns} dataSource={detail.shifts} pagination={false}/> : <Empty description="未配置班次"/>}
        {detail.deductRule && <h4 className="hrm-drawer-subtitle">扣款规则</h4>}
        {detail.deductRule && <Descriptions column={2} size="small" bordered items={[
          { key: 'late', label: '迟到', children: `方式 ${detail.deductRule.lateMethod}，扣 ${detail.deductRule.lateDeductMoney} 元` },
          { key: 'early', label: '早退', children: `方式 ${detail.deductRule.earlyMethod}，扣 ${detail.deductRule.earlyDeductMoney} 元` },
          { key: 'absent', label: '旷工', children: `方式 ${detail.deductRule.absenteeismMethod}，扣 ${detail.deductRule.absenteeismDeductMoney} 元` },
          { key: 'misscard', label: '缺卡', children: `方式 ${detail.deductRule.misscardMethod}，扣 ${detail.deductRule.misscardDeductMoney} 元` }
        ]}/>}
      </>}
    </Drawer>

    <Modal title={editing ? '编辑考勤组' : '新增考勤组'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(960px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="考勤组名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input placeholder="如 早班 9:00-18:00"/>
        </Form.Item>
        <Space size="large">
          <Form.Item name="rest" label="法定节假日休息" valuePropName="checked"><Switch/></Form.Item>
          <Form.Item name="defaultStatus" label="设为默认考勤组" valuePropName="checked"><Switch/></Form.Item>
        </Space>
        <Form.Item name="deptIds" label="适用部门">
          <DeptTreeSelect multiple treeCheckable placeholder="不选则全部"/>
        </Form.Item>
        <Form.Item name="employeeIds" label="适用员工">
          <HrmEmployeePicker mode="multiple" placeholder="不选则全部"/>
        </Form.Item>
        <Form.Item label="班次配置">
          <Form.List name="shifts">
            {(fields, { add, remove }) => <>
              {fields.map(({ key, name, ...rest }) => (
                <div key={key} className="hrm-shift-row">
                  <Space wrap>
                    <Form.Item {...rest} name={[name, 'weeks']} label="工作日" rules={[{ required: true, message: '请选择工作日' }]}>
                      <Select mode="multiple" options={WEEK_OPTIONS} placeholder="选择星期" style={{ width: 180 }}/>
                    </Form.Item>
                    <Form.Item {...rest} name={[name, 'startTime']} label="上班"><TimePicker format="HH:mm"/></Form.Item>
                    <Form.Item {...rest} name={[name, 'endTime']} label="下班"><TimePicker format="HH:mm"/></Form.Item>
                    <Form.Item {...rest} name={[name, 'clockInStartTime']} label="上班打卡起始"><TimePicker format="HH:mm"/></Form.Item>
                    <Form.Item {...rest} name={[name, 'clockInEndTime']} label="上班打卡截止"><TimePicker format="HH:mm"/></Form.Item>
                    <Form.Item {...rest} name={[name, 'clockOutStartTime']} label="下班打卡起始"><TimePicker format="HH:mm"/></Form.Item>
                    <Form.Item {...rest} name={[name, 'clockOutEndTime']} label="下班打卡截止"><TimePicker format="HH:mm"/></Form.Item>
                    <Form.Item {...rest} name={[name, 'excludeRestTime']} label="休息不计时" valuePropName="checked"><Switch/></Form.Item>
                  </Space>
                  <Button type="link" danger icon={<MinusCircleOutlined/>} onClick={() => remove(name)}>移除该班次</Button>
                </div>
              ))}
              <Button type="dashed" block icon={<PlusOutlined/>} onClick={() => add({ weeks: [1, 2, 3, 4, 5] })}>添加班次</Button>
            </>}
          </Form.List>
        </Form.Item>
        <Alert message="打卡地点、WiFi 与特殊日期请在管理后台进一步配置" type="info" showIcon/>
      </Form>
    </Modal>
  </section>
}
