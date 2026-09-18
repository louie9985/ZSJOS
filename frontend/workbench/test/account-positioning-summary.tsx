// Isolated browser fixture: actual account component, synthetic API adapter, no business writes.
import { createRoot } from 'react-dom/client'
import { Profiler } from 'react'
import { App, ConfigProvider, Typography } from 'antd'
import AccountProfilePanel from '../src/components/AccountProfilePanel'
import AccountPositioningCard from '../src/components/AccountPositioningCard'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const values = { pc_account_name: '不应在摘要出现的账号名称建议', pc_join_goal: '提升专业能力，形成稳定的健康知识分享习惯。',
  pc_learning_stage: '在学', pc_primary_track: '营养', pc_secondary_track: '健康管理', pc_cooperation: '每周可投入 4—8 小时',
  pc_shoot_time: '周末连续半天', pc_appearance: '愿意出镜', pc_expression: '可自然表达', pc_assets: '营养专业学习经历与实践笔记',
  pc_trust: '课程学习记录和已获得的资质', pc_risk: '工作日时间有限，需要提前安排拍摄。' }
const card = { id: 900001, cardNo: 'PC-TEST', serviceRelationId: 900001, submissionId: 1, status: 'confirmed', version: 1, submissionNo: 1,
  fieldsSnapshot: Object.keys(values).map((key, sort) => ({ key, title: key, type: 'textarea', enabled: true, sort })), valuesSnapshot: values, availableActions: [] }
