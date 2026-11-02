// Actual pages with synthetic HTTP only: target 555/999 is deliberately outside page one.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RegistrationPoolPage } from '../src/pages/RegistrationPages'
import StudentInfoTasksPage from '../src/pages/StudentInfoTasksPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const permissions=['zsjos:registration:query-pool','zsjos:student-info-task:query','zsjos:student-info-form:read']
const registration=(id:number)=>({id,orderId:id,orderItemId:id,orderNo:`TEST-ORDER-${id}`,studentName:id===555?'合成目标客户':`合成学员 ${id}`,productName:'合成课程',status:'pending',statusLabel:'待处理',orderStatus:'effective',orderStatusLabel:'已通过',version:0,items:[],routes:[],classAssignments:[],deliveries:[],completable:false,completionBlockReason:'合成只读验证'})
const task=(id:number)=>({id,orderId:id===999?555:id,orderItemId:id===999?555:id,personId:id,registrationCaseId:id===888?undefined:id===999?555:id,orderNo:`TEST-ORDER-${id===999?555:id}`,studentName:id===999?'合成目标客户':`合成学员 ${id}`,productName:'合成课程',orderStatus:id===888?'pending_approval':'effective',status:'PENDING',archiveStatus:'NONE',canProcess:true,candidates:[],version:0})
const requests:string[]=[]
http.defaults.adapter=async config=>{
  const url=config.url||'';requests.push(url);document.documentElement.dataset.navigationRequests=JSON.stringify(requests)
  let data:unknown=[];let code=0;let msg=''
  if(url.endsWith('/registration/pool-page'))data={list:Array.from({length:20},(_,i)=>registration(i+1)),total:60}
  else if(/\/registration\/\d+$/.test(url))data=registration(Number(url.split('/').pop()))
  else if(url.endsWith('/student-info-task/page'))data={list:Array.from({length:20},(_,i)=>task(i+1)),total:60}
  else if(url.endsWith('/by-registration/555'))data=task(999)
  else if(url.includes('/by-registration/')){code=400;msg='合成：未找到对应收集任务'}
  else if(/\/student-info-task\/\d+$/.test(url))data=task(Number(url.split('/').pop()))
  else if(url.endsWith('/information'))data={status:'NONE',canReadSensitive:false,canExport:false,fields:[],values:{},images:[]}
  return {data:{code,msg,data},status:200,statusText:'OK',headers:{},config}
}
document.documentElement.style.height='100%';document.body.style.height='100%';document.body.style.margin='0';document.getElementById('root')!.style.height='100%'
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App style={{height:'100%'}}><Routes>
  <Route path="/zsjos/registration-pool" element={<RegistrationPoolPage permissions={permissions}/>}/>
  <Route path="/zsjos/student-info-tasks" element={<StudentInfoTasksPage permissions={permissions}/>}/>
</Routes></App></ThemeProvider></BrowserRouter>)
