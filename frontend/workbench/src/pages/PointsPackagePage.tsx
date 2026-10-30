import { useEffect, useState } from 'react'
import { Alert, Button, Form, Input, InputNumber, Modal, Popconfirm, Result, Space, Switch, Typography, message } from 'antd'
import BusinessTable from '../components/BusinessTable'
import { pointsApi, type PointsPackage } from '../services/pointsRedemptionApi'
export default function PointsPackagePage({ permissions }: { permissions: string[] }) {
  const allowed = permissions.includes('*:*:*') || permissions.includes('zsjos:points-redemption:package')
  const canSave = permissions.includes('*:*:*') || permissions.includes('zsjos:points-redemption:package-save')
  const [rows, setRows] = useState<PointsPackage[]>([]), [loading, setLoading] = useState(false), [error, setError] = useState('')
  const canDelete = permissions.includes('*:*:*') || permissions.includes('zsjos:points-redemption:package-delete')
  const [search, setSearch] = useState('')
  const [deleting, setDeleting] = useState<number>()
  const [open, setOpen] = useState(false), [saving, setSaving] = useState(false)
  const [form] = Form.useForm<PointsPackage>()
  const load = async () => { setLoading(true); setError(''); try { setRows(await pointsApi.packages(true)) } catch (e) { setError(e instanceof Error ? e.message : '套餐加载失败') } finally { setLoading(false) } }
  useEffect(() => { if (allowed) void load() }, [allowed])
  const edit = (row?: PointsPackage) => { form.resetFields(); form.setFieldsValue(row || { enabled: true }); setOpen(true) }
  const save = async () => { try { const values = await form.validateFields(); setSaving(true); await pointsApi.savePackage({ ...values, validDays: values.validDays || null }); setOpen(false); message.success('套餐保存成功'); await load() }
    catch (e) { if (e instanceof Error) message.error(e.message) } finally { setSaving(false) } }
  const remove = async (row: PointsPackage) => { setDeleting(row.id); try { await pointsApi.deletePackage(row.id); message.success('套餐已删除'); await load() } catch (e) { message.error(e instanceof Error ? e.message : '删除失败') } finally { setDeleting(undefined) } }
  if (!allowed) return <Result status="403" title="无套餐管理权限" />
  return <section className="workspace-page"><div className="page-heading"><Typography.Title level={4}>积分套餐</Typography.Title><Space>{canSave && <Button type="primary" onClick={() => edit()}>新增套餐</Button>}</Space></div>
    <Typography.Paragraph type="secondary">每次申请生成一个兑换码。套餐修改只影响新申请，已提交申请保留原积分和有效期。</Typography.Paragraph>
    {error && <Alert type="error" message={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    <BusinessTable tableKey="points-packages" columnMode="native" rowKey="id" loading={loading} onReload={load} filters={<Input.Search aria-label="搜索套餐名称" placeholder="搜索套餐名称" allowClear value={search} onChange={e => setSearch(e.target.value)} />} dataSource={rows.filter(row => row.name.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()))} pagination={{ defaultPageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100], showTotal: total => `共 ${total} 条` }} columns={[
      { title: '套餐名称', dataIndex: 'name', sorter: (a: PointsPackage, b: PointsPackage) => a.name.localeCompare(b.name, 'zh-CN') }, { title: '积分', dataIndex: 'creditsAmount', sorter: (a: PointsPackage, b: PointsPackage) => a.creditsAmount - b.creditsAmount },
      { title: '有效期', key: 'validDays', sorter: (a: PointsPackage, b: PointsPackage) => (a.validDays ?? Number.MAX_SAFE_INTEGER) - (b.validDays ?? Number.MAX_SAFE_INTEGER), render: (_, r: PointsPackage) => r.validDays ? `${r.validDays}天` : '永久有效' },
      { title: '状态', key: 'enabled', filters: [{ text: '启用', value: true }, { text: '停用', value: false }], onFilter: (value, row: PointsPackage) => row.enabled === value, sorter: (a: PointsPackage, b: PointsPackage) => Number(a.enabled) - Number(b.enabled), render: (_, r: PointsPackage) => r.enabled ? '启用' : '停用' },
      { title: '操作', key: 'action', render: (_, r: PointsPackage) => <Space>{canSave && <Button type="link" onClick={() => edit(r)}>编辑</Button>}{canDelete && <Popconfirm title={`删除套餐“${r.name}”？`} description="历史申请保留原套餐快照。" onConfirm={() => remove(r)} okText="删除" cancelText="取消"><Button danger type="link" loading={deleting === r.id} disabled={deleting !== undefined}>删除</Button></Popconfirm>}</Space> }
    ]} />
    <Modal title="积分套餐" open={open} onCancel={() => setOpen(false)} onOk={() => void save()} confirmLoading={saving}>
      <Form form={form} layout="vertical"><Form.Item name="id" hidden><Input /></Form.Item>
        <Form.Item name="name" label="套餐名称" rules={[{ required: true, whitespace: true }]}><Input maxLength={100} /></Form.Item>
        <Form.Item name="creditsAmount" label="兑换积分" rules={[{ required: true }]}><InputNumber min={1} max={1000000} precision={0} /></Form.Item>
        <Form.Item name="validDays" label="有效天数" extra="留空表示永久有效，从首次准备发码时起计算。"><InputNumber min={1} max={36500} precision={0} /></Form.Item>
        <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
      </Form>
    </Modal>
  </section>
}
