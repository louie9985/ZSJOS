import { useId, useState } from 'react'
import { Button, Radio } from 'antd'
import { followUpChoiceView, FOLLOW_UP_VISIBLE_OPTIONS, type FollowUpOption } from '../services/followUpForm'

export default function FollowUpChoiceGroup({ options, value, onChange, disabled, label, allowClear = false, id }: {
  options: FollowUpOption[]; value?: string; onChange?: (value?: string) => void; disabled?: boolean; label: string; allowClear?: boolean; id?: string
}) {
  const [expanded, setExpanded] = useState(false)
  const groupId = useId()
  const { visible, hiddenSelection } = followUpChoiceView(options, value, expanded)
  return <div id={id} className="follow-up-choice-field">
    {allowClear && <Button type="link" size="small" disabled={disabled || !value} onClick={() => onChange?.(undefined)} aria-label={`清空${label}`}>清空</Button>}
    <Radio.Group id={groupId} aria-label={label} value={value} onChange={event => onChange?.(event.target.value)} disabled={disabled} size="small" className="follow-up-choices">
      {visible.map(option => <Radio.Button key={option.value} value={option.value} disabled={option.disabled}>{option.label}</Radio.Button>)}
    </Radio.Group>
    {hiddenSelection && <div className="follow-up-selected-summary" aria-live="polite">已选：{hiddenSelection}</div>}
    {options.length > FOLLOW_UP_VISIBLE_OPTIONS && <Button type="link" size="small" disabled={disabled} aria-controls={groupId} aria-expanded={expanded} onClick={() => setExpanded(current => !current)}>{expanded ? '收起' : `展开全部（共 ${options.length} 项）`}</Button>}
    {!options.length && <div className="follow-up-hint">暂无可选{label}{allowClear ? '' : '，请联系管理员'}</div>}
  </div>
}
