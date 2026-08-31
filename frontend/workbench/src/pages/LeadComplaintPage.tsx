import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Avatar, Button, Drawer, Empty, Form, Image, Input, Modal, Pagination, Segmented, Space, Spin, Tag, Typography, message } from 'antd'
import { CheckOutlined } from '@ant-design/icons'
import DetailFieldGrid from '../components/DetailFieldGrid'
import { api, type LeadComplaint } from '../services/api'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 20
const RESULT_LABELS = { founded: '成立', unfounded: '不成立' } as const

export default function LeadComplaintPage() {
  const [status, setStatus] = useState<'pending' | 'handled'>('pending')
  const [items, setItems] = useState<LeadComplaint[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [selectedId, setSelectedId] = useState<number>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [current, setCurrent] = useState<LeadComplaint>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{ result: 'founded' | 'unfounded'; opinion: string }>()

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await api.leadComplaintPage({ status, pageNo: page, pageSize: PAGE_SIZE })
      setItems(result.list)
      setTotal(result.total)
      setSelectedId(existing => existing && result.list.some(item => item.id === existing) ? existing : result.list[0]?.id)
    } catch (cause) {
      setItems([])
      setTotal(0)
      setSelectedId(undefined)
      setError(cause instanceof Error ? cause.message : '投诉队列加载失败')
    } finally {
      setLoading(false)
    }
  }, [page, status])

  useEffect(() => { void load() }, [load])
  const selected = useMemo(() => items.find(item => item.id === selectedId), [items, selectedId])

  const decide = async () => {
    if (!current) return
    const values = await form.validateFields()
    setSaving(true)
    try {
      await api.decideLeadComplaint(current.id, values.result, values.opinion.trim(), [])
      message.success('投诉结论已提交')
      setCurrent(undefined)
      form.resetFields()
      await load()
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '投诉处理失败')
      await load()
    } finally {
      setSaving(false)
    }
  }

  const detailContent = selected ? <div className="business-inbox-detail">
    <header className="business-inbox-detail-hero">
      <div className="business-inbox-detail-heading">
        <Avatar>{(selected.complainantUserName || '投').slice(0, 1)}</Avatar>
        <div><Typography.Title level={4}>{selected.leadNo}</Typography.Title><Typography.Text type="secondary">销售投诉</Typography.Text></div>
      </div>
      <Space wrap className="business-inbox-detail-actions">
        <Tag color={selected.status === 'pending' ? 'processing' : 'success'}>{selected.status === 'pending' ? '待处理' : '已处理'}</Tag>
        {selected.status === 'pending' && <Button type="primary" icon={<CheckOutlined/>} onClick={() => { form.resetFields(); setCurrent(selected) }}>处理</Button>}
      </Space>
    </header>
    <section className="business-inbox-card">
      <Typography.Title level={5}>投诉信息</Typography.Title>
      <DetailFieldGrid items={[
        { key: 'leadNo', label: '客资编号', value: selected.leadNo },
        { key: 'complainant', label: '投诉人', value: selected.complainantUserName },
        { key: 'sales', label: '被投诉销售', value: selected.salesUserName },
        { key: 'created', label: '提交时间', value: formatTimestamp(selected.createTime) },
        { key: 'reason', label: '投诉原因', value: selected.reason, span: 2 }
      ]}/>
    </section>
    {selected.evidence?.length ? <section className="business-inbox-card">
      <Typography.Title level={5}>投诉凭证</Typography.Title>
      <Image.PreviewGroup><Space wrap>{selected.evidence.map(file => <Image key={file.infraFileId} width={112} height={84} src={file.fileUrl} alt={file.originalName || '投诉凭证'} />)}</Space></Image.PreviewGroup>
    </section> : null}
    {selected.status === 'handled' && <section className="business-inbox-card">
      <Typography.Title level={5}>处理结果</Typography.Title>
      <DetailFieldGrid items={[
        { key: 'result', label: '处理结论', value: selected.result ? RESULT_LABELS[selected.result] : undefined },
        { key: 'handler', label: '处理人', value: selected.handlerUserName },
        { key: 'handledAt', label: '处理时间', value: formatTimestamp(selected.handledAt) },
        { key: 'opinion', label: '处理意见', value: selected.handlerOpinion, span: 2 }
      ]}/>
    </section>}
  </div> : <Empty description="从左侧选择一条投诉"/>

  return <section className="workspace-page business-inbox-page lead-complaint-page">
    <header className="business-inbox-scope-bar"><div className="business-inbox-scope-row"><Segmented value={status} onChange={value => { setStatus(value as typeof status); setPage(1); setDrawerOpen(false) }} options={[{ label: '待处理', value: 'pending' }, { label: '已处理', value: 'handled' }]}/></div></header>
    <div className="business-inbox-layout">
      <aside className="business-inbox-list-pane">
        {error && <Alert className="business-inbox-error" type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
        <div className="business-inbox-scroll">
          {loading ? <Spin/> : !items.length && !error ? <Empty description="暂无投诉"/> : items.map(item => <button key={item.id} type="button" className={`business-inbox-item${item.id === selectedId ? ' active' : ''}`} onClick={() => { setSelectedId(item.id); if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>
            <div className="business-inbox-item-main"><Avatar>{(item.complainantUserName || '投').slice(0, 1)}</Avatar><div className="business-inbox-item-copy"><div className="business-inbox-item-title"><strong>{item.leadNo}</strong><Tag color={item.status === 'pending' ? 'processing' : 'success'}>{item.status === 'pending' ? '待处理' : '已处理'}</Tag></div><span>{item.complainantUserName || '未知投诉人'} → {item.salesUserName || '未知销售'}</span><span>{item.reason}</span></div></div>
            <div className="business-inbox-item-meta"><span>{formatTimestamp(item.createTime)}</span></div>
          </button>)}
        </div>
        {total > PAGE_SIZE && <div className="business-inbox-pagination"><Pagination simple current={page} pageSize={PAGE_SIZE} total={total} onChange={setPage}/></div>}
      </aside>
      <main className="business-inbox-detail-pane">{detailContent}</main>
    </div>
    <Drawer className="business-inbox-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="投诉详情" width="100%">{detailContent}</Drawer>
    <Modal title="处理销售投诉" open={Boolean(current)} confirmLoading={saving} onCancel={() => setCurrent(undefined)} onOk={() => void decide()}>
      <Form form={form} layout="vertical"><Form.Item name="result" label="处理结论" rules={[{ required: true }]}><Segmented block options={[{ label: '成立', value: 'founded' }, { label: '不成立', value: 'unfounded' }]}/></Form.Item><Form.Item name="opinion" label="处理意见" rules={[{ required: true }, { max: 1000 }]}><Input.TextArea rows={5} showCount maxLength={1000}/></Form.Item></Form>
    </Modal>
  </section>
}
