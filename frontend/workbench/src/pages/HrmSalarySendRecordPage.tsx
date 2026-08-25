import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Pagination, Segmented, Select, Skeleton, Space, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmSalarySlip, type HrmSalarySlipSendEmployee, type HrmSalarySlipSendRecord, type HrmSalarySlipTemplate } from '../services/api'
import { fmtAmount, yearOptions } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

function fmtTime(value?: number | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

/** 工资条发放批次：支持按筛选结果或已选员工发放，并可查看批次工资条。 */
export default function HrmSalarySendRecordPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmSalarySlipSendRecord[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [year, setYear] = useState<number>()
  const [month, setMonth] = useState<number>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [templates, setTemplates] = useState<HrmSalarySlipTemplate[]>([])
  const [monthRecords, setMonthRecords] = useState<Array<{ value: number; label: string }>>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{ monthRecordId: number; templateId?: number }>()
  const version = useRef(0)

  const [sendMode, setSendMode] = useState<'all' | 'selected'>('all')
  const [sendSearch, setSendSearch] = useState('')
  const [sendEmployees, setSendEmployees] = useState<HrmSalarySlipSendEmployee[]>([])
  const [sendEmployeeTotal, setSendEmployeeTotal] = useState(0)
  const [sendEmployeePageNo, setSendEmployeePageNo] = useState(1)
  const [sendEmployeeLoading, setSendEmployeeLoading] = useState(false)
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<number[]>([])
  const selectedMonthRecordId = Form.useWatch('monthRecordId', form)

  const [detail, setDetail] = useState<HrmSalarySlipSendRecord>()
  const [slips, setSlips] = useState<HrmSalarySlip[]>([])
  const [slipTotal, setSlipTotal] = useState(0)
  const [slipPageNo, setSlipPageNo] = useState(1)
  const [slipLoading, setSlipLoading] = useState(false)
  const [slipError, setSlipError] = useState('')

  const canCreate = permissions.includes('hrm:salary:slip:create')
  const canDelete = permissions.includes('hrm:salary:slip:delete')

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.salary.slip.sendRecords.page({ pageNo, pageSize: PAGE_SIZE, year, month })
      if (current === version.current) { setItems(result.list); setTotal(result.total) }
    } catch (e) { if (current === version.current) setError(e instanceof Error ? e.message : '发放记录加载失败') }
    finally { if (current === version.current) setLoading(false) }
  }, [pageNo, year, month])

  useEffect(() => { void load() }, [load])

  useEffect(() => {
    void Promise.all([
      api.hrm.salary.slip.templates(),
      api.hrm.salary.monthRecord.page({ pageNo: 1, pageSize: 100 })
    ]).then(([templateList, records]) => {
      setTemplates(templateList)
      setMonthRecords(records.list.flatMap(item => item.id != null ? [{ value: item.id, label: item.title || `${item.year}年${item.month}月` }] : []))
    }).catch(() => undefined)
  }, [])

  const loadSendEmployees = useCallback(async (recordId: number, page: number, search: string) => {
    setSendEmployeeLoading(true)
    try {
      const result = await api.hrm.salary.slip.sendRecords.employeePage({ pageNo: page, pageSize: PAGE_SIZE, monthRecordId: recordId, search: search || undefined, sent: false })
      setSendEmployees(result.list); setSendEmployeeTotal(result.total)
    } catch (e) { setSendEmployees([]); message.error(e instanceof Error ? e.message : '待发员工加载失败') }
    finally { setSendEmployeeLoading(false) }
  }, [])

  useEffect(() => {
    if (!createOpen || !selectedMonthRecordId) return
    setSendEmployeePageNo(1); setSelectedEmployeeIds([])
    void loadSendEmployees(selectedMonthRecordId, 1, '')
  }, [createOpen, selectedMonthRecordId, loadSendEmployees])

  const openCreate = () => {
    form.resetFields(); setSendMode('all'); setSendSearch(''); setSelectedEmployeeIds([]); setCreateOpen(true)
  }

  const save = async () => {
    const values = await form.validateFields()
    if (sendMode === 'selected' && !selectedEmployeeIds.length) {
      message.warning('请选择需要发放工资条的员工')
      return
    }
    const template = templates.find(item => item.id === values.templateId) || templates.find(item => item.defaultStatus)
    setSaving(true)
    try {
      await api.hrm.salary.slip.sendRecords.create({
        monthRecordId: values.monthRecordId,
        hideEmpty: template?.hideEmpty ?? true,
        options: template?.options,
        all: sendMode === 'all',
        employeeIds: sendMode === 'selected' ? selectedEmployeeIds : undefined,
        search: sendMode === 'all' ? sendSearch || undefined : undefined,
        sent: false
      })
      message.success('工资条发放批次已创建'); setCreateOpen(false); form.resetFields(); await load()
    } catch (e) { message.error(e instanceof Error ? e.message : '发放失败') }
    finally { setSaving(false) }
  }

  const loadSlips = useCallback(async (recordId: number, page: number) => {
    setSlipLoading(true); setSlipError('')
    try {
      const result = await api.hrm.salary.slip.page({ pageNo: page, pageSize: PAGE_SIZE, sendRecordId: recordId })
      setSlips(result.list); setSlipTotal(result.total)
    } catch (e) { setSlipError(e instanceof Error ? e.message : '批次工资条加载失败') }
    finally { setSlipLoading(false) }
  }, [])

  const openDetail = async (row: HrmSalarySlipSendRecord) => {
    setDetail(row); setSlipPageNo(1); setSlips([])
    try { setDetail(await api.hrm.salary.slip.sendRecords.get(row.id)) }
    catch (e) { message.error(e instanceof Error ? e.message : '批次详情加载失败') }
    await loadSlips(row.id, 1)
  }

  const changeSlipPage = (page: number) => {
    if (!detail) return
    setSlipPageNo(page); void loadSlips(detail.id, page)
  }

  const columns: ColumnsType<HrmSalarySlipSendRecord> = [
    { title: '工资月份', width: 140, render: (_, row) => <Button type="link" onClick={() => void openDetail(row)}>{row.year || '-'}年{row.month || '-'}月</Button> },
    { title: '创建人', dataIndex: 'creatorName', width: 130, render: value => value || '-' },
    { title: '工资表人数', dataIndex: 'employeeCount', width: 120, render: (value?: number) => `${value ?? 0} 人` },
    { title: '发放人数', dataIndex: 'sendEmployeeCount', width: 110, render: (value?: number) => `${value ?? 0} 人` },
    { title: '已查看人数', dataIndex: 'readCount', width: 110, render: (value?: number) => `${value ?? 0} 人` },
    { title: '发放时间', dataIndex: 'createTime', width: 170, render: fmtTime },
    { title: '状态', width: 90, render: () => <Tag color="success">已发放</Tag> },
    { title: '操作', width: 130, render: (_, row) => <Space size="small"><Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>{canDelete && <Button type="link" size="small" danger onClick={() => Modal.confirm({ title: '删除发放批次', content: '删除后本批次工资条也会删除，确定继续吗？', okType: 'danger', onOk: async () => { await api.hrm.salary.slip.sendRecords.delete(row.id); message.success('已删除'); load() } })}>删除</Button>}</Space> }
  ]

  const sendEmployeeColumns: ColumnsType<HrmSalarySlipSendEmployee> = [
    { title: '员工', dataIndex: 'employeeName', width: 110, fixed: 'left' },
    { title: '工号', dataIndex: 'jobNumber', width: 110 },
    { title: '部门', dataIndex: 'deptName', width: 130 },
    { title: '岗位', dataIndex: 'postName', width: 120 },
    { title: '应发工资', dataIndex: 'expectedPaySalary', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '实发工资', dataIndex: 'realPaySalary', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' }
  ]

  const slipColumns: ColumnsType<HrmSalarySlip> = [
    { title: '员工', dataIndex: 'employeeName', width: 110, fixed: 'left' },
    { title: '工号', dataIndex: 'jobNumber', width: 110 },
    { title: '部门', dataIndex: 'deptName', width: 130 },
    { title: '岗位', dataIndex: 'postName', width: 120 },
    { title: '实发工资', dataIndex: 'realPaySalary', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '阅读状态', dataIndex: 'readStatus', width: 100, render: (value?: number) => value === 1 ? <Tag color="success">已查看</Tag> : <Tag>未查看</Tag> },
    { title: '备注', dataIndex: 'remark', width: 180, ellipsis: true, render: (value?: string) => value || '-' }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
      : !items.length ? <Empty description="暂无发放记录"/>
        : <><HrmProTable advanced persistenceKey="salary-send-record" onReload={load} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1050 }}/><Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo}/></>

  return <section className="workspace-page hrm-page hrm-salary-send-record-page">
    <div className="page-heading">
      <Space><Select allowClear placeholder="年份" value={year} options={yearOptions()} onChange={value => { setYear(value); setPageNo(1) }}/><Select allowClear placeholder="月份" value={month} options={Array.from({ length: 12 }, (_, index) => ({ value: index + 1, label: `${index + 1}月` }))} onChange={value => { setMonth(value); setPageNo(1) }}/></Space>
      <Space>{canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>发放工资条</Button>}<Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button></Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title="发放工资条" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => void save()} confirmLoading={saving} width="min(1040px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Space align="start" wrap>
          <Form.Item name="monthRecordId" label="工资表" rules={[{ required: true }]}><Select options={monthRecords} style={{ width: 260 }}/></Form.Item>
          <Form.Item name="templateId" label="工资条模板"><Select allowClear options={templates.flatMap(item => item.id != null ? [{ value: item.id, label: item.name }] : [])} style={{ width: 220 }}/></Form.Item>
          <Form.Item label="发放范围"><Segmented value={sendMode} options={[{ value: 'all', label: '全部筛选结果' }, { value: 'selected', label: '已选员工' }]} onChange={value => setSendMode(value as 'all' | 'selected')}/></Form.Item>
        </Space>
      </Form>
      {selectedMonthRecordId ? <>
        <Input.Search allowClear placeholder="姓名、工号或手机号" value={sendSearch} onChange={event => setSendSearch(event.target.value)} onSearch={() => void loadSendEmployees(selectedMonthRecordId, 1, sendSearch)} style={{ width: 240, marginBottom: 12 }}/>
        {sendMode === 'all' && <Alert type="info" showIcon message={`将发放当前筛选结果中的未发送员工，共 ${sendEmployeeTotal} 人`}/>}
        <HrmProTable<HrmSalarySlipSendEmployee> rowKey="employeeId" size="small" loading={sendEmployeeLoading} columns={sendEmployeeColumns} dataSource={sendEmployees} pagination={false} rowSelection={sendMode === 'selected' ? { selectedRowKeys: selectedEmployeeIds, onChange: keys => setSelectedEmployeeIds(keys.map(Number)) } : undefined} scroll={{ x: 720 }}/>
        <Pagination className="hrm-pagination" current={sendEmployeePageNo} total={sendEmployeeTotal} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={page => { setSendEmployeePageNo(page); void loadSendEmployees(selectedMonthRecordId, page, sendSearch) }}/>
      </> : <Empty description="请先选择工资表"/>}
    </Modal>

    <Drawer title={`${detail?.year || '-'} 年 ${detail?.month || '-'} 月工资条发放详情`} width="min(1040px, 96vw)" open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detail && <Descriptions bordered size="small" column={4} className="hrm-summary" items={[
        { key: 'employee', label: '工资表人数', children: `${detail.employeeCount ?? 0} 人` },
        { key: 'sent', label: '发放人数', children: `${detail.sendEmployeeCount ?? 0} 人` },
        { key: 'read', label: '已查看', children: `${detail.readCount ?? 0} 人` },
        { key: 'time', label: '发放时间', children: fmtTime(detail.createTime) }
      ]}/>}
      {slipLoading && !slips.length ? <Skeleton active paragraph={{ rows: 8 }}/>
        : slipError ? <Alert type="error" showIcon message={slipError} action={<Button size="small" onClick={() => detail && void loadSlips(detail.id, slipPageNo)}>重试</Button>}/>
          : !slips.length ? <Empty description="该批次暂无工资条"/>
            : <><HrmProTable<HrmSalarySlip> rowKey="id" columns={slipColumns} dataSource={slips} pagination={false} loading={slipLoading} scroll={{ x: 900 }}/><Pagination className="hrm-pagination" current={slipPageNo} total={slipTotal} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={changeSlipPage}/></>}
    </Drawer>
  </section>
}
