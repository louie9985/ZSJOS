import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Input, InputNumber, Modal, Pagination, Select, Skeleton, Space, Switch, Tag, Upload, message } from 'antd'
import { DownloadOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import {
  api,
  type HrmSalaryMonthEmployeeRecord,
  type HrmSalaryMonthRecord,
  type HrmSalaryOption,
  type HrmSalaryPayrollReadiness
} from '../services/api'
import { downloadBlob } from '../services/download'
import { useDict } from '../services/useDict'
import { HRM_DICT, SALARY_COMPUTED_OPTION_CODES, fmtAmount, yearOptions } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'

const PAGE_SIZE = 10

function leafOptions(options?: HrmSalaryOption[]) {
  const result: HrmSalaryOption[] = []
  const append = (nodes?: HrmSalaryOption[]) => {
    for (const node of nodes || []) {
      if (node.children?.length) append(node.children)
      else result.push(node)
    }
  }
  append(options)
  return result
}

function optionValue(record: HrmSalaryMonthEmployeeRecord, code?: number) {
  return code == null ? undefined : record.optionValues?.find(option => option.code === code)?.value
}

/** 月度工资表：核算准备、导入核算、员工工资项维护和历史查询。 */
export default function HrmSalaryMonthRecordPage({ permissions, historyOnly = false }: { permissions: string[]; historyOnly?: boolean }) {
  const [items, setItems] = useState<HrmSalaryMonthRecord[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)
  const [filterYear, setFilterYear] = useState<number>()
  const [acting, setActing] = useState(false)

  const [detail, setDetail] = useState<HrmSalaryMonthRecord>()
  const [detailLoading, setDetailLoading] = useState(false)
  const [employeeRows, setEmployeeRows] = useState<HrmSalaryMonthEmployeeRecord[]>([])
  const [employeeTotal, setEmployeeTotal] = useState(0)
  const [employeePageNo, setEmployeePageNo] = useState(1)
  const [employeeLoading, setEmployeeLoading] = useState(false)
  const [employeeError, setEmployeeError] = useState('')
  const employeeVersion = useRef(0)
  const [employeeName, setEmployeeName] = useState('')
  const [jobNumber, setJobNumber] = useState('')
  const [summaryOptions, setSummaryOptions] = useState<HrmSalaryOption[]>([])

  const [editorOpen, setEditorOpen] = useState(false)
  const [editorRows, setEditorRows] = useState<HrmSalaryMonthEmployeeRecord[]>([])
  const [editorLoading, setEditorLoading] = useState(false)
  const [dirtyIds, setDirtyIds] = useState<Set<number>>(new Set())

  const [computeOpen, setComputeOpen] = useState(false)
  const [computeRecord, setComputeRecord] = useState<HrmSalaryMonthRecord>()
  const [readiness, setReadiness] = useState<HrmSalaryPayrollReadiness>()
  const [readinessLoading, setReadinessLoading] = useState(false)
  const [syncInsurance, setSyncInsurance] = useState(true)
  const [syncAttendance, setSyncAttendance] = useState(false)
  const [attendanceFile, setAttendanceFile] = useState<File>()
  const [cumulativeTaxFile, setCumulativeTaxFile] = useState<File>()
  const [additionalDeductionFile, setAdditionalDeductionFile] = useState<File>()

  const monthStatus = useDict(HRM_DICT.SALARY_MONTH_STATUS)
  const canCreate = permissions.includes('hrm:salary:month-record:create')
  const canUpdate = permissions.includes('hrm:salary:month-record:update')
  const canCompute = permissions.includes('hrm:salary:month-record:compute')
  const canDelete = permissions.includes('hrm:salary:month-record:delete')
  const options = useMemo(() => leafOptions(detail?.optionHeaders), [detail?.optionHeaders])
  const editableOptions = useMemo(() => options.filter(option => option.code != null && !SALARY_COMPUTED_OPTION_CODES.has(option.code)), [options])
  const summaryMap = useMemo(() => new Map(summaryOptions.map(option => [option.code, option.value])), [summaryOptions])

  const loadPage = useCallback(async (page: number, year?: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.salary.monthRecord.page({ pageNo: page, pageSize: PAGE_SIZE, year, status: historyOnly ? 10 : undefined })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '工资表加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [historyOnly])

  useEffect(() => { void loadPage(pageNo, filterYear) }, [loadPage, pageNo, filterYear])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, filterYear) }, [loadPage, filterYear])

  const loadEmployees = useCallback(async (recordId: number, page: number, name: string, job: string) => {
    const version = ++employeeVersion.current
    setEmployeeLoading(true); setEmployeeError('')
    try {
      const params = { monthRecordId: recordId, employeeName: name || undefined, jobNumber: job || undefined }
      const [pageResult, summary] = await Promise.all([
        api.hrm.salary.monthRecord.employeePage({ ...params, pageNo: page, pageSize: PAGE_SIZE }),
        api.hrm.salary.monthRecord.optionSummary(params)
      ])
      if (version !== employeeVersion.current) return
      setEmployeeRows(pageResult.list); setEmployeeTotal(pageResult.total); setSummaryOptions(summary)
    } catch (e) {
      if (version === employeeVersion.current) setEmployeeError(e instanceof Error ? e.message : '工资明细加载失败')
    } finally {
      if (version === employeeVersion.current) setEmployeeLoading(false)
    }
  }, [])

  const openDetail = async (row: HrmSalaryMonthRecord) => {
    setDetail(row); setDetailLoading(true); setEmployeeName(''); setJobNumber(''); setEmployeePageNo(1)
    try {
      const record = await api.hrm.salary.monthRecord.get(row.id)
      setDetail(record)
      await loadEmployees(row.id, 1, '', '')
    } catch (e) { message.error(e instanceof Error ? e.message : '工资表详情加载失败') }
    finally { setDetailLoading(false) }
  }

  const searchEmployees = (page = 1) => {
    if (!detail?.id) return
    setEmployeePageNo(page)
    void loadEmployees(detail.id, page, employeeName, jobNumber)
  }

  const handleCreateNext = () => {
    Modal.confirm({
      title: '创建下月工资表',
      content: '将按当前在职员工与薪资档案生成下一个月的工资表。如果下月工资表已存在，操作会失败。',
      okText: '创建',
      onOk: async () => {
        setActing(true)
        try { await api.hrm.salary.monthRecord.createNext(); message.success('下月工资表已创建'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '创建失败'); throw e }
        finally { setActing(false) }
      }
    })
  }

  const openCompute = async (row: HrmSalaryMonthRecord) => {
    setComputeOpen(true); setComputeRecord(row); setReadiness(undefined); setReadinessLoading(true)
    setSyncInsurance(true); setSyncAttendance(false)
    setAttendanceFile(undefined); setCumulativeTaxFile(undefined); setAdditionalDeductionFile(undefined)
    try { setReadiness(await api.hrm.salary.monthRecord.payrollReadiness(row.id)) }
    catch (e) { message.error(e instanceof Error ? e.message : '核算准备情况加载失败') }
    finally { setReadinessLoading(false) }
  }

  const compute = async () => {
    if (!computeRecord?.id) return
    const data = new FormData()
    data.append('id', String(computeRecord.id))
    data.append('syncInsuranceData', String(syncInsurance))
    data.append('syncAttendanceData', String(syncAttendance))
    if (attendanceFile) data.append('attendanceFile', attendanceFile)
    if (cumulativeTaxFile) data.append('cumulativeTaxFile', cumulativeTaxFile)
    if (additionalDeductionFile) data.append('additionalDeductionFile', additionalDeductionFile)
    setActing(true)
    try {
      await api.hrm.salary.monthRecord.computeImport(data)
      message.success('核算完成'); setComputeOpen(false); reload()
      if (detail?.id === computeRecord.id) await openDetail(computeRecord)
    } catch (e) { message.error(e instanceof Error ? e.message : '核算失败') }
    finally { setActing(false) }
  }

  const template = async (type: 'attendance' | 'cumulative' | 'additional') => {
    const id = computeRecord?.id
    const config = type === 'attendance'
      ? ['/hrm/salary/month-record/get-attendance-import-template', '月度工资考勤导入模板.xls']
      : type === 'cumulative'
        ? ['/hrm/salary/month-record/get-cumulative-tax-import-template', '月度工资上月个税累计导入模板.xls']
        : ['/hrm/salary/month-record/get-additional-deduction-import-template', '月度工资专项附加扣除导入模板.xls']
    try { await downloadBlob(config[0], config[1], { monthRecordId: id }) }
    catch (e) { message.error(e instanceof Error ? e.message : '模板下载失败') }
  }

  const handleDelete = (row: HrmSalaryMonthRecord) => {
    Modal.confirm({
      title: '删除工资表',
      content: `确定要删除「${row.title || `${row.year} 年 ${row.month} 月`}」吗？该工资表下的员工工资记录会一并删除，且无法恢复。`,
      okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.salary.monthRecord.delete(row.id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const openEditor = async () => {
    if (!detail?.id) return
    setEditorOpen(true); setEditorLoading(true); setDirtyIds(new Set())
    try { setEditorRows(await api.hrm.salary.monthRecord.employeeList({ monthRecordId: detail.id, employeeName: employeeName || undefined, jobNumber: jobNumber || undefined })) }
    catch (e) { message.error(e instanceof Error ? e.message : '工资编辑数据加载失败'); setEditorRows([]) }
    finally { setEditorLoading(false) }
  }

  const editOption = (rowIndex: number, code: number, value?: number | null) => {
    setEditorRows(current => current.map((row, index) => {
      if (index !== rowIndex) return row
      const currentOptions = [...(row.optionValues || [])]
      const optionIndex = currentOptions.findIndex(option => option.code === code)
      if (optionIndex >= 0) currentOptions[optionIndex] = { ...currentOptions[optionIndex], value: Number(value || 0) }
      else currentOptions.push({ code, value: Number(value || 0) })
      return { ...row, optionValues: currentOptions }
    }))
    const id = editorRows[rowIndex]?.id
    if (id) setDirtyIds(current => new Set(current).add(id))
  }

  const saveEditor = async () => {
    const changed = editorRows.filter(row => row.id && dirtyIds.has(row.id)).map(row => ({ id: row.id!, optionValues: row.optionValues || [] }))
    if (!changed.length) return
    setEditorLoading(true)
    try {
      await api.hrm.salary.monthRecord.employeeUpdateList(changed)
      message.success('工资项已保存'); setEditorOpen(false); searchEmployees(employeePageNo)
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setEditorLoading(false) }
  }

  const columns: ColumnsType<HrmSalaryMonthRecord> = [
    { title: '工资表', dataIndex: 'title', width: 180, fixed: 'left', ellipsis: true, render: (value: string | undefined, row) => <Button type="link" onClick={() => void openDetail(row)}>{value || `${row.year} 年 ${row.month} 月`}</Button> },
    { title: '计薪人数', dataIndex: 'employeeCount', width: 100, align: 'right', render: (value?: number) => value != null ? `${value} 人` : '-' },
    { title: '应发工资', dataIndex: 'expectedPaySalary', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '个人社保', dataIndex: 'personalInsuranceAmount', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '个人公积金', dataIndex: 'personalProvidentFundAmount', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '个税', dataIndex: 'personalTax', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '实发工资', dataIndex: 'realPaySalary', width: 130, align: 'right', render: (value?: number) => value != null ? <strong>¥{fmtAmount(value)}</strong> : '-' },
    { title: '状态', dataIndex: 'status', width: 100, align: 'center', render: (value?: number) => value != null ? <Tag>{monthStatus.labels[String(value)] || value}</Tag> : '-' },
    { title: '操作', width: 150, align: 'center', fixed: 'right', render: (_, row) => historyOnly ? null : <Space size="small">
      {canCompute && <Button type="link" size="small" disabled={acting} onClick={() => void openCompute(row)}>核算</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  const employeeColumns: ColumnsType<HrmSalaryMonthEmployeeRecord> = [
    { title: '员工', dataIndex: 'employeeName', width: 120, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 130, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '岗位', dataIndex: 'postName', width: 130, render: (value?: string) => value || '-' },
    ...options.map(option => ({
      title: option.name || String(option.code), dataIndex: `option-${option.code}`, width: 120, align: 'right' as const,
      render: (_: unknown, row: HrmSalaryMonthEmployeeRecord) => {
        const value = optionValue(row, option.code)
        return value != null ? `¥${fmtAmount(value)}` : '-'
      }
    }))
  ]

  const editorColumns: ColumnsType<HrmSalaryMonthEmployeeRecord> = [
    { title: '员工', dataIndex: 'employeeName', width: 120, fixed: 'left' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, fixed: 'left' },
    { title: '部门', dataIndex: 'deptName', width: 130, fixed: 'left' },
    ...editableOptions.map(option => ({
      title: option.name || String(option.code), width: 150,
      render: (_: unknown, row: HrmSalaryMonthEmployeeRecord, index: number) => <InputNumber min={0} max={100000000} precision={2} value={optionValue(row, option.code)} onChange={value => editOption(index, option.code!, value)} style={{ width: '100%' }}/>
    }))
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无月度工资表"/>
        : <>
          <HrmProTable<HrmSalaryMonthRecord> advanced persistenceKey="salary-month-record" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1350 }} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 张`}/>
        </>

  const employeeContent = employeeLoading && !employeeRows.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : employeeError ? <Alert type="error" showIcon message={employeeError} action={<Button size="small" onClick={() => searchEmployees(employeePageNo)}>重试</Button>}/>
      : !employeeRows.length ? <Empty description="当前条件下暂无员工工资"/>
        : <>
          <HrmProTable<HrmSalaryMonthEmployeeRecord> rowKey={row => row.id ?? row.employeeId ?? 0} columns={employeeColumns} dataSource={employeeRows} pagination={false} loading={employeeLoading} scroll={{ x: Math.max(800, 490 + options.length * 120) }} summary={() => <HrmProTable.Summary fixed><HrmProTable.Summary.Row><HrmProTable.Summary.Cell index={0}>合计</HrmProTable.Summary.Cell><HrmProTable.Summary.Cell index={1}/><HrmProTable.Summary.Cell index={2}/><HrmProTable.Summary.Cell index={3}/>{options.map((option, index) => <HrmProTable.Summary.Cell key={option.code ?? index} index={index + 4} align="right">{summaryMap.get(option.code) != null ? `¥${fmtAmount(summaryMap.get(option.code))}` : '-'}</HrmProTable.Summary.Cell>)}</HrmProTable.Summary.Row></HrmProTable.Summary>}/>
          <Pagination className="hrm-pagination" current={employeePageNo} total={employeeTotal} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={searchEmployees} showTotal={count => `共 ${count} 人`}/>
        </>

  return <section className="workspace-page hrm-page hrm-salary-month-record-page">
    <div className="page-heading">
      <Select allowClear placeholder="年份" value={filterYear} onChange={value => { setFilterYear(value); setPageNo(1) }} style={{ width: 120 }} options={yearOptions()}/>
      <Space>
        {!historyOnly && canCreate && <Button type="primary" icon={<PlusOutlined/>} loading={acting} onClick={handleCreateNext}>创建下月工资表</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {monthStatus.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`工资表状态字典加载失败：${monthStatus.error}`} action={<Button size="small" onClick={monthStatus.reload}>重试</Button>}/>}
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.title || '工资表详情'} width={1100} open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detailLoading && !detail ? <Skeleton active/> : detail && <>
        <Descriptions bordered size="small" column={4} className="hrm-summary" items={[
          { key: 'count', label: '计薪人数', children: `${detail.employeeCount ?? 0} 人` },
          { key: 'expected', label: '应发工资', children: `¥${fmtAmount(detail.expectedPaySalary)}` },
          { key: 'tax', label: '个税', children: `¥${fmtAmount(detail.personalTax)}` },
          { key: 'real', label: '实发工资', children: `¥${fmtAmount(detail.realPaySalary)}` }
        ]}/>
        <div className="page-heading">
          <Space wrap><Input.Search allowClear placeholder="员工姓名" value={employeeName} onChange={event => setEmployeeName(event.target.value)} onSearch={() => searchEmployees(1)} style={{ width: 180 }}/><Input.Search allowClear placeholder="工号" value={jobNumber} onChange={event => setJobNumber(event.target.value)} onSearch={() => searchEmployees(1)} style={{ width: 160 }}/><Button onClick={() => searchEmployees(1)}>查询</Button></Space>
          {!historyOnly && canUpdate && <Button onClick={() => void openEditor()}>在线编辑工资</Button>}
        </div>
        <div className="hrm-table-area">{employeeContent}</div>
      </>}
    </Drawer>

    <Modal title="在线编辑工资" open={editorOpen} onCancel={() => setEditorOpen(false)} onOk={() => void saveEditor()} confirmLoading={editorLoading} okButtonProps={{ disabled: !dirtyIds.size }} width="calc(100vw - 48px)" destroyOnClose>
      {!editableOptions.length && !editorLoading ? <Empty description="当前工资表没有可人工编辑的工资项"/> : <HrmProTable<HrmSalaryMonthEmployeeRecord> rowKey={row => row.id ?? row.employeeId ?? 0} size="small" loading={editorLoading} dataSource={editorRows} columns={editorColumns} pagination={false} scroll={{ x: Math.max(700, 360 + editableOptions.length * 150), y: 'calc(100vh - 320px)' }}/>}
    </Modal>

    <Modal title="核算工资表" open={computeOpen} onCancel={() => setComputeOpen(false)} onOk={() => void compute()} confirmLoading={acting} width="min(960px, 96vw)" destroyOnClose>
      {readinessLoading ? <Skeleton active paragraph={{ rows: 8 }}/> : <>
        <Descriptions bordered size="small" column={3} items={[
          { key: 'title', label: '工资表', children: computeRecord?.title || '-' },
          { key: 'payroll', label: '计薪人员', children: `${readiness?.payrollEmployeeCount ?? 0} 人` },
          { key: 'salary', label: '已定薪', children: `${readiness?.salaryEmployeeCount ?? 0} 人` },
          { key: 'noSalary', label: '未定薪', children: `${readiness?.noSalaryEmployeeCount ?? 0} 人` },
          { key: 'noGroup', label: '未分配薪资组', children: `${readiness?.noSalaryGroupEmployeeCount ?? 0} 人` },
          { key: 'change', label: '异动员工', children: `${readiness?.changeEmployeeCount ?? 0} 人` }
        ]}/>
        {Boolean(readiness?.noSalaryEmployeeCount || readiness?.noSalaryGroupEmployeeCount) && <Alert className="hrm-inline-alert" type="warning" showIcon message="存在未定薪或未分配薪资组员工，请确认后再核算" description={[...(readiness?.noSalaryEmployees || []), ...(readiness?.noSalaryGroupEmployees || [])].slice(0, 8).map(employee => employee.employeeName || employee.jobNumber).filter(Boolean).join('、') || undefined}/>}
        <Descriptions size="small" column={1} items={[
          { key: 'insurance', label: '社保数据', children: <Switch checked={syncInsurance} onChange={setSyncInsurance} checkedChildren="从社保表同步" unCheckedChildren="本次不带入"/> },
          { key: 'attendanceSync', label: '考勤来源', children: <Switch checked={syncAttendance} onChange={setSyncAttendance} checkedChildren="从考勤统计同步" unCheckedChildren="使用导入文件"/> },
          { key: 'attendance', label: '考勤数据', children: <Space><Upload maxCount={1} accept=".xls,.xlsx" beforeUpload={file => { setAttendanceFile(file); return false }} onRemove={() => setAttendanceFile(undefined)} fileList={attendanceFile ? [{ uid: 'attendance', name: attendanceFile.name }] : []} disabled={syncAttendance}><Button icon={<UploadOutlined/>} disabled={syncAttendance}>选择文件</Button></Upload><Button icon={<DownloadOutlined/>} onClick={() => void template('attendance')}>下载模板</Button></Space> },
          { key: 'tax', label: '上月个税累计', children: <Space><Upload maxCount={1} accept=".xls,.xlsx" beforeUpload={file => { setCumulativeTaxFile(file); return false }} onRemove={() => setCumulativeTaxFile(undefined)} fileList={cumulativeTaxFile ? [{ uid: 'tax', name: cumulativeTaxFile.name }] : []}><Button icon={<UploadOutlined/>}>选择文件</Button></Upload><Button icon={<DownloadOutlined/>} onClick={() => void template('cumulative')}>下载模板</Button></Space> },
          { key: 'deduction', label: '专项附加扣除', children: <Space><Upload maxCount={1} accept=".xls,.xlsx" beforeUpload={file => { setAdditionalDeductionFile(file); return false }} onRemove={() => setAdditionalDeductionFile(undefined)} fileList={additionalDeductionFile ? [{ uid: 'deduction', name: additionalDeductionFile.name }] : []}><Button icon={<UploadOutlined/>}>选择文件</Button></Upload><Button icon={<DownloadOutlined/>} onClick={() => void template('additional')}>下载模板</Button></Space> }
        ]}/>
      </>}
    </Modal>
  </section>
}
