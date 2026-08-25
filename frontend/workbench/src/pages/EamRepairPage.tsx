import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Form, Input, InputNumber, Modal, Space, Tag, message } from 'antd'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type EamRepairCreate, type EamRepairFinish, type EamRepairItem } from '../services/api'
import { useDict } from '../services/useDict'
import AssetSelect from '../components/AssetSelect'
import dayjs from 'dayjs'

const DEFAULT_PAGE_SIZE = 10

function fmtTime(value?: string | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

type CreateFormValues = Omit<EamRepairCreate, 'startTime'> & { startTime?: dayjs.Dayjs }
type FinishFormValues = Omit<EamRepairFinish, 'id' | 'endTime'> & { endTime?: dayjs.Dayjs }

export default function EamRepairPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<EamRepairItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [createOpen, setCreateOpen] = useState(false)
  const [createLoading, setCreateLoading] = useState(false)
  const [createForm] = Form.useForm<CreateFormValues>()

  const [finishOpen, setFinishOpen] = useState(false)
  const [finishLoading, setFinishLoading] = useState(false)
  const [finishForm] = Form.useForm<FinishFormValues>()
  const [currentRepair, setCurrentRepair] = useState<EamRepairItem>()

  const assetStatus = useDict('eam_asset_status')

  const canCreate = permissions.includes('eam:repair:create')
  const canUpdate = permissions.includes('eam:repair:update')
  const canDelete = permissions.includes('eam:repair:delete')

  const loadPage = useCallback(async (page: number, size: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.eam.repair.page({ pageNo: page, pageSize: size })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '维修记录加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, pageSize) }, [loadPage, pageNo, pageSize])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, pageSize) }, [loadPage, pageSize])

  const handleCreate = async () => {
    const values = await createForm.validateFields()
    setCreateLoading(true)
    try {
      await api.eam.repair.create({
        ...values,
        startTime: values.startTime ? values.startTime.format('YYYY-MM-DD HH:mm:ss') : undefined
      })
      message.success('送修登记成功')
      setCreateOpen(false); createForm.resetFields(); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '送修失败') }
    finally { setCreateLoading(false) }
  }

  const handleFinish = async () => {
    if (!currentRepair) return
    const values = await finishForm.validateFields()
    setFinishLoading(true)
    try {
      await api.eam.repair.finish({
        ...values,
        id: currentRepair.id,
        endTime: values.endTime ? values.endTime.format('YYYY-MM-DD HH:mm:ss') : undefined
      })
      message.success('维修已完成')
      setFinishOpen(false); finishForm.resetFields(); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '操作失败') }
    finally { setFinishLoading(false) }
  }

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除', content: '确定要删除该维修记录吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.eam.repair.delete(id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ProColumns<EamRepairItem>[] = [
    { title: '资产编号', dataIndex: 'assetCode', width: 140, fixed: 'left' },
    { title: '资产名称', dataIndex: 'assetName', width: 150, ellipsis: true },
    { title: '故障描述', dataIndex: 'faultDesc', width: 200, ellipsis: true },
    { title: '维修方', dataIndex: 'repairVendor', width: 140, ellipsis: true },
    { title: '费用', dataIndex: 'cost', width: 100, align: 'right', render: (_, row) => row.cost != null ? `¥${row.cost}` : '-' },
    { title: '状态', width: 90, align: 'center', render: (_, row) => <Tag color={row.endTime ? 'success' : 'warning'}>{row.endTime ? '已完成' : '维修中'}</Tag> },
    { title: '送修时间', dataIndex: 'startTime', width: 170, render: (_, row) => fmtTime(row.startTime) },
    { title: '完成时间', dataIndex: 'endTime', width: 170, render: (_, row) => fmtTime(row.endTime) },
    { title: '操作', width: 150, align: 'center', fixed: 'right', render: (_, row) => <Space size="small">
      {!row.endTime && canUpdate && <Button type="link" size="small" onClick={() => {
        setCurrentRepair(row); finishForm.setFieldsValue({ cost: row.cost, result: undefined, endTime: undefined }); setFinishOpen(true)
      }}>维修完成</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row.id)}>删除</Button>}
    </Space> }
  ]

  const content = error
    ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
    : <ProTable<EamRepairItem> rowKey="id" columns={columns} dataSource={items} loading={loading} search={false}
        columnsState={{ persistenceKey: 'eam-repair-table-columns', persistenceType: 'localStorage' }}
        options={{ reload, density: true, setting: true, fullScreen: true }} scroll={{ x: 1200 }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showQuickJumper: true,
          showTotal: count => `共 ${count} 条`, onChange: (page, size) => { setPageNo(page); setPageSize(size) } }}/>

  return <section className="workspace-page eam-repair-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => { createForm.resetFields(); setCreateOpen(true) }}>送修登记</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {assetStatus.error && <Alert className="eam-inline-alert" type="warning" showIcon message={`资产状态字典加载失败：${assetStatus.error}`} action={<Button size="small" onClick={assetStatus.reload}>重试</Button>}/>}
    <div className="eam-table-area">{content}</div>

    <Modal title="送修登记" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={handleCreate} confirmLoading={createLoading} width={760} destroyOnClose>
      <Form form={createForm} layout="vertical" className="eam-wide-form">
        <Form.Item name="assetId" label="资产" rules={[{ required: true, message: '请选择资产' }]}>
          <AssetSelect placeholder="输入资产名称搜索" statusLabels={assetStatus.labels}/>
        </Form.Item>
        <Form.Item name="faultDesc" label="故障描述" rules={[{ required: true, message: '请输入故障描述' }]}>
          <Input.TextArea rows={3} placeholder="请描述故障现象"/>
        </Form.Item>
        <Form.Item name="repairVendor" label="维修方">
          <Input placeholder="如 Apple 授权服务中心"/>
        </Form.Item>
        <Form.Item name="cost" label="预估费用">
          <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="可留空，完成时再填"/>
        </Form.Item>
        <Form.Item name="startTime" label="送修时间">
          <DatePicker showTime style={{ width: '100%' }} placeholder="留空则取当前时间"/>
        </Form.Item>
        <Alert message="登记后资产状态将变更为「维修中」，维修完成时自动恢复原状态" type="info" showIcon/>
      </Form>
    </Modal>

    <Modal title="维修完成" open={finishOpen} onCancel={() => setFinishOpen(false)} onOk={handleFinish} confirmLoading={finishLoading} width={760} destroyOnClose>
      <Form form={finishForm} layout="vertical" className="eam-wide-form">
        {currentRepair && <Form.Item label="资产"><span>{currentRepair.assetCode} {currentRepair.assetName}</span></Form.Item>}
        <Form.Item name="endTime" label="完成时间">
          <DatePicker showTime style={{ width: '100%' }} placeholder="留空则取当前时间"/>
        </Form.Item>
        <Form.Item name="cost" label="维修费用">
          <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="请输入维修费用"/>
        </Form.Item>
        <Form.Item name="result" label="维修结果">
          <Input.TextArea rows={2} placeholder="如 已更换屏幕"/>
        </Form.Item>
        <Alert message="确认后资产将恢复到送修前的状态" type="info" showIcon/>
      </Form>
    </Modal>
  </section>
}
