// Browser acceptance fixture; synthetic records, no backend writes.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import AccountPositioningCard from '../src/components/AccountPositioningCard'
import { http } from '../src/services/api'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'

const field = { key: 'pc_account_name', title: '账号名称建议', type: 'textarea', enabled: true, required: false, systemField: false, sort: 10, description: '最好帮助运营直接定下来' }
const card = { id: 900001, cardNo:'PC-TEST', submissionId:1, status: 'confirmed', version: 1, submissionNo: 1, studentDecidedAt:'2026-09-17 10:00', fieldsSnapshot: [field], valuesSnapshot: { pc_account_name: '旧版名称保持不变' }, availableActions: [] }
const newer={...card,submissionId:2,submissionNo:2,valuesSnapshot:{pc_account_name:'新版已确认名称'}}
let applied=card,version=1,writes=0
http.defaults.adapter = async config => {
 const url=config.url||'';let data:unknown
 if(url.endsWith('/application-options'))data={accountId:900001,submissionId:applied.submissionId,version,canApply:true,newerAvailable:applied.submissionId===1,candidates:[newer,card]}
 else if(url.endsWith('/apply')){applied=JSON.parse(config.data).submissionId===2?newer:card;version++;writes++;data=true}
 else data={effective:applied,current:null,history:[]}
 document.getElementById('writes')!.textContent=`应用写请求 ${writes}`
 return {config,status:200,statusText:'OK',headers:{},data:{code:0,data}}
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App><div style={{ padding: 16, maxWidth: 720 }}><div id="writes">应用写请求 0</div><AccountPositioningCard accountId={900001} canQuery={!location.search.includes('denied')} /></div></App></ConfigProvider></ThemeProvider>)
