import {createRoot} from "react-dom/client"
import {App} from "antd"
import {MemoryRouter} from "react-router-dom"
import ThemeProvider from "../src/components/Theme/ThemeProvider"
import {MyStudentsPage} from "../src/pages/RegistrationPages"
import {api,http,type StudentServiceSubject} from "../src/services/api"
import "../src/styles/index.css"
const params=new URLSearchParams(location.search)
localStorage.setItem("crm-theme",JSON.stringify({inboxLayoutMode:params.get("layout")||"table"}))
const rows:StudentServiceSubject[]=[1,2].map(id=>({personId:7,personNo:"TEST-7",name:"同一学员",serviceRelationId:id,service:{serviceRelationId:id,courseName:`课程${id}`,className:`班级${id}`,status:"active",acceptanceStatus:"pending",owner:true,version:0}}))
rows.forEach((r,i)=> { r.mobile='1390000000'+i; r.wechatId='fixture-'+i; r.service.ownerUserId=20+i; r.service.ownerUserName='规划师'+(i+1); r.service.skuName='具体方案'+(i+1); r.summary={deliveryStage:'supervision',deliveryStageLabel:'常规督学', examDate:'2026-11-10',currentTask:{id:30+i,type:'student_contact',status:'pending',dueAt:'2026-10-14 10:00:00',overdue:i===0},orderHandoff:{access:'denied'},infoSubmission:{access:'absent'}} })
if(params.has('saved'))localStorage.setItem('crm-student-management-table-columns',JSON.stringify({mobile:{show:false,order:2},activatedAt:{show:true,order:20}}))
const state={requests:[] as unknown[], writes:[] as number[],fail:false,delay:0}
Object.assign(window,{subjectFixture:state})
http.defaults.adapter=async()=>{throw Error("No live API in fixture")}
api.myStudentServices=async p=>{state.requests.push(p);const filtered=rows.filter(r=>!p.ownerUserId||r.service.ownerUserId===p.ownerUserId);return {list:structuredClone(filtered),total:filtered.length}}
api.myStudentService=async id=>{if(id===1&&state.delay)await new Promise(r=>setTimeout(r,state.delay));if(state.fail)throw Error("服务读取失败");return structuredClone(rows.find(r=>r.serviceRelationId===id)!)}
api.advancedFilterCatalog=async()=>({fields:[{fieldKey:'service.ownerUserId',label:'学习规划师（班主任）',group:'人员',valueType:'select',operators:['in'],options:[{value:'20',label:'规划师1'},{value:'21',label:'规划师2'}]}] as Awaited<ReturnType<typeof api.advancedFilterCatalog>>['fields'],relativeDateOptions:[]})
api.advancedFilterTemplates=async()=>[]
api.dictDataByType=async()=>[]
api.deliveryClasses.page=async()=>({list:[],total:0})
api.studentContactContext=async id=>({serviceRelationId:id,version:0,availableActions:rows[id-1].service.acceptanceStatus==="pending"?["ACCEPT"]:[],visibleTabs:[],firstContactChecklist:[],quickNotes:[],acceptanceStatus:rows[id-1].service.acceptanceStatus} as Awaited<ReturnType<typeof api.studentContactContext>>)
api.studentContactRecords=async()=>({list:[],total:0})
api.studentAccept=async id=>{state.writes.push(id);rows[id-1].service.acceptanceStatus="accepted";return true}
createRoot(document.getElementById("root")!).render(<ThemeProvider><App><MemoryRouter initialEntries={[{pathname:"/zsjos/my-students",state:params.has("legacy")?{personId:7}:undefined}]}><MyStudentsPage permissions={["zsjos:student:query-my","zsjos:student:accept"]}/></MemoryRouter></App></ThemeProvider>)
