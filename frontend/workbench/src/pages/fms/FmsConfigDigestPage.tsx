import { useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Skeleton, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { fmsConfig } from '../../services/fms'
import type { FmsDigestVO } from '../../services/fms/types'
import { useFmsAccountSet, useFmsResource } from '../../services/useFmsAccountSet'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

function fmtTime(value?: string | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

export default function FmsConfigDigestPage({ permissions }: { permissions: string[] }) {
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const { data: items, loading, error, reload } = useFmsResource<FmsDigestVO[]>((id) => fmsConfig.digest.list(id))

  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{ content: string }>()
  const editingId = useRef<number | undefined>(undefined)

  const canCreate = writable && permissions.includes('fms:config:digest:create')
  const canUpdate = writable && permissions.includes('fms:config:digest:update')
  const canDelete = writable && permissions.includes('fms:config:digest:delete')

  const openCreate = () => {
    editingId.current = undefined
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: FmsDigestVO) => {
    editingId.current = row.id
    form.setFieldsValue({ content: row.content })
    setModalOpen(true)
  }

  const handleSave = async () => {
    if (!accountSetId) return
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editingId.current != null) {
        await fmsConfig.digest.update({ ...values, id: editingId.current, accountSetId })
        message.success('摘要已更新')
      } else {
        await fmsConfig.digest.create({ content: values.content, accountSetId })
        message.success('摘要已添加')
      }
      setModalOpen(false)
      form.resetFields()
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = (id: number) => {
    if (!accountSetId) return
    Modal.confirm({
      title: '确认删除', content: '确定要删除该摘要吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await fmsConfig.digest.delete(accountSetId, id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ColumnsType<FmsDigestVO> = [
    { title: '摘要内容', dataIndex: 'content', ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', width: 170, render: fmtTime },
    { title: '操作', width: 150, align: 'center', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => openEdit(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row.id!)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !items?.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items?.length ? <Empty description="暂无摘要"/>
        : <FmsProTable<FmsDigestVO> rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading}/>

  return <section className="workspace-page fms-page">
    <div className="page-heading">
      <h4>常用摘要</h4>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>新增摘要</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="fms-table-area">{content}</div>

    <Modal title={editingId.current != null ? '编辑摘要' : '新增摘要'} open={modalOpen} onCancel={() => setModalOpen(false)} onOk={handleSave} confirmLoading={saving} width={720} destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item name="content" label="摘要内容" rules={[{ required: true, message: '请输入摘要内容' }]}>
          <Input.TextArea rows={3} maxLength={500} showCount placeholder="请输入摘要内容"/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
