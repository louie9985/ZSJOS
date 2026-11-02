// UTF-8. Synthetic test-only transport; real collection page.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { BrowserRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import StudentInfoTasksPage from '../src/pages/StudentInfoTasksPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const rows = [
 {id:3,orderId:3,orderItemId:3,personId:3,formId:33,version:0,orderNo:"TEST-003",studentName:"草稿示例",productName:"学历提升",orderStatus:"effective",status:"DRAFT",archiveStatus:"COMPLETE",canProcess:true,candidates:[{formId:7,productName:"当前唯一档案",revision:0,missing:[]}]},
 {id:1,orderId:1,orderItemId:1,personId:1,version:0,orderNo:'TEST-001',studentName:'合成学员',productName:'学历提升',orderStatus:'pending_approval',status:'PENDING',archiveStatus:'INCOMPLETE',canProcess:true,candidates:[{formId:5,productName:'历史职业项目',revision:0,missing:['报名层次','拟报名院校']}]},
 {id:2,orderId:2,orderItemId:2,personId:2,formId:22,version:0,orderNo:'TEST-002',studentName:'终止示例',productName:'职业项目',orderStatus:'terminated',status:'SUBMITTED',archiveStatus:'NONE',canProcess:false,candidates:[]}
]
const fixtureParams=new URLSearchParams(location.search)
const lazyFixture=fixtureParams.has('lazy')
const requests:number[]=[]
let failedOnce=false
const longFixture = fixtureParams.has('long')||lazyFixture
if(longFixture) {
  for(let id=4;id<=(lazyFixture?64:24);id++) rows.push({...rows[0],id,orderNo:`TEST-${id}`,studentName:`合成长列表 ${id}`})
  document.documentElement.style.height='100%';document.body.style.height='100%';document.body.style.margin='0';
  document.getElementById('root')!.style.height='100%'
}
http.defaults.adapter = async config => {
  if(lazyFixture&&config.url?.endsWith('/page')) {
    const page=Number(config.params?.pageNo||1)
    requests.push(page);document.documentElement.dataset.requestPages=JSON.stringify(requests)
    if(page===2&&fixtureParams.has('fail')&&!failedOnce){failedOnce=true;throw new Error('合成第二页加载失败')}
  }
  const taskId=Number(config.url?.match(/student-info-task\/(\d+)/)?.[1])
  const taskRow=rows.find(row=>row.id===taskId)
  if(config.url?.endsWith('/reuse')&&taskRow){document.documentElement.dataset.archiveReuse=String(config.data);taskRow.status='REUSED';taskRow.formId=99;taskRow.version++}
  const filtered=rows.filter(r=>(!config.params?.personId||r.personId===Number(config.params.personId))&&(!config.params?.status||r.status===config.params.status)&&(!config.params?.orderStatus||r.orderStatus===config.params.orderStatus))
  const data=/student-info-task\/\d+$/.test(config.url||'')||config.url?.endsWith('/reuse')?taskRow:config.url?.endsWith('/archive')?{id:config.params?.formId,status:'SUBMITTED',canReadSensitive:false,canExport:false,fields:[{key:'name',label:'姓名',type:'text',enabled:true,required:true,group:'basic',sort:10}],values:{name:'当前档案合成资料'},images:[]}:config.url?.endsWith('/link')?{formId:33,status:'DRAFT',registrationVersion:0,url:'https://example.test/student-info-form#token=synthetic',createdAt:Date.now(),expiresAt:Date.now()+86400000,canRegenerate:true,canRevoke:true}:config.url?.includes('/1/information')?{status:'NONE',canReadSensitive:false,canExport:false,fields:[],values:{},images:[]}:config.url?.endsWith('/information')?{id:33,status:config.url?.includes('/3/')?'DRAFT':'SUBMITTED',canReadSensitive:false,canExport:false,fields:longFixture?Array.from({length:32},(_,index)=>({key:`field${index}`,label:`合成字段 ${index}`,type:'text',enabled:true,required:true,group:'basic',sort:index})):[{key:'name',label:'姓名',type:'text',enabled:true,required:true,group:'basic',sort:10}],values:{name:'已保存的合成报名资料'},images:[]}:{list:lazyFixture?filtered.slice(((config.params?.pageNo||1)-1)*20,(config.params?.pageNo||1)*20):filtered,total:filtered.length}
  return {data:{code:0,data},status:200,statusText:'OK',headers:{},config}
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App style={longFixture?{height:'100%'}:undefined}><StudentInfoTasksPage permissions={['zsjos:student-info-form:read','zsjos:student-info-task:query','zsjos:registration:query-pool','zsjos:student-info-task:reuse','zsjos:student-info-form:create','zsjos:student-info-form:sensitive-read','zsjos:student-info-form:link-read','zsjos:student-info-form:sent']}/></App></ThemeProvider></BrowserRouter>)
