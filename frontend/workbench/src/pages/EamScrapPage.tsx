import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Form, Input, Modal, Select, Space, Tag, message } from 'antd'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type EamScrap, type EamScrapCreate } from '../services/api'
import { SCRAP_STATUS, SCRAP_STATUS_COLORS, SCRAP_STATUS_LABELS } from '../services/eam'
import { useDict } from '../services/useDict'
import AssetSelect from '../components/AssetSelect'
import dayjs from 'dayjs'

const DEFAULT_PAGE_SIZE = 10

function fmtTime(value?: string | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

type FormValues = Omit<EamScrapCreate, 'scrapDate'> & { scrapDate?: dayjs.Dayjs }

export default function EamScrapPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<EamScrap[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [query, setQuery] = useState<{ no?: string; status?: number }>({})
  const listVersion = useRef(0)

  const [createOpen, setCreateOpen] = useState(false)
  const [createLoading, setCreateLoading] = useState(false)
  const [form] = Form.useForm<FormValues>()

  const reasonDict = useDict('eam_scrap_reason')
  const assetStatus = useDict('eam_asset_status')

  const canCreate = permissions.includes('eam:scrap:create')
  const canUpdate = permissions.includes('eam:scrap:update')

  const loadPage = useCallback(async (page: number, size: number, params: typeof query) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.eam.scrap.page({ pageNo: page, pageSize: size, ...params })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '报废单加载失败')
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
      await api.eam.scrap.create({ ...values, scrapDate: values.scrapDate?.format('YYYY-MM-DD') })
      message.success('已提交报废申请')
      setCreateOpen(false); form.resetFields(); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '提交失败') }
    finally { setCreateLoading(false) }
  }

  const handleApprove = (id: number) => {
    Modal.confirm({
      title: '通过报废申请', content: '确认通过该报废申请？资产将进入已报废终态，不可恢复', okType: 'danger', okText: '确认通过',
      onOk: async () => {
        try { await api.eam.scrap.approve(id); message.success('已报废'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '审批失败'); throw e }
      }
    })
  }

  const handleReject = (id: number) => {
    let reason = ''
    Modal.confirm({
      title: '驳回报废申请', okType: 'danger', okText: '驳回',
      content: <Input.TextArea rows={3} placeholder="请输入驳回原因" onChange={event => { reason = event.target.value }}/>,
      onOk: async () => {
        try { await api.eam.scrap.reject(id, reason.trim() || undefined); message.success('已驳回，资产恢复原状态'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '驳回失败'); throw e }
      }
    })
  }

  const columns: ProColumns<EamScrap>[] = [
    { title: '单据编号', dataIndex: 'no', width: 150, fixed: 'left' },
    { title: '资产编号', dataIndex: 'assetCode', width: 140 },
    { title: '资产名称', dataIndex: 'assetName', width: 150, ellipsis: true },
    { title: '报废原因', width: 130, render: (_, row) => <Tag>{reasonDict.labels[String(row.reasonType)] ?? row.reasonType}</Tag> },
    { title: '详细说明', dataIndex: 'reason', width: 180, ellipsis: true },
    { title: '状态', width: 90, align: 'center', render: (_, row) => row.status != null
      ? <Tag color={SCRAP_STATUS_COLORS[row.status]}>{SCRAP_STATUS_LABELS[row.status] ?? row.status}</Tag> : '-' },
    { title: '申请人', dataIndex: 'applyUserName', width: 100 },
    { title: '申请时间', dataIndex: 'applyTime', width: 170, render: (_, row) => fmtTime(row.applyTime) },
    { title: '操作', width: 140, align: 'center', fixed: 'right', render: (_, row) => {
      if (row.status !== SCRAP_STATUS.APPROVING || !canUpdate) return <span className="eam-muted">-</span>
      return <Space size="small">
        <Button type="link" size="small" onClick={() => handleApprove(row.id)}>通过</Button>
        <Button type="link" size="small" danger onClick={() => handleReject(row.id)}>驳回</Button>
      </Space>
    }}
  ]

  const content = error
    ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
    : <ProTable<EamScrap> rowKey="id" columns={columns} dataSource={items} loading={loading} search={false}
        columnsState={{ persistenceKey: 'eam-scrap-table-columns', persistenceType: 'localStorage' }}
        options={{ reload, density: true, setting: true, fullScreen: true }} scroll={{ x: 1300 }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showQuickJumper: true,
          showTotal: count => `共 ${count} 条`, onChange: (page, size) => { setPageNo(page); setPageSize(size) } }}/>

  return <section className="workspace-page eam-scrap-page">
    <div className="page-heading">
      <Space wrap>
        <Input.Search placeholder="单据编号" allowClear style={{ width: 170 }} onSearch={value => { setQuery(prev => ({ ...prev, no: value || undefined })); setPageNo(1) }}/>
        <Select placeholder="全部状态" style={{ width: 140 }} allowClear
          options={[{ value: 0, label: '审批中' }, { value: 1, label: '已报废' }, { value: 2, label: '已驳回' }]}
          onChange={value => { setQuery(prev => ({ ...prev, status: value })); setPageNo(1) }}/>
      </Space>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => { form.resetFields(); setCreateOpen(true) }}>申请报废</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {reasonDict.error && <Alert className="eam-inline-alert" type="warning" showIcon message={`报废原因字典加载失败：${reasonDict.error}`} action={<Button size="small" onClick={reasonDict.reload}>重试</Button>}/>}
    <div className="eam-table-area">{content}</div>

    <Modal title="申请报废" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={handleCreate} confirmLoading={createLoading} width={760} destroyOnClose>
      <Form form={form} layout="vertical" className="eam-wide-form">
        <Form.Item name="assetId" label="资产" rules={[{ required: true, message: '请选择资产' }]}>
          <AssetSelect placeholder="输入资产名称搜索" statusLabels={assetStatus.labels}/>
        </Form.Item>
        <Form.Item name="reasonType" label="报废原因" rules={[{ required: true, message: '请选择报废原因' }]}>
          <Select placeholder="请选择报废原因" options={reasonDict.options}/>
        </Form.Item>
        <Form.Item name="reason" label="详细说明">
          <Input.TextArea rows={3} placeholder="如 主板损坏，维修成本超过残值"/>
        </Form.Item>
        <Form.Item name="scrapDate" label="报废日期">
          <DatePicker style={{ width: '100%' }} placeholder="留空则取当前日期"/>
        </Form.Item>
        <Alert message="提交后资产将进入「待报废」状态并冻结流转，审批通过后变为已报废终态" type="warning" showIcon/>
      </Form>
    </Modal>
  </section>
}
