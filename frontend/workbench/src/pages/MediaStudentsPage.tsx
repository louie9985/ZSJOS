import { FileSearchOutlined, MessageOutlined, PlusOutlined, ReloadOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Cascader, Checkbox, DatePicker, Empty, Form, Input, InputNumber, Modal, Pagination, Radio, Select, Skeleton, Space, Steps, Switch, Tabs, Tag, Timeline, Tooltip, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import DetailFieldGrid from '../components/DetailFieldGrid'
import { NameAvatar } from '../components/LeadDetailOverview'
import LeadDetail from '../components/LeadDetail'
import LeadDetailOverview from '../components/LeadDetailOverview'
import OverflowToolbar, { type ToolbarAction } from '../components/OverflowToolbar'
import { ApiError, api, type AreaNode, type DictData, type DirectorTemplateSnapshot, type ManagedLead, type MediaAccountField, type MediaAccountFieldConfig, type MediaStudentDetail, type MediaStudentTalkRecord, type MyStudent, type StudentContactContext, type StudyPlanner } from '../services/api'
import { DICT_TYPE } from '../constants'
import { dictionaryDisplayLabel } from '../services/leadManagement'
import { hasPermission } from '../services/managementAccess'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 20
const labels: Record<string, string> = { active: '服务中', completed: '已完成', cancelled: '已取消', precheck: '资料预审', interview: '学员采访', positioning_ready: '账号定位准备', co_creating: '账号定位', ip_review: '待审核', operator_feasibility: '待运营确认', student_confirm: '待学员确认', trial_14d: '试运行', confirmed: '正式定位', archived: '已归档', topic: '选题', script: '脚本', in_production: '制作中', acceptance: '待验收', published: '已发布', rejected: '已退回', revising: '修改中' }
const statusLabel = (value?: string) => value ? labels[value] || value : '未记录'
const actionLabels: Record<string, string> = {
  COMPLETE_TOPIC: '完成选题', SUBMIT_PRODUCTION: '提交制作', SUBMIT_ACCEPTANCE: '提交验收',
  APPROVE_CONTENT: '通过验收', REJECT_CONTENT: '退回修改', START_CONTENT_REVISION: '开始修改',
  RESUBMIT_PRODUCTION: '重新提交', SUBMIT_POSITIONING_REVIEW: '提交审核',
  APPROVE_POSITIONING_FEASIBILITY: '复核通过', REJECT_POSITIONING_FEASIBILITY: '复核退回',
  CONFIRM_POSITIONING_TRIAL: '确认试跑', ARCHIVE_POSITIONING: '归档'
}
const errorText = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试'
const findAreaPath = (nodes: AreaNode[], targetId: number, parents: number[] = []): number[] | undefined => {
  for (const node of nodes) {
    const path = [...parents, node.id]
    if (node.id === targetId) return path
    const childPath = findAreaPath(node.children || [], targetId, path)
    if (childPath) return childPath
  }
  return undefined
}
export default function MediaStudentsPage({ permissions = [] }: { permissions?: string[] }) {
  const { message } = App.useApp()
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<MyStudent[]>([]), [detail, setDetail] = useState<MediaStudentDetail>()
  const [leadDetail, setLeadDetail] = useState<ManagedLead>(), [selectedServiceId, setSelectedServiceId] = useState<number>(), [selectedAccountId, setSelectedAccountId] = useState<number>()
  const [talks, setTalks] = useState<MediaStudentTalkRecord[]>([]), [selectedId, setSelectedId] = useState<number>()
  const [keyword, setKeyword] = useState(''), [search, setSearch] = useState(''), [pageNo, setPageNo] = useState(1), [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false), [detailLoading, setDetailLoading] = useState(false), [error, setError] = useState(''), [detailError, setDetailError] = useState(''), [leadError, setLeadError] = useState('')
  const [tab, setTab] = useState(params.get('tab') || 'overview'), [dialog, setDialog] = useState<'account' | 'content' | 'positioning' | 'talk' | 'reject-content' | 'reject-positioning' | 'precheck' | 'interview' | 'operator'>(), [saving, setSaving] = useState(false)
  const [directorContext, setDirectorContext] = useState<StudentContactContext>(), [operatorCandidates, setOperatorCandidates] = useState<StudyPlanner[]>([])
  const [platforms, setPlatforms] = useState<DictData[]>([]), [contentClasses, setContentClasses] = useState<DictData[]>([])
  const [categories, setCategories] = useState<DictData[]>([]), [channels, setChannels] = useState<DictData[]>([])
  const [categoryError, setCategoryError] = useState(false), [channelError, setChannelError] = useState(false)
  const [accountFieldConfig, setAccountFieldConfig] = useState<MediaAccountFieldConfig>(), [fieldDicts, setFieldDicts] = useState<Record<string, DictData[]>>({})
  const [areas, setAreas] = useState<AreaNode[]>([]), [legacyRegionText, setLegacyRegionText] = useState<string>()
  const [positioningTemplate, setPositioningTemplate] = useState<DirectorTemplateSnapshot>()
  const [rejectingContent, setRejectingContent] = useState<MediaStudentDetail['contents'][number]>()
  const [rejectingPositioning, setRejectingPositioning] = useState<MediaStudentDetail['positioningCards'][number]>()
  const [form] = Form.useForm<Record<string, unknown>>(), listRun = useRef(0), detailRun = useRef(0)

  const loadDetail = useCallback(async (personId: number, preferredServiceId?: number, preferredAccountId?: number) => {
    const run = ++detailRun.current; setSelectedId(personId); setDetailLoading(true); setDetailError(''); setLeadError('')
    try {
      const [value, records] = await Promise.all([api.mediaStudents.get(personId), api.mediaStudents.talks(personId)])
      const service = value.student.services.find(item => item.serviceRelationId === preferredServiceId) || value.student.services[0]
      let lead: ManagedLead | undefined
      if (service?.leadId) {
        try { lead = await api.managedLead(service.leadId) }
        catch (cause) {
          lead = undefined
          if (run === detailRun.current) setLeadError(cause instanceof ApiError && cause.code === 403
            ? '无权查看该课程客资' : errorText(cause))
        }
      }
      const context = service ? await api.studentContactContext(service.serviceRelationId) : undefined
      const accountId = value.accounts.some(item => item.id === preferredAccountId) ? preferredAccountId : value.accounts[0]?.id
      if (run === detailRun.current) { setDetail(value); setTalks(records); setSelectedServiceId(service?.serviceRelationId); setSelectedAccountId(accountId); setLeadDetail(lead); setDirectorContext(context) }
    }
    catch (cause) { if (run === detailRun.current) { setDetail(undefined); setLeadDetail(undefined); setTalks([]); setDetailError(cause instanceof ApiError && cause.code === 403 ? '无权查看该学员' : errorText(cause)) } }
    finally { if (run === detailRun.current) setDetailLoading(false) }
  }, [])
  const loadPage = useCallback(async (targetPage: number, preferred?: number) => {
    const run = ++listRun.current; setLoading(true); setError('')
    try { const result = await api.mediaStudents.page({ pageNo: targetPage, pageSize: PAGE_SIZE, keyword: keyword || undefined }); if (run !== listRun.current) return
      setRows(result.list); setTotal(result.total); setPageNo(targetPage); const target = preferred || Number(params.get('personId')) || result.list[0]?.personId
      if (target) await loadDetail(target, undefined, Number(params.get('accountId')) || undefined); else setDetail(undefined)
    } catch (cause) { if (run === listRun.current) { setRows([]); setDetail(undefined); setError(errorText(cause)) } } finally { if (run === listRun.current) setLoading(false) }
  }, [keyword, loadDetail, params])
  useEffect(() => { void loadPage(1) }, [keyword])
  useEffect(() => {
    void Promise.allSettled([api.dictDataByType(DICT_TYPE.LEAD_CATEGORY), api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL)]).then(([categoryResult, channelResult]) => {
      if (categoryResult.status === 'fulfilled') { setCategories(categoryResult.value); setCategoryError(false) } else setCategoryError(true)
      if (channelResult.status === 'fulfilled') { setChannels(channelResult.value); setChannelError(false) } else setChannelError(true)
    })
  }, [])

  const accountName = (id: number) => detail?.accounts.find(item => item.id === id)?.nickname || '未匹配账号'
  const open = async (type: typeof dialog, accountId?: number) => { form.resetFields(); setLegacyRegionText(undefined); if (accountId) form.setFieldValue('accountId', accountId); setDialog(type)
    if (type === 'precheck' || type === 'interview') {
      const stageForm = directorContext?.directorForms?.[type]
      const dictTypes = [...new Set((stageForm?.fields || []).filter(field => field.dictType).map(field => field.dictType!))]
      try {
        const [entries, areaRows] = await Promise.all([
          Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const)),
          type === 'interview' && (stageForm?.fields || []).some(field => field.type === 'region')
            ? (areas.length ? Promise.resolve(areas) : api.areaTree()) : Promise.resolve(areas)
        ])
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) })); setAreas(areaRows)
        const data = { ...(stageForm?.values || {}) }
        const existingRegion = data.region
        if (type === 'interview' && existingRegion && typeof existingRegion === 'object' && !Array.isArray(existingRegion)) {
          const code = Number((existingRegion as { code?: unknown }).code)
          data.region = Number.isFinite(code) ? findAreaPath(areaRows, code) : undefined
        } else if (type === 'interview' && typeof existingRegion === 'string') {
          setLegacyRegionText(existingRegion); delete data.region
        }
        form.setFieldsValue({ data, confirmed: false, interviewAt: stageForm?.interviewAt ? dayjs(stageForm.interviewAt) : directorContext?.defaultDirectorInterviewAt ? dayjs(directorContext.defaultDirectorInterviewAt) : undefined, submit: true })
      }
      catch (cause) { setDialog(undefined); message.error(errorText(cause)); return }
    }
    if (type === 'positioning') {
      const template = await api.positioningCard.publishedTemplate()
      setPositioningTemplate(template)
      form.setFieldsValue({ accountId, data: template.values || {}, trialEndDate: dayjs().add(directorContext?.directorTrialDays || 14, 'day') })
      const dictTypes = [...new Set(template.fields.filter(field => field.dictType).map(field => field.dictType!))]
      try {
        const entries = await Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const))
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) }))
      } catch (cause) { setDialog(undefined); message.error(errorText(cause)); return }
    }
    if (type === 'account') {
      try {
        const [platformRows, config] = await Promise.all([platforms.length ? platforms : api.dictDataByType('zsjos_account_platform'), api.mediaAccount.publishedFieldConfig()])
        setPlatforms(platformRows); setAccountFieldConfig(config)
        const dictTypes = [...new Set(config.fields.filter(field => field.enabled && field.dictType).map(field => field.dictType!))]
        const entries = await Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const))
        setFieldDicts(Object.fromEntries(entries))
      } catch (cause) { setDialog(undefined); message.error(errorText(cause)); return }
    }
    if (type === 'operator' && selectedServiceId) {
      try { setOperatorCandidates(await api.studentCollaboratorCandidates(selectedServiceId, 'operator')) }
      catch (cause) { setDialog(undefined); message.error(errorText(cause)); return }
    }
    if (type === 'content' && !contentClasses.length) setContentClasses(await api.dictDataByType('zsjos_content_class')) }
  const submit = async () => { if (!detail || !dialog) return
    try { const values = await form.validateFields(); setSaving(true)
      if (dialog === 'account') await api.mediaAccount.create({ studentPersonId: detail.student.personId, platformValue: String(values.platformValue), platformLabelSnapshot: platforms.find(x => x.value === values.platformValue)?.label || '', detailValues: (values.detailValues || {}) as Record<string, unknown> })
      if (dialog === 'content') await api.mediaContent.create({ ...values, contentClassLabelSnapshot: contentClasses.find(x => x.value === values.contentClassValue)?.label || '' } as never)
      if (dialog === 'positioning') await api.positioningCard.create({ accountId: Number(values.accountId), studentPersonId: detail.student.personId, serviceRelationId: selectedService?.serviceRelationId, templateId: positioningTemplate?.templateId, trialEndDate: values.trialEndDate ? dayjs(values.trialEndDate as never).format('YYYY-MM-DD') : undefined, values: (values.data || {}) as Record<string, unknown> })
      if (dialog === 'talk') await api.mediaStudents.createTalk(detail.student.personId, values as { accountId?: number; content: string })
      if ((dialog === 'precheck' || dialog === 'interview') && selectedService && directorContext) {
        const stage = dialog
        const shouldSubmit = values.submit !== false
        if (stage === 'precheck' && shouldSubmit && !values.interviewAt) throw new Error('提交资料预审前请填写访谈预约时间')
        const interviewAt = values.interviewAt && typeof (values.interviewAt as { format?: (value: string) => string }).format === 'function' ? (values.interviewAt as { format: (value: string) => string }).format('YYYY-MM-DDTHH:mm:ss') : undefined
        if (stage === 'precheck' && !values.confirmed) throw new Error('请先确认学员和服务资料无误')
        const data = stage === 'precheck' ? {} : { ...((values.data || {}) as Record<string, unknown>) }
        if (stage === 'interview') {
          const regionPath = data.region
          if (Array.isArray(regionPath) && regionPath.length) data.region = { code: Number(regionPath[regionPath.length - 1]) }
          else if (!regionPath && legacyRegionText) data.region = legacyRegionText
        }
        const request = { interviewAt, data, version: directorContext.version, idempotencyKey: crypto.randomUUID() }
        if (stage === 'precheck' && shouldSubmit) await api.studentDirectorPrecheckSubmit(selectedService.serviceRelationId, request)
        else if (stage === 'precheck') await api.studentDirectorPrecheckDraft(selectedService.serviceRelationId, request)
        else if (shouldSubmit) await api.studentDirectorInterviewSubmit(selectedService.serviceRelationId, request)
        else await api.studentDirectorInterviewDraft(selectedService.serviceRelationId, request)
      }
      if (dialog === 'operator' && selectedService) await api.studentAssignCollaborator(selectedService.serviceRelationId, { collaboratorType: 'operator', userId: Number(values.userId), version: selectedService.version || 0, correctionReason: values.correctionReason ? String(values.correctionReason) : undefined, idempotencyKey: crypto.randomUUID() })
      if (dialog === 'reject-content' && rejectingContent) await api.mediaContent.rejectAcceptance(rejectingContent.id, rejectingContent.version, String(values.reason))
      if (dialog === 'reject-positioning' && rejectingPositioning) await api.positioningCard.operatorReject(rejectingPositioning.id, rejectingPositioning.version, String(values.reason))
      setDialog(undefined); message.success('已保存'); await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId)
    } catch (cause) { if (!(cause as { errorFields?: unknown }).errorFields) message.error(errorText(cause)) } finally { setSaving(false) } }
  const contentAction = async (row: MediaStudentDetail['contents'][number], action: string) => { try {
    if (action === 'REJECT_CONTENT') { form.resetFields(); setRejectingContent(row); setDialog('reject-content'); return }
    if (action === 'COMPLETE_TOPIC') await api.mediaContent.completeTopic(row.id, row.version); else if (action === 'SUBMIT_PRODUCTION') await api.mediaContent.submitProduction(row.id, row.version); else if (action === 'SUBMIT_ACCEPTANCE') await api.mediaContent.submitAcceptance(row.id, row.version); else if (action === 'APPROVE_CONTENT') await api.mediaContent.approveAcceptance(row.id, row.version); else if (action === 'START_CONTENT_REVISION') await api.mediaContent.startRevision(row.id, row.version); else if (action === 'RESUBMIT_PRODUCTION') await api.mediaContent.resubmitProduction(row.id, row.version); else return
    if (selectedId) await loadDetail(selectedId, selectedServiceId, selectedAccountId) } catch (cause) { message.error(errorText(cause)) } }
  const positioningAction = async (row: MediaStudentDetail['positioningCards'][number], action: string) => { try {
    if (action === 'REJECT_POSITIONING_FEASIBILITY') { form.resetFields(); setRejectingPositioning(row); setDialog('reject-positioning'); return }
    if (action === 'SUBMIT_POSITIONING_REVIEW') await api.positioningCard.submitReview(row.id, row.version); else if (action === 'APPROVE_POSITIONING_FEASIBILITY') await api.positioningCard.operatorApprove(row.id, row.version); else if (action === 'CONFIRM_POSITIONING_TRIAL') await api.positioningCard.confirmTrial(row.id, row.version); else if (action === 'ARCHIVE_POSITIONING') await api.positioningCard.archive(row.id, row.version); else return
    if (selectedId) await loadDetail(selectedId, selectedServiceId, selectedAccountId) } catch (cause) { message.error(errorText(cause)) } }
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
  const directorField = (field: NonNullable<StudentContactContext['formFields']>[number]) => {
    const name = ['data', field.key], rules = field.required ? [{ validator: (_: unknown, value: unknown) => {
      const empty = value == null || value === '' || Array.isArray(value) && value.length === 0
      return form.getFieldValue('submit') === false || !empty ? Promise.resolve() : Promise.reject(new Error(`请填写${field.title}`))
    } }] : undefined
    if (field.type === 'textarea') return <Form.Item key={field.key} name={name} label={field.title} rules={rules}><Input.TextArea rows={4} /></Form.Item>
    if (field.type === 'number') return <Form.Item key={field.key} name={name} label={field.title} rules={rules}><InputNumber style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'date' || field.type === 'datetime') return <Form.Item key={field.key} name={name} label={field.title} rules={rules}><DatePicker showTime={field.type === 'datetime'} style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'region') return <div key={field.key}>{legacyRegionText && <Alert type="warning" showIcon message={`历史地区：${legacyRegionText}`} description="历史文本仅保留用于草稿兼容，正式提交前请从系统地区中重新选择。"/>}<Form.Item name={name} label={field.title} rules={rules}><Cascader options={areas} fieldNames={{ label: 'name', value: 'id', children: 'children' }} changeOnSelect showSearch placeholder="请选择地区" style={{ width: '100%' }} /></Form.Item></div>
    if (field.type === 'dict' || field.type === 'select' || field.type === 'multi_select' || field.type === 'radio' || field.type === 'checkbox_group') {
      const options = (fieldDicts[field.dictType || ''] || []).map(item => ({ value: item.value, label: item.label }))
      if (field.type === 'radio') return <Form.Item key={field.key} name={name} label={field.title} rules={rules}><Radio.Group options={options} /></Form.Item>
      return <Form.Item key={field.key} name={name} label={field.title} rules={rules}><Select mode={field.multiple || field.type === 'multi_select' || field.type === 'checkbox_group' ? 'multiple' : undefined} options={options} /></Form.Item>
    }
    if (field.type === 'checkbox') return <Form.Item key={field.key} name={name} valuePropName="checked"><Checkbox>{field.title}</Checkbox></Form.Item>
    return <Form.Item key={field.key} name={name} label={field.title} rules={rules}><Input /></Form.Item>
  }

  const selectedService = detail?.student.services.find(item => item.serviceRelationId === selectedServiceId) || detail?.student.services[0]
  const selectedAccount = detail?.accounts.find(item => item.id === selectedAccountId)
  const selectedPositioningCards = detail?.positioningCards.filter(item => item.accountId === selectedAccountId) || []
  const selectedContents = detail?.contents.filter(item => item.accountId === selectedAccountId) || []
  const directorStage = directorContext?.directorStage || selectedService?.directorStage || 'precheck'
  const studentTaskLine = [
    { key: 'precheck', label: '资料预审', status: directorStage === 'precheck' ? 'current' as const : 'done' as const, detail: directorStage === 'precheck' ? '待编导审核资料并预约访谈' : '资料预审已提交' },
    { key: 'interview', label: '学员采访', status: directorStage === 'positioning_ready' ? 'done' as const : directorStage === 'interview' ? 'current' as const : 'pending' as const, detail: '采集学员级基础信息' }
  ]
  const taskLine = [...studentTaskLine, ...(selectedAccount?.taskLine || [])]
  const openDirectorAction = (action: string) => {
    if (action === 'DIRECTOR_PRECHECK') void open('precheck')
    if (action === 'DIRECTOR_INTERVIEW') void open('interview')
    if (action === 'ASSIGN_OPERATOR') void open('operator')
  }
  const selectService = async (serviceRelationId: number) => {
    const service = detail?.student.services.find(item => item.serviceRelationId === serviceRelationId)
    if (!service) return
    const run = ++detailRun.current
    setDetailLoading(true); setDetailError(''); setLeadError('')
    try {
      const [contextResult, leadResult] = await Promise.allSettled([
        api.studentContactContext(serviceRelationId),
        service.leadId ? api.managedLead(service.leadId) : Promise.resolve(undefined)
      ])
      if (contextResult.status === 'rejected') throw contextResult.reason
      if (run === detailRun.current) {
        setSelectedServiceId(serviceRelationId)
        setDirectorContext(contextResult.value)
        if (leadResult.status === 'fulfilled') setLeadDetail(leadResult.value)
        else {
          setLeadDetail(undefined)
          setLeadError(leadResult.reason instanceof ApiError && leadResult.reason.code === 403 ? '无权查看该课程客资' : errorText(leadResult.reason))
        }
      }
    } catch (cause) {
      if (run === detailRun.current) {
        setDetailError(cause instanceof ApiError && cause.code === 403 ? '无权查看该课程服务' : errorText(cause))
      }
    } finally { if (run === detailRun.current) setDetailLoading(false) }
  }
  const selectAccount = (accountId: number) => {
    if (!detail) return
    setSelectedAccountId(accountId)
    setParams({ personId: String(detail.student.personId), tab: tab === 'overview' ? 'accounts' : tab, accountId: String(accountId) })
  }
  const latestContent = selectedContents[0]
  const mediaOverview = detail && leadDetail && selectedService ? <LeadDetailOverview
    lead={{ ...leadDetail, submittedName: detail.student.name || leadDetail.submittedName, submittedMobile: detail.student.mobile || leadDetail.submittedMobile, submittedWechatId: detail.student.wechatId || leadDetail.submittedWechatId }}
    categoryLabel={value => dictionaryDisplayLabel(categories, value, categoryError)}
    channelLabel={value => dictionaryDisplayLabel(channels, value, channelError)}
    showFollowUp={false}
    studentService={selectedService}
    toolbar={<OverflowToolbar actions={directorContext?.availableActions.filter(action => ['DIRECTOR_PRECHECK', 'DIRECTOR_INTERVIEW', 'ASSIGN_OPERATOR'].includes(action)).map<ToolbarAction>(action => ({
      key: action,
      icon: action === 'DIRECTOR_PRECHECK' ? <FileSearchOutlined /> : action === 'DIRECTOR_INTERVIEW' ? <MessageOutlined /> : <UserSwitchOutlined />,
      label: action === 'DIRECTOR_PRECHECK' ? '资料预审' : action === 'DIRECTOR_INTERVIEW' ? '学员采访' : '指派运营',
      onClick: () => openDirectorAction(action)
    })) || []} />}
    slots={{
      latestActivity: <div className="lead-latest-followup"><div className="lead-section-header"><Typography.Text strong>最新内容</Typography.Text>{latestContent?.lastActivityAt && <Typography.Text type="secondary">{formatTimestamp(latestContent.lastActivityAt)}</Typography.Text>}</div>{latestContent ? <div className="lead-latest-followup-body"><div className="lead-latest-followup-tags"><Tag>{statusLabel(latestContent.status)}</Tag><Tag color="blue">{accountName(latestContent.accountId)}</Tag></div><Typography.Paragraph className="lead-latest-followup-remark">{latestContent.title || latestContent.contentNo}</Typography.Paragraph></div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内容" />}</div>,
      timeline: <div className="lead-flow-timeline"><div className="lead-section-header"><Typography.Text strong>操作时间线</Typography.Text></div>{detail.operationTimeline.length ? <Timeline items={detail.operationTimeline.slice(0, 8).map(item => ({ children: <div className="media-students-timeline-item"><strong>{item.title}</strong><span>{item.detail}</span><Typography.Text type="secondary">{item.operatorName || '系统记录'} · {formatTimestamp(item.occurredAt)}</Typography.Text></div> }))} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无操作记录" />}</div>,
      mainBeforeColumns: directorContext?.operatorAssignmentConflict ? <Alert type="warning" showIcon message="该学员存在多个运营归属，请通过“指派运营”统一修正" /> : undefined,
      taskStatus: <>
        <div className="lead-status-pipeline">
          {taskLine.map((item, index) => <div key={item.key} className={`lead-status-node ${item.status === 'done' ? 'done' : item.status === 'current' ? 'current' : 'future'}`}>
            <div className="lead-status-dot" />
            {index < taskLine.length - 1 && <div className="lead-status-connector" />}
            <span className="lead-status-step-label">{item.label}</span>
          </div>)}
        </div>
        <div className="lead-status-divider" />
        <div className="lead-status-labels">
          <div className={`lead-status-label-item color-${directorStage === 'positioning_ready' ? 'green' : 'blue'}`}>
            <span className="lead-status-label-name">学员级阶段</span>
            <span className="lead-status-label-value">{statusLabel(directorStage)}</span>
          </div>
          <div className={`lead-status-label-item color-${selectedAccount ? 'green' : 'gray'}`}>
            <span className="lead-status-label-name">账号级阶段</span>
            <span className="lead-status-label-value">{selectedAccount ? `${selectedAccount.nickname || selectedAccount.accountNo} · ${statusLabel(selectedPositioningCards[0]?.status)}` : '尚无账号'}</span>
          </div>
          <div className={`lead-status-label-item color-${directorContext?.directorInterviewAt ? 'blue' : 'gray'}`}>
            <span className="lead-status-label-name">访谈预约</span>
            <span className="lead-status-label-value">{directorContext?.directorInterviewAt ? formatTimestamp(directorContext.directorInterviewAt) : '未预约'}</span>
          </div>
        </div>
      </>,
      sidebarBeforeStatus: <><section className="lead-card"><div className="lead-card-header"><Typography.Text strong>课程服务信息</Typography.Text></div><DetailFieldGrid columns={1} items={[{ key: 'course', label: '课程', value: selectedService.courseName || selectedService.skuName }, { key: 'sku', label: '具体方案', value: selectedService.skuName }, { key: 'category', label: '分类', value: selectedService.categoryPath?.join(' / ') }, { key: 'attributes', label: '课程属性', value: selectedService.attributeValues?.join(' / ') }, { key: 'order', label: '订单号', value: selectedService.orderNo }, { key: 'status', label: '服务状态', value: statusLabel(selectedService.status) }, { key: 'planner', label: '学习规划师', value: selectedService.ownerUserName || '未分配' }, { key: 'director', label: '编导', value: selectedService.contentDirectorUserName || '未分配' }, { key: 'operator', label: '运营负责人', value: selectedService.operatorUserName || '未分配' }]} /></section><section className="lead-card"><div className="lead-card-header"><Typography.Text strong>待处理</Typography.Text></div><div className="media-students-stats"><div><strong>{detail.pendingStats.accountCount}</strong><span>第三方账号</span></div><div><strong>{detail.pendingStats.positioningCount}</strong><span>定位任务</span></div><div><strong>{detail.pendingStats.contentCount}</strong><span>内容任务</span></div><div><strong>{detail.pendingStats.productionCount}</strong><span>拍剪任务</span></div></div></section></>
    }}
  /> : undefined

  const tabs = detail ? [
    { key: 'accounts', label: '账号', children: <><section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>学员账号</Typography.Title>{hasPermission(permissions, 'zsjos:media-account:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void open('account')}>新增账号</Button>}</Space>{detail.accounts.length ? <div className="media-students-record-list">{detail.accounts.map(x => <div className={`media-students-record${selectedAccountId === x.id ? ' active' : ''}`} key={x.id} onClick={() => selectAccount(x.id)}><div><strong>{x.nickname || x.accountNo}</strong><span>{x.platformLabel || '平台未记录'} · {x.accountNo}</span>{x.detailSnapshots?.length ? <span>{x.detailSnapshots.map(field => `${field.label}：${field.displayValue || '未记录'}`).join(' · ')}</span> : <span>详情字段未记录</span>}</div><Space><Tag>{statusLabel(x.stage)}</Tag>{hasPermission(permissions, 'zsjos:positioning-card:create') && <Button onClick={event => { event.stopPropagation(); void open('positioning', x.id) }}>填写定位卡</Button>}</Space></div>)}</div> : <Empty description="暂无账号" />}</section><section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>账号定位卡</Typography.Title></Space>{selectedAccount ? selectedPositioningCards.length ? selectedPositioningCards.map(x => <div className="media-students-record" key={x.id}><div><strong>{x.cardNo}</strong><span>{accountName(x.accountId)} · 版本 {x.versionNo || 1}</span></div><Space><Tag>{statusLabel(x.status)}</Tag>{actions(x.availableActions, action => void positioningAction(x, action))}</Space></div>) : <Empty description="当前账号暂无定位卡" /> : <Empty description="请选择账号" />}</section></> },
    { key: 'content', label: '内容生产历史', children: <section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>内容生产历史</Typography.Title>{hasPermission(permissions, 'zsjos:content:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void open('content')}>创建内容</Button>}</Space>{selectedAccount ? selectedContents.map(x => <div className="media-students-record" key={x.id}><div><strong>{x.title || x.contentNo}</strong><span>{accountName(x.accountId)} · {x.contentNo}</span></div><Space><Tag>{statusLabel(x.status)}</Tag>{actions(x.availableActions, action => void contentAction(x, action))}</Space></div>) : <Empty description="请选择账号" />}</section> },
    { key: 'student', label: '学员信息', children: <><section className="media-students-card"><Typography.Title level={5}>学员级业务资料</Typography.Title><DetailFieldGrid columns={2} items={[{ key: 'precheck', label: '资料预审', value: statusLabel(selectedService?.directorStage === 'precheck' ? 'precheck' : 'completed') }, { key: 'interview', label: '学员采访', value: statusLabel(selectedService?.directorStage || 'precheck') }, { key: 'appointment', label: '访谈预约', value: selectedService?.directorInterviewAt ? formatTimestamp(selectedService.directorInterviewAt) : '未预约' }, { key: 'lead-no', label: '客资编号', value: selectedService?.leadNo || detail.student.leadNo || '未记录' }]} /></section><section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>沟通记录</Typography.Title><Button type="primary" icon={<PlusOutlined />} onClick={() => void open('talk')}>新增记录</Button></Space>{talks.length ? <Timeline items={talks.map(x => ({ children: <><strong>{x.operatorUserName || '操作人'}</strong><div>{x.content}</div><Typography.Text type="secondary">{formatTimestamp(x.occurredAt)} · {x.accountId ? accountName(x.accountId) : '学员级'}</Typography.Text></> }))} /> : <Empty description="暂无沟通记录" />}</section></> }
  ].sort((a, b) => ['accounts', 'student', 'content'].indexOf(a.key) - ['accounts', 'student', 'content'].indexOf(b.key)) : []

  const body = detailLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : detailError ? <Alert type="warning" showIcon message={detailError} action={selectedId ? <Button size="small" onClick={() => void loadDetail(selectedId, selectedServiceId)}>重试</Button> : undefined} /> : !detail ? <Empty description="从左侧选择一名学员" /> : leadDetail && selectedService ? <LeadDetail
    lead={{ ...leadDetail, submittedName: detail.student.name || leadDetail.submittedName, submittedMobile: detail.student.mobile || leadDetail.submittedMobile, submittedWechatId: detail.student.wechatId || leadDetail.submittedWechatId }}
    categories={categories}
    categoryLabel={value => dictionaryDisplayLabel(categories, value, categoryError)}
    channelLabel={value => dictionaryDisplayLabel(channels, value, channelError)}
    mode="student-readonly"
    autoExpandFollowUp={false}
    baseTabs={['overview']}
    activeTab={tab}
    onTabChange={value => { setTab(value); setParams({ personId: String(detail.student.personId), tab: value }) }}
    onDirtyChange={() => undefined}
    onChanged={() => void loadDetail(detail.student.personId, selectedService.serviceRelationId)}
    studentService={selectedService}
    overviewContent={mediaOverview}
    contextHeader={<div className="media-students-service-context"><Typography.Text strong>课程上下文</Typography.Text><Select value={selectedService.serviceRelationId} onChange={value => void selectService(value)} options={detail.student.services.map(service => ({ value: service.serviceRelationId, label: `${service.courseName || service.skuName || '课程服务'} · ${service.orderNo || service.orderId || '订单未记录'}` }))} /></div>}
    extraTabs={tabs}
  /> : <div className="media-students-detail"><div className="media-students-detail-hero"><div className="media-students-identity"><NameAvatar name={detail.student.name || '学员'} size={44} /><div><Typography.Title level={4}>{detail.student.name || '未填写姓名'}</Typography.Title><Typography.Text type="secondary">{detail.student.leadNo || '暂无客资编号'}</Typography.Text></div></div></div><Alert type={leadError ? "error" : "warning"} showIcon message={leadError || "当前课程服务尚未关联客资详情"} action={leadError && selectedService?.leadId ? <Button size="small" onClick={() => void selectService(selectedService.serviceRelationId)}>重试</Button> : undefined} /><Tabs activeKey={tab === 'overview' ? 'accounts' : tab} onChange={value => { setTab(value); setParams({ personId: String(detail.student.personId), tab: value }) }} items={tabs} /></div>

  return <section className="workspace-page media-students-page"><header className="media-students-filter-shell"><Typography.Title level={4}>我的学员</Typography.Title><Tooltip title="刷新"><Button icon={<ReloadOutlined />} onClick={() => void loadPage(pageNo, selectedId)} /></Tooltip></header><div className="media-students-inbox-layout"><aside className="media-students-list-pane"><div className="media-students-toolbar"><Input.Search allowClear value={search} onChange={e => setSearch(e.target.value)} onSearch={value => setKeyword(value.trim())} placeholder="搜索姓名、手机号或客资编号" /></div>{error && <Alert type="error" showIcon message={error} />}<div className="media-students-scroll">{loading && !rows.length ? <Skeleton active /> : rows.map(x => <button type="button" className={`media-students-item${selectedId === x.personId ? ' active' : ''}`} key={x.personId} onClick={() => void loadDetail(x.personId)}><NameAvatar name={x.name || '学员'} size={36} /><span className="media-students-item-copy"><strong>{x.name || '未填写姓名'}</strong><span>{x.leadNo || '暂无客资编号'}</span><span>{x.mobile || '无手机号'} · {x.services.length} 项服务</span></span></button>)}</div>{total > PAGE_SIZE && <Pagination simple current={pageNo} pageSize={PAGE_SIZE} total={total} onChange={value => void loadPage(value)} />}</aside><main className="media-students-detail-pane">{body}</main></div>
    <Modal width={dialog === 'interview' || dialog === 'positioning' ? 860 : undefined} title={dialog === 'account' ? '新增第三方账号' : dialog === 'content' ? '创建内容' : dialog === 'positioning' ? '填写账号定位卡' : dialog === 'reject-content' ? '退回内容修改' : dialog === 'reject-positioning' ? '退回定位卡修改' : dialog === 'precheck' ? '资料预审' : dialog === 'interview' ? '学员采访' : dialog === 'operator' ? '指派运营' : '新增沟通记录'} open={Boolean(dialog)} onCancel={() => setDialog(undefined)} onOk={() => void submit()} confirmLoading={saving}><Form form={form} layout="vertical">{dialog === 'account' && <><Form.Item name="platformValue" label="平台" rules={[{ required: true }]}><Select options={platforms.map(x => ({ value: x.value, label: x.label }))} /></Form.Item>{accountFieldConfig?.fields.filter(field => field.enabled).map(accountField)}</>}{dialog === 'content' && <><Form.Item name="accountId" label="第三方账号" rules={[{ required: true }]}><Select options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item><Form.Item name="title" label="内容标题" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="topic" label="选题说明"><Input.TextArea rows={3} /></Form.Item><Form.Item name="contentClassValue" label="内容分类" rules={[{ required: true }]}><Select options={contentClasses.map(x => ({ value: x.value, label: x.label }))} /></Form.Item></>}{dialog === 'positioning' && <><Form.Item name="accountId" label="第三方账号" rules={[{ required: true }]}><Select options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item>{positioningTemplate?.fields.map(directorField)}<Form.Item name="trialEndDate" label="试运行结束日期" rules={[{ required: true }]}><DatePicker style={{width:'100%'}} /></Form.Item></>}{dialog === 'precheck' && <><Alert type="info" showIcon message="请核对学员、课程、订单及服务归属信息。资料预审不填写业务表单。"/><Form.Item name="confirmed" valuePropName="checked" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false||v?Promise.resolve():Promise.reject(new Error('请确认资料无误'))}]}><Checkbox>已确认资料无误</Checkbox></Form.Item><Form.Item name="interviewAt" label="访谈预约时间" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false||v?Promise.resolve():Promise.reject(new Error('请选择访谈预约时间'))}]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item><Form.Item name="submit" initialValue={true} valuePropName="checked"><Checkbox>确认完成资料预审</Checkbox></Form.Item></>}{dialog === 'interview' && <>{(directorContext?.directorForms?.interview?.fields || []).map(directorField)}<Form.Item name="submit" initialValue={true} valuePropName="checked"><Checkbox>提交并完成学员采访</Checkbox></Form.Item></>}{dialog === 'operator' && <><Form.Item name="userId" label="运营负责人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={operatorCandidates.map(user => ({ value: user.id, label: user.nickname }))} /></Form.Item>{directorContext?.operatorAssignmentConflict && <Form.Item name="correctionReason" label="统一归属说明" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={3} /></Form.Item>}</>}{dialog === 'talk' && <><Form.Item name="accountId" label="关联账号（可选）"><Select allowClear options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item><Form.Item name="content" label="沟通内容" rules={[{ required: true, max: 2000 }]}><Input.TextArea rows={5} /></Form.Item></>}{(dialog === 'reject-content' || dialog === 'reject-positioning') && <Form.Item name="reason" label="退回原因" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={4} /></Form.Item>}</Form></Modal></section>
}
