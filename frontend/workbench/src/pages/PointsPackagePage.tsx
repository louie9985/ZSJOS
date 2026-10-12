import { useEffect, useState } from 'react'
import { Alert, Button, Form, Input, InputNumber, Modal, Result, Space, Switch, Typography, message } from 'antd'
import BusinessTable from '../components/BusinessTable'
import { pointsApi, type PointsPackage } from '../services/pointsRedemptionApi'
export default function PointsPackagePage({ permissions }: { permissions: string[] }) {
  const allowed = permissions.includes('*:*:*') || permissions.includes('zsjos:points-redemption:package')
  const canSave = permissions.includes('*:*:*') || permissions.includes('zsjos:points-redemption:package-save')
  const [rows, setRows] = useState<PointsPackage[]>([]), [loading, setLoading] = useState(false), [error, setError] = useState('')
  const [open, setOpen] = useState(false), [saving, setSaving] = useState(false)
  const [form] = Form.useForm<PointsPackage>()
  const load = async () => { setLoading(true); setError(''); try { setRows(await pointsApi.packages(true)) } catch (e) { setError(e instanceof Error ? e.message : '套餐加载失败') } finally { setLoading(false) } }
  useEffect(() => { if (allowed) void load() }, [allowed])
  const edit = (row?: PointsPackage) => { form.resetFields(); form.setFieldsValue(row || { enabled: true }); setOpen(true) }
  const save = async () => { try { const values = await form.validateFields(); setSaving(true); await pointsApi.savePackage({ ...values, validDays: values.validDays || null }); setOpen(false); await load() }
    catch (e) { if (e instanceof Error) message.error(e.message) } finally { setSaving(false) } }
  if (!allowed) return <Result status="403" title="无套餐管理权限" />
  return <section className="workspace-page"><div className="page-heading"><Typography.Title level={4}>积分套餐</Typography.Title><Space><Button onClick={() => void load()}>刷新</Button>{canSave && <Button type="primary" onClick={() => edit()}>新增套餐</Button>}</Space></div>
    <Typography.Paragraph type="secondary">每次申请生成一个兑换码。套餐修改只影响新申请，已提交申请保留原积分和有效期。</Typography.Paragraph>
    {error && <Alert type="error" message={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    <BusinessTable tableKey="points-packages" columnMode="native" rowKey="id" loading={loading} dataSource={rows} pagination={false} columns={[
      { title: '套餐名称', dataIndex: 'name' }, { title: '积分', dataIndex: 'creditsAmount' },
      { title: '有效期', render: (_, r: PointsPackage) => r.validDays ? `${r.validDays}天` : '永久有效' },
      { title: '状态', render: (_, r: PointsPackage) => r.enabled ? '启用' : '停用' },
      { title: '操作', render: (_, r: PointsPackage) => canSave && <Button type="link" onClick={() => edit(r)}>编辑</Button> }
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
