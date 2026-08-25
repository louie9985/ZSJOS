import { useCallback, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Skeleton, Space, Tag, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { fmsConfig } from '../../services/fms'
import type { FmsVoucherWordVO } from '../../services/fms/types'
import { useFmsAccountSet, useFmsResource } from '../../services/useFmsAccountSet'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

export default function FmsConfigVoucherWordPage({ permissions }: { permissions: string[] }) {
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const { data: items, loading, error, reload } = useFmsResource<FmsVoucherWordVO[]>(
    (id) => fmsConfig.voucherWord.list(id)
  )

  const canCreate = writable && permissions.includes('fms:config:voucher-word:create')
  const canUpdate = writable && permissions.includes('fms:config:voucher-word:update')
  const canDelete = writable && permissions.includes('fms:config:voucher-word:delete')

  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const editingId = useRef<number | undefined>(undefined)

  const openCreate = useCallback(() => {
    editingId.current = undefined
    form.resetFields()
    setModalOpen(true)
  }, [form])

  const openEdit = useCallback((row: FmsVoucherWordVO) => {
    editingId.current = row.id
    form.setFieldsValue({ name: row.name, printTitle: row.printTitle, sort: row.sort })
    setModalOpen(true)
  }, [form])

  const handleSave = async () => {
    if (!accountSetId) return
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editingId.current != null) {
        await fmsConfig.voucherWord.update({ ...values, id: editingId.current, accountSetId })
        message.success('凭证字已更新')
      } else {
        await fmsConfig.voucherWord.create({ ...values, accountSetId })
        message.success('凭证字已添加')
      }
      setModalOpen(false)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = (row: FmsVoucherWordVO) => {
    if (!accountSetId || row.id == null) return
    if (row.defaultStatus) {
      message.error('默认凭证字不能删除')
      return
    }
    const id = row.id
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除凭证字「${row.name}」吗？`,
      okType: 'danger',
      okText: '删除',
      onOk: async () => {
        try {
          await fmsConfig.voucherWord.delete(accountSetId, id)
          message.success('已删除')
          reload()
        } catch (e) {
          message.error(e instanceof Error ? e.message : '删除失败')
          throw e
        }
      }
    })
  }

  const columns: ColumnsType<FmsVoucherWordVO> = [
    { title: '凭证字', dataIndex: 'name', width: 120 },
    { title: '打印标题', dataIndex: 'printTitle', width: 160 },
    {
      title: '是否默认', dataIndex: 'defaultStatus', width: 100, align: 'center',
      render: (val?: boolean) => val ? <Tag color="blue">默认</Tag> : <Tag>否</Tag>
    },
    { title: '排序', dataIndex: 'sort', width: 80, align: 'center' },
    {
      title: '创建时间', dataIndex: 'createTime', width: 170,
      render: (val?: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-'
    },
    {
      title: '操作', width: 140, align: 'center',
      render: (_, row) => (
        <Space size="small">
          {canUpdate && <Button type="link" size="small" onClick={() => openEdit(row)}>编辑</Button>}
          {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
        </Space>
      )
    }
  ]

  const content = loading && !items
    ? <Skeleton active paragraph={{ rows: 6 }} />
    : error
      ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>} />
      : !items?.length
        ? <Empty description="暂无凭证字" />
        : <FmsProTable<FmsVoucherWordVO> rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} />

  return (
    <section className="workspace-page fms-page">
      <div className="page-heading">
        <h4>凭证字</h4>
        <Space>
          {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增</Button>}
          <Button icon={<ReloadOutlined />} onClick={reload}>刷新</Button>
        </Space>
      </div>
      <div className="fms-table-area">{content}</div>

      <Modal
        title={editingId.current != null ? '编辑凭证字' : '新增凭证字'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        destroyOnClose
        width={720}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="凭证字" rules={[{ required: true, message: '请输入凭证字' }]}>
            <Input placeholder="如 记、收、付、转" />
          </Form.Item>
          <Form.Item name="printTitle" label="打印标题">
            <Input placeholder="如 记账凭证" />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="数值越小越靠前" />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  )
}
