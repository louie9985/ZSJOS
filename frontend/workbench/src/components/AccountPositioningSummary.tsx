import { Tag, Typography } from 'antd'
import type { ReactNode } from 'react'
import type { PositioningCard } from '../services/api'
import { accountPositioningSummary } from '../services/accountPositioningSummary'

export default function AccountPositioningSummary({ card, studentName, studentContact, history }: {
  card?: PositioningCard; studentName?: string; studentContact?: string; history?: ReactNode
}) {
  const row = (key: string, label: string, content: ReactNode, identity = false) => <div key={key}
    className={`account-profile-row account-positioning-summary-row ${identity ? 'owner-auto' : 'owner-director'}`} data-positioning-summary-key={key}>
    <div><Typography.Text>{label}</Typography.Text><Tag className="account-owner" color={identity ? 'red' : 'blue'}>
      {identity ? '学员资料' : '定位卡同步'}</Tag></div>
    <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', minWidth: 0 }}>{content}</div>
  </div>
  return <div className="account-positioning-summary" style={{ minWidth: 0, width: '100%' }}>
    {row('student_name', '学员姓名', studentName || '未记录', true)}
    {row('contact', '联系方式', studentContact || '未记录', true)}
    {accountPositioningSummary(card).map(field => row(field.key, field.label, field.value))}
    {history && <div className="account-profile-row" data-positioning-summary-key="positioning_history">
      <div><Typography.Text>历史定位、采访记录</Typography.Text></div>
      <div>{history}</div>
    </div>}
  </div>
}
