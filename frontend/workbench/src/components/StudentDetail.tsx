import { useState, type ReactNode } from 'react'
import { Tabs, Typography } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'
import type { MyStudent, StudentContactContext } from '../services/api'
import { formatTimestamp } from '../services/time'
import LeadDetailOverview, { type LeadOverviewSlots, type StudentOverviewContext } from './LeadDetailOverview'
import type { LeadDetailExtraTab } from './LeadDetail'
import StudentInfoPanel from './StudentInfoPanel'

export default function StudentDetail({ student, service, contactContext, contactRecords = [], toolbar, overviewSlots, overviewContent, contextHeader, extraTabs = [], activeTab: controlledActiveTab, onTabChange }: {
  student: MyStudent
  service: MyStudent['services'][number]
  contactContext?: StudentContactContext
  contactRecords?: StudentOverviewContext['contactRecords']
  toolbar?: ReactNode
  overviewSlots?: LeadOverviewSlots
  overviewContent?: ReactNode
  contextHeader?: ReactNode
  extraTabs?: LeadDetailExtraTab[]
  activeTab?: string
  onTabChange?: (key: string) => void
}) {
  const [internalActiveTab, setInternalActiveTab] = useState('overview')
  const studentContext = contactContext ? { service, contactContext, contactRecords } : undefined
  const collectionLeadId = service.leadId ?? student.leadId
  const items: LeadDetailExtraTab[] = [{
    key: 'overview',
    label: '概览',
    children: <div className="lead-detail-tab-content">{overviewContent || <LeadDetailOverview student={student} showFollowUp={false} categoryLabel={() => '-'} channelLabel={() => '-'} toolbar={toolbar} studentContext={studentContext} studentService={service} slots={overviewSlots} />}</div>
  }, ...(collectionLeadId && contactContext?.visibleTabs.includes('student-info') ? [{ key: 'student-info', label: '学员信息', children: <StudentInfoPanel key={collectionLeadId} leadId={collectionLeadId} /> }] : []), ...extraTabs]
  const keys = new Set(items.map(item => item.key))
  const activeTab = controlledActiveTab && keys.has(controlledActiveTab)
    ? controlledActiveTab : keys.has(internalActiveTab) ? internalActiveTab : items[0]?.key

  return <div className="lead-inbox-detail">
    <div className="lead-detail-hero">
      <Typography.Title level={4}>{student.name || '未填写姓名'}</Typography.Title>
      {contactContext?.currentTask?.dueAt && <div className="lead-hero-next-followup">
        <ClockCircleOutlined />
        <span className="lead-hero-next-label">下次联系</span>
        <span className="lead-hero-next-time">{formatTimestamp(new Date(contactContext.currentTask.dueAt).getTime())}</span>
      </div>}
    </div>
    {contextHeader}
    <Tabs className="lead-detail-tabs" activeKey={activeTab} onChange={key => { setInternalActiveTab(key); onTabChange?.(key) }} items={items} />
  </div>
}
