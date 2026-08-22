import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, App, Button, Checkbox, DatePicker, Empty, Form, Input, InputNumber, Modal, Pagination, Select, Skeleton, Space, Steps, Switch, Tabs, Tag, Timeline, Tooltip, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import DetailFieldGrid from '../components/DetailFieldGrid'
import { NameAvatar } from '../components/LeadDetailOverview'
import { ApiError, api, type DictData, type MediaAccountField, type MediaAccountFieldConfig, type MediaStudentDetail, type MediaStudentTalkRecord, type MyStudent } from '../services/api'
import { hasPermission } from '../services/managementAccess'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 20
const labels: Record<string, string> = { active: '服务中', completed: '已完成', cancelled: '已取消', co_creating: '定位共创', ip_review: 'IP 审核', operator_feasibility: '运营复核', student_confirm: '学员确认', trial_14d: '14 天试跑', confirmed: '已确认', archived: '已归档', topic: '选题', script: '脚本', in_production: '制作中', acceptance: '待验收', published: '已发布', rejected: '已退回', revising: '修改中' }
const statusLabel = (value?: string) => value ? labels[value] || value : '未记录'
const actionLabels: Record<string, string> = {
  COMPLETE_TOPIC: '完成选题', SUBMIT_PRODUCTION: '提交制作', SUBMIT_ACCEPTANCE: '提交验收',
  APPROVE_CONTENT: '通过验收', REJECT_CONTENT: '退回修改', START_CONTENT_REVISION: '开始修改',
  RESUBMIT_PRODUCTION: '重新提交', SUBMIT_POSITIONING_REVIEW: '提交审核',
  APPROVE_POSITIONING_FEASIBILITY: '复核通过', REJECT_POSITIONING_FEASIBILITY: '复核退回',
  CONFIRM_POSITIONING_TRIAL: '确认试跑', ARCHIVE_POSITIONING: '归档'
}
const errorText = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试'

