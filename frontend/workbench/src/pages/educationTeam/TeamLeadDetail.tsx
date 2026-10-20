import { Alert,Button,Descriptions,Empty,Space,Spin,Tabs } from 'antd'
import { educationTeamApi,type EducationTeamLead } from '../../services/educationTeam'
import LeadFollowUpPanel from '../../components/LeadFollowUpPanel'
import LeadFlowHistoryPanel from '../../components/LeadFlowHistoryPanel'
import LeadAppealPanel from '../../components/LeadAppealPanel'
import LeadComplaintPanel from '../../components/LeadComplaintPanel'
import { useEducationResource } from './useEducationResource'
import type { ManagedLead } from '../../services/api'
const noop=()=>{}
export default function TeamLeadDetail({id,onBack}:{id:number;onBack:()=>void}){
 const state=useEducationResource(()=>educationTeamApi.lead(id),[id]);const lead=state.data
 function tabs(row:EducationTeamLead){
  const readOnly={...row,availableActions:[]} as ManagedLead
  return [{key:'overview',label:'客资概览',children:<Descriptions column={1} bordered items={[{key:'number',label:'客资编号',children:row.leadNo||'编号缺失'},{key:'name',label:'姓名',children:row.submittedName},{key:'phone',label:'手机号',children:row.submittedMobile},{key:'category',label:'客资分类',children:row.leadCategoryLabelSnapshot||'历史快照缺失'},{key:'channel',label:'来源渠道',children:row.sourceChannelLabelSnapshot||'历史快照缺失'}]}/>},
   {key:'follow-ups',label:'跟进记录',children:<LeadFollowUpPanel lead={readOnly} open={false} refreshVersion={0} onClose={noop} onChanged={noop} onTotalChange={noop}/>},
   {key:'flow-history',label:'流转记录',children:<LeadFlowHistoryPanel leadId={id}/>},
   {key:'appeals',label:'申诉记录',children:<LeadAppealPanel lead={readOnly} readOnly onChanged={noop}/>},
   {key:'complaints',label:'投诉记录',children:<LeadComplaintPanel leadId={id}/>},
  ].filter(tab=>(row.visibleTabs??['overview']).includes(tab.key as NonNullable<ManagedLead['visibleTabs']>[number]))
 }
 return <><Space><Button onClick={onBack}>返回员工详情</Button><strong>{lead?.leadNo}</strong></Space>{state.loading?<Spin/>:state.error?<Alert type="error" title={state.error} action={<Button onClick={state.reload}>重试</Button>}/>:lead?<Tabs items={tabs(lead)}/>:<Empty/>}</>
}
