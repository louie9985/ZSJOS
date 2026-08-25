import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Alert, Badge, Button, Descriptions, Drawer, Empty, Modal, Pagination, Skeleton, Space, Tabs, Tag, message } from 'antd'
import { BellOutlined, DeleteOutlined, DownloadOutlined, PlusOutlined, ReloadOutlined, UploadOutlined, UserAddOutlined } from '@ant-design/icons'
import { api, type HrmEmployee } from '../services/api'
import { useDict } from '../services/useDict'
import {
  CHANGE_TYPE, CHANGE_TYPE_LABELS, EMPLOYEE_STATUS_TAB, EMPLOYEE_STATUS_TAB_LABELS,
  EMPLOYEE_TYPE_LABELS, EMPLOYEE_STATUS_LABELS, ENTRY_STATUS_COLORS, ENTRY_STATUS_LABELS,
  HRM_DICT, employeeActionsOf
} from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { EmployeeFormModal, PositionChangeModal, EmployeeQuitModal } from '../components/EmployeeModals'
import EmployeeSubTabs from '../components/EmployeeSubTabs'
import { HrmEmployeeCreateFromUserModal, HrmEmployeeImportModal } from '../components/HrmEmployeeBulkModals'
import { downloadBlob } from '../services/download'

const PAGE_SIZE = 10

function fmtDate(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }

const EMPLOYEE_TABS = [
  { key: String(EMPLOYEE_STATUS_TAB.ACTIVE), label: '在职' },
  { key: String(EMPLOYEE_STATUS_TAB.FULL_TIME), label: '全职' },
  { key: String(EMPLOYEE_STATUS_TAB.PENDING_ENTRY), label: '待入职' },
  { key: String(EMPLOYEE_STATUS_TAB.PENDING_LEAVE), label: '待离职' },
  { key: String(EMPLOYEE_STATUS_TAB.LEFT), label: '已离职' }
]

