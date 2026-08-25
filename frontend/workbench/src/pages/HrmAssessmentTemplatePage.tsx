import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Pagination, Space, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmAssessmentDimension, type HrmAssessmentQuota, type HrmAssessmentTemplate } from '../services/api'
import type { ColumnsType } from 'antd/es/table'
import PerformanceAssessmentConfigFields from '../components/PerformanceAssessmentConfigFields'

const PAGE_SIZE = 10

type TemplateFormValues = {
  id?: number; name: string; illustrate?: string; scoreCalculation: number; upperLimitType: number
  upperLimitScore: number; dimensions: Array<HrmAssessmentDimension & { quotas: HrmAssessmentQuota[] }>
}

/** 考核指标模板：维度 → 指标两级结构，为绩效计划提供考核配置。 */
export default function HrmAssessmentTemplatePage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmAssessmentTemplate[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmAssessmentTemplate>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<TemplateFormValues>()

  const canCreate = permissions.includes('hrm:performance:assessment-template:create')
  const canUpdate = permissions.includes('hrm:performance:assessment-template:update')
  const canDelete = permissions.includes('hrm:performance:assessment-template:delete')

  const loadPage = useCallback(async (page: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.perfCfg.assessmentTemplate.page({ pageNo: page, pageSize: PAGE_SIZE })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) { if (version === listVersion.current) setError(e instanceof Error ? e.message : '考核模板加载失败') }
    finally { if (version === listVersion.current) setLoading(false) }
  }, [])

  useEffect(() => { void loadPage(pageNo) }, [loadPage, pageNo])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1) }, [loadPage])

  const openForm = async (row?: HrmAssessmentTemplate) => {
    try {
      const detail = row?.id ? await api.hrm.perfCfg.assessmentTemplate.get(row.id) : undefined
      setEditing(detail)
      form.setFieldsValue(detail ? {
        id: detail.id, name: detail.name, illustrate: detail.illustrate,
        scoreCalculation: detail.scoreCalculation || 1, upperLimitType: detail.upperLimitType || 1,
        upperLimitScore: detail.upperLimitScore ?? 100,
        dimensions: (detail.dimensions || []).map(dimension => ({ ...dimension, quotas: (dimension.quotas || []).map(quota => ({ ...quota })) }))
      } : {
        name: '', illustrate: '', scoreCalculation: 1, upperLimitType: 1, upperLimitScore: 100,
        dimensions: [{ name: '', quotaType: 1, weight: 100, allowEdit: false, remark: '', quotas: [{ name: '', illustrate: '', standard: '', weight: 100, scoreType: 1 }] }]
      })
      setFormOpen(true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '考核模板详情加载失败')
    }
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) await api.hrm.perfCfg.assessmentTemplate.update({ ...values, id: editing.id })
      else await api.hrm.perfCfg.assessmentTemplate.create(values)
      message.success(editing ? '已保存' : '已创建')
      setFormOpen(false); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const handleDelete = (row: HrmAssessmentTemplate) => {
    Modal.confirm({ title: '删除考核模板', content: `确定删除「${row.name}」吗？`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.perfCfg.assessmentTemplate.delete(row.id!); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      } })
  }

  const columns: ColumnsType<HrmAssessmentTemplate> = [
    { title: '模板名称', dataIndex: 'name', width: 180, render: (value: string) => value },
    { title: '计分方式', dataIndex: 'scoreCalculation', width: 110, render: (value?: number) => value === 1 ? '加权计算' : '-' },
    { title: '维度数', width: 90, align: 'center', render: (_, row) => `${row.dimensions?.length || 0} 个` },
    { title: '单项上限', dataIndex: 'upperLimitScore', width: 100, align: 'right', render: (value?: number) => value != null ? String(value) : '-' },
    { title: '操作', width: 150, align: 'center', render: (_, row) => <Space size="small">
      {canUpdate && <Button type="link" size="small" onClick={() => void openForm(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无考核模板"/>
        : <>
          <HrmProTable<HrmAssessmentTemplate> advanced persistenceKey="assessment-template" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 900 }}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-assessment-template-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openForm()}>新增模板</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title={editing ? '编辑考核模板' : '新增考核模板'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(960px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input placeholder="如 季度绩效模板"/>
        </Form.Item>
        <Form.Item name="illustrate" label="模板说明">
          <Input.TextArea rows={2} maxLength={200}/>
        </Form.Item>
        <PerformanceAssessmentConfigFields/>
      </Form>
    </Modal>
  </section>
}
