// UTF-8. Synthetic verification fixture; all business API calls are intercepted.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { App, Modal } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import StudentFirstContactProgress from '../src/components/StudentFirstContactProgress'
import { StudentContactForm } from '../src/pages/RegistrationPages'
import { api, type StudentContactContext } from '../src/services/api'
const query = new URLSearchParams(location.search)
const context: StudentContactContext = {
  serviceRelationId: 10, version: 2, acceptanceStatus: 'accepted', visibleTabs: ['overview'],
  availableActions: query.has('denied') ? [] : ['UPDATE_FIRST_CONTACT_CHECKLIST'],
  firstContactTimeoutMinutes: 120, studyPlanTimeoutMinutes: 4320, quickNotes: [],
  firstContactChecklist: [{ key: 'add', title: '添加学员', type: 'checkbox', attachmentRequired: false }, { key: 'group', title: '邀请进入学习群并推崇 IP 老师', type: 'checkbox', attachmentRequired: false }],
  currentTask: { id: 20, type: 'student_first_contact', status: 'pending' },
  firstContactProgress: query.has('empty') ? undefined : {
    recordId: 40, configVersionId: 30, source: 'snapshot', historyIncomplete: false,
    submittedAt: Date.now(), operatorUserId: 7, operatorUserName: '测试规划师', studyPlanCompletedAtFirstContact: true,
    studyPlanStatus: 'first_contact', studyPlanCompletedAt: Date.now(),
    items: [{key:'add',title:'添加学员',sort:1,completed:true,attachmentRequired:false,attachmentFileIds:[]}, {key:'group',title:'邀请进入学习群并推崇 IP 老师',sort:2,completed:false,attachmentRequired:false,attachmentFileIds:[]}]
  }
}
if (query.has('ordinary')) {
  context.currentTask = undefined; context.availableActions = ['FOLLOW_UP']; context.deliveryStage = 'supervision'
}
function Fixture() {
  const [value,setValue]=useState(context)
  const [done,setDone]=useState(false)
  return <div style={{maxWidth: 680,margin:'16px auto',padding:16}}>
    {query.has('form') ? <Modal title="首联" open={!done} footer={null} width={650}><StudentContactForm relationId={10} context={value} onDone={async()=>{setDone(true)}}/></Modal>
      : <StudentFirstContactProgress context={value} onRefresh={async()=>{const result=await api.studentContactContext(10);setValue(result)}}/>}
    {done && <p>首联已提交</p>}
  </div>
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><Fixture/></App></ThemeProvider></BrowserRouter>)
