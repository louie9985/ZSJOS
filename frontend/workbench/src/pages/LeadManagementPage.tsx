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
import { ReloadOutlined } from '@ant-design/icons'
import { useLocation } from 'react-router-dom'
import { api, type DictData, type LeadInboxFilterProfile, type ManagedLead } from '../services/api'
import {
  applyInvalidRemarkTemplate,
  canJudgeLeadQualification,
  defaultInboxStage,
  mergeUniqueLeads,
  tryStartLeadPageRequest
} from '../services/leadManagement'
import {
  DICT_TYPE,
  LEAD_ASSIGNMENT_STATUS_LABELS,
  LEAD_DISPATCH_MODE_LABELS,
  LEAD_HANDLING_STAGE_LABELS
} from '../constants'
import LeadFollowUpPanel from '../components/LeadFollowUpPanel'
import LeadAppealPanel from '../components/LeadAppealPanel'
import LeadAppealEvidenceUpload from '../components/LeadAppealEvidenceUpload'
import type { LeadAppealEvidence } from '../services/api'
import { defaultLeadDetailTab, shouldBlockLeadSwitch, type LeadDetailTab } from '../services/leadFollowUp'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 20

function userText(id?: number, name?: string) {
  return name || (id ? `用户 #${id}` : '未分配')
}

function productText(lead: ManagedLead) {
  const product = lead.primaryProduct
  return product ? [product.spuName || '未明确课程', product.skuName].filter(Boolean).join(' / ') : '未填写意向产品'
}

function LeadDetail({ lead, categoryLabel, channelLabel, audience, autoExpandFollowUp, onDirtyChange, onChanged, canQualify }: {
  lead: ManagedLead
  categoryLabel: (value?: string) => string
  channelLabel: (value?: string) => string
  audience: 'submitter' | 'owner'
  autoExpandFollowUp: boolean
  onDirtyChange: (dirty: boolean) => void
  onChanged: () => void
  canQualify?: boolean
}) {
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
  const [invalidEvidence, setInvalidEvidence] = useState<LeadAppealEvidence[]>([])
  const [qualificationSaving, setQualificationSaving] = useState(false)
  const canJudge = canJudgeLeadQualification(lead, audience, Boolean(canQualify))

  const judgeValid = async () => {
    setQualificationSaving(true)
    try {
      await api.judgeLeadValid(lead.id, crypto.randomUUID())
      message.success('已判定为有效客资')
      onChanged()
    } finally { setQualificationSaving(false) }
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
    setInvalidOpen(true)
    if (!invalidReasons.length && !invalidReasonLoading) void loadInvalidReasons()
    if (!invalidRemarkTemplates.length && !invalidRemarkTemplateLoading) void loadInvalidRemarkTemplates()
  }

  const judgeInvalid = async () => {
    if (!invalidReason || !invalidDescription.trim()) {
      message.warning('请选择无效原因并填写备注')
      return
    }
    setQualificationSaving(true)
    try {
      await api.judgeLeadInvalid(lead.id, { reasonCode: invalidReason, description: invalidDescription.trim(), attachments: invalidEvidence.map(item => ({ infraFileId: item.infraFileId })), idempotencyKey: crypto.randomUUID() })
      message.success('已判定为无效客资')
      setInvalidOpen(false)
      setInvalidReason(undefined)
      setInvalidDescription('')
      setInvalidEvidence([])
      onChanged()
    } finally { setQualificationSaving(false) }
  }

  useEffect(() => {
    setActiveTab(defaultLeadDetailTab(autoExpandFollowUp))
    setFollowUpTotal(0)
  }, [autoExpandFollowUp, lead.id])

  return <div className="lead-inbox-detail">
    <div className="lead-detail-hero">
      <Avatar size={48}>{lead.submittedName.slice(0, 1)}</Avatar>
      <div className="lead-detail-title">
        <Space wrap><Typography.Title level={4}>{lead.submittedName}</Typography.Title><Tag color="blue">{LEAD_HANDLING_STAGE_LABELS[lead.handlingStage] || lead.handlingStage}</Tag></Space>
        <Typography.Text type="secondary">{lead.submittedMobile || '无手机号'} · {lead.submittedWechatId || '无微信号'} · 客资 #{lead.id}</Typography.Text>
      </div>
    </div>
    {lead.handlingStage === 'suspended' && <Alert type="warning" showIcon message="客资已挂起" description="销售当前只能查看，需由销售主管恢复、转派、回收或释放。"/>}
    {lead.status === 'invalid' && <Alert type="error" showIcon message="客资已判无效" description={[lead.invalidReasonLabelSnapshot || lead.invalidReason, lead.invalidDescription].filter(Boolean).join('：')}/>} 
    {lead.handlingStage === 'qualification_pending' && <Alert type="info" showIcon message="待完成有效性判定" description={`截止时间：${formatTimestamp(lead.qualificationDeadlineAt)}`}
      action={canJudge ? <Space><Button type="primary" loading={qualificationSaving} onClick={() => void judgeValid()}>判有效</Button><Button danger onClick={() => void openInvalid()}>判无效</Button></Space> : undefined}/>} 
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
                  <Descriptions.Item label="当前负责人">{userText(lead.ownerUserId, lead.ownerUserName)}</Descriptions.Item>
                </Descriptions>
              </Card>

              <Card size="small" title="客资信息" className="lead-detail-card">
                <Descriptions className="lead-detail-table" column={{ xs: 1, sm: 2 }} layout="vertical" size="small" colon={false}>
                  <Descriptions.Item label="客资分类">{categoryLabel(lead.leadCategory)}</Descriptions.Item>
                  <Descriptions.Item label="来源渠道">{channelLabel(lead.sourceChannel)}</Descriptions.Item>
                  <Descriptions.Item label="当前阶段">{LEAD_HANDLING_STAGE_LABELS[lead.handlingStage] || lead.handlingStage}</Descriptions.Item>
                  <Descriptions.Item label="分配状态">{LEAD_ASSIGNMENT_STATUS_LABELS[lead.assignmentStatus] || lead.assignmentStatus}</Descriptions.Item>
                  <Descriptions.Item label="提交备注" span={2}>{lead.remark || '-'}</Descriptions.Item>
                  {lead.invalidReason && <Descriptions.Item label="无效原因" span={2}>{lead.invalidReasonLabelSnapshot || lead.invalidReason}</Descriptions.Item>}
                  {lead.invalidDescription && <Descriptions.Item label="备注" span={2}>{lead.invalidDescription}</Descriptions.Item>}
                  {lead.status === 'invalid' && <Descriptions.Item label="附件" span={2}>
                    {lead.invalidEvidence?.length ? <Image.PreviewGroup><Space wrap>
                      {lead.invalidEvidence.map(file => <Image key={file.infraFileId} width={64} height={64}
                        src={file.fileUrl} alt={file.originalName} title={file.originalName}/>) }
                    </Space></Image.PreviewGroup> : '-'}
                  </Descriptions.Item>}
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
                  <Descriptions.Item label="派单方式">{lead.dispatchMode ? LEAD_DISPATCH_MODE_LABELS[lead.dispatchMode] || lead.dispatchMode : '-'}</Descriptions.Item>
                  <Descriptions.Item label="待接单人">{userText(lead.pendingAssigneeUserId, lead.pendingAssigneeUserName)}</Descriptions.Item>
                  <Descriptions.Item label="提交时间">{formatTimestamp(lead.submittedAt)}</Descriptions.Item>
                  <Descriptions.Item label="更新时间">{formatTimestamp(lead.updateTime)}</Descriptions.Item>
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
            <LeadFollowUpPanel lead={lead} editable={audience === 'owner' && lead.status === 'submitted'} autoExpand={autoExpandFollowUp}
              onDirtyChange={onDirtyChange} onChanged={onChanged} onTotalChange={setFollowUpTotal}/>
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
    <Modal title="判定为无效客资" open={invalidOpen} confirmLoading={qualificationSaving} okButtonProps={{ disabled: invalidReasonLoading || Boolean(invalidReasonError) || !invalidReasons.length }} onOk={() => void judgeInvalid()} onCancel={() => setInvalidOpen(false)} okText="确认判无效">
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
        <LeadAppealEvidenceUpload value={invalidEvidence} onChange={setInvalidEvidence}
          disabled={qualificationSaving} uploadImage={api.uploadLeadQualificationImage}/>
      </Space>
    </Modal>
  </div>
}

