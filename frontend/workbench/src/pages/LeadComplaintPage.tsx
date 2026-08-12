import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Segmented, Space, Spin, Table, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type LeadComplaint } from '../services/api'
import { formatTimestamp } from '../services/time'

export default function LeadComplaintPage() {
  const [status, setStatus] = useState<'pending' | 'handled'>('pending')
  const [items, setItems] = useState<LeadComplaint[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [current, setCurrent] = useState<LeadComplaint>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{ result: 'founded' | 'unfounded'; opinion: string }>()
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setItems((await api.leadComplaintPage(status)).list) }
    catch (cause) { setItems([]); setError(cause instanceof Error ? cause.message : '投诉队列加载失败') }
    finally { setLoading(false) }
  }, [status])
  useEffect(() => { void load() }, [load])
  const decide = async () => {
    if (!current) return
    const values = await form.validateFields(); setSaving(true)
    try { await api.decideLeadComplaint(current.id, values.result, values.opinion.trim(), []); message.success('投诉结论已提交'); setCurrent(undefined); form.resetFields(); await load() }
    catch (cause) { message.error(cause instanceof Error ? cause.message : '投诉处理失败'); await load() }
    finally { setSaving(false) }
  }
  return <section className="workspace-page">
    <div className="workspace-page-heading"><div><Typography.Title level={3}>销售投诉处理</Typography.Title><Typography.Text type="secondary">公共队列提交结论时加锁，首位处理人成功</Typography.Text></div><Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button></div>
    <Segmented value={status} onChange={value => setStatus(value as typeof status)} options={[{ label: '待处理', value: 'pending' }, { label: '已处理', value: 'handled' }]}/>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} 
    <Spin spinning={loading}><Table rowKey="id" dataSource={items} pagination={false} locale={{ emptyText: <Empty description="暂无投诉"/> }} scroll={{ x: 900 }} columns={[
      { title: '投诉', dataIndex: 'id', width: 90, render: id => `#${id}` }, { title: '客资', dataIndex: 'leadId', width: 90, render: id => `#${id}` },
      { title: '投诉原因', dataIndex: 'reason' }, { title: '提交时间', dataIndex: 'createTime', width: 180, render: value => formatTimestamp(value) },
      { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={value === 'pending' ? 'processing' : 'success'}>{value === 'pending' ? '待处理' : '已处理'}</Tag> },
      { title: '结论', dataIndex: 'result', width: 100, render: value => value === 'founded' ? '成立' : value === 'unfounded' ? '不成立' : '-' },
      { title: '操作', width: 100, fixed: 'right' as const, render: (_, row) => row.status === 'pending' ? <Button size="small" type="primary" onClick={() => setCurrent(row)}>处理</Button> : '-' }
    ]}/></Spin>
    <Modal title="处理销售投诉" open={Boolean(current)} confirmLoading={saving} onCancel={() => setCurrent(undefined)} onOk={() => void decide()}>
      <Form form={form} layout="vertical"><Form.Item name="result" label="处理结论" rules={[{ required: true }]}><Segmented block options={[{ label: '成立', value: 'founded' }, { label: '不成立', value: 'unfounded' }]}/></Form.Item><Form.Item name="opinion" label="处理意见" rules={[{ required: true }, { max: 1000 }]}><Input.TextArea rows={5} showCount maxLength={1000}/></Form.Item></Form>
    </Modal>
  </section>
}
