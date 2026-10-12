import { Empty, Tabs } from 'antd'
import { api, type ManagedLead } from '../../services/api'
import LeadDetailOverview from '../../components/LeadDetailOverview'
import LeadFollowUpPanel from '../../components/LeadFollowUpPanel'
import LeadAppealPanel from '../../components/LeadAppealPanel'
import LeadFlowHistoryPanel from '../../components/LeadFlowHistoryPanel'
import LeadComplaintPanel from '../../components/LeadComplaintPanel'
import SalesOrderDetailCards from '../../components/SalesOrderDetailCards'
import { Resource, useResource } from './PerformanceShared'

const noop = () => {}
export type ConversionSelection = { kind: 'lead' | 'order'; id: number }

function ReadOnlyLead({ lead }: { lead: ManagedLead }) {
 const items = [
  { key: 'overview', label: '客资概览', children: <LeadDetailOverview lead={lead} categoryLabel={() => lead.leadCategoryLabelSnapshot || '客资分类快照缺失'} channelLabel={() => lead.sourceChannelLabelSnapshot || '来源渠道快照缺失'} showFollowUp={false}/> },
  { key: 'follow-ups', label: '跟进记录', children: <LeadFollowUpPanel lead={lead} open={false} refreshVersion={0} onClose={noop} onChanged={noop} onTotalChange={noop}/> },
  { key: 'appeals', label: '申诉记录', children: <LeadAppealPanel lead={lead} readOnly onChanged={noop}/> },
  { key: 'flow-history', label: '流转记录', children: <LeadFlowHistoryPanel leadId={lead.id}/> },
  { key: 'complaints', label: '投诉记录', children: <LeadComplaintPanel leadId={lead.id}/> },
 ].filter(item => (lead.visibleTabs ?? ['overview']).includes(item.key as NonNullable<ManagedLead['visibleTabs']>[number]))
 return items.length ? <Tabs items={items}/> : <Empty description="暂无授权的客资详情内容"/>
}

export default function ConversionRecordDetail({ selection }: { selection: ConversionSelection }) {
 const state = useResource(async () => selection.kind === 'lead'
  ? { kind: 'lead' as const, value: await api.managedLead(selection.id) }
  : { kind: 'order' as const, value: await api.managementSalesOrder(selection.id) }, [selection.kind, selection.id])
 return <Resource state={state}>{state.data?.kind === 'lead' ? <ReadOnlyLead lead={state.data.value}/>
  : state.data?.kind === 'order' ? <SalesOrderDetailCards order={state.data.value} mode="team"/> : null}</Resource>
}