export default function LeadManagementPage({ audience, canQualify = false }: { audience: 'submitter' | 'owner'; canQualify?: boolean }) {
  const location = useLocation()
  const routeState = location.state as { leadId?: number; openFollowUp?: boolean } | null
  const requestedLeadId = routeState?.leadId
  const [items, setItems] = useState<ManagedLead[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [inboxGroup, setInboxGroup] = useState('all')
  const [inboxStage, setInboxStage] = useState('all')
  const [filterProfile, setFilterProfile] = useState<LeadInboxFilterProfile>({ groups: [] })
  const [filterLoading, setFilterLoading] = useState(true)
  const [metadataError, setMetadataError] = useState('')
  const [categories, setCategories] = useState<DictData[]>([])
  const [channels, setChannels] = useState<DictData[]>([])
  const [selectedId, setSelectedId] = useState<number | undefined>(requestedLeadId)
  const [detail, setDetail] = useState<ManagedLead>()
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [followUpDirty, setFollowUpDirty] = useState(false)
  const requestVersion = useRef(0)
  const metadataVersion = useRef(0)
  const activePageRequests = useRef(new Set<string>())

  const loadMetadata = useCallback(async () => {
    const version = ++metadataVersion.current
    setMetadataError('')
    setFilterLoading(true)
    const results = await Promise.allSettled([
      api.leadInboxFilterProfile(audience),
      api.dictDataByType(DICT_TYPE.LEAD_CATEGORY),
      api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL)
    ])
    if (version !== metadataVersion.current) return
    if (results[0].status === 'fulfilled') setFilterProfile(results[0].value)
    if (results[1].status === 'fulfilled') setCategories(results[1].value)
    if (results[2].status === 'fulfilled') setChannels(results[2].value)
    if (results.some(result => result.status === 'rejected')) setMetadataError('筛选项加载不完整，可重试恢复字典和状态统计。')
    setFilterLoading(false)
  }, [audience])

  const loadPage = useCallback(async (targetPage: number, replace: boolean, version: number) => {
    const requestKey = tryStartLeadPageRequest(activePageRequests.current, version, targetPage)
    if (!requestKey) return
    setLoading(true)
    setError('')
    try {
      const result = await api.managedLeadInboxPage(audience, {
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
        keyword: keyword || undefined,
        inboxGroup: inboxGroup === 'all' ? undefined : inboxGroup,
        inboxStage: inboxStage === 'all' ? undefined : inboxStage
      })
      if (version !== requestVersion.current) return
      setItems(current => replace ? result.list : mergeUniqueLeads(current, result.list))
      setTotal(result.total)
      setPageNo(targetPage)
      if (replace) setSelectedId(current => requestedLeadId || current || result.list[0]?.id)
    } catch (loadError) {
      if (version === requestVersion.current) {
        setError(loadError instanceof Error ? loadError.message : '客资列表加载失败')
        if (replace) setItems([])
      }
    } finally {
      activePageRequests.current.delete(requestKey)
      if (version === requestVersion.current) setLoading(false)
    }
  }, [audience, inboxGroup, inboxStage, keyword, requestedLeadId])

  useEffect(() => { void loadMetadata() }, [loadMetadata])
  useEffect(() => {
    const version = ++requestVersion.current
    setItems([])
    setTotal(0)
    setPageNo(1)
    setSelectedId(undefined)
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
  const categoryLabel = useCallback((value?: string) => categories.find(item => item.value === value)?.label || value || '-', [categories])
  const channelLabel = useCallback((value?: string) => channels.find(item => item.value === value)?.label || value || '-', [channels])
  const hasMore = items.length < total

  const selectLead = (id: number) => {
    if (shouldBlockLeadSwitch(followUpDirty) && !window.confirm('当前跟进记录尚未提交，切换客资将丢失已填写内容。确定继续吗？')) return
    setFollowUpDirty(false)
    setSelectedId(id)
  }
  const onListScroll = (event: React.UIEvent<HTMLDivElement>) => {
    const node = event.currentTarget
    if (!loading && hasMore && node.scrollHeight - node.scrollTop - node.clientHeight < 80) {
      void loadPage(pageNo + 1, false, requestVersion.current)
    }
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
        ? <LeadDetail lead={detail} categoryLabel={categoryLabel} channelLabel={channelLabel}
          audience={audience} autoExpandFollowUp={Boolean(routeState?.openFollowUp && requestedLeadId === detail.id)}
          onDirtyChange={setFollowUpDirty} onChanged={() => void loadDetail(detail.id)} canQualify={canQualify}/>
        : <Empty description="从左侧选择一条客资"/>

  return <section className="workspace-page lead-management-page">
    <header className="lead-inbox-filter-shell">
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
    </header>
    <div className="lead-inbox-layout">
      <aside className="lead-inbox-list-pane">
        <div className="lead-inbox-toolbar">
          <Input.Search allowClear placeholder="搜索姓名 / 手机号 / 微信号" onSearch={value => setKeyword(value.trim())}/>
        </div>
        {error && <Alert className="lead-list-error" type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadPage(1, true, requestVersion.current)}>重试</Button>}/>} 
        <div className="lead-inbox-scroll" onScroll={onListScroll}>
          {!loading && !items.length && !error ? <Empty description="暂无可查看客资"/> : items.map(item => {
            const active = item.id === selectedId
            return <button key={item.id} type="button" className={active ? 'lead-inbox-item active' : 'lead-inbox-item'} onClick={() => selectLead(item.id)}>
              <div className="lead-inbox-item-main">
                <Avatar>{item.submittedName.slice(0, 1)}</Avatar>
                <div className="lead-inbox-item-copy">
                  <div className="lead-inbox-item-title"><strong>{item.submittedName}</strong><Tag color="blue">{LEAD_HANDLING_STAGE_LABELS[item.handlingStage] || item.handlingStage}</Tag></div>
                  <span>{productText(item)}</span>
                  <span>{item.submittedMobile || '无手机号'} · {item.submittedWechatId || '无微信号'}</span>
                </div>
              </div>
              <div className="lead-inbox-item-meta"><Badge status="processing"/><span>{channelLabel(item.sourceChannel)} · {categoryLabel(item.leadCategory)} · {formatTimestamp(item.submittedAt)}</span></div>
            </button>
          })}
          {loading && <div className="lead-list-loading"><Spin size="small"/> 加载中</div>}
          {!loading && items.length > 0 && !hasMore && <Typography.Text type="secondary" className="lead-list-end">已加载全部 {total} 条客资</Typography.Text>}
        </div>
      </aside>
      <main className="lead-inbox-detail-pane">{detailContent}</main>
    </div>
  </section>
}
