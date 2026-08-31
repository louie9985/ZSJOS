import {
  ApartmentOutlined,
  CloseOutlined,
  FileTextOutlined,
  SearchOutlined,
  TeamOutlined
} from '@ant-design/icons'
import {
  App,
  Button,
  Checkbox,
  Empty,
  Input,
  Modal,
  Pagination,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { api, type AssignmentLog, type AssignmentRelation, type AssignmentUser } from '../services/api'
import { useBusinessOverlay } from '../components/OverlayCoordinator'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'
import { assignmentConfirmAction } from '../services/irreversibleConfirm'
import EmployeeAvatar from '../components/EmployeeAvatar'
import ResizableDrawer from '../components/ResizableDrawer'
import { ASSIGNMENT_DRAWER_WIDTH_STORAGE_KEY } from '../constants'

const { Text } = Typography
type SaveMode = 'append' | 'replace' | 'remove'

const userLabel = (user: AssignmentUser) =>
  `${user.nickname}${user.maskedMobile ? `（${user.maskedMobile}）` : ''}${user.deptName ? ` · ${user.deptName}` : ''}`

export default function LeadAssignmentPage() {
  const { message } = App.useApp()
  const [loading, setLoading] = useState(true)
  const [rows, setRows] = useState<AssignmentRelation[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [configured, setConfigured] = useState<boolean>()
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [activeRow, setActiveRow] = useState<AssignmentRelation>()
  const [batchMode, setBatchMode] = useState(false)
  const [saveMode, setSaveMode] = useState<SaveMode>('replace')
  const [eligibleSales, setEligibleSales] = useState<AssignmentUser[]>([])
  const [selectedSalesIds, setSelectedSalesIds] = useState<number[]>([])
  const [salesKeyword, setSalesKeyword] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const [logOpen, setLogOpen] = useState(false)
  const [logLoading, setLogLoading] = useState(false)
  const [logs, setLogs] = useState<AssignmentLog[]>([])
  const [logTotal, setLogTotal] = useState(0)
  const [logPage, setLogPage] = useState(1)
  useBusinessOverlay(drawerOpen)
  useBusinessOverlay(logOpen)

  const loadList = async (targetPage = pageNo, targetSize = pageSize) => {
    setLoading(true)
    try {
      const result = await api.assignmentRelationPage({
        pageNo: targetPage,
        pageSize: targetSize,
        keyword: keyword || undefined,
        configured
      })
      setRows(result.list)
      setTotal(result.total)
    } catch {
      message.error('派单关系加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void loadList() }, [pageNo, pageSize, configured])

  const ensureSales = async () => {
    if (eligibleSales.length > 0) return eligibleSales
    const users = await api.eligibleSalesUsers()
    setEligibleSales(users)
    return users
  }

  const openSingle = async (row: AssignmentRelation) => {
    await ensureSales()
    setBatchMode(false)
    setActiveRow(row)
    setSaveMode('replace')
    setSelectedSalesIds(row.salesUsers.map(user => user.id))
    setSalesKeyword('')
    setDrawerOpen(true)
  }

  const openBatch = async () => {
    await ensureSales()
    setBatchMode(true)
    setActiveRow(undefined)
    setSaveMode('append')
    setSelectedSalesIds([])
    setSalesKeyword('')
    setDrawerOpen(true)
  }

  const submit = async () => {
    setConfirmOpen(false)
    if (selectedSalesIds.length === 0 && saveMode !== 'replace') {
      message.warning('请至少选择一名销售')
      return
    }
    setSaving(true)
    try {
      await api.saveAssignmentRelations({
        sourceUserIds: batchMode ? selectedRowKeys.map(Number) : [activeRow!.id],
        targetUserIds: selectedSalesIds,
        mode: saveMode
      })
      message.success('派单关系已更新')
      setDrawerOpen(false)
      setSelectedRowKeys([])
      await loadList()
    } catch {
      message.error('派单关系保存失败')
    } finally {
      setSaving(false)
    }
  }

  const prepareSubmit = () => {
    if (selectedSalesIds.length === 0 && saveMode !== 'replace') {
      message.warning('请至少选择一名销售')
      return
    }
    setConfirmOpen(true)
  }

  const loadLogs = async (targetPage = logPage) => {
    setLogLoading(true)
    try {
      const result = await api.assignmentLogPage({ pageNo: targetPage, pageSize: 10 })
      setLogs(result.list)
      setLogTotal(result.total)
    } catch {
      message.error('变更记录加载失败')
    } finally {
      setLogLoading(false)
    }
  }

  const filteredSales = useMemo(() => {
    const normalized = salesKeyword.trim().toLowerCase()
    if (!normalized) return eligibleSales
    return eligibleSales.filter(user => userLabel(user).toLowerCase().includes(normalized))
  }, [eligibleSales, salesKeyword])

  const selectedSales = useMemo(
    () => eligibleSales.filter(user => selectedSalesIds.includes(user.id)),
    [eligibleSales, selectedSalesIds]
  )

  const columns: TableColumnsType<AssignmentRelation> = [
    {
      title: '派单员工',
      key: 'user',
      width: 220,
      render: (_, row) => <div className="assignment-person"><EmployeeAvatar avatar={row.avatar} name={row.nickname}/><div><Text strong>{row.nickname}</Text><Text type="secondary">{row.maskedMobile || '未填写手机号'}</Text></div></div>
    },
    { title: '所属部门', dataIndex: 'deptName', width: 160, render: value => value || '-' },
    {
      title: '已绑定销售',
      key: 'sales',
      render: (_, row) => row.salesUsers.length > 0
        ? <Space size={[4, 4]} wrap>{row.salesUsers.slice(0, 3).map(user => <Tag key={user.id}><span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><EmployeeAvatar avatar={user.avatar} name={user.nickname} size={20}/>{user.nickname}</span></Tag>)}{row.salesUsers.length > 3 && <Tag>+{row.salesUsers.length - 3}</Tag>}</Space>
        : <Text type="secondary">尚未配置</Text>
    },
    {
      title: '有效 / 异常', key: 'counts', width: 110, align: 'center',
      render: (_, row) => <><Text type="success" strong>{row.validSalesCount}</Text><Text> / </Text><Text type={row.invalidSalesCount > 0 ? 'danger' : 'secondary'} strong>{row.invalidSalesCount}</Text></>
    },
    { title: '状态', dataIndex: 'status', width: 90, render: value => <Tag color={value === 0 ? 'success' : 'default'}>{value === 0 ? '正常' : '停用'}</Tag> },
    { title: '操作', key: 'action', width: 80, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void openSingle(row)}>配置</Button> }
  ]

  const actionLabels: Record<AssignmentLog['actionType'], string> = { append: '追加绑定', replace: '替换绑定', remove: '解除绑定' }

  return <section className="workspace-page assignment-page">
    <div className="assignment-toolbar">
      <Input value={keyword} onChange={event => setKeyword(event.target.value)} onPressEnter={() => { setPageNo(1); void loadList(1) }} allowClear prefix={<SearchOutlined/>} placeholder="搜索姓名或手机号" className="assignment-search"/>
      <Select value={configured} onChange={value => { setConfigured(value); setPageNo(1) }} allowClear placeholder="全部配置状态" className="assignment-filter" options={[{ label: '已配置', value: true }, { label: '未配置', value: false }]}/>
      <Button onClick={() => { setPageNo(1); void loadList(1) }}>查询</Button>
      <span className="assignment-toolbar-spacer"/>
      <Space><Button icon={<FileTextOutlined/>} onClick={() => { setLogOpen(true); setLogPage(1); void loadLogs(1) }}>变更记录</Button><Button type="primary" icon={<TeamOutlined/>} disabled={selectedRowKeys.length === 0} onClick={() => void openBatch()}>批量配置</Button></Space>
    </div>
    <Table<AssignmentRelation> rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} scroll={{ x: 980 }} rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}/>
    <div className="assignment-pagination"><Pagination current={pageNo} pageSize={pageSize} total={total} showSizeChanger onChange={(page, size) => { setPageNo(page); setPageSize(size) }}/></div>

    <ResizableDrawer title={batchMode ? '批量配置派单关系' : '配置可派销售'} width="min(760px, 100vw)" defaultSize={760} minSize={640} storageKey={ASSIGNMENT_DRAWER_WIDTH_STORAGE_KEY} open={drawerOpen} onClose={() => setDrawerOpen(false)} extra={<IrreversiblePopconfirm action={assignmentConfirmAction(saveMode, batchMode ? { batchCount: selectedRowKeys.length } : { name: activeRow?.nickname || '当前员工' })} danger={saveMode === 'remove'} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}><Button type="primary" danger={saveMode === 'remove'} loading={saving} onClick={prepareSubmit}>保存配置</Button></IrreversiblePopconfirm>}>
      <div className="assignment-subject"><ApartmentOutlined/><Text type="secondary">配置对象</Text><Text strong>{batchMode ? `已选择 ${selectedRowKeys.length} 名员工` : activeRow ? userLabel(activeRow) : ''}</Text></div>
      {batchMode && <Segmented<SaveMode> block value={saveMode} onChange={setSaveMode} options={[{ label: '追加绑定', value: 'append' }, { label: '替换原绑定', value: 'replace' }, { label: '解除指定绑定', value: 'remove' }]}/>} 
      <div className="assignment-picker">
        <div className="assignment-candidates"><div className="assignment-pane-title"><Text strong>可选销售</Text><Text type="secondary">{filteredSales.length} 人</Text></div><Input value={salesKeyword} onChange={event => setSalesKeyword(event.target.value)} allowClear prefix={<SearchOutlined/>} placeholder="搜索姓名、手机号或部门"/>
          <Checkbox.Group value={selectedSalesIds} onChange={values => setSelectedSalesIds(values.map(Number))} className="assignment-check-list">
            {filteredSales.map(user => <Checkbox key={user.id} value={user.id} className="assignment-check-row"><span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><EmployeeAvatar avatar={user.avatar} name={user.nickname} size={28}/><span><Text strong>{user.nickname}</Text><Text type="secondary">{user.maskedMobile || '未填写手机号'} · {user.deptName || '未分配部门'}</Text></span></span></Checkbox>)}
          </Checkbox.Group>
          {filteredSales.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的销售账号"/>}
        </div>
        <div className="assignment-selected"><div className="assignment-pane-title"><Text strong>已选择</Text><Tag color="blue">{selectedSales.length} 人</Tag></div>{selectedSales.map(user => <div key={user.id} className="assignment-selected-row"><div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><EmployeeAvatar avatar={user.avatar} name={user.nickname} size={28}/><span><Text strong>{user.nickname}</Text><Text type="secondary">{user.deptName || '未分配部门'}</Text></span></div><Button type="text" danger icon={<CloseOutlined/>} onClick={() => setSelectedSalesIds(ids => ids.filter(id => id !== user.id))}/></div>)}{selectedSales.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂未选择销售"/>}</div>
      </div>
    </ResizableDrawer>

    <Modal title="派单关系变更记录" width={900} open={logOpen} footer={null} onCancel={() => setLogOpen(false)}>
      <Table<AssignmentLog> rowKey="id" loading={logLoading} dataSource={logs} pagination={false} columns={[{ title: '操作时间', dataIndex: 'createTime', width: 180, render: value => value?.replace('T', ' ') }, { title: '操作人', dataIndex: 'operatorName', width: 110 }, { title: '操作', dataIndex: 'actionType', width: 100, render: value => actionLabels[value as AssignmentLog['actionType']] }, { title: '派单员工', dataIndex: 'sourceUsers', ellipsis: true }, { title: '销售人员', dataIndex: 'targetUsers', ellipsis: true }]}/>
      <div className="assignment-pagination"><Pagination current={logPage} pageSize={10} total={logTotal} onChange={page => { setLogPage(page); void loadLogs(page) }}/></div>
    </Modal>
  </section>
}
