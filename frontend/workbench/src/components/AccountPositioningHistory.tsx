import { Alert, Button, Empty, Spin, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { api, type PositioningCard } from '../services/api'
import { formatTimestamp } from '../services/time'
import { positioningStatus } from '../services/positioningStatus'
import PositioningSnapshot, { PositioningFileView } from './PositioningSnapshot'
import PositioningDialog from './PositioningDialog'
import PositioningInterviewDialog from './PositioningInterviewDialog'

export default function AccountPositioningHistory({ relationId, canReadInterview }: { relationId?: number; canReadInterview: boolean }) {
  const [history, setHistory] = useState<PositioningCard[]>([]), [error, setError] = useState(''), [loading, setLoading] = useState(false)
  const [retry, setRetry] = useState(0), [preview, setPreview] = useState<PositioningCard>(), [interview, setInterview] = useState(false)
  useEffect(() => {
    let active = true; setHistory([]); setError(''); setLoading(Boolean(relationId))
    if (relationId) api.positioningCard.serviceOverview(relationId).then(data => { if (active) setHistory(data.history) })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '历史记录加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [relationId, retry])
  return <div className="account-positioning-history-grid">
    {loading ? <Spin size="small" /> : error ? <Alert type="error" message={error} action={<Button onClick={() => setRetry(v => v + 1)}>重试</Button>} />
      : history.length ? history.map(card => <article className="account-positioning-history-card" key={card.submissionId}>
        <Typography.Text>第 {card.submissionNo} 次提交 · {positioningStatus(card)}</Typography.Text>
        <Typography.Paragraph type="secondary">{formatTimestamp(card.submittedAt)}</Typography.Paragraph>
        <Button size="small" onClick={() => setPreview(card)}>查看本轮定位记录</Button>
        {Array.isArray(card.dictSnapshot?.pc_interview_files) && card.dictSnapshot.pc_interview_files.map((file: { id: number; name: string }) =>
          <PositioningFileView key={file.id} name={file.name} load={() => api.positioningCard.snapshotAttachment(card.id, file.id, card.submissionId)} />)}
      </article>) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无历史定位记录" />}
    {relationId && canReadInterview && <div className="account-positioning-history-actions"><Button size="small" onClick={() => setInterview(true)}>查看定位访谈及附件</Button></div>}
    <PositioningDialog title="历史定位记录" open={Boolean(preview)} onCancel={() => setPreview(undefined)} footer={<Button onClick={() => setPreview(undefined)}>关闭</Button>}>
      {preview && <PositioningSnapshot card={preview} />}
    </PositioningDialog>
    {interview && relationId && <PositioningInterviewDialog forceReadOnly relationId={relationId} onClose={() => setInterview(false)} onChanged={() => setInterview(false)} />}
  </div>
}
