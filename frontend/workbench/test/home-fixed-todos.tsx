import {createRoot} from 'react-dom/client'
import {BrowserRouter} from 'react-router-dom'
import {ConfigProvider} from 'antd'
import HomeTodoSummary from '../src/components/HomeTodoSummary'
import {api} from '../src/services/api'
import '../src/styles/index.css'
const params=new URLSearchParams(location.search)
api.bpmTaskPage=async()=>{document.body.dataset.bpmRequests=String(Number(document.body.dataset.bpmRequests||0)+1);if(params.has('error'))throw new Error('审批加载失败');return {list:[],total:7}}
createRoot(document.getElementById('root')!).render(<ConfigProvider><BrowserRouter><main style={{padding:20}}><p>固定待办隔离验证</p><section className="home-summary-region" style={{height:innerWidth>900?320:'auto'}}><HomeTodoSummary permissions={params.has('denied')?[]:['zsjos:business-task:query','bpm:task:query']} summary={params.has('error')?undefined:{overdue:2,today:3,future:4,unscheduled:5}} loading={false} error={params.has('error')?'业务加载失败':''} refreshVersion={0} onRefresh={async()=>{}}/></section></main></BrowserRouter></ConfigProvider>)
