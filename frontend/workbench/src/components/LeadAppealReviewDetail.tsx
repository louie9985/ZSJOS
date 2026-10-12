import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Skeleton, Space, Typography } from 'antd'
import { api, type LeadAppeal, type LeadAppealReviewDetail as ReviewDetail } from '../services/api'
import LeadDetail from './LeadDetail'
import { LEAD_QUALIFICATION_STATUS_LABELS } from '../constants'

export default function LeadAppealReviewDetail({ item, onDirtyChange, onChanged, onBusyChange }: {
  item: LeadAppeal
  onDirtyChange: (dirty: boolean) => void
  onChanged: () => void
  onBusyChange: (busy: boolean) => void
}) {
  const [data, setData] = useState<ReviewDetail>()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const read = useCallback(async () => {
    if (!item.taskId) throw new Error('申诉任务不可用，请返回列表刷新')
    return api.leadAppealReviewDetail(item.id, item.taskId)
  }, [item.id, item.taskId])
  const reload = useCallback(async () => {
    setError('')
    try { const result = await read(); setData(result); return result.context }
    catch (cause) { setError(cause instanceof Error ? cause.message : '客资详情加载失败'); throw cause }
    finally { setLoading(false) }
  }, [read])
  useEffect(() => { void reload().catch(() => {}) }, [reload])
  const refresh = async () => { const context = await reload(); onChanged(); return context }
  return <div className="appeal-shared-detail">
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void reload().catch(() => {})}>重试</Button>} />}
    {loading && <Skeleton active paragraph={{ rows: 8 }} />}
    {data && <LeadDetail lead={data.lead} categories={[]} categoryLabel={() => data.lead.leadCategoryLabelSnapshot || '未记录分类'}
      channelLabel={() => data.lead.sourceChannelLabelSnapshot || '未记录渠道'} mode="manager-readonly" autoExpandFollowUp={false} initialTab="appeals"
      onDirtyChange={onDirtyChange} onChanged={() => void refresh().catch(() => {})}
      contextHeader={<Space wrap className="appeal-lead-context"><Typography.Text>{data.lead.leadNo}</Typography.Text>
        <Typography.Text type="secondary">当前判定：{LEAD_QUALIFICATION_STATUS_LABELS[data.lead.qualificationStatus ?? ''] || '未记录'}</Typography.Text></Space>}
      appealReview={{ context: data.context, appealId: item.id, reload: refresh, read: async () => (await read()).context, onBusyChange }} />}
  </div>
}
