import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Select, Space, Switch, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmSalaryTaxRule } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'

/** 计税规则。列表 + 新增/编辑/删除。 */
export default function HrmSalaryTaxRulePage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmSalaryTaxRule[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmSalaryTaxRule>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<HrmSalaryTaxRule>()

  const taxType = useDict(HRM_DICT.SALARY_TAX_TYPE)
  const canCreate = permissions.includes('hrm:salary:tax-rule:create')
  const canUpdate = permissions.includes('hrm:salary:tax-rule:update')
  const canDelete = permissions.includes('hrm:salary:tax-rule:delete')

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.salaryCfg.taxRule.list()
      if (current !== version.current) return
      setItems(result)
    } catch (e) {
      if (current === version.current) setError(e instanceof Error ? e.message : '计税规则加载失败')
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const openForm = (row?: HrmSalaryTaxRule) => {
    setEditing(row)
    form.setFieldsValue(row ? { ...row } : { name: undefined, type: undefined, taxEnabled: true, threshold: 5000, decimalScale: 2, cycleType: undefined })
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) await api.hrm.salaryCfg.taxRule.update(values)
      else await api.hrm.salaryCfg.taxRule.create(values)
      message.success(editing ? '已保存' : '已创建')
      setFormOpen(false); void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const handleDelete = (row: HrmSalaryTaxRule) => {
    Modal.confirm({
      title: '删除计税规则', content: `确定删除「${row.name}」吗？被薪资组使用的规则无法删除。`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.salaryCfg.taxRule.delete(row.id!); message.success('已删除'); void load() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ColumnsType<HrmSalaryTaxRule> = [
    { title: '规则名称', dataIndex: 'name', width: 200, render: (value: string) => value },
    { title: '计税类型', dataIndex: 'type', width: 120, render: (value?: number) => value != null ? (taxType.labels[String(value)] || value) : '-' },
    { title: '是否计税', dataIndex: 'taxEnabled', width: 90, align: 'center', render: (value?: boolean) => value ? <Tag color="success">是</Tag> : <Tag>否</Tag> },
    { title: '起征阈值', dataIndex: 'threshold', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${value.toLocaleString('zh-CN')}` : '-' },
    { title: '小数位数', dataIndex: 'decimalScale', width: 90, align: 'center', render: (value?: number) => value != null ? `${value} 位` : '-' },
    { title: '使用薪资组', dataIndex: 'usedGroupCount', width: 110, align: 'right', render: (value?: number) => value != null ? `${value} 个` : '-' },
    { title: '操作', width: 150, align: 'center', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => openForm(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
      : !items.length ? <Empty description="暂无计税规则"/>
        : <HrmProTable<HrmSalaryTaxRule> advanced persistenceKey="salary-tax-rule" onReload={load} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 900 }}/>

  return <section className="workspace-page hrm-page hrm-salary-tax-rule-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => openForm()}>新增规则</Button>}
        <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title={editing ? '编辑计税规则' : '新增计税规则'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(760px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
          <Input placeholder="如 综合所得月度预扣"/>
        </Form.Item>
        <Form.Item name="type" label="计税类型" rules={[{ required: true, message: '请选择计税类型' }]}>
          <Select placeholder="请选择" loading={taxType.loading} options={taxType.options}/>
        </Form.Item>
        <Form.Item name="taxEnabled" label="是否计税" valuePropName="checked">
          <Switch checkedChildren="是" unCheckedChildren="否"/>
        </Form.Item>
        <Form.Item name="threshold" label="起征阈值" rules={[{ required: true, message: '请输入起征阈值' }]}>
          <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="如 5000"/>
        </Form.Item>
        <Form.Item name="decimalScale" label="小数位数">
          <InputNumber min={0} max={6} precision={0} style={{ width: '100%' }}/>
        </Form.Item>
        <Form.Item name="cycleType" label="计税周期类型">
          <Select placeholder="请选择" allowClear options={[{ value: 1, label: '月' }, { value: 2, label: '年' }]}/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
