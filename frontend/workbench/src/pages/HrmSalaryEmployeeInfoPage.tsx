import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Descriptions, Drawer, Empty, Form, Input, InputNumber, Modal, Pagination, Segmented, Select, Skeleton, Space, Tag, Upload, message } from 'antd'
import { DownloadOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import {
  api,
  type HrmSalaryChangeRecord,
  type HrmSalaryEmployeeInfo,
  type HrmSalaryOption
} from '../services/api'
import { downloadBlob } from '../services/download'
import { useDict } from '../services/useDict'
import {
  HRM_DICT,
  SALARY_BATCH_ADJUST_TYPE,
  SALARY_CHANGE_RECORD_STATUS,
  fmtAmount
} from '../services/hrm'
import SalaryOptionTable from '../components/SalaryOptionTable'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10
const BASIC_SALARY_CATEGORY_CODE = 10

type SalaryFormValues = {
  recordType: number; changeReason: number; effectTime?: dayjs.Dayjs; remark?: string
  salaryOptions?: Array<{ code?: number; name?: string; value?: number }>
  probationSalaryOptions?: Array<{ code?: number; name?: string; value?: number }>
}

type BatchFormValues = {
  type: number; changeReason: number; effectTime: dayjs.Dayjs; remark?: string
  salaryOptions: Array<{ code?: number; name?: string; value?: number }>
}

function fmtDate(value?: number | null) {
  return value ? dayjs(value).format('YYYY-MM-DD') : '-'
}

/** 薪资档案：单人/批量定薪调薪、Excel 导入和调薪记录维护。 */
export default function HrmSalaryEmployeeInfoPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmSalaryEmployeeInfo[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<number[]>([])

  const [detail, setDetail] = useState<HrmSalaryEmployeeInfo>()
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [changeRecords, setChangeRecords] = useState<HrmSalaryChangeRecord[]>([])
  const [changeLoading, setChangeLoading] = useState(false)
  const detailVersion = useRef(0)

  const [editOpen, setEditOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState<HrmSalaryChangeRecord>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<SalaryFormValues>()

  const [batchOpen, setBatchOpen] = useState(false)
  const [batchLoading, setBatchLoading] = useState(false)
  const [batchMinDate, setBatchMinDate] = useState<string | null>()
  const [batchForm] = Form.useForm<BatchFormValues>()
  const batchType = Form.useWatch('type', batchForm) ?? SALARY_BATCH_ADJUST_TYPE.PERCENT

  const [importOpen, setImportOpen] = useState(false)
  const [importType, setImportType] = useState<'fix' | 'change'>('fix')
  const [importFile, setImportFile] = useState<File>()
  const [importLoading, setImportLoading] = useState(false)

  const employeeStatus = useDict(HRM_DICT.EMPLOYEE_STATUS)
  const optionType = useDict(HRM_DICT.SALARY_OPTION_TYPE)
  const changeReason = useDict(HRM_DICT.SALARY_CHANGE_REASON)
  const changeRecordStatus = useDict(HRM_DICT.SALARY_CHANGE_RECORD_STATUS)
  const canUpdate = permissions.includes('hrm:salary:employee-info:update')
  const canImport = permissions.includes('hrm:salary:employee-info:import')
  const canDeleteRecord = permissions.includes('hrm:salary:change-record:delete')

  const loadPage = useCallback(async (page: number, keyword: string) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.salary.employeeInfo.page({ pageNo: page, pageSize: PAGE_SIZE, search: keyword || undefined })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total); setSelectedEmployeeIds([])
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '薪资档案加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, search) }, [loadPage, pageNo, search])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, search) }, [loadPage, search])

  const loadChangeRecords = async (employeeId: number) => {
    setChangeLoading(true)
    try { setChangeRecords(await api.hrm.salary.changeRecord.list(employeeId)) }
    catch (e) { setChangeRecords([]); message.error(e instanceof Error ? e.message : '调薪记录加载失败') }
    finally { setChangeLoading(false) }
  }

  const openDetail = async (row: HrmSalaryEmployeeInfo) => {
    if (row.employeeId == null) return
    const version = ++detailVersion.current
    setDetail(row); setDetailOpen(true); setDetailLoading(true); setChangeRecords([])
    try {
      const [result] = await Promise.all([api.hrm.salary.employeeInfo.get(row.employeeId), loadChangeRecords(row.employeeId)])
      if (version !== detailVersion.current) return
      setDetail(result)
    } catch (e) {
      if (version === detailVersion.current) message.error(e instanceof Error ? e.message : '薪资详情加载失败')
    } finally {
      if (version === detailVersion.current) setDetailLoading(false)
    }
  }

  const openEdit = (record?: HrmSalaryChangeRecord) => {
    if (!detail?.employeeId) return
    const source = record || detail
    setEditingRecord(record)
    form.setFieldsValue({
      recordType: record?.recordType ?? (detail.changeType == null ? 1 : 2),
      changeReason: source.changeReason ?? 0,
      effectTime: source.effectTime ? dayjs(source.effectTime) : dayjs(),
      remark: source.remark,
      salaryOptions: source.salaryOptions?.map(item => ({ code: item.code, name: item.name, value: item.value })),
      probationSalaryOptions: source.probationSalaryOptions?.map(item => ({ code: item.code, name: item.name, value: item.value }))
    })
    setEditOpen(true)
  }

  const saveEdit = async () => {
    if (!detail?.employeeId) return
    const values = await form.validateFields()
    setSaving(true)
    try {
      await api.hrm.salary.employeeInfo.update({
        id: editingRecord?.id,
        employeeId: detail.employeeId,
        recordType: values.recordType,
        changeReason: values.changeReason,
        effectTime: values.effectTime?.startOf('day').valueOf(),
        remark: values.remark,
        salaryOptions: values.salaryOptions,
        probationSalaryOptions: values.probationSalaryOptions
      })
      message.success('薪资档案已保存'); setEditOpen(false); await openDetail(detail); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const openBatch = async () => {
    if (!selectedEmployeeIds.length) return
    setBatchOpen(true); setBatchLoading(true)
    try {
      const [options, minDate] = await Promise.all([api.hrm.salaryCfg.option.list(), api.hrm.salary.employeeInfo.minEffectDate()])
      const salaryOptions = options.filter(option => option.parentCode === BASIC_SALARY_CATEGORY_CODE).map(option => ({ code: option.code, name: option.name, value: 0 }))
      setBatchMinDate(minDate)
      batchForm.setFieldsValue({ type: SALARY_BATCH_ADJUST_TYPE.PERCENT, changeReason: 0, effectTime: dayjs(), remark: '', salaryOptions })
    } catch (e) { message.error(e instanceof Error ? e.message : '批量调薪配置加载失败') }
    finally { setBatchLoading(false) }
  }

  const saveBatch = async () => {
    const values = await batchForm.validateFields()
    setBatchLoading(true)
    try {
      const result = await api.hrm.salary.employeeInfo.updateList({
        employeeIds: selectedEmployeeIds,
        deptIds: [],
        type: values.type,
        changeReason: values.changeReason,
        effectTime: values.effectTime.startOf('day').valueOf(),
        remark: values.remark,
        salaryOptions: values.salaryOptions as HrmSalaryOption[]
      })
      const successCount = result.successEmployeeIds.length
      const failureCount = Object.keys(result.failureEmployeeReasons).length
      if (failureCount) message.warning(`批量调薪完成：成功 ${successCount} 人，失败 ${failureCount} 人`)
      else message.success(`批量调薪完成：成功 ${successCount} 人`)
      if (successCount) { setBatchOpen(false); reload() }
    } catch (e) { message.error(e instanceof Error ? e.message : '批量调薪失败') }
    finally { setBatchLoading(false) }
  }

  const openImport = (type: 'fix' | 'change') => {
    setImportType(type); setImportFile(undefined); setImportOpen(true)
  }

  const downloadImportTemplate = async () => {
    const fix = importType === 'fix'
    try { await downloadBlob(`/hrm/salary/employee-info/get-${fix ? 'fix' : 'change'}-import-template`, `薪资档案${fix ? '定薪' : '调薪'}导入模板.xls`) }
    catch (e) { message.error(e instanceof Error ? e.message : '模板下载失败') }
  }

  const submitImport = async () => {
    if (!importFile) { message.warning('请选择 Excel 文件'); return }
    setImportLoading(true)
    try {
      const result = importType === 'fix' ? await api.hrm.salary.employeeInfo.importFix(importFile) : await api.hrm.salary.employeeInfo.importChange(importFile)
      const failureCount = Object.keys(result.failureJobNumbers).length
      const summary = `导入成功 ${result.successJobNumbers.length} 条，失败 ${failureCount} 条`
      failureCount ? message.warning(summary) : message.success(summary)
      if (result.successJobNumbers.length) { setImportOpen(false); reload() }
    } catch (e) { message.error(e instanceof Error ? e.message : '导入失败') }
    finally { setImportLoading(false) }
  }

  const cancelRecord = (record: HrmSalaryChangeRecord) => {
    Modal.confirm({ title: '取消调薪记录', content: '确认取消该待生效的薪资调整吗？', onOk: async () => {
      await api.hrm.salary.changeRecord.cancel(record.id); message.success('已取消')
      if (detail?.employeeId) { await loadChangeRecords(detail.employeeId); await openDetail(detail) }
    } })
  }

  const deleteRecord = (record: HrmSalaryChangeRecord) => {
    Modal.confirm({ title: '删除调薪记录', content: '确定删除该未生效的薪资调整记录吗？', okType: 'danger', onOk: async () => {
      await api.hrm.salary.changeRecord.delete(record.id); message.success('已删除')
      if (detail?.employeeId) { await loadChangeRecords(detail.employeeId); await openDetail(detail) }
    } })
  }

  const columns: ColumnsType<HrmSalaryEmployeeInfo> = [
    { title: '员工', dataIndex: 'employeeName', width: 110, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 140, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '职位', dataIndex: 'postName', width: 120, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '员工状态', dataIndex: 'status', width: 100, render: (value?: number) => value != null ? (employeeStatus.labels[String(value)] || value) : '-' },
    { title: '试用期工资', dataIndex: 'probationSalary', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '转正工资', dataIndex: 'regularSalary', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '入职时间', dataIndex: 'entryTime', width: 120, render: fmtDate },
    { title: '操作', width: 90, align: 'center', fixed: 'right', render: (_, row) => <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button> }
  ]

  const changeColumns: ColumnsType<HrmSalaryChangeRecord> = [
    { title: '类型', dataIndex: 'recordType', width: 80, render: (value?: number) => value === 1 ? '定薪' : '调薪' },
    { title: '调整原因', dataIndex: 'changeReason', width: 110, render: (value?: number) => value != null ? changeReason.labels[String(value)] || value : '-' },
    { title: '生效日期', dataIndex: 'effectTime', width: 110, render: fmtDate },
    { title: '正式调整前', dataIndex: 'beforeTotal', width: 110, align: 'right', render: (value?: number) => `¥${fmtAmount(value)}` },
    { title: '正式调整后', dataIndex: 'afterTotal', width: 110, align: 'right', render: (value?: number) => `¥${fmtAmount(value)}` },
    { title: '状态', dataIndex: 'status', width: 100, render: (value?: number) => value != null ? <Tag>{changeRecordStatus.labels[String(value)] || value}</Tag> : '-' },
    { title: '操作', width: 160, fixed: 'right', render: (_, record) => <Space size="small">
      {canUpdate && record.status !== SALARY_CHANGE_RECORD_STATUS.EFFECTIVE && <Button type="link" size="small" onClick={() => openEdit(record)}>编辑</Button>}
      {canUpdate && record.status === SALARY_CHANGE_RECORD_STATUS.PENDING && <Button type="link" size="small" onClick={() => cancelRecord(record)}>取消</Button>}
      {canDeleteRecord && record.status !== SALARY_CHANGE_RECORD_STATUS.EFFECTIVE && <Button type="link" size="small" danger onClick={() => deleteRecord(record)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无薪资档案"/>
        : <><HrmProTable<HrmSalaryEmployeeInfo> advanced persistenceKey="salary-employee-info" onReload={reload} rowKey={row => row.employeeId ?? row.id ?? 0} columns={columns} dataSource={items} pagination={false} scroll={{ x: 1100 }} loading={loading} rowSelection={canUpdate ? { selectedRowKeys: selectedEmployeeIds, onChange: keys => setSelectedEmployeeIds(keys.map(Number)), getCheckboxProps: row => ({ disabled: row.employeeId == null }) } : undefined}/><Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 人`}/></>

  return <section className="workspace-page hrm-page hrm-salary-employee-info-page">
    <div className="page-heading">
      <Input.Search allowClear placeholder="员工姓名或工号" value={search} onChange={event => setSearch(event.target.value)} onSearch={() => reload()} style={{ width: 220 }}/>
      <Space wrap>
        {canUpdate && <Button disabled={!selectedEmployeeIds.length} onClick={() => void openBatch()}>批量调薪</Button>}
        {canImport && <><Button icon={<UploadOutlined/>} onClick={() => openImport('fix')}>定薪导入</Button><Button icon={<UploadOutlined/>} onClick={() => openImport('change')}>调薪导入</Button></>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.employeeName ? `${detail.employeeName} · 薪资档案` : '薪资档案'} width="min(1040px, 96vw)" open={detailOpen} onClose={() => setDetailOpen(false)} destroyOnClose>
      {detailLoading && !detail?.salaryOptions ? <Skeleton active paragraph={{ rows: 10 }}/> : detail && <>
        <Descriptions className="hrm-summary" size="small" column={3} bordered items={[
          { key: 'employee', label: '员工', children: `${detail.employeeName || '-'}${detail.jobNumber ? `（${detail.jobNumber}）` : ''}` },
          { key: 'dept', label: '部门', children: detail.deptName || '-' },
          { key: 'post', label: '职位', children: detail.postName || '-' },
          { key: 'probation', label: '试用期工资', children: detail.probationSalary != null ? `¥${fmtAmount(detail.probationSalary)}` : '-' },
          { key: 'regular', label: '转正工资', children: detail.regularSalary != null ? `¥${fmtAmount(detail.regularSalary)}` : '-' },
          { key: 'reason', label: '最近调薪原因', children: detail.changeReason != null ? changeReason.labels[String(detail.changeReason)] || detail.changeReason : '-' }
        ]}/>
        {canUpdate && <Button type="primary" onClick={() => openEdit()}>定薪/调薪</Button>}
        <h4 className="hrm-drawer-subtitle">转正工资项</h4>
        {detail.salaryOptions?.length ? <SalaryOptionTable options={detail.salaryOptions} typeLabels={optionType.labels}/> : <Empty description="暂无转正工资项"/>}
        <h4 className="hrm-drawer-subtitle">试用期工资项</h4>
        {detail.probationSalaryOptions?.length ? <SalaryOptionTable options={detail.probationSalaryOptions} typeLabels={optionType.labels}/> : <Empty description="暂无试用期工资项"/>}
        <h4 className="hrm-drawer-subtitle">定薪/调薪记录</h4>
        <HrmProTable<HrmSalaryChangeRecord> rowKey="id" size="small" loading={changeLoading} columns={changeColumns} dataSource={changeRecords} pagination={false} scroll={{ x: 850 }}/>
      </>}
    </Drawer>

    <Modal title={editingRecord ? '编辑定薪/调薪记录' : '定薪/调薪'} open={editOpen} onCancel={() => setEditOpen(false)} onOk={() => void saveEdit()} confirmLoading={saving} width="min(960px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Space align="start" wrap>
          <Form.Item name="recordType" label="记录类型" rules={[{ required: true }]}><Select options={[{ value: 1, label: '定薪' }, { value: 2, label: '调薪' }]} style={{ width: 160 }}/></Form.Item>
          <Form.Item name="changeReason" label="调整原因" rules={[{ required: true }]}><Select loading={changeReason.loading} options={changeReason.options} style={{ width: 180 }}/></Form.Item>
          <Form.Item name="effectTime" label="生效日期" rules={[{ required: true }]}><DatePicker/></Form.Item>
        </Space>
        <h4 className="hrm-drawer-subtitle">转正工资项</h4>
        <Form.List name="salaryOptions">{fields => <>{fields.map(field => <Space key={field.key} align="baseline"><Form.Item {...field} name={[field.name, 'code']} hidden><Input/></Form.Item><Form.Item {...field} name={[field.name, 'name']}><Input disabled style={{ width: 180 }}/></Form.Item><Form.Item {...field} name={[field.name, 'value']}><InputNumber min={0} precision={2} addonAfter="元"/></Form.Item></Space>)}</>}</Form.List>
        <h4 className="hrm-drawer-subtitle">试用期工资项</h4>
        <Form.List name="probationSalaryOptions">{fields => <>{fields.map(field => <Space key={field.key} align="baseline"><Form.Item {...field} name={[field.name, 'code']} hidden><Input/></Form.Item><Form.Item {...field} name={[field.name, 'name']}><Input disabled style={{ width: 180 }}/></Form.Item><Form.Item {...field} name={[field.name, 'value']}><InputNumber min={0} precision={2} addonAfter="元"/></Form.Item></Space>)}</>}</Form.List>
        <Form.Item name="remark" label="备注"><Input.TextArea rows={3} maxLength={500} showCount/></Form.Item>
      </Form>
    </Modal>

    <Modal title={`批量调薪（已选 ${selectedEmployeeIds.length} 人）`} open={batchOpen} onCancel={() => setBatchOpen(false)} onOk={() => void saveBatch()} confirmLoading={batchLoading} width="min(960px, 96vw)" destroyOnClose>
      <Form form={batchForm} layout="vertical">
        <Space align="start" wrap>
          <Form.Item name="type" label="调薪方式" rules={[{ required: true }]}><Segmented options={[{ value: SALARY_BATCH_ADJUST_TYPE.PERCENT, label: '按比例' }, { value: SALARY_BATCH_ADJUST_TYPE.AMOUNT, label: '按金额' }]}/></Form.Item>
          <Form.Item name="changeReason" label="调整原因" rules={[{ required: true }]}><Select loading={changeReason.loading} options={changeReason.options} style={{ width: 180 }}/></Form.Item>
          <Form.Item name="effectTime" label="生效日期" rules={[{ required: true }]}><DatePicker disabledDate={date => Boolean(batchMinDate && date.isBefore(dayjs(batchMinDate), 'day'))}/></Form.Item>
        </Space>
        <Form.List name="salaryOptions">{fields => <>{fields.map(field => <Space key={field.key} align="baseline"><Form.Item {...field} name={[field.name, 'code']} hidden><Input/></Form.Item><Form.Item {...field} name={[field.name, 'name']}><Input disabled style={{ width: 220 }}/></Form.Item><Form.Item {...field} name={[field.name, 'value']}><InputNumber precision={2} addonAfter={batchType === SALARY_BATCH_ADJUST_TYPE.PERCENT ? '%' : '元'}/></Form.Item></Space>)}</>}</Form.List>
        <Form.Item name="remark" label="备注"><Input.TextArea rows={3} maxLength={500} showCount/></Form.Item>
      </Form>
    </Modal>

    <Modal title={`薪资档案${importType === 'fix' ? '定薪' : '调薪'}导入`} open={importOpen} onCancel={() => setImportOpen(false)} onOk={() => void submitImport()} confirmLoading={importLoading} width="min(960px, 96vw)" destroyOnClose>
      <Upload.Dragger accept=".xls,.xlsx" maxCount={1} beforeUpload={file => { setImportFile(file); return false }} onRemove={() => setImportFile(undefined)} fileList={importFile ? [{ uid: 'salary-import', name: importFile.name }] : []}>
        <UploadOutlined/>
        <div>选择或拖入 Excel 文件</div>
      </Upload.Dragger>
      <Button type="link" icon={<DownloadOutlined/>} onClick={() => void downloadImportTemplate()}>下载导入模板</Button>
    </Modal>
  </section>
}