export default function MediaStudentsPage({ permissions = [] }: { permissions?: string[] }) {
  const { message } = App.useApp()
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<MyStudent[]>([]), [detail, setDetail] = useState<MediaStudentDetail>()
  const [talks, setTalks] = useState<MediaStudentTalkRecord[]>([]), [selectedId, setSelectedId] = useState<number>()
  const [keyword, setKeyword] = useState(''), [search, setSearch] = useState(''), [pageNo, setPageNo] = useState(1), [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false), [detailLoading, setDetailLoading] = useState(false), [error, setError] = useState(''), [detailError, setDetailError] = useState('')
  const [tab, setTab] = useState(params.get('tab') || 'overview'), [dialog, setDialog] = useState<'account' | 'content' | 'positioning' | 'talk' | 'reject-content'>(), [saving, setSaving] = useState(false)
  const [platforms, setPlatforms] = useState<DictData[]>([]), [contentClasses, setContentClasses] = useState<DictData[]>([])
  const [accountFieldConfig, setAccountFieldConfig] = useState<MediaAccountFieldConfig>(), [fieldDicts, setFieldDicts] = useState<Record<string, DictData[]>>({})
  const [rejectingContent, setRejectingContent] = useState<MediaStudentDetail['contents'][number]>()
  const [form] = Form.useForm<Record<string, unknown>>(), listRun = useRef(0), detailRun = useRef(0)

  const loadDetail = useCallback(async (personId: number) => {
    const run = ++detailRun.current; setSelectedId(personId); setDetailLoading(true); setDetailError('')
    try { const [value, records] = await Promise.all([api.mediaStudents.get(personId), api.mediaStudents.talks(personId)]); if (run === detailRun.current) { setDetail(value); setTalks(records) } }
    catch (cause) { if (run === detailRun.current) { setDetail(undefined); setTalks([]); setDetailError(cause instanceof ApiError && cause.code === 403 ? '无权查看该学员' : errorText(cause)) } }
    finally { if (run === detailRun.current) setDetailLoading(false) }
  }, [])
  const loadPage = useCallback(async (targetPage: number, preferred?: number) => {
    const run = ++listRun.current; setLoading(true); setError('')
    try { const result = await api.mediaStudents.page({ pageNo: targetPage, pageSize: PAGE_SIZE, keyword: keyword || undefined }); if (run !== listRun.current) return
      setRows(result.list); setTotal(result.total); setPageNo(targetPage); const target = preferred || Number(params.get('personId')) || result.list[0]?.personId
      if (target) await loadDetail(target); else setDetail(undefined)
    } catch (cause) { if (run === listRun.current) { setRows([]); setDetail(undefined); setError(errorText(cause)) } } finally { if (run === listRun.current) setLoading(false) }
  }, [keyword, loadDetail, params])
  useEffect(() => { void loadPage(1) }, [keyword])

  const accountName = (id: number) => detail?.accounts.find(item => item.id === id)?.nickname || '未匹配账号'
  const open = async (type: typeof dialog, accountId?: number) => { form.resetFields(); if (accountId) form.setFieldValue('accountId', accountId); setDialog(type)
    if (type === 'account') {
      try {
        const [platformRows, config] = await Promise.all([platforms.length ? platforms : api.dictDataByType('zsjos_account_platform'), api.mediaAccount.publishedFieldConfig()])
        setPlatforms(platformRows); setAccountFieldConfig(config)
        const dictTypes = [...new Set(config.fields.filter(field => field.enabled && field.dictType).map(field => field.dictType!))]
        const entries = await Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const))
        setFieldDicts(Object.fromEntries(entries))
      } catch (cause) { setDialog(undefined); message.error(errorText(cause)); return }
    }
    if (type === 'content' && !contentClasses.length) setContentClasses(await api.dictDataByType('zsjos_content_class')) }
  const submit = async () => { if (!detail || !dialog) return
    try { const values = await form.validateFields(); setSaving(true)
      if (dialog === 'account') await api.mediaAccount.create({ studentPersonId: detail.student.personId, platformValue: String(values.platformValue), platformLabelSnapshot: platforms.find(x => x.value === values.platformValue)?.label || '', detailValues: (values.detailValues || {}) as Record<string, unknown> })
      if (dialog === 'content') await api.mediaContent.create({ ...values, contentClassLabelSnapshot: contentClasses.find(x => x.value === values.contentClassValue)?.label || '' } as never)
      if (dialog === 'positioning') await api.positioningCard.create({ ...values, studentPersonId: detail.student.personId } as never)
      if (dialog === 'talk') await api.mediaStudents.createTalk(detail.student.personId, values as { accountId?: number; content: string })
      if (dialog === 'reject-content' && rejectingContent) await api.mediaContent.rejectAcceptance(rejectingContent.id, rejectingContent.version, String(values.reason))
      setDialog(undefined); message.success('已保存'); await loadDetail(detail.student.personId)
    } catch (cause) { if (!(cause as { errorFields?: unknown }).errorFields) message.error(errorText(cause)) } finally { setSaving(false) } }
  const contentAction = async (row: MediaStudentDetail['contents'][number], action: string) => { try {
    if (action === 'REJECT_CONTENT') { form.resetFields(); setRejectingContent(row); setDialog('reject-content'); return }
    if (action === 'COMPLETE_TOPIC') await api.mediaContent.completeTopic(row.id, row.version); else if (action === 'SUBMIT_PRODUCTION') await api.mediaContent.submitProduction(row.id, row.version); else if (action === 'SUBMIT_ACCEPTANCE') await api.mediaContent.submitAcceptance(row.id, row.version); else if (action === 'APPROVE_CONTENT') await api.mediaContent.approveAcceptance(row.id, row.version); else if (action === 'START_CONTENT_REVISION') await api.mediaContent.startRevision(row.id, row.version); else if (action === 'RESUBMIT_PRODUCTION') await api.mediaContent.resubmitProduction(row.id, row.version); else return
    if (selectedId) await loadDetail(selectedId) } catch (cause) { message.error(errorText(cause)) } }
  const positioningAction = async (row: MediaStudentDetail['positioningCards'][number], action: string) => { try {
    if (action === 'SUBMIT_POSITIONING_REVIEW') await api.positioningCard.submitReview(row.id, row.version); else if (action === 'APPROVE_POSITIONING_FEASIBILITY') await api.positioningCard.operatorApprove(row.id, row.version); else if (action === 'REJECT_POSITIONING_FEASIBILITY') await api.positioningCard.operatorReject(row.id, row.version); else if (action === 'CONFIRM_POSITIONING_TRIAL') await api.positioningCard.confirmTrial(row.id, row.version); else if (action === 'ARCHIVE_POSITIONING') await api.positioningCard.archive(row.id, row.version); else return
    if (selectedId) await loadDetail(selectedId) } catch (cause) { message.error(errorText(cause)) } }
  const actions = (items: string[] | undefined, click: (action: string) => void) => items?.map(action => <Button size="small" key={action} onClick={() => click(action)}>{actionLabels[action] || action}</Button>)
  const accountField = (field: MediaAccountField) => {
    const name = ['detailValues', field.key]
    const rules = field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined
    if (field.type === 'textarea') return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><Input.TextArea rows={3} /></Form.Item>
    if (field.type === 'number') return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><InputNumber style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'date') return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><DatePicker style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'select' || field.type === 'multi_select') return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><Select mode={field.type === 'multi_select' ? 'multiple' : undefined} options={(fieldDicts[field.dictType || ''] || []).map(item => ({ value: item.value, label: item.label }))} /></Form.Item>
    if (field.type === 'boolean') return <Form.Item key={field.key} name={name} label={field.label} valuePropName="checked"><Switch /></Form.Item>
    return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><Input /></Form.Item>
  }

  const tabs = detail ? [
    { key: 'overview', label: '概览', children: <div className="media-students-overview-grid">
      <div className="media-students-overview-main">
        <section className="media-students-card"><Typography.Title level={5}>最新内容</Typography.Title>{detail.contents[0] ? <div className="media-students-record"><div><strong>{detail.contents[0].title || detail.contents[0].contentNo}</strong><span>{accountName(detail.contents[0].accountId)}</span></div><Tag>{statusLabel(detail.contents[0].status)}</Tag></div> : <Empty description="暂无内容" />}</section>
        <section className="media-students-card"><Typography.Title level={5}>操作时间线</Typography.Title><Timeline items={[...talks.slice(0, 5).map(x => ({ children: `${x.operatorUserName || '操作人'}：${x.content} · ${formatTimestamp(x.occurredAt)}` })), ...detail.contents.slice(0, 3).map(x => ({ children: `内容 ${x.title || x.contentNo} · ${statusLabel(x.status)}` }))]} /></section>
        <section className="media-students-card"><Typography.Title level={5}>任务线</Typography.Title><Steps current={detail.contents.some(x => x.status === 'published') ? 2 : detail.positioningCards.length ? 1 : 0} items={[{ title: '定位' }, { title: '运营' }, { title: '结业' }]} /></section>
      </div>
      <aside className="media-students-overview-aside">
        <section className="media-students-card"><Typography.Title level={5}>学员信息</Typography.Title><DetailFieldGrid columns={1} items={[{ key: 'mobile', label: '手机号', value: detail.student.mobile }, { key: 'wechat', label: '微信号', value: detail.student.wechatId }, { key: 'activated', label: '服务开始', value: formatTimestamp(detail.student.activatedAt) }]} /></section>
        <section className="media-students-card"><Typography.Title level={5}>课程服务</Typography.Title><div className="media-students-service-list">{detail.student.services.map(service => <div key={service.serviceRelationId}><strong>{service.courseName || service.skuName || '课程服务'}</strong><span>{service.orderNo || '订单号未记录'} · {statusLabel(service.status)}</span></div>)}</div></section>
        <section className="media-students-card"><Typography.Title level={5}>待处理</Typography.Title><div className="media-students-stats"><div><strong>{detail.accounts.length}</strong><span>第三方账号</span></div><div><strong>{detail.positioningCards.filter(item => item.availableActions?.length).length}</strong><span>定位任务</span></div><div><strong>{detail.contents.filter(item => item.availableActions?.length).length}</strong><span>内容任务</span></div></div></section>
      </aside>
    </div> },
    { key: 'accounts', label: '第三方平台账号', children: <section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>第三方平台账号</Typography.Title>{hasPermission(permissions, 'zsjos:media-account:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void open('account')}>新增账号</Button>}</Space>{detail.accounts.length ? <div className="media-students-record-list">{detail.accounts.map(x => <div className={`media-students-record${Number(params.get('accountId')) === x.id ? ' active' : ''}`} key={x.id}><div><strong>{x.nickname || x.accountNo}</strong><span>{x.platformLabel || '平台未记录'} · {x.accountNo}</span>{x.detailSnapshots?.length ? <span>{x.detailSnapshots.map(field => `${field.label}：${field.displayValue || '未记录'}`).join(' · ')}</span> : <span>详情字段未记录</span>}</div><Space><Tag>{x.stage?.toUpperCase() || '未分阶段'}</Tag>{hasPermission(permissions, 'zsjos:positioning-card:create') && <Button onClick={() => void open('positioning', x.id)}>账号定位</Button>}</Space></div>)}</div> : <Empty description="暂无第三方账号" />}</section> },
    { key: 'positioning', label: '定位历史', children: <section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>定位历史</Typography.Title>{hasPermission(permissions, 'zsjos:positioning-card:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void open('positioning')}>发起账号定位</Button>}</Space>{detail.positioningCards.map(x => <div className="media-students-record" key={x.id}><div><strong>{x.cardNo}</strong><span>{accountName(x.accountId)} · 版本 {x.versionNo || 1}</span></div><Space><Tag>{statusLabel(x.status)}</Tag>{actions(x.availableActions, action => void positioningAction(x, action))}</Space></div>)}</section> },
    { key: 'content', label: '内容生产历史', children: <section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>内容生产历史</Typography.Title>{hasPermission(permissions, 'zsjos:content:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void open('content')}>创建内容</Button>}</Space>{detail.contents.map(x => <div className="media-students-record" key={x.id}><div><strong>{x.title || x.contentNo}</strong><span>{accountName(x.accountId)} · {x.contentNo}</span></div><Space><Tag>{statusLabel(x.status)}</Tag>{actions(x.availableActions, action => void contentAction(x, action))}</Space></div>)}</section> },
    { key: 'talks', label: '交谈记录', children: <section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>交谈记录</Typography.Title><Button type="primary" icon={<PlusOutlined />} onClick={() => void open('talk')}>新增记录</Button></Space>{talks.length ? <Timeline items={talks.map(x => ({ children: <><strong>{x.operatorUserName || '操作人'}</strong><div>{x.content}</div><Typography.Text type="secondary">{formatTimestamp(x.occurredAt)} · {x.accountId ? accountName(x.accountId) : '未关联账号'}</Typography.Text></> }))} /> : <Empty description="暂无交谈记录" />}</section> }
  ] : []

  const body = detailLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : detailError ? <Alert type="warning" showIcon message={detailError} /> : !detail ? <Empty description="从左侧选择一名学员" /> : <div className="media-students-detail"><div className="media-students-detail-hero"><div className="media-students-identity"><NameAvatar name={detail.student.name || '学员'} size={44} /><div><Typography.Title level={4}>{detail.student.name || '未填写姓名'}</Typography.Title><Typography.Text type="secondary">{detail.student.leadNo || '暂无客资编号'}</Typography.Text></div></div></div><Tabs activeKey={tab} onChange={value => { setTab(value); setParams({ personId: String(detail.student.personId), tab: value }) }} items={tabs} /></div>

  return <section className="workspace-page media-students-page"><header className="media-students-filter-shell"><Typography.Title level={4}>我的学员</Typography.Title><Tooltip title="刷新"><Button icon={<ReloadOutlined />} onClick={() => void loadPage(pageNo, selectedId)} /></Tooltip></header><div className="media-students-inbox-layout"><aside className="media-students-list-pane"><div className="media-students-toolbar"><Input.Search allowClear value={search} onChange={e => setSearch(e.target.value)} onSearch={value => setKeyword(value.trim())} placeholder="搜索姓名、手机号或客资编号" /></div>{error && <Alert type="error" showIcon message={error} />}<div className="media-students-scroll">{loading && !rows.length ? <Skeleton active /> : rows.map(x => <button type="button" className={`media-students-item${selectedId === x.personId ? ' active' : ''}`} key={x.personId} onClick={() => void loadDetail(x.personId)}><NameAvatar name={x.name || '学员'} size={36} /><span className="media-students-item-copy"><strong>{x.name || '未填写姓名'}</strong><span>{x.leadNo || '暂无客资编号'}</span><span>{x.mobile || '无手机号'} · {x.services.length} 项服务</span></span></button>)}</div>{total > PAGE_SIZE && <Pagination simple current={pageNo} pageSize={PAGE_SIZE} total={total} onChange={value => void loadPage(value)} />}</aside><main className="media-students-detail-pane">{body}</main></div>
    <Modal title={dialog === 'account' ? '新增第三方账号' : dialog === 'content' ? '创建内容' : dialog === 'positioning' ? '发起账号定位' : dialog === 'reject-content' ? '退回内容修改' : '新增交谈记录'} open={Boolean(dialog)} onCancel={() => setDialog(undefined)} onOk={() => void submit()} confirmLoading={saving}><Form form={form} layout="vertical">{dialog === 'account' && <><Form.Item name="platformValue" label="平台" rules={[{ required: true }]}><Select options={platforms.map(x => ({ value: x.value, label: x.label }))} /></Form.Item>{accountFieldConfig?.fields.filter(field => field.enabled).map(accountField)}</>}{dialog === 'content' && <><Form.Item name="accountId" label="第三方账号" rules={[{ required: true }]}><Select options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item><Form.Item name="title" label="内容标题" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="topic" label="选题说明"><Input.TextArea rows={3} /></Form.Item><Form.Item name="contentClassValue" label="内容分类" rules={[{ required: true }]}><Select options={contentClasses.map(x => ({ value: x.value, label: x.label }))} /></Form.Item></>}{dialog === 'positioning' && <><Form.Item name="accountId" label="第三方账号" rules={[{ required: true }]}><Select options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item><Form.Item name="professionalRisk" valuePropName="checked"><Checkbox>存在专业风险</Checkbox></Form.Item><Form.Item name="layer1Json" label="定位表单" rules={[{ required: true }]}><Input.TextArea rows={6} /></Form.Item></>}{dialog === 'talk' && <><Form.Item name="accountId" label="关联账号（可选）"><Select allowClear options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item><Form.Item name="content" label="交谈内容" rules={[{ required: true, max: 2000 }]}><Input.TextArea rows={5} /></Form.Item></>}{dialog === 'reject-content' && <Form.Item name="reason" label="退回原因" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={4} /></Form.Item>}</Form></Modal></section>
}
