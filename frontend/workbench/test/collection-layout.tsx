import {http} from '../src/services/api'
import React from 'react'
import {createRoot} from 'react-dom/client'
import {MemoryRouter} from 'react-router-dom'
import {App,ConfigProvider} from 'antd'
import Theme from '../src/components/Theme/ThemeProvider'
import Page from '../src/pages/StudentInfoTasksPage'
import {collectionTasksApi,collectionTaskReader} from '../src/services/studentInfoTasks'
import '../src/styles/index.css'
// Read-only synthetic fixture; never sends business requests.
http.defaults.adapter = async () => { throw new Error('Unexpected fixture request') }
 const row={id:90001,orderId:90001,orderItemId:90001,personId:90001,formId:90001,version:1,orderNo:'OD-FIXTURE-001',studentName:'布局验收学员',productName:'公共营养师',orderStatus:'effective',status:'UNSENT',archiveStatus:'NONE',canProcess:true,candidates:[],specs:[{attrKey:'sale',attrName:'销售规格',value:'v1',label:'中国营养学会 | 三级 | 超值精品班'}]};
 collectionTasksApi.page=async()=>({list:[row],total:1});collectionTasksApi.detail=async()=>row;
 collectionTaskReader.detail=async()=>({id:90001,status:'DRAFT',canReadSensitive:false,canExport:false,fields:[{key:'name',label:'姓名',group:'basic',type:'text',sort:1}],values:{},images:[]});
 const host=document.createElement('div');host.style.height='100vh';document.body.replaceChildren(host);
 createRoot(host).render(React.createElement(ConfigProvider,null,React.createElement(Theme,null,React.createElement(App,null,React.createElement(MemoryRouter,{initialEntries:['/zsjos/student-info-tasks?taskId=90001']},React.createElement(Page,{permissions:['zsjos:student-info-task:query','zsjos:registration:query-pool','zsjos:student-info-form:read']}))))));
