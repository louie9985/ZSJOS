import { useEffect, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Select, Space, Spin, Tabs, Typography, message } from 'antd'
import { BellOutlined, CheckOutlined, ClockCircleOutlined, CloseOutlined, EditOutlined, FileAddOutlined, PlusOutlined, WarningOutlined } from '@ant-design/icons'
import { api, type DictData, type LeadAppealEvidence, type ManagedLead } from '../services/api'
import { applyInvalidRemarkTemplate } from '../services/leadManagement'
import { DICT_TYPE } from '../constants'
import { defaultLeadDetailTab, detailTabsFromProjection, resolveLeadDetailTab, type LeadDetailMode, type LeadDetailTab } from '../services/leadFollowUp'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import LeadDetailOverview from './LeadDetailOverview'
import LeadFollowUpPanel from './LeadFollowUpPanel'
import LeadAppealPanel from './LeadAppealPanel'
import LeadFlowHistoryPanel from './LeadFlowHistoryPanel'
import LeadAppealEvidenceUpload from './LeadAppealEvidenceUpload'
import LeadBasicInfoModal from './LeadBasicInfoModal'
import LeadComplaintPanel from './LeadComplaintPanel'
import LeadCustomerOrders from './LeadCustomerOrders'
import OverflowToolbar, { type ToolbarAction } from './OverflowToolbar'
import SalesOrderEntryModal from './SalesOrderEntryModal'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'
import FollowUpModal from './FollowUpModal'
import { formatTimestamp } from '../services/time'

