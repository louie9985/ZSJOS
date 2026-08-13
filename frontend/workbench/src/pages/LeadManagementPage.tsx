import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Descriptions,
  Empty,
  Image,
  Input,
  List,
  Modal,
  Select,
  Skeleton,
  Space,
  Spin,
  Tag,
  Tabs,
  Typography
} from 'antd'
import { message } from 'antd'
import { BellOutlined, CheckOutlined, CloseOutlined, EditOutlined, FileAddOutlined, PlusOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons'
import { useLocation } from 'react-router-dom'
import { api, type AdvancedFilterGroup, type DictData, type LeadInboxFilterProfile, type ManagedLead } from '../services/api'
import { AdvancedFilterToolbar, filterCount } from '../components/AdvancedFilter'
import {
  applyInvalidRemarkTemplate,
  defaultInboxStage,
  dictionaryDisplayLabel,
  invalidReasonSnapshotLabel,
  hasNextLeadInboxPage,
  isLeadInboxUnauthorized,
  leadPendingTaskAlert,
  mergeUniqueLeads,
  protocolDisplayLabel,
  tryStartLeadPageRequest
} from '../services/leadManagement'
import {
  DICT_TYPE,
  LEAD_ASSIGNMENT_STATUS_LABELS,
  LEAD_DISPATCH_MODE_LABELS,
  LEAD_QUALIFICATION_STATUS_LABELS,
  LEAD_FOLLOW_UP_STATUS_LABELS
} from '../constants'
import LeadFollowUpPanel from '../components/LeadFollowUpPanel'
import LeadAppealPanel from '../components/LeadAppealPanel'
import LeadAppealEvidenceUpload from '../components/LeadAppealEvidenceUpload'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import LeadBasicInfoModal from '../components/LeadBasicInfoModal'
import SalesOrderEntryModal from '../components/SalesOrderEntryModal'
import type { LeadAppealEvidence } from '../services/api'
import { defaultLeadDetailTab, shouldBlockLeadSwitch, type LeadDetailTab } from '../services/leadFollowUp'
import { formatTimestamp } from '../services/time'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'

const PAGE_SIZE = 20

function userText(id?: number, name?: string) {
  return name || (id ? `用户 #${id}` : '未分配')
}

function productText(lead: ManagedLead) {
  const product = lead.primaryProduct
  return product ? [product.spuName || '未明确课程', product.skuName].filter(Boolean).join(' / ') : '未填写意向产品'
}

function LeadStateTags({ lead }: { lead: ManagedLead }) {
  return <Space size={4} wrap>
    <Tag color={lead.qualificationStatus === 'invalid' ? 'red' : lead.qualificationStatus === 'valid' ? 'green' : 'gold'}>
      {protocolDisplayLabel(LEAD_QUALIFICATION_STATUS_LABELS, lead.qualificationStatus, '未知有效状态')}
    </Tag>
    {lead.followUpStatus && <Tag color="blue">{protocolDisplayLabel(LEAD_FOLLOW_UP_STATUS_LABELS, lead.followUpStatus, '未知跟进状态')}</Tag>}
    {lead.operationalStatus === 'suspended' && <Tag color="orange">已挂起</Tag>}
  </Space>
}

function LeadDetail({ lead, categories, categoryLabel, channelLabel, audience, autoExpandFollowUp, onDirtyChange, onChanged }: {
  lead: ManagedLead
  categories: DictData[]
  categoryLabel: (value?: string) => string
  channelLabel: (value?: string) => string
  audience: 'submitter' | 'owner'
  autoExpandFollowUp: boolean
  onDirtyChange: (dirty: boolean) => void
  onChanged: () => void
}) {
  const pendingTaskAlert = leadPendingTaskAlert(lead)
  const [activeTab, setActiveTab] = useState<LeadDetailTab>(defaultLeadDetailTab(autoExpandFollowUp))
  const [followUpTotal, setFollowUpTotal] = useState(0)
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
  const [followUpOpen, setFollowUpOpen] = useState(autoExpandFollowUp)
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
  const actions = new Map((lead.availableActions || []).map(item => [item.code, item]))
  useEffect(() => { onDirtyChange(followUpFormDirty || basicInfoDirty) },
    [basicInfoDirty, followUpFormDirty, onDirtyChange])

  const judgeValid = async () => {
    setValidConfirmOpen(false)
    await runQualification(async ({ idempotencyKey, complete }) => {
      await api.judgeLeadValid(lead.id, { leadCategory: validCategory, remark: validRemark.trim(), idempotencyKey })
      complete()
      message.success('已判定为有效客资')
      setValidOpen(false); setValidRemark('')
      onChanged()
    }).catch(error => message.error(error instanceof Error ? error.message : '有效判定失败'))
  }

  const prepareJudgeValid = () => {
    if (!validRemark.trim()) { message.warning('请填写有效备注'); return }
    setValidConfirmOpen(true)
  }

  const openValid = async () => {
    resetQualificationIntent()
    setValidCategory(lead.leadCategory); setValidRemark(''); setValidOpen(true); setValidTemplateError('')
    try { setValidTemplates(await api.dictDataByType(DICT_TYPE.LEAD_VALID_REMARK_TEMPLATE)) }
    catch (error) { setValidTemplates([]); setValidTemplateError(error instanceof Error ? error.message : '快捷备注加载失败') }
  }

  const loadInvalidReasons = async () => {
    setInvalidReasonLoading(true)
    setInvalidReasonError('')
    try { setInvalidReasons(await api.dictDataByType(DICT_TYPE.LEAD_INVALID_REASON)) }
    catch (error) {
      setInvalidReasons([])
      setInvalidReasonError(error instanceof Error ? error.message : '无效原因加载失败')
    } finally { setInvalidReasonLoading(false) }
  }

  const loadInvalidRemarkTemplates = async () => {
    setInvalidRemarkTemplateLoading(true)
    setInvalidRemarkTemplateError('')
    try { setInvalidRemarkTemplates(await api.dictDataByType(DICT_TYPE.LEAD_INVALID_REMARK_TEMPLATE)) }
    catch (error) {
      setInvalidRemarkTemplates([])
      setInvalidRemarkTemplateError(error instanceof Error ? error.message : '快捷备注加载失败')
    } finally { setInvalidRemarkTemplateLoading(false) }
  }

  const openInvalid = () => {
    resetQualificationIntent()
    setInvalidOpen(true)
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
      complete()
      message.success('已判定为无效客资')
      setInvalidOpen(false)
      setInvalidReason(undefined)
      setInvalidDescription('')
      setInvalidEvidence([])
      onChanged()
    }).catch(error => message.error(error instanceof Error ? error.message : '无效判定失败'))
  }


  const prepareJudgeInvalid = () => {
    if (!invalidReason || !invalidDescription.trim()) {
      message.warning('请选择无效原因并填写备注')
      return
    }
    setInvalidConfirmOpen(true)
  }

  useEffect(() => {
    setActiveTab(defaultLeadDetailTab(autoExpandFollowUp))
    setFollowUpTotal(0)
    setFollowUpOpen(autoExpandFollowUp)
  }, [autoExpandFollowUp, lead.id])

  return <div className="lead-inbox-detail">
    <div className="lead-detail-hero">
      <Avatar size={48}>{lead.submittedName.slice(0, 1)}</Avatar>
      <div className="lead-detail-title">
        <Space wrap><Typography.Title level={4}>{lead.submittedName}</Typography.Title><LeadStateTags lead={lead}/></Space>
        <Typography.Text type="secondary">{lead.submittedMobile || '无手机号'} · {lead.submittedWechatId || '无微信号'} · 客资 #{lead.id}</Typography.Text>
      </div>
      <Space wrap className="lead-detail-actions">
        {actions.has('EDIT_BASIC_INFO') && <Button icon={<EditOutlined/>} onClick={() => setBasicInfoOpen(true)}>修改基础信息</Button>}
        {actions.has('SUBMITTER_SUPPLEMENT') && <Button icon={<EditOutlined/>} onClick={() => setSubmitterSupplementOpen(true)}>补充资料</Button>}
        {actions.has('SUBMITTER_URGE') && <Button icon={<BellOutlined/>} onClick={() => setUrgeOpen(true)}>催促</Button>}
        {actions.has('SUBMITTER_COMPLAINT') && <Button danger icon={<WarningOutlined/>} onClick={() => setComplaintOpen(true)}>投诉</Button>}
        {actions.has('ADD_FOLLOW_UP') && <Button type="primary" icon={<PlusOutlined/>} onClick={() => setFollowUpOpen(true)}>跟进</Button>}
        {actions.has('JUDGE_VALID') && <Button icon={<CheckOutlined/>} onClick={() => void openValid()}>判有效</Button>}
        {actions.has('JUDGE_INVALID') && <Button danger icon={<CloseOutlined/>} onClick={() => void openInvalid()}>判无效</Button>}
        {actions.has('ENTER_DEAL') && <Button icon={<FileAddOutlined/>}
          disabled={!actions.get('ENTER_DEAL')?.enabled} onClick={() => setSalesOrderOpen(true)}>录入成交</Button>}
        {actions.has('REVISE_DEAL') && <Button icon={<FileAddOutlined/>}
          disabled={!actions.get('REVISE_DEAL')?.enabled} onClick={() => setSalesOrderOpen(true)}>补正成交</Button>}
        {actions.has('ENTER_REPURCHASE') && <Button icon={<FileAddOutlined/>}
          disabled={!actions.get('ENTER_REPURCHASE')?.enabled} onClick={() => setRepurchaseOpen(true)}>录入复购</Button>}
      </Space>
    </div>
    {lead.operationalStatus === 'suspended' && <Alert type="warning" showIcon message="客资已挂起" description="销售当前只能查看，需由销售主管恢复、转派、回收或释放。"/>}
    {lead.status === 'invalid' && <Alert type="error" showIcon message="客资已判无效" description={[lead.invalidReason ? invalidReasonSnapshotLabel(lead.invalidReasonLabelSnapshot) : undefined, lead.invalidDescription].filter(Boolean).join('：')}/>}
    {pendingTaskAlert && <Alert type="info" showIcon message={pendingTaskAlert.message}
      description={`截止时间：${formatTimestamp(pendingTaskAlert.deadline)}`}/>}
    <Tabs
      className="lead-detail-tabs"
      activeKey={activeTab}
      onChange={key => setActiveTab(key as LeadDetailTab)}
      items={[
        {
          key: 'overview',
          label: '概览',
          children: <div className="lead-detail-tab-content lead-detail-overview">
            <div className="lead-detail-card-grid">
              <Card size="small" title="客户资料" className="lead-detail-card">
                <Descriptions className="lead-detail-table" column={{ xs: 1, sm: 2 }} layout="vertical" size="small" colon={false}>
                  <Descriptions.Item label="手机号">{lead.submittedMobile || '-'}</Descriptions.Item>
                  <Descriptions.Item label="微信号">{lead.submittedWechatId || '-'}</Descriptions.Item>
                  <Descriptions.Item label="所在地区">{[lead.provinceName, lead.cityName].filter(Boolean).join(' / ') || '-'}</Descriptions.Item>
                </Descriptions>
              </Card>

              <Card size="small" title="客资信息" className="lead-detail-card">
                <Descriptions className="lead-detail-table" column={{ xs: 1, sm: 2 }} layout="vertical" size="small" colon={false}>
                  <Descriptions.Item label="客资分类">{categoryLabel(lead.leadCategory)}</Descriptions.Item>
                  <Descriptions.Item label="来源渠道">{channelLabel(lead.sourceChannel)}</Descriptions.Item>
                  <Descriptions.Item label="客资有效状态">{protocolDisplayLabel(LEAD_QUALIFICATION_STATUS_LABELS, lead.qualificationStatus, '未知有效状态')}</Descriptions.Item>
                  <Descriptions.Item label="客资跟进状态">{protocolDisplayLabel(LEAD_FOLLOW_UP_STATUS_LABELS, lead.followUpStatus, '未知跟进状态')}</Descriptions.Item>
                  <Descriptions.Item label="分配状态">{protocolDisplayLabel(LEAD_ASSIGNMENT_STATUS_LABELS, lead.assignmentStatus, '未知分配状态')}</Descriptions.Item>
                  <Descriptions.Item label="提交备注" span={2}>{lead.remark || '-'}</Descriptions.Item>
                  {lead.currentAssignmentFirstFollowUpDeadlineAt && <Descriptions.Item label="首次跟进截止">{formatTimestamp(lead.currentAssignmentFirstFollowUpDeadlineAt)}</Descriptions.Item>}
                  {lead.qualificationDeadlineAt && <Descriptions.Item label="判定截止">{formatTimestamp(lead.qualificationDeadlineAt)}</Descriptions.Item>}
                  {lead.closeReason && <Descriptions.Item label="关闭原因" span={2}>{lead.closeReason}</Descriptions.Item>}
                </Descriptions>
              </Card>

              <Card size="small" title="意向产品" className="lead-detail-card">
                <List
                  size="small"
                  dataSource={lead.intendedProducts || []}
                  locale={{ emptyText: '暂无意向产品' }}
                  renderItem={product => <List.Item extra={product.price == null ? null : `¥${Number(product.price).toFixed(2)}`}>
                    <List.Item.Meta
                      title={<Space wrap>{product.primary && <Tag color="green">主意向</Tag>}<span>{product.spuName || '未明确课程'}</span></Space>}
                      description={[product.skuName, product.categoryName].filter(Boolean).join(' · ') || '未明确 SKU'}
                    />
                  </List.Item>}
                />
              </Card>

              <Card size="small" title="提交与分配" className="lead-detail-card">
                <Descriptions className="lead-detail-table" column={{ xs: 1, sm: 2 }} layout="vertical" size="small" colon={false}>
                  <Descriptions.Item label="提交人">{userText(lead.sourceUserId, lead.sourceUserName)}</Descriptions.Item>
                  <Descriptions.Item label="负责人">{userText(lead.ownerUserId, lead.ownerUserName)}</Descriptions.Item>
                  <Descriptions.Item label="派单方式">{protocolDisplayLabel(LEAD_DISPATCH_MODE_LABELS, lead.dispatchMode, '未知派单方式')}</Descriptions.Item>
                  <Descriptions.Item label="待接单人">{userText(lead.pendingAssigneeUserId, lead.pendingAssigneeUserName)}</Descriptions.Item>
                  <Descriptions.Item label="提交时间">{formatTimestamp(lead.submittedAt)}</Descriptions.Item>
                  <Descriptions.Item label="更新时间">{formatTimestamp(lead.updateTime)}</Descriptions.Item>
                  {lead.invalidReason && <Descriptions.Item label="无效原因" span={2}>{invalidReasonSnapshotLabel(lead.invalidReasonLabelSnapshot)}</Descriptions.Item>}
                  {lead.invalidDescription && <Descriptions.Item label="判定备注" span={2}>{lead.invalidDescription}</Descriptions.Item>}
                  {lead.status === 'invalid' && <Descriptions.Item label="判定附件" span={2}>{lead.invalidEvidence?.length ? <Image.PreviewGroup><Space wrap>{lead.invalidEvidence.map(file => <Image key={file.infraFileId} width={64} height={64} src={file.fileUrl} alt={file.originalName} title={file.originalName}/>)}</Space></Image.PreviewGroup> : '-'}</Descriptions.Item>}
                </Descriptions>
              </Card>

              <Card size="small" title="附件" className="lead-detail-card lead-detail-card-wide">
                {lead.attachments?.length ? <Image.PreviewGroup>
                  <div className="lead-attachment-grid">
                    {lead.attachments.map(file => <div key={file.id} className="lead-attachment-item" title={file.originalName}>
                      <Image src={file.fileUrl} alt={file.originalName}/><span>{file.originalName}</span>
                    </div>)}
                  </div>
                </Image.PreviewGroup> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无附件"/>}
              </Card>
            </div>
          </div>
        },
        {
          key: 'follow-ups',
          label: `跟进记录 (${followUpTotal})`,
          forceRender: true,
          children: <div className="lead-detail-tab-content lead-detail-follow-up">
            <LeadFollowUpPanel lead={lead} open={followUpOpen} onClose={() => setFollowUpOpen(false)}
              onDirtyChange={setFollowUpFormDirty} onChanged={onChanged} onTotalChange={setFollowUpTotal}/>
          </div>
        },
        ...(audience === 'submitter' ? [{
          key: 'appeals',
          label: '申诉记录',
          forceRender: true,
          children: <div className="lead-detail-tab-content"><LeadAppealPanel lead={lead} audience={audience} onChanged={onChanged}/></div>
        }] : [])
      ]}
    />
    <Modal title="判定为无效客资" open={invalidOpen} onCancel={closeInvalid} footer={<Space><Button onClick={closeInvalid}>取消</Button><IrreversiblePopconfirm action={`将客资「${lead.submittedName}」判定为无效`} danger open={invalidConfirmOpen} onOpenChange={setInvalidConfirmOpen} onConfirm={judgeInvalid}><Button danger type="primary" loading={qualificationSaving} disabled={invalidReasonLoading || Boolean(invalidReasonError) || !invalidReasons.length} onClick={prepareJudgeInvalid}>确认判无效</Button></IrreversiblePopconfirm></Space>}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Typography.Text strong>无效原因</Typography.Text>
        {invalidReasonError && <Alert type="error" showIcon message={invalidReasonError} action={<Button size="small" onClick={() => void loadInvalidReasons()}>重试</Button>}/>} 
        <Select loading={invalidReasonLoading} disabled={invalidReasonLoading || Boolean(invalidReasonError) || !invalidReasons.length} value={invalidReason} onChange={setInvalidReason} placeholder={invalidReasonLoading ? '正在加载无效原因' : invalidReasons.length ? '选择无效原因' : '暂无可用无效原因'} options={invalidReasons.map(item => ({ value: item.value, label: item.label }))} style={{ width: '100%' }}/>
        <Typography.Text strong>备注</Typography.Text>
        {invalidRemarkTemplateError && <Alert type="error" showIcon message={invalidRemarkTemplateError} action={<Button size="small" onClick={() => void loadInvalidRemarkTemplates()}>重试</Button>}/>} 
        {invalidRemarkTemplateLoading ? <Spin size="small"/> : invalidRemarkTemplates.length ? <Space wrap>
          {invalidRemarkTemplates.map(template => <Button size="small" key={template.value}
            onClick={() => setInvalidDescription(current => applyInvalidRemarkTemplate(current, template.label))}>{template.label}</Button>)}
        </Space> : !invalidRemarkTemplateError && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无快捷备注"/>}
        <Input.TextArea value={invalidDescription} onChange={event => setInvalidDescription(event.target.value)} rows={4} maxLength={2000} showCount placeholder="填写无效判定备注"/>
        <Typography.Text strong>附件</Typography.Text>
        <LeadAppealEvidenceUpload value={invalidEvidence} onChange={setInvalidEvidence} disabled={qualificationSaving}/>
      </Space>
    </Modal>
    <Modal title="判定为有效客资" open={validOpen} onCancel={closeValid} footer={<Space><Button onClick={closeValid}>取消</Button><IrreversiblePopconfirm action={`将客资「${lead.submittedName}」判定为有效`} open={validConfirmOpen} onOpenChange={setValidConfirmOpen} onConfirm={judgeValid}><Button type="primary" loading={qualificationSaving} onClick={prepareJudgeValid}>确认判有效</Button></IrreversiblePopconfirm></Space>}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Typography.Text strong>客资分类</Typography.Text>
        <Select allowClear value={validCategory} onChange={setValidCategory} placeholder="可不选择"
          options={categories.map(item => ({ value: item.value, label: item.label }))} style={{ width: '100%' }}/>
        <Typography.Text strong>有效备注</Typography.Text>
        {validTemplateError && <Alert type="error" showIcon message={validTemplateError}/>}
        {validTemplates.length > 0 && <Space wrap>{validTemplates.map(template => <Button size="small" key={template.value}
          onClick={() => setValidRemark(current => [current.trim(), template.label].filter(Boolean).join('\n'))}>{template.label}</Button>)}</Space>}
        <Input.TextArea value={validRemark} onChange={event => setValidRemark(event.target.value)} rows={4} maxLength={2000} showCount/>
      </Space>
    </Modal>
    <LeadBasicInfoModal lead={lead} open={basicInfoOpen} onClose={() => setBasicInfoOpen(false)}
      onDirtyChange={setBasicInfoDirty} onChanged={onChanged}/>
    <LeadBasicInfoModal lead={lead} open={submitterSupplementOpen} submitterOnly onClose={() => setSubmitterSupplementOpen(false)} onChanged={onChanged}/>
    <Modal title="催促当前责任销售" open={urgeOpen} confirmLoading={submitterActionSaving} onCancel={() => setUrgeOpen(false)} onOk={async () => {
      if (!urgeReason.trim()) { message.warning('请填写催促原因'); return }
      setSubmitterActionSaving(true); try { await api.urgeLead(lead.id, urgeReason.trim()); message.success('催促已发送'); setUrgeReason(''); setUrgeOpen(false) }
      catch (error) { message.error(error instanceof Error ? error.message : '催促失败') } finally { setSubmitterActionSaving(false) }
    }}><Input.TextArea value={urgeReason} onChange={event => setUrgeReason(event.target.value)} rows={4} maxLength={500} showCount placeholder="填写本次催促原因"/></Modal>
    <Modal title="发起销售投诉" open={complaintOpen} confirmLoading={submitterActionSaving} onCancel={() => setComplaintOpen(false)} onOk={async () => {
      if (!complaintReason.trim()) { message.warning('请填写投诉原因'); return }
      setSubmitterActionSaving(true); try { await api.createLeadComplaint(lead.id, complaintReason.trim(), []); message.success('投诉已提交'); setComplaintReason(''); setComplaintOpen(false) }
      catch (error) { message.error(error instanceof Error ? error.message : '投诉提交失败') } finally { setSubmitterActionSaving(false) }
    }}><Input.TextArea value={complaintReason} onChange={event => setComplaintReason(event.target.value)} rows={5} maxLength={1000} showCount placeholder="填写投诉事实与诉求"/></Modal>
    <SalesOrderEntryModal lead={lead} orderId={actions.has('REVISE_DEAL') ? lead.activeSalesOrderId : undefined}
      open={salesOrderOpen} onClose={() => setSalesOrderOpen(false)}
      onSubmitted={() => { setSalesOrderOpen(false); onChanged() }}/>
    <SalesOrderEntryModal lead={lead} repurchase open={repurchaseOpen} onClose={() => setRepurchaseOpen(false)}
      onSubmitted={() => { setRepurchaseOpen(false); onChanged() }}/>
  </div>
}

export default function LeadManagementPage({ audience }: { audience: 'submitter' | 'owner' }) {
  const location = useLocation()
  const routeState = location.state as { leadId?: number; openFollowUp?: boolean } | null
  const requestedLeadId = routeState?.leadId
  const [items, setItems] = useState<ManagedLead[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [initialLoading, setInitialLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [initialError, setInitialError] = useState('')
  const [loadMoreError, setLoadMoreError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [inboxGroup, setInboxGroup] = useState('all')
  const [inboxStage, setInboxStage] = useState('all')
  const [filterProfile, setFilterProfile] = useState<LeadInboxFilterProfile>({ groups: [] })
  const [filterLoading, setFilterLoading] = useState(true)
  const [metadataError, setMetadataError] = useState('')
  const [categories, setCategories] = useState<DictData[]>([])
  const [channels, setChannels] = useState<DictData[]>([])
  const [categoryError, setCategoryError] = useState(false)
  const [channelError, setChannelError] = useState(false)
  const [selectedId, setSelectedId] = useState<number | undefined>(requestedLeadId)
  const [detail, setDetail] = useState<ManagedLead>()
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [followUpDirty, setFollowUpDirty] = useState(false)
  const requestVersion = useRef(0)
  const metadataVersion = useRef(0)
  const activePageRequests = useRef(new Set<string>())
  const listScrollRef = useRef<HTMLDivElement>(null)
  const listSentinelRef = useRef<HTMLDivElement>(null)

  const loadMetadata = useCallback(async () => {
    const version = ++metadataVersion.current
    setMetadataError('')
    setCategoryError(false)
    setChannelError(false)
    setFilterLoading(true)
    const results = await Promise.allSettled([
      api.leadInboxFilterProfile(audience),
      api.dictDataByType(DICT_TYPE.LEAD_CATEGORY),
      api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL)
    ])
    if (version !== metadataVersion.current) return
    if (results[0].status === 'fulfilled') setFilterProfile(results[0].value)
    if (results[1].status === 'fulfilled') setCategories(results[1].value)
    else { setCategories([]); setCategoryError(true) }
    if (results[2].status === 'fulfilled') setChannels(results[2].value)
    else { setChannels([]); setChannelError(true) }
    if (results.some(result => result.status === 'rejected')) setMetadataError('筛选项加载不完整，可重试恢复字典和状态统计。')
    setFilterLoading(false)
  }, [audience])

  const loadPage = useCallback(async (targetPage: number, replace: boolean, version: number) => {
    const requestKey = tryStartLeadPageRequest(activePageRequests.current, version, targetPage)
    if (!requestKey) return
    if (replace) {
      setInitialLoading(true)
      setInitialError('')
    } else {
      setLoadingMore(true)
      setLoadMoreError('')
    }
    try {
      const result = await api.managedLeadInboxPage(audience, {
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
        keyword: keyword || undefined, advancedFilter,
        inboxGroup: inboxGroup === 'all' ? undefined : inboxGroup,
        inboxStage: inboxStage === 'all' ? undefined : inboxStage
      })
      if (version !== requestVersion.current) return
      setItems(current => replace ? result.list : mergeUniqueLeads(current, result.list))
      setTotal(result.total)
      setPageNo(targetPage)
      if (replace) setSelectedId(current => requestedLeadId
        || (current && result.list.some(item => item.id === current) ? current : result.list[0]?.id))
    } catch (loadError) {
      if (version === requestVersion.current) {
        const message = loadError instanceof Error ? loadError.message : '客资列表加载失败'
        if (replace) {
          setInitialError(message)
        } else {
          setLoadMoreError(message)
        }
      }
    } finally {
      activePageRequests.current.delete(requestKey)
      if (version === requestVersion.current) {
        if (replace) setInitialLoading(false)
        else setLoadingMore(false)
      }
    }
  }, [advancedFilter, audience, inboxGroup, inboxStage, keyword, requestedLeadId])

  useEffect(() => { void loadMetadata() }, [loadMetadata])
  useEffect(() => {
    const version = ++requestVersion.current
    setPageNo(1)
    setInitialLoading(false)
    setLoadingMore(false)
    setInitialError('')
    setLoadMoreError('')
    if (listScrollRef.current) listScrollRef.current.scrollTop = 0
    void loadPage(1, true, version)
  }, [loadPage])

  const loadDetail = useCallback(async (id: number) => {
    setDetailLoading(true)
    setDetailError('')
    try {
      setDetail(await api.managedLead(id))
    } catch (loadError) {
      setDetail(undefined)
      setDetailError(loadError instanceof Error ? loadError.message : '客资详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }, [])

  useEffect(() => {
    if (selectedId) void loadDetail(selectedId)
    else setDetail(undefined)
  }, [loadDetail, selectedId])

  const refreshAfterLeadChange = useCallback(async (id: number) => {
    const version = ++requestVersion.current
    await Promise.all([loadMetadata(), loadPage(1, true, version), loadDetail(id)])
  }, [loadDetail, loadMetadata, loadPage])

  const activeGroup = useMemo(
    () => filterProfile.groups.find(item => item.key === inboxGroup),
    [filterProfile.groups, inboxGroup]
  )
  useEffect(() => {
    if (!filterProfile.groups.length || filterProfile.groups.some(item => item.key === inboxGroup)) return
    const firstGroup = filterProfile.groups[0]
    setInboxGroup(firstGroup.key)
    setInboxStage(defaultInboxStage(filterProfile.groups, firstGroup.key))
  }, [filterProfile.groups, inboxGroup])
  const categoryLabel = useCallback(
    (value?: string) => dictionaryDisplayLabel(categories, value, categoryError),
    [categories, categoryError]
  )
  const channelLabel = useCallback(
    (value?: string) => dictionaryDisplayLabel(channels, value, channelError),
    [channelError, channels]
  )
  const hasMore = hasNextLeadInboxPage(pageNo, PAGE_SIZE, total)

  useEffect(() => {
    const root = listScrollRef.current
    const sentinel = listSentinelRef.current
    if (!root || !sentinel || !hasMore || initialLoading || loadingMore || loadMoreError) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting) {
        void loadPage(pageNo + 1, false, requestVersion.current)
      }
    }, { root, rootMargin: '240px 0px', threshold: 0 })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, initialLoading, loadMoreError, loadPage, loadingMore, pageNo])

  const selectLead = (id: number) => {
    if (shouldBlockLeadSwitch(followUpDirty) && !window.confirm('当前表单尚未提交，切换客资将丢失已填写内容。确定继续吗？')) return
    setFollowUpDirty(false)
    setSelectedId(id)
  }
  const changeInboxGroup = (key: string) => {
    setInboxGroup(key)
    setInboxStage(defaultInboxStage(filterProfile.groups, key))
  }
  const detailContent = detailLoading
    ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError
      ? <Alert type="error" showIcon message={detailError} action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>}/>
      : detail
        ? <LeadDetail lead={detail} categories={categories} categoryLabel={categoryLabel} channelLabel={channelLabel}
          audience={audience} autoExpandFollowUp={Boolean(routeState?.openFollowUp && requestedLeadId === detail.id)}
          onDirtyChange={setFollowUpDirty} onChanged={() => void refreshAfterLeadChange(detail.id)}/>
        : <Empty description="从左侧选择一条客资"/>

  return <section className="workspace-page lead-management-page">
    {filterCount(advancedFilter) === 0 && <header className="lead-inbox-filter-shell">
      {metadataError && <Alert className="lead-inbox-metadata-error" type="warning" showIcon message={metadataError} action={<Button type="link" size="small" onClick={() => void loadMetadata()}>重试</Button>}/>} 
      {filterLoading
        ? <Skeleton active title={false} paragraph={{ rows: 2 }}/>
        : filterProfile.groups.length > 0
          ? <>
            <Tabs
              className="lead-inbox-group-tabs"
              activeKey={inboxGroup}
              onChange={changeInboxGroup}
              items={filterProfile.groups.map(group => ({
                key: group.key,
                label: <span>{group.label}<small>{group.count}</small></span>
              }))}
            />
            {activeGroup?.sections.length ? <div className="lead-inbox-filter-sections">
              {activeGroup.sections.map(section => <div className="lead-inbox-filter-row" key={section.key}>
                <span className="lead-inbox-filter-label">{section.label}</span>
                <div className="lead-inbox-filter-options">
                  {section.options.map(option => <button
                    type="button"
                    key={option.key}
                    className={inboxStage === option.key ? 'active' : ''}
                    aria-pressed={inboxStage === option.key}
                    onClick={() => setInboxStage(option.key)}
                  >{option.label}<small>{option.count}</small></button>)}
                </div>
              </div>)}
            </div> : null}
          </>
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可用筛选配置"/>}
    </header>}
    <div className="lead-inbox-layout">
      <aside className="lead-inbox-list-pane">
        <div className="lead-inbox-toolbar"><AdvancedFilterToolbar scene="lead" placeholder="搜索姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/></div>
        {initialError && <Alert className="lead-list-error" type={isLeadInboxUnauthorized(initialError) ? 'warning' : 'error'} showIcon
          message={isLeadInboxUnauthorized(initialError) ? '无权查看客资收件箱' : '客资列表加载失败'} description={initialError}
          action={!isLeadInboxUnauthorized(initialError) ? <Button size="small" onClick={() => void loadPage(1, true, requestVersion.current)}>重试</Button> : undefined}/>}
        <div ref={listScrollRef} className="lead-inbox-scroll">
          {initialLoading ? <div className="lead-list-skeletons">
            {Array.from({ length: 5 }, (_, index) => <div className="lead-inbox-item" key={index}><Skeleton active avatar paragraph={{ rows: 2 }}/></div>)}
          </div> : !items.length && !initialError ? <Empty description="当前筛选下暂无客资"/> : items.map(item => {
            const active = item.id === selectedId
            return <button key={item.id} type="button" className={active ? 'lead-inbox-item active' : 'lead-inbox-item'} onClick={() => selectLead(item.id)}>
              <div className="lead-inbox-item-main">
                <Avatar>{item.submittedName.slice(0, 1)}</Avatar>
                <div className="lead-inbox-item-copy">
                  <div className="lead-inbox-item-title"><strong>{item.submittedName}</strong><LeadStateTags lead={item}/></div>
                  <span>{productText(item)}</span>
                  <span>{item.submittedMobile || '无手机号'} · {item.submittedWechatId || '无微信号'}</span>
                </div>
              </div>
              <div className="lead-inbox-item-meta"><Badge status="processing"/><span>{channelLabel(item.sourceChannel)} · {categoryLabel(item.leadCategory)} · {formatTimestamp(item.submittedAt)}</span></div>
            </button>
          })}
          {!initialLoading && items.length > 0 && <div ref={listSentinelRef} className="lead-list-sentinel">
            {loadMoreError
              ? <Alert type="error" showIcon message="更多客资加载失败" description={loadMoreError}
                action={<Button size="small" onClick={() => void loadPage(pageNo + 1, false, requestVersion.current)}>重试</Button>}/>
              : loadingMore
                ? <div className="lead-list-loading"><Spin size="small"/> 加载中</div>
                : hasMore
                  ? <Typography.Text type="secondary">继续下滑加载</Typography.Text>
                  : <Typography.Text type="secondary" className="lead-list-end">已加载全部 {total} 条客资</Typography.Text>}
          </div>}
        </div>
      </aside>
      <main className="lead-inbox-detail-pane">{detailContent}</main>
    </div>
  </section>
}