let applied = card, version = 1, writes = 0
const diagnosis = location.search.includes('diagnosis')
let diagnosisSaved: Record<string, unknown> | undefined
const earlyDiagnosis = location.search.includes('early-diagnosis')
if (earlyDiagnosis) diagnosisSaved = { templateType: 'diagnosis_initial', cycle: 0 }
const completedTasks = new Set<number>()
let taskFailed = false
const fixtureTasks = ['diagnosis_7d','diagnosis_14d','diagnosis_28d'].map((templateType, index) => ({taskId:700+index,accountId:900001,studentPersonId:900001,title:templateType,templateType,cycle:1,dueAt:Date.now()+(index+1)*86400000,payload:{requirementSnapshot:{[templateType]:'测试诊断要求'}}}))
if (location.search.includes('overdue')) fixtureTasks[0].dueAt = Date.now()-86400000
let dictFailed = false
const diagnosisFields = ['stage','current_status','bottleneck'].map((key,i)=>({key,label:['当前期段','账号状态','当前瓶颈'][i],type:'text',group:'STATUS',ownerType:'AUTO',enabled:true,sort:30+i}))
const appearance = location.search.includes('appearance')
const metrics = location.search.includes('metrics')
const metricValues = location.search.includes('unbound') ? {} : {total_leads:8,month_leads:4,total_conversion:0.375,month_conversion:0.25,total_amount:1234.5,month_amount:234.5}
const metricFields = ['total_leads','total_conversion','total_amount','month_leads','month_conversion','month_amount'].map((key,index)=>({key,label:['累计客资数','累计成交率','累计成交金额','本月客资数','本月成交率','本月成交金额'][index],type:'number',group:'METRICS',ownerType:'AUTO',enabled:true,sort:20+index}))
const profileValues: Record<string, string> = { nickname: '测试账号', avatar: '清晰正面头像\n背景干净', background: '品牌背景\n展示专业方向' }
const newer = { ...card, submissionId: 2, submissionNo: 2, valuesSnapshot: { ...values, pc_join_goal: '新版目标：建立长期内容计划' } }
http.defaults.adapter = async config => {
  const url = config.url || ''; let data: unknown
  if (earlyDiagnosis && url.endsWith('/diagnosis/tasks')) {
    if (location.search.includes('task-error') && !taskFailed) {taskFailed=true;throw new Error('测试任务加载失败')}
    return {config,status:200,statusText:'OK',headers:{},data:{code:0,data:location.search.includes('empty-tasks') ? [] : fixtureTasks.filter(task=>!completedTasks.has(task.taskId))}}
  }
  if (url.includes('/dict-data/') && location.search.includes('dict-error') && !dictFailed) { dictFailed = true; throw new Error('测试字典加载失败') }
  if (url.includes('/dict-data/')) data=diagnosis ? ['zsjos_media_account_stage','zsjos_media_account_current_status','zsjos_media_account_primary_problem','zsjos_media_account_cooperation_level'].map(dictType=>({value:'fixture_value',label:'测试历史标签',dictType,status:0})) : []
  else if (url.endsWith('/profile/diagnosis')) { diagnosisSaved=JSON.parse(config.data); if (earlyDiagnosis && diagnosisSaved?.taskId) completedTasks.add(Number(diagnosisSaved.taskId)); document.documentElement.dataset.diagnosisPayload=JSON.stringify(diagnosisSaved); writes++; await new Promise(resolve=>setTimeout(resolve,250)); data=2 }
  else if (url.endsWith('/profile/history')) data={list:diagnosisSaved ? [{id:1,kind:'DIAGNOSIS',fieldKey:'diagnosis_initial',title:'启动诊断',resultVersion:2,content:JSON.stringify({...diagnosisSaved,currentStageLabel:'测试历史标签',accountStatusLabel:'测试历史标签',primaryProblemLabel:'测试历史标签'}),snapshots:[],files:[]}] : [],total:diagnosisSaved ? 1 : 0}
  else if (url.endsWith('/profile') && config.method === 'put') { Object.assign(profileValues, JSON.parse(config.data).changes); writes++; data = 2 }
  else if (url.endsWith('/profile')) data = { account: { id:900001,accountNo:'TEST-ACCOUNT',nickname:'测试账号',version:1,availableActions:['MAINTAIN_ACCOUNT'] },
    studentName:'测试学员',config:{id:1,versionNo:1,fields:[{key:'nickname',label:'昵称',type:'text',group:'PROFILE',ownerType:'OPERATOR',enabled:true,sort:1}, ...(appearance ? ['avatar','background'].map((key, index) => ({key,label:index ? '背景设置':'头像设置',type:'textarea',group:'PROFILE',ownerType:'OPERATOR',enabled:true,sort:index+2})) : [])]},
    values:appearance ? {...profileValues} : {nickname:'测试账号'},snapshots:[],files:{},sourceNotes:{},editableFields:appearance ? ['nickname','avatar','background'] : ['nickname'],missingFields:[],missingByOwner:{},canViewHistory:false }
  else if (url.endsWith('/application-options')) data = { submissionId: applied.submissionId, version, canApply: true, newerAvailable: applied.submissionId === 1, candidates: [newer, card] }
  else if (url.endsWith('/apply')) { applied = JSON.parse(config.data).submissionId === 2 ? newer : card; version++; writes++; data = true }
  else if (url.endsWith('/service-overview')) data = { history: [card], candidates: [] }
  else data = { effective: applied, current: null, history: [] }
  if (metrics && url.endsWith('/profile') && config.method !== 'put') {
    const profile = data as {config:{fields:unknown[]};values:Record<string,unknown>;sourceNotes:Record<string,string>}
    profile.config.fields.push(...metricFields); Object.assign(profile.values,metricValues)
    profile.sourceNotes=Object.fromEntries(metricFields.map(f=>[f.key,location.search.includes('unbound')?'学员尚未绑定本系统兼职账号':'来自学员绑定的本系统兼职账号']))
  }
  if (diagnosis && url.endsWith('/profile') && config.method !== 'put') {
    const profile = data as Record<string, any>
    profile.config.fields.push(...diagnosisFields)
    const periodicFields = ['diagnosis_7d','diagnosis_14d','adjustment_28d']
    profile.config.fields.push(...periodicFields.map((key,i)=>({key,label:['7天诊断记录','14天诊断记录','28天诊断记录'][i],type:'record',group:'REVIEW',ownerType:'DIRECTOR',enabled:true,sort:40+i})))
    profile.editableFields.push(...periodicFields)
    if (diagnosisSaved && diagnosisSaved.templateType !== 'diagnosis_initial') {
      const key = diagnosisSaved.templateType === 'diagnosis_28d' ? 'adjustment_28d' : String(diagnosisSaved.templateType)
      profile.latestRecords = {[key]:{id:42,kind:'DIAGNOSIS',fieldKey:key,title:'周期诊断',content:JSON.stringify(diagnosisSaved),snapshots:[],files:[],resultVersion:2}}
    }
    profile.canStartDiagnosis=!diagnosisSaved; profile.canSubmitDiagnosis=!!diagnosisSaved; profile.diagnosisStarted=!!diagnosisSaved; profile.canViewHistory=true
    if (earlyDiagnosis) {profile.diagnosisContext={submissionNo:1,syncedAt:'2026-09-18 09:00:00'};profile.positioningRequirements={diagnosis_7d:'测试诊断要求',diagnosis_14d:'测试诊断要求',diagnosis_28d:'测试诊断要求'}}
    profile.account.version=diagnosisSaved ? 2 : 1
    profile.snapshots=diagnosisSaved ? diagnosisFields.map(f=>({key:f.key,displayValue:'测试历史标签'})) : []
    Object.assign(profile.values,diagnosisSaved ? {stage:'fixture_value',current_status:'fixture_value',bottleneck:'fixture_value'} : {})
    profile.sourceNotes=Object.fromEntries(diagnosisFields.map(f=>[f.key,diagnosisSaved ? '启动诊断':'待完成启动诊断']))
  }
  document.getElementById('writes')!.textContent = `写请求 ${writes}`
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App>
  <div style={{ padding: 12 }}><div id="writes">写请求 0</div>
    <output id="render-cost" />
    {location.search.includes('panel') ? <Profiler id="maintenance" onRender={(_, phase, duration) => { const output = document.getElementById('render-cost'); if (output) { output.textContent = `${phase}: ${duration.toFixed(2)}ms`; output.dataset.samples = JSON.stringify([...JSON.parse(output.dataset.samples || '[]'), duration].slice(-100)) } }}><AccountProfilePanel account={{id:900001,accountNo:'TEST-ACCOUNT',version:1,primaryProblems:[],detailSnapshots:[],taskLine:[],availableActions:['MAINTAIN_ACCOUNT']}}
      student={{personId:900001,name:'测试学员',mobile:'测试联系方式',services:[]}} serviceRelationId={900001} canQuery canQueryPositioning canMaintain onSaved={async()=>{}} /></Profiler> : <section className="account-profile-section" style={{ width: 390, maxWidth: '100%', boxSizing: 'border-box' }}>
      <div className="account-profile-section-heading"><Typography.Title level={5}>账号定位卡</Typography.Title></div>
      <div className="account-profile-section-body"><AccountPositioningCard accountId={900001} canQuery={!location.search.includes('denied')} studentName="测试学员" studentContact="测试联系方式" serviceRelationId={900001} /></div>
    </section>}
  </div>
</App></ConfigProvider></ThemeProvider>)
