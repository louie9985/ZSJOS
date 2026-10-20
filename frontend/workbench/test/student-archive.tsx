import {createRoot} from 'react-dom/client'
import {App} from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import StudentArchivePanel from '../src/components/StudentArchivePanel'
import {studentArchiveApi} from '../src/services/studentArchive'
import {studentArchiveExtraApi} from '../src/services/studentArchiveExtra'
import '../src/styles/index.css'
let revision=0,job='原职业'
studentArchiveApi.detail=async()=>({personId:1,serviceRelationId:2,revision,canMaintain:!location.search.includes('read'),productName:'合成报名产品',history:revision?[{revision,operatorUserId:1,operatorName:'测试维护人',modifiedAt:'2026-10-18',reason:'测试修正'}]:[],information:{status:'SUBMITTED',fields:[{key:'job',label:'从事职业',type:'text',enabled:true,required:true,sort:1,group:'career',sensitive:false},{key:'education_level',label:'现学历',type:'dict',enabled:true,required:true,sort:2,group:'basic',sensitive:false}],values:{job,education_level:'历史学历标签'},images:[],canReadSensitive:false,canExport:false}})
studentArchiveApi.edit=async()=>({revision,values:{job,education_level:'old'},options:{education_level:[{value:'new',label:'当前学历标签'}]},images:[]})
studentArchiveApi.areas=async()=>[]
studentArchiveApi.save=async(_,rev,changes)=>{if(rev!==revision)throw Error('版本冲突');job=String(changes.job);return ++revision}
const extraFields=[['channel','对接渠道','text'],['registration_fee','报名费','number'],['admission_ticket','准考证','composite'],['theory','理论','composite'],['skill','技能','composite'],['thesis','论文/综合评审','composite'],['assessment_result','评定结果','text'],['issuing_authority','发证单位','composite'],['certificate_images','证书图片','composite'],['certificate_shipping_address','证书邮寄地址','textarea'],['certificate_tracking_no','证书邮寄单号','text']]
studentArchiveExtraApi.detail=async()=>({revision:0,visibilityRevision:0,canMaintain:!location.search.includes('read'),canReadAttachments:false,canConfigureVisibility:location.search.includes('config'),fields:extraFields.map(([key,label,type])=>({key,label,type,allowedExtensions:['pdf','jpg','png']})),values:{channel:{text:'教务转介绍',links:[],attachmentIds:[]},theory:{text:'2026-04-18 09:00',links:['https://example.com/theory'],attachmentIds:[]},skill:{text:'待安排',links:[],attachmentIds:[]}},educationVisibility:Object.fromEntries(extraFields.map(([key])=>[key,key!=='skill'])),attachments:[],history:[{revision:1,operatorUserId:1,operatorName:'测试维护人',modifiedAt:'2026-10-18',reason:'测试修正'}]})
studentArchiveExtraApi.save=async()=>1
studentArchiveExtraApi.saveVisibility=async()=>1
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><StudentArchivePanel relationId={2}/></App></ThemeProvider>)
