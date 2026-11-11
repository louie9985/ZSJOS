import { createRoot } from 'react-dom/client';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { http } from '../src/services/api';
import { examPlannerApi } from '../src/services/examCollection';
import ExamPlannerProvider, { ExamPlannerNotice } from '../src/components/examCollection/ExamPlannerProvider';
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator';
import '../src/styles/index.css';

// Isolated fixture. No business requests or real notification receipts are sent.
http.defaults.adapter=async()=>{throw new Error('Unexpected fixture request')};
let unread=[901];
const todo={todoId:901,collectionTaskId:902,title:'考试资料收集验收任务',classes:['验收班级'],expected:2,collected:0,url:'https://example.com/exam-collection#token=fixture-only'};
examPlannerApi.state=async()=>({pending:[todo],unreadTodoIds:unread});
examPlannerApi.acknowledge=async ids=>{unread=unread.filter(id=>!ids.includes(id));return true};
examPlannerApi.members=async()=>({list:[{serviceRelationId:903,studentName:'验收学员',className:'验收班级',filled:false}],total:1});
createRoot(document.getElementById('root')!).render(<ConfigProvider><App><MemoryRouter><OverlayCoordinatorProvider><ExamPlannerProvider enabled><ExamPlannerNotice/></ExamPlannerProvider></OverlayCoordinatorProvider></MemoryRouter></App></ConfigProvider>);