/** 管理端员工档案：列表（状态页签）+ 详情 + 新增/编辑/异动/离职。 */
export default function HrmEmployeePage({ permissions }: { permissions: string[] }) {
  const [tab, setTab] = useState(String(EMPLOYEE_STATUS_TAB.ACTIVE))
  const [items, setItems] = useState<HrmEmployee[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [statusCounts, setStatusCounts] = useState<Record<number, number>>({})
  const [detail, setDetail] = useState<HrmEmployee>()
  const [detailOpen, setDetailOpen] = useState(false)

  const [formOpen, setFormOpen] = useState(false)
  const [formEmployee, setFormEmployee] = useState<HrmEmployee>()
  const [changeType, setChangeType] = useState<number>()
  const [quitOpen, setQuitOpen] = useState(false)
  const [actingEmployee, setActingEmployee] = useState<HrmEmployee>()
  const [acting, setActing] = useState(false)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [importOpen, setImportOpen] = useState(false)
  const [createFromUserOpen, setCreateFromUserOpen] = useState(false)
  const [exporting, setExporting] = useState(false)

  const entryStatus = useDict(HRM_DICT.EMPLOYEE_ENTRY_STATUS)
  const canCreate = permissions.includes('hrm:employee:create')
  const canUpdate = permissions.includes('hrm:employee:update')
  const canDelete = permissions.includes('hrm:employee:delete')
  const canImport = permissions.includes('hrm:employee:import')
  const canExport = permissions.includes('hrm:employee:export')

  const loadPage = useCallback(async (page: number, category: string) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const [result, counts] = await Promise.all([
        api.hrm.employee.page({ pageNo: page, pageSize: PAGE_SIZE, statusCategory: Number(category) }),
        api.hrm.employee.statusCount({})
      ])
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
      setStatusCounts(Object.fromEntries(counts.map((item) => [item.status, item.count])))
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '员工加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, tab) }, [loadPage, pageNo, tab])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, tab) }, [loadPage, tab])

  const openDetail = async (row: HrmEmployee) => {
    setDetail(row); setDetailOpen(true)
    try {
      const result = await api.hrm.employee.get(row.id)
      setDetail(result)
    } catch (e) { message.error(e instanceof Error ? e.message : '详情加载失败') }
  }

  const openForm = (employee?: HrmEmployee) => { setFormEmployee(employee); setFormOpen(true) }
  const openPositionChange = (employee: HrmEmployee, type: number) => { setActingEmployee(employee); setChangeType(type) }
  const openQuit = (employee: HrmEmployee) => { setActingEmployee(employee); setQuitOpen(true) }

  const handleConfirmEntry = (row: HrmEmployee) => {
    message.loading('确认入职中...')
    api.hrm.employee.confirmEntry({ id: row.id })
      .then(() => { message.success('已确认入职'); reload() })
      .catch((e: Error) => message.error(e.message))
  }

  const handleRehire = (row: HrmEmployee) => {
    Modal.confirm({ title: '办理再入职', content: `确定以「${row.name}（${row.jobNumber || '-'}）」办理再入职吗？`, okText: '确认',
      onOk: async () => {
        setActing(true)
        try { await api.hrm.employee.rehire({ employeeId: row.id }); message.success('已办理再入职'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '操作失败'); throw e }
        finally { setActing(false) }
      } })
  }

  const handleCancelQuit = (row: HrmEmployee) => {
    Modal.confirm({ title: '取消离职', content: `确定终止「${row.name}」的离职流程吗？`, okText: '确认',
      onOk: async () => {
        setActing(true)
        try { await api.hrm.employee.cancelQuit({ employeeId: row.id, reason: '管理员取消' }); message.success('已取消离职'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '操作失败'); throw e }
        finally { setActing(false) }
      } })
  }

  const handleDelete = (row: HrmEmployee) => {
    Modal.confirm({ title: '删除员工', content: `确定删除「${row.name}（${row.jobNumber || '-'}）」吗？删除后不可恢复。`, okType: 'danger', okText: '删除',
      onOk: async () => {
        setActing(true)
        try { await api.hrm.employee.delete(row.id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
        finally { setActing(false) }
      } })
  }

  const handleNotify = () => {
    if (!selectedIds.length) { message.warning('请选择需要提醒的员工'); return }
    Modal.confirm({ title: '提醒填写档案', content: `向已选择的 ${selectedIds.length} 名员工发送档案填写通知？`, okText: '发送',
      onOk: async () => {
        try {
          const result = await api.hrm.employee.sendProfileFillMessage(selectedIds)
          message.success(`发送成功 ${result.successCount} 人，跳过 ${result.skippedCount} 人，失败 ${result.failureCount} 人`)
          setSelectedIds([])
        } catch (e) { message.error(e instanceof Error ? e.message : '通知发送失败'); throw e }
      } })
  }

  const handleBatchDelete = () => {
    if (!selectedIds.length) { message.warning('请选择需要删除的员工'); return }
    Modal.confirm({ title: '批量删除员工', content: `确定删除已选择的 ${selectedIds.length} 份员工档案吗？删除后不可恢复。`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try {
          await api.hrm.employee.deleteList(selectedIds)
          message.success('已批量删除'); setSelectedIds([]); reload()
        } catch (e) { message.error(e instanceof Error ? e.message : '批量删除失败'); throw e }
      } })
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      await downloadBlob('/hrm/employee/export-excel', `员工档案-${dayjs().format('YYYYMMDD')}.xlsx`, { statusCategory: Number(tab) })
    } catch (e) { message.error(e instanceof Error ? e.message : '导出失败') }
    finally { setExporting(false) }
  }

  const renderActions = (row: HrmEmployee) => {
    const actions = employeeActionsOf(row)
    const buttons: ReactNode[] = []
    for (const action of actions) {
      if (action === 'edit' && canUpdate) buttons.push(<Button key="edit" type="link" size="small" onClick={() => openForm(row)}>编辑</Button>)
      if (action === 'delete' && canDelete) buttons.push(<Button key="delete" type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>)
      if (action === 'confirmEntry' && canUpdate) buttons.push(<Button key="confirm" type="link" size="small" onClick={() => handleConfirmEntry(row)}>确认入职</Button>)
      if (action === 'rehire' && canUpdate) buttons.push(<Button key="rehire" type="link" size="small" onClick={() => handleRehire(row)}>再入职</Button>)
      if (action === 'cancelQuit' && canUpdate) buttons.push(<Button key="cancelQuit" type="link" size="small" onClick={() => handleCancelQuit(row)}>取消离职</Button>)
      if (action === 'regular' && canUpdate) buttons.push(<Button key="regular" type="link" size="small" onClick={() => openPositionChange(row, CHANGE_TYPE.REGULAR)}>转正</Button>)
      if (action === 'convertToFullTime' && canUpdate) buttons.push(<Button key="full" type="link" size="small" onClick={() => openPositionChange(row, CHANGE_TYPE.FULL_TIME)}>转全职</Button>)
      if (action === 'transfer' && canUpdate) buttons.push(<Button key="transfer" type="link" size="small" onClick={() => openPositionChange(row, CHANGE_TYPE.TRANSFER)}>调岗</Button>)
      if (action === 'promote' && canUpdate) buttons.push(<Button key="promote" type="link" size="small" onClick={() => openPositionChange(row, CHANGE_TYPE.PROMOTION)}>晋升</Button>)
      if (action === 'demote' && canUpdate) buttons.push(<Button key="demote" type="link" size="small" onClick={() => openPositionChange(row, CHANGE_TYPE.DEMOTION)}>降级</Button>)
      if (action === 'quit' && canUpdate) buttons.push(<Button key="quit" type="link" size="small" danger onClick={() => openQuit(row)}>离职</Button>)
    }
    return <Space size="small">{buttons}</Space>
  }

  const columns: ColumnsType<HrmEmployee> = [
    { title: '姓名', dataIndex: 'name', width: 100, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 140, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '职位', dataIndex: 'postName', width: 120, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '聘用形式', dataIndex: 'type', width: 100, render: (value?: number) => value != null ? EMPLOYEE_TYPE_LABELS[value] : '-' },
    { title: '状态', dataIndex: 'status', width: 90, render: (value?: number) => value != null ? EMPLOYEE_STATUS_LABELS[value] : '-' },
    { title: '手机号', dataIndex: 'mobile', width: 130, render: (value?: string) => value || '-' },
    { title: '入职时间', dataIndex: 'entryTime', width: 120, render: fmtDate },
    { title: '操作', width: 280, fixed: 'right', render: (_, row) => <>
      <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
      {renderActions(row)}
    </> }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无员工"/>
        : <>
          <HrmProTable<HrmEmployee> advanced persistenceKey="employee" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1500 }} loading={loading}
            rowSelection={{ selectedRowKeys: selectedIds, onChange: keys => setSelectedIds(keys.map(Number)) }}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 人`}/>
        </>

  return <section className="workspace-page hrm-page hrm-employee-page">
    <div className="page-heading">
      <span className="hrm-muted">共 {total} 人</span>
      <Space wrap>
        {selectedIds.length > 0 && canUpdate && <Button icon={<BellOutlined/>} onClick={handleNotify}>提醒填写（{selectedIds.length}）</Button>}
        {selectedIds.length > 0 && canDelete && <Button danger icon={<DeleteOutlined/>} onClick={handleBatchDelete}>批量删除</Button>}
        {canExport && <Button icon={<DownloadOutlined/>} loading={exporting} onClick={() => void handleExport()}>导出</Button>}
        {canImport && <Button icon={<UploadOutlined/>} onClick={() => setImportOpen(true)}>导入</Button>}
        {canCreate && <Button icon={<UserAddOutlined/>} onClick={() => setCreateFromUserOpen(true)}>从后台用户建档</Button>}
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => openForm()}>新增员工</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {entryStatus.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`员工状态字典加载失败：${entryStatus.error}`} action={<Button size="small" onClick={entryStatus.reload}>重试</Button>}/>}
    <Tabs activeKey={tab} onChange={value => { setTab(value); setPageNo(1); setSelectedIds([]) }} items={EMPLOYEE_TABS.map(item => ({
      key: item.key,
      label: <span className="hrm-tab-label">{item.label}
        {statusCounts[Number(item.key)] != null && <Badge count={statusCounts[Number(item.key)]} size="small" offset={[4, 0]}/>}
      </span>
    }))}/>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.name ? `${detail.name} · 员工档案` : '员工档案'} width="min(960px, 96vw)" open={detailOpen} onClose={() => setDetailOpen(false)} destroyOnClose>
      {detail && <>
        <Descriptions column={3} size="small" bordered items={[
          { key: 'name', label: '姓名', children: detail.name || '-' },
          { key: 'jobNumber', label: '工号', children: detail.jobNumber || '-' },
          { key: 'mobile', label: '手机号', children: detail.mobile || '-' },
          { key: 'dept', label: '部门', children: detail.deptName || '-' },
          { key: 'post', label: '职位', children: detail.postName || '-' },
          { key: 'postLevel', label: '职级', children: detail.postLevel || '-' },
          { key: 'type', label: '聘用形式', children: detail.type != null ? EMPLOYEE_TYPE_LABELS[detail.type] : '-' },
          { key: 'leader', label: '直属上级', children: detail.leaderEmployeeName || '-' },
          {
            key: 'entry', label: '入职状态', children: detail.entryStatus != null
              ? <Tag color={ENTRY_STATUS_COLORS[detail.entryStatus]}>{ENTRY_STATUS_LABELS[detail.entryStatus]}</Tag> : '-'
          },
          {
            key: 'status', label: '员工状态', children: detail.status != null ? (EMPLOYEE_STATUS_LABELS[detail.status] || detail.status) : '-'
          },
          { key: 'entryTime', label: '入职时间', children: fmtDate(detail.entryTime) },
          { key: 'regularTime', label: '转正时间', children: fmtDate(detail.regularTime) },
          { key: 'leaveTime', label: '离职时间', children: fmtDate(detail.leaveTime) },
          { key: 'probation', label: '试用期', children: detail.probation != null ? `${detail.probation} 个月` : '-' },
          { key: 'workCity', label: '工作城市', children: detail.workCity || '-' },
          { key: 'workAddress', label: '工作地点', children: detail.workAddress || '-' },
          { key: 'email', label: '邮箱', children: detail.email || '-' },
          { key: 'idNumber', label: '证件号码', children: detail.idNumber || '-' },
          { key: 'remark', label: '备注', children: detail.remark || '-' }
        ]}/>
        <EmployeeSubTabs employeeId={detail.id} permissions={permissions}/>
      </>}
    </Drawer>

    <EmployeeFormModal open={formOpen} employee={formEmployee || undefined} onClose={() => setFormOpen(false)} onSaved={reload}/>
    <PositionChangeModal open={!!changeType} employee={actingEmployee} changeType={changeType || CHANGE_TYPE.TRANSFER}
      onClose={() => setChangeType(undefined)} onSaved={reload}/>
    <EmployeeQuitModal open={quitOpen} employee={actingEmployee} onClose={() => setQuitOpen(false)} onSaved={reload}/>
    <HrmEmployeeImportModal open={importOpen} onClose={() => setImportOpen(false)} onImported={reload}/>
    <HrmEmployeeCreateFromUserModal open={createFromUserOpen} onClose={() => setCreateFromUserOpen(false)} onCreated={reload}/>
  </section>
}
