import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Pagination, Select, Space, message } from 'antd'
import { PlusOutlined, MinusCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmPerformanceResultLevel, type HrmPerformanceResultTemplate } from '../services/api'
import type { ColumnsType } from 'antd/es/table'

const PAGE_SIZE = 10

type ResultTemplateFormValues = { name: string; levels: HrmPerformanceResultLevel[] }

/** 考核结果设置：结果等级模板（等级/分数区间/绩效系数）。 */
export default function HrmResultTemplatePage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmPerformanceResultTemplate[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmPerformanceResultTemplate>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<ResultTemplateFormValues>()

  const canCreate = permissions.includes('hrm:performance:result-template:create')
  const canUpdate = permissions.includes('hrm:performance:result-template:update')
  const canDelete = permissions.includes('hrm:performance:result-template:delete')

  const loadPage = useCallback(async (page: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.perfCfg.resultTemplate.page({ pageNo: page, pageSize: PAGE_SIZE })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) { if (version === listVersion.current) setError(e instanceof Error ? e.message : '结果模板加载失败') }
    finally { if (version === listVersion.current) setLoading(false) }
  }, [])

  useEffect(() => { void loadPage(pageNo) }, [loadPage, pageNo])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1) }, [loadPage])

  const openForm = (row?: HrmPerformanceResultTemplate) => {
    setEditing(row)
    form.setFieldsValue(row ? { name: row.name, levels: row.levels?.map(level => ({ ...level })) } : { name: undefined, levels: [{ name: 'A', minScore: 90, maxScore: 100, coefficient: 1.2 }] })
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) await api.hrm.perfCfg.resultTemplate.update(values)
      else await api.hrm.perfCfg.resultTemplate.create(values)
      message.success(editing ? '已保存' : '已创建')
      setFormOpen(false); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const handleDelete = (row: HrmPerformanceResultTemplate) => {
    Modal.confirm({ title: '删除结果模板', content: `确定删除「${row.name}」吗？`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.perfCfg.resultTemplate.delete(row.id!); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      } })
  }

  const columns: ColumnsType<HrmPerformanceResultTemplate> = [
    { title: '模板名称', dataIndex: 'name', width: 180, render: (value: string) => value },
    { title: '等级数', width: 90, align: 'center', render: (_, row) => `${row.levels?.length || 0} 级` },
    { title: '等级划分', width: 420, render: (_, row) => <Space size={[4, 4]} wrap>
      {(row.levels || []).map((level, i) => <span key={i} className="hrm-level-chip">
        {level.name}（{level.minScore}-{level.maxScore}，系数 {level.coefficient}）
      </span>)}
    </Space> },
    { title: '创建人', dataIndex: 'creatorName', width: 110, render: (value?: string) => value || '-' },
    { title: '操作', width: 150, align: 'center', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => openForm(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无结果模板"/>
        : <>
          <HrmProTable<HrmPerformanceResultTemplate> advanced persistenceKey="performance-result-template" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 1000 }}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-result-template-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => openForm()}>新增模板</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title={editing ? '编辑结果模板' : '新增结果模板'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(840px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input placeholder="如 标准五级制"/>
        </Form.Item>
        <Form.Item label="结果等级">
          <Form.List name="levels">
            {(fields, { add, remove }) => <>
              {fields.map(({ key, name, ...rest }) => (
                <Space key={key} align="start">
                  <Form.Item {...rest} name={[name, 'name']} rules={[{ required: true, message: '等级' }]} label="等级">
                    <Input placeholder="如 A" style={{ width: 80 }}/>
                  </Form.Item>
                  <Form.Item {...rest} name={[name, 'minScore']} rules={[{ required: true, message: '最低分' }]} label="最低分">
                    <InputNumber min={0} max={100} style={{ width: 90 }}/>
                  </Form.Item>
                  <Form.Item {...rest} name={[name, 'maxScore']} rules={[{ required: true, message: '最高分' }]} label="最高分">
                    <InputNumber min={0} max={100} style={{ width: 90 }}/>
                  </Form.Item>
                  <Form.Item {...rest} name={[name, 'coefficient']} rules={[{ required: true, message: '系数' }]} label="绩效系数">
                    <InputNumber min={0} step={0.1} style={{ width: 90 }}/>
                  </Form.Item>
                  <Button type="link" danger icon={<MinusCircleOutlined/>} onClick={() => remove(name)}>删除</Button>
                </Space>
              ))}
              <Button type="dashed" block icon={<PlusOutlined/>} onClick={() => add({ name: '', minScore: 0, maxScore: 0, coefficient: 1 })}>添加等级</Button>
            </>}
          </Form.List>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
