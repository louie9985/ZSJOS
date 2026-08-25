import { useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Skeleton, Space, Tag, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { fmsConfig } from '../../services/fms'
import type { FmsCurrencyVO } from '../../services/fms/types'
import { useFmsAccountSet, useFmsResource } from '../../services/useFmsAccountSet'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

export default function FmsConfigCurrencyPage({ permissions }: { permissions: string[] }) {
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const { data, loading, error, reload } = useFmsResource<FmsCurrencyVO[]>((id) => fmsConfig.currency.list(id))

  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const editingId = useRef<number | undefined>(undefined)

  const canCreate = writable && permissions.includes('fms:config:currency:create')
  const canUpdate = writable && permissions.includes('fms:config:currency:update')
  const canDelete = writable && permissions.includes('fms:config:currency:delete')

  const openCreate = () => {
    editingId.current = undefined
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: FmsCurrencyVO) => {
    editingId.current = row.id
    form.setFieldsValue({ code: row.code, name: row.name, exchangeRate: row.exchangeRate })
    setModalOpen(true)
  }

  const handleSave = async () => {
    if (!accountSetId) return
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editingId.current != null) {
        await fmsConfig.currency.update({ ...values, id: editingId.current, accountSetId })
        message.success('币别已更新')
      } else {
        await fmsConfig.currency.create({ ...values, accountSetId })
        message.success('币别已添加')
      }
      setModalOpen(false)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = (id: number) => {
    if (!accountSetId) return
    Modal.confirm({
      title: '确认删除', content: '确定要删除该币别吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await fmsConfig.currency.delete(accountSetId, id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ColumnsType<FmsCurrencyVO> = [
    { title: '币别编码', dataIndex: 'code', width: 120 },
    { title: '币别名称', dataIndex: 'name', width: 150 },
    { title: '汇率', dataIndex: 'exchangeRate', width: 140, align: 'right' },
    { title: '本位币', dataIndex: 'standard', width: 90, align: 'center', render: (val: boolean) => val ? <Tag color="blue">是</Tag> : <Tag>否</Tag> },
    { title: '创建时间', dataIndex: 'createTime', width: 170, render: (val?: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-' },
    { title: '操作', width: 140, align: 'center', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => openEdit(row)}>编辑</Button>}
      {canDelete && !row.standard && <Button type="link" size="small" danger onClick={() => handleDelete(row.id!)}>删除</Button>}
    </Space> }
  ]

  const items = data ?? []
  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 6 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无币别"/>
        : <FmsProTable<FmsCurrencyVO> rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading}/>

  return <section className="workspace-page fms-page">
    <div className="page-heading">
      <h4>币别管理</h4>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>新增币别</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="fms-table-area">{content}</div>

    <Modal title={editingId.current != null ? '编辑币别' : '新增币别'} open={modalOpen} onCancel={() => setModalOpen(false)} onOk={handleSave} confirmLoading={saving} destroyOnClose width={720}>
      <Form form={form} layout="vertical">
        <Form.Item name="code" label="币别编码" rules={[{ required: true, message: '请输入币别编码' }]}>
          <Input placeholder="如 USD" maxLength={10}/>
        </Form.Item>
        <Form.Item name="name" label="币别名称" rules={[{ required: true, message: '请输入币别名称' }]}>
          <Input placeholder="如 美元" maxLength={50}/>
        </Form.Item>
        <Form.Item name="exchangeRate" label="汇率" rules={[{ required: true, message: '请输入汇率' }]}>
          <InputNumber min={0} precision={6} style={{ width: '100%' }} placeholder="如 1.000000"/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