export default function LeadDetail({ lead, categories, categoryLabel, channelLabel, mode, autoExpandFollowUp, initialTab, onDirtyChange, onChanged }: {
  lead: ManagedLead
  categories: DictData[]
  categoryLabel: (value?: string) => string
  channelLabel: (value?: string) => string
  mode: LeadDetailMode
  autoExpandFollowUp: boolean
  initialTab?: LeadDetailTab
  onDirtyChange: (dirty: boolean) => void
  onChanged: () => void
}) {
  const readOnly = mode === 'manager-readonly' || mode === 'student-readonly'
  const visibleTabs = detailTabsFromProjection(lead.visibleTabs)
  const requestedInitialTab = initialTab || defaultLeadDetailTab(autoExpandFollowUp)
  const visibleTabKey = visibleTabs.join(',')
  const [activeTab, setActiveTab] = useState<LeadDetailTab>(resolveLeadDetailTab(visibleTabs, requestedInitialTab))
  const [followUpTotal, setFollowUpTotal] = useState(0)
  const [followUpRefreshVersion, setFollowUpRefreshVersion] = useState(0)
  const [orderTotal, setOrderTotal] = useState<number>()
  const [invalidOpen, setInvalidOpen] = useState(false)
  const [invalidReasons, setInvalidReasons] = useState<DictData[]>([])
  const [invalidReasonLoading, setInvalidReasonLoading] = useState(false)
  const [invalidReasonError, setInvalidReasonError] = useState('')
  const [invalidRemarkTemplates, setInvalidRemarkTemplates] = useState<DictData[]>([])
  const [invalidRemarkTemplateLoading, setInvalidRemarkTemplateLoading] = useState(false)
  const [invalidRemarkTemplateError, setInvalidRemarkTemplateError] = useState('')
  const [invalidReason, setInvalidReason] = useState<string>()
  const [invalidDescription, setInvalidDescription] = useState('')
  const [invalidEvidence, setInvalidEvidence] = useState<DeferredUploadItem<LeadAppealEvidence>[]>([])
  const { submitting: qualificationSaving, run: runQualification, resetIntent: resetQualificationIntent } = useSubmissionGuard()
  const [followUpOpen, setFollowUpOpen] = useState(true)
  const [followUpModalOpen, setFollowUpModalOpen] = useState(false)
  const [followUpFormDirty, setFollowUpFormDirty] = useState(false)
  const [basicInfoOpen, setBasicInfoOpen] = useState(false)
  const [basicInfoDirty, setBasicInfoDirty] = useState(false)
  const [validOpen, setValidOpen] = useState(false)
  const [validCategory, setValidCategory] = useState<string | undefined>(lead.leadCategory)
  const [validRemark, setValidRemark] = useState('')
  const [validTemplates, setValidTemplates] = useState<DictData[]>([])
  const [validTemplateError, setValidTemplateError] = useState('')
  const [salesOrderOpen, setSalesOrderOpen] = useState(false)
  const [submitterSupplementOpen, setSubmitterSupplementOpen] = useState(false)
  const [urgeOpen, setUrgeOpen] = useState(false)
  const [complaintOpen, setComplaintOpen] = useState(false)
  const [urgeReason, setUrgeReason] = useState('')
  const [complaintReason, setComplaintReason] = useState('')
  const [submitterActionSaving, setSubmitterActionSaving] = useState(false)
  const [validConfirmOpen, setValidConfirmOpen] = useState(false)
  const [repurchaseOpen, setRepurchaseOpen] = useState(false)
  const [invalidConfirmOpen, setInvalidConfirmOpen] = useState(false)
  const closeInvalid = () => { setInvalidConfirmOpen(false); setInvalidOpen(false) }
  const closeValid = () => { setValidConfirmOpen(false); setValidOpen(false) }
  const actions = readOnly ? new Map<string, NonNullable<ManagedLead['availableActions']>[number]>()
    : new Map((lead.availableActions || []).map(item => [item.code, item]))

  useEffect(() => { onDirtyChange(readOnly ? false : followUpFormDirty || basicInfoDirty) },
    [basicInfoDirty, followUpFormDirty, onDirtyChange, readOnly])

  const judgeValid = async () => {
    setValidConfirmOpen(false)
    await runQualification(async ({ idempotencyKey, complete }) => {
      await api.judgeLeadValid(lead.id, { leadCategory: validCategory, remark: validRemark.trim(), idempotencyKey })
      complete(); message.success('已判定为有效客资'); setValidOpen(false); setValidRemark(''); onChanged()
    }).catch(error => message.error(error instanceof Error ? error.message : '有效判定失败'))
  }
  const prepareJudgeValid = () => { if (!validRemark.trim()) message.warning('请填写有效备注'); else setValidConfirmOpen(true) }
  const openValid = async () => {
    resetQualificationIntent(); setValidCategory(lead.leadCategory); setValidRemark(''); setValidOpen(true); setValidTemplateError('')
    try { setValidTemplates(await api.dictDataByType(DICT_TYPE.LEAD_VALID_REMARK_TEMPLATE)) }
    catch (error) { setValidTemplates([]); setValidTemplateError(error instanceof Error ? error.message : '快捷备注加载失败') }
  }
  const loadInvalidReasons = async () => {
    setInvalidReasonLoading(true); setInvalidReasonError('')
    try { setInvalidReasons(await api.dictDataByType(DICT_TYPE.LEAD_INVALID_REASON)) }
    catch (error) { setInvalidReasons([]); setInvalidReasonError(error instanceof Error ? error.message : '无效原因加载失败') }
    finally { setInvalidReasonLoading(false) }
  }
  const loadInvalidRemarkTemplates = async () => {
    setInvalidRemarkTemplateLoading(true); setInvalidRemarkTemplateError('')
    try { setInvalidRemarkTemplates(await api.dictDataByType(DICT_TYPE.LEAD_INVALID_REMARK_TEMPLATE)) }
    catch (error) { setInvalidRemarkTemplates([]); setInvalidRemarkTemplateError(error instanceof Error ? error.message : '快捷备注加载失败') }
    finally { setInvalidRemarkTemplateLoading(false) }
  }
  const openInvalid = () => {
    resetQualificationIntent(); setInvalidOpen(true)
    if (!invalidReasons.length && !invalidReasonLoading) void loadInvalidReasons()
    if (!invalidRemarkTemplates.length && !invalidRemarkTemplateLoading) void loadInvalidRemarkTemplates()
  }
  const judgeInvalid = async () => {
    setInvalidConfirmOpen(false)
    const reasonCode = invalidReason
    if (!reasonCode || !invalidDescription.trim()) return
    await runQualification(async ({ idempotencyKey, complete }) => {
      const uploadResult = await uploadDeferredFiles(invalidEvidence, api.uploadLeadQualificationImage, setInvalidEvidence)
      if (uploadResult.failed) { message.error('有判定附件上传失败，请重试失败项'); return }
      await api.judgeLeadInvalid(lead.id, { reasonCode, description: invalidDescription.trim(), attachments: uploadResult.items.filter(item => item.uploaded).map(item => ({ infraFileId: item.uploaded!.infraFileId })), idempotencyKey })
      complete(); message.success('已判定为无效客资'); setInvalidOpen(false); setInvalidReason(undefined); setInvalidDescription(''); setInvalidEvidence([]); onChanged()
    }).catch(error => message.error(error instanceof Error ? error.message : '无效判定失败'))
  }
  const prepareJudgeInvalid = () => {
    if (!invalidReason || !invalidDescription.trim()) { message.warning('请选择无效原因并填写备注'); return }
    setInvalidConfirmOpen(true)
  }

  useEffect(() => {
    setActiveTab(resolveLeadDetailTab(visibleTabs, requestedInitialTab)); setFollowUpTotal(0); setOrderTotal(undefined); setFollowUpOpen(!readOnly)
  // visibleTabKey tracks server projection changes without resetting on every render.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lead.id, readOnly, requestedInitialTab, visibleTabKey])

  const handleStandaloneFollowUpSuccess = () => {
    setFollowUpRefreshVersion(current => current + 1)
    onChanged()
  }

  const toolbarActions: ToolbarAction[] = [
    actions.has('ADD_FOLLOW_UP') && { key: 'follow-up', icon: <PlusOutlined/>, label: '跟进', onClick: () => setFollowUpModalOpen(true) },
    actions.has('JUDGE_VALID') && { key: 'judge-valid', icon: <CheckOutlined/>, label: '判有效', onClick: () => void openValid() },
    actions.has('JUDGE_INVALID') && { key: 'judge-invalid', icon: <CloseOutlined/>, label: '判无效', danger: true, onClick: () => void openInvalid() },
    actions.has('ENTER_DEAL') && { key: 'enter-deal', icon: <FileAddOutlined/>, label: '录入成交', disabled: !actions.get('ENTER_DEAL')?.enabled, onClick: () => setSalesOrderOpen(true) },
    actions.has('REVISE_DEAL') && { key: 'revise-deal', icon: <FileAddOutlined/>, label: '补正成交', disabled: !actions.get('REVISE_DEAL')?.enabled, onClick: () => setSalesOrderOpen(true) },
    actions.has('EDIT_BASIC_INFO') && { key: 'edit-info', icon: <EditOutlined/>, label: '修改信息', onClick: () => setBasicInfoOpen(true) },
    actions.has('SUBMITTER_SUPPLEMENT') && { key: 'submitter-supplement', icon: <EditOutlined/>, label: '补充资料', onClick: () => setSubmitterSupplementOpen(true) },
    actions.has('SUBMITTER_URGE') && { key: 'submitter-urge', icon: <BellOutlined/>, label: '催促', onClick: () => setUrgeOpen(true) },
    actions.has('SUBMITTER_COMPLAINT') && { key: 'submitter-complaint', icon: <WarningOutlined/>, label: '投诉', danger: true, onClick: () => setComplaintOpen(true) },
    actions.has('ENTER_REPURCHASE') && { key: 'enter-repurchase', icon: <FileAddOutlined/>, label: '录入复购', disabled: !actions.get('ENTER_REPURCHASE')?.enabled, onClick: () => setRepurchaseOpen(true) },
  ].filter(Boolean) as ToolbarAction[]

  const tabItems = visibleTabs.map(tab => {
    if (tab === 'overview') return { key: tab, label: '概览', children: <div className="lead-detail-tab-content"><LeadDetailOverview lead={lead} categoryLabel={categoryLabel} channelLabel={channelLabel} showFollowUp={visibleTabs.includes('follow-ups')} toolbar={toolbarActions.length ? <OverflowToolbar actions={toolbarActions}/> : undefined}/></div> }
    if (tab === 'follow-ups') return { key: tab, label: `跟进记录 (${followUpTotal})`, forceRender: true, children: <div className="lead-detail-tab-content lead-detail-follow-up"><LeadFollowUpPanel lead={lead} open={followUpOpen} refreshVersion={followUpRefreshVersion} onOpen={!readOnly && actions.has('ADD_FOLLOW_UP') ? () => setFollowUpOpen(true) : undefined} onClose={() => setFollowUpOpen(false)} onDirtyChange={readOnly ? undefined : setFollowUpFormDirty} onChanged={onChanged} onTotalChange={setFollowUpTotal}/></div> }
    if (tab === 'appeals') return { key: tab, label: '申诉记录', forceRender: true, children: <div className="lead-detail-tab-content"><LeadAppealPanel lead={lead} onChanged={onChanged}/></div> }
    if (tab === 'complaints') return { key: tab, label: '投诉记录', children: <div className="lead-detail-tab-content"><LeadComplaintPanel leadId={lead.id}/></div> }
    if (tab === 'flow-history') return { key: tab, label: '流转记录', children: <div className="lead-detail-tab-content"><LeadFlowHistoryPanel leadId={lead.id}/></div> }
    return { key: tab, label: `订单记录${orderTotal === undefined ? '' : ` (${orderTotal})`}`, children: <div className="lead-detail-tab-content"><LeadCustomerOrders leadId={lead.id} onCount={setOrderTotal}/></div> }
  })

  return <div className="lead-inbox-detail">
    <div className="lead-detail-hero">
      <Typography.Title level={4}>{lead.submittedName}</Typography.Title>
      {visibleTabs.includes('follow-ups') && lead.nextFollowUpAt && <div className="lead-hero-next-followup">
        <ClockCircleOutlined />
        <span className="lead-hero-next-label">下次跟进</span>
        <span className="lead-hero-next-time">{formatTimestamp(lead.nextFollowUpAt)}</span>
      </div>}
    </div>
    <Tabs className="lead-detail-tabs" activeKey={activeTab} onChange={key => setActiveTab(key as LeadDetailTab)} items={tabItems}/>
    {!readOnly && <>
      <Modal title="判定为无效客资" open={invalidOpen} onCancel={closeInvalid} footer={<Space><Button onClick={closeInvalid}>取消</Button><IrreversiblePopconfirm action={`将客资「${lead.submittedName}」判定为无效`} danger open={invalidConfirmOpen} onOpenChange={setInvalidConfirmOpen} onConfirm={judgeInvalid}><Button danger type="primary" loading={qualificationSaving} disabled={invalidReasonLoading || Boolean(invalidReasonError) || !invalidReasons.length} onClick={prepareJudgeInvalid}>确认判无效</Button></IrreversiblePopconfirm></Space>}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {invalidReasonError && <Alert type="error" showIcon message={invalidReasonError} action={<Button size="small" onClick={() => void loadInvalidReasons()}>重试</Button>}/>} 
          <Form.Item label="无效原因" required style={{ marginBottom: 0, width: '100%' }}><Select loading={invalidReasonLoading} disabled={invalidReasonLoading || Boolean(invalidReasonError) || !invalidReasons.length} value={invalidReason} onChange={setInvalidReason} placeholder={invalidReasonLoading ? '正在加载无效原因' : invalidReasons.length ? '选择无效原因' : '暂无可用无效原因'} options={invalidReasons.map(item => ({ value: item.value, label: item.label }))} style={{ width: '100%' }}/></Form.Item>
          {invalidRemarkTemplateError && <Alert type="error" showIcon message={invalidRemarkTemplateError} action={<Button size="small" onClick={() => void loadInvalidRemarkTemplates()}>重试</Button>}/>} 
          {invalidRemarkTemplateLoading ? <Spin size="small"/> : invalidRemarkTemplates.length ? <Space wrap>{invalidRemarkTemplates.map(template => <Button size="small" key={template.value} onClick={() => setInvalidDescription(current => applyInvalidRemarkTemplate(current, template.label))}>{template.label}</Button>)}</Space> : !invalidRemarkTemplateError && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无快捷备注"/>}
          <Form.Item label="备注" required style={{ marginBottom: 0, width: '100%' }}><Input.TextArea value={invalidDescription} onChange={event => setInvalidDescription(event.target.value)} rows={4} maxLength={2000} showCount placeholder="填写无效判定备注"/></Form.Item>
          <Typography.Text strong>附件</Typography.Text><LeadAppealEvidenceUpload value={invalidEvidence} onChange={setInvalidEvidence} disabled={qualificationSaving}/>
        </Space>
      </Modal>
      <Modal title="判定为有效客资" open={validOpen} onCancel={closeValid} footer={<Space><Button onClick={closeValid}>取消</Button><IrreversiblePopconfirm action={`将客资「${lead.submittedName}」判定为有效`} open={validConfirmOpen} onOpenChange={setValidConfirmOpen} onConfirm={judgeValid}><Button type="primary" loading={qualificationSaving} onClick={prepareJudgeValid}>确认判有效</Button></IrreversiblePopconfirm></Space>}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}><Typography.Text strong>客资分类</Typography.Text><Select allowClear value={validCategory} onChange={setValidCategory} placeholder="可不选择" options={categories.map(item => ({ value: item.value, label: item.label }))} style={{ width: '100%' }}/>{validTemplateError && <Alert type="error" showIcon message={validTemplateError}/>} {validTemplates.length > 0 && <Space wrap>{validTemplates.map(template => <Button size="small" key={template.value} onClick={() => setValidRemark(current => [current.trim(), template.label].filter(Boolean).join('\n'))}>{template.label}</Button>)}</Space>}<Form.Item label="有效备注" required><Input.TextArea value={validRemark} onChange={event => setValidRemark(event.target.value)} rows={4} maxLength={2000} showCount/></Form.Item></Space>
      </Modal>
      <LeadBasicInfoModal lead={lead} open={basicInfoOpen} onClose={() => setBasicInfoOpen(false)} onDirtyChange={setBasicInfoDirty} onChanged={onChanged}/>
      <LeadBasicInfoModal lead={lead} open={submitterSupplementOpen} submitterOnly onClose={() => setSubmitterSupplementOpen(false)} onChanged={onChanged}/>
      <Modal title="催促当前责任销售" open={urgeOpen} confirmLoading={submitterActionSaving} onCancel={() => setUrgeOpen(false)} onOk={async () => { if (!urgeReason.trim()) { message.warning('请填写催促原因'); return } setSubmitterActionSaving(true); try { await api.urgeLead(lead.id, urgeReason.trim()); message.success('催促已发送'); setUrgeReason(''); setUrgeOpen(false) } catch (error) { message.error(error instanceof Error ? error.message : '催促失败') } finally { setSubmitterActionSaving(false) } }}><Form.Item label="催促原因" required><Input.TextArea value={urgeReason} onChange={event => setUrgeReason(event.target.value)} rows={4} maxLength={500} showCount placeholder="填写本次催促原因"/></Form.Item></Modal>
      <Modal title="发起销售投诉" open={complaintOpen} confirmLoading={submitterActionSaving} onCancel={() => setComplaintOpen(false)} onOk={async () => { if (!complaintReason.trim()) { message.warning('请填写投诉原因'); return } setSubmitterActionSaving(true); try { await api.createLeadComplaint(lead.id, complaintReason.trim(), []); message.success('投诉已提交'); setComplaintReason(''); setComplaintOpen(false) } catch (error) { message.error(error instanceof Error ? error.message : '投诉提交失败') } finally { setSubmitterActionSaving(false) } }}><Form.Item label="投诉原因" required><Input.TextArea value={complaintReason} onChange={event => setComplaintReason(event.target.value)} rows={5} maxLength={1000} showCount placeholder="填写投诉事实与诉求"/></Form.Item></Modal>
      <SalesOrderEntryModal lead={lead} orderId={actions.has('REVISE_DEAL') ? lead.activeSalesOrderId : undefined} open={salesOrderOpen} onClose={() => setSalesOrderOpen(false)} onSubmitted={() => { setSalesOrderOpen(false); onChanged() }}/>
      <SalesOrderEntryModal lead={lead} repurchase open={repurchaseOpen} onClose={() => setRepurchaseOpen(false)} onSubmitted={() => { setRepurchaseOpen(false); onChanged() }}/>
      <FollowUpModal lead={lead} open={followUpModalOpen} onClose={() => setFollowUpModalOpen(false)} onSuccess={handleStandaloneFollowUpSuccess}/>
    </>}
  </div>
}
