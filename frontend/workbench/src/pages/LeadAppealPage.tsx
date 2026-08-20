import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Avatar, Button, Drawer, Empty, Form, Image, Input, Modal, Segmented, Skeleton, Space, Spin, Tag, Typography, message } from 'antd'
import { api, type AdvancedFilterGroup, type LeadAppeal, type LeadAppealEvidence } from '../services/api'
import { formatTimestamp } from '../services/time'
import LeadAppealEvidenceUpload from '../components/LeadAppealEvidenceUpload'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import { invalidReasonSnapshotLabel } from '../services/leadManagement'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'
import DetailFieldGrid from '../components/DetailFieldGrid'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'

const statusLabel: Record<string, string> = { sales_manager_reviewing: '销售主管复核中', quality_reviewing: '质控复核中', chairman_reviewing: '董事长终审中', overturned: '已改判有效', upheld: '维持无效', withdrawn: '已撤回' }
const stageLabel: Record<string, string> = { sales_manager: '销售主管', quality: '质控部门', chairman: '董事长' }

function Detail({ item, onDecision }: { item: LeadAppeal; onDecision: (type: 'overturn' | 'uphold') => void }) {
  const evidence = (items: LeadAppealEvidence[]) => items.length ? <Image.PreviewGroup><div className="appeal-evidence-grid">{items.map(file => <Image key={file.infraFileId} src={file.fileUrl} alt={file.originalName}/>)}</div></Image.PreviewGroup> : null
  return <article className="message-inbox-detail">
    <div className="message-detail-hero"><Avatar>{item.leadName.slice(0, 1)}</Avatar><div className="message-detail-heading"><Typography.Title level={4}>{item.leadNo} · {item.leadName}</Typography.Title><Space wrap><Tag>第 {item.roundNo} 次申诉</Tag><Tag color="processing">{stageLabel[item.reviewStage]}</Tag><Tag color={item.status === 'upheld' ? 'error' : 'processing'}>{statusLabel[item.status]}</Tag></Space></div></div>
    <div className="message-detail-section"><Typography.Text type="secondary">申诉理由</Typography.Text><Typography.Paragraph>{item.reason}</Typography.Paragraph>{evidence(item.evidence)}</div>
    <div className="message-detail-section"><Typography.Text type="secondary">原无效结论</Typography.Text><Typography.Paragraph>{[invalidReasonSnapshotLabel(item.invalidReasonSnapshot), item.invalidDescriptionSnapshot].filter(Boolean).join('：') || '-'}</Typography.Paragraph>{evidence(item.invalidEvidenceSnapshot)}</div>
    {item.decisionReason && <div className="message-detail-section"><Typography.Text type="secondary">裁决意见</Typography.Text><Typography.Paragraph>{item.decisionReason}</Typography.Paragraph>{evidence(item.decisionEvidence)}</div>}
    <DetailFieldGrid className="message-detail-meta" items={[{ key: 'submittedAt', label: '提交时间', value: formatTimestamp(item.submittedAt) }, { key: 'reviewer', label: '处理人', value: item.reviewerUserName }, { key: 'decidedAt', label: '处理时间', value: formatTimestamp(item.decidedAt) }]}/>
    {item.taskId && item.status.endsWith('reviewing') && <Space className="appeal-decision-actions"><Button type="primary" onClick={() => onDecision('overturn')}>改判有效</Button><Button danger onClick={() => onDecision('uphold')}>维持无效</Button></Space>}
  </article>
}

