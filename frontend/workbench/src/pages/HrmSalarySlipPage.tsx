import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Pagination, Select, Skeleton, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmSalarySlip } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, MONTH_OPTIONS, fmtAmount, yearOptions } from '../services/hrm'
import SalaryOptionTable from '../components/SalaryOptionTable'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

/** 管理端工资条发放记录：查询全员工资条、查看明细、维护备注。 */
export default function HrmSalarySlipPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmSalarySlip[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [filterYear, setFilterYear] = useState<number>()
  const [filterMonth, setFilterMonth] = useState<number>()
  const [filterReadStatus, setFilterReadStatus] = useState<number>()

  const [detail, setDetail] = useState<HrmSalarySlip>()
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const detailVersion = useRef(0)

  const [remarkOpen, setRemarkOpen] = useState(false)
  const [remarkLoading, setRemarkLoading] = useState(false)
  const [remarkForm] = Form.useForm<{ remark?: string }>()
  const [remarkTarget, setRemarkTarget] = useState<HrmSalarySlip>()

  const optionType = useDict(HRM_DICT.SALARY_OPTION_TYPE)
  const readStatus = useDict(HRM_DICT.SALARY_SLIP_READ_STATUS)

  const canUpdate = permissions.includes('hrm:salary:slip:update')

  const loadPage = useCallback(async (page: number, year?: number, month?: number, status?: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.salary.slip.page({ pageNo: page, pageSize: PAGE_SIZE, year, month, readStatus: status })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '工资条加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, filterYear, filterMonth, filterReadStatus) },
    [loadPage, pageNo, filterYear, filterMonth, filterReadStatus])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, filterYear, filterMonth, filterReadStatus) },
    [loadPage, filterYear, filterMonth, filterReadStatus])

  const openDetail = async (row: HrmSalarySlip) => {
    const version = ++detailVersion.current
    setDetail(row); setDetailOpen(true); setDetailLoading(true)
    try {
      const result = await api.hrm.salary.slip.get(row.id)
      if (version !== detailVersion.current) return
      setDetail(result)
    } catch (e) {
      if (version === detailVersion.current) message.error(e instanceof Error ? e.message : '工资条详情加载失败')
    } finally {
      if (version === detailVersion.current) setDetailLoading(false)
    }
  }

  const handleRemark = async () => {
    if (!remarkTarget) return
    const values = await remarkForm.validateFields()
    setRemarkLoading(true)
    try {
      await api.hrm.salary.slip.remark({ id: remarkTarget.id, remark: values.remark })
      message.success('备注已保存')
      setRemarkOpen(false); remarkForm.resetFields(); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setRemarkLoading(false) }
  }

  const columns: ColumnsType<HrmSalarySlip> = [
    { title: '员工', dataIndex: 'employeeName', width: 110, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 140, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '所属月份', width: 120, render: (_, row) => `${row.year} 年 ${row.month} 月` },
    { title: '实发工资', dataIndex: 'realPaySalary', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    {
      title: '阅读状态', dataIndex: 'readStatus', width: 100, align: 'center',
      render: (value?: number) => value != null ? <Tag>{readStatus.labels[String(value)] || value}</Tag> : '-'
    },
    { title: '备注', dataIndex: 'remark', width: 160, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '发放时间', dataIndex: 'createTime', width: 170, render: fmtTime },
    {
      title: '操作', width: 140, align: 'center', fixed: 'right',
      render: (_, row) => <Space size="small">
        <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
        {canUpdate && <Button type="link" size="small" onClick={() => {
          setRemarkTarget(row); remarkForm.setFieldsValue({ remark: row.remark }); setRemarkOpen(true)
        }}>备注</Button>}
      </Space>
    }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无工资条"/>
        : <>
          <HrmProTable<HrmSalarySlip> advanced persistenceKey="salary-slip" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1300 }} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-salary-slip-page">
    <div className="page-heading">
      <Space wrap>
        <Select allowClear placeholder="年份" value={filterYear} onChange={value => { setFilterYear(value); setPageNo(1) }}
          style={{ width: 110 }} options={yearOptions()}/>
        <Select allowClear placeholder="月份" value={filterMonth} onChange={value => { setFilterMonth(value); setPageNo(1) }}
          style={{ width: 90 }} options={MONTH_OPTIONS}/>
        <Select allowClear placeholder="阅读状态" value={filterReadStatus} onChange={value => { setFilterReadStatus(value); setPageNo(1) }}
          style={{ width: 120 }} loading={readStatus.loading} options={readStatus.options}/>
      </Space>
      <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail ? `${detail.employeeName || ''} · ${detail.year} 年 ${detail.month} 月工资条` : ''} width="min(960px, 96vw)"
      open={detailOpen} onClose={() => setDetailOpen(false)} destroyOnClose>
      {detailLoading && !detail?.options ? <Skeleton active paragraph={{ rows: 10 }}/> : detail && <>
        <Descriptions className="hrm-summary" size="small" column={2} bordered items={[
          { key: 'employee', label: '员工', children: `${detail.employeeName || '-'}${detail.jobNumber ? `（${detail.jobNumber}）` : ''}` },
          { key: 'dept', label: '部门', children: detail.deptName || '-' },
          { key: 'real', label: '实发工资', children: <strong>¥{fmtAmount(detail.realPaySalary)}</strong> },
          { key: 'period', label: '所属月份', children: `${detail.year} 年 ${detail.month} 月` },
          { key: 'read', label: '阅读状态', children: detail.readStatus != null ? (readStatus.labels[String(detail.readStatus)] || detail.readStatus) : '-' },
          { key: 'create', label: '发放时间', children: fmtTime(detail.createTime) },
          { key: 'remark', label: '备注', children: detail.remark || '-' }
        ]}/>
        <h4 className="hrm-drawer-subtitle">工资项明细</h4>
        {detail.options?.length
          ? <SalaryOptionTable options={detail.options} typeLabels={optionType.labels}/>
          : <Empty description="暂无工资项明细"/>}
      </>}
    </Drawer>

    <Modal title="修改备注" open={remarkOpen} onCancel={() => setRemarkOpen(false)} onOk={handleRemark}
      confirmLoading={remarkLoading} width="min(760px, 96vw)" destroyOnClose>
      <Form form={remarkForm} layout="vertical">
        {remarkTarget && <Form.Item label="工资条">
          <span>{remarkTarget.employeeName} · {remarkTarget.year} 年 {remarkTarget.month} 月</span>
        </Form.Item>}
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={3} placeholder="员工在自己的工资条上可以看到该备注"/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
