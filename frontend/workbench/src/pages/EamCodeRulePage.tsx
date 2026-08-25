import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Tag, TreeSelect, message } from 'antd'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type EamCategory, type EamCodeRule } from '../services/api'
import { buildEamTree, previewAssetCode, toTreeSelectData } from '../services/eam'

export default function EamCodeRulePage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<EamCodeRule[]>([])
  const [categories, setCategories] = useState<EamCategory[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [formOpen, setFormOpen] = useState(false)
  const [formLoading, setFormLoading] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'update'>('create')
  const [editingId, setEditingId] = useState<number>()
  const [form] = Form.useForm<EamCodeRule>()
  const previewValues = Form.useWatch([], form)

  const canCreate = permissions.includes('eam:code-rule:create')
  const canUpdate = permissions.includes('eam:code-rule:update')
  const canDelete = permissions.includes('eam:code-rule:delete')

  const categoryTree = useMemo(() => toTreeSelectData(buildEamTree(categories)), [categories])
  const categoryName = useCallback((id?: number) => id == null ? '' : categories.find(item => item.id === id)?.name ?? `分类#${id}`, [categories])
  const categoryCode = useCallback((id?: number) => id == null ? undefined : categories.find(item => item.id === id)?.code, [categories])

  const load = useCallback(async () => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const [rules, categoryList] = await Promise.all([api.eam.codeRule.list(), api.eam.category.list()])
      if (version !== listVersion.current) return
      setItems(rules); setCategories(categoryList)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '编号规则加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])
  useEffect(() => { void load() }, [load])

  const openForm = async (mode: 'create' | 'update', id?: number) => {
    setFormMode(mode); setEditingId(id); setFormOpen(true); form.resetFields()
    if (mode === 'create') {
      form.setFieldsValue({ useCategoryCode: true, dateFormat: 'yyyy', serialLength: 4, separator: '-' })
      return
    }
    if (!id) return
    setFormLoading(true)
    try { form.setFieldsValue(await api.eam.codeRule.get(id)) }
    catch (e) { message.error(e instanceof Error ? e.message : '规则详情加载失败'); setFormOpen(false) }
    finally { setFormLoading(false) }
  }

  const submitForm = async () => {
    const values = await form.validateFields()
    setFormLoading(true)
    try {
      if (formMode === 'create') { await api.eam.codeRule.create(values); message.success('创建成功') }
      else { await api.eam.codeRule.update({ ...values, id: editingId }); message.success('更新成功') }
      setFormOpen(false); void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setFormLoading(false) }
  }

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除', content: '确定要删除该编号规则吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.eam.codeRule.delete(id); message.success('已删除'); void load() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const columns: ProColumns<EamCodeRule>[] = [
    { title: '适用分类', width: 140, render: (_, row) => row.categoryId
      ? categoryName(row.categoryId) : <Tag color="warning">全局默认</Tag> },
    { title: '前缀', dataIndex: 'prefix', width: 100 },
    { title: '拼接分类编码', width: 120, align: 'center', render: (_, row) => <Tag color={row.useCategoryCode ? 'success' : 'default'}>{row.useCategoryCode ? '是' : '否'}</Tag> },
    { title: '日期格式', width: 110, render: (_, row) => row.dateFormat || '不含日期' },
    { title: '流水位数', dataIndex: 'serialLength', width: 90, align: 'center' },
    { title: '分隔符', dataIndex: 'separator', width: 80, align: 'center' },
    { title: '当前流水号', dataIndex: 'currentSerial', width: 110, align: 'center', render: (_, row) => row.currentSerial ?? 0 },
    { title: '编号示例', width: 160, render: (_, row) => <span className="eam-code-preview">{previewAssetCode(row, categoryCode(row.categoryId))}</span> },
    { title: '操作', width: 140, align: 'center', fixed: 'right', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => void openForm('update', row.id)}>编辑</Button>}
      {canDelete && row.id != null && <Button type="link" size="small" danger onClick={() => handleDelete(row.id!)}>删除</Button>}
    </Space> }
  ]

  const content = error
    ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
    : <ProTable<EamCodeRule> rowKey={row => String(row.id)} columns={columns} dataSource={items} loading={loading}
        search={false} pagination={false} columnsState={{ persistenceKey: 'eam-code-rule-table-columns', persistenceType: 'localStorage' }}
        options={{ reload: load, density: true, setting: true, fullScreen: true }} scroll={{ x: 1100 }}/>

  return <section className="workspace-page eam-code-rule-page">
    <Alert className="eam-inline-alert" type="info" showIcon
      message="资产创建时按分类匹配编号规则；找不到分类规则时使用全局规则（适用分类为空的那条）"/>
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openForm('create')}>新增规则</Button>}
        <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
      </Space>
    </div>
    <div className="eam-table-area">{content}</div>

    <Modal title={formMode === 'create' ? '新增编号规则' : '编辑编号规则'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={submitForm} confirmLoading={formLoading} width={760} destroyOnClose>
      <Form form={form} layout="vertical" className="eam-wide-form">
        <Form.Item name="categoryId" label="适用分类">
          <TreeSelect treeData={categoryTree} placeholder="留空表示全局默认规则" style={{ width: '100%' }} allowClear treeDefaultExpandAll/>
        </Form.Item>
        <Form.Item name="prefix" label="固定前缀"><Input placeholder="如 AS"/></Form.Item>
        <Form.Item name="useCategoryCode" label="拼接分类编码" valuePropName="checked" rules={[{ required: true }]}><Switch/></Form.Item>
        <Form.Item name="dateFormat" label="日期格式">
          <Select allowClear placeholder="不含日期" options={[
            { value: 'yyyy', label: '年份（2026）' },
            { value: 'yyyyMM', label: '年月（202608）' }
          ]}/>
        </Form.Item>
        <Form.Item name="serialLength" label="流水号位数" rules={[{ required: true, message: '请输入流水号位数' }]}>
          <InputNumber min={1} max={12} style={{ width: '100%' }}/>
        </Form.Item>
        <Form.Item name="separator" label="分隔符"><Input maxLength={5} placeholder="默认 -"/></Form.Item>
        <Form.Item label="编号示例">
          <span className="eam-code-preview eam-code-preview-accent">
            {previewValues ? previewAssetCode(previewValues, categoryCode(previewValues.categoryId)) : '-'}
          </span>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
