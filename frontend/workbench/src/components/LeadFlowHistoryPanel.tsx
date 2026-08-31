import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Image, Modal, Spin, Tag, Typography } from 'antd'
import { ArrowRightOutlined, FilePdfOutlined, FileUnknownOutlined } from '@ant-design/icons'
import { api, type LeadFlowAttachment, type LeadFlowHistory } from '../services/api'
import { formatTimestamp } from '../services/time'

function ChangeLine({ label, before, after }: { label: string; before?: string; after?: string }) {
  if (!before && !after) return null
  return <span className="flow-history-change">
    <span className="flow-history-change-label">{label}</span>
    <span className="flow-history-change-value">{before || '-'}</span>
    <ArrowRightOutlined aria-hidden/>
    <strong>{after || '-'}</strong>
  </span>
}

export const flowAttachmentState = (attachment: LeadFlowAttachment): 'unsupported' | 'unavailable' | 'image' | 'pdf' => {
  if (!attachment.previewable) return 'unsupported'
  if (!attachment.available || !attachment.previewUrl) return 'unavailable'
  return attachment.contentType?.startsWith('image/') ? 'image' : 'pdf'
}

export const flowPanelState = (loading: boolean, error: string, itemCount: number): 'loading' | 'error' | 'empty' | 'ready' => {
  if (loading) return 'loading'
  if (error) return 'error'
  return itemCount ? 'ready' : 'empty'
}

function AttachmentPreview({ attachment }: { attachment: LeadFlowAttachment }) {
  const [pdfOpen, setPdfOpen] = useState(false)
  const name = attachment.originalName || '未命名附件'
  const state = flowAttachmentState(attachment)
  if (state === 'unsupported') {
    return <Tag icon={<FileUnknownOutlined/>}>{name} · 暂不支持预览</Tag>
  }
  if (state === 'unavailable') {
    return <Tag icon={<FileUnknownOutlined/>}>{name} · 附件不可用</Tag>
  }
  if (state === 'image') {
    return <Image className="flow-history-attachment-image" src={attachment.previewUrl}
      preview={{ mask: '预览' }} alt={name}/>
  }
  return <>
    <Button size="small" icon={<FilePdfOutlined/>} onClick={() => setPdfOpen(true)}>{name}</Button>
    <Modal title={name} open={pdfOpen} onCancel={() => setPdfOpen(false)} footer={null} width="min(960px, 94vw)"
      destroyOnHidden>
      <iframe className="flow-history-pdf-preview" src={`${attachment.previewUrl}#toolbar=0&navpanes=0`} title={name}/>
    </Modal>
  </>
}

function FlowItem({ item }: { item: LeadFlowHistory }) {
  const hasOwnership = Boolean(item.fromOwner || item.toOwner)
  const hasChanges = Boolean(item.leadStatusBefore || item.leadStatusAfter
    || item.assignmentStatusBefore || item.assignmentStatusAfter)
  return <div className="flow-history-node">
    <div className="flow-history-marker" aria-hidden><span/></div>
    <div className="flow-history-content">
      <time className="flow-history-time">{formatTimestamp(item.occurredAt, '-', 'second')}</time>
      <article className="flow-history-card">
        <header className="flow-history-card-header">
          <div className="flow-history-title">
            <Typography.Text strong>{item.flowNode}</Typography.Text>
            <Tag>{item.businessObject || '客资'}</Tag>
            {item.source && <Tag color="blue">{item.source}</Tag>}
          </div>
          <Typography.Text type="secondary" className="flow-history-operator">
            操作人：{item.operator || '系统'}
          </Typography.Text>
        </header>

        {(hasOwnership || hasChanges) && <div className="flow-history-changes">
          {hasOwnership && <span className="flow-history-change">
            <span className="flow-history-change-label">归属销售</span>
            <span className="flow-history-change-value">{item.fromOwner || '-'}</span>
            <ArrowRightOutlined aria-hidden/>
            <strong>{item.toOwner || '-'}</strong>
          </span>}
          <ChangeLine label="客资状态" before={item.leadStatusBefore} after={item.leadStatusAfter}/>
          <ChangeLine label="分配状态" before={item.assignmentStatusBefore} after={item.assignmentStatusAfter}/>
        </div>}

        {(item.reason || item.remark) && <div className="flow-history-notes">
          {item.reason && <Typography.Paragraph className="flow-history-note" ellipsis={{ rows: 2, expandable: 'collapsible' }}>
            原因：{item.reason}
          </Typography.Paragraph>}
          {item.remark && <Typography.Paragraph className="flow-history-note" ellipsis={{ rows: 2, expandable: 'collapsible' }}>
            备注：{item.remark}
          </Typography.Paragraph>}
        </div>}

        {item.attachments.length > 0 && <div className="flow-history-attachments">
          <Typography.Text type="secondary" className="flow-history-attachment-label">附件</Typography.Text>
          <div className="flow-history-attachment-list">
              {item.attachments.map((attachment, index) =>
                <AttachmentPreview key={`${attachment.infraFileId || 'file'}-${index}`} attachment={attachment}/>) }
          </div>
        </div>}
      </article>
    </div>
  </div>
}

export default function LeadFlowHistoryPanel({ leadId }: { leadId: number }) {
  const [items, setItems] = useState<LeadFlowHistory[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError(''); setItems([])
    try { setItems(await api.leadFlowHistory(leadId)) }
    catch (loadError) { setError(loadError instanceof Error ? loadError.message : '流转记录加载失败') }
    finally { setLoading(false) }
  }, [leadId])
  useEffect(() => { void load() }, [load])

  const panelState = flowPanelState(loading, error, items.length)
  if (panelState === 'loading') return <div className="flow-history-loading"><Spin/> 加载流转记录</div>
  if (panelState === 'error') return <Alert type="error" showIcon message="流转记录加载失败" description={error}
    action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
  if (panelState === 'empty') return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无流转记录"/>
  return <section className="flow-history-panel">
    <header className="flow-history-header">
      <span className="flow-history-heading">流转记录<span>{items.length} 条</span></span>
    </header>
    <div className="flow-history-timeline" aria-label="客资流转记录">
      {items.map(item => <FlowItem key={item.id} item={item}/>) }
    </div>
  </section>
}
