import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Pagination, Select, Skeleton, Space, Tag, message } from 'antd'
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmAttendanceMonthDetail, type HrmAttendanceMonthRecord } from '../services/api'
import { MONTH_OPTIONS, currentYearMonth, fmtAmount, fmtMinutes, yearOptions } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { downloadBlob } from '../services/download'

const PAGE_SIZE = 10

function fmtDate(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }

/** 管理端月度考勤汇总：按年月查看全员出勤统计，下钻到单人每日明细。 */
export default function HrmAttendanceMonthPage({ permissions }: { permissions: string[] }) {
  const initial = currentYearMonth()
  const [year, setYear] = useState(initial.year)
  const [month, setMonth] = useState(initial.month)

  const [items, setItems] = useState<HrmAttendanceMonthRecord[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [detail, setDetail] = useState<HrmAttendanceMonthDetail>()
  const [detailTitle, setDetailTitle] = useState('')
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const detailVersion = useRef(0)
  const [exporting, setExporting] = useState<'summary' | 'daily'>()
  const canExportSummary = permissions.includes('hrm:attendance:statistics:export')
  const canExportDaily = permissions.includes('hrm:attendance:clock:export')

  const loadPage = useCallback(async (page: number, targetYear: number, targetMonth: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.attendance.statistics.monthRecordPage({
        pageNo: page, pageSize: PAGE_SIZE, year: targetYear, month: targetMonth
      })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '月度考勤加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, year, month) }, [loadPage, pageNo, year, month])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, year, month) }, [loadPage, year, month])

  const openDetail = async (row: HrmAttendanceMonthRecord) => {
    const version = ++detailVersion.current
    setDetailTitle(`${row.employeeName} · ${row.year} 年 ${row.month} 月`)
    setDetail(undefined); setDetailOpen(true); setDetailLoading(true)
    try {
      const result = await api.hrm.attendance.statistics.monthDetail({ employeeId: row.employeeId, year: row.year, month: row.month })
      if (version !== detailVersion.current) return
      setDetail(result)
    } catch (e) {
      if (version === detailVersion.current) message.error(e instanceof Error ? e.message : '考勤明细加载失败')
    } finally {
      if (version === detailVersion.current) setDetailLoading(false)
    }
  }

  const exportReport = async (type: 'summary' | 'daily') => {
    setExporting(type)
    const target = type === 'summary'
      ? { url: '/hrm/attendance/statistics/month-record-export-excel', name: `员工月度考勤汇总-${year}-${String(month).padStart(2, '0')}.xls` }
      : { url: '/hrm/attendance/statistics/month-daily-export-excel', name: `员工月度打卡概况-${year}-${String(month).padStart(2, '0')}.xls` }
    try { await downloadBlob(target.url, target.name, { year, month }) }
    catch (e) { message.error(e instanceof Error ? e.message : '导出失败') }
    finally { setExporting(undefined) }
  }

  const columns: ColumnsType<HrmAttendanceMonthRecord> = [
    { title: '员工', dataIndex: 'employeeName', width: 110, fixed: 'left' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 140, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '考勤组', dataIndex: 'attendanceGroupName', width: 130, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '应出勤', dataIndex: 'attendDays', width: 90, align: 'right', render: (value: number) => `${value} 天` },
    { title: '实际出勤', dataIndex: 'actualDays', width: 100, align: 'right', render: (value: number) => `${value} 天` },
    { title: '迟到', dataIndex: 'lateCount', width: 90, align: 'right', render: (value: number) => value ? `${value} 次` : '-' },
    { title: '早退', dataIndex: 'earlyCount', width: 90, align: 'right', render: (value: number) => value ? `${value} 次` : '-' },
    { title: '缺卡', dataIndex: 'misscardCount', width: 90, align: 'right', render: (value: number) => value ? `${value} 次` : '-' },
    { title: '旷工', dataIndex: 'absenteeismDays', width: 90, align: 'right', render: (value: number) => value ? `${value} 天` : '-' },
    { title: '请假', dataIndex: 'leaveDays', width: 90, align: 'right', render: (value: number) => value ? `${value} 天` : '-' },
    { title: '考勤扣款', dataIndex: 'attendanceDeductAmount', width: 110, align: 'right', render: (value: number) => value ? `¥${fmtAmount(value)}` : '-' },
    { title: '全勤', dataIndex: 'fullAttendance', width: 80, align: 'center', render: (value: boolean) => value ? <Tag color="success">是</Tag> : <Tag>否</Tag> },
    {
      title: '操作', width: 90, align: 'center', fixed: 'right',
      render: (_, row) => <Button type="link" size="small" onClick={() => void openDetail(row)}>明细</Button>
    }
  ]

  const dailyColumns: ColumnsType<HrmAttendanceMonthDetail['dailyDetails'][number]> = [
    { title: '日期', dataIndex: 'attendanceTime', width: 110, render: fmtDate },
    { title: '班次', dataIndex: 'shiftName', width: 110, render: (value?: string) => value || '-' },
    { title: '考勤结果', dataIndex: 'attendanceResult', width: 120, render: (value?: string) => value || '-' },
    { title: '迟到', dataIndex: 'lateMinutes', width: 100, render: (value?: number) => fmtMinutes(value) },
    { title: '早退', dataIndex: 'earlyMinutes', width: 100, render: (value?: number) => fmtMinutes(value) },
    { title: '请假', dataIndex: 'leaveDays', width: 90, align: 'right', render: (value?: number) => value ? `${value} 天` : '-' }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description={`${year} 年 ${month} 月暂无考勤汇总`}/>
        : <>
          <HrmProTable<HrmAttendanceMonthRecord> advanced persistenceKey="attendance-month" onReload={reload} rowKey="employeeId" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1600 }} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 人`}/>
        </>

  return <section className="workspace-page hrm-page hrm-attendance-month-page">
    <div className="page-heading">
      <Space>
        <Select value={year} onChange={value => { setYear(value); setPageNo(1) }} options={yearOptions()} style={{ width: 110 }}/>
        <Select value={month} onChange={value => { setMonth(value); setPageNo(1) }} options={MONTH_OPTIONS} style={{ width: 90 }}/>
      </Space>
      <Space wrap>
        {canExportSummary && <Button icon={<DownloadOutlined/>} loading={exporting === 'summary'} onClick={() => void exportReport('summary')}>导出汇总</Button>}
        {canExportDaily && <Button icon={<DownloadOutlined/>} loading={exporting === 'daily'} onClick={() => void exportReport('daily')}>导出打卡概况</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detailTitle} width="min(960px, 96vw)" open={detailOpen} onClose={() => setDetailOpen(false)} destroyOnClose>
      {detailLoading && !detail ? <Skeleton active paragraph={{ rows: 10 }}/> : detail && <>
        <Descriptions className="hrm-summary" size="small" column={3} bordered items={[
          { key: 'attend', label: '应出勤', children: `${detail.summary.attendDays} 天` },
          { key: 'actual', label: '实际出勤', children: `${detail.summary.actualDays} 天` },
          { key: 'full', label: '全勤', children: detail.summary.fullAttendance ? <Tag color="success">是</Tag> : <Tag>否</Tag> },
          { key: 'late', label: '迟到', children: detail.summary.lateCount ? `${detail.summary.lateCount} 次 / ${fmtMinutes(detail.summary.lateMinute)}` : '无' },
          { key: 'early', label: '早退', children: detail.summary.earlyCount ? `${detail.summary.earlyCount} 次 / ${fmtMinutes(detail.summary.earlyMinute)}` : '无' },
          { key: 'misscard', label: '缺卡', children: detail.summary.misscardCount ? `${detail.summary.misscardCount} 次` : '无' },
          { key: 'absent', label: '旷工', children: detail.summary.absenteeismDays ? `${detail.summary.absenteeismDays} 天` : '无' },
          { key: 'leave', label: '请假', children: detail.summary.leaveDays ? `${detail.summary.leaveDays} 天` : '无' },
          { key: 'deduct', label: '考勤扣款', children: detail.summary.attendanceDeductAmount ? `¥${fmtAmount(detail.summary.attendanceDeductAmount)}` : '无' }
        ]}/>
        <h4 className="hrm-drawer-subtitle">每日明细</h4>
        {detail.dailyDetails.length
          ? <HrmProTable rowKey="attendanceTime" size="small" columns={dailyColumns} dataSource={detail.dailyDetails}
            pagination={false} scroll={{ x: 630, y: 320 }}/>
          : <Empty description="暂无每日明细"/>}
      </>}
    </Drawer>
  </section>
}
