import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Avatar, Badge, Button, Drawer, Empty, Form, Input, Modal, Pagination, Segmented, Space, Spin, Tag, Typography, message } from 'antd'
import { api, type AdvancedFilterGroup, type AssignmentUser, type LeadQualificationException } from '../services/api'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'
import { LEAD_HANDLING_STAGE_LABELS } from '../constants'
import { formatTimestamp } from '../services/time'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'
import EmployeeSelect from '../components/EmployeeSelect'
import DetailFieldGrid from '../components/DetailFieldGrid'

type ExceptionType = 'suspended' | 'recycle_pending'
type Action = 'restore' | 'transfer' | 'recycle' | 'release'
const PAGE_SIZE = 20

export default function LeadQualificationExceptionPage() {
  const [type, setType] = useState<ExceptionType>('suspended')
  const [items, setItems] = useState<LeadQualificationException[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [selected, setSelected] = useState<LeadQualificationException>()
  const [pageNo, setPageNo] = useState(1)
  const [total, setTotal] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [action, setAction] = useState<Action>()
  const [reason, setReason] = useState('')
  const [salesUserId, setSalesUserId] = useState<number>()
  const [candidates, setCandidates] = useState<AssignmentUser[]>([])
  const { submitting: saving, run: runSubmission, resetIntent } = useSubmissionGuard()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const requestVersion = useRef(0)
  const closeAction = () => { setConfirmOpen(false); setAction(undefined) }

  const load = useCallback(async (targetPage: number) => {
    const version = ++requestVersion.current
    setLoading(true); setError('')
    try {
      const page = await api.qualificationExceptionPage(type, { pageNo: targetPage, pageSize: PAGE_SIZE, keyword: keyword || undefined, advancedFilter })
      if (version === requestVersion.current) {
        setItems(page.list)
        setTotal(page.total)
        setPageNo(targetPage)
        setSelected(current => page.list.find(item => item.id === current?.id) || page.list[0])
      }
    }
    catch (loadError) { if (version === requestVersion.current) setError(loadError instanceof Error ? loadError.message : '异常客资加载失败') }
    finally { if (version === requestVersion.current) setLoading(false) }
  }, [advancedFilter, keyword, type])

  useEffect(() => { void load(1) }, [load])

  const openAction = async (lead: LeadQualificationException, nextAction: Action) => {
    resetIntent()
    setSelected(lead); setAction(nextAction); setReason(''); setSalesUserId(undefined); setCandidates([])
    if (nextAction === 'transfer') {
      try { setCandidates(await api.leadTransferCandidates(lead.id)) }
      catch (loadError) { message.error(loadError instanceof Error ? loadError.message : '转派销售加载失败') }
    }
  }

  const submit = async () => {
    setConfirmOpen(false)
    if (!selected || !action) return
    await runSubmission(async ({ idempotencyKey, complete }) => {
      const command = { reason: reason.trim(), idempotencyKey }
      if (action === 'restore') await api.restoreLead(selected.id, command)
      if (action === 'transfer') await api.transferLead(selected.id, { ...command, salesUserId: salesUserId! })
      if (action === 'recycle') await api.recycleLead(selected.id, command)
      if (action === 'release') await api.releaseLeadToClaimPool(selected.id, command)
      complete()
      message.success('异常客资已处理')
      setAction(undefined); setSelected(undefined)
      await load(pageNo)
    }).catch(submitError => message.error(submitError instanceof Error ? submitError.message : '异常客资处理失败'))
  }

  const prepareSubmit = () => {
    if (!selected || !action || !reason.trim()) { message.warning('请填写处置理由'); return }
    if (action === 'transfer' && !salesUserId) { message.warning('请选择目标销售'); return }
    setConfirmOpen(true)
  }

  const confirmAction = !selected || !action ? '处理异常客资' : ({
    restore: `恢复客资「${selected.submittedName}」至原销售`,
    transfer: `将客资「${selected.submittedName}」转派给「${candidates.find(user => user.id === salesUserId)?.nickname || '目标销售'}」`,
    recycle: `回收客资「${selected.submittedName}」`,
    release: `将客资「${selected.submittedName}」释放到抢单池`
  } satisfies Record<Action, string>)[action]

  const ownerLabel = !selected ? '-' : type === 'suspended'
    ? selected.ownerUserName || (selected.ownerUserId ? `用户 #${selected.ownerUserId}` : '-')
    : selected.recycleSourceOwnerUserName || (selected.recycleSourceOwnerUserId ? `用户 #${selected.recycleSourceOwnerUserId}` : '-')
  const detail = selected ? <article className="business-inbox-detail">
    <header className="business-inbox-detail-hero">
      <div className="business-inbox-detail-heading">
        <Avatar>{selected.submittedName.slice(0, 1)}</Avatar>
        <div><Typography.Title level={4}>{selected.leadNo} · {selected.submittedName}</Typography.Title><Space wrap><Tag color="warning">{LEAD_HANDLING_STAGE_LABELS[selected.handlingStage] || '未知处理阶段'}</Tag><Tag>{type === 'suspended' ? '挂起客资' : '回收待处理'}</Tag></Space></div>
      </div>
      <Space wrap className="business-inbox-detail-actions">
        {type === 'suspended' && <Button onClick={() => void openAction(selected, 'restore')}>恢复</Button>}
        <Button onClick={() => void openAction(selected, 'transfer')}>转派</Button>
        {type === 'suspended' && <Button danger onClick={() => void openAction(selected, 'recycle')}>回收</Button>}
        <Button type="primary" danger onClick={() => void openAction(selected, 'release')}>释放</Button>
      </Space>
    </header>
    <section className="business-inbox-card">
      <DetailFieldGrid items={[
        { key: 'mobile', label: '手机号', value: selected.submittedMobile },
        { key: 'stage', label: '处理阶段', value: LEAD_HANDLING_STAGE_LABELS[selected.handlingStage] || '未知处理阶段' },
        { key: 'owner', label: type === 'suspended' ? '当前销售' : '回收来源销售', value: ownerLabel },
        { key: 'deadline', label: '判定截止', value: formatTimestamp(selected.qualificationDeadlineAt) },
        { key: 'suspendedAt', label: '挂起时间', value: formatTimestamp(selected.suspendedAt) },
      ]}/>
    </section>
  </article> : <Empty description="从左侧选择一条异常客资"/>

  return <section className="workspace-page business-inbox-page qualification-exception-page">
    <header className="business-inbox-scope-bar"><div className="business-inbox-scope-row"><Segmented value={type} onChange={value => setType(value as ExceptionType)} options={[{ value: 'suspended', label: '挂起客资' }, { value: 'recycle_pending', label: '回收待处理' }]}/></div></header>
    <div className="business-inbox-layout">
      <aside className="business-inbox-list-pane">
        <div className="business-inbox-toolbar"><AdvancedFilterToolbar scene="lead" placeholder="搜索姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/></div>
        {error && <Alert className="business-inbox-error" type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(pageNo)}>重试</Button>}/>}
        <div className="business-inbox-scroll">
          {loading ? <div className="business-inbox-list-state"><Spin/></div> : items.length ? items.map(item => <button type="button" key={item.id} className={`business-inbox-item${selected?.id === item.id ? ' active' : ''}`} onClick={() => { setSelected(item); if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>
            <div className="business-inbox-item-main"><Avatar>{item.submittedName.slice(0, 1)}</Avatar><div className="business-inbox-item-copy"><div className="business-inbox-item-title"><strong>{item.submittedName}</strong><Tag color="warning">{LEAD_HANDLING_STAGE_LABELS[item.handlingStage] || '未知阶段'}</Tag></div><span>{item.leadNo}</span><span>{item.submittedMobile || '无手机号'}</span></div></div>
            <div className="business-inbox-item-meta"><Badge status="warning"/><span>{formatTimestamp(item.qualificationDeadlineAt)}</span></div>
          </button>) : <Empty description="暂无异常客资"/>}
        </div>
        {total > PAGE_SIZE && <div className="business-inbox-pagination"><Pagination simple size="small" current={pageNo} pageSize={PAGE_SIZE} total={total} onChange={page => void load(page)}/></div>}
      </aside>
      <main className="business-inbox-detail-pane">{detail}</main>
    </div>
    <Drawer className="business-inbox-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="异常客资详情" width="100%">{detail}</Drawer>
    <Modal open={Boolean(action)} title={{ restore: '恢复原销售', transfer: '转派客资', recycle: '回收客资', release: '释放到抢单池' }[action || 'restore']} onCancel={closeAction} footer={<Space><Button onClick={closeAction}>取消</Button><IrreversiblePopconfirm action={confirmAction} danger={action === 'recycle' || action === 'release'} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}><Button type="primary" danger={action === 'recycle' || action === 'release'} loading={saving} onClick={prepareSubmit}>确认处理</Button></IrreversiblePopconfirm></Space>}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {action === 'transfer' && <Form.Item label="目标销售" required><EmployeeSelect users={candidates} showSearch optionFilterProp="label" value={salesUserId} onChange={setSalesUserId} placeholder={candidates.length ? '选择目标销售' : '暂无可转派销售'} style={{ width: '100%' }}/></Form.Item>}
        <Form.Item label="处置理由" required><Input.TextArea value={reason} onChange={event => setReason(event.target.value)} rows={4} maxLength={500} showCount placeholder="填写本次处置理由"/></Form.Item>
      </Space>
    </Modal>
  </section>
}
