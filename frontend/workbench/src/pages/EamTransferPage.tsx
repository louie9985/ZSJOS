import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Form, Input, Modal, Radio, Select, Space, Tag, TreeSelect, message } from 'antd'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type EamTransfer, type EamTransferCreate } from '../services/api'
import { NEED_APPROVAL_TYPES, NEED_RECEIVER_TYPES, TRANSFER_STATUS, TRANSFER_TYPE, buildEamTree, toTreeSelectData } from '../services/eam'
import { useDict } from '../services/useDict'
import AssetSelect from '../components/AssetSelect'
import dayjs from 'dayjs'

const DEFAULT_PAGE_SIZE = 10

function fmtTime(value?: string | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

type FormValues = Omit<EamTransferCreate, 'expectedReturnDate' | 'actualReturnDate'> & {
  expectedReturnDate?: dayjs.Dayjs; actualReturnDate?: dayjs.Dayjs
}

export default function EamTransferPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<EamTransfer[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [query, setQuery] = useState<{ no?: string; type?: number; status?: number }>({})
  const listVersion = useRef(0)

  const [createOpen, setCreateOpen] = useState(false)
  const [createLoading, setCreateLoading] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const [transferType, setTransferType] = useState<number>(TRANSFER_TYPE.RECEIVE)

  const [depts, setDepts] = useState<Array<{ id: number; name: string; parentId: number }>>([])
  const [users, setUsers] = useState<Array<{ id: number; nickname: string }>>([])
  const [lookupError, setLookupError] = useState('')

  const typeDict = useDict('eam_transfer_type')
  const statusDict = useDict('eam_transfer_status')
  const assetStatus = useDict('eam_asset_status')

  const canCreate = permissions.includes('eam:transfer:create')
  const canUpdate = permissions.includes('eam:transfer:update')

  const deptTree = useMemo(() => toTreeSelectData(buildEamTree(depts)), [depts])
  const needReceiver = NEED_RECEIVER_TYPES.includes(transferType)
  const needApproval = NEED_APPROVAL_TYPES.includes(transferType)

  const loadLookups = useCallback(async () => {
    setLookupError('')
    const [deptResult, userResult] = await Promise.allSettled([api.eam.deptSimpleList(), api.eam.userSimpleList()])
    if (deptResult.status === 'fulfilled') setDepts(deptResult.value)
    if (userResult.status === 'fulfilled') setUsers(userResult.value)
    if ([deptResult, userResult].some(item => item.status === 'rejected')) setLookupError('部门/用户选项加载失败，接收方可能无法选择')
  }, [])
  useEffect(() => { void loadLookups() }, [loadLookups])

  const loadPage = useCallback(async (page: number, size: number, params: typeof query) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.eam.transfer.page({ pageNo: page, pageSize: size, ...params })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '流转单加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, pageSize, query) }, [loadPage, pageNo, pageSize, query])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, pageSize, query) }, [loadPage, pageSize, query])

  const handleCreate = async () => {
    const values = await form.validateFields()
    setCreateLoading(true)
    try {
      await api.eam.transfer.create({
        ...values,
        expectedReturnDate: values.expectedReturnDate?.format('YYYY-MM-DD'),
        actualReturnDate: values.actualReturnDate?.format('YYYY-MM-DD')
      })
      message.success(needApproval ? '已提交，等待审批' : '创建成功')
      setCreateOpen(false); form.resetFields(); setTransferType(TRANSFER_TYPE.RECEIVE); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '创建失败') }
    finally { setCreateLoading(false) }
  }

  const handleApprove = (id: number) => {
    Modal.confirm({
      title: '审批通过', content: '确认审批通过该流转单？通过后资产状态与归属将立即变更',
      onOk: async () => {
        try { await api.eam.transfer.approve(id); message.success('审批通过'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '审批失败'); throw e }
      }
    })
  }

  const handleReject = (id: number) => {
    let reason = ''
    Modal.confirm({
      title: '驳回流转单', okType: 'danger', okText: '驳回',
      content: <Input.TextArea rows={3} placeholder="请输入驳回原因" onChange={event => { reason = event.target.value }}/>,
      onOk: async () => {
        try { await api.eam.transfer.reject(id, reason.trim() || undefined); message.success('已驳回'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '驳回失败'); throw e }
      }
    })
  }

  const handleCancel = (id: number) => {
    Modal.confirm({
      title: '取消流转单', content: '确认取消该流转单？',
      onOk: async () => {
        try { await api.eam.transfer.cancel(id); message.success('已取消'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '取消失败'); throw e }
      }
    })
  }

  const columns: ProColumns<EamTransfer>[] = [
    { title: '单据编号', dataIndex: 'no', width: 150, fixed: 'left' },
    { title: '类型', width: 90, align: 'center', render: (_, row) => <Tag>{typeDict.labels[String(row.type)] ?? row.type}</Tag> },
    { title: '资产编号', dataIndex: 'assetCode', width: 140 },
    { title: '资产名称', dataIndex: 'assetName', width: 160, ellipsis: true },
    { title: '转出人', dataIndex: 'fromUserName', width: 100 },
    { title: '接收人', dataIndex: 'toUserName', width: 100 },
    { title: '状态', width: 90, align: 'center', render: (_, row) => row.status != null
      ? <Tag color={row.status === TRANSFER_STATUS.APPROVING ? 'warning' : row.status === TRANSFER_STATUS.APPROVED ? 'success' : 'default'}>
        {statusDict.labels[String(row.status)] ?? row.status}</Tag> : '-' },
    { title: '申请人', dataIndex: 'applyUserName', width: 100 },
    { title: '申请时间', dataIndex: 'applyTime', width: 170, render: (_, row) => fmtTime(row.applyTime) },
    { title: '操作', width: 180, align: 'center', fixed: 'right', render: (_, row) => {
      // 仅审批中的单据可审批或取消，其余状态为终态
      if (row.status !== TRANSFER_STATUS.APPROVING) return <span className="eam-muted">-</span>
      if (!canUpdate) return <span className="eam-muted">-</span>
      return <Space size="small">
        <Button type="link" size="small" onClick={() => handleApprove(row.id)}>通过</Button>
        <Button type="link" size="small" danger onClick={() => handleReject(row.id)}>驳回</Button>
        <Button type="link" size="small" onClick={() => handleCancel(row.id)}>取消</Button>
      </Space>
    }}
  ]

  const content = error
    ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
    : <ProTable<EamTransfer> rowKey="id" columns={columns} dataSource={items} loading={loading} search={false}
        columnsState={{ persistenceKey: 'eam-transfer-table-columns', persistenceType: 'localStorage' }}
        options={{ reload, density: true, setting: true, fullScreen: true }} scroll={{ x: 1400 }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showQuickJumper: true,
          showTotal: count => `共 ${count} 条`, onChange: (page, size) => { setPageNo(page); setPageSize(size) } }}/>

  return <section className="workspace-page eam-transfer-page">
    <div className="page-heading">
      <Space wrap>
        <Input.Search placeholder="单据编号" allowClear style={{ width: 170 }} onSearch={value => { setQuery(prev => ({ ...prev, no: value || undefined })); setPageNo(1) }}/>
        <Select placeholder="全部类型" style={{ width: 140 }} allowClear options={typeDict.options}
          onChange={value => { setQuery(prev => ({ ...prev, type: value })); setPageNo(1) }}/>
        <Select placeholder="全部状态" style={{ width: 140 }} allowClear options={statusDict.options}
          onChange={value => { setQuery(prev => ({ ...prev, status: value })); setPageNo(1) }}/>
      </Space>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => { form.resetFields(); setTransferType(TRANSFER_TYPE.RECEIVE); setCreateOpen(true) }}>发起流转</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {lookupError && <Alert className="eam-inline-alert" type="warning" showIcon message={lookupError} action={<Button size="small" onClick={() => void loadLookups()}>重试</Button>}/>}
    <div className="eam-table-area">{content}</div>

    <Modal title="发起资产流转" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={handleCreate} confirmLoading={createLoading} width={760} destroyOnClose>
      <Form form={form} layout="vertical" className="eam-wide-form" initialValues={{ type: TRANSFER_TYPE.RECEIVE }}>
        <Form.Item name="type" label="流转类型" rules={[{ required: true, message: '请选择流转类型' }]}
          extra={needApproval ? '该类型需要审批后才会生效' : '该类型提交后立即生效'}>
          <Radio.Group optionType="button" buttonStyle="solid" onChange={event => setTransferType(event.target.value)}
            options={typeDict.options}/>
        </Form.Item>
        <Form.Item name="assetId" label="资产" rules={[{ required: true, message: '请选择资产' }]}>
          <AssetSelect statusLabels={assetStatus.labels}/>
        </Form.Item>
        {needReceiver && <>
          <Form.Item name="toUserId" label="接收人" rules={[{ required: true, message: '请选择接收人' }]}>
            <Select allowClear showSearch optionFilterProp="label" placeholder="请选择接收人" options={users.map(user => ({ value: user.id, label: user.nickname }))}/>
          </Form.Item>
          <Form.Item name="toDeptId" label="接收部门">
            <TreeSelect treeData={deptTree} placeholder="请选择接收部门" style={{ width: '100%' }} allowClear treeDefaultExpandAll/>
          </Form.Item>
        </>}
        {transferType === TRANSFER_TYPE.BORROW && <Form.Item name="expectedReturnDate" label="预计归还" rules={[{ required: true, message: '请选择预计归还日期' }]}>
          <DatePicker style={{ width: '100%' }} placeholder="请选择预计归还日期"/>
        </Form.Item>}
        {transferType === TRANSFER_TYPE.GIVE_BACK && <Form.Item name="actualReturnDate" label="实际归还">
          <DatePicker style={{ width: '100%' }} placeholder="请选择实际归还日期"/>
        </Form.Item>}
        <Form.Item name="reason" label="事由"><Input.TextArea rows={2} placeholder="请输入事由"/></Form.Item>
      </Form>
    </Modal>
  </section>
}
