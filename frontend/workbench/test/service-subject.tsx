import {createRoot} from "react-dom/client"
import {App} from "antd"
import {MemoryRouter} from "react-router-dom"
import ThemeProvider from "../src/components/Theme/ThemeProvider"
import {MyStudentsPage} from "../src/pages/RegistrationPages"
import {api,http,type StudentServiceSubject} from "../src/services/api"
import "../src/styles/index.css"
const params=new URLSearchParams(location.search)
localStorage.setItem("crm-theme",JSON.stringify({inboxLayoutMode:params.get("layout")||"inbox"}))
const rows:StudentServiceSubject[]=[1,2].map(id=>({personId:7,personNo:"TEST-7",name:"同一学员",serviceRelationId:id,service:{serviceRelationId:id,courseName:`课程${id}`,className:`班级${id}`,status:"active",acceptanceStatus:"pending",owner:true,version:0}}))
const state={writes:[] as number[],fail:false,delay:0}
Object.assign(window,{subjectFixture:state})
http.defaults.adapter=async()=>{throw Error("No live API in fixture")}
api.myStudentServices=async()=>({list:structuredClone(rows),total:rows.length})
api.myStudentService=async id=>{if(id===1&&state.delay)await new Promise(r=>setTimeout(r,state.delay));if(state.fail)throw Error("服务读取失败");return structuredClone(rows.find(r=>r.serviceRelationId===id)!)}
api.advancedFilterCatalog=async()=>({fields:[],relativeDateOptions:[]})
api.advancedFilterTemplates=async()=>[]
api.dictDataByType=async()=>[]
api.deliveryClasses.page=async()=>({list:[],total:0})
api.studentContactContext=async id=>({serviceRelationId:id,version:0,availableActions:rows[id-1].service.acceptanceStatus==="pending"?["ACCEPT"]:[],visibleTabs:[],firstContactChecklist:[],quickNotes:[],acceptanceStatus:rows[id-1].service.acceptanceStatus} as Awaited<ReturnType<typeof api.studentContactContext>>)
api.studentContactRecords=async()=>({list:[],total:0})
api.studentAccept=async id=>{state.writes.push(id);rows[id-1].service.acceptanceStatus="accepted";return true}
createRoot(document.getElementById("root")!).render(<ThemeProvider><App><MemoryRouter initialEntries={[{pathname:"/zsjos/my-students",state:params.has("legacy")?{personId:7}:undefined}]}><MyStudentsPage permissions={["zsjos:student:query-my","zsjos:student:accept"]}/></MemoryRouter></App></ThemeProvider>)
