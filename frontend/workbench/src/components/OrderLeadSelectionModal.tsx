import { useId } from 'react'
import { Alert, Tag, Typography } from 'antd'
import type { OrderLeadCandidate } from '../services/api'
import { LEAD_STATUS_LABELS } from '../constants'
import OrderLeadProfile from './OrderLeadProfile'
import '../styles/components/order-lead-selection.css'

export default function OrderLeadSelectionModal({ candidates, selectedId, onSelect, disabled = false, showNotice = true }: {
  candidates: OrderLeadCandidate[]; selectedId?: number; disabled?: boolean; showNotice?: boolean
  onSelect: (id: number) => void
}) {
  const groupName = useId()
  return <section aria-label="关联客资">
    {showNotice && <Alert type="info" showIcon title={candidates.length === 1 ? '已自动关联唯一匹配客资' : '请选择本次订单关联的客资'}
      description="订单归属当前录单人，关联不会改变客资状态或归属。"/>}
    <div className="order-lead-deck" role="radiogroup" aria-label="候选客资">
      {candidates.map(candidate => {
        const selected = candidate.id === selectedId
        return <section key={candidate.id} className={`order-lead-card${selected ? ' is-selected' : ''}${disabled ? ' is-disabled' : ''}`}
          onClick={event => {
            if (!disabled && !(event.target as HTMLElement).closest('button,a,input,label')) onSelect(candidate.id)
          }}>
          <div className="order-lead-card-heading">
            <Tag>{LEAD_STATUS_LABELS[candidate.status === 'converted' ? 'valid' : candidate.status] || '未知状态'}</Tag>
            <label className="order-lead-card-choice">
              <input type="radio" name={groupName} value={candidate.id} checked={selected} disabled={disabled}
                aria-label={`选择客资 ${candidate.profile.leadNo || '未记录'}`} onChange={() => onSelect(candidate.id)}/>
              {selected ? '已选中' : '选择'}
            </label>
          </div>
          <Typography.Title level={4}>{candidate.profile.submittedName || '姓名未记录'}</Typography.Title>
          <OrderLeadProfile leadProfile={candidate.profile} formalOwnerIdentityLabel="按当前录单人确定" table={false}/>
        </section>
      })}
    </div>
  </section>
}
