import HrmProTable from './HrmProTable'
import { useCallback, useEffect, useState } from 'react'
import { Button, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Switch, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

/** 通用档案子表：列表 + 新增/编辑 + 删除。字段由配置驱动，日期用时间戳(dayjs 转换)。 */

type FieldType = 'text' | 'textarea' | 'number' | 'date' | 'switch' | 'select'

interface FieldConfig {
  name: string
  label: string
  type?: FieldType
  required?: boolean
  options?: Array<{ value: number | string; label: string }>
  span?: number
}

interface SubTableProps<T> {
  title: string
  items: T[]
  fields: FieldConfig[]
  columns: ColumnsType<T>
  loading?: boolean
  canCreate?: boolean
  canUpdate?: boolean
  canDelete?: boolean
  onReload: () => void
  onCreate: (values: Record<string, unknown>) => Promise<void>
  onUpdate: (values: Record<string, unknown>) => Promise<void>
  onDelete: (id: number) => Promise<void>
  dateFields?: string[] // 需在 dayjs↔ts 间转换的日期字段
}

export default function EmployeeSubTable<T extends { id?: number }>({
  title, items, fields, columns, loading, canCreate, canUpdate, canDelete,
  onReload, onCreate, onUpdate, onDelete, dateFields = []
}: SubTableProps<T>) {
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<T | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  const openCreate = () => { setEditing(null); form.resetFields(); setFormOpen(true) }

  const openEdit = (row: T) => {
    setEditing(row)
    const values: Record<string, unknown> = { ...row }
    for (const field of dateFields) {
      const raw = row[field as keyof T] as number | undefined
      values[field] = raw != null ? dayjs(raw) : undefined
    }
    form.setFieldsValue(values)
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const payload: Record<string, unknown> = { ...values }
      for (const field of dateFields) {
        const day = values[field] as dayjs.Dayjs | undefined
        payload[field] = day ? day.valueOf() : undefined
      }
      if (editing) await onUpdate({ ...payload, id: editing.id })
      else await onCreate(payload)
      message.success(editing ? '已保存' : '已添加')
      setFormOpen(false); onReload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const handleDelete = (row: T) => {
    Modal.confirm({ title: '删除记录', content: '确定删除该条记录吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await onDelete(row.id!); message.success('已删除'); onReload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      } })
  }

  const operationCol: ColumnsType<T> = [{ title: '操作', width: 120, align: 'center', fixed: 'right', render: (_, row) => (
    <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => openEdit(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space>
  ) }]

  const renderControl = (field: FieldConfig) => {
    if (field.type === 'number') return <InputNumber style={{ width: '100%' }} placeholder={field.label}/>
    if (field.type === 'date') return <DatePicker style={{ width: '100%' }} placeholder={field.label}/>
    if (field.type === 'switch') return <Switch checkedChildren="是" unCheckedChildren="否"/>
    if (field.type === 'select') return <Select allowClear placeholder={field.label} options={field.options}/>
    if (field.type === 'textarea') return <Input.TextArea rows={3} placeholder={field.label}/>
    return <Input placeholder={field.label}/>
  }

  return <>
    <Space className="hrm-subtab-head">
      <span className="hrm-drawer-subtitle">{title}（{items.length}）</span>
      <Space size="small">
        {canCreate && <Button size="small" icon={<PlusOutlined/>} onClick={openCreate}>新增</Button>}
        <Button size="small" icon={<ReloadOutlined/>} onClick={onReload}>刷新</Button>
      </Space>
    </Space>
    <HrmProTable<T> rowKey="id" size="small" columns={[...columns, ...operationCol]} dataSource={items}
      pagination={false} loading={loading} scroll={{ x: 800 }}/>

    <Modal title={editing ? `编辑${title}` : `新增${title}`} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(840px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical" className="hrm-edit-grid">
        {fields.map(field => <Form.Item key={field.name} name={field.name} label={field.label}
          rules={field.required ? [{ required: true, message: `请输入${field.label}` }] : undefined}>
          {renderControl(field)}
        </Form.Item>)}
      </Form>
    </Modal>
  </>
}
