import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Image, Modal, Spin, Tag, Typography } from 'antd'
import { ArrowRightOutlined, FilePdfOutlined, FileUnknownOutlined } from '@ant-design/icons'
import { api, type LeadFlowAttachment, type LeadFlowHistory } from '../services/api'
import { formatTimestamp } from '../services/time'

function ValueOrDash({ value }: { value?: string }) {
  return <span className="lead-flow-history-value">{value || '-'}</span>
}

function TransitionValue({ before, after }: { before?: string; after?: string }) {
  if (!before && !after) return <span className="lead-flow-history-value">-</span>
  return <span className="lead-flow-history-transition">
    <span>{before || '-'}</span>
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
    return <Image className="lead-flow-history-attachment-image" src={attachment.previewUrl}
      preview={{ mask: '预览' }} alt={name}/>
  }
  return <>
    <Button size="small" icon={<FilePdfOutlined/>} onClick={() => setPdfOpen(true)}>{name}</Button>
    <Modal title={name} open={pdfOpen} onCancel={() => setPdfOpen(false)} footer={null} width="min(960px, 94vw)"
      destroyOnHidden>
      <iframe className="lead-flow-history-pdf-preview" src={`${attachment.previewUrl}#toolbar=0&navpanes=0`} title={name}/>
    </Modal>
  </>
}

function FlowItem({ item }: { item: LeadFlowHistory }) {
  return <div className="lead-flow-history-node">
    <div className="lead-flow-history-marker" aria-hidden><span/></div>
    <div className="lead-flow-history-node-content">
      <time className="lead-flow-history-time">{formatTimestamp(item.occurredAt)}</time>
      <article className="lead-flow-history-item">
        <header className="lead-flow-history-item-header">
          <div className="lead-flow-history-title-row">
            <Typography.Text strong>{item.flowNode}</Typography.Text>
            <Tag>{item.businessObject || '客资'}</Tag>
          </div>
          <div className="lead-flow-history-meta">
            <span>{item.source || '-'}</span>
            <span className="lead-flow-history-meta-separator" aria-hidden>·</span>
            <span>{item.operator || '系统'}</span>
          </div>
        </header>
        <div className="lead-flow-history-grid">
          <div className="lead-flow-history-field">
            <span>原归属销售</span><ValueOrDash value={item.fromOwner}/>
          </div>
          <div className="lead-flow-history-field">
            <span>新归属销售</span><ValueOrDash value={item.toOwner}/>
          </div>
          <div className="lead-flow-history-field">
            <span>客资状态变化</span><TransitionValue before={item.leadStatusBefore} after={item.leadStatusAfter}/>
          </div>
          <div className="lead-flow-history-field">
            <span>分配状态变化</span><TransitionValue before={item.assignmentStatusBefore} after={item.assignmentStatusAfter}/>
          </div>
        </div>
        <div className="lead-flow-history-supporting">
          <div className="lead-flow-history-note">
            <span>原因</span>
            <Typography.Paragraph ellipsis={{ rows: 2, expandable: 'collapsible' }}>{item.reason || '-'}</Typography.Paragraph>
          </div>
          <div className="lead-flow-history-note">
            <span>备注</span>
            <Typography.Paragraph ellipsis={{ rows: 2, expandable: 'collapsible' }}>{item.remark || '-'}</Typography.Paragraph>
          </div>
          <div className="lead-flow-history-attachment-row">
            <span>附件</span>
            {item.attachments.length > 0 ? <div className="lead-flow-history-attachments">
              {item.attachments.map((attachment, index) =>
                <AttachmentPreview key={`${attachment.infraFileId || 'file'}-${index}`} attachment={attachment}/>) }
            </div> : <span className="lead-flow-history-empty">无附件</span>}
          </div>
        </div>
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
  if (panelState === 'loading') return <div className="lead-flow-history-loading"><Spin/> 加载流转记录</div>
  if (panelState === 'error') return <Alert type="error" showIcon message="流转记录加载失败" description={error}
    action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
  if (panelState === 'empty') return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无流转记录"/>
  return <div className="lead-flow-history-timeline" aria-label="客资流转记录">
    {items.map(item => <FlowItem key={item.id} item={item}/>) }
  </div>
}
