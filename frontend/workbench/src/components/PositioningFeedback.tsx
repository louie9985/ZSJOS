import { Tag } from 'antd'
import type { PositioningCard } from '../services/api'
import { formatTimestamp } from '../services/time'
import { LinkedText } from './ResourceLink'
import type { ReactNode } from 'react'

export default function PositioningFeedback({ card, children }: { card: PositioningCard; children?: ReactNode }) {
  const studentChanged = card.status === 'change_requested' || card.submissionStatus === 'change_requested'
  const rejected = card.status === 'operator_rejected' || card.submissionStatus === 'operator_rejected'
  return <section className="positioning-feedback" aria-label="本轮确认与反馈">
    <header><strong>本轮确认与反馈</strong><span>第 {card.submissionNo || 0} 次提交</span></header>
    <div className="positioning-feedback-grid">
      <section data-attention={studentChanged}><div className="positioning-feedback-title"><strong>学员确认意见</strong>{studentChanged && <Tag color="warning">要求修改</Tag>}</div>
        <time>{card.studentDecidedAt ? formatTimestamp(card.studentDecidedAt) : '确认时间未记录'}</time>
        <p><LinkedText text={card.studentDecisionComment || (card.studentDecidedAt ? '未附文字意见' : '暂无确认意见')} /></p>
      </section>
      <section data-attention={rejected}><div className="positioning-feedback-title"><strong>运营复核意见</strong>{rejected && <Tag color="warning">已退回</Tag>}</div>
        <time>{card.operatorReviewedAt ? formatTimestamp(card.operatorReviewedAt) : '复核时间未记录'}</time>
        <p><LinkedText text={card.operatorReviewComment || (card.operatorReviewedAt ? '未附文字意见' : '暂无复核意见')} /></p>
      </section>
    </div>
    {children}
  </section>
}
