import { ArrowLeftOutlined, FilterOutlined, ReloadOutlined } from '@ant-design/icons'
import { App, Button, DatePicker, Empty, Form, Input, Modal, Pagination, Select, Skeleton, Tabs, Tag, Timeline } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type ProductionTicket } from '../services/api'
import { hasPermission } from '../services/managementAccess'
import { formatTimestamp } from '../services/time'
import { TICKET_CHANGED, notifyTicketChanged } from '../services/productionTicketEvents'
import { deadlinePresentation, groupStatuses, historyLabels, ticketActionLabels, ticketGroups, ticketNextStep, ticketStatuses } from '../services/productionTicketPresentation'
import { ProductionTicketDetail } from './MediaFeaturePage'
import ResourceLinkInput from '../components/ResourceLinkInput'
import type { Dayjs } from 'dayjs'
import '../styles/pages/production-tickets.css'

type View = 'pending' | 'mine' | 'pool'
const errorText = (cause: unknown) => cause instanceof Error ? cause.message : '加载失败，请重试'
const identity = (ticket: ProductionTicket) => [ticket.dispatchContext?.studentName, ticket.dispatchContext?.accountName || ticket.dispatchContext?.accountNo].filter(Boolean).join(' · ') || '未关联账号'
const stages = ['接单', '制作', '核对', '完成']
function FlowCard({ ticket }: { ticket: ProductionTicket }) {
  const index = ['completed'].includes(ticket.status) ? 3 : ['submitted', 'checking'].includes(ticket.status) ? 2 : ['accepted', 'in_production', 'rejected'].includes(ticket.status) ? 1 : 0
  return <section className="production-ticket-side-card"><h3>工单流转</h3>
    <ol className="production-ticket-stages">{stages.map((stage, i) => <li key={stage} className={i === index && !['cancelled', 'assignment_rejected'].includes(ticket.status) ? 'current' : ''}><span>{i + 1}</span>{stage}</li>)}</ol>
    <p className="production-ticket-muted">当前：{ticketStatuses[ticket.status] || ticket.status}{ticket.currentRound ? ` · 第 ${ticket.currentRound} 轮` : ''}</p>
    {ticket.timeline?.length ? <Timeline items={ticket.timeline.map((event, i) => ({ key: i, content: <div className="production-ticket-event"><strong>{historyLabels[event.operation] || event.operation}</strong><span>{event.operatorName || '处理人未记录'}{event.roundNo ? ` · 第 ${event.roundNo} 轮` : ''}</span><time>{formatTimestamp(event.operatedAt, '时间未记录', 'second')}</time>{(event.reason || event.resultRemark) && <p>{event.reason || event.resultRemark}</p>}</div> }))} /> : <p className="production-ticket-muted">暂无流转历史记录</p>}
  </section>
}

