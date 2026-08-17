import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Image, Skeleton, Space, Tag, Timeline, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type LeadComplaint, type LeadComplaintEvidence } from '../services/api'
import { formatTimestamp } from '../services/time'

function Evidence({ items }: { items?: LeadComplaintEvidence[] }) {
  if (!items?.length) return null
  return <Image.PreviewGroup><div className="appeal-evidence-grid">{items.map(item =>
    item.contentType === 'application/pdf'
      ? <Button key={item.infraFileId} href={item.fileUrl} target="_blank">{item.originalName || '查看附件'}</Button>
      : <Image key={item.infraFileId} src={item.fileUrl} alt={item.originalName || '投诉附件'}/>
  )}</div></Image.PreviewGroup>
}

export default function LeadComplaintPanel({ leadId }: { leadId: number }) {
  const [items, setItems] = useState<LeadComplaint[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setItems(await api.leadComplaints(leadId)) }
    catch (cause) { setItems([]); setError(cause instanceof Error ? cause.message : '投诉记录加载失败') }
    finally { setLoading(false) }
  }, [leadId])
  useEffect(() => { void load() }, [load])

  if (loading) return <Skeleton active paragraph={{ rows: 6 }}/>
  if (error) return <Alert type="error" showIcon message={error} action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void load()}>重试</Button>}/>
  if (!items.length) return <Empty description="暂无投诉记录"/>
  return <Timeline items={items.map(item => ({
    color: item.status === 'pending' ? 'blue' : item.result === 'founded' ? 'red' : 'green',
    children: <section className="appeal-timeline-item">
      <Space wrap>
        <Typography.Text strong>投诉 #{item.id}</Typography.Text>
        <Tag color={item.status === 'pending' ? 'processing' : 'success'}>{item.status === 'pending' ? '待处理' : '已处理'}</Tag>
        {item.result && <Tag color={item.result === 'founded' ? 'error' : 'default'}>{item.result === 'founded' ? '投诉成立' : '投诉不成立'}</Tag>}
      </Space>
      <Typography.Paragraph type="secondary">投诉人：{item.complainantUserName || '外部提交人'} · 被投诉销售：{item.salesUserName || '未记录'} · {formatTimestamp(item.createTime)}</Typography.Paragraph>
      <Typography.Text type="secondary">投诉事实与诉求</Typography.Text>
      <Typography.Paragraph>{item.reason}</Typography.Paragraph>
      <Evidence items={item.evidence}/>
      {item.status === 'handled' && <>
        <Typography.Text type="secondary">处理意见</Typography.Text>
        <Typography.Paragraph>{item.handlerOpinion || '-'}</Typography.Paragraph>
        <Evidence items={item.handlerEvidence}/>
        <Typography.Text type="secondary">处理人：{item.handlerUserName || '未记录'} · {formatTimestamp(item.handledAt)}</Typography.Text>
      </>}
    </section>
  }))}/>
}
