import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Drawer, Skeleton, Tabs, Modal, Input } from 'antd'
import { api, type DictData, type ManagedLead } from '../services/api'
import LeadDetailOverview from './LeadDetailOverview'
import LeadFollowUpPanel from './LeadFollowUpPanel'
import LeadAppealPanel from './LeadAppealPanel'
import { APP_ROUTES, DICT_TYPE } from '../constants'
import { Link } from 'react-router-dom'
import { hasPermission } from '../services/managementAccess'

type TabKey = 'overview' | 'follow-ups' | 'appeals'

export default function LeadDetailModal({
  leadId,
  open,
  onClose,
  permissions = [],
}: {
  leadId: number
  open: boolean
  onClose: () => void
  permissions?: string[]
}) {
  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState('')
  const [lead, setLead] = useState<ManagedLead>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState<TabKey>('overview')
  const [categories, setCategories] = useState<DictData[]>([])
  const [channels, setChannels] = useState<DictData[]>([])
  const [followUpTotal, setFollowUpTotal] = useState(0)
  const [followUpRefreshVersion, setFollowUpRefreshVersion] = useState(0)

  const loadLead = useCallback(async () => {
    if (!open) return
    setLoading(true)
    setError('')
    try {
      const [leadData, categoryDict, channelDict] = await Promise.all([
        api.managedLead(leadId),
        api.dictDataByType(DICT_TYPE.LEAD_CATEGORY),
        api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL),
      ])
      setLead(leadData)
      setCategories(categoryDict)
      setChannels(channelDict)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '客资详情加载失败')
    } finally {
      setLoading(false)
    }
  }, [leadId, open])

  useEffect(() => {
    setCancelOpen(false)
    if (open) {
      void loadLead()
      setActiveTab('overview')
    } else {
      setLead(undefined)
      setError('')
    }
  }, [open, loadLead])

  const categoryLabel = (value?: string) =>
    categories.find((item) => item.value === value)?.label || value || '未分类'
  const channelLabel = (value?: string) =>
    channels.find((item) => item.value === value)?.label || value || '未知渠道'

  const handleChanged = () => {
    void loadLead()
  }

  return (
    <Drawer
      className="lead-detail-modal-drawer"
      open={open}
      onClose={onClose}
      title="客户档案"
      width="90%"
      styles={{ body: { padding: 0 } }}
      extra={lead?.status === 'won' && lead.nextFollowUpAt &&
        hasPermission(permissions, 'zsjos:lead-follow-up:create') && hasPermission(permissions, 'zsjos:business-task:cancel') ?
        <Button onClick={() => { setCancelReason(''); setCancelError(''); setCancelOpen(true) }}>取消跟进时间</Button> : undefined}
      destroyOnClose
    >
      <Modal title="取消跟进时间" open={cancelOpen} confirmLoading={cancelling}
        okText="确认取消" cancelText="返回" okButtonProps={{ disabled: !cancelReason.trim() }}
        onCancel={() => { if (!cancelling) setCancelOpen(false) }}
        onOk={async () => {
          setCancelling(true); setCancelError('')
          try {
            await api.cancelLeadNextFollowUp(leadId, cancelReason.trim())
            setCancelOpen(false)
            await loadLead()
          } catch (e) { setCancelError(e instanceof Error ? e.message : '取消失败，请重试') }
          finally { setCancelling(false) }
        }}>
        <p>取消当前跟进提醒，保留历史跟进记录。</p>
        <Input.TextArea aria-label="取消原因" placeholder="请填写取消原因" maxLength={500}
          value={cancelReason} onChange={e => setCancelReason(e.target.value)} disabled={cancelling} />
        {cancelError && <Alert type="error" showIcon message={cancelError} />}
      </Modal>
      {loading && <Skeleton active paragraph={{ rows: 10 }} style={{ padding: 24 }} />}
      {error && (
        <Alert
          type="error"
          showIcon
          message={error}
          action={
            <Button size="small" onClick={() => void loadLead()}>
              重试
            </Button>
          }
          style={{ margin: 24 }}
        />
      )}
      {!loading && !error && lead && (
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key as TabKey)}
          items={[
            {
              key: 'overview',
              label: '概览',
              children: (
                <div className="lead-detail-tab-content" style={{ padding: 24 }}>
                  {lead.personId && hasPermission(permissions,'zsjos:student-info-task:query') && hasPermission(permissions,'zsjos:registration:query-pool') && <Link to={`${APP_ROUTES.STUDENT_INFO_TASKS}?personId=${lead.personId}`} onClick={onClose}>查看该客户的收集任务</Link>}
                  <LeadDetailOverview
                    lead={lead}
                    categoryLabel={categoryLabel}
                    channelLabel={channelLabel}
                    showFollowUp={false}
                  />
                </div>
              ),
            },
            {
              key: 'follow-ups',
              label: `跟进历史 (${followUpTotal})`,
              children: (
                <div className="lead-detail-tab-content" style={{ padding: 24 }}>
                  <LeadFollowUpPanel
                    lead={lead}
                    open={true}
                    refreshVersion={followUpRefreshVersion}
                    onClose={() => {}}
                    onChanged={handleChanged}
                    onTotalChange={setFollowUpTotal}
                  />
                </div>
              ),
            },
            {
              key: 'appeals',
              label: '申诉历史',
              children: (
                <div className="lead-detail-tab-content" style={{ padding: 24 }}>
                  <LeadAppealPanel lead={lead} onChanged={handleChanged} />
                </div>
              ),
            },
          ]}
        />
      )}
    </Drawer>
  )
}
