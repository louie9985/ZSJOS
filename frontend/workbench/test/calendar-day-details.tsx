import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import CourseCalendarPage from '../src/pages/CourseCalendarPage'
import PersonalCalendarPage from '../src/pages/PersonalCalendarPage'
import { api, ApiError, type CourseCalendarEvent } from '../src/services/api'
import { calendarColors } from '../src/services/calendarColors'
import { courseAttachments } from '../src/services/courseAttachments'
const params = new URLSearchParams(location.search)
const day = (params.has('future') ? dayjs().add(2,'month') : dayjs()).format('YYYY-MM-DD')
const title = '完整课程名称验证：高级课程教学安排与实践指导'.repeat(5)
let rows: CourseCalendarEvent[] = [1,2,3].map((id) => ({ id, creatorName: '创建员工', courseName: id === 1 ? title : `相关课程${id}`, courseFormValue:'LIVE',courseFormLabelSnapshot:'直播', startTime:day+'T09:00:00',endTime:day+(id === 1 ? 'T09:05:00' : 'T10:00:00'), remark:'课程资料 https://example.com/course?id=1',attachmentIds: [11],attachments:[{fileId:11,name:'课程资料.pdf',type:'application/pdf'}] }))
calendarColors.get=async()=>({presets:[],colors:[]})
api.dictDataByType=async()=>[]
api.simpleDepartments=async()=>[]
api.courseCalendar.notifyUsers=async()=>({list:[],total:0})
api.courseCalendar.page=async()=>rows
api.courseCalendar.get=async id=>{
 if(params.has('missing'))throw new ApiError(1900020001,'课程安排不存在')
 if(params.has('forbidden'))throw new ApiError(403,'无权限')
 const row=rows.find(row=>row.id===id);if(!row)throw new ApiError(404,'不存在');return row
}
api.courseCalendar.delete=async id=>{rows=rows.filter(row=>row.id!==id);return true}
api.personalCalendar.list=async()=>rows.map(row=>({id:row.id,title:row.courseName,creatorName:row.creatorName,ownerName:'所属员工',startTime:row.startTime,endTime:row.endTime,allDay:false,status:'ACTIVE',sourceType:'MANUAL',description:row.remark,createTime:row.startTime,updateTime:row.startTime}))
courseAttachments.read=async()=>{
 if(params.has('fileError'))throw new Error('附件暂不可用，请重试')
 return {fileId:11,name:'课程资料.pdf',type:'application/pdf',url:location.origin+'/test/day-details.pdf'}
}
const permissions=params.has('readonly')?[]:['*:*:*']
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>{params.has('personal')?<PersonalCalendarPage permissions={permissions}/>:<CourseCalendarPage permissions={permissions}/>}</App></ThemeProvider></BrowserRouter>)
