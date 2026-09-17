// Actual page with synthetic API responses only; never writes business data.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import { http } from '../src/services/api'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'
const fields = [
 { key: 'name', title: '账号名称建议', type: 'textarea', enabled: true, required: true, systemField: false, sort: 1, description: '最好帮助运营直接定下来' },
 { key: 'files', title: '采访稿全文', type: 'attachment', enabled: true, required: false, systemField: false, sort: 2, description: '上传本次文稿' },
]
const student = { personId: 2, personNo: 'S-FIXTURE', name: '合成测试学员', services: [{serviceRelationId: 30, status: 'active', directorStage: 'positioning_interview_completed', productName: '测试课程'}] }
const storageKey = 'zsjos-test-manual-card'
let card: any = JSON.parse(localStorage.getItem(storageKey) || 'null')
let writes = 0, uploads = 0, failUpload = false
const updateCounter = () => { document.getElementById('fixture-status')!.textContent = `写请求 ${writes}；上传 ${uploads}；版本 ${card?.version ?? '-'}；附件 ${JSON.stringify(card?.valuesSnapshot?.files || [])}` }
http.defaults.adapter = async config => {
 card = JSON.parse(localStorage.getItem(storageKey) || 'null')
 let data: any = [], code = 0, msg = ''
 const url = config.url || '', body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
 if (url.endsWith('/media-students/page')) data = {list:[student],total:1}
 else if (url.endsWith('/media-students/2')) data = {student,accounts:[],positioningDrafts:card?[{...card,accountId:null}]:[],positioningCards:[],contents:[],productionTickets:[],operationTimeline:[]}
 else if (url.endsWith('/contact-context')) data = {availableActions:['CREATE_POSITIONING_CARD'],visibleTabs:[],directorStage:'positioning_interview_completed'}
 else if (url.endsWith('/published-template')) data = {templateId:2,templateVersionId:7,fields,values:{},dictSnapshots:{}}
 else if (url.endsWith('/positioning-card/service-overview')) data={serviceRelationId:30,masterCardId:card?.id,canCreate:!card,canSubmit:true,canSelectMaster:false,candidates:card?[card]:[],current:card?{...card,availableActions:card.status==='co_creating'?['EDIT_POSITIONING_DRAFT']:[]}:null,effective:null,history:[]}
 else if (url.endsWith('/19/submit-review')) {writes++;card.status='operator_feasibility';card.version++;data=true}
 else if (url.endsWith('/positioning-card/get')) data = card
 else if (url.endsWith('/positioning-card/draft')) { writes++; card={id:19,templateId:2,templateVersionId:7,version:0,status:'co_creating',serviceRelationId:30,accountId:null,fieldsSnapshot:fields,valuesSnapshot:body.values,dictSnapshot:{}}; data={id:19,version:0} }
 else if (url.endsWith('/positioning-card/draft/19')) { writes++; if(body.version!==card.version){code=1900014003;msg='定位卡已被其他人修改，请刷新后重试'}else{card.version++;card.valuesSnapshot=body.values;data={id:19,version:card.version}} }
 else if (url.endsWith('/19/attachments')) { writes++;uploads++; if(failUpload){failUpload=false;code=500;msg='模拟上传失败'}else data={id:100+uploads,name:'测试附件.txt',size:8} }
 else if (url.includes('/19/attachments/')) data={id:101,name:'测试附件.txt',size:8,url:'data:text/plain,test'}
 localStorage.setItem(storageKey, JSON.stringify(card))
 updateCounter()
 return {config,status:200,statusText:'OK',headers:{},data:{code,msg,data}}
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App><div id="fixture-status">写请求 0；上传 0</div><button onClick={()=>{card=JSON.parse(localStorage.getItem(storageKey) || 'null');if(card) card.version++;localStorage.setItem(storageKey,JSON.stringify(card));updateCounter()}}>模拟其他窗口保存</button><button onClick={()=>{failUpload=true}}>下次上传失败</button><MemoryRouter><MediaStudentsPage permissions={['zsjos:positioning-card:query','zsjos:positioning-card:create','zsjos:positioning-card:submit-review']} /></MemoryRouter></App></ConfigProvider></ThemeProvider>)