export default function LeadAppealPage() {
  const [handled, setHandled] = useState(false); const [items, setItems] = useState<LeadAppeal[]>([]); const [selected, setSelected] = useState<LeadAppeal>(); const [loading, setLoading] = useState(true); const [loadingMore, setLoadingMore] = useState(false); const [cursor, setCursor] = useState<string>(); const [hasMore, setHasMore] = useState(true); const [error, setError] = useState(''); const [decision, setDecisionState] = useState<'overturn' | 'uphold'>(); const [reason, setReason] = useState(''); const [evidence, setEvidence] = useState<DeferredUploadItem<LeadAppealEvidence>[]>([]); const [drawerOpen, setDrawerOpen] = useState(false)
  const [keyword, setKeyword] = useState(''); const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const listScrollRef = useRef<HTMLDivElement>(null); const sentinelRef = useRef<HTMLDivElement>(null)
  const { submitting: saving, run: runDecision, resetIntent } = useSubmissionGuard()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const load = useCallback(async (append = false) => { if (append) setLoadingMore(true); else setLoading(true); setError(''); try { const result = await api.leadAppealInboxCursor(handled, { cursor: append ? cursor : undefined, limit: 20, keyword: keyword || undefined, advancedFilter }); setItems(current => append ? [...current, ...result.list.filter(item => !current.some(existing => existing.id === item.id))] : result.list); setCursor(result.nextCursor); setHasMore(result.hasMore); if (!append) setSelected(current => result.list.find(item => item.id === current?.id) || result.list[0]) } catch (e) { setError(e instanceof Error ? e.message : '申诉处理列表加载失败') } finally { setLoading(false); setLoadingMore(false) } }, [advancedFilter, cursor, handled, keyword])
  useEffect(() => { void load() }, [advancedFilter, handled, keyword])
  useEffect(() => { const node = sentinelRef.current; if (!node || !hasMore || loading || loadingMore || !cursor) return; const observer = new IntersectionObserver(entries => { if (entries[0]?.isIntersecting) void load(true) }, { root: node.parentElement, rootMargin: '160px' }); observer.observe(node); return () => observer.disconnect() }, [cursor, hasMore, load, loading, loadingMore])
  useEffect(() => {
    const node = listScrollRef.current
    if (!node) return
    const onScroll = () => { if (!loading && !loadingMore && hasMore && cursor && node.scrollHeight - node.scrollTop - node.clientHeight < 80) void load(true) }
    node.addEventListener('scroll', onScroll)
    return () => node.removeEventListener('scroll', onScroll)
  }, [cursor, hasMore, load, loading, loadingMore])
  const setDecision = (next: 'overturn' | 'uphold' | undefined) => { setConfirmOpen(false); if (next) resetIntent(); setDecisionState(next) }
  const submitDecision = async () => { setConfirmOpen(false); if (!selected?.taskId || !decision) return; const appeal = selected; const nextDecision = decision; await runDecision(async ({ idempotencyKey, complete }) => { const uploadResult = await uploadDeferredFiles(evidence, api.uploadLeadAppealImage, setEvidence); if (uploadResult.failed) { message.error('有裁决图片上传失败，请重试失败项'); return } await api.decideLeadAppeal(appeal.id, nextDecision, { taskId: appeal.taskId!, reason: reason.trim(), attachments: uploadResult.items.filter(item => item.uploaded).map(item => ({ infraFileId: item.uploaded!.infraFileId })), idempotencyKey }); complete(); message.success(nextDecision === 'overturn' ? '已改判有效' : '已维持无效'); setDecisionState(undefined); setReason(''); setEvidence([]); await load() }).catch(saveError => message.error(saveError instanceof Error ? saveError.message : '申诉裁决失败')) }
  const prepareDecision = () => { if (!selected?.taskId || !reason.trim() || !decision) { message.warning('裁决理由为必填项'); return }; setConfirmOpen(true) }
  const detail = selected ? <Detail item={selected} onDecision={setDecision}/> : <Empty description="从左侧选择一条申诉"/>
  return <section className="workspace-page business-inbox-page lead-appeal-inbox-page">
    <header className="business-inbox-scope-bar"><div className="business-inbox-scope-row"><Segmented value={handled ? 'done' : 'todo'} onChange={value => setHandled(value === 'done')} options={[{ value: 'todo', label: '待处理' }, { value: 'done', label: '已处理' }]}/></div></header>
    <div className="business-inbox-layout">
      <aside className="business-inbox-list-pane">
        <div className="business-inbox-toolbar"><AdvancedFilterToolbar scene="lead_appeal" placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/></div>
        {error && <Alert className="business-inbox-error" type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
        <div ref={listScrollRef} className="business-inbox-scroll">
          {loading ? <Skeleton active paragraph={{ rows: 8 }}/> : items.length ? items.map(item => <button type="button" key={item.id} className={`business-inbox-item${selected?.id === item.id ? ' active' : ''}`} onClick={() => { setSelected(item); if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>
            <div className="business-inbox-item-main"><Avatar>{item.leadName.slice(0, 1)}</Avatar><div className="business-inbox-item-copy"><div className="business-inbox-item-title"><strong>{item.leadName}</strong><Tag>{statusLabel[item.status]}</Tag></div><span>{item.leadNo}</span><span>第 {item.roundNo} 次 · {stageLabel[item.reviewStage]}</span><span>{item.reason}</span></div></div><div className="business-inbox-item-meta"><span>{formatTimestamp(item.submittedAt)}</span></div>
          </button>) : <Empty description="暂无申诉"/>}
          {!loading && items.length > 0 && <div ref={sentinelRef} className="business-inbox-list-state"><Typography.Text type="secondary">{loadingMore ? <><Spin size="small"/> 加载中</> : hasMore ? '继续下滑加载' : '已加载全部申诉'}</Typography.Text></div>}
        </div>
      </aside>
      <main className="business-inbox-detail-pane">{detail}</main>
    </div>
    <Drawer className="business-inbox-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="申诉详情" placement="right" width="100%">{detail}</Drawer>
    <Modal title={decision === 'overturn' ? '改判有效' : '维持无效'} open={Boolean(decision)} onCancel={() => setDecision(undefined)} footer={<Space><Button onClick={() => setDecision(undefined)}>取消</Button><IrreversiblePopconfirm action={decision === 'overturn' ? `将客资「${selected?.leadName || ''}」改判为有效` : `维持客资「${selected?.leadName || ''}」的无效结论`} danger={decision === 'uphold'} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submitDecision}><Button type="primary" danger={decision === 'uphold'} loading={saving} onClick={prepareDecision}>提交裁决</Button></IrreversiblePopconfirm></Space>}><Space direction="vertical" style={{ width: '100%' }}><Form.Item label="裁决理由" required><Input.TextArea rows={5} maxLength={1000} showCount value={reason} onChange={e => setReason(e.target.value)} placeholder="填写裁决理由"/></Form.Item><Form.Item label={`裁决附件${evidence.some(item => item.status === 'uploading') ? '（上传中）' : ''}`}><LeadAppealEvidenceUpload value={evidence} onChange={setEvidence} disabled={saving}/></Form.Item></Space></Modal>
  </section>
}
