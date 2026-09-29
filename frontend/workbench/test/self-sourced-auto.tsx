// UTF-8. Isolated synthetic services for the real submission and history components.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import LeadSubmissionPage from '../src/pages/LeadSubmissionPage'
import FollowUpTimeline from '../src/components/FollowUpTimeline'
import LeadFollowUpCharts from '../src/components/LeadFollowUpCharts'
import { api, type LeadFollowUp } from '../src/services/api'
const query=new URLSearchParams(location.search)
const fixture={requests:[] as unknown[],outcome:query.get('outcome')||'created',fail:false}
Object.assign(window,{autoFixture:fixture})
const record:LeadFollowUp={id:1,leadId:1,recordScope:'lead',generationSource:query.has('education')?'education_self_sourced_auto':'sales_self_sourced_auto',operatorUserId:1,operatorName:'测试销售',occurredAt:Date.now()-86400000,firstInAssignment:true,method:'other',methodLabel:'录单时其他方式',result:'interested',resultLabel:'录单时有意向',categoryBefore:'a',categoryBeforeLabel:'录单分类',categoryAfter:'a',categoryAfterLabel:'录单分类',salesStageAfterLabelSnapshot:'录单阶段',remark:'已联系，有意向',images:[]}
const records=[record,{...record,id:2,recordScope:'opportunity' as const,generationSource:undefined,method:'phone',methodLabel:'电话',firstInAssignment:false}]
const create=async (payload:any)=>{fixture.requests.push(JSON.parse(JSON.stringify(payload)));if(fixture.fail)throw new Error('模拟提交失败');return {outcome:fixture.outcome,leadNo:'LD-UI-TEST',reviewId:3,qualificationStatus:payload.newMediaProviderUserId?'pending':'valid',automaticQualificationApplied:!payload.newMediaProviderUserId}}
Object.assign(api,{
 areaTree:async()=>[{id:990000000,name:'其他省份',selectionCode:'OTHER',children:[{id:990000001,name:'其他城市',selectionCode:'OTHER',children:[]}]}],
 leadCatalog:async()=>({categoryTree:[],spus:[],skus:[]}),
 dictDataByType:async(type:string)=>[{value:type==='zsjos_lead_category'?'a':'channel',label:type==='zsjos_lead_category'?'测试分类':'测试渠道'}],
 newMediaProviders:async()=>[{id:2,nickname:'测试提供方'}],
 createSelfSourcedLead:create,createEducationSelfSourcedLead:create,createLead:create,
 leadFollowUpPage:async()=>({list:records,total:records.length}),checkSelfSourcedLeadContact:async()=>false
})
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><main style={{padding:16,maxWidth:1100,margin:'auto'}}>{query.has('history')?<><FollowUpTimeline records={records}/><LeadFollowUpCharts leadId={1}/></>:<LeadSubmissionPage selfSourced educationSelfSourced={query.has('education')} permissions={[query.has('education')?'zsjos:lead:education-self-sourced:create':'zsjos:lead:self-sourced:create']}/>}</main></BrowserRouter></App></ThemeProvider>)
