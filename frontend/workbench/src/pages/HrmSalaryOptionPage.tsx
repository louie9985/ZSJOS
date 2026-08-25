import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Space, Switch, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmSalaryOptionCfg } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'

function buildTree(list: HrmSalaryOptionCfg[]): HrmSalaryOptionCfg[] {
  const map = new Map<number, HrmSalaryOptionCfg>()
  for (const item of list) map.set(item.code, { ...item, children: [] })
  const roots: HrmSalaryOptionCfg[] = []
  for (const item of list) {
    const node = map.get(item.code)!
    const parent = item.parentCode ? map.get(item.parentCode) : undefined
    if (parent) parent.children!.push(node)
    else roots.push(node)
  }
  return roots
}

/** 工资项设置（薪资目录树）。系统项不可删，只能开关显示/计算/计税。 */
export default function HrmSalaryOptionPage({ permissions }: { permissions: string[] }) {
  const [list, setList] = useState<HrmSalaryOptionCfg[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const [createOpen, setCreateOpen] = useState(false)
  const [createLoading, setCreateLoading] = useState(false)
  const [createForm] = Form.useForm<{ name: string; remark?: string }>()
  const [parent, setParent] = useState<HrmSalaryOptionCfg>()
  const [acting, setActing] = useState(false)

  const optionType = useDict(HRM_DICT.SALARY_OPTION_TYPE)
  const canCreate = permissions.includes('hrm:salary:option:create')
  const canDelete = permissions.includes('hrm:salary:option:delete')

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.salaryCfg.option.list()
      if (current !== version.current) return
      setList(result)
    } catch (e) {
      if (current === version.current) setError(e instanceof Error ? e.message : '薪资项加载失败')
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const handleCreate = async () => {
    const values = await createForm.validateFields()
    setCreateLoading(true)
    try {
      await api.hrm.salaryCfg.option.create({ ...values, parentCode: parent?.code })
      message.success('已创建')
      setCreateOpen(false); createForm.resetFields(); void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '创建失败') }
    finally { setCreateLoading(false) }
  }

  const toggle = async (item: HrmSalaryOptionCfg, field: 'enabled' | 'visible', value: boolean) => {
    setActing(true)
    try {
      const fn = field === 'enabled' ? api.hrm.salaryCfg.option.updateEnabled : api.hrm.salaryCfg.option.updateVisible
      await fn(item.id, value)
      setList(current => current.map(node => node.code === item.code ? { ...node, [field]: value } : node))
    } catch (e) { message.error(e instanceof Error ? e.message : '操作失败') }
    finally { setActing(false) }
  }

  const handleDelete = (item: HrmSalaryOptionCfg) => {
    Modal.confirm({
      title: '删除薪资项', content: `确定删除「${item.name}」吗？若非系统项且未被使用则删除。`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.salaryCfg.option.delete(item.id); message.success('已删除'); void load() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ColumnsType<HrmSalaryOptionCfg> = [
    { title: '薪资项名称', dataIndex: 'name', width: 200, render: (value: string, row) => <span className={row.systemFlag ? 'hrm-option-system' : ''}>{value}</span> },
    { title: '编码', dataIndex: 'code', width: 90, render: (value: number) => value },
    { title: '类型', dataIndex: 'type', width: 100, render: (value?: number) => value != null ? (optionType.labels[String(value)] || value) : '-' },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (value?: string) => value || '-' },
    { title: '计税', dataIndex: 'taxEnabled', width: 80, align: 'center', render: (value?: boolean) => value ? <Tag color="success">是</Tag> : <Tag>否</Tag> },
    { title: '显示', dataIndex: 'visible', width: 80, align: 'center', render: (value: boolean, row) =>
      <Switch size="small" checked={value} disabled={acting || row.systemFlag} onChange={val => void toggle(row, 'visible', val)}/> },
    { title: '启用', dataIndex: 'enabled', width: 80, align: 'center', render: (value: boolean, row) =>
      <Switch size="small" checked={value} disabled={acting || row.systemFlag} onChange={val => void toggle(row, 'enabled', val)}/> },
    { title: '操作', width: 150, align: 'center', render: (_, row) => <Space size="small">
      {canCreate && <Button type="link" size="small" onClick={() => { setParent(row); createForm.resetFields(); setCreateOpen(true) }}>加子项</Button>}
      {canDelete && !row.systemFlag && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !list.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
      : !list.length ? <Empty description="暂无薪资项"/>
        : <HrmProTable<HrmSalaryOptionCfg> advanced persistenceKey="salary-option" onReload={load} rowKey="id" columns={columns} dataSource={buildTree(list)} pagination={false}
          loading={loading} expandable={{ defaultExpandAllRows: true }} scroll={{ x: 900 }}/>

  return <section className="workspace-page hrm-page hrm-salary-option-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => { setParent(undefined); createForm.resetFields(); setCreateOpen(true) }}>新增薪资项</Button>}
        <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title={parent ? `在「${parent.name}」下添加子项` : '新增薪资项'} open={createOpen} onCancel={() => setCreateOpen(false)}
      onOk={() => void handleCreate()} confirmLoading={createLoading} width="min(760px, 96vw)" destroyOnClose>
      <Form form={createForm} layout="vertical">
        <Form.Item name="name" label="薪资项名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input placeholder="如 全勤奖"/>
        </Form.Item>
        <Form.Item name="remark" label="备注"><Input.TextArea rows={2}/></Form.Item>
        <Alert message="系统默认项不可删除，仅能控制是否启用/显示" type="info" showIcon/>
      </Form>
    </Modal>
  </section>
}
