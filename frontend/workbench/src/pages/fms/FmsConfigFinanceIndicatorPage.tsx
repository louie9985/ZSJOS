import { useCallback, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Radio, Skeleton, Space, Tag, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { DICT_TYPE } from '../../constants'
import { fmsConfig } from '../../services/fms'
import { FMS_FINANCE_INDICATOR_TYPE } from '../../services/fms/constants'
import type { FmsFinanceIndicatorVO } from '../../services/fms/types'
import { useDict } from '../../services/useDict'
import { useFmsAccountSet, useFmsResource } from '../../services/useFmsAccountSet'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

export default function FmsConfigFinanceIndicatorPage({ permissions }: { permissions: string[] }) {
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const indicatorTypes = useDict(DICT_TYPE.FMS_FINANCE_INDICATOR_TYPE)
  const commonStatuses = useDict(DICT_TYPE.COMMON_STATUS)
  const { data, loading, error, reload } = useFmsResource<FmsFinanceIndicatorVO[]>(
    (id) => fmsConfig.financeIndicator.list(id)
  )

  const canCreate = writable && permissions.includes('fms:config:finance-indicator:create')
  const canUpdate = writable && permissions.includes('fms:config:finance-indicator:update')
  const canDelete = writable && permissions.includes('fms:config:finance-indicator:delete')

  const [modalOpen, setModalOpen] = useState(false)
  const [modalLoading, setModalLoading] = useState(false)
  const [form] = Form.useForm()
  const editingId = useRef<number | undefined>(undefined)

  const openCreate = () => {
    editingId.current = undefined
    form.resetFields()
    form.setFieldsValue({
      type: FMS_FINANCE_INDICATOR_TYPE.INCOME_STATEMENT,
      formula: 'L1',
      sort: 10,
      status: 0
    })
    setModalOpen(true)
  }

  const openEdit = async (row: FmsFinanceIndicatorVO) => {
    if (!accountSetId || row.id == null) return
    editingId.current = row.id
    form.resetFields()
    setModalOpen(true)
    setModalLoading(true)
    try {
      form.setFieldsValue(await fmsConfig.financeIndicator.get(accountSetId, row.id))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '指标详情加载失败')
      setModalOpen(false)
    } finally {
      setModalLoading(false)
    }
  }

  const handleSave = useCallback(async () => {
    if (!accountSetId) return
    const values = await form.validateFields()
    setModalLoading(true)
    try {
      if (editingId.current != null) {
        await fmsConfig.financeIndicator.update({ ...values, id: editingId.current, accountSetId })
        message.success('指标已更新')
      } else {
        await fmsConfig.financeIndicator.create({ ...values, accountSetId })
        message.success('指标已添加')
      }
      setModalOpen(false)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    } finally {
      setModalLoading(false)
    }
  }, [accountSetId, form, reload])

  const handleDelete = (id: number) => {
    if (!accountSetId) return
    Modal.confirm({
      title: '确认删除', content: '确定要删除该财务指标吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await fmsConfig.financeIndicator.delete(accountSetId, id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ColumnsType<FmsFinanceIndicatorVO> = [
    { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 120 },
    { title: '取数报表', dataIndex: 'type', width: 120, render: value => indicatorTypes.labels[String(value)] || value },
    { title: '公式', dataIndex: 'formula', width: 200, ellipsis: true },
    { title: '排序', dataIndex: 'sort', width: 80, align: 'center' },
    { title: '状态', dataIndex: 'status', width: 90, align: 'center', render: (value: number) => <Tag color={value === 0 ? 'success' : 'default'}>{commonStatuses.labels[String(value)] || value}</Tag> },
    { title: '创建时间', dataIndex: 'createTime', width: 170, render: (v?: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-' },
    { title: '操作', width: 140, align: 'center', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => void openEdit(row)}>编辑</Button>}
      {canDelete && row.id != null && <Button type="link" size="small" danger onClick={() => handleDelete(row.id!)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !data
    ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error
      ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !data?.length
        ? <Empty description="暂无财务指标"/>
        : <FmsProTable<FmsFinanceIndicatorVO> rowKey="id" columns={columns} dataSource={data} pagination={false} loading={loading}/>

  return <section className="workspace-page fms-page">
    <div className="page-heading">
      <h4>财务指标</h4>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>新增指标</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    {(indicatorTypes.error || commonStatuses.error) && <Alert type="error" showIcon message="财务指标字典加载失败" description={indicatorTypes.error || commonStatuses.error} action={<Button size="small" onClick={() => { void indicatorTypes.reload(); void commonStatuses.reload() }}>重试</Button>} style={{ marginBottom: 12 }}/>}
    <div className="fms-table-area">{content}</div>

    <Modal
      title={editingId.current != null ? '编辑指标' : '新增指标'}
      open={modalOpen}
      onCancel={() => setModalOpen(false)}
      onOk={handleSave}
      confirmLoading={modalLoading}
      width={800}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入指标名称' }]}>
          <Input placeholder="请输入指标名称"/>
        </Form.Item>
        <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入指标编码' }]}>
          <Input placeholder="请输入编码" disabled={editingId.current != null}/>
        </Form.Item>
        <Form.Item name="type" label="取数报表" rules={[{ required: true, message: '请选择取数报表' }]}>
          <Radio.Group options={indicatorTypes.options}/>
        </Form.Item>
        <Form.Item name="formula" label="公式" rules={[{ required: true, message: '请输入指标公式' }]}>
          <Input.TextArea rows={4} maxLength={2000} showCount placeholder="例如：L1+L2-L3，或科目公式 JSON"/>
        </Form.Item>
        <Form.Item name="sort" label="排序">
          <InputNumber min={0} style={{ width: '100%' }} placeholder="排序号"/>
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Radio.Group options={commonStatuses.options}/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
