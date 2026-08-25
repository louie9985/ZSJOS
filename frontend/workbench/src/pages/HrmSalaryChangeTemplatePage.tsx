import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Select, Space, Switch, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmSalaryChangeOption, type HrmSalaryChangeTemplate } from '../services/api'
import type { ColumnsType } from 'antd/es/table'

type ChangeTemplateFormValues = Omit<HrmSalaryChangeTemplate, 'options'> & { options: number[] }

/** 调薪模板。模板由若干调薪项组成，调薪项从薪资项目录中挑选。 */
export default function HrmSalaryChangeTemplatePage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmSalaryChangeTemplate[]>([])
  const [allOptions, setAllOptions] = useState<Array<{ value: number; label: string }>>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmSalaryChangeTemplate>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<ChangeTemplateFormValues>()

  const canCreate = permissions.includes('hrm:salary:change-template:create')
  const canUpdate = permissions.includes('hrm:salary:change-template:update')
  const canDelete = permissions.includes('hrm:salary:change-template:delete')

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const [templates, salaryOptions] = await Promise.all([
        api.hrm.salaryCfg.changeTemplate.list(),
        api.hrm.salaryCfg.option.list()
      ])
      if (current !== version.current) return
      setItems(templates)
      setAllOptions(salaryOptions.filter((item) => !item.parentCode).map((item) => ({ value: item.code, label: item.name })))
    } catch (e) {
      if (current === version.current) setError(e instanceof Error ? e.message : '调薪模板加载失败')
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const openForm = (row?: HrmSalaryChangeTemplate) => {
    setEditing(row)
    form.setFieldsValue(row ? { ...row, options: (row.options || []).map(opt => opt.code) } : { name: undefined, defaultStatus: false, options: [] })
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      // 从选中的 code 还原成 {name, code}
      const selectedCodes: number[] = values.options || []
      const options: HrmSalaryChangeOption[] = selectedCodes.map(code => ({
        code,
        name: allOptions.find(opt => opt.value === code)?.label || ''
      })).filter(opt => opt.name)
      const payload = { ...values, options }
      if (editing) await api.hrm.salaryCfg.changeTemplate.update(payload)
      else await api.hrm.salaryCfg.changeTemplate.create(payload)
      message.success(editing ? '已保存' : '已创建')
      setFormOpen(false); void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const handleDelete = (row: HrmSalaryChangeTemplate) => {
    Modal.confirm({
      title: '删除调薪模板', content: `确定删除「${row.name}」吗？`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.salaryCfg.changeTemplate.delete(row.id!); message.success('已删除'); void load() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ColumnsType<HrmSalaryChangeTemplate> = [
    { title: '模板名称', dataIndex: 'name', width: 200, render: (value: string) => value },
    { title: '默认模板', dataIndex: 'defaultStatus', width: 100, align: 'center', render: (value?: boolean) => value ? <Tag color="success">默认</Tag> : <Tag>普通</Tag> },
    { title: '调薪项', width: 320, render: (_, row) => renderTags(row.options || []) },
    { title: '创建时间', dataIndex: 'createTime', width: 170, render: (value?: string) => value ? new Date(value).toLocaleString('zh-CN') : '-' },
    { title: '操作', width: 150, align: 'center', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => openForm(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  function renderTags(options: HrmSalaryChangeOption[]) {
    if (!options?.length) return <span className="hrm-muted">无调薪项</span>
    return <Space size={[4, 4]} wrap>{options.map(opt => <Tag key={opt.code} color="blue">{opt.name}</Tag>)}</Space>
  }

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
      : !items.length ? <Empty description="暂无调薪模板"/>
        : <HrmProTable<HrmSalaryChangeTemplate> advanced persistenceKey="salary-change-template" onReload={load} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 900 }}/>

  return <section className="workspace-page hrm-page hrm-salary-change-template-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => openForm()}>新增模板</Button>}
        <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title={editing ? '编辑调薪模板' : '新增调薪模板'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(760px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
          <Input placeholder="如 年度普调模板"/>
        </Form.Item>
        <Form.Item name="defaultStatus" label="设为默认模板" valuePropName="checked">
          <Switch checkedChildren="是" unCheckedChildren="否"/>
        </Form.Item>
        <Form.Item name="options" label="调薪项" rules={[{ required: true, message: '请选择调薪项' }]}>
          <Select mode="multiple" placeholder="选择参与调薪的薪资项" options={allOptions}/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
