// UTF-8. Synthetic browser fixture for the production dialog; no business API writes.
import { createRoot } from 'react-dom/client'
import ClassExamProgressDialog from '../src/components/ClassExamProgressDialog'
import { http } from '../src/services/api'
import type { ExamProgressView } from '../src/services/classExamProgress'
import '../src/styles/index.css'
const fixture: ExamProgressView = { stage: 'STUDY', stageLabel: '常规学习', version: 0, actions: ['SPRINT'], outcomes: [{ value: 'graduated', label: '结业' }, { value: 'not_graduated', label: '未结业' }], students: [
  { serviceRelationId: 1, name: '验证学员甲', stage: 'STUDY', stageLabel: '制定学习计划', supervisionReady: false },
  { serviceRelationId: 2, name: '验证学员乙', stage: 'STUDY', stageLabel: '常规督学', supervisionReady: true },
] }
http.defaults.adapter = async config => ({ status: 200, statusText: 'OK', headers: {}, config, data: { code: 0, data: config.method === 'post' ? { changed: false, message: '以下学员尚未进入常规督学，无法进入考前冲刺', view: fixture, blockers: [fixture.students[0]] } : fixture } })
createRoot(document.getElementById('root')!).render(<ClassExamProgressDialog id={1} name="中世健验证班级" onClose={() => {}} />)
