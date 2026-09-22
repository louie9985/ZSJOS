// UTF-8. Interactive layout proposal only: no API imports and no business requests.
import { useEffect, useMemo, useRef, useState } from 'react'
import dayjs from 'dayjs'
import { createRoot } from 'react-dom/client'
import { App, Alert, Avatar, Button, Checkbox, Collapse, DatePicker, Empty, Input, Modal, Progress, Radio, Select, Skeleton, Space, Tabs, Tag, Tooltip } from 'antd'
import { ArrowLeftOutlined, CheckCircleOutlined, ClockCircleOutlined, CloseOutlined, FileTextOutlined, FilterOutlined, HistoryOutlined, LinkOutlined, PlusOutlined, ReloadOutlined, RightOutlined, UserOutlined } from '@ant-design/icons'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { categories, filterBatches, initialBatches, statusNames, type Account, type Batch, type Filters, type Work } from './content-review-layout-data'
import '../src/styles/tokens.css'
import '../src/styles/base.css'
import './content-review-layout.css'

type Decision = { result?: string; comment: string; saved: boolean }
type AccountTarget = { batch: Batch; account: Account }
const allAccounts = initialBatches.flatMap(batch => batch.accounts)
const options = (key: 'operator' | 'director' | 'platform') => [...new Set(allAccounts.map(account => account[key]))].map(value => ({ value, label: value }))
const isPending = (batch: Batch) => ['DIRECTOR_REVIEW', 'FINAL_REVIEW'].includes(batch.status)
function Status({ batch }: { batch: Batch }) {
  const color = isPending(batch) ? 'processing' : batch.status === 'NEED_MODIFY' ? 'warning' : ['COMPLETED', 'PUBLISHED'].includes(batch.status) ? 'success' : 'default'
  return <Tag color={color}>{statusNames[batch.status]}</Tag>
}
function Field({ label, children, wide = false }: { label: string; children: React.ReactNode; wide?: boolean }) {
  return <div className={`crp-field${wide ? ' crp-wide' : ''}`}><dt>{label}</dt><dd>{children}</dd></div>
}
function Preview() {
  const { message, modal } = App.useApp()
  const [batches, setBatches] = useState(initialBatches)
  const [category, setCategory] = useState('PENDING')
  const [search, setSearch] = useState('')
  const [keyword, setKeyword] = useState('')
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [filters, setFilters] = useState<Filters>({ mine: false })
  const [selectedId, setSelectedId] = useState(1)
  const [mobileDetail, setMobileDetail] = useState(false)
  const [scenario, setScenario] = useState('normal')
  const [historyOpen, setHistoryOpen] = useState(false)
  const [decisions, setDecisions] = useState<Record<number, Decision>>({})
  const [activeTab, setActiveTab] = useState('review')
  const [target, setTarget] = useState<AccountTarget>()
  const [accountDraft, setAccountDraft] = useState('')
  const [accountEditing, setAccountEditing] = useState(false)
  const [completion, setCompletion] = useState<'APPROVED' | 'RETURNED'>()
  const [reason, setReason] = useState('')
  const [narrowDetail, setNarrowDetail] = useState(false)
  const [flowExpanded, setFlowExpanded] = useState<boolean>()
  const reviewScroll = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const element = reviewScroll.current
    if (!element) return
    const observer = new ResizeObserver(entries => {
      const width = entries[0]?.contentRect.width
      if (width) setNarrowDetail(width <= 900)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const selected = batches.find(batch => batch.id === selectedId)!
  const rows = useMemo(() => filterBatches(batches, category, keyword, filters), [batches, category, keyword, filters])
  const filterCount = Number(Boolean(filters.operator)) + Number(Boolean(filters.director)) + Number(Boolean(filters.platform))
    + Number(Boolean(filters.from || filters.to)) + Number(Boolean(filters.stage && category === 'PENDING')) + Number(filters.mine)
  const selectedVisible = rows.some(batch => batch.id === selectedId)
  const pendingChanges = selected.works.some(work => decisions[work.id] && !decisions[work.id].saved)
  function confirmLeave(action: () => void) {
    if (!pendingChanges) { action(); return }
    modal.confirm({ title: '切换后保留未保存意见？', content: '可以保留当前编辑内容，稍后回到此批次继续审核。', okText: '保留并切换', cancelText: '继续审核', onOk: action })
  }
  function switchBatch(id: number) { confirmLeave(() => { setSelectedId(id); setMobileDetail(true); reviewScroll.current?.scrollTo(0, 0) }) }
  function updateFilters(value: Partial<Filters>) { confirmLeave(() => { setFilters(current => ({ ...current, ...value })); setMobileDetail(false) }) }
  function reset() { confirmLeave(() => { setSearch(''); setKeyword(''); setFilters({ mine: false }); setMobileDetail(false) }) }
  function openAccount(batch: Batch, account: Account) {
    const change = () => { setTarget({ batch, account }); setActiveTab('media'); setAccountEditing(false); setAccountDraft('') }
    if (accountEditing && target?.account.id !== account.id) {
      modal.confirm({ title: '账号页面有未保存修改', content: '切换账号将放弃这次修改。', okText: '放弃并切换', cancelText: '继续编辑', onOk: change })
    } else if (target?.account.id === account.id && target.batch.studentId === batch.studentId) setActiveTab('media')
    else change()
  }
  function accountLink(batch: Batch, account: Account) {
    return <a className="crp-account-link" href={`?view=media&personId=${batch.studentId}&accountId=${account.id}`}
      onClick={event => { event.preventDefault(); openAccount(batch, account) }}><span>{account.name}</span><LinkOutlined /></a>
  }
  function closeAccount() {
    const close = () => { setTarget(undefined); setActiveTab('review'); setAccountEditing(false); setAccountDraft('') }
    if (accountEditing) modal.confirm({ title: '放弃账号页未保存修改并关闭？', okText: '放弃并关闭', cancelText: '继续编辑', onOk: close })
    else close()
  }
  const done = selected.works.filter(work => decisions[work.id]?.saved).length
  const allSaved = done === selected.works.length && !pendingChanges
  const hasReturned = selected.works.some(work => decisions[work.id]?.result === 'RETURNED')
  function complete() {
    const nextStatus = completion === 'RETURNED' ? 'NEED_MODIFY' : selected.status === 'DIRECTOR_REVIEW' ? 'FINAL_REVIEW' : 'COMPLETED'
    setBatches(current => current.map(batch => batch.id === selected.id ? { ...batch, status: nextStatus } : batch))
    setDecisions(current => { const next = { ...current }; selected.works.forEach(work => delete next[work.id]); return next })
    setCompletion(undefined); setReason(''); message.success('演示状态已更新，未提交真实审批')
  }
  function processPanel() {
    return <aside className="crp-process" aria-label="审批流程与操作">
      <div className="crp-process-heading"><strong>审批流程</strong><Status batch={selected} /></div>
      <Collapse ghost activeKey={(flowExpanded ?? !narrowDetail) ? ['flow'] : []} onChange={keys => setFlowExpanded(keys.length > 0)} items={[{ key: 'flow', label: '本轮流程', children:
        <ol className="crp-timeline">
          <li className="completed"><CheckCircleOutlined /><div><strong>运营提交</strong><span>{selected.accounts[0].operator} · {selected.submitted || '尚未提交'}</span></div></li>
          <li className={selected.status === 'DIRECTOR_REVIEW' ? 'current' : ''}><ClockCircleOutlined /><div><strong>编导审核</strong><span>{selected.accounts.map(account => account.director).join('、')}</span></div></li>
          <li className={selected.status === 'FINAL_REVIEW' ? 'current' : ''}><ClockCircleOutlined /><div><strong>终审</strong><span>{selected.status === 'DIRECTOR_REVIEW' ? '等待编导通过' : '本轮终审节点'}</span></div></li>
        </ol> }]} />
      {isPending(selected) ? <div className="crp-process-actions">
        <div className="crp-row"><span>逐条审核进度</span><strong>{done} / {selected.works.length}</strong></div>
        <Progress percent={Math.round(done / selected.works.length * 100)} showInfo={false} size="small" />
        <p className="crp-muted">{pendingChanges ? '有意见尚未保存，请先保存本条结论。' : allSaved ? '逐条结论已保存，可提交本轮审批。' : '请先逐条查看内容并保存审核结论。'}</p>
        <Button type="primary" block disabled={!allSaved || hasReturned} onClick={() => setCompletion('APPROVED')}>通过本次审批</Button>
        <Button danger block disabled={!allSaved || !hasReturned} onClick={() => setCompletion('RETURNED')}>退回运营修改</Button>
      </div> : <p className="crp-muted">当前批次{statusNames[selected.status]}，暂无待处理审批。</p>}
    </aside>
  }
  function renderWork(work: Work, index: number) {
    const decision = decisions[work.id] || { comment: '', saved: false }
    const update = (value: Partial<Decision>) => setDecisions(current => ({ ...current, [work.id]: { ...decision, ...value, saved: false } }))
    return <article className="crp-work" key={work.id} aria-label={`作品 ${index + 1}`}>
      <header className="crp-work-heading"><div className="crp-work-title"><span className="crp-index">{String(index + 1).padStart(2, '0')}</span><div><h3>{work.title}</h3><span className="crp-muted">内容版本 V1 · {work.format}</span></div></div><Tag color={decision.saved ? 'success' : 'default'}>{decision.saved ? '已保存结论' : '待审核'}</Tag></header>
      <div className="crp-work-body">
        <div className="crp-cover"><FileTextOutlined /><strong>十分钟早餐</strong><span>生活有序，从早晨开始</span><small>示例封面</small></div>
        <dl className="crp-fields">
          <Field label="预计发布时间">2026-09-25 08:30</Field><Field label="作品目的">{work.purpose}</Field>
          <Field label="作品形式">{work.format}</Field><Field label="选题">{work.topic}</Field>
          <Field label="发布标题" wide>{work.title}</Field>
          <Field label={`正文文稿 · ${work.script.length.toLocaleString()} 字`} wide><div className="crp-script" tabIndex={0} aria-label={`正文文稿 ${index + 1}`}>{work.script}</div></Field>
          <Field label="评论区钩子" wide>你最喜欢哪一种早餐搭配？在评论区分享你的日常。</Field>
        </dl>
      </div>
      <div className="crp-resources"><span className="crp-muted">内容资料</span><Button size="small" icon={<FileTextOutlined />} onClick={() => modal.info({ title: '审核附件 · 示例拍摄提纲', content: '开场：晨间厨房。主体：三种搭配与制作步骤。结尾：引导观众分享。此预览展示附件入口，不访问真实文件。' })}>拍摄提纲.txt</Button><Button size="small" type="link" onClick={() => modal.info({ title: '参考素材', content: '示例参考：晨间生活方式短片，展示自然光、食材特写与成品镜头。' })}>查看参考素材</Button></div>
      {isPending(selected) && <div className="crp-decision">
        <div className="crp-row"><strong>本条审核结论</strong>{decision.saved ? <span className="crp-saved">已保存</span> : decision.comment || decision.result ? <span className="crp-unsaved">未保存</span> : null}</div>
        <div className="crp-decision-controls"><Radio.Group aria-label={`作品 ${index + 1} 结论`} value={decision.result} onChange={event => update({ result: event.target.value })} options={[{ label: '通过', value: 'APPROVED' }, { label: '不通过', value: 'RETURNED' }]} />
          <Input.TextArea aria-label={`作品 ${index + 1} 审核意见`} placeholder="填写本条审核意见" autoSize={{ minRows: 2, maxRows: 5 }} value={decision.comment} onChange={event => update({ comment: event.target.value })} />
          <Button disabled={!decision.result || !decision.comment.trim() || decision.saved} onClick={() => setDecisions(current => ({ ...current, [work.id]: { ...decision, saved: true } }))}>保存结论</Button></div>
      </div>}
    </article>
  }
  return <div className="crp-shell">
    <div className="crp-preview-bar"><span><strong>中世健</strong><span className="crp-preview-description">内容审核 · 交互预览</span><Tag>隔离示例数据</Tag></span>
      <Select aria-label="预览场景" value={scenario} onChange={setScenario} options={[
        { value: 'normal', label: '正常场景' }, { value: 'loading', label: '加载中' }, { value: 'empty', label: '空列表' }, { value: 'error', label: '加载失败' }, { value: 'denied', label: '账号无权限' }, { value: 'missing', label: '账号已失效' }
      ]} /></div>
    <nav className="crp-internal-tabs" aria-label="工作台内部标签">
      <button type="button" className={activeTab === 'review' ? 'active' : ''} onClick={() => setActiveTab('review')}><FileTextOutlined /> 内容审核</button>
      {target && <div className={`crp-tab-with-close ${activeTab === 'media' ? 'active' : ''}`}><button type="button" aria-label="媒体学员" onClick={() => setActiveTab('media')}><UserOutlined /> 媒体学员</button><button type="button" aria-label="关闭媒体学员标签" onClick={closeAccount}><CloseOutlined /></button></div>}
    </nav>
    {/* Both preview surfaces stay mounted: navigating to an account must not discard a review draft or scroll offset. */}
    <section className="crp-review" hidden={activeTab !== 'review'}>
      <header className="crp-header">
        <div className="crp-title-row"><div><h1>内容审核</h1><span className="crp-muted">查看内容，给出反馈，推进每一轮创作</span></div><Space><Tooltip title="刷新列表"><Button aria-label="刷新列表" icon={<ReloadOutlined />} onClick={() => { setScenario('normal'); message.success('预览列表已刷新，编辑内容保留') }} /></Tooltip><Button type="primary" icon={<PlusOutlined />} onClick={() => modal.info({ title: '创建内容审批', content: '正式接入时沿用现有创建表单。本次预览仅演示入口和操作布局，不创建业务记录。' })}>创建批次</Button></Space></div>
        <Tabs className="crp-status-tabs" activeKey={category} onChange={value => confirmLeave(() => { setCategory(value); setMobileDetail(false); setFilters(current => ({ ...current, stage: undefined })) })} items={categories.map(({ key, label }) => ({ key, label }))} />
        <div className="crp-search-row"><Input.Search aria-label="搜索内容审核" allowClear placeholder="搜索学员、账号、标题、选题、正文或批次编号" value={search} onChange={event => { setSearch(event.target.value); if (!event.target.value) setKeyword('') }} onSearch={value => confirmLeave(() => { setKeyword(value.trim()); setMobileDetail(false) })} />
          <Button aria-label="筛选" icon={<FilterOutlined />} type={filtersOpen || filterCount ? 'primary' : 'default'} ghost={filtersOpen || Boolean(filterCount)} onClick={() => setFiltersOpen(value => !value)}>筛选{filterCount ? ` (${filterCount})` : ''}</Button><Button aria-label="重置" onClick={reset}>重置</Button><span className="crp-result-count">{scenario === 'empty' ? 0 : rows.length} 个批次</span></div>
        {filtersOpen && <div className="crp-filters">
          <label>责任运营<Select aria-label="责任运营筛选" allowClear placeholder="全部运营" value={filters.operator} options={options('operator')} onChange={operator => updateFilters({ operator })} /></label>
          <label>责任编导<Select aria-label="责任编导筛选" allowClear placeholder="全部编导" value={filters.director} options={options('director')} onChange={director => updateFilters({ director })} /></label>
          <label>发布平台<Select aria-label="平台筛选" allowClear placeholder="全部平台" value={filters.platform} options={options('platform')} onChange={platform => updateFilters({ platform })} /></label>
          <label className="crp-date-filter">提交时间<DatePicker.RangePicker value={filters.from && filters.to ? [dayjs(filters.from), dayjs(filters.to)] : null} onChange={(_, strings) => updateFilters({ from: strings[0] || undefined, to: strings[1] || undefined })} /></label>
          {category === 'PENDING' && <label>审批阶段<Select aria-label="审批阶段筛选" allowClear placeholder="全部阶段" value={filters.stage} options={[{ value: 'DIRECTOR_REVIEW', label: '编导审核' }, { value: 'FINAL_REVIEW', label: '终审' }]} onChange={stage => updateFilters({ stage })} /></label>}
          <Checkbox checked={filters.mine} onChange={event => updateFilters({ mine: event.target.checked })}>我是批次运营</Checkbox>
        </div>}
      </header>
      <div className={`crp-layout ${mobileDetail ? 'show-detail' : ''}`}>
        <aside className="crp-inbox" aria-label="内容收件箱"><div className="crp-pane-caption"><strong>内容收件箱</strong><span>最新审批轮次</span></div><div className="crp-inbox-scroll">
          {scenario === 'loading' ? <div className="crp-state"><Skeleton active paragraph={{ rows: 8 }} /></div> : scenario === 'error' ? <div className="crp-state"><Alert title="列表加载失败" type="error" description="请重试获取审核列表。" action={<Button aria-label="重试" onClick={() => setScenario('normal')}>重试</Button>} /></div> : !rows.length || scenario === 'empty' ? <Empty description="没有符合条件的批次" /> : rows.map(batch =>
            <button type="button" key={batch.id} className={`crp-inbox-item ${batch.id === selectedId ? 'selected' : ''}`} onClick={() => switchBatch(batch.id)}>
              <div className="crp-inbox-title"><strong>{batch.student}</strong><Status batch={batch} /></div>
              {batch.accounts.map(account => <div className="crp-inbox-account" key={account.id}><div className="crp-account-name">{account.name}<span>{account.platform}</span></div><div className="crp-owners"><span>责任运营 <b>{account.operator}</b></span><span>责任编导 <b>{account.director}</b></span></div></div>)}
              <div className="crp-inbox-footer"><Tag color="blue">{batch.works.length} 条内容</Tag><time>{batch.submitted || '尚未提交'}</time></div>
            </button>)}
        </div></aside>
        <main className="crp-detail" ref={reviewScroll} aria-label="审核详情">
          <Button aria-label="返回收件箱" className="crp-mobile-back" icon={<ArrowLeftOutlined />} onClick={() => setMobileDetail(false)}>返回收件箱</Button>
          {scenario === 'loading' ? <Skeleton active paragraph={{ rows: 14 }} /> : scenario === 'error' ? <Empty description="列表恢复后查看详情" /> : scenario === 'empty' || !rows.length ? <Empty description="调整搜索或筛选条件后查看内容" /> : !selectedVisible ? <Empty description="从收件箱选择一个批次查看详情" /> : <>
            <header className="crp-detail-header"><div className="crp-detail-identity"><Avatar size={44} icon={<UserOutlined />} /><div><h2>{selected.student}<span>内容审核</span></h2><div className="crp-account-links">{selected.accounts.map(account => <span key={account.id}>{accountLink(selected, account)}</span>)}</div></div><Status batch={selected} /></div>
              <div className="crp-detail-meta"><span>提交时间 <b>{selected.submitted || '尚未提交'}</b></span><span>当前阶段 <b>{statusNames[selected.status]}</b></span><Tag color="blue">{selected.works.length} 条内容</Tag><span className="crp-batch-no">审批编号 {selected.no}</span></div>
              <div className="crp-detail-actions"><Button icon={<HistoryOutlined />} onClick={() => setHistoryOpen(true)}>历史轮次</Button><Space wrap>
                {selected.status === 'DRAFT' && <><Button onClick={() => message.info('正式接入时打开现有草稿编辑器')}>编辑草稿</Button><Button type="primary" onClick={() => { setBatches(current => current.map(batch => batch.id === selected.id ? { ...batch, status: 'DIRECTOR_REVIEW', submitted: '2026-09-22 12:00' } : batch)); message.success('演示：草稿已进入待审批') }}>提交审批</Button><Button danger onClick={() => modal.confirm({ title: '取消这个示例批次？', onOk: () => setBatches(current => current.map(batch => batch.id === selected.id ? { ...batch, status: 'CANCELLED' } : batch)) })}>取消批次</Button></>}
                {selected.status === 'NEED_MODIFY' && <Button type="primary" onClick={() => message.info('正式接入时沿用现有修改并重新提交表单')}>修改后重新提交</Button>}
              </Space></div>
            </header>
            <div className="crp-workspace"><div className="crp-content">
              <section className="crp-profiles"><h3>账号资料 <span>本轮审批快照</span></h3>{selected.accounts.map(account => <article className="crp-profile" key={account.id}>
                <div className="crp-profile-heading">{accountLink(selected, account)}<Tag>{account.platform}</Tag></div>
                <dl className="crp-profile-fields"><Field label="责任运营">{account.operator}</Field><Field label="责任编导">{account.director}</Field><Field label="当前期段">内容起步期</Field><Field label="账号状态">稳定更新</Field></dl>
                <Collapse ghost items={[{ key: 'background', label: '运营背景 · 产品目标、发布节奏与当前瓶颈', children: <dl className="crp-profile-fields"><Field label="承接产品目标">建立可信赖的生活方式内容</Field><Field label="主要产品形式">知识分享</Field><Field label="发布节奏">每周 3 条</Field><Field label="当前瓶颈">提高开头吸引力与内容完成度</Field></dl> }]} />
              </article>)}</section>
              <div className="crp-section-heading"><h3>待审内容</h3><span className="crp-muted">共 {selected.works.length} 条 · 逐条审核后提交本轮结论</span></div>
              {selected.works.map(renderWork)}
            </div>{processPanel()}</div>
          </>}
        </main>
      </div>
    </section>
    <section className="crp-media-page" hidden={activeTab !== 'media'} aria-label="媒体学员账号详情">
      {target && <><div className="crp-media-breadcrumb"><Button aria-label="返回内容审核" icon={<ArrowLeftOutlined />} onClick={() => setActiveTab('review')}>返回内容审核</Button><span>媒体学员 <RightOutlined /> {target.batch.student}</span></div>
        {scenario === 'denied' || scenario === 'missing' ? <Alert type="warning" showIcon title={scenario === 'denied' ? '无权查看此账号' : '账号已失效或不再属于此学员'} description="审批中的历史快照仍可查看，不自动打开其他账号。" /> : <>
          <header className="crp-media-heading"><Avatar size={56} icon={<UserOutlined />} /><div><h1>{target.batch.student}</h1><p className="crp-muted">学员档案 · 当前账号资料</p></div><Tag color="success">服务中</Tag></header>
          <Tabs activeKey={String(target.account.id)} onChange={id => { const account = target.batch.accounts.find(item => String(item.id) === id); if (account) openAccount(target.batch, account) }} items={target.batch.accounts.map(account => ({ key: String(account.id), label: account.name }))} />
          <div className="crp-media-columns"><article className="crp-media-card"><div className="crp-row"><h2>{target.account.name}</h2><Tag>{target.account.platform}</Tag></div><p className="crp-muted">已定位到点击的账号，所有账号共用“媒体学员”内部标签。</p>
            <dl className="crp-profile-fields"><Field label="责任运营">{target.account.operator}</Field><Field label="责任编导">{target.account.director}</Field><Field label="账号定位">分享真实、可实践的日常生活方式</Field><Field label="当前期段">内容起步期</Field><Field label="账号状态">稳定更新</Field><Field label="发布节奏">每周 3 条</Field></dl>
            <h3>账号定位卡</h3><dl className="crp-fields"><Field label="内容方向" wide>以日常场景为起点，通过清晰的步骤与自然的表达建立信任。</Field><Field label="目标受众" wide>希望改善生活节奏、关注轻松实用方法的用户。</Field><Field label="当前目标" wide>保持稳定更新，找到适合账号的表达方式。</Field></dl>
            {accountEditing && <label className="crp-account-editor">账号维护备注<Input.TextArea aria-label="账号维护备注" value={accountDraft} onChange={event => setAccountDraft(event.target.value)} rows={4} /></label>}
          </article><aside className="crp-media-card"><h3>账号操作</h3><p className="crp-muted">此处仅演示跳转目标与未保存修改保护，正式接入复用现有账号详情。</p><Button block type="primary" onClick={() => setAccountEditing(true)}>维护账号资料</Button>{accountEditing && <Button block onClick={() => { setAccountEditing(false); message.success('演示修改已保存，无业务写入') }}>保存演示修改</Button>}<Button block onClick={() => setActiveTab('review')}>回到审批继续审核</Button></aside></div>
        </>}
      </>}
    </section>
    <Modal title="审批历史轮次" open={historyOpen} footer={null} onCancel={() => setHistoryOpen(false)}><p>当前为第 1 轮审批，暂无更早轮次。</p><div className="crp-row"><span>{selected.no}</span><Status batch={selected} /></div></Modal>
    <Modal title={completion === 'RETURNED' ? '退回运营修改' : '通过本次审批'} open={Boolean(completion)} onCancel={() => { setCompletion(undefined); setReason('') }} onOk={complete} okText="确认（仅演示）" okButtonProps={{ disabled: !reason.trim() }}><p>本轮意见会随审批保留。预览仅更新本地示例状态。</p><Input.TextArea aria-label="本轮审核意见" rows={4} placeholder="填写本轮审核意见" value={reason} onChange={event => setReason(event.target.value)} /></Modal>
  </div>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Preview /></App></ThemeProvider>)
