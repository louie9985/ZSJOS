import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Cascader, Drawer, Descriptions, Empty, Form, Input, InputNumber, Modal, Select, Space, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type AreaNode, type HrmInsuranceScheme, type HrmInsuranceProjectCfg } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, fmtAmount } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'

function findAreaPath(nodes: AreaNode[], targetId: number, parents: number[] = []): number[] | undefined {
  for (const node of nodes) {
    const path = [...parents, node.id]
    if (node.id === targetId) return path
    const childPath = findAreaPath(node.children || [], targetId, path)
    if (childPath) return childPath
  }
  return undefined
}

function ProjectTable({ projects, typeLabels }: { projects?: HrmInsuranceProjectCfg[]; typeLabels: Record<string, string> }) {
  const columns: ColumnsType<HrmInsuranceProjectCfg> = [
    { title: '项目', dataIndex: 'name', render: (value?: string) => value || '-' },
    { title: '类型', dataIndex: 'type', width: 100, render: (value?: number) => value != null ? (typeLabels[String(value)] || value) : '-' },
    { title: '基数', dataIndex: 'baseAmount', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '企业比例', dataIndex: 'corporateRate', width: 90, align: 'right', render: (value?: number) => value != null ? `${value}%` : '-' },
    { title: '个人比例', dataIndex: 'personalRate', width: 90, align: 'right', render: (value?: number) => value != null ? `${value}%` : '-' },
    { title: '企业金额', dataIndex: 'corporateAmount', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '个人金额', dataIndex: 'personalAmount', width: 110, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' }
  ]
  if (!projects?.length) return <Empty description="无项目"/>
  return <HrmProTable rowKey={project => project.id ?? project.name ?? '0'} size="small" columns={columns} dataSource={projects} pagination={false}/>
}

/** 社保方案管理。方案项目（社保/公积金）在表单/详情分组展示。 */
export default function HrmInsuranceSchemePage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmInsuranceScheme[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const [detail, setDetail] = useState<HrmInsuranceScheme>()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmInsuranceScheme>()
  const [saving, setSaving] = useState(false)
  const [areas, setAreas] = useState<AreaNode[]>([])
  const [form] = Form.useForm<HrmInsuranceScheme & { areaPath?: number[] }>()

  const schemeType = useDict(HRM_DICT.INSURANCE_SCHEME_TYPE)
  const projectType = useDict(HRM_DICT.INSURANCE_PROJECT_TYPE)
  const canDelete = permissions.includes('hrm:insurance:scheme:delete')
  const canCreate = permissions.includes('hrm:insurance:scheme:create')
  const canUpdate = permissions.includes('hrm:insurance:scheme:update')

  useEffect(() => { void api.areaTree().then(setAreas).catch(() => setAreas([])) }, [])

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.insurance.scheme.list()
      if (current !== version.current) return
      setItems(result)
    } catch (e) { if (current === version.current) setError(e instanceof Error ? e.message : '社保方案加载失败') }
    finally { if (current === version.current) setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])

  const openDetail = async (row: HrmInsuranceScheme) => {
    setDetail(row)
    try { setDetail(await api.hrm.insurance.scheme.get(row.id!)) }
    catch (e) { message.error(e instanceof Error ? e.message : '详情加载失败') }
  }

  const handleDelete = (row: HrmInsuranceScheme) => {
    Modal.confirm({ title: '删除社保方案', content: `确定删除「${row.name}」吗？`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.insurance.scheme.delete(row.id!); message.success('已删除'); void load() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      } })
  }

  const openForm = async (row?: HrmInsuranceScheme) => {
    if (row) {
      const current = await api.hrm.insurance.scheme.get(row.id!).catch(() => row)
      setEditing(current); form.setFieldsValue({ ...current, areaPath: current.areaId ? findAreaPath(areas, current.areaId) : undefined, projectList: current.projectList || [...(current.socialSecurityProjectList || []), ...(current.providentFundProjectList || [])] })
    } else { setEditing(undefined); form.resetFields(); form.setFieldsValue({ projectList: [{}] }) }
    setFormOpen(true)
  }

  const save = async () => {
    const values = await form.validateFields(); const areaPath = values.areaPath || []
    const payload = { ...values, id: editing?.id, areaId: areaPath[areaPath.length - 1] }
    setSaving(true)
    try { if (editing) await api.hrm.insurance.scheme.update(payload); else await api.hrm.insurance.scheme.create(payload); message.success(editing ? '已保存' : '已创建'); setFormOpen(false); load() }
    catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const columns: ColumnsType<HrmInsuranceScheme> = [
    { title: '方案名称', dataIndex: 'name', width: 180, render: (value: string) => value },
    { title: '参保地区', dataIndex: 'areaName', width: 140, render: (value?: string) => value || '-' },
    { title: '方案类型', dataIndex: 'type', width: 120, render: (value?: number) => value != null ? (schemeType.labels[String(value)] || value) : '-' },
    { title: '使用人数', dataIndex: 'useCount', width: 100, align: 'right', render: (value?: number) => value != null ? `${value} 人` : '-' },
    { title: '社保项目', width: 90, align: 'center', render: (_, row) => `${row.socialSecurityProjectList?.length || 0} 项` },
    { title: '公积金项目', width: 90, align: 'center', render: (_, row) => `${row.providentFundProjectList?.length || 0} 项` },
    { title: '操作', width: 120, align: 'center', render: (_, row) => <Space size="small">
      <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
      {canUpdate && <Button type="link" size="small" onClick={() => void openForm(row)}>编辑</Button>}
      {canDelete && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
    </Space> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
      : !items.length ? <Empty description="暂无社保方案"/>
        : <HrmProTable<HrmInsuranceScheme> advanced persistenceKey="insurance-scheme" onReload={load} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 1000 }}/>

  return <section className="workspace-page hrm-page hrm-insurance-scheme-page">
    <div className="page-heading">
      <span className="hrm-muted">社保方案由地区、方案类型和缴费项目组成</span>
      <Space>{canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openForm()}>新增方案</Button>}<Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button></Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.name || '社保方案'} width="min(960px, 96vw)" open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detail && <>
        <Descriptions className="hrm-summary" size="small" column={2} bordered items={[
          { key: 'name', label: '方案名称', children: detail.name },
          { key: 'area', label: '参保地区', children: detail.areaName || '-' },
          { key: 'household', label: '户籍类型', children: detail.householdType || '-' },
          { key: 'type', label: '方案类型', children: detail.type != null ? (schemeType.labels[String(detail.type)] || detail.type) : '-' },
          { key: 'personalIns', label: '个人社保', children: detail.personalInsuranceAmount != null ? `¥${fmtAmount(detail.personalInsuranceAmount)}` : '-' },
          { key: 'personalFund', label: '个人公积金', children: detail.personalProvidentFundAmount != null ? `¥${fmtAmount(detail.personalProvidentFundAmount)}` : '-' },
          { key: 'corpIns', label: '公司社保', children: detail.corporateInsuranceAmount != null ? `¥${fmtAmount(detail.corporateInsuranceAmount)}` : '-' },
          { key: 'corpFund', label: '公司公积金', children: detail.corporateProvidentFundAmount != null ? `¥${fmtAmount(detail.corporateProvidentFundAmount)}` : '-' }
        ]}/>
        <h4 className="hrm-drawer-subtitle">社保项目</h4>
        <ProjectTable projects={detail.socialSecurityProjectList} typeLabels={projectType.labels}/>
        <h4 className="hrm-drawer-subtitle">公积金项目</h4>
        <ProjectTable projects={detail.providentFundProjectList} typeLabels={projectType.labels}/>
      </>}
    </Drawer>
    <Modal title={editing ? '编辑社保方案' : '新增社保方案'} open={formOpen} onCancel={() => setFormOpen(false)} onOk={() => void save()} confirmLoading={saving} width="min(1040px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical">
        <Space align="start" style={{ width: '100%' }}><Form.Item name="name" label="方案名称" rules={[{ required: true }]} style={{ flex: 1 }}><Input/></Form.Item><Form.Item name="areaPath" label="参保城市" rules={[{ required: true }]} style={{ flex: 1 }}><Cascader options={areas.map(area => ({ value: area.id, label: area.name, children: area.children?.map(city => ({ value: city.id, label: city.name, children: city.children?.map(county => ({ value: county.id, label: county.name })) })) }))} changeOnSelect/></Form.Item><Form.Item name="householdType" label="户籍类型" style={{ flex: 1 }}><Input/></Form.Item><Form.Item name="type" label="方案类型" rules={[{ required: true }]} style={{ flex: 1 }}><Select loading={schemeType.loading} options={schemeType.options}/></Form.Item></Space>
        <Form.List name="projectList">{(fields, { add, remove }) => <><Space><strong>缴费项目</strong><Button size="small" icon={<PlusOutlined/>} onClick={() => add({})}>添加项目</Button></Space>{fields.map(field => <Space key={field.key} align="baseline" wrap><Form.Item {...field} name={[field.name, 'type']} rules={[{ required: true }]}><Select placeholder="项目类型" style={{ width: 140 }} loading={projectType.loading} options={projectType.options}/></Form.Item><Form.Item {...field} name={[field.name, 'name']} rules={[{ required: true }]}><Input placeholder="项目名称"/></Form.Item><Form.Item {...field} name={[field.name, 'baseAmount']}><InputNumber min={0} precision={2} placeholder="缴费基数"/></Form.Item><Form.Item {...field} name={[field.name, 'corporateRate']}><InputNumber min={0} max={100} precision={2} placeholder="企业比例"/></Form.Item><Form.Item {...field} name={[field.name, 'personalRate']}><InputNumber min={0} max={100} precision={2} placeholder="个人比例"/></Form.Item><Button type="link" danger onClick={() => remove(field.name)}>删除</Button></Space>)}</>}</Form.List>
      </Form>
    </Modal>
  </section>
}
