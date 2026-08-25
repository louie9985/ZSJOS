import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Select, Space, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmSalaryGroup, type HrmSalaryTaxRule } from '../services/api'
import type { ColumnsType } from 'antd/es/table'
import DeptTreeSelect from '../components/DeptTreeSelect'
import HrmEmployeePicker from '../components/HrmEmployeePicker'

/** 薪资组：规定适用部门/员工的月计薪标准与计税规则。 */
export default function HrmSalaryGroupPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmSalaryGroup[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [taxRules, setTaxRules] = useState<HrmSalaryTaxRule[]>([])
  const version = useRef(0)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmSalaryGroup>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<HrmSalaryGroup>()

  const canCreate = permissions.includes('hrm:salary:group:create')
  const canUpdate = permissions.includes('hrm:salary:group:update')
  const canDelete = permissions.includes('hrm:salary:group:delete')

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const [groups, rules] = await Promise.all([
        api.hrm.salaryCfg.group.page({ pageNo: 1, pageSize: 100 }),
        api.hrm.salaryCfg.taxRule.list()
      ])
      if (current !== version.current) return
      setItems(groups.list); setTaxRules(rules)
    } catch (e) {
      if (current === version.current) setError(e instanceof Error ? e.message : '薪资组加载失败')
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const openForm = async (row?: HrmSalaryGroup) => {
    if (row) {
      const detail = await api.hrm.salaryCfg.group.get(row.id).catch(() => row)
      setEditing(detail)
      form.setFieldsValue({ ...detail })
    } else {
      setEditing(undefined)
      form.resetFields()
    }
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) await api.hrm.salaryCfg.group.update(values)
      else await api.hrm.salaryCfg.group.create(values)
      message.success(editing ? '已保存' : '已创建')
      setFormOpen(false); void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const handleDelete = (row: HrmSalaryGroup) => {
    Modal.confirm({ title: '删除薪资组', content: `确定删除「${row.name}」吗？`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.salaryCfg.group.delete(row.id); message.success('已删除'); void load() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      } })
  }

  const columns: ColumnsType<HrmSalaryGroup> = [
    { title: '薪资组名称', dataIndex: 'name', width: 180, render: (value: string) => value },
    { title: '月计薪标准', dataIndex: 'salaryStandard', width: 120, align: 'right', render: (value?: number) => value != null ? `${value} 天` : '-' },
    { title: '计税规则', dataIndex: 'taxRuleName', width: 140, render: (value?: string) => value || '-' },
    { title: '适用范围', width: 240, render: (_, row) => {
      const count = (row.deptNames?.length || 0) + (row.employeeNames?.length || 0)
      return count ? <Tag>{count}{row.deptNames?.length && row.employeeNames?.length ? ' 个部门/员工' : row.deptNames?.length ? ' 个部门' : ' 名员工'}</Tag> : <span className="hrm-muted">全部</span>
    } },
    { title: '创建时间', dataIndex: 'createTime', width: 170, render: (value?: string) => value ? new Date(value).toLocaleString('zh-CN') : '-' },
    { title: '操作', width: 150, align: 'center', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => void openForm(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
      : !items.length ? <Empty description="暂无薪资组"/>
        : <HrmProTable<HrmSalaryGroup> advanced persistenceKey="salary-group" onReload={load} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 1000 }}/>

  return <section className="workspace-page hrm-page hrm-salary-group-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openForm()}>新增薪资组</Button>}
        <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title={editing ? '编辑薪资组' : '新增薪资组'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(840px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="薪资组名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input placeholder="如 一线员工"/>
        </Form.Item>
        <Form.Item name="salaryStandard" label="月计薪标准(天)">
          <InputNumber min={1} max={31} precision={0} style={{ width: '100%' }}/>
        </Form.Item>
        <Form.Item name="taxRuleId" label="计税规则">
          <Select allowClear placeholder="请选择" options={taxRules.map(rule => ({ value: rule.id!, label: rule.name }))}/>
        </Form.Item>
        <Form.Item name="changeRule" label="转正/调薪月规则">
          <Input placeholder="如 当月转正次月调薪"/>
        </Form.Item>
        <Form.Item name="deptIds" label="适用部门">
          <DeptTreeSelect multiple treeCheckable placeholder="选择部门（可多选）"/>
        </Form.Item>
        <Form.Item name="employeeIds" label="适用员工">
          <HrmEmployeePicker mode="multiple" placeholder="选择员工（可多选）"/>
        </Form.Item>
        <Alert message="部门与员工可同时指定，薪资组适用范围为两者的并集" type="info" showIcon/>
      </Form>
    </Modal>
  </section>
}