export default function ProductionTicketsPage({ permissions = [] }: { permissions?: string[] }) {
  const { message } = App.useApp()
  const [params, setParams] = useSearchParams()
  const requestedId = Number(params.get('ticketId')) || undefined
  const [view, setView] = useState<View>('mine'), [group, setGroup] = useState('todo')
  const [keywordInput, setKeywordInput] = useState(''), [keyword, setKeyword] = useState(''), [status, setStatus] = useState<string>()
  const [dates, setDates] = useState<[Dayjs | null, Dayjs | null] | null>(null), [filtersOpen, setFiltersOpen] = useState(false)
  const [page, setPage] = useState(1), [total, setTotal] = useState(0), [rows, setRows] = useState<ProductionTicket[]>([])
  const [selected, setSelected] = useState<ProductionTicket>(), [detailLoading, setDetailLoading] = useState(false), [detailError, setDetailError] = useState('')
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [revision, setRevision] = useState(0)
  const [detailOpen, setDetailOpen] = useState(Boolean(requestedId)), [now, setNow] = useState(Date.now())
  const [dialogAction, setDialogAction] = useState<string>(), [reason, setReason] = useState(''), [completionUrl, setCompletionUrl] = useState(''), [saving, setSaving] = useState(false)
  const detailTarget = useRef<number | undefined>(undefined)
  const listSequence = useRef(0), detailSequence = useRef(0), savingRef = useRef(false), clockOffset = useRef(0), poolScroll = useRef(0)
  const scrollRef = useRef<HTMLDivElement>(null), selectedRef = useRef<ProductionTicket | undefined>(undefined)
  selectedRef.current = selected
  const pageSize = view === 'pool' ? 12 : 20
  const canAccept = hasPermission(permissions, 'zsjos:production-ticket:accept')
  const canPool = hasPermission(permissions, 'zsjos:production-ticket:pool-query') || hasPermission(permissions, 'zsjos:production-ticket:claim')
  const setTicketId = useCallback((id?: number) => setParams(current => { const next = new URLSearchParams(current); if (id) next.set('ticketId', String(id)); else next.delete('ticketId'); return next }, { replace: true }), [setParams])
  const readDetail = useCallback(async (id: number) => {
    detailTarget.current = id
    const sequence = ++detailSequence.current
    setDetailLoading(true); setDetailError(''); setSelected(undefined)
    try {
      const ticket = await api.productionTicket.get(id)
      if (sequence !== detailSequence.current) return
      clockOffset.current = typeof ticket.serverNow === 'number' ? ticket.serverNow - Date.now() : 0
      setNow(Date.now() + clockOffset.current); setSelected(ticket)
    } catch (cause) { if (sequence === detailSequence.current) setDetailError(errorText(cause)) }
    finally { if (sequence === detailSequence.current) setDetailLoading(false) }
  }, [])
  useEffect(() => { if (requestedId) { setDetailOpen(true); void readDetail(requestedId) } }, [requestedId, readDetail, revision])
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now() + clockOffset.current), 30000); return () => window.clearInterval(timer) }, [])
  useEffect(() => { const refresh = () => setRevision(value => value + 1); window.addEventListener(TICKET_CHANGED, refresh); return () => window.removeEventListener(TICKET_CHANGED, refresh) }, [])
  const deadlineFrom = dates?.[0]?.startOf('day').format('YYYY-MM-DD HH:mm:ss'), deadlineTo = dates?.[1]?.endOf('day').format('YYYY-MM-DD HH:mm:ss')
  useEffect(() => {
    const sequence = ++listSequence.current
    setLoading(true); setError('')
    const query = { pageNo: page, pageSize, keyword: keyword || undefined, deadlineFrom, deadlineTo }
    const request = view === 'pool' ? api.productionTicket.poolPage(query) : api.productionTicket.page({ ...query, pendingAssignment: view === 'pending' || undefined, status: view === 'pending' ? 'pending_accept' : status, statusGroup: view === 'mine' ? group : undefined })
    request.then(result => {
      if (sequence !== listSequence.current) return
      setRows(result.list); setTotal(result.total)
      if (!result.list.length && page > 1 && result.total <= (page - 1) * pageSize) setPage(Math.max(1, Math.ceil(result.total / pageSize)))
      if (!requestedId && view !== 'pool') {
        const id = result.list.find(row => row.id === selectedRef.current?.id)?.id || result.list[0]?.id
        if (id) void readDetail(id)
        else { ++detailSequence.current; setSelected(undefined); setDetailLoading(false) }
      }
    }).catch(cause => { if (sequence === listSequence.current) { setError(errorText(cause)); setRows([]); setTotal(0) } })
      .finally(() => { if (sequence === listSequence.current) setLoading(false) })
    return () => { ++listSequence.current }
  }, [view, group, status, keyword, deadlineFrom, deadlineTo, page, pageSize, revision, readDetail])
  const clearSelection = () => { detailTarget.current = undefined; ++detailSequence.current; setSelected(undefined); setDetailError(''); setDetailLoading(false); setDetailOpen(false); setTicketId(undefined) }
  const changeView = (value: View) => { clearSelection(); setView(value); setPage(1); setStatus(undefined); setKeyword(''); setKeywordInput(''); setDates(null) }
  const filterChanged = () => { setPage(1); clearSelection() }
  const openTicket = (ticket: ProductionTicket) => { poolScroll.current = scrollRef.current?.scrollTop || 0; setDetailOpen(true); if (requestedId === ticket.id) void readDetail(ticket.id); else setTicketId(ticket.id) }
  const back = () => { setDetailOpen(false); setTicketId(undefined); requestAnimationFrame(() => { if (scrollRef.current) scrollRef.current.scrollTop = poolScroll.current }) }
  const execute = async (action: string) => {
    const ticket = selected
    if (!ticket || savingRef.current || !ticket.availableActions.includes(action)) return
    if (action === 'SUBMIT_TICKET') { try { const url = new URL(completionUrl.trim()); if (!['http:', 'https:'].includes(url.protocol)) throw new Error() } catch { message.error('请填写有效的 HTTP(S) 成品链接'); return } }
    if (['REJECT_TICKET', 'REJECT_TICKET_ASSIGNMENT'].includes(action) && !reason.trim()) { message.error('请填写原因'); return }
    savingRef.current = true; setSaving(true)
    try {
      const { id, version } = ticket
      switch (action) {
        case 'ACCEPT_TICKET': await api.productionTicket.accept(id, version); break
        case 'REJECT_TICKET_ASSIGNMENT': await api.productionTicket.rejectAssignment(id, version, reason.trim()); break
        case 'CLAIM_TICKET': await api.productionTicket.claim(id, version); break
        case 'START_TICKET': await api.productionTicket.startProduction(id, version); break
        case 'SUBMIT_TICKET': await api.productionTicket.submit(id, { version, remark: reason, completionUrl: completionUrl.trim() }); break
        case 'START_TICKET_CHECK': await api.productionTicket.startCheck(id, version); break
        case 'APPROVE_TICKET': await api.productionTicket.approve(id, version); break
        case 'REJECT_TICKET': await api.productionTicket.reject(id, version, reason.trim()); break
        case 'REACCEPT_TICKET': await api.productionTicket.reaccept(id, version); break
        default: return
      }
      message.success(`${ticketActionLabels[action]}成功`); setDialogAction(undefined)
      if (action === 'CLAIM_TICKET' || action === 'ACCEPT_TICKET') { setView('mine'); setGroup('todo'); setPage(1); setStatus(undefined); setDates(null); setKeyword(''); setKeywordInput('') }
      else if (action === 'START_TICKET') { setGroup('producing'); setStatus(undefined); setPage(1) }
      else if (action === 'SUBMIT_TICKET') { setGroup('review'); setStatus(undefined); setPage(1) }
      else if (action === 'APPROVE_TICKET') { setGroup('completed'); setStatus(undefined); setPage(1) }
      else if (action === 'REJECT_TICKET') { setGroup('todo'); setStatus(undefined); setPage(1) }
      setTicketId(id); void readDetail(id); notifyTicketChanged()
    } catch (cause) { message.error(errorText(cause)); void readDetail(ticket.id); setRevision(value => value + 1) }
    finally { savingRef.current = false; setSaving(false) }
  }
  const action = (value: string) => { if (['SUBMIT_TICKET', 'REJECT_TICKET', 'REJECT_TICKET_ASSIGNMENT', 'APPROVE_TICKET'].includes(value)) { setDialogAction(value); setReason(''); setCompletionUrl('') } else void execute(value) }
  const deadline = selected && deadlinePresentation(selected.deadlineAt, selected.status, now)
  const returnReason = selected?.status === 'rejected' ? [...(selected.timeline || [])].reverse().find(event => event.operation === 'production-return')?.reason : undefined
  const detail = <main className="media-feature-detail-pane production-ticket-detail-pane">
    <Button className={`production-ticket-back ${view === 'pool' ? 'pool-back' : ''}`} icon={<ArrowLeftOutlined />} onClick={back}>返回{view === 'pool' ? '抢单池' : '列表'}</Button>
    {detailLoading ? <Skeleton active paragraph={{ rows: 10 }} /> : detailError ? <div role="status"><p>{detailError}</p><Button onClick={() => detailTarget.current && void readDetail(detailTarget.current)}>重试</Button></div> : selected && deadline ? <>
      <header className="production-ticket-heading"><div><h2>{selected.ticketNo}</h2><span>{selected.sceneName} · {identity(selected)}</span></div><Tag color={selected.status === 'rejected' ? 'error' : 'blue'}>{ticketStatuses[selected.status] || selected.status}</Tag></header>
      <div className="production-ticket-grid"><div className="production-ticket-main"><ProductionTicketDetail ticket={selected} /></div><aside className="production-ticket-aside">
        <section className={`production-ticket-side-card production-ticket-deadline tone-${deadline.tone}`}><h3>交付截止</h3><strong>{deadline.text}</strong><time>{deadline.date}</time>{selected.deadlineAt && <span>北京时间</span>}</section>
        <section className="production-ticket-side-card production-ticket-actions"><h3>{ticketStatuses[selected.status] || selected.status}</h3><p>{ticketNextStep[selected.status] || '查看工单详情与流转记录。'}</p><span className="production-ticket-muted">处理人：{selected.assigneeName || '未记录'}</span>{returnReason && <div className="production-ticket-rework"><strong>返工原因</strong><p>{returnReason}</p></div>}
          {selected.availableActions.filter(value => ticketActionLabels[value]).map(value => <Button key={value} block type={value.startsWith('REJECT_') ? 'default' : 'primary'} danger={value.startsWith('REJECT_')} loading={saving} onClick={() => action(value)}>{ticketActionLabels[value]}</Button>)}
          {!selected.availableActions.length && <span className="production-ticket-muted">当前没有可执行操作</span>}
        </section><FlowCard ticket={selected} />
      </aside></div>
    </> : <Empty description="请选择一张工单" />}
  </main>
  const toolbar = <div className="production-ticket-search"><Input.Search allowClear placeholder="搜索工单编号" value={keywordInput} onChange={event => { setKeywordInput(event.target.value); if (!event.target.value) { setKeyword(''); filterChanged() } }} onSearch={value => { setKeyword(value.trim()); filterChanged() }} /><Button icon={<FilterOutlined />} type={filtersOpen || status || dates ? 'primary' : 'default'} onClick={() => setFiltersOpen(value => !value)}>筛选</Button>
    {filtersOpen && <div className="production-ticket-filters">{view === 'mine' && <Select aria-label="工单状态" allowClear placeholder="具体状态" value={status} options={(groupStatuses[group] || Object.keys(ticketStatuses)).map(value => ({ value, label: ticketStatuses[value] }))} onChange={value => { setStatus(value); filterChanged() }} />}<DatePicker.RangePicker aria-label="截止日期范围" value={dates} onChange={value => { setDates(value); filterChanged() }} /><Button type="link" onClick={() => { setStatus(undefined); setDates(null); setKeyword(''); setKeywordInput(''); filterChanged() }}>清空</Button></div>}
  </div>
  const pagination = <Pagination simple current={page} pageSize={pageSize} total={total} onChange={value => { setPage(value); clearSelection() }} showSizeChanger={false} />
  return <section className="workspace-page media-tickets-page production-tickets-page">
    <div className="production-ticket-nav"><Tabs activeKey={view} onChange={key => changeView(key as View)} items={[...(canAccept ? [{ key: 'pending', label: '待接单' }] : []), { key: 'mine', label: '我的工单' }, { key: 'pool', label: '抢单池', disabled: !canPool }]} /><Button aria-label="刷新工单" icon={<ReloadOutlined />} onClick={() => setRevision(value => value + 1)} /></div>
    {view === 'mine' && <Tabs className="production-ticket-subtabs" activeKey={group} items={ticketGroups} onChange={key => { setGroup(key); setStatus(undefined); filterChanged() }} />}
    {view === 'pool' ? detailOpen ? detail : <><div className="production-ticket-pool-toolbar">{toolbar}<span>共 {total} 条工单</span></div><div className="production-ticket-pool-scroll" ref={scrollRef}>
      {error ? <div role="status">{error}<Button onClick={() => setRevision(value => value + 1)}>重试</Button></div> : loading ? <Skeleton active /> : rows.length ? <div className="production-ticket-pool-grid">{rows.map(ticket => { const due = deadlinePresentation(ticket.deadlineAt, ticket.status, now); return <button key={ticket.id} className="production-ticket-pool-card" onClick={() => openTicket(ticket)}><div><strong>{ticket.ticketNo}</strong><Tag color="blue">待抢单</Tag></div><h3>{ticket.sceneName || '制作需求'}</h3><p>{identity(ticket)}</p><p className="production-ticket-summary">{ticket.dispatchContext?.operatorRemark || '点击查看完整制作要求'}</p><span>提交人：{ticket.submitterName || '未记录'}</span><footer className={`tone-${due.tone}`}><strong>{due.text}</strong><time>{due.date}</time><span>查看详情 →</span></footer></button> })}</div> : <Empty description="抢单池暂无符合条件的工单" />}
    </div>{pagination}</> : <div className={`production-ticket-inbox ${detailOpen ? 'show-detail' : 'show-list'}`}><aside className="production-ticket-list-pane">{toolbar}<span className="production-ticket-count">共 {total} 条工单</span><div className="production-ticket-list-scroll">{error ? <div role="status">{error}<Button onClick={() => setRevision(value => value + 1)}>重试</Button></div> : loading ? <Skeleton active /> : rows.length ? rows.map(ticket => <button key={ticket.id} className={`production-ticket-list-item ${selected?.id === ticket.id ? 'active' : ''}`} onClick={() => openTicket(ticket)}><div><strong>{ticket.ticketNo}</strong><Tag color={ticket.status === 'rejected' ? 'error' : undefined}>{ticketStatuses[ticket.status] || ticket.status}</Tag></div><span>{identity(ticket)}</span><time>截止 {formatTimestamp(ticket.deadlineAt, '未设置')}</time></button>) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无符合条件的工单" />}</div>{pagination}</aside>{detail}</div>}
    <Modal title={ticketActionLabels[dialogAction || '']} open={Boolean(dialogAction)} confirmLoading={saving} cancelButtonProps={{ disabled: saving }} closable={!saving} maskClosable={!saving} onCancel={() => setDialogAction(undefined)} onOk={() => dialogAction && void execute(dialogAction)} okText="确认">
      {dialogAction === 'APPROVE_TICKET' ? <p>确认成品符合要求，通过后工单将完成。</p> : <Form layout="vertical">{dialogAction === 'SUBMIT_TICKET' && <Form.Item label="成品链接" required><ResourceLinkInput aria-label="成品链接" value={completionUrl} maxLength={2000} onChange={event => setCompletionUrl(event.target.value)} /></Form.Item>}<Form.Item label={dialogAction === 'SUBMIT_TICKET' ? '提交备注（可选）' : '原因'} required={dialogAction !== 'SUBMIT_TICKET'}><Input.TextArea aria-label={dialogAction === 'SUBMIT_TICKET' ? '提交备注（可选）' : '原因'} rows={4} maxLength={dialogAction === 'SUBMIT_TICKET' ? 1000 : 500} showCount value={reason} onChange={event => setReason(event.target.value)} /></Form.Item></Form>}
    </Modal>
  </section>
}
