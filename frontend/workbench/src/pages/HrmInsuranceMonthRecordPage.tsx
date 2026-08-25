import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Input, InputNumber, Modal, Pagination, Segmented, Select, Skeleton, Space, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import {
  api,
  type HrmEmployee,
  type HrmInsuranceMonthEmployeeRecord,
  type HrmInsuranceMonthRecord,
  type HrmInsuranceProject,
  type HrmInsuranceScheme
} from '../services/api'
import { useDict } from '../services/useDict'
import {
  HRM_DICT,
  INSURANCE_EMPLOYEE_STATUS,
  INSURANCE_MONTH_STATUS,
  INSURANCE_SCHEME_TYPE,
  fmtAmount,
  yearOptions
} from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

function fmtDate(value?: number | null) {
  return value ? dayjs(value).format('YYYY-MM-DD') : '-'
}

function projectTotal(project: HrmInsuranceProject) {
  return Number(project.personalAmount || 0) + Number(project.corporateAmount || 0)
}

/** 社保月表及其员工明细：月表汇总、参保人员维护和缴费项目调整。 */
export default function HrmInsuranceMonthRecordPage({ permissions }: { permissions: string[] }) {
  const [year, setYear] = useState(new Date().getFullYear())
  const [items, setItems] = useState<HrmInsuranceMonthRecord[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)
  const [acting, setActing] = useState(false)

  const [monthDetail, setMonthDetail] = useState<HrmInsuranceMonthRecord>()
  const [monthDetailOpen, setMonthDetailOpen] = useState(false)
  const [monthDetailLoading, setMonthDetailLoading] = useState(false)
  const [employees, setEmployees] = useState<HrmInsuranceMonthEmployeeRecord[]>([])
  const [employeeTotal, setEmployeeTotal] = useState(0)
  const [employeePageNo, setEmployeePageNo] = useState(1)
  const [employeeLoading, setEmployeeLoading] = useState(false)
  const [employeeError, setEmployeeError] = useState('')
  const employeeVersion = useRef(0)
  const [employeeName, setEmployeeName] = useState('')
  const [schemeId, setSchemeId] = useState<number>()
  const [employeeStatus, setEmployeeStatus] = useState<number>(INSURANCE_EMPLOYEE_STATUS.NORMAL)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [schemes, setSchemes] = useState<HrmInsuranceScheme[]>([])

  const [employeeDetail, setEmployeeDetail] = useState<HrmInsuranceMonthEmployeeRecord>()
  const [employeeDetailLoading, setEmployeeDetailLoading] = useState(false)

  const [addOpen, setAddOpen] = useState(false)
  const [uninsured, setUninsured] = useState<HrmEmployee[]>([])
  const [uninsuredLoading, setUninsuredLoading] = useState(false)
  const [addEmployeeIds, setAddEmployeeIds] = useState<number[]>([])

  const [editOpen, setEditOpen] = useState(false)
  const [editIds, setEditIds] = useState<number[]>([])
  const [editEmployeeLabel, setEditEmployeeLabel] = useState('')
  const [editSchemeId, setEditSchemeId] = useState<number>()
  const [editSchemeType, setEditSchemeType] = useState<number>()
  const [editProjects, setEditProjects] = useState<HrmInsuranceProject[]>([])
  const [editLoading, setEditLoading] = useState(false)

  const monthStatus = useDict(HRM_DICT.INSURANCE_MONTH_STATUS)
  const insuredStatus = useDict(HRM_DICT.INSURANCE_EMPLOYEE_STATUS)
  const projectType = useDict(HRM_DICT.INSURANCE_PROJECT_TYPE)
  const canCreate = permissions.includes('hrm:insurance:month-record:create')
  const canUpdate = permissions.includes('hrm:insurance:month-record:update')
  const canDelete = permissions.includes('hrm:insurance:month-record:delete')
  const editable = monthDetail?.status === INSURANCE_MONTH_STATUS.UNARCHIVED && canUpdate

  useEffect(() => {
    void api.hrm.insurance.scheme.list().then(setSchemes).catch(() => setSchemes([]))
  }, [])

  const loadPage = useCallback(async (page: number, targetYear: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.insurance.monthRecord.page({ pageNo: page, pageSize: PAGE_SIZE, year: targetYear })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '社保记录加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, year) }, [loadPage, pageNo, year])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, year) }, [loadPage, year])

  const loadEmployees = useCallback(async (
    recordId: number,
    page: number,
    filters: { employeeName?: string; schemeId?: number; status?: number }
  ) => {
    const version = ++employeeVersion.current
    setEmployeeLoading(true); setEmployeeError('')
    try {
      const result = await api.hrm.insurance.monthRecord.employeePage({
        pageNo: page,
        pageSize: PAGE_SIZE,
        monthRecordId: recordId,
        employeeName: filters.employeeName || undefined,
        schemeId: filters.schemeId,
        status: filters.status
      })
      if (version !== employeeVersion.current) return
      setEmployees(result.list); setEmployeeTotal(result.total); setSelectedIds([])
    } catch (e) {
      if (version === employeeVersion.current) setEmployeeError(e instanceof Error ? e.message : '参保员工加载失败')
    } finally {
      if (version === employeeVersion.current) setEmployeeLoading(false)
    }
  }, [])

  const openMonthDetail = async (row: HrmInsuranceMonthRecord) => {
    if (!row.id) return
    setMonthDetailOpen(true); setMonthDetail(row); setMonthDetailLoading(true)
    setEmployeeName(''); setSchemeId(undefined); setEmployeeStatus(INSURANCE_EMPLOYEE_STATUS.NORMAL); setEmployeePageNo(1)
    try {
      const detail = await api.hrm.insurance.monthRecord.get(row.id)
      setMonthDetail(detail)
      await loadEmployees(row.id, 1, { status: INSURANCE_EMPLOYEE_STATUS.NORMAL })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '社保表详情加载失败')
    } finally {
      setMonthDetailLoading(false)
    }
  }

  const refreshDetail = async () => {
    if (!monthDetail?.id) return
    const detail = await api.hrm.insurance.monthRecord.get(monthDetail.id)
    setMonthDetail(detail)
    await loadEmployees(monthDetail.id, employeePageNo, { employeeName, schemeId, status: employeeStatus })
    reload()
  }

  const searchEmployees = (page = 1) => {
    if (!monthDetail?.id) return
    setEmployeePageNo(page)
    void loadEmployees(monthDetail.id, page, { employeeName, schemeId, status: employeeStatus })
  }

  const createNext = () => {
    Modal.confirm({ title: '创建下月社保表', content: '将依据上一月社保方案生成下一个月记录。', onOk: async () => {
      setActing(true)
      try { await api.hrm.insurance.monthRecord.createNext(); message.success('已创建'); reload() }
      catch (e) { message.error(e instanceof Error ? e.message : '创建失败'); throw e }
      finally { setActing(false) }
    } })
  }

  const openEmployeeDetail = async (id: number) => {
    setEmployeeDetailLoading(true); setEmployeeDetail({ id } as HrmInsuranceMonthEmployeeRecord)
    try { setEmployeeDetail(await api.hrm.insurance.monthRecord.employeeGet(id)) }
    catch (e) { setEmployeeDetail(undefined); message.error(e instanceof Error ? e.message : '员工社保详情加载失败') }
    finally { setEmployeeDetailLoading(false) }
  }

  const openAdd = async () => {
    if (!monthDetail?.id) return
    setAddOpen(true); setAddEmployeeIds([]); setUninsuredLoading(true)
    try { setUninsured(await api.hrm.insurance.monthRecord.uninsuredEmployeeList(monthDetail.id)) }
    catch (e) { setUninsured([]); message.error(e instanceof Error ? e.message : '未参保员工加载失败') }
    finally { setUninsuredLoading(false) }
  }

  const addEmployees = async () => {
    if (!monthDetail?.id || !addEmployeeIds.length) return
    setActing(true)
    try {
      await api.hrm.insurance.monthRecord.employeeCreateList({ monthRecordId: monthDetail.id, employeeIds: addEmployeeIds })
      message.success('参保人员已添加'); setAddOpen(false); await refreshDetail()
    } catch (e) { message.error(e instanceof Error ? e.message : '添加失败') }
    finally { setActing(false) }
  }

  const useSchemeProjects = async (targetSchemeId?: number) => {
    setEditSchemeId(targetSchemeId)
    if (!targetSchemeId) { setEditSchemeType(undefined); setEditProjects([]); return }
    setEditLoading(true)
    try {
      const scheme = await api.hrm.insurance.scheme.get(targetSchemeId)
      setEditSchemeType(scheme.type)
      const projects = scheme.projectList || [...(scheme.socialSecurityProjectList || []), ...(scheme.providentFundProjectList || [])]
      setEditProjects(projects.map(project => ({ ...project, schemeProjectId: project.id })))
    } catch (e) { message.error(e instanceof Error ? e.message : '社保方案加载失败') }
    finally { setEditLoading(false) }
  }

  const openEdit = async (record?: HrmInsuranceMonthEmployeeRecord, ids = selectedIds) => {
    const targetIds = record ? [record.id] : ids
    if (!targetIds.length) return
    setEditOpen(true); setEditIds(targetIds); setEditLoading(true)
    setEditEmployeeLabel(record ? `${record.employeeName || '员工'}${record.jobNumber ? ` / ${record.jobNumber}` : ''}` : `已选 ${targetIds.length} 人`)
    try {
      if (record) {
        const detail = await api.hrm.insurance.monthRecord.employeeGet(record.id)
        setEditSchemeId(detail.schemeId); setEditSchemeType(detail.schemeType)
        setEditProjects([...(detail.socialSecurityProjectList || []), ...(detail.providentFundProjectList || [])].map(project => ({ ...project })))
      } else {
        setEditSchemeId(undefined); setEditSchemeType(undefined); setEditProjects([])
      }
    } catch (e) { message.error(e instanceof Error ? e.message : '参保信息加载失败') }
    finally { setEditLoading(false) }
  }

  const updateProject = (index: number, patch: Partial<HrmInsuranceProject>) => {
    setEditProjects(current => current.map((project, projectIndex) => projectIndex === index ? { ...project, ...patch } : project))
  }

  const saveEdit = async () => {
    if (!editSchemeId || !editIds.length || !editProjects.length) {
      message.warning('请选择社保方案并确认缴费项目')
      return
    }
    setEditLoading(true)
    const projects = editProjects.map(project => ({
      schemeProjectId: project.schemeProjectId!,
      ...(editSchemeType === INSURANCE_SCHEME_TYPE.PROPORTION
        ? { baseAmount: project.baseAmount }
        : { corporateAmount: project.corporateAmount, personalAmount: project.personalAmount })
    }))
    try {
      await Promise.all(editIds.map(id => api.hrm.insurance.monthRecord.employeeUpdate({ id, schemeId: editSchemeId, projects })))
      message.success(editIds.length > 1 ? '批量调整完成' : '参保方案已调整')
      setEditOpen(false); setEmployeeDetail(undefined); await refreshDetail()
    } catch (e) { message.error(e instanceof Error ? e.message : '调整失败') }
    finally { setEditLoading(false) }
  }

  const stopEmployees = (ids: number[]) => {
    if (!ids.length) return
    Modal.confirm({
      title: '停止参保',
      content: `确认停止选中的 ${ids.length} 名员工参保吗？`,
      okType: 'danger',
      okText: '停止参保',
      onOk: async () => {
        await api.hrm.insurance.monthRecord.employeeStopList(ids)
        message.success('已停止参保'); await refreshDetail()
      }
    })
  }

  const monthColumns: ColumnsType<HrmInsuranceMonthRecord> = [
    { title: '月份', width: 150, render: (_, row) => `${row.year} 年 ${row.month} 月` },
    { title: '参保人数', dataIndex: 'insuredEmployeeCount', width: 110, align: 'right', render: (value?: number) => `${value ?? 0} 人` },
    { title: '停保人数', dataIndex: 'stoppedEmployeeCount', width: 110, align: 'right', render: (value?: number) => `${value ?? 0} 人` },
    { title: '个人社保', dataIndex: 'personalInsuranceAmount', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '个人公积金', dataIndex: 'personalProvidentFundAmount', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '公司社保', dataIndex: 'corporateInsuranceAmount', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '公司公积金', dataIndex: 'corporateProvidentFundAmount', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '状态', dataIndex: 'status', width: 90, align: 'center', render: (value?: number) => value != null ? <Tag>{monthStatus.labels[String(value)] || value}</Tag> : '-' },
    { title: '操作', width: 130, fixed: 'right', render: (_, row) => <Space size="small"><Button type="link" size="small" onClick={() => void openMonthDetail(row)}>查看</Button>{canDelete && row.id != null && <Button type="link" size="small" danger onClick={() => Modal.confirm({ title: '删除社保表', content: '删除后该月员工参保记录也会被删除，确定继续吗？', okType: 'danger', onOk: async () => { await api.hrm.insurance.monthRecord.delete(row.id!); message.success('已删除'); reload() } })}>删除</Button>}</Space> }
  ]

  const employeeColumns: ColumnsType<HrmInsuranceMonthEmployeeRecord> = [
    { title: '姓名', dataIndex: 'employeeName', width: 120, fixed: 'left', render: (value: string, row) => <Button type="link" size="small" onClick={() => void openEmployeeDetail(row.id)}>{value || '-'}</Button> },
    { title: '工号', dataIndex: 'jobNumber', width: 110, render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 130, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '入职日期', dataIndex: 'entryTime', width: 110, render: fmtDate },
    { title: '手机号码', dataIndex: 'mobile', width: 130, render: (value?: string) => value || '-' },
    { title: '参保城市', dataIndex: 'areaName', width: 150, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '参保方案', dataIndex: 'schemeName', width: 160, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '个人社保', dataIndex: 'personalInsuranceAmount', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '公司社保', dataIndex: 'corporateInsuranceAmount', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '个人公积金', dataIndex: 'personalProvidentFundAmount', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '公司公积金', dataIndex: 'corporateProvidentFundAmount', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '状态', dataIndex: 'status', width: 90, align: 'center', render: (value?: number) => value != null ? <Tag>{insuredStatus.labels[String(value)] || value}</Tag> : '-' },
    { title: '操作', width: 90, fixed: 'right', render: (_, row) => editable ? <Button type="link" size="small" onClick={() => void openEdit(row)}>调整</Button> : null }
  ]

  const projectColumns: ColumnsType<HrmInsuranceProject> = [
    { title: '缴纳项目', dataIndex: 'name', render: (value: string | undefined, row) => value || (row.type != null ? projectType.labels[String(row.type)] : '-') || '-' },
    { title: '缴纳基数', dataIndex: 'baseAmount', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    ...(employeeDetail?.schemeType === INSURANCE_SCHEME_TYPE.PROPORTION ? [
      { title: '企业比例', dataIndex: 'corporateRate', width: 100, align: 'right' as const, render: (value?: number) => value != null ? `${value}%` : '-' },
      { title: '个人比例', dataIndex: 'personalRate', width: 100, align: 'right' as const, render: (value?: number) => value != null ? `${value}%` : '-' }
    ] : []),
    { title: '个人缴纳', dataIndex: 'personalAmount', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '企业缴纳', dataIndex: 'corporateAmount', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '合计', width: 110, align: 'right', render: (_, row) => `¥${fmtAmount(projectTotal(row))}` }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description={`${year} 年暂无社保月度记录`}/>
        : <>
          <HrmProTable<HrmInsuranceMonthRecord> advanced persistenceKey="insurance-month-record" onReload={reload} rowKey="id" columns={monthColumns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 1200 }}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  const employeeContent = employeeLoading && !employees.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : employeeError ? <Alert type="error" showIcon message={employeeError} action={<Button size="small" onClick={() => searchEmployees(employeePageNo)}>重试</Button>}/>
      : !employees.length ? <Empty description="当前条件下暂无参保员工"/>
        : <>
          <HrmProTable<HrmInsuranceMonthEmployeeRecord>
            rowKey="id"
            columns={employeeColumns}
            dataSource={employees}
            pagination={false}
            loading={employeeLoading}
            rowSelection={editable ? { selectedRowKeys: selectedIds, onChange: keys => setSelectedIds(keys.map(Number)) } : undefined}
            scroll={{ x: 1700 }}
          />
          <Pagination className="hrm-pagination" current={employeePageNo} total={employeeTotal} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={searchEmployees} showTotal={count => `共 ${count} 人`}/>
        </>

  const employeeProjects = employeeDetail ? [...(employeeDetail.socialSecurityProjectList || []), ...(employeeDetail.providentFundProjectList || [])] : []

  return <section className="workspace-page hrm-page hrm-insurance-month-page">
    <div className="page-heading">
      <Select value={year} onChange={value => { setYear(value); setPageNo(1) }} options={yearOptions(8)} style={{ width: 110 }}/>
      <Space><Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>{canCreate && <Button type="primary" loading={acting} onClick={createNext}>创建下月社保表</Button>}</Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={monthDetail?.title || '月度社保详情'} width={1100} open={monthDetailOpen} onClose={() => setMonthDetailOpen(false)} destroyOnClose>
      {monthDetailLoading && !monthDetail ? <Skeleton active/> : monthDetail && <>
        <Descriptions size="small" bordered column={3} className="hrm-summary" items={[
          { key: 'insured', label: '参保人数', children: `${monthDetail.insuredEmployeeCount ?? 0} 人` },
          { key: 'stopped', label: '停保人数', children: `${monthDetail.stoppedEmployeeCount ?? 0} 人` },
          { key: 'status', label: '状态', children: <Tag>{monthStatus.labels[String(monthDetail.status)] || monthDetail.status}</Tag> },
          { key: 'personalInsurance', label: '个人社保', children: `¥${fmtAmount(monthDetail.personalInsuranceAmount)}` },
          { key: 'corporateInsurance', label: '公司社保', children: `¥${fmtAmount(monthDetail.corporateInsuranceAmount)}` },
          { key: 'personalFund', label: '个人公积金', children: `¥${fmtAmount(monthDetail.personalProvidentFundAmount)}` },
          { key: 'corporateFund', label: '公司公积金', children: `¥${fmtAmount(monthDetail.corporateProvidentFundAmount)}` }
        ]}/>
        {!editable && monthDetail.status === INSURANCE_MONTH_STATUS.ARCHIVED && <Alert className="hrm-inline-alert" type="info" showIcon message="当前社保表已归档，仅可查询"/>}
        <div className="page-heading">
          <Space wrap>
            <Segmented value={employeeStatus} options={[{ value: INSURANCE_EMPLOYEE_STATUS.NORMAL, label: `参保 ${monthDetail.insuredEmployeeCount ?? 0}` }, { value: INSURANCE_EMPLOYEE_STATUS.STOPPED, label: `停保 ${monthDetail.stoppedEmployeeCount ?? 0}` }]} onChange={value => { setEmployeeStatus(Number(value)); setEmployeePageNo(1); void loadEmployees(monthDetail.id!, 1, { employeeName, schemeId, status: Number(value) }) }}/>
            <Input.Search allowClear placeholder="员工姓名" value={employeeName} onChange={event => setEmployeeName(event.target.value)} onSearch={() => searchEmployees(1)} style={{ width: 180 }}/>
            <Select allowClear placeholder="参保方案" value={schemeId} onChange={setSchemeId} options={schemes.map(scheme => ({ value: scheme.id!, label: scheme.name }))} style={{ width: 190 }}/>
            <Button onClick={() => searchEmployees(1)}>查询</Button>
          </Space>
          {editable && <Space wrap>
            <Button icon={<PlusOutlined/>} type="primary" onClick={() => void openAdd()}>添加参保人员</Button>
            <Button disabled={!selectedIds.length} onClick={() => void openEdit(undefined, selectedIds)}>调整参保方案</Button>
            <Button danger disabled={!selectedIds.length || !employees.some(row => selectedIds.includes(row.id) && row.status === INSURANCE_EMPLOYEE_STATUS.NORMAL)} onClick={() => stopEmployees(employees.filter(row => selectedIds.includes(row.id) && row.status === INSURANCE_EMPLOYEE_STATUS.NORMAL).map(row => row.id))}>停止参保</Button>
          </Space>}
        </div>
        <div className="hrm-table-area">{employeeContent}</div>
      </>}
    </Drawer>

    <Drawer title="员工月度社保详情" width="min(960px, 96vw)" open={!!employeeDetail} onClose={() => setEmployeeDetail(undefined)} destroyOnClose>
      {employeeDetailLoading ? <Skeleton active paragraph={{ rows: 10 }}/> : employeeDetail && <>
        <div className="page-heading"><Space><strong>{employeeDetail.employeeName || '-'}</strong><Tag>{insuredStatus.labels[String(employeeDetail.status)] || employeeDetail.status}</Tag></Space>{editable && <Button onClick={() => void openEdit(employeeDetail)}>编辑</Button>}</div>
        <Descriptions size="small" bordered column={3} className="hrm-summary" items={[
          { key: 'job', label: '工号', children: employeeDetail.jobNumber || '-' },
          { key: 'dept', label: '部门', children: employeeDetail.deptName || '-' },
          { key: 'post', label: '岗位', children: employeeDetail.postName || '-' },
          { key: 'entry', label: '入职日期', children: fmtDate(employeeDetail.entryTime) },
          { key: 'area', label: '参保城市', children: employeeDetail.areaName || '-' },
          { key: 'scheme', label: '参保方案', children: employeeDetail.schemeName || '-' },
          { key: 'idNumber', label: '身份证号', children: employeeDetail.idNumber || '-' },
          { key: 'socialNo', label: '个人社保号', children: employeeDetail.socialSecurityNumber || '-' },
          { key: 'fundNo', label: '个人公积金号', children: employeeDetail.accumulationFundNumber || '-' }
        ]}/>
        <h4 className="hrm-drawer-subtitle">缴费项目</h4>
        <HrmProTable rowKey={(row, index) => `${row.schemeProjectId || row.name}-${index}`} size="small" columns={projectColumns} dataSource={employeeProjects} pagination={false} scroll={{ x: 850 }}/>
      </>}
    </Drawer>

    <Modal title="添加参保人员" open={addOpen} onCancel={() => setAddOpen(false)} onOk={() => void addEmployees()} confirmLoading={acting} okButtonProps={{ disabled: !addEmployeeIds.length }} width="min(840px, 96vw)" destroyOnClose>
      <Select mode="multiple" showSearch optionFilterProp="label" placeholder="请选择本月未参保员工" loading={uninsuredLoading} value={addEmployeeIds} onChange={setAddEmployeeIds} options={uninsured.map(employee => ({ value: employee.id, label: `${employee.name || '未命名'}${employee.jobNumber ? ` / ${employee.jobNumber}` : ''}` }))} style={{ width: '100%' }}/>
      {!uninsuredLoading && !uninsured.length && <Empty description="没有可添加的未参保员工" imageStyle={{ height: 48 }}/>}
    </Modal>

    <Modal title={editIds.length > 1 ? '批量调整参保方案' : '调整参保方案'} open={editOpen} onCancel={() => setEditOpen(false)} onOk={() => void saveEdit()} confirmLoading={editLoading} width="min(1040px, 96vw)" destroyOnClose>
      <Space align="start" wrap style={{ marginBottom: 16 }}>
        <Input value={editEmployeeLabel} disabled style={{ width: 220 }}/>
        <Select placeholder="请选择社保方案" value={editSchemeId} onChange={value => void useSchemeProjects(value)} options={schemes.map(scheme => ({ value: scheme.id!, label: scheme.name }))} style={{ width: 240 }}/>
      </Space>
      <HrmProTable<HrmInsuranceProject> rowKey={(row, index) => `${row.schemeProjectId || row.name}-${index}`} size="small" pagination={false} dataSource={editProjects} columns={[
        { title: '类型', dataIndex: 'type', width: 120, render: (value?: number) => value != null ? projectType.labels[String(value)] || value : '-' },
        { title: '项目', dataIndex: 'name' },
        ...(editSchemeType === INSURANCE_SCHEME_TYPE.PROPORTION ? [
          { title: '缴纳基数', dataIndex: 'baseAmount', width: 150, render: (value: number | undefined, _row: HrmInsuranceProject, index: number) => <InputNumber min={0} precision={2} value={value} onChange={next => updateProject(index, { baseAmount: next ?? undefined })}/> },
          { title: '企业比例', dataIndex: 'corporateRate', width: 110, render: (value?: number) => value != null ? `${value}%` : '-' },
          { title: '个人比例', dataIndex: 'personalRate', width: 110, render: (value?: number) => value != null ? `${value}%` : '-' }
        ] : [
          { title: '企业金额', dataIndex: 'corporateAmount', width: 150, render: (value: number | undefined, _row: HrmInsuranceProject, index: number) => <InputNumber min={0} precision={2} value={value} onChange={next => updateProject(index, { corporateAmount: next ?? undefined })}/> },
          { title: '个人金额', dataIndex: 'personalAmount', width: 150, render: (value: number | undefined, _row: HrmInsuranceProject, index: number) => <InputNumber min={0} precision={2} value={value} onChange={next => updateProject(index, { personalAmount: next ?? undefined })}/> }
        ])
      ]}/>
    </Modal>
  </section>
}
