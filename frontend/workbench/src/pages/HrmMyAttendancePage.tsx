import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Descriptions, Empty, Form, Input, InputNumber, Modal, Select, Skeleton, Space, Tabs, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmClockItem, type HrmLeaveItem } from '../services/api'
import { useDict } from '../services/useDict'
import {
  HRM_DICT, LEAVE_APPROVAL_STATUS_COLORS, LEAVE_APPROVAL_STATUS_LABELS,
  MONTH_OPTIONS, canCancelLeave, currentYearMonth, fmtMinutes, yearOptions
} from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

type LeaveFormValues = { type: string; range: [dayjs.Dayjs, dayjs.Dayjs]; day: number; reason?: string; remark?: string }

/** 员工端考勤：查看自己的打卡记录与月度汇总，提交/撤销自己的请假申请。 */
export default function HrmMyAttendancePage() {
  const initial = currentYearMonth()
  const [year, setYear] = useState(initial.year)
  const [month, setMonth] = useState(initial.month)

  const [clocks, setClocks] = useState<HrmClockItem[]>([])
  const [summary, setSummary] = useState<Awaited<ReturnType<typeof api.hrm.portal.attendance.monthDetail>>>()
  const [clockLoading, setClockLoading] = useState(false)
  const [clockError, setClockError] = useState('')
  const clockVersion = useRef(0)

  const [leaves, setLeaves] = useState<HrmLeaveItem[]>([])
  const [leaveLoading, setLeaveLoading] = useState(false)
  const [leaveError, setLeaveError] = useState('')
  const leaveVersion = useRef(0)

  const [applyOpen, setApplyOpen] = useState(false)
  const [applyLoading, setApplyLoading] = useState(false)
  const [applyForm] = Form.useForm<LeaveFormValues>()

  const clockType = useDict(HRM_DICT.CLOCK_TYPE)
  const clockStatus = useDict(HRM_DICT.CLOCK_STATUS)
  const leaveType = useDict(HRM_DICT.LEAVE_TYPE)

  const loadAttendance = useCallback(async (targetYear: number, targetMonth: number) => {
    const version = ++clockVersion.current
    setClockLoading(true); setClockError('')
    try {
      const [list, detail] = await Promise.all([
        api.hrm.portal.attendance.clockList({ year: targetYear, month: targetMonth }),
        api.hrm.portal.attendance.monthDetail({ year: targetYear, month: targetMonth })
      ])
      if (version !== clockVersion.current) return
      setClocks(list); setSummary(detail)
    } catch (e) {
      if (version === clockVersion.current) setClockError(e instanceof Error ? e.message : '考勤记录加载失败')
    } finally {
      if (version === clockVersion.current) setClockLoading(false)
    }
  }, [])

  const loadLeaves = useCallback(async () => {
    const version = ++leaveVersion.current
    setLeaveLoading(true); setLeaveError('')
    try {
      const list = await api.hrm.portal.attendance.leaveList()
      if (version !== leaveVersion.current) return
      setLeaves(list)
    } catch (e) {
      if (version === leaveVersion.current) setLeaveError(e instanceof Error ? e.message : '请假记录加载失败')
    } finally {
      if (version === leaveVersion.current) setLeaveLoading(false)
    }
  }, [])

  useEffect(() => { void loadAttendance(year, month) }, [loadAttendance, year, month])
  useEffect(() => { void loadLeaves() }, [loadLeaves])

  const handleApply = async () => {
    const values = await applyForm.validateFields()
    const [start, end] = values.range
    setApplyLoading(true)
    try {
      await api.hrm.portal.attendance.leaveCreate({
        type: values.type,
        startTime: start.valueOf(),
        endTime: end.valueOf(),
        day: values.day,
        reason: values.reason,
        remark: values.remark
      })
      message.success('请假申请已提交，等待审批')
      setApplyOpen(false); applyForm.resetFields(); void loadLeaves()
    } catch (e) { message.error(e instanceof Error ? e.message : '提交失败') }
    finally { setApplyLoading(false) }
  }

  const handleCancel = (row: HrmLeaveItem) => {
    let reason = ''
    Modal.confirm({
      title: '撤销请假申请',
      content: <div>
        <p>撤销后该申请将终止审批流程，需要重新提交。</p>
        <Input.TextArea rows={2} placeholder="请填写撤销原因" onChange={event => { reason = event.target.value }}/>
      </div>,
      okType: 'danger', okText: '撤销申请',
      onOk: async () => {
        if (!reason.trim()) { message.error('请填写撤销原因'); throw new Error('撤销原因为空') }
        try { await api.hrm.portal.attendance.leaveCancel(row.id, reason.trim()); message.success('已撤销'); void loadLeaves() }
        catch (e) { message.error(e instanceof Error ? e.message : '撤销失败'); throw e }
      }
    })
  }

  const clockColumns: ColumnsType<HrmClockItem> = [
    { title: '打卡时间', dataIndex: 'clockTime', width: 170, render: fmtTime },
    { title: '应打卡时间', dataIndex: 'attendanceTime', width: 170, render: fmtTime },
    { title: '类型', dataIndex: 'type', width: 100, render: (value?: number) => value != null ? (clockType.labels[String(value)] || value) : '-' },
    { title: '状态', dataIndex: 'status', width: 110, render: (value?: number) => value != null ? <Tag>{clockStatus.labels[String(value)] || value}</Tag> : '-' },
    { title: '打卡地点', dataIndex: 'address', ellipsis: true, render: (value?: string) => value || '-' },
    { title: '备注', dataIndex: 'remark', width: 160, ellipsis: true, render: (value?: string) => value || '-' }
  ]

  const leaveColumns: ColumnsType<HrmLeaveItem> = [
    { title: '请假类型', dataIndex: 'type', width: 120, render: (value: string) => leaveType.labels[value] || value },
    { title: '开始时间', dataIndex: 'startTime', width: 170, render: fmtTime },
    { title: '结束时间', dataIndex: 'endTime', width: 170, render: fmtTime },
    { title: '天数', dataIndex: 'day', width: 80, align: 'right', render: (value: number) => `${value} 天` },
    { title: '事由', dataIndex: 'reason', ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: '审批状态', dataIndex: 'approvalStatus', width: 110, align: 'center',
      render: (value?: number) => value != null
        ? <Tag color={LEAVE_APPROVAL_STATUS_COLORS[value] || 'default'}>{LEAVE_APPROVAL_STATUS_LABELS[value] || value}</Tag>
        : '-'
    },
    { title: '申请时间', dataIndex: 'createTime', width: 170, render: fmtTime },
    {
      title: '操作', width: 100, align: 'center', fixed: 'right',
      render: (_, row) => canCancelLeave(row.approvalStatus)
        ? <Button type="link" size="small" danger onClick={() => handleCancel(row)}>撤销</Button>
        : <span className="hrm-muted">-</span>
    }
  ]

  const monthPicker = <Space>
    <Select value={year} onChange={setYear} options={yearOptions()} style={{ width: 110 }}/>
    <Select value={month} onChange={setMonth} options={MONTH_OPTIONS} style={{ width: 90 }}/>
    <Button icon={<ReloadOutlined/>} onClick={() => void loadAttendance(year, month)}>刷新</Button>
  </Space>

  const summaryCard = summary?.summary && <Descriptions className="hrm-summary" size="small" column={4} bordered items={[
    { key: 'attend', label: '应出勤', children: `${summary.summary.attendDays} 天` },
    { key: 'actual', label: '实际出勤', children: `${summary.summary.actualDays} 天` },
    { key: 'late', label: '迟到', children: summary.summary.lateCount ? `${summary.summary.lateCount} 次 / ${fmtMinutes(summary.summary.lateMinute)}` : '无' },
    { key: 'early', label: '早退', children: summary.summary.earlyCount ? `${summary.summary.earlyCount} 次 / ${fmtMinutes(summary.summary.earlyMinute)}` : '无' },
    { key: 'misscard', label: '缺卡', children: summary.summary.misscardCount ? `${summary.summary.misscardCount} 次` : '无' },
    { key: 'absent', label: '旷工', children: summary.summary.absenteeismDays ? `${summary.summary.absenteeismDays} 天` : '无' },
    { key: 'leave', label: '请假', children: summary.summary.leaveDays ? `${summary.summary.leaveDays} 天` : '无' },
    { key: 'full', label: '全勤', children: summary.summary.fullAttendance ? <Tag color="success">是</Tag> : <Tag>否</Tag> }
  ]}/>

  const clockContent = clockLoading && !clocks.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : clockError ? <Alert type="error" showIcon message={clockError} action={<Button size="small" onClick={() => void loadAttendance(year, month)}>重试</Button>}/>
      : <>
        {summaryCard}
        {!clocks.length
          ? <Empty description={`${year} 年 ${month} 月暂无打卡记录`}/>
          : <HrmProTable<HrmClockItem> advanced persistenceKey="my-attendance-clock" onReload={() => loadAttendance(year, month)} rowKey="id" columns={clockColumns} dataSource={clocks} loading={clockLoading}
            pagination={{ pageSize: 20, showSizeChanger: false, showTotal: count => `共 ${count} 条` }} scroll={{ x: 900 }}/>}
      </>

  const leaveContent = leaveLoading && !leaves.length ? <Skeleton active paragraph={{ rows: 6 }}/>
    : leaveError ? <Alert type="error" showIcon message={leaveError} action={<Button size="small" onClick={() => void loadLeaves()}>重试</Button>}/>
      : !leaves.length ? <Empty description="暂无请假记录"/>
        : <HrmProTable<HrmLeaveItem> advanced persistenceKey="my-attendance-leave" onReload={() => loadLeaves()} rowKey="id" columns={leaveColumns} dataSource={leaves} loading={leaveLoading}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: count => `共 ${count} 条` }} scroll={{ x: 1100 }}/>

  return <section className="workspace-page hrm-page hrm-my-attendance-page">
    {clockType.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`打卡类型字典加载失败：${clockType.error}`} action={<Button size="small" onClick={clockType.reload}>重试</Button>}/>}
    {leaveType.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`请假类型字典加载失败：${leaveType.error}`} action={<Button size="small" onClick={leaveType.reload}>重试</Button>}/>}
    <Tabs
      defaultActiveKey="clock"
      items={[
        {
          key: 'clock', label: '打卡记录',
          children: <>
            <div className="page-heading">{monthPicker}</div>
            <div className="hrm-table-area">{clockContent}</div>
          </>
        },
        {
          key: 'leave', label: '请假申请',
          children: <>
            <div className="page-heading">
              <Space>
                <Button type="primary" icon={<PlusOutlined/>} onClick={() => { applyForm.resetFields(); setApplyOpen(true) }}>申请请假</Button>
                <Button icon={<ReloadOutlined/>} onClick={() => void loadLeaves()}>刷新</Button>
              </Space>
            </div>
            <div className="hrm-table-area">{leaveContent}</div>
          </>
        }
      ]}
    />

    <Modal title="申请请假" open={applyOpen} onCancel={() => setApplyOpen(false)} onOk={handleApply} confirmLoading={applyLoading} width="min(840px, 96vw)" destroyOnClose>
      <Form form={applyForm} layout="vertical">
        <Form.Item name="type" label="请假类型" rules={[{ required: true, message: '请选择请假类型' }]}>
          <Select placeholder="请选择" loading={leaveType.loading}
            options={leaveType.items.map(item => ({ value: item.value, label: item.label }))}/>
        </Form.Item>
        <Form.Item name="range" label="请假时间" rules={[{ required: true, message: '请选择请假时间' }]}>
          <DatePicker.RangePicker showTime style={{ width: '100%' }} placeholder={['开始时间', '结束时间']}/>
        </Form.Item>
        <Form.Item name="day" label="请假天数" rules={[{ required: true, message: '请输入请假天数' }]}
          extra="按实际请假天数填写，支持半天（0.5）">
          <InputNumber min={0.5} step={0.5} precision={1} style={{ width: '100%' }} placeholder="如 1.5"/>
        </Form.Item>
        <Form.Item name="reason" label="请假事由" rules={[{ required: true, message: '请填写请假事由' }]}>
          <Input.TextArea rows={3} placeholder="请说明请假原因"/>
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} placeholder="选填"/>
        </Form.Item>
        <Alert message="提交后进入审批流程，审批中的申请可以撤销" type="info" showIcon/>
      </Form>
    </Modal>
  </section>
}
