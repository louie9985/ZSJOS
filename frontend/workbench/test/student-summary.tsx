import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import StudentSummaryPage from '../src/pages/StudentSummaryPage'
import { studentSummaryApi } from '../src/services/studentSummary'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const state = { queries: [] as unknown[], exports: [] as unknown[], fail: params.has('fail') }
Object.assign(window, { summaryFixture: state })
http.defaults.adapter = async () => { throw Error('No live API in fixture') }
studentSummaryApi.columns = async () => [
 {key:'student.name',label:'学员·姓名',group:'学员'}, {key:'student.mobile',label:'学员·手机号',group:'学员'},
 {key:'collectionStatus',label:'填写状态',group:'状态',filters:[{text:'已填写',value:'true'},{text:'未填写',value:'false'}]},
 {key:'info.name',label:'姓名',group:'报名资料'}, {key:'info.mobile',label:'手机号码',group:'报名资料'},
 {key:'info.id_card',label:'身份证号码',group:'资料'}, {key:'extra.theory',label:'理论',group:'档案'},
]
studentSummaryApi.classes = async () => [{value:1,label:'测试班级一 · 班主任甲 · 2026年11月考期'}, {value:2,label:'测试班级二 · 班主任乙'}]
studentSummaryApi.coverage = async () => ({submitted:1,members:2})
studentSummaryApi.page = async query => {
 state.queries.push(query)
 if(state.fail) throw Error('测试加载失败')
 const list=[{serviceRelationId:1,values:{'student.name':'学员甲','student.mobile':'13800000001','info.name':'档案甲','info.mobile':'13800000002',collectionStatus:'已填写'}},
 {serviceRelationId:2,values:{'student.name':'学员乙','student.mobile':'13800000003',collectionStatus:'未填写'}}]
 const filtered=list.filter(row=>query.filled===undefined || (row.serviceRelationId===1)===query.filled)
 return {total:filtered.length,list:filtered}
}
studentSummaryApi.export = async (query,columns) => {state.exports.push({query,columns})}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><StudentSummaryPage permissions={params.has('denied')?[]:params.has('readOnly')?['zsjos:student-summary:query']:['zsjos:student-summary:query','zsjos:student-summary:export']} /></App></ThemeProvider>)
