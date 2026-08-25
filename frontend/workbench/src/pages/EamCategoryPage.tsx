import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Radio, Segmented, Select, Skeleton, Space, Switch, Tag, Tooltip, Tree, TreeSelect, message } from 'antd'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { PlusOutlined, ReloadOutlined, UploadOutlined, SearchOutlined } from '@ant-design/icons'
import { api, type EamCategory, type EamCategoryField, type EamCategorySave } from '../services/api'
import { FIELD_TYPE, FIELD_TYPE_LABELS, MANAGEMENT_MODE, buildEamTree, filterCategoryTree, toTreeSelectData, type TreeSelectNode } from '../services/eam'
import CategoryImportModal from '../components/CategoryImportModal'

type FieldFormValues = Omit<EamCategoryField, 'options'> & { options?: string[] }

export default function EamCategoryPage({ permissions }: { permissions: string[] }) {
  const [categories, setCategories] = useState<EamCategory[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [rootFilter, setRootFilter] = useState<number>()
  const [selected, setSelected] = useState<EamCategory>()
  const listVersion = useRef(0)

  const [categoryOpen, setCategoryOpen] = useState(false)
  const [categoryImportOpen, setCategoryImportOpen] = useState(false)
  const [categoryLoading, setCategoryLoading] = useState(false)
  const [categoryMode, setCategoryMode] = useState<'create' | 'update'>('create')
  const [editingId, setEditingId] = useState<number>()
  const [categoryForm] = Form.useForm<EamCategorySave>()

  const [fields, setFields] = useState<EamCategoryField[]>([])
  const [fieldsLoading, setFieldsLoading] = useState(false)
  const [fieldsError, setFieldsError] = useState('')
  const fieldsVersion = useRef(0)

  const [fieldOpen, setFieldOpen] = useState(false)
  const [fieldLoading, setFieldLoading] = useState(false)
  const [fieldMode, setFieldMode] = useState<'create' | 'update'>('create')
  const [editingFieldId, setEditingFieldId] = useState<number>()
  const [fieldForm] = Form.useForm<FieldFormValues>()
  const [fieldType, setFieldType] = useState<number>(FIELD_TYPE.TEXT)
  const [optionSource, setOptionSource] = useState<'STATIC' | 'SYSTEM_DICT'>('STATIC')
  const [collectionVisible, setCollectionVisible] = useState(false)
  const [conditionRuleText, setConditionRuleText] = useState('')

  const canCreate = permissions.includes('eam:category:create')
  const canUpdate = permissions.includes('eam:category:update')
  const canDelete = permissions.includes('eam:category:delete')
  const canFieldCreate = permissions.includes('eam:category-field:create')
  const canFieldUpdate = permissions.includes('eam:category-field:update')
  const canFieldDelete = permissions.includes('eam:category-field:delete')

  const tree = useMemo(() => buildEamTree(categories), [categories])
  const rootOptions = useMemo(() => categories.filter(item => item.parentId === 0), [categories])
  const visibleTree = useMemo(() => {
    const scoped = rootFilter ? tree.filter(node => node.id === rootFilter) : tree
    return filterCategoryTree(scoped, keyword)
  }, [tree, rootFilter, keyword])

  /** 编辑时把自身从父分类候选中剔除，避免选出环形结构 */
  const parentTreeData = useMemo<TreeSelectNode[]>(() => {
    const prune = (nodes: typeof tree): typeof tree => nodes
      .filter(node => node.id !== editingId)
      .map(node => ({ ...node, children: prune(node.children) }))
    return [{ title: '顶级分类', value: 0, children: toTreeSelectData(prune(tree)) }]
  }, [tree, editingId])

  const treeData = useMemo(() => {
    const toNode = (nodes: typeof visibleTree): Array<Record<string, unknown>> => nodes.map(node => ({
      key: node.id,
      title: <span className="eam-category-node">
        <span className="eam-category-name">{node.name}</span>
        <Tag>{node.code}</Tag>
        {node.status === 1 && <Tag color="default">已关闭</Tag>}
        <Tag color="default">{node.managementMode === MANAGEMENT_MODE.BATCH ? '批量' : '单件'} / {node.unit || '个'}</Tag>
      </span>,
      children: toNode(node.children)
    }))
    return toNode(visibleTree)
  }, [visibleTree])

  const loadCategories = useCallback(async () => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.eam.category.list()
      if (version === listVersion.current) setCategories(result)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '分类加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])
  useEffect(() => { void loadCategories() }, [loadCategories])

  const loadFields = useCallback(async (categoryId?: number) => {
    const version = ++fieldsVersion.current
    if (!categoryId) { setFields([]); return }
    setFieldsLoading(true); setFieldsError('')
    try {
      // 用生效列表，让管理员直观看到本级字段与父级继承字段的合并结果
      const result = await api.eam.categoryField.effectiveList(categoryId)
      if (version === fieldsVersion.current) setFields(result)
    } catch (e) {
      if (version === fieldsVersion.current) setFieldsError(e instanceof Error ? e.message : '自定义字段加载失败')
    } finally {
      if (version === fieldsVersion.current) setFieldsLoading(false)
    }
  }, [])
  useEffect(() => { void loadFields(selected?.id) }, [loadFields, selected])

  const openCategoryForm = async (mode: 'create' | 'update', id?: number, parentId?: number) => {
    setCategoryMode(mode); setEditingId(id); setCategoryOpen(true); categoryForm.resetFields()
    if (mode === 'create') {
      categoryForm.setFieldsValue({ parentId: parentId ?? 0, sort: 0, status: 0, managementMode: MANAGEMENT_MODE.SINGLE, unit: '个' })
      return
    }
    if (!id) return
    setCategoryLoading(true)
    try { categoryForm.setFieldsValue(await api.eam.category.get(id)) }
    catch (e) { message.error(e instanceof Error ? e.message : '分类详情加载失败'); setCategoryOpen(false) }
    finally { setCategoryLoading(false) }
  }

  const submitCategory = async () => {
    const values = await categoryForm.validateFields()
    setCategoryLoading(true)
    try {
      if (categoryMode === 'create') { await api.eam.category.create(values); message.success('创建成功') }
      else { await api.eam.category.update({ ...values, id: editingId }); message.success('更新成功') }
      setCategoryOpen(false); void loadCategories()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setCategoryLoading(false) }
  }

  const handleDeleteCategory = (id: number) => {
    Modal.confirm({
      title: '确认删除', content: '确定要删除该分类吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try {
          await api.eam.category.delete(id); message.success('已删除')
          if (selected?.id === id) setSelected(undefined)
          void loadCategories()
        } catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const openFieldForm = (mode: 'create' | 'update', field?: EamCategoryField) => {
    setFieldMode(mode); setEditingFieldId(field?.id); setFieldOpen(true); fieldForm.resetFields()
    if (mode === 'create') {
      setFieldType(FIELD_TYPE.TEXT)
      setOptionSource('STATIC')
      fieldForm.setFieldsValue({ fieldType: FIELD_TYPE.TEXT, optionSource: 'STATIC', required: false, adminVisible: true, collectionVisible: false, collectionRequired: false, sort: 0 })
    } else if (field) {
      setFieldType(field.fieldType)
      setOptionSource(field.optionSource ?? 'STATIC')
      fieldForm.setFieldsValue(field)
    }
    setCollectionVisible(mode === 'create' ? false : (field?.collectionVisible ?? false))
    setConditionRuleText(mode === 'create' ? '' : (field?.conditionRule ? JSON.stringify(field.conditionRule, null, 2) : ''))
  }

  const submitField = async () => {
    if (!selected) return
    const values = await fieldForm.validateFields()
    const isSelect = values.fieldType === FIELD_TYPE.SELECT
    // 条件规则：可选 JSON 对象；留空则置空
    let conditionRule: Record<string, unknown> | undefined
    if (conditionRuleText.trim()) {
      try {
        const parsed = JSON.parse(conditionRuleText)
        if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') { message.warning('条件规则必须是 JSON 对象'); return }
        conditionRule = parsed
      } catch { message.warning('条件规则不是有效的 JSON'); return }
    }
    // 与后端 normalizeOptions 保持一致：非下拉类型清空选项相关字段；系统字典源不留静态选项
    const payload: EamCategoryField = {
      ...values,
      categoryId: selected.id,
      id: fieldMode === 'update' ? editingFieldId : undefined,
      optionSource: isSelect ? (values.optionSource ?? 'STATIC') : undefined,
      dictType: isSelect && values.optionSource === 'SYSTEM_DICT' ? values.dictType : undefined,
      options: isSelect && values.optionSource === 'STATIC' ? (values.options ?? []).map(item => item.trim()).filter(Boolean) : undefined,
      required: false,
      conditionRule,
      // 员工表不可见时必填无意义，与 admin 端一致强制关闭
      collectionRequired: values.collectionVisible ? values.collectionRequired : false
    }
    setFieldLoading(true)
    try {
      if (fieldMode === 'create') { await api.eam.categoryField.create(payload); message.success('创建成功') }
      else { await api.eam.categoryField.update(payload); message.success('更新成功') }
      setFieldOpen(false); void loadFields(selected.id)
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setFieldLoading(false) }
  }

  const handleDeleteField = (id: number) => {
    Modal.confirm({
      title: '确认删除', content: '确定要删除该自定义字段吗？', okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.eam.categoryField.delete(id); message.success('已删除'); void loadFields(selected?.id) }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  const fieldColumns: ProColumns<EamCategoryField>[] = [
    { title: '字段名', dataIndex: 'fieldName', width: 120 },
    { title: '标识', dataIndex: 'fieldKey', width: 120 },
    { title: '类型', width: 100, render: (_, row) => FIELD_TYPE_LABELS[row.fieldType] ?? '未知' },
    { title: '管理端', width: 90, align: 'center', render: (_, row) => row.adminVisible ? <Tag color="success">显示</Tag> : <span className="eam-muted">隐藏</span> },
    { title: '员工收集表', width: 120, align: 'center', render: (_, row) => !row.collectionVisible
      ? <span className="eam-muted">隐藏</span>
      : row.collectionRequired ? <Tag color="error">必填</Tag> : <Tag>选填</Tag> },
    { title: '来源', width: 90, align: 'center', render: (_, row) => row.inherited ? <Tag>继承</Tag> : <Tag color="success">本级</Tag> },
    { title: '排序', dataIndex: 'sort', width: 70, align: 'center' },
    { title: '操作', width: 130, align: 'center', fixed: 'right', render: (_, row) => {
      // 继承字段属于父分类，只能到父分类上修改，此处禁用避免误改影响其他子类
      if (row.inherited) return <Tooltip title="继承字段请到父分类修改"><Button type="link" size="small" disabled>编辑</Button></Tooltip>
      return <Space size="small">
        {canFieldUpdate && <Button type="link" size="small" onClick={() => openFieldForm('update', row)}>编辑</Button>}
        {canFieldDelete && row.id != null && <Button type="link" size="small" danger onClick={() => handleDeleteField(row.id!)}>删除</Button>}
      </Space>
    }}
  ]

  const treePane = loading ? <Skeleton active paragraph={{ rows: 10 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadCategories()}>重试</Button>}/>
      : !visibleTree.length ? <Empty description="没有匹配的资产分类"/>
        : <Tree treeData={treeData} defaultExpandAll blockNode selectedKeys={selected ? [selected.id] : []}
          onSelect={keys => {
            const id = keys[0] as number | undefined
            setSelected(id != null ? categories.find(item => item.id === id) : undefined)
          }}/>

  const fieldsPane = !selected ? <Alert type="info" showIcon message="请先在左侧选择一个分类"/>
    : fieldsError ? <Alert type="error" showIcon message={fieldsError} action={<Button size="small" onClick={() => void loadFields(selected.id)}>重试</Button>}/>
      : <ProTable<EamCategoryField> rowKey={row => String(row.id ?? row.fieldKey)} columns={fieldColumns} dataSource={fields}
          loading={fieldsLoading} search={false} pagination={false} size="small"
          columnsState={{ persistenceKey: 'eam-category-field-table-columns', persistenceType: 'localStorage' }}
          options={{ reload: () => loadFields(selected.id), density: true, setting: true, fullScreen: true }} scroll={{ x: 900 }}/>

  return <section className="workspace-page eam-category-page">
    <div className="eam-category-layout">
      <aside className="eam-category-tree-pane">
        <div className="eam-pane-header">
          <span className="eam-pane-title">资产分类</span>
          <Space size="small">
            {permissions.includes('eam:category:import') && <Button size="small" icon={<UploadOutlined/>} onClick={() => setCategoryImportOpen(true)}>导入配置</Button>}
            {canCreate && <Button size="small" type="primary" ghost icon={<PlusOutlined/>} onClick={() => void openCategoryForm('create')}>新增</Button>}
            <Button size="small" icon={<ReloadOutlined/>} onClick={() => void loadCategories()}/>
          </Space>
        </div>
        <Space.Compact className="eam-category-filters">
          <Input allowClear prefix={<SearchOutlined/>} placeholder="搜索分类名称或编码" value={keyword} onChange={event => setKeyword(event.target.value)}/>
          <Select allowClear placeholder="全部顶级分类" value={rootFilter} onChange={setRootFilter} style={{ minWidth: 150 }}
            options={rootOptions.map(item => ({ value: item.id, label: item.name }))}/>
        </Space.Compact>
        <div className="eam-category-tree-scroll">{treePane}</div>
        {selected && <div className="eam-category-actions">
          {canCreate && <Button size="small" onClick={() => void openCategoryForm('create', undefined, selected.id)}>添加子类</Button>}
          {canUpdate && <Button size="small" onClick={() => void openCategoryForm('update', selected.id)}>编辑</Button>}
          {canDelete && <Button size="small" danger onClick={() => handleDeleteCategory(selected.id)}>删除</Button>}
        </div>}
      </aside>

      <main className="eam-category-detail-pane">
        <div className="eam-pane-header">
          <span className="eam-pane-title">自定义字段{selected && <span className="eam-muted">（{selected.name}）</span>}</span>
          {canFieldCreate && <Button size="small" type="primary" ghost icon={<PlusOutlined/>} disabled={!selected} onClick={() => openFieldForm('create')}>新增字段</Button>}
        </div>
        {fieldsPane}
      </main>
    </div>

    <Modal title={categoryMode === 'create' ? '新增分类' : '编辑分类'} open={categoryOpen} onCancel={() => setCategoryOpen(false)}
      onOk={submitCategory} confirmLoading={categoryLoading} width={760} destroyOnClose>
      <Form form={categoryForm} layout="vertical" className="eam-wide-form">
        <Form.Item name="parentId" label="父分类" rules={[{ required: true, message: '请选择父分类' }]}>
          <TreeSelect style={{ width: '100%' }} treeDefaultExpandAll placeholder="不选则为顶级分类" treeData={parentTreeData}/>
        </Form.Item>
        <Form.Item name="name" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}><Input placeholder="请输入分类名称"/></Form.Item>
        <Form.Item name="code" label="分类编码" rules={[{ required: true, message: '请输入分类编码' }]}><Input placeholder="用于拼接资产编号，如 IT"/></Form.Item>
        <Form.Item name="managementMode" label="管理模式" rules={[{ required: true }]}>
          <Segmented block options={[{ label: '单件管理', value: MANAGEMENT_MODE.SINGLE }, { label: '批量管理', value: MANAGEMENT_MODE.BATCH }]}/>
        </Form.Item>
        <Form.Item name="unit" label="计量单位" rules={[{ required: true, message: '请输入计量单位' }]}><Input placeholder="如 个、本、套、箱"/></Form.Item>
        <Form.Item name="sort" label="排序" rules={[{ required: true, message: '请输入排序' }]}><InputNumber min={0} style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="status" label="状态" rules={[{ required: true }]}>
          <Radio.Group options={[{ value: 0, label: '开启' }, { value: 1, label: '关闭' }]}/>
        </Form.Item>
        <Form.Item name="remark" label="备注"><Input.TextArea rows={2} placeholder="请输入备注"/></Form.Item>
      </Form>
    </Modal>

    <Modal title={fieldMode === 'create' ? '新增自定义字段' : '编辑自定义字段'} open={fieldOpen} onCancel={() => setFieldOpen(false)}
      onOk={submitField} confirmLoading={fieldLoading} width={760} destroyOnClose>
      <Form form={fieldForm} layout="vertical" className="eam-wide-form">
        <Form.Item name="fieldName" label="字段名" rules={[{ required: true, message: '请输入字段名' }]}><Input placeholder="展示给用户的名称"/></Form.Item>
        <Form.Item name="fieldKey" label="字段标识" rules={[{ required: true, message: '请输入字段标识' }]}>
          <Input placeholder="英文标识，保存后不建议修改" disabled={fieldMode === 'update'}/>
        </Form.Item>
        <Form.Item name="fieldType" label="字段类型" rules={[{ required: true }]}>
          <Select onChange={setFieldType} options={Object.entries(FIELD_TYPE_LABELS).map(([value, label]) => ({ value: Number(value), label }))}/>
        </Form.Item>
        {fieldType === FIELD_TYPE.SELECT && <>
          <Form.Item name="optionSource" label="选项来源" rules={[{ required: true }]}>
            <Select onChange={setOptionSource} options={[
              { value: 'STATIC', label: '固定选项' },
              { value: 'SYSTEM_DICT', label: '系统字典' }
            ]}/>
          </Form.Item>
          {optionSource === 'SYSTEM_DICT' && <Form.Item name="dictType" label="字典类型" rules={[{ required: true, message: '请输入 System 字典类型编码' }]}>
            <Input placeholder="请输入 System 字典类型编码"/>
          </Form.Item>}
          {optionSource === 'STATIC' && <Form.Item name="options" label="下拉选项" rules={[{ required: true, message: '请添加至少一个选项' }]}>
            <Select mode="tags" placeholder="输入后回车添加" tokenSeparators={[',']}/>
          </Form.Item>}
        </>}
        <Form.Item name="sort" label="排序" rules={[{ required: true, message: '请输入排序' }]}><InputNumber min={0} style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="adminVisible" label="管理端显示" valuePropName="checked"><Switch/></Form.Item>
        <Form.Item name="collectionVisible" label="员工收集表显示" valuePropName="checked">
          <Switch onChange={checked => setCollectionVisible(checked)}/>
        </Form.Item>
        <Form.Item name="collectionRequired" label="员工收集表必填" valuePropName="checked">
          <Switch disabled={!collectionVisible}/>
        </Form.Item>
        <Form.Item label="条件规则">
          <Input.TextArea rows={3} value={conditionRuleText} onChange={event => setConditionRuleText(event.target.value)}
            placeholder='可选 JSON，例如 {"field":"ownership","equals":"公司资产"}'/>
        </Form.Item>
      </Form>
    </Modal>

    <CategoryImportModal open={categoryImportOpen} onClose={() => setCategoryImportOpen(false)} onImported={() => void loadCategories()}/>
  </section>
}
