// UTF-8. All requests are intercepted; no production data or writes.
import { createRoot } from 'react-dom/client'
import { App, Button, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import { http } from '../src/services/api'
import { useContentReviewSubmit } from '../src/components/ContentReviewReviewerSelect'
import AccountMaintenancePanel from '../src/components/AccountMaintenancePanel'
import ContentReviewStudentDetail from '../src/components/ContentReviewStudentDetail'
import { useState } from 'react'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'
const mode = new URLSearchParams(location.search).get('mode') || 'normal'
const card = {id:60,submissionId:61,submissionNo:1,status:'confirmed',fieldsSnapshot:[{key:'goal',title:'内容目标',type:'textarea',enabled:true}],valuesSnapshot:{goal:'本次账号内容定位'},dictSnapshot:{},availableActions:[],canUploadEvidence:false}
let expired = false
const requests: unknown[] = []
Object.assign(window,{reviewFixture:{requests,expire:()=>{expired=true}}})
http.defaults.adapter=async config=>{
  const url=config.url || '';requests.push({url,method:config.method,data:config.data})
  let data: unknown
  if(url.endsWith('/submit-context')) data={processDefinitionId:'version-2',processDefinitionVersion:2,directorTaskKey:'opaque',roleIds:[7],candidates:mode==='empty'?[]:[{id:20,nickname:'审核编导甲'},{id:21,nickname:'审核编导乙'}],previousDirectorUserId:mode==='revision'?20:undefined}
  else if(url.endsWith('/submit')) data=true
  else if(url.includes('/student-read') && expired) return {config,status:200,statusText:'OK',headers:{},data:{code:1900012144,msg:'本次审批的学员查看权限已失效'}}
  else if(url.endsWith('/student-read')) data={personId:30,personNo:'ST-TEST-30',name:'验收学员',mobile:'测试联系方式',accounts:[{id:40,accountNo:'AC-TEST',nickname:'本轮账号',platformLabel:'测试平台',serviceRelationId:50}]}
  else if(url.endsWith('/account-overview')) data={effective:card,history:[card]}
  else if(url.endsWith('/application-options')) data={candidates:[],canApply:false}
  else if(url.endsWith('/profile')) data={account:{id:40,availableActions:[]},config:{fields:[{key:'bio',label:'账号简介',type:'textarea',enabled:true,ownerType:'DIRECTOR',group:'STATUS'}]},values:{bio:'仅本轮相关资料'},snapshots:[],files:{},editableFields:[],missingFields:[],missingByOwner:{},sourceNotes:{},canViewHistory:false}
  else if(url.endsWith('/positioning-overview')) data={account:{effective:card,history:[card]},service:{serviceRelationId:50,masterCardId:60,current:card,effective:card,history:[card],candidates:[],canCreate:false,canSelectMaster:false,canSubmit:false}}
  else if(url.endsWith('/positioning')) data=[{id:60,submissionId:61,submissionNo:1,fieldsSnapshot:[{key:'goal',title:'内容目标',type:'textarea',enabled:true}],valuesSnapshot:{goal:'本次账号内容定位'},dictSnapshot:{},availableActions:[]}]
  else if(url.endsWith('/file-content')) return {config,status:200,statusText:'OK',headers:{},data:new Blob(['%PDF-1.4'],{type:'application/octet-stream'})}
  else if(url.endsWith('/file')) data={id:99,name:'访谈测试.pdf',type:'application/pdf',size:8}
  else if(url.endsWith('/interview')) data={studentPersonId:30,serviceRelationId:50,studentName:'验收学员',status:'completed',version:1,fields:[{key:'experience',title:'经历',enabled:true,allowRemark:true}],items:[{fieldKey:'experience',status:'DONE',remark:'已核对访谈资料'}],attachments:[{fileId:99,fileName:'访谈测试.pdf',mimeType:'application/pdf',fileSize:8}],statusOptions:{DONE:"已沟通"},availableActions:[]}
  else throw new Error(`Unexpected fixture request ${url}`)
  return {config,status:200,statusText:'OK',headers:{},data:{code:0,data}}
}
function Fixture(){if(mode==='ordinary')return <AccountMaintenancePanel account={{id:40,availableActions:[]}} canQuery canQueryPositioning={false} canMaintain={false} onSaved={async()=>undefined} />;return <ReviewFixture/>}
function ReviewFixture(){const submit=useContentReviewSubmit();const [done,setDone]=useState(false)
return <main style={{padding:16}}><Button onClick={async()=>setDone(await submit(10,2))}>提交测试审批</Button>{done&&<p>提交成功</p>}
<ContentReviewStudentDetail batchId={10} personId={30} initialAccountId={mode==='tampered'?99:40}/></main>}
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><ConfigProvider><App><Fixture/></App></ConfigProvider></ThemeProvider></MemoryRouter>)
