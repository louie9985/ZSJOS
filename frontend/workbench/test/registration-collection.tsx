import {useState} from 'react'
import {createRoot} from 'react-dom/client'
import {App,Button} from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import RegistrationStudentInfo from '../src/components/RegistrationStudentInfo'
import {http,type RegistrationCase} from '../src/services/api'
import {studentInfoApi,type StudentInfoLink} from '../src/services/studentInfo'
import '../src/styles/index.css'
http.defaults.adapter=async()=>{throw Error('No live API allowed')}
let link:StudentInfoLink|null=null
let row:RegistrationCase={id:1,orderId:10,status:'processing',version:0,informationSent:false,informationSubmissionStatus:'NONE',completable:false}
const snapshot=()=>structuredClone(link)
studentInfoApi.link=async()=>snapshot()
studentInfoApi.generate=async()=>{if(!link){row.version++;link={formId:20,status:'DRAFT',registrationVersion:row.version,url:'https://h5.example.test/student-info-form#token=demo',canRegenerate:true,canRevoke:true};row.informationSubmissionStatus='DRAFT'}return snapshot()!}
studentInfoApi.sent=async(_,req)=>{if(req.version!==row.version)throw Error('版本冲突');row.version++;row.informationSent=req.sent;row.completable=req.sent;link!.registrationVersion=row.version;return snapshot()!}
studentInfoApi.regenerate=async()=>{row.version++;row.informationSent=false;row.completable=false;link!.registrationVersion=row.version;link!.url='https://h5.example.test/student-info-form#token=rotated';return snapshot()!}
studentInfoApi.revoke=async()=>{row.version++;row.informationSent=false;row.completable=false;link!.status='REVOKED';link!.url=undefined;link!.registrationVersion=row.version;return snapshot()!}
studentInfoApi.detail=async()=>({status:'DRAFT',fields:[],values:{},images:[],canReadSensitive:false,canExport:false})
function Fixture(){const[item,setItem]=useState({...row});return <div style={{padding:20,maxWidth:850,margin:'auto'}}><RegistrationStudentInfo item={item} permissions={location.search.includes('denied')?[]:['*:*:*']} disabled={false} onChanged={()=>setItem({...row})}/><Button disabled={!item.completable} onClick={()=>setItem({...item,status:'completed'})}>完成报名履约</Button><p>学员仍未提交</p></div>}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture/></App></ThemeProvider>)
