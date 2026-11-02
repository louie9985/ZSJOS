// Synthetic transport only; renders the actual public-pool page and shared loading footer.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { BrowserRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { RegistrationPoolPage } from '../src/pages/RegistrationPages'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const rows=Array.from({length:65},(_,index)=>({id:index+1,orderId:index+1,orderItemId:index+1,orderNo:`TEST-ORDER-${index+1}`,studentName:`合成履约 ${index+1}`,productName:'合成课程',status:'pending',statusLabel:'待处理',orderStatus:'effective',orderStatusLabel:'已通过',version:0,items:[],routes:[],classAssignments:[],deliveries:[],completable:false,completionBlockReason:'合成只读验证'}))
const requests:number[]=[],details:number[]=[]
http.defaults.adapter=async config=>{
  let data:unknown=[]
  if(config.url?.endsWith('/pool-page')){const page=Number(config.params?.pageNo||1);requests.push(page);data={list:rows.slice((page-1)*20,page*20),total:65}}
  else if(/\/registration\/\d+$/.test(config.url||'')){const id=Number(config.url?.split('/').pop());details.push(id);data=rows.find(row=>row.id===id)}
  document.documentElement.dataset.requests=JSON.stringify(requests);document.documentElement.dataset.details=JSON.stringify(details)
  return {data:{code:0,data},status:200,statusText:'OK',headers:{},config}
}
document.documentElement.style.height='100%';document.body.style.height='100%';document.body.style.margin='0';document.getElementById('root')!.style.height='100%'
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App style={{height:'100%'}}><RegistrationPoolPage permissions={['zsjos:registration:query-pool']}/></App></ThemeProvider></BrowserRouter>)
