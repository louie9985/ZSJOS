import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Popconfirm, Space, Switch, Tree, message } from 'antd'
import type { TreeDataNode } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api } from '../services/api'

type HrmDeptNode = { id: number; parentId: number; name: string; sort?: number; status?: number; leaderUserId?: number; createTime?: number; children?: HrmDeptNode[] }

function buildTree(list: Array<{ id: number; parentId: number; name: string }>): HrmDeptNode[] {
  const map = new Map<number, HrmDeptNode>()
  for (const item of list) map.set(item.id, { ...item, children: [] })
  const roots: HrmDeptNode[] = []
  for (const item of list) {
    const node = map.get(item.id)!
    const parent = map.get(item.parentId)
    if (parent) parent.children!.push(node)
    else roots.push(node)
  }
  return roots
}

function findPath(nodes: HrmDeptNode[], id: number, trail: string[] = []): string | undefined {
  for (const node of nodes) {
    const next = [...trail, node.name]
    if (node.id === id) return next.join(' / ')
    const found = findPath(node.children || [], id, next)
    if (found) return found
  }
  return undefined
}

function findById(nodes: HrmDeptNode[], id?: number): HrmDeptNode | undefined {
  if (!id) return undefined
  for (const node of nodes) {
    if (node.id === id) return node
    const found = findById(node.children || [], id)
    if (found) return found
  }
  return undefined
}

/** 组织管理：部门树 + 部门增删改。 */
export default function HrmDeptPage({ permissions }: { permissions: string[] }) {
  const [tree, setTree] = useState<HrmDeptNode[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)
  const [selected, setSelected] = useState<HrmDeptNode>()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmDeptNode>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{ name: string; sort?: number; status?: number }>()
  const watchStatus = Form.useWatch('status', form) ?? 0

  const canCreate = permissions.includes('system:dept:create')
  const canUpdate = permissions.includes('system:dept:update')
  const canDelete = permissions.includes('system:dept:delete')

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const list = await api.hrm.dept.list()
      if (current !== version.current) return
      setTree(buildTree(list))
    } catch (e) {
      if (current === version.current) setError(e instanceof Error ? e.message : '组织加载失败')
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const openForm = (parent?: HrmDeptNode, node?: HrmDeptNode) => {
    setEditing(node)
    form.setFieldsValue(node ? { name: node.name, sort: node.sort, status: node.status ?? 0 } : { name: '', sort: 0, status: 0 })
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) await api.hrm.dept.update({ ...editing, ...values })
      else await api.hrm.dept.create({ ...values, parentId: selected?.id || 0 })
      message.success(editing ? '已保存' : '已创建')
      setFormOpen(false); void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const handleDelete = (node: HrmDeptNode) => {
    if (node.status === 1) { message.error('请先停用该部门再删除'); return }
    Modal.confirm({
      title: '删除部门', content: `确定删除「${node.name}」吗？删除前请确认无下级部门与员工。`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.dept.delete(node.id); message.success('已删除'); void load() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const renderTitle = (node: HrmDeptNode) => (
    <Space size="small">
      <span>{node.name}</span>
      {node.children?.length ? null : <span className="hrm-muted">按 {node.sort ?? 0} 排序</span>}
      <span className="hrm-dept-actions">
        {canCreate && <Button type="link" size="small" onClick={(e) => { e.stopPropagation(); setSelected(node); openForm(node) }}>加子级</Button>}
        {canUpdate && <Button type="link" size="small" onClick={(e) => { e.stopPropagation(); setSelected(node); openForm(node, node) }}>编辑</Button>}
        {canDelete && <Popconfirm title="确认删除该部门？" onConfirm={() => handleDelete(node)}>
          <Button type="link" size="small" danger onClick={(e) => e.stopPropagation()}>删除</Button>
        </Popconfirm>}
      </span>
    </Space>
  )

  const content = loading && !tree.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
      : !tree.length ? <Empty description="暂无组织"/>
        : <Tree
          blockNode
          treeData={tree as unknown as TreeDataNode[]}
          defaultExpandAll
          selectedKeys={selected ? [selected.id] : []}
          onSelect={keys => { setSelected(findById(tree, typeof keys[0] === 'number' ? keys[0] : Number(keys[0]))) }}
          titleRender={node => renderTitle(node as unknown as HrmDeptNode)}
        />

  return <section className="workspace-page hrm-page hrm-dept-page">
    <div className="page-heading">
      <span className="hrm-muted">组织管理维护系统部门树，员工档案按部门归属</span>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => { setSelected(undefined); openForm() }}>新增一级部门</Button>}
        <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title={editing ? `编辑部门：${editing.name}` : `新增${selected ? `「${findPath(tree, selected.id)}」的子` : '一级'}部门`}
      open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(760px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="部门名称" rules={[{ required: true, message: '请输入部门名称' }]}>
          <Input placeholder="如 销售一部"/>
        </Form.Item>
        <Form.Item name="sort" label="排序"><Input type="number" placeholder="数字越小越靠前"/></Form.Item>
        <Form.Item name="status" label="部门状态">
          <Switch checked={watchStatus === 1} onChange={checked => form.setFieldValue('status', checked ? 1 : 0)}
            checkedChildren="启用" unCheckedChildren="停用"/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
